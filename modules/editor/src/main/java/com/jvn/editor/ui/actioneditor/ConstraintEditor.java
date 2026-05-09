package com.jvn.editor.ui.actioneditor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * UI panel for managing entity constraints (parent-child, look-at).
 */
public class ConstraintEditor extends VBox {
    
    private AnimationProject project;
    private Runnable onConstraintChanged;
    
    private ComboBox<String> cmbEntity;
    private ComboBox<Constraint.Type> cmbConstraintType;
    private ComboBox<String> cmbTargetEntity;
    private Spinner<Double> spOffsetX;
    private Spinner<Double> spOffsetY;
    private CheckBox cbInheritRotation;
    private CheckBox cbInheritScale;
    private Button btnApply;
    private Button btnRemove;
    
    private static final String ACCENT_BUTTON_STYLE = "-fx-background-color: #007acc; -fx-text-fill: white; -fx-border-color: #005a9e; -fx-border-radius: 4; -fx-background-radius: 4;";
    private static final String DANGER_BUTTON_STYLE = "-fx-background-color: #c42b1c; -fx-text-fill: white; -fx-border-color: #a02015; -fx-border-radius: 4; -fx-background-radius: 4;";
    
    public ConstraintEditor() {
        setSpacing(10);
        setPadding(new Insets(10));
        setFillWidth(true);
        
        buildUI();
    }
    
    private void buildUI() {
        // Header
        Label header = new Label("Constraints");
        header.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e0e0e0;");
        
        // Entity selector
        Label lblEntity = new Label("Entity:");
        lblEntity.setStyle("-fx-text-fill: #a0a0a0;");
        cmbEntity = new ComboBox<>();
        cmbEntity.setPromptText("Select entity...");
        cmbEntity.setPrefWidth(Double.MAX_VALUE);
        cmbEntity.setCellFactory(lv -> new EntityListCell());
        cmbEntity.setButtonCell(new EntityListCell());
        cmbEntity.setOnAction(e -> onEntitySelected());
        
        // Constraint type
        Label lblType = new Label("Constraint Type:");
        lblType.setStyle("-fx-text-fill: #a0a0a0;");
        cmbConstraintType = new ComboBox<>();
        cmbConstraintType.getItems().addAll(Constraint.Type.PARENT_CHILD, Constraint.Type.LOOK_AT);
        cmbConstraintType.setPromptText("Select type...");
        cmbConstraintType.setPrefWidth(Double.MAX_VALUE);
        cmbConstraintType.setOnAction(e -> onConstraintTypeChanged());
        
        // Target entity
        Label lblTarget = new Label("Target Entity:");
        lblTarget.setStyle("-fx-text-fill: #a0a0a0;");
        cmbTargetEntity = new ComboBox<>();
        cmbTargetEntity.setPromptText("Select target...");
        cmbTargetEntity.setPrefWidth(Double.MAX_VALUE);
        cmbTargetEntity.setCellFactory(lv -> new EntityListCell());
        cmbTargetEntity.setButtonCell(new EntityListCell());
        
        // Offset controls (for parent-child)
        GridPane offsetGrid = new GridPane();
        offsetGrid.setHgap(10);
        offsetGrid.setVgap(8);
        
        Label lblOffsetX = new Label("Offset X:");
        lblOffsetX.setStyle("-fx-text-fill: #a0a0a0;");
        spOffsetX = new Spinner<>(-1000.0, 1000.0, 0.0, 10.0);
        spOffsetX.setPrefWidth(120);
        spOffsetX.setEditable(true);
        
        Label lblOffsetY = new Label("Offset Y:");
        lblOffsetY.setStyle("-fx-text-fill: #a0a0a0;");
        spOffsetY = new Spinner<>(-1000.0, 1000.0, 0.0, 10.0);
        spOffsetY.setPrefWidth(120);
        spOffsetY.setEditable(true);
        
        offsetGrid.add(lblOffsetX, 0, 0);
        offsetGrid.add(spOffsetX, 1, 0);
        offsetGrid.add(lblOffsetY, 0, 1);
        offsetGrid.add(spOffsetY, 1, 1);
        
        // Inherit controls (for parent-child)
        cbInheritRotation = new CheckBox("Inherit Rotation");
        cbInheritRotation.setSelected(true);
        cbInheritRotation.setStyle("-fx-text-fill: #e0e0e0;");
        
        cbInheritScale = new CheckBox("Inherit Scale");
        cbInheritScale.setSelected(true);
        cbInheritScale.setStyle("-fx-text-fill: #e0e0e0;");
        
        HBox inheritBox = new HBox(15, cbInheritRotation, cbInheritScale);
        inheritBox.setAlignment(Pos.CENTER_LEFT);
        
        // Buttons
        btnApply = new Button("Apply Constraint");
        btnApply.setStyle(ACCENT_BUTTON_STYLE);
        btnApply.setPrefWidth(Double.MAX_VALUE);
        btnApply.setOnAction(e -> applyConstraint());
        
        btnRemove = new Button("Remove Constraint");
        btnRemove.setStyle(DANGER_BUTTON_STYLE);
        btnRemove.setPrefWidth(Double.MAX_VALUE);
        btnRemove.setOnAction(e -> removeConstraint());
        
        // Layout
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(10));
        
        form.add(lblEntity, 0, 0);
        form.add(cmbEntity, 1, 0);
        form.add(lblType, 0, 1);
        form.add(cmbConstraintType, 1, 1);
        form.add(lblTarget, 0, 2);
        form.add(cmbTargetEntity, 1, 2);
        
        // Parent-child specific controls
        VBox parentChildControls = new VBox(10, offsetGrid, inheritBox);
        parentChildControls.setPadding(new Insets(10));
        parentChildControls.setStyle("-fx-background-color: #252525; -fx-background-radius: 4;");
        parentChildControls.setVisible(false);
        parentChildControls.setManaged(false);
        
        form.add(parentChildControls, 0, 3, 2, 1);
        
        HBox buttonBox = new HBox(10, btnApply, btnRemove);
        buttonBox.setSpacing(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        getChildren().addAll(header, new Separator(), form, buttonBox);
        
        // Store reference to parent-child controls
        parentChildControls.setId("parentChildControls");
    }
    
    public void setProject(AnimationProject project) {
        this.project = project;
        refreshEntityList();
    }
    
    public void setOnConstraintChanged(Runnable onConstraintChanged) {
        this.onConstraintChanged = onConstraintChanged;
    }
    
    private void refreshEntityList() {
        if (project == null) return;
        
        String selectedEntity = cmbEntity.getValue();
        String selectedTarget = cmbTargetEntity.getValue();
        
        cmbEntity.getItems().clear();
        cmbTargetEntity.getItems().clear();
        
        for (EntityTrack track : project.getTracks()) {
            String name = track.getEntityName();
            cmbEntity.getItems().add(name);
            cmbTargetEntity.getItems().add(name);
        }
        
        cmbEntity.setValue(selectedEntity);
        cmbTargetEntity.setValue(selectedTarget);
    }
    
    private void onEntitySelected() {
        String entityName = cmbEntity.getValue();
        if (entityName == null || entityName.isBlank() || project == null) {
            clearForm();
            return;
        }
        
        Constraint constraint = project.getConstraint(entityName);
        if (constraint != null) {
            loadConstraint(constraint);
        } else {
            clearForm();
        }
    }
    
    private void onConstraintTypeChanged() {
        Constraint.Type type = cmbConstraintType.getValue();
        VBox parentChildControls = (VBox) lookup("#parentChildControls");
        
        if (parentChildControls != null) {
            boolean isParentChild = type == Constraint.Type.PARENT_CHILD;
            parentChildControls.setVisible(isParentChild);
            parentChildControls.setManaged(isParentChild);
        }
    }
    
    private void loadConstraint(Constraint constraint) {
        cmbConstraintType.setValue(constraint.getType());
        cmbTargetEntity.setValue(constraint.getTargetEntityName());
        
        if (constraint.getType() == Constraint.Type.PARENT_CHILD) {
            spOffsetX.getValueFactory().setValue(constraint.getOffsetX());
            spOffsetY.getValueFactory().setValue(constraint.getOffsetY());
            cbInheritRotation.setSelected(constraint.isInheritRotation());
            cbInheritScale.setSelected(constraint.isInheritScale());
        }
        
        onConstraintTypeChanged();
    }
    
    private void clearForm() {
        cmbConstraintType.setValue(null);
        cmbTargetEntity.setValue(null);
        spOffsetX.getValueFactory().setValue(0.0);
        spOffsetY.getValueFactory().setValue(0.0);
        cbInheritRotation.setSelected(true);
        cbInheritScale.setSelected(true);
        onConstraintTypeChanged();
    }
    
    private void applyConstraint() {
        String entityName = cmbEntity.getValue();
        Constraint.Type type = cmbConstraintType.getValue();
        String targetName = cmbTargetEntity.getValue();
        
        if (entityName == null || entityName.isBlank() || type == null || targetName == null || targetName.isBlank()) {
            return;
        }
        
        if (entityName.equals(targetName)) {
            return; // Can't constrain to self
        }
        
        Constraint constraint;
        if (type == Constraint.Type.PARENT_CHILD) {
            double offsetX = spOffsetX.getValue();
            double offsetY = spOffsetY.getValue();
            boolean inheritRot = cbInheritRotation.isSelected();
            boolean inheritScale = cbInheritScale.isSelected();
            constraint = Constraint.parentChild(targetName, offsetX, offsetY, inheritRot, inheritScale);
        } else {
            constraint = Constraint.lookAt(targetName);
        }
        
        if (project != null) {
            project.setConstraint(entityName, constraint);
            if (onConstraintChanged != null) {
                onConstraintChanged.run();
            }
        }
    }
    
    private void removeConstraint() {
        String entityName = cmbEntity.getValue();
        if (entityName == null || entityName.isBlank()) return;
        
        if (project != null) {
            project.removeConstraint(entityName);
            clearForm();
            if (onConstraintChanged != null) {
                onConstraintChanged.run();
            }
        }
    }
    
    private static class EntityListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item);
                setStyle("-fx-text-fill: #e0e0e0;");
            }
        }
    }
    
    public static Tab createTab() {
        ConstraintEditor editor = new ConstraintEditor();
        Tab tab = new Tab("Constraints", editor);
        tab.setClosable(false);
        return tab;
    }
}
