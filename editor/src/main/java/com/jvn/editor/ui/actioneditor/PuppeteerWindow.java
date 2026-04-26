package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineRegistry;
import com.jvn.core.animation.TimelineRunner;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePresetLoader;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
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
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class PuppeteerWindow extends Stage {
    private static final double LEFT_LIBRARY_WORKING_WIDTH = 360.0;
    private static final List<PropertyType> GROUP_PROPERTY_CHOICES = List.of(
        PropertyType.X,
        PropertyType.Y,
        PropertyType.Z,
        PropertyType.ROTATION,
        PropertyType.SCALE_X,
        PropertyType.SCALE_Y,
        PropertyType.ALPHA
    );
    private static final List<PropertyType> ENTITY_MATRIX_PROPERTY_CHOICES = List.of(
        PropertyType.MATRIX_MXX,
        PropertyType.MATRIX_MXY,
        PropertyType.MATRIX_MYX,
        PropertyType.MATRIX_MYY,
        PropertyType.MATRIX_TX,
        PropertyType.MATRIX_TY,
        PropertyType.BLUR
    );
    private static final List<String> ENTITY_DEDICATED_CUSTOM_KEYS = List.of(
        "matrix.mxx", "matrix.mxy", "matrix.myx", "matrix.myy", "matrix.tx", "matrix.ty",
        "effect.blur",
        "color.m00", "color.m01", "color.m02", "color.m03", "color.m04",
        "color.m10", "color.m11", "color.m12", "color.m13", "color.m14",
        "color.m20", "color.m21", "color.m22", "color.m23", "color.m24",
        "color.m30", "color.m31", "color.m32", "color.m33", "color.m34"
    );
    private static final List<String> CAMERA_DEDICATED_CUSTOM_KEYS = List.of(
        "dof.focus", "dof.strength", "dof.maxBlur"
    );
    private static final double PUPPETEER_FROST_BLUR_RADIUS = 5.5;
    private static final double PUPPETEER_FROST_OPACITY = 0.72;
    private static final Duration PUPPETEER_FROST_DURATION = Duration.millis(135);
    private static final String PUPPETEER_FROST_OVERLAY_KEY = "jvn.puppeteerFrost.overlay";
    private static final String PUPPETEER_FROST_BLUR_KEY = "jvn.puppeteerFrost.blur";
    private static final String PUPPETEER_FROST_BASE_EFFECT_KEY = "jvn.puppeteerFrost.baseEffect";
    private static final String PUPPETEER_FROST_BASE_EFFECT_PRESENT_KEY = "jvn.puppeteerFrost.baseEffectPresent";
    private static final String PUPPETEER_FROST_ANIMATION_KEY = "jvn.puppeteerFrost.animation";
    private static final String PUPPETEER_FROST_HANDLER_KEY = "jvn.puppeteerFrost.handlerInstalled";

    private final AnimationProject project;
    private JesScene2D scene;

    private final EntitySelector entitySelector;
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
    private ToggleButton cbRuntimePreview;

    private AnimationTimer playbackTimer;
    private long lastNanos = 0;
    private double playbackSpeed = 1.0;
    private boolean autoKeyEnabled = false;
    private boolean runtimeParityPreview = false;

    private final PuppeteerCommand.Stack commandStack = new PuppeteerCommand.Stack();
    private final KeyframeSelectionModel selectionModel;
    private Consumer<String> onCopyCode;
    private final TextField tfTimelineName;
    private final Map<String, CollapsibleToolbarCluster> toolbarClusters = new LinkedHashMap<>();
    private AnimatedToolbarPane toolbarPane;
    private HBox toolbarCommandBar;
    private VBox toolbarShell;
    private Label lblToolbarCommandSummary;
    private Label statusBar;
    private Label viewportInfoLabel;
    private Button btnSidebarPreviewLayout;
    private BorderPane previewPane;
    private StackPane previewViewportHost;
    private Button btnPreviewFullscreen;
    private Button btnPreviewBack;
    private MediaPlayer audioPreviewPlayer;
    private Label lblSidebarSelectionTarget;
    private Label lblSidebarSelectionScope;
    private Label lblSidebarSelectionProperty;
    private Label lblSidebarSelectionPlayhead;
    private Label lblSidebarSelectionCount;
    private VBox sidebarAdvancedUnavailableCard;
    private VBox sidebarMatrixCard;
    private VBox sidebarColorMatrixCard;
    private VBox sidebarCameraDofCard;
    private VBox sidebarCustomChannelsCard;
    private Label lblSidebarAdvancedHint;
    private ComboBox<String> cbSidebarCustomPropertyKey;
    private TextField tfSidebarCustomPropertyValue;
    private final TextField[] sidebarMatrixFields = new TextField[7];
    private final TextField[] sidebarColorMatrixFields = new TextField[20];
    private final TextField[] sidebarCameraDofFields = new TextField[3];
    private Label lblSidebarSceneTracks;
    private Label lblSidebarSceneGroups;
    private Label lblSidebarSceneDuration;
    private Label lblSidebarSceneViewport;
    private Label lblSidebarSceneCamera;
    private Label lblSidebarSceneCodePane;
    private Label lblSidebarSceneAnchors;
    private Label lblSidebarSceneStage;
    private Button btnSidebarAddKeyframe;
    private Button btnSidebarFocusSelection;
    private Button btnSidebarClearSelection;
    private Button btnSidebarCodePane;
    private Stage assetImporterWindow;
    private AssetPickerPanel assetImporterPanel;
    private SplitPane topWorkspaceSplit;
    private SplitPane bottomWorkspaceSplit;
    private SplitPane workspaceContentSplit;
    private SplitPane mainWorkspaceSplit;
    private SplitPane previewFocusSplit;
    private StackPane workspaceModeHost;
    private Node puppeteerLeftFrostTarget;
    private Node puppeteerKeyframeFrostTarget;
    private Node puppeteerCodeFrostTarget;
    private StackPane codePreviewFrostShell;
    private Node activePuppeteerFrostNode;
    private boolean puppeteerFrostSceneReleaseInstalled = false;
    private boolean dirty = false;
    private boolean compactExport = false;
    private boolean previewStaged = false;
    private boolean dirtyBeforePreviewStage = false;
    private AnimationProject previewBaselineProject;
    private TransformInteractionState activeTransformInteraction;
    private CameraInteractionState activeCameraInteraction;
    private final ActionEditorDialogOverlay overlayDialog = new ActionEditorDialogOverlay();
    private final Map<String, String> launchCharacterImagePaths = new LinkedHashMap<>();
    private final Map<String, String> launchBackgroundPaths = new LinkedHashMap<>();
    private final Map<String, String> sceneBaselineImagePaths = new LinkedHashMap<>();
    private final Map<String, Boolean> sceneBaselineVisibility = new LinkedHashMap<>();
    private final Map<String, Map<String, Double>> sceneBaselineCustomProperties = new LinkedHashMap<>();
    private final Map<String, Double> sceneBaselineCameraCustomProperties = new LinkedHashMap<>();
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
    private static final double TOOLBAR_SHELL_SPACING_DYNAMIC = 6.0;
    private static final double TOOLBAR_SHELL_SPACING_COMPACT = 1.0;
    private static final String PROP_TOOLBAR_BASE_SPACING = "puppeteerToolbarBaseSpacing";
    private static final String PROP_TOOLBAR_BASE_PREF_WIDTH = "puppeteerToolbarBasePrefWidth";
    private static final String PROP_TOOLBAR_BASE_PREF_HEIGHT = "puppeteerToolbarBasePrefHeight";
    private static final String PROP_TOOLBAR_BASE_ICON_WIDTH = "puppeteerToolbarBaseIconWidth";
    private static final String PROP_TOOLBAR_BASE_ICON_HEIGHT = "puppeteerToolbarBaseIconHeight";
    private static final PropertyType[] TRANSFORM_INTERACTION_PROPERTIES = {
        PropertyType.X,
        PropertyType.Y,
        PropertyType.PIVOT_X,
        PropertyType.PIVOT_Y,
        PropertyType.ROTATION,
        PropertyType.MATRIX_MXX,
        PropertyType.MATRIX_MXY,
        PropertyType.MATRIX_MYX,
        PropertyType.MATRIX_MYY,
        PropertyType.MATRIX_TX,
        PropertyType.MATRIX_TY
    };
    private static final PropertyType[] CAMERA_INTERACTION_PROPERTIES = {
        PropertyType.CAMERA_X,
        PropertyType.CAMERA_Y,
        PropertyType.CAMERA_ZOOM
    };

    private record TransformInteractionState(
        String entityName,
        double timeMs,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> beforeStates
    ) {}

    private record CameraInteractionState(
        double timeMs,
        Map<PropertyType, PuppeteerCommand.PropertySnapshot> beforeStates
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
        timelinePanel = new TimelinePanel(this.project, commandStack);
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
        animationPreview.setOnCameraInteractionStarted(() -> {
            double time = this.project.getPlayheadMs();
            EntityTrack track = resolveRuntimeCameraTrack(true);
            activeCameraInteraction = new CameraInteractionState(
                time,
                captureTrackSnapshots(track, time, CAMERA_INTERACTION_PROPERTIES, null)
            );
        });
        animationPreview.setOnCameraMoved(state -> {
            if (state == null || state.length < 3) return;
            EntityTrack track = resolveRuntimeCameraTrack(true);
            if (track == null) return;
            double time = activeCameraInteraction != null ? activeCameraInteraction.timeMs() : this.project.getPlayheadMs();
            track.upsertKeyframe(PropertyType.CAMERA_X, new Keyframe(time, state[0]));
            track.upsertKeyframe(PropertyType.CAMERA_Y, new Keyframe(time, state[1]));
            track.sortKeyframes(PropertyType.CAMERA_X);
            track.sortKeyframes(PropertyType.CAMERA_Y);
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });
        animationPreview.setOnCameraInteractionFinished(state -> {
            CameraInteractionState interaction = activeCameraInteraction;
            activeCameraInteraction = null;
            if (interaction == null || state == null || state.length < 3) return;
            EntityTrack track = resolveRuntimeCameraTrack(true);
            if (track == null) return;
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> afterStates =
                captureTrackSnapshots(track, interaction.timeMs(), CAMERA_INTERACTION_PROPERTIES, null);
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> beforeStates = interaction.beforeStates();
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> changedBefore = new EnumMap<>(PropertyType.class);
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> changedAfter = new EnumMap<>(PropertyType.class);
            for (PropertyType property : CAMERA_INTERACTION_PROPERTIES) {
                PuppeteerCommand.PropertySnapshot before = beforeStates.get(property);
                PuppeteerCommand.PropertySnapshot after = afterStates.get(property);
                if (!transformChanged(property, before, after)) continue;
                changedBefore.put(property, before != null ? before : new PuppeteerCommand.PropertySnapshot(false, 0.0));
                changedAfter.put(property, after != null ? after : new PuppeteerCommand.PropertySnapshot(false, 0.0));
            }
            if (changedBefore.isEmpty()) {
                restoreTrackSnapshots(track, interaction.timeMs(), beforeStates);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
                return;
            }
            commandStack.execute(PuppeteerCommand.applyPropertiesAtTime(
                track,
                interaction.timeMs(),
                changedBefore,
                changedAfter,
                "Edit runtime camera"
            ));
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });
        animationPreview.setOnAssetDropped(payload -> {
            if (payload == null || !payload.isValid()) return;
            addAssetToScene(payload.relativePath(), payload.suggestedName(), PuppeteerAssetPlacementRole.PROP);
        });
        codePreview = new CodePreviewPane();
        keyframeEditor.setCameraState(animationPreview.getCamera().getX(), animationPreview.getCamera().getY(), animationPreview.getCamera().getZoom());

        timelinePanel.setOnTargetSelectionChanged((name, isGroup) -> {
            keyframeEditor.setSelectionContext(
                selectionLabel(name, isGroup),
                isGroup,
                timelinePanel.isRuntimeCameraSelected());
            animationPreview.setRuntimeCameraSelected(timelinePanel.isRuntimeCameraSelected());
            if (timelinePanel.isRuntimeCameraSelected()) {
                entitySelector.selectEntity(null);
                animationPreview.clearSelection();
            } else if (isGroup) {
                entitySelector.selectGroup(name);
                animationPreview.selectGroup(name);
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

        entitySelector.setOnDeleteSelection(this::deleteSceneSelection);

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

        entitySelector.setOnEntityVisibilityChanged((entityName, visible) -> {
            EntityTrack track = this.project.getTrack(entityName);
            if (track == null) return;
            track.setVisible(visible);
            entitySelector.refresh(this.project);
            entitySelector.selectEntity(entityName);
            updatePreview();
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
        timelinePanel.setOnEdited(() -> {
            syncDurationUi();
            refreshExportPreviewAndMarkDirty();
        });

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
            EntityTrack track = resolveAnimatedTrack(name, true);
            if (track == null) return;
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

        animationPreview.setOnEntityMatrixChanged((name, matrixValues) -> {
            if (name == null || name.isBlank() || matrixValues == null || matrixValues.length < 6) return;
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            PropertyType[] properties = {
                PropertyType.MATRIX_MXX,
                PropertyType.MATRIX_MXY,
                PropertyType.MATRIX_MYX,
                PropertyType.MATRIX_MYY,
                PropertyType.MATRIX_TX,
                PropertyType.MATRIX_TY
            };
            for (int i = 0; i < properties.length; i++) {
                double value = matrixValues[i];
                if (!Double.isFinite(value)) continue;
                track.upsertKeyframe(properties[i], new Keyframe(time, value));
                track.sortKeyframes(properties[i]);
            }
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

        Button btnLoopIn = makeToolbarIconButton("icon-puppeteer-loop-in", "Set loop IN at playhead");
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

        Button btnLoopOut = makeToolbarIconButton("icon-puppeteer-loop-out", "Set loop OUT at playhead");
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

        Button slotButton = makeToolbarIconButton("icon-puppeteer-slot", "Place selected entity at a VN character slot");
        slotButton.setOnAction(e -> showSlotMenuOverlay());

        Button btnBatchKeyframe = makeToolbarIconButton("icon-puppeteer-batch-key", "Add keyframe for ALL entities at playhead (batch)");
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

        Button btnDistributeKeys = makeToolbarIconButton("icon-puppeteer-distribute", "Distribute selected keyframes evenly across their current range");
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

        Button btnStretchKeys = makeToolbarIconButton("icon-puppeteer-stretch", "Stretch selected keyframes 25% wider");
        btnStretchKeys.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(1.25)) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnCompressKeys = makeToolbarIconButton("icon-puppeteer-compress", "Compress selected keyframes to 80% of their current range");
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

        cbRuntimePreview = makeToolbarIconToggle(
            "icon-puppeteer-register",
            "Runtime data preview: render through TimelineData/TimelineRunner"
        );
        cbRuntimePreview.setSelected(runtimeParityPreview);
        cbRuntimePreview.setOnAction(e -> {
            runtimeParityPreview = cbRuntimePreview.isSelected();
            updatePreview();
            updateStatusBar();
        });

        // --- Auto-key toggle ---
        ToggleButton cbAutoKey = makeToolbarIconToggle("icon-puppeteer-autokey", "Auto-key: automatically insert keyframe on drag");
        cbAutoKey.setSelected(false);
        cbAutoKey.setOnAction(e -> autoKeyEnabled = cbAutoKey.isSelected());
        Label lblAutoKey = new Label("Auto");
        lblAutoKey.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10px;");

        // --- Snap-to-grid / snap-to-entity toggles ---
        ToggleButton cbSnapGrid = makeToolbarIconToggle("icon-puppeteer-snap", "Snap entities to grid when dragging");
        cbSnapGrid.setSelected(false);
        cbSnapGrid.setOnAction(e -> animationPreview.setSnapToGridEnabled(cbSnapGrid.isSelected()));
        ToggleButton cbSnapEntity = makeToolbarIconToggle("icon-puppeteer-snap-entity", "Snap to nearby entity positions");
        cbSnapEntity.setSelected(false);
        cbSnapEntity.setOnAction(e -> animationPreview.setSnapToEntityEnabled(cbSnapEntity.isSelected()));

        HBox autoKeyBox = new HBox(4, cbAutoKey, lblAutoKey);
        autoKeyBox.setAlignment(Pos.CENTER_LEFT);
        HBox previewSnapBox = new HBox(4, cbSnapGrid, cbSnapEntity, cbRuntimePreview, cbSpeed, cbWheelMode);
        previewSnapBox.setAlignment(Pos.CENTER_LEFT);

        cbOrbitTool = makeToolbarIconToggle("icon-puppeteer-orbit", "Enable orbit-anchor tool. Shift+click preview to place anchor. Alt+Shift+click another entity to link the anchor at the exact cursor point (joint/nail).");
        cbOrbitTool.setSelected(animationPreview.isOrbitToolEnabled());
        cbOrbitTool.setOnAction(e -> animationPreview.setOrbitToolEnabled(cbOrbitTool.isSelected()));

        cbOrbitAlign = makeToolbarIconToggle("icon-puppeteer-orbit-align", "When orbiting, update entity rotation to face outward.");
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
        Button btnHelp = makeToolbarIconButton("icon-puppeteer-help", "Show keyboard shortcuts");
        btnHelp.setOnAction(e -> showShortcutsOverlay());

        // --- Audio + event cues ---
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
        Button btnManageEvents = makeToolbarIconButton("icon-puppeteer-events", "Manage timeline event cues");
        btnManageEvents.setOnAction(e -> showEventCueManagerDialog(null));
        Button btnClearEvents = makeToolbarIconButton("icon-puppeteer-audio-clear", "Remove all timeline event cues");
        btnClearEvents.setOnAction(e -> {
            if (project.getEditorEventCues().isEmpty()) return;
            overlayDialog.showDialog(
                "Clear Event Cues",
                "Remove all timeline event cues from this animation?",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    project.clearEditorEventCues();
                    timelinePanel.refresh();
                    updatePreview();
                    refreshExportPreviewAndMarkDirty();
                })
            );
        });
        HBox cueBox = new HBox(4, btnAddCue, btnClearCues, btnManageEvents, btnClearEvents);
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
        btnRegister.setOnAction(e -> requestRegisterTimeline());

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
        CollapsibleToolbarCluster audioCluster = registerToolbarCluster("audio", "Cues", cueBox);
        CollapsibleToolbarCluster registerCluster = registerToolbarCluster("register", "Register", nameBox);
        CollapsibleToolbarCluster helpCluster = registerToolbarCluster("help", "Help", btnHelp);

        toolbarPane = new AnimatedToolbarPane(8, 5);
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

        toolbarCommandBar = buildToolbarCommandBar();

        toolbarShell = new VBox(6, toolbarCommandBar, toolbarPane) {
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
                double clustersHeight = toolbarPane.prefHeight(contentWidth);
                return insets.getTop()
                    + commandBarHeight
                    + getSpacing()
                    + clustersHeight
                    + insets.getBottom();
            }
        };
        toolbarShell.getStyleClass().add("puppeteer-toolbar-shell");
        toolbarShell.setFillWidth(true);
        toolbarShell.setMinHeight(Region.USE_PREF_SIZE);
        toolbarShell.setMaxWidth(Double.MAX_VALUE);
        setToolbarClustersExpanded(true);
        setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.COMPACT);

        entitySelector.setMinWidth(0);

        Tab entitiesTab = new Tab("Entities", entitySelector);
        entitiesTab.setClosable(false);
        Tab selectionTab = buildSelectionTab();
        selectionTab.setClosable(false);
        Tab sceneTab = buildSceneTab();
        sceneTab.setClosable(false);
        TabPane leftTabs = new TabPane(entitiesTab, selectionTab, sceneTab);
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
        leftTabsScrollPane.setPannable(false);
        leftTabsScrollPane.setMinHeight(0);
        leftTabsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftTabsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftTabsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        puppeteerLeftFrostTarget = leftTabsScrollPane;
        StackPane leftTabsFrostShell = createPuppeteerFrostShell(leftTabsScrollPane, "left");

        keyframeEditor.setMinWidth(0);
        keyframeEditor.setMinHeight(0);
        puppeteerKeyframeFrostTarget = keyframeEditor;
        StackPane keyframeFrostShell = createPuppeteerFrostShell(keyframeEditor, "left");
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
        btnPreviewFullscreen.getStyleClass().add("puppeteer-preview-overlay-button-idle");
        btnPreviewFullscreen.setText("Focus");
        btnPreviewFullscreen.setContentDisplay(ContentDisplay.LEFT);
        btnPreviewFullscreen.setGraphicTextGap(6);
        btnPreviewFullscreen.setMinSize(Region.USE_PREF_SIZE, 34);
        btnPreviewFullscreen.setPrefSize(Region.USE_COMPUTED_SIZE, 34);
        btnPreviewFullscreen.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        btnPreviewFullscreen.setManaged(false);
        btnPreviewFullscreen.setVisible(false);
        btnPreviewFullscreen.setOnAction(e -> enterFullscreenPreview());

        btnPreviewBack = makeToolbarIconButton("icon-puppeteer-rewind", "Return to the standard editor workspace");
        btnPreviewBack.getStyleClass().add("puppeteer-preview-overlay-button");
        btnPreviewBack.getStyleClass().add("puppeteer-preview-overlay-button-back");
        btnPreviewBack.setText("◀ Back to Editor");
        btnPreviewBack.setContentDisplay(ContentDisplay.LEFT);
        btnPreviewBack.setGraphicTextGap(8);
        btnPreviewBack.setMinSize(160, 42);
        btnPreviewBack.setPrefSize(160, 42);
        btnPreviewBack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        btnPreviewBack.setManaged(false);
        btnPreviewBack.setVisible(false);
        btnPreviewBack.setOnAction(e -> exitFullscreenPreview());

        previewViewportHost = new StackPane(animationPreview, btnPreviewBack, btnPreviewFullscreen);
        previewViewportHost.getStyleClass().add("puppeteer-preview-viewport-host");
        previewViewportHost.setMinHeight(0);
        previewViewportHost.hoverProperty().addListener((obs, wasHover, isHover) -> updatePreviewOverlayVisibility());
        // Unmanaged nodes ignore StackPane alignment, so position manually.
        // Keep fullscreen/back controls pinned to the same top-right anchor.
        btnPreviewBack.layoutXProperty().bind(
            previewViewportHost.widthProperty()
                .subtract(btnPreviewBack.widthProperty())
                .subtract(14));
        btnPreviewBack.setLayoutY(8);
        btnPreviewFullscreen.layoutXProperty().bind(
            previewViewportHost.widthProperty()
                .subtract(btnPreviewFullscreen.widthProperty())
                .subtract(14));
        btnPreviewFullscreen.setLayoutY(8);
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
        topWorkspaceSplit.getItems().addAll(leftTabsFrostShell, previewPane);
        SplitPane.setResizableWithParent(leftTabsFrostShell, Boolean.TRUE);
        SplitPane.setResizableWithParent(previewPane, Boolean.TRUE);
        topWorkspaceSplit.setDividerPositions(0.2);

        bottomWorkspaceSplit = new SplitPane();
        bottomWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        bottomWorkspaceSplit.setOrientation(Orientation.HORIZONTAL);
        bottomWorkspaceSplit.setMinWidth(0);
        bottomWorkspaceSplit.setMinHeight(0);
        bottomWorkspaceSplit.getItems().addAll(keyframeFrostShell, timelinePanel);
        SplitPane.setResizableWithParent(keyframeFrostShell, Boolean.TRUE);
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
        puppeteerCodeFrostTarget = codePreview;
        codePreviewFrostShell = createPuppeteerFrostShell(codePreview, "right");
        mainWorkspaceSplit.getItems().addAll(workspaceContentSplit, codePreviewFrostShell);
        SplitPane.setResizableWithParent(workspaceContentSplit, Boolean.TRUE);
        SplitPane.setResizableWithParent(codePreviewFrostShell, Boolean.TRUE);
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

        installPuppeteerFrostHandlers();
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
        miSaveRegister.setOnAction(e -> requestRegisterTimeline());
        MenuItem miVerifyRuntime = new MenuItem("Verify Runtime Registration...");
        miVerifyRuntime.setOnAction(e -> showRuntimeVerificationReport());
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
        MenuItem miImportAssets = new MenuItem("Import Assets...");
        miImportAssets.setOnAction(e -> showAssetImporterWindow());
        MenuItem miClose = new MenuItem("Close Puppeteer");
        miClose.setOnAction(e -> requestWindowClose());

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
            miSaveRegister,
            miVerifyRuntime,
            new SeparatorMenuItem(),
            miRefreshCode,
            miStagePreview,
            miCommitPreview,
            miDiscardPreview,
            new SeparatorMenuItem(),
            miCopyExportCode,
            miSaveClip,
            miLoadClip,
            miImportAssets,
            new SeparatorMenuItem(),
            miClose
        );
        fileMenu.setOnShowing(e -> {
            String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
            boolean hasTimelineName = timelineName != null && !timelineName.isBlank();
            boolean hasTrack = selectedTrackForEditing(false) != null;
            miSaveRegister.setText(dirty || previewStaged ? "Save & Register" : "Save & Register Again");
            miSaveRegister.setDisable(!hasTimelineName);
            miVerifyRuntime.setDisable(project == null);
            miStagePreview.setText(previewStaged ? "Restage Code Preview" : "Stage Code Preview");
            miStagePreview.setDisable(codePreview == null || codePreview.getCode() == null || codePreview.getCode().isBlank());
            miCommitPreview.setDisable(!previewStaged);
            miDiscardPreview.setDisable(!previewStaged);
            miCopyExportCode.setDisable(codePreview == null || codePreview.getCode() == null || codePreview.getCode().isBlank());
            miSaveClip.setDisable(!hasTrack || projectRoot == null);
            miLoadClip.setDisable(!hasTrack || !hasSavedClips());
            miImportAssets.setDisable(projectRoot == null || !projectRoot.isDirectory());
            miClose.setText(dirty || previewStaged ? "Close..." : "Close");
        });

        MenuItem miUndo = new MenuItem("Undo");
        miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        miUndo.setOnAction(e -> executeUndo());
        MenuItem miRedo = new MenuItem("Redo");
        miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
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
        CheckMenuItem miShowSafeGuides = new CheckMenuItem("Show Safe Guides");
        miShowSafeGuides.setOnAction(e -> animationPreview.setShowSafeGuides(miShowSafeGuides.isSelected()));
        CheckMenuItem miShowTitleGuides = new CheckMenuItem("Show Title Guides");
        miShowTitleGuides.setOnAction(e -> animationPreview.setShowTitleGuides(miShowTitleGuides.isSelected()));
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

        MenuItem miExpandAll = new MenuItem("Expand All Clusters");
        miExpandAll.setOnAction(e -> toolbarClusters.values().forEach(c -> c.setExpanded(true)));
        MenuItem miCollapseAll = new MenuItem("Collapse All Clusters");
        miCollapseAll.setOnAction(e -> toolbarClusters.values().forEach(c -> c.setExpanded(false)));

        Menu toolbarClustersMenu = new Menu("Toolbar Clusters");
        toolbarClustersMenu.setOnShowing(e -> {
            toolbarClustersMenu.getItems().clear();
            for (CollapsibleToolbarCluster cluster : toolbarClusters.values()) {
                CheckMenuItem mi = new CheckMenuItem(cluster.getTitle());
                mi.setSelected(cluster.isExpanded());
                mi.setOnAction(ev -> cluster.setExpanded(mi.isSelected()));
                toolbarClustersMenu.getItems().add(mi);
            }
        });

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
            miShowCodePane,
            miOnionSkin,
            miShowSafeGuides,
            miShowTitleGuides,
            new SeparatorMenuItem(),
            miLayoutDynamic,
            miLayoutCompact,
            new SeparatorMenuItem(),
            toolbarClustersMenu,
            miExpandAll,
            miCollapseAll,
            new SeparatorMenuItem(),
            miFocusTimeline,
            miZoomFit,
            miFullscreenPreview
        );
        viewMenu.setOnShowing(e -> {
            miShowCodePane.setSelected(codePaneVisible);
            miOnionSkin.setSelected(animationPreview.isOnionSkinning());
            miShowSafeGuides.setSelected(animationPreview.isShowSafeGuides());
            miShowTitleGuides.setSelected(animationPreview.isShowTitleGuides());
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

        lblToolbarCommandSummary = null;

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, menuBar, spacer);
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
            keyframeEditor.setSelectionContext(null, false, false);
            keyframeEditor.setKeyframe(null, null);
            refreshSidebarTabs();
        });

        HBox selectionActionsPrimary = buildSidebarButtonRow(btnSidebarAddKeyframe, btnSidebarFocusSelection);
        HBox selectionActionsSecondary = buildSidebarButtonRow(btnPrevKey, btnNextKey, btnSidebarClearSelection);

        sidebarAdvancedUnavailableCard = buildSidebarCard(
            "Advanced",
            createSidebarHintLabel("Advanced transform and custom-channel authoring is available for entity and runtime camera tracks."));
        sidebarMatrixCard = buildSidebarCard("Matrix / Blur", buildSidebarMatrixInspector());
        sidebarColorMatrixCard = buildSidebarCard("Color Matrix", buildSidebarColorMatrixInspector());
        sidebarCameraDofCard = buildSidebarCard("Camera DOF", buildSidebarCameraDofInspector());
        sidebarCustomChannelsCard = buildSidebarCard("Custom Channels", buildSidebarCustomChannelInspector());

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
            ),
            sidebarAdvancedUnavailableCard,
            sidebarMatrixCard,
            sidebarColorMatrixCard,
            sidebarCameraDofCard,
            sidebarCustomChannelsCard
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
        lblSidebarSceneStage = buildSidebarValueLabel();

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
                buildSidebarInfoBlock("Orbit Anchors", lblSidebarSceneAnchors),
                buildSidebarInfoBlock("Lighting Stage", lblSidebarSceneStage)
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

    private Node buildSidebarMatrixInspector() {
        String[] captions = {"MXX", "MXY", "MYX", "MYY", "TX", "TY", "Blur"};
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int i = 0; i < captions.length; i++) {
            Label label = new Label(captions[i]);
            label.setStyle(STYLE_SIDEBAR_META_LABEL);
            TextField field = buildSidebarNumberField(captions[i].toLowerCase(Locale.ROOT));
            sidebarMatrixFields[i] = field;
            int row = i / 2;
            int col = (i % 2) * 2;
            if (i == captions.length - 1) {
                row = 3;
                col = 0;
            }
            grid.add(label, col, row);
            grid.add(field, col + 1, row);
        }

        Button btnFillIdentity = buildSidebarActionButton("Fill Identity", this::fillSidebarMatrixIdentity);
        Button btnKeyMatrix = buildSidebarActionButton("Key At Playhead", this::applySidebarMatrixKeyframes);
        return new VBox(8, grid, buildSidebarButtonRow(btnFillIdentity, btnKeyMatrix));
    }

    private Node buildSidebarColorMatrixInspector() {
        VBox body = new VBox(8);
        lblSidebarAdvancedHint = createSidebarHintLabel("RGBA 4x5 matrix. Values are keyed as dedicated color channels at the current playhead.");
        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        String[] rowLabels = {"R", "G", "B", "A"};
        for (int row = 0; row < 4; row++) {
            Label rowLabel = new Label(rowLabels[row]);
            rowLabel.setStyle(STYLE_SIDEBAR_META_LABEL);
            grid.add(rowLabel, 0, row);
            for (int col = 0; col < 5; col++) {
                TextField field = buildSidebarCompactNumberField("m" + row + col);
                sidebarColorMatrixFields[row * 5 + col] = field;
                grid.add(field, col + 1, row);
            }
        }
        Button btnFillIdentity = buildSidebarActionButton("Fill Identity", this::fillSidebarColorMatrixIdentity);
        Button btnKeyColors = buildSidebarActionButton("Key At Playhead", this::applySidebarColorMatrixKeyframes);
        body.getChildren().addAll(lblSidebarAdvancedHint, grid, buildSidebarButtonRow(btnFillIdentity, btnKeyColors));
        return body;
    }

    private Node buildSidebarCameraDofInspector() {
        String[] captions = {"Focus", "Strength", "Max Blur"};
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        for (int i = 0; i < captions.length; i++) {
            Label label = new Label(captions[i]);
            label.setStyle(STYLE_SIDEBAR_META_LABEL);
            TextField field = buildSidebarNumberField(captions[i].toLowerCase(Locale.ROOT));
            sidebarCameraDofFields[i] = field;
            grid.add(label, 0, i);
            grid.add(field, 1, i);
        }
        Button btnReset = buildSidebarActionButton("Fill Neutral", this::fillSidebarDofNeutral);
        Button btnKey = buildSidebarActionButton("Key At Playhead", this::applySidebarDofKeyframes);
        return new VBox(8, grid, buildSidebarButtonRow(btnReset, btnKey));
    }

    private Node buildSidebarCustomChannelInspector() {
        cbSidebarCustomPropertyKey = new ComboBox<>();
        cbSidebarCustomPropertyKey.setEditable(true);
        cbSidebarCustomPropertyKey.setMaxWidth(Double.MAX_VALUE);
        cbSidebarCustomPropertyKey.setStyle(STYLE_TEXT_FIELD);
        cbSidebarCustomPropertyKey.setPromptText("property.key");
        cbSidebarCustomPropertyKey.setTooltip(new Tooltip("Registered or freeform numeric channel key"));
        cbSidebarCustomPropertyKey.getEditor().setStyle(STYLE_TEXT_FIELD);
        cbSidebarCustomPropertyKey.setOnAction(event -> refreshSidebarCustomPropertyValue());
        cbSidebarCustomPropertyKey.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshSidebarCustomPropertyValue());

        tfSidebarCustomPropertyValue = buildSidebarNumberField("value");

        Label lblKey = new Label("Key");
        lblKey.setStyle(STYLE_SIDEBAR_META_LABEL);
        Label lblValue = new Label("Value");
        lblValue.setStyle(STYLE_SIDEBAR_META_LABEL);
        Button btnKey = buildSidebarActionButton("Key At Playhead", this::applySidebarCustomPropertyKeyframe);
        Button btnRemove = buildSidebarActionButton("Remove Key", this::removeSidebarCustomPropertyKeyframe);
        VBox body = new VBox(6,
            lblKey,
            cbSidebarCustomPropertyKey,
            lblValue,
            tfSidebarCustomPropertyValue,
            createSidebarHintLabel("Uses the engine registry for known numeric channels, but also accepts freeform keys."),
            buildSidebarButtonRow(btnKey, btnRemove));
        body.setFillWidth(true);
        return body;
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
        scrollPane.setPannable(false);
        scrollPane.setMinWidth(0);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scrollPane;
    }

    private TextField buildSidebarNumberField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle(STYLE_TEXT_FIELD);
        field.setPrefWidth(90);
        return field;
    }

    private TextField buildSidebarCompactNumberField(String prompt) {
        TextField field = buildSidebarNumberField(prompt);
        field.setPrefWidth(48);
        field.setMaxWidth(54);
        return field;
    }

    private Label createSidebarHintLabel(String text) {
        Label label = new Label(text);
        label.setStyle(STYLE_SIDEBAR_META_LABEL + " -fx-font-weight: normal;");
        label.setWrapText(true);
        return label;
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
        boolean runtimeCamera = timelinePanel != null && timelinePanel.isRuntimeCameraSelected();
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
                    : timelinePanel != null && timelinePanel.isRuntimeCameraSelected()
                        ? "Runtime camera track"
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
        if (lblSidebarSceneStage != null) {
            lblSidebarSceneStage.setText(describeStageContext(project.getStageContext()));
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
        refreshSidebarAdvancedPanels(selectedName, selectedGroup, runtimeCamera, hasTarget);
        refreshToolbarCommandSummary();
    }

    private void refreshSidebarAdvancedPanels(
        String selectedName,
        boolean selectedGroup,
        boolean runtimeCamera,
        boolean hasTarget
    ) {
        boolean entityTrack = hasTarget && !selectedGroup && !runtimeCamera;
        boolean cameraTrack = hasTarget && runtimeCamera;

        setSidebarCardVisible(sidebarAdvancedUnavailableCard, !entityTrack && !cameraTrack);
        setSidebarCardVisible(sidebarMatrixCard, entityTrack);
        setSidebarCardVisible(sidebarColorMatrixCard, entityTrack);
        setSidebarCardVisible(sidebarCameraDofCard, cameraTrack);
        setSidebarCardVisible(sidebarCustomChannelsCard, entityTrack || cameraTrack);

        if (sidebarAdvancedUnavailableCard != null && !entityTrack && !cameraTrack && sidebarAdvancedUnavailableCard.getChildren().size() > 1) {
            Node messageNode = sidebarAdvancedUnavailableCard.getChildren().get(1);
            if (messageNode instanceof Label label) {
                label.setText(
                    selectedGroup
                        ? "Advanced authoring is available on entity tracks and the runtime camera track, not on group selections."
                        : "Select an entity or the runtime camera to author matrix, color, DOF, and custom numeric channels."
                );
            }
        }

        if (entityTrack) {
            refreshSidebarEntityAdvancedFields(selectedName);
        } else if (cameraTrack) {
            refreshSidebarCameraAdvancedFields(selectedName);
        } else {
            refreshSidebarCustomPropertyOptions(false, false, null);
        }
    }

    private void refreshSidebarEntityAdvancedFields(String entityName) {
        EntityTrack track = entityName != null ? project.getTrack(entityName) : null;
        com.jvn.core.scene2d.Entity2D entity = scene != null && entityName != null ? scene.find(entityName) : null;
        double timeMs = project.getPlayheadMs();

        for (int i = 0; i < ENTITY_MATRIX_PROPERTY_CHOICES.size() && i < sidebarMatrixFields.length; i++) {
            PropertyType property = ENTITY_MATRIX_PROPERTY_CHOICES.get(i);
            double fallback = fallbackPropertyValue(entity, property);
            double value = track != null && track.hasKeyframes(property)
                ? track.getValueAt(property, timeMs)
                : fallback;
            setSidebarFieldValue(sidebarMatrixFields[i], value);
        }

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col;
                String propertyKey = "color.m" + row + col;
                double fallback = entity != null
                    ? entity.readCustomProperty(propertyKey)
                    : defaultCustomPropertyValue(false, propertyKey);
                double value = track != null && track.hasCustomKeyframes(propertyKey)
                    ? track.getCustomValueAt(propertyKey, timeMs, fallback)
                    : fallback;
                setSidebarFieldValue(sidebarColorMatrixFields[index], value);
            }
        }

        refreshSidebarCustomPropertyOptions(true, false, track);
    }

    private void refreshSidebarCameraAdvancedFields(String targetName) {
        EntityTrack track = resolveRuntimeCameraTrack(false);
        var camera = animationPreview.getCamera();
        double timeMs = project.getPlayheadMs();
        PropertyType[] properties = {
            PropertyType.CAMERA_DOF_FOCUS,
            PropertyType.CAMERA_DOF_STRENGTH,
            PropertyType.CAMERA_DOF_MAX_BLUR
        };
        double[] fallbackValues = {
            camera != null ? camera.getFocusDepth() : PropertyType.CAMERA_DOF_FOCUS.getDefaultValue(),
            camera != null ? camera.getDepthOfFieldStrength() : PropertyType.CAMERA_DOF_STRENGTH.getDefaultValue(),
            camera != null ? camera.getDepthOfFieldMaxBlur() : PropertyType.CAMERA_DOF_MAX_BLUR.getDefaultValue()
        };
        for (int i = 0; i < properties.length && i < sidebarCameraDofFields.length; i++) {
            double value = track != null && track.hasKeyframes(properties[i])
                ? track.getValueAt(properties[i], timeMs)
                : fallbackValues[i];
            setSidebarFieldValue(sidebarCameraDofFields[i], value);
        }

        refreshSidebarCustomPropertyOptions(false, TimelinePanel.isRuntimeCameraTarget(targetName), track);
    }

    private void refreshSidebarCustomPropertyOptions(boolean entityTrack, boolean cameraTrack, EntityTrack track) {
        if (cbSidebarCustomPropertyKey == null) return;
        Set<String> options = new LinkedHashSet<>();
        if (entityTrack) {
            for (var definition : com.jvn.core.scene2d.Entity2D.animatableProperties()) {
                if (definition == null) continue;
                String key = definition.getKey();
                if (key != null && !ENTITY_DEDICATED_CUSTOM_KEYS.contains(key)) {
                    options.add(key);
                }
            }
        } else if (cameraTrack) {
            for (var definition : com.jvn.core.graphics.Camera2D.animatableProperties()) {
                if (definition == null) continue;
                String key = definition.getKey();
                if (key != null && !CAMERA_DEDICATED_CUSTOM_KEYS.contains(key)) {
                    options.add(key);
                }
            }
        }
        if (track != null) {
            for (String key : track.getAnimatedCustomProperties()) {
                if (key == null || key.isBlank()) continue;
                if (entityTrack && ENTITY_DEDICATED_CUSTOM_KEYS.contains(key)) continue;
                if (cameraTrack && CAMERA_DEDICATED_CUSTOM_KEYS.contains(key)) continue;
                options.add(key);
            }
        }

        String currentKey = readSidebarCustomPropertyKey();
        cbSidebarCustomPropertyKey.getItems().setAll(options);
        if (!cameraTrack && !entityTrack) {
            if (!cbSidebarCustomPropertyKey.isFocused() && !cbSidebarCustomPropertyKey.getEditor().isFocused()) {
                cbSidebarCustomPropertyKey.setValue(null);
                cbSidebarCustomPropertyKey.getEditor().clear();
            }
            clearSidebarFieldValue(tfSidebarCustomPropertyValue);
            return;
        }

        if ((currentKey == null || currentKey.isBlank()) && !options.isEmpty()) {
            currentKey = cbSidebarCustomPropertyKey.getItems().get(0);
        }
        if (currentKey != null && !currentKey.isBlank()
            && !cbSidebarCustomPropertyKey.isFocused()
            && !cbSidebarCustomPropertyKey.getEditor().isFocused()) {
            cbSidebarCustomPropertyKey.setValue(currentKey);
            cbSidebarCustomPropertyKey.getEditor().setText(currentKey);
        }
        refreshSidebarCustomPropertyValue();
    }

    private void refreshSidebarCustomPropertyValue() {
        if (tfSidebarCustomPropertyValue == null) return;
        String propertyKey = readSidebarCustomPropertyKey();
        if (propertyKey == null || propertyKey.isBlank()) {
            clearSidebarFieldValue(tfSidebarCustomPropertyValue);
            return;
        }

        EntityTrack track = selectedTrackForEditing(false);
        double timeMs = project.getPlayheadMs();
        String targetName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        boolean runtimeCamera = timelinePanel != null && timelinePanel.isRuntimeCameraSelected();
        double value;

        PropertyType mapped = PropertyType.fromTimelineCustomKey(propertyKey);
        if (mapped != null && track != null) {
            double fallback = runtimeCamera
                ? defaultCustomPropertyValue(true, propertyKey)
                : defaultCustomPropertyValue(false, propertyKey);
            value = track.hasKeyframes(mapped) ? track.getValueAt(mapped, timeMs) : fallback;
        } else if (runtimeCamera) {
            var camera = animationPreview.getCamera();
            double fallback = baselineCustomPropertyValue(targetName, propertyKey, camera);
            value = track != null && track.hasCustomKeyframes(propertyKey)
                ? track.getCustomValueAt(propertyKey, timeMs, fallback)
                : fallback;
        } else {
            com.jvn.core.scene2d.Entity2D entity = scene != null && targetName != null ? scene.find(targetName) : null;
            double fallback = baselineCustomPropertyValue(targetName, propertyKey, entity);
            value = track != null && track.hasCustomKeyframes(propertyKey)
                ? track.getCustomValueAt(propertyKey, timeMs, fallback)
                : fallback;
        }
        setSidebarFieldValue(tfSidebarCustomPropertyValue, value);
    }

    private void fillSidebarMatrixIdentity() {
        double[] values = {1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0};
        for (int i = 0; i < values.length && i < sidebarMatrixFields.length; i++) {
            forceSidebarFieldValue(sidebarMatrixFields[i], values[i]);
        }
    }

    private void applySidebarMatrixKeyframes() {
        String entityName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        if (entityName == null || entityName.isBlank() || timelinePanel == null || timelinePanel.isSelectedGroup()
            || timelinePanel.isRuntimeCameraSelected()) {
            return;
        }
        EntityTrack track = project.getOrCreateTrack(entityName);
        com.jvn.core.scene2d.Entity2D entity = scene != null ? scene.find(entityName) : null;
        double timeMs = project.getPlayheadMs();
        List<PuppeteerCommand> commands = new ArrayList<>();
        for (int i = 0; i < ENTITY_MATRIX_PROPERTY_CHOICES.size() && i < sidebarMatrixFields.length; i++) {
            PropertyType property = ENTITY_MATRIX_PROPERTY_CHOICES.get(i);
            double fallback = fallbackPropertyValue(entity, property);
            double value = parseSidebarFieldValue(sidebarMatrixFields[i], fallback);
            commands.add(PuppeteerCommand.upsertKeyframe(track, property, timeMs, value));
        }
        executeSidebarCommand("Key matrix properties", commands);
    }

    private void fillSidebarColorMatrixIdentity() {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col;
                double value = col == row ? 1.0 : 0.0;
                if (col == 4) value = 0.0;
                forceSidebarFieldValue(sidebarColorMatrixFields[index], value);
            }
        }
    }

    private void applySidebarColorMatrixKeyframes() {
        String entityName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        if (entityName == null || entityName.isBlank() || timelinePanel == null || timelinePanel.isSelectedGroup()
            || timelinePanel.isRuntimeCameraSelected()) {
            return;
        }
        EntityTrack track = project.getOrCreateTrack(entityName);
        com.jvn.core.scene2d.Entity2D entity = scene != null ? scene.find(entityName) : null;
        double timeMs = project.getPlayheadMs();
        List<PuppeteerCommand> commands = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col;
                String propertyKey = "color.m" + row + col;
                double fallback = entity != null
                    ? entity.readCustomProperty(propertyKey)
                    : defaultCustomPropertyValue(false, propertyKey);
                double value = parseSidebarFieldValue(sidebarColorMatrixFields[index], fallback);
                commands.add(PuppeteerCommand.upsertCustomKeyframe(track, propertyKey, timeMs, value));
            }
        }
        executeSidebarCommand("Key color matrix", commands);
    }

    private void fillSidebarDofNeutral() {
        for (TextField field : sidebarCameraDofFields) {
            forceSidebarFieldValue(field, 0.0);
        }
    }

    private void applySidebarDofKeyframes() {
        if (timelinePanel == null || !timelinePanel.isRuntimeCameraSelected()) return;
        EntityTrack track = resolveRuntimeCameraTrack(true);
        double timeMs = project.getPlayheadMs();
        PropertyType[] properties = {
            PropertyType.CAMERA_DOF_FOCUS,
            PropertyType.CAMERA_DOF_STRENGTH,
            PropertyType.CAMERA_DOF_MAX_BLUR
        };
        double[] fallbackValues = {
            animationPreview.getCamera().getFocusDepth(),
            animationPreview.getCamera().getDepthOfFieldStrength(),
            animationPreview.getCamera().getDepthOfFieldMaxBlur()
        };
        List<PuppeteerCommand> commands = new ArrayList<>();
        for (int i = 0; i < properties.length && i < sidebarCameraDofFields.length; i++) {
            double value = parseSidebarFieldValue(sidebarCameraDofFields[i], fallbackValues[i]);
            commands.add(PuppeteerCommand.upsertKeyframe(track, properties[i], timeMs, value));
        }
        executeSidebarCommand("Key DOF properties", commands);
    }

    private void applySidebarCustomPropertyKeyframe() {
        EntityTrack track = selectedTrackForEditing(true);
        if (track == null) return;
        String propertyKey = readSidebarCustomPropertyKey();
        if (propertyKey == null || propertyKey.isBlank()) return;

        double timeMs = project.getPlayheadMs();
        boolean runtimeCamera = timelinePanel != null && timelinePanel.isRuntimeCameraSelected();
        double fallback = resolveSidebarCustomFallback(propertyKey, runtimeCamera);
        double value = parseSidebarFieldValue(tfSidebarCustomPropertyValue, fallback);
        PropertyType mapped = PropertyType.fromTimelineCustomKey(propertyKey);
        PuppeteerCommand command = mapped != null
            ? PuppeteerCommand.upsertKeyframe(track, mapped, timeMs, value)
            : PuppeteerCommand.upsertCustomKeyframe(track, propertyKey, timeMs, value);
        executeSidebarCommand(command);
    }

    private void removeSidebarCustomPropertyKeyframe() {
        EntityTrack track = selectedTrackForEditing(false);
        if (track == null) return;
        String propertyKey = readSidebarCustomPropertyKey();
        if (propertyKey == null || propertyKey.isBlank()) return;

        double timeMs = project.getPlayheadMs();
        PropertyType mapped = PropertyType.fromTimelineCustomKey(propertyKey);
        if (mapped != null) {
            Keyframe existing = track.findKeyframeAt(mapped, timeMs);
            if (existing == null) return;
            executeSidebarCommand(PuppeteerCommand.removeKeyframe(track, mapped, existing));
            return;
        }
        executeSidebarCommand(PuppeteerCommand.removeCustomKeyframeAt(track, propertyKey, timeMs));
    }

    private void executeSidebarCommand(String description, List<PuppeteerCommand> commands) {
        if (commands == null || commands.isEmpty()) return;
        executeSidebarCommand(PuppeteerCommand.composite(description, commands));
    }

    private void executeSidebarCommand(PuppeteerCommand command) {
        if (command == null) return;
        commandStack.execute(command);
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    private String readSidebarCustomPropertyKey() {
        if (cbSidebarCustomPropertyKey == null) return null;
        String key = cbSidebarCustomPropertyKey.getEditor() != null
            ? cbSidebarCustomPropertyKey.getEditor().getText()
            : null;
        if ((key == null || key.isBlank()) && cbSidebarCustomPropertyKey.getValue() != null) {
            key = cbSidebarCustomPropertyKey.getValue();
        }
        return key != null ? key.trim() : null;
    }

    private double resolveSidebarCustomFallback(String propertyKey, boolean runtimeCamera) {
        String targetName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        if (runtimeCamera) {
            return baselineCustomPropertyValue(targetName, propertyKey, animationPreview.getCamera());
        }
        com.jvn.core.scene2d.Entity2D entity = scene != null && targetName != null ? scene.find(targetName) : null;
        return baselineCustomPropertyValue(targetName, propertyKey, entity);
    }

    private double defaultCustomPropertyValue(boolean camera, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return 0.0;
        if (camera) {
            var definition = com.jvn.core.graphics.Camera2D.getAnimatableProperty(propertyKey);
            return definition != null ? definition.getDefaultValue() : 0.0;
        }
        var definition = com.jvn.core.scene2d.Entity2D.getAnimatableProperty(propertyKey);
        return definition != null ? definition.getDefaultValue() : 0.0;
    }

    private double parseSidebarFieldValue(TextField field, double fallback) {
        if (field == null) return fallback;
        String text = field.getText();
        if (text == null || text.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void setSidebarFieldValue(TextField field, double value) {
        if (field == null || field.isFocused()) return;
        field.setText(formatSidebarNumber(value));
    }

    private void forceSidebarFieldValue(TextField field, double value) {
        if (field == null) return;
        field.setText(formatSidebarNumber(value));
    }

    private void clearSidebarFieldValue(TextField field) {
        if (field == null || field.isFocused()) return;
        field.clear();
    }

    private String formatSidebarNumber(double value) {
        if (!Double.isFinite(value)) return "0";
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.contains(".") && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return "-0".equals(text) ? "0" : text;
    }

    private void setSidebarCardVisible(VBox card, boolean visible) {
        if (card == null) return;
        card.setVisible(visible);
        card.setManaged(visible);
    }

    private int countItems(Iterable<?> items) {
        if (items == null) return 0;
        int count = 0;
        for (Object ignored : items) {
            count++;
        }
        return count;
    }

    private String describeStageContext(AnimationProject.StageContext stage) {
        if (stage == null || !stage.isPresent()) return "None";
        StringBuilder sb = new StringBuilder(stage.presetId());
        List<String> counts = new ArrayList<>();
        if (stage.lightCount() > 0) counts.add(stage.lightCount() + " lights");
        if (stage.occluderCount() > 0) counts.add(stage.occluderCount() + " occluders");
        if (stage.responseZoneCount() > 0) counts.add(stage.responseZoneCount() + " zones");
        if (!counts.isEmpty()) {
            sb.append(" (").append(String.join(", ", counts)).append(")");
        }
        if (!stage.sourcePath().isBlank()) {
            sb.append("\n").append(stage.sourcePath());
        }
        return sb.toString();
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

    private StackPane createPuppeteerFrostShell(Node content, String sideClass) {
        if (content == null) return new StackPane();
        if (content instanceof Region region) {
            region.setMinWidth(0);
            region.setMinHeight(0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        Region frost = new Region();
        frost.getStyleClass().add("puppeteer-sidebar-frost");
        if (sideClass != null && !sideClass.isBlank()) {
            frost.getStyleClass().add(sideClass);
        }
        frost.setMouseTransparent(true);
        frost.setOpacity(0.0);
        frost.setVisible(false);
        frost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        StackPane shell = new StackPane(content, frost);
        shell.getStyleClass().add("puppeteer-frost-shell");
        shell.setMinWidth(0);
        shell.setMinHeight(0);
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        content.getProperties().put(PUPPETEER_FROST_OVERLAY_KEY, frost);
        return shell;
    }

    private void installPuppeteerFrostHandlers() {
        installPuppeteerFrostHandlers(topWorkspaceSplit, puppeteerLeftFrostTarget, 0);
        installPuppeteerFrostHandlers(bottomWorkspaceSplit, puppeteerKeyframeFrostTarget, 0);
        installPuppeteerFrostHandlers(mainWorkspaceSplit, puppeteerCodeFrostTarget, 0);
        installPuppeteerFrostSceneReleaseHandler();
    }

    private void installPuppeteerFrostHandlers(SplitPane split, Node frostTarget, int attempt) {
        if (split == null || frostTarget == null) return;
        Platform.runLater(() -> {
            List<StackPane> dividerNodes = split.lookupAll(".split-pane-divider").stream()
                .filter(StackPane.class::isInstance)
                .map(StackPane.class::cast)
                .toList();
            if (dividerNodes.isEmpty()) {
                if (attempt < 8) {
                    PauseTransition retry = new PauseTransition(Duration.millis(80));
                    retry.setOnFinished(e -> installPuppeteerFrostHandlers(split, frostTarget, attempt + 1));
                    retry.play();
                }
                return;
            }
            for (StackPane divider : dividerNodes) {
                installPuppeteerFrostHandler(divider, frostTarget);
            }
        });
    }

    private void installPuppeteerFrostHandler(StackPane divider, Node frostTarget) {
        if (divider == null || frostTarget == null) return;
        if (Boolean.TRUE.equals(divider.getProperties().get(PUPPETEER_FROST_HANDLER_KEY))) return;
        divider.getProperties().put(PUPPETEER_FROST_HANDLER_KEY, true);

        divider.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                beginPuppeteerFrost(frostTarget);
            }
        });
        divider.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (event.isPrimaryButtonDown()) {
                beginPuppeteerFrost(frostTarget);
            }
        });
        divider.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> endPuppeteerFrost());
    }

    private void installPuppeteerFrostSceneReleaseHandler() {
        if (puppeteerFrostSceneReleaseInstalled || getScene() == null) return;
        puppeteerFrostSceneReleaseInstalled = true;
        Scene fxScene = getScene();
        fxScene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> endPuppeteerFrost());
        fxScene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            if (!event.isPrimaryButtonDown()) {
                endPuppeteerFrost();
            }
        });
        if (fxScene.getWindow() != null) {
            fxScene.getWindow().focusedProperty().addListener((o, ov, nv) -> {
                if (!nv) endPuppeteerFrost();
            });
        }
    }

    private void beginPuppeteerFrost(Node target) {
        if (target == null) return;
        if (activePuppeteerFrostNode == target) return;
        if (activePuppeteerFrostNode != null) {
            animatePuppeteerFrost(activePuppeteerFrostNode, false);
        }
        activePuppeteerFrostNode = target;
        animatePuppeteerFrost(target, true);
    }

    private void endPuppeteerFrost() {
        Node target = activePuppeteerFrostNode;
        if (target == null) return;
        activePuppeteerFrostNode = null;
        animatePuppeteerFrost(target, false);
    }

    private void animatePuppeteerFrost(Node target, boolean active) {
        if (target == null) return;
        Object overlayValue = target.getProperties().get(PUPPETEER_FROST_OVERLAY_KEY);
        if (!(overlayValue instanceof Region overlay)) return;

        Object existingAnimation = target.getProperties().get(PUPPETEER_FROST_ANIMATION_KEY);
        if (existingAnimation instanceof Timeline timeline) {
            timeline.stop();
        }

        GaussianBlur blur = puppeteerFrostBlurFor(target);
        if (active) {
            if (!Boolean.TRUE.equals(target.getProperties().get(PUPPETEER_FROST_BASE_EFFECT_PRESENT_KEY))) {
                Effect baseEffect = target.getEffect();
                if (baseEffect != null && baseEffect != blur) {
                    target.getProperties().put(PUPPETEER_FROST_BASE_EFFECT_KEY, baseEffect);
                } else {
                    target.getProperties().remove(PUPPETEER_FROST_BASE_EFFECT_KEY);
                }
                target.getProperties().put(PUPPETEER_FROST_BASE_EFFECT_PRESENT_KEY, true);
            }
            Object baseEffectValue = target.getProperties().get(PUPPETEER_FROST_BASE_EFFECT_KEY);
            blur.setInput(baseEffectValue instanceof Effect effect ? effect : null);
            target.setEffect(blur);
            overlay.setVisible(true);
        }

        Timeline animation = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(blur.radiusProperty(), blur.getRadius(), Interpolator.EASE_BOTH),
                new KeyValue(overlay.opacityProperty(), overlay.getOpacity(), Interpolator.EASE_BOTH)),
            new KeyFrame(PUPPETEER_FROST_DURATION,
                new KeyValue(blur.radiusProperty(), active ? PUPPETEER_FROST_BLUR_RADIUS : 0.0, Interpolator.EASE_BOTH),
                new KeyValue(overlay.opacityProperty(), active ? PUPPETEER_FROST_OPACITY : 0.0, Interpolator.EASE_BOTH)));
        animation.setOnFinished(event -> {
            target.getProperties().remove(PUPPETEER_FROST_ANIMATION_KEY);
            if (!active) {
                overlay.setOpacity(0.0);
                overlay.setVisible(false);
                blur.setRadius(0.0);
                Object baseEffectValue = target.getProperties().get(PUPPETEER_FROST_BASE_EFFECT_KEY);
                target.setEffect(baseEffectValue instanceof Effect effect ? effect : null);
                blur.setInput(null);
                target.getProperties().remove(PUPPETEER_FROST_BASE_EFFECT_KEY);
                target.getProperties().remove(PUPPETEER_FROST_BASE_EFFECT_PRESENT_KEY);
            }
        });
        target.getProperties().put(PUPPETEER_FROST_ANIMATION_KEY, animation);
        animation.play();
    }

    private GaussianBlur puppeteerFrostBlurFor(Node target) {
        Object existing = target.getProperties().get(PUPPETEER_FROST_BLUR_KEY);
        if (existing instanceof GaussianBlur blur) {
            return blur;
        }
        GaussianBlur blur = new GaussianBlur(0.0);
        target.getProperties().put(PUPPETEER_FROST_BLUR_KEY, blur);
        return blur;
    }

    public void setCodePaneVisible(boolean visible) {
        codePaneVisible = visible;
        if (codePreview != null) {
            codePreview.setManaged(visible);
            codePreview.setVisible(visible);
        }
        Node codePaneNode = codePreviewSplitNode();
        if (codePreviewFrostShell != null) {
            codePreviewFrostShell.setManaged(visible);
            codePreviewFrostShell.setVisible(visible);
        }
        if (mainWorkspaceSplit == null) {
            return;
        }
        if (visible) {
            if (!mainWorkspaceSplit.getItems().contains(codePaneNode)) {
                mainWorkspaceSplit.getItems().add(codePaneNode);
                SplitPane.setResizableWithParent(codePaneNode, Boolean.TRUE);
                installPuppeteerFrostHandlers(mainWorkspaceSplit, puppeteerCodeFrostTarget, 0);
            }
            mainWorkspaceSplit.setDividerPositions(codePaneDividerPosition);
            refreshSidebarTabs();
            return;
        }
        if (mainWorkspaceSplit.getItems().contains(codePaneNode)) {
            double[] positions = mainWorkspaceSplit.getDividerPositions();
            if (positions.length >= 1) {
                codePaneDividerPosition = positions[0];
            }
            mainWorkspaceSplit.getItems().remove(codePaneNode);
        }
        refreshSidebarTabs();
        refreshToolbarCommandSummary();
    }

    private Node codePreviewSplitNode() {
        return codePreviewFrostShell != null ? codePreviewFrostShell : codePreview;
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
            captureSceneStateBaseline();
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

    public void setLaunchSceneSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
        launchCharacterImagePaths.clear();
        launchBackgroundPaths.clear();
        if (snapshot != null) {
            if (snapshot.characterImagePaths != null) {
                launchCharacterImagePaths.putAll(snapshot.characterImagePaths);
            }
            if (snapshot.backgroundPaths != null) {
                launchBackgroundPaths.putAll(snapshot.backgroundPaths);
            }
            applyLaunchStageContext(snapshot);
        }
    }

    private void applyLaunchStageContext(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
        if (snapshot == null || snapshot.activeStagePresetId == null || snapshot.activeStagePresetId.isBlank()) return;
        String sourcePath = snapshot.resolveStagePresetPath(projectRoot);
        AnimationProject.StageContext context = buildStageContext(
            snapshot.activeStagePresetId,
            sourcePath,
            snapshot.backgroundId,
            snapshot.characters.isEmpty() ? "" : snapshot.characters.get(0).characterId);
        project.setStageContext(context);
        refreshExportPreview();
        refreshSidebarTabs();
    }

    private AnimationProject.StageContext buildStageContext(
        String presetId,
        String sourcePath,
        String fallbackBackgroundTag,
        String fallbackSubjectTag
    ) {
        if (presetId == null || presetId.isBlank()) return null;
        String metadataPath = relativizePreviewAssetPath(sourcePath);
        if (sourcePath != null && !sourcePath.isBlank()) {
            try {
                Path path = Path.of(sourcePath).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) {
                    try (var input = Files.newInputStream(path)) {
                        VnStagePreset preset = VnStagePresetLoader.load(presetId, sourcePath, input);
                        return new AnimationProject.StageContext(
                            presetId,
                            metadataPath,
                            firstNonBlank(preset.getBackgroundTag(), fallbackBackgroundTag),
                            firstNonBlank(preset.getSubjectTag(), fallbackSubjectTag),
                            preset.getLights().size(),
                            preset.getOccluders().size(),
                            preset.getResponseZones().size()
                        );
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new AnimationProject.StageContext(
            presetId,
            metadataPath,
            fallbackBackgroundTag,
            fallbackSubjectTag,
            0,
            0,
            0
        );
    }

    private java.io.File projectRoot;
    private java.io.File scriptTargetFile;

    public void setProjectRoot(java.io.File root) {
        this.projectRoot = root;
        animationPreview.setProjectRoot(root);
        if (assetImporterPanel != null) {
            assetImporterPanel.setProjectRoot(root);
        }
        keyframeEditor.setProjectRoot(root);
        codePreview.setProjectRoot(root);
        updateViewportInfoLabel();
        refreshSidebarTabs();
    }

    public void setSourceScriptFile(java.io.File file) {
        this.scriptTargetFile = file;
        if (assetImporterPanel != null) {
            assetImporterPanel.setScriptTargetFile(file);
        }
    }

    private void showAssetImporterWindow() {
        if (assetImporterWindow != null) {
            assetImporterWindow.show();
            assetImporterWindow.toFront();
            assetImporterWindow.requestFocus();
            return;
        }

        assetImporterPanel = new AssetPickerPanel();
        assetImporterPanel.setImportEnabled(true);
        assetImporterPanel.setPlacementActionsVisible(true);
        assetImporterPanel.setOnAddToScene(this::addImporterEntryToScene);
        assetImporterPanel.setProjectRoot(projectRoot);
        assetImporterPanel.setScriptTargetFile(scriptTargetFile);
        assetImporterPanel.setMinWidth(0);

        BorderPane root = new BorderPane(assetImporterPanel);
        root.setStyle("-fx-background-color: #121212;");

        Scene importerScene = new Scene(root, 920, 720);
        EditorTheme.apply(importerScene);

        Stage window = new Stage();
        window.initOwner(this);
        window.setTitle("Puppeteer Asset Importer");
        window.setScene(importerScene);
        window.setOnHidden(event -> {
            assetImporterWindow = null;
            assetImporterPanel = null;
        });

        assetImporterWindow = window;
        window.show();
    }

    private void addImporterEntryToScene(AssetPickerPanel.AssetEntry entry, PuppeteerAssetPlacementRole role) {
        if (entry == null) return;
        if (entry.isPresetEntry()) {
            addPresetToScene(entry, role);
        } else {
            addAssetToScene(entry.relativePath, entry.baseName, role);
        }
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

    private void addPresetToScene(AssetPickerPanel.AssetEntry presetEntry, PuppeteerAssetPlacementRole role) {
        if (presetEntry == null || !presetEntry.isPresetEntry() || presetEntry.presetLayers.isEmpty()) {
            return;
        }
        if (scene == null) {
            setScene(new JesScene2D());
        }
        if (scene == null) return;

        PuppeteerAssetPlacementRole resolvedRole = role != null ? role : PuppeteerAssetPlacementRole.CHARACTER;
        String groupBase = buildPresetGroupBaseName(presetEntry);
        String groupName = resolveUniqueGroupName(groupBase);

        double[] compositeSize = resolvePresetNaturalSize(presetEntry);
        PuppeteerAssetPlacement.Placement placement = PuppeteerAssetPlacement.plan(
            resolvedRole,
            animationPreview.getViewportDimensions(),
            compositeSize[0],
            compositeSize[1]
        );

        double naturalWidth = compositeSize[0] > 1.0 ? compositeSize[0] : placement.width();
        double naturalHeight = compositeSize[1] > 1.0 ? compositeSize[1] : placement.height();
        double scaleX = naturalWidth > 0.0 ? placement.width() / naturalWidth : 1.0;
        double scaleY = naturalHeight > 0.0 ? placement.height() / naturalHeight : 1.0;
        double scale = Double.isFinite(scaleX) && Double.isFinite(scaleY) ? Math.min(scaleX, scaleY) : 1.0;
        if (!Double.isFinite(scale) || scale <= 0.0) {
            scale = 1.0;
        }

        EntityGroup group = project.getOrCreateGroup(groupName);
        group.setLayerOrder((int) Math.round(placement.z()));

        int layerIndex = 0;
        for (AssetPickerPanel.AssetEntry.PresetLayer layer : presetEntry.presetLayers) {
            if (layer == null || layer.relativePath() == null || layer.relativePath().isBlank()) {
                continue;
            }
            String layerBase = buildPresetLayerEntityBaseName(groupBase, layer);
            String entityName = resolveUniqueEntityName(layerBase, resolvedRole);
            double[] layerSize = resolveAssetNaturalSize(layer.relativePath());
            double layerWidth = layerSize[0] > 1.0 ? layerSize[0] * scale : placement.width();
            double layerHeight = layerSize[1] > 1.0 ? layerSize[1] * scale : placement.height();

            com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(layer.relativePath(), layerWidth, layerHeight);
            sprite.setOrigin(placement.originX(), placement.originY());
            sprite.setPosition(placement.x(), placement.y());
            sprite.setZ(placement.z() + layerIndex);

            scene.add(sprite);
            scene.registerEntity(entityName, sprite);

            EntityTrack track = project.getOrCreateTrack(entityName);
            track.setLayerOrder(layerIndex);
            project.addEntityToGroup(entityName, groupName);
            layerIndex++;
        }

        if (layerIndex == 0) {
            project.removeGroup(groupName);
            return;
        }

        captureProjectSnapshotBaseline();
        entitySelector.refresh(project);
        entitySelector.selectGroup(groupName);
        timelinePanel.refresh();
        timelinePanel.setSelectedTarget(groupName, true);
        animationPreview.clearSelection();
        updatePreview();
        refreshExportPreviewAndMarkDirty();

    }

    private void deleteSceneSelection(String name, boolean group) {
        if (name == null || name.isBlank()) return;

        String selectedTarget = timelinePanel.getSelectedEntity();
        boolean clearSelection = Objects.equals(selectedTarget, name) && timelinePanel.isSelectedGroup() == group;
        if (!group && Objects.equals(animationPreview.getSelectedEntityName(), name)) {
            clearSelection = true;
        }

        if (group) {
            project.removeGroup(name);
        } else {
            project.removeTrack(name);
            if (scene != null) {
                scene.removeEntity(name);
                animationPreview.setScene(scene);
                entitySelector.setScene(scene);
            }
        }

        project.pruneOrbitAnchors(collectProjectEntityNames());
        animationPreview.setOrbitAnchors(project.getOrbitAnchorsView());
        animationPreview.setOrbitAnchorSources(project.getOrbitAnchorSourcesView());
        animationPreview.setOrbitAnchorSourceOffsets(project.getOrbitAnchorSourceOffsetsView());

        if (clearSelection) {
            clearInspectorSelection();
        }

        captureSceneStateBaseline();
        captureProjectSnapshotBaseline();
        entitySelector.refresh(project);
        timelinePanel.refresh();
        refreshPropertyPickerChoices();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    private void clearInspectorSelection() {
        entitySelector.selectEntity(null);
        animationPreview.clearSelection();
        timelinePanel.setSelectedTarget(null, false);
        keyframeEditor.setSelectionContext(null, false, false);
        keyframeEditor.setKeyframe(null, null);
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

    private EntityTrack resolveAnimatedTrack(String name, boolean createIfMissing) {
        if (name == null || name.isBlank()) {
            return null;
        }
        EntityGroup group = project.getGroup(name);
        if (group != null) {
            return group.getGroupTrack();
        }
        return createIfMissing ? project.getOrCreateTrack(name) : project.getTrack(name);
    }

    private String resolveUniqueGroupName(String suggestedName) {
        String base = suggestedName == null ? "" : suggestedName.trim();
        if (base.isBlank()) {
            base = "CharacterGroup";
        }
        String groupName = base;
        int suffix = 2;
        while (project.getGroup(groupName) != null) {
            groupName = base + "_" + suffix++;
        }
        return groupName;
    }

    private String buildPresetGroupBaseName(AssetPickerPanel.AssetEntry presetEntry) {
        if (presetEntry == null) {
            return "CharacterPreset";
        }
        String character = presetEntry.presetCharacterId != null && !presetEntry.presetCharacterId.isBlank()
            ? presetEntry.presetCharacterId
            : "character";
        String preset = presetEntry.presetId != null && !presetEntry.presetId.isBlank()
            ? presetEntry.presetId
            : "preset";
        return character + "_" + preset;
    }

    private String buildPresetLayerEntityBaseName(String groupBase, AssetPickerPanel.AssetEntry.PresetLayer layer) {
        String suffix = layer != null && layer.layerId() != null && !layer.layerId().isBlank()
            ? layer.layerId()
            : (layer != null && layer.displayName() != null && !layer.displayName().isBlank() ? layer.displayName() : "layer");
        return (groupBase == null || groupBase.isBlank() ? "character" : groupBase) + "_" + suffix;
    }

    private double[] resolvePresetNaturalSize(AssetPickerPanel.AssetEntry presetEntry) {
        double width = -1.0;
        double height = -1.0;
        if (presetEntry == null || presetEntry.presetLayers.isEmpty()) {
            return new double[]{width, height};
        }
        for (AssetPickerPanel.AssetEntry.PresetLayer layer : presetEntry.presetLayers) {
            if (layer == null || layer.relativePath() == null || layer.relativePath().isBlank()) {
                continue;
            }
            double[] size = resolveAssetNaturalSize(layer.relativePath());
            if (size[0] > width) {
                width = size[0];
            }
            if (size[1] > height) {
                height = size[1];
            }
        }
        return new double[]{width, height};
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
        List<PropertyType> allowed;
        if (timelinePanel.isRuntimeCameraSelected()) {
            allowed = List.of(
                PropertyType.CAMERA_X,
                PropertyType.CAMERA_Y,
                PropertyType.CAMERA_ZOOM,
                PropertyType.CAMERA_DOF_FOCUS,
                PropertyType.CAMERA_DOF_STRENGTH,
                PropertyType.CAMERA_DOF_MAX_BLUR);
        } else if (timelinePanel.isSelectedGroup()) {
            allowed = GROUP_PROPERTY_CHOICES;
        } else {
            allowed = List.of(PropertyType.values());
        }
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
        restorePreviewBaselineState();
        if (runtimeParityPreview) {
            applyRuntimeParityPreview(time);
            animationPreview.render();
            refreshSidebarTabs();
            return;
        }
        var previewCamera = animationPreview.getCamera();
        double cameraX = previewCamera.getX();
        double cameraY = previewCamera.getY();
        double cameraZoom = previewCamera.getZoom();
        boolean hasCameraX = false;
        boolean hasCameraY = false;
        boolean hasCameraZoom = false;
        double dofFocus = previewCamera.getFocusDepth();
        double dofStrength = previewCamera.getDepthOfFieldStrength();
        double dofMaxBlur = previewCamera.getDepthOfFieldMaxBlur();
        boolean hasDofFocus = false;
        boolean hasDofStrength = false;
        boolean hasDofMaxBlur = false;
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
            if (track.hasKeyframes(PropertyType.CAMERA_DOF_FOCUS)) {
                dofFocus = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_DOF_FOCUS, time, dofFocus);
                hasDofFocus = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_DOF_STRENGTH)) {
                dofStrength = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_DOF_STRENGTH, time, dofStrength);
                hasDofStrength = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_DOF_MAX_BLUR)) {
                dofMaxBlur = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_DOF_MAX_BLUR, time, dofMaxBlur);
                hasDofMaxBlur = true;
            }
            for (String customPropertyKey : track.getAnimatedCustomProperties()) {
                if (TimelinePanel.isRuntimeCameraTarget(track.getEntityName())) {
                    double baselineValue = baselineCustomPropertyValue(track.getEntityName(), customPropertyKey, previewCamera);
                    double customValue = track.getCustomValueAt(customPropertyKey, time, baselineValue);
                    previewCamera.applyCustomProperty(customPropertyKey, customValue);
                }
            }
        }

        if (hasCameraX || hasCameraY || hasCameraZoom) {
            previewCamera.setPosition(cameraX, cameraY);
            previewCamera.setZoom(cameraZoom);
        }
        if (hasDofFocus) previewCamera.setFocusDepth(dofFocus);
        if (hasDofStrength) previewCamera.setDepthOfFieldStrength(dofStrength);
        if (hasDofMaxBlur) previewCamera.setDepthOfFieldMaxBlur(dofMaxBlur);
        keyframeEditor.setCameraState(previewCamera.getX(), previewCamera.getY(), previewCamera.getZoom());

        for (EntityTrack track : project.getTracks()) {
            var entity = scene.find(track.getEntityName());
            if (entity == null) continue;
            if (!track.isVisible()) {
                entity.setVisible(false);
                continue;
            }
            String entityName = track.getEntityName();
            double baseZ = baselinePropertyValue(entityName, entity, PropertyType.Z);
            double z = project.hasEffectiveAnimation(entityName, PropertyType.Z)
                ? project.computeValueAt(entityName, PropertyType.Z, time, baseZ)
                : project.computeEffectiveLayerOrder(entityName);
            entity.setZ(z);

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

            double baseVisibility = baselinePropertyValue(entityName, entity, PropertyType.VISIBILITY);
            double visibility = project.hasEffectiveAnimation(entityName, PropertyType.VISIBILITY)
                ? project.computeValueAt(entityName, PropertyType.VISIBILITY, time, baseVisibility)
                : baseVisibility;
            entity.setVisible(visibility >= 0.5);

            double matrixMxx = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_MXX)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_MXX, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MXX))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MXX);
            double matrixMxy = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_MXY)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_MXY, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MXY))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MXY);
            double matrixMyx = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_MYX)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_MYX, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MYX))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MYX);
            double matrixMyy = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_MYY)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_MYY, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MYY))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_MYY);
            double matrixTx = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_TX)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_TX, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_TX))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_TX);
            double matrixTy = project.hasEffectiveAnimation(entityName, PropertyType.MATRIX_TY)
                ? project.computeValueAt(entityName, PropertyType.MATRIX_TY, time, baselinePropertyValue(entityName, entity, PropertyType.MATRIX_TY))
                : baselinePropertyValue(entityName, entity, PropertyType.MATRIX_TY);
            entity.setSupplementalTransform(matrixMxx, matrixMxy, matrixMyx, matrixMyy, matrixTx, matrixTy);

            double baseBlur = baselinePropertyValue(entityName, entity, PropertyType.BLUR);
            double blur = project.hasEffectiveAnimation(entityName, PropertyType.BLUR)
                ? project.computeValueAt(entityName, PropertyType.BLUR, time, baseBlur)
                : baseBlur;
            entity.setBlurRadius(blur);

            for (String customPropertyKey : track.getAnimatedCustomProperties()) {
                double baselineValue = baselineCustomPropertyValue(entityName, customPropertyKey, entity);
                double customValue = track.getCustomValueAt(customPropertyKey, time, baselineValue);
                entity.applyCustomProperty(customPropertyKey, customValue);
            }
        }

        applyPreviewEventCuesUpTo(time);

        animationPreview.render();
        refreshSidebarTabs();
    }

    private void applyRuntimeParityPreview(double timeMs) {
        TimelineData data = project.toTimelineData(resolveTimelineNameForRuntimeData());
        TimelineRunner runner = new TimelineRunner(data, createPreviewSceneAccessor());
        runner.update(Math.max(0L, Math.round(timeMs)));
        var previewCamera = animationPreview.getCamera();
        keyframeEditor.setCameraState(previewCamera.getX(), previewCamera.getY(), previewCamera.getZoom());
    }

    private SceneAccessor createPreviewSceneAccessor() {
        return new SceneAccessor() {
            @Override
            public com.jvn.core.scene2d.Entity2D findEntity(String name) {
                return scene == null || name == null ? null : scene.find(name);
            }

            @Override
            public void setCameraX(double x) {
                var camera = animationPreview.getCamera();
                camera.setPosition(x, camera.getY());
            }

            @Override
            public void setCameraY(double y) {
                var camera = animationPreview.getCamera();
                camera.setPosition(camera.getX(), y);
            }

            @Override
            public void setCameraZoom(double zoom) {
                animationPreview.getCamera().setZoom(zoom);
            }

            @Override
            public void applyCustomProperty(String target, String propertyKey, double value) {
                if (TimelinePanel.isRuntimeCameraTarget(target)) {
                    animationPreview.getCamera().applyCustomProperty(propertyKey, value);
                    return;
                }
                SceneAccessor.super.applyCustomProperty(target, propertyKey, value);
            }

            @Override
            public void onEventCue(String type, Map<String, String> payload) {
                applyPreviewEventCue(type, payload);
            }
        };
    }

    private String resolveTimelineNameForRuntimeData() {
        String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
        if (timelineName.isBlank()) timelineName = project.getName();
        if (timelineName == null || timelineName.isBlank()) timelineName = "Untitled Animation";
        return timelineName;
    }

    private void captureSceneStateBaseline() {
        sceneBaselineImagePaths.clear();
        sceneBaselineVisibility.clear();
        sceneBaselineCustomProperties.clear();
        sceneBaselineCameraCustomProperties.clear();
        if (scene == null) return;
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;
            sceneBaselineVisibility.put(entityName, entity.isVisible());
            sceneBaselineCustomProperties.put(entityName, captureEntityCustomPropertyBaseline(entity));
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                sceneBaselineImagePaths.put(entityName, sprite.getImagePath());
            }
        }
        sceneBaselineCameraCustomProperties.putAll(captureCameraCustomPropertyBaseline(animationPreview.getCamera()));
    }

    private void restorePreviewBaselineState() {
        if (scene == null) return;
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;
            Boolean visible = sceneBaselineVisibility.get(entityName);
            if (visible != null) {
                entity.setVisible(visible);
            }
            restoreEntityCustomPropertyBaseline(entity, sceneBaselineCustomProperties.get(entityName));
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                String baselinePath = sceneBaselineImagePaths.get(entityName);
                if (baselinePath != null) {
                    sprite.setImagePath(baselinePath);
                }
            }
        }
        restoreCameraCustomPropertyBaseline(animationPreview.getCamera(), sceneBaselineCameraCustomProperties);
    }

    private void applyPreviewEventCuesUpTo(double timeMs) {
        for (EditorEventCue cue : project.getEditorEventCues()) {
            if (cue == null || cue.getType() == null || cue.getType().isBlank()) continue;
            if (cue.getTimeMs() > timeMs + 0.001) break;
            applyPreviewEventCue(cue);
        }
    }

    private void applyPreviewEventCue(EditorEventCue cue) {
        if (cue == null) return;
        applyPreviewEventCue(cue.getType(), cue.getPayloadView());
    }

    private void applyPreviewEventCue(String rawType, Map<String, String> payload) {
        if (scene == null || rawType == null) return;
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        String target = payloadValue(payload, "target");
        com.jvn.core.scene2d.Entity2D entity = target == null || target.isBlank() ? null : scene.find(target);

        switch (type) {
            case "expression" -> {
                String expression = payloadValue(payload, "value");
                String path = resolveCueAssetPath(target, expression, payloadValue(payload, "path"), false);
                if (path != null && entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(path);
                    entity.setVisible(true);
                }
            }
            case "show" -> {
                if (entity != null) entity.setVisible(true);
                String expression = payloadValue(payload, "expression");
                if (expression.isBlank()) expression = payloadValue(payload, "value");
                String path = resolveCueAssetPath(target, expression, payloadValue(payload, "path"), false);
                if (path != null && entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(path);
                }
            }
            case "hide" -> {
                if (entity != null) entity.setVisible(false);
            }
            case "replace" -> {
                String expression = payloadValue(payload, "expression");
                if (expression.isBlank()) expression = payloadValue(payload, "value");
                String path = resolveCueAssetPath(target, expression, payloadValue(payload, "path"), false);
                if (path != null && entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(path);
                    entity.setVisible(true);
                }
            }
            case "scene" -> {
                String resolvedPath = resolveCueAssetPath(target, payloadValue(payload, "id"), payloadValue(payload, "path"), true);
                com.jvn.core.scene2d.Entity2D background = entity;
                if (background == null) {
                    background = findBackgroundEntity();
                }
                if (resolvedPath != null && background instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(resolvedPath);
                    background.setVisible(true);
                }
            }
            default -> {
            }
        }
    }

    private static String payloadValue(Map<String, String> payload, String key) {
        if (payload == null || key == null) return "";
        return payload.getOrDefault(key, "");
    }

    private com.jvn.core.scene2d.Entity2D findBackgroundEntity() {
        if (scene == null) return null;
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            if (!entityName.startsWith("bg_")) continue;
            return scene.find(entityName);
        }
        return null;
    }

    private String resolveCueAssetPath(String target, String expressionOrId, String directPath, boolean backgroundCue) {
        if (directPath != null && !directPath.isBlank()) {
            return resolvePreviewAssetPathSpec(directPath.trim());
        }
        String token = expressionOrId == null ? "" : expressionOrId.trim();
        if (token.isBlank()) return null;
        if (backgroundCue) {
            String mapped = launchBackgroundPaths.get(token);
            return mapped == null || mapped.isBlank() ? null : resolvePreviewAssetPathSpec(mapped);
        }
        if (target == null || target.isBlank()) return null;
        String mapped = launchCharacterImagePaths.get(target + "/" + token);
        if ((mapped == null || mapped.isBlank()) && !"neutral".equals(token)) {
            mapped = launchCharacterImagePaths.get(target + "/neutral");
        }
        return mapped == null || mapped.isBlank() ? null : resolvePreviewAssetPathSpec(mapped);
    }

    private String resolvePreviewAssetPathSpec(String pathSpec) {
        if (pathSpec == null || pathSpec.isBlank()) return null;
        if (pathSpec.indexOf('|') < 0) {
            return resolvePreviewAssetPath(pathSpec.trim());
        }
        StringBuilder out = new StringBuilder();
        for (String token : pathSpec.split("\\|")) {
            String part = token == null ? "" : token.trim();
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(" | ");
            out.append(resolvePreviewAssetPath(part));
        }
        return out.isEmpty() ? null : out.toString();
    }

    private String resolvePreviewAssetPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return rawPath;
        File file = new File(rawPath);
        if (!file.isAbsolute() && projectRoot != null) {
            file = new File(projectRoot, rawPath);
        }
        return file.exists() ? file.getAbsolutePath() : rawPath;
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

        // Remove via SplitPane items API (not detachNode — node.getParent()
        // returns the internal Content wrapper, not the SplitPane itself)
        topWorkspaceSplit.getItems().remove(previewPane);
        bottomWorkspaceSplit.getItems().remove(timelinePanel);
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

        // Remove via SplitPane items API (not detachNode — node.getParent()
        // returns the internal Content wrapper, not the SplitPane itself,
        // so detachNode leaves stale refs in the items list that block re-addition)
        previewFocusSplit.getItems().remove(previewPane);
        previewFocusSplit.getItems().remove(timelinePanel);

        previewFocusMode = false;
        updatePreviewWorkspaceModeVisibility();

        // Re-attach to original split panes (force-remove stale refs first)
        topWorkspaceSplit.getItems().remove(previewPane);
        topWorkspaceSplit.getItems().add(previewPane);
        SplitPane.setResizableWithParent(previewPane, Boolean.TRUE);

        bottomWorkspaceSplit.getItems().remove(timelinePanel);
        bottomWorkspaceSplit.getItems().add(timelinePanel);
        SplitPane.setResizableWithParent(timelinePanel, Boolean.TRUE);

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
        boolean fullscreen = isPreviewFullscreenActive();
        boolean hasScene = scene != null;
        boolean hovering = previewViewportHost.isHover();

        // Focus button: always visible when scene loaded and not in fullscreen;
        // swap between idle (subtle) and hover (prominent) style classes
        btnPreviewFullscreen.setVisible(hasScene && !fullscreen);
        btnPreviewFullscreen.getStyleClass().removeAll(
            "puppeteer-preview-overlay-button-idle", "puppeteer-preview-overlay-button-hover");
        btnPreviewFullscreen.getStyleClass().add(
            hovering ? "puppeteer-preview-overlay-button-hover" : "puppeteer-preview-overlay-button-idle");

        // Back button: always visible during fullscreen
        if (btnPreviewBack != null) {
            btnPreviewBack.setVisible(fullscreen);
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
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN),
            this::executeUndo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
            this::executeRedo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN),
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
        List<TimelineDiagnostic.Message> diags = new ArrayList<>(
            PuppeteerVerification.diagnose(
                project,
                knownSceneEntities(),
                projectRoot,
                PuppeteerVerification.Mode.EXPORT_CODE
            )
        );
        diags.addAll(TimelineDiagnostic.diagnoseDsl(codePreview.getCode()));
        codePreview.setDiagnostics(diags);
        refreshSidebarTabs();
    }

    private void syncDurationUi() {
        String current = tfDuration.getText();
        String updated = String.valueOf((int) project.getTotalDurationMs());
        if (!updated.equals(current)) {
            tfDuration.setText(updated);
            keyframeEditor.setTimelineDurationMs(project.getTotalDurationMs());
        }
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
        if (runtimeParityPreview) sb.append("  │  Runtime Preview ON");
        sb.append("  │  Speed: ").append(playbackSpeed).append("x");
        statusBar.setText(sb.toString());
        refreshToolbarCommandSummary();
    }

    private void updateViewportInfoLabel() {
        if (viewportInfoLabel == null) return;
        ProjectViewportSpec.Dimensions vp = ProjectViewportSpec.resolve(projectRoot);
        viewportInfoLabel.setText(
            "Viewport: " + vp.width() + "x" + vp.height()
                + ""
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
        content.setStyle("-fx-control-inner-background: #121212; -fx-text-fill: #d7d7d7; -fx-font-family: Monospaced;");
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

        List<PuppeteerAudioLibrary.AudioEntry> libraryEntries = new ArrayList<>(PuppeteerAudioLibrary.scan(projectRoot));
        TextField tfLibraryFilter = new TextField();
        tfLibraryFilter.setPromptText("Filter project audio...");
        tfLibraryFilter.setStyle(STYLE_TEXT_FIELD);

        ListView<PuppeteerAudioLibrary.AudioEntry> libraryList = new ListView<>();
        libraryList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(PuppeteerAudioLibrary.AudioEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.relativePath());
            }
        });
        libraryList.setPrefHeight(180);
        libraryList.setMinHeight(120);
        libraryList.setStyle("-fx-background-color: #14171d; -fx-control-inner-background: #14171d;");

        Label libraryMeta = makeToolbarLabel("");
        refreshAudioLibraryList(libraryList, libraryEntries, tfLibraryFilter.getText());
        libraryMeta.setText(libraryEntries.isEmpty()
            ? "No project audio found yet. Use Browse or Import to seed the library."
            : libraryEntries.size() + " project audio files");
        tfLibraryFilter.textProperty().addListener((obs, oldValue, newValue) ->
            refreshAudioLibraryList(libraryList, libraryEntries, newValue)
        );
        libraryList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                tfPath.setText(newValue.relativePath());
            }
        });
        libraryList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && libraryList.getSelectionModel().getSelectedItem() != null) {
                tfPath.setText(libraryList.getSelectionModel().getSelectedItem().relativePath());
                playAudioPreview(tfPath.getText());
                event.consume();
            }
        });

        Button btnBrowseAudio = new Button("Browse...");
        btnBrowseAudio.setStyle(STYLE_BTN_DARK);
        btnBrowseAudio.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose Audio Cue");
            chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Audio", "*.ogg", "*.wav", "*.mp3", "*.m4a", "*.aac", "*.flac", "*.opus"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
            if (projectRoot != null && projectRoot.isDirectory()) {
                chooser.setInitialDirectory(projectRoot);
            }
            File selected = chooser.showOpenDialog(this);
            if (selected == null) return;
            if (projectRoot != null && projectRoot.isDirectory()) {
                Path rootPath = projectRoot.toPath().toAbsolutePath().normalize();
                Path selectedPath = selected.toPath().toAbsolutePath().normalize();
                if (selectedPath.startsWith(rootPath)) {
                    tfPath.setText(rootPath.relativize(selectedPath).toString().replace('\\', '/'));
                    return;
                }
            }
            tfPath.setText(selected.getAbsolutePath().replace('\\', '/'));
        });

        Button btnImportAudio = new Button("Import...");
        btnImportAudio.setStyle(STYLE_BTN_DARK);
        btnImportAudio.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Import Audio Into Project");
            chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Audio", "*.ogg", "*.wav", "*.mp3", "*.m4a", "*.aac", "*.flac", "*.opus"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
            File initialDir = projectRoot != null && projectRoot.isDirectory()
                ? new File(projectRoot, PuppeteerAudioLibrary.IMPORT_RELATIVE_DIR)
                : null;
            if (initialDir != null && initialDir.isDirectory()) {
                chooser.setInitialDirectory(initialDir);
            } else if (projectRoot != null && projectRoot.isDirectory()) {
                chooser.setInitialDirectory(projectRoot);
            }
            File selected = chooser.showOpenDialog(this);
            if (selected == null) return;
            if (projectRoot == null || !projectRoot.isDirectory()) {
                tfPath.setText(selected.getAbsolutePath().replace('\\', '/'));
                return;
            }
            try {
                Path importDir = projectRoot.toPath().resolve(PuppeteerAudioLibrary.IMPORT_RELATIVE_DIR);
                Files.createDirectories(importDir);
                Path target = PuppeteerAudioLibrary.resolveUniqueImportTarget(importDir, selected.getName());
                Files.copy(selected.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                String relativePath = projectRoot.toPath().toAbsolutePath().normalize()
                    .relativize(target.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
                tfPath.setText(relativePath);
                libraryEntries.clear();
                libraryEntries.addAll(PuppeteerAudioLibrary.scan(projectRoot));
                refreshAudioLibraryList(libraryList, libraryEntries, tfLibraryFilter.getText());
                libraryMeta.setText(libraryEntries.size() + " project audio files");
            } catch (IOException ex) {
                statusBar.setText("Audio import failed: " + ex.getMessage());
            }
        });

        Button btnPreviewAudio = new Button("Preview");
        btnPreviewAudio.setStyle(STYLE_BTN_DARK);
        btnPreviewAudio.setOnAction(e -> playAudioPreview(tfPath.getText()));

        Button btnStopAudio = new Button("Stop");
        btnStopAudio.setStyle(STYLE_BTN_DARK);
        btnStopAudio.setOnAction(e -> stopAudioPreview());

        HBox pathRow = new HBox(8, tfPath, btnBrowseAudio, btnImportAudio);
        HBox.setHgrow(tfPath, Priority.ALWAYS);
        pathRow.setAlignment(Pos.CENTER_LEFT);

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
        grid.add(pathRow, 1, 0);
        grid.add(lChannel, 0, 1);
        grid.add(new HBox(8, rbMusic, rbSound, rbVoice), 1, 1);
        grid.add(lVolume, 0, 2);
        grid.add(new HBox(8, volume, volumeLabel), 1, 2);

        Label libraryTitle = makeToolbarLabel("Project Audio");
        HBox libraryActions = new HBox(8, btnPreviewAudio, btnStopAudio, libraryMeta);
        libraryActions.setAlignment(Pos.CENTER_LEFT);
        VBox libraryBox = new VBox(8, libraryTitle, tfLibraryFilter, libraryList, libraryActions);
        libraryBox.setPadding(new Insets(0, 8, 8, 8));
        overlayDialog.showDialog(
            "Add Audio Cue",
            "Create an audio trigger at playhead " + String.format("%.0fms", project.getPlayheadMs()),
            new VBox(8, grid, libraryBox),
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", () -> {
                stopAudioPreview();
                overlayDialog.hideOverlay();
            }).defaultFocus(true),
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
                stopAudioPreview();
                overlayDialog.hideOverlay();
            })
        );
    }

    private void showEventCueManagerDialog(EditorEventCue initialSelection) {
        List<String> presets = List.of(
            "expression",
            "show",
            "hide",
            "replace",
            "scene",
            "dialogue_marker",
            "script_call",
            "custom"
        );

        ListView<EditorEventCue> cueList = new ListView<>();
        cueList.setPrefHeight(180);
        cueList.setMinHeight(120);
        cueList.setStyle("-fx-background-color: #14171d; -fx-control-inner-background: #14171d;");
        cueList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(EditorEventCue item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String target = item.getPayloadValue("target");
                String suffix = target == null || target.isBlank() ? "" : " -> " + target;
                setText(String.format("%.0fms  %s%s", item.getTimeMs(), item.getType(), suffix));
            }
        });

        ComboBox<String> cbTypePreset = new ComboBox<>();
        cbTypePreset.getItems().setAll(presets);
        cbTypePreset.setValue("expression");
        cbTypePreset.setStyle(STYLE_TEXT_FIELD);
        cbTypePreset.setPrefWidth(160);

        TextField tfType = new TextField("expression");
        tfType.setPromptText("event type");
        tfType.setStyle(STYLE_TEXT_FIELD);

        TextField tfTime = new TextField(String.format("%.0f", project.getPlayheadMs()));
        tfTime.setPromptText("playhead ms");
        tfTime.setStyle(STYLE_TEXT_FIELD);

        TextField tfTarget = new TextField();
        tfTarget.setPromptText("entity / character id");
        tfTarget.setStyle(STYLE_TEXT_FIELD);

        TextField tfValue = new TextField();
        tfValue.setPromptText("expression / id / name");
        tfValue.setStyle(STYLE_TEXT_FIELD);

        TextField tfPath = new TextField();
        tfPath.setPromptText("optional image path");
        tfPath.setStyle(STYLE_TEXT_FIELD);

        TextField tfPosition = new TextField();
        tfPosition.setPromptText("optional slot like left / center / right");
        tfPosition.setStyle(STYLE_TEXT_FIELD);

        TextArea taExtra = new TextArea();
        taExtra.setPromptText("extra payload\nkey=value");
        taExtra.setPrefRowCount(5);
        taExtra.setWrapText(false);
        taExtra.setStyle("-fx-control-inner-background: #111111; -fx-text-fill: #ececec;");

        Label lblTypeHint = makeToolbarLabel("");
        Label lblValue = makeToolbarLabel("Value");
        Label lblPath = makeToolbarLabel("Path");
        Label lblPosition = makeToolbarLabel("Position");

        Runnable refreshList = () -> cueList.getItems().setAll(project.getEditorEventCues());
        Runnable clearForm = () -> {
            cueList.getSelectionModel().clearSelection();
            cbTypePreset.setValue("expression");
            tfType.setText("expression");
            tfTime.setText(String.format("%.0f", project.getPlayheadMs()));
            tfTarget.clear();
            tfValue.clear();
            tfPath.clear();
            tfPosition.clear();
            taExtra.clear();
        };
        Runnable refreshTypeUi = () -> {
            String preset = cbTypePreset.getValue() == null ? "expression" : cbTypePreset.getValue();
            boolean custom = "custom".equals(preset);
            tfType.setDisable(!custom);
            if (!custom) {
                tfType.setText(preset);
            }
            switch (preset) {
                case "expression" -> {
                    lblValue.setText("Expression");
                    lblPath.setText("Path Override");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Swap a character or sprite to another expression at an exact frame.");
                }
                case "show" -> {
                    lblValue.setText("Expression");
                    lblPath.setText("Path Override");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Reveal an existing entity or character, optionally swapping its image or slot.");
                }
                case "hide" -> {
                    lblValue.setText("Value");
                    lblPath.setText("Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Hide an entity or character instantly.");
                }
                case "replace" -> {
                    lblValue.setText("Expression");
                    lblPath.setText("Replacement Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Replace the current sprite path or expression mid-sequence.");
                }
                case "scene" -> {
                    lblValue.setText("Scene / BG Id");
                    lblPath.setText("Background Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Trigger a background or cutaway change without leaving the timeline.");
                }
                case "dialogue_marker" -> {
                    lblValue.setText("Marker Id");
                    lblPath.setText("Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Drop a script-facing dialogue marker at the playhead.");
                }
                case "script_call" -> {
                    lblValue.setText("Call Name");
                    lblPath.setText("Arg");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Trigger a named script call cue with optional payload lines.");
                }
                default -> {
                    lblValue.setText("Value");
                    lblPath.setText("Path");
                    lblPosition.setText("Position");
                    lblTypeHint.setText("Author a custom event type with any payload keys you need.");
                }
            }
        };

        java.util.function.Consumer<EditorEventCue> loadCue = cue -> {
            if (cue == null) {
                clearForm.run();
                refreshTypeUi.run();
                return;
            }
            String type = cue.getType() == null ? "" : cue.getType().trim();
            cbTypePreset.setValue(presets.contains(type) ? type : "custom");
            tfType.setText(type);
            tfTime.setText(String.format("%.0f", cue.getTimeMs()));
            tfTarget.setText(cue.getPayloadValue("target"));

            Map<String, String> remaining = new LinkedHashMap<>(cue.getPayloadView());
            remaining.remove("target");
            switch (type) {
                case "expression" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("value"), remaining.remove("expression")));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.setText(remaining.remove("position"));
                }
                case "show" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("expression"), remaining.remove("value")));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.setText(remaining.remove("position"));
                }
                case "hide" -> {
                    tfValue.clear();
                    tfPath.clear();
                    tfPosition.clear();
                }
                case "replace" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("expression"), remaining.remove("value")));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.clear();
                }
                case "scene" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("id"), remaining.remove("value")));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.clear();
                }
                case "dialogue_marker" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("id"), remaining.remove("value")));
                    tfPath.clear();
                    tfPosition.clear();
                }
                case "script_call" -> {
                    tfValue.setText(firstNonBlank(remaining.remove("name"), remaining.remove("value")));
                    tfPath.setText(firstNonBlank(remaining.remove("arg"), remaining.remove("path")));
                    tfPosition.clear();
                }
                default -> {
                    tfValue.setText(remaining.remove("value"));
                    tfPath.setText(remaining.remove("path"));
                    tfPosition.setText(remaining.remove("position"));
                }
            }
            taExtra.setText(formatPayloadLines(remaining));
            refreshTypeUi.run();
        };

        cueList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> loadCue.accept(newValue));
        cbTypePreset.setOnAction(e -> refreshTypeUi.run());

        refreshList.run();
        if (initialSelection != null) {
            cueList.getSelectionModel().select(initialSelection);
            loadCue.accept(initialSelection);
        } else {
            clearForm.run();
            refreshTypeUi.run();
        }

        javafx.scene.layout.GridPane form = new javafx.scene.layout.GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(8, 8, 4, 8));
        form.add(makeToolbarLabel("Cue Type"), 0, 0);
        form.add(new HBox(8, cbTypePreset, tfType), 1, 0);
        form.add(makeToolbarLabel("Time"), 0, 1);
        form.add(tfTime, 1, 1);
        form.add(makeToolbarLabel("Target"), 0, 2);
        form.add(tfTarget, 1, 2);
        form.add(lblValue, 0, 3);
        form.add(tfValue, 1, 3);
        form.add(lblPath, 0, 4);
        form.add(tfPath, 1, 4);
        form.add(lblPosition, 0, 5);
        form.add(tfPosition, 1, 5);

        VBox body = new VBox(
            8,
            form,
            lblTypeHint,
            makeToolbarLabel("Timeline Event Cues"),
            cueList,
            makeToolbarLabel("Extra Payload"),
            taExtra
        );
        body.setPadding(new Insets(0, 8, 8, 8));

        overlayDialog.showDialog(
            "Timeline Event Cues",
            "Author discrete sprite swaps, show/hide beats, and scene changes.",
            body,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("New Cue", ActionEditorDialogOverlay.ButtonStyle.NEUTRAL, clearForm),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Save Cue", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                double timeMs;
                try {
                    timeMs = Math.max(0.0, Double.parseDouble(tfTime.getText().trim()));
                } catch (NumberFormatException ex) {
                    tfTime.requestFocus();
                    return;
                }

                String preset = cbTypePreset.getValue() == null ? "expression" : cbTypePreset.getValue();
                String type = "custom".equals(preset) ? tfType.getText().trim() : preset;
                if (type.isBlank()) {
                    tfType.requestFocus();
                    return;
                }

                Map<String, String> payload = parsePayloadLines(taExtra.getText());
                String target = tfTarget.getText() == null ? "" : tfTarget.getText().trim();
                String value = tfValue.getText() == null ? "" : tfValue.getText().trim();
                String path = tfPath.getText() == null ? "" : tfPath.getText().trim();
                String position = tfPosition.getText() == null ? "" : tfPosition.getText().trim();

                if (!target.isBlank()) payload.put("target", target);

                switch (type) {
                    case "expression" -> {
                        if (!value.isBlank()) payload.put("value", value);
                        if (!path.isBlank()) payload.put("path", path);
                        if (!position.isBlank()) payload.put("position", position);
                    }
                    case "show" -> {
                        if (!value.isBlank()) payload.put("expression", value);
                        if (!path.isBlank()) payload.put("path", path);
                        if (!position.isBlank()) payload.put("position", position);
                    }
                    case "replace" -> {
                        if (!value.isBlank()) payload.put("expression", value);
                        if (!path.isBlank()) payload.put("path", path);
                    }
                    case "scene" -> {
                        if (!value.isBlank()) payload.put("id", value);
                        if (!path.isBlank()) payload.put("path", path);
                    }
                    case "dialogue_marker" -> {
                        if (!value.isBlank()) payload.put("id", value);
                    }
                    case "script_call" -> {
                        if (!value.isBlank()) payload.put("name", value);
                        if (!path.isBlank()) payload.put("arg", path);
                    }
                    default -> {
                        if (!value.isBlank()) payload.putIfAbsent("value", value);
                        if (!path.isBlank()) payload.putIfAbsent("path", path);
                        if (!position.isBlank()) payload.putIfAbsent("position", position);
                    }
                }

                EditorEventCue selected = cueList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    project.removeEditorEventCue(selected);
                    selected.setTimeMs(timeMs);
                    selected.setType(type);
                    selected.getPayload().clear();
                    selected.getPayload().putAll(payload);
                    project.addEditorEventCue(selected);
                } else {
                    selected = new EditorEventCue(timeMs, type, payload);
                    project.addEditorEventCue(selected);
                }

                refreshList.run();
                cueList.getSelectionModel().select(selected);
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
            }),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Delete Cue", ActionEditorDialogOverlay.ButtonStyle.DANGER, () -> {
                EditorEventCue selected = cueList.getSelectionModel().getSelectedItem();
                if (selected == null) return;
                project.removeEditorEventCue(selected);
                refreshList.run();
                clearForm.run();
                refreshTypeUi.run();
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
            })
        );
    }

    private static Map<String, String> parsePayloadLines(String raw) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return payload;
        for (String line : raw.split("\\r?\\n")) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (key.isEmpty()) continue;
            payload.put(key, value);
        }
        return payload;
    }

    private static String formatPayloadLines(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(entry.getKey()).append('=').append(entry.getValue() == null ? "" : entry.getValue());
        }
        return sb.toString();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null ? "" : second;
    }

    private void refreshAudioLibraryList(ListView<PuppeteerAudioLibrary.AudioEntry> listView,
                                         List<PuppeteerAudioLibrary.AudioEntry> allEntries,
                                         String filterText) {
        if (listView == null) return;
        String query = filterText == null ? "" : filterText.trim().toLowerCase(Locale.ROOT);
        listView.getItems().clear();
        for (PuppeteerAudioLibrary.AudioEntry entry : allEntries) {
            if (entry == null) continue;
            if (query.isEmpty() || entry.relativePath().toLowerCase(Locale.ROOT).contains(query)) {
                listView.getItems().add(entry);
            }
        }
    }

    private void playAudioPreview(String rawPath) {
        stopAudioPreview();
        String uri = resolveAudioPreviewUri(rawPath);
        if (uri == null || uri.isBlank()) {
            if (statusBar != null) statusBar.setText("Audio preview not found");
            return;
        }
        try {
            audioPreviewPlayer = new MediaPlayer(new Media(uri));
            audioPreviewPlayer.setOnError(() -> {
                if (statusBar != null) statusBar.setText("Audio preview failed");
                stopAudioPreview();
            });
            audioPreviewPlayer.play();
        } catch (Exception ex) {
            if (statusBar != null) statusBar.setText("Audio preview failed: " + ex.getMessage());
            stopAudioPreview();
        }
    }

    private void stopAudioPreview() {
        if (audioPreviewPlayer == null) return;
        try {
            audioPreviewPlayer.stop();
            audioPreviewPlayer.dispose();
        } catch (Exception ignored) {
        }
        audioPreviewPlayer = null;
    }

    private String resolveAudioPreviewUri(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isBlank()) return null;
        File direct = new File(path);
        if (direct.isFile()) return direct.toURI().toString();
        if (projectRoot != null) {
            File fromRoot = new File(projectRoot, path.replace('\\', '/'));
            if (fromRoot.isFile()) return fromRoot.toURI().toString();
        }
        return null;
    }

    private void requestWindowClose() {
        if (!dirty && !previewStaged) {
            stopAudioPreview();
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
                requestRegisterTimeline(this::closeNow);
            })
        );
    }

    private void closeNow() {
        stopAudioPreview();
        if (assetImporterWindow != null) {
            assetImporterWindow.close();
            assetImporterWindow = null;
            assetImporterPanel = null;
        }
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

    private static RadioButton buildChannelButton(String channel, ToggleGroup group, boolean selected) {
        RadioButton button = new RadioButton(channel);
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setStyle("-fx-text-fill: #d7d7d7; -fx-font-size: 11px;");
        return button;
    }

    private Button buildOverlayMenuButton(String label, Runnable action) {
        Button button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(
            "-fx-background-color: #232323; -fx-text-fill: #d7d7d7; -fx-background-radius: 4; "
                + "-fx-border-color: #444444; -fx-border-radius: 4; -fx-padding: 7 10; -fx-font-size: 11px; -fx-cursor: hand;");
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

    private record ClipLibraryEntry(
        Path file,
        String relativePath,
        String folderLabel,
        AnimationClip clip
    ) {
        String displayName() {
            return clip != null ? clip.getName() : file.getFileName().toString();
        }
    }

    private boolean hasSavedClips() {
        return !scanClipLibrary().isEmpty();
    }

    private void saveSelectionAsClip() {
        EntityTrack track = selectedTrackForEditing(false);
        if (track == null) return;
        double start = project.hasLoopRegion() ? project.getLoopStartMs() : 0;
        double end = project.hasLoopRegion() ? project.getLoopEndMs() : project.getTotalDurationMs();

        AnimationClip clip = new AnimationClip(track.getEntityName() + "_clip");
        clip.captureFromTrack(track, start, end);
        if (clip.getChannels().isEmpty()) return;

        if (projectRoot == null) {
            showSaveError(clip.getName(), "No project root set.");
            return;
        }

        TextField clipPathField = new TextField(clip.getName());
        clipPathField.setPromptText("motion/hero_enter");
        clipPathField.setStyle(STYLE_TEXT_FIELD);

        Label summary = makeToolbarLabel(
            String.format(
                "Range %.0fms → %.0fms  •  %.0fms  •  %d animated channel(s)",
                start,
                end,
                clip.getDurationMs(),
                clip.getChannels().size()
            )
        );
        summary.setWrapText(true);

        Label hint = makeToolbarLabel("Use nested paths to organize clips into folders under config/puppeteer/clips.");
        hint.setWrapText(true);

        VBox content = new VBox(8,
            makeToolbarLabel("Clip Path"),
            clipPathField,
            summary,
            hint
        );
        content.setPadding(new Insets(4, 4, 0, 4));

        overlayDialog.showDialog(
            "Save Clip",
            "Save the selected track range as a reusable clip.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Save", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                Path clipsRoot = projectRoot.toPath().resolve("config").resolve("puppeteer").resolve("clips").normalize();
                Path clipFile = resolveClipFile(clipsRoot, clipPathField.getText());
                if (clipFile == null) {
                    clipPathField.requestFocus();
                    return;
                }
                try {
                    clip.setName(stripClipExtension(clipFile.getFileName().toString()));
                    clip.saveTo(clipFile);
                    setTitle("Puppeteer - saved clip '" + clipFile.getFileName() + "'");
                    overlayDialog.hideOverlay();
                } catch (IOException ex) {
                    showSaveError(clip.getName(), ex.getMessage());
                }
            })
        );
    }

    private void loadAndApplyClip() {
        EntityTrack track = selectedTrackForEditing(true);
        if (track == null || projectRoot == null) return;
        List<ClipLibraryEntry> entries = scanClipLibrary();
        if (entries.isEmpty()) return;

        TextField filterField = new TextField();
        filterField.setPromptText("Filter clips...");
        filterField.setStyle(STYLE_TEXT_FIELD);

        ListView<ClipLibraryEntry> clipList = new ListView<>();
        clipList.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ClipLibraryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(item.displayName());
                name.setStyle("-fx-text-fill: #f0f0f0; -fx-font-size: 12px; -fx-font-weight: bold;");
                Label meta = new Label(item.folderLabel().isBlank()
                    ? clipMetaLine(item.clip())
                    : item.folderLabel() + "  •  " + clipMetaLine(item.clip()));
                meta.setStyle("-fx-text-fill: #8c8c8c; -fx-font-size: 10px;");
                VBox box = new VBox(2, name, meta);
                box.setMinWidth(0);
                setGraphic(box);
                setText(null);
            }
        });
        clipList.setPrefHeight(280);
        clipList.setMinHeight(180);
        clipList.setStyle("-fx-background-color: #14171d; -fx-control-inner-background: #14171d;");

        Label selectionMeta = makeToolbarLabel("");
        selectionMeta.setWrapText(true);
        Label selectionProps = makeToolbarLabel("");
        selectionProps.setWrapText(true);

        TextField durationScaleField = new TextField("1.00");
        durationScaleField.setStyle(STYLE_TEXT_FIELD);
        durationScaleField.setPrefColumnCount(5);

        ComboBox<String> applyModeBox = new ComboBox<>();
        applyModeBox.getItems().setAll("Layer On Top", "Replace Range");
        applyModeBox.setValue("Layer On Top");
        applyModeBox.setStyle(STYLE_TEXT_FIELD);

        Runnable refreshClipFilter = () -> {
            String query = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
            clipList.getItems().clear();
            for (ClipLibraryEntry entry : entries) {
                if (query.isBlank()
                    || entry.displayName().toLowerCase(Locale.ROOT).contains(query)
                    || entry.relativePath().toLowerCase(Locale.ROOT).contains(query)) {
                    clipList.getItems().add(entry);
                }
            }
            if (!clipList.getItems().isEmpty() && clipList.getSelectionModel().getSelectedItem() == null) {
                clipList.getSelectionModel().select(0);
            }
        };
        filterField.textProperty().addListener((obs, oldValue, newValue) -> refreshClipFilter.run());
        refreshClipFilter.run();

        clipList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.clip() == null) {
                selectionMeta.setText("");
                selectionProps.setText("");
                return;
            }
            selectionMeta.setText("Target: " + track.getEntityName() + "  •  " + clipMetaLine(newValue.clip()));
            selectionProps.setText("Channels: " + clipPropertySummary(newValue.clip()));
        });

        HBox options = new HBox(10,
            new VBox(4, makeToolbarLabel("Scale"), durationScaleField),
            new VBox(4, makeToolbarLabel("Apply Mode"), applyModeBox)
        );
        VBox preview = new VBox(6,
            makeToolbarLabel("Clip Preview"),
            selectionMeta,
            selectionProps
        );
        preview.setStyle(STYLE_SIDEBAR_CARD);

        VBox content = new VBox(10,
            filterField,
            clipList,
            preview,
            options
        );
        content.setPadding(new Insets(4, 4, 0, 4));

        overlayDialog.showDialog(
            "Load Clip",
            "Apply a saved clip to '" + track.getEntityName() + "' at playhead.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Apply", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                ClipLibraryEntry selectedClip = clipList.getSelectionModel().getSelectedItem();
                if (selectedClip == null || selectedClip.clip() == null) {
                    clipList.requestFocus();
                    return;
                }
                double scale;
                try {
                    scale = Math.max(0.05, Double.parseDouble(durationScaleField.getText().trim()));
                } catch (Exception ex) {
                    durationScaleField.requestFocus();
                    return;
                }
                boolean replaceRange = "Replace Range".equals(applyModeBox.getValue());
                try {
                    applyClipToTrack(selectedClip.clip(), track, project.getPlayheadMs(), scale, replaceRange);
                    timelinePanel.refresh();
                    refreshExportPreviewAndMarkDirty();
                    overlayDialog.hideOverlay();
                } catch (Exception ex) {
                    showOverlayError("Clip Load Failed", "Could not apply clip '" + selectedClip.displayName() + "'", ex.getMessage());
                }
            })
        );
    }

    private List<ClipLibraryEntry> scanClipLibrary() {
        if (projectRoot == null || !projectRoot.isDirectory()) return List.of();
        Path clipsRoot = projectRoot.toPath().resolve("config").resolve("puppeteer").resolve("clips").normalize();
        if (!Files.isDirectory(clipsRoot)) return List.of();

        List<ClipLibraryEntry> entries = new ArrayList<>();
        try (var paths = Files.walk(clipsRoot)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".clip"))
                .forEach(path -> {
                    try {
                        AnimationClip clip = AnimationClip.loadFrom(path);
                        String relative = clipsRoot.relativize(path).toString().replace('\\', '/');
                        String folderLabel = "";
                        int slash = relative.lastIndexOf('/');
                        if (slash >= 0) {
                            folderLabel = relative.substring(0, slash);
                        }
                        entries.add(new ClipLibraryEntry(path, relative, folderLabel, clip));
                    } catch (Exception ignored) {
                    }
                });
        } catch (IOException ignored) {
            return List.of();
        }
        entries.sort((left, right) -> left.relativePath().compareToIgnoreCase(right.relativePath()));
        return entries;
    }

    private static String clipMetaLine(AnimationClip clip) {
        if (clip == null) return "Invalid clip";
        return String.format(
            "%.0fms  •  %d channel(s)",
            clip.getDurationMs(),
            clip.getChannels().size()
        );
    }

    private static String clipPropertySummary(AnimationClip clip) {
        if (clip == null || clip.getChannels().isEmpty()) return "No animated channels";
        List<String> labels = new ArrayList<>();
        for (PropertyType property : clip.getChannels().keySet()) {
            if (property != null) {
                labels.add(property.getDisplayName());
            }
        }
        return String.join(", ", labels);
    }

    private void applyClipToTrack(
        AnimationClip clip,
        EntityTrack track,
        double insertTimeMs,
        double durationScale,
        boolean replaceRange
    ) {
        if (clip == null || track == null) return;
        double scale = Math.max(0.05, durationScale);
        if (replaceRange) {
            double endTimeMs = insertTimeMs + clip.getDurationMs() * scale;
            for (PropertyType property : clip.getChannels().keySet()) {
                List<Keyframe> existing = new ArrayList<>(track.getKeyframes(property));
                for (Keyframe keyframe : existing) {
                    double time = keyframe.getTimeMs();
                    if (time >= insertTimeMs - 0.001 && time <= endTimeMs + 0.001) {
                        track.removeKeyframe(property, keyframe);
                    }
                }
            }
        }
        clip.applyToTrack(track, insertTimeMs, scale);
    }

    private static Path resolveClipFile(Path clipsRoot, String rawPath) {
        if (clipsRoot == null || rawPath == null) return null;
        String normalized = sanitizeClipRelativePath(rawPath);
        if (normalized.isBlank()) return null;
        Path relative = Path.of(normalized).normalize();
        if (relative.isAbsolute()) return null;
        if (relative.startsWith("..")) return null;
        Path file = clipsRoot.resolve(relative + ".clip").normalize();
        return file.startsWith(clipsRoot) ? file : null;
    }

    private static String sanitizeClipRelativePath(String rawPath) {
        String normalized = rawPath == null ? "" : rawPath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.endsWith(".clip")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized.trim();
    }

    private static String stripClipExtension(String fileName) {
        if (fileName == null) return "";
        return fileName.endsWith(".clip")
            ? fileName.substring(0, fileName.length() - 5)
            : fileName;
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

    private void showRuntimeVerificationReport() {
        List<TimelineDiagnostic.Message> findings = PuppeteerVerification.diagnose(
            project,
            knownSceneEntities(),
            projectRoot,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );
        if (findings.isEmpty()) {
            findings = List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.INFO,
                "(timeline)",
                "No runtime registration issues found",
                "This timeline is ready to register with the current project state"
            ));
        }
        showVerificationOverlay(
            "Runtime Verification",
            "Puppeteer checked this timeline against runtime registration rules.",
            findings,
            false,
            null
        );
    }

    private void requestRegisterTimeline() {
        requestRegisterTimeline(null);
    }

    private void requestRegisterTimeline(Runnable onSuccess) {
        String name = tfTimelineName.getText().trim();
        if (name.isEmpty()) {
            showOverlayError(
                "Missing Timeline Name",
                "Set a timeline name before registering.",
                "Use the timeline name field in the top toolbar."
            );
            return;
        }
        List<TimelineDiagnostic.Message> findings = PuppeteerVerification.diagnose(
            project,
            knownSceneEntities(),
            projectRoot,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );
        boolean hasErrors = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
        boolean hasWarnings = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.WARNING);

        if (hasErrors) {
            showVerificationOverlay(
                "Registration Blocked",
                "Puppeteer found runtime registration errors. Fix them before registering this timeline.",
                findings,
                false,
                null
            );
            return;
        }
        if (hasWarnings) {
            showVerificationOverlay(
                "Register Timeline?",
                "Puppeteer found warnings that may affect runtime playback. Continue registering anyway?",
                findings,
                true,
                () -> performRegisterTimeline(name, onSuccess)
            );
            return;
        }
        performRegisterTimeline(name, onSuccess);
    }

    private boolean performRegisterTimeline(String name, Runnable onSuccess) {
        project.setSceneEntitySnapshots(captureSceneEntitySnapshots());
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
        if (saved && onSuccess != null) {
            onSuccess.run();
        }
        return saved;
    }

    private void showVerificationOverlay(
        String title,
        String header,
        List<TimelineDiagnostic.Message> findings,
        boolean allowContinue,
        Runnable onContinue
    ) {
        TextArea body = new TextArea(formatVerificationMessages(findings));
        body.setEditable(false);
        body.setWrapText(true);
        body.setFocusTraversable(false);
        body.setPrefRowCount(Math.min(18, Math.max(8, findings == null ? 8 : findings.size() + 2)));
        body.setStyle("-fx-control-inner-background: #121212; -fx-text-fill: #d7d7d7; -fx-font-family: Monospaced;");
        if (allowContinue) {
            overlayDialog.showDialog(
                title,
                header,
                body,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.accent("Continue", () -> {
                    if (onContinue != null) {
                        onContinue.run();
                    }
                })
            );
            return;
        }
        overlayDialog.showDialog(
            title,
            header,
            body,
            ActionEditorDialogOverlay.ActionSpec.accent("Close", overlayDialog::hideOverlay)
        );
    }

    private static String formatVerificationMessages(List<TimelineDiagnostic.Message> findings) {
        if (findings == null || findings.isEmpty()) {
            return "No issues found.";
        }
        StringBuilder out = new StringBuilder();
        for (TimelineDiagnostic.Message finding : findings) {
            if (finding == null) continue;
            String prefix = switch (finding.severity()) {
                case ERROR -> "[ERROR]";
                case WARNING -> "[WARN]";
                case INFO -> "[INFO]";
            };
            out.append(prefix)
                .append(' ')
                .append(finding.entityOrTrack() == null || finding.entityOrTrack().isBlank()
                    ? "(timeline)"
                    : finding.entityOrTrack())
                .append(" — ")
                .append(finding.description());
            if (finding.quickFix() != null && !finding.quickFix().isBlank()) {
                out.append("\n  fix: ").append(finding.quickFix());
            }
            out.append("\n\n");
        }
        return out.toString().trim();
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
            backupExistingTimelineFile(file);
            Files.writeString(file, jesCode);
            return true;
        } catch (IOException ex) {
            showSaveError(name, ex.getMessage());
            return false;
        }
    }

    static Path backupExistingTimelineFile(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) return null;
        Path backupDir = file.getParent().resolve(".backups");
        Files.createDirectories(backupDir);
        String fileName = file.getFileName().toString();
        Path backup = backupDir.resolve(fileName + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup;
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

    private List<AnimationProject.SceneEntitySnapshot> captureSceneEntitySnapshots() {
        if (scene == null) return List.of();
        List<AnimationProject.SceneEntitySnapshot> snapshots = new ArrayList<>();
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;

            String type = "entity";
            String imagePath = "";
            double width = 1.0;
            double height = 1.0;
            double alpha = snapshotOrCurrent(entityName, PropertyType.ALPHA, fallbackPropertyValue(entity, PropertyType.ALPHA));
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                type = "sprite";
                imagePath = relativizePreviewAssetPathSpec(sprite.getImagePath());
                width = sprite.getWidth();
                height = sprite.getHeight();
                alpha = snapshotOrCurrent(entityName, PropertyType.ALPHA, sprite.getAlpha());
            } else if (entity instanceof com.jvn.core.scene2d.Panel2D panel) {
                type = "panel";
                width = panel.getWidth();
                height = panel.getHeight();
                alpha = snapshotOrCurrent(entityName, PropertyType.ALPHA, panel.getFillA());
            } else if (entity instanceof com.jvn.core.scene2d.CharacterEntity2D character) {
                type = "character";
                width = character.getDrawWidth();
                height = character.getDrawHeight();
            }

            snapshots.add(new AnimationProject.SceneEntitySnapshot(
                entityName,
                type,
                imagePath,
                snapshotOrCurrent(entityName, PropertyType.X, entity.getX()),
                snapshotOrCurrent(entityName, PropertyType.Y, entity.getY()),
                width,
                height,
                snapshotOrCurrent(entityName, PropertyType.PIVOT_X, entity.getOriginX()),
                snapshotOrCurrent(entityName, PropertyType.PIVOT_Y, entity.getOriginY()),
                snapshotOrCurrent(entityName, PropertyType.Z, entity.getZ()),
                snapshotOrCurrent(entityName, PropertyType.VISIBILITY, entity.isVisible() ? 1.0 : 0.0) >= 0.5,
                alpha
            ));
        }
        return snapshots;
    }

    private double snapshotOrCurrent(String entityName, PropertyType property, double fallback) {
        Double value = project.getInitialSnapshotValue(entityName, property);
        return value != null && Double.isFinite(value) ? value : fallback;
    }

    private String relativizePreviewAssetPathSpec(String pathSpec) {
        if (pathSpec == null || pathSpec.isBlank()) return "";
        if (pathSpec.indexOf('|') < 0) return relativizePreviewAssetPath(pathSpec.trim());
        StringBuilder out = new StringBuilder();
        for (String token : pathSpec.split("\\|")) {
            String part = token == null ? "" : token.trim();
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(" | ");
            out.append(relativizePreviewAssetPath(part));
        }
        return out.toString();
    }

    private String relativizePreviewAssetPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || projectRoot == null) {
            return rawPath == null ? "" : rawPath.trim();
        }
        try {
            Path root = projectRoot.toPath().toAbsolutePath().normalize();
            Path path = Path.of(rawPath.trim()).toAbsolutePath().normalize();
            if (path.startsWith(root)) {
                return root.relativize(path).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return rawPath.trim();
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
            resolveAnimatedTrack(primaryEntityName, true);
        }
        for (String entityName : names) {
            EntityTrack track = resolveAnimatedTrack(entityName, false);
            if (track == null) continue;
            var entity = scene != null ? scene.find(track.getEntityName()) : null;
            snapshots.put(entityName, captureTrackSnapshots(track, timeMs, TRANSFORM_INTERACTION_PROPERTIES, entity));
        }
        return snapshots;
    }

    private Map<PropertyType, PuppeteerCommand.PropertySnapshot> captureTrackSnapshots(
        EntityTrack track,
        double timeMs,
        PropertyType[] properties,
        com.jvn.core.scene2d.Entity2D entity
    ) {
        Map<PropertyType, PuppeteerCommand.PropertySnapshot> snapshots = new java.util.EnumMap<>(PropertyType.class);
        if (track == null || properties == null) {
            return snapshots;
        }
        for (PropertyType property : properties) {
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
            EntityTrack track = resolveAnimatedTrack(entityName, true);
            if (track == null) continue;
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
            EntityTrack track = resolveAnimatedTrack(entry.getKey(), true);
            if (track == null) continue;
            restoreTrackSnapshots(track, timeMs, entry.getValue());
        }
        updatePreview();
    }

    private void restoreTrackSnapshots(
        EntityTrack track,
        double timeMs,
        Map<PropertyType, PuppeteerCommand.PropertySnapshot> snapshots
    ) {
        if (track == null || snapshots == null) return;
        for (Map.Entry<PropertyType, PuppeteerCommand.PropertySnapshot> propertyEntry : snapshots.entrySet()) {
            restorePropertySnapshot(track, propertyEntry.getKey(), timeMs, propertyEntry.getValue());
        }
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
            case Z -> entity.getZ();
            case PIVOT_X -> getEntityPivotX(entity);
            case PIVOT_Y -> getEntityPivotY(entity);
            case ROTATION -> entity.getRotationDeg();
            case SCALE_X -> entity.getScaleX();
            case SCALE_Y -> entity.getScaleY();
            case ALPHA -> getEntityAlpha(entity);
            case VISIBILITY -> entity.isVisible() ? 1.0 : 0.0;
            case MATRIX_MXX -> entity.getMatrixMxx();
            case MATRIX_MXY -> entity.getMatrixMxy();
            case MATRIX_MYX -> entity.getMatrixMyx();
            case MATRIX_MYY -> entity.getMatrixMyy();
            case MATRIX_TX -> entity.getMatrixTx();
            case MATRIX_TY -> entity.getMatrixTy();
            case BLUR -> entity.getBlurRadius();
            case CAMERA_DOF_FOCUS, CAMERA_DOF_STRENGTH, CAMERA_DOF_MAX_BLUR -> property.getDefaultValue();
            default -> property.getDefaultValue();
        };
    }

    private static double getEntityAlpha(com.jvn.core.scene2d.Entity2D entity) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D s) return s.getAlpha();
        if (entity instanceof com.jvn.core.scene2d.Label2D l) return l.getAlpha();
        return 1.0;
    }

    private Map<String, Double> captureEntityCustomPropertyBaseline(com.jvn.core.scene2d.Entity2D entity) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (entity == null) return values;
        for (var definition : com.jvn.core.scene2d.Entity2D.animatableProperties()) {
            if (definition == null) continue;
            values.put(definition.getKey(), definition.getValue(entity));
        }
        values.putAll(entity.getCustomPropertiesView());
        return values;
    }

    private Map<String, Double> captureCameraCustomPropertyBaseline(com.jvn.core.graphics.Camera2D camera) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (camera == null) return values;
        for (var definition : com.jvn.core.graphics.Camera2D.animatableProperties()) {
            if (definition == null) continue;
            values.put(definition.getKey(), definition.getValue(camera));
        }
        values.putAll(camera.getCustomPropertiesView());
        return values;
    }

    private void restoreEntityCustomPropertyBaseline(
        com.jvn.core.scene2d.Entity2D entity,
        Map<String, Double> baseline
    ) {
        if (entity == null) return;
        entity.resetSupplementalTransform();
        entity.resetColorMatrix();
        entity.setBlurRadius(0.0);
        entity.clearCustomProperties();
        if (baseline == null) return;
        for (Map.Entry<String, Double> entry : baseline.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            entity.applyCustomProperty(entry.getKey(), entry.getValue());
        }
    }

    private void restoreCameraCustomPropertyBaseline(
        com.jvn.core.graphics.Camera2D camera,
        Map<String, Double> baseline
    ) {
        if (camera == null) return;
        camera.setFocusDepth(0.0);
        camera.setDepthOfFieldStrength(0.0);
        camera.setDepthOfFieldMaxBlur(0.0);
        camera.clearCustomProperties();
        if (baseline == null) return;
        for (Map.Entry<String, Double> entry : baseline.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            camera.applyCustomProperty(entry.getKey(), entry.getValue());
        }
    }

    private double baselineCustomPropertyValue(
        String targetName,
        String propertyKey,
        com.jvn.core.scene2d.Entity2D entity
    ) {
        if (targetName != null && !targetName.isBlank()) {
            Map<String, Double> baseline = sceneBaselineCustomProperties.get(targetName);
            if (baseline != null && baseline.containsKey(propertyKey)) {
                Double value = baseline.get(propertyKey);
                if (value != null && Double.isFinite(value)) return value;
            }
        }
        return entity != null ? entity.readCustomProperty(propertyKey) : 0.0;
    }

    private double baselineCustomPropertyValue(
        String targetName,
        String propertyKey,
        com.jvn.core.graphics.Camera2D camera
    ) {
        if (targetName != null && TimelinePanel.isRuntimeCameraTarget(targetName)) {
            Double value = sceneBaselineCameraCustomProperties.get(propertyKey);
            if (value != null && Double.isFinite(value)) return value;
        }
        return camera != null ? camera.readCustomProperty(propertyKey) : 0.0;
    }

    private String selectionLabel(String name, boolean group) {
        if (name == null || name.isBlank()) return "-";
        if (TimelinePanel.isRuntimeCameraTarget(name)) return "Runtime Camera / Frame";
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
        if (TimelinePanel.isRuntimeCameraTarget(name)) {
            return resolveRuntimeCameraTrack(createEntityTrack);
        }
        if (timelinePanel.isSelectedGroup()) {
            EntityGroup group = project.getGroup(name);
            return group != null ? group.getGroupTrack() : null;
        }
        return createEntityTrack ? project.getOrCreateTrack(name) : project.getTrack(name);
    }

    private EntityTrack resolveRuntimeCameraTrack(boolean createIfMissing) {
        EntityTrack dedicated = project.getTrack(TimelinePanel.RUNTIME_CAMERA_TARGET);
        if (dedicated != null) return dedicated;
        for (EntityTrack track : project.getTracks()) {
            if (track == null) continue;
            if (track.hasKeyframes(PropertyType.CAMERA_X)
                || track.hasKeyframes(PropertyType.CAMERA_Y)
                || track.hasKeyframes(PropertyType.CAMERA_ZOOM)
                || track.hasKeyframes(PropertyType.CAMERA_DOF_FOCUS)
                || track.hasKeyframes(PropertyType.CAMERA_DOF_STRENGTH)
                || track.hasKeyframes(PropertyType.CAMERA_DOF_MAX_BLUR)) {
                return track;
            }
            for (String propertyKey : track.getAnimatedCustomProperties()) {
                if (com.jvn.core.graphics.Camera2D.getAnimatableProperty(propertyKey) != null) {
                    return track;
                }
            }
        }
        return createIfMissing ? project.getOrCreateTrack(TimelinePanel.RUNTIME_CAMERA_TARGET) : null;
    }

    @Override
    public void close() {
        if (playbackTimer != null) playbackTimer.stop();
        super.close();
    }
}
