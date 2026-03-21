package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Asset picker panel for Puppeteer. Browses the project directory for image files
 * and allows the user to add them as Sprite2D entities to the current scene.
 */
public class AssetPickerPanel extends VBox {
    static final String IMPORT_RELATIVE_DIR = "assets/puppeteer/imported";
    static final String CHARPRESET_IMPORT_RELATIVE_DIR = "assets/characters";

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
        "-fx-background-color: #4da3ff; -fx-text-fill: #fff; -fx-background-radius: 4; "
            + "-fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand; -fx-font-weight: bold;";
    private static final String STYLE_CELL_BASE =
        "-fx-background-color: transparent; -fx-background-radius: 6; -fx-border-radius: 6; "
            + "-fx-border-color: transparent; -fx-padding: 2 4;";
    private static final String STYLE_CELL_HOVER =
        "-fx-background-color: #262c35; -fx-background-radius: 6; -fx-border-radius: 6; "
            + "-fx-border-color: #2f3946; -fx-padding: 2 4;";
    private static final String STYLE_CELL_SELECTED =
        "-fx-background-color: linear-gradient(to right, #24384e, #1d2d40); "
            + "-fx-background-radius: 6; -fx-border-radius: 6; "
            + "-fx-border-color: #4da3ff; -fx-padding: 2 4;";
    private static final String STYLE_IMPORT_PREVIEW_FRAME =
        "-fx-background-color: #0f1116; -fx-background-radius: 8; -fx-border-radius: 8; "
            + "-fx-border-color: #2f3540; -fx-padding: 10;";
    private static final String STYLE_DIALOG_HELP =
        "-fx-text-fill: #a9b3c1; -fx-font-size: 11px;";
    private static final String STYLE_DIALOG_META =
        "-fx-text-fill: #7f8796; -fx-font-size: 10px;";
    private static final String STYLE_DIALOG_STATUS =
        "-fx-text-fill: #f0b673; -fx-font-size: 10px;";
    private static final String STYLE_SNIPPET_AREA =
        "-fx-control-inner-background: #12151b; -fx-font-family: 'Menlo'; -fx-highlight-fill: #315d98; "
            + "-fx-highlight-text-fill: white; -fx-text-fill: #d7dde6; -fx-border-color: #2f3540; "
            + "-fx-border-radius: 6; -fx-background-radius: 6;";

    private final ListView<AssetEntry> listView;
    private final TextField filterField;
    private final Label lblStatus;
    private final Label lblEmptyHint;
    private final Button btnImport;
    private final Button btnImportPreset;
    private final Button btnMakeBackground;
    private final Button btnMakeCharacter;
    private final Button btnMakeProp;
    private final ActionEditorDialogOverlay importOverlay;

    private File projectRoot;
    private File scriptTargetFile;
    private final List<AssetEntry> allAssets = new ArrayList<>();

    @FunctionalInterface
    interface AssetPlacementHandler {
        void accept(String relativePath, String suggestedName, PuppeteerAssetPlacementRole role);
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
        btnImport.setTooltip(new Tooltip("Review and import image files into " + IMPORT_RELATIVE_DIR));
        btnImport.setOnAction(e -> importFromChooser());

        btnImportPreset = new Button("Preset...");
        btnImportPreset.setStyle(STYLE_BTN);
        btnImportPreset.setTooltip(new Tooltip("Import layered character images and generate @charpreset declarations."));
        btnImportPreset.setOnAction(e -> importCharpresetFromChooser());

        HBox filterRow = new HBox(4, filterField, btnRefresh, btnImport, btnImportPreset);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        lblEmptyHint = new Label("No project root set.\nOpen a VNS file and launch\nPuppeteer to browse assets.");
        lblEmptyHint.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");
        lblEmptyHint.setWrapText(true);

        listView = new ListView<>();
        listView.setMinWidth(0);
        listView.setStyle("-fx-background-color: #1a1a1a; -fx-control-inner-background: #1a1a1a;");
        listView.setCellFactory(lv -> new AssetCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateActionState());
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                addSelectedToScene(PuppeteerAssetPlacementRole.PROP);
                event.consume();
            }
        });
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                addSelectedToScene(PuppeteerAssetPlacementRole.PROP);
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

        HBox actionRow = new HBox(6, btnMakeBackground, btnMakeCharacter, btnMakeProp);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(btnMakeBackground, Priority.ALWAYS);
        HBox.setHgrow(btnMakeCharacter, Priority.ALWAYS);
        HBox.setHgrow(btnMakeProp, Priority.ALWAYS);

        lblStatus = new Label("");
        lblStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 10px;");

        importOverlay = new ActionEditorDialogOverlay();

        installImportDropTarget();
        content.getChildren().addAll(header, filterRow, lblEmptyHint, listView, actionRow, lblStatus);

        StackPane contentStack = new StackPane(content, importOverlay);
        contentStack.setMinWidth(0);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        getChildren().add(contentStack);
        updateEmptyState();
        updateActionState();
    }

    public void setOnAddToScene(AssetPlacementHandler callback) {
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

    public void setScriptTargetFile(File file) {
        this.scriptTargetFile = isVnsScriptFile(file) ? file : null;
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
        listView.getItems().clear();
        for (AssetEntry entry : allAssets) {
            if (query.isEmpty() || entry.relativePath.toLowerCase(Locale.ROOT).contains(query)) {
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
        btnImportPreset.setDisable(!hasProject);
        boolean disablePlacement = listView.getSelectionModel().getSelectedItem() == null;
        btnMakeBackground.setDisable(disablePlacement);
        btnMakeCharacter.setDisable(disablePlacement);
        btnMakeProp.setDisable(disablePlacement);
    }

    private void addSelectedToScene(PuppeteerAssetPlacementRole role) {
        AssetEntry selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            lblStatus.setText("Select an image first");
            return;
        }
        if (onAddToScene != null) {
            onAddToScene.accept(selected.relativePath, selected.baseName, role != null ? role : PuppeteerAssetPlacementRole.PROP);
            PuppeteerAssetPlacementRole resolvedRole = role != null ? role : PuppeteerAssetPlacementRole.PROP;
            lblStatus.setText("Added " + resolvedRole.displayName().toLowerCase(Locale.ROOT) + ": " + selected.baseName);
        }
    }

    private void startSelectedAssetDrag() {
        AssetEntry selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
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
        previewList.setStyle("-fx-background-color: #12151b; -fx-control-inner-background: #12151b;");
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
            return null;
        }
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
