package com.jvn.editor.ui.actioneditor;

import com.jvn.core.vn.LayeredCharacterResolver;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.SnapshotParameters;
import javafx.stage.FileChooser;

/**
 * Asset picker panel for Puppeteer. Browses the project directory for image files
 * and allows the user to add them as Sprite2D entities to the current scene.
 */
public class AssetPickerPanel extends VBox {
    static final String IMPORT_RELATIVE_DIR = "assets/puppeteer/imported";
    static final String CHARPRESET_IMPORT_RELATIVE_DIR = "assets/characters";
    private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARIMG_PATTERN = Pattern.compile("^\\s*@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARGROUP_PATTERN = Pattern.compile("^\\s*@chargroup\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^\\s*@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        "png", "jpg", "jpeg", "gif", "bmp", "webp"
    );

    private static final String STYLE_HEADER =
        "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e6e6e6; -fx-padding: 6 8;";
    private static final String STYLE_FILTER =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-prompt-text-fill: #555; "
            + "-fx-border-color: #3a3a3a; -fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 4 6;";
    private static final String STYLE_BTN =
        "-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 4; "
            + "-fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String STYLE_BTN_ACCENT =
        "-fx-background-color: #3a3a3a; -fx-text-fill: #f2f2f2; -fx-background-radius: 4; "
            + "-fx-border-color: #5a5a5a; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand; -fx-font-weight: bold;";
    private static final String STYLE_CELL_BASE =
        "-fx-background-color: transparent; -fx-background-radius: 6; -fx-border-radius: 6; "
            + "-fx-border-color: transparent; -fx-padding: 2 4;";
    private static final String STYLE_CELL_HOVER =
        "-fx-background-color: #262626; -fx-background-radius: 6; -fx-border-radius: 6; "
            + "-fx-border-color: #3e3e3e; -fx-padding: 2 4;";
    private static final String STYLE_CELL_SELECTED =
        "-fx-background-color: linear-gradient(to right, #2d2d2d, #252525); "
            + "-fx-background-radius: 6; -fx-border-radius: 6; "
            + "-fx-border-color: #595959; -fx-padding: 2 4;";
    private static final String STYLE_IMPORT_PREVIEW_FRAME =
        "-fx-background-color: #0f0f0f; -fx-background-radius: 8; -fx-border-radius: 8; "
            + "-fx-border-color: #383838; -fx-padding: 10;";
    private static final String STYLE_DIALOG_HELP =
        "-fx-text-fill: #b0b0b0; -fx-font-size: 11px;";
    private static final String STYLE_DIALOG_META =
        "-fx-text-fill: #808080; -fx-font-size: 10px;";
    private static final String STYLE_DIALOG_STATUS =
        "-fx-text-fill: #f0b673; -fx-font-size: 10px;";
    private static final String STYLE_SNIPPET_AREA =
        "-fx-control-inner-background: #121212; -fx-font-family: 'Menlo'; -fx-highlight-fill: #3d3d3d; "
            + "-fx-highlight-text-fill: white; -fx-text-fill: #d7d7d7; -fx-border-color: #383838; "
            + "-fx-border-radius: 6; -fx-background-radius: 6;";
    private static final String STYLE_PREVIEW_CARD =
        "-fx-background-color: #171717; -fx-background-radius: 10; -fx-border-radius: 10; "
            + "-fx-border-color: #2f2f2f; -fx-padding: 12;";
    private static final String STYLE_FILTER_COMBO =
        "-fx-background-color: #1b1b1b; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; "
            + "-fx-background-radius: 4; -fx-border-radius: 4; -fx-font-size: 11px;";

    private enum AssetScopeFilter {
        ALL("All Folders"),
        CHARACTERS("Characters"),
        BACKGROUNDS("Backgrounds"),
        IMPORTED("Imported"),
        OTHER("Other");

        private final String label;

        AssetScopeFilter(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    private enum AssetReferenceFilter {
        ALL("All Usage"),
        DECLARED("Any VNS Ref"),
        CHARIMG("@charimg"),
        CHARLAYER("@charlayer"),
        CHARPRESET("@charpreset"),
        BACKGROUND("@background"),
        UNREFERENCED("Unreferenced");

        private final String label;

        AssetReferenceFilter(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    private final ListView<AssetEntry> listView;
    private final TextField filterField;
    private final ComboBox<AssetScopeFilter> scopeFilterBox;
    private final ComboBox<AssetReferenceFilter> referenceFilterBox;
    private final Label lblStatus;
    private final Label lblEmptyHint;
    private final Label lblCount;
    private final Button btnImport;
    private final Button btnImportPreset;
    private final Button btnMakeBackground;
    private final Button btnMakeCharacter;
    private final Button btnMakeProp;
    private final ActionEditorDialogOverlay importOverlay;
    private final HBox placementActionRow;
    private final ImageView previewImageView;
    private final Label previewTitleLabel;
    private final Label previewPathLabel;
    private final Label previewMetaLabel;
    private final Label previewTagsLabel;
    private final Label previewUsageLabel;

    private File projectRoot;
    private File scriptTargetFile;
    private final List<AssetEntry> allAssets = new ArrayList<>();
    private boolean importEnabled = true;
    private boolean placementActionsVisible = true;

    @FunctionalInterface
    interface AssetPlacementHandler {
        void accept(AssetEntry entry, PuppeteerAssetPlacementRole role);
    }

    private AssetPlacementHandler onAddToScene;

    public AssetPickerPanel() {
        setSpacing(4);
        setMinWidth(0);
        setPadding(new Insets(4));
        setStyle("-fx-background-color: #1a1a1a;");

        VBox content = new VBox(4);
        content.setMinWidth(0);
        content.setFillWidth(true);

        Label header = new Label("Assets");
        header.setStyle(STYLE_HEADER);
        lblCount = new Label("0 assets");
        lblCount.setStyle("-fx-text-fill: #7f7f7f; -fx-font-size: 10px; -fx-padding: 6 8;");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(header, headerSpacer, lblCount);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        filterField = new TextField();
        filterField.setPromptText("Search path, character, expression, layer, preset...");
        filterField.setStyle(STYLE_FILTER);
        filterField.textProperty().addListener((obs, old, val) -> applyFilter());

        scopeFilterBox = new ComboBox<>();
        scopeFilterBox.getItems().setAll(AssetScopeFilter.values());
        scopeFilterBox.setValue(AssetScopeFilter.ALL);
        scopeFilterBox.setStyle(STYLE_FILTER_COMBO);
        scopeFilterBox.valueProperty().addListener((obs, old, val) -> applyFilter());

        referenceFilterBox = new ComboBox<>();
        referenceFilterBox.getItems().setAll(AssetReferenceFilter.values());
        referenceFilterBox.setValue(AssetReferenceFilter.ALL);
        referenceFilterBox.setStyle(STYLE_FILTER_COMBO);
        referenceFilterBox.valueProperty().addListener((obs, old, val) -> applyFilter());

        Button btnRefresh = new Button("Refresh");
        btnRefresh.setStyle(STYLE_BTN);
        btnRefresh.setTooltip(new Tooltip("Rescan project for images"));
        btnRefresh.setOnAction(e -> scanProject());

        btnImport = new Button("Import...");
        btnImport.setStyle(STYLE_BTN);
        btnImport.setTooltip(new Tooltip("Review and import image files into " + IMPORT_RELATIVE_DIR));
        btnImport.setOnAction(e -> importFromChooser());

        btnImportPreset = new Button("Preset...");
        btnImportPreset.setStyle(STYLE_BTN);
        btnImportPreset.setTooltip(new Tooltip("Import layered character images and generate @charpreset declarations."));
        btnImportPreset.setOnAction(e -> importCharpresetFromChooser());

        HBox filterRow = new HBox(4, filterField, scopeFilterBox, referenceFilterBox, btnRefresh, btnImport, btnImportPreset);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        lblEmptyHint = new Label("No project root set.\nOpen a VNS file and launch\nPuppeteer to browse assets.");
        lblEmptyHint.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");
        lblEmptyHint.setWrapText(true);

        listView = new ListView<>();
        listView.setMinWidth(0);
        listView.setStyle("-fx-background-color: #1a1a1a; -fx-control-inner-background: #1a1a1a;");
        listView.setCellFactory(lv -> new AssetCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            updateActionState();
            updatePreviewPane(newValue);
        });
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                AssetEntry selected = listView.getSelectionModel().getSelectedItem();
                addSelectedToScene(selected != null && selected.isPresetEntry()
                    ? PuppeteerAssetPlacementRole.CHARACTER
                    : PuppeteerAssetPlacementRole.PROP);
                event.consume();
            }
        });
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                AssetEntry selected = listView.getSelectionModel().getSelectedItem();
                addSelectedToScene(selected != null && selected.isPresetEntry()
                    ? PuppeteerAssetPlacementRole.CHARACTER
                    : PuppeteerAssetPlacementRole.PROP);
                event.consume();
            }
        });
        listView.setOnDragDetected(event -> startSelectedAssetDrag());
        VBox.setVgrow(listView, Priority.ALWAYS);

        btnMakeBackground = new Button("Make Background");
        btnMakeBackground.setStyle(STYLE_BTN);
        btnMakeBackground.setMaxWidth(Double.MAX_VALUE);
        btnMakeBackground.setTooltip(new Tooltip("Add the selected image as a centered, viewport-covering background."));
        btnMakeBackground.setOnAction(e -> addSelectedToScene(PuppeteerAssetPlacementRole.BACKGROUND));

        btnMakeCharacter = new Button("Make Character");
        btnMakeCharacter.setStyle(STYLE_BTN);
        btnMakeCharacter.setMaxWidth(Double.MAX_VALUE);
        btnMakeCharacter.setTooltip(new Tooltip("Add the selected image as a bottom-centered character."));
        btnMakeCharacter.setOnAction(e -> addSelectedToScene(PuppeteerAssetPlacementRole.CHARACTER));

        btnMakeProp = new Button("Make Prop");
        btnMakeProp.setStyle(STYLE_BTN_ACCENT);
        btnMakeProp.setMaxWidth(Double.MAX_VALUE);
        btnMakeProp.setTooltip(new Tooltip("Add the selected image as a centered prop. Double-click, Enter, or drag to the preview also uses this."));
        btnMakeProp.setOnAction(e -> addSelectedToScene(PuppeteerAssetPlacementRole.PROP));

        placementActionRow = new HBox(6, btnMakeBackground, btnMakeCharacter, btnMakeProp);
        placementActionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(btnMakeBackground, Priority.ALWAYS);
        HBox.setHgrow(btnMakeCharacter, Priority.ALWAYS);
        HBox.setHgrow(btnMakeProp, Priority.ALWAYS);

        lblStatus = new Label("");
        lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 10px;");

        importOverlay = new ActionEditorDialogOverlay();

        previewImageView = createDialogPreviewImageView(360, 360);
        previewTitleLabel = new Label("No asset selected");
        previewTitleLabel.setWrapText(true);
        previewTitleLabel.setStyle("-fx-text-fill: #f0f0f0; -fx-font-size: 14px; -fx-font-weight: bold;");
        previewPathLabel = new Label();
        previewPathLabel.setWrapText(true);
        previewPathLabel.setStyle("-fx-text-fill: #9a9a9a; -fx-font-size: 11px;");
        previewMetaLabel = new Label();
        previewMetaLabel.setWrapText(true);
        previewMetaLabel.setStyle(STYLE_DIALOG_META);
        previewTagsLabel = new Label();
        previewTagsLabel.setWrapText(true);
        previewTagsLabel.setStyle(STYLE_DIALOG_HELP);
        previewUsageLabel = new Label("Search by character, expression, layer, preset, or path.");
        previewUsageLabel.setWrapText(true);
        previewUsageLabel.setStyle(STYLE_DIALOG_HELP);

        Label usageHeader = new Label("Usage");
        usageHeader.setStyle("-fx-text-fill: #d9d9d9; -fx-font-size: 11px; -fx-font-weight: bold;");
        VBox previewBox = new VBox(
            10,
            wrapPreviewFrame(previewImageView),
            previewTitleLabel,
            previewPathLabel,
            previewMetaLabel,
            previewTagsLabel,
            usageHeader,
            previewUsageLabel
        );
        previewBox.setStyle(STYLE_PREVIEW_CARD);
        previewBox.setMinWidth(280);

        SplitPane browserSplit = new SplitPane(listView, previewBox);
        browserSplit.setMinWidth(0);
        browserSplit.setDividerPositions(0.64);
        VBox.setVgrow(browserSplit, Priority.ALWAYS);

        installImportDropTarget();
        content.getChildren().addAll(headerRow, filterRow, lblEmptyHint, browserSplit, placementActionRow, lblStatus);

        StackPane contentStack = new StackPane(content, importOverlay);
        contentStack.setMinWidth(0);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        getChildren().add(contentStack);
        updateEmptyState();
        updateActionState();
    }

    public void setOnAddToScene(AssetPlacementHandler callback) {
        this.onAddToScene = callback;
        updateActionState();
    }

    public void setImportEnabled(boolean enabled) {
        this.importEnabled = enabled;
        btnImport.setManaged(enabled);
        btnImport.setVisible(enabled);
        btnImportPreset.setManaged(enabled);
        btnImportPreset.setVisible(enabled);
        updateActionState();
    }

    public void setPlacementActionsVisible(boolean visible) {
        this.placementActionsVisible = visible;
        placementActionRow.setManaged(visible);
        placementActionRow.setVisible(visible);
        updateActionState();
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

    public void setScriptTargetFile(File file) {
        this.scriptTargetFile = isVnsScriptFile(file) ? file : null;
    }

    private void scanProject() {
        String previousSelection = selectedRelativePath();
        allAssets.clear();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            lblEmptyHint.setText("No project root set.\nOpen a VNS file and launch\nPuppeteer to browse assets.");
            lblCount.setText("0 assets");
            updatePreviewPane(null);
            updateEmptyState();
            updateActionState();
            return;
        }

        List<File> imageFiles = new ArrayList<>();
        collectImages(projectRoot, imageFiles, 0);
        Map<String, AssetEntry> byRelativePath = new LinkedHashMap<>();

        for (File f : imageFiles) {
            String relativePath = projectRoot.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String baseName = dot > 0 ? name.substring(0, dot) : name;
            AssetEntry entry = new AssetEntry(relativePath, baseName, f);
            allAssets.add(entry);
            byRelativePath.put(relativePath, entry);
        }

        allAssets.addAll(enrichAssetMetadata(byRelativePath));
        for (AssetEntry entry : allAssets) {
            entry.rebuildSearchIndex();
        }

        allAssets.sort((a, b) -> a.relativePath.compareToIgnoreCase(b.relativePath));
        lblStatus.setText(allAssets.size() + " images found");
        lblCount.setText(allAssets.size() + " assets");

        if (allAssets.isEmpty()) {
            lblEmptyHint.setText("No images found in project.\nDrop image files here or click Import...");
        }

        applyFilter();
        reselectByRelativePath(previousSelection);
        updateEmptyState();
        updateActionState();
    }

    private void collectImages(File dir, List<File> out, int depth) {
        if (depth > 10) return;
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

    private void collectScripts(File dir, List<File> out, int depth) {
        if (depth > 10) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (name.startsWith(".") || name.equals("build") || name.equals("bin") || name.equals("node_modules")) {
                    continue;
                }
                collectScripts(f, out, depth + 1);
            } else if ("vns".equals(getExtension(f.getName()))) {
                out.add(f);
            }
        }
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void applyFilter() {
        String previousSelection = selectedRelativePath();
        String query = filterField.getText().trim().toLowerCase(Locale.ROOT);
        AssetScopeFilter scope = scopeFilterBox.getValue() != null ? scopeFilterBox.getValue() : AssetScopeFilter.ALL;
        AssetReferenceFilter reference = referenceFilterBox.getValue() != null ? referenceFilterBox.getValue() : AssetReferenceFilter.ALL;
        listView.getItems().clear();
        for (AssetEntry entry : allAssets) {
            if (entry.matchesScope(scope) && entry.matchesReference(reference) && entry.matchesQuery(query)) {
                listView.getItems().add(entry);
            }
        }
        reselectByRelativePath(previousSelection);
        lblCount.setText(listView.getItems().size() + " shown");
        updateEmptyState();
        updateActionState();
    }

    private void updateEmptyState() {
        boolean empty = listView.getItems().isEmpty();
        lblEmptyHint.setVisible(empty);
        lblEmptyHint.setManaged(empty);
        listView.setVisible(!empty);
        listView.setManaged(!empty);
        if (empty) {
            updatePreviewPane(null);
        }
    }

    private void updateActionState() {
        boolean hasProject = projectRoot != null && projectRoot.isDirectory();
        btnImport.setDisable(!importEnabled || !hasProject);
        btnImportPreset.setDisable(!importEnabled || !hasProject);
        AssetEntry selected = listView.getSelectionModel().getSelectedItem();
        boolean allowPlacement = placementActionsVisible && onAddToScene != null && selected != null && selected.isSceneInstantiable();
        btnMakeBackground.setDisable(!allowPlacement || !selected.supportsPlacementRole(PuppeteerAssetPlacementRole.BACKGROUND));
        btnMakeCharacter.setDisable(!allowPlacement || !selected.supportsPlacementRole(PuppeteerAssetPlacementRole.CHARACTER));
        btnMakeProp.setDisable(!allowPlacement || !selected.supportsPlacementRole(PuppeteerAssetPlacementRole.PROP));
    }

    private void addSelectedToScene(PuppeteerAssetPlacementRole role) {
        AssetEntry selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            lblStatus.setText("Select an image first");
            return;
        }
        PuppeteerAssetPlacementRole resolvedRole = role != null ? role : PuppeteerAssetPlacementRole.PROP;
        if (!selected.isSceneInstantiable() || !selected.supportsPlacementRole(resolvedRole)) {
            lblStatus.setText(selected.isPresetEntry()
                ? "This preset can be placed as a layered character or prop, not as a background."
                : "This selection cannot be added to the scene with that role.");
            return;
        }
        if (onAddToScene != null) {
            onAddToScene.accept(selected, resolvedRole);
            lblStatus.setText("Added " + resolvedRole.displayName().toLowerCase(Locale.ROOT) + ": " + selected.baseName);
        }
    }

    private void startSelectedAssetDrag() {
        AssetEntry selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isPlaceable()) return;
        String encoded = PuppeteerAssetTransfer.encode(selected.relativePath, selected.baseName);
        if (encoded.isBlank()) return;
        var dragboard = listView.startDragAndDrop(TransferMode.COPY);
        ClipboardContent content = new ClipboardContent();
        content.putString(encoded);
        dragboard.setContent(content);
        lblStatus.setText("Drop into the preview to add '" + selected.baseName + "' as a prop.");
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
        showImageImportPreview(chooser.showOpenMultipleDialog(getScene() == null ? null : getScene().getWindow()));
    }

    private void importCharpresetFromChooser() {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            lblStatus.setText("Open a project first");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Character Preset Layers");
        chooser.getExtensionFilters().setAll(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
            new FileChooser.ExtensionFilter("All Files", "*.*"));
        File initialDir = initialCharpresetDirectory();
        if (initialDir != null && initialDir.isDirectory()) {
            chooser.setInitialDirectory(initialDir);
        }
        showCharpresetImportPreview(chooser.showOpenMultipleDialog(getScene() == null ? null : getScene().getWindow()));
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

    private File initialCharpresetDirectory() {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return new File(System.getProperty("user.home", "."));
        }
        File charDir = resolveCharpresetImportDirectory(projectRoot, "character", null);
        if (charDir.isDirectory()) {
            return charDir;
        }
        File charactersRoot = new File(projectRoot, CHARPRESET_IMPORT_RELATIVE_DIR);
        if (charactersRoot.isDirectory()) {
            return charactersRoot;
        }
        return projectRoot;
    }

    private void installImportDropTarget() {
        setOnDragOver(event -> {
            if (importEnabled && projectRoot != null && projectRoot.isDirectory() && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        setOnDragEntered(event -> {
            if (importEnabled && projectRoot != null && projectRoot.isDirectory() && event.getDragboard().hasFiles()) {
                if (!getStyle().contains("#232323")) {
                    setStyle("-fx-background-color: #232323; -fx-border-color: #5a5a5a; -fx-border-width: 1;");
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
            if (importEnabled && projectRoot != null && projectRoot.isDirectory()) {
                List<File> files = event.getDragboard().getFiles();
                showImageImportPreview(files);
                success = hasImportableImages(files);
            }
            setStyle("-fx-background-color: #1a1a1a;");
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void showImageImportPreview(List<File> files) {
        List<ImportPreviewItem> items = buildImportPreviewItems(files);
        if (items.isEmpty()) {
            lblStatus.setText("No importable images selected");
            return;
        }

        Label intro = new Label("Review the selected image files before importing them into the Puppeteer asset library.");
        intro.setWrapText(true);
        intro.setStyle(STYLE_DIALOG_HELP);

        ListView<ImportPreviewItem> previewList = buildImportPreviewList(items);
        previewList.setPrefHeight(Math.min(280, 56 + items.size() * 52));
        previewList.setMinWidth(240);
        HBox.setHgrow(previewList, Priority.ALWAYS);

        ImageView previewImage = createDialogPreviewImageView(240, 220);
        Label previewTitle = new Label();
        previewTitle.setWrapText(true);
        previewTitle.setStyle("-fx-text-fill: #f2f4f7; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label previewMeta = new Label();
        previewMeta.setWrapText(true);
        previewMeta.setStyle(STYLE_DIALOG_META);
        Label targetLabel = new Label();
        targetLabel.setWrapText(true);
        targetLabel.setStyle(STYLE_DIALOG_HELP);

        VBox detailBox = new VBox(8, wrapPreviewFrame(previewImage), previewTitle, previewMeta, targetLabel);
        detailBox.setMinWidth(240);
        HBox.setHgrow(detailBox, Priority.ALWAYS);

        previewList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) ->
            updateImageImportPreview(newValue, previewImage, previewTitle, previewMeta, targetLabel)
        );
        previewList.getSelectionModel().select(0);

        Label statusLabel = new Label(items.size() == 1
            ? "1 image ready to import."
            : items.size() + " images ready to import.");
        statusLabel.setWrapText(true);
        statusLabel.setStyle(STYLE_DIALOG_STATUS);

        HBox body = new HBox(12, previewList, detailBox);
        body.setAlignment(Pos.TOP_LEFT);

        VBox content = new VBox(10, intro, body, statusLabel);
        content.setFillWidth(true);
        content.setMaxWidth(Double.MAX_VALUE);

        importOverlay.showDialog(
            "Import Images",
            "Destination: " + IMPORT_RELATIVE_DIR,
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", importOverlay::hideOverlay),
            ActionEditorDialogOverlay.ActionSpec
                .stayOpen("Import", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                    int imported = importFiles(files);
                    if (imported > 0) {
                        importOverlay.hideOverlay();
                    } else {
                        statusLabel.setText(lblStatus.getText());
                    }
                })
                .defaultFocus(true)
        );
    }

    private void showCharpresetImportPreview(List<File> files) {
        List<ImportPreviewItem> items = buildImportPreviewItems(files);
        if (items.isEmpty()) {
            lblStatus.setText("Select one or more image layers first");
            return;
        }

        String suggestedCharacterId = suggestCharacterId(items);
        String suggestedExpressionId = "neutral";
        Set<String> usedLayerIds = new LinkedHashSet<>();

        TextField characterIdField = new TextField(suggestedCharacterId);
        characterIdField.setPromptText("character_id");
        characterIdField.setStyle(STYLE_FILTER);

        TextField expressionField = new TextField(suggestedExpressionId);
        expressionField.setPromptText("expression");
        expressionField.setStyle(STYLE_FILTER);

        HBox configRow = new HBox(8, labeledField("Character", characterIdField), labeledField("Expression", expressionField));
        configRow.setAlignment(Pos.CENTER_LEFT);

        VBox layerRows = new VBox(8);
        List<CharpresetLayerEditor> editors = new ArrayList<>();
        for (ImportPreviewItem item : items) {
            TextField layerField = new TextField(suggestLayerId(item.file.getName(), usedLayerIds));
            layerField.setPromptText("layer_id");
            layerField.setStyle(STYLE_FILTER);
            layerField.setPrefWidth(140);

            ImageView thumb = createDialogPreviewImageView(48, 48);
            thumb.setImage(item.previewImage);

            Label fileLabel = new Label(item.file.getName());
            fileLabel.setWrapText(true);
            fileLabel.setStyle("-fx-text-fill: #f2f4f7; -fx-font-size: 11px; -fx-font-weight: bold;");
            Label sourceLabel = new Label(item.existingProjectPath != null
                ? "Reuse " + item.existingProjectPath
                : item.file.getAbsolutePath().replace('\\', '/'));
            sourceLabel.setWrapText(true);
            sourceLabel.setStyle(STYLE_DIALOG_META);
            Label targetLabel = new Label();
            targetLabel.setWrapText(true);
            targetLabel.setStyle(STYLE_DIALOG_HELP);

            VBox textBox = new VBox(2, fileLabel, sourceLabel, targetLabel);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            HBox row = new HBox(8, thumb, textBox, layerField);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6));
            row.setStyle("-fx-background-color: #13161c; -fx-background-radius: 6; -fx-border-color: #272d37; -fx-border-radius: 6;");
            layerRows.getChildren().add(row);
            editors.add(new CharpresetLayerEditor(item, layerField, targetLabel));
        }

        StackPane compositePreview = new StackPane();
        compositePreview.setPrefSize(240, 260);
        compositePreview.setMinHeight(220);
        compositePreview.setStyle(STYLE_IMPORT_PREVIEW_FRAME);
        for (ImportPreviewItem item : items) {
            ImageView layerView = createDialogPreviewImageView(220, 240);
            layerView.setImage(item.previewImage);
            compositePreview.getChildren().add(layerView);
        }

        TextArea snippetArea = new TextArea();
        snippetArea.setEditable(false);
        snippetArea.setWrapText(false);
        snippetArea.setPrefRowCount(6);
        snippetArea.setStyle(STYLE_SNIPPET_AREA);

        Label targetModeLabel = new Label(scriptTargetFile != null
            ? "Active VNS target: " + scriptTargetFile.getName()
            : "No active VNS target. Import will still copy the generated snippet.");
        targetModeLabel.setWrapText(true);
        targetModeLabel.setStyle(STYLE_DIALOG_HELP);

        Label statusLabel = new Label("Review layer IDs before importing.");
        statusLabel.setWrapText(true);
        statusLabel.setStyle(STYLE_DIALOG_STATUS);

        Runnable refreshCharpresetPreview = () -> {
            String characterId = normalizedIdentifierOrDefault(characterIdField.getText(), "character");
            String expressionId = normalizedIdentifierOrDefault(expressionField.getText(), "neutral");
            List<CharpresetSnippetLayer> layers = new ArrayList<>();
            for (CharpresetLayerEditor editor : editors) {
                String layerId = normalizedIdentifierOrDefault(editor.layerField().getText(), "layer");
                String relativePath = editor.item().existingProjectPath != null
                    ? editor.item().existingProjectPath
                    : previewCharpresetImportRelativePath(characterId, layerId, editor.item().file().getName());
                editor.targetLabel().setText(editor.item().existingProjectPath != null
                    ? "Will reuse existing asset path."
                    : "Will import to " + relativePath);
                layers.add(new CharpresetSnippetLayer(layerId, relativePath));
            }
            snippetArea.setText(buildCharpresetSnippet(characterId, expressionId, layers));
        };

        characterIdField.textProperty().addListener((obs, oldValue, newValue) -> refreshCharpresetPreview.run());
        expressionField.textProperty().addListener((obs, oldValue, newValue) -> refreshCharpresetPreview.run());
        for (CharpresetLayerEditor editor : editors) {
            editor.layerField().textProperty().addListener((obs, oldValue, newValue) -> refreshCharpresetPreview.run());
        }
        refreshCharpresetPreview.run();

        VBox previewBox = new VBox(8, compositePreview, targetModeLabel);
        HBox.setHgrow(previewBox, Priority.ALWAYS);
        VBox snippetBox = new VBox(8,
            new Label("Generated snippet"),
            snippetArea,
            statusLabel
        );
        VBox.setVgrow(snippetArea, Priority.ALWAYS);

        Label intro = new Label(
            "Import layered character art into the project, then generate matching @charlayer and @charpreset declarations."
        );
        intro.setWrapText(true);
        intro.setStyle(STYLE_DIALOG_HELP);

        VBox content = new VBox(10, intro, configRow, previewBox, layerRows, snippetBox);
        content.setFillWidth(true);
        content.setMaxWidth(Double.MAX_VALUE);

        List<ActionEditorDialogOverlay.ActionSpec> actions = new ArrayList<>();
        actions.add(ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", importOverlay::hideOverlay));
        if (scriptTargetFile != null) {
            actions.add(
                ActionEditorDialogOverlay.ActionSpec
                    .stayOpen("Import + Append", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                        if (importCharpresetFiles(characterIdField, expressionField, editors, statusLabel, true)) {
                            importOverlay.hideOverlay();
                        }
                    })
                    .defaultFocus(true)
            );
            actions.add(
                ActionEditorDialogOverlay.ActionSpec
                    .stayOpen("Import + Copy", ActionEditorDialogOverlay.ButtonStyle.NEUTRAL, () -> {
                        if (importCharpresetFiles(characterIdField, expressionField, editors, statusLabel, false)) {
                            importOverlay.hideOverlay();
                        }
                    })
            );
        } else {
            actions.add(
                ActionEditorDialogOverlay.ActionSpec
                    .stayOpen("Import + Copy Snippet", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                        if (importCharpresetFiles(characterIdField, expressionField, editors, statusLabel, false)) {
                            importOverlay.hideOverlay();
                        }
                    })
                    .defaultFocus(true)
            );
        }

        importOverlay.showDialog(
            "Import Charpreset",
            items.size() == 1
                ? "1 layer selected"
                : items.size() + " layers selected",
            content,
            actions.toArray(ActionEditorDialogOverlay.ActionSpec[]::new)
        );
    }

    private boolean importCharpresetFiles(
        TextField characterIdField,
        TextField expressionField,
        List<CharpresetLayerEditor> editors,
        Label statusLabel,
        boolean appendToScript
    ) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            statusLabel.setText("Open a project first.");
            return false;
        }
        String characterId = normalizedIdentifierOrDefault(characterIdField.getText(), "character");
        String expressionId = normalizedIdentifierOrDefault(expressionField.getText(), "neutral");

        List<String> layerIds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (CharpresetLayerEditor editor : editors) {
            String layerId = normalizedIdentifierOrDefault(editor.layerField().getText(), suggestLayerId(editor.item().file().getName(), seen));
            if (!seen.add(layerId)) {
                statusLabel.setText("Layer IDs must be unique. Duplicate: " + layerId);
                return false;
            }
            layerIds.add(layerId);
        }

        Path projectRootPath = projectRoot.toPath().toAbsolutePath().normalize();
        List<String> importedPaths = new ArrayList<>();
        List<CharpresetSnippetLayer> snippetLayers = new ArrayList<>();
        int copiedCount = 0;
        int reusedCount = 0;

        try {
            for (int i = 0; i < editors.size(); i++) {
                CharpresetLayerEditor editor = editors.get(i);
                ImportPreviewItem item = editor.item();
                String layerId = layerIds.get(i);
                String relativePath;

                Path source = item.file().toPath().toAbsolutePath().normalize();
                if (item.existingProjectPath() != null && source.startsWith(projectRootPath)) {
                    relativePath = item.existingProjectPath();
                    reusedCount++;
                } else {
                    Path target = resolveCharpresetImportTarget(projectRootPath, characterId, layerId, item.file().getName());
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    relativePath = projectRootPath.relativize(target).toString().replace('\\', '/');
                    copiedCount++;
                }
                importedPaths.add(relativePath);
                snippetLayers.add(new CharpresetSnippetLayer(layerId, relativePath));
            }
        } catch (IOException ex) {
            statusLabel.setText("Import failed: " + ex.getMessage());
            return false;
        }

        String snippet = buildCharpresetSnippet(characterId, expressionId, snippetLayers);
        if (snippet.isBlank()) {
            statusLabel.setText("No valid layer declarations were generated.");
            return false;
        }

        copyToClipboard(snippet);

        if (appendToScript) {
            try {
                appendCharpresetSnippet(scriptTargetFile.toPath(), snippet);
            } catch (IOException ex) {
                statusLabel.setText("Imported assets, but failed to append snippet: " + ex.getMessage());
                return false;
            }
        }

        filterField.clear();
        scanProject();
        if (!importedPaths.isEmpty()) {
            reselectByRelativePath(importedPaths.get(importedPaths.size() - 1));
            listView.scrollTo(Math.max(0, listView.getSelectionModel().getSelectedIndex()));
        }

        StringBuilder status = new StringBuilder();
        status.append("Imported charpreset ").append(characterId).append('/').append(expressionId);
        if (copiedCount > 0) {
            status.append(" (").append(copiedCount).append(" copied");
            if (reusedCount > 0) {
                status.append(", ").append(reusedCount).append(" reused");
            }
            status.append(")");
        } else if (reusedCount > 0) {
            status.append(" (").append(reusedCount).append(" reused)");
        }
        status.append(appendToScript ? " and appended snippet to active VNS." : ". Snippet copied to clipboard.");
        lblStatus.setText(status.toString());
        statusLabel.setText(status.toString());
        return true;
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

    private List<ImportPreviewItem> buildImportPreviewItems(List<File> files) {
        List<ImportPreviewItem> items = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return items;
        }
        Path projectRootPath = projectRoot != null && projectRoot.isDirectory()
            ? projectRoot.toPath().toAbsolutePath().normalize()
            : null;
        Set<Path> seen = new LinkedHashSet<>();
        for (File file : files) {
            if (file == null || !file.isFile()) continue;
            String ext = getExtension(file.getName());
            if (!IMAGE_EXTENSIONS.contains(ext)) continue;
            Path absolute = file.toPath().toAbsolutePath().normalize();
            if (!seen.add(absolute)) continue;

            String existingProjectPath = null;
            if (projectRootPath != null && absolute.startsWith(projectRootPath)) {
                existingProjectPath = projectRootPath.relativize(absolute).toString().replace('\\', '/');
            }

            Image preview = loadPreviewImage(file);
            double width = preview != null ? preview.getWidth() : 0.0;
            double height = preview != null ? preview.getHeight() : 0.0;
            items.add(new ImportPreviewItem(
                file,
                stripExtension(file.getName()),
                existingProjectPath,
                file.length(),
                width,
                height,
                preview
            ));
        }
        return items;
    }

    private ListView<ImportPreviewItem> buildImportPreviewList(List<ImportPreviewItem> items) {
        ListView<ImportPreviewItem> previewList = new ListView<>();
        previewList.getItems().setAll(items);
        previewList.setCellFactory(lv -> new ImportPreviewCell());
        previewList.setStyle("-fx-background-color: #121212; -fx-control-inner-background: #121212;");
        return previewList;
    }

    private void updateImageImportPreview(
        ImportPreviewItem item,
        ImageView previewImage,
        Label previewTitle,
        Label previewMeta,
        Label targetLabel
    ) {
        if (item == null) {
            previewImage.setImage(null);
            previewTitle.setText("No image selected");
            previewMeta.setText("");
            targetLabel.setText("");
            return;
        }
        previewImage.setImage(item.previewImage);
        previewTitle.setText(item.file.getName());
        previewMeta.setText(formatPreviewMeta(item));
        targetLabel.setText(item.existingProjectPath != null
            ? "This file is already inside the project and will be reused as " + item.existingProjectPath + "."
            : "Import destination: " + previewImageImportRelativePath(item.file.getName()));
    }

    private void updatePreviewPane(AssetEntry entry) {
        if (entry == null) {
            previewImageView.setImage(null);
            previewTitleLabel.setText("No asset selected");
            previewPathLabel.setText("");
            previewMetaLabel.setText("");
            previewTagsLabel.setText("");
            previewUsageLabel.setText("Search by character, expression, layer, preset, or path.");
            return;
        }
        previewImageView.setImage(entry.previewImage());
        previewTitleLabel.setText(entry.baseName);
        previewPathLabel.setText(entry.relativePath);
        previewMetaLabel.setText(entry.describeMeta());
        previewTagsLabel.setText(entry.describeTags());
        previewUsageLabel.setText(entry.describeUsage());
    }

    private List<AssetEntry> enrichAssetMetadata(Map<String, AssetEntry> entriesByRelativePath) {
        List<AssetEntry> presetEntries = new ArrayList<>();
        if (projectRoot == null || !projectRoot.isDirectory() || entriesByRelativePath == null || entriesByRelativePath.isEmpty()) {
            return presetEntries;
        }
        List<File> scripts = new ArrayList<>();
        collectScripts(projectRoot, scripts, 0);
        Path projectRootPath = projectRoot.toPath().toAbsolutePath().normalize();
        Map<String, AssetEntry> presetEntriesByRef = new LinkedHashMap<>();

        for (File script : scripts) {
            Path scriptPath = script.toPath().toAbsolutePath().normalize();
            String scriptRelativePath = projectRootPath.relativize(scriptPath).toString().replace('\\', '/');
            Map<String, Map<String, String>> charLayerPaths = new LinkedHashMap<>();
            Map<String, Map<String, List<String>>> charGroupLayerIds = new LinkedHashMap<>();
            Map<String, Map<String, List<String>>> presetPaths = new LinkedHashMap<>();
            List<String> lines;
            try {
                String source = Files.readString(scriptPath, StandardCharsets.UTF_8);
                lines = List.of(LayeredCharacterResolver
                    .collapseLayerDirectiveContinuations(source)
                    .split("\\R", -1));
            } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
                continue;
            }
            for (String rawLine : lines) {
                if (rawLine == null) continue;
                String line = rawLine.trim();
                if (line.isEmpty()) continue;

                Matcher bg = BG_DECL_PATTERN.matcher(line);
                if (bg.matches()) {
                    annotateBackground(entriesByRelativePath, normalizeDeclaredAssetPath(bg.group(2)), bg.group(1), scriptRelativePath);
                    continue;
                }

                Matcher charImg = CHARIMG_PATTERN.matcher(line);
                if (charImg.matches()) {
                    annotateCharImg(
                        entriesByRelativePath,
                        normalizeDeclaredAssetPath(charImg.group(3)),
                        charImg.group(1),
                        charImg.group(2),
                        scriptRelativePath
                    );
                    continue;
                }

                Matcher charLayer = CHARLAYER_PATTERN.matcher(line);
                if (charLayer.matches()) {
                    String characterId = charLayer.group(1);
                    String layerId = charLayer.group(2);
                    String relativePath = normalizeDeclaredAssetPath(charLayer.group(3));
                    charLayerPaths.computeIfAbsent(characterId, ignored -> new LinkedHashMap<>()).put(layerId, relativePath);
                    annotateCharLayer(entriesByRelativePath, relativePath, characterId, layerId, scriptRelativePath);
                    continue;
                }

                Matcher charGroup = CHARGROUP_PATTERN.matcher(line);
                if (charGroup.matches()) {
                    String characterId = charGroup.group(1);
                    String groupId = charGroup.group(2);
                    List<String> layerIds = resolveGroupLayerIds(charLayerPaths, charGroupLayerIds, characterId, charGroup.group(3));
                    if (!layerIds.isEmpty()) {
                        charGroupLayerIds.computeIfAbsent(characterId, ignored -> new LinkedHashMap<>()).put(groupId, layerIds);
                    }
                    continue;
                }

                Matcher charPreset = CHARPRESET_PATTERN.matcher(line);
                if (charPreset.matches()) {
                    String characterId = charPreset.group(1);
                    String presetId = charPreset.group(2);
                    List<String> resolvedPaths = resolvePresetPaths(charLayerPaths, charGroupLayerIds, presetPaths, characterId, charPreset.group(3));
                    if (!resolvedPaths.isEmpty()) {
                        presetPaths.computeIfAbsent(characterId, ignored -> new LinkedHashMap<>()).put(presetId, resolvedPaths);
                        annotateCharPreset(entriesByRelativePath, resolvedPaths, characterId, presetId, scriptRelativePath);
                        registerPresetEntry(presetEntriesByRef, entriesByRelativePath, resolvedPaths, characterId, presetId, scriptRelativePath);
                    }
                }
            }
        }
        presetEntries.addAll(presetEntriesByRef.values());
        return presetEntries;
    }

    private void annotateBackground(
        Map<String, AssetEntry> entriesByRelativePath,
        String relativePath,
        String backgroundId,
        String scriptRelativePath
    ) {
        AssetEntry entry = entriesByRelativePath.get(relativePath);
        if (entry == null) return;
        entry.backgroundIds.add(backgroundId);
        entry.sourceScripts.add(scriptRelativePath);
    }

    private void annotateCharImg(
        Map<String, AssetEntry> entriesByRelativePath,
        String relativePath,
        String characterId,
        String expressionId,
        String scriptRelativePath
    ) {
        AssetEntry entry = entriesByRelativePath.get(relativePath);
        if (entry == null) return;
        entry.charImgRefs.add(characterId + "/" + expressionId);
        entry.sourceScripts.add(scriptRelativePath);
    }

    private void annotateCharLayer(
        Map<String, AssetEntry> entriesByRelativePath,
        String relativePath,
        String characterId,
        String layerId,
        String scriptRelativePath
    ) {
        AssetEntry entry = entriesByRelativePath.get(relativePath);
        if (entry == null) return;
        entry.charLayerRefs.add(characterId + "/" + layerId);
        entry.sourceScripts.add(scriptRelativePath);
    }

    private void annotateCharPreset(
        Map<String, AssetEntry> entriesByRelativePath,
        List<String> resolvedPaths,
        String characterId,
        String presetId,
        String scriptRelativePath
    ) {
        String ref = characterId + "/" + presetId;
        for (String relativePath : resolvedPaths) {
            AssetEntry entry = entriesByRelativePath.get(relativePath);
            if (entry == null) continue;
            entry.presetRefs.add(ref);
            entry.sourceScripts.add(scriptRelativePath);
        }
    }

    private void registerPresetEntry(
        Map<String, AssetEntry> presetEntriesByRef,
        Map<String, AssetEntry> entriesByRelativePath,
        List<String> resolvedPaths,
        String characterId,
        String presetId,
        String scriptRelativePath
    ) {
        if (resolvedPaths == null || resolvedPaths.isEmpty()) {
            return;
        }
        String ref = characterId + "/" + presetId;
        AssetEntry presetEntry = presetEntriesByRef.computeIfAbsent(ref, ignored ->
            AssetEntry.presetEntry(characterId, presetId)
        );
        presetEntry.presetRefs.add(ref);
        presetEntry.sourceScripts.add(scriptRelativePath);
        for (String relativePath : resolvedPaths) {
            if (relativePath == null || relativePath.isBlank()) {
                continue;
            }
            boolean newLayer = presetEntry.presetLayerPaths.add(relativePath);
            AssetEntry layerEntry = entriesByRelativePath.get(relativePath);
            if (layerEntry != null) {
                String layerId = layerEntry.charLayerRefs.stream()
                    .filter(value -> value != null && value.startsWith(characterId + "/"))
                    .map(value -> value.substring((characterId + "/").length()))
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(layerEntry.baseName);
                presetEntry.presetLayerNames.add(layerEntry.baseName);
                presetEntry.charLayerRefs.addAll(layerEntry.charLayerRefs);
                presetEntry.charImgRefs.addAll(layerEntry.charImgRefs);
                presetEntry.sourceScripts.addAll(layerEntry.sourceScripts);
                if (newLayer && layerEntry.file != null && layerEntry.file.isFile()) {
                    presetEntry.presetLayerFiles.add(layerEntry.file);
                    presetEntry.presetLayers.add(new AssetEntry.PresetLayer(
                        relativePath,
                        layerId,
                        layerEntry.baseName,
                        layerEntry.file
                    ));
                }
            }
        }
    }

    static List<String> resolvePresetPaths(
        Map<String, Map<String, String>> charLayerPaths,
        Map<String, Map<String, List<String>>> charGroupLayerIds,
        Map<String, Map<String, List<String>>> presetPaths,
        String defaultCharacterId,
        String spec
    ) {
        List<String> resolved = new ArrayList<>();
        if (spec == null || spec.isBlank()) return resolved;
        String[] tokens = spec.split("\\|");
        for (String token : tokens) {
            String part = token == null ? "" : token.trim();
            if (part.isEmpty()) continue;
            if (part.startsWith("$")) {
                String rawRef = part.substring(1).trim();
                List<LayeredCharacterResolver.LayerMatch> matches =
                    LayeredCharacterResolver.resolveLayerMatches(charLayerPaths, defaultCharacterId, rawRef);
                if (!matches.isEmpty()) {
                    for (LayeredCharacterResolver.LayerMatch match : matches) {
                        resolved.add(normalizeDeclaredAssetPath(match.path()));
                    }
                } else {
                    resolved.addAll(resolveGroupPaths(charLayerPaths, charGroupLayerIds, defaultCharacterId, rawRef));
                }
                continue;
            }
            if (part.startsWith("@")) {
                String rawPresetRef = part.substring(1).trim();
                LayeredCharacterResolver.CharacterRef ref = LayeredCharacterResolver.parseReference(rawPresetRef, defaultCharacterId);
                List<String> preset = presetPaths.getOrDefault(ref.characterId(), Map.of()).get(ref.localId());
                if (preset != null) {
                    resolved.addAll(preset);
                }
                continue;
            }
            resolved.add(normalizeDeclaredAssetPath(part));
        }
        return resolved;
    }

    private static List<String> resolveGroupPaths(
        Map<String, Map<String, String>> charLayerPaths,
        Map<String, Map<String, List<String>>> charGroupLayerIds,
        String defaultCharacterId,
        String rawRef
    ) {
        LayeredCharacterResolver.CharacterRef ref = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
        List<String> layerIds = charGroupLayerIds.getOrDefault(ref.characterId(), Map.of()).get(ref.localId());
        if (layerIds == null || layerIds.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String layerId : layerIds) {
            String path = LayeredCharacterResolver.resolveLayerPath(charLayerPaths, ref.characterId(), layerId);
            if (path != null && !path.isBlank()) out.add(normalizeDeclaredAssetPath(path));
        }
        return List.copyOf(out);
    }

    static List<String> resolveGroupLayerIds(
        Map<String, Map<String, String>> charLayerPaths,
        Map<String, Map<String, List<String>>> charGroupLayerIds,
        String defaultCharacterId,
        String rawSpec
    ) {
        String spec = stripLeadingGroupOptions(rawSpec);
        if (spec.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String token : spec.split("\\|")) {
            String part = token == null ? "" : token.trim();
            if (part.isEmpty() || !part.startsWith("$")) continue;
            String rawRef = part.substring(1).trim();
            LayeredCharacterResolver.CharacterRef ref = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
            List<LayeredCharacterResolver.LayerMatch> matches =
                LayeredCharacterResolver.resolveLayerMatches(charLayerPaths, defaultCharacterId, rawRef);
            if (!matches.isEmpty()) {
                for (LayeredCharacterResolver.LayerMatch match : matches) {
                    if (!out.contains(match.layerId())) out.add(match.layerId());
                }
                continue;
            }
            List<String> nested = charGroupLayerIds.getOrDefault(ref.characterId(), Map.of()).get(ref.localId());
            if (nested != null) {
                for (String layerId : nested) {
                    if (layerId != null && !layerId.isBlank() && !out.contains(layerId)) out.add(layerId);
                }
            }
        }
        return List.copyOf(out);
    }

    private static String stripLeadingGroupOptions(String rawSpec) {
        String spec = rawSpec == null ? "" : rawSpec.trim();
        while (!spec.isBlank()) {
            int split = firstWhitespaceIndex(spec);
            String token = split < 0 ? spec : spec.substring(0, split);
            String lower = token.toLowerCase(Locale.ROOT);
            boolean option = lower.startsWith("parent=")
                || lower.startsWith("parent:")
                || lower.startsWith("in=")
                || lower.startsWith("in:")
                || lower.startsWith("pivot=")
                || lower.startsWith("pivot:")
                || lower.startsWith("origin=")
                || lower.startsWith("origin:");
            if (!option) return spec;
            spec = split < 0 ? "" : spec.substring(split + 1).trim();
        }
        return spec;
    }

    private static int firstWhitespaceIndex(String value) {
        if (value == null) return -1;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
    }

    private static String normalizeDeclaredAssetPath(String rawPath) {
        if (rawPath == null) return "";
        String normalized = rawPath.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
            || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized.replace('\\', '/');
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

    static File resolveCharpresetImportDirectory(File projectRoot, String characterId, String layerId) {
        String normalizedCharacterId = normalizedIdentifierOrDefault(characterId, "character");
        File base = new File(projectRoot, CHARPRESET_IMPORT_RELATIVE_DIR + "/" + normalizedCharacterId);
        if (layerId == null || layerId.isBlank()) {
            return base;
        }
        return new File(base, normalizedIdentifierOrDefault(layerId, "layer"));
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

    static Path resolveCharpresetImportTarget(Path projectRoot, String characterId, String layerId, String originalFileName) {
        Path importDir = projectRoot
            .resolve(CHARPRESET_IMPORT_RELATIVE_DIR)
            .resolve(normalizedIdentifierOrDefault(characterId, "character"))
            .resolve(normalizedIdentifierOrDefault(layerId, "layer"));
        return resolveUniqueImportTarget(importDir, originalFileName);
    }

    static String sanitizeCharpresetIdentifier(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(normalized.length());
        boolean previousUnderscore = false;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                out.append(ch);
                previousUnderscore = false;
            } else if (!previousUnderscore) {
                out.append('_');
                previousUnderscore = true;
            }
        }
        while (!out.isEmpty() && out.charAt(0) == '_') {
            out.deleteCharAt(0);
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '_') {
            out.deleteCharAt(out.length() - 1);
        }
        return out.toString();
    }

    static String normalizedIdentifierOrDefault(String value, String fallback) {
        String normalized = sanitizeCharpresetIdentifier(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        String safeFallback = sanitizeCharpresetIdentifier(fallback);
        return safeFallback.isBlank() ? "item" : safeFallback;
    }

    static String previewImageImportRelativePath(String originalFileName) {
        String fileName = (originalFileName == null || originalFileName.isBlank()) ? "asset.png" : originalFileName.trim();
        return IMPORT_RELATIVE_DIR + "/" + fileName;
    }

    static String previewCharpresetImportRelativePath(String characterId, String layerId, String originalFileName) {
        String safeCharacterId = normalizedIdentifierOrDefault(characterId, "character");
        String safeLayerId = normalizedIdentifierOrDefault(layerId, "layer");
        String fileName = (originalFileName == null || originalFileName.isBlank()) ? "layer.png" : originalFileName.trim();
        return CHARPRESET_IMPORT_RELATIVE_DIR + "/" + safeCharacterId + "/" + safeLayerId + "/" + fileName;
    }

    static String buildCharpresetSnippet(String characterId, String expressionId, List<CharpresetSnippetLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return "";
        }
        String safeCharacterId = normalizedIdentifierOrDefault(characterId, "character");
        String safeExpressionId = normalizedIdentifierOrDefault(expressionId, "neutral");
        StringBuilder out = new StringBuilder();
        StringBuilder presetSpec = new StringBuilder();
        for (CharpresetSnippetLayer layer : layers) {
            if (layer == null) continue;
            String safeLayerId = normalizedIdentifierOrDefault(layer.layerId(), "layer");
            String relativePath = layer.relativePath() == null ? "" : layer.relativePath().trim().replace('\\', '/');
            if (relativePath.isBlank()) continue;
            out.append("@charlayer ")
                .append(safeCharacterId).append(' ')
                .append(safeLayerId).append(' ')
                .append(relativePath)
                .append('\n');
            if (presetSpec.length() > 0) {
                presetSpec.append(" | ");
            }
            presetSpec.append('$').append(safeLayerId);
        }
        if (presetSpec.length() == 0) {
            return "";
        }
        out.append("@charpreset ")
            .append(safeCharacterId).append(' ')
            .append(safeExpressionId).append(' ')
            .append(presetSpec)
            .append('\n');
        return out.toString();
    }

    static void appendCharpresetSnippet(Path scriptFile, String snippet) throws IOException {
        if (scriptFile == null || snippet == null || snippet.isBlank()) {
            return;
        }
        String normalized = snippet.endsWith("\n") ? snippet : snippet + "\n";
        boolean exists = Files.exists(scriptFile);
        if (!exists) {
            Files.writeString(scriptFile, normalized, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return;
        }
        String prefix = Files.size(scriptFile) == 0 ? "" : "\n";
        Files.writeString(scriptFile, prefix + normalized, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String suggestCharacterId(List<ImportPreviewItem> items) {
        if (items == null || items.isEmpty()) {
            return "character";
        }
        File first = items.get(0).file();
        File parent = first != null ? first.getParentFile() : null;
        if (parent != null) {
            String parentId = sanitizeCharpresetIdentifier(parent.getName());
            if (!parentId.isBlank()) {
                return parentId;
            }
        }
        return normalizedIdentifierOrDefault(items.get(0).displayName(), "character");
    }

    private static String suggestLayerId(String fileName, Set<String> usedIds) {
        String base = normalizedIdentifierOrDefault(stripExtension(fileName), "layer");
        if (usedIds == null) {
            return base;
        }
        String candidate = base;
        int suffix = 2;
        while (!usedIds.add(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static boolean hasImportableImages(List<File> files) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        for (File file : files) {
            if (file != null && file.isFile() && IMAGE_EXTENSIONS.contains(getExtension(file.getName()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVnsScriptFile(File file) {
        return file != null && getExtension(file.getName()).equals("vns");
    }

    private static Image loadPreviewImage(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            Image image = new Image(file.toURI().toString(), 640, 640, true, true, false);
            return image.isError() ? null : image;
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            return null;
        }
    }

    private static Image buildCompositePreview(List<File> files, double maxSize) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        List<Image> images = new ArrayList<>();
        double maxWidth = 0.0;
        double maxHeight = 0.0;
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            try {
                Image image = new Image(file.toURI().toString(), 0, 0, true, true, false);
                if (image.isError() || image.getWidth() <= 0.0 || image.getHeight() <= 0.0) {
                    continue;
                }
                images.add(image);
                maxWidth = Math.max(maxWidth, image.getWidth());
                maxHeight = Math.max(maxHeight, image.getHeight());
            } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
        }
        if (images.isEmpty() || maxWidth <= 0.0 || maxHeight <= 0.0) {
            return null;
        }
        double scale = Math.min(1.0, Math.min(maxSize / maxWidth, maxSize / maxHeight));
        double width = Math.max(1.0, Math.round(maxWidth * scale));
        double height = Math.max(1.0, Math.round(maxHeight * scale));
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        for (Image image : images) {
            gc.drawImage(image, 0, 0, image.getWidth() * scale, image.getHeight() * scale);
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage snapshot = new WritableImage((int) Math.ceil(width), (int) Math.ceil(height));
        return canvas.snapshot(params, snapshot);
    }

    private static ImageView createDialogPreviewImageView(double fitWidth, double fitHeight) {
        ImageView view = new ImageView();
        view.setFitWidth(fitWidth);
        view.setFitHeight(fitHeight);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static StackPane wrapPreviewFrame(ImageView view) {
        StackPane frame = new StackPane(view);
        frame.setAlignment(Pos.CENTER);
        frame.setMinHeight(180);
        frame.setStyle(STYLE_IMPORT_PREVIEW_FRAME);
        return frame;
    }

    private static VBox labeledField(String label, TextField field) {
        Label header = new Label(label);
        header.setStyle(STYLE_DIALOG_HELP + "; -fx-font-weight: bold;");
        VBox box = new VBox(4, header, field);
        HBox.setHgrow(box, Priority.ALWAYS);
        VBox.setVgrow(field, Priority.NEVER);
        return box;
    }

    private static String formatPreviewMeta(ImportPreviewItem item) {
        if (item == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add(humanFileSize(item.sizeBytes));
        if (item.width > 0.0 && item.height > 0.0) {
            parts.add(String.format(Locale.ROOT, "%.0f x %.0f px", item.width, item.height));
        }
        if (item.existingProjectPath != null) {
            parts.add("Already in project");
        }
        return String.join("  •  ", parts);
    }

    private static String humanFileSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        double kb = sizeBytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", gb);
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    static class AssetEntry {
        record PresetLayer(
            String relativePath,
            String layerId,
            String displayName,
            File file
        ) {
        }

        enum Kind {
            FILE,
            PRESET
        }

        final Kind kind;
        final String relativePath;
        final String baseName;
        final File file;
        final Set<String> charImgRefs = new LinkedHashSet<>();
        final Set<String> charLayerRefs = new LinkedHashSet<>();
        final Set<String> presetRefs = new LinkedHashSet<>();
        final Set<String> backgroundIds = new LinkedHashSet<>();
        final Set<String> sourceScripts = new LinkedHashSet<>();
        final Set<String> presetLayerPaths = new LinkedHashSet<>();
        final Set<String> presetLayerNames = new LinkedHashSet<>();
        final List<File> presetLayerFiles = new ArrayList<>();
        final List<PresetLayer> presetLayers = new ArrayList<>();
        String searchIndex = "";
        Image previewCache;
        String presetCharacterId;
        String presetId;

        AssetEntry(String relativePath, String baseName, File file) {
            this(Kind.FILE, relativePath, baseName, file);
        }

        AssetEntry(Kind kind, String relativePath, String baseName, File file) {
            this.kind = kind != null ? kind : Kind.FILE;
            this.relativePath = relativePath;
            this.baseName = baseName;
            this.file = file;
        }

        static AssetEntry presetEntry(String characterId, String presetId) {
            String ref = normalizedIdentifierOrDefault(characterId, "character")
                + "/" + normalizedIdentifierOrDefault(presetId, "preset");
            AssetEntry entry = new AssetEntry(Kind.PRESET, "@charpreset " + ref, ref + " [Preset]", null);
            entry.presetCharacterId = normalizedIdentifierOrDefault(characterId, "character");
            entry.presetId = normalizedIdentifierOrDefault(presetId, "preset");
            return entry;
        }

        boolean isPresetEntry() {
            return kind == Kind.PRESET;
        }

        boolean isPlaceable() {
            return kind == Kind.FILE && file != null && file.isFile();
        }

        boolean isSceneInstantiable() {
            return isPlaceable() || isPresetEntry();
        }

        boolean supportsPlacementRole(PuppeteerAssetPlacementRole role) {
            PuppeteerAssetPlacementRole resolvedRole = role != null ? role : PuppeteerAssetPlacementRole.PROP;
            if (isPresetEntry()) {
                return resolvedRole != PuppeteerAssetPlacementRole.BACKGROUND;
            }
            return isPlaceable();
        }

        boolean isImported() {
            return relativePath.startsWith(IMPORT_RELATIVE_DIR + "/");
        }

        boolean isCharacterAsset() {
            if (isPresetEntry()) {
                return true;
            }
            return relativePath.startsWith(CHARPRESET_IMPORT_RELATIVE_DIR + "/")
                || relativePath.startsWith("assets/characters/")
                || !charImgRefs.isEmpty()
                || !charLayerRefs.isEmpty()
                || !presetRefs.isEmpty();
        }

        boolean isBackgroundAsset() {
            if (isPresetEntry()) {
                return false;
            }
            return relativePath.startsWith("assets/backgrounds/") || !backgroundIds.isEmpty();
        }

        boolean isReferenced() {
            return !charImgRefs.isEmpty() || !charLayerRefs.isEmpty() || !presetRefs.isEmpty() || !backgroundIds.isEmpty();
        }

        boolean matchesScope(AssetScopeFilter filter) {
            return switch (filter) {
                case ALL -> true;
                case CHARACTERS -> isCharacterAsset();
                case BACKGROUNDS -> isBackgroundAsset();
                case IMPORTED -> isImported();
                case OTHER -> !isCharacterAsset() && !isBackgroundAsset() && !isImported();
            };
        }

        boolean matchesReference(AssetReferenceFilter filter) {
            return switch (filter) {
                case ALL -> true;
                case DECLARED -> isReferenced();
                case CHARIMG -> !charImgRefs.isEmpty();
                case CHARLAYER -> !charLayerRefs.isEmpty();
                case CHARPRESET -> !presetRefs.isEmpty();
                case BACKGROUND -> !backgroundIds.isEmpty();
                case UNREFERENCED -> !isReferenced();
            };
        }

        boolean matchesQuery(String query) {
            if (query == null || query.isBlank()) return true;
            String[] tokens = query.split("\\s+");
            for (String token : tokens) {
                if (token.isBlank()) continue;
                if (!searchIndex.contains(token)) {
                    return false;
                }
            }
            return true;
        }

        void rebuildSearchIndex() {
            StringBuilder search = new StringBuilder();
            appendSearch(search, baseName);
            appendSearch(search, relativePath);
            for (String value : charImgRefs) appendSearch(search, value);
            for (String value : charLayerRefs) appendSearch(search, value);
            for (String value : presetRefs) appendSearch(search, value);
            for (String value : presetLayerPaths) appendSearch(search, value);
            for (String value : presetLayerNames) appendSearch(search, value);
            for (String value : backgroundIds) appendSearch(search, value);
            for (String value : sourceScripts) appendSearch(search, value);
            if (isImported()) appendSearch(search, "imported");
            if (isCharacterAsset()) appendSearch(search, "character");
            if (isBackgroundAsset()) appendSearch(search, "background");
            if (isPresetEntry()) appendSearch(search, "preset composite charpreset");
            searchIndex = search.toString();
        }

        String usageBadge() {
            if (isPresetEntry()) {
                return "Preset • @charpreset";
            }
            List<String> tags = new ArrayList<>();
            if (!charImgRefs.isEmpty()) tags.add("@charimg");
            if (!charLayerRefs.isEmpty()) tags.add("@charlayer");
            if (!presetRefs.isEmpty()) tags.add("@charpreset");
            if (!backgroundIds.isEmpty()) tags.add("@background");
            if (tags.isEmpty()) {
                return isImported() ? "Imported" : "Unreferenced";
            }
            return String.join(" • ", tags);
        }

        String describeMeta() {
            List<String> parts = new ArrayList<>();
            if (isPresetEntry()) {
                parts.add("Preset composite");
                parts.add(presetLayerPaths.size() + " layer" + (presetLayerPaths.size() == 1 ? "" : "s"));
                Image preview = previewImage();
                if (preview != null && !preview.isError()) {
                    parts.add(String.format(Locale.ROOT, "%.0f x %.0f px", preview.getWidth(), preview.getHeight()));
                }
            } else {
                parts.add(humanFileSize(file.length()));
                Image preview = previewImage();
                if (preview != null && !preview.isError()) {
                    parts.add(String.format(Locale.ROOT, "%.0f x %.0f px", preview.getWidth(), preview.getHeight()));
                }
            }
            if (isImported()) parts.add("Imported");
            if (!sourceScripts.isEmpty()) parts.add(sourceScripts.size() + " script" + (sourceScripts.size() == 1 ? "" : "s"));
            return String.join("  •  ", parts);
        }

        String describeTags() {
            List<String> tags = new ArrayList<>();
            if (!charImgRefs.isEmpty()) tags.add("charimg: " + String.join(", ", charImgRefs));
            if (!charLayerRefs.isEmpty()) tags.add("charlayer: " + String.join(", ", charLayerRefs));
            if (!presetRefs.isEmpty()) tags.add("charpreset: " + String.join(", ", presetRefs));
            if (!backgroundIds.isEmpty()) tags.add("background: " + String.join(", ", backgroundIds));
            if (isPresetEntry() && !presetLayerNames.isEmpty()) {
                tags.add("layers: " + String.join(", ", presetLayerNames));
            }
            if (tags.isEmpty()) return "No VNS declaration references found for this asset yet.";
            return String.join("\n", tags);
        }

        String describeUsage() {
            List<String> lines = new ArrayList<>();
            if (!sourceScripts.isEmpty()) {
                lines.add("Referenced in: " + String.join(", ", sourceScripts));
            }
            if (isPresetEntry() && !presetLayerPaths.isEmpty()) {
                lines.add("Resolves to: " + String.join(", ", presetLayerPaths));
            }
            if (isImported() && !isReferenced()) {
                lines.add("Imported asset with no current VNS declarations.");
            }
            if (lines.isEmpty()) {
                lines.add("No matching @charimg, @charlayer, @charpreset, or @background declarations were found.");
            }
            return String.join("\n", lines);
        }

        Image previewImage() {
            if (previewCache != null) {
                return previewCache;
            }
            previewCache = isPresetEntry()
                ? buildCompositePreview(presetLayerFiles, 640)
                : loadPreviewImage(file);
            return previewCache;
        }

        private static void appendSearch(StringBuilder search, String value) {
            if (value == null || value.isBlank()) return;
            if (!search.isEmpty()) search.append(' ');
            search.append(value.toLowerCase(Locale.ROOT).replace('\\', '/'));
        }

        @Override
        public String toString() { return relativePath; }
    }

    record CharpresetSnippetLayer(String layerId, String relativePath) {
    }

    private record ImportPreviewItem(
        File file,
        String displayName,
        String existingProjectPath,
        long sizeBytes,
        double width,
        double height,
        Image previewImage
    ) {
    }

    private record CharpresetLayerEditor(
        ImportPreviewItem item,
        TextField layerField,
        Label targetLabel
    ) {
    }

    private static class AssetCell extends ListCell<AssetEntry> {
        private static final double THUMB_SIZE = 32;
        private final ImageView thumb = new ImageView();
        private final Label nameLabel = new Label();
        private final Label pathLabel = new Label();
        private final Label usageLabel = new Label();
        private final VBox textBox = new VBox(1);
        private final HBox row = new HBox(8, thumb, textBox);

        AssetCell() {
            thumb.setFitWidth(THUMB_SIZE);
            thumb.setFitHeight(THUMB_SIZE);
            thumb.setPreserveRatio(true);
            thumb.setSmooth(true);

            nameLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            pathLabel.setStyle("-fx-font-size: 9px;");
            usageLabel.setStyle("-fx-font-size: 9px;");
            textBox.getChildren().addAll(nameLabel, pathLabel, usageLabel);
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
                thumb.setImage(item.previewImage());
            } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
                thumb.setImage(null);
            }

            nameLabel.setText(item.baseName);
            pathLabel.setText(item.relativePath);
            usageLabel.setText(item.usageBadge());

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
                nameLabel.setStyle("-fx-text-fill: #f0f0f0; -fx-font-size: 11px; -fx-font-weight: bold;");
                pathLabel.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 9px;");
                usageLabel.setStyle("-fx-text-fill: #c9c9c9; -fx-font-size: 9px;");
            } else if (isHover()) {
                setStyle(STYLE_CELL_HOVER);
                nameLabel.setStyle("-fx-text-fill: #f2f2f2; -fx-font-size: 11px; -fx-font-weight: bold;");
                pathLabel.setStyle("-fx-text-fill: #939393; -fx-font-size: 9px;");
                usageLabel.setStyle("-fx-text-fill: #b8b8b8; -fx-font-size: 9px;");
            } else {
                setStyle(STYLE_CELL_BASE);
                nameLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");
                pathLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 9px;");
                usageLabel.setStyle("-fx-text-fill: #9a9a9a; -fx-font-size: 9px;");
            }
        }
    }

    private static class ImportPreviewCell extends ListCell<ImportPreviewItem> {
        private static final double THUMB_SIZE = 42;
        private final ImageView thumb = createDialogPreviewImageView(THUMB_SIZE, THUMB_SIZE);
        private final Label title = new Label();
        private final Label meta = new Label();
        private final VBox textBox = new VBox(2);
        private final HBox row = new HBox(8, thumb, textBox);

        ImportPreviewCell() {
            title.setStyle("-fx-text-fill: #f2f4f7; -fx-font-size: 11px; -fx-font-weight: bold;");
            meta.setStyle(STYLE_DIALOG_META);
            meta.setWrapText(true);
            textBox.getChildren().addAll(title, meta);
            HBox.setHgrow(textBox, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4));
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(ImportPreviewItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            thumb.setImage(item.previewImage);
            title.setText(item.file.getName());
            meta.setText(formatPreviewMeta(item));
            setGraphic(row);
            setText(null);
        }
    }
}
