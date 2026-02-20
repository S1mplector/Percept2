package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class KeyframeEditor extends VBox {
    private final Label lblEntity;
    private final Label lblProperty;
    private final TextField tfTime;
    private final TextField tfValue;
    private final ComboBox<Easing.Type> cbEasing;
    private final Button btnDelete;

    private Keyframe currentKeyframe;
    private PropertyType currentProperty;
    private Runnable onKeyframeChanged;

    public KeyframeEditor() {
        setSpacing(8);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #1e1e2e;");
        setMinHeight(150);

        Label header = new Label("Keyframe Editor");
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #cdd6f4;");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);

        lblEntity = new Label("-");
        lblProperty = new Label("-");
        tfTime = new TextField();
        tfTime.setPrefWidth(80);
        tfValue = new TextField();
        tfValue.setPrefWidth(80);

        cbEasing = new ComboBox<>();
        cbEasing.getItems().addAll(Easing.Type.values());
        cbEasing.setValue(Easing.Type.LINEAR);

        btnDelete = new Button("Delete");
        btnDelete.setStyle("-fx-background-color: #f38ba8;");

        grid.add(new Label("Entity:"), 0, 0);
        grid.add(lblEntity, 1, 0);
        grid.add(new Label("Property:"), 0, 1);
        grid.add(lblProperty, 1, 1);
        grid.add(new Label("Time (ms):"), 0, 2);
        grid.add(tfTime, 1, 2);
        grid.add(new Label("Value:"), 0, 3);
        grid.add(tfValue, 1, 3);
        grid.add(new Label("Easing:"), 0, 4);
        grid.add(cbEasing, 1, 4);
        grid.add(btnDelete, 1, 5);

        for (var node : grid.getChildren()) {
            if (node instanceof Label l) l.setStyle("-fx-text-fill: #a6adc8;");
        }

        getChildren().addAll(header, grid);

        tfTime.setOnAction(e -> applyChanges());
        tfValue.setOnAction(e -> applyChanges());
        cbEasing.setOnAction(e -> applyChanges());
        btnDelete.setOnAction(e -> {
            if (currentKeyframe != null && onKeyframeChanged != null) {
                currentKeyframe = null;
                onKeyframeChanged.run();
            }
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
            setFieldsDisabled(true);
        } else {
            lblProperty.setText(property != null ? property.getDisplayName() : "-");
            tfTime.setText(String.format("%.0f", kf.getTimeMs()));
            tfValue.setText(String.format("%.2f", kf.getValue()));
            cbEasing.setValue(kf.getEasing());
            setFieldsDisabled(false);
        }
    }

    public void setEntityName(String name) {
        lblEntity.setText(name != null ? name : "-");
    }

    public void setOnKeyframeChanged(Runnable callback) {
        this.onKeyframeChanged = callback;
    }

    public Keyframe getCurrentKeyframe() { return currentKeyframe; }

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

    private void setFieldsDisabled(boolean disabled) {
        tfTime.setDisable(disabled);
        tfValue.setDisable(disabled);
        cbEasing.setDisable(disabled);
        btnDelete.setDisable(disabled);
    }
}
