package com.jvn.editor.ui.actioneditor;

import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * UI panel for managing named anchor points on entities.
 * Anchors define custom rotation/attachment pivots (e.g. an elbow on an arm
 * sprite) that animators can rotate around independently of the entity's
 * default origin.
 */
public class AnchorEditor extends VBox {

    private static final String LABEL_STYLE    = "-fx-text-fill: #a0a0a0; -fx-font-size: 11px;";
    private static final String SECTION_STYLE  = "-fx-text-fill: #c0c0c0; -fx-font-size: 11px; -fx-font-weight: bold;";
    private static final String FIELD_STYLE    = "-fx-background-color: #2d2d2d; -fx-text-fill: #e0e0e0; "
                                                + "-fx-border-color: #444; -fx-border-radius: 3; -fx-background-radius: 3;";
    private static final String BTN_PRIMARY    = "-fx-background-color: #1a6ea8; -fx-text-fill: white; "
                                                + "-fx-background-radius: 3; -fx-font-size: 11px;";
    private static final String BTN_DANGER     = "-fx-background-color: #7a1e1e; -fx-text-fill: #e0e0e0; "
                                                + "-fx-background-radius: 3; -fx-font-size: 11px;";

    private AnimationProject project;
    private Runnable onAnchorChanged;

    private ComboBox<String> cmbEntity;
    private ListView<String> anchorListView;
    private Label lblNoAnchors;
    private Label lblFormTitle;
    private TextField txtAnchorName;
    private Spinner<Double> spAnchorX;
    private Spinner<Double> spAnchorY;
    private CheckBox cbRelative;
    private Button btnApply;
    private Button btnRemove;
    private Button btnNew;

    public AnchorEditor() {
        setSpacing(0);
        setFillWidth(true);
        buildUI();
    }

    private void buildUI() {
        Label header = new Label("Anchors");
        header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e0e0e0;");
        header.setPadding(new Insets(10, 10, 6, 10));

        // ── Entity selector ───────────────────────────────────────────────
        VBox entitySection = new VBox(4);
        entitySection.setPadding(new Insets(8, 10, 8, 10));

        Label lblEntityHdr = new Label("Entity");
        lblEntityHdr.setStyle(SECTION_STYLE);

        cmbEntity = new ComboBox<>();
        cmbEntity.setPromptText("Select entity…");
        cmbEntity.setMaxWidth(Double.MAX_VALUE);
        cmbEntity.setCellFactory(lv -> new EntityCell());
        cmbEntity.setButtonCell(new EntityCell());
        cmbEntity.setOnAction(e -> onEntitySelected());

        entitySection.getChildren().addAll(lblEntityHdr, cmbEntity);

        // ── Anchor list ───────────────────────────────────────────────────
        VBox listSection = new VBox(4);
        listSection.setPadding(new Insets(8, 10, 6, 10));

        Label lblListHdr = new Label("Defined Anchors");
        lblListHdr.setStyle(SECTION_STYLE);

        anchorListView = new ListView<>();
        anchorListView.setPrefHeight(110);
        anchorListView.setMinHeight(60);
        anchorListView.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #3a3a3a; -fx-border-width: 1;");
        anchorListView.setCellFactory(lv -> new AnchorCell());
        anchorListView.getSelectionModel().selectedItemProperty()
                      .addListener((obs, old, val) -> onAnchorSelected(val));

        lblNoAnchors = new Label("No anchors defined for this entity.");
        lblNoAnchors.setStyle("-fx-text-fill: #666; -fx-font-size: 10.5px; -fx-font-style: italic;");
        lblNoAnchors.setPadding(new Insets(2, 0, 0, 0));
        lblNoAnchors.setVisible(false);
        lblNoAnchors.setManaged(false);

        listSection.getChildren().addAll(lblListHdr, anchorListView, lblNoAnchors);

        // ── Anchor form ───────────────────────────────────────────────────
        VBox formSection = new VBox(6);
        formSection.setPadding(new Insets(8, 10, 10, 10));

        lblFormTitle = new Label("New Anchor");
        lblFormTitle.setStyle(SECTION_STYLE);

        Label lblName = new Label("Name");
        lblName.setStyle(LABEL_STYLE);

        txtAnchorName = new TextField();
        txtAnchorName.setPromptText("e.g. elbow, shoulder, wrist");
        txtAnchorName.setStyle(FIELD_STYLE);
        txtAnchorName.setMaxWidth(Double.MAX_VALUE);

        cbRelative = new CheckBox("Normalized (0–1 per axis)");
        cbRelative.setSelected(true);
        cbRelative.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 11px;");
        Tooltip relTip = new Tooltip(
            "When on:  (0.5, 0.5) = sprite center\n" +
            "          (0, 0)     = top-left corner\n" +
            "          (1, 1)     = bottom-right corner\n\n" +
            "When off: values are pixel offsets from\n" +
            "          the entity's world position.");
        relTip.setWrapText(true);
        Tooltip.install(cbRelative, relTip);
        cbRelative.setOnAction(e -> updateSpinnerRange());

        GridPane posGrid = new GridPane();
        posGrid.setHgap(8);
        posGrid.setVgap(5);
        ColumnConstraints colLabel = new ColumnConstraints(24);
        ColumnConstraints colSpinner = new ColumnConstraints();
        colSpinner.setHgrow(Priority.ALWAYS);
        colSpinner.setFillWidth(true);
        posGrid.getColumnConstraints().addAll(colLabel, colSpinner);

        Label lblX = new Label("X");
        lblX.setStyle(LABEL_STYLE);
        spAnchorX = new Spinner<>(0.0, 1.0, 0.5, 0.05);
        spAnchorX.setMaxWidth(Double.MAX_VALUE);
        spAnchorX.setEditable(true);

        Label lblY = new Label("Y");
        lblY.setStyle(LABEL_STYLE);
        spAnchorY = new Spinner<>(0.0, 1.0, 0.5, 0.05);
        spAnchorY.setMaxWidth(Double.MAX_VALUE);
        spAnchorY.setEditable(true);

        posGrid.add(lblX, 0, 0);
        posGrid.add(spAnchorX, 1, 0);
        posGrid.add(lblY, 0, 1);
        posGrid.add(spAnchorY, 1, 1);

        btnApply = new Button("Add Anchor");
        btnApply.setStyle(BTN_PRIMARY);
        btnApply.setMaxWidth(Double.MAX_VALUE);
        btnApply.setOnAction(e -> applyAnchor());
        HBox.setHgrow(btnApply, Priority.ALWAYS);

        btnRemove = new Button("Remove");
        btnRemove.setStyle(BTN_DANGER);
        btnRemove.setDisable(true);
        btnRemove.setOnAction(e -> removeAnchor());

        btnNew = new Button("New");
        btnNew.setStyle("-fx-background-color: #2d2d2d; -fx-text-fill: #c0c0c0; "
                      + "-fx-border-color: #555; -fx-border-radius: 3; -fx-background-radius: 3; -fx-font-size: 11px;");
        btnNew.setDisable(true);
        btnNew.setOnAction(e -> clearForm(true));

        HBox btnRow = new HBox(5, btnApply, btnRemove, btnNew);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        formSection.getChildren().addAll(
            lblFormTitle,
            lblName, txtAnchorName,
            cbRelative, posGrid,
            btnRow
        );

        getChildren().addAll(
            header, new Separator(),
            entitySection, new Separator(),
            listSection, new Separator(),
            formSection
        );
    }

    // ── Public API ────────────────────────────────────────────────────────

    public void setProject(AnimationProject project) {
        this.project = project;
        refreshEntityList();
    }

    public void setOnAnchorChanged(Runnable cb) {
        this.onAnchorChanged = cb;
    }

    /**
     * Called when the user selects an entity in the AnimationPreview so the
     * anchor editor can automatically switch to that entity's anchors.
     */
    public void setSelectedEntityName(String entityName) {
        if (entityName == null) return;
        if (cmbEntity.getItems().contains(entityName)
                && !entityName.equals(cmbEntity.getValue())) {
            cmbEntity.setValue(entityName);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private void refreshEntityList() {
        if (project == null) return;
        String prev = cmbEntity.getValue();
        cmbEntity.getItems().clear();
        for (EntityTrack t : project.getTracks()) {
            cmbEntity.getItems().add(t.getEntityName());
        }
        if (prev != null && cmbEntity.getItems().contains(prev)) {
            cmbEntity.setValue(prev);
        }
    }

    private void onEntitySelected() {
        String entityName = cmbEntity.getValue();
        anchorListView.getItems().clear();
        clearForm(false);
        if (entityName == null || entityName.isBlank() || project == null) {
            setListVisible(false);
            return;
        }
        setListVisible(true);
        refreshAnchorList(entityName);
    }

    private void refreshAnchorList(String entityName) {
        anchorListView.getItems().clear();
        Map<String, Anchor> anchors = project.getAnchorsForEntity(entityName);
        boolean hasAnchors = anchors != null && !anchors.isEmpty();
        if (hasAnchors) {
            anchorListView.getItems().addAll(anchors.keySet());
        }
        lblNoAnchors.setVisible(!hasAnchors);
        lblNoAnchors.setManaged(!hasAnchors);
        anchorListView.setVisible(hasAnchors);
        anchorListView.setManaged(hasAnchors);
    }

    private void setListVisible(boolean show) {
        lblNoAnchors.setVisible(!show);
        lblNoAnchors.setManaged(!show);
        anchorListView.setVisible(show);
        anchorListView.setManaged(show);
    }

    private void onAnchorSelected(String anchorName) {
        if (anchorName == null) {
            clearForm(false);
            btnRemove.setDisable(true);
            btnNew.setDisable(true);
            return;
        }
        String entityName = cmbEntity.getValue();
        if (entityName == null || project == null) return;

        Anchor anchor = project.getAnchor(entityName, anchorName);
        if (anchor == null) return;

        txtAnchorName.setText(anchor.getName());
        cbRelative.setSelected(anchor.isRelative());
        updateSpinnerRange();
        spAnchorX.getValueFactory().setValue(anchor.getX());
        spAnchorY.getValueFactory().setValue(anchor.getY());

        lblFormTitle.setText("Edit Anchor");
        btnApply.setText("Update Anchor");
        btnRemove.setDisable(false);
        btnNew.setDisable(false);
    }

    private void clearForm(boolean resetName) {
        if (resetName) txtAnchorName.clear();
        cbRelative.setSelected(true);
        updateSpinnerRange();
        spAnchorX.getValueFactory().setValue(0.5);
        spAnchorY.getValueFactory().setValue(0.5);
        lblFormTitle.setText("New Anchor");
        btnApply.setText("Add Anchor");
        btnRemove.setDisable(true);
        btnNew.setDisable(true);
        anchorListView.getSelectionModel().clearSelection();
    }

    private void updateSpinnerRange() {
        boolean rel  = cbRelative.isSelected();
        double  min  = rel ? 0.0 : -9999.0;
        double  max  = rel ? 1.0 : 9999.0;
        double  step = rel ? 0.05 : 1.0;
        double  curX = spAnchorX.getValue();
        double  curY = spAnchorY.getValue();
        spAnchorX.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
            min, max, clamp(curX, min, max), step));
        spAnchorY.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
            min, max, clamp(curY, min, max), step));
        spAnchorX.setEditable(true);
        spAnchorY.setEditable(true);
    }

    private void applyAnchor() {
        String entityName = cmbEntity.getValue();
        String name       = txtAnchorName.getText();
        if (entityName == null || entityName.isBlank() || project == null) return;
        if (name == null || name.isBlank()) return;

        commitSpinners();
        Anchor anchor = new Anchor(name, spAnchorX.getValue(), spAnchorY.getValue(), cbRelative.isSelected());
        project.setAnchor(entityName, anchor);
        refreshAnchorList(entityName);
        anchorListView.getSelectionModel().select(name);
        if (onAnchorChanged != null) onAnchorChanged.run();
    }

    private void removeAnchor() {
        String entityName  = cmbEntity.getValue();
        String anchorName  = anchorListView.getSelectionModel().getSelectedItem();
        if (entityName == null || anchorName == null || project == null) return;

        project.removeAnchor(entityName, anchorName);
        refreshAnchorList(entityName);
        clearForm(true);
        if (onAnchorChanged != null) onAnchorChanged.run();
    }

    private void commitSpinners() {
        try { spAnchorX.commitValue(); } catch (Exception ignored) {}
        try { spAnchorY.commitValue(); } catch (Exception ignored) {}
    }

    private static double clamp(double v, double min, double max) {
        return Math.min(max, Math.max(min, v));
    }

    // ── List cells ────────────────────────────────────────────────────────

    private static final class EntityCell extends ListCell<String> {
        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item);
            setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
        }
    }

    private final class AnchorCell extends ListCell<String> {
        @Override protected void updateItem(String name, boolean empty) {
            super.updateItem(name, empty);
            if (empty || name == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            String entityName = cmbEntity.getValue();
            Anchor anchor = (entityName != null && project != null)
                ? project.getAnchor(entityName, name) : null;

            if (anchor != null) {
                String coords = anchor.isRelative()
                    ? String.format("(%.2f, %.2f)  rel", anchor.getX(), anchor.getY())
                    : String.format("(%+.1f, %+.1f) px",  anchor.getX(), anchor.getY());

                Rectangle dot = new Rectangle(7, 7, Color.web("#a855f7"));
                dot.setArcWidth(2); dot.setArcHeight(2);

                Label nameLabel = new Label(name);
                nameLabel.setStyle("-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");

                Label coordLabel = new Label(coords);
                coordLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");
                HBox.setHgrow(coordLabel, Priority.ALWAYS);
                coordLabel.setAlignment(Pos.CENTER_RIGHT);

                HBox row = new HBox(6, dot, nameLabel, coordLabel);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            } else {
                setText(name);
                setGraphic(null);
            }
            setStyle("-fx-background-color: transparent;");
        }
    }
}
