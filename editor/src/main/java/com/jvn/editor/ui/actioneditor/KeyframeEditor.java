package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.editor.ui.ProjectViewportSpec;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class KeyframeEditor extends VBox {
    enum SelectionTargetKind {
        NONE,
        ENTITY,
        GROUP,
        RUNTIME_CAMERA
    }

    private final Label lblTargetCaption;
    private final Label lblEntity;
    private final Label lblProperty;
    private final Label lblTargetContextHint;
    private final Label lblSelectionMode;
    private final TextField tfTime;
    private final Slider sliderTime;
    private final TextField tfValue;
    private final Slider sliderValue;
    private final ComboBox<Easing.Interpolation> cbInterpolation;
    private final PuppeteerEasingComboBox cbEasing;
    private final Button btnPresetLibrary;
    private final Button btnEditCurve;
    private final Button btnUpdateCurvePreset;
    private final Button btnExpandCurveEditor;
    private final Button btnResetCurve;
    private final Button btnReverseCurve;
    private final Button btnClampCurve;
    private final Button btnDelete;
    private final Button btnResetValue;
    private final Label lblCameraBoxTitle;
    private final Label lblCameraState;
    private final Label lblCameraViewportState;
    private final Label lblCurvePresetHint;
    private final Label lblCurveInteractionHint;
    private final Label lblTimeStepHint;
    private final Label lblValueStepHint;
    private final TextField tfCurveX1;
    private final TextField tfCurveY1;
    private final TextField tfCurveX2;
    private final TextField tfCurveY2;
    private final TextField tfCurveSpec;
    private final Button btnApplyCurveSpec;
    private final EasingCurveEditor curveEditor;
    private final PuppeteerEasingPresetLibraryPanel presetLibraryPanel;
    private final GridPane pivotPresetsGrid;
    private final Label lblPivotPresets;
    private final VBox pivotBox;
    private final VBox batchBox;
    private final Label lblSelectionSummary;
    private final TextField tfTimeOffset;
    private final TextField tfValueOffset;
    private final Button btnApplyBatch;
    private final List<Button> curveQuickPresetButtons = new ArrayList<>();
    private final Button btnPlayStopAnim;
    private final SpringParameterEditor springParamEditor;
    private final List<Keyframe> adjacentKeyframes = new ArrayList<>();

    private static final String PANEL_STYLE =
        "-fx-background-color: #181818; -fx-border-color: #2a2a2a; -fx-border-width: 1 0 0 0;";
    private static final String HEADER_STYLE =
        "-fx-font-weight: bold; -fx-text-fill: #efefef; -fx-font-size: 13px;";
    private static final String FIELD_STYLE =
        "-fx-background-color: #111111; -fx-text-fill: #ececec; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 5 8; -fx-font-size: 12px;";
    private static final String FIELD_STYLE_ERROR =
        "-fx-background-color: #111111; -fx-text-fill: #ececec; -fx-border-color: #e05577; " +
        "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 5 8; -fx-font-size: 12px;";
    private static final String SECTION_STYLE =
        "-fx-background-color: #121212; -fx-border-color: #2f2f2f; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 10 12;";
    private static final String TILE_STYLE =
        "-fx-background-color: #121212; -fx-border-color: #2f2f2f; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 8 10;";
    private static final String SECTION_TITLE_STYLE =
        "-fx-text-fill: #ececec; -fx-font-size: 11px; -fx-font-weight: bold;";
    private static final String LABEL_STYLE =
        "-fx-text-fill: #a0a0a0; -fx-font-size: 11px;";
    private static final String META_CAPTION_STYLE =
        "-fx-text-fill: #7f7f7f; -fx-font-size: 10px; -fx-font-weight: bold;";
    private static final String META_VALUE_STYLE =
        "-fx-text-fill: #f0f0f0; -fx-font-size: 16px; -fx-font-weight: bold;";
    private static final String SUBTLE_HINT_STYLE =
        "-fx-text-fill: #909090; -fx-font-size: 10px;";
    private static final String SECONDARY_BUTTON_STYLE =
        "-fx-background-color: #242424; -fx-text-fill: #e6e6e6; -fx-background-radius: 6; " +
        "-fx-border-color: #3d3d3d; -fx-border-radius: 6; -fx-padding: 5 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String TOGGLE_BUTTON_STYLE =
        "-fx-background-color: #3b3b3b; -fx-text-fill: #f1f1f1; -fx-background-radius: 6; " +
        "-fx-border-color: #6a6a6a; -fx-border-radius: 6; -fx-padding: 5 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String SUCCESS_BUTTON_STYLE =
        "-fx-background-color: #294229; -fx-text-fill: #dff3df; -fx-background-radius: 6; " +
        "-fx-border-color: #446644; -fx-border-radius: 6; -fx-padding: 5 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String DANGER_BUTTON_STYLE =
        "-fx-background-color: #6b3038; -fx-text-fill: #ffe6ea; -fx-background-radius: 6; " +
        "-fx-border-color: #904652; -fx-border-radius: 6; -fx-padding: 5 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String BADGE_IDLE_STYLE =
        "-fx-background-color: #1d1d1d; -fx-text-fill: #8f8f8f; -fx-border-color: #3b3b3b; " +
        "-fx-border-radius: 999; -fx-background-radius: 999; -fx-padding: 4 10; -fx-font-size: 10px; -fx-font-weight: bold;";
    private static final String BADGE_ACTIVE_STYLE =
        "-fx-background-color: #2b2b2b; -fx-text-fill: #ececec; -fx-border-color: #616161; " +
        "-fx-border-radius: 999; -fx-background-radius: 999; -fx-padding: 4 10; -fx-font-size: 10px; -fx-font-weight: bold;";
    private static final String BADGE_MULTI_STYLE =
        "-fx-background-color: #2f2a21; -fx-text-fill: #f0d89b; -fx-border-color: #736040; " +
        "-fx-border-radius: 999; -fx-background-radius: 999; -fx-padding: 4 10; -fx-font-size: 10px; -fx-font-weight: bold;";
    private static final double EDITOR_WORKING_WIDTH = 600.0;

    private final Label lblEmptyHint;
    private final VBox contentBox;
    private File projectRoot;
    private double timelineDurationMs = 3000.0;
    private Keyframe currentKeyframe;
    private PropertyType currentProperty;
    private final List<Keyframe> currentSelection = new ArrayList<>();
    private boolean updatingUi = false;
    private boolean curveEditorExpanded = false;
    private SelectionTargetKind selectionTargetKind = SelectionTargetKind.NONE;
    private String activePresetEditId;
    private String activePresetEditName;
    private EasingSpec curveEditBaselineSpec = EasingSpec.cubicBezier(0.25, 0.10, 0.25, 1.00);
    private Runnable onKeyframeChanged;
    private Runnable onDeleteRequested;
    private java.util.function.BiConsumer<Double, Double> onPivotPresetApplied;
    private java.util.function.Consumer<Boolean> onCurveEditorExpandedChanged;

    public KeyframeEditor() {
        setSpacing(10);
        setPadding(new Insets(10, 10, 8, 10));
        setFillWidth(true);
        setStyle(PANEL_STYLE);
        setMinHeight(200);

        Label header = new Label("Keyframe Editor");
        header.setStyle(HEADER_STYLE);

        lblSelectionMode = new Label("No Selection");
        lblSelectionMode.setStyle(BADGE_IDLE_STYLE);

        HBox headerRow = new HBox(8, header, createSpacer(), lblSelectionMode);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        lblEmptyHint = new Label("Select a keyframe in the timeline to edit timing, value, easing, and curve behavior here.");
        lblEmptyHint.setStyle(SECTION_STYLE + " -fx-text-fill: #717171; -fx-font-size: 11px;");
        lblEmptyHint.setWrapText(true);

        contentBox = new VBox(10);
        contentBox.setFillWidth(true);
        contentBox.setMaxWidth(Double.MAX_VALUE);

        lblTargetCaption = new Label("Target");
        lblTargetCaption.setStyle(META_CAPTION_STYLE);
        lblEntity = new Label("-");
        lblEntity.setStyle(META_VALUE_STYLE);
        lblEntity.setWrapText(true);
        lblEntity.setMaxWidth(Double.MAX_VALUE);
        lblProperty = new Label("-");
        lblProperty.setStyle(META_VALUE_STYLE);
        lblProperty.setWrapText(true);
        lblProperty.setMaxWidth(Double.MAX_VALUE);
        lblTargetContextHint = new Label();
        lblTargetContextHint.setStyle(SUBTLE_HINT_STYLE);
        lblTargetContextHint.setWrapText(true);
        lblTargetContextHint.setVisible(false);
        lblTargetContextHint.setManaged(false);

        tfTime = new TextField();
        tfTime.setPromptText("ms");
        tfTime.setPrefWidth(86);
        tfTime.setStyle(FIELD_STYLE);
        sliderTime = new Slider(0, 3000, 0);
        sliderTime.setTooltip(new Tooltip("Drag to adjust keyframe time"));
        HBox timeRow = new HBox(8, tfTime, sliderTime);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(sliderTime, Priority.ALWAYS);

        tfValue = new TextField();
        tfValue.setPromptText("value");
        tfValue.setPrefWidth(96);
        tfValue.setStyle(FIELD_STYLE);
        sliderValue = new Slider(-2000, 2000, 0);
        sliderValue.setTooltip(new Tooltip("Drag to adjust value"));
        HBox valueRow = new HBox(8, tfValue, sliderValue);
        valueRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(sliderValue, Priority.ALWAYS);

        cbEasing = new PuppeteerEasingComboBox();
        cbEasing.setCurrentSpecSupplier(this::resolveEditorEasingSpec);
        cbEasing.setCurrentSpec(EasingSpec.of(Easing.Type.LINEAR));
        btnPresetLibrary = new Button("Show Library");
        btnPresetLibrary.setTooltip(new Tooltip("Open the project easing preset library"));
        btnPresetLibrary.setStyle(SECONDARY_BUTTON_STYLE);
        cbInterpolation = new ComboBox<>();
        cbInterpolation.getItems().addAll(Easing.Interpolation.values());
        cbInterpolation.setValue(Easing.Interpolation.TWEEN);
        cbInterpolation.setMaxWidth(Double.MAX_VALUE);
        cbInterpolation.setStyle(FIELD_STYLE);
        btnEditCurve = new Button("Make Editable");
        btnEditCurve.setTooltip(new Tooltip("Convert the current easing into an editable cubic bezier"));
        btnEditCurve.setStyle(SECONDARY_BUTTON_STYLE);
        btnUpdateCurvePreset = new Button("Update Preset");
        btnUpdateCurvePreset.setTooltip(new Tooltip("Write the edited curve back into the selected preset"));
        btnUpdateCurvePreset.setStyle(SUCCESS_BUTTON_STYLE);
        btnExpandCurveEditor = new Button("Expand");
        btnExpandCurveEditor.setTooltip(new Tooltip("Grow the curve editor inside the left panel"));
        btnExpandCurveEditor.setStyle(SECONDARY_BUTTON_STYLE);
        btnResetCurve = new Button("Reset Curve");
        btnResetCurve.setTooltip(new Tooltip("Reset the editable curve back to the easing you started from"));
        btnResetCurve.setStyle(SECONDARY_BUTTON_STYLE);
        btnReverseCurve = new Button("Reverse");
        btnReverseCurve.setTooltip(new Tooltip("Reverse the current curve in time to swap ease-in and ease-out feel"));
        btnReverseCurve.setStyle(SECONDARY_BUTTON_STYLE);
        btnClampCurve = new Button("Clamp Y");
        btnClampCurve.setTooltip(new Tooltip("Clamp both curve Y handles into 0..1 to remove overshoot and undershoot"));
        btnClampCurve.setStyle(SECONDARY_BUTTON_STYLE);
        lblCurvePresetHint = new Label();
        lblCurvePresetHint.setStyle(SUBTLE_HINT_STYLE);
        lblCurvePresetHint.setWrapText(true);
        lblCurveInteractionHint = new Label();
        lblCurveInteractionHint.setStyle(SUBTLE_HINT_STYLE);
        lblCurveInteractionHint.setWrapText(true);

        lblTimeStepHint = new Label();
        lblTimeStepHint.setStyle(SUBTLE_HINT_STYLE);
        lblValueStepHint = new Label();
        lblValueStepHint.setStyle(SUBTLE_HINT_STYLE);

        tfCurveX1 = createCurveParamField("x1", "First control point X. Use Up/Down or mouse wheel to nudge.");
        tfCurveY1 = createCurveParamField("y1", "First control point Y. Hold Shift for larger nudges.");
        tfCurveX2 = createCurveParamField("x2", "Second control point X. Use Up/Down or mouse wheel to nudge.");
        tfCurveY2 = createCurveParamField("y2", "Second control point Y. Hold Shift for larger nudges.");
        tfCurveSpec = new TextField();
        tfCurveSpec.setPromptText("cubic-bezier(0.25, 0.1, 0.25, 1.0)");
        tfCurveSpec.setStyle(FIELD_STYLE);
        tfCurveSpec.setTooltip(new Tooltip("Paste cubic-bezier(...) or any easing token to convert it into an editable curve."));
        btnApplyCurveSpec = new Button("Apply Spec");
        btnApplyCurveSpec.setTooltip(new Tooltip("Apply the curve spec above as an editable bezier for the selected keyframe"));
        btnApplyCurveSpec.setStyle(SECONDARY_BUTTON_STYLE);

        presetLibraryPanel = new PuppeteerEasingPresetLibraryPanel();
        presetLibraryPanel.setCurrentSpecSupplier(this::resolveEditorEasingSpec);
        presetLibraryPanel.setSelectedEntrySupplier(cbEasing::getSelectedEntry);
        presetLibraryPanel.setOnPresetApplied(spec -> {
            applyExplicitEasingSpec(spec);
            syncCurveEditBaseline(spec);
            syncPresetEditSessionFromSelection(false);
            refreshPresetUiState();
            if (onKeyframeChanged != null) onKeyframeChanged.run();
        });
        presetLibraryPanel.setOnLibraryChanged(() -> {
            EasingSpec current = resolveEditorEasingSpec();
            cbEasing.reloadProjectPresets();
            cbEasing.setCurrentSpec(current);
            syncPresetEditSessionFromSelection(false);
            refreshPresetUiState();
        });

        btnDelete = new Button("Delete");
        btnDelete.setStyle(DANGER_BUTTON_STYLE);
        btnDelete.setTooltip(new Tooltip("Delete this keyframe (Del)"));

        btnResetValue = new Button("Reset Value");
        btnResetValue.setTooltip(new Tooltip("Reset to property default"));
        btnResetValue.setStyle(SECONDARY_BUTTON_STYLE);

        lblSelectionSummary = new Label("No multi-selection");
        lblSelectionSummary.setStyle(LABEL_STYLE);
        tfTimeOffset = new TextField("0");
        tfTimeOffset.setPromptText("+/- ms");
        tfTimeOffset.setPrefWidth(70);
        tfTimeOffset.setStyle(FIELD_STYLE);
        tfTimeOffset.setTooltip(new Tooltip("Add this delta to all selected keyframe times"));
        tfValueOffset = new TextField("0");
        tfValueOffset.setPromptText("+/- value");
        tfValueOffset.setPrefWidth(70);
        tfValueOffset.setStyle(FIELD_STYLE);
        tfValueOffset.setTooltip(new Tooltip("Add this delta to all selected keyframe values"));
        btnApplyBatch = new Button("Apply Offsets");
        btnApplyBatch.setStyle(SECONDARY_BUTTON_STYLE);
        btnApplyBatch.setTooltip(new Tooltip("Apply time and value offsets to the current multi-selection"));

        VBox batchTimeBox = buildLabeledControl("Time Offset", tfTimeOffset);
        VBox batchValueBox = buildLabeledControl("Value Offset", tfValueOffset);
        HBox batchOffsets = new HBox(10, batchTimeBox, batchValueBox, createSpacer(), btnApplyBatch);
        batchOffsets.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(batchTimeBox, Priority.ALWAYS);
        HBox.setHgrow(batchValueBox, Priority.ALWAYS);
        batchBox = buildSection("Batch Adjustments", lblSelectionSummary, batchOffsets);
        batchBox.setVisible(false);
        batchBox.setManaged(false);

        lblPivotPresets = new Label("Pivot Anchors");
        lblPivotPresets.setStyle(SECTION_TITLE_STYLE);
        pivotPresetsGrid = buildPivotPresetsGrid();
        Label pivotHint = new Label("Quick positions apply both pivot axes together.");
        pivotHint.setStyle(SUBTLE_HINT_STYLE);
        pivotBox = buildSection(lblPivotPresets, pivotHint, pivotPresetsGrid);
        pivotBox.setVisible(false);
        pivotBox.setManaged(false);

        curveEditor = new EasingCurveEditor();
        curveEditor.setOnBezierChanged(params -> {
            if (currentKeyframe != null && currentKeyframe.getEasing() == Easing.Type.CUSTOM) {
                applyCurveSpec(EasingSpec.cubicBezier(params[0], params[1], params[2], params[3]), true);
            }
        });

        // --- B) Play/Stop animation preview button ---
        btnPlayStopAnim = new Button("▶ Preview");
        btnPlayStopAnim.setStyle(SECONDARY_BUTTON_STYLE);
        btnPlayStopAnim.setTooltip(new Tooltip("Animate a dot along the curve to preview the motion feel"));
        btnPlayStopAnim.setOnAction(e -> {
            curveEditor.toggleAnimation();
            boolean playing = curveEditor.isAnimating();
            btnPlayStopAnim.setText(playing ? "■ Stop" : "▶ Preview");
            btnPlayStopAnim.setStyle(playing ? TOGGLE_BUTTON_STYLE : SECONDARY_BUTTON_STYLE);
        });

        // --- C) Spring parameter editor ---
        springParamEditor = new SpringParameterEditor();
        springParamEditor.setVisible(false);
        springParamEditor.setManaged(false);
        springParamEditor.setOnSpecChanged(spec -> {
            if (currentKeyframe == null) return;
            currentKeyframe.setEasingSpec(spec);
            curveEditor.setEasingSpec(spec);
            syncCurveSpecField(spec, false);
            if (onKeyframeChanged != null) onKeyframeChanged.run();
        });

        VBox entityTile = buildMetaTile(lblTargetCaption, lblEntity);
        VBox propertyTile = buildMetaTile("Property", lblProperty);
        HBox infoRow = new HBox(10, entityTile, propertyTile);
        infoRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(entityTile, Priority.ALWAYS);
        HBox.setHgrow(propertyTile, Priority.ALWAYS);
        VBox targetMetaBox = new VBox(6, infoRow, lblTargetContextHint);
        targetMetaBox.setMaxWidth(Double.MAX_VALUE);

        VBox timeEditor = buildEditorRow("Time", "Timeline position", timeRow, lblTimeStepHint);
        VBox valueEditor = buildEditorRow("Value", "Current property value", valueRow, lblValueStepHint);
        Label nudgeHint = new Label("Arrow keys or mouse wheel nudge the focused field. Hold Shift for larger steps.");
        nudgeHint.setStyle(SUBTLE_HINT_STYLE);
        VBox valueSection = buildSection("Keyframe Values", timeEditor, valueEditor, nudgeHint);

        HBox easingPickerRow = new HBox(8, cbEasing, btnPresetLibrary);
        easingPickerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cbEasing, Priority.ALWAYS);

        VBox interpolationBox = buildLabeledControl("Interpolation", cbInterpolation);
        interpolationBox.setMinWidth(150);
        VBox easingBox = buildLabeledControl("Easing", easingPickerRow);
        HBox easingSectionRow = new HBox(10, interpolationBox, easingBox);
        easingSectionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(easingBox, Priority.ALWAYS);
        Label easingHint = new Label("Tweens can use library presets or be converted into an editable bezier curve.");
        easingHint.setStyle(SUBTLE_HINT_STYLE);
        VBox easingSection = buildSection("Interpolation + Easing", easingSectionRow, easingHint);

        HBox curveToolbar = new HBox(8, new Label("Curve Workspace"), createSpacer(), btnPlayStopAnim, btnEditCurve, btnUpdateCurvePreset, btnExpandCurveEditor);
        curveToolbar.setAlignment(Pos.CENTER_LEFT);
        ((Label) curveToolbar.getChildren().get(0)).setStyle(SECTION_TITLE_STYLE);
        FlowPane curveQuickPresetBar = new FlowPane(8, 8);
        curveQuickPresetBar.getChildren().addAll(
            createCurveQuickPresetButton("Linear", "0,0 → 1,1 straight timing", EasingSpec.cubicBezier(0.0, 0.0, 1.0, 1.0)),
            createCurveQuickPresetButton("Ease", "Standard balanced ease curve", EasingSpec.cubicBezier(0.25, 0.10, 0.25, 1.00)),
            createCurveQuickPresetButton("Ease In", "Fast finish with slower start", EasingSpec.cubicBezier(0.42, 0.0, 1.0, 1.0)),
            createCurveQuickPresetButton("Ease Out", "Fast start with softer finish", EasingSpec.cubicBezier(0.0, 0.0, 0.58, 1.0)),
            createCurveQuickPresetButton("Ease In-Out", "Symmetric ease in and out", EasingSpec.cubicBezier(0.42, 0.0, 0.58, 1.0)),
            createCurveQuickPresetButton("Overshoot", "A punchy back-style overshoot curve", EasingSpec.cubicBezier(0.34, 1.56, 0.64, 1.0))
        );
        curveQuickPresetBar.setMaxWidth(Double.MAX_VALUE);
        FlowPane curveTransformBar = new FlowPane(8, 8);
        curveTransformBar.getChildren().addAll(btnReverseCurve, btnClampCurve);
        curveTransformBar.setMaxWidth(Double.MAX_VALUE);
        HBox curveSpecRow = new HBox(
            8,
            buildLabeledControl("Curve Spec", tfCurveSpec),
            btnApplyCurveSpec
        );
        curveSpecRow.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(curveSpecRow.getChildren().get(0), Priority.ALWAYS);
        HBox curveParamsRow = new HBox(
            8,
            buildLabeledControl("x1", tfCurveX1),
            buildLabeledControl("y1", tfCurveY1),
            buildLabeledControl("x2", tfCurveX2),
            buildLabeledControl("y2", tfCurveY2),
            createSpacer(),
            btnResetCurve
        );
        curveParamsRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setVgrow(curveEditor, Priority.ALWAYS);
        VBox curveSection = new VBox(
            8,
            curveToolbar,
            lblCurvePresetHint,
            curveQuickPresetBar,
            curveTransformBar,
            curveSpecRow,
            curveParamsRow,
            lblCurveInteractionHint,
            curveEditor
        );
        curveSection.setStyle(SECTION_STYLE);
        curveSection.setMaxWidth(Double.MAX_VALUE);

        lblCameraBoxTitle = new Label("Runtime Camera");
        lblCameraBoxTitle.setStyle(META_CAPTION_STYLE);
        lblCameraState = new Label("X 0.0  Y 0.0  Z 1.00");
        lblCameraState.setStyle("-fx-text-fill: #d5b27f; -fx-font-size: 11px; -fx-font-family: monospace;");
        lblCameraViewportState = new Label("Frame 1920 x 1080");
        lblCameraViewportState.setStyle(SUBTLE_HINT_STYLE);
        VBox cameraBox = new VBox(2, lblCameraBoxTitle, lblCameraState, lblCameraViewportState);
        cameraBox.setStyle(TILE_STYLE);
        cameraBox.setMinWidth(180);

        HBox footerRow = new HBox(8, cameraBox, createSpacer(), btnResetValue, btnDelete);
        footerRow.setAlignment(Pos.CENTER_LEFT);

        contentBox.getChildren().addAll(
            targetMetaBox,
            valueSection,
            easingSection,
            curveSection,
            springParamEditor,
            presetLibraryPanel,
            batchBox,
            pivotBox,
            footerRow
        );
        contentBox.setVisible(false);
        contentBox.setManaged(false);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        updateStepHints();

        VBox editorBody = new VBox(10, headerRow, lblEmptyHint, contentBox);
        editorBody.setFillWidth(true);
        editorBody.setMinWidth(0);
        editorBody.setPrefWidth(EDITOR_WORKING_WIDTH);
        editorBody.setMaxWidth(Double.MAX_VALUE);

        ScrollPane editorScrollPane = new ScrollPane(editorBody);
        editorScrollPane.setMinWidth(0);
        editorScrollPane.setPrefViewportWidth(EDITOR_WORKING_WIDTH);
        editorScrollPane.setPrefWidth(EDITOR_WORKING_WIDTH);
        editorScrollPane.setMaxWidth(Double.MAX_VALUE);
        editorScrollPane.setFitToWidth(true);
        editorScrollPane.setFitToHeight(false);
        // This pane hosts sliders and curve-handle drags, so mouse-drag panning fights the controls.
        editorScrollPane.setPannable(false);
        editorScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox.setVgrow(editorScrollPane, Priority.ALWAYS);
        getChildren().add(editorScrollPane);

        tfTime.setTooltip(new Tooltip("Direct time edit. Use Up/Down or mouse wheel to nudge."));
        tfValue.setTooltip(new Tooltip("Direct value edit. Use Up/Down or mouse wheel to nudge."));
        tfTimeOffset.setTooltip(new Tooltip("Add this delta to all selected keyframe times"));
        tfValueOffset.setTooltip(new Tooltip("Add this delta to all selected keyframe values"));

        tfTime.setOnAction(e -> applyChanges());
        tfValue.setOnAction(e -> applyChanges());
        tfTime.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) applyChanges();
        });
        tfValue.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) applyChanges();
        });
        tfTimeOffset.setOnAction(e -> applyBatchOffsets());
        tfValueOffset.setOnAction(e -> applyBatchOffsets());
        installNudgeHandlers(tfTime, true, this::nudgeTimeField);
        installNudgeHandlers(tfValue, false, this::nudgeValueField);
        installCurveParamHandlers(tfCurveX1, 0);
        installCurveParamHandlers(tfCurveY1, 1);
        installCurveParamHandlers(tfCurveX2, 2);
        installCurveParamHandlers(tfCurveY2, 3);
        tfCurveSpec.setOnAction(e -> applyCurveSpecText());
        tfCurveSpec.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) applyCurveSpecText();
        });
        cbInterpolation.setOnAction(e -> {
            applyChanges();
            updateCurveEditorState();
        });

        cbEasing.setOnAction(e -> {
            applyChanges();
            syncCurveEditBaseline(resolveEditorEasingSpec());
            syncPresetEditSessionFromSelection(false);
            refreshCurveEditorPreview();
            updateCurveEditorState();
        });
        btnPresetLibrary.setOnAction(e -> {
            presetLibraryPanel.toggleVisible();
            refreshPresetUiState();
        });
        btnEditCurve.setOnAction(e -> beginCurveEdit());
        btnApplyCurveSpec.setOnAction(e -> applyCurveSpecText());
        btnUpdateCurvePreset.setOnAction(e -> updateActivePresetFromCurve());
        btnResetCurve.setOnAction(e -> resetCurveToBaseline());
        btnReverseCurve.setOnAction(e -> reverseCurrentCurve());
        btnClampCurve.setOnAction(e -> clampCurrentCurve());
        btnExpandCurveEditor.setOnAction(e -> setCurveEditorExpanded(!curveEditorExpanded, true));

        sliderTime.valueProperty().addListener((obs, oldV, newV) -> {
            if (currentKeyframe == null || !sliderTime.isValueChanging()) return;
            currentKeyframe.setTimeMs(newV.doubleValue());
            tfTime.setText(String.format("%.0f", newV.doubleValue()));
            if (onKeyframeChanged != null) onKeyframeChanged.run();
        });
        sliderTime.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && currentKeyframe != null) {
                if (onKeyframeChanged != null) onKeyframeChanged.run();
            }
        });

        sliderValue.valueProperty().addListener((obs, oldV, newV) -> {
            if (currentKeyframe == null || !sliderValue.isValueChanging()) return;
            currentKeyframe.setValue(newV.doubleValue());
            tfValue.setText(String.format("%.2f", newV.doubleValue()));
            if (onKeyframeChanged != null) onKeyframeChanged.run();
        });
        sliderValue.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && currentKeyframe != null) {
                if (onKeyframeChanged != null) onKeyframeChanged.run();
            }
        });

        btnResetValue.setOnAction(e -> {
            if (currentSelection.size() > 1 && currentProperty != null) {
                for (Keyframe keyframe : currentSelection) {
                    keyframe.setValue(currentProperty.getDefaultValue());
                }
                tfValueOffset.setText("0");
                if (onKeyframeChanged != null) onKeyframeChanged.run();
            } else if (currentKeyframe != null && currentProperty != null) {
                currentKeyframe.setValue(currentProperty.getDefaultValue());
                tfValue.setText(String.format("%.2f", currentProperty.getDefaultValue()));
                sliderValue.setValue(currentProperty.getDefaultValue());
                if (onKeyframeChanged != null) onKeyframeChanged.run();
            }
        });
        btnApplyBatch.setOnAction(e -> applyBatchOffsets());
        btnDelete.setOnAction(e -> {
            if ((!currentSelection.isEmpty() || currentKeyframe != null) && onDeleteRequested != null) {
                onDeleteRequested.run();
            }
            currentKeyframe = null;
            currentProperty = null;
            currentSelection.clear();
            showEmptyState(true);
            showBatchEditor(false);
            showPivotPresets(false);
            setFieldsDisabled(true);
        });

        setFieldsDisabled(true);
        setCurveEditorExpanded(false, false);
        refreshSelectionContextUi();
        refreshPresetUiState();
    }

    private VBox buildMetaTile(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(META_CAPTION_STYLE);
        return buildMetaTile(titleLabel, valueLabel);
    }

    private VBox buildMetaTile(Label titleLabel, Label valueLabel) {
        VBox box = new VBox(4, titleLabel, valueLabel);
        box.setStyle(TILE_STYLE);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox buildLabeledControl(String title, Node control) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(META_CAPTION_STYLE);
        VBox box = new VBox(5, titleLabel, control);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private TextField createCurveParamField(String prompt, String tooltipText) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefWidth(72);
        field.setStyle(FIELD_STYLE);
        field.setTooltip(new Tooltip(tooltipText));
        field.setOnAction(e -> applyCurveParameterFields());
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) applyCurveParameterFields();
        });
        return field;
    }

    private Button createCurveQuickPresetButton(String label, String tooltipText, EasingSpec spec) {
        Button button = new Button(label);
        button.setStyle(SECONDARY_BUTTON_STYLE);
        button.setTooltip(new Tooltip(tooltipText));
        button.setOnAction(e -> applyQuickCurveSpec(spec));
        curveQuickPresetButtons.add(button);
        return button;
    }

    private VBox buildEditorRow(String title, String subtitle, Node editorNode, Label stepHint) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(SECTION_TITLE_STYLE);
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle(SUBTLE_HINT_STYLE);
        VBox headerBox = new VBox(1, titleLabel, subtitleLabel);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        HBox hintRow = new HBox(8, headerBox, createSpacer(), stepHint);
        hintRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, hintRow, editorNode);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox buildSection(String title, Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(SECTION_TITLE_STYLE);
        return buildSection(titleLabel, content);
    }

    private VBox buildSection(Label titleLabel, Node... content) {
        VBox box = new VBox(8);
        box.setStyle(SECTION_STYLE);
        box.setMaxWidth(Double.MAX_VALUE);
        if (titleLabel != null) {
            box.getChildren().add(titleLabel);
        }
        if (content != null) {
            box.getChildren().addAll(content);
        }
        return box;
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private boolean isDirectCurveEditAvailable() {
        return currentKeyframe != null
            && currentSelection.size() <= 1
            && cbInterpolation.getValue() == Easing.Interpolation.TWEEN;
    }

    private void updateSelectionModeBadge() {
        if (currentSelection.size() > 1) {
            lblSelectionMode.setText(resolveSelectionModeLabel(true, false, selectionTargetKind));
            lblSelectionMode.setStyle(BADGE_MULTI_STYLE);
            return;
        }
        if (currentKeyframe != null) {
            lblSelectionMode.setText(resolveSelectionModeLabel(false, true, selectionTargetKind));
            lblSelectionMode.setStyle(BADGE_ACTIVE_STYLE);
            return;
        }
        if (selectionTargetKind == SelectionTargetKind.RUNTIME_CAMERA || selectionTargetKind == SelectionTargetKind.GROUP) {
            lblSelectionMode.setText(resolveSelectionModeLabel(false, false, selectionTargetKind));
            lblSelectionMode.setStyle(BADGE_ACTIVE_STYLE);
        } else {
            lblSelectionMode.setText("No Selection");
            lblSelectionMode.setStyle(BADGE_IDLE_STYLE);
        }
    }

    static String resolveSelectionModeLabel(boolean multiSelection,
                                            boolean singleKeyframe,
                                            SelectionTargetKind targetKind) {
        if (multiSelection) {
            return targetKind == SelectionTargetKind.RUNTIME_CAMERA ? "Camera Multi-Select" : "Multi-Select";
        }
        if (singleKeyframe) {
            return switch (targetKind != null ? targetKind : SelectionTargetKind.NONE) {
                case RUNTIME_CAMERA -> "Camera Keyframe";
                case GROUP -> "Group Keyframe";
                default -> "Single Keyframe";
            };
        }
        return switch (targetKind != null ? targetKind : SelectionTargetKind.NONE) {
            case RUNTIME_CAMERA -> "Runtime Camera";
            case GROUP -> "Group Target";
            default -> "No Selection";
        };
    }

    private void updateStepHints() {
        lblTimeStepHint.setText("Nudge: " + formatStep(resolveTimeNudgeStep(false)) + " ms  |  Shift " + formatStep(resolveTimeNudgeStep(true)) + " ms");
        PropertyType hintProperty = currentProperty != null
            ? currentProperty
            : selectionTargetKind == SelectionTargetKind.RUNTIME_CAMERA
                ? PropertyType.CAMERA_X
                : PropertyType.X;
        double fine = resolveValueNudgeStep(hintProperty, false);
        double large = resolveValueNudgeStep(hintProperty, true);
        lblValueStepHint.setText("Nudge: " + formatStep(fine) + "  |  Shift " + formatStep(large));
    }

    private String formatStep(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format("%.0f", value);
        }
        if (Math.abs(value) >= 1.0) {
            return String.format("%.2f", value);
        }
        return String.format("%.2f", value);
    }

    public void setTimelineDurationMs(double durationMs) {
        timelineDurationMs = Math.max(100.0, durationMs);
        syncTimeSliderBounds(currentKeyframe != null ? currentKeyframe.getTimeMs() : 0.0);
    }

    public void setProjectRoot(File root) {
        this.projectRoot = root;
        cbEasing.setProjectRoot(root);
        presetLibraryPanel.setProjectRoot(root);
        updateCameraViewportSummary();
        refreshPresetUiState();
    }

    public void setKeyframe(Keyframe kf, PropertyType property) {
        // Stop any running animation and spring preview on selection change
        if (curveEditor.isAnimating()) {
            curveEditor.stopAnimation();
            btnPlayStopAnim.setText("▶ Preview");
            btnPlayStopAnim.setStyle(SECONDARY_BUTTON_STYLE);
        }
        springParamEditor.stopAnimation();

        updatingUi = true;
        currentSelection.clear();
        this.currentKeyframe = kf;
        this.currentProperty = property;

        if (kf == null) {
            syncCurveEditBaseline(EasingSpec.of(Easing.Type.LINEAR));
            clearPresetEditSession();
            lblProperty.setText("-");
            tfTime.setText("");
            tfValue.setText("");
            syncCurveSpecField(null, true);
            cbInterpolation.setValue(Easing.Interpolation.TWEEN);
            cbEasing.setCurrentSpec(EasingSpec.of(Easing.Type.LINEAR));
            curveEditor.setInterpolation(Easing.Interpolation.TWEEN);
            curveEditor.setEasingSpec(EasingSpec.of(Easing.Type.LINEAR));
            updateCurveEditorState();
            syncTimeSliderBounds(0.0);
            showEmptyState(true);
            setFieldsDisabled(true);
            showPivotPresets(false);
            showBatchEditor(false);
        } else {
            syncCurveEditBaseline(kf.getEasingSpec());
            showEmptyState(false);
            lblProperty.setText(property != null ? property.getDisplayName() : "-");
            tfTime.setText(String.format("%.0f", kf.getTimeMs()));
            tfValue.setText(String.format("%.2f", kf.getValue()));
            cbInterpolation.setValue(kf.getInterpolation());
            cbEasing.setCurrentSpec(kf.getEasingSpec());
            syncPresetEditSessionFromSelection(false);
            curveEditor.setInterpolation(kf.getInterpolation());
            curveEditor.setEasingSpec(kf.getEasingSpec());
            updateCurveEditorState();
            syncTimeSliderBounds(kf.getTimeMs());
            sliderTime.setValue(kf.getTimeMs());
            configureSliderForProperty(property);
            sliderValue.setValue(kf.getValue());
            setFieldsDisabled(false);
            showPivotPresets(property == PropertyType.PIVOT_X || property == PropertyType.PIVOT_Y);
            showBatchEditor(false);
        }
        updatingUi = false;
        refreshSelectionContextUi();
        updateStepHints();
        updateSelectionModeBadge();
        refreshPresetUiState();
    }

    public void setSelection(List<Keyframe> selection, PropertyType property) {
        updatingUi = true;
        currentSelection.clear();
        if (selection != null) {
            for (Keyframe keyframe : selection) {
                if (keyframe != null && !currentSelection.contains(keyframe)) currentSelection.add(keyframe);
            }
        }
        if (currentSelection.size() <= 1) {
            setKeyframe(currentSelection.isEmpty() ? null : currentSelection.get(0), property);
            return;
        }

        currentKeyframe = null;
        currentProperty = property;
        syncCurveEditBaseline(resolveSharedEasingSpec(currentSelection));
        showEmptyState(false);
        lblProperty.setText(property != null ? property.getDisplayName() : "Mixed");
        lblSelectionSummary.setText(currentSelection.size() + " keyframes selected. Interpolation and easing changes apply to the full selection.");
        tfTime.setText("");
        tfValue.setText("");
        tfTimeOffset.setText("0");
        tfValueOffset.setText("0");
        syncTimeSliderBounds(currentSelection.get(0).getTimeMs());
        cbInterpolation.setValue(resolveSharedInterpolation(currentSelection));
        cbEasing.setCurrentSpec(resolveSharedEasingSpec(currentSelection));
        clearPresetEditSession();
        curveEditor.setInterpolation(cbInterpolation.getValue());
        curveEditor.setEasingSpec(resolveSharedEasingSpec(currentSelection));
        setFieldsDisabled(false);
        tfTime.setDisable(true);
        sliderTime.setDisable(true);
        tfValue.setDisable(true);
        sliderValue.setDisable(true);
        curveEditor.setDisable(true);
        showPivotPresets(false);
        showBatchEditor(true);
        updateCurveEditorState();
        updatingUi = false;
        refreshSelectionContextUi();
        updateStepHints();
        updateSelectionModeBadge();
        refreshPresetUiState();
    }

    public void setEntityName(String name) {
        setSelectionContext(name, false, false);
    }

    public void setSelectionContext(String name, boolean group, boolean runtimeCamera) {
        selectionTargetKind = runtimeCamera
            ? SelectionTargetKind.RUNTIME_CAMERA
            : group
                ? SelectionTargetKind.GROUP
                : name != null && !name.isBlank()
                    ? SelectionTargetKind.ENTITY
                    : SelectionTargetKind.NONE;
        lblEntity.setText(name != null && !name.isBlank() ? name : "-");
        refreshSelectionContextUi();
    }

    public void setOnKeyframeChanged(Runnable callback) {
        this.onKeyframeChanged = callback;
    }

    public void setOnDeleteRequested(Runnable callback) {
        this.onDeleteRequested = callback;
    }

    public void setOnCurveEditorExpandedChanged(java.util.function.Consumer<Boolean> callback) {
        this.onCurveEditorExpandedChanged = callback;
    }

    public Keyframe getCurrentKeyframe() { return currentKeyframe; }
    public PropertyType getCurrentProperty() { return currentProperty; }

    public void setOnPivotPresetApplied(java.util.function.BiConsumer<Double, Double> callback) {
        this.onPivotPresetApplied = callback;
    }

    public void setCameraState(double cameraX, double cameraY, double cameraZoom) {
        lblCameraState.setText(String.format("X %.1f  Y %.1f  Z %.2f", cameraX, cameraY, cameraZoom));
        updateCameraViewportSummary();
    }

    private void refreshSelectionContextUi() {
        lblTargetCaption.setText(selectionTargetKind == SelectionTargetKind.GROUP ? "Group" : "Target");
        lblCameraBoxTitle.setText(selectionTargetKind == SelectionTargetKind.RUNTIME_CAMERA
            ? "Runtime Camera / Frame"
            : "Runtime Camera");

        String hint = resolveTargetContextHint(selectionTargetKind, currentProperty);
        boolean showHint = hint != null && !hint.isBlank();
        lblTargetContextHint.setText(showHint ? hint : "");
        lblTargetContextHint.setVisible(showHint);
        lblTargetContextHint.setManaged(showHint);

        btnResetValue.setText(resolveResetValueLabel(selectionTargetKind, currentProperty));
        btnResetValue.setTooltip(new Tooltip(resolveResetValueTooltip(selectionTargetKind, currentProperty)));
        updateCameraViewportSummary();
        updateSelectionModeBadge();
    }

    private void updateCameraViewportSummary() {
        ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
        String suffix = selectionTargetKind == SelectionTargetKind.RUNTIME_CAMERA ? "  •  active target" : "";
        lblCameraViewportState.setText("Frame " + viewport.width() + " x " + viewport.height() + suffix);
    }

    static String resolveTargetContextHint(SelectionTargetKind targetKind, PropertyType property) {
        if (targetKind == SelectionTargetKind.RUNTIME_CAMERA) {
            return switch (property != null ? property : PropertyType.CAMERA_X) {
                case CAMERA_ZOOM -> "Editing the runtime frame zoom. Higher zoom shows a tighter visible area inside the runtime viewport.";
                case CAMERA_X, CAMERA_Y -> "Editing the runtime frame position. Camera X/Y pans the visible runtime area across the scene.";
                default -> "Editing the runtime camera. Camera channels control the red runtime frame shown in preview.";
            };
        }
        if (targetKind == SelectionTargetKind.GROUP) {
            return "Editing a group container track. These keyframes affect the grouped stack instead of a single entity.";
        }
        return "";
    }

    static String resolveResetValueLabel(SelectionTargetKind targetKind, PropertyType property) {
        if (targetKind == SelectionTargetKind.RUNTIME_CAMERA) {
            return property == PropertyType.CAMERA_ZOOM ? "Reset Zoom" : "Reset Camera";
        }
        return "Reset Value";
    }

    static String resolveResetValueTooltip(SelectionTargetKind targetKind, PropertyType property) {
        if (targetKind == SelectionTargetKind.RUNTIME_CAMERA) {
            return property == PropertyType.CAMERA_ZOOM
                ? "Reset runtime camera zoom to its default value"
                : "Reset runtime camera position to the property default";
        }
        return "Reset to property default";
    }

    // --- A) Ghost curve overlay: accept adjacent keyframes from the track ---
    public void setAdjacentKeyframes(List<Keyframe> adjacent) {
        adjacentKeyframes.clear();
        if (adjacent != null) adjacentKeyframes.addAll(adjacent);
        rebuildGhostCurves();
    }

    private void rebuildGhostCurves() {
        if (adjacentKeyframes.isEmpty()) {
            curveEditor.clearGhostCurves();
            return;
        }
        java.util.List<EasingCurveEditor.GhostCurve> ghosts = new java.util.ArrayList<>();
        javafx.scene.paint.Color prevColor = javafx.scene.paint.Color.web("#6b8ab5", 0.40);
        javafx.scene.paint.Color nextColor = javafx.scene.paint.Color.web("#b58a6b", 0.40);
        for (Keyframe adj : adjacentKeyframes) {
            boolean isPrev = currentKeyframe != null && adj.getTimeMs() < currentKeyframe.getTimeMs();
            String label = isPrev ? "prev" : "next";
            javafx.scene.paint.Color color = isPrev ? prevColor : nextColor;
            ghosts.add(new EasingCurveEditor.GhostCurve(label, adj.getEasingSpec(), adj.getInterpolation(), color));
        }
        curveEditor.setGhostCurves(ghosts);
    }

    private void refreshPresetUiState() {
        cbEasing.refreshState();
        presetLibraryPanel.refreshView();
        boolean libraryVisible = presetLibraryPanel.isPanelVisible();
        btnPresetLibrary.setText(libraryVisible ? "Hide Library" : "Show Library");
        btnPresetLibrary.setStyle(libraryVisible ? TOGGLE_BUTTON_STYLE : SECONDARY_BUTTON_STYLE);
        refreshCurveActionState();
    }

    private EasingSpec resolveEditorEasingSpec() {
        if (currentSelection.size() > 1) {
            return resolveSharedEasingSpec(currentSelection);
        }
        if (currentKeyframe != null) {
            return currentKeyframe.getEasingSpec();
        }
        EasingSpec selected = cbEasing.getSelectedSpec();
        if (selected.getType() == Easing.Type.CUSTOM) {
            double[] params = curveEditor.getBezierParams();
            return EasingSpec.cubicBezier(params[0], params[1], params[2], params[3]);
        }
        return selected;
    }

    private void syncCurveEditBaseline(EasingSpec spec) {
        curveEditBaselineSpec = toEditableCurveSpec(spec);
    }

    private void applyCurveSpec(EasingSpec spec, boolean notifyChange) {
        if (spec == null || currentKeyframe == null || currentSelection.size() > 1) return;
        applyExplicitEasingSpec(spec);
        if (notifyChange && onKeyframeChanged != null) onKeyframeChanged.run();
    }

    private void applyQuickCurveSpec(EasingSpec spec) {
        if (!isDirectCurveEditAvailable() || spec == null) return;
        EasingSpec editable = toEditableCurveSpec(spec);
        syncCurveEditBaseline(editable);
        applyExplicitEasingSpec(editable);
        curveEditor.requestFocus();
        if (onKeyframeChanged != null) onKeyframeChanged.run();
    }

    private void reverseCurrentCurve() {
        if (!isDirectCurveEditAvailable()) return;
        applyCurveSpec(reverseEditableCurveSpec(resolveEditorEasingSpec()), true);
        curveEditor.requestFocus();
    }

    private void clampCurrentCurve() {
        if (!isDirectCurveEditAvailable()) return;
        applyCurveSpec(clampEditableCurveSpec(resolveEditorEasingSpec()), true);
        curveEditor.requestFocus();
    }

    private void applyCurveSpecText() {
        if (updatingUi) return;

        EasingSpec current = resolveEditorEasingSpec();
        if (!isDirectCurveEditAvailable()) return;

        String raw = tfCurveSpec.getText();
        if (raw == null || raw.isBlank()) {
            setFieldError(tfCurveSpec, false);
            syncCurveSpecField(current, true);
            return;
        }

        EasingSpec parsed = EasingSpec.tryParse(raw);
        if (parsed == null) {
            setFieldError(tfCurveSpec, true);
            return;
        }

        EasingSpec editable = toEditableCurveSpec(parsed);
        setFieldError(tfCurveSpec, false);
        syncCurveEditBaseline(editable);
        applyExplicitEasingSpec(editable);
        curveEditor.requestFocus();
        if (onKeyframeChanged != null) onKeyframeChanged.run();
    }

    private void applyExplicitEasingSpec(EasingSpec spec) {
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        boolean priorUpdating = updatingUi;
        updatingUi = true;
        cbEasing.setCurrentSpec(resolved);
        curveEditor.setInterpolation(cbInterpolation.getValue());
        curveEditor.setEasingSpec(resolved);
        if (currentSelection.size() > 1) {
            for (Keyframe keyframe : currentSelection) {
                keyframe.setEasingSpec(resolved);
            }
        } else if (currentKeyframe != null) {
            currentKeyframe.setEasingSpec(resolved);
        }
        syncCurveParameterFields(resolveCurveParams(resolved));
        syncCurveSpecField(resolved, true);
        updatingUi = priorUpdating;
        refreshCurveEditorPreview();
        updateCurveEditorState();
    }

    private void beginCurveEdit() {
        if (currentSelection.size() > 1 || currentKeyframe == null) return;
        if (cbInterpolation.getValue() != Easing.Interpolation.TWEEN) return;

        PuppeteerEasingCatalog.Entry selected = cbEasing.getSelectedEntry();
        if (selected != null && selected.isPreset()) {
            activePresetEditId = selected.id();
            activePresetEditName = selected.label();
        } else {
            clearPresetEditSession();
        }

        EasingSpec editable = toEditableCurveSpec(resolveEditorEasingSpec());
        syncCurveEditBaseline(editable);
        applyExplicitEasingSpec(editable);
        if (onKeyframeChanged != null) onKeyframeChanged.run();
        refreshPresetUiState();
    }

    private void resetCurveToBaseline() {
        if (currentSelection.size() > 1 || currentKeyframe == null) return;
        if (cbInterpolation.getValue() != Easing.Interpolation.TWEEN) return;
        EasingSpec baseline = curveEditBaselineSpec != null
            ? curveEditBaselineSpec
            : toEditableCurveSpec(resolveEditorEasingSpec());
        applyExplicitEasingSpec(baseline);
        if (onKeyframeChanged != null) onKeyframeChanged.run();
    }

    private void updateActivePresetFromCurve() {
        if (activePresetEditId == null || activePresetEditName == null) return;
        EasingSpec current = resolveEditorEasingSpec();
        EasingSpec editable = current.getType() == Easing.Type.CUSTOM ? current : toEditableCurveSpec(current);
        if (cbEasing.updatePreset(activePresetEditId, activePresetEditName, editable)) {
            syncCurveEditBaseline(editable);
            presetLibraryPanel.reloadLibrary();
            syncPresetEditSessionFromSelection(true);
            refreshCurveEditorPreview();
            updateCurveEditorState();
            if (onKeyframeChanged != null) onKeyframeChanged.run();
        }
    }

    private void applyChanges() {
        if (updatingUi) return;
        if (currentSelection.size() > 1) {
            for (Keyframe keyframe : currentSelection) {
                keyframe.setInterpolation(cbInterpolation.getValue());
                applySelectedEasing(keyframe);
            }
            refreshCurveEditorPreview();
            if (onKeyframeChanged != null) onKeyframeChanged.run();
            refreshPresetUiState();
            return;
        }
        if (currentKeyframe == null) return;

        boolean hasError = false;
        try {
            double time = Math.max(0.0, Double.parseDouble(tfTime.getText().trim()));
            currentKeyframe.setTimeMs(time);
            syncTimeSliderBounds(time);
            sliderTime.setValue(time);
            tfTime.setText(String.format("%.0f", time));
            setFieldError(tfTime, false);
        } catch (NumberFormatException ignored) {
            setFieldError(tfTime, true);
            hasError = true;
        }

        try {
            double value = Double.parseDouble(tfValue.getText().trim());
            currentKeyframe.setValue(value);
            sliderValue.setValue(value);
            tfValue.setText(String.format("%.2f", value));
            setFieldError(tfValue, false);
        } catch (NumberFormatException ignored) {
            setFieldError(tfValue, true);
            hasError = true;
        }

        if (hasError) return;

        currentKeyframe.setInterpolation(cbInterpolation.getValue());
        applySelectedEasing(currentKeyframe);
        refreshCurveEditorPreview();

        if (onKeyframeChanged != null) onKeyframeChanged.run();
        refreshPresetUiState();
    }

    private void applyBatchOffsets() {
        if (updatingUi) return;
        if (currentSelection.size() <= 1) return;
        double timeDelta;
        double valueDelta;
        try {
            timeDelta = Double.parseDouble(tfTimeOffset.getText().trim());
            setFieldError(tfTimeOffset, false);
        } catch (NumberFormatException ex) {
            setFieldError(tfTimeOffset, true);
            return;
        }
        try {
            valueDelta = Double.parseDouble(tfValueOffset.getText().trim());
            setFieldError(tfValueOffset, false);
        } catch (NumberFormatException ex) {
            setFieldError(tfValueOffset, true);
            return;
        }

        for (Keyframe keyframe : currentSelection) {
            keyframe.setTimeMs(Math.max(0.0, keyframe.getTimeMs() + timeDelta));
            keyframe.setValue(keyframe.getValue() + valueDelta);
            keyframe.setInterpolation(cbInterpolation.getValue());
            applySelectedEasing(keyframe);
        }
        tfTimeOffset.setText("0");
        tfValueOffset.setText("0");
        refreshCurveEditorPreview();
        if (onKeyframeChanged != null) onKeyframeChanged.run();
    }

    private void installNudgeHandlers(TextField field,
                                      boolean timeField,
                                      java.util.function.Consumer<Double> nudger) {
        if (field == null || nudger == null) return;
        field.setOnKeyPressed(event -> {
            if (event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN) return;
            double step = timeField
                ? resolveTimeNudgeStep(event.isShiftDown())
                : resolveValueNudgeStep(event.isShiftDown());
            nudger.accept(event.getCode() == KeyCode.UP ? step : -step);
            event.consume();
        });
        field.setOnScroll(event -> {
            if (Math.abs(event.getDeltaY()) < 0.001) return;
            double step = timeField
                ? resolveTimeNudgeStep(event.isShiftDown())
                : resolveValueNudgeStep(event.isShiftDown());
            nudger.accept(event.getDeltaY() > 0 ? step : -step);
            event.consume();
        });
    }

    private void installCurveParamHandlers(TextField field, int parameterIndex) {
        if (field == null) return;
        field.setOnKeyPressed(event -> {
            if (event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN) return;
            double step = resolveCurveNudgeStep(event.isShiftDown());
            nudgeCurveParameterField(parameterIndex, event.getCode() == KeyCode.UP ? step : -step);
            event.consume();
        });
        field.setOnScroll(event -> {
            if (Math.abs(event.getDeltaY()) < 0.001) return;
            double step = resolveCurveNudgeStep(event.isShiftDown());
            nudgeCurveParameterField(parameterIndex, event.getDeltaY() > 0 ? step : -step);
            event.consume();
        });
    }

    static double resolveTimeNudgeStep(boolean large) {
        return large ? 50.0 : 10.0;
    }

    static double resolveCurveNudgeStep(boolean large) {
        return large ? 0.05 : 0.01;
    }

    private double resolveValueNudgeStep(boolean large) {
        return resolveValueNudgeStep(currentProperty != null ? currentProperty : PropertyType.X, large);
    }

    static double resolveValueNudgeStep(PropertyType property, boolean large) {
        return switch (property != null ? property : PropertyType.X) {
            case X, Y, CAMERA_X, CAMERA_Y -> large ? 10.0 : 1.0;
            case ROTATION -> large ? 15.0 : 1.0;
            case SCALE_X, SCALE_Y, CAMERA_ZOOM -> large ? 0.10 : 0.01;
            case ALPHA, PIVOT_X, PIVOT_Y -> large ? 0.05 : 0.01;
        };
    }

    private void nudgeTimeField(double deltaMs) {
        if (currentSelection.size() > 1 || currentKeyframe == null) return;
        double base = parseOrFallback(tfTime.getText(), currentKeyframe.getTimeMs());
        double next = Math.max(0.0, Math.min(timelineDurationMs, base + deltaMs));
        tfTime.setText(String.format("%.0f", next));
        applyChanges();
    }

    private void nudgeValueField(double delta) {
        if (currentSelection.size() > 1 || currentKeyframe == null) return;
        double base = parseOrFallback(tfValue.getText(), currentKeyframe.getValue());
        double next = base + delta;
        tfValue.setText(formatValue(next));
        applyChanges();
    }

    private void nudgeCurveParameterField(int parameterIndex, double delta) {
        if (!isCurveParameterEditingEnabled()) return;
        TextField field = curveParamField(parameterIndex);
        if (field == null) return;
        double[] params = curveEditor.getBezierParams();
        double current = parameterIndex >= 0 && parameterIndex < params.length
            ? params[parameterIndex]
            : 0.0;
        double next = clampCurveParam(parameterIndex, parseOrFallback(field.getText(), current) + delta);
        field.setText(formatCurveParam(next));
        applyCurveParameterFields();
    }

    private void applyCurveParameterFields() {
        if (updatingUi || !isCurveParameterEditingEnabled()) return;
        double[] current = curveEditor.getBezierParams();
        double[] next = current.clone();
        TextField[] fields = curveParamFields();
        boolean hasError = false;

        for (int i = 0; i < fields.length; i++) {
            TextField field = fields[i];
            try {
                double value = clampCurveParam(i, Double.parseDouble(field.getText().trim()));
                next[i] = value;
                field.setText(formatCurveParam(value));
                setFieldError(field, false);
            } catch (NumberFormatException ex) {
                setFieldError(field, true);
                hasError = true;
            }
        }
        if (hasError) return;
        applyCurveSpec(EasingSpec.cubicBezier(next[0], next[1], next[2], next[3]), true);
    }

    private double parseOrFallback(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String formatValue(double value) {
        return String.format("%.2f", value);
    }

    private String formatCurveParam(double value) {
        return String.format("%.3f", value);
    }

    private double[] resolveCurveParams(EasingSpec spec) {
        EasingSpec editable = toEditableCurveSpec(spec);
        return Easing.coerceParameters(Easing.Type.CUSTOM, editable.getParameters());
    }

    private TextField[] curveParamFields() {
        return new TextField[]{ tfCurveX1, tfCurveY1, tfCurveX2, tfCurveY2 };
    }

    private TextField curveParamField(int parameterIndex) {
        return switch (parameterIndex) {
            case 0 -> tfCurveX1;
            case 1 -> tfCurveY1;
            case 2 -> tfCurveX2;
            case 3 -> tfCurveY2;
            default -> null;
        };
    }

    private boolean isCurveParameterEditingEnabled() {
        return currentKeyframe != null
            && currentSelection.size() <= 1
            && cbInterpolation.getValue() == Easing.Interpolation.TWEEN
            && resolveEditorEasingSpec().getType() == Easing.Type.CUSTOM;
    }

    private void syncCurveParameterFields(double[] params) {
        double[] resolved = params != null && params.length == 4
            ? params
            : Easing.coerceParameters(Easing.Type.CUSTOM, null);
        boolean priorUpdating = updatingUi;
        updatingUi = true;
        try {
            tfCurveX1.setText(formatCurveParam(resolved[0]));
            tfCurveY1.setText(formatCurveParam(resolved[1]));
            tfCurveX2.setText(formatCurveParam(resolved[2]));
            tfCurveY2.setText(formatCurveParam(resolved[3]));
            for (TextField field : curveParamFields()) {
                setFieldError(field, false);
            }
        } finally {
            updatingUi = priorUpdating;
        }
    }

    private void syncCurveSpecField(EasingSpec spec, boolean force) {
        if (tfCurveSpec == null) return;
        if (!force && tfCurveSpec.isFocused()) return;
        boolean priorUpdating = updatingUi;
        updatingUi = true;
        try {
            tfCurveSpec.setText(formatCurveSpecInput(spec));
            setFieldError(tfCurveSpec, false);
        } finally {
            updatingUi = priorUpdating;
        }
    }

    private String formatCurveSpecInput(EasingSpec spec) {
        if (spec == null) {
            return "";
        }
        if (spec.getType() != Easing.Type.CUSTOM) {
            return spec.toDslString();
        }
        double[] params = Easing.coerceParameters(Easing.Type.CUSTOM, spec.getParameters());
        return "cubic-bezier(" + formatCurveParam(params[0])
            + ", " + formatCurveParam(params[1])
            + ", " + formatCurveParam(params[2])
            + ", " + formatCurveParam(params[3]) + ")";
    }

    private static double clampCurveParam(int parameterIndex, double value) {
        return switch (parameterIndex) {
            case 0, 2 -> Math.max(0.0, Math.min(1.0, value));
            default -> Math.max(-0.5, Math.min(1.5, value));
        };
    }

    private void syncTimeSliderBounds(double timeMs) {
        double max = Math.max(timelineDurationMs, Math.max(0.0, timeMs));
        sliderTime.setMin(0.0);
        sliderTime.setMax(Math.max(100.0, max));
    }

    private void setFieldError(TextField field, boolean error) {
        field.setStyle(error ? FIELD_STYLE_ERROR : FIELD_STYLE);
    }

    private void configureSliderForProperty(PropertyType prop) {
        if (prop == null) return;
        switch (prop) {
            case X, Y, CAMERA_X, CAMERA_Y -> {
                sliderValue.setMin(-2000); sliderValue.setMax(2000);
            }
            case PIVOT_X, PIVOT_Y -> {
                sliderValue.setMin(0.0); sliderValue.setMax(1.0);
            }
            case ROTATION -> {
                sliderValue.setMin(-360); sliderValue.setMax(360);
            }
            case SCALE_X, SCALE_Y, CAMERA_ZOOM -> {
                sliderValue.setMin(0.01); sliderValue.setMax(5.0);
            }
            case ALPHA -> {
                sliderValue.setMin(0.0); sliderValue.setMax(1.0);
            }
        }
        updateStepHints();
    }

    private void showEmptyState(boolean empty) {
        lblEmptyHint.setVisible(empty);
        lblEmptyHint.setManaged(empty);
        contentBox.setVisible(!empty);
        contentBox.setManaged(!empty);
    }

    private void setFieldsDisabled(boolean disabled) {
        tfTime.setDisable(disabled);
        sliderTime.setDisable(disabled);
        tfValue.setDisable(disabled);
        sliderValue.setDisable(disabled);
        cbInterpolation.setDisable(disabled);
        cbEasing.setDisable(disabled);
        btnEditCurve.setDisable(disabled);
        btnUpdateCurvePreset.setDisable(disabled);
        btnExpandCurveEditor.setDisable(disabled);
        btnPlayStopAnim.setDisable(disabled);
        btnResetCurve.setDisable(disabled);
        btnReverseCurve.setDisable(disabled);
        btnClampCurve.setDisable(disabled);
        btnDelete.setDisable(disabled);
        btnResetValue.setDisable(disabled);
        tfTimeOffset.setDisable(disabled);
        tfValueOffset.setDisable(disabled);
        btnApplyBatch.setDisable(disabled);
        tfCurveSpec.setDisable(disabled);
        btnApplyCurveSpec.setDisable(disabled);
        for (TextField field : curveParamFields()) {
            field.setDisable(disabled);
        }
        for (Button button : curveQuickPresetButtons) {
            button.setDisable(disabled);
        }
        if (!disabled) {
            updateCurveEditorState();
        }
        updateSelectionModeBadge();
        refreshPresetUiState();
    }

    private void showBatchEditor(boolean show) {
        batchBox.setVisible(show);
        batchBox.setManaged(show);
    }

    private void showPivotPresets(boolean show) {
        pivotBox.setVisible(show);
        pivotBox.setManaged(show);
    }

    private void setCurveEditorExpanded(boolean expanded, boolean notifyParent) {
        curveEditorExpanded = expanded;
        curveEditor.setExpanded(expanded);
        btnExpandCurveEditor.setText(expanded ? "Compact" : "Expand");
        btnExpandCurveEditor.setStyle(expanded ? TOGGLE_BUTTON_STYLE : SECONDARY_BUTTON_STYLE);
        btnExpandCurveEditor.setTooltip(new Tooltip(expanded
            ? "Return the curve editor to its compact height"
            : "Grow the curve editor inside the left panel"));
        if (notifyParent && onCurveEditorExpandedChanged != null) {
            onCurveEditorExpandedChanged.accept(expanded);
        }
    }

    private GridPane buildPivotPresetsGrid() {
        GridPane pg = new GridPane();
        pg.setHgap(2);
        pg.setVgap(2);

        String[][] labels = {
            {"TL", "TC", "TR"},
            {"ML", " C", "MR"},
            {"BL", "BC", "BR"}
        };
        double[][] pivotX = {{0.0, 0.5, 1.0}, {0.0, 0.5, 1.0}, {0.0, 0.5, 1.0}};
        double[][] pivotY = {{0.0, 0.0, 0.0}, {0.5, 0.5, 0.5}, {1.0, 1.0, 1.0}};
        String[][] tooltips = {
            {"Top-Left (0, 0)", "Top-Center (0.5, 0)", "Top-Right (1, 0)"},
            {"Mid-Left (0, 0.5)", "Center (0.5, 0.5)", "Mid-Right (1, 0.5)"},
            {"Bottom-Left (0, 1)", "Bottom-Center (0.5, 1)", "Bottom-Right (1, 1)"}
        };

        String btnStyle = "-fx-background-color: #232323; -fx-text-fill: #d2d2d2; -fx-border-color: #3d3d3d; -fx-background-radius: 4; " +
            "-fx-border-radius: 4; -fx-padding: 3 5; -fx-font-size: 9px; -fx-cursor: hand; -fx-min-width: 28; -fx-min-height: 20;";
        String btnHoverStyle = "-fx-background-color: #343434; -fx-text-fill: #f2d79b; -fx-border-color: #6a6a6a; -fx-background-radius: 4; " +
            "-fx-border-radius: 4; -fx-padding: 3 5; -fx-font-size: 9px; -fx-cursor: hand; -fx-min-width: 28; -fx-min-height: 20;";

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                Button btn = new Button(labels[row][col]);
                btn.setStyle(btnStyle);
                btn.setTooltip(new Tooltip(tooltips[row][col]));
                final double px = pivotX[row][col];
                final double py = pivotY[row][col];
                btn.setOnMouseEntered(e -> btn.setStyle(btnHoverStyle));
                btn.setOnMouseExited(e -> btn.setStyle(btnStyle));
                btn.setOnAction(e -> applyPivotPreset(px, py));
                pg.add(btn, col, row);
            }
        }
        return pg;
    }

    private void applyPivotPreset(double px, double py) {
        if (currentKeyframe == null || currentProperty == null) return;

        double axisValue = (currentProperty == PropertyType.PIVOT_X) ? px : py;
        currentKeyframe.setValue(axisValue);
        tfValue.setText(String.format("%.2f", axisValue));
        sliderValue.setValue(axisValue);
        if (onKeyframeChanged != null) onKeyframeChanged.run();

        if (onPivotPresetApplied != null) {
            onPivotPresetApplied.accept(px, py);
        }
    }

    private void updateCurveEditorState() {
        Easing.Interpolation mode = cbInterpolation.getValue() != null
            ? cbInterpolation.getValue()
            : Easing.Interpolation.TWEEN;
        curveEditor.setInterpolation(mode);
        EasingSpec current = resolveEditorEasingSpec();
        boolean tween = mode == Easing.Interpolation.TWEEN;
        boolean hasPreview = currentKeyframe != null || currentSelection.size() > 1;
        boolean singleKeyframe = currentKeyframe != null && currentSelection.size() <= 1;
        boolean customCurve = singleKeyframe && tween && current.getType() == Easing.Type.CUSTOM;

        if (currentSelection.size() > 1) {
            cbEasing.setDisable(!tween);
        } else if (currentKeyframe != null) {
            cbEasing.setDisable(!tween);
        }

        syncCurveSpecField(hasPreview ? current : null, false);
        syncCurveParameterFields(resolveCurveParams(current));
        for (TextField field : curveParamFields()) {
            field.setDisable(!customCurve);
        }
        boolean directCurveEdit = singleKeyframe && tween;
        tfCurveSpec.setDisable(!directCurveEdit);
        btnApplyCurveSpec.setDisable(!directCurveEdit);
        for (Button button : curveQuickPresetButtons) {
            button.setDisable(!directCurveEdit);
        }
        btnReverseCurve.setDisable(!directCurveEdit);
        btnClampCurve.setDisable(!directCurveEdit);
        btnResetCurve.setDisable(!customCurve);
        btnExpandCurveEditor.setDisable(!hasPreview);
        btnPlayStopAnim.setDisable(!hasPreview);
        curveEditor.setDisable(!hasPreview);
        curveEditor.setOpacity(!hasPreview ? 0.55 : customCurve ? 1.0 : tween ? 0.96 : 0.90);
        lblCurveInteractionHint.setText(resolveCurveInteractionHint(hasPreview, singleKeyframe, tween, customCurve));
        curveEditor.setHelperText(resolveCurveCanvasHint(hasPreview, singleKeyframe, tween, customCurve));

        // --- C) Show spring editor for SPRING / DAMPED_SPRING types ---
        boolean isSpring = singleKeyframe && tween &&
            (current.getType() == Easing.Type.SPRING || current.getType() == Easing.Type.DAMPED_SPRING);
        springParamEditor.setVisible(isSpring);
        springParamEditor.setManaged(isSpring);
        if (isSpring) {
            springParamEditor.setSpec(current);
        }

        refreshCurveActionState();
    }

    private void applySelectedEasing(Keyframe keyframe) {
        if (keyframe == null) return;
        EasingSpec selectedSpec = cbEasing.getSelectedSpec();
        if (Objects.equals(keyframe.getEasingSpec(), selectedSpec)) return;
        keyframe.setEasingSpec(selectedSpec);
    }

    private void refreshCurveEditorPreview() {
        curveEditor.setInterpolation(cbInterpolation.getValue());
        if (currentSelection.size() > 1) {
            EasingSpec shared = resolveSharedEasingSpec(currentSelection);
            curveEditor.setEasingSpec(shared);
            syncCurveSpecField(shared, false);
            return;
        }
        if (currentKeyframe != null) {
            EasingSpec current = currentKeyframe.getEasingSpec();
            curveEditor.setEasingSpec(current);
            syncCurveSpecField(current, false);
            return;
        }
        EasingSpec selected = cbEasing.getSelectedSpec();
        curveEditor.setEasingSpec(selected);
        syncCurveSpecField(selected, false);
    }

    private static Easing.Interpolation resolveSharedInterpolation(List<Keyframe> keyframes) {
        Easing.Interpolation first = keyframes.get(0).getInterpolation();
        for (Keyframe keyframe : keyframes) {
            if (keyframe.getInterpolation() != first) {
                return Easing.Interpolation.TWEEN;
            }
        }
        return first;
    }

    private static Easing.Type resolveSharedEasing(List<Keyframe> keyframes) {
        Easing.Type first = keyframes.get(0).getEasing();
        for (Keyframe keyframe : keyframes) {
            if (keyframe.getEasing() != first) {
                return Easing.Type.LINEAR;
            }
        }
        return first;
    }

    private static EasingSpec resolveSharedEasingSpec(List<Keyframe> keyframes) {
        EasingSpec first = keyframes.get(0).getEasingSpec();
        for (Keyframe keyframe : keyframes) {
            if (!first.equals(keyframe.getEasingSpec())) {
                return EasingSpec.of(resolveSharedEasing(keyframes));
            }
        }
        return first;
    }

    private void refreshCurveActionState() {
        boolean singleKeyframe = currentKeyframe != null && currentSelection.size() <= 1;
        boolean tween = cbInterpolation.getValue() == Easing.Interpolation.TWEEN;
        EasingSpec current = resolveEditorEasingSpec();
        boolean customCurve = singleKeyframe && tween && current.getType() == Easing.Type.CUSTOM;

        btnEditCurve.setDisable(!singleKeyframe || !tween || customCurve);
        btnUpdateCurvePreset.setDisable(!customCurve || activePresetEditId == null || activePresetEditName == null);

        if (!singleKeyframe) {
            lblCurvePresetHint.setText("Curve editing only activates for a single keyframe selection.");
            return;
        }
        if (!tween) {
            lblCurvePresetHint.setText("Switch interpolation to TWEEN to unlock editable easing curves.");
            return;
        }
        if (activePresetEditId != null && activePresetEditName != null) {
            if (customCurve) {
                lblCurvePresetHint.setText("Editing preset '" + activePresetEditName + "'. Drag handles, use 1/2 + arrow keys, apply Reverse or Clamp Y, or paste a curve spec, then click Update Preset.");
            } else {
                lblCurvePresetHint.setText("Preset '" + activePresetEditName + "' is selected. Click Make Editable, Reverse, Clamp Y, or pick a quick shape to start tweaking it.");
            }
            return;
        }
        if (customCurve) {
            lblCurvePresetHint.setText("Drag the red and green handles, press 1/2 to select a handle, use arrow keys to nudge, or use Reverse and Clamp Y for faster reshaping.");
            return;
        }
        lblCurvePresetHint.setText("Click Make Editable, use a quick shape, hit Reverse or Clamp Y, or paste a curve spec to convert the current easing into an editable cubic bezier.");
    }

    private String resolveCurveInteractionHint(boolean hasPreview,
                                               boolean singleKeyframe,
                                               boolean tween,
                                               boolean customCurve) {
        if (!hasPreview) {
            return "Select a keyframe to preview easing curves here.";
        }
        if (!singleKeyframe) {
            return "Preview only. Multi-selection shows the shared curve or a fallback. Select one keyframe to edit handles.";
        }
        if (!tween) {
            return "Preview only. HOLD and STEP are inspectable here, but editable handles only apply to TWEEN.";
        }
        if (customCurve) {
            return "Precise controls are live. Drag, use 1/2 to pick a handle, arrow keys to nudge, Shift to snap in 0.05 increments, or use Reverse and Clamp Y.";
        }
        return "Preview only. Click Make Editable, use a quick shape, Reverse, Clamp Y, or apply a spec to turn this easing into a draggable cubic bezier.";
    }

    private String resolveCurveCanvasHint(boolean hasPreview,
                                          boolean singleKeyframe,
                                          boolean tween,
                                          boolean customCurve) {
        if (!hasPreview) {
            return "Select a keyframe to preview a curve.";
        }
        if (!singleKeyframe) {
            return "Preview only for multi-selection.";
        }
        if (!tween) {
            return "Preview only for non-tween interpolation.";
        }
        if (customCurve) {
            return "Drag handles. 1/2 selects a handle. Arrows nudge. Shift snaps to 0.05.";
        }
        return "Hover to inspect. Make Editable, quick shapes, or a pasted spec unlock handle editing.";
    }

    private void syncPresetEditSessionFromSelection(boolean preserveCurrent) {
        PuppeteerEasingCatalog.Entry entry = cbEasing.getSelectedEntry();
        if (entry != null && entry.isPreset()) {
            activePresetEditId = entry.id();
            activePresetEditName = entry.label();
        } else if (!preserveCurrent) {
            clearPresetEditSession();
        }
    }

    private void clearPresetEditSession() {
        activePresetEditId = null;
        activePresetEditName = null;
    }

    static EasingSpec toEditableCurveSpec(EasingSpec spec) {
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        if (resolved.getType() == Easing.Type.CUSTOM) {
            double[] params = Easing.coerceParameters(Easing.Type.CUSTOM, resolved.getParameters());
            return EasingSpec.cubicBezier(params[0], params[1], params[2], params[3]);
        }
        EasingSpec named = Easing.namedCurveSpec(resolved.getType());
        if (named.getType() == Easing.Type.CUSTOM) {
            double[] params = Easing.coerceParameters(Easing.Type.CUSTOM, named.getParameters());
            return EasingSpec.cubicBezier(params[0], params[1], params[2], params[3]);
        }
        double[] bezier = approximateBezier(resolved);
        return EasingSpec.cubicBezier(bezier[0], bezier[1], bezier[2], bezier[3]);
    }

    static EasingSpec reverseEditableCurveSpec(EasingSpec spec) {
        double[] params = Easing.coerceParameters(Easing.Type.CUSTOM, toEditableCurveSpec(spec).getParameters());
        return EasingSpec.cubicBezier(
            1.0 - params[2],
            1.0 - params[3],
            1.0 - params[0],
            1.0 - params[1]
        );
    }

    static EasingSpec clampEditableCurveSpec(EasingSpec spec) {
        double[] params = Easing.coerceParameters(Easing.Type.CUSTOM, toEditableCurveSpec(spec).getParameters());
        return EasingSpec.cubicBezier(
            params[0],
            clampUnitRange(params[1]),
            params[2],
            clampUnitRange(params[3])
        );
    }

    static double[] approximateBezier(EasingSpec spec) {
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        double[] best = seededBezier(resolved);
        double bestError = approximationError(resolved, best);
        double[] steps = {0.35, 0.18, 0.09, 0.045, 0.022, 0.011, 0.005};

        for (double step : steps) {
            boolean improved;
            do {
                improved = false;
                for (int index = 0; index < 4; index++) {
                    for (double delta : new double[]{-step, step}) {
                        double[] candidate = best.clone();
                        candidate[index] = clampBezierParam(index, candidate[index] + delta);
                        double error = approximationError(resolved, candidate);
                        if (error + 1e-9 < bestError) {
                            best = candidate;
                            bestError = error;
                            improved = true;
                        }
                    }
                }
            } while (improved);
        }
        return best;
    }

    private static double[] seededBezier(EasingSpec spec) {
        Easing.Type type = spec != null ? spec.getType() : Easing.Type.LINEAR;
        return switch (type) {
            case LINEAR -> new double[]{0.0, 0.0, 1.0, 1.0};
            case EASE_IN_SINE -> new double[]{0.12, 0.0, 0.39, 0.0};
            case EASE_OUT_SINE -> new double[]{0.61, 1.0, 0.88, 1.0};
            case EASE_IN_OUT_SINE -> new double[]{0.37, 0.0, 0.63, 1.0};
            case EASE_IN_QUAD -> new double[]{0.11, 0.0, 0.50, 0.0};
            case EASE_OUT_QUAD -> new double[]{0.50, 1.0, 0.89, 1.0};
            case EASE_IN_OUT_QUAD -> new double[]{0.45, 0.0, 0.55, 1.0};
            case EASE_IN_CUBIC -> new double[]{0.32, 0.0, 0.67, 0.0};
            case EASE_OUT_CUBIC -> new double[]{0.33, 1.0, 0.68, 1.0};
            case EASE_IN_OUT_CUBIC -> new double[]{0.65, 0.0, 0.35, 1.0};
            case EASE_IN_QUART -> new double[]{0.50, 0.0, 0.75, 0.0};
            case EASE_OUT_QUART -> new double[]{0.25, 1.0, 0.50, 1.0};
            case EASE_IN_OUT_QUART -> new double[]{0.76, 0.0, 0.24, 1.0};
            case EASE_IN_QUINT -> new double[]{0.64, 0.0, 0.78, 0.0};
            case EASE_OUT_QUINT -> new double[]{0.22, 1.0, 0.36, 1.0};
            case EASE_IN_OUT_QUINT -> new double[]{0.83, 0.0, 0.17, 1.0};
            case UI_SOFT_IN -> new double[]{0.18, 0.96, 0.33, 1.0};
            default -> new double[]{0.25, 0.10, 0.25, 1.0};
        };
    }

    private static double approximationError(EasingSpec spec, double[] bezier) {
        double error = 0.0;
        for (int i = 1; i < 24; i++) {
            double t = i / 24.0;
            double expected = Easing.apply(spec, t);
            double actual = Easing.cubicBezier(bezier[0], bezier[1], bezier[2], bezier[3], t);
            double diff = actual - expected;
            double weight = 1.0 + Math.abs(t - 0.5);
            error += diff * diff * weight;
        }
        return error;
    }

    private static double clampBezierParam(int index, double value) {
        return switch (index) {
            case 0, 2 -> Math.max(0.0, Math.min(1.0, value));
            default -> Math.max(-0.5, Math.min(1.5, value));
        };
    }

    private static double clampUnitRange(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
