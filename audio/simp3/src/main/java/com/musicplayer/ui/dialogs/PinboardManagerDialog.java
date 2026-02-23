package com.musicplayer.ui.dialogs;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.musicplayer.ui.components.PinboardItem;
import com.musicplayer.ui.components.PinboardPanel;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class PinboardManagerDialog extends Stage {

    private final PinboardPanel pinboardPanel;
    private final LinkedHashMap<String, String> actionLabels; // id -> label
    private final Map<String, Runnable> actionRunnables;       // id -> action

    private final VBox rowsContainer = new VBox(8);

    public PinboardManagerDialog(Window owner,
                                 PinboardPanel panel,
                                 LinkedHashMap<String, String> actionLabels,
                                 Map<String, Runnable> actionRunnables) {
        this.pinboardPanel = panel;
        this.actionLabels = actionLabels;
        this.actionRunnables = actionRunnables;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Manage Pinboard");

        VBox root = new VBox(12);
        root.setPadding(new Insets(12));

        // Header with Add button
        HBox header = new HBox(8);
        Label title = new Label("Pins");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Button addBtn = new Button("Add Pin");
        addBtn.setOnAction(e -> addRow(null));
        header.getChildren().addAll(title, addBtn);

        // Existing rows
        for (PinboardItem item : pinboardPanel.getPinnedItems()) {
            addRow(item);
        }

        ScrollPane scroll = new ScrollPane(rowsContainer);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);

        // Buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button close = new Button("Close");
        close.setOnAction(e -> close());
        actions.getChildren().addAll(close);

        root.getChildren().addAll(header, new Separator(), scroll, new Separator(), actions);

        Scene scene = new Scene(root, 640, 520);
        setScene(scene);
        centerOnScreen();
    }

    private void addRow(PinboardItem existing) {
        GridPane row = new GridPane();
        row.setHgap(8);
        row.setVgap(4);
        row.setPadding(new Insets(4, 0, 4, 0));

        CheckBox visible = new CheckBox("Visible");
        TextField nameField = new TextField();
        nameField.setPromptText("Pin name");
        ComboBox<String> actionCombo = new ComboBox<>();
        actionCombo.getItems().addAll(actionLabels.values());
        actionCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(actionCombo, Priority.ALWAYS);

        Button chooseIcon = new Button("Choose Icon...");
        Button clearIcon = new Button("Clear Icon");
        Button removeRow = new Button("Remove");

        // Layout
        row.add(visible, 0, 0);
        row.add(new Label("Name:"), 1, 0);
        row.add(nameField, 2, 0);
        row.add(new Label("Action:"), 3, 0);
        row.add(actionCombo, 4, 0);
        HBox iconBox = new HBox(6, chooseIcon, clearIcon, removeRow);
        row.add(iconBox, 5, 0);

        // State initialization
        final PinboardItem target;
        if (existing == null) {
            // Create a new pin but don't add to panel until configured
            target = new PinboardItem("custom-" + System.currentTimeMillis(), "New Pin", PinboardItem.ItemType.PLAYLIST, null);
            visible.setSelected(true);
            nameField.setText(target.getName());
            // Default action: All Songs (first entry)
            String firstId = actionLabels.keySet().stream().findFirst().orElse(null);
            if (firstId != null) {
                actionCombo.getSelectionModel().select(actionLabels.get(firstId));
                target.setActionId(firstId);
                target.setOnClickAction(actionRunnables.get(firstId));
            }
            // Attach immediately if visible
            if (visible.isSelected()) {
                PinboardItem added = pinboardPanel.addPinnedItem(target.getItemId(), target.getName(), target.getType(), target.getOnClickAction());
                if (added != null) {
                    added.setActionId(target.getActionId());
                }
            }
        } else {
            target = existing;
            visible.setSelected(pinboardPanel.isPinned(target.getItemId()));
            nameField.setText(target.getName());
            // Map current action id to label
            String currentLabel = actionLabels.getOrDefault(target.getActionId(), null);
            if (currentLabel != null) actionCombo.getSelectionModel().select(currentLabel);
        }

        // Handlers
        visible.selectedProperty().addListener((o, ov, nv) -> {
            boolean present = pinboardPanel.isPinned(target.getItemId());
            if (nv && !present) {
                PinboardItem added = pinboardPanel.addPinnedItem(target.getItemId(), target.getName(), target.getType(), target.getOnClickAction());
                if (added != null) {
                    added.setActionId(target.getActionId());
                }
            } else if (!nv && present) {
                pinboardPanel.removePinnedItem(target.getItemId());
            }
        });

        nameField.textProperty().addListener((o, ov, nv) -> {
            target.setName(nv);
        });

        actionCombo.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv == null) return;
            String selectedId = actionLabels.entrySet().stream()
                    .filter(e -> e.getValue().equals(nv))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);
            if (selectedId != null) {
                target.setActionId(selectedId);
                target.setOnClickAction(actionRunnables.get(selectedId));
            }
        });

        chooseIcon.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose Custom Icon");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File f = fc.showOpenDialog(getOwner());
            if (f != null) {
                target.setCustomIconFile(f);
            }
        });

        clearIcon.setOnAction(e -> target.clearCustomIcon());

        removeRow.setOnAction(e -> {
            rowsContainer.getChildren().remove(row);
            pinboardPanel.removePinnedItem(target.getItemId());
            pinboardPanel.getPinnedItems().remove(target);
        });

        rowsContainer.getChildren().add(row);
    }

    // Utility to build default actions map labels if needed externally
    public static LinkedHashMap<String, String> buildActionLabelsFromIds(List<String> ids) {
        return ids.stream().collect(Collectors.toMap(
            id -> id,
            id -> Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('-', ' '),
            (a, b) -> a,
            LinkedHashMap::new
        ));
    }
}
