package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class KeyframeEditor extends VBox {
    private final Label lblEntity;
    private final Label lblProperty;
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
    private final Button btnDelete;
    private final Button btnResetValue;
    private final Label lblCameraState;
    private final Label lblCurvePresetHint;
    private final Label lblTimeStepHint;
    private final Label lblValueStepHint;
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
    private static final double EDITOR_WORKING_WIDTH = 760.0;

    private final Label lblEmptyHint;
    private final VBox contentBox;
    private double timelineDurationMs = 3000.0;
    private Keyframe currentKeyframe;
    private PropertyType currentProperty;
    private final List<Keyframe> currentSelection = new ArrayList<>();
    private boolean updatingUi = false;
    private boolean curveEditorExpanded = false;
    private String activePresetEditId;
    private String activePresetEditName;
    private Runnable onKeyframeChanged;
    private Runnable onDeleteRequested;
    private java.util.function.BiConsumer<Double, Double> onPivotPresetApplied;
    private java.util.function.Consumer<Boolean> onCurveEditorExpandedChanged;

    public KeyframeEditor() {
        setSpacing(10);
        setPadding(new Insets(10, 10, 8, 10));
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

        lblEntity = new Label("-");
        lblEntity.setStyle(META_VALUE_STYLE);
        lblEntity.setWrapText(true);
        lblEntity.setMaxWidth(Double.MAX_VALUE);
        lblProperty = new Label("-");
        lblProperty.setStyle(META_VALUE_STYLE);
        lblProperty.setWrapText(true);
        lblProperty.setMaxWidth(Double.MAX_VALUE);

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
        lblCurvePresetHint = new Label();
        lblCurvePresetHint.setStyle(SUBTLE_HINT_STYLE);
        lblCurvePresetHint.setWrapText(true);

        lblTimeStepHint = new Label();
        lblTimeStepHint.setStyle(SUBTLE_HINT_STYLE);
        lblValueStepHint = new Label();
        lblValueStepHint.setStyle(SUBTLE_HINT_STYLE);

        presetLibraryPanel = new PuppeteerEasingPresetLibraryPanel();
        presetLibraryPanel.setCurrentSpecSupplier(this::resolveEditorEasingSpec);
        presetLibraryPanel.setSelectedEntrySupplier(cbEasing::getSelectedEntry);
        presetLibraryPanel.setOnPresetApplied(spec -> {
            applyExplicitEasingSpec(spec);
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
                currentKeyframe.setBezierParams(params[0], params[1], params[2], params[3]);
                cbEasing.setCurrentSpec(currentKeyframe.getEasingSpec());
                if (onKeyframeChanged != null) onKeyframeChanged.run();
            }
        });

        VBox entityTile = buildMetaTile("Entity", lblEntity);
        VBox propertyTile = buildMetaTile("Property", lblProperty);
        HBox infoRow = new HBox(10, entityTile, propertyTile);
        infoRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(entityTile, Priority.ALWAYS);
        HBox.setHgrow(propertyTile, Priority.ALWAYS);

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

        HBox curveToolbar = new HBox(8, new Label("Curve Workspace"), createSpacer(), btnEditCurve, btnUpdateCurvePreset, btnExpandCurveEditor);
        curveToolbar.setAlignment(Pos.CENTER_LEFT);
        ((Label) curveToolbar.getChildren().get(0)).setStyle(SECTION_TITLE_STYLE);
        VBox curveSection = new VBox(8, curveToolbar, lblCurvePresetHint, curveEditor);
        curveSection.setStyle(SECTION_STYLE);
        curveSection.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(curveEditor, Priority.ALWAYS);

        lblCameraState = new Label("X 0.0  Y 0.0  Z 1.00");
        lblCameraState.setStyle("-fx-text-fill: #d5b27f; -fx-font-size: 11px; -fx-font-family: monospace;");
        Label cameraLabel = new Label("Camera");
        cameraLabel.setStyle(META_CAPTION_STYLE);
        VBox cameraBox = new VBox(2, cameraLabel, lblCameraState);
        cameraBox.setStyle(TILE_STYLE);
        cameraBox.setMinWidth(180);

        HBox footerRow = new HBox(8, cameraBox, createSpacer(), btnResetValue, btnDelete);
        footerRow.setAlignment(Pos.CENTER_LEFT);

        contentBox.getChildren().addAll(
            infoRow,
            valueSection,
            easingSection,
            curveSection,
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
        editorBody.setMinWidth(EDITOR_WORKING_WIDTH);
        editorBody.setPrefWidth(EDITOR_WORKING_WIDTH);
        editorBody.setMaxWidth(EDITOR_WORKING_WIDTH);

        ScrollPane editorScrollPane = new ScrollPane(editorBody);
        editorScrollPane.setFitToWidth(false);
        editorScrollPane.setFitToHeight(false);
        editorScrollPane.setPannable(true);
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
        cbInterpolation.setOnAction(e -> {
            applyChanges();
            updateCurveEditorState();
        });

        cbEasing.setOnAction(e -> {
            applyChanges();
            syncPresetEditSessionFromSelection(false);
            refreshCurveEditorPreview();
            updateCurveEditorState();
        });
        btnPresetLibrary.setOnAction(e -> {
            presetLibraryPanel.toggleVisible();
            refreshPresetUiState();
        });
        btnEditCurve.setOnAction(e -> beginCurveEdit());
        btnUpdateCurvePreset.setOnAction(e -> updateActivePresetFromCurve());
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
        refreshPresetUiState();
    }

    private VBox buildMetaTile(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(META_CAPTION_STYLE);
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

    private void updateSelectionModeBadge() {
        if (currentSelection.size() > 1) {
            lblSelectionMode.setText("Multi-Select");
            lblSelectionMode.setStyle(BADGE_MULTI_STYLE);
            return;
        }
        if (currentKeyframe != null) {
            lblSelectionMode.setText("Single Keyframe");
            lblSelectionMode.setStyle(BADGE_ACTIVE_STYLE);
            return;
        }
        lblSelectionMode.setText("No Selection");
        lblSelectionMode.setStyle(BADGE_IDLE_STYLE);
    }

    private void updateStepHints() {
        lblTimeStepHint.setText("Nudge: " + formatStep(resolveTimeNudgeStep(false)) + " ms  |  Shift " + formatStep(resolveTimeNudgeStep(true)) + " ms");
        double fine = resolveValueNudgeStep(currentProperty != null ? currentProperty : PropertyType.X, false);
        double large = resolveValueNudgeStep(currentProperty != null ? currentProperty : PropertyType.X, true);
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
        cbEasing.setProjectRoot(root);
        presetLibraryPanel.setProjectRoot(root);
        refreshPresetUiState();
    }

    public void setKeyframe(Keyframe kf, PropertyType property) {
        updatingUi = true;
        currentSelection.clear();
        this.currentKeyframe = kf;
        this.currentProperty = property;

        if (kf == null) {
            clearPresetEditSession();
            lblProperty.setText("-");
            tfTime.setText("");
            tfValue.setText("");
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
        updateStepHints();
        updateSelectionModeBadge();
        refreshPresetUiState();
    }

    public void setEntityName(String name) {
        lblEntity.setText(name != null ? name : "-");
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
        applyExplicitEasingSpec(editable);
        if (onKeyframeChanged != null) onKeyframeChanged.run();
        refreshPresetUiState();
    }

    private void updateActivePresetFromCurve() {
        if (activePresetEditId == null || activePresetEditName == null) return;
        EasingSpec current = resolveEditorEasingSpec();
        EasingSpec editable = current.getType() == Easing.Type.CUSTOM ? current : toEditableCurveSpec(current);
        if (cbEasing.updatePreset(activePresetEditId, activePresetEditName, editable)) {
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

    static double resolveTimeNudgeStep(boolean large) {
        return large ? 50.0 : 10.0;
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
        btnDelete.setDisable(disabled);
        btnResetValue.setDisable(disabled);
        tfTimeOffset.setDisable(disabled);
        tfValueOffset.setDisable(disabled);
        btnApplyBatch.setDisable(disabled);
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
        boolean tween = mode == Easing.Interpolation.TWEEN;
        boolean curveInteractive = tween
            && currentKeyframe != null
            && currentSelection.size() <= 1
            && resolveEditorEasingSpec().getType() == Easing.Type.CUSTOM;
        btnExpandCurveEditor.setDisable(!curveInteractive);
        if (currentSelection.size() > 1) {
            cbEasing.setDisable(!tween);
            curveEditor.setDisable(true);
            curveEditor.setOpacity(0.45);
            refreshCurveActionState();
            return;
        }
        if (currentKeyframe != null) {
            cbEasing.setDisable(!tween);
        }
        curveEditor.setDisable(currentKeyframe == null || !curveInteractive);
        curveEditor.setOpacity(tween ? 1.0 : 0.88);
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
            curveEditor.setEasingSpec(resolveSharedEasingSpec(currentSelection));
            return;
        }
        if (currentKeyframe != null) {
            curveEditor.setEasingSpec(currentKeyframe.getEasingSpec());
            return;
        }
        curveEditor.setEasingSpec(cbEasing.getSelectedSpec());
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
                lblCurvePresetHint.setText("Editing preset '" + activePresetEditName + "'. Drag the handles, then click Update Preset.");
            } else {
                lblCurvePresetHint.setText("Preset '" + activePresetEditName + "' is selected. Click Make Editable to tweak it.");
            }
            return;
        }
        if (customCurve) {
            lblCurvePresetHint.setText("Drag the red and green handles below to shape the curve.");
            return;
        }
        lblCurvePresetHint.setText("Click Make Editable to convert the current easing into a cubic bezier you can drag.");
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
}
