package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class KeyframeEditor extends VBox {
    private final Label lblEntity;
    private final Label lblProperty;
    private final TextField tfTime;
    private final Slider sliderTime;
    private final TextField tfValue;
    private final Slider sliderValue;
    private final ComboBox<Easing.Type> cbEasing;
    private final Button btnDelete;
    private final Button btnResetValue;

    private static final String FIELD_STYLE =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 3 6; -fx-font-size: 11px;";

    private final Label lblEmptyHint;
    private final GridPane grid;

    private Keyframe currentKeyframe;
    private PropertyType currentProperty;
    private Runnable onKeyframeChanged;
    private Runnable onDeleteRequested;

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

        cbEasing = new ComboBox<>();
        cbEasing.getItems().addAll(Easing.Type.values());
        cbEasing.setValue(Easing.Type.LINEAR);

        btnDelete = new Button("Delete");
        btnDelete.setStyle("-fx-background-color: #e05577; -fx-text-fill: #0a0a0a; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnDelete.setTooltip(new Tooltip("Delete this keyframe (Del)"));

        btnResetValue = new Button("Reset");
        btnResetValue.setTooltip(new Tooltip("Reset to property default"));
        btnResetValue.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 3; " +
            "-fx-border-radius: 3; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;");

        HBox actionRow = new HBox(6, btnDelete, btnResetValue);

        grid.add(new Label("Entity:"), 0, 0);
        grid.add(lblEntity, 1, 0);
        grid.add(new Label("Property:"), 0, 1);
        grid.add(lblProperty, 1, 1);
        grid.add(new Label("Time (ms):"), 0, 2);
        grid.add(timeRow, 1, 2);
        grid.add(new Label("Value:"), 0, 3);
        grid.add(valueRow, 1, 3);
        grid.add(new Label("Easing:"), 0, 4);
        grid.add(cbEasing, 1, 4);
        grid.add(actionRow, 1, 5);

        for (var node : grid.getChildren()) {
            if (node instanceof Label l && l != header) l.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        }

        grid.setVisible(false);
        grid.setManaged(false);
        getChildren().addAll(header, lblEmptyHint, grid);

        tfTime.setOnAction(e -> applyChanges());
        tfValue.setOnAction(e -> applyChanges());
        cbEasing.setOnAction(e -> applyChanges());

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
            if (currentKeyframe != null && currentProperty != null) {
                currentKeyframe.setValue(currentProperty.getDefaultValue());
                tfValue.setText(String.format("%.2f", currentProperty.getDefaultValue()));
                sliderValue.setValue(currentProperty.getDefaultValue());
                if (onKeyframeChanged != null) onKeyframeChanged.run();
            }
        });
        btnDelete.setOnAction(e -> {
            if (currentKeyframe != null && onDeleteRequested != null) {
                onDeleteRequested.run();
            }
            currentKeyframe = null;
            currentProperty = null;
            setFieldsDisabled(true);
        });

        setFieldsDisabled(true);
    }

    public void setKeyframe(Keyframe kf, PropertyType property) {
        this.currentKeyframe = kf;
        this.currentProperty = property;

        if (kf == null) {
            lblEntity.setText("-");
            lblProperty.setText("-");
            tfTime.setText("");
            tfValue.setText("");
            cbEasing.setValue(Easing.Type.LINEAR);
            showEmptyState(true);
            setFieldsDisabled(true);
        } else {
            showEmptyState(false);
            lblProperty.setText(property != null ? property.getDisplayName() : "-");
            tfTime.setText(String.format("%.0f", kf.getTimeMs()));
            tfValue.setText(String.format("%.2f", kf.getValue()));
            cbEasing.setValue(kf.getEasing());
            sliderTime.setValue(kf.getTimeMs());
            configureSliderForProperty(property);
            sliderValue.setValue(kf.getValue());
            setFieldsDisabled(false);
        }
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

    private void applyChanges() {
        if (currentKeyframe == null) return;

        try {
            double time = Double.parseDouble(tfTime.getText());
            currentKeyframe.setTimeMs(time);
        } catch (NumberFormatException ignored) {}

        try {
            double value = Double.parseDouble(tfValue.getText());
            currentKeyframe.setValue(value);
        } catch (NumberFormatException ignored) {}

        currentKeyframe.setEasing(cbEasing.getValue());

        if (onKeyframeChanged != null) onKeyframeChanged.run();
    }

    private void configureSliderForProperty(PropertyType prop) {
        if (prop == null) return;
        switch (prop) {
            case X, Y, CAMERA_X, CAMERA_Y -> {
                sliderValue.setMin(-2000); sliderValue.setMax(2000);
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
        cbEasing.setDisable(disabled);
        btnDelete.setDisable(disabled);
        btnResetValue.setDisable(disabled);
    }
}
