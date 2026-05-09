package com.jvn.editor.ui.actioneditor;

import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * UI panel for managing entity anchors (named pivot points).
 * Anchors allow animators to place rotation centers at specific points
 * like elbows, shoulders, etc. on sprites.
 */
public class AnchorEditor extends VBox {
    
    private AnimationProject project;
    private Runnable onAnchorChanged;
    
    private ComboBox<String> cmbEntity;
    private ListView<String> anchorListView;
    private TextField txtAnchorName;
    private Spinner<Double> spAnchorX;
    private Spinner<Double> spAnchorY;
    private CheckBox cbRelative;
    private Button btnAddAnchor;
    private Button btnRemoveAnchor;
    
    private static final String ACCENT_BUTTON_STYLE = "-fx-background-color: #007acc; -fx-text-fill: white; -fx-border-color: #005a9e; -fx-border-radius: 4; -fx-background-radius: 4;";
    private static final String DANGER_BUTTON_STYLE = "-fx-background-color: #c42b1c; -fx-text-fill: white; -fx-border-color: #a02015; -fx-border-radius: 4; -fx-background-radius: 4;";
    
    public AnchorEditor() {
        setSpacing(10);
        setPadding(new Insets(10));
        setFillWidth(true);
        
        buildUI();
    }
    
    private void buildUI() {
        // Header
        Label header = new Label("Anchors");
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
        
        // Anchor list
        Label lblAnchors = new Label("Anchors:");
        lblAnchors.setStyle("-fx-text-fill: #a0a0a0;");
        anchorListView = new ListView<>();
        anchorListView.setPrefHeight(120);
        anchorListView.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333;");
        anchorListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> onAnchorSelected(newVal));
        
        // Anchor name
        Label lblName = new Label("Anchor Name:");
        lblName.setStyle("-fx-text-fill: #a0a0a0;");
        txtAnchorName = new TextField();
        txtAnchorName.setPromptText("e.g., elbow, shoulder");
        txtAnchorName.setPrefWidth(Double.MAX_VALUE);
        
        // Position controls
        GridPane posGrid = new GridPane();
        posGrid.setHgap(10);
        posGrid.setVgap(8);
        
        Label lblX = new Label("X:");
        lblX.setStyle("-fx-text-fill: #a0a0a0;");
        spAnchorX = new Spinner<>(-1000.0, 1000.0, 0.5, 0.05);
        spAnchorX.setPrefWidth(120);
        spAnchorX.setEditable(true);
        
        Label lblY = new Label("Y:");
        lblY.setStyle("-fx-text-fill: #a0a0a0;");
        spAnchorY = new Spinner<>(-1000.0, 1000.0, 0.5, 0.05);
        spAnchorY.setPrefWidth(120);
        spAnchorY.setEditable(true);
        
        posGrid.add(lblX, 0, 0);
        posGrid.add(spAnchorX, 1, 0);
        posGrid.add(lblY, 0, 1);
        posGrid.add(spAnchorY, 1, 1);
        
        // Relative checkbox
        cbRelative = new CheckBox("Relative (0-1)");
        cbRelative.setSelected(true);
        cbRelative.setStyle("-fx-text-fill: #e0e0e0;");
        
        // Buttons
        btnAddAnchor = new Button("Add Anchor");
        btnAddAnchor.setStyle(ACCENT_BUTTON_STYLE);
        btnAddAnchor.setPrefWidth(Double.MAX_VALUE);
        btnAddAnchor.setOnAction(e -> addAnchor());
        
        btnRemoveAnchor = new Button("Remove Anchor");
        btnRemoveAnchor.setStyle(DANGER_BUTTON_STYLE);
        btnRemoveAnchor.setPrefWidth(Double.MAX_VALUE);
        btnRemoveAnchor.setOnAction(e -> removeAnchor());
        btnRemoveAnchor.setDisable(true);
        
        // Layout
        VBox form = new VBox(10);
        form.setPadding(new Insets(10));
        form.getChildren().addAll(
            lblEntity, cmbEntity,
            new Separator(),
            lblAnchors, anchorListView,
            new Separator(),
            lblName, txtAnchorName,
            posGrid,
            cbRelative,
            btnAddAnchor,
            btnRemoveAnchor
        );
        
        getChildren().addAll(header, new Separator(), form);
    }
    
    public void setProject(AnimationProject project) {
        this.project = project;
        refreshEntityList();
    }
    
    public void setOnAnchorChanged(Runnable onAnchorChanged) {
        this.onAnchorChanged = onAnchorChanged;
    }
    
    private void refreshEntityList() {
        if (project == null) return;
        
        String selectedEntity = cmbEntity.getValue();
        
        cmbEntity.getItems().clear();
        
        for (EntityTrack track : project.getTracks()) {
            cmbEntity.getItems().add(track.getEntityName());
        }
        
        cmbEntity.setValue(selectedEntity);
    }
    
    private void onEntitySelected() {
        String entityName = cmbEntity.getValue();
        if (entityName == null || entityName.isBlank() || project == null) {
            anchorListView.getItems().clear();
            return;
        }
        
        refreshAnchorList(entityName);
    }
    
    private void refreshAnchorList(String entityName) {
        anchorListView.getItems().clear();
        
        Map<String, Anchor> anchors = project.getAnchorsForEntity(entityName);
        if (anchors != null) {
            anchorListView.getItems().addAll(anchors.keySet());
        }
    }
    
    private void onAnchorSelected(String anchorName) {
        if (anchorName == null || anchorName.isBlank()) {
            txtAnchorName.clear();
            spAnchorX.getValueFactory().setValue(0.5);
            spAnchorY.getValueFactory().setValue(0.5);
            cbRelative.setSelected(true);
            btnRemoveAnchor.setDisable(true);
            return;
        }
        
        String entityName = cmbEntity.getValue();
        if (entityName == null || project == null) return;
        
        Anchor anchor = project.getAnchor(entityName, anchorName);
        if (anchor != null) {
            txtAnchorName.setText(anchor.getName());
            spAnchorX.getValueFactory().setValue(anchor.getX());
            spAnchorY.getValueFactory().setValue(anchor.getY());
            cbRelative.setSelected(anchor.isRelative());
            btnRemoveAnchor.setDisable(false);
        }
    }
    
    private void addAnchor() {
        String entityName = cmbEntity.getValue();
        String anchorName = txtAnchorName.getText();
        
        if (entityName == null || entityName.isBlank()) return;
        if (anchorName == null || anchorName.isBlank()) return;
        
        double x = spAnchorX.getValue();
        double y = spAnchorY.getValue();
        boolean isRelative = cbRelative.isSelected();
        
        Anchor anchor = new Anchor(anchorName, x, y, isRelative);
        
        if (project != null) {
            project.setAnchor(entityName, anchor);
            refreshAnchorList(entityName);
            if (onAnchorChanged != null) {
                onAnchorChanged.run();
            }
        }
    }
    
    private void removeAnchor() {
        String entityName = cmbEntity.getValue();
        String anchorName = anchorListView.getSelectionModel().getSelectedItem();
        
        if (entityName == null || entityName.isBlank()) return;
        if (anchorName == null || anchorName.isBlank()) return;
        
        if (project != null) {
            project.removeAnchor(entityName, anchorName);
            refreshAnchorList(entityName);
            txtAnchorName.clear();
            btnRemoveAnchor.setDisable(true);
            if (onAnchorChanged != null) {
                onAnchorChanged.run();
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
}
