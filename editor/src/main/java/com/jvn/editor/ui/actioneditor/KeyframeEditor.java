package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class KeyframeEditor extends VBox {
    private final Label lblEntity;
    private final Label lblProperty;
    private final TextField tfTime;
    private final Slider sliderTime;
    private final TextField tfValue;
    private final Slider sliderValue;
    private final ComboBox<Easing.Interpolation> cbInterpolation;
    private final ComboBox<EasingPickerModel.Option> cbEasing;
    private final Button btnDelete;
    private final Button btnResetValue;
    private final Label lblCameraState;
    private final EasingCurveEditor curveEditor;
    private final ComboBox<PuppeteerEasingPresetStore.Preset> cbCurvePreset;
    private final TextField tfCurvePresetName;
    private final Button btnApplyCurvePreset;
    private final Button btnSaveCurvePreset;
    private final Button btnUpdateCurvePreset;
    private final Button btnDeleteCurvePreset;
    private final Label lblCurvePresetStatus;
    private final GridPane pivotPresetsGrid;
    private final Label lblPivotPresets;
    private final VBox batchBox;
    private final Label lblSelectionSummary;
    private final TextField tfTimeOffset;
    private final TextField tfValueOffset;
    private final Button btnApplyBatch;

    private static final String FIELD_STYLE =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 3 6; -fx-font-size: 11px;";
    private static final String FIELD_STYLE_ERROR =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #e05577; " +
        "-fx-border-width: 1.5; -fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 3 6; -fx-font-size: 11px;";

    private final Label lblEmptyHint;
    private final GridPane grid;
    private double timelineDurationMs = 3000.0;
    private final List<EasingPickerModel.Option> easingOptions;
    private final List<PuppeteerEasingPresetStore.Preset> curvePresets = new ArrayList<>();

    private Keyframe currentKeyframe;
    private PropertyType currentProperty;
    private final List<Keyframe> currentSelection = new ArrayList<>();
    private boolean updatingUi = false;
    private boolean syncingEasingSearch = false;
    private boolean updatingPresetUi = false;
    private File projectRoot;
    private Runnable onKeyframeChanged;
    private Runnable onDeleteRequested;
    private java.util.function.BiConsumer<Double, Double> onPivotPresetApplied;

    public KeyframeEditor() {
        setSpacing(6);
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #1a1a1a;");
        setMinHeight(140);

        Label header = new Label("Keyframe Editor");
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #e6e6e6; -fx-font-size: 12px;");

        lblEmptyHint = new Label("Select a keyframe in the timeline\nto edit its properties here.");
        lblEmptyHint.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-padding: 12 0 0 0;");
        lblEmptyHint.setWrapText(true);

        grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);

        lblEntity = new Label("-");
        lblProperty = new Label("-");

        tfTime = new TextField();
        tfTime.setPrefWidth(60);
        tfTime.setStyle(FIELD_STYLE);
        sliderTime = new Slider(0, 3000, 0);
        sliderTime.setTooltip(new Tooltip("Drag to adjust keyframe time"));
        HBox timeRow = new HBox(6, tfTime, sliderTime);
        HBox.setHgrow(sliderTime, Priority.ALWAYS);

        tfValue = new TextField();
        tfValue.setPrefWidth(60);
        tfValue.setStyle(FIELD_STYLE);
        sliderValue = new Slider(-2000, 2000, 0);
        sliderValue.setTooltip(new Tooltip("Drag to adjust value"));
        HBox valueRow = new HBox(6, tfValue, sliderValue);
        HBox.setHgrow(sliderValue, Priority.ALWAYS);

        easingOptions = EasingPickerModel.allOptions();
        cbEasing = new ComboBox<>();
        cbEasing.getItems().setAll(easingOptions);
        cbEasing.setEditable(true);
        cbEasing.setTooltip(new Tooltip("Type to filter easing names"));
        cbEasing.setConverter(new StringConverter<>() {
            @Override
            public String toString(EasingPickerModel.Option option) {
                return option != null ? option.label() : "";
            }

            @Override
            public EasingPickerModel.Option fromString(String string) {
                List<EasingPickerModel.Option> matches = EasingPickerModel.filter(string);
                return matches.isEmpty() ? resolveEasingOption(Easing.Type.LINEAR) : matches.get(0);
            }
        });
        cbEasing.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(EasingPickerModel.Option item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item.label() + "  [" + item.group() + "]");
                setTooltip(new Tooltip(Easing.token(item.type())));
            }
        });
        cbEasing.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(EasingPickerModel.Option item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        cbEasing.setValue(resolveEasingOption(Easing.Type.LINEAR));
        syncEasingEditorText();
        configureEasingSearch();
        cbInterpolation = new ComboBox<>();
        cbInterpolation.getItems().addAll(Easing.Interpolation.values());
        cbInterpolation.setValue(Easing.Interpolation.TWEEN);

        btnDelete = new Button("Delete");
        btnDelete.setStyle("-fx-background-color: #e05577; -fx-text-fill: #0a0a0a; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnDelete.setTooltip(new Tooltip("Delete this keyframe (Del)"));

        btnResetValue = new Button("Reset");
        btnResetValue.setTooltip(new Tooltip("Reset to property default"));
        btnResetValue.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;");

        HBox actionRow = new HBox(6, btnDelete, btnResetValue);

        lblSelectionSummary = new Label("No multi-selection");
        lblSelectionSummary.setStyle("-fx-text-fill: #8ea4c6; -fx-font-size: 11px;");
        tfTimeOffset = new TextField("0");
        tfTimeOffset.setPrefWidth(70);
        tfTimeOffset.setStyle(FIELD_STYLE);
        tfTimeOffset.setTooltip(new Tooltip("Add this delta to all selected keyframe times"));
        tfValueOffset = new TextField("0");
        tfValueOffset.setPrefWidth(70);
        tfValueOffset.setStyle(FIELD_STYLE);
        tfValueOffset.setTooltip(new Tooltip("Add this delta to all selected keyframe values"));
        btnApplyBatch = new Button("Apply Batch");
        btnApplyBatch.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;");
        btnApplyBatch.setTooltip(new Tooltip("Apply time and value offsets to the current multi-selection"));
        HBox batchOffsets = new HBox(6,
            new Label("Time Δ"), tfTimeOffset,
            new Label("Value Δ"), tfValueOffset,
            btnApplyBatch);
        batchBox = new VBox(4, lblSelectionSummary, batchOffsets);
        batchBox.setVisible(false);
        batchBox.setManaged(false);

        lblPivotPresets = new Label("Pivot Presets:");
        lblPivotPresets.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        pivotPresetsGrid = buildPivotPresetsGrid();
        pivotPresetsGrid.setVisible(false);
        pivotPresetsGrid.setManaged(false);
        lblPivotPresets.setVisible(false);
        lblPivotPresets.setManaged(false);

        grid.add(new Label("Entity:"), 0, 0);
        grid.add(lblEntity, 1, 0);
        grid.add(new Label("Property:"), 0, 1);
        grid.add(lblProperty, 1, 1);
        grid.add(new Label("Time (ms):"), 0, 2);
        grid.add(timeRow, 1, 2);
        grid.add(new Label("Value:"), 0, 3);
        grid.add(valueRow, 1, 3);
        curveEditor = new EasingCurveEditor();
        curveEditor.setOnBezierChanged(params -> {
            if (currentKeyframe != null && currentKeyframe.getEasing() == Easing.Type.CUSTOM) {
                currentKeyframe.setBezierParams(params[0], params[1], params[2], params[3]);
                if (onKeyframeChanged != null) onKeyframeChanged.run();
            }
        });

        cbCurvePreset = new ComboBox<>();
        cbCurvePreset.setPromptText("Project presets");
        cbCurvePreset.setTooltip(new Tooltip("Saved project easing presets"));
        cbCurvePreset.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PuppeteerEasingPresetStore.Preset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item.name());
                setTooltip(new Tooltip(item.spec().toDslString()));
            }
        });
        cbCurvePreset.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PuppeteerEasingPresetStore.Preset item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        HBox.setHgrow(cbCurvePreset, Priority.ALWAYS);

        btnApplyCurvePreset = new Button("Apply");
        btnApplyCurvePreset.setTooltip(new Tooltip("Apply the selected preset to the current easing"));
        btnApplyCurvePreset.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;");
        HBox presetPickerRow = new HBox(6, cbCurvePreset, btnApplyCurvePreset);
        HBox.setHgrow(cbCurvePreset, Priority.ALWAYS);

        tfCurvePresetName = new TextField();
        tfCurvePresetName.setPromptText("Preset name");
        tfCurvePresetName.setStyle(FIELD_STYLE);
        HBox.setHgrow(tfCurvePresetName, Priority.ALWAYS);

        btnSaveCurvePreset = new Button("Save New");
        btnSaveCurvePreset.setTooltip(new Tooltip("Save the current easing as a new project preset"));
        btnSaveCurvePreset.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;");

        btnUpdateCurvePreset = new Button("Update");
        btnUpdateCurvePreset.setTooltip(new Tooltip("Overwrite the selected preset with the current easing"));
        btnUpdateCurvePreset.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;");

        btnDeleteCurvePreset = new Button("Delete");
        btnDeleteCurvePreset.setTooltip(new Tooltip("Delete the selected preset from the project"));
        btnDeleteCurvePreset.setStyle("-fx-background-color: #3a2323; -fx-text-fill: #f1c1c1; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;");

        HBox presetActionsRow = new HBox(6,
            tfCurvePresetName,
            btnSaveCurvePreset,
            btnUpdateCurvePreset,
            btnDeleteCurvePreset);

        lblCurvePresetStatus = new Label("Open a project to save easing presets.");
        lblCurvePresetStatus.setStyle("-fx-text-fill: #7f8796; -fx-font-size: 10px;");
        VBox presetBox = new VBox(4, presetPickerRow, presetActionsRow, lblCurvePresetStatus);

        grid.add(new Label("Interp:"), 0, 4);
        grid.add(cbInterpolation, 1, 4);
        grid.add(new Label("Easing:"), 0, 5);
        grid.add(cbEasing, 1, 5);
        grid.add(curveEditor, 0, 6, 2, 1);
        grid.add(new Label("Presets:"), 0, 7);
        grid.add(presetBox, 1, 7);
        grid.add(batchBox, 0, 8, 2, 1);
        grid.add(lblPivotPresets, 0, 9);
        grid.add(pivotPresetsGrid, 1, 9);
        grid.add(actionRow, 1, 10);
        lblCameraState = new Label("X 0.0  Y 0.0  Z 1.00");
        lblCameraState.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px; -fx-font-family: monospace;");
        grid.add(new Label("Camera:"), 0, 11);
        grid.add(lblCameraState, 1, 11);

        for (var node : grid.getChildren()) {
            if (node instanceof Label l && l != header) l.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        }

        grid.setVisible(false);
        grid.setManaged(false);
        getChildren().addAll(header, lblEmptyHint, grid);

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
        cbInterpolation.setOnAction(e -> {
            applyChanges();
            updateCurveEditorState();
        });

        cbEasing.setOnAction(e -> {
            applyChanges();
            syncEasingEditorText();
            refreshCurveEditorPreview();
            updateCurveEditorState();
        });
        cbCurvePreset.setOnAction(e -> {
            if (updatingPresetUi) return;
            PuppeteerEasingPresetStore.Preset preset = cbCurvePreset.getValue();
            tfCurvePresetName.setText(preset != null ? preset.name() : "");
            refreshPresetUiState();
        });
        tfCurvePresetName.textProperty().addListener((obs, oldValue, newValue) -> refreshPresetUiState());
        tfCurvePresetName.setOnAction(e -> saveCurvePreset(false));
        btnApplyCurvePreset.setOnAction(e -> applySelectedCurvePreset());
        btnSaveCurvePreset.setOnAction(e -> saveCurvePreset(false));
        btnUpdateCurvePreset.setOnAction(e -> saveCurvePreset(true));
        btnDeleteCurvePreset.setOnAction(e -> deleteSelectedCurvePreset());

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
            setFieldsDisabled(true);
        });

        setFieldsDisabled(true);
        refreshPresetUiState();
    }

    public void setTimelineDurationMs(double durationMs) {
        timelineDurationMs = Math.max(100.0, durationMs);
        syncTimeSliderBounds(currentKeyframe != null ? currentKeyframe.getTimeMs() : 0.0);
    }

    public void setProjectRoot(File root) {
        projectRoot = root;
        reloadCurvePresets();
    }

    public void setKeyframe(Keyframe kf, PropertyType property) {
        updatingUi = true;
        currentSelection.clear();
        this.currentKeyframe = kf;
        this.currentProperty = property;

        if (kf == null) {
            lblProperty.setText("-");
            tfTime.setText("");
            tfValue.setText("");
            cbInterpolation.setValue(Easing.Interpolation.TWEEN);
            cbEasing.setValue(resolveEasingOption(Easing.Type.LINEAR));
            curveEditor.setInterpolation(Easing.Interpolation.TWEEN);
            curveEditor.setEasingSpec(EasingSpec.of(Easing.Type.LINEAR));
            syncEasingEditorText();
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
            cbEasing.setValue(resolveEasingOption(kf.getEasing()));
            syncEasingEditorText();
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
        lblSelectionSummary.setText(currentSelection.size() + " keyframes selected");
        tfTime.setText("");
        tfValue.setText("");
        tfTimeOffset.setText("0");
        tfValueOffset.setText("0");
        syncTimeSliderBounds(currentSelection.get(0).getTimeMs());
        cbInterpolation.setValue(resolveSharedInterpolation(currentSelection));
        cbEasing.setValue(resolveEasingOption(resolveSharedEasing(currentSelection)));
        syncEasingEditorText();
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

    public Keyframe getCurrentKeyframe() { return currentKeyframe; }
    public PropertyType getCurrentProperty() { return currentProperty; }

    public void setOnPivotPresetApplied(java.util.function.BiConsumer<Double, Double> callback) {
        this.onPivotPresetApplied = callback;
    }

    public void setCameraState(double cameraX, double cameraY, double cameraZoom) {
        lblCameraState.setText(String.format("X %.1f  Y %.1f  Z %.2f", cameraX, cameraY, cameraZoom));
    }

    private void reloadCurvePresets() {
        String selectedId = cbCurvePreset.getValue() != null ? cbCurvePreset.getValue().id() : null;
        curvePresets.clear();
        curvePresets.addAll(PuppeteerEasingPresetStore.load(projectRoot));
        updatingPresetUi = true;
        cbCurvePreset.getItems().setAll(curvePresets);
        PuppeteerEasingPresetStore.Preset selected = null;
        if (selectedId != null) {
            for (PuppeteerEasingPresetStore.Preset preset : curvePresets) {
                if (preset.id().equals(selectedId)) {
                    selected = preset;
                    break;
                }
            }
        }
        cbCurvePreset.setValue(selected);
        if (selected != null) {
            tfCurvePresetName.setText(selected.name());
        } else if (tfCurvePresetName.getText() == null || tfCurvePresetName.getText().isBlank()) {
            tfCurvePresetName.setText("");
        }
        updatingPresetUi = false;
        if (projectRoot == null) {
            setCurvePresetStatus("Open a project to save easing presets.");
        } else if (curvePresets.isEmpty()) {
            setCurvePresetStatus("No project presets yet. Save the current easing to " + PuppeteerEasingPresetStore.CONFIG_PATH + ".");
        } else {
            setCurvePresetStatus(curvePresets.size() + " project preset"
                + (curvePresets.size() == 1 ? "" : "s") + " loaded from " + PuppeteerEasingPresetStore.CONFIG_PATH + ".");
        }
        refreshPresetUiState();
    }

    private void persistCurvePresets(String successMessage) {
        if (projectRoot == null) {
            setCurvePresetStatus("Open a project before saving easing presets.");
            return;
        }
        try {
            PuppeteerEasingPresetStore.save(projectRoot, curvePresets);
            setCurvePresetStatus(successMessage);
        } catch (IOException ex) {
            setCurvePresetStatus("Failed to save easing presets: " + ex.getMessage());
        }
        refreshPresetUiState();
    }

    private void applySelectedCurvePreset() {
        PuppeteerEasingPresetStore.Preset preset = cbCurvePreset.getValue();
        if (preset == null) {
            setCurvePresetStatus("Select a preset to apply.");
            refreshPresetUiState();
            return;
        }
        applyExplicitEasingSpec(preset.spec());
        tfCurvePresetName.setText(preset.name());
        setCurvePresetStatus("Applied easing preset '" + preset.name() + "'.");
        if (onKeyframeChanged != null && (!currentSelection.isEmpty() || currentKeyframe != null)) {
            onKeyframeChanged.run();
        }
        refreshPresetUiState();
    }

    private void saveCurvePreset(boolean updateExisting) {
        if (projectRoot == null) {
            setCurvePresetStatus("Open a project before saving easing presets.");
            refreshPresetUiState();
            return;
        }
        String name = PuppeteerEasingPresetStore.normalizeName(tfCurvePresetName.getText());
        if (name.isBlank()) {
            setCurvePresetStatus("Preset name cannot be blank.");
            refreshPresetUiState();
            return;
        }
        EasingSpec spec = resolveEditorEasingSpec();
        if (updateExisting) {
            PuppeteerEasingPresetStore.Preset selected = cbCurvePreset.getValue();
            if (selected == null) {
                setCurvePresetStatus("Select a preset to update.");
                refreshPresetUiState();
                return;
            }
            int index = curvePresets.indexOf(selected);
            if (index < 0) {
                setCurvePresetStatus("The selected preset is no longer loaded.");
                refreshPresetUiState();
                return;
            }
            PuppeteerEasingPresetStore.Preset updated =
                new PuppeteerEasingPresetStore.Preset(selected.id(), name, spec);
            curvePresets.set(index, updated);
            updatingPresetUi = true;
            cbCurvePreset.getItems().setAll(curvePresets);
            cbCurvePreset.setValue(updated);
            updatingPresetUi = false;
            persistCurvePresets("Updated easing preset '" + updated.name() + "'.");
            return;
        }

        for (PuppeteerEasingPresetStore.Preset preset : curvePresets) {
            if (preset.name().equalsIgnoreCase(name)) {
                setCurvePresetStatus("Preset '" + name + "' already exists. Use Update instead.");
                refreshPresetUiState();
                return;
            }
        }
        PuppeteerEasingPresetStore.Preset created = new PuppeteerEasingPresetStore.Preset(
            PuppeteerEasingPresetStore.uniqueId(name, curvePresets, null),
            name,
            spec);
        curvePresets.add(created);
        updatingPresetUi = true;
        cbCurvePreset.getItems().setAll(curvePresets);
        cbCurvePreset.setValue(created);
        updatingPresetUi = false;
        persistCurvePresets("Saved easing preset '" + created.name() + "'.");
    }

    private void deleteSelectedCurvePreset() {
        if (projectRoot == null) {
            setCurvePresetStatus("Open a project before deleting easing presets.");
            refreshPresetUiState();
            return;
        }
        PuppeteerEasingPresetStore.Preset selected = cbCurvePreset.getValue();
        if (selected == null) {
            setCurvePresetStatus("Select a preset to delete.");
            refreshPresetUiState();
            return;
        }
        curvePresets.removeIf(preset -> preset.id().equals(selected.id()));
        updatingPresetUi = true;
        cbCurvePreset.getItems().setAll(curvePresets);
        cbCurvePreset.setValue(null);
        updatingPresetUi = false;
        tfCurvePresetName.setText("");
        persistCurvePresets("Deleted easing preset '" + selected.name() + "'.");
    }

    private void refreshPresetUiState() {
        boolean hasProject = projectRoot != null;
        boolean hasSelection = currentKeyframe != null || !currentSelection.isEmpty();
        boolean hasPreset = cbCurvePreset.getValue() != null;
        btnApplyCurvePreset.setDisable(!hasPreset);
        tfCurvePresetName.setDisable(!hasProject);
        btnSaveCurvePreset.setDisable(!hasProject || tfCurvePresetName.getText().trim().isEmpty());
        btnUpdateCurvePreset.setDisable(!hasProject || !hasPreset || tfCurvePresetName.getText().trim().isEmpty());
        btnDeleteCurvePreset.setDisable(!hasProject || !hasPreset);
        cbCurvePreset.setDisable(curvePresets.isEmpty());
        if (!hasProject) {
            tfCurvePresetName.setPromptText("Preset name (open a project first)");
        } else if (hasSelection) {
            tfCurvePresetName.setPromptText("Preset name");
        } else {
            tfCurvePresetName.setPromptText("Preset name (optional without selection)");
        }
    }

    private void setCurvePresetStatus(String message) {
        lblCurvePresetStatus.setText(message != null ? message : "");
    }

    private EasingSpec resolveEditorEasingSpec() {
        if (currentSelection.size() > 1) {
            return resolveSharedEasingSpec(currentSelection);
        }
        if (currentKeyframe != null) {
            return currentKeyframe.getEasingSpec();
        }
        Easing.Type type = selectedEasingType();
        if (type == Easing.Type.CUSTOM) {
            double[] params = curveEditor.getBezierParams();
            return EasingSpec.cubicBezier(params[0], params[1], params[2], params[3]);
        }
        return EasingSpec.of(type);
    }

    private void applyExplicitEasingSpec(EasingSpec spec) {
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        boolean priorUpdating = updatingUi;
        updatingUi = true;
        cbEasing.setValue(resolveEasingOption(resolved.getType()));
        syncEasingEditorText();
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
    }

    private void showEmptyState(boolean empty) {
        lblEmptyHint.setVisible(empty);
        lblEmptyHint.setManaged(empty);
        grid.setVisible(!empty);
        grid.setManaged(!empty);
    }

    private void setFieldsDisabled(boolean disabled) {
        tfTime.setDisable(disabled);
        sliderTime.setDisable(disabled);
        tfValue.setDisable(disabled);
        sliderValue.setDisable(disabled);
        cbInterpolation.setDisable(disabled);
        cbEasing.setDisable(disabled);
        btnDelete.setDisable(disabled);
        btnResetValue.setDisable(disabled);
        tfTimeOffset.setDisable(disabled);
        tfValueOffset.setDisable(disabled);
        btnApplyBatch.setDisable(disabled);
        if (!disabled) {
            updateCurveEditorState();
        }
        refreshPresetUiState();
    }

    private void showBatchEditor(boolean show) {
        batchBox.setVisible(show);
        batchBox.setManaged(show);
    }

    private void showPivotPresets(boolean show) {
        pivotPresetsGrid.setVisible(show);
        pivotPresetsGrid.setManaged(show);
        lblPivotPresets.setVisible(show);
        lblPivotPresets.setManaged(show);
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

        String btnStyle = "-fx-background-color: #2a2a2a; -fx-text-fill: #c0c0c0; -fx-background-radius: 2; " +
            "-fx-border-radius: 2; -fx-padding: 2 4; -fx-font-size: 9px; -fx-cursor: hand; -fx-min-width: 26; -fx-min-height: 18;";
        String btnHoverStyle = "-fx-background-color: #3a3a5a; -fx-text-fill: #f7d07a; -fx-background-radius: 2; " +
            "-fx-border-radius: 2; -fx-padding: 2 4; -fx-font-size: 9px; -fx-cursor: hand; -fx-min-width: 26; -fx-min-height: 18;";

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
        if (currentSelection.size() > 1) {
            cbEasing.setDisable(!tween);
            curveEditor.setDisable(true);
            curveEditor.setOpacity(0.45);
            return;
        }
        if (currentKeyframe != null) {
            cbEasing.setDisable(!tween);
        }
        curveEditor.setDisable(currentKeyframe == null);
        curveEditor.setOpacity(tween ? 1.0 : 0.88);
    }

    private void configureEasingSearch() {
        cbEasing.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (syncingEasingSearch) return;
            if (!cbEasing.getEditor().isFocused()) return;
            cbEasing.getItems().setAll(EasingPickerModel.filter(newValue));
            if (!cbEasing.isShowing()) cbEasing.show();
        });
        cbEasing.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                cbEasing.getItems().setAll(easingOptions);
                cbEasing.show();
            } else {
                commitEasingSearch();
            }
        });
        cbEasing.setOnHidden(e -> {
            if (!cbEasing.getEditor().isFocused()) commitEasingSearch();
        });
        cbEasing.getEditor().setOnAction(e -> commitEasingSearch());
    }

    private void commitEasingSearch() {
        EasingPickerModel.Option selection = cbEasing.getValue();
        List<EasingPickerModel.Option> matches = EasingPickerModel.filter(cbEasing.getEditor().getText());
        if ((selection == null || !matches.contains(selection)) && !matches.isEmpty()) {
            cbEasing.setValue(matches.get(0));
        }
        cbEasing.getItems().setAll(easingOptions);
        syncEasingEditorText();
    }

    private void syncEasingEditorText() {
        syncingEasingSearch = true;
        EasingPickerModel.Option value = cbEasing.getValue();
        cbEasing.getEditor().setText(value != null ? value.label() : "");
        syncingEasingSearch = false;
    }

    private EasingPickerModel.Option resolveEasingOption(Easing.Type type) {
        return EasingPickerModel.findByType(type);
    }

    private Easing.Type selectedEasingType() {
        EasingPickerModel.Option option = cbEasing.getValue();
        return option != null ? option.type() : Easing.Type.LINEAR;
    }

    private void applySelectedEasing(Keyframe keyframe) {
        if (keyframe == null) return;
        Easing.Type selectedType = selectedEasingType();
        if (keyframe.getEasing() == selectedType) return;
        EasingPickerModel.Option option = cbEasing.getValue();
        keyframe.setEasingSpec(option != null ? option.defaultSpec() : EasingSpec.of(Easing.Type.LINEAR));
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
        curveEditor.setEasingSpec(resolveEditorEasingSpec());
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
}
