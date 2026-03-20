package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Asset picker panel for Puppeteer. Browses the project directory for image files
 * and allows the user to add them as Sprite2D entities to the current scene.
 */
public class AssetPickerPanel extends VBox {
    static final String IMPORT_RELATIVE_DIR = "assets/puppeteer/imported";

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
    private static final String STYLE_CELL_BASE =
        "-fx-background-color: transparent; -fx-background-radius: 6; -fx-border-radius: 6; " +
        "-fx-border-color: transparent; -fx-padding: 2 4;";
    private static final String STYLE_CELL_HOVER =
        "-fx-background-color: #262c35; -fx-background-radius: 6; -fx-border-radius: 6; " +
        "-fx-border-color: #2f3946; -fx-padding: 2 4;";
    private static final String STYLE_CELL_SELECTED =
        "-fx-background-color: linear-gradient(to right, #24384e, #1d2d40); "
            + "-fx-background-radius: 6; -fx-border-radius: 6; "
            + "-fx-border-color: #4da3ff; -fx-padding: 2 4;";

    private final ListView<AssetEntry> listView;
    private final TextField filterField;
    private final Label lblStatus;
    private final Label lblEmptyHint;
    private final Button btnImport;
    private final Button btnAdd;

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

        btnImport = new Button("Import...");
        btnImport.setStyle(STYLE_BTN);
        btnImport.setTooltip(new Tooltip("Import image files into " + IMPORT_RELATIVE_DIR));
        btnImport.setOnAction(e -> importFromChooser());

        HBox filterRow = new HBox(4, filterField, btnRefresh, btnImport);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        lblEmptyHint = new Label("No project root set.\nOpen a VNS file and launch\nPuppeteer to browse assets.");
        lblEmptyHint.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");
        lblEmptyHint.setWrapText(true);

        listView = new ListView<>();
        listView.setStyle("-fx-background-color: #1a1a1a; -fx-control-inner-background: #1a1a1a;");
        listView.setCellFactory(lv -> new AssetCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateActionState());
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                addSelectedToScene();
                event.consume();
            }
        });
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addSelectedToScene();
                event.consume();
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        btnAdd = new Button("+ Add to Scene");
        btnAdd.setStyle(STYLE_BTN_ACCENT);
        btnAdd.setMaxWidth(Double.MAX_VALUE);
        btnAdd.setTooltip(new Tooltip("Add selected image as a new entity. Double-click or press Enter also works."));
        btnAdd.setOnAction(e -> addSelectedToScene());

        lblStatus = new Label("");
        lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 10px;");

        installImportDropTarget();
        getChildren().addAll(header, filterRow, lblEmptyHint, listView, btnAdd, lblStatus);
        updateEmptyState();
        updateActionState();
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
            allAssets.clear();
            listView.getItems().clear();
            updateEmptyState();
            updateActionState();
        }
    }

    private void scanProject() {
        String previousSelection = selectedRelativePath();
        allAssets.clear();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            lblEmptyHint.setText("No project root set.\nOpen a VNS file and launch\nPuppeteer to browse assets.");
            updateEmptyState();
            updateActionState();
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
            lblEmptyHint.setText("No images found in project.\nDrop image files here or click Import...");
        }

        applyFilter();
        reselectByRelativePath(previousSelection);
        updateEmptyState();
        updateActionState();
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
        String previousSelection = selectedRelativePath();
        String query = filterField.getText().trim().toLowerCase();
        listView.getItems().clear();
        for (AssetEntry entry : allAssets) {
            if (query.isEmpty() || entry.relativePath.toLowerCase().contains(query)) {
                listView.getItems().add(entry);
            }
        }
        reselectByRelativePath(previousSelection);
        updateEmptyState();
        updateActionState();
    }

    private void updateEmptyState() {
        boolean empty = listView.getItems().isEmpty();
        lblEmptyHint.setVisible(empty);
        lblEmptyHint.setManaged(empty);
        listView.setVisible(!empty);
        listView.setManaged(!empty);
    }

    private void updateActionState() {
        boolean hasProject = projectRoot != null && projectRoot.isDirectory();
        btnImport.setDisable(!hasProject);
        btnAdd.setDisable(listView.getSelectionModel().getSelectedItem() == null);
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

    private void importFromChooser() {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            lblStatus.setText("Open a project first");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Images Into Puppeteer");
        chooser.getExtensionFilters().setAll(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
            new FileChooser.ExtensionFilter("All Files", "*.*"));
        File initialDir = initialImportDirectory();
        if (initialDir != null && initialDir.isDirectory()) {
            chooser.setInitialDirectory(initialDir);
        }
        List<File> selectedFiles = chooser.showOpenMultipleDialog(getScene() == null ? null : getScene().getWindow());
        importFiles(selectedFiles);
    }

    private File initialImportDirectory() {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return new File(System.getProperty("user.home", "."));
        }
        File importDir = resolveImportDirectory(projectRoot);
        if (importDir.isDirectory()) {
            return importDir;
        }
        return projectRoot;
    }

    private void installImportDropTarget() {
        setOnDragOver(event -> {
            if (projectRoot != null && projectRoot.isDirectory() && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        setOnDragEntered(event -> {
            if (projectRoot != null && projectRoot.isDirectory() && event.getDragboard().hasFiles()) {
                if (!getStyle().contains("#202833")) {
                    setStyle("-fx-background-color: #202833; -fx-border-color: #4da3ff; -fx-border-width: 1;");
                }
            }
            event.consume();
        });
        setOnDragExited(event -> {
            setStyle("-fx-background-color: #1a1a1a;");
            event.consume();
        });
        setOnDragDropped(event -> {
            boolean success = false;
            if (projectRoot != null && projectRoot.isDirectory()) {
                success = importFiles(event.getDragboard().getFiles()) > 0;
            }
            setStyle("-fx-background-color: #1a1a1a;");
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private int importFiles(List<File> files) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            lblStatus.setText("Open a project first");
            return 0;
        }
        if (files == null || files.isEmpty()) {
            return 0;
        }

        List<String> importedPaths = new ArrayList<>();
        int skipped = 0;
        File importDir = resolveImportDirectory(projectRoot);
        Path projectRootPath = projectRoot.toPath().toAbsolutePath().normalize();

        try {
            Files.createDirectories(importDir.toPath());
            for (File file : files) {
                if (file == null || !file.isFile()) {
                    skipped++;
                    continue;
                }
                String ext = getExtension(file.getName());
                if (!IMAGE_EXTENSIONS.contains(ext)) {
                    skipped++;
                    continue;
                }

                Path source = file.toPath().toAbsolutePath().normalize();
                Path target;
                if (source.startsWith(projectRootPath)) {
                    target = source;
                } else {
                    target = resolveUniqueImportTarget(importDir.toPath(), file.getName());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                importedPaths.add(projectRootPath.relativize(target).toString().replace('\\', '/'));
            }
        } catch (IOException ex) {
            lblStatus.setText("Import failed: " + ex.getMessage());
            return 0;
        }

        if (importedPaths.isEmpty()) {
            lblStatus.setText(skipped > 0 ? "No importable images selected" : "Nothing imported");
            return 0;
        }

        filterField.clear();
        scanProject();
        reselectByRelativePath(importedPaths.get(importedPaths.size() - 1));
        listView.scrollTo(Math.max(0, listView.getSelectionModel().getSelectedIndex()));

        if (importedPaths.size() == 1) {
            lblStatus.setText("Imported: " + importedPaths.get(0));
        } else {
            lblStatus.setText("Imported " + importedPaths.size() + " images into " + IMPORT_RELATIVE_DIR);
        }
        if (skipped > 0) {
            lblStatus.setText(lblStatus.getText() + " (" + skipped + " skipped)");
        }
        return importedPaths.size();
    }

    private String selectedRelativePath() {
        AssetEntry selected = listView.getSelectionModel().getSelectedItem();
        return selected != null ? selected.relativePath : null;
    }

    private void reselectByRelativePath(String relativePath) {
        if (relativePath != null) {
            for (AssetEntry entry : listView.getItems()) {
                if (relativePath.equals(entry.relativePath)) {
                    listView.getSelectionModel().select(entry);
                    return;
                }
            }
        }
        if (!listView.getItems().isEmpty() && listView.getSelectionModel().getSelectedItem() == null) {
            listView.getSelectionModel().select(0);
        }
    }

    static File resolveImportDirectory(File projectRoot) {
        return new File(projectRoot, IMPORT_RELATIVE_DIR);
    }

    static Path resolveUniqueImportTarget(Path importDir, String originalFileName) {
        String fileName = (originalFileName == null || originalFileName.isBlank()) ? "asset.png" : originalFileName.trim();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        Path candidate = importDir.resolve(fileName);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = importDir.resolve(base + "-" + suffix + ext);
            suffix++;
        }
        return candidate;
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
        private final ImageView thumb = new ImageView();
        private final Label nameLabel = new Label();
        private final Label pathLabel = new Label();
        private final VBox textBox = new VBox(1);
        private final HBox row = new HBox(8, thumb, textBox);

        AssetCell() {
            thumb.setFitWidth(THUMB_SIZE);
            thumb.setFitHeight(THUMB_SIZE);
            thumb.setPreserveRatio(true);
            thumb.setSmooth(true);

            nameLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            pathLabel.setStyle("-fx-font-size: 9px;");
            textBox.getChildren().addAll(nameLabel, pathLabel);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(3));

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            hoverProperty().addListener((obs, wasHover, isHover) -> refreshCellStyle());
        }

        @Override
        protected void updateItem(AssetEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle(STYLE_CELL_BASE);
                return;
            }

            try {
                Image img = new Image(item.file.toURI().toString(), THUMB_SIZE * 2, THUMB_SIZE * 2, true, true, true);
                thumb.setImage(img);
            } catch (Exception ignored) {
                thumb.setImage(null);
            }

            nameLabel.setText(item.baseName);
            pathLabel.setText(item.relativePath);

            setGraphic(row);
            setText(null);
            refreshCellStyle();
        }

        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            refreshCellStyle();
        }

        private void refreshCellStyle() {
            if (getItem() == null || isEmpty()) {
                setStyle(STYLE_CELL_BASE);
                return;
            }
            if (isSelected()) {
                setStyle(STYLE_CELL_SELECTED);
                nameLabel.setStyle("-fx-text-fill: #eef6ff; -fx-font-size: 11px; -fx-font-weight: bold;");
                pathLabel.setStyle("-fx-text-fill: #9fc6ef; -fx-font-size: 9px;");
            } else if (isHover()) {
                setStyle(STYLE_CELL_HOVER);
                nameLabel.setStyle("-fx-text-fill: #f2f2f2; -fx-font-size: 11px; -fx-font-weight: bold;");
                pathLabel.setStyle("-fx-text-fill: #8b93a2; -fx-font-size: 9px;");
            } else {
                setStyle(STYLE_CELL_BASE);
                nameLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");
                pathLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 9px;");
            }
        }
    }
}
