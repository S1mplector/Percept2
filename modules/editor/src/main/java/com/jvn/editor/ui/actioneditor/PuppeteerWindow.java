package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineRegistry;
import com.jvn.core.animation.TimelineRunner;
import com.jvn.core.vn.VnEyeFocusProfile;
import com.jvn.core.vn.VnEyeFocusProfileStore;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePresetLoader;
import com.jvn.editor.ui.CssIcon;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.editor.ui.SidebarToolHelp;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
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
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

public class PuppeteerWindow extends Stage {
    private static final StringConverter<PropertyType> PROPERTY_TYPE_LABEL_CONVERTER =
        new StringConverter<>() {
            @Override
            public String toString(PropertyType property) {
                return property == null ? "" : property.getDisplayName();
            }

            @Override
            public PropertyType fromString(String value) {
                if (value == null || value.isBlank()) return PropertyType.X;
                for (PropertyType property : PropertyType.values()) {
                    if (property.getDisplayName().equalsIgnoreCase(value.trim())
                        || property.name().equalsIgnoreCase(value.trim())
                        || property.getCode().equalsIgnoreCase(value.trim())) {
                        return property;
                    }
                }
                return PropertyType.X;
            }
        };
    private static final double LEFT_LIBRARY_WORKING_WIDTH = 360.0;
    private static final List<PropertyType> GROUP_PROPERTY_CHOICES = List.of(
        PropertyType.X,
        PropertyType.Y,
        PropertyType.Z,
        PropertyType.PIVOT_X,
        PropertyType.PIVOT_Y,
        PropertyType.ROTATION,
        PropertyType.SCALE_X,
        PropertyType.SCALE_Y,
        PropertyType.MIRROR_X,
        PropertyType.ALPHA,
        PropertyType.VISIBILITY,
        PropertyType.MATRIX_MXX,
        PropertyType.MATRIX_MXY,
        PropertyType.MATRIX_MYX,
        PropertyType.MATRIX_MYY,
        PropertyType.MATRIX_TX,
        PropertyType.MATRIX_TY,
        PropertyType.BLUR
    );
    private static final List<PropertyType> ENTITY_MATRIX_PROPERTY_CHOICES = List.of(
        PropertyType.MATRIX_MXX,
        PropertyType.MATRIX_MXY,
        PropertyType.MATRIX_MYX,
        PropertyType.MATRIX_MYY,
        PropertyType.MATRIX_TX,
        PropertyType.MATRIX_TY,
        PropertyType.BLUR,
        PropertyType.BRIGHTNESS
    );
    private static final List<String> ENTITY_DEDICATED_CUSTOM_KEYS = List.of(
        "matrix.mxx", "matrix.mxy", "matrix.myx", "matrix.myy", "matrix.tx", "matrix.ty",
        "effect.blur", "effect.brightness", "effect.exposure",
        "color.m00", "color.m01", "color.m02", "color.m03", "color.m04",
        "color.m10", "color.m11", "color.m12", "color.m13", "color.m14",
        "color.m20", "color.m21", "color.m22", "color.m23", "color.m24",
        "color.m30", "color.m31", "color.m32", "color.m33", "color.m34"
    );
    private static final List<String> CAMERA_DEDICATED_CUSTOM_KEYS = List.of(
        "dof.focus", "dof.strength", "dof.maxBlur"
    );
    public final AnimationProject project;
    public JesScene2D scene;

    private final EntitySelector entitySelector;
    public final TimelinePanel timelinePanel;
    public final KeyframeEditor keyframeEditor;
    private AnchorEditor anchorEditor;
    private ConstraintEditor constraintEditor;
    public final AnimationPreview animationPreview;
    public final CodePreviewPane codePreview;

    final Button btnPlay;
    final Button btnPause;
    final Button btnStop;
    final Button btnRewind;
    final Button btnUndo;
    final Button btnRedo;
    private final TextField tfDuration;
    public final ToggleButton cbLoop;
    private final Label lblTime;
    private ComboBox<PropertyType> cbProperty;
    private ToggleButton cbSnap;
    private TextField tfSnapMs;
    ToggleButton cbOrbitTool;
    private ToggleButton cbOrbitAlign;
    private ToggleButton cbRuntimePreview;
    private ToggleButton cbViewportStabilize;

    AnimationTimer playbackTimer;
    long lastNanos = 0;
    private double playbackSpeed = 1.0;
    private boolean autoKeyEnabled = false;
    private boolean runtimeParityPreview = false;
    private boolean viewportStabilizationEnabled = false;

    public final PuppeteerCommand.Stack commandStack = new PuppeteerCommand.Stack();
    private final KeyframeSelectionModel selectionModel;
    Consumer<String> onCopyCode;
    public final TextField tfTimelineName;
    public final Map<String, CollapsibleToolbarCluster> toolbarClusters = new LinkedHashMap<>();
    private AnimatedToolbarPane toolbarPane;
    private HBox toolbarCommandBar;
    private VBox toolbarShell;
    private BorderPane toolbarDock;
    private Button btnTopToolbarVisibility;
    private Label lblToolbarCommandSummary;
    Label statusBar;
    private Label statusPlaybackLabel;
    private Label statusTimelineLabel;
    private Label statusSelectionLabel;
    private Label statusSceneLabel;
    private Label statusComplexityLabel;
    private Label statusModeLabel;
    private Label statusSaveLabel;
    private Label statusViewportLabel;
    private Label statusProjectLabel;
    private Label statusExportLabel;
    private HBox statusPlaybackSegment;
    private HBox statusComplexitySegment;
    private HBox statusSaveSegment;
    private HBox statusModeSegment;
    private HBox statusViewportSegment;
    private HBox statusExportSegment;
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
    private final TextField[] sidebarMatrixFields = new TextField[8];
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
    private SplitPane rootWorkspaceSplit;
    private SplitPane previewFocusSplit;
    private StackPane workspaceModeHost;
    public boolean dirty = false;
    private boolean compactExport = false;
    public boolean previewStaged = false;
    private boolean dirtyBeforePreviewStage = false;
    private AnimationProject previewBaselineProject;
    private TransformInteractionState activeTransformInteraction;
    private CameraInteractionState activeCameraInteraction;
    public final ActionEditorDialogOverlay overlayDialog = new ActionEditorDialogOverlay();
    private final Map<String, String> launchCharacterImagePaths = new LinkedHashMap<>();
    private final Map<String, List<PuppeteerLauncherPanel.CharacterLayerEntry>> launchCharacterPresetLayers = new LinkedHashMap<>();
    private final Map<String, String> launchBackgroundPaths = new LinkedHashMap<>();
    private final Map<String, String> launchAudioPaths = new LinkedHashMap<>();
    final List<List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry>> clipboardHistory = new java.util.ArrayList<>();
    static final int MAX_CLIPBOARD_HISTORY = 10;
    private final Map<String, Boolean> sceneBaselineVisibility = new LinkedHashMap<>();
    private final Map<String, Map<String, Double>> sceneBaselineCustomProperties = new LinkedHashMap<>();
    private final Map<String, Double> sceneBaselineCameraCustomProperties = new LinkedHashMap<>();
    private final Map<String, String> sceneBaselineImagePaths = new LinkedHashMap<>();
    private boolean bypassCloseConfirmation = false;
    public boolean codePaneVisible = true;
    private double codePaneDividerPosition = 0.78;
    private boolean previewFocusMode = false;
    private double previewFocusDividerPosition = 0.72;
    private double topWorkspaceDividerPosition = 0.2;
    private double bottomWorkspaceDividerPosition = 0.28;
    private double toolbarDividerPosition = 0.16;
    private boolean topToolbarVisible = true;
    private double toolbarDragStartSceneY = 0.0;
    private double toolbarDragStartDivider = 0.16;

    private static final double MOVE_INTERACTION_EPSILON = 0.01;
    private static final double TOOLBAR_COLLAPSED_DIVIDER_POSITION = 0.035;
    private static final double TOOLBAR_MIN_VISIBLE_DIVIDER_POSITION = 0.075;
    private static final double TOOLBAR_MAX_DIVIDER_POSITION = 0.36;
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
        PropertyType.MATRIX_TY,
        PropertyType.MIRROR_X
    };
    private static final PropertyType[] CAMERA_INTERACTION_PROPERTIES = {
        PropertyType.CAMERA_X,
        PropertyType.CAMERA_Y,
        PropertyType.CAMERA_ZOOM
    };

    enum PuppeteerErrorType {
        VALIDATION("Validation"),
        PROJECT_CONTEXT("Project context"),
        CODE_PARSE("Code parse"),
        EXPORT("Export"),
        SAVE_IO("Save / disk"),
        REGISTRATION("Runtime registration"),
        PREVIEW("Preview"),
        ASSET("Asset"),
        AUDIO("Audio"),
        CLIP("Animation clip"),
        CLIPBOARD("Clipboard"),
        TIMELINE("Timeline"),
        KEYFRAME("Keyframe"),
        PROPERTY("Property"),
        ENTITY("Entity / Track"),
        CAMERA("Camera"),
        CUE("Cue"),
        INTERPOLATION("Interpolation"),
        IMPORT("Import"),
        STATE("State"),
        PLAYBACK("Playback"),
        UNKNOWN("Unknown");

        private final String label;

        PuppeteerErrorType(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private record TransformInteractionState(
        String entityName,
        double timeMs,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> beforeStates
    ) {}

    private record CameraInteractionState(
        double timeMs,
        Map<PropertyType, PuppeteerCommand.PropertySnapshot> beforeStates
    ) {}

    private record GroupPivotAdjustment(double x, double y, boolean translate) {}

    private record ExpressionLayerSpec(String layerId, String path) {}
    private record ExpressionLayerCandidate(String name, com.jvn.core.scene2d.Sprite2D sprite) {}

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
                anchorEditor.setSelectedEntityName(null, false);
                if (constraintEditor != null) constraintEditor.selectEntity(null);
            } else if (isGroup) {
                entitySelector.selectGroup(name);
                animationPreview.selectGroup(name);
                anchorEditor.setSelectedEntityName(name, true);
                if (constraintEditor != null) constraintEditor.selectEntity(null);
            } else {
                entitySelector.selectEntity(name);
                animationPreview.selectEntity(name);
                anchorEditor.setSelectedEntityName(name, false);
                if (constraintEditor != null) constraintEditor.selectEntity(name);
            }
            PropertyType selectedProp = timelinePanel.getSelectedProperty();
            if (selectedProp != null && cbProperty != null && cbProperty.getValue() != selectedProp) {
                cbProperty.setValue(selectedProp);
            }
            refreshPropertyPickerChoices();
            refreshSidebarTabs();
            updateStatusBar();
        });

        entitySelector.setOnSelectionChanged((name, isGroup) -> {
            timelinePanel.setSelectedTarget(name, isGroup);
            anchorEditor.setSelectedEntityName(name, isGroup);
            if (constraintEditor != null) constraintEditor.selectEntity(isGroup ? null : name);
            refreshPropertyPickerChoices();
            updateStatusBar();
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

        entitySelector.setOnEntityLockChanged((entityName, locked) -> {
            EntityTrack track = this.project.getTrack(entityName);
            if (track == null) return;
            track.setLocked(locked);
            entitySelector.refresh(this.project);
            entitySelector.selectEntity(entityName);
        });

        entitySelector.setOnGroupLockChanged((groupName, locked) -> {
            EntityGroup group = this.project.getGroup(groupName);
            if (group == null) return;
            group.setLocked(locked);
            entitySelector.refresh(this.project);
            entitySelector.selectGroup(groupName);
        });

        entitySelector.setOnSelectionSoloChanged((name, isGroup, soloed) -> {
            // When solo is toggled, force-select the soloed target and update the timeline.
            if (soloed) {
                timelinePanel.setSelectedTarget(name, isGroup);
                if (isGroup) {
                    animationPreview.selectGroup(name);
                    anchorEditor.setSelectedEntityName(name, true);
                } else {
                    animationPreview.selectEntity(name);
                    anchorEditor.setSelectedEntityName(name, false);
                }
            }
            timelinePanel.refresh();
            refreshPropertyPickerChoices();
        });

        entitySelector.setOnGroupResetRequested(groupName -> {
            EntityGroup group = this.project.getGroup(groupName);
            if (group == null) return;
            
            double time = this.project.getPlayheadMs();
            java.util.List<PuppeteerCommand> commands = new ArrayList<>();
            collectResetCommands(group, time, commands);
            
            if (!commands.isEmpty()) {
                commandStack.execute(PuppeteerCommand.composite("Reset Group Transforms", commands));
                refreshExportPreviewAndMarkDirty();
                updatePreview();
                if (timelinePanel != null) timelinePanel.refresh();
            }
        });

        timelinePanel.setOnEventCueSelected(this::showEventCueManagerDialog);

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
            updateStatusBar();
        });

        timelinePanel.setOnPlayheadChanged(time -> {
            this.project.setPlayheadMs(time);
            updateTimeLabel();
            updatePreview();
            refreshSidebarTabs();
            updateStatusBar();
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
            anchorEditor.setSelectedEntityName(name, false);
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
            Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> afterStates = captureTransformSnapshots(name, time);
            List<PuppeteerCommand> commands = buildTransformInteractionCommands(time, beforeStates, afterStates);

            if (commands.isEmpty()) {
                restoreTransformSnapshots(time, beforeStates);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
                return;
            }

            restoreTransformSnapshots(time, beforeStates);
            commandStack.execute(PuppeteerCommand.composite("Edit transform", commands));
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityPivotChanged((name, pivot) -> {
            if (name == null || pivot == null || pivot.length < 2) return;
            EntityTrack track = resolveAnimatedTrack(name, true);
            if (track == null) return;
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            if (project.getGroup(name) != null) {
                upsertGroupPivotPreservingPose(name, track, time, pivot[0], pivot[1]);
            } else {
                track.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(time, pivot[0]));
                track.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(time, pivot[1]));
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        keyframeEditor.setOnPivotPresetApplied((px, py) -> {
            String name = timelinePanel.getSelectedEntity();
            if (name == null || name.isBlank()) return;
            EntityTrack track = selectedTrackForEditing(true);
            if (track == null) return;
            double time = this.project.getPlayheadMs();
            commandStack.execute(PuppeteerCommand.composite(
                "Apply pivot preset",
                List.of(
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_X, time, px),
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_Y, time, py)
                )
            ));
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityRotationChanged((name, rotationDeg) -> {
            if (name == null || rotationDeg == null || !Double.isFinite(rotationDeg)) return;
            boolean groupTarget = this.project.getGroup(name) != null;
            EntityTrack track = resolveAnimatedTrack(name, true);
            if (track == null) return;
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            track.upsertKeyframe(PropertyType.ROTATION, new Keyframe(time, rotationDeg));
            timelinePanel.refresh();
            if (groupTarget) {
                updatePreview();
            }
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityScaleChanged((name, scale) -> {
            if (name == null || scale == null || scale.length < 2) return;
            double scaleX = scale[0], scaleY = scale[1];
            if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY)) return;
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            track.upsertKeyframe(PropertyType.SCALE_X, new Keyframe(time, scaleX));
            track.upsertKeyframe(PropertyType.SCALE_Y, new Keyframe(time, scaleY));
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
            requestRefreshGeneratedCode();
        });

        // --- Transport controls ---
        btnRewind = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.skipPrevious("#7ab3e0"), "Rewind (Home)");
        btnPlay = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.play("#57c464"), "Play (Space)");
        btnPause = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.pause("#f0c030"), "Pause (Space)");
        btnStop = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.stop("#e05050"), "Stop");
        btnUndo = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.undo("#7ab3e0"), "Undo (Ctrl/Cmd+Z)");
        btnRedo = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.redo("#7ab3e0"), "Redo (Ctrl/Cmd+Shift+Z)");

        btnPlay.setOnAction(e -> play());
        btnPause.setOnAction(e -> pause());
        btnStop.setOnAction(e -> stop());
        btnRewind.setOnAction(e -> rewind());
        btnUndo.setOnAction(e -> executeUndo());
        btnRedo.setOnAction(e -> executeRedo());
        refreshUndoRedoControls();

        lblTime = new Label("0 ms");
        lblTime.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 72; -fx-alignment: center;");

        HBox transportBox = new HBox(4, btnRewind, btnPlay, btnPause, btnStop, makeSpacer(6), lblTime);
        transportBox.setAlignment(Pos.CENTER_LEFT);
        HBox historyBox = new HBox(4, btnUndo, btnRedo);
        historyBox.setAlignment(Pos.CENTER_LEFT);

        // --- Duration controls ---
        tfDuration = new TextField(String.valueOf((int) this.project.getTotalDurationMs()));
        tfDuration.setPrefWidth(64);
        tfDuration.setStyle(STYLE_TEXT_FIELD);
        tfDuration.setOnAction(e -> {
            try {
                double dur = Double.parseDouble(tfDuration.getText());
                if (!Double.isFinite(dur) || dur < 0.0) {
                    throw new NumberFormatException("Duration must be a finite non-negative number.");
                }
                this.project.setTotalDurationMs(dur);
                keyframeEditor.setTimelineDurationMs(this.project.getTotalDurationMs());
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            } catch (NumberFormatException ex) {
                syncDurationUi();
                showOverlayError(
                    PuppeteerErrorType.VALIDATION,
                    "Invalid Duration",
                    "Puppeteer could not apply the timeline duration.",
                    "Duration must be a finite non-negative number of milliseconds.",
                    ex
                );
            }
        });

        Button btnFitDuration = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.rectSelect(), "Fit duration to content");
        btnFitDuration.setOnAction(e -> {
            this.project.fitDurationToContent();
            tfDuration.setText(String.valueOf((int) this.project.getTotalDurationMs()));
            keyframeEditor.setTimelineDurationMs(this.project.getTotalDurationMs());
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        cbLoop = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.loop("#3cbcaa"), "Loop timeline playback");
        cbLoop.setSelected(this.project.isLooping());
        cbLoop.setOnAction(e -> {
            this.project.setLooping(cbLoop.isSelected());
            refreshExportPreviewAndMarkDirty();
            updateStatusBar();
        });

        Button btnLoopIn = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.verticalAlignBottom("#3cbcaa"), "Set loop IN at playhead");
        btnLoopIn.setOnAction(e -> {
            double inMs = project.getPlayheadMs();
            double outMs = project.hasLoopRegion() ? project.getLoopEndMs() : project.getTotalDurationMs();
            if (inMs < outMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnLoopOut = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.verticalAlignTop("#3cbcaa"), "Set loop OUT at playhead");
        btnLoopOut.setOnAction(e -> {
            double outMs = project.getPlayheadMs();
            double inMs = project.hasLoopRegion() ? project.getLoopStartMs() : 0;
            if (outMs > inMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnLoopClear = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.clearX(), "Clear loop region");
        btnLoopClear.setOnAction(e -> {
            project.clearLoopRegion();
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        tfDuration.setTooltip(new Tooltip("Timeline duration (ms)"));
        HBox durationBox = new HBox(4, tfDuration, btnFitDuration, cbLoop, btnLoopIn, btnLoopOut, btnLoopClear);
        durationBox.setAlignment(Pos.CENTER_LEFT);

        // --- Presets ---
        Button presetButton = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.folder(), "Apply animation preset to selected entity");
        presetButton.setOnAction(e -> showPresetMenuOverlay());

        // --- Property target + snapping ---
        cbProperty = new ComboBox<>();
        cbProperty.getItems().setAll(PropertyType.values());
        cbProperty.setConverter(PROPERTY_TYPE_LABEL_CONVERTER);
        cbProperty.setButtonCell(propertyTypeListCell());
        cbProperty.setCellFactory(list -> propertyTypeListCell());
        cbProperty.setValue(PropertyType.X);
        cbProperty.setStyle(STYLE_TEXT_FIELD);
        cbProperty.setPrefWidth(158);
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

        Button btnCopyKeyframes = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.copy(), "Copy selected keyframes (Ctrl/Cmd+Alt+C)");
        btnCopyKeyframes.setOnAction(e -> copySelectedKeyframesToClipboard());
        Button btnPasteKeyframes = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.contentPaste(), "Paste keyframes at playhead (Ctrl/Cmd+Alt+V)");
        btnPasteKeyframes.setOnAction(e -> pasteCopiedKeyframesAtPlayhead());
        Button btnClipboardHistory = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.timeline(), "Clipboard history");
        btnClipboardHistory.setOnAction(e -> showClipboardHistoryPopup(btnClipboardHistory));
        Button btnDuplicateKeyframes = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.controlPointDuplicate(), "Duplicate selected keyframes by snap step (Ctrl/Cmd+Alt+D)");
        btnDuplicateKeyframes.setOnAction(e -> duplicateSelectedKeyframesBySnapStep());
        Button btnSaveClip = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.libraryAdd(), "Save selection as reusable clip");
        btnSaveClip.setOnAction(e -> saveSelectionAsClip());
        Button btnLoadClip = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.input(), "Load and apply a saved clip at playhead");
        btnLoadClip.setOnAction(e -> loadAndApplyClip());

        Button slotButton = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.emojiPeople(), "Place selected entity at a VN character slot");
        slotButton.setOnAction(e -> showSlotMenuOverlay());

        Button btnBatchKeyframe = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.plus(), "Add keyframe for ALL entities at playhead (batch)");
        btnBatchKeyframe.setOnAction(e -> {
            PropertyType prop = cbProperty.getValue();
            if (prop == null) prop = PropertyType.X;
            timelinePanel.addKeyframeForAllEntities(project.getPlayheadMs(), prop);
            refreshExportPreviewAndMarkDirty();
        });

        Button btnZoomFit = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.zoomOutMap(), "Zoom timeline to fit content");
        btnZoomFit.setOnAction(e -> timelinePanel.zoomToFit());
        Button btnFocusSelection = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.myLocation(), "Zoom timeline to the current selection or active track");
        btnFocusSelection.setOnAction(e -> timelinePanel.zoomToSelection());
        Button btnPrevKeyframe = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.fastRewind("#7ab3e0"), "Jump playhead to previous keyframe (Page Up)");
        btnPrevKeyframe.setOnAction(e -> timelinePanel.jumpPlayheadToPreviousKeyframe());
        Button btnNextKeyframe = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.fastForward("#7ab3e0"), "Jump playhead to next keyframe (Page Down)");
        btnNextKeyframe.setOnAction(e -> timelinePanel.jumpPlayheadToNextKeyframe());

        ToggleButton cbRipple = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.link("#f0a830"), "Ripple-retime: shift following keys when nudging a selection");
        cbRipple.setSelected(timelinePanel.isRippleRetimeEnabled());
        cbRipple.setOnAction(e -> timelinePanel.setRippleRetimeEnabled(cbRipple.isSelected()));

        Button btnDistributeKeys = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.formatAlignJustify(), "Distribute selected keyframes evenly across their current range");
        btnDistributeKeys.setOnAction(e -> {
            if (timelinePanel.distributeSelectedKeyframes()) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnReverseKeys = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.swapHoriz(), "Reverse selected keyframes within their current range");
        btnReverseKeys.setOnAction(e -> {
            if (timelinePanel.reverseSelectedKeyframes()) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnStretchKeys = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.openInFull(), "Stretch selected keyframes 25% wider");
        btnStretchKeys.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(1.25)) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnCompressKeys = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.closeFullscreen(), "Compress selected keyframes to 80% of their current range");
        btnCompressKeys.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(0.8)) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        ToggleButton cbCompactExport = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.folderZip(), "Use compact export format");
        cbCompactExport.setSelected(false);
        cbCompactExport.setOnAction(e -> {
            compactExport = cbCompactExport.isSelected();
            refreshExportPreview();
            updateStatusBar();
        });

        HBox keyframeOpsPrimaryRow = new HBox(4,
            btnCopyKeyframes,
            btnPasteKeyframes,
            btnClipboardHistory,
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

        cbSnap = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.grid4x4(), "Enable snapping");
        cbSnap.setSelected(timelinePanel.isSnapEnabled());
        cbSnap.setOnAction(e -> {
            timelinePanel.setSnapEnabled(cbSnap.isSelected());
            updateStatusBar();
        });

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
            updateStatusBar();
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
            com.jvn.editor.ui.CssIcon.movie("#9b72d4"),
            "Runtime data preview: render through TimelineData/TimelineRunner"
        );
        cbRuntimePreview.setSelected(runtimeParityPreview);
        cbRuntimePreview.setOnAction(e -> {
            runtimeParityPreview = cbRuntimePreview.isSelected();
            updatePreview();
            updateStatusBar();
        });

        cbViewportStabilize = makeToolbarIconToggle(
            com.jvn.editor.ui.CssIcon.myLocation("#8bd2ff"),
            "Stabilize preview viewport: lock framing so animated bounds cannot shake the view"
        );
        cbViewportStabilize.setSelected(viewportStabilizationEnabled);
        cbViewportStabilize.setOnAction(e -> setViewportStabilizationEnabled(cbViewportStabilize.isSelected()));

        // --- Auto-key toggle ---
        ToggleButton cbAutoKey = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.fiberSmartRecord("#e05050"), "Auto-key: automatically insert keyframe on drag");
        cbAutoKey.setSelected(false);
        cbAutoKey.setOnAction(e -> {
            autoKeyEnabled = cbAutoKey.isSelected();
            updateStatusBar();
        });
        Label lblAutoKey = new Label("Auto");
        lblAutoKey.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10px;");

        // --- Snap-to-grid / snap-to-entity toggles ---
        ToggleButton cbSnapGrid = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.borderAll(), "Snap entities to grid when dragging");
        cbSnapGrid.setSelected(false);
        cbSnapGrid.setOnAction(e -> animationPreview.setSnapToGridEnabled(cbSnapGrid.isSelected()));
        ToggleButton cbSnapEntity = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.joinInner(), "Snap to nearby entity positions");
        cbSnapEntity.setSelected(false);
        cbSnapEntity.setOnAction(e -> animationPreview.setSnapToEntityEnabled(cbSnapEntity.isSelected()));

        HBox autoKeyBox = new HBox(4, cbAutoKey, lblAutoKey);
        autoKeyBox.setAlignment(Pos.CENTER_LEFT);
        HBox previewSnapBox = new HBox(4, cbSnapGrid, cbSnapEntity, cbRuntimePreview, cbViewportStabilize, cbSpeed, cbWheelMode);
        previewSnapBox.setAlignment(Pos.CENTER_LEFT);

        cbOrbitTool = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.threeSixty("#9b72d4"), "Enable orbit-anchor tool. Shift+click preview to place anchor. Alt+Shift+click another entity to link the anchor at the exact cursor point (joint/nail).");
        cbOrbitTool.setSelected(animationPreview.isOrbitToolEnabled());
        cbOrbitTool.setOnAction(e -> animationPreview.setOrbitToolEnabled(cbOrbitTool.isSelected()));

        cbOrbitAlign = makeToolbarIconToggle(com.jvn.editor.ui.CssIcon.explore(), "When orbiting, update entity rotation to face outward.");
        cbOrbitAlign.setSelected(animationPreview.isOrbitAlignRotation());
        cbOrbitAlign.setOnAction(e -> animationPreview.setOrbitAlignRotation(cbOrbitAlign.isSelected()));

        Button btnClearAnchor = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.clearX(), "Clear orbit anchor for selected entity");
        btnClearAnchor.setOnAction(e -> {
            animationPreview.clearOrbitAnchorForSelectedEntity();
            updatePreview();
        });

        HBox orbitBox = new HBox(4, cbOrbitTool, cbOrbitAlign, btnClearAnchor);
        orbitBox.setAlignment(Pos.CENTER_LEFT);

        // --- Help button ---
        Button btnHelp = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.speech(), "Show keyboard shortcuts");
        btnHelp.setOnAction(e -> showShortcutsOverlay());

        // --- Audio + event cues ---
        Button btnAddCue = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.plus(), "Add audio cue at playhead");
        btnAddCue.setOnAction(e -> showAddAudioCueDialog());
        Button btnClearCues = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.clearX(), "Remove all timeline audio cues");
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
        Button btnAddExpressionCue = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.theater("#7bd88f"), "Add expression keyframe at playhead");
        btnAddExpressionCue.setOnAction(e -> showExpressionKeyframeDialog());
        Button btnManageEvents = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.list(), "Manage timeline event cues");
        btnManageEvents.setOnAction(e -> showEventCueManagerDialog(null));
        Button btnClearEvents = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.clearX(), "Remove all timeline event cues");
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
        HBox cueBox = new HBox(4, btnAddCue, btnClearCues, btnAddExpressionCue, btnManageEvents, btnClearEvents);
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

        Button btnRegister = makeToolbarSuccessIconButton(com.jvn.editor.ui.CssIcon.save(), "Register timeline for VNS interop");
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
        CollapsibleToolbarCluster historyCluster = registerToolbarCluster("history", "History", historyBox);
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

        // --- Export cluster ---
        Button btnExportGif = makeToolbarIconButton(
            com.jvn.editor.ui.CssIcon.fiberSmartRecord("#e57373"), "Record preview as GIF animation");
        btnExportGif.setOnAction(e -> showRecordGifDialog());
        Button btnExportCopyCode = makeToolbarIconButton(
            com.jvn.editor.ui.CssIcon.copy("#a8d0f0"), "Copy exported JES code to clipboard");
        btnExportCopyCode.setOnAction(e -> copyExportedCodeToClipboard());
        HBox exportBox = new HBox(4, btnExportGif, btnExportCopyCode);
        exportBox.setAlignment(Pos.CENTER_LEFT);
        CollapsibleToolbarCluster exportCluster = registerToolbarCluster("export", "Export", exportBox);

        // --- Diagnostics cluster ---
        Button btnVerify = makeToolbarIconButton(
            com.jvn.editor.ui.CssIcon.list("#90d090"), "Verify runtime timeline registration");
        btnVerify.setOnAction(e -> showRuntimeVerificationReport());
        CollapsibleToolbarCluster diagnosticsCluster = registerToolbarCluster("diagnostics", "Diagnostics", btnVerify);

        CollapsibleToolbarCluster helpCluster = registerToolbarCluster("help", "Help", btnHelp);

        toolbarPane = new AnimatedToolbarPane(8, 5);
        toolbarPane.addCluster(transportCluster);
        toolbarPane.addCluster(historyCluster);
        toolbarPane.addCluster(durationCluster);
        toolbarPane.addCluster(presetsCluster);
        toolbarPane.addCluster(propertyCluster);
        toolbarPane.addCluster(keyframesCluster);
        toolbarPane.addCluster(snapCluster);
        toolbarPane.addCluster(previewCluster);
        toolbarPane.addCluster(orbitCluster);
        toolbarPane.addCluster(audioCluster);
        toolbarPane.addCluster(registerCluster);
        toolbarPane.addCluster(exportCluster);
        toolbarPane.addCluster(diagnosticsCluster);
        toolbarPane.addCluster(helpCluster);
        toolbarPane.registerMarker("toolbar-group-transport-duration", transportCluster, historyCluster, durationCluster);
        toolbarPane.registerMarker("toolbar-group-keyframe-ops", propertyCluster, keyframesCluster);
        toolbarPane.registerMarker("toolbar-group-preview-modes", snapCluster, previewCluster);
        toolbarPane.registerMarker("toolbar-group-orbit-audio-register", orbitCluster, audioCluster, registerCluster);
        toolbarPane.registerMarker("toolbar-group-export-help", exportCluster, diagnosticsCluster, helpCluster);
        toolbarPane.setId("puppeteer-toolbar");
        toolbarPane.setPadding(TOOLBAR_PADDING_DYNAMIC);
        toolbarPane.setMinHeight(Region.USE_PREF_SIZE);
        toolbarPane.setMaxWidth(Double.MAX_VALUE);

        toolbarCommandBar = buildToolbarCommandBar();

        toolbarShell = new VBox(6, toolbarCommandBar, toolbarPane) {
            @Override
            protected double computeMinHeight(double width) {
                return 0.0;
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
        toolbarShell.setMinHeight(0);
        toolbarShell.setMaxWidth(Double.MAX_VALUE);
        toolbarDock = buildToolbarDock(toolbarShell);
        setToolbarClustersExpanded(true);
        setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.COMPACT);

        entitySelector.setMinWidth(0);

        Tab entitiesTab = new Tab("Entities", entitySelector);
        entitiesTab.setClosable(false);
        Tab selectionTab = buildSelectionTab();
        selectionTab.setClosable(false);
        Tab sceneTab = buildSceneTab();
        sceneTab.setClosable(false);
        anchorEditor = new AnchorEditor();
        anchorEditor.setProject(this.project);
        anchorEditor.setAnimationPreview(animationPreview);
        animationPreview.setOnAnchorPlacementAt(coords -> {
            if (coords == null || coords.length < 2) return;
            anchorEditor.startPendingPlacement(coords[0], coords[1]);
        });
        anchorEditor.setOnAnchorChanged(() -> {
            animationPreview.render();
            timelinePanel.refresh();
        });
        anchorEditor.setOnAnchorPlaced((entityName, anchor) -> {
            // Seed PIVOT_X/Y at t=0 when no pivot keyframes exist yet.
            // Leaf entities also get X/Y compensation to preserve visual placement.
            if (entityName == null || !anchor.isRelative()) return;
            EntityTrack track = resolveAnimatedTrack(entityName, true);
            if (track == null) return;
            if (!track.getKeyframes(PropertyType.PIVOT_X).isEmpty()
                    || !track.getKeyframes(PropertyType.PIVOT_Y).isEmpty()) return;
            if (project.getGroup(entityName) != null) {
                upsertGroupPivotPreservingPose(entityName, track, 0.0, anchor.getX(), anchor.getY());
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
                return;
            }
            com.jvn.core.scene2d.Entity2D ent = scene != null ? scene.find(entityName) : null;
            double newPX = anchor.getX(), newPY = anchor.getY();
            double oldPX = ent != null ? ent.getOriginX() : 0.5;
            double oldPY = ent != null ? ent.getOriginY() : 0.5;
            double baseX = baselinePropertyValue(entityName, ent, PropertyType.X);
            double baseY = baselinePropertyValue(entityName, ent, PropertyType.Y);
            double[] comp = pivotCompensation(ent, newPX, newPY, oldPX, oldPY);
            track.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(0.0, newPX));
            track.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(0.0, newPY));
            track.upsertKeyframe(PropertyType.X, new Keyframe(0.0, baseX + comp[0]));
            track.upsertKeyframe(PropertyType.Y, new Keyframe(0.0, baseY + comp[1]));
            timelinePanel.refresh();
            updatePreview();
        });
        anchorEditor.setOnAnchorUsedAsPivot((entityName, anchor) -> {
            // Insert PIVOT_X/Y at the current playhead time.
            // Leaf entities also get X/Y compensation to preserve visual placement.
            if (entityName == null || !anchor.isRelative()) return;
            EntityTrack track = resolveAnimatedTrack(entityName, true);
            if (track == null) return;
            double time = project.getPlayheadMs();
            double newPX = anchor.getX(), newPY = anchor.getY();
            if (project.getGroup(entityName) != null) {
                commandStack.execute(PuppeteerCommand.composite(
                    "Set group rotation pivot to anchor",
                    groupPivotPreservingCommands(entityName, track, time, newPX, newPY)
                ));
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
                return;
            }
            com.jvn.core.scene2d.Entity2D ent = scene != null ? scene.find(entityName) : null;
            double oldPX = ent != null ? ent.getOriginX() : 0.5;
            double oldPY = ent != null ? ent.getOriginY() : 0.5;
            double curX  = ent != null ? ent.getX() : baselinePropertyValue(entityName, ent, PropertyType.X);
            double curY  = ent != null ? ent.getY() : baselinePropertyValue(entityName, ent, PropertyType.Y);
            double[] comp = pivotCompensation(ent, newPX, newPY, oldPX, oldPY);
            commandStack.execute(PuppeteerCommand.composite(
                "Set rotation pivot to anchor",
                List.of(
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_X, time, newPX),
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_Y, time, newPY),
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.X, time, curX + comp[0]),
                    PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, time, curY + comp[1])
                )
            ));
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });
        Tab anchorsTab = new Tab("Anchors", anchorEditor);
        anchorsTab.setClosable(false);
        constraintEditor = new ConstraintEditor();
        constraintEditor.setProject(this.project);
        constraintEditor.setCurrentTimeSupplier(this.project::getPlayheadMs);
        constraintEditor.setOnConstraintChanged(() -> {
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });
        Tab rigTab = new Tab("Rig", constraintEditor);
        rigTab.setClosable(false);
        TabPane leftTabs = new TabPane(entitiesTab, selectionTab, sceneTab, anchorsTab, rigTab);
        leftTabs.getStyleClass().add("sidebar-tab-pane");
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
        leftTabsScrollPane.setMaxHeight(Double.MAX_VALUE);
        leftTabsScrollPane.setFitToWidth(true);
        leftTabsScrollPane.setFitToHeight(true);
        leftTabsScrollPane.setPannable(false);
        leftTabsScrollPane.setMinHeight(0);
        leftTabsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftTabsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftTabsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        keyframeEditor.setMinWidth(0);
        keyframeEditor.setMinHeight(0);
        keyframeEditor.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
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

        btnPreviewFullscreen = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.expand(), "Focus the preview in the editor workspace");
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

        btnPreviewBack = makeToolbarIconButton(com.jvn.editor.ui.CssIcon.arrowLeft(), "Return to the standard editor workspace");
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
        codePreview.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mainWorkspaceSplit.getItems().addAll(workspaceContentSplit, codePreview);
        SplitPane.setResizableWithParent(workspaceContentSplit, Boolean.TRUE);
        SplitPane.setResizableWithParent(codePreview, Boolean.TRUE);
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

        rootWorkspaceSplit = new SplitPane();
        rootWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        rootWorkspaceSplit.getStyleClass().add("puppeteer-root-layout-split");
        rootWorkspaceSplit.setOrientation(Orientation.VERTICAL);
        rootWorkspaceSplit.setMinWidth(0);
        rootWorkspaceSplit.setMinHeight(0);
        rootWorkspaceSplit.getItems().addAll(toolbarDock, workspaceModeHost);
        SplitPane.setResizableWithParent(toolbarDock, Boolean.FALSE);
        SplitPane.setResizableWithParent(workspaceModeHost, Boolean.TRUE);
        rootWorkspaceSplit.setDividerPositions(toolbarDividerPosition);

        HBox puppeteerStatusBar = buildPuppeteerStatusBar();
        updateStatusBar();

        BorderPane root = new BorderPane();
        root.setCenter(rootWorkspaceSplit);
        root.setBottom(puppeteerStatusBar);
        root.setStyle("-fx-background-color: #121212;");

        StackPane rootStack = new StackPane(root, overlayDialog);
        Scene fxScene = new Scene(rootStack);
        EditorTheme.apply(fxScene);
        setScene(fxScene);
        applyLinuxDefaultWindowState();

        setupKeyboardShortcuts(fxScene);
        setupPlaybackTimer();
        Platform.runLater(this::applyToolbarDivider);
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

    private HBox buildPuppeteerStatusBar() {
        statusBar = puppeteerStatusLabel("Ready", 300);
        statusPlaybackLabel = puppeteerStatusLabel("Stopped", 120);
        statusTimelineLabel = puppeteerStatusLabel("00:00.000 / 00:00.000", 160);
        statusSelectionLabel = puppeteerStatusLabel("No selection", 260);
        statusSceneLabel = puppeteerStatusLabel("0 tracks", 170);
        statusComplexityLabel = puppeteerStatusLabel("0 keys", 150);
        statusModeLabel = puppeteerStatusLabel("Snap 50ms", 220);
        statusSaveLabel = puppeteerStatusLabel("Saved", 120);
        statusViewportLabel = puppeteerStatusLabel("Viewport --", 130);
        statusProjectLabel = puppeteerStatusLabel("No project", 180);
        statusExportLabel = puppeteerStatusLabel("Code Pane", 180);

        HBox productSegment = puppeteerStatusSegment(CssIcon.movie("#d6d6d6"), statusBar, "jvn-status-segment-strong");
        statusPlaybackSegment = puppeteerStatusSegment(CssIcon.play("#d6d6d6"), statusPlaybackLabel);
        HBox timelineSegment = puppeteerStatusSegment(CssIcon.timeline("#d6d6d6"), statusTimelineLabel);
        HBox selectionSegment = puppeteerStatusSegment(CssIcon.rectSelect("#d6d6d6"), statusSelectionLabel);
        HBox sceneSegment = puppeteerStatusSegment(CssIcon.landscape("#d6d6d6"), statusSceneLabel);
        statusComplexitySegment = puppeteerStatusSegment(CssIcon.memory("#d6d6d6"), statusComplexityLabel);
        statusModeSegment = puppeteerStatusSegment(CssIcon.grid4x4("#d6d6d6"), statusModeLabel);
        statusSaveSegment = puppeteerStatusSegment(CssIcon.save("#d6d6d6"), statusSaveLabel);
        statusViewportSegment = puppeteerStatusSegment(CssIcon.openInFull("#d6d6d6"), statusViewportLabel);
        HBox projectSegment = puppeteerStatusSegment(CssIcon.folder("#d6d6d6"), statusProjectLabel);
        statusExportSegment = puppeteerStatusSegment(CssIcon.document("#d6d6d6"), statusExportLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox();
        bar.getStyleClass().addAll("jvn-status-bar", "puppeteer-status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(25);
        bar.setPrefHeight(25);
        bar.setPadding(new Insets(0, 8, 0, 8));
        bar.getChildren().addAll(
            productSegment,
            puppeteerStatusSeparator(),
            statusPlaybackSegment,
            puppeteerStatusSeparator(),
            timelineSegment,
            puppeteerStatusSeparator(),
            selectionSegment,
            puppeteerStatusSeparator(),
            sceneSegment,
            puppeteerStatusSeparator(),
            statusComplexitySegment,
            spacer,
            statusModeSegment,
            puppeteerStatusSeparator(),
            statusSaveSegment,
            puppeteerStatusSeparator(),
            statusViewportSegment,
            puppeteerStatusSeparator(),
            projectSegment,
            puppeteerStatusSeparator(),
            statusExportSegment
        );

        installPuppeteerStatusMenu(productSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Keyboard Shortcuts", this::showShortcutsOverlay),
            puppeteerStatusItem("Copy Exported Code", this::copyExportedCodeToClipboard),
            puppeteerStatusSeparatorItem(),
            puppeteerStatusItem(codePaneVisible ? "Hide Code Pane" : "Show Code Pane", () -> setCodePaneVisible(!isCodePaneVisible()))
        ));
        installPuppeteerStatusMenu(statusPlaybackSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem(project.isPlaying() ? "Pause" : "Play", () -> { if (project.isPlaying()) pause(); else play(); }),
            puppeteerStatusItem("Stop", this::stop),
            puppeteerStatusItem("Rewind", this::rewind),
            puppeteerStatusSeparatorItem(),
            puppeteerStatusItem(project.isLooping() ? "Disable Loop" : "Enable Loop", () -> {
                project.setLooping(!project.isLooping());
                if (cbLoop != null) cbLoop.setSelected(project.isLooping());
                refreshExportPreviewAndMarkDirty();
            })
        ));
        installPuppeteerStatusMenu(timelineSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Add Keyframe at Playhead", () -> {
                timelinePanel.addKeyframeAtPlayhead();
                refreshExportPreviewAndMarkDirty();
            }),
            puppeteerStatusItem("Previous Keyframe", () -> {
                if (timelinePanel.jumpPlayheadToPreviousKeyframe()) updateStatusBar();
            }),
            puppeteerStatusItem("Next Keyframe", () -> {
                if (timelinePanel.jumpPlayheadToNextKeyframe()) updateStatusBar();
            }),
            puppeteerStatusItem("Zoom to Selection", () -> timelinePanel.zoomToSelection())
        ));
        installPuppeteerStatusMenu(selectionSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Focus Preview Selection", () -> animationPreview.fitToContent()),
            puppeteerStatusItem("Clear Keyframe Selection", () -> {
                timelinePanel.clearKeyframeSelection();
                updateStatusBar();
                refreshSidebarTabs();
            }),
            puppeteerStatusItem("Copy Selected Keyframes", () -> {
                timelinePanel.copySelectedKeyframes();
                updateStatusBar();
            })
        ));
        installPuppeteerStatusMenu(sceneSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Import Assets", this::showAssetImporterWindow),
            puppeteerStatusItem("Fit Preview Viewport", () -> animationPreview.fitToContent()),
            puppeteerStatusItem("Manage Event Cues", () -> showEventCueManagerDialog(null)),
            puppeteerStatusItem("Add Audio Cue at Playhead", this::showAddAudioCueDialog)
        ));
        installPuppeteerStatusMenu(statusComplexitySegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Runtime Verification", this::showRuntimeVerificationReport),
            puppeteerStatusItem("Zoom Timeline to Fit", () -> timelinePanel.zoomToFit()),
            puppeteerStatusItem("Manage Event Cues", () -> showEventCueManagerDialog(null))
        ));
        installPuppeteerStatusMenu(statusModeSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem(timelinePanel.isSnapEnabled() ? "Disable Timeline Snap" : "Enable Timeline Snap", () -> {
                timelinePanel.setSnapEnabled(!timelinePanel.isSnapEnabled());
                if (cbSnap != null) cbSnap.setSelected(timelinePanel.isSnapEnabled());
                updateStatusBar();
            }),
            puppeteerStatusItem(runtimeParityPreview ? "Disable Runtime Preview" : "Enable Runtime Preview", () -> {
                runtimeParityPreview = !runtimeParityPreview;
                if (cbRuntimePreview != null) cbRuntimePreview.setSelected(runtimeParityPreview);
                updatePreview();
                updateStatusBar();
            }),
            puppeteerStatusItem(viewportStabilizationEnabled ? "Disable Viewport Stabilization" : "Enable Viewport Stabilization",
                () -> setViewportStabilizationEnabled(!viewportStabilizationEnabled))
        ));
        installPuppeteerStatusMenu(statusSaveSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Save & Register", this::requestRegisterTimeline),
            puppeteerStatusItem("Stage Code Preview", this::stagePreviewFromCode),
            puppeteerStatusItem("Commit Staged Preview", this::commitStagedPreview),
            puppeteerStatusItem("Discard Staged Preview", this::discardStagedPreview)
        ));
        installPuppeteerStatusMenu(statusViewportSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Fit Preview Viewport", () -> animationPreview.fitToContent()),
            puppeteerStatusItem(previewFocusMode ? "Exit Preview Focus" : "Focus Preview", () -> {
                if (previewFocusMode) exitFullscreenPreview();
                else enterFullscreenPreview();
            }),
            puppeteerStatusItem("Record GIF / Frames", this::showRecordGifDialog)
        ));
        installPuppeteerStatusMenu(projectSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Import Assets", this::showAssetImporterWindow),
            puppeteerStatusItem("Record GIF / Frames", this::showRecordGifDialog),
            puppeteerStatusItem("Fit Preview Viewport", () -> animationPreview.fitToContent())
        ));
        installPuppeteerStatusMenu(statusExportSegment, () -> puppeteerStatusMenu(
            puppeteerStatusItem("Copy Exported Code", this::copyExportedCodeToClipboard),
            puppeteerStatusItem("Refresh Code Preview", this::refreshExportPreview),
            puppeteerStatusItem(codePaneVisible ? "Hide Code Pane" : "Show Code Pane", () -> setCodePaneVisible(!isCodePaneVisible()))
        ));
        return bar;
    }

    private static Label puppeteerStatusLabel(String text, double maxWidth) {
        Label label = new Label(text);
        label.getStyleClass().add("jvn-status-label");
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMinWidth(0);
        label.setMaxWidth(maxWidth);
        return label;
    }

    private static HBox puppeteerStatusSegment(Node icon, Label label, String... extraStyleClasses) {
        HBox segment = new HBox(5);
        segment.getStyleClass().add("jvn-status-segment");
        if (extraStyleClasses != null) {
            segment.getStyleClass().addAll(extraStyleClasses);
        }
        segment.setAlignment(Pos.CENTER_LEFT);
        segment.setMinWidth(0);
        if (icon != null) segment.getChildren().add(icon);
        segment.getChildren().add(label);
        return segment;
    }

    private static Region puppeteerStatusSeparator() {
        Region separator = new Region();
        separator.getStyleClass().add("jvn-status-separator");
        separator.setMinWidth(1);
        separator.setPrefWidth(1);
        separator.setMaxWidth(1);
        return separator;
    }

    private static void installPuppeteerStatusMenu(Node node, java.util.function.Supplier<ContextMenu> menuSupplier) {
        node.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY && event.getButton() != MouseButton.SECONDARY) return;
            ContextMenu menu = menuSupplier.get();
            if (menu == null || menu.getItems().isEmpty()) return;
            menu.show(node, Side.TOP, 0, 0);
            event.consume();
        });
    }

    private static ContextMenu puppeteerStatusMenu(MenuItem... items) {
        ContextMenu menu = new ContextMenu();
        if (items != null) {
            for (MenuItem item : items) {
                if (item != null) menu.getItems().add(item);
            }
        }
        return menu;
    }

    private static MenuItem puppeteerStatusItem(String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.setDisable(action == null);
        if (action != null) item.setOnAction(e -> action.run());
        return item;
    }

    private static MenuItem puppeteerStatusSeparatorItem() {
        return new SeparatorMenuItem();
    }

    private BorderPane buildToolbarDock(VBox content) {
        Region gripLine = new Region();
        gripLine.getStyleClass().add("puppeteer-toolbar-grip-line");
        HBox.setHgrow(gripLine, Priority.ALWAYS);

        Label title = new Label("Puppeteer Toolbar");
        title.getStyleClass().add("puppeteer-toolbar-grip-label");

        btnTopToolbarVisibility = makeToolbarIconButton(
            com.jvn.editor.ui.CssIcon.arrowUp("#b0b8c8"), "Hide top toolbar");
        btnTopToolbarVisibility.getStyleClass().add("puppeteer-toolbar-dock-toggle");
        btnTopToolbarVisibility.setMinSize(30, 24);
        btnTopToolbarVisibility.setPrefSize(30, 24);
        btnTopToolbarVisibility.setMaxSize(30, 24);
        btnTopToolbarVisibility.setOnAction(e -> setTopToolbarVisible(!topToolbarVisible));

        HBox gripBar = new HBox(8, title, gripLine, btnTopToolbarVisibility);
        gripBar.getStyleClass().add("puppeteer-toolbar-grip-bar");
        gripBar.setAlignment(Pos.CENTER_LEFT);
        gripBar.setMinHeight(28);
        gripBar.setPrefHeight(28);
        gripBar.addEventFilter(MouseEvent.MOUSE_PRESSED, this::beginToolbarDrag);
        gripBar.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::dragToolbar);

        BorderPane dock = new BorderPane(content) {
            @Override
            protected double computeMinHeight(double width) {
                return gripBar.prefHeight(width);
            }
        };
        dock.getStyleClass().add("puppeteer-toolbar-dock");
        dock.setTop(gripBar);
        dock.setMinHeight(0);
        dock.setMaxWidth(Double.MAX_VALUE);
        installLayoutClip(dock);
        updateTopToolbarChrome();
        return dock;
    }

    private HBox buildToolbarCommandBar() {
        MenuItem miSaveRegister = new MenuItem("Save & Register");
        miSaveRegister.setOnAction(e -> requestRegisterTimeline());
        MenuItem miVerifyRuntime = new MenuItem("Verify Runtime Registration...");
        miVerifyRuntime.setOnAction(e -> showRuntimeVerificationReport());
        MenuItem miRefreshCode = new MenuItem("Refresh Generated Code");
        miRefreshCode.setOnAction(e -> requestRefreshGeneratedCode());
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
        MenuItem miRecordGif = new MenuItem("Record Preview as GIF...");
        miRecordGif.setOnAction(e -> showRecordGifDialog());
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
            miRecordGif,
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

        // === Timeline menu ===
        MenuItem miJumpStart = new MenuItem("Jump to Start");
        miJumpStart.setAccelerator(new KeyCodeCombination(KeyCode.HOME));
        miJumpStart.setOnAction(e -> rewind());

        MenuItem miJumpEnd = new MenuItem("Jump to End");
        miJumpEnd.setAccelerator(new KeyCodeCombination(KeyCode.END));
        miJumpEnd.setOnAction(e -> {
            if (project == null) return;
            project.setPlayheadMs(project.getTotalDurationMs());
            updateTimeLabel();
            updatePreview();
            timelinePanel.refresh();
        });

        MenuItem miTimelinePrevKey = new MenuItem("Previous Keyframe");
        miTimelinePrevKey.setAccelerator(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHORTCUT_DOWN));
        miTimelinePrevKey.setOnAction(e -> {
            if (timelinePanel.jumpPlayheadToPreviousKeyframe()) { updateTimeLabel(); updatePreview(); }
            refreshSidebarTabs();
        });

        MenuItem miTimelineNextKey = new MenuItem("Next Keyframe");
        miTimelineNextKey.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHORTCUT_DOWN));
        miTimelineNextKey.setOnAction(e -> {
            if (timelinePanel.jumpPlayheadToNextKeyframe()) { updateTimeLabel(); updatePreview(); }
            refreshSidebarTabs();
        });

        MenuItem miTimelineFocusSel = new MenuItem("Focus Timeline on Selection");
        miTimelineFocusSel.setOnAction(e -> timelinePanel.zoomToSelection());
        MenuItem miTimelineZoomFit = new MenuItem("Zoom Timeline to Fit");
        miTimelineZoomFit.setOnAction(e -> timelinePanel.zoomToFit());

        Menu timelineMenu = new Menu("Timeline");
        timelineMenu.getItems().addAll(
            miJumpStart, miJumpEnd,
            new SeparatorMenuItem(),
            miTimelinePrevKey, miTimelineNextKey,
            new SeparatorMenuItem(),
            miTimelineFocusSel, miTimelineZoomFit
        );
        timelineMenu.setOnShowing(e -> {
            boolean hasTarget = timelinePanel != null && timelinePanel.getSelectedEntity() != null
                && !timelinePanel.getSelectedEntity().isBlank();
            boolean hasSelection = timelinePanel != null && timelinePanel.getSelectionCount() > 0;
            miTimelinePrevKey.setDisable(!hasTarget);
            miTimelineNextKey.setDisable(!hasTarget);
            miTimelineFocusSel.setDisable(!hasSelection);
        });

        // === Scene menu ===
        MenuItem miSceneImport = new MenuItem("Import Assets...");
        miSceneImport.setOnAction(e -> showAssetImporterWindow());

        MenuItem miSceneAddCue = new MenuItem("Add Audio Cue at Playhead");
        miSceneAddCue.setOnAction(e -> showAddAudioCueDialog());

        MenuItem miSceneClearCues = new MenuItem("Clear All Audio Cues");
        miSceneClearCues.setOnAction(e -> {
            if (project == null || project.getAudioCues().isEmpty()) return;
            overlayDialog.showDialog("Clear Audio Cues",
                "Remove all audio cues from this animation? This cannot be undone from the cue panel.",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    project.clearAudioCues();
                    timelinePanel.refresh();
                    refreshExportPreviewAndMarkDirty();
                }));
        });

        MenuItem miSceneManageEvents = new MenuItem("Manage Event Cues...");
        miSceneManageEvents.setOnAction(e -> showEventCueManagerDialog(null));

        MenuItem miSceneClearEvents = new MenuItem("Clear All Event Cues");
        miSceneClearEvents.setOnAction(e -> {
            if (project == null || project.getEditorEventCues().isEmpty()) return;
            overlayDialog.showDialog("Clear Event Cues",
                "Remove all timeline event cues from this animation?",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    project.clearEditorEventCues();
                    timelinePanel.refresh();
                    updatePreview();
                    refreshExportPreviewAndMarkDirty();
                }));
        });

        MenuItem miSceneFitPreview = new MenuItem("Fit Preview Viewport");
        miSceneFitPreview.setOnAction(e -> animationPreview.fitToContent());

        Menu sceneMenu = new Menu("Scene");
        sceneMenu.getItems().addAll(
            miSceneImport,
            new SeparatorMenuItem(),
            miSceneAddCue, miSceneClearCues,
            new SeparatorMenuItem(),
            miSceneManageEvents, miSceneClearEvents,
            new SeparatorMenuItem(),
            miSceneFitPreview
        );
        sceneMenu.setOnShowing(e -> {
            boolean hasRoot = projectRoot != null && projectRoot.isDirectory();
            miSceneImport.setDisable(!hasRoot);
            miSceneClearCues.setDisable(project == null || project.getAudioCues().isEmpty());
            miSceneClearEvents.setDisable(project == null || project.getEditorEventCues().isEmpty());
        });

        CheckMenuItem miShowToolbar = new CheckMenuItem("Show Top Toolbar");
        miShowToolbar.setOnAction(e -> setTopToolbarVisible(miShowToolbar.isSelected()));
        CheckMenuItem miShowCodePane = new CheckMenuItem("Show Code Pane");
        miShowCodePane.setOnAction(e -> setCodePaneVisible(miShowCodePane.isSelected()));
        CheckMenuItem miOnionSkin = new CheckMenuItem("Onion Skin Preview");
        miOnionSkin.setOnAction(e -> animationPreview.setOnionSkinning(miOnionSkin.isSelected()));
        CheckMenuItem miInterpolationGhosts = new CheckMenuItem("Interpolation Ghosts");
        miInterpolationGhosts.setOnAction(e -> animationPreview.setShowInterpolationGhosts(miInterpolationGhosts.isSelected()));
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
            miShowToolbar,
            miShowCodePane,
            miOnionSkin,
            miInterpolationGhosts,
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
            miShowToolbar.setSelected(isTopToolbarVisible());
            miShowCodePane.setSelected(codePaneVisible);
            miOnionSkin.setSelected(animationPreview.isOnionSkinning());
            miInterpolationGhosts.setSelected(animationPreview.isShowInterpolationGhosts());
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
        CheckMenuItem miViewportStabilization = new CheckMenuItem("Stabilize Preview Viewport");
        miViewportStabilization.setOnAction(e -> setViewportStabilizationEnabled(miViewportStabilization.isSelected()));

        Menu playbackMenu = new Menu("Playback");
        playbackMenu.getItems().addAll(
            miPlayPause,
            miStop,
            miRewind,
            miViewportStabilization,
            new SeparatorMenuItem(),
            miLoopPlayback,
            miLoopIn,
            miLoopOut,
            miLoopClear
        );
        playbackMenu.setOnShowing(e -> {
            miPlayPause.setText(project.isPlaying() ? "Pause" : "Play");
            miViewportStabilization.setSelected(viewportStabilizationEnabled);
            miLoopPlayback.setSelected(project.isLooping());
            miLoopClear.setDisable(!project.hasLoopRegion());
        });

        MenuItem miShowShortcuts = new MenuItem("Keyboard Shortcuts");
        miShowShortcuts.setOnAction(e -> showShortcutsOverlay());
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().add(miShowShortcuts);

        MenuBar menuBar = new MenuBar(fileMenu, editMenu, timelineMenu, viewMenu, playbackMenu, sceneMenu, helpMenu);
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

    void refreshToolbarCommandSummary() {
        refreshUndoRedoControls();
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

    void refreshUndoRedoControls() {
        if (btnUndo != null) {
            btnUndo.setDisable(!commandStack.canUndo());
            String undoText = commandStack.canUndo()
                ? "Undo " + commandStack.undoDescription() + " (Ctrl/Cmd+Z)"
                : "Undo (Ctrl/Cmd+Z)";
            installToolbarTooltip(btnUndo, undoText);
        }
        if (btnRedo != null) {
            btnRedo.setDisable(!commandStack.canRedo());
            String redoText = commandStack.canRedo()
                ? "Redo " + commandStack.redoDescription() + " (Ctrl/Cmd+Shift+Z)"
                : "Redo (Ctrl/Cmd+Shift+Z)";
            installToolbarTooltip(btnRedo, redoText);
        }
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
        }, "Add a keyframe at the current playhead for the selected entity");
        btnSidebarFocusSelection = buildSidebarActionButton("Focus Timeline", () -> {
            timelinePanel.zoomToSelection();
            refreshSidebarTabs();
        }, "Zoom the timeline to show the selected keyframes");
        Button btnPrevKey = buildSidebarActionButton("Prev Key", () -> {
            if (timelinePanel.jumpPlayheadToPreviousKeyframe()) {
                updateTimeLabel();
                updatePreview();
            }
            refreshSidebarTabs();
        }, "Jump to the previous keyframe");
        Button btnNextKey = buildSidebarActionButton("Next Key", () -> {
            if (timelinePanel.jumpPlayheadToNextKeyframe()) {
                updateTimeLabel();
                updatePreview();
            }
            refreshSidebarTabs();
        }, "Jump to the next keyframe");
        btnSidebarClearSelection = buildSidebarActionButton("Clear", () -> {
            entitySelector.selectEntity(null);
            animationPreview.clearSelection();
            timelinePanel.setSelectedTarget(null, false);
            keyframeEditor.setSelectionContext(null, false, false);
            keyframeEditor.setKeyframe(null, null);
            refreshSidebarTabs();
        }, "Clear the current selection in the timeline and viewport");

        HBox selectionActionsPrimary = buildSidebarButtonRow(btnSidebarAddKeyframe, btnSidebarFocusSelection);
        HBox selectionActionsSecondary = buildSidebarButtonRow(btnPrevKey, btnNextKey, btnSidebarClearSelection);

        sidebarAdvancedUnavailableCard = buildSidebarCard(
            "Advanced",
            "The Matrix / Effects, Color Matrix, Camera DOF, and Custom Channels inspectors are available when an entity track or the runtime camera track is selected.\n\nSelect an entity or the camera in the timeline, then return to this tab to access the advanced controls.",
            createSidebarHintLabel("Advanced transform and custom-channel authoring is available for entity and runtime camera tracks."));
        sidebarMatrixCard = buildSidebarCard("Matrix / Effects",
            "Raw 2D transform coefficients for advanced per-keyframe control.\n\n" +
            "• MXX / MXY — first column of the 2×2 linear transform (scale X and horizontal shear)\n" +
            "• MYX / MYY — second column (vertical shear and scale Y)\n" +
            "• TX / TY — translation offsets applied after the linear transform\n" +
            "• Blur — Gaussian blur radius in scene units (0 = sharp)\n\n" +
            "• Brightness — RGB exposure multiplier (1 = neutral)\n\n" +
            "Identity: MXX=1, MXY=0, MYX=0, MYY=1, TX=0, TY=0, Blur=0, Brightness=1.\n" +
            "\"Fill Identity\" resets all fields. \"Key At Playhead\" writes all values as a single keyframe.",
            buildSidebarMatrixInspector());
        sidebarColorMatrixCard = buildSidebarCard("Color Matrix",
            "A 4×5 RGBA color transform matrix applied per-pixel to the entity.\n\n" +
            "Each row targets an output channel (R, G, B, A). Each row of 5 values computes:\n" +
            "  out = m_0·R + m_1·G + m_2·B + m_3·A + m_4\n\n" +
            "The fifth column (m_4) is a constant offset added to the channel (0–1 range).\n\n" +
            "Identity: diagonal = 1, all other values = 0.\n" +
            "\"Fill Identity\" resets to no color transformation. \"Key At Playhead\" writes all 20 values.",
            buildSidebarColorMatrixInspector());
        sidebarCameraDofCard = buildSidebarCard("Camera DOF",
            "Depth of Field applies a focus-blur effect to the scene camera.\n\n" +
            "• Focus — Z depth of the focal plane; entities at this depth appear sharp\n" +
            "• Strength — defocus rate for entities moving away from the focal plane\n" +
            "• Max Blur — upper limit on the blur radius applied to out-of-focus entities\n\n" +
            "Set Strength to 0 to disable DOF. \"Fill Neutral\" resets all values to no-blur defaults.",
            buildSidebarCameraDofInspector());
        sidebarCustomChannelsCard = buildSidebarCard("Custom Channels",
            "Key arbitrary numeric properties beyond the standard property types.\n\n" +
            "• Enter a dot-separated key (e.g. my.effect.intensity) to create a freeform channel.\n" +
            "• Known engine keys (color.m00, dof.focus, etc.) appear in the dropdown for quick access.\n" +
            "• Dedicated matrix and color matrix keys are mirrored by the inspectors above.\n\n" +
            "Values are keyed at the current playhead using the selected easing and interpolation.",
            buildSidebarCustomChannelInspector());

        ScrollPane content = buildSidebarTabContent(
            buildSidebarCard(
                "Selection",
                "Shows the current editing context in the timeline.\n\n" +
                "• Target — the entity or group whose track is active\n" +
                "• Scope — whether the full entity or a single property track is selected\n" +
                "• Property — the currently active property type (X, Rotation, Alpha, etc.)\n" +
                "• Playhead — current time position in milliseconds\n" +
                "• Selected Keyframes — count of highlighted keyframes in the timeline",
                buildSidebarInfoBlock("Target", lblSidebarSelectionTarget),
                buildSidebarInfoBlock("Scope", lblSidebarSelectionScope),
                buildSidebarInfoBlock("Property", lblSidebarSelectionProperty),
                buildSidebarInfoBlock("Playhead", lblSidebarSelectionPlayhead),
                buildSidebarInfoBlock("Selected Keyframes", lblSidebarSelectionCount)
            ),
            buildSidebarCard(
                "Actions",
                "Keyframe actions for the current selection.\n\n" +
                "• Add Keyframe — inserts a keyframe at the playhead for the selected entity and property\n" +
                "• Focus Timeline — zooms the timeline to fit all selected keyframes on screen\n" +
                "• Prev / Next Key — moves the playhead to the nearest keyframe on either side\n" +
                "• Clear — deselects all entities and keyframes",
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
        }, "Fit the preview viewport to show all scene content");
        btnSidebarPreviewLayout = buildSidebarActionButton("Focus Preview", this::togglePreviewFocusMode, "Toggle between workspace and focused preview layout");
        btnSidebarCodePane = buildSidebarActionButton("Hide Code Pane", () -> {
            setCodePaneVisible(!isCodePaneVisible());
            refreshSidebarTabs();
        }, "Toggle visibility of the VNS code preview pane");
        Button btnRefreshCode = buildSidebarActionButton("Refresh Code", this::refreshExportPreview, "Regenerate the VNS code from the current timeline");

        HBox previewActions = buildSidebarButtonRow(btnFitPreview, btnSidebarPreviewLayout);
        HBox workspaceActions = buildSidebarButtonRow(btnSidebarCodePane, btnRefreshCode);

        ScrollPane content = buildSidebarTabContent(
            buildSidebarCard(
                "Project",
                "Overview of the current animation project.\n\n" +
                "• Tracks — total entity tracks in the timeline\n" +
                "• Groups — entity groups for coordinated multi-track animation\n" +
                "• Duration — total timeline length in milliseconds\n" +
                "• Orbit Anchors — named pivot points used by the orbit animation tool\n" +
                "• Lighting Stage — the VN stage preset loaded for lighting reference",
                buildSidebarInfoBlock("Tracks", lblSidebarSceneTracks),
                buildSidebarInfoBlock("Groups", lblSidebarSceneGroups),
                buildSidebarInfoBlock("Duration", lblSidebarSceneDuration),
                buildSidebarInfoBlock("Orbit Anchors", lblSidebarSceneAnchors),
                buildSidebarInfoBlock("Lighting Stage", lblSidebarSceneStage)
            ),
            buildSidebarCard(
                "Preview",
                "Live preview viewport state and layout controls.\n\n" +
                "• Viewport — current canvas resolution and pan/zoom offset\n" +
                "• Camera — whether a runtime camera track is active\n" +
                "• Code Pane — whether the JES export panel is currently shown\n\n" +
                "\"Fit Preview\" resets zoom to show all scene content.\n" +
                "\"Focus Preview\" switches to a full-screen preview layout, hiding the timeline.\n" +
                "\"Hide/Show Code Pane\" toggles the right-side JES output panel.\n" +
                "\"Refresh Code\" regenerates JES from the current timeline state.",
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
        String[] captions = {"MXX", "MXY", "MYX", "MYY", "TX", "TY", "Blur", "Brightness"};
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
            grid.add(label, col, row);
            grid.add(field, col + 1, row);
        }

        Button btnFillIdentity = buildSidebarActionButton("Fill Identity", this::fillSidebarMatrixIdentity, "Reset the matrix to identity (no transformation)");
        Button btnKeyMatrix = buildSidebarActionButton("Key At Playhead", this::applySidebarMatrixKeyframes, "Key the current matrix values at the playhead");
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
        Button btnFillIdentity = buildSidebarActionButton("Fill Identity", this::fillSidebarColorMatrixIdentity, "Reset the color matrix to identity (no color transformation)");
        Button btnKeyColors = buildSidebarActionButton("Key At Playhead", this::applySidebarColorMatrixKeyframes, "Key the current color matrix values at the playhead");
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
        Button btnReset = buildSidebarActionButton("Fill Neutral", this::fillSidebarDofNeutral, "Reset DOF settings to neutral (no blur)");
        Button btnKey = buildSidebarActionButton("Key At Playhead", this::applySidebarDofKeyframes, "Key the current DOF values at the playhead");
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
        Button btnKey = buildSidebarActionButton("Key At Playhead", this::applySidebarCustomPropertyKeyframe, "Key the custom property value at the playhead");
        Button btnRemove = buildSidebarActionButton("Remove Key", this::removeSidebarCustomPropertyKeyframe, "Remove the keyframe for this custom property at the playhead");
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

    private VBox buildSidebarCard(String title, String helpText, Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(STYLE_SIDEBAR_CARD_TITLE);
        HBox titleRow = new HBox(4, titleLabel, SidebarToolHelp.button(this, title, helpText));
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8);
        card.setStyle(STYLE_SIDEBAR_CARD);
        card.setMinWidth(0);
        card.getChildren().add(titleRow);
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
        return buildSidebarActionButton(text, action, null);
    }

    private Button buildSidebarActionButton(String text, Runnable action, String tooltip) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(STYLE_BTN_DARK);
        if (tooltip != null && !tooltip.isBlank()) {
            button.setTooltip(new Tooltip(tooltip));
        }
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

    public void refreshSidebarTabs() {
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
        if (constraintEditor != null) {
            constraintEditor.selectEntity(hasTarget && !selectedGroup && !runtimeCamera ? selectedName : null);
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
        double[] values = {1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0};
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
        executeSidebarCommand("Key matrix/effect properties", commands);
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

    public void setCodePaneVisible(boolean visible) {
        codePaneVisible = visible;
        if (codePreview != null) {
            codePreview.setManaged(visible);
            codePreview.setVisible(visible);
        }
        Node codePaneNode = codePreviewSplitNode();
        if (mainWorkspaceSplit == null) {
            return;
        }
        if (visible) {
            if (!mainWorkspaceSplit.getItems().contains(codePaneNode)) {
                mainWorkspaceSplit.getItems().add(codePaneNode);
                SplitPane.setResizableWithParent(codePaneNode, Boolean.TRUE);
            }
            mainWorkspaceSplit.setDividerPositions(codePaneDividerPosition);
            refreshSidebarTabs();
            refreshToolbarCommandSummary();
            updateStatusBar();
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
        updateStatusBar();
    }

    private Node codePreviewSplitNode() {
        return codePreview;
    }

    public boolean isCodePaneVisible() {
        return codePaneVisible;
    }

    public void setTopToolbarVisible(boolean visible) {
        if (!visible) {
            double current = readDividerPosition(rootWorkspaceSplit, toolbarDividerPosition);
            if (current > TOOLBAR_MIN_VISIBLE_DIVIDER_POSITION) {
                toolbarDividerPosition = clampToolbarDivider(current, true);
            }
        }
        topToolbarVisible = visible;
        if (toolbarShell != null) {
            toolbarShell.setManaged(visible);
            toolbarShell.setVisible(visible);
        }
        updateTopToolbarChrome();
        applyToolbarDivider();
    }

    public boolean isTopToolbarVisible() {
        return topToolbarVisible;
    }

    private void beginToolbarDrag(MouseEvent event) {
        if (event == null || event.getButton() != MouseButton.PRIMARY) return;
        toolbarDragStartSceneY = event.getSceneY();
        toolbarDragStartDivider = readDividerPosition(rootWorkspaceSplit,
            topToolbarVisible ? toolbarDividerPosition : TOOLBAR_COLLAPSED_DIVIDER_POSITION);
    }

    private void dragToolbar(MouseEvent event) {
        if (event == null || !event.isPrimaryButtonDown()) return;
        if (rootWorkspaceSplit == null || rootWorkspaceSplit.getDividers().isEmpty()) return;
        double height = rootWorkspaceSplit.getHeight();
        if (!Double.isFinite(height) || height <= 1.0) return;
        double next = toolbarDragStartDivider + ((event.getSceneY() - toolbarDragStartSceneY) / height);
        if (!topToolbarVisible && next > TOOLBAR_MIN_VISIBLE_DIVIDER_POSITION) {
            setTopToolbarVisible(true);
        }
        setToolbarDividerPosition(next, true);
        event.consume();
    }

    private void applyToolbarDivider() {
        if (rootWorkspaceSplit == null || rootWorkspaceSplit.getDividers().isEmpty()) return;
        double target = topToolbarVisible
            ? clampToolbarDivider(toolbarDividerPosition, true)
            : TOOLBAR_COLLAPSED_DIVIDER_POSITION;
        rootWorkspaceSplit.setDividerPositions(target);
    }

    private void setToolbarDividerPosition(double dividerPosition, boolean remember) {
        if (rootWorkspaceSplit == null || rootWorkspaceSplit.getDividers().isEmpty()) return;
        double target = clampToolbarDivider(dividerPosition, topToolbarVisible);
        rootWorkspaceSplit.setDividerPositions(target);
        if (topToolbarVisible && remember) {
            toolbarDividerPosition = target;
        }
    }

    private void updateTopToolbarChrome() {
        if (toolbarDock != null) {
            toolbarDock.getStyleClass().remove("collapsed");
            if (!topToolbarVisible) {
                toolbarDock.getStyleClass().add("collapsed");
            }
        }
        if (btnTopToolbarVisibility != null) {
            btnTopToolbarVisibility.setGraphic(topToolbarVisible
                ? com.jvn.editor.ui.CssIcon.arrowUp("#b0b8c8")
                : com.jvn.editor.ui.CssIcon.arrowDown("#b0b8c8"));
            Tooltip tooltip = btnTopToolbarVisibility.getTooltip();
            if (tooltip != null) {
                tooltip.setText(topToolbarVisible ? "Hide top toolbar" : "Show top toolbar");
            }
        }
    }

    private static double clampToolbarDivider(double value, boolean visible) {
        double min = visible ? TOOLBAR_MIN_VISIBLE_DIVIDER_POSITION : TOOLBAR_COLLAPSED_DIVIDER_POSITION;
        if (!Double.isFinite(value)) return visible ? 0.16 : TOOLBAR_COLLAPSED_DIVIDER_POSITION;
        return Math.max(min, Math.min(TOOLBAR_MAX_DIVIDER_POSITION, value));
    }

    private static void installLayoutClip(Region region) {
        if (region == null) return;
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
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
            applyLaunchScenePresetGrouping();
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
        updateStatusBar();
    }

    public void setLaunchSceneSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
        this.launchSceneSnapshot = snapshot;
        launchCharacterImagePaths.clear();
        launchCharacterPresetLayers.clear();
        launchBackgroundPaths.clear();
        if (snapshot != null) {
            if (snapshot.characterImagePaths != null) {
                launchCharacterImagePaths.putAll(snapshot.characterImagePaths);
            }
            if (snapshot.characterPresetLayers != null) {
                launchCharacterPresetLayers.putAll(snapshot.characterPresetLayers);
            }
            if (snapshot.backgroundPaths != null) {
                launchBackgroundPaths.putAll(snapshot.backgroundPaths);
            }
            applyLaunchStageContext(snapshot);
            applyLaunchScenePresetGrouping();
        }
    }

    public void setRuntimeExportBaselines(Map<String, Map<PropertyType, Double>> baselines) {
        runtimeExportBaselines.clear();
        if (baselines == null || baselines.isEmpty()) return;
        for (Map.Entry<String, Map<PropertyType, Double>> entry : baselines.entrySet()) {
            String entityName = entry.getKey();
            Map<PropertyType, Double> props = entry.getValue();
            if (entityName == null || entityName.isBlank() || props == null || props.isEmpty()) continue;
            Map<PropertyType, Double> copy = new EnumMap<>(PropertyType.class);
            for (Map.Entry<PropertyType, Double> prop : props.entrySet()) {
                if (prop.getKey() == null || prop.getValue() == null || !Double.isFinite(prop.getValue())) continue;
                copy.put(prop.getKey(), prop.getValue());
            }
            if (!copy.isEmpty()) {
                runtimeExportBaselines.put(entityName.trim(), copy);
            }
        }
    }

    private void applyLaunchScenePresetGrouping() {
        if (launchSceneSnapshot == null || scene == null || project == null) return;
        boolean changed = false;
        
        Map<String, String> orbitSources = new java.util.LinkedHashMap<>(project.getOrbitAnchorSourcesView());
        Map<String, double[]> orbitOffsets = new java.util.LinkedHashMap<>(project.getOrbitAnchorSourceOffsetsView());
        
        for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
            if (character == null) continue;
            List<PuppeteerLauncherPanel.CharacterLayerEntry> layers =
                launchSceneSnapshot.resolveCharacterLayers(character.characterId, character.expression);
            if (layers == null || layers.isEmpty()) continue;
            String groupName = snapshotCharacterGroupName(character);
            EntityGroup group = project.getOrCreateGroup(groupName);
            int layerIndex = 0;
            int groupLayer = Integer.MAX_VALUE;
            String baseEntityName = null;
            for (PuppeteerLauncherPanel.CharacterLayerEntry layer : layers) {
                if (layer == null) continue;
                List<String> entityNames = findSnapshotLayerEntityNames(character, layer.layerId);
                if (entityNames.isEmpty()) continue;
                String primaryEntityName = entityNames.get(0);

                boolean baseLayer = baseEntityName == null;
                if (baseEntityName == null) {
                    baseEntityName = primaryEntityName;
                }

                for (String entityName : entityNames) {
                    if (entityName == null || entityName.isBlank()) continue;
                    if (!baseLayer && !entityName.equals(baseEntityName)) {
                        orbitSources.put(entityName, baseEntityName);
                        com.jvn.core.scene2d.Entity2D baseEntity = scene.find(baseEntityName);
                        com.jvn.core.scene2d.Entity2D childEntity = scene.find(entityName);
                        if (baseEntity != null && childEntity != null) {
                            orbitOffsets.put(entityName, new double[]{childEntity.getX() - baseEntity.getX(), childEntity.getY() - baseEntity.getY()});
                        }
                    }

                    EntityTrack track = project.getTrack(entityName);
                    if (track == null) continue;
                    track.setLayerOrder(layerIndex);
                    project.addEntityToGroup(entityName, groupName);
                    groupLayer = Math.min(groupLayer, layerIndex);
                }
                layerIndex++;
                changed = true;
            }
            if (groupLayer != Integer.MAX_VALUE) {
                group.setLayerOrder(groupLayer);
            }
        }
        if (changed) {
            project.setOrbitAnchorSources(orbitSources);
            project.setOrbitAnchorSourceOffsets(orbitOffsets);
            entitySelector.refresh(project);
            timelinePanel.refresh();
            refreshPropertyPickerChoices();
            refreshExportPreview();
        }
    }

    private List<String> findSnapshotLayerEntityNames(PuppeteerLauncherPanel.CharacterEntry character, String layerId) {
        if (character == null || layerId == null || layerId.isBlank()) return List.of();
        List<String> names = new java.util.ArrayList<>();
        for (String candidate : PuppeteerLauncherPanel.equivalentSnapshotLayerEntityNames(launchSceneSnapshot, character, layerId)) {
            String entityName = findSnapshotLayerEntityName(candidate);
            if (entityName != null && !entityName.isBlank() && !names.contains(entityName)) {
                names.add(entityName);
            }
        }
        return names;
    }

    private String findSnapshotLayerEntityName(String groupName, String layerId) {
        String expected = groupName + "_" + selectorSafeName(layerId);
        return findSnapshotLayerEntityName(expected);
    }

    private String findSnapshotLayerEntityName(String expected) {
        if (expected == null || expected.isBlank()) return null;
        if (scene.find(expected) != null) return expected;
        for (String name : scene.names()) {
            if (name != null && name.startsWith(expected + "_")) return name;
        }
        return null;
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

    public java.io.File projectRoot;
    private java.io.File scriptTargetFile;
    private PuppeteerLauncherPanel.SceneSnapshot launchSceneSnapshot;
    private final Map<String, Map<PropertyType, Double>> runtimeExportBaselines = new LinkedHashMap<>();
    private PuppeteerWorkspacePrefs workspacePrefs;
    private PuppeteerDraftStore draftStore;
    private PuppeteerPreviewRecorder previewRecorder;
    private boolean draftRestorePromptShown;

    public void setProjectRoot(java.io.File root) {
        this.projectRoot = root;
        animationPreview.setProjectRoot(root);
        if (assetImporterPanel != null) {
            assetImporterPanel.setProjectRoot(root);
        }
        keyframeEditor.setProjectRoot(root);
        codePreview.setProjectRoot(root);
        anchorEditor.setProjectRoot(root);
        updateViewportInfoLabel();
        refreshSidebarTabs();
        installWorkspaceServicesForProjectRoot();
        updateStatusBar();
    }

    private void installWorkspaceServicesForProjectRoot() {
        // Replace any previously bound services if the project root changed.
        if (draftStore != null) {
            draftStore.shutdown();
            draftStore = null;
        }
        previewRecorder = null;
        workspacePrefs = null;
        draftRestorePromptShown = false;
        if (projectRoot == null) return;

        workspacePrefs = PuppeteerWorkspacePrefs.load(projectRoot);
        applyWorkspacePrefs();
        PuppeteerAnchorStore.load(projectRoot, project);
        project.setEyeFocusProfiles(VnEyeFocusProfileStore.load(projectRoot));
        PuppeteerRigStore.load(projectRoot, project);

        draftStore = new PuppeteerDraftStore(projectRoot);
        draftStore.setOnSaveCallback(timelineName -> Platform.runLater(() -> showAutoSaveIndicator(timelineName)));
        previewRecorder = new PuppeteerPreviewRecorder(animationPreview.getPreviewCanvas());

        // Run on the next tick so the timeline name field has the resolved value.
        Platform.runLater(this::promptDraftRestoreIfNeeded);
    }

    private void applyWorkspacePrefs() {
        if (workspacePrefs == null) return;
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_TOOLBAR).ifPresent(v -> {
            toolbarDividerPosition = clampToolbarDivider(v, true);
            if (topToolbarVisible) {
                applyToolbarDivider();
            }
        });
        workspacePrefs.getBoolean(PuppeteerWorkspacePrefs.KEY_TOP_TOOLBAR_VISIBLE)
            .ifPresent(this::setTopToolbarVisible);
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_TOP).ifPresent(v -> {
            if (topWorkspaceSplit != null && !topWorkspaceSplit.getDividers().isEmpty()) {
                topWorkspaceSplit.setDividerPositions(v);
                topWorkspaceDividerPosition = v;
            }
        });
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_BOTTOM).ifPresent(v -> {
            if (bottomWorkspaceSplit != null && !bottomWorkspaceSplit.getDividers().isEmpty()) {
                bottomWorkspaceSplit.setDividerPositions(v);
                bottomWorkspaceDividerPosition = v;
            }
        });
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_CONTENT).ifPresent(v -> {
            if (workspaceContentSplit != null && !workspaceContentSplit.getDividers().isEmpty()) {
                workspaceContentSplit.setDividerPositions(v);
            }
        });
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_CODE_PANE).ifPresent(v -> {
            codePaneDividerPosition = v;
            if (mainWorkspaceSplit != null && !mainWorkspaceSplit.getDividers().isEmpty()) {
                mainWorkspaceSplit.setDividerPositions(v);
            }
        });
        workspacePrefs.getDivider(PuppeteerWorkspacePrefs.DIVIDER_PREVIEW_FOCUS).ifPresent(v -> {
            previewFocusDividerPosition = v;
            if (previewFocusMode && previewFocusSplit != null && !previewFocusSplit.getDividers().isEmpty()) {
                previewFocusSplit.setDividerPositions(v);
            }
        });

        if (animationPreview != null) {
            double panX = workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_X).orElse(0.0);
            double panY = workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_Y).orElse(0.0);
            double zoom = workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_ZOOM).orElse(1.0);
            animationPreview.setViewPanAndZoom(panX, panY, zoom);
        }
        if (project != null) {
            workspacePrefs.getDouble(PuppeteerWorkspacePrefs.KEY_TIMELINE_PLAYHEAD).ifPresent(ms -> {
                project.setPlayheadMs(ms);
            });
        }
    }

    private File resolveRegisteredJesFile(String timelineName) {
        if (projectRoot == null || timelineName == null || timelineName.isBlank()) return null;
        if (!PuppeteerVerification.isValidTimelineName(timelineName)) return null;
        return projectRoot.toPath()
            .resolve("scripts").resolve("timelines").resolve(timelineName + ".jes")
            .toFile();
    }

    private void scheduleDraftSave() {
        if (draftStore == null) return;
        String name = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
        if (name.isEmpty()) return;
        if (!PuppeteerVerification.isValidTimelineName(name)) return;
        String code = codePreview != null ? codePreview.getCode() : null;
        if (code == null || code.isBlank()) return;
        draftStore.scheduleSave(name, code);
    }

    public void showRecordGifDialog() {
        if (previewRecorder == null || projectRoot == null) {
            overlayDialog.showDialog(
                "Record Preview as GIF",
                "Recording requires a saved project (no project root is set).",
                null,
                ActionEditorDialogOverlay.ActionSpec.accent("Close", overlayDialog::hideOverlay));
            return;
        }
        if (previewRecorder.isActive()) {
            overlayDialog.showDialog(
                "Record Preview as GIF",
                "A recording is already in progress.",
                null,
                ActionEditorDialogOverlay.ActionSpec.accent("OK", overlayDialog::hideOverlay));
            return;
        }

        double durationMs = Math.max(100.0, project.getTotalDurationMs());
        double loopStart = project.hasLoopRegion() ? project.getLoopStartMs() : 0.0;
        double loopEnd = project.hasLoopRegion() ? project.getLoopEndMs() : durationMs;

        String defaultBaseName = (tfTimelineName != null && !tfTimelineName.getText().isBlank())
            ? tfTimelineName.getText().trim()
            : "preview";
        File defaultDir = projectRoot.toPath().resolve("exports").resolve("puppeteer").toFile();

        TextField tfBaseName = new TextField(defaultBaseName);
        tfBaseName.setPrefColumnCount(20);
        TextField tfOutputDir = new TextField(defaultDir.getAbsolutePath());
        tfOutputDir.setPrefColumnCount(28);
        Button btnBrowse = new Button("Browse...");
        btnBrowse.setStyle(STYLE_BTN_DARK);
        btnBrowse.setOnAction(ev -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose recording output directory");
            File current = new File(tfOutputDir.getText().trim());
            File initial = current.isDirectory() ? current
                : (current.getParentFile() != null && current.getParentFile().isDirectory()
                    ? current.getParentFile()
                    : projectRoot);
            if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
            File picked = chooser.showDialog(this);
            if (picked != null) tfOutputDir.setText(picked.getAbsolutePath());
        });

        TextField tfStart = new TextField(String.valueOf((long) loopStart));
        tfStart.setPrefColumnCount(7);
        TextField tfEnd = new TextField(String.valueOf((long) loopEnd));
        tfEnd.setPrefColumnCount(7);
        Spinner<Integer> spFps = new Spinner<>();
        spFps.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 24));
        spFps.setEditable(true);
        spFps.setPrefWidth(90);
        CheckBox cbGif = new CheckBox("Animated GIF");
        cbGif.setSelected(true);
        CheckBox cbPng = new CheckBox("PNG sequence");
        cbPng.setSelected(false);

        // Resolution controls — default to canvas dimensions, lock aspect by default.
        javafx.scene.canvas.Canvas previewCanvas = animationPreview.getPreviewCanvas();
        int nativeW = previewCanvas != null ? Math.max(1, (int) Math.round(previewCanvas.getWidth())) : 1280;
        int nativeH = previewCanvas != null ? Math.max(1, (int) Math.round(previewCanvas.getHeight())) : 720;
        double aspectRatio = nativeH == 0 ? 1.0 : (double) nativeW / nativeH;

        Spinner<Integer> spWidth = new Spinner<>();
        spWidth.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(16, 4096, nativeW, 16));
        spWidth.setEditable(true);
        spWidth.setPrefWidth(120);

        Spinner<Integer> spHeight = new Spinner<>();
        spHeight.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(16, 4096, nativeH, 16));
        spHeight.setEditable(true);
        spHeight.setPrefWidth(120);

        CheckBox cbLockAspect = new CheckBox("Lock aspect");
        cbLockAspect.setSelected(true);

        Button btnResetRes = new Button("Native");
        btnResetRes.setStyle(STYLE_BTN_DARK);
        btnResetRes.setTooltip(new Tooltip("Reset to native preview size (" + nativeW + " x " + nativeH + ")"));
        btnResetRes.setOnAction(ev -> {
            spWidth.getValueFactory().setValue(nativeW);
            spHeight.getValueFactory().setValue(nativeH);
        });

        // Lock aspect ratio: editing one side updates the other.
        final boolean[] aspectGuard = {false};
        spWidth.valueProperty().addListener((obs, oldV, newV) -> {
            if (!cbLockAspect.isSelected() || aspectGuard[0] || newV == null) return;
            aspectGuard[0] = true;
            try {
                int derivedH = Math.max(16, Math.min(4096, (int) Math.round(newV / aspectRatio)));
                spHeight.getValueFactory().setValue(derivedH);
            } finally {
                aspectGuard[0] = false;
            }
        });
        spHeight.valueProperty().addListener((obs, oldV, newV) -> {
            if (!cbLockAspect.isSelected() || aspectGuard[0] || newV == null) return;
            aspectGuard[0] = true;
            try {
                int derivedW = Math.max(16, Math.min(4096, (int) Math.round(newV * aspectRatio)));
                spWidth.getValueFactory().setValue(derivedW);
            } finally {
                aspectGuard[0] = false;
            }
        });

        ProgressBar progress = new ProgressBar(0.0);
        progress.setPrefWidth(420);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);

        Label lblStatus = new Label("Captures the preview canvas frame-by-frame using the chosen FPS.");
        lblStatus.setWrapText(true);
        lblStatus.setStyle("-fx-text-fill: #989898; -fx-font-size: 11px;");

        HBox baseRow = new HBox(8, tfBaseName);
        HBox.setHgrow(tfBaseName, Priority.ALWAYS);
        tfBaseName.setMaxWidth(Double.MAX_VALUE);

        HBox outputRow = new HBox(6, tfOutputDir, btnBrowse);
        HBox.setHgrow(tfOutputDir, Priority.ALWAYS);
        tfOutputDir.setMaxWidth(Double.MAX_VALUE);

        HBox rangeRow = new HBox(6,
            new Label("Start"), tfStart,
            spacer(8),
            new Label("End"), tfEnd);
        rangeRow.setAlignment(Pos.CENTER_LEFT);

        HBox fpsRow = new HBox(6,
            new Label("FPS"), spFps,
            spacer(12),
            cbGif, cbPng);
        fpsRow.setAlignment(Pos.CENTER_LEFT);

        HBox resolutionRow = new HBox(6,
            new Label("Width"), spWidth,
            new Label("\u00d7"), spHeight,
            spacer(8),
            cbLockAspect,
            spacer(8),
            btnResetRes);
        resolutionRow.setAlignment(Pos.CENTER_LEFT);

        Label headerBaseName = sectionLabel("Base name");
        Label headerOutputDir = sectionLabel("Output directory");
        Label headerRange = sectionLabel("Time range (ms)");
        Label headerFps = sectionLabel("Playback");
        Label headerResolution = sectionLabel("Resolution");

        VBox content = new VBox(6,
            headerBaseName, baseRow,
            headerOutputDir, outputRow,
            headerRange, rangeRow,
            headerFps, fpsRow,
            headerResolution, resolutionRow,
            progress, lblStatus);
        content.setFillWidth(true);
        content.setSpacing(6);
        content.getStyleClass().add("puppeteer-record-form");

        // Holders so callbacks can mutate state.
        final Runnable[] showInitialActions = {null};
        final Runnable[] showRecordingActions = {null};
        final Runnable[] showFinishedActions = {null};
        // Captured at record-start so the post-export Reveal button can target it
        // even after the user typed something else into the directory field afterwards.
        final File[] lastOutputDir = {null};
        final java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<String>> showErrorAction =
            new java.util.concurrent.atomic.AtomicReference<>();

        Runnable beginRecording = () -> {
            double startMs;
            double endMs;
            try {
                startMs = Double.parseDouble(tfStart.getText().trim());
                endMs = Double.parseDouble(tfEnd.getText().trim());
            } catch (NumberFormatException ex) {
                lblStatus.setText("Start and End must be numeric milliseconds.");
                showInitialActions[0].run();
                return;
            }
            if (endMs <= startMs) {
                lblStatus.setText("End must be greater than Start.");
                showInitialActions[0].run();
                return;
            }
            String baseName = tfBaseName.getText().trim();
            if (baseName.isEmpty()) {
                lblStatus.setText("Choose a base name for the output files.");
                showInitialActions[0].run();
                return;
            }
            String dirPath = tfOutputDir.getText().trim();
            if (dirPath.isEmpty()) {
                lblStatus.setText("Choose an output directory.");
                showInitialActions[0].run();
                return;
            }
            File outputDir = new File(dirPath);
            if (!cbGif.isSelected() && !cbPng.isSelected()) {
                lblStatus.setText("Enable at least one output format (GIF or PNG sequence).");
                showInitialActions[0].run();
                return;
            }
            int fps = spFps.getValue() != null ? spFps.getValue() : 24;
            int outW = spWidth.getValue() != null ? spWidth.getValue() : nativeW;
            int outH = spHeight.getValue() != null ? spHeight.getValue() : nativeH;
            PuppeteerPreviewRecorder.Spec spec = new PuppeteerPreviewRecorder.Spec(
                outputDir, baseName, startMs, endMs, fps,
                cbPng.isSelected(), cbGif.isSelected(),
                outW, outH);

            if (project.isPlaying()) pause();

            // Snapshot the output folder so the finished-state Reveal action survives
            // the user editing the directory field after recording completes.
            lastOutputDir[0] = outputDir;

            tfBaseName.setDisable(true);
            tfOutputDir.setDisable(true);
            btnBrowse.setDisable(true);
            tfStart.setDisable(true);
            tfEnd.setDisable(true);
            spFps.setDisable(true);
            cbGif.setDisable(true);
            cbPng.setDisable(true);
            spWidth.setDisable(true);
            spHeight.setDisable(true);
            cbLockAspect.setDisable(true);
            btnResetRes.setDisable(true);

            progress.setVisible(true);
            progress.setManaged(true);
            progress.setProgress(0.0);
            lblStatus.setText("Recording " + spec.frameCount() + " frames at " + fps + " fps...");
            showRecordingActions[0].run();

            previewRecorder.record(spec, new PuppeteerPreviewRecorder.Hooks() {
                @Override
                public void seekAndRender(double timeMs) {
                    project.setPlayheadMs(timeMs);
                    timelinePanel.refresh();
                    updateTimeLabel();
                    updatePreview();
                }
                @Override
                public void onProgress(double normalizedProgress) {
                    progress.setProgress(normalizedProgress);
                }
                @Override
                public void onFinished(PuppeteerPreviewRecorder.Result result) {
                    if (result.success()) {
                        StringBuilder sb = new StringBuilder("Recording complete:\n");
                        for (File f : result.outputs()) {
                            sb.append("• ").append(f.getAbsolutePath()).append('\n');
                        }
                        lblStatus.setText(sb.toString().trim());
                        showFinishedActions[0].run();
                    } else {
                        showErrorAction.get().accept(result.error());
                    }
                }
            });
        };

        showInitialActions[0] = () -> overlayDialog.showDialog(
            "Record Preview as GIF",
            "Capture the preview canvas frame-by-frame to PNG and/or animated GIF.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", () -> {
                if (previewRecorder.isActive()) previewRecorder.cancel();
            }),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Start Recording",
                ActionEditorDialogOverlay.ButtonStyle.ACCENT, beginRecording));

        showRecordingActions[0] = () -> overlayDialog.showDialog(
            "Recording Puppeteer Preview",
            "Recording is in progress. Stay on this dialog to monitor progress.",
            content,
            ActionEditorDialogOverlay.ActionSpec.danger("Cancel Recording", () -> {
                if (previewRecorder.isActive()) previewRecorder.cancel();
            }));

        showFinishedActions[0] = () -> overlayDialog.showDialog(
            "Recording Complete",
            "Files were written to disk.",
            content,
            ActionEditorDialogOverlay.ActionSpec.accent("Reveal in Folder", () -> {
                File dir = lastOutputDir[0];
                if (dir == null || !dir.isDirectory()) {
                    lblStatus.setText("Output folder is no longer available.");
                    return;
                }
                try {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(dir);
                    } else {
                        lblStatus.setText("Desktop integration is unavailable in this environment.");
                    }
                } catch (Exception ex) {
                    lblStatus.setText("Could not open folder: " + ex.getMessage());
                }
            }),
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay));

        showErrorAction.set(errorMessage -> {
            lblStatus.setText("Recording failed: " + errorMessage);
            tfBaseName.setDisable(false);
            tfOutputDir.setDisable(false);
            btnBrowse.setDisable(false);
            tfStart.setDisable(false);
            tfEnd.setDisable(false);
            spFps.setDisable(false);
            cbGif.setDisable(false);
            cbPng.setDisable(false);
            spWidth.setDisable(false);
            spHeight.setDisable(false);
            cbLockAspect.setDisable(false);
            btnResetRes.setDisable(false);
            progress.setVisible(false);
            progress.setManaged(false);
            showInitialActions[0].run();
        });

        showInitialActions[0].run();
    }

    private static Region spacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        r.setPrefWidth(width);
        return r;
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #c0c0c0; -fx-font-size: 10px; -fx-font-weight: bold;");
        return label;
    }

    private void promptDraftRestoreIfNeeded() {
        if (draftRestorePromptShown || draftStore == null || tfTimelineName == null) return;
        String name = tfTimelineName.getText().trim();
        if (name.isEmpty()) return;
        File registered = resolveRegisteredJesFile(name);
        Optional<PuppeteerDraftStore.DraftRecord> draft =
            draftStore.findRestorableDraft(name, registered);
        if (draft.isEmpty()) return;
        draftRestorePromptShown = true;
        PuppeteerDraftStore.DraftRecord rec = draft.get();

        long ageSec = Math.max(0L,
            (System.currentTimeMillis() - rec.lastModifiedMs()) / 1000L);
        String ageText = ageSec < 60 ? ageSec + "s ago"
            : ageSec < 3600 ? (ageSec / 60) + " min ago"
            : (ageSec / 3600) + "h " + ((ageSec % 3600) / 60) + "m ago";

        Label ageLabel = sectionLabel("Saved");
        Label ageValue = new Label(ageText);
        ageValue.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 12px;");

        Label pathLabel = sectionLabel("Draft file");
        Label pathValue = new Label(rec.file().getAbsolutePath());
        pathValue.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px; -fx-font-family: 'JetBrains Mono', 'SF Mono', 'Consolas', monospace;");
        pathValue.setWrapText(true);

        Label hint = new Label(
            "Restore loads the draft into the code preview where you can review it and "
                + "Commit it (overwriting the registered timeline). Discard deletes the draft "
                + "and continues with the registered timeline. Keep Draft leaves the file untouched "
                + "for next session.");
        hint.setStyle("-fx-text-fill: #989898; -fx-font-size: 11px;");
        hint.setWrapText(true);

        VBox content = new VBox(6,
            ageLabel, ageValue,
            pathLabel, pathValue,
            hint);
        content.setFillWidth(true);

        overlayDialog.showDialog(
            "Restore Puppeteer draft?",
            "An unsaved draft for \"" + name + "\" was found.",
            content,
            ActionEditorDialogOverlay.ActionSpec.danger("Discard", () -> draftStore.deleteDraft(name)),
            ActionEditorDialogOverlay.ActionSpec.neutral("Keep Draft", () -> {}),
            ActionEditorDialogOverlay.ActionSpec.accent("Restore", () -> {
                if (codePreview != null) {
                    codePreview.setCode(rec.code());
                    stagePreviewFromCode();
                }
            }));
    }

    public void setSourceScriptFile(java.io.File file) {
        this.scriptTargetFile = file;
        if (assetImporterPanel != null) {
            assetImporterPanel.setScriptTargetFile(file);
        }
    }

    public void showAssetImporterWindow() {
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

        project.pruneOrbitAnchors(collectProjectAnchorTargetNames());
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

    public void play() {
        if (project.isPlaying()) return;
        project.setPlaying(true);
        lastNanos = System.nanoTime();
        playbackTimer.start();
        refreshTransportButtonStates();
        updateStatusBar();
    }

    public void pause() {
        project.setPlaying(false);
        playbackTimer.stop();
        refreshTransportButtonStates();
        updateStatusBar();
    }

    public void stop() {
        pause();
        project.setPlayheadMs(0);
        timelinePanel.setPlayhead(0);
        updateTimeLabel();
        updatePreview();
    }

    public void rewind() {
        project.setPlayheadMs(0);
        timelinePanel.setPlayhead(0);
        updateTimeLabel();
        updatePreview();
    }

    private void setViewportStabilizationEnabled(boolean enabled) {
        viewportStabilizationEnabled = enabled;
        if (cbViewportStabilize != null && cbViewportStabilize.isSelected() != enabled) {
            cbViewportStabilize.setSelected(enabled);
        }
        animationPreview.setViewportStabilizationEnabled(enabled);
        updatePreview();
        updateStatusBar();
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

    public void updateTimeLabel() {
        lblTime.setText(String.format("%.0f ms", project.getPlayheadMs()));
        refreshSidebarTabs();
        updateStatusBar();
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

    private static ListCell<PropertyType> propertyTypeListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PropertyType property, boolean empty) {
                super.updateItem(property, empty);
                setText(empty || property == null ? null : property.getDisplayName());
            }
        };
    }

    public void updatePreview() {
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

        Set<com.jvn.core.scene2d.Entity2D> previewAppliedEntities =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (EntityTrack track : project.getTracks()) {
            var entity = scene.find(track.getEntityName());
            if (entity == null) continue;
            boolean authoredTrack = trackHasAuthoredValues(track);
            if (!authoredTrack && previewAppliedEntities.contains(entity)) {
                continue;
            }
            if (!track.isVisible()) {
                entity.setVisible(false);
                previewAppliedEntities.add(entity);
                continue;
            }
            String entityName = track.getEntityName();
            Map<PropertyType, Double> baselineValues = new EnumMap<>(PropertyType.class);
            for (PropertyType property : PropertyType.values()) {
                if (property.isEntityProperty()) {
                    baselineValues.put(property, baselinePropertyValue(entityName, entity, property));
                }
            }
            AnimationProject.EffectiveEntityTransform effectiveTransform =
                project.computeEffectiveEntityTransform(entityName, time, baselineValues);

            double z = project.hasEffectiveAnimation(entityName, PropertyType.Z)
                ? effectiveTransform.z()
                : project.computeEffectiveLayerOrder(entityName);
            entity.setZ(z);

            double visualX = effectiveTransform.x();
            double visualY = effectiveTransform.y();
            if (track.hasKeyframes(PropertyType.PIVOT_X) || track.hasKeyframes(PropertyType.PIVOT_Y)) {
                double[] size = entityVisualSize(entity);
                if (!track.hasKeyframes(PropertyType.X)) {
                    double basePivotX = baselineValues.getOrDefault(PropertyType.PIVOT_X, entity.getOriginX());
                    visualX += (effectiveTransform.pivotX() - basePivotX) * size[0];
                }
                if (!track.hasKeyframes(PropertyType.Y)) {
                    double basePivotY = baselineValues.getOrDefault(PropertyType.PIVOT_Y, entity.getOriginY());
                    visualY += (effectiveTransform.pivotY() - basePivotY) * size[1];
                }
            }
            entity.setPosition(visualX, visualY);

            setEntityPivot(entity, effectiveTransform.pivotX(), effectiveTransform.pivotY());
            entity.setRotationDeg(effectiveTransform.rotationDeg());
            entity.setScale(effectiveTransform.scaleX(), effectiveTransform.scaleY());
            setEntityAlpha(entity, effectiveTransform.alpha());
            entity.setVisible(effectiveTransform.visibility() >= 0.5);

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
            // Compose parent-group supplemental matrices (outermost group applied last)
            {
                String grpCursor = project.getEntityParentGroupName(entityName);
                Set<String> grpVisited = new LinkedHashSet<>();
                while (grpCursor != null && grpVisited.add(grpCursor)) {
                    EntityGroup grp = project.getGroup(grpCursor);
                    if (grp == null) break;
                    EntityTrack grpTrack = grp.getGroupTrack();
                    boolean hasMatrix = grpTrack.hasKeyframes(PropertyType.MATRIX_MXX)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_MXY)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_MYX)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_MYY)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_TX)
                        || grpTrack.hasKeyframes(PropertyType.MATRIX_TY);
                    if (hasMatrix) {
                        double gMxx = grpTrack.hasKeyframes(PropertyType.MATRIX_MXX) ? grpTrack.getValueAt(PropertyType.MATRIX_MXX, time) : 1.0;
                        double gMxy = grpTrack.hasKeyframes(PropertyType.MATRIX_MXY) ? grpTrack.getValueAt(PropertyType.MATRIX_MXY, time) : 0.0;
                        double gMyx = grpTrack.hasKeyframes(PropertyType.MATRIX_MYX) ? grpTrack.getValueAt(PropertyType.MATRIX_MYX, time) : 0.0;
                        double gMyy = grpTrack.hasKeyframes(PropertyType.MATRIX_MYY) ? grpTrack.getValueAt(PropertyType.MATRIX_MYY, time) : 1.0;
                        double gTx  = grpTrack.hasKeyframes(PropertyType.MATRIX_TX)  ? grpTrack.getValueAt(PropertyType.MATRIX_TX,  time) : 0.0;
                        double gTy  = grpTrack.hasKeyframes(PropertyType.MATRIX_TY)  ? grpTrack.getValueAt(PropertyType.MATRIX_TY,  time) : 0.0;
                        // G * E  (group transform wraps entity transform)
                        double rMxx = gMxx * matrixMxx + gMxy * matrixMyx;
                        double rMxy = gMxx * matrixMxy + gMxy * matrixMyy;
                        double rMyx = gMyx * matrixMxx + gMyy * matrixMyx;
                        double rMyy = gMyx * matrixMxy + gMyy * matrixMyy;
                        double rTx  = gMxx * matrixTx  + gMxy * matrixTy  + gTx;
                        double rTy  = gMyx * matrixTx  + gMyy * matrixTy  + gTy;
                        matrixMxx = rMxx; matrixMxy = rMxy;
                        matrixMyx = rMyx; matrixMyy = rMyy;
                        matrixTx  = rTx;  matrixTy  = rTy;
                    }
                    grpCursor = grp.getParentGroupName();
                }
            }
            entity.setSupplementalTransform(matrixMxx, matrixMxy, matrixMyx, matrixMyy, matrixTx, matrixTy);

            double baseBlur = baselinePropertyValue(entityName, entity, PropertyType.BLUR);
            double blur = project.hasEffectiveAnimation(entityName, PropertyType.BLUR)
                ? project.computeValueAt(entityName, PropertyType.BLUR, time, baseBlur)
                : baseBlur;
            // Add blur contributions from parent groups
            {
                String grpCursor = project.getEntityParentGroupName(entityName);
                Set<String> grpVisited = new LinkedHashSet<>();
                while (grpCursor != null && grpVisited.add(grpCursor)) {
                    EntityGroup grp = project.getGroup(grpCursor);
                    if (grp == null) break;
                    EntityTrack grpTrack = grp.getGroupTrack();
                    if (grpTrack.hasKeyframes(PropertyType.BLUR)) {
                        blur += grpTrack.getValueAt(PropertyType.BLUR, time);
                    }
                    grpCursor = grp.getParentGroupName();
                }
            }
            entity.setBlurRadius(blur);

            double baseBrightness = baselinePropertyValue(entityName, entity, PropertyType.BRIGHTNESS);
            double brightness = project.hasEffectiveAnimation(entityName, PropertyType.BRIGHTNESS)
                ? project.computeValueAt(entityName, PropertyType.BRIGHTNESS, time, baseBrightness)
                : baseBrightness;
            entity.setBrightness(brightness);

            for (String customPropertyKey : track.getAnimatedCustomProperties()) {
                double baselineValue = baselineCustomPropertyValue(entityName, customPropertyKey, entity);
                double customValue = track.getCustomValueAt(customPropertyKey, time, baselineValue);
                entity.applyCustomProperty(customPropertyKey, customValue);
            }
            previewAppliedEntities.add(entity);
        }

        applyPreviewEventCuesUpTo(time);

        animationPreview.render();
        refreshSidebarTabs();
    }

    private boolean trackHasAuthoredValues(EntityTrack track) {
        if (track == null) return false;
        for (PropertyType property : PropertyType.values()) {
            if (track.hasKeyframes(property)) return true;
        }
        return track.getAnimatedCustomProperties().iterator().hasNext();
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
                if (expression.isBlank()) expression = payloadValue(payload, "expression");
                if (applyLayeredExpressionCue(target, expression, payload)) {
                    break;
                }
                String directPath = firstNonBlank(payloadValue(payload, "path"), pathSpecFromLayerPayload(payload));
                String path = resolveCueAssetPath(target, expression, directPath, false);
                if (path != null && entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                    sprite.setImagePath(path);
                    entity.setVisible(true);
                }
            }
            case "show" -> {
                if (entity != null) entity.setVisible(true);
                String expression = payloadValue(payload, "expression");
                if (expression.isBlank()) expression = payloadValue(payload, "value");
                if (applyLayeredExpressionCue(target, expression, payload)) {
                    break;
                }
                String directPath = firstNonBlank(payloadValue(payload, "path"), pathSpecFromLayerPayload(payload));
                String path = resolveCueAssetPath(target, expression, directPath, false);
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
                if (applyLayeredExpressionCue(target, expression, payload)) {
                    break;
                }
                String directPath = firstNonBlank(payloadValue(payload, "path"), pathSpecFromLayerPayload(payload));
                String path = resolveCueAssetPath(target, expression, directPath, false);
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

    private String inferExpressionTargetFromSelection() {
        String selected = timelinePanel != null ? timelinePanel.getSelectedEntity() : "";
        String inferred = inferCharacterIdFromSelection(selected);
        if (inferred != null && !inferred.isBlank()) return inferred;
        if (launchSceneSnapshot != null && !launchSceneSnapshot.characters.isEmpty()) {
            PuppeteerLauncherPanel.CharacterEntry first = launchSceneSnapshot.characters.get(0);
            if (first != null && first.characterId != null && !first.characterId.isBlank()) {
                return first.characterId;
            }
        }
        return "";
    }

    private String inferCharacterIdFromSelection(String rawSelection) {
        String selection = rawSelection == null ? "" : rawSelection.trim();
        if (selection.isBlank()) return "";
        if (launchSceneSnapshot != null) {
            for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
                if (character == null || character.characterId == null || character.characterId.isBlank()) continue;
                String characterId = character.characterId.trim();
                String safeCharacter = selectorSafeName(characterId);
                String groupName = snapshotCharacterGroupName(character);
                if (selection.equals(characterId)
                    || selection.equals(groupName)
                    || selection.startsWith(groupName + "_")
                    || (!safeCharacter.isBlank() && selection.startsWith(safeCharacter + "_"))) {
                    return characterId;
                }
            }
        }
        return selection;
    }

    private List<String> expressionTargetSuggestions() {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        String inferred = inferExpressionTargetFromSelection();
        if (inferred != null && !inferred.isBlank()) targets.add(inferred);
        if (launchSceneSnapshot != null) {
            for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
                if (character != null && character.characterId != null && !character.characterId.isBlank()) {
                    targets.add(character.characterId.trim());
                }
            }
        }
        for (String key : launchCharacterImagePaths.keySet()) {
            int slash = key == null ? -1 : key.indexOf('/');
            if (slash > 0) targets.add(key.substring(0, slash));
        }
        for (String key : launchCharacterPresetLayers.keySet()) {
            int slash = key == null ? -1 : key.indexOf('/');
            if (slash > 0) targets.add(key.substring(0, slash));
        }
        for (EntityTrack track : project.getTracks()) {
            if (track != null && track.getEntityName() != null && !track.getEntityName().isBlank()) {
                targets.add(inferCharacterIdFromSelection(track.getEntityName()));
            }
        }
        return List.copyOf(targets);
    }

    private List<String> expressionSuggestionsForTarget(String rawTarget) {
        String target = inferCharacterIdFromSelection(rawTarget);
        LinkedHashSet<String> expressions = new LinkedHashSet<>();
        if (launchSceneSnapshot != null) {
            for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
                if (character == null || !Objects.equals(character.characterId, target)) continue;
                if (character.expression != null && !character.expression.isBlank()) {
                    expressions.add(character.expression.trim());
                }
            }
        }
        String prefix = target == null || target.isBlank() ? "" : target + "/";
        for (String key : launchCharacterImagePaths.keySet()) {
            if (key != null && key.startsWith(prefix) && key.length() > prefix.length()) {
                expressions.add(key.substring(prefix.length()));
            }
        }
        for (String key : launchCharacterPresetLayers.keySet()) {
            if (key != null && key.startsWith(prefix) && key.length() > prefix.length()) {
                expressions.add(key.substring(prefix.length()));
            }
        }
        if (expressions.isEmpty()) expressions.add("neutral");
        return List.copyOf(expressions);
    }

    private String preferredExpressionForTarget(String rawTarget) {
        String target = inferCharacterIdFromSelection(rawTarget);
        if (launchSceneSnapshot != null) {
            for (PuppeteerLauncherPanel.CharacterEntry character : launchSceneSnapshot.characters) {
                if (character == null || !Objects.equals(character.characterId, target)) continue;
                if (character.expression != null && !character.expression.isBlank()) return character.expression.trim();
            }
        }
        List<String> suggestions = expressionSuggestionsForTarget(target);
        return suggestions.isEmpty() ? "neutral" : suggestions.get(0);
    }

    private void enrichExpressionPayload(String type,
                                         Map<String, String> payload,
                                         String rawTarget,
                                         String expression,
                                         String directPath) {
        if (payload == null) return;
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("expression", "show", "replace").contains(normalizedType)) return;
        String target = inferCharacterIdFromSelection(rawTarget);
        if (target.isBlank() || expression == null || expression.isBlank()) return;
        String path = directPath == null || directPath.isBlank()
            ? resolveRawCueAssetPath(target, expression)
            : directPath.trim();
        if (!path.isBlank()) {
            payload.putIfAbsent("path", path);
        }
        if (!payload.containsKey("layers")) {
            Map<String, String> pathPayload = path.isBlank() ? Map.of() : Map.of("path", path);
            String layerPayload = encodeLayerPayload(resolveExpressionLayerSpecs(target, expression, pathPayload));
            if (!layerPayload.isBlank()) {
                payload.put("layers", layerPayload);
            }
        }
    }

    private String resolveRawCueAssetPath(String rawTarget, String expressionOrId) {
        String target = inferCharacterIdFromSelection(rawTarget);
        String token = expressionOrId == null ? "" : expressionOrId.trim();
        if (target.isBlank() || token.isBlank()) return "";
        String mapped = launchCharacterImagePaths.get(target + "/" + token);
        return mapped == null ? "" : mapped.trim();
    }

    private boolean applyLayeredExpressionCue(String rawTarget, String expression, Map<String, String> payload) {
        if (scene == null) return false;
        String target = inferCharacterIdFromSelection(rawTarget);
        if (target.isBlank()) return false;
        List<ExpressionLayerSpec> layers = resolveExpressionLayerSpecs(target, expression, payload);
        if (layers.isEmpty()) return false;

        List<ExpressionLayerCandidate> candidates = expressionLayerCandidates(target);
        if (candidates.isEmpty()) return false;

        Set<String> used = new LinkedHashSet<>();
        ExpressionLayerCandidate template = candidates.get(0);
        int applied = 0;
        for (int i = 0; i < layers.size(); i++) {
            ExpressionLayerSpec layer = layers.get(i);
            if (layer == null || layer.path() == null || layer.path().isBlank()) continue;
            ExpressionLayerCandidate candidate = findExpressionLayerCandidate(candidates, layer.layerId(), used);
            if (candidate == null) {
                candidate = createExpressionLayerEntity(target, expression, layer, template, i);
                if (candidate == null) continue;
                candidates.add(candidate);
            }
            candidate.sprite().setImagePath(resolvePreviewAssetPath(layer.path()));
            candidate.sprite().setVisible(true);
            used.add(candidate.name());
            applied++;
        }
        for (ExpressionLayerCandidate candidate : candidates) {
            if (candidate != null && !used.contains(candidate.name())) {
                candidate.sprite().setVisible(false);
            }
        }
        return applied > 0;
    }

    private List<ExpressionLayerSpec> resolveExpressionLayerSpecs(String rawTarget,
                                                                  String expression,
                                                                  Map<String, String> payload) {
        List<ExpressionLayerSpec> fromPayload = parseLayerPayload(payloadValue(payload, "layers"));
        if (!fromPayload.isEmpty()) return fromPayload;

        String target = inferCharacterIdFromSelection(rawTarget);
        String token = expression == null ? "" : expression.trim();
        if (!target.isBlank() && !token.isBlank()) {
            List<PuppeteerLauncherPanel.CharacterLayerEntry> mapped = launchCharacterPresetLayers.get(target + "/" + token);
            if (mapped != null && !mapped.isEmpty()) {
                List<ExpressionLayerSpec> specs = new ArrayList<>();
                for (PuppeteerLauncherPanel.CharacterLayerEntry layer : mapped) {
                    if (layer == null || layer.path == null || layer.path.isBlank()) continue;
                    specs.add(new ExpressionLayerSpec(layer.layerId, layer.path));
                }
                if (!specs.isEmpty()) return specs;
            }
        }

        String path = firstNonBlank(payloadValue(payload, "path"), resolveRawCueAssetPath(target, token));
        if (path == null || path.isBlank() || path.indexOf('|') < 0) return List.of();
        List<ExpressionLayerSpec> specs = new ArrayList<>();
        int index = 1;
        for (String part : path.split("\\|")) {
            String layerPath = part == null ? "" : part.trim();
            if (layerPath.isBlank()) continue;
            specs.add(new ExpressionLayerSpec("layer" + index++, layerPath));
        }
        return List.copyOf(specs);
    }

    private List<ExpressionLayerSpec> parseLayerPayload(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<ExpressionLayerSpec> specs = new ArrayList<>();
        int index = 1;
        for (String token : raw.split("\\|")) {
            String part = token == null ? "" : token.trim();
            if (part.isBlank()) continue;
            int sep = part.indexOf('=');
            String layerId = sep > 0 ? part.substring(0, sep).trim() : "layer" + index;
            String path = sep > 0 && sep < part.length() - 1 ? part.substring(sep + 1).trim() : part;
            if (!path.isBlank()) specs.add(new ExpressionLayerSpec(layerId, path));
            index++;
        }
        return List.copyOf(specs);
    }

    private String encodeLayerPayload(List<ExpressionLayerSpec> layers) {
        if (layers == null || layers.isEmpty()) return "";
        StringBuilder encoded = new StringBuilder();
        int index = 1;
        for (ExpressionLayerSpec layer : layers) {
            if (layer == null || layer.path() == null || layer.path().isBlank()) continue;
            if (!encoded.isEmpty()) encoded.append(" | ");
            String layerId = layer.layerId() == null || layer.layerId().isBlank()
                ? "layer" + index
                : layer.layerId().trim();
            encoded.append(layerId).append('=').append(layer.path().trim());
            index++;
        }
        return encoded.toString();
    }

    private String pathSpecFromLayerPayload(Map<String, String> payload) {
        return pathSpecFromLayers(parseLayerPayload(payloadValue(payload, "layers")));
    }

    private String pathSpecFromLayers(List<ExpressionLayerSpec> layers) {
        if (layers == null || layers.isEmpty()) return "";
        StringBuilder pathSpec = new StringBuilder();
        for (ExpressionLayerSpec layer : layers) {
            if (layer == null || layer.path() == null || layer.path().isBlank()) continue;
            if (!pathSpec.isEmpty()) pathSpec.append(" | ");
            pathSpec.append(layer.path().trim());
        }
        return pathSpec.toString();
    }

    private List<ExpressionLayerCandidate> expressionLayerCandidates(String rawTarget) {
        if (scene == null) return List.of();
        String target = inferCharacterIdFromSelection(rawTarget);
        String safeTarget = selectorSafeName(target);
        if (safeTarget.isBlank()) return List.of();
        List<ExpressionLayerCandidate> candidates = new ArrayList<>();
        for (String name : scene.names()) {
            if (name == null || name.isBlank() || name.equals(target)) continue;
            if (!name.startsWith(safeTarget + "_")) continue;
            com.jvn.core.scene2d.Entity2D entity = scene.find(name);
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                candidates.add(new ExpressionLayerCandidate(name, sprite));
            }
        }
        candidates.sort(java.util.Comparator
            .comparingDouble((ExpressionLayerCandidate candidate) -> candidate.sprite().getZ())
            .thenComparing(ExpressionLayerCandidate::name));
        return candidates;
    }

    private ExpressionLayerCandidate findExpressionLayerCandidate(List<ExpressionLayerCandidate> candidates,
                                                                  String layerId,
                                                                  Set<String> used) {
        if (candidates == null || candidates.isEmpty()) return null;
        String safeLayer = selectorSafeName(layerId);
        if (!safeLayer.isBlank()) {
            for (ExpressionLayerCandidate candidate : candidates) {
                if (candidate == null || used.contains(candidate.name())) continue;
                if (candidate.name().endsWith("_" + safeLayer)) return candidate;
            }
            for (ExpressionLayerCandidate candidate : candidates) {
                if (candidate == null || used.contains(candidate.name())) continue;
                if (candidate.name().contains("_" + safeLayer + "_")) return candidate;
            }
        }
        for (ExpressionLayerCandidate candidate : candidates) {
            if (candidate != null && !used.contains(candidate.name())) return candidate;
        }
        return null;
    }

    private ExpressionLayerCandidate createExpressionLayerEntity(String target,
                                                                 String expression,
                                                                 ExpressionLayerSpec layer,
                                                                 ExpressionLayerCandidate template,
                                                                 int index) {
        if (scene == null || template == null || template.sprite() == null || layer == null) return null;
        com.jvn.core.scene2d.Sprite2D source = template.sprite();
        String baseName = selectorSafeName(target) + "_"
            + selectorSafeName(expression == null || expression.isBlank() ? "expression" : expression)
            + "_" + selectorSafeName(layer.layerId() == null || layer.layerId().isBlank() ? "layer" + (index + 1) : layer.layerId());
        if (baseName.isBlank()) baseName = "expression_layer";
        String entityName = baseName;
        int suffix = 2;
        while (scene.find(entityName) != null) {
            entityName = baseName + "_" + suffix++;
        }
        com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(
            resolvePreviewAssetPath(layer.path()),
            source.getWidth(),
            source.getHeight());
        sprite.setOrigin(source.getOriginX(), source.getOriginY());
        sprite.setPosition(source.getX(), source.getY());
        sprite.setScale(source.getScaleX(), source.getScaleY());
        sprite.setRotationDeg(source.getRotationDeg());
        sprite.setZ(source.getZ() + 0.01 * (index + 1));
        sprite.setAlpha(source.getAlpha());
        sprite.setVisible(false);
        scene.add(sprite);
        scene.registerEntity(entityName, sprite);
        sceneBaselineVisibility.put(entityName, false);
        sceneBaselineImagePaths.put(entityName, sprite.getImagePath());
        sceneBaselineCustomProperties.put(entityName, captureEntityCustomPropertyBaseline(sprite));
        return new ExpressionLayerCandidate(entityName, sprite);
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
        target = inferCharacterIdFromSelection(target);
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

    private static double[] entityVisualSize(com.jvn.core.scene2d.Entity2D entity) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
            return new double[] { Math.max(1.0, sprite.getWidth()), Math.max(1.0, sprite.getHeight()) };
        }
        if (entity instanceof com.jvn.core.scene2d.Panel2D panel) {
            return new double[] { Math.max(1.0, panel.getWidth()), Math.max(1.0, panel.getHeight()) };
        }
        return new double[] { 1.0, 1.0 };
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

    private void upsertGroupPivotPreservingPose(
        String groupName,
        EntityTrack track,
        double timeMs,
        double pivotX,
        double pivotY
    ) {
        GroupPivotAdjustment adjustment = computeGroupPivotAdjustment(groupName, track, timeMs, pivotX, pivotY);
        track.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(timeMs, pivotX));
        track.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(timeMs, pivotY));
        if (adjustment != null && adjustment.translate()) {
            track.upsertKeyframe(PropertyType.X, new Keyframe(timeMs, adjustment.x()));
            track.upsertKeyframe(PropertyType.Y, new Keyframe(timeMs, adjustment.y()));
        }
    }

    private List<PuppeteerCommand> groupPivotPreservingCommands(
        String groupName,
        EntityTrack track,
        double timeMs,
        double pivotX,
        double pivotY
    ) {
        List<PuppeteerCommand> commands = new ArrayList<>();
        GroupPivotAdjustment adjustment = computeGroupPivotAdjustment(groupName, track, timeMs, pivotX, pivotY);
        commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_X, timeMs, pivotX));
        commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_Y, timeMs, pivotY));
        if (adjustment != null && adjustment.translate()) {
            commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.X, timeMs, adjustment.x()));
            commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, timeMs, adjustment.y()));
        }
        return commands;
    }

    private GroupPivotAdjustment computeGroupPivotAdjustment(
        String groupName,
        EntityTrack track,
        double timeMs,
        double newPivotX,
        double newPivotY
    ) {
        if (groupName == null || groupName.isBlank() || track == null || project.getGroup(groupName) == null) {
            return null;
        }
        double oldPivotX = trackValueAt(track, PropertyType.PIVOT_X, timeMs);
        double oldPivotY = trackValueAt(track, PropertyType.PIVOT_Y, timeMs);
        double[] oldPivot = project.computeGroupLocalPivot(groupName, oldPivotX, oldPivotY);
        double[] newPivot = project.computeGroupLocalPivot(groupName, newPivotX, newPivotY);
        if (oldPivot == null || oldPivot.length < 2 || newPivot == null || newPivot.length < 2) {
            return null;
        }
        double dx = newPivot[0] - oldPivot[0];
        double dy = newPivot[1] - oldPivot[1];
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return null;

        double scaleX = trackValueAt(track, PropertyType.SCALE_X, timeMs);
        if (track.hasKeyframes(PropertyType.MIRROR_X)) {
            scaleX *= mirrorFactor(track.getValueAt(PropertyType.MIRROR_X, timeMs));
        }
        double scaleY = trackValueAt(track, PropertyType.SCALE_Y, timeMs);
        double radians = Math.toRadians(trackValueAt(track, PropertyType.ROTATION, timeMs));
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double transformedDx = dx * scaleX * cos - dy * scaleY * sin;
        double transformedDy = dx * scaleX * sin + dy * scaleY * cos;
        double compensationX = transformedDx - dx;
        double compensationY = transformedDy - dy;
        if (!Double.isFinite(compensationX) || !Double.isFinite(compensationY)) return null;

        boolean translate = Math.abs(compensationX) > 1e-6 || Math.abs(compensationY) > 1e-6;
        double currentX = trackValueAt(track, PropertyType.X, timeMs);
        double currentY = trackValueAt(track, PropertyType.Y, timeMs);
        return new GroupPivotAdjustment(currentX + compensationX, currentY + compensationY, translate);
    }

    private static double trackValueAt(EntityTrack track, PropertyType property, double timeMs) {
        if (track == null || property == null) return 0.0;
        return track.hasKeyframes(property)
            ? track.getValueAt(property, timeMs)
            : property.getDefaultValue();
    }

    private static double mirrorFactor(double mirrorX) {
        if (!Double.isFinite(mirrorX)) return 1.0;
        return Math.cos(clampPivot(mirrorX) * Math.PI);
    }

    /**
     * Computes the world-space position delta needed to keep an entity visually
     * stationary when its pivot changes from (oldPX, oldPY) to (newPX, newPY).
     * Returns [deltaX, deltaY] to add to the entity's current position.
     */
    private static double[] pivotCompensation(com.jvn.core.scene2d.Entity2D entity,
                                               double newPX, double newPY,
                                               double oldPX, double oldPY) {
        if (entity == null) return new double[]{0.0, 0.0};
        double w = 1.0, h = 1.0;
        if (entity instanceof com.jvn.core.scene2d.Sprite2D sp) {
            w = Math.max(1.0, sp.getWidth());
            h = Math.max(1.0, sp.getHeight());
        }
        double scaleX  = entity.getScaleX();
        double scaleY  = entity.getScaleY();
        double rotRad  = Math.toRadians(entity.getRotationDeg());
        double cos     = Math.cos(rotRad);
        double sin     = Math.sin(rotRad);
        double dx      = (newPX - oldPX) * w * scaleX;
        double dy      = (newPY - oldPY) * h * scaleY;
        return new double[]{dx * cos - dy * sin, dx * sin + dy * cos};
    }

    private static double clampPivot(double value) {
        if (!Double.isFinite(value)) return 0.5;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    void refreshTransportButtonStates() {
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

    public boolean isPreviewFullscreenActive() {
        return previewFocusMode;
    }

    public void togglePreviewFocusMode() {
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

    void exitFullscreenPreview() {
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

    public void copySelectedKeyframesToClipboard() {
        timelinePanel.copySelectedKeyframes();
        // Save to clipboard history
        List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry> current = timelinePanel.getCopiedKeyframes();
        if (!current.isEmpty()) {
            clipboardHistory.add(0, List.copyOf(current));
            if (clipboardHistory.size() > MAX_CLIPBOARD_HISTORY) {
                clipboardHistory.remove(clipboardHistory.size() - 1);
            }
        }
        refreshToolbarCommandSummary();
    }

    public void copyExportedCodeToClipboard() {
        showCodeGenerationActionPreview(
            "Copy Generated Timeline Code?",
            "Puppeteer will generate JES code from the current visual timeline and copy it to the clipboard.",
            "Copy Code",
            List.of(
                "Generate a standard JES timeline block from the current visual model.",
                "Run export/runtime diagnostics before copying.",
                "Copy the generated text to the system clipboard.",
                "Leave the project file, registered timeline file, and TimelineRegistry unchanged.",
                "This action does not include named Puppeteer reopen metadata; Save & Register writes the named metadata-rich .jes file."
            ),
            this::performCopyExportedCodeToClipboard
        );
    }

    private void performCopyExportedCodeToClipboard() {
        try {
            String code = CodeExporter.export(project);
            List<TimelineDiagnostic.Message> findings = new ArrayList<>(
                PuppeteerVerification.diagnose(
                    project,
                    knownSceneEntities(),
                    projectRoot,
                    PuppeteerVerification.Mode.EXPORT_CODE
                )
            );
            findings.addAll(TimelineDiagnostic.diagnoseDsl(code));
            boolean hasErrors = findings.stream()
                .anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
            if (hasErrors) {
                showVerificationOverlay(
                    "Export Blocked",
                    "Puppeteer found export errors. Fix them before copying this timeline code.",
                    findings,
                    null,
                    null
                );
                return;
            }
            copyToClipboard(code);
            if (statusBar != null) statusBar.setText("Exported timeline code copied to clipboard");
            if (onCopyCode != null) onCopyCode.accept(code);
        } catch (Exception ex) {
            showOverlayError(
                PuppeteerErrorType.EXPORT,
                "Export Failed",
                "Could not generate timeline code.",
                ex.getMessage(),
                ex
            );
        }
    }

    public void requestRefreshGeneratedCode() {
        showCodeGenerationActionPreview(
            "Regenerate Timeline Code?",
            "Puppeteer will replace the right-side Timeline Code text with freshly generated code from the visual model.",
            "Regenerate",
            List.of(
                "Discard manual edits currently present in the Timeline Code panel.",
                "Generate " + (compactExport ? "compact" : "standard") + " JES timeline code from the current visual model.",
                "Refresh parse and timeline diagnostics for the generated code.",
                "Leave saved files and TimelineRegistry unchanged.",
                "Use Preview Parse and Commit if you want hand-edited code to become the visual model instead."
            ),
            this::refreshExportPreview
        );
    }

    public void pasteCopiedKeyframesAtPlayhead() {
        timelinePanel.pasteCopiedKeyframesAtPlayhead();
        refreshToolbarCommandSummary();
    }

    public void duplicateSelectedKeyframesBySnapStep() {
        double delta = Math.max(1.0, timelinePanel.getSnapStepMs());
        timelinePanel.duplicateSelectedKeyframes(delta);
        refreshToolbarCommandSummary();
    }

    public void executeUndo() {
        if (!commandStack.canUndo()) return;
        commandStack.undo();
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
        refreshUndoRedoControls();
    }

    public void executeRedo() {
        if (!commandStack.canRedo()) return;
        commandStack.redo();
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
        refreshUndoRedoControls();
    }

    public void showPresetMenuOverlay() {
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

    public void showSlotMenuOverlay() {
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

    public void showEyeFocusOverlay() {
        String selected = timelinePanel != null ? timelinePanel.getSelectedEntity() : "";
        String[] inferred = inferEyeFocusSelection(selected);
        TextField characterField = new TextField(inferred[0].isBlank() ? "john" : inferred[0]);
        TextField expressionField = new TextField(inferred[1].isBlank() ? "neutral" : inferred[1]);
        VnEyeFocusProfile existing = project.getEyeFocusProfile(characterField.getText(), expressionField.getText());

        TextField sourceXField = new TextField(formatDialogNumber(existing != null ? existing.sourceX() : 0.5));
        TextField sourceYField = new TextField(formatDialogNumber(existing != null ? existing.sourceY() : 0.26));
        TextField targetXField = new TextField("1");
        TextField targetYField = new TextField(formatDialogNumber(existing != null ? existing.sourceY() : 0.26));
        TextField deadZoneField = new TextField(formatDialogNumber(existing != null ? existing.deadZone() : 0.12));
        TextField maxNudgeField = new TextField(formatDialogNumber(existing != null ? existing.maxNudgePx() : 3.0));
        TextField strengthField = new TextField(formatDialogNumber(existing != null ? existing.strength() : 1.0));

        TextField[] layerFields = new TextField[10];
        for (int i = 1; i <= 9; i++) {
            String layer = existing != null ? existing.layerIdFor(i) : "";
            if (layer == null || layer.isBlank()) {
                layer = inferred[2].isBlank() ? String.format(Locale.ROOT, "eyes_%02d", i) : layerVariant(inferred[2], i);
            }
            layerFields[i] = new TextField(layer);
        }

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(formLabel("Character"), 0, 0);
        grid.add(characterField, 1, 0);
        grid.add(formLabel("Expression"), 2, 0);
        grid.add(expressionField, 3, 0);
        grid.add(formLabel("Source X"), 0, 1);
        grid.add(sourceXField, 1, 1);
        grid.add(formLabel("Source Y"), 2, 1);
        grid.add(sourceYField, 3, 1);
        grid.add(formLabel("Target X"), 0, 2);
        grid.add(targetXField, 1, 2);
        grid.add(formLabel("Target Y"), 2, 2);
        grid.add(targetYField, 3, 2);
        grid.add(formLabel("Dead Zone"), 0, 3);
        grid.add(deadZoneField, 1, 3);
        grid.add(formLabel("Max Nudge"), 2, 3);
        grid.add(maxNudgeField, 3, 3);
        grid.add(formLabel("Strength"), 0, 4);
        grid.add(strengthField, 1, 4);

        int row = 5;
        int[][] keypad = {{7, 8, 9}, {4, 5, 6}, {1, 2, 3}};
        for (int r = 0; r < keypad.length; r++) {
            for (int c = 0; c < keypad[r].length; c++) {
                int index = keypad[r][c];
                VBox cell = new VBox(3, formLabel(Integer.toString(index)), layerFields[index]);
                cell.setPrefWidth(150);
                grid.add(cell, c, row + r);
            }
        }

        VBox content = new VBox(10);
        content.setFillWidth(true);
        Label note = new Label("Applies visibility keys for the 9 mapped pupil layers and X/Y nudge keys for the selected gaze at the current playhead.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #bfc7d5; -fx-font-size: 11px;");
        content.getChildren().addAll(note, grid);

        overlayDialog.showDialog(
            "Eye Focus / Look At",
            "Bake a keypad pupil focus pose into the timeline.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Apply at Playhead", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                try {
                    Map<Integer, String> layers = new LinkedHashMap<>();
                    for (int i = 1; i <= 9; i++) {
                        String layer = layerFields[i].getText() == null ? "" : layerFields[i].getText().trim();
                        if (!layer.isBlank()) layers.put(i, layer);
                    }
                    VnEyeFocusProfile profile = new VnEyeFocusProfile(
                        characterField.getText(),
                        expressionField.getText(),
                        "eyes",
                        parseDialogDouble(sourceXField, 0.5),
                        parseDialogDouble(sourceYField, 0.26),
                        parseDialogDouble(deadZoneField, 0.12),
                        parseDialogDouble(maxNudgeField, 3.0),
                        parseDialogDouble(strengthField, 1.0),
                        layers);
                    if (profile.characterId().isBlank() || profile.layerIds().isEmpty()) {
                        showOverlayError("Eye Focus Failed", "Missing eye-focus rig data.", "Provide a character id and at least one pupil layer mapping.");
                        return;
                    }
                    AnimationProject before = project.copy();
                    AnimationProject after = project.copy();
                    PuppeteerEyeFocusBaker.applyAt(
                        after,
                        profile,
                        project.getPlayheadMs(),
                        parseDialogDouble(sourceXField, 0.5),
                        parseDialogDouble(sourceYField, 0.26),
                        parseDialogDouble(targetXField, 1.0),
                        parseDialogDouble(targetYField, 0.0));
                    commandStack.execute(new PuppeteerCommand(
                        "Apply eye focus",
                        () -> project.replaceFrom(after),
                        () -> project.replaceFrom(before)
                    ));
                    timelinePanel.refresh();
                    updatePreview();
                    refreshExportPreviewAndMarkDirty();
                    overlayDialog.hideOverlay();
                } catch (Exception ex) {
                    showOverlayError("Eye Focus Failed", "Could not bake the eye focus pose.", ex.getMessage());
                }
            })
        );
    }

    private Label formLabel(String text) {
        Label label = new Label(text == null ? "" : text);
        label.setStyle("-fx-text-fill: #9fb1c9; -fx-font-size: 10px; -fx-font-weight: bold;");
        return label;
    }

    private double parseDialogDouble(TextField field, double fallback) {
        try {
            double value = Double.parseDouble(field == null || field.getText() == null ? "" : field.getText().trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String formatDialogNumber(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.abs(value - Math.rint(value)) < 0.000001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.4f", value)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
    }

    private String[] inferEyeFocusSelection(String selectedTarget) {
        String value = selectedTarget == null ? "" : selectedTarget.trim();
        if (value.isBlank()) return new String[] {"", "neutral", ""};
        String[] parts = value.split("_");
        for (int i = 0; i < parts.length; i++) {
            String one = parts[i];
            String two = i + 1 < parts.length ? one + "_" + parts[i + 1] : one;
            int oneIndex = VnEyeFocusProfile.detectKeypadIndex(one);
            int twoIndex = VnEyeFocusProfile.detectKeypadIndex(two);
            if (twoIndex >= 1) {
                String expression = i > 0 ? parts[i - 1] : "neutral";
                String character = joinParts(parts, 0, Math.max(0, i - 1));
                return new String[] {character, expression, two};
            }
            if (oneIndex >= 1) {
                String expression = i > 0 ? parts[i - 1] : "neutral";
                String character = joinParts(parts, 0, Math.max(0, i - 1));
                return new String[] {character, expression, one};
            }
        }
        return new String[] {"", "neutral", ""};
    }

    private String layerVariant(String selectedLayer, int keypadIndex) {
        if (selectedLayer == null || selectedLayer.isBlank()) {
            return String.format(Locale.ROOT, "eyes_%02d", keypadIndex);
        }
        return selectedLayer.replaceFirst("(?i)(0?[1-9])$", String.format(Locale.ROOT, "%02d", keypadIndex));
    }

    private String joinParts(String[] parts, int startInclusive, int endExclusive) {
        if (parts == null || startInclusive >= endExclusive) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, startInclusive); i < Math.min(parts.length, endExclusive); i++) {
            if (parts[i] == null || parts[i].isBlank()) continue;
            if (sb.length() > 0) sb.append('_');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private void applyPreset(AnimationPreset preset) {
        if (timelinePanel.isSelectedGroup()) return;
        EntityTrack track = selectedTrackForEditing(true);
        if (track == null) return;
        double startTime = project.getPlayheadMs();
        commandStack.execute(PuppeteerCommand.applyPreset(track, preset, startTime));
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    public void refreshExportPreview() {
        try {
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
        } catch (Exception ex) {
            codePreview.setDiagnostics(List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.ERROR,
                "(export)",
                "Code preview could not be regenerated: " + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()),
                "Review recent timeline edits, invalid numeric values, and custom property keys"
            )));
            if (statusBar != null) {
                statusBar.setText("Code preview refresh failed: " + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()));
            }
        }
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

    public void refreshExportPreviewAndMarkDirty() {
        refreshExportPreview();
        setDirty(true);
        updateStatusBar();
        scheduleDraftSave();
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
        if (viewportStabilizationEnabled) {
            sb.append("  │  Stabilization ");
            sb.append(animationPreview.isViewportStabilizationActive() ? "ACTIVE" : "ON");
        }
        sb.append("  │  Speed: ").append(playbackSpeed).append("x");
        statusBar.setText(sb.toString());
        statusBar.setTooltip(new Tooltip(sb.toString()));

        if (statusPlaybackLabel != null) {
            String playback = project.isPlaying() ? "Playing" : "Stopped";
            if (project.isLooping()) playback += " / Loop";
            playback += " / " + trimPlaybackSpeed(playbackSpeed) + "x";
            statusPlaybackLabel.setText(playback);
            statusPlaybackLabel.setTooltip(new Tooltip("Playback state. Click for play, stop, rewind, and loop controls."));
            setSegmentState(statusPlaybackSegment, project.isPlaying() ? "jvn-status-diagnostics-ok" : "",
                "jvn-status-diagnostics-ok");
        }
        if (statusTimelineLabel != null) {
            String timeline = formatStatusTime(project.getPlayheadMs()) + " / " + formatStatusTime(project.getTotalDurationMs());
            statusTimelineLabel.setText(timeline);
            statusTimelineLabel.setTooltip(new Tooltip("Playhead and duration. Click for keyframe navigation."));
        }
        if (statusSelectionLabel != null) {
            String selection = "No selection";
            if (timelinePanel != null) {
                int selectedKeys = timelinePanel.getSelectionCount();
                if (selectedKeys > 0) {
                    selection = selectedKeys + " keyframe" + (selectedKeys == 1 ? "" : "s");
                } else if (timelinePanel.getSelectedEntity() != null && !timelinePanel.getSelectedEntity().isBlank()) {
                    selection = selectionLabel(timelinePanel.getSelectedEntity(), timelinePanel.isSelectedGroup());
                }
                PropertyType selectedProperty = timelinePanel.getSelectedProperty();
                if (selectedProperty != null && !selection.equals("No selection")) {
                    selection += " / " + selectedProperty.getDisplayName();
                }
            }
            statusSelectionLabel.setText(selection);
            statusSelectionLabel.setTooltip(new Tooltip("Current target, property, and keyframe selection."));
        }
        if (statusSceneLabel != null) {
            int tracks = project.getTrackCount();
            int groups = 0;
            for (EntityGroup ignored : project.getGroups()) groups++;
            int audio = project.getAudioCues().size();
            int events = project.getEditorEventCues().size();
            String sceneStats = tracks + " track" + (tracks == 1 ? "" : "s")
                + " / " + groups + " group" + (groups == 1 ? "" : "s");
            if (audio > 0 || events > 0) {
                sceneStats += " / " + audio + " audio / " + events + " events";
            }
            statusSceneLabel.setText(sceneStats);
            statusSceneLabel.setTooltip(new Tooltip("Scene contents, groups, audio cues, and event cues."));
        }
        if (statusComplexityLabel != null) {
            StatusComplexity complexity = computeStatusComplexity();
            String text = complexity.keyframes() + " key" + (complexity.keyframes() == 1 ? "" : "s")
                + " / " + complexity.channels() + " chan" + (complexity.channels() == 1 ? "" : "s");
            statusComplexityLabel.setText(text);
            statusComplexityLabel.setTooltip(new Tooltip(
                "Animated keyframes/channels: " + complexity.keyframes() + " / " + complexity.channels()
                    + "\nConstraints: " + complexity.constraints()
                    + "\nAnchors: " + complexity.anchors()
                    + "\nEye focus profiles: " + complexity.eyeProfiles()));
            setSegmentState(statusComplexitySegment,
                complexity.keyframes() > 240 || complexity.channels() > 32 ? "jvn-status-diagnostics-warn" : "",
                "jvn-status-diagnostics-warn");
        }
        if (statusModeLabel != null) {
            List<String> modes = new ArrayList<>();
            modes.add(timelinePanel != null && timelinePanel.isSnapEnabled()
                ? "Snap " + String.format(Locale.ROOT, "%.0fms", timelinePanel.getSnapStepMs())
                : "Snap Off");
            if (autoKeyEnabled) modes.add("Auto-Key");
            if (runtimeParityPreview) modes.add("Runtime");
            if (viewportStabilizationEnabled) {
                modes.add(animationPreview.isViewportStabilizationActive() ? "Stable Active" : "Stable");
            }
            statusModeLabel.setText(String.join(" / ", modes));
            statusModeLabel.setTooltip(new Tooltip("Timeline snapping, auto-key, runtime preview, and viewport stabilization."));
            setSegmentState(statusModeSegment,
                autoKeyEnabled || runtimeParityPreview || viewportStabilizationEnabled ? "jvn-status-diagnostics-warn" : "",
                "jvn-status-diagnostics-warn");
        }
        if (statusSaveLabel != null) {
            String saveState = previewStaged ? "Preview Staged" : dirty ? "Unsaved" : "Saved";
            statusSaveLabel.setText(saveState);
            statusSaveLabel.setTooltip(new Tooltip("Timeline save and staged code-preview state."));
            setSegmentState(statusSaveSegment,
                previewStaged || dirty ? "jvn-status-dirty" : "jvn-status-clean",
                "jvn-status-clean", "jvn-status-dirty");
        }
        if (statusViewportLabel != null) {
            ProjectViewportSpec.Dimensions viewport = animationPreview != null
                ? animationPreview.getViewportDimensions()
                : ProjectViewportSpec.resolve(projectRoot);
            String viewportText = viewport.width() + "x" + viewport.height();
            if (previewFocusMode) viewportText += " Focus";
            statusViewportLabel.setText(viewportText);
            String cameraText = animationPreview == null
                ? ""
                : String.format(Locale.ROOT,
                    "\nCamera: X %.1f  Y %.1f  Z %.2f",
                    animationPreview.getCamera().getX(),
                    animationPreview.getCamera().getY(),
                    animationPreview.getCamera().getZoom());
            statusViewportLabel.setTooltip(new Tooltip(
                "Runtime viewport: " + viewport.width() + " x " + viewport.height()
                    + cameraText
                    + "\nClick for fit, focus, and recording actions."));
            setSegmentState(statusViewportSegment,
                previewFocusMode ? "jvn-status-diagnostics-ok" : "",
                "jvn-status-diagnostics-ok");
        }
        if (statusProjectLabel != null) {
            String projectName = projectRoot == null
                ? "No project"
                : firstNonBlank(projectRoot.getName(), projectRoot.getAbsolutePath());
            statusProjectLabel.setText(projectName);
            statusProjectLabel.setTooltip(new Tooltip(projectRoot == null
                ? "No project root is bound."
                : projectRoot.getAbsolutePath()));
        }
        if (statusExportLabel != null) {
            List<String> export = new ArrayList<>();
            export.add(compactExport ? "Compact JES" : "Standard JES");
            export.add(codePaneVisible ? "Code visible" : "Code hidden");
            int codeLines = countCodeLines(codePreview == null ? null : codePreview.getCode());
            if (codeLines > 0) export.add(codeLines + " lines");
            int copied = timelinePanel == null ? 0 : timelinePanel.getCopiedKeyframeCount();
            if (copied > 0) export.add("Clipboard " + copied);
            statusExportLabel.setText(String.join(" / ", export));
            statusExportLabel.setTooltip(new Tooltip(
                "Export format, generated code size, code pane visibility, and keyframe clipboard state."));
            setSegmentState(statusExportSegment, codePaneVisible ? "" : "jvn-status-diagnostics-warn",
                "jvn-status-diagnostics-warn");
        }
        refreshToolbarCommandSummary();
    }

    private StatusComplexity computeStatusComplexity() {
        int keyframes = 0;
        int channels = 0;
        int anchors = 0;
        for (EntityTrack track : project.getTracks()) {
            if (track == null) continue;
            for (PropertyType property : track.getAnimatedProperties()) {
                int count = track.getKeyframes(property).size();
                if (count > 0) {
                    keyframes += count;
                    channels++;
                }
            }
            for (String propertyKey : track.getAnimatedCustomProperties()) {
                int count = track.getCustomKeyframes(propertyKey).size();
                if (count > 0) {
                    keyframes += count;
                    channels++;
                }
            }
            anchors += project.getAnchorsForEntity(track.getEntityName()).size();
        }
        return new StatusComplexity(
            keyframes,
            channels,
            project.getConstraintsView().size(),
            anchors,
            project.getEyeFocusProfilesView().size());
    }

    private static int countCodeLines(String code) {
        if (code == null || code.isBlank()) return 0;
        int lines = 1;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private static void setSegmentState(HBox segment, String active, String... states) {
        if (segment == null || states == null) return;
        segment.getStyleClass().removeAll(states);
        if (active != null && !active.isBlank() && !segment.getStyleClass().contains(active)) {
            segment.getStyleClass().add(active);
        }
    }

    private static String formatStatusTime(double millis) {
        if (!Double.isFinite(millis)) millis = 0.0;
        long totalMillis = Math.max(0L, Math.round(millis));
        long minutes = totalMillis / 60_000L;
        long seconds = (totalMillis / 1_000L) % 60L;
        long ms = totalMillis % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, ms);
    }

    private static String trimPlaybackSpeed(double speed) {
        if (!Double.isFinite(speed)) return "1";
        if (Math.abs(speed - Math.rint(speed)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", speed);
        }
        return String.format(Locale.ROOT, "%.2f", speed).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record StatusComplexity(
        int keyframes,
        int channels,
        int constraints,
        int anchors,
        int eyeProfiles
    ) {}

    private void updateViewportInfoLabel() {
        if (viewportInfoLabel == null) return;
        ProjectViewportSpec.Dimensions vp = ProjectViewportSpec.resolve(projectRoot);
        viewportInfoLabel.setText(
            "Viewport: " + vp.width() + "x" + vp.height()
                + ""
        );
    }

    public void showShortcutsOverlay() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 8;");

        String[][] shortcuts = {
            {"Space", "Play / Pause"},
            {"Home", "Rewind"},
            {"Page Up / Down", "Jump to previous / next keyframe"},
            {"K", "Add keyframe at playhead"},
            {"Del", "Delete selected keyframe"},
            {"Ctrl/Cmd + Alt + F", "Focus timeline on selection"},
            {"Alt + ← / →", "Nudge keyframe by snap step"},
            {"Alt + Shift + ← / →", "Nudge keyframe by 1ms"},
            {"Alt + Shift + R", "Reverse selected keyframes"},
            {"Alt + Shift + E", "Distribute selected keyframes"},
            {"Ctrl/Cmd + Alt + C", "Copy selected keyframes"},
            {"Ctrl/Cmd + Alt + V", "Paste keyframes at playhead"},
            {"Ctrl/Cmd + Alt + D", "Duplicate keyframes"},
            {"Ctrl/Cmd + Shift + C", "Copy exported code"},
            {"Ctrl/Cmd + Z", "Undo"},
            {"Ctrl/Cmd + Shift + Z", "Redo"},
            {"Ctrl/Cmd + O", "Toggle onion skinning"},
            {"A", "Toggle orbit tool"},
            {"Shift + A", "Clear orbit anchor"}
        };

        for (int i = 0; i < shortcuts.length; i++) {
            Label keyLabel = new Label(shortcuts[i][0]);
            keyLabel.setStyle("-fx-text-fill: #9cdcfe; -fx-font-family: Monospaced; -fx-font-weight: bold; -fx-background-color: #2d2d2d; -fx-padding: 4 8; -fx-background-radius: 4; -fx-border-color: #3d3d3d; -fx-border-radius: 4;");
            
            Label descLabel = new Label(shortcuts[i][1]);
            descLabel.setStyle("-fx-text-fill: #d7d7d7; -fx-font-size: 13px;");
            
            grid.add(keyLabel, 0, i);
            grid.add(descLabel, 1, i);
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(380);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");

        overlayDialog.showDialog(
            "Keyboard Shortcuts",
            "Puppeteer keyboard shortcuts",
            scroll,
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
        updateStatusBar();
    }

    private void applySnapStepFromField() {
        try {
            double step = Double.parseDouble(tfSnapMs.getText().trim());
            timelinePanel.setSnapStepMs(step);
            tfSnapMs.setText(String.format("%.0f", timelinePanel.getSnapStepMs()));
        } catch (Exception ex) {
            tfSnapMs.setText(String.format("%.0f", timelinePanel.getSnapStepMs()));
        }
        updateStatusBar();
    }

    public void showAddAudioCueDialog() {
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

    private void showExpressionKeyframeDialog() {
        String initialTarget = inferExpressionTargetFromSelection();
        ComboBox<String> cbTarget = new ComboBox<>();
        cbTarget.setEditable(true);
        cbTarget.getItems().setAll(expressionTargetSuggestions());
        cbTarget.setValue(initialTarget);
        cbTarget.setPromptText("character / entity");
        cbTarget.setStyle(STYLE_TEXT_FIELD);

        ComboBox<String> cbExpression = new ComboBox<>();
        cbExpression.setEditable(true);
        cbExpression.getItems().setAll(expressionSuggestionsForTarget(initialTarget));
        cbExpression.setValue(preferredExpressionForTarget(initialTarget));
        cbExpression.setPromptText("expression");
        cbExpression.setStyle(STYLE_TEXT_FIELD);

        TextField tfTime = new TextField(String.format(Locale.ROOT, "%.0f", project.getPlayheadMs()));
        tfTime.setPromptText("ms");
        tfTime.setStyle(STYLE_TEXT_FIELD);

        TextField tfPath = new TextField();
        tfPath.setPromptText("optional path override");
        tfPath.setStyle(STYLE_TEXT_FIELD);

        CheckBox cbEmbedResolved = new CheckBox("Embed resolved sprite path / layers");
        cbEmbedResolved.setSelected(true);
        cbEmbedResolved.setStyle("-fx-text-fill: #d8d8d8; -fx-font-size: 11px;");

        Label lblResolved = makeToolbarLabel("");
        lblResolved.setWrapText(true);

        TextArea taDslPreview = new TextArea();
        taDslPreview.setEditable(false);
        taDslPreview.setFocusTraversable(false);
        taDslPreview.setPrefRowCount(6);
        taDslPreview.setWrapText(false);
        taDslPreview.setStyle("-fx-control-inner-background: #111111; -fx-text-fill: #ececec;");

        Runnable refreshResolved = () -> {
            String target = cbTarget.getValue() == null ? "" : cbTarget.getValue().trim();
            String expression = cbExpression.getValue() == null ? "" : cbExpression.getValue().trim();
            List<ExpressionLayerSpec> layers = resolveExpressionLayerSpecs(target, expression, Map.of());
            String path = firstNonBlank(tfPath.getText(), resolveRawCueAssetPath(target, expression));
            if (!layers.isEmpty()) {
                String layerNames = String.join(", ", layers.stream()
                    .map(ExpressionLayerSpec::layerId)
                    .filter(name -> name != null && !name.isBlank())
                    .toList());
                lblResolved.setText(layers.size() + " layered sprite entries resolved"
                    + (layerNames.isBlank() ? "" : ": " + layerNames));
            } else if (path != null && !path.isBlank()) {
                lblResolved.setText("Sprite path resolved: " + path);
            } else {
                lblResolved.setText("No mapping found; the expression name will still export for VN runtime playback.");
            }
            double previewTime = parseNonNegativeDoubleOr(tfTime.getText(), project.getPlayheadMs());
            Map<String, String> payload = buildExpressionCuePayload(
                target,
                expression,
                tfPath.getText(),
                cbEmbedResolved.isSelected());
            taDslPreview.setText(new EditorEventCue(previewTime, "expression", payload).getDslPreview());
        };

        cbTarget.valueProperty().addListener((obs, oldValue, newValue) -> {
            List<String> expressions = expressionSuggestionsForTarget(newValue);
            cbExpression.getItems().setAll(expressions);
            if (cbExpression.getValue() == null || cbExpression.getValue().isBlank()
                || !expressions.contains(cbExpression.getValue())) {
                cbExpression.setValue(preferredExpressionForTarget(newValue));
            }
            refreshResolved.run();
        });
        cbExpression.valueProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        tfPath.textProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        tfTime.textProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        cbEmbedResolved.selectedProperty().addListener((obs, oldValue, newValue) -> refreshResolved.run());
        refreshResolved.run();

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(8));
        form.add(makeToolbarLabel("Target"), 0, 0);
        form.add(cbTarget, 1, 0);
        form.add(makeToolbarLabel("Expression"), 0, 1);
        form.add(cbExpression, 1, 1);
        form.add(makeToolbarLabel("Time"), 0, 2);
        form.add(tfTime, 1, 2);
        form.add(makeToolbarLabel("Path"), 0, 3);
        form.add(tfPath, 1, 3);
        form.add(cbEmbedResolved, 1, 4);

        VBox body = new VBox(
            8,
            form,
            lblResolved,
            makeToolbarLabel("DSL Preview"),
            taDslPreview
        );
        body.setPadding(new Insets(0, 8, 8, 8));

        overlayDialog.showDialog(
            "Expression Keyframe",
            "Switches a character sprite/expression; the preview shows the exact timeline DSL and layered sprite payload.",
            body,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Add", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                double timeMs;
                try {
                    timeMs = Math.max(0.0, Double.parseDouble(tfTime.getText().trim()));
                } catch (NumberFormatException ex) {
                    tfTime.requestFocus();
                    return;
                }
                String target = cbTarget.getValue() == null ? "" : cbTarget.getValue().trim();
                String expression = cbExpression.getValue() == null ? "" : cbExpression.getValue().trim();
                if (target.isBlank()) {
                    cbTarget.requestFocus();
                    return;
                }
                if (expression.isBlank()) {
                    cbExpression.requestFocus();
                    return;
                }
                String directPath = tfPath.getText() == null ? "" : tfPath.getText().trim();
                Map<String, String> payload = buildExpressionCuePayload(
                    target,
                    expression,
                    directPath,
                    cbEmbedResolved.isSelected());
                project.addEditorEventCue(new EditorEventCue(timeMs, "expression", payload));
                timelinePanel.refresh();
                updatePreview();
                refreshExportPreviewAndMarkDirty();
                overlayDialog.hideOverlay();
            })
        );
    }

    public void showEventCueManagerDialog(EditorEventCue initialSelection) {
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
                setText(item.getDisplayLabel());
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

        TextArea taDslPreview = new TextArea();
        taDslPreview.setEditable(false);
        taDslPreview.setFocusTraversable(false);
        taDslPreview.setPrefRowCount(6);
        taDslPreview.setWrapText(false);
        taDslPreview.setStyle("-fx-control-inner-background: #111111; -fx-text-fill: #ececec;");

        Label lblTypeHint = makeToolbarLabel("");
        Label lblValue = makeToolbarLabel("Value");
        Label lblPath = makeToolbarLabel("Path");
        Label lblPosition = makeToolbarLabel("Position");

        Runnable refreshList = () -> cueList.getItems().setAll(project.getEditorEventCues());
        Runnable refreshCuePreview = () -> {
            String preset = cbTypePreset.getValue() == null ? "expression" : cbTypePreset.getValue();
            String type = "custom".equals(preset) ? tfType.getText().trim() : preset;
            double timeMs = parseNonNegativeDoubleOr(tfTime.getText(), project.getPlayheadMs());
            Map<String, String> payload = buildEventCuePayload(
                type,
                tfTarget.getText(),
                tfValue.getText(),
                tfPath.getText(),
                tfPosition.getText(),
                parsePayloadLines(taExtra.getText()),
                true);
            taDslPreview.setText(new EditorEventCue(timeMs, type, payload).getDslPreview());
        };
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
            refreshCuePreview.run();
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
            refreshCuePreview.run();
        };

        cueList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> loadCue.accept(newValue));
        cbTypePreset.setOnAction(e -> {
            refreshTypeUi.run();
            refreshCuePreview.run();
        });
        tfType.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfTime.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfTarget.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfValue.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfPath.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        tfPosition.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());
        taExtra.textProperty().addListener((obs, oldValue, newValue) -> refreshCuePreview.run());

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
            makeToolbarLabel("DSL Preview"),
            taDslPreview,
            makeToolbarLabel("Timeline Event Cues"),
            cueList,
            makeToolbarLabel("Extra Payload"),
            taExtra
        );
        body.setPadding(new Insets(0, 8, 8, 8));

        overlayDialog.showDialog(
            "Timeline Event Cues",
            "Author discrete sprite swaps, show/hide beats, and scene changes with a live DSL preview.",
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

                String target = tfTarget.getText() == null ? "" : tfTarget.getText().trim();
                String value = tfValue.getText() == null ? "" : tfValue.getText().trim();
                String path = tfPath.getText() == null ? "" : tfPath.getText().trim();
                String position = tfPosition.getText() == null ? "" : tfPosition.getText().trim();
                Map<String, String> payload = buildEventCuePayload(
                    type,
                    target,
                    value,
                    path,
                    position,
                    parsePayloadLines(taExtra.getText()),
                    true);

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

    private Map<String, String> buildExpressionCuePayload(String target,
                                                          String expression,
                                                          String directPath,
                                                          boolean embedResolved) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (target != null && !target.isBlank()) {
            payload.put("target", target.trim());
        }
        if (expression != null && !expression.isBlank()) {
            payload.put("value", expression.trim());
        }
        if (directPath != null && !directPath.isBlank()) {
            payload.put("path", directPath.trim());
        }
        if (embedResolved) {
            enrichExpressionPayload("expression", payload, target, expression, directPath);
        }
        return payload;
    }

    private Map<String, String> buildEventCuePayload(String type,
                                                     String target,
                                                     String value,
                                                     String path,
                                                     String position,
                                                     Map<String, String> extraPayload,
                                                     boolean enrichResolvedExpression) {
        Map<String, String> payload = extraPayload == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(extraPayload);
        String normalizedType = type == null ? "" : type.trim();
        String typeKey = normalizedType.toLowerCase(Locale.ROOT);
        String cleanTarget = target == null ? "" : target.trim();
        String cleanValue = value == null ? "" : value.trim();
        String cleanPath = path == null ? "" : path.trim();
        String cleanPosition = position == null ? "" : position.trim();

        if (!cleanTarget.isBlank()) payload.put("target", cleanTarget);

        switch (typeKey) {
            case "expression" -> {
                if (!cleanValue.isBlank()) payload.put("value", cleanValue);
                if (!cleanPath.isBlank()) payload.put("path", cleanPath);
                if (!cleanPosition.isBlank()) payload.put("position", cleanPosition);
            }
            case "show" -> {
                if (!cleanValue.isBlank()) payload.put("expression", cleanValue);
                if (!cleanPath.isBlank()) payload.put("path", cleanPath);
                if (!cleanPosition.isBlank()) payload.put("position", cleanPosition);
            }
            case "replace" -> {
                if (!cleanValue.isBlank()) payload.put("expression", cleanValue);
                if (!cleanPath.isBlank()) payload.put("path", cleanPath);
            }
            case "scene" -> {
                if (!cleanValue.isBlank()) payload.put("id", cleanValue);
                if (!cleanPath.isBlank()) payload.put("path", cleanPath);
            }
            case "dialogue_marker" -> {
                if (!cleanValue.isBlank()) payload.put("id", cleanValue);
            }
            case "script_call" -> {
                if (!cleanValue.isBlank()) payload.put("name", cleanValue);
                if (!cleanPath.isBlank()) payload.put("arg", cleanPath);
            }
            default -> {
                if (!cleanValue.isBlank()) payload.putIfAbsent("value", cleanValue);
                if (!cleanPath.isBlank()) payload.putIfAbsent("path", cleanPath);
                if (!cleanPosition.isBlank()) payload.putIfAbsent("position", cleanPosition);
            }
        }
        if (enrichResolvedExpression) {
            enrichExpressionPayload(typeKey, payload, cleanTarget, cleanValue, cleanPath);
        }
        return payload;
    }

    private static double parseNonNegativeDoubleOr(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return Math.max(0.0, fallback);
        try {
            return Math.max(0.0, Double.parseDouble(raw.trim()));
        } catch (NumberFormatException ex) {
            return Math.max(0.0, fallback);
        }
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

    public void requestWindowClose() {
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
        // Hide the window FIRST so the user doesn't see a frozen editor while we clean up.
        // Each cleanup step is best-effort and isolated; a failure in any one step must not
        // prevent the rest from running, and must not block the JavaFX Application Thread.
        bypassCloseConfirmation = true;
        try { stopAudioPreview(); } catch (Exception ignored) {}
        try { if (previewRecorder != null) previewRecorder.cancel(); } catch (Exception ignored) {}
        try {
            if (assetImporterWindow != null) {
                assetImporterWindow.close();
                assetImporterWindow = null;
                assetImporterPanel = null;
            }
        } catch (Exception ignored) {}

        // Snapshot divider positions on the FX thread (Node properties are FX-only),
        // but defer the actual disk write to the background.
        PuppeteerWorkspacePrefs prefsSnapshot = null;
        try {
            if (workspacePrefs != null) {
                captureWorkspacePrefsInto(workspacePrefs);
                prefsSnapshot = workspacePrefs;
            }
        } catch (Exception ignored) {}

        // Hand off the draft store; null out the field immediately so no more save tasks
        // can be scheduled by listeners that fire during close.
        PuppeteerDraftStore draftSnapshot = draftStore;
        draftStore = null;

        try { close(); } catch (Exception ignored) {}

        // Background best-effort persistence — never blocks the FX thread.
        final PuppeteerWorkspacePrefs prefsToSave = prefsSnapshot;
        final PuppeteerDraftStore draftsToFlush = draftSnapshot;
        final java.io.File anchorRoot = projectRoot;
        final AnimationProject anchorProject = project;
        if (prefsToSave != null || draftsToFlush != null || anchorRoot != null) {
            Thread cleanup = new Thread(() -> {
                try { if (prefsToSave != null) prefsToSave.save(); } catch (Throwable ignored) {}
                try { if (draftsToFlush != null) draftsToFlush.shutdown(); } catch (Throwable ignored) {}
                try { PuppeteerAnchorStore.save(anchorRoot, anchorProject); } catch (Throwable ignored) {}
                try { VnEyeFocusProfileStore.save(anchorRoot, anchorProject.getEyeFocusProfilesView().values()); } catch (Throwable ignored) {}
                try { PuppeteerRigStore.save(anchorRoot, anchorProject); } catch (Throwable ignored) {}
            }, "puppeteer-close-cleanup");
            cleanup.setDaemon(true);
            cleanup.start();
        }
    }

    /** Copy the current SplitPane divider positions and camera state into the prefs object (no IO). */
    private void captureWorkspacePrefsInto(PuppeteerWorkspacePrefs prefs) {
        if (prefs == null) return;
        if (rootWorkspaceSplit != null && !rootWorkspaceSplit.getDividers().isEmpty() && topToolbarVisible) {
            toolbarDividerPosition = clampToolbarDivider(
                rootWorkspaceSplit.getDividerPositions()[0],
                true
            );
        }
        prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_TOOLBAR, toolbarDividerPosition);
        prefs.setBoolean(PuppeteerWorkspacePrefs.KEY_TOP_TOOLBAR_VISIBLE, topToolbarVisible);
        if (topWorkspaceSplit != null && !topWorkspaceSplit.getDividers().isEmpty()) {
            prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_TOP,
                topWorkspaceSplit.getDividerPositions()[0]);
        }
        if (bottomWorkspaceSplit != null && !bottomWorkspaceSplit.getDividers().isEmpty()) {
            prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_BOTTOM,
                bottomWorkspaceSplit.getDividerPositions()[0]);
        }
        if (workspaceContentSplit != null && !workspaceContentSplit.getDividers().isEmpty()) {
            prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_CONTENT,
                workspaceContentSplit.getDividerPositions()[0]);
        }
        if (mainWorkspaceSplit != null && !mainWorkspaceSplit.getDividers().isEmpty()) {
            prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_CODE_PANE,
                mainWorkspaceSplit.getDividerPositions()[0]);
        }
        if (previewFocusSplit != null && !previewFocusSplit.getDividers().isEmpty()) {
            previewFocusDividerPosition = readDividerPosition(previewFocusSplit, previewFocusDividerPosition);
        }
        prefs.setDivider(PuppeteerWorkspacePrefs.DIVIDER_PREVIEW_FOCUS, previewFocusDividerPosition);
        if (animationPreview != null) {
            prefs.setDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_X, animationPreview.getViewPanX());
            prefs.setDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_PAN_Y, animationPreview.getViewPanY());
            prefs.setDouble(PuppeteerWorkspacePrefs.KEY_VIEWPORT_ZOOM, animationPreview.getViewZoomFactor());
        }
        if (project != null) {
            prefs.setDouble(PuppeteerWorkspacePrefs.KEY_TIMELINE_PLAYHEAD, project.getPlayheadMs());
        }
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

    private static Button makeToolbarIconButton(javafx.scene.layout.Region iconClass, String tooltip) {
        Button btn = new Button();
        btn.getStyleClass().add("puppeteer-toolbar-icon-button");
        btn.setText("");
        btn.setGraphic(iconClass);
        installToolbarTooltip(btn, tooltip);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.setGraphicTextGap(0);
        btn.setMnemonicParsing(false);
        btn.setMinSize(36, 32);
        btn.setPrefSize(36, 32);
        btn.setMaxSize(36, 32);
        btn.setFocusTraversable(false);
        return btn;
    }

    private static Button makeToolbarSuccessIconButton(javafx.scene.layout.Region iconClass, String tooltip) {
        Button btn = makeToolbarIconButton(iconClass, tooltip);
        btn.getStyleClass().add("puppeteer-toolbar-icon-button-success");
        return btn;
    }

    private static ToggleButton makeToolbarIconToggle(javafx.scene.layout.Region iconClass, String tooltip) {
        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("puppeteer-toolbar-icon-toggle");
        toggle.setText("");
        toggle.setGraphic(iconClass);
        installToolbarTooltip(toggle, tooltip);
        toggle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        toggle.setGraphicTextGap(0);
        toggle.setMnemonicParsing(false);
        toggle.setMinSize(36, 32);
        toggle.setPrefSize(36, 32);
        toggle.setMaxSize(36, 32);
        toggle.setFocusTraversable(false);
        return toggle;
    }

    private static void installToolbarTooltip(ButtonBase control, String tooltipText) {
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(Duration.millis(220));
        tooltip.setShowDuration(Duration.seconds(8));
        tooltip.setHideDelay(Duration.millis(80));
        control.setTooltip(tooltip);
        control.setAccessibleText(tooltipText);
        control.setAccessibleHelp(tooltipText);
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

    public boolean hasSavedClips() {
        return !scanClipLibrary().isEmpty();
    }

    public void saveSelectionAsClip() {
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

    public void loadAndApplyClip() {
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

        CheckBox reverseClipCheck = new CheckBox("Reverse motion");
        reverseClipCheck.setTooltip(new Tooltip("Apply the clip backwards without modifying the saved clip."));
        reverseClipCheck.setStyle("-fx-text-fill: #d0d0d0;");

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
            new VBox(4, makeToolbarLabel("Apply Mode"), applyModeBox),
            new VBox(4, makeToolbarLabel("Transform"), reverseClipCheck)
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
                    AnimationClip clip = reverseClipCheck.isSelected()
                        ? selectedClip.clip().reversed()
                        : selectedClip.clip();
                    applyClipToTrack(clip, track, project.getPlayheadMs(), scale, replaceRange);
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
            "%.0fms  •  %d channel(s)  •  %d key(s)",
            clip.getDurationMs(),
            clip.getChannels().size(),
            clip.getKeyframeCount()
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
        commandStack.execute(PuppeteerCommand.composite(
            "Place entity at VN slot",
            List.of(
                PuppeteerCommand.upsertKeyframe(track, PropertyType.X, time, VnSlotHelper.slotX(slot, viewportW)),
                PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, time, VnSlotHelper.baselineY(viewportH))
            )
        ));
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    public KeyframeSelectionModel getSelectionModel() { return selectionModel; }

    public void showRuntimeVerificationReport() {
        List<TimelineDiagnostic.Message> findings = PuppeteerVerification.diagnose(
            project,
            knownSceneEntities(),
            projectRoot,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );
        boolean hasErrors = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
        boolean hasWarnings = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.WARNING);

        if (findings.isEmpty()) {
            findings = List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.INFO,
                "(timeline)",
                "No runtime registration issues found",
                "This timeline is ready to register with the current project state"
            ));
        }

        if (hasErrors) {
            showVerificationOverlay(
                "Runtime Verification (Errors Found)",
                "Puppeteer checked this timeline and found errors blocking registration.",
                findings,
                null,
                null
            );
        } else if (hasWarnings) {
            showVerificationOverlay(
                "Runtime Verification (Warnings Found)",
                "Puppeteer checked this timeline and found potential issues. Proceed with registration?",
                findings,
                "Register Anyway",
                () -> requestRegisterTimeline() 
            );
        } else {
            showVerificationOverlay(
                "Runtime Verification Passed",
                "Puppeteer checked this timeline. It is ready for runtime registration.",
                findings,
                "Register Now",
                () -> requestRegisterTimeline()
            );
        }
    }

    public void requestRegisterTimeline() {
        requestRegisterTimeline(null);
    }

    public void requestRegisterTimeline(Runnable onSuccess) {
        String name = tfTimelineName.getText().trim();
        List<TimelineDiagnostic.Message> nameFindings = PuppeteerVerification.validateTimelineName(name);
        boolean hasNameErrors = nameFindings.stream()
            .anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
        if (hasNameErrors) {
            showVerificationOverlay(
                "Registration Blocked",
                "Puppeteer found timeline name problems. Fix the name before registering.",
                nameFindings,
                null,
                null
            );
            return;
        }
        project.setName(name);
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
                null,
                null
            );
            return;
        }
        showRegistrationActionPreview(name, findings, onSuccess, hasWarnings);
    }

    private void showRegistrationActionPreview(
        String name,
        List<TimelineDiagnostic.Message> findings,
        Runnable onSuccess,
        boolean hasWarnings
    ) {
        String filePath = Optional.ofNullable(resolveRegisteredJesFile(name))
            .map(File::getAbsolutePath)
            .orElse("scripts/timelines/" + name + ".jes");
        String backupPath = projectRoot != null
            ? projectRoot.toPath()
                .resolve("scripts")
                .resolve("timelines")
                .resolve(".backups")
                .resolve(name + ".jes.bak")
                .toString()
            : "scripts/timelines/.backups/" + name + ".jes.bak";

        List<String> details = new ArrayList<>();
        details.add("Validate the timeline name and runtime registration diagnostics.");
        details.add("Capture current scene entity snapshots so Puppeteer can reopen the animation with the same visible scene context.");
        details.add("Convert the visual timeline to TimelineData named \"" + name + "\".");
        details.add("Generate a named JES export with VNS usage comments and Puppeteer metadata comments.");
        details.add("Write the file to: " + filePath);
        details.add("If that file already exists, write a backup to: " + backupPath);
        details.add("Save Puppeteer anchor/orbit metadata for this project.");
        details.add("Register the TimelineData in TimelineRegistry for immediate runtime/VNS playback in this editor session.");
        details.add("Mark the animation clean, remove its autosave draft, and add it to Puppeteer's recent registered timelines.");
        if (previewStaged) {
            details.add("Keep the currently staged code-preview model as the saved registered model.");
        }
        if (onSuccess != null) {
            details.add("Run the requested follow-up action after registration succeeds, such as closing this Puppeteer window.");
        }
        details.add("This will not insert or edit any .vns script line; reference it manually with @external jes_timeline " + name + " when needed.");

        VBox body = buildActionDetailsContent(details);
        if (hasWarnings) {
            Label warningHeader = new Label("Warnings that will be accepted if you continue:");
            warningHeader.setStyle("-fx-text-fill: #ffe2a8; -fx-font-size: 11px; -fx-font-weight: bold;");
            body.getChildren().add(warningHeader);
            body.getChildren().add(buildVerificationContent(findings));
        }

        overlayDialog.showDialog(
            hasWarnings ? "Register Timeline With Warnings?" : "Register Timeline?",
            "Review exactly what Puppeteer will do before it saves and registers \"" + name + "\".",
            body,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.accent("Register", () -> performRegisterTimeline(name, onSuccess))
        );
    }

    private boolean performRegisterTimeline(String name, Runnable onSuccess) {
        try {
            List<TimelineDiagnostic.Message> nameFindings = PuppeteerVerification.validateTimelineName(name);
            boolean hasNameErrors = nameFindings.stream()
                .anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
            if (hasNameErrors) {
                showVerificationOverlay(
                    "Registration Blocked",
                    "Puppeteer found timeline name problems. Fix the name before registering.",
                    nameFindings,
                    null,
                    null
                );
                return false;
            }
            project.setName(name);
            project.setSceneEntitySnapshots(captureSceneEntitySnapshots());
            TimelineData data = project.toTimelineData(name);
            String code = CodeExporter.exportNamed(project, name);
            codePreview.setCode(code);
            boolean saved = saveTimelineFile(name, code);
            if (!saved) {
                setTitle("Puppeteer - " + name + " (save failed)");
                return false;
            }
            PuppeteerAnchorStore.save(projectRoot, project);
            VnEyeFocusProfileStore.save(projectRoot, project.getEyeFocusProfilesView().values());
            PuppeteerRigStore.save(projectRoot, project);
            TimelineRegistry.register(data);
            if (previewStaged) {
                previewStaged = false;
                previewBaselineProject = null;
                codePreview.markPreviewCommitted();
            }
            setDirty(false);
            setTitle("Puppeteer - " + name + " (saved & registered)");
            if (draftStore != null) draftStore.deleteDraft(name);
            if (workspacePrefs != null) {
                workspacePrefs.pushRecent(name, resolveRegisteredJesFile(name));
            }
            if (onSuccess != null) {
                onSuccess.run();
            }
            return true;
        } catch (Exception ex) {
            showOverlayError(
                PuppeteerErrorType.REGISTRATION,
                "Registration Failed",
                "Puppeteer could not register this timeline for runtime playback.",
                ex.getMessage(),
                ex
            );
            return false;
        }
    }

    void showVerificationOverlay(
        String title,
        String header,
        List<TimelineDiagnostic.Message> findings,
        String continueText,
        Runnable onContinue
    ) {
        VBox body = buildVerificationContent(findings);
        if (continueText != null) {
            overlayDialog.showDialog(
                title,
                header,
                body,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.accent(continueText, () -> {
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
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
        );
    }

    private void showCodeGenerationActionPreview(
        String title,
        String header,
        String actionLabel,
        List<String> details,
        Runnable onContinue
    ) {
        overlayDialog.showDialog(
            title,
            header,
            buildActionDetailsContent(details),
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.accent(actionLabel, () -> {
                if (onContinue != null) {
                    onContinue.run();
                }
            })
        );
    }

    private VBox buildActionDetailsContent(List<String> details) {
        VBox content = new VBox(8);
        content.setFillWidth(true);
        content.setMaxWidth(520);
        content.setStyle("-fx-padding: 0 0 2 0;");

        for (String detail : details == null ? List.<String>of() : details) {
            if (detail == null || detail.isBlank()) continue;
            HBox row = new HBox(8);
            row.setAlignment(Pos.TOP_LEFT);
            Label bullet = new Label("-");
            bullet.setStyle("-fx-text-fill: #7ab3e0; -fx-font-size: 12px; -fx-font-weight: bold;");
            Label text = new Label(detail.trim());
            text.setWrapText(true);
            text.setMaxWidth(470);
            text.setStyle("-fx-text-fill: #d7d7d7; -fx-font-size: 11px;");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getChildren().addAll(bullet, text);
            content.getChildren().add(row);
        }

        if (content.getChildren().isEmpty()) {
            Label fallback = new Label("Puppeteer will perform the selected action after you confirm.");
            fallback.setWrapText(true);
            fallback.setStyle("-fx-text-fill: #d7d7d7; -fx-font-size: 11px;");
            content.getChildren().add(fallback);
        }
        return content;
    }

    private VBox buildVerificationContent(List<TimelineDiagnostic.Message> findings) {
        VBox content = new VBox(8);
        content.setFillWidth(true);
        content.setMaxWidth(500);
        content.setStyle("-fx-padding: 0 0 2 0;");

        List<TimelineDiagnostic.Message> safeFindings = findings == null
            ? List.of()
            : findings.stream().filter(Objects::nonNull).toList();
        if (safeFindings.isEmpty()) {
            safeFindings = List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.INFO,
                "(timeline)",
                "No issues found.",
                ""
            ));
        }

        for (TimelineDiagnostic.Message finding : safeFindings) {
            content.getChildren().add(buildVerificationMessageRow(finding));
        }
        return content;
    }

    private Node buildVerificationMessageRow(TimelineDiagnostic.Message finding) {
        TimelineDiagnostic.Severity severity = finding.severity() == null
            ? TimelineDiagnostic.Severity.INFO
            : finding.severity();

        Label badge = new Label(verificationSeverityLabel(severity));
        badge.setMinWidth(58);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle(verificationSeverityBadgeStyle(severity));

        Label target = new Label(firstNonBlank(finding.entityOrTrack(), "(timeline)"));
        target.setWrapText(true);
        target.setStyle("-fx-text-fill: #f0f0f0; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label description = new Label(firstNonBlank(finding.description(), "No details available."));
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: #cfcfcf; -fx-font-size: 11px;");

        VBox text = new VBox(3, target, description);
        text.setFillWidth(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        String quickFix = finding.quickFix();
        if (quickFix != null && !quickFix.isBlank()) {
            Label fix = new Label("Fix: " + quickFix.trim());
            fix.setWrapText(true);
            fix.setStyle("-fx-text-fill: #9eb8d8; -fx-font-size: 11px;");
            text.getChildren().add(fix);
        }

        HBox row = new HBox(10, badge, text);
        row.setAlignment(Pos.TOP_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setStyle(
            "-fx-background-color: #111111;"
                + "-fx-background-radius: 6;"
                + "-fx-border-color: #2e2e2e;"
                + "-fx-border-radius: 6;"
                + "-fx-padding: 8 10;"
        );
        return row;
    }

    private static String verificationSeverityLabel(TimelineDiagnostic.Severity severity) {
        return switch (severity) {
            case ERROR -> "ERROR";
            case WARNING -> "WARN";
            case INFO -> "INFO";
        };
    }

    private static String verificationSeverityBadgeStyle(TimelineDiagnostic.Severity severity) {
        String colors = switch (severity) {
            case ERROR -> "-fx-background-color: #5d2430; -fx-text-fill: #ffd9df; -fx-border-color: #8a3a49;";
            case WARNING -> "-fx-background-color: #4b3a1d; -fx-text-fill: #ffe2a8; -fx-border-color: #765c2d;";
            case INFO -> "-fx-background-color: #213a4a; -fx-text-fill: #d8eefc; -fx-border-color: #365a70;";
        };
        return colors
            + " -fx-background-radius: 999;"
            + " -fx-border-radius: 999;"
            + " -fx-font-size: 10px;"
            + " -fx-font-weight: bold;"
            + " -fx-padding: 3 8;";
    }

    private boolean saveTimelineFile(String name, String jesCode) {
        if (projectRoot == null) {
            showSaveError(name, "No project root is set. Open Puppeteer from a saved JVN project before registering.");
            return false;
        }
        if (!PuppeteerVerification.isValidTimelineName(name)) {
            showSaveError(name, "Timeline name is not safe to use as a .jes filename.");
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
            PuppeteerErrorType.SAVE_IO,
            "Save Failed",
            "Could not save timeline '" + name + "' to disk.",
            detail != null ? detail : "Unknown error",
            null));
    }

    void showOverlayError(String title, String header, String detail) {
        showOverlayError(classifyPuppeteerError(title, header, detail, null), title, header, detail, null);
    }

    void showOverlayError(PuppeteerErrorType type,
                                  String title,
                                  String header,
                                  String detail,
                                  Throwable cause) {
        PuppeteerErrorType effectiveType = type == null
            ? classifyPuppeteerError(title, header, detail, cause)
            : type;
        String normalizedTitle = title == null || title.isBlank() ? "Puppeteer Error" : title.trim();
        String normalizedHeader = header == null || header.isBlank()
            ? "Puppeteer could not complete this action."
            : header.trim();
        String normalizedDetail = firstNonBlank(detail, cause != null ? cause.getMessage() : "Unknown error").trim();
        if (normalizedDetail.isBlank()) normalizedDetail = "Unknown error";
        List<String> hints = overlayErrorHints(effectiveType, normalizedHeader, normalizedDetail);

        VBox content = new VBox(10);
        content.setFillWidth(true);
        content.getChildren().addAll(
            overlaySectionLabel("Type"),
            overlayBodyLabel(effectiveType.label(), "#9eb8d8"),
            overlaySectionLabel("What happened"),
            overlayBodyLabel(normalizedHeader, "#d8d8d8"),
            overlaySectionLabel("Details"),
            overlayBodyLabel(normalizedDetail, "#e6a8b3"));
        if (!hints.isEmpty()) {
            content.getChildren().add(overlaySectionLabel("What you can try"));
            for (String hint : hints) {
                content.getChildren().add(overlayBodyLabel("- " + hint, "#b8b8b8"));
            }
        }

        String report = overlayErrorReport(effectiveType, normalizedTitle, normalizedHeader, normalizedDetail, hints, cause);
        overlayDialog.showDialog(
            normalizedTitle,
            "JVN could not complete this Puppeteer action.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Copy Details", () -> copyToClipboard(report))
                .closeOnAction(false),
            ActionEditorDialogOverlay.ActionSpec.accent("Close", overlayDialog::hideOverlay)
        );
    }

    private Label overlaySectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #f0f0f0; -fx-font-size: 11px; -fx-font-weight: bold;");
        return label;
    }

    private Label overlayBodyLabel(String text, String color) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: " + (color == null ? "#b0b0b0" : color) + "; -fx-font-size: 11px;");
        return label;
    }

    private List<String> overlayErrorHints(PuppeteerErrorType type, String header, String detail) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        String haystack = ((header == null ? "" : header) + " " + (detail == null ? "" : detail))
            .toLowerCase(Locale.ROOT);
        switch (type == null ? PuppeteerErrorType.UNKNOWN : type) {
            case VALIDATION -> hints.add("Correct the highlighted field or timeline diagnostic, then try again.");
            case PROJECT_CONTEXT -> hints.add("Open Puppeteer from a project-backed scene or reopen the project in the editor.");
            case CODE_PARSE -> hints.add("Check the edited JES timeline code for missing braces, missing quoted targets, or invalid values.");
            case EXPORT -> hints.add("Open the code pane diagnostics and fix blocking export errors before copying or registering.");
            case SAVE_IO -> hints.add("Confirm the project folder exists, is writable, and is not locked by another process.");
            case REGISTRATION -> hints.add("Run Runtime Verification and fix errors before registering for VNS interop.");
            case PREVIEW -> hints.add("Regenerate the code preview, then stage the code again once parser diagnostics are clear.");
            case ASSET -> hints.add("Relink missing image or stage assets from inside the project folder.");
            case AUDIO -> hints.add("Check the audio path, channel, and file format, then retry preview or registration.");
            case CLIP -> hints.add("Verify the clip still matches the selected track and entity/property type.");
            case CLIPBOARD -> hints.add("Try copying again after focusing the editor window.");
            case TIMELINE -> hints.add("Check timeline duration, loop region bounds, and playhead position. Use Fit Duration if needed.");
            case KEYFRAME -> hints.add("Verify keyframe times are non-negative and properly sorted. Use Timeline Diagnostics to locate conflicts.");
            case PROPERTY -> hints.add("Confirm the property is supported for the selected entity type. Check value ranges in the Keyframe Editor.");
            case ENTITY -> hints.add("Ensure the entity exists in the project and is visible in the Entities tab. Check spelling and case sensitivity.");
            case CAMERA -> hints.add("Verify camera bounds are within the viewport. Check DOF values are non-negative and within valid ranges.");
            case CUE -> hints.add("Confirm audio cue files exist and event cue payloads are valid. Check cue timing is within timeline duration.");
            case INTERPOLATION -> hints.add("Verify easing curve control points are valid. Try resetting to LINEAR or a built-in easing preset.");
            case IMPORT -> hints.add("Check the source file exists and has a valid format. Ensure you have read permissions on the file.");
            case STATE -> hints.add("Try undoing recent changes or reopening Puppeteer. Check if the command stack is corrupted.");
            case PLAYBACK -> hints.add("Verify the timeline has keyframes to play. Check if auto-key is enabled and try setting a keyframe first.");
            case UNKNOWN -> hints.add("Review the selected timeline, track, and project context, then try again.");
        }
        if (haystack.contains("project root")) {
            hints.add("Open Puppeteer from a project-backed scene or reopen the project in the editor.");
        }
        if (haystack.contains("save") || haystack.contains("disk") || haystack.contains("write")) {
            hints.add("Confirm the destination folder exists and your account can write to it.");
        }
        if (haystack.contains("parse") || haystack.contains("code") || haystack.contains("syntax")) {
            hints.add("Use the line-numbered diagnostics in the code pane to locate the bad action or property.");
        }
        if (haystack.contains("asset") || haystack.contains("image") || haystack.contains("missing")) {
            hints.add("Verify referenced files exist relative to the project root.");
        }
        if (haystack.contains("audio")) {
            hints.add("Use music, sound, or voice channels and keep audio assets under the project folder.");
        }
        if (haystack.contains("timeline name") || haystack.contains("filename")) {
            hints.add("Use a portable name like hero_intro_01 with no spaces or path separators.");
        }
        if (haystack.contains("loop") || haystack.contains("in") || haystack.contains("out")) {
            hints.add("Ensure loop IN is before loop OUT and both are within the timeline duration.");
        }
        if (haystack.contains("duration") && haystack.contains("negative")) {
            hints.add("Timeline duration must be a positive number. Use Fit Duration to auto-adjust based on keyframes.");
        }
        if (haystack.contains("snap") || haystack.contains("grid")) {
            hints.add("Adjust the snap step value or disable snapping if you need finer time resolution.");
        }
        if (hints.isEmpty()) {
            hints.add("Review the selected timeline, track, and project context, then try again.");
        }
        hints.add("Copy the details if you need to report the issue.");
        return new ArrayList<>(hints);
    }

    private String overlayErrorReport(PuppeteerErrorType type,
                                      String title,
                                      String header,
                                      String detail,
                                      List<String> hints,
                                      Throwable cause) {
        StringBuilder report = new StringBuilder();
        report.append("Title: ").append(title).append('\n');
        report.append("Type: ").append(type == null ? PuppeteerErrorType.UNKNOWN.label() : type.label()).append('\n');
        report.append("Summary: ").append(header).append('\n');
        report.append("Details: ").append(detail).append('\n');
        if (hints != null && !hints.isEmpty()) {
            report.append('\n').append("Suggested next steps:").append('\n');
            for (String hint : hints) {
                report.append("- ").append(hint).append('\n');
            }
        }
        if (cause != null) {
            report.append('\n').append("Stack trace:").append('\n');
            StringWriter stack = new StringWriter();
            cause.printStackTrace(new PrintWriter(stack));
            report.append(stack.toString().stripTrailing());
        }
        return report.toString().stripTrailing();
    }

    private PuppeteerErrorType classifyPuppeteerError(String title, String header, String detail, Throwable cause) {
        String haystack = ((title == null ? "" : title) + " "
            + (header == null ? "" : header) + " "
            + (detail == null ? "" : detail) + " "
            + (cause == null || cause.getMessage() == null ? "" : cause.getMessage()))
            .toLowerCase(Locale.ROOT);
        if (haystack.contains("duration") || haystack.contains("timeline name") || haystack.contains("validation")) {
            return PuppeteerErrorType.VALIDATION;
        }
        if (haystack.contains("project root") || haystack.contains("project-backed")) {
            return PuppeteerErrorType.PROJECT_CONTEXT;
        }
        if (haystack.contains("parse") || haystack.contains("syntax") || haystack.contains("code")) {
            return PuppeteerErrorType.CODE_PARSE;
        }
        if (haystack.contains("export") || haystack.contains("generate")) {
            return PuppeteerErrorType.EXPORT;
        }
        if (haystack.contains("save") || haystack.contains("disk") || haystack.contains("write")) {
            return PuppeteerErrorType.SAVE_IO;
        }
        if (haystack.contains("register") || haystack.contains("runtime")) {
            return PuppeteerErrorType.REGISTRATION;
        }
        if (haystack.contains("preview")) {
            return PuppeteerErrorType.PREVIEW;
        }
        if (haystack.contains("asset") || haystack.contains("image") || haystack.contains("missing")) {
            return PuppeteerErrorType.ASSET;
        }
        if (haystack.contains("audio") || haystack.contains("media")) {
            return PuppeteerErrorType.AUDIO;
        }
        if (haystack.contains("clip")) {
            return PuppeteerErrorType.CLIP;
        }
        if (haystack.contains("clipboard") || haystack.contains("copy")) {
            return PuppeteerErrorType.CLIPBOARD;
        }
        if (haystack.contains("timeline") || haystack.contains("loop") || haystack.contains("playhead")) {
            return PuppeteerErrorType.TIMELINE;
        }
        if (haystack.contains("keyframe") || haystack.contains("key") || haystack.contains("kf")) {
            return PuppeteerErrorType.KEYFRAME;
        }
        if (haystack.contains("property") || haystack.contains("prop") || haystack.contains("channel")) {
            return PuppeteerErrorType.PROPERTY;
        }
        if (haystack.contains("entity") || haystack.contains("track") || haystack.contains("sprite") || haystack.contains("character")) {
            return PuppeteerErrorType.ENTITY;
        }
        if (haystack.contains("camera") || haystack.contains("dof") || haystack.contains("zoom") || haystack.contains("pan")) {
            return PuppeteerErrorType.CAMERA;
        }
        if (haystack.contains("cue") || haystack.contains("event") || haystack.contains("marker")) {
            return PuppeteerErrorType.CUE;
        }
        if (haystack.contains("easing") || haystack.contains("interpol") || haystack.contains("curve") || haystack.contains("bezier")) {
            return PuppeteerErrorType.INTERPOLATION;
        }
        if (haystack.contains("import") || haystack.contains("load") || haystack.contains("read")) {
            return PuppeteerErrorType.IMPORT;
        }
        if (haystack.contains("state") || haystack.contains("undo") || haystack.contains("redo") || haystack.contains("command")) {
            return PuppeteerErrorType.STATE;
        }
        if (haystack.contains("playback") || haystack.contains("play") || haystack.contains("pause") || haystack.contains("stop")) {
            return PuppeteerErrorType.PLAYBACK;
        }
        return PuppeteerErrorType.UNKNOWN;
    }

    void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    void showClipboardHistoryPopup(Button sourceButton) {
        if (clipboardHistory.isEmpty()) {
            if (statusBar != null) statusBar.setText("Clipboard history is empty");
            return;
        }

        VBox content = new VBox(8);
        for (int i = 0; i < clipboardHistory.size(); i++) {
            List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry> entries = clipboardHistory.get(i);
            int index = i;
            String description = describeClipboardEntry(entries);
            Button restoreButton = buildOverlayMenuButton(
                (i == 0 ? "Current: " : "Item " + (i + 1) + ": ") + description,
                () -> restoreClipboardEntry(index)
            );
            content.getChildren().add(restoreButton);
        }

        overlayDialog.showDialog(
            "Clipboard History",
            "Select a previous clipboard entry to restore.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
        );
    }

    private String describeClipboardEntry(List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry> entries) {
        if (entries == null || entries.isEmpty()) return "Empty";
        int count = entries.size();
        String entity = entries.get(0).sourceName();
        if (entity == null || entity.isBlank()) entity = "Unknown";
        return count + " keyframe" + (count > 1 ? "s" : "") + " from " + entity;
    }

    private void restoreClipboardEntry(int index) {
        if (index < 0 || index >= clipboardHistory.size()) return;
        List<com.jvn.editor.ui.actioneditor.TimelinePanel.ClipboardEntry> entries = clipboardHistory.get(index);
        timelinePanel.setCopiedKeyframes(entries);
        overlayDialog.hideOverlay();
        if (statusBar != null) statusBar.setText("Restored clipboard entry " + (index + 1));
        refreshToolbarCommandSummary();
    }

    private void showAutoSaveIndicator(String timelineName) {
        if (statusBar == null) return;
        statusBar.setText("Auto-saved: " + timelineName);
        javafx.util.Duration duration = javafx.util.Duration.seconds(3);
        javafx.animation.KeyFrame keyFrame = new javafx.animation.KeyFrame(duration, e -> {
            if (statusBar != null) statusBar.setText("");
        });
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(keyFrame);
        timeline.play();
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
        project.setSceneEntitySnapshots(captureSceneEntitySnapshots());
    }

    private List<AnimationProject.SceneEntitySnapshot> captureSceneEntitySnapshots() {
        if (scene == null) return List.of();
        List<AnimationProject.SceneEntitySnapshot> snapshots = new ArrayList<>();
        SpritePixelAnalyzer pixelAnalyzer = new SpritePixelAnalyzer(projectRoot);
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;

            String type = "entity";
            String imagePath = "";
            String rawImagePath = "";
            double width = 1.0;
            double height = 1.0;
            double alpha = snapshotOrCurrent(entityName, PropertyType.ALPHA, fallbackPropertyValue(entity, PropertyType.ALPHA));
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                type = "sprite";
                rawImagePath = sprite.getImagePath();
                imagePath = relativizePreviewAssetPathSpec(rawImagePath);
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

            double x = snapshotOrCurrent(entityName, PropertyType.X, entity.getX());
            double y = snapshotOrCurrent(entityName, PropertyType.Y, entity.getY());
            double originX = snapshotOrCurrent(entityName, PropertyType.PIVOT_X, entity.getOriginX());
            double originY = snapshotOrCurrent(entityName, PropertyType.PIVOT_Y, entity.getOriginY());
            double runtimeBaseX = runtimeExportBaselineOrSnapshot(entityName, PropertyType.X, x);
            double runtimeBaseY = runtimeExportBaselineOrSnapshot(entityName, PropertyType.Y, y);
            double[] visualBounds = computeSnapshotVisualBounds(
                pixelAnalyzer,
                rawImagePath,
                x,
                y,
                width,
                height,
                originX,
                originY
            );
            snapshots.add(new AnimationProject.SceneEntitySnapshot(
                entityName,
                type,
                imagePath,
                x,
                y,
                width,
                height,
                originX,
                originY,
                snapshotOrCurrent(entityName, PropertyType.Z, entity.getZ()),
                snapshotOrCurrent(entityName, PropertyType.VISIBILITY, entity.isVisible() ? 1.0 : 0.0) >= 0.5,
                alpha,
                visualBounds[0],
                visualBounds[1],
                visualBounds[2],
                visualBounds[3],
                runtimeBaseX,
                runtimeBaseY
            ));
        }
        return snapshots;
    }

    private double[] computeSnapshotVisualBounds(
        SpritePixelAnalyzer pixelAnalyzer,
        String imagePathSpec,
        double x,
        double y,
        double width,
        double height,
        double originX,
        double originY
    ) {
        double fallbackMinX = x - originX * width;
        double fallbackMinY = y - originY * height;
        double fallbackMaxX = fallbackMinX + width;
        double fallbackMaxY = fallbackMinY + height;
        if (pixelAnalyzer == null || imagePathSpec == null || imagePathSpec.isBlank()) {
            return new double[]{fallbackMinX, fallbackMinY, fallbackMaxX, fallbackMaxY};
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (String layerPath : imagePathSpec.split("\\|")) {
            String path = layerPath == null ? "" : layerPath.trim();
            if (path.isEmpty()) continue;
            List<SpritePixelAnalyzer.DetectedRegion> regions = pixelAnalyzer.detectRegions(path);
            if (regions.isEmpty()) continue;
            javafx.scene.image.Image image = loadAssetImage(path);
            double imageW = image != null && !image.isError() && image.getWidth() > 1.0
                ? image.getWidth()
                : width;
            double imageH = image != null && !image.isError() && image.getHeight() > 1.0
                ? image.getHeight()
                : height;
            double imgToSpriteX = width / Math.max(1.0, imageW);
            double imgToSpriteY = height / Math.max(1.0, imageH);
            for (SpritePixelAnalyzer.DetectedRegion region : regions) {
                double regionMinX = fallbackMinX + region.minX * imgToSpriteX;
                double regionMinY = fallbackMinY + region.minY * imgToSpriteY;
                double regionMaxX = fallbackMinX + (region.minX + region.width) * imgToSpriteX;
                double regionMaxY = fallbackMinY + (region.minY + region.height) * imgToSpriteY;
                minX = Math.min(minX, regionMinX);
                minY = Math.min(minY, regionMinY);
                maxX = Math.max(maxX, regionMaxX);
                maxY = Math.max(maxY, regionMaxY);
            }
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minY)
            || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            return new double[]{fallbackMinX, fallbackMinY, fallbackMaxX, fallbackMaxY};
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    private double snapshotOrCurrent(String entityName, PropertyType property, double fallback) {
        Double value = project.getInitialSnapshotValue(entityName, property);
        return value != null && Double.isFinite(value) ? value : fallback;
    }

    private double runtimeExportBaselineOrSnapshot(String entityName, PropertyType property, double fallback) {
        if (entityName != null && property != null) {
            Map<PropertyType, Double> props = runtimeExportBaselines.get(entityName);
            Double value = props == null ? null : props.get(property);
            if (value != null && Double.isFinite(value)) {
                return value;
            }
        }
        return fallback;
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

    Set<String> knownSceneEntities() {
        if (scene == null) return null;
        Set<String> names = new LinkedHashSet<>();
        for (String name : scene.names()) {
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names.isEmpty() ? null : names;
    }

    public void stagePreviewFromCode() {
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
                PuppeteerErrorType.CODE_PARSE,
                "Preview Failed",
                "Could not parse the edited code.",
                ex.getMessage(),
                ex));
        }
    }

    public void commitStagedPreview() {
        if (!previewStaged) return;
        previewStaged = false;
        previewBaselineProject = null;
        codePreview.markPreviewCommitted();
        setDirty(true);
        refreshExportPreview();
    }

    public void discardStagedPreview() {
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
        Map<String, Constraint> constraintSnapshot = project.getConstraintsView();
        Map<String, Map<String, Anchor>> namedAnchorSnapshot = project.getEntityAnchorsView();
        boolean importedHasOrbitAnchors = !imported.getOrbitAnchorsView().isEmpty();
        boolean importedHasConstraints = !imported.getConstraintsView().isEmpty();
        boolean importedHasNamedAnchors = !imported.getEntityAnchorsView().isEmpty();
        project.replaceFrom(imported);
        if (!importedHasOrbitAnchors) {
            project.setOrbitAnchors(anchorSnapshot);
            project.setOrbitAnchorSources(anchorSourceSnapshot);
            project.setOrbitAnchorSourceOffsets(anchorOffsetSnapshot);
        }
        if (!importedHasConstraints && !constraintSnapshot.isEmpty()) {
            project.clearConstraints();
            for (Map.Entry<String, Constraint> entry : constraintSnapshot.entrySet()) {
                project.setConstraint(entry.getKey(), entry.getValue());
            }
        }
        if (!importedHasNamedAnchors && !namedAnchorSnapshot.isEmpty()) {
            project.clearAllAnchors();
            for (Map.Entry<String, Map<String, Anchor>> entry : namedAnchorSnapshot.entrySet()) {
                for (Anchor anchor : entry.getValue().values()) {
                    project.setAnchor(entry.getKey(), anchor);
                }
            }
        }
        project.pruneOrbitAnchors(collectProjectAnchorTargetNames());
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

    private Set<String> collectProjectAnchorTargetNames() {
        Set<String> names = collectProjectEntityNames();
        for (EntityGroup group : project.getGroups()) {
            if (group == null) continue;
            String name = group.getName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names;
    }

    private Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> captureTransformSnapshots(double timeMs) {
        return captureTransformSnapshots(null, timeMs);
    }

    private Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> captureTransformSnapshots(String primaryEntityName, double timeMs) {
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> snapshots = new LinkedHashMap<>();
        if (primaryEntityName != null && !primaryEntityName.isBlank()) {
            EntityGroup primaryGroup = project.getGroup(primaryEntityName);
            if (primaryGroup != null) {
                snapshots.put(primaryEntityName,
                    captureTrackSnapshots(primaryGroup.getGroupTrack(), timeMs, TRANSFORM_INTERACTION_PROPERTIES, null));
                return snapshots;
            }
        }
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
            case MIRROR_X -> property.getDefaultValue();
            case ALPHA -> getEntityAlpha(entity);
            case VISIBILITY -> entity.isVisible() ? 1.0 : 0.0;
            case MATRIX_MXX -> entity.getMatrixMxx();
            case MATRIX_MXY -> entity.getMatrixMxy();
            case MATRIX_MYX -> entity.getMatrixMyx();
            case MATRIX_MYY -> entity.getMatrixMyy();
            case MATRIX_TX -> entity.getMatrixTx();
            case MATRIX_TY -> entity.getMatrixTy();
            case BLUR -> entity.getBlurRadius();
            case BRIGHTNESS -> entity.getBrightness();
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
        entity.setBrightness(1.0);
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

    private static String snapshotCharacterGroupName(PuppeteerLauncherPanel.CharacterEntry character) {
        return PuppeteerLauncherPanel.snapshotCharacterGroupName(character);
    }

    private static String selectorSafeName(String raw) {
        return PuppeteerLauncherPanel.selectorSafeName(raw);
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

    public EntityTrack selectedTrackForEditing(boolean createEntityTrack) {
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

    private void collectResetCommands(EntityGroup group, double time, java.util.List<PuppeteerCommand> commands) {
        if (group == null || this.project == null) return;

        if (!group.isLocked()) {
            EntityTrack groupTrack = group.getGroupTrack();
            if (groupTrack != null) {
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.X, time, PropertyType.X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.Y, time, PropertyType.Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.Z, time, PropertyType.Z.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.PIVOT_X, time, PropertyType.PIVOT_X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.PIVOT_Y, time, PropertyType.PIVOT_Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.ROTATION, time, PropertyType.ROTATION.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.SCALE_X, time, PropertyType.SCALE_X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.SCALE_Y, time, PropertyType.SCALE_Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.MIRROR_X, time, PropertyType.MIRROR_X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(groupTrack, PropertyType.ALPHA, time, PropertyType.ALPHA.getDefaultValue()));
            }
        }
        
        for (String childEntity : group.getChildEntityNames()) {
            EntityTrack track = this.project.getTrack(childEntity);
            if (track != null && !track.isLocked()) {
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.X, time, PropertyType.X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, time, PropertyType.Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.ROTATION, time, PropertyType.ROTATION.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.SCALE_X, time, PropertyType.SCALE_X.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.SCALE_Y, time, PropertyType.SCALE_Y.getDefaultValue()));
                commands.add(PuppeteerCommand.upsertKeyframe(track, PropertyType.MIRROR_X, time, PropertyType.MIRROR_X.getDefaultValue()));
            }
        }
        for (String childGroup : group.getChildGroupNames()) {
            EntityGroup child = this.project.getGroup(childGroup);
            if (child != null && !child.isLocked()) {
                collectResetCommands(child, time, commands);
            }
        }
    }
}
