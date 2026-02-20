package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Asset picker panel for Puppeteer. Browses the project directory for image files
 * and allows the user to add them as Sprite2D entities to the current scene.
 */
public class AssetPickerPanel extends VBox {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        "png", "jpg", "jpeg", "gif", "bmp", "webp"
    );

    private static final String STYLE_HEADER =
        "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e6e6e6; -fx-padding: 6 8;";
    private static final String STYLE_FILTER =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-prompt-text-fill: #555; " +
        "-fx-border-color: #3a3a3a; -fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 4 6;";
    private static final String STYLE_BTN =
        "-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 4; " +
        "-fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String STYLE_BTN_ACCENT =
        "-fx-background-color: #4da3ff; -fx-text-fill: #fff; -fx-background-radius: 4; " +
        "-fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand; -fx-font-weight: bold;";

    private final ListView<AssetEntry> listView;
    private final TextField filterField;
    private final Label lblStatus;
    private final Label lblEmptyHint;

    private File projectRoot;
    private final List<AssetEntry> allAssets = new ArrayList<>();

    // callback: (relativePath, suggestedName) -> add entity to scene
    private BiConsumer<String, String> onAddToScene;

    public AssetPickerPanel() {
        setSpacing(4);
        setPadding(new Insets(4));
        setStyle("-fx-background-color: #1a1a1a;");

        Label header = new Label("Assets");
        header.setStyle(STYLE_HEADER);

        filterField = new TextField();
        filterField.setPromptText("Filter images...");
        filterField.setStyle(STYLE_FILTER);
        filterField.textProperty().addListener((obs, old, val) -> applyFilter());

        Button btnRefresh = new Button("Refresh");
        btnRefresh.setStyle(STYLE_BTN);
        btnRefresh.setTooltip(new Tooltip("Rescan project for images"));
        btnRefresh.setOnAction(e -> scanProject());

        HBox filterRow = new HBox(4, filterField, btnRefresh);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        lblEmptyHint = new Label("No project root set.\nOpen a VNS file and launch\nPuppeteer to browse assets.");
        lblEmptyHint.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");
        lblEmptyHint.setWrapText(true);

        listView = new ListView<>();
        listView.setStyle("-fx-background-color: #1a1a1a; -fx-control-inner-background: #1a1a1a;");
        listView.setCellFactory(lv -> new AssetCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        Button btnAdd = new Button("+ Add to Scene");
        btnAdd.setStyle(STYLE_BTN_ACCENT);
        btnAdd.setMaxWidth(Double.MAX_VALUE);
        btnAdd.setTooltip(new Tooltip("Add selected image as a new entity"));
        btnAdd.setOnAction(e -> addSelectedToScene());

        lblStatus = new Label("");
        lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 10px;");

        getChildren().addAll(header, filterRow, lblEmptyHint, listView, btnAdd, lblStatus);
        updateEmptyState();
    }

    public void setOnAddToScene(BiConsumer<String, String> callback) {
        this.onAddToScene = callback;
    }

    public void setProjectRoot(File root) {
        this.projectRoot = root;
        if (root != null && root.isDirectory()) {
            lblEmptyHint.setText("Scanning...");
            scanProject();
        } else {
            updateEmptyState();
        }
    }

    private void scanProject() {
        allAssets.clear();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            lblEmptyHint.setText("No project root set.\nOpen a VNS file and launch\nPuppeteer to browse assets.");
            updateEmptyState();
            return;
        }

        List<File> imageFiles = new ArrayList<>();
        collectImages(projectRoot, imageFiles, 0);

        for (File f : imageFiles) {
            String relativePath = projectRoot.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String baseName = dot > 0 ? name.substring(0, dot) : name;
            allAssets.add(new AssetEntry(relativePath, baseName, f));
        }

        allAssets.sort((a, b) -> a.relativePath.compareToIgnoreCase(b.relativePath));
        lblStatus.setText(allAssets.size() + " images found");

        if (allAssets.isEmpty()) {
            lblEmptyHint.setText("No images found in project.\nSupported: png, jpg, gif, bmp, webp");
        }

        applyFilter();
        updateEmptyState();
    }

    private void collectImages(File dir, List<File> out, int depth) {
        if (depth > 10) return; // prevent deep recursion
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (name.startsWith(".") || name.equals("build") || name.equals("bin") || name.equals("node_modules")) {
                    continue;
                }
                collectImages(f, out, depth + 1);
            } else {
                String ext = getExtension(f.getName());
                if (IMAGE_EXTENSIONS.contains(ext)) {
                    out.add(f);
                }
            }
        }
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private void applyFilter() {
        String query = filterField.getText().trim().toLowerCase();
        listView.getItems().clear();
        for (AssetEntry entry : allAssets) {
            if (query.isEmpty() || entry.relativePath.toLowerCase().contains(query)) {
                listView.getItems().add(entry);
            }
        }
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = listView.getItems().isEmpty();
        lblEmptyHint.setVisible(empty);
        lblEmptyHint.setManaged(empty);
        listView.setVisible(!empty);
        listView.setManaged(!empty);
    }

    private void addSelectedToScene() {
        AssetEntry selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            lblStatus.setText("Select an image first");
            return;
        }
        if (onAddToScene != null) {
            onAddToScene.accept(selected.relativePath, selected.baseName);
            lblStatus.setText("Added: " + selected.baseName);
        }
    }

    // --- Data model ---
    static class AssetEntry {
        final String relativePath;
        final String baseName;
        final File file;

        AssetEntry(String relativePath, String baseName, File file) {
            this.relativePath = relativePath;
            this.baseName = baseName;
            this.file = file;
        }

        @Override
        public String toString() { return relativePath; }
    }

    // --- Custom cell with thumbnail ---
    private static class AssetCell extends ListCell<AssetEntry> {
        private static final double THUMB_SIZE = 32;

        @Override
        protected void updateItem(AssetEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            ImageView thumb = new ImageView();
            thumb.setFitWidth(THUMB_SIZE);
            thumb.setFitHeight(THUMB_SIZE);
            thumb.setPreserveRatio(true);
            thumb.setSmooth(true);

            try {
                Image img = new Image(item.file.toURI().toString(), THUMB_SIZE * 2, THUMB_SIZE * 2, true, true, true);
                thumb.setImage(img);
            } catch (Exception ignored) {
                // no thumbnail available
            }

            VBox textBox = new VBox(1);
            Label nameLabel = new Label(item.baseName);
            nameLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label pathLabel = new Label(item.relativePath);
            pathLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 9px;");
            textBox.getChildren().addAll(nameLabel, pathLabel);

            HBox row = new HBox(6, thumb, textBox);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(2));

            setGraphic(row);
            setText(null);
            setStyle("-fx-background-color: transparent; -fx-padding: 2;");

            setOnMouseEntered(e -> setStyle("-fx-background-color: #2a2a2a; -fx-padding: 2;"));
            setOnMouseExited(e -> {
                if (!isSelected()) setStyle("-fx-background-color: transparent; -fx-padding: 2;");
            });
        }
    }
}
