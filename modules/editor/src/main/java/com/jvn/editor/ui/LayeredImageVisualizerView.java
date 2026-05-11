package com.jvn.editor.ui;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiStyleSpec;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * Sidebar utility for layered sprite exploration and snippet generation.
 *
 * <h3>.layersetup files</h3>
 * The visualizer can export the current layer selection to a {@code .layersetup}
 * file via the save icon in the file-ops toolbar. This file is a simple
 * key=value text format that records which image is selected for each layer
 * group. It can later be re-imported into the visualizer (via the folder icon)
 * to restore the exact same layer configuration — useful for sharing presets
 * between team members or restoring a specific expression across sessions.
 * <p>
 * {@code .layersetup} files are an <b>editor-only</b> artifact; they are not
 * consumed by the JVN runtime.
 *
 * <h3>Hardening goals</h3>
 * <ul>
 *   <li>per-set persistence (selected layers, crop/focus/zoom, ids)</li>
 *   <li>save/load/delete presets</li>
 *   <li>active-group randomization and manual render-order controls</li>
 *   <li>multiple snippet export formats</li>
 * </ul>
 */
public class LayeredImageVisualizerView extends BorderPane implements ImageToolPanel {
  private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");
  private static final String NONE_LABEL = "(none)";
  private static final String DEFAULT_STATE_FILE = ".jvn/layered-image-visualizer.properties";
  private static final String DEFAULT_TOOL_TITLE = "Layered Image Visualizer";
  private static final double DEFAULT_CHARACTER_HEIGHT_FACTOR = 0.85;
  private static final double DEFAULT_CHARACTER_BASELINE_Y = 1.0;
  private static final double DEFAULT_SIDEBAR_DIVIDER = 0.67;
  private static final Map<String, String> GROUP_TOKEN_ALIASES = Map.ofEntries(
      Map.entry("eye", "eyes"),
      Map.entry("eyes", "eyes"),
      Map.entry("mouth", "mouth"),
      Map.entry("lip", "mouth"),
      Map.entry("lips", "mouth"),
      Map.entry("brow", "brow"),
      Map.entry("eyebrow", "brow"),
      Map.entry("eyebrows", "brow"),
      Map.entry("base", "base"),
      Map.entry("body", "body"),
      Map.entry("hair", "hair"),
      Map.entry("face", "face"),
      Map.entry("outfit", "outfit"),
      Map.entry("clothes", "outfit"),
      Map.entry("accessory", "accessory"),
      Map.entry("accessories", "accessory"),
      Map.entry("acc", "accessory")
  );

  private static final String SNIPPET_COMBINED = "@charimg + [show]";
  private static final String SNIPPET_CHARIMG = "@charimg only";
  private static final String SNIPPET_CHARPRESET_COMBINED = "@charpreset + [show]";
  private static final String SNIPPET_CHARPRESET = "@charpreset only";
  private static final String SNIPPET_INLINE_SHOW = "Inline composite [show]";
  private static final String SNIPPET_SHOW = "[show] only";
  private static final String SNIPPET_RECIPE = "Recipe comments";
  private static final String GALLERY_CELL_STYLE =
      "-fx-cursor: hand; -fx-padding: 2; -fx-background-radius: 4; -fx-border-radius: 4; "
          + "-fx-border-width: 1; -fx-border-color: transparent;";
  private static final String GALLERY_CELL_SELECTED_STYLE =
      "-fx-cursor: hand; -fx-padding: 2; -fx-background-radius: 4; -fx-border-radius: 4; "
          + "-fx-border-width: 1; -fx-border-color: #5a5a5a; -fx-background-color: rgba(255,255,255,0.08);";
  private static final String GALLERY_LABEL_STYLE = "-fx-font-size: 9px; -fx-text-fill: #9a9a9a;";
  private static final String GALLERY_LABEL_SELECTED_STYLE = "-fx-font-size: 9px; -fx-text-fill: #e0e0e0;";
  private static final String TILE_NORMAL_STYLE =
      "-fx-cursor: hand; -fx-padding: 3; -fx-background-radius: 4; -fx-border-radius: 4; "
          + "-fx-border-width: 2; -fx-border-color: transparent;";
  private static final String TILE_SELECTED_STYLE =
      "-fx-cursor: hand; -fx-padding: 3; -fx-background-radius: 4; -fx-border-radius: 4; "
          + "-fx-border-width: 2; -fx-border-color: #5a9fd4; -fx-background-color: rgba(90,159,212,0.12);";
  private static final String DEFAULT_SHORTFORMS = String.join("\n",
      "# Example:",
      "# happy = eyes=neutral mouth=happy",
      "# serious = eyes=cross_closed mouth=neutral");

  private final Label summaryLabel = new Label("Open a project to inspect layered image sets.");
  private final Label statusLabel = new Label("");
  private final Label previewInfoLabel = new Label("No layers selected.");
  private final Label groupStatsLabel = new Label("No groups loaded.");
  private final Label interactionHintLabel = new Label("Drag preview to pan, scroll to zoom, double-click to reset view.");

  private final TextField filterField = new TextField();
  private final CheckBox characterAssetsOnlyScan = new CheckBox("Characters only");
  private final ComboBox<String> setBox = new ComboBox<>();
  private final TextField characterIdField = new TextField();
  private final TextField expressionField = new TextField();
  private final CheckBox autoExpression = new CheckBox("Auto expression from selected layers");

  private final ComboBox<String> presetBox = new ComboBox<>();
  private final TextField presetNameField = new TextField();
  private final CheckBox customExportNameCheck = new CheckBox("Custom name");
  private final TextField exportNameField = new TextField();
  private final TextField exportDirectoryField = new TextField();
  private final Label exportInfoLabel = new Label("");

  private final ComboBox<String> snippetFormatBox = new ComboBox<>();
  private final CheckBox randomizeActiveOnly = new CheckBox("Randomize active groups only");
  private final CheckBox matchGameFraming = new CheckBox("Match game framing");
  private final CheckBox showOverlayGuides = new CheckBox("Show overlay guides");
  private final TextField attributeFilterField = new TextField();
  private final TextField typedAttributesField = new TextField();
  private final CheckBox typedRealtime = new CheckBox("Realtime preview");
  private final TextArea shortformsArea = new TextArea(DEFAULT_SHORTFORMS);
  private final ComboBox<String> shortformBox = new ComboBox<>();
  private final FlowPane galleryPane = new FlowPane(6, 6);
  private final Label galleryStatusLabel = new Label("");

  private final Canvas previewCanvas = new Canvas(320, 250);
  private final SplitPane workspaceSplit = new SplitPane();
  private final Slider focusXSlider = slider(0, 100, 50);
  private final Slider focusYSlider = slider(0, 100, 50);
  private final Slider cropSlider = slider(20, 100, 100);
  private final Slider zoomSlider = slider(50, 300, 100);

  private final VBox groupBox = new VBox(8);

  private final Map<String, ComboBox<LayerOption>> selectors = new LinkedHashMap<>();
  private final Map<String, CheckBox> activeGroupChecks = new LinkedHashMap<>();
  private final Map<String, CheckBox> swapGroupChecks = new LinkedHashMap<>();
  private final Map<String, Region> groupRows = new LinkedHashMap<>();
  private final Map<String, Map<LayerOption, Region>> groupTileNodes = new LinkedHashMap<>();
  private final Map<String, FlowPane> groupTilePanes = new LinkedHashMap<>();
  private final Map<String, Button> groupCollapseBtns = new LinkedHashMap<>();
  private final Map<String, Label> groupHeaderLabels = new LinkedHashMap<>();
  private final ListView<String> layerOrderList = new ListView<>();
  private final Map<String, String> shortforms = new LinkedHashMap<>();
  private final List<String> groupOrder = new ArrayList<>();
  private final Map<String, LayeredSet> sets = new LinkedHashMap<>();
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Map<String, String> presetNameToKey = new LinkedHashMap<>();
  private final Map<Canvas, ViewportFrame> viewportFrames = new IdentityHashMap<>();
  private final Map<ComboBox<LayerOption>, SearchableComboPopup<LayerOption>> selectorSearchPopups = new IdentityHashMap<>();

  private final Properties persisted = new Properties();
  private final Random random = new Random();
  private final PauseTransition stateSaveDebounce = new PauseTransition(Duration.millis(250));
  private final String toolTitle;
  private final String stateFile;
  private final boolean presetControlsEnabled;

  private File projectRoot;
  private String currentSetId;
  private String preferredSetId;
  private VBox selectedGalleryCell;
  private Label selectedGalleryLabel;
  private boolean applyingState;
  private Button fullscreenButton;
  private Runnable fullscreenToggleHandler;
  private boolean fullscreenActive;
  private Canvas dragCanvas;
  private double dragLastX;
  private double dragLastY;
  private boolean dragDirty;
  private boolean stateSavePending;
  private Task<LayeredCatalogScanResult> scanTask;
  private Button refreshCatalogButton;
  private Button sidebarHideButton;
  private Button sidebarShowButton;
  private double gameCharacterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double gameCharacterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;
  private boolean disposed;
  private SearchableComboPopup<String> setBoxSearchPopup;
  private SearchableComboPopup<String> presetBoxSearchPopup;
  private SearchableComboPopup<String> shortformBoxSearchPopup;
  private double sidebarDividerPosition = DEFAULT_SIDEBAR_DIVIDER;
  private boolean sidebarCollapsed;
  private StackPane previewHost;
  private ScrollPane sidebarScroll;

  public LayeredImageVisualizerView() {
    this(DEFAULT_TOOL_TITLE, DEFAULT_STATE_FILE, false);
  }

  protected LayeredImageVisualizerView(String toolTitle, String stateFile) {
    this(toolTitle, stateFile, true);
  }

  protected LayeredImageVisualizerView(String toolTitle, String stateFile, boolean presetControlsEnabled) {
    this.toolTitle = toolTitle == null || toolTitle.isBlank() ? DEFAULT_TOOL_TITLE : toolTitle.trim();
    this.stateFile = stateFile == null || stateFile.isBlank() ? DEFAULT_STATE_FILE : stateFile.trim();
    this.presetControlsEnabled = presetControlsEnabled;
    stateSaveDebounce.setOnFinished(e -> flushPendingStateSave());

    getStyleClass().add("sidebar-tool-root");
    setPadding(new Insets(8));

    Label title = new Label(this.toolTitle);
    title.getStyleClass().add("sidebar-tool-title");
    summaryLabel.getStyleClass().add("sidebar-tool-summary");
    summaryLabel.setWrapText(true);
    interactionHintLabel.getStyleClass().add("sidebar-tool-subtitle");
    interactionHintLabel.setWrapText(true);
    previewInfoLabel.getStyleClass().add("sidebar-tool-subtitle");
    groupStatsLabel.getStyleClass().add("sidebar-tool-subtitle");
    groupStatsLabel.setWrapText(true);
    statusLabel.getStyleClass().add("sidebar-tool-status");
    statusLabel.setWrapText(true);

    filterField.setPromptText("Filter sets...");
    filterField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshSetOptions();
    });
    filterField.setOnAction(e -> persistGlobalState());
    filterField.focusedProperty().addListener((o, ov, nv) -> {
      if (!nv) persistGlobalState();
    });
    characterAssetsOnlyScan.setSelected(true);
    characterAssetsOnlyScan.setTooltip(new Tooltip("Scan character asset folders by default; disable to include all images."));
    characterAssetsOnlyScan.selectedProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      persistGlobalState();
      refreshCatalog();
    });

    setBox.setPromptText("Select layered set");
    setBox.setConverter(new StringConverter<>() {
      @Override public String toString(String object) { return object == null ? "" : object; }
      @Override public String fromString(String string) { return string; }
    });
    setBoxSearchPopup = installSearchableComboPopup(
        setBox,
        searchableComboAdapter(
            value -> value == null ? "" : value,
            value -> value == null ? "" : value,
            value -> value == null ? null : value,
            (value, query) -> matchesSearchableText(value, query)));
    setBox.setOnAction(e -> onSetSelectionChanged());

    refreshCatalogButton = iconButton(CssIcon.redo("#7ec8e3"), "Refresh set scan", this::onCatalogRefreshRequested);
    updateRefreshButtonUi(false);
    Button newSetButton = iconButton(CssIcon.plus("#9ed67a"), "Create a new layered set from scratch", this::showNewSetDialog);

    HBox setRow = new HBox(4, new Label("Set"), setBox, refreshCatalogButton, newSetButton);
    setRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setBox, Priority.ALWAYS);

    HBox filterRow = new HBox(4, new Label("Filter"), filterField, characterAssetsOnlyScan);
    filterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);

    VBox setSection = new VBox(4, filterRow, setRow);
    if (presetControlsEnabled) {
      presetBox.setPromptText("Preset");
      presetBoxSearchPopup = installSearchableComboPopup(
          presetBox,
          searchableComboAdapter(
              value -> value == null ? "" : value,
              value -> value == null ? "" : value,
              value -> value == null ? null : value,
              (value, query) -> matchesSearchableText(value, query)));
      HBox.setHgrow(presetBox, Priority.ALWAYS);

      Button loadPresetButton = iconButton(CssIcon.download("#8ab4f8"), "Load selected preset", this::loadSelectedPreset);
      Button deletePresetButton = iconButton(CssIcon.clearX("#f38ba8"), "Delete selected preset", this::deleteSelectedPreset);
      HBox presetLoadRow = new HBox(4, new Label("Preset"), presetBox, loadPresetButton, deletePresetButton);
      presetLoadRow.setAlignment(Pos.CENTER_LEFT);

      presetNameField.setPromptText("Preset name");
      HBox.setHgrow(presetNameField, Priority.ALWAYS);
      Button savePresetButton = iconButton(CssIcon.save("#9ed67a"), "Save preset", this::savePreset);
      HBox presetSaveRow = new HBox(4, new Label("Name"), presetNameField, savePresetButton);
      presetSaveRow.setAlignment(Pos.CENTER_LEFT);

      setSection.getChildren().addAll(presetLoadRow, presetSaveRow);
    }
    TitledPane setPane = new TitledPane("Set & Presets", setSection);
    setPane.setExpanded(true);
    setPane.setAnimated(false);
    setPane.setCollapsible(true);

    // Preview pane — fills center
    StackPane previewPane = new StackPane(previewCanvas);
    previewPane.setStyle("-fx-background-color: #161616; -fx-border-color: #333333; -fx-border-radius: 4; -fx-background-radius: 4;");
    previewPane.widthProperty().addListener((o, ov, nv) -> {
      previewCanvas.setWidth(Math.max(120, nv.doubleValue() - 4));
      redrawPreview();
    });
    previewPane.heightProperty().addListener((o, ov, nv) -> {
      previewCanvas.setHeight(Math.max(120, nv.doubleValue() - 4));
      redrawPreview();
    });

    installSlider(focusXSlider);
    installSlider(focusYSlider);
    installSlider(cropSlider);
    installSlider(zoomSlider);
    installViewportInteractions(previewCanvas);

    autoExpression.setSelected(true);
    autoExpression.selectedProperty().addListener((o, ov, nv) -> {
      if (Boolean.TRUE.equals(nv)) updateExpressionFromSelection();
      if (!applyingState) persistCurrentSetState();
    });

    randomizeActiveOnly.setSelected(true);
    randomizeActiveOnly.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistCurrentSetState();
    });

    matchGameFraming.setTooltip(new Tooltip("Use runtime VN character framing (characterHeightFactor / characterBaselineY)"));
    matchGameFraming.selectedProperty().addListener((o, ov, nv) -> {
      updateViewportControlState();
      redrawPreview();
      if (!applyingState) persistCurrentSetState();
    });

    showOverlayGuides.setSelected(true);
    showOverlayGuides.setTooltip(new Tooltip("Toggle crosshair and bounding-box overlay on preview"));
    showOverlayGuides.selectedProperty().addListener((o, ov, nv) -> {
      redrawPreview();
      if (!applyingState) persistCurrentSetState();
    });
    customExportNameCheck.selectedProperty().addListener((o, ov, nv) -> {
      updateExportControls();
      if (!applyingState) persistCurrentSetState();
    });
    exportNameField.setPromptText("Auto from tag / expression");
    exportNameField.textProperty().addListener((o, ov, nv) -> {
      updateExportControls();
      if (!applyingState) persistCurrentSetState();
    });
    exportDirectoryField.setEditable(false);
    exportDirectoryField.setFocusTraversable(false);
    exportDirectoryField.setPromptText("Project root");
    exportInfoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9a9a9a;");
    exportInfoLabel.setWrapText(true);

    characterIdField.setPromptText("Image tag");
    expressionField.setPromptText("Expression id");
    characterIdField.textProperty().addListener((o, ov, nv) -> {
      updateExportControls();
      if (!applyingState) persistCurrentSetState();
    });
    characterIdField.setOnAction(e -> syncSetFromImageTag());
    characterIdField.focusedProperty().addListener((o, ov, focused) -> {
      if (!focused && !applyingState) syncSetFromImageTag();
    });
    expressionField.textProperty().addListener((o, ov, nv) -> {
      updateExportControls();
      if (!applyingState) persistCurrentSetState();
    });

    HBox idRow = new HBox(4, new Label("Tag"), characterIdField, new Label("Expr"), expressionField);
    idRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(characterIdField, Priority.ALWAYS);
    HBox.setHgrow(expressionField, Priority.ALWAYS);

    Button copyShowAttrsButton = iconButton(CssIcon.copy("#9ad19c"), "Copy: show <tag> <attributes>", () -> copyTagAttributes(true, true));
    Button copyAttrsOnlyButton = iconButton(CssIcon.copy("#d6b4ff"), "Copy: <attributes> only", () -> copyTagAttributes(false, false));
    HBox attrCopyRow = new HBox(4, new Label("Attrs"), copyShowAttrsButton, copyAttrsOnlyButton);
    attrCopyRow.setAlignment(Pos.CENTER_LEFT);

    // Snippet export
    snippetFormatBox.getItems().setAll(
        SNIPPET_COMBINED,
        SNIPPET_CHARIMG,
        SNIPPET_CHARPRESET_COMBINED,
        SNIPPET_CHARPRESET,
        SNIPPET_INLINE_SHOW,
        SNIPPET_SHOW,
        SNIPPET_RECIPE);
    snippetFormatBox.getSelectionModel().select(SNIPPET_COMBINED);
    HBox.setHgrow(snippetFormatBox, Priority.ALWAYS);

    Button copySnippetButton = iconButton(CssIcon.copy("#9ad19c"), "Copy selected snippet format", this::copySnippet);
    Button copyRecipeButton = iconButton(CssIcon.copy("#d6b4ff"), "Copy detailed layer recipe comments", this::copyLayerRecipe);
    Button exportCharpresetBtn = iconButton(CssIcon.copy("#c6a0f6"), "Copy @charpreset snippet to clipboard", this::copyCharpresetSnippet);

    HBox snippetRow = new HBox(4, new Label("Snippet"), snippetFormatBox, copySnippetButton, copyRecipeButton, exportCharpresetBtn);
    snippetRow.setAlignment(Pos.CENTER_LEFT);

    // Action row
    Button randomizeButton = iconButton(CssIcon.sort("#f0b673"), "Randomize layer choices", this::randomizeSelection);
    Button defaultsButton = iconButton(CssIcon.home("#9ed67a"), "Restore default first option per group", this::applyDefaultSelection);
    Button noneButton = iconButton(CssIcon.clearX("#f38ba8"), "Clear all selected layers", this::applyNoneSelection);
    Button swapPrevButton = iconButton(CssIcon.undo("#8ab4f8"), "Swap previous on marked groups", () -> swapMarkedGroups(-1));
    Button swapNextButton = iconButton(CssIcon.redo("#8ab4f8"), "Swap next on marked groups", () -> swapMarkedGroups(1));
    Button resetViewButton = iconButton(CssIcon.expand("#8ab4f8"), "Reset preview focus and zoom", () -> {
      resetViewportControls(true);
    });
    fullscreenButton = iconButton(CssIcon.expand("#f5c46b"), "Fullscreen this panel in the current editor window", this::requestFullscreenToggle);
    updateFullscreenButtonUi();

    HBox toolRow = new HBox(
        4,
        randomizeButton,
        defaultsButton,
        noneButton,
        swapPrevButton,
        swapNextButton,
        resetViewButton,
        fullscreenButton,
        randomizeActiveOnly);
    toolRow.setAlignment(Pos.CENTER_LEFT);

    Button chooseExportFolderButton = iconButton(CssIcon.folder("#f5c46b"), "Choose export folder", this::chooseExportDirectory);
    Button revealExportFolderButton = iconButton(CssIcon.link("#9cc7ff"), "Reveal export folder in file manager", this::revealExportDirectory);
    Button exportPngButton = actionButton("PNG", CssIcon.download("#8ab4f8"), "Quick export composited PNG to the configured folder", this::quickExportCompositePng);
    Button exportPngAsButton = actionButton("PNG As", CssIcon.save("#8ab4f8"), "Choose a PNG destination", this::exportCompositePngAs);
    Button exportSetupBtn = actionButton("Setup", CssIcon.save("#9ed67a"), "Quick export .layersetup to the configured folder", this::quickExportSetupToFile);
    Button exportSetupAsButton = actionButton("Setup As", CssIcon.download("#9ed67a"), "Choose a .layersetup destination", this::exportSetupToFileAs);
    Button importSetupBtn = actionButton("Import", CssIcon.folder("#f5c46b"), "Import setup from .layersetup file", this::importSetupFromFile);
    Button exportBundleButton = actionButton("PNG + Setup", CssIcon.download("#d8c48a"), "Quick export both PNG and .layersetup to the configured folder", this::quickExportBundle);
    HBox exportDirRow = new HBox(4, new Label("Folder"), exportDirectoryField, chooseExportFolderButton, revealExportFolderButton);
    exportDirRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportDirectoryField, Priority.ALWAYS);
    HBox exportNameRow = new HBox(4, customExportNameCheck, exportNameField);
    exportNameRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportNameField, Priority.ALWAYS);
    HBox exportBundleRow = new HBox(4, exportBundleButton);
    exportBundleRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportBundleButton, Priority.ALWAYS);
    HBox filePngRow = new HBox(4, exportPngButton, exportPngAsButton);
    filePngRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportPngButton, Priority.ALWAYS);
    HBox.setHgrow(exportPngAsButton, Priority.ALWAYS);
    HBox fileSetupRow = new HBox(4, exportSetupBtn, exportSetupAsButton, importSetupBtn);
    fileSetupRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportSetupBtn, Priority.ALWAYS);
    HBox.setHgrow(exportSetupAsButton, Priority.ALWAYS);
    HBox.setHgrow(importSetupBtn, Priority.ALWAYS);
    TitledPane exportPane = new TitledPane(
        "Export",
        new VBox(4, exportDirRow, exportNameRow, exportInfoLabel, exportBundleRow, filePngRow, fileSetupRow));
    exportPane.setExpanded(true);
    exportPane.setAnimated(false);
    exportPane.setCollapsible(true);

    HBox framingRow = new HBox(4, matchGameFraming, showOverlayGuides);
    framingRow.setAlignment(Pos.CENTER_LEFT);

    VBox controls = new VBox(
        6,
        sliderRow("Focus X", focusXSlider),
        sliderRow("Focus Y", focusYSlider),
        sliderRow("Crop", cropSlider),
        sliderRow("Zoom", zoomSlider));
    TitledPane viewControlsPane = new TitledPane("View controls", controls);
    viewControlsPane.setExpanded(false);
    viewControlsPane.setAnimated(false);
    viewControlsPane.setCollapsible(true);

    VBox scriptRoot = new VBox(4, idRow, autoExpression, attrCopyRow, snippetRow);
    TitledPane scriptPane = new TitledPane("Script controls", scriptRoot);
    scriptPane.setExpanded(false);
    scriptPane.setAnimated(false);
    scriptPane.setCollapsible(true);

    VBox actionsRoot = new VBox(4, toolRow, framingRow);
    TitledPane actionsPane = new TitledPane("Actions", actionsRoot);
    actionsPane.setExpanded(true);
    actionsPane.setAnimated(false);
    actionsPane.setCollapsible(true);

    // ── Layer groups section ──
    Label groupsLabel = new Label("Layer Groups  ·  top = behind  ·  bottom = in front");
    groupsLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");
    Button groupsOrderHelp = helpButton("Layer Render Order", """
        Groups are drawn from top to bottom — the group at the TOP of the list is drawn \
        first and appears behind all other groups. The group at the BOTTOM is drawn last \
        and appears on top of everything else.

        Think of it like a stack of transparent sheets:
          sheet 1 (top of list)    ← furthest back, behind all others
          sheet 2
          sheet 3
          sheet N (bottom of list) ← frontmost, on top of all others

        ── Typical character layer order (top → bottom) ──

          1. bg / background      drawn first → behind character
          2. shadow               under the character
          3. arm_behind           the arm that goes behind the torso
          4. tail / wing_behind   body parts that sit behind the torso
          5. body / torso         the main body/clothing
          6. arm_front            arm in front of the torso
          7. face / blush         face overlays
          8. eyes_base            whites of the eyes
          9. eyes / iris          coloured irises, pupils
         10. mouth                mouth/expression
         11. hair                 hair on top of face
         12. accessories          hats, glasses, etc. — topmost

        ── Common mistakes ──

        • Putting tail at the BOTTOM of the list will render it on top of the body.
          Move it ABOVE body so body covers it.

        • Putting hair ABOVE eyes means eyes will render on top of hair.
          Hair should be below eyes in the list only if eyes peek through hair.

        Use the ↑/↓ arrows on each group, or open the Layer Order tab for drag-and-drop reordering.""");
    HBox groupsLabelRow = new HBox(4, groupsLabel, groupsOrderHelp);
    groupsLabelRow.setAlignment(Pos.CENTER_LEFT);

    Button activeAllButton = iconButton(CssIcon.check("#9ed67a"), "Mark all groups active for randomization", () -> setAllGroupsActive(true));
    Button activeNoneButton = iconButton(CssIcon.minus("#f0b673"), "Mark all groups inactive for randomization", () -> setAllGroupsActive(false));
    Button swapAllButton = iconButton(CssIcon.check("#8ab4f8"), "Mark all groups for swap", () -> setAllSwapGroups(true));
    Button swapNoneButton = iconButton(CssIcon.minus("#8ab4f8"), "Clear swap marks", () -> setAllSwapGroups(false));
    Button collapseAllButton = new Button("▶▶");
    collapseAllButton.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #888; -fx-font-size: 9px; -fx-padding: 2 6; -fx-background-radius: 3;");
    collapseAllButton.setTooltip(new Tooltip("Collapse all groups"));
    collapseAllButton.setFocusTraversable(false);
    collapseAllButton.setOnAction(e -> setAllGroupsCollapsed(true));
    Button expandAllButton = new Button("▼▼");
    expandAllButton.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #888; -fx-font-size: 9px; -fx-padding: 2 6; -fx-background-radius: 3;");
    expandAllButton.setTooltip(new Tooltip("Expand all groups"));
    expandAllButton.setFocusTraversable(false);
    expandAllButton.setOnAction(e -> setAllGroupsCollapsed(false));
    HBox groupTools = new HBox(4, activeAllButton, activeNoneButton, swapAllButton, swapNoneButton, collapseAllButton, expandAllButton);
    groupTools.setAlignment(Pos.CENTER_LEFT);

    attributeFilterField.setPromptText("Filter attributes/groups...");
    attributeFilterField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) {
        refreshGroupRows();
        persistCurrentSetState();
      }
    });
    HBox filterRowAttrs = new HBox(4, new Label("Filter"), attributeFilterField);
    filterRowAttrs.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(attributeFilterField, Priority.ALWAYS);

    VBox groupsRoot = new VBox(4, filterRowAttrs, groupsLabelRow, groupStatsLabel, groupTools, groupBox);
    groupsRoot.setPadding(new Insets(2));
    ScrollPane groupsScroll = new ScrollPane(groupsRoot);
    groupsScroll.setFitToWidth(true);
    groupsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    groupsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

    typedAttributesField.setPromptText("eyes=angry mouth=smile or eyes_angry mouth_smile");
    typedAttributesField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState || !typedRealtime.isSelected()) return;
      applyTypedAttributes(false);
    });
    typedRealtime.setSelected(true);
    typedRealtime.selectedProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistCurrentSetState();
    });
    Button applyTypedButton = iconButton(CssIcon.check("#9ed67a"), "Apply typed attributes", () -> applyTypedAttributes(true));
    HBox typedHeader = new HBox(4, typedRealtime, applyTypedButton);
    typedHeader.setAlignment(Pos.CENTER_LEFT);
    VBox typedRoot = new VBox(4, new Label("Type attributes to preview"), typedAttributesField, typedHeader);
    typedRoot.setPadding(new Insets(4));

    shortformsArea.setPrefRowCount(6);
    shortformsArea.setWrapText(false);
    shortformsArea.textProperty().addListener((o, ov, nv) -> {
      refreshShortforms();
      if (!applyingState) persistCurrentSetState();
    });
    shortformBox.setPromptText("Shortform");
    shortformBoxSearchPopup = installSearchableComboPopup(
        shortformBox,
        searchableComboAdapter(
            value -> value == null ? "" : value,
            value -> value == null ? "" : value,
            value -> value == null ? null : value,
            (value, query) -> matchesSearchableText(value, query)));
    HBox.setHgrow(shortformBox, Priority.ALWAYS);
    Button applyShortformButton = iconButton(CssIcon.check("#9ed67a"), "Apply selected shortform", this::applySelectedShortform);
    Button copyShortformButton = iconButton(CssIcon.copy("#d6b4ff"), "Copy selected shortform expression", this::copySelectedShortform);
    HBox shortformRow = new HBox(4, shortformBox, applyShortformButton, copyShortformButton);
    shortformRow.setAlignment(Pos.CENTER_LEFT);
    VBox shortformsRoot = new VBox(4, new Label("Format: name = attribute expression"), shortformsArea, shortformRow);
    shortformsRoot.setPadding(new Insets(4));

    TabPane groupsTabs = new TabPane();
    groupsTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    Tab attributesTab = new Tab("Attributes", groupsScroll);
    Tab typedTab = new Tab("Typed", typedRoot);
    Tab shortformsTab = new Tab("Shortforms", shortformsRoot);
    Tab layerOrderTab = new Tab("Layer Order", buildLayerOrderPanel());
    groupsTabs.getTabs().addAll(attributesTab, typedTab, shortformsTab, layerOrderTab);

    // ── Gallery section ──
    galleryPane.setPadding(new Insets(4));
    galleryPane.setStyle("-fx-background-color: #161616;");
    galleryStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9a9a9a;");
    galleryStatusLabel.setWrapText(true);
    Button generateGalleryButton = iconButton(CssIcon.sort("#8ab4f8"), "Generate all charpreset combinations", this::generateGallery);
    Button clearGalleryButton = iconButton(CssIcon.clearX("#f38ba8"), "Clear gallery", this::clearGallery);
    HBox galleryToolRow = new HBox(4, new Label("Gallery"), generateGalleryButton, clearGalleryButton, galleryStatusLabel);
    galleryToolRow.setAlignment(Pos.CENTER_LEFT);
    VBox galleryRoot = new VBox(4, galleryToolRow, galleryPane);
    TitledPane galleryTitledPane = new TitledPane("Charpreset Gallery", galleryRoot);
    galleryTitledPane.setExpanded(false);
    galleryTitledPane.setAnimated(false);
    galleryTitledPane.setCollapsible(true);

    sidebarHideButton = iconButton(CssIcon.arrowRight("#d0d0d0"), "Hide controls sidebar", () -> setSidebarCollapsed(true, true));
    sidebarHideButton.getStyleClass().add("image-tool-sidebar-toggle");
    Region sidebarHeaderSpacer = new Region();
    HBox.setHgrow(sidebarHeaderSpacer, Priority.ALWAYS);
    HBox sidebarHeader = new HBox(8, title, sidebarHeaderSpacer, sidebarHideButton);
    sidebarHeader.setAlignment(Pos.CENTER_LEFT);
    sidebarHeader.getStyleClass().add("sidebar-tool-header");

    // ── Right sidebar ──
    VBox sidebar = new VBox(6,
        sidebarHeader, summaryLabel,
        setPane,
        exportPane,
        actionsPane,
        viewControlsPane,
        scriptPane,
        groupsTabs,
        galleryTitledPane,
        previewInfoLabel, interactionHintLabel,
        statusLabel);
    sidebar.setPadding(new Insets(6));
    sidebar.setStyle("-fx-font-size: 11px;");

    sidebarScroll = new ScrollPane(sidebar);
    sidebarScroll.setFitToWidth(true);
    sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    sidebarScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    sidebarScroll.getStyleClass().add("image-tool-sidebar-scroll");
    sidebarScroll.setPrefWidth(320);
    sidebarScroll.setMinWidth(0);

    sidebarShowButton = iconButton(CssIcon.arrowLeft("#d0d0d0"), "Show controls sidebar", () -> setSidebarCollapsed(false, true));
    sidebarShowButton.getStyleClass().addAll("image-tool-sidebar-toggle", "image-tool-sidebar-overlay-toggle");
    sidebarShowButton.setManaged(false);
    sidebarShowButton.setVisible(false);

    previewHost = new StackPane(previewPane, sidebarShowButton);
    previewHost.getStyleClass().add("image-tool-preview-host");
    previewHost.setAlignment(Pos.TOP_RIGHT);
    previewHost.setMinWidth(0);
    StackPane.setAlignment(sidebarShowButton, Pos.TOP_RIGHT);
    StackPane.setMargin(sidebarShowButton, new Insets(10));

    workspaceSplit.getItems().setAll(previewHost, sidebarScroll);
    workspaceSplit.getStyleClass().add("image-tool-workspace");
    workspaceSplit.setDividerPositions(DEFAULT_SIDEBAR_DIVIDER);
    SplitPane.setResizableWithParent(previewHost, true);
    SplitPane.setResizableWithParent(sidebarScroll, true);

    setCenter(workspaceSplit);
    updateViewportControlState();
    refreshShortforms();
    updateExportControls();
    redrawPreview();
  }

  public void setProjectRoot(File projectRoot) {
    if (disposed) return;
    if (Objects.equals(this.projectRoot, projectRoot)) return;
    persistCurrentSetState();
    persistGlobalState();
    this.projectRoot = projectRoot;
    refreshCatalog();
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    stateSaveDebounce.stop();
    hideAllSearchablePopups();
    Task<LayeredCatalogScanResult> task = scanTask;
    scanTask = null;
    if (task != null && task.isRunning()) {
      task.cancel();
    }
    updateRefreshButtonUi(false);
    clearCatalogUi("Layered visualizer disposed.");
    projectRoot = null;
    selectedGalleryCell = null;
    selectedGalleryLabel = null;
    dragCanvas = null;
  }

  public void setOnToggleFullscreen(Runnable handler) {
    fullscreenToggleHandler = handler;
  }

  public void setFullscreenActive(boolean active) {
    if (fullscreenActive == active) return;
    fullscreenActive = active;
    updateFullscreenButtonUi();
  }

  private void onCatalogRefreshRequested() {
    if (scanTask != null && scanTask.isRunning()) {
      scanTask.cancel();
      status("Cancelling scan...");
      return;
    }
    refreshCatalog();
  }

  private void updateRefreshButtonUi(boolean scanning) {
    if (refreshCatalogButton == null) return;
    if (scanning) {
      refreshCatalogButton.setGraphic(CssIcon.clearX("#f38ba8"));
      refreshCatalogButton.setTooltip(new Tooltip("Cancel set scan"));
    } else {
      refreshCatalogButton.setGraphic(CssIcon.redo("#7ec8e3"));
      refreshCatalogButton.setTooltip(new Tooltip("Refresh set scan"));
    }
  }

  private void clearCatalogUi(String message) {
    currentSetId = null;
    clearSelectorSearchPopups();
    hidePersistentSearchPopups();
    sets.clear();
    selectors.clear();
    activeGroupChecks.clear();
    swapGroupChecks.clear();
    groupRows.clear();
    groupTileNodes.clear();
    groupTilePanes.clear();
    groupCollapseBtns.clear();
    groupHeaderLabels.clear();
    groupOrder.clear();
    groupBox.getChildren().clear();
    imageCache.clear();
    viewportFrames.clear();
    presetNameToKey.clear();
    presetBox.getItems().clear();
    setBox.getItems().clear();
    summaryLabel.setText(message);
    updateGroupStats();
    redrawPreview();
  }

  public void refreshCatalog() {
    if (disposed) return;
    persistCurrentSetState();

    loadPersistentState();
    reloadGameFramingSettings();
    preferredSetId = persisted.getProperty("global.selectedSet", "");

    applyingState = true;
    filterField.setText(persisted.getProperty("global.filter", ""));
    characterAssetsOnlyScan.setSelected(parseBoolean(persisted.getProperty("global.charactersOnly"), true));
    sidebarDividerPosition = clampSidebarDivider(
        parseDouble(persisted.getProperty("global.sidebarDivider"), DEFAULT_SIDEBAR_DIVIDER),
        DEFAULT_SIDEBAR_DIVIDER);
    sidebarCollapsed = parseBoolean(persisted.getProperty("global.sidebarCollapsed"), false);
    applyingState = false;
    setSidebarCollapsed(sidebarCollapsed, false);
    final boolean charactersOnlyMode = characterAssetsOnlyScan.isSelected();

    if (scanTask != null && scanTask.isRunning()) {
      scanTask.cancel();
    }
    updateRefreshButtonUi(false);

    if (projectRoot == null || !projectRoot.isDirectory()) {
      clearCatalogUi("Open a project to inspect layered image sets.");
      return;
    }

    File rootDir = projectRoot;
    clearCatalogUi("Scanning layered image sets...");
    status("Scanning assets...");
    updateRefreshButtonUi(true);

    Task<LayeredCatalogScanResult> task = new Task<>() {
      @Override
      protected LayeredCatalogScanResult call() throws Exception {
        Map<String, LayeredSet> scannedSets = new LinkedHashMap<>();
        LayeredCharacterProjectCatalog.Catalog projectCatalog = LayeredCharacterProjectCatalog.load(rootDir);
        Map<String, LayeredSet> declaredSets = buildDeclaredLayeredSets(rootDir, projectCatalog);
        Path root = rootDir.toPath();
        List<Path> files;
        try (Stream<Path> stream = Files.walk(root, 10)) {
          files = stream
              .filter(Files::isRegularFile)
              .filter(LayeredImageVisualizerView.this::isImageFile)
              .filter(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (isIgnoredPath(relative)) return false;
                return shouldIncludePathForScan(relative, charactersOnlyMode);
              })
              .sorted()
              .toList();
        }
        int imageCount = countLayerOptions(declaredSets);
        int total = files.size();
        int index = 0;
        for (Path p : files) {
          if (isCancelled()) return LayeredCatalogScanResult.cancelledResult(charactersOnlyMode);
          String relative = root.relativize(p).toString().replace('\\', '/');
          String setId = deriveSetId(relative);
          if (declaredSets.containsKey(setId)) {
            index++;
            if (index == total || (index % 200) == 0) {
              updateProgress(index, Math.max(total, 1));
            }
            continue;
          }
          LayerOption option = parseOption(relative, p.toFile());
          if (option != null) {
            scannedSets.computeIfAbsent(setId, LayeredSet::new).add(option);
            imageCount++;
          }
          index++;
          if (index == total || (index % 200) == 0) {
            updateProgress(index, Math.max(total, 1));
          }
        }
        for (LayeredSet declaredSet : declaredSets.values()) {
          scannedSets.put(declaredSet.id, declaredSet);
        }
        return new LayeredCatalogScanResult(scannedSets, imageCount, false, charactersOnlyMode);
      }
    };
    scanTask = task;

    task.setOnSucceeded(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      LayeredCatalogScanResult result = task.getValue();
      if (result == null || result.cancelled()) {
        summaryLabel.setText("Layered set scan cancelled.");
        status("Scan cancelled.");
        return;
      }
      sets.clear();
      sets.putAll(result.sets());
      if (result.charactersOnly() && result.imageCount() == 0) {
        summaryLabel.setText("No character assets found. Disable Characters only to scan all assets.");
      } else {
        summaryLabel.setText(
            "Layer sets: " + sets.size() + "  |  Images: " + result.imageCount()
                + (result.charactersOnly() ? "  |  Mode: Characters only" : "  |  Mode: All assets"));
      }
      refreshSetOptions();
      status(result.charactersOnly() ? "Scan complete (characters only)." : "Scan complete.");
    });

    task.setOnCancelled(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      summaryLabel.setText("Layered set scan cancelled.");
      status("Scan cancelled.");
    });

    task.setOnFailed(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      Throwable ex = task.getException();
      clearCatalogUi("Failed to scan project assets: " + (ex == null ? "Unknown error" : ex.getMessage()));
      status("Scan failed.");
    });

    Thread scanThread = new Thread(task, "jvn-layered-set-scan");
    scanThread.setDaemon(true);
    scanThread.start();
  }

  private void refreshSetOptions() {
    String filter = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
    String previous = setBox.getValue();

    List<String> visible = new ArrayList<>();
    for (LayeredSet set : sets.values()) {
      if (filter.isEmpty() || set.id.toLowerCase(Locale.ROOT).contains(filter)) {
        visible.add(set.id);
      }
    }

    setBox.getItems().setAll(visible);
    refreshVisibleSearchablePopup(setBoxSearchPopup);

    Map<String, Integer> groupCounts = new HashMap<>();
    for (String setId : visible) {
      LayeredSet set = sets.get(setId);
      groupCounts.put(setId, set == null ? 0 : set.groups.size());
    }
    String target = chooseSetSelection(previous, preferredSetId, visible, groupCounts);

    if (target != null) {
      applyingState = true;
      setBox.getSelectionModel().select(target);
      applyingState = false;
      onSetSelectionChanged();
    } else {
      currentSetId = null;
      selectors.clear();
      activeGroupChecks.clear();
      swapGroupChecks.clear();
      groupRows.clear();
      groupTileNodes.clear();
      groupTilePanes.clear();
      groupCollapseBtns.clear();
      groupHeaderLabels.clear();
      groupOrder.clear();
      groupBox.getChildren().clear();
      presetNameToKey.clear();
      presetBox.getItems().clear();
      updateGroupStats();
      redrawPreview();
    }
  }

  private void setSidebarCollapsed(boolean collapsed, boolean persist) {
    if (workspaceSplit == null || previewHost == null || sidebarScroll == null) return;
    if (collapsed) {
      captureSidebarDividerPosition();
      if (workspaceSplit.getItems().size() != 1 || workspaceSplit.getItems().get(0) != previewHost) {
        workspaceSplit.getItems().setAll(previewHost);
      }
      sidebarCollapsed = true;
    } else {
      if (workspaceSplit.getItems().size() != 2 || workspaceSplit.getItems().get(1) != sidebarScroll) {
        workspaceSplit.getItems().setAll(previewHost, sidebarScroll);
      }
      sidebarCollapsed = false;
      double divider = clampSidebarDivider(sidebarDividerPosition, DEFAULT_SIDEBAR_DIVIDER);
      Platform.runLater(() -> workspaceSplit.setDividerPositions(divider));
    }
    updateSidebarToggleButtons();
    if (persist && !applyingState) {
      persistGlobalState();
    }
  }

  private void updateSidebarToggleButtons() {
    boolean collapsed = sidebarCollapsed;
    if (sidebarHideButton != null) {
      sidebarHideButton.setManaged(!collapsed);
      sidebarHideButton.setVisible(!collapsed);
    }
    if (sidebarShowButton != null) {
      sidebarShowButton.setManaged(collapsed);
      sidebarShowButton.setVisible(collapsed);
    }
  }

  private void captureSidebarDividerPosition() {
    if (workspaceSplit == null || workspaceSplit.getItems().size() < 2 || workspaceSplit.getDividers().isEmpty()) return;
    sidebarDividerPosition = clampSidebarDivider(workspaceSplit.getDividerPositions()[0], DEFAULT_SIDEBAR_DIVIDER);
  }

  private static double clampSidebarDivider(double value, double fallback) {
    if (!Double.isFinite(value)) return fallback;
    return Math.max(0.35, Math.min(0.90, value));
  }

  private void hideAllSearchablePopups() {
    clearSelectorSearchPopups();
    hidePersistentSearchPopups();
  }

  private void clearSelectorSearchPopups() {
    selectorSearchPopups.values().forEach(SearchableComboPopup::hide);
    selectorSearchPopups.clear();
  }

  private void hidePersistentSearchPopups() {
    if (setBoxSearchPopup != null) setBoxSearchPopup.hide();
    if (presetBoxSearchPopup != null) presetBoxSearchPopup.hide();
    if (shortformBoxSearchPopup != null) shortformBoxSearchPopup.hide();
  }

  private void refreshVisibleSearchablePopup(SearchableComboPopup<?> popup) {
    if (popup != null && popup.isShowing()) {
      popup.refreshFromItems();
    }
  }

  private static <T> SearchableComboAdapter<T> searchableComboAdapter(
      Function<T, String> buttonText,
      Function<T, String> popupText,
      Function<T, String> tooltipText,
      BiPredicate<T, String> matches
  ) {
    return new SearchableComboAdapter<>(buttonText, popupText, tooltipText, matches);
  }

  private <T> SearchableComboPopup<T> installSearchableComboPopup(ComboBox<T> box, SearchableComboAdapter<T> adapter) {
    if (box == null || adapter == null) return null;
    box.setEditable(false);
    box.setVisibleRowCount(14);
    box.setButtonCell(createSearchableComboCell(adapter, true));
    box.setCellFactory(list -> createSearchableComboCell(adapter, false));
    SearchableComboPopup<T> popup = new SearchableComboPopup<>(box, adapter);
    box.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      if (disposed || box.isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
      event.consume();
      box.requestFocus();
      Platform.runLater(() -> {
        if (!disposed && !box.isDisabled()) {
          toggleSearchablePopup(popup);
        }
      });
    });
    box.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
      if (disposed || box.isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
      event.consume();
    });
    box.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
      if (disposed || box.isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
      event.consume();
    });
    box.setOnShowing(event -> {
      event.consume();
      popup.hide();
    });
    box.setOnHidden(event -> popup.hide());
    box.showingProperty().addListener((obs, wasShowing, showing) -> {
      if (!showing || disposed) return;
      Platform.runLater(() -> {
        if (box.isShowing()) {
          box.hide();
        }
      });
    });
    box.setOnKeyPressed(event -> {
      if (disposed || box.isDisabled()) return;
      if (event.getCode() == KeyCode.DOWN
          || event.getCode() == KeyCode.UP
          || event.getCode() == KeyCode.SPACE
          || event.getCode() == KeyCode.ENTER
          || event.getCode() == KeyCode.F4) {
        event.consume();
        openSearchablePopup(popup);
      } else if (event.getCode() == KeyCode.ESCAPE) {
        popup.hide();
      }
    });
    return popup;
  }

  private void toggleSearchablePopup(SearchableComboPopup<?> popup) {
    if (popup == null) return;
    if (popup.isShowing()) {
      popup.hide();
      return;
    }
    openSearchablePopup(popup);
  }

  private void openSearchablePopup(SearchableComboPopup<?> popup) {
    if (popup == null || disposed) return;
    popup.show();
  }

  private <T> ListCell<T> createSearchableComboCell(SearchableComboAdapter<T> adapter, boolean buttonCell) {
    return new ListCell<>() {
      @Override
      protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setTooltip(null);
          return;
        }
        String text = buttonCell ? adapter.buttonText().apply(item) : adapter.popupText().apply(item);
        String tooltip = adapter.tooltipText().apply(item);
        setText(text == null || text.isBlank() ? null : text);
        setTooltip(tooltip == null || tooltip.isBlank() ? null : new Tooltip(tooltip));
      }
    };
  }

  static boolean matchesSearchableText(String text, String query) {
    String value = text == null ? "" : text.trim();
    String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (normalizedQuery.isBlank()) return true;
    String lower = value.toLowerCase(Locale.ROOT);
    if (lower.contains(normalizedQuery)) return true;
    String sanitizedValue = sanitizeId(value.replace('/', ' '));
    String sanitizedQuery = sanitizeId(normalizedQuery);
    return !sanitizedQuery.isBlank() && sanitizedValue.contains(sanitizedQuery);
  }

  private static String layerOptionButtonText(LayerOption option) {
    return option == null ? "" : option.label;
  }

  static String layerOptionPopupText(LayerOption option) {
    if (option == null) return "";
    if (option.isNone()) return option.label;
    return layerOptionPopupText(option.label, option.relativePath);
  }

  static String layerOptionPopupText(String label, String relativePath) {
    String normalizedLabel = label == null ? "" : label.trim();
    String normalizedPath = relativePath == null ? "" : relativePath.trim();
    if (normalizedLabel.isBlank() || normalizedLabel.equals(normalizedPath)) {
      return normalizedPath;
    }
    return normalizedLabel + "  ·  " + normalizedPath;
  }

  private static String layerOptionTooltipText(LayerOption option) {
    if (option == null) return null;
    return option.relativePath == null || option.relativePath.isBlank() ? option.label : option.relativePath;
  }

  static boolean matchesLayerOptionSearch(LayerOption option, String query) {
    if (option == null) return false;
    return matchesLayerOptionSearch(option.label, option.group, option.relativePath, query);
  }

  static boolean matchesLayerOptionSearch(String label, String group, String relativePath, String query) {
    String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (normalizedQuery.isBlank()) return true;
    return matchesSearchableText(label, normalizedQuery)
        || matchesSearchableText(relativePath, normalizedQuery)
        || matchesSearchableText(group, normalizedQuery)
        || matchesSearchableText(layerOptionPopupText(label, relativePath), normalizedQuery);
  }

  private static record SearchableComboAdapter<T>(
      Function<T, String> buttonText,
      Function<T, String> popupText,
      Function<T, String> tooltipText,
      BiPredicate<T, String> matches
  ) {}

  private final class SearchableComboPopup<T> {
    private final ComboBox<T> owner;
    private final SearchableComboAdapter<T> adapter;
    private final Popup popup = new Popup();
    private final VBox root = new VBox(6);
    private final TextField searchField = new TextField();
    private final ListView<T> listView = new ListView<>();
    private List<T> sourceItems = List.of();

    private SearchableComboPopup(ComboBox<T> owner, SearchableComboAdapter<T> adapter) {
      this.owner = owner;
      this.adapter = adapter;
      popup.setAutoHide(true);
      popup.setHideOnEscape(true);
      popup.setAutoFix(true);

      root.setPadding(new Insets(6));
      root.setMinWidth(220);
      root.setStyle(
          "-fx-background-color: #161616;"
              + "-fx-border-color: #3a3a3a;"
              + "-fx-border-radius: 4;"
              + "-fx-background-radius: 4;");
      searchField.setPromptText("Search...");
      listView.setCellFactory(list -> createSearchableComboCell(adapter, false));
      listView.setPlaceholder(new Label("No matching items"));
      VBox.setVgrow(listView, Priority.ALWAYS);
      root.getChildren().setAll(searchField, listView);
      popup.getContent().setAll(root);

      searchField.textProperty().addListener((o, ov, nv) -> applyFilter());
      searchField.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.DOWN) {
          if (!listView.getItems().isEmpty()) {
            listView.requestFocus();
            listView.getSelectionModel().select(Math.max(0, listView.getSelectionModel().getSelectedIndex()));
            listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
          }
          event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
          commitSelection();
          event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
          hide();
          owner.requestFocus();
          event.consume();
        }
      });

      listView.setOnMouseClicked(event -> {
        if (event.getButton() != MouseButton.PRIMARY) return;
        commitSelection();
        event.consume();
      });
      listView.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.ENTER) {
          commitSelection();
          event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
          hide();
          owner.requestFocus();
          event.consume();
        }
      });
    }

    private boolean isShowing() {
      return popup.isShowing();
    }

    private void show() {
      if (owner.isShowing()) {
        owner.hide();
      }
      sourceItems = new ArrayList<>(owner.getItems());
      searchField.clear();
      applyFilter();
      selectCurrentValue();

      Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());
      if (bounds == null) return;
      double width = Math.max(280.0, bounds.getWidth());
      root.setPrefWidth(width);
      root.setMaxWidth(width);
      searchField.setPrefWidth(width - 12);
      listView.setPrefWidth(width - 12);
      listView.setPrefHeight(Math.min(360.0, Math.max(160.0, listView.getItems().size() * 28.0 + 8.0)));

      popup.show(owner, bounds.getMinX(), bounds.getMaxY());
      Platform.runLater(searchField::requestFocus);
    }

    private void hide() {
      popup.hide();
    }

    private void refreshFromItems() {
      sourceItems = new ArrayList<>(owner.getItems());
      applyFilter();
      selectCurrentValue();
    }

    private void applyFilter() {
      List<T> filtered = new ArrayList<>();
      String query = searchField.getText();
      for (T item : sourceItems) {
        if (adapter.matches().test(item, query)) {
          filtered.add(item);
        }
      }
      listView.getItems().setAll(filtered);
      if (!filtered.isEmpty() && listView.getSelectionModel().isEmpty()) {
        listView.getSelectionModel().select(0);
      }
    }

    private void selectCurrentValue() {
      T current = owner.getValue();
      if (current == null) {
        if (!listView.getItems().isEmpty()) {
          listView.getSelectionModel().select(0);
          listView.scrollTo(0);
        }
        return;
      }
      int index = listView.getItems().indexOf(current);
      if (index >= 0) {
        listView.getSelectionModel().select(index);
        listView.scrollTo(index);
      } else if (!listView.getItems().isEmpty()) {
        listView.getSelectionModel().select(0);
        listView.scrollTo(0);
      }
    }

    private void commitSelection() {
      T selected = listView.getSelectionModel().getSelectedItem();
      if (selected == null) return;
      T previous = owner.getValue();
      owner.getSelectionModel().select(selected);
      if (!Objects.equals(previous, owner.getValue())) {
        owner.fireEvent(new ActionEvent());
      }
      hide();
      owner.requestFocus();
    }
  }

  private void onSetSelectionChanged() {
    String selectedSet = setBox.getValue();
    if (selectedSet == null || selectedSet.isBlank()) return;

    if (currentSetId != null && !currentSetId.equals(selectedSet)) {
      persistCurrentSetState();
    }

    currentSetId = selectedSet;

    clearSelectorSearchPopups();
    selectors.clear();
    activeGroupChecks.clear();
    swapGroupChecks.clear();
    groupRows.clear();
    groupTileNodes.clear();
    groupTilePanes.clear();
    groupCollapseBtns.clear();
    groupHeaderLabels.clear();
    groupOrder.clear();
    groupBox.getChildren().clear();
    imageCache.clear();

    LayeredSet set = sets.get(selectedSet);
    if (set == null || set.groups.isEmpty()) {
      redrawPreview();
      return;
    }

    List<String> groupNames = set.scriptBacked && !set.defaultGroupOrder.isEmpty()
        ? new ArrayList<>(set.defaultGroupOrder)
        : new ArrayList<>(set.groups.keySet());
    if (!set.scriptBacked) {
      groupNames.sort(Comparator.naturalOrder());
    }

    for (String groupName : groupNames) {
      List<LayerOption> options = new ArrayList<>(set.groups.get(groupName));
      options.sort(Comparator
          .comparingInt((LayerOption o) -> o.sortKey)
          .thenComparing(o -> o.label.toLowerCase(Locale.ROOT)));

      ComboBox<LayerOption> combo = new ComboBox<>();
      combo.setConverter(new StringConverter<>() {
        @Override public String toString(LayerOption object) {
          return object == null ? "" : object.label;
        }
        @Override public LayerOption fromString(String string) { return null; }
      });

      combo.getItems().add(LayerOption.none());
      combo.getItems().addAll(options);
      selectPreferredLayerOption(groupName, combo);
      combo.setMaxWidth(Double.MAX_VALUE);
      selectorSearchPopups.put(
          combo,
          installSearchableComboPopup(
              combo,
              searchableComboAdapter(
                  LayeredImageVisualizerView::layerOptionButtonText,
                  LayeredImageVisualizerView::layerOptionPopupText,
                  LayeredImageVisualizerView::layerOptionTooltipText,
                  LayeredImageVisualizerView::matchesLayerOptionSearch)));

      CheckBox activeCheck = new CheckBox();
      activeCheck.setSelected(true);
      activeCheck.setStyle("-fx-color: #3a7a4f;");
      activeCheck.setTooltip(new Tooltip("Include this group when randomizing with active-only mode"));
      activeCheck.selectedProperty().addListener((o, ov, nv) -> {
        if (applyingState) return;
        persistCurrentSetState();
      });

      CheckBox swapCheck = new CheckBox();
      swapCheck.setSelected(false);
      swapCheck.setStyle("-fx-color: #b07830;");
      swapCheck.setTooltip(new Tooltip("Mark this group for quick swapping"));
      swapCheck.selectedProperty().addListener((o, ov, nv) -> {
        if (applyingState) return;
        persistCurrentSetState();
      });

      Button prevButton = iconButton(CssIcon.arrowLeft("#8ab4f8"), "Cycle this group backward", () -> cycleGroupSelection(combo, -1));

      Button nextButton = iconButton(CssIcon.arrowRight("#8ab4f8"), "Cycle this group forward", () -> cycleGroupSelection(combo, 1));

      Button openButton = iconButton(CssIcon.folder("#7ec8e3"), "Open selected image in OS viewer", () -> openSelectedImage(combo.getValue()));

      Button upButton = iconButton(CssIcon.arrowUp("#b0b8c8"), "Move this group up in render order", () -> moveGroupInOrder(groupName, -1));

      Button downButton = iconButton(CssIcon.arrowDown("#b0b8c8"), "Move this group down in render order", () -> moveGroupInOrder(groupName, 1));

      boolean isBgGroup = isLikelyBackgroundGroupName(groupName);
      String labelColor = isBgGroup ? "-fx-text-fill: #f0b673;" : "-fx-text-fill: #d0d0d0;";
      Label groupLabel = new Label(groupName + " (" + options.size() + ")");
      groupLabel.setStyle(
          "-fx-font-family: 'Consolas', 'Menlo', 'DejaVu Sans Mono', monospace; "
          + "-fx-font-size: 11px; -fx-font-weight: 600; " + labelColor);
      if (isBgGroup) {
        groupLabel.setTooltip(new Tooltip("Background group — suppressed from snippet export when foreground layers are active"));
      }

      combo.valueProperty().addListener((o, ov, nv) -> {
        updateGroupTileStyles(groupName, groupTileNodes.get(groupName), nv);
        Label lbl = groupHeaderLabels.get(groupName);
        if (lbl != null) {
          String selName = (nv == null || nv.isNone()) ? "(none)"
              : (nv.layerId != null && !nv.layerId.isBlank() ? nv.layerId : nv.label);
          lbl.setText(groupName + " (" + options.size() + ")  ·  " + selName);
        }
        layerOrderList.refresh();
        if (applyingState) {
          redrawPreview();
          return;
        }
        updateExpressionFromSelection();
        redrawPreview();
        persistCurrentSetState();
      });

      Button collapseBtn = new Button("▼");
      collapseBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; -fx-font-size: 9px; -fx-padding: 2 4;");
      collapseBtn.setFocusTraversable(false);
      collapseBtn.setMinSize(22, 22);

      Region headerSpacer = new Region();
      HBox.setHgrow(headerSpacer, Priority.ALWAYS);
      HBox headerButtons = new HBox(3, prevButton, nextButton, openButton, upButton, downButton);
      headerButtons.setAlignment(Pos.CENTER_RIGHT);

      HBox headerRow = new HBox(4, collapseBtn, activeCheck, swapCheck, groupLabel, headerSpacer, headerButtons);
      headerRow.setAlignment(Pos.CENTER_LEFT);
      headerRow.setStyle("-fx-padding: 5 2 5 2;");

      // Thumbnail tile grid
      FlowPane tilesPane = new FlowPane(4, 4);
      tilesPane.setPadding(new Insets(2, 2, 6, 2));

      Map<LayerOption, Region> tileMap = new LinkedHashMap<>();
      LayerOption noneOption = combo.getItems().get(0);
      Region noneTile = buildLayerTile(noneOption);
      noneTile.setOnMouseClicked(e -> combo.getSelectionModel().select(noneOption));
      tileMap.put(noneOption, noneTile);
      tilesPane.getChildren().add(noneTile);

      for (LayerOption option : options) {
        Region tile = buildLayerTile(option);
        tile.setOnMouseClicked(e -> combo.getSelectionModel().select(option));
        tileMap.put(option, tile);
        tilesPane.getChildren().add(tile);
      }
      groupTileNodes.put(groupName, tileMap);
      groupTilePanes.put(groupName, tilesPane);
      groupCollapseBtns.put(groupName, collapseBtn);
      groupHeaderLabels.put(groupName, groupLabel);
      updateGroupTileStyles(groupName, tileMap, combo.getValue());

      collapseBtn.setOnAction(e -> {
        boolean visible = tilesPane.isManaged();
        tilesPane.setManaged(!visible);
        tilesPane.setVisible(!visible);
        collapseBtn.setText(!visible ? "▼" : "▶");
      });

      // Auto-collapse groups with many options so the panel doesn't overflow
      if (options.size() > 6) {
        tilesPane.setManaged(false);
        tilesPane.setVisible(false);
        collapseBtn.setText("▶");
      }

      combo.setManaged(false);
      combo.setVisible(false);

      VBox groupSection = new VBox(0, headerRow, tilesPane, combo);
      groupSection.setStyle("-fx-border-color: #282828; -fx-border-width: 0 0 1 0;");

      selectors.put(groupName, combo);
      activeGroupChecks.put(groupName, activeCheck);
      swapGroupChecks.put(groupName, swapCheck);
      groupRows.put(groupName, groupSection);
      groupOrder.add(groupName);
    }

    String prefix = statePrefix(selectedSet);
    boolean hasStoredSelection = hasStoredLayerSelections(prefix);
    applyStateFromPrefix(prefix);
    if (!hasStoredSelection) {
      applyDefaultProjectPreset(set);
    }
    refreshPresetList();

    persistGlobalState();
    persistCurrentSetState();
    updateExportControls();
    redrawPreview();
  }

  private void refreshGroupRows() {
    groupBox.getChildren().clear();
    String filter = sanitizeId(attributeFilterField.getText());
    for (String groupName : groupOrder) {
      if (!matchesAttributeFilter(groupName, filter)) continue;
      Region row = groupRows.get(groupName);
      if (row != null) groupBox.getChildren().add(row);
    }
    if (groupBox.getChildren().isEmpty()) {
      Label empty = new Label("No attributes match the current filter.");
      empty.setStyle("-fx-text-fill: rgba(255,255,255,0.7);");
      groupBox.getChildren().add(empty);
    }
    updateGroupStats();
    refreshLayerOrderList();
  }

  private Region buildLayerTile(LayerOption option) {
    StackPane imgContainer = new StackPane();
    imgContainer.setMinSize(64, 68);
    imgContainer.setMaxSize(64, 68);
    imgContainer.setStyle("-fx-background-color: #1a1a1a; -fx-background-radius: 3;");
    if (option == null || option.isNone()) {
      Label dash = new Label("—");
      dash.setStyle("-fx-text-fill: #444; -fx-font-size: 18px;");
      imgContainer.getChildren().add(dash);
    } else if (option.file != null && option.file.isFile()) {
      javafx.scene.image.Image img = imageCache.computeIfAbsent(
          option.relativePath + ":t",
          k -> new javafx.scene.image.Image(option.file.toURI().toString(), 64, 68, true, true, true));
      javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
      iv.setFitWidth(64);
      iv.setFitHeight(68);
      iv.setPreserveRatio(true);
      iv.setSmooth(true);
      imgContainer.getChildren().add(iv);
    } else {
      Label missing = new Label("?");
      missing.setStyle("-fx-text-fill: #555; -fx-font-size: 18px;");
      imgContainer.getChildren().add(missing);
    }
    String displayName = (option == null || option.isNone()) ? "(none)"
        : (option.layerId != null && !option.layerId.isBlank() ? option.layerId : option.label);
    Label nameLabel = new Label(displayName);
    nameLabel.setMaxWidth(72);
    nameLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #9a9a9a; -fx-alignment: center;");
    VBox tile = new VBox(2, imgContainer, nameLabel);
    tile.setAlignment(javafx.geometry.Pos.TOP_CENTER);
    tile.setCursor(javafx.scene.Cursor.HAND);
    tile.setStyle(TILE_NORMAL_STYLE);
    return tile;
  }

  private void updateGroupTileStyles(String groupName, Map<LayerOption, Region> tileMap, LayerOption selected) {
    if (tileMap == null) return;
    tileMap.forEach((opt, tile) -> {
      boolean match = opt == selected
          || (opt != null && selected != null
              && ((opt.isNone() && selected.isNone())
                  || (opt.file != null && selected.file != null
                      && Objects.equals(opt.relativePath, selected.relativePath))));
      tile.setStyle(match ? TILE_SELECTED_STYLE : TILE_NORMAL_STYLE);
    });
  }

  private void updateGroupStats() {
    if (groupOrder.isEmpty()) {
      groupStatsLabel.setText("No groups loaded.");
      return;
    }
    String filter = sanitizeId(attributeFilterField.getText());
    int visible = 0;
    int selected = 0;
    int active = 0;
    int swap = 0;
    for (String groupName : groupOrder) {
      if (matchesAttributeFilter(groupName, filter)) {
        visible++;
      }
      ComboBox<LayerOption> combo = selectors.get(groupName);
      LayerOption option = combo == null ? null : combo.getValue();
      if (option != null && !option.isNone()) {
        selected++;
      }
      CheckBox activeCheck = activeGroupChecks.get(groupName);
      if (activeCheck != null && activeCheck.isSelected()) {
        active++;
      }
      CheckBox swapCheck = swapGroupChecks.get(groupName);
      if (swapCheck != null && swapCheck.isSelected()) {
        swap++;
      }
    }
    groupStatsLabel.setText(
        "Total " + groupOrder.size()
            + "  |  Visible " + visible
            + "  |  Selected " + selected
            + "  |  Active " + active
            + "  |  Swap " + swap);
  }

  private void cycleGroupSelection(ComboBox<LayerOption> combo, int direction) {
    if (combo == null || combo.getItems().size() <= 1) return;
    int idx = combo.getSelectionModel().getSelectedIndex();
    if (idx < 1) idx = 0;
    int next = idx + (direction < 0 ? -1 : 1);
    if (next < 1) next = combo.getItems().size() - 1;
    if (next >= combo.getItems().size()) next = 1;
    combo.getSelectionModel().select(next);
  }

  private void setAllGroupsActive(boolean active) {
    for (CheckBox check : activeGroupChecks.values()) {
      check.setSelected(active);
    }
    persistCurrentSetState();
  }

  private void setAllSwapGroups(boolean active) {
    for (CheckBox check : swapGroupChecks.values()) {
      check.setSelected(active);
    }
    persistCurrentSetState();
  }

  private void setAllGroupsCollapsed(boolean collapsed) {
    groupTilePanes.forEach((name, pane) -> {
      pane.setManaged(!collapsed);
      pane.setVisible(!collapsed);
      Button btn = groupCollapseBtns.get(name);
      if (btn != null) btn.setText(collapsed ? "▶" : "▼");
    });
  }

  private VBox buildLayerOrderPanel() {
    layerOrderList.setStyle("-fx-background-color: #1a1a1a;");
    layerOrderList.setFocusTraversable(false);
    VBox.setVgrow(layerOrderList, Priority.ALWAYS);

    layerOrderList.setCellFactory(lv -> {
      ListCell<String> cell = new ListCell<>() {
        @Override
        protected void updateItem(String groupName, boolean empty) {
          super.updateItem(groupName, empty);
          if (empty || groupName == null) {
            setGraphic(null);
            setText(null);
            setStyle("");
            return;
          }
          int listIdx = getIndex();
          int totalGroups = layerOrderList.getItems().size();
          // listIdx 0 = drawn first (behind everything); last = drawn last (on top)
          int zNum = listIdx + 1;

          // Colour: dim for low (background) items, brighter for high (foreground)
          int total2 = layerOrderList.getItems().size();
          double frac = total2 <= 1 ? 1 : (double) listIdx / (total2 - 1);
          String zColor = String.format("#%02x%02x%02x",
              (int) (0x44 + frac * (0xaa - 0x44)),
              (int) (0x44 + frac * (0xaa - 0x44)),
              (int) (0x44 + frac * (0xaa - 0x44)));
          Label zLabel = new Label(String.format("%2d", zNum));
          zLabel.setMinWidth(24);
          zLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 10px; -fx-text-fill: "
              + zColor + "; -fx-alignment: center-right;");

          Label nameLabel = new Label(groupName);
          nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #d0d0d0;");
          HBox.setHgrow(nameLabel, Priority.ALWAYS);

          ComboBox<LayerOption> combo = selectors.get(groupName);
          LayerOption selected = combo != null ? combo.getValue() : null;
          String selName = (selected == null || selected.isNone()) ? ""
              : (selected.layerId != null && !selected.layerId.isBlank() ? selected.layerId : selected.label);
          Label selLabel = new Label(selName);
          selLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #666; -fx-padding: 0 4 0 0;");
          selLabel.setMaxWidth(80);

          Label handle = new Label("⣿");
          handle.setStyle("-fx-text-fill: #3a3a3a; -fx-font-size: 12px; -fx-padding: 0 4 0 2; -fx-cursor: open-hand;");

          Button upBtn = new Button("↑");
          upBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666; -fx-font-size: 10px; -fx-padding: 1 3;");
          upBtn.setFocusTraversable(false);
          upBtn.setDisable(listIdx == 0);
          upBtn.setOnAction(e -> moveGroupInOrder(groupName, -1));

          Button downBtn = new Button("↓");
          downBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666; -fx-font-size: 10px; -fx-padding: 1 3;");
          downBtn.setFocusTraversable(false);
          downBtn.setDisable(listIdx == totalGroups - 1);
          downBtn.setOnAction(e -> moveGroupInOrder(groupName, 1));

          HBox row = new HBox(2, handle, zLabel, nameLabel, selLabel, upBtn, downBtn);
          row.setAlignment(Pos.CENTER_LEFT);
          row.setStyle("-fx-padding: 2 4;");
          setGraphic(row);
          setText(null);
          setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        }
      };

      cell.setOnDragDetected(e -> {
        if (cell.isEmpty()) return;
        Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent cc = new ClipboardContent();
        cc.putString(cell.getItem());
        db.setContent(cc);
        e.consume();
      });

      cell.setOnDragOver(e -> {
        if (e.getDragboard().hasString() && !cell.isEmpty()
            && !cell.getItem().equals(e.getDragboard().getString())) {
          e.acceptTransferModes(TransferMode.MOVE);
          cell.setStyle("-fx-background-color: #2a3a4a; -fx-border-color: transparent;");
        }
        e.consume();
      });

      cell.setOnDragExited(e -> cell.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;"));

      cell.setOnDragDropped(e -> {
        String dragged = e.getDragboard().getString();
        String target = cell.getItem();
        if (dragged != null && target != null && !dragged.equals(target)) {
          int fromIdx = groupOrder.indexOf(dragged);
          int toIdx = groupOrder.indexOf(target);
          if (fromIdx >= 0 && toIdx >= 0) {
            groupOrder.remove(fromIdx);
            groupOrder.add(toIdx, dragged);
            refreshGroupRows();
            redrawPreview();
            persistCurrentSetState();
          }
        }
        e.setDropCompleted(true);
        e.consume();
      });

      return cell;
    });

    Label hint = new Label("Drag rows or use ↑/↓ to reorder.");
    hint.setStyle("-fx-font-size: 9px; -fx-text-fill: #555; -fx-padding: 4 4 0 4;");

    Label topAnchor = new Label("▲  top of list  =  drawn first  =  behind everything (background)");
    topAnchor.setStyle("-fx-font-size: 9px; -fx-text-fill: #3a5a3a; -fx-padding: 2 4 1 4; "
        + "-fx-background-color: #1a2a1a; -fx-border-color: #2a4a2a; -fx-border-width: 0 0 1 0;");

    Label bottomAnchor = new Label("▼  bottom of list  =  drawn last  =  on top of everything (foreground)");
    bottomAnchor.setStyle("-fx-font-size: 9px; -fx-text-fill: #5a3a7a; -fx-padding: 1 4 2 4; "
        + "-fx-background-color: #1a1a2a; -fx-border-color: #3a2a5a; -fx-border-width: 1 0 0 0;");

    VBox panel = new VBox(0, hint, topAnchor, layerOrderList, bottomAnchor);
    panel.setPadding(new Insets(4, 4, 4, 4));
    VBox.setVgrow(layerOrderList, Priority.ALWAYS);
    return panel;
  }

  private void refreshLayerOrderList() {
    layerOrderList.getItems().setAll(groupOrder);
    layerOrderList.refresh();
  }

  private void moveGroupInOrder(String groupName, int delta) {
    int idx = groupOrder.indexOf(groupName);
    if (idx < 0) return;
    int next = idx + delta;
    if (next < 0 || next >= groupOrder.size()) return;
    Collections.swap(groupOrder, idx, next);
    refreshGroupRows();
    redrawPreview();
    persistCurrentSetState();
  }

  private void swapMarkedGroups(int direction) {
    int swapped = 0;
    applyingState = true;
    for (String group : groupOrder) {
      CheckBox mark = swapGroupChecks.get(group);
      if (mark == null || !mark.isSelected()) continue;
      ComboBox<LayerOption> combo = selectors.get(group);
      if (combo == null || combo.getItems().size() <= 1) continue;
      int idx = combo.getSelectionModel().getSelectedIndex();
      if (idx < 1) idx = 1;
      int next = idx + (direction < 0 ? -1 : 1);
      if (next < 1) next = combo.getItems().size() - 1;
      if (next >= combo.getItems().size()) next = 1;
      combo.getSelectionModel().select(next);
      swapped++;
    }
    applyingState = false;
    if (swapped > 0) {
      updateExpressionFromSelection();
      redrawPreview();
      persistCurrentSetState();
      status("Swapped " + swapped + " marked groups.");
    } else {
      status("No marked groups available for swapping.");
    }
  }

  private boolean matchesAttributeFilter(String groupName, String normalizedFilter) {
    if (normalizedFilter == null || normalizedFilter.isBlank()) return true;
    String groupKey = sanitizeId(groupName);
    if (groupKey.contains(normalizedFilter)) return true;
    ComboBox<LayerOption> combo = selectors.get(groupName);
    if (combo == null) return false;
    for (LayerOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      if (sanitizeId(option.label).contains(normalizedFilter)) return true;
    }
    return false;
  }

  private void syncSetFromImageTag() {
    if (applyingState || sets.isEmpty()) return;
    String tag = sanitizeId(characterIdField.getText());
    if (tag.isBlank()) return;
    String selected = setBox.getValue();
    if (selected != null && sanitizeId(takeLastPathToken(selected)).equals(tag)) return;
    String preferred = null;
    for (String setId : setBox.getItems()) {
      String token = sanitizeId(takeLastPathToken(setId));
      if (token.equals(tag)) {
        preferred = setId;
        break;
      }
      if (preferred == null && token.contains(tag)) preferred = setId;
    }
    if (preferred == null) return;
    applyingState = true;
    try {
      setBox.getSelectionModel().select(preferred);
    } finally {
      applyingState = false;
    }
    onSetSelectionChanged();
  }

  private void refreshShortforms() {
    Map<String, String> parsed = parseAttributeShortforms(shortformsArea.getText());
    shortforms.clear();
    shortforms.putAll(parsed);
    String keep = shortformBox.getValue();
    shortformBox.getItems().setAll(shortforms.keySet());
    refreshVisibleSearchablePopup(shortformBoxSearchPopup);
    if (keep != null && shortforms.containsKey(keep)) {
      shortformBox.getSelectionModel().select(keep);
    } else if (!shortforms.isEmpty()) {
      shortformBox.getSelectionModel().select(0);
    }
  }

  private void applySelectedShortform() {
    String key = shortformBox.getValue();
    if (key == null || key.isBlank()) {
      status("Select a shortform first.");
      return;
    }
    String expression = shortforms.get(key);
    if (expression == null || expression.isBlank()) {
      status("Shortform is empty.");
      return;
    }
    typedAttributesField.setText(expression);
    applyTypedAttributes(true);
    status("Applied shortform: " + key);
  }

  private void copySelectedShortform() {
    String key = shortformBox.getValue();
    if (key == null || key.isBlank()) {
      status("Select a shortform first.");
      return;
    }
    String expression = shortforms.get(key);
    if (expression == null || expression.isBlank()) {
      status("Shortform is empty.");
      return;
    }
    copy(expression);
    status("Copied shortform: " + key);
  }

  private void applyTypedAttributes(boolean persist) {
    Map<String, String> assignments = parseAttributeAssignments(typedAttributesField.getText());
    if (assignments.isEmpty()) {
      if (persist) status("No valid attributes found in typed input.");
      return;
    }
    int applied = applyAttributeAssignments(assignments);
    if (applied > 0) {
      updateExpressionFromSelection();
      redrawPreview();
      if (persist) persistCurrentSetState();
      status("Applied " + applied + " typed attributes.");
    } else if (persist) {
      status("Typed attributes did not match current groups.");
    }
  }

  private int applyAttributeAssignments(Map<String, String> assignments) {
    if (assignments == null || assignments.isEmpty()) return 0;
    int applied = 0;
    applyingState = true;
    try {
      for (Map.Entry<String, String> entry : assignments.entrySet()) {
        String group = resolveGroupName(entry.getKey());
        if (group == null || group.isBlank()) continue;
        ComboBox<LayerOption> combo = selectors.get(group);
        if (combo == null) continue;
        LayerOption target = findOptionByValue(combo, entry.getValue());
        if (target == null) continue;
        combo.getSelectionModel().select(target);
        applied++;
      }
    } finally {
      applyingState = false;
    }
    return applied;
  }

  private String resolveGroupName(String rawGroup) {
    String key = sanitizeId(rawGroup);
    if (key.isBlank()) return null;
    String alias = GROUP_TOKEN_ALIASES.getOrDefault(key, key);
    for (String group : selectors.keySet()) {
      if (sanitizeId(group).equals(alias)) return group;
    }
    for (String group : selectors.keySet()) {
      if (sanitizeId(group).contains(alias) || alias.contains(sanitizeId(group))) return group;
    }
    return null;
  }

  private LayerOption findOptionByValue(ComboBox<LayerOption> combo, String rawValue) {
    if (combo == null) return null;
    String value = sanitizeId(rawValue);
    if (value.isBlank()) return null;
    if ("none".equals(value) || "off".equals(value) || "clear".equals(value) || "-".equals(value)) {
      return combo.getItems().isEmpty() ? null : combo.getItems().get(0);
    }
    for (LayerOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      if (sanitizeId(option.label).equals(value)) return option;
    }
    for (LayerOption option : combo.getItems()) {
      if (option == null || option.isNone()) continue;
      if (sanitizeId(option.label).contains(value) || value.contains(sanitizeId(option.label))) return option;
    }
    return null;
  }

  private void copyTagAttributes(boolean includeShowKeyword, boolean includeTag) {
    String attrs = String.join(" ", buildAttributeTokens());
    String tag = sanitizeId(characterIdField.getText());
    if (tag.isBlank()) tag = "character";
    StringBuilder out = new StringBuilder();
    if (includeShowKeyword) out.append("show ");
    if (includeTag) {
      out.append(tag);
      if (!attrs.isBlank()) out.append(' ');
    }
    out.append(attrs);
    copy(out.toString().trim());
    status("Copied attribute string.");
  }

  private List<String> buildAttributeTokens() {
    List<String> tokens = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo == null ? null : combo.getValue();
      if (option == null || option.isNone()) continue;
      String groupToken = sanitizeId(group);
      String optionToken = sanitizeId(option.label);
      if (groupToken.isBlank() || optionToken.isBlank()) continue;
      tokens.add(groupToken + "_" + optionToken);
    }
    return tokens;
  }

  private void applyDefaultSelection() {
    applyingState = true;
    for (Map.Entry<String, ComboBox<LayerOption>> entry : selectors.entrySet()) {
      selectPreferredLayerOption(entry.getKey(), entry.getValue());
    }
    applyingState = false;
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentSetState();
    status("Restored default layer picks.");
  }

  private void applyNoneSelection() {
    applyingState = true;
    for (ComboBox<LayerOption> combo : selectors.values()) {
      combo.getSelectionModel().select(0);
    }
    applyingState = false;
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentSetState();
    status("Cleared all layer picks.");
  }

  private void randomizeSelection() {
    if (selectors.isEmpty()) return;
    int changed = 0;
    applyingState = true;
    for (String group : groupOrder) {
      if (randomizeActiveOnly.isSelected()) {
        CheckBox check = activeGroupChecks.get(group);
        if (check != null && !check.isSelected()) continue;
      }
      ComboBox<LayerOption> combo = selectors.get(group);
      if (combo == null) continue;
      int size = combo.getItems().size();
      if (size <= 1) continue;
      int idx = 1 + random.nextInt(size - 1);
      combo.getSelectionModel().select(idx);
      changed++;
    }
    applyingState = false;
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentSetState();
    status("Randomized " + changed + " groups.");
  }

  private void generateGallery() {
    galleryPane.getChildren().clear();
    setSelectedGalleryCell(null, null);
    if (selectors.isEmpty() || groupOrder.isEmpty()) {
      galleryStatusLabel.setText("No groups loaded.");
      return;
    }

    // Collect groups that have >1 real option (excluding (none))
    List<String> varyGroups = new ArrayList<>();
    List<List<LayerOption>> varyOptions = new ArrayList<>();
    for (String group : groupOrder) {
      if (isLikelyBackgroundGroupName(group)) continue;
      ComboBox<LayerOption> combo = selectors.get(group);
      if (combo == null) continue;
      List<LayerOption> realOptions = new ArrayList<>();
      for (LayerOption opt : combo.getItems()) {
        if (opt != null && !opt.isNone()) realOptions.add(opt);
      }
      if (realOptions.size() > 1) {
        varyGroups.add(group);
        varyOptions.add(realOptions);
      }
    }

    if (varyGroups.isEmpty()) {
      galleryStatusLabel.setText("All groups have only one option — nothing to combine.");
      return;
    }

    // Compute total combinations and cap
    long total = 1;
    for (List<LayerOption> opts : varyOptions) {
      total *= opts.size();
      if (total > 500) { total = 500; break; }
    }

    // Generate cartesian product via index iteration
    int comboCount = (int) Math.min(total, 500);
    int numVary = varyGroups.size();
    int[] sizes = new int[numVary];
    for (int i = 0; i < numVary; i++) sizes[i] = varyOptions.get(i).size();

    // Collect fixed layers (groups not being varied, excluding backgrounds)
    List<LayerOption> fixedLayers = new ArrayList<>();
    for (String group : groupOrder) {
      if (varyGroups.contains(group)) continue;
      if (isLikelyBackgroundGroupName(group)) continue;
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption opt = combo != null ? combo.getValue() : null;
      if (opt != null && !opt.isNone()) fixedLayers.add(opt);
    }

    // Preload fixed layer images
    List<Image> fixedImages = new ArrayList<>();
    double maxW = 0, maxH = 0;
    for (LayerOption opt : fixedLayers) {
      Image img = loadImage(opt);
      if (img != null && !img.isError() && img.getWidth() > 0) {
        fixedImages.add(img);
        if (img.getWidth() > maxW) maxW = img.getWidth();
        if (img.getHeight() > maxH) maxH = img.getHeight();
      }
    }

    // Also scan variable layer images for max dimensions
    for (List<LayerOption> opts : varyOptions) {
      for (LayerOption opt : opts) {
        Image img = loadImage(opt);
        if (img != null && !img.isError() && img.getWidth() > 0) {
          if (img.getWidth() > maxW) maxW = img.getWidth();
          if (img.getHeight() > maxH) maxH = img.getHeight();
        }
      }
    }

    if (maxW <= 0 || maxH <= 0) {
      galleryStatusLabel.setText("Could not load any layer images.");
      return;
    }

    double thumbH = 96;
    double thumbW = Math.max(24, thumbH * (maxW / maxH));
    final double fMaxW = maxW, fMaxH = maxH;

    int generated = 0;
    int[] indices = new int[numVary];
    outer:
    for (int c = 0; c < comboCount; c++) {
      // Build the layer list for this combination
      List<Image> layers = new ArrayList<>(fixedImages);
      StringBuilder exprBuilder = new StringBuilder();
      Map<String, LayerOption> comboMap = new LinkedHashMap<>();
      for (String group : groupOrder) {
        int vi = varyGroups.indexOf(group);
        if (vi >= 0) {
          LayerOption opt = varyOptions.get(vi).get(indices[vi]);
          comboMap.put(group, opt);
          Image img = loadImage(opt);
          if (img != null && !img.isError()) layers.add(img);
          if (exprBuilder.length() > 0) exprBuilder.append('_');
          exprBuilder.append(sanitizeId(opt.label));
        }
      }

      if (!layers.isEmpty()) {
        String expr = exprBuilder.toString();
        VBox cell = createGalleryThumbnail(layers, thumbW, thumbH, fMaxW, fMaxH, expr, comboMap);
        galleryPane.getChildren().add(cell);
        generated++;
      }

      // Advance indices (odometer)
      for (int i = numVary - 1; i >= 0; i--) {
        indices[i]++;
        if (indices[i] < sizes[i]) break;
        indices[i] = 0;
        if (i == 0) break outer;
      }
    }

    galleryStatusLabel.setText(generated + " combinations (" + varyGroups.size() + " groups varied)");
    status("Gallery: generated " + generated + " charpreset thumbnails.");
  }

  private void clearGallery() {
    galleryPane.getChildren().clear();
    galleryStatusLabel.setText("");
    setSelectedGalleryCell(null, null);
  }

  private void setSelectedGalleryCell(VBox cell, Label label) {
    if (selectedGalleryCell != null) selectedGalleryCell.setStyle(GALLERY_CELL_STYLE);
    if (selectedGalleryLabel != null) selectedGalleryLabel.setStyle(GALLERY_LABEL_STYLE);
    selectedGalleryCell = cell;
    selectedGalleryLabel = label;
    if (selectedGalleryCell != null) selectedGalleryCell.setStyle(GALLERY_CELL_SELECTED_STYLE);
    if (selectedGalleryLabel != null) selectedGalleryLabel.setStyle(GALLERY_LABEL_SELECTED_STYLE);
  }

  private VBox createGalleryThumbnail(
      List<Image> layers, double thumbW, double thumbH,
      double maxW, double maxH, String expression,
      Map<String, LayerOption> comboMap
  ) {
    Canvas thumb = new Canvas(thumbW, thumbH);
    GraphicsContext g = thumb.getGraphicsContext2D();

    // Background
    drawCheckerBackground(g, thumbW, thumbH);

    // Composite layers scaled to fit
    double scale = Math.min(thumbW / maxW, thumbH / maxH);
    double offX = (thumbW - maxW * scale) / 2.0;
    double offY = (thumbH - maxH * scale) / 2.0;
    for (Image img : layers) {
      g.drawImage(img, offX, offY, img.getWidth() * scale, img.getHeight() * scale);
    }

    // Border
    g.setStroke(Color.color(1, 1, 1, 0.12));
    g.strokeRect(0.5, 0.5, thumbW - 1, thumbH - 1);

    // Label
    String shortExpr = expression.length() > 18 ? expression.substring(0, 16) + ".." : expression;
    Label label = new Label(shortExpr);
    label.setStyle(GALLERY_LABEL_STYLE);
    label.setMaxWidth(thumbW);
    label.setTooltip(new Tooltip(expression));

    VBox cell = new VBox(2, thumb, label);
    cell.setAlignment(Pos.TOP_CENTER);
    cell.setStyle(GALLERY_CELL_STYLE);

    // Click to apply and mark this chip as selected.
    cell.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.PRIMARY) {
        setSelectedGalleryCell(cell, label);
        applyGalleryCombination(comboMap);
      }
    });

    return cell;
  }

  private void applyGalleryCombination(Map<String, LayerOption> comboMap) {
    applyingState = true;
    for (var entry : comboMap.entrySet()) {
      ComboBox<LayerOption> combo = selectors.get(entry.getKey());
      if (combo == null) continue;
      for (int i = 0; i < combo.getItems().size(); i++) {
        LayerOption item = combo.getItems().get(i);
        if (item != null && item.file != null && entry.getValue().file != null
            && item.file.equals(entry.getValue().file)) {
          combo.getSelectionModel().select(i);
          break;
        }
      }
    }
    applyingState = false;
    updateExpressionFromSelection();
    redrawPreview();
    persistCurrentSetState();
    status("Applied gallery combination.");
  }

  private void redrawPreview() {
    updateGroupStats();
    renderPreviewToCanvas(previewCanvas, true);
  }

  private void renderPreviewToCanvas(Canvas canvas, boolean updateInfoLabel) {
    if (canvas == null) return;
    double w = Math.max(1, canvas.getWidth());
    double h = Math.max(1, canvas.getHeight());
    GraphicsContext g = canvas.getGraphicsContext2D();

    drawCheckerBackground(g, w, h);

    List<LayerOption> active = selectedLayers();
    if (active.isEmpty()) {
      viewportFrames.remove(canvas);
      drawCenteredText(g, w, h, "Select layer options to preview");
      if (updateInfoLabel) previewInfoLabel.setText("No layers selected.");
      return;
    }

    List<Image> layers = new ArrayList<>();
    double maxW = 0;
    double maxH = 0;
    for (LayerOption option : active) {
      Image img = loadImage(option);
      if (img == null || img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) continue;
      layers.add(img);
      if (img.getWidth() > maxW) maxW = img.getWidth();
      if (img.getHeight() > maxH) maxH = img.getHeight();
    }
    if (layers.isEmpty()) {
      viewportFrames.remove(canvas);
      drawCenteredText(g, w, h, "Selected images failed to load");
      if (updateInfoLabel) previewInfoLabel.setText("Selected images could not be decoded.");
      return;
    }

    if (matchGameFraming.isSelected()) {
      viewportFrames.remove(canvas);
      renderGameFramedPreview(g, layers, w, h, maxW, maxH);
      if (updateInfoLabel) {
        previewInfoLabel.setText(
            "Layers: " + active.size()
                + "  |  Active groups: " + activeGroupCount()
                + "  |  Virtual size: " + (int) maxW + "x" + (int) maxH
                + "  |  Game framing: h=" + formatDouble(gameCharacterHeightFactor)
                + ", baseline=" + formatDouble(gameCharacterBaselineY));
      }
      return;
    }

    viewportFrames.put(canvas, renderViewportPreview(g, layers, w, h, maxW, maxH));
    if (updateInfoLabel) {
      previewInfoLabel.setText(
          "Layers: " + active.size()
              + "  |  Active groups: " + activeGroupCount()
              + "  |  Virtual size: " + (int) maxW + "x" + (int) maxH);
    }
  }

  private void renderGameFramedPreview(GraphicsContext g, List<Image> layers, double canvasWidth, double canvasHeight, double maxW, double maxH) {
    double referenceAspect = maxH > 0 ? (maxW / maxH) : 0.5;
    if (!(referenceAspect > 0)) referenceAspect = 0.5;

    double spriteHeight = canvasHeight * gameCharacterHeightFactor;
    double spriteWidth = spriteHeight * referenceAspect;
    double x = (canvasWidth - spriteWidth) * 0.5;
    double y = (canvasHeight * gameCharacterBaselineY) - spriteHeight;

    for (Image img : layers) {
      g.drawImage(img, x, y, spriteWidth, spriteHeight);
    }

    if (showOverlayGuides.isSelected()) {
      g.setStroke(Color.color(1, 1, 1, 0.22));
      g.strokeRect(x + 0.5, y + 0.5, spriteWidth - 1, spriteHeight - 1);
      g.strokeLine(canvasWidth / 2.0, y, canvasWidth / 2.0, y + spriteHeight);
      g.strokeLine(x, canvasHeight / 2.0, x + spriteWidth, canvasHeight / 2.0);
    }
  }

  private ViewportFrame renderViewportPreview(GraphicsContext g, List<Image> layers, double canvasWidth, double canvasHeight, double maxW, double maxH) {
    double focusX = focusXSlider.getValue() / 100.0;
    double focusY = focusYSlider.getValue() / 100.0;
    double crop = cropSlider.getValue() / 100.0;
    double zoom = zoomSlider.getValue() / 100.0;
    if (zoom < 0.1) zoom = 0.1;
    crop = clamp(crop, 0.2, 1.0);

    double srcW = clamp((maxW * crop) / zoom, 1, maxW);
    double srcH = clamp((maxH * crop) / zoom, 1, maxH);

    double centerX = focusX * maxW;
    double centerY = focusY * maxH;
    double srcX = clamp(centerX - srcW * 0.5, 0, maxW - srcW);
    double srcY = clamp(centerY - srcH * 0.5, 0, maxH - srcH);

    double fit = Math.min(canvasWidth / srcW, canvasHeight / srcH);
    double destW = srcW * fit;
    double destH = srcH * fit;
    double destX = (canvasWidth - destW) * 0.5;
    double destY = (canvasHeight - destH) * 0.5;

    for (Image img : layers) {
      double sx = srcX * img.getWidth() / maxW;
      double sy = srcY * img.getHeight() / maxH;
      double sw = srcW * img.getWidth() / maxW;
      double sh = srcH * img.getHeight() / maxH;
      g.drawImage(img, sx, sy, sw, sh, destX, destY, destW, destH);
    }

    if (showOverlayGuides.isSelected()) {
      g.setStroke(Color.color(1, 1, 1, 0.22));
      g.strokeRect(destX + 0.5, destY + 0.5, destW - 1, destH - 1);
      g.strokeLine(canvasWidth / 2.0, destY, canvasWidth / 2.0, destY + destH);
      g.strokeLine(destX, canvasHeight / 2.0, destX + destW, canvasHeight / 2.0);
    }
    return new ViewportFrame(maxW, maxH, srcX, srcY, srcW, srcH, destX, destY, destW, destH);
  }

  private int activeGroupCount() {
    int count = 0;
    for (CheckBox check : activeGroupChecks.values()) {
      if (check.isSelected()) count++;
    }
    return count;
  }

  private void installSlider(Slider slider) {
    slider.valueProperty().addListener((o, ov, nv) -> {
      redrawPreview();
      if (!slider.isValueChanging() && !applyingState) persistCurrentSetState();
    });
    slider.valueChangingProperty().addListener((o, ov, nv) -> {
      if (!nv && !applyingState) persistCurrentSetState();
    });
  }

  private void installViewportInteractions(Canvas canvas) {
    if (canvas == null) return;

    canvas.setOnMouseClicked(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (e.getClickCount() < 2) return;
      resetViewportControls(true);
      e.consume();
    });

    canvas.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (matchGameFraming.isSelected()) return;
      if (!viewportFrames.containsKey(canvas)) return;
      dragCanvas = canvas;
      dragLastX = e.getX();
      dragLastY = e.getY();
      dragDirty = false;
      e.consume();
    });

    canvas.setOnMouseDragged(e -> {
      if (dragCanvas != canvas || !e.isPrimaryButtonDown()) return;
      double dx = e.getX() - dragLastX;
      double dy = e.getY() - dragLastY;
      dragLastX = e.getX();
      dragLastY = e.getY();
      if (Math.abs(dx) < 0.0001 && Math.abs(dy) < 0.0001) return;
      if (panViewportByPixels(canvas, dx, dy)) {
        dragDirty = true;
      }
      e.consume();
    });

    canvas.setOnMouseReleased(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (dragCanvas != canvas) return;
      dragCanvas = null;
      if (dragDirty) {
        dragDirty = false;
        persistCurrentSetState();
      }
      e.consume();
    });

    canvas.setOnScroll(e -> {
      if (Math.abs(e.getDeltaY()) < 0.0001) return;
      if (zoomViewportAt(canvas, e.getX(), e.getY(), e.getDeltaY())) {
        persistCurrentSetState();
        e.consume();
      }
    });
  }

  private boolean panViewportByPixels(Canvas canvas, double dx, double dy) {
    if (canvas == null || matchGameFraming.isSelected()) return false;
    ViewportFrame frame = viewportFrames.get(canvas);
    if (frame == null || !frame.valid()) return false;

    double focusX = focusXSlider.getValue() / 100.0;
    double focusY = focusYSlider.getValue() / 100.0;
    double nextFocusX = draggedFocus(focusX, dx, frame.srcW(), frame.destW(), frame.maxW());
    double nextFocusY = draggedFocus(focusY, dy, frame.srcH(), frame.destH(), frame.maxH());
    return setViewportState(nextFocusX, nextFocusY, null);
  }

  private boolean zoomViewportAt(Canvas canvas, double canvasX, double canvasY, double deltaY) {
    if (canvas == null || matchGameFraming.isSelected()) return false;
    ViewportFrame frame = viewportFrames.get(canvas);
    if (frame == null || !frame.valid()) return false;

    double minZoom = zoomSlider.getMin() / 100.0;
    double maxZoom = zoomSlider.getMax() / 100.0;
    double currentZoom = clamp(zoomSlider.getValue() / 100.0, minZoom, maxZoom);
    double nextZoom = zoomFromScroll(currentZoom, deltaY, minZoom, maxZoom);
    if (Math.abs(nextZoom - currentZoom) < 0.000001) return false;

    double px = clamp(canvasX, frame.destX(), frame.destX() + frame.destW());
    double py = clamp(canvasY, frame.destY(), frame.destY() + frame.destH());
    double ratioX = frame.destW() <= 0 ? 0.5 : (px - frame.destX()) / frame.destW();
    double ratioY = frame.destH() <= 0 ? 0.5 : (py - frame.destY()) / frame.destH();

    double anchorX = frame.srcX() + ratioX * frame.srcW();
    double anchorY = frame.srcY() + ratioY * frame.srcH();
    double crop = clamp(cropSlider.getValue() / 100.0, 0.2, 1.0);
    double newSrcW = clamp((frame.maxW() * crop) / nextZoom, 1.0, frame.maxW());
    double newSrcH = clamp((frame.maxH() * crop) / nextZoom, 1.0, frame.maxH());

    double nextFocusX = anchoredFocus(anchorX, ratioX, newSrcW, frame.maxW());
    double nextFocusY = anchoredFocus(anchorY, ratioY, newSrcH, frame.maxH());
    return setViewportState(nextFocusX, nextFocusY, nextZoom * 100.0);
  }

  private boolean setViewportState(double focusX, double focusY, Double zoomPercent) {
    double clampedFocusX = clamp(focusX, 0.0, 1.0) * 100.0;
    double clampedFocusY = clamp(focusY, 0.0, 1.0) * 100.0;
    double clampedZoom = zoomPercent == null
        ? zoomSlider.getValue()
        : clamp(zoomPercent, zoomSlider.getMin(), zoomSlider.getMax());

    boolean changed = false;
    applyingState = true;
    try {
      if (Math.abs(focusXSlider.getValue() - clampedFocusX) >= 0.0001) {
        focusXSlider.setValue(clampedFocusX);
        changed = true;
      }
      if (Math.abs(focusYSlider.getValue() - clampedFocusY) >= 0.0001) {
        focusYSlider.setValue(clampedFocusY);
        changed = true;
      }
      if (zoomPercent != null && Math.abs(zoomSlider.getValue() - clampedZoom) >= 0.0001) {
        zoomSlider.setValue(clampedZoom);
        changed = true;
      }
    } finally {
      applyingState = false;
    }

    if (changed) redrawPreview();
    return changed;
  }

  private void resetViewportControls(boolean persist) {
    applyingState = true;
    try {
      focusXSlider.setValue(50);
      focusYSlider.setValue(50);
      cropSlider.setValue(100);
      zoomSlider.setValue(100);
    } finally {
      applyingState = false;
    }
    redrawPreview();
    if (persist) persistCurrentSetState();
  }

  static double draggedFocus(double currentFocus, double deltaPixels, double srcSpan, double destSpan, double maxSpan) {
    if (!(destSpan > 0) || !(maxSpan > 0)) return clamp(currentFocus, 0.0, 1.0);
    double sourceDelta = deltaPixels * (srcSpan / destSpan);
    return clamp(currentFocus - (sourceDelta / maxSpan), 0.0, 1.0);
  }

  static double anchoredFocus(double anchorSource, double anchorRatio, double srcSpan, double maxSpan) {
    if (!(maxSpan > 0)) return 0.5;
    double ratio = clamp(anchorRatio, 0.0, 1.0);
    double span = clamp(srcSpan, 1.0, maxSpan);
    double start = clamp(anchorSource - ratio * span, 0.0, Math.max(0.0, maxSpan - span));
    return clamp((start + span * 0.5) / maxSpan, 0.0, 1.0);
  }

  static double zoomFromScroll(double currentZoom, double deltaY, double minZoom, double maxZoom) {
    double base = currentZoom > 0 ? currentZoom : minZoom;
    double scaled = base * Math.pow(1.12, deltaY / 40.0);
    return clamp(scaled, minZoom, maxZoom);
  }

  private void updateViewportControlState() {
    boolean gameFraming = matchGameFraming.isSelected();
    focusXSlider.setDisable(gameFraming);
    focusYSlider.setDisable(gameFraming);
    cropSlider.setDisable(gameFraming);
    zoomSlider.setDisable(gameFraming);
  }

  private Button iconButton(Region icon, String tooltip, Runnable action) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setMinSize(28, 28);
    button.setPrefSize(28, 28);
    button.setMaxSize(28, 28);
    button.setFocusTraversable(false);
    if (tooltip != null && !tooltip.isBlank()) {
      button.setTooltip(new Tooltip(tooltip));
    }
    button.setOnAction(e -> {
      if (action != null) action.run();
    });
    return button;
  }

  private Button actionButton(String text, Region icon, String tooltip, Runnable action) {
    Button button = new Button(text, icon);
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setGraphicTextGap(6);
    button.setMinHeight(28);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setFocusTraversable(false);
    if (tooltip != null && !tooltip.isBlank()) {
      button.setTooltip(new Tooltip(tooltip));
    }
    button.setOnAction(e -> {
      if (action != null) action.run();
    });
    return button;
  }

  private void requestFullscreenToggle() {
    if (fullscreenToggleHandler != null) {
      fullscreenToggleHandler.run();
      return;
    }
    status("Fullscreen toggle is unavailable in this host.");
  }

  private void updateFullscreenButtonUi() {
    if (fullscreenButton == null) return;
    if (fullscreenActive) {
      fullscreenButton.setGraphic(CssIcon.clearX("#f38ba8"));
      fullscreenButton.setTooltip(new Tooltip("Exit panel fullscreen"));
    } else {
      fullscreenButton.setGraphic(CssIcon.expand("#f5c46b"));
      fullscreenButton.setTooltip(new Tooltip("Fullscreen this panel in the current editor window"));
    }
  }

  private void copySnippet() {
    String format = snippetFormatBox.getValue();
    if (format == null || format.isBlank()) format = SNIPPET_COMBINED;
    String snippet = buildSnippet(format);
    copy(snippet);
    status("Copied snippet: " + format + ".");
  }

  private void copyCharpresetSnippet() {
    String snippet = buildSnippet(SNIPPET_CHARPRESET);
    if (snippet == null || snippet.isBlank()) {
      status("No layers selected — nothing to export.");
      return;
    }
    copy(snippet);
    status("Copied @charpreset snippet to clipboard.");
  }

  private String buildSnippet(String format) {
    String characterId = sanitizeId(characterIdField.getText());
    String expression = sanitizeId(expressionField.getText());
    if (characterId.isBlank()) characterId = "character_id";
    if (expression.isBlank()) expression = "neutral";

    String layerSpec = buildLayerPathSpec();
    String fallbackPath = "assets/characters/" + characterId + "/" + expression + ".png";
    String imageSpec = layerSpec.isBlank() ? fallbackPath : layerSpec;
    String charimg = "@charimg " + characterId + " " + expression + " " + imageSpec;
    CharpresetSnippet charpreset = buildCharpresetSnippet(characterId, expression, fallbackPath);
    String show = formatShowSnippet(characterId, formatPresetShowExpressionToken(expression));
    String inlineShow = buildInlineCompositeShowSnippet(characterId, charpreset, expression);

    if (SNIPPET_CHARIMG.equals(format)) return charimg + "\n";
    if (SNIPPET_CHARPRESET.equals(format)) return charpreset.declarations() + charpreset.presetLine() + "\n";
    if (SNIPPET_CHARPRESET_COMBINED.equals(format)) {
      return charpreset.declarations() + charpreset.presetLine() + "\n" + show + "\n";
    }
    if (SNIPPET_INLINE_SHOW.equals(format)) return inlineShow + "\n";
    if (SNIPPET_SHOW.equals(format)) return show + "\n";
    if (SNIPPET_RECIPE.equals(format)) {
      StringBuilder out = new StringBuilder();
      out.append("# Layer recipe generated by JVN Layered Image Visualizer\n");
      out.append("# Multi-layer @charimg is supported via: pathA | pathB | pathC\n");
      out.append("# @charpreset export below uses @charlayer + $layer references\n");
      out.append("# Inline composite [show] export uses $layer+$layer syntax\n");
      if (currentSetId != null && !currentSetId.isBlank()) {
        out.append("# Source set: ").append(currentSetId).append('\n');
      }
      for (String group : groupOrder) {
        ComboBox<LayerOption> combo = selectors.get(group);
        LayerOption option = combo != null ? combo.getValue() : null;
        if (option == null || option.isNone()) continue;
        out.append("# ").append(group).append(" -> ").append(option.relativePath).append('\n');
      }
      out.append('\n');
      out.append("# Charpreset form\n");
      out.append(charpreset.declarations());
      out.append(charpreset.presetLine()).append('\n');
      out.append(show).append('\n');
      out.append('\n');
      out.append("# Inline composite show form\n");
      out.append(inlineShow).append('\n');
      out.append('\n');
      out.append("# Direct charimg form\n");
      out.append(charimg).append('\n').append(show).append('\n');
      return out.toString();
    }

    return charimg + "\n" + show + "\n";
  }

  private CharpresetSnippet buildCharpresetSnippet(String characterId, String expression, String fallbackPath) {
    List<LayerSelection> selected = selectedLayerEntries();
    Map<String, Integer> layerIdCounts = new HashMap<>();
    StringBuilder declarations = new StringBuilder();
    StringBuilder presetSpec = new StringBuilder();
    List<String> layerRefs = new ArrayList<>();

    if (selected.isEmpty()) {
      String layerId = nextLayerId(layerIdCounts, "base");
      declarations.append("@charlayer ")
          .append(characterId).append(' ')
          .append(layerId).append(' ')
          .append(fallbackPath)
          .append('\n');
      presetSpec.append('$').append(layerId);
      layerRefs.add("base");
    } else {
      for (LayerSelection selection : selected) {
        LayerOption option = selection.option();
        if (option == null) continue;
        String relativePath = option.relativePath == null ? "" : option.relativePath.trim();
        if (relativePath.isBlank()) continue;

        String declaredLayerId = sanitizeId(option.layerId);
        String groupId = sanitizeId(selection.group());
        String labelId = sanitizeId(option.label);
        String baseLayerId = !declaredLayerId.isBlank()
            ? declaredLayerId
            : sanitizeId((groupId.isBlank() ? "layer" : groupId) + "_" + (labelId.isBlank() ? "variant" : labelId));
        if (baseLayerId.isBlank()) baseLayerId = "layer";
        String layerId = nextLayerId(layerIdCounts, baseLayerId);

        declarations.append("@charlayer ")
            .append(characterId).append(' ')
            .append(layerId).append(' ')
            .append(relativePath)
            .append('\n');
        String layerRef = !declaredLayerId.isBlank()
            ? layerId
            : !groupId.isBlank() && !labelId.isBlank()
            ? groupId + "=" + labelId
            : layerId;
        if (presetSpec.length() > 0) presetSpec.append(" | ");
        presetSpec.append('$').append(layerRef);
        layerRefs.add(layerRef);
      }
    }

    if (presetSpec.length() == 0) {
      String layerId = nextLayerId(layerIdCounts, "base");
      declarations.append("@charlayer ")
          .append(characterId).append(' ')
          .append(layerId).append(' ')
          .append(fallbackPath)
          .append('\n');
      presetSpec.append('$').append(layerId);
      layerRefs.add("base");
    }

    String presetLine = "@charpreset " + characterId + " " + expression + " " + presetSpec;
    return new CharpresetSnippet(declarations.toString(), presetLine, List.copyOf(layerRefs));
  }

  private String buildInlineCompositeShowSnippet(String characterId, CharpresetSnippet charpreset, String expression) {
    String inlineExpression = formatInlineLayerExpressionToken(charpreset.layerRefs());
    if (inlineExpression.isBlank()) {
      inlineExpression = formatPresetShowExpressionToken(expression);
    }
    return formatShowSnippet(characterId, inlineExpression);
  }

  static String formatPresetShowExpressionToken(String expression) {
    String normalized = sanitizeId(expression);
    if (normalized.isBlank()) normalized = "neutral";
    return "@" + normalized;
  }

  static String formatInlineLayerExpressionToken(List<String> layerRefs) {
    if (layerRefs == null || layerRefs.isEmpty()) return "";
    StringBuilder out = new StringBuilder();
    for (String layerRef : layerRefs) {
      String normalized = normalizeLayerReferenceToken(layerRef);
      if (normalized.isBlank()) continue;
      if (out.length() > 0) out.append('+');
      out.append('$').append(normalized);
    }
    return out.toString();
  }

  private static String normalizeLayerReferenceToken(String layerRef) {
    String raw = layerRef == null ? "" : layerRef.trim();
    if (raw.isBlank()) return "";
    int eq = raw.indexOf('=');
    if (eq > 0 && eq < raw.length() - 1) {
      String group = sanitizeId(raw.substring(0, eq));
      String variant = sanitizeId(raw.substring(eq + 1));
      if (!group.isBlank() && !variant.isBlank()) {
        return group + "=" + variant;
      }
      if (!group.isBlank()) return group;
      return variant;
    }
    return sanitizeId(raw);
  }

  static String formatShowSnippet(String characterId, String expressionToken) {
    String normalizedCharacterId = sanitizeId(characterId);
    if (normalizedCharacterId.isBlank()) normalizedCharacterId = "character_id";
    String normalizedExpression = expressionToken == null ? "" : expressionToken.trim();
    if (normalizedExpression.isBlank()) normalizedExpression = "@neutral";
    return "[show " + normalizedCharacterId + " center " + normalizedExpression + "]";
  }

  private static String nextLayerId(Map<String, Integer> counts, String base) {
    String normalizedBase = sanitizeId(base);
    if (normalizedBase.isBlank()) normalizedBase = "layer";
    int next = counts.getOrDefault(normalizedBase, 0) + 1;
    counts.put(normalizedBase, next);
    return next <= 1 ? normalizedBase : (normalizedBase + "_" + next);
  }

  private String buildLayerPathSpec() {
    StringBuilder spec = new StringBuilder();
    for (LayerOption option : selectedLayers()) {
      if (option == null || option.isNone() || option.relativePath == null || option.relativePath.isBlank()) continue;
      if (spec.length() > 0) spec.append(" | ");
      spec.append(option.relativePath.trim());
    }
    return spec.toString();
  }

  private void copyLayerRecipe() {
    copy(buildSnippet(SNIPPET_RECIPE));
    status("Copied detailed layer recipe.");
  }

  private void updateExpressionFromSelection() {
    if (!autoExpression.isSelected()) return;
    LayeredSet set = currentSet();
    if (set != null && !set.projectPresets.isEmpty()) {
      Map<String, String> selectedByGroup = selectedPathsByGroup();
      for (Map.Entry<String, Map<String, String>> entry : set.projectPresets.entrySet()) {
        if (selectedByGroup.equals(entry.getValue())) {
          applyingState = true;
          expressionField.setText(sanitizeId(entry.getKey()));
          applyingState = false;
          return;
        }
      }
    }
    List<String> names = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo != null ? combo.getValue() : null;
      if (option == null || option.isNone()) continue;
      names.add(sanitizeId(option.label));
    }
    String expr = names.isEmpty() ? "neutral" : String.join("_", names);
    applyingState = true;
    expressionField.setText(expr);
    applyingState = false;
  }

  private List<LayerOption> selectedLayers() {
    List<LayerOption> out = new ArrayList<>();
    for (LayerSelection selection : selectedLayerEntries()) {
      out.add(selection.option());
    }
    return out;
  }

  private List<LayerSelection> selectedLayerEntries() {
    List<String> selectedGroupNames = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo != null ? combo.getValue() : null;
      if (option == null || option.isNone()) continue;
      selectedGroupNames.add(group);
    }
    boolean suppressBackground = shouldSuppressBackgroundGroups(selectedGroupNames);

    List<LayerSelection> out = new ArrayList<>();
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo != null ? combo.getValue() : null;
      if (option == null || option.isNone()) continue;
      if (suppressBackground && isLikelyBackgroundGroupName(group)) continue;
      out.add(new LayerSelection(group, option));
    }
    return out;
  }

  private Map<String, String> selectedPathsByGroup() {
    Map<String, String> out = new LinkedHashMap<>();
    for (LayerSelection selection : selectedLayerEntries()) {
      LayerOption option = selection.option();
      if (option == null || option.isNone() || option.relativePath == null || option.relativePath.isBlank()) continue;
      out.put(selection.group(), option.relativePath);
    }
    return out;
  }

  private LayeredSet currentSet() {
    if (currentSetId == null || currentSetId.isBlank()) return null;
    return sets.get(currentSetId);
  }

  private void updateExportControls() {
    exportNameField.setDisable(!customExportNameCheck.isSelected());
    File directory = resolveExportDirectory();
    exportDirectoryField.setText(directory == null ? "(choose folder)" : describePathRelativeToProject(directory));
    exportInfoLabel.setText("PNG: " + buildCompositePngFileName() + "\nSetup: " + buildLayerSetupFileName());
  }

  private File configuredExportDirectory() {
    String raw = persisted.getProperty("global.exportDir", "").trim();
    return raw.isBlank() ? null : new File(raw);
  }

  private File resolveExportDirectory() {
    File configured = configuredExportDirectory();
    if (configured != null) return configured;
    return projectRoot != null && projectRoot.isDirectory() ? projectRoot : null;
  }

  private File resolveExistingExportDirectory() {
    File directory = resolveExportDirectory();
    while (directory != null && (!directory.exists() || !directory.isDirectory())) {
      directory = directory.getParentFile();
    }
    if (directory != null) return directory;
    return projectRoot != null && projectRoot.isDirectory() ? projectRoot : null;
  }

  private void chooseExportDirectory() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Choose Layered Export Folder");
    File initial = resolveExistingExportDirectory();
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File selected = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
    if (selected == null) return;
    setConfiguredExportDirectory(selected);
    status("Export folder: " + describePathRelativeToProject(selected));
  }

  private void revealExportDirectory() {
    File directory = resolveExportDirectory();
    if (directory == null) {
      status("Choose an export folder first.");
      return;
    }
    try {
      Files.createDirectories(directory.toPath());
    } catch (Exception ex) {
      status("Failed to prepare export folder: " + ex.getMessage());
      return;
    }
    if (AssetPickerSupport.revealFile(directory)) {
      status("Opened export folder: " + describePathRelativeToProject(directory));
    } else {
      status("Could not reveal export folder.");
    }
  }

  private void setConfiguredExportDirectory(File directory) {
    if (directory == null) {
      persisted.remove("global.exportDir");
    } else {
      persisted.setProperty("global.exportDir", directory.getAbsolutePath());
    }
    updateExportControls();
    savePersistentState();
  }

  private File resolveQuickExportFile(String fileName) {
    File directory = resolveExportDirectory();
    if (directory == null || fileName == null || fileName.isBlank()) return null;
    return new File(directory, fileName);
  }

  private String currentExportBaseName() {
    String defaultName = buildDefaultExportStem(currentSetId, characterIdField.getText(), expressionField.getText());
    if (customExportNameCheck.isSelected()) {
      String custom = sanitizeExportStem(exportNameField.getText(), "");
      if (!custom.isBlank()) return custom;
    }
    return defaultName;
  }

  private String buildCompositePngFileName() {
    return buildExportFileName(currentExportBaseName(), "png");
  }

  private String buildLayerSetupFileName() {
    return buildExportFileName(currentExportBaseName(), "layersetup");
  }

  static String buildDefaultExportStem(String setId, String characterId, String expressionId) {
    String base = sanitizeExportStem(characterId, "");
    if (base.isBlank()) {
      base = sanitizeExportStem(takeLastPathToken(setId), "layered_image");
    }
    String expr = sanitizeExportStem(expressionId, "");
    if (!expr.isBlank() && !base.equals(expr) && !base.endsWith("_" + expr)) {
      base = base + "_" + expr;
    }
    return base.isBlank() ? "layered_image" : base;
  }

  static String buildExportFileName(String baseName, String extension) {
    String ext = sanitizeExportStem(extension, "dat");
    String stem = sanitizeExportStem(baseName, "export");
    return stem + "." + ext;
  }

  private static String sanitizeExportStem(String raw, String fallback) {
    String value = raw == null ? "" : raw.trim().replace('\\', '/');
    if (value.isBlank()) return fallback == null ? "" : fallback;
    String sanitized = value
        .replaceAll("\\.[A-Za-z0-9]+$", "")
        .replaceAll("[^A-Za-z0-9]+", "_")
        .replaceAll("_+", "_")
        .replaceAll("^_+", "")
        .replaceAll("_+$", "")
        .toLowerCase(Locale.ROOT);
    if (sanitized.isBlank()) return fallback == null ? "" : fallback;
    return sanitized;
  }

  private String describePathRelativeToProject(File file) {
    if (file == null) return "Project root";
    try {
      if (projectRoot != null && projectRoot.isDirectory()) {
        Path root = projectRoot.toPath().toAbsolutePath().normalize();
        Path target = file.toPath().toAbsolutePath().normalize();
        if (target.equals(root)) return "Project root";
        if (target.startsWith(root)) {
          String relative = root.relativize(target).toString().replace('\\', '/');
          return relative.isBlank() ? "Project root" : relative;
        }
      }
    } catch (Exception ignored) {
    }
    return file.getAbsolutePath();
  }

  private void selectPreferredLayerOption(String groupName, ComboBox<LayerOption> combo) {
    if (combo == null || combo.getItems().isEmpty()) return;
    if (combo.getItems().size() == 1) {
      combo.getSelectionModel().select(0);
      return;
    }
    int preferredIdx = 1;
    int preferredScore = Integer.MAX_VALUE;
    for (int i = 1; i < combo.getItems().size(); i++) {
      LayerOption option = combo.getItems().get(i);
      int score = defaultOptionScore(option == null ? null : option.label);
      if (score < preferredScore) {
        preferredScore = score;
        preferredIdx = i;
      }
    }
    if (preferredScore >= 10 && isLikelyOptionalOverlayGroup(groupName)) {
      combo.getSelectionModel().select(0);
      return;
    }
    combo.getSelectionModel().select(preferredIdx);
  }

  static int defaultOptionScore(String label) {
    String key = sanitizeId(label);
    if ("neutral".equals(key)) return 0;
    if ("default".equals(key)) return 1;
    if ("base".equals(key)) return 2;
    if (key.contains("neutral")) return 3;
    if (key.contains("default")) return 4;
    if (key.contains("base")) return 5;
    return 10;
  }

  static boolean isLikelyOptionalOverlayGroup(String groupName) {
    String g = sanitizeId(groupName);
    if (g.isBlank()) return false;
    return g.contains("arm")
        || g.contains("hand")
        || g.contains("gesture")
        || g.contains("phone")
        || g.contains("additions")
        || g.contains("addition")
        || g.contains("accessory")
        || g.contains("prop")
        || g.contains("item")
        || g.contains("glasses")
        || g.contains("cig")
        || g.contains("sweat")
        || g.contains("tail_front");
  }

  static boolean isLikelyBackgroundGroupName(String groupName) {
    String g = sanitizeId(groupName);
    if (g.isBlank()) return false;
    return g.equals("bg")
        || g.contains("background")
        || g.equals("field")
        || g.equals("scene")
        || g.equals("location")
        || g.equals("room")
        || g.equals("environment")
        || g.equals("menu")
        || g.equals("mainmenu")
        || g.equals("title")
        || g.equals("backdrop");
  }

  static boolean shouldSuppressBackgroundGroups(List<String> selectedGroupNames) {
    if (selectedGroupNames == null || selectedGroupNames.isEmpty()) return false;
    boolean hasBackground = false;
    boolean hasForeground = false;
    for (String groupName : selectedGroupNames) {
      if (isLikelyBackgroundGroupName(groupName)) {
        hasBackground = true;
      } else {
        hasForeground = true;
      }
    }
    return hasBackground && hasForeground;
  }

  private Image loadImage(LayerOption option) {
    if (option == null || option.file == null) return null;
    String key = option.file.getAbsolutePath();
    Image cached = imageCache.get(key);
    if (cached != null) return cached;
    Image img = new Image(option.file.toURI().toString(), false);
    if (!img.isError()) imageCache.put(key, img);
    return img;
  }

  private void openSelectedImage(LayerOption option) {
    if (option == null || option.file == null) return;
    try {
      if (!Desktop.isDesktopSupported()) {
        status("Desktop open is not supported on this platform.");
        return;
      }
      Desktop.getDesktop().open(option.file);
      status("Opened " + option.file.getName());
    } catch (Exception ex) {
      status("Failed to open image: " + ex.getMessage());
    }
  }

  private void refreshPresetList() {
    presetNameToKey.clear();
    presetBox.getItems().clear();
    refreshVisibleSearchablePopup(presetBoxSearchPopup);
    if (currentSetId == null || currentSetId.isBlank()) return;

    String prefix = "preset." + encodeKey(currentSetId) + ".";
    Map<String, String> namesToKeys = new HashMap<>();

    for (String key : persisted.stringPropertyNames()) {
      if (!key.startsWith(prefix) || !key.endsWith(".name")) continue;
      String presetKey = key.substring(prefix.length(), key.length() - ".name".length());
      String name = persisted.getProperty(key, "").trim();
      if (name.isBlank()) continue;
      String uniqueName = name;
      int counter = 2;
      while (namesToKeys.containsKey(uniqueName)) {
        uniqueName = name + " (" + counter + ")";
        counter++;
      }
      namesToKeys.put(uniqueName, presetKey);
    }

    List<String> names = new ArrayList<>(namesToKeys.keySet());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    presetBox.getItems().setAll(names);
    refreshVisibleSearchablePopup(presetBoxSearchPopup);
    for (String name : names) {
      presetNameToKey.put(name, namesToKeys.get(name));
    }

    String lastPreset = persisted.getProperty(statePrefix(currentSetId) + "lastPreset", "");
    if (!lastPreset.isBlank()) {
      for (Map.Entry<String, String> e : presetNameToKey.entrySet()) {
        if (lastPreset.equals(e.getValue())) {
          presetBox.getSelectionModel().select(e.getKey());
          presetNameField.setText(e.getKey());
          return;
        }
      }
    }

    if (!names.isEmpty()) {
      presetBox.getSelectionModel().select(0);
      presetNameField.setText(names.get(0));
    } else {
      presetNameField.clear();
    }
  }

  private void savePreset() {
    if (currentSetId == null || currentSetId.isBlank()) {
      status("Select a set before saving presets.");
      return;
    }

    String name = presetNameField.getText() == null ? "" : presetNameField.getText().trim();
    if (name.isBlank()) {
      String expr = sanitizeId(expressionField.getText());
      name = expr.isBlank() ? "preset" : expr;
    }

    String existing = presetNameToKey.get(name);
    String presetKey = existing != null ? existing : uniquePresetKey(currentSetId, name);
    String prefix = presetPrefix(currentSetId, presetKey);

    captureStateToPrefix(prefix);
    persisted.setProperty(prefix + "name", name);
    persisted.setProperty(statePrefix(currentSetId) + "lastPreset", presetKey);

    savePersistentState();
    refreshPresetList();
    presetBox.getSelectionModel().select(name);
    status("Saved preset: " + name);
  }

  private void loadSelectedPreset() {
    if (currentSetId == null || currentSetId.isBlank()) {
      status("Select a set before loading presets.");
      return;
    }

    String name = presetBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a preset first.");
      return;
    }

    String presetKey = presetNameToKey.get(name);
    if (presetKey == null || presetKey.isBlank()) {
      status("Preset key not found.");
      return;
    }

    applyStateFromPrefix(presetPrefix(currentSetId, presetKey));
    persisted.setProperty(statePrefix(currentSetId) + "lastPreset", presetKey);
    savePersistentState();
    status("Loaded preset: " + name);
  }

  private void deleteSelectedPreset() {
    if (currentSetId == null || currentSetId.isBlank()) {
      status("Select a set before deleting presets.");
      return;
    }

    String name = presetBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a preset first.");
      return;
    }

    String presetKey = presetNameToKey.get(name);
    if (presetKey == null || presetKey.isBlank()) {
      status("Preset key not found.");
      return;
    }

    clearPrefix(presetPrefix(currentSetId, presetKey));
    String stateKey = statePrefix(currentSetId) + "lastPreset";
    if (presetKey.equals(persisted.getProperty(stateKey, ""))) {
      persisted.remove(stateKey);
    }

    savePersistentState();
    refreshPresetList();
    status("Deleted preset: " + name);
  }

  private Button helpButton(String title, String body) {
    Button btn = new Button("?");
    btn.setStyle(
        "-fx-background-color: #253545; -fx-text-fill: #7ec8e3; -fx-font-size: 9px; "
        + "-fx-font-weight: bold; -fx-min-width: 18; -fx-min-height: 18; "
        + "-fx-padding: 0 5; -fx-background-radius: 9; -fx-cursor: hand;");
    btn.setFocusTraversable(false);
    btn.setTooltip(new Tooltip("Click for help: " + title));
    btn.setOnAction(e -> {
      Label titleLabel = new Label(title);
      titleLabel.setStyle("-fx-text-fill: #e8e8e8; -fx-font-size: 13px; -fx-font-weight: bold;");
      Label bodyLabel = new Label(body);
      bodyLabel.setWrapText(true);
      bodyLabel.setStyle("-fx-text-fill: #c8c8c8; -fx-font-size: 11px; -fx-line-spacing: 3;");
      bodyLabel.setPrefWidth(440);
      Button okBtn = new Button("Got it");
      okBtn.setStyle("-fx-font-size: 11px;");
      VBox root = new VBox(10, titleLabel, bodyLabel, okBtn);
      root.setPadding(new Insets(16));
      root.setStyle("-fx-background-color: #1e1e1e;");
      javafx.stage.Stage helpStage = new javafx.stage.Stage();
      helpStage.setTitle(title);
      helpStage.initOwner(getScene() != null ? getScene().getWindow() : null);
      helpStage.initModality(javafx.stage.Modality.NONE);
      javafx.scene.Scene helpScene = new javafx.scene.Scene(root);
      if (getScene() != null) helpScene.getStylesheets().addAll(getScene().getStylesheets());
      helpStage.setScene(helpScene);
      helpStage.setWidth(490);
      helpStage.sizeToScene();
      okBtn.setOnAction(ev -> helpStage.close());
      helpStage.show();
    });
    return btn;
  }

  private void showNewSetDialog() {
    // ── In-dialog data model ─────────────────────────────────────────────────
    ObservableList<String> groupNames = FXCollections.observableArrayList();
    Map<String, List<NewSetOption>> groupOptionMap = new HashMap<>();
    String[] selectedGroup = {null};

    // ── Header fields ────────────────────────────────────────────────────────
    final String FIELD_STYLE = "-fx-prompt-text-fill: #666;";
    final String FIELD_ERROR_STYLE = "-fx-prompt-text-fill: #666; -fx-border-color: #f38ba8; -fx-border-radius: 3;";

    TextField setIdField = new TextField();
    setIdField.setPromptText("e.g. char01_casual");
    setIdField.setStyle(FIELD_STYLE);

    TextField charIdField = new TextField();
    charIdField.setPromptText("e.g. char01");
    charIdField.setStyle(FIELD_STYLE);
    charIdField.setTooltip(new Tooltip(
        "The character identifier written into @charlayer and @charpreset lines.\n"
        + "Must match the character tag used in your scripts (e.g. char01)."));

    Label setIdError = new Label("Set ID is required.");
    setIdError.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 9px; -fx-padding: 1 0 0 2;");
    setIdError.setVisible(false);
    setIdError.setManaged(false);

    Label charIdError = new Label("Character ID is required — it becomes the first argument in every @charlayer line.");
    charIdError.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 9px; -fx-padding: 1 0 0 2;");
    charIdError.setVisible(false);
    charIdError.setManaged(false);

    // ── Snippet preview ──────────────────────────────────────────────────────
    TextArea snippetArea = new TextArea();
    snippetArea.setEditable(false);
    snippetArea.setPrefRowCount(6);
    snippetArea.setStyle("-fx-font-family: 'Consolas', 'Menlo', monospace; -fx-font-size: 10px;");

    Runnable updateSnippet = () -> {
      String charId = charIdField.getText().trim();
      if (charId.isBlank()) charId = "char";
      StringBuilder sb = new StringBuilder();
      Map<String, Integer> idCounts = new HashMap<>();
      List<String> firstLayerIds = new ArrayList<>();
      for (String grp : groupNames) {
        List<NewSetOption> opts = groupOptionMap.getOrDefault(grp, List.of());
        boolean first = true;
        for (NewSetOption opt : opts) {
          if (opt.relativePath.isBlank()) continue;
          String base = !opt.layerId.isBlank() ? sanitizeId(opt.layerId)
              : sanitizeId(grp) + "_" + sanitizeId(opt.label.isBlank() ? "variant" : opt.label);
          if (base.isBlank()) base = "layer";
          int n = idCounts.getOrDefault(base, 0);
          String lid = n == 0 ? base : base + n;
          idCounts.put(base, n + 1);
          sb.append("@charlayer ").append(charId).append(' ').append(lid).append(' ').append(opt.relativePath).append('\n');
          if (first) { firstLayerIds.add(lid); first = false; }
        }
      }
      if (!firstLayerIds.isEmpty()) {
        sb.append("@charpreset ").append(charId).append(" neutral");
        for (String lid : firstLayerIds) sb.append(" $").append(lid);
        sb.append('\n');
      }
      snippetArea.setText(sb.isEmpty() ? "# Add groups and options to see generated snippet" : sb.toString());
    };

    // ── Option rows ──────────────────────────────────────────────────────────
    VBox optionRowsBox = new VBox(4);
    optionRowsBox.setPadding(new Insets(4));
    Label rightTitle = new Label("Select a group to edit its options");
    rightTitle.setStyle("-fx-text-fill: #888; -fx-font-size: 10px; -fx-padding: 2 0 4 0;");

    Runnable[] rebuildOpts = {null};
    rebuildOpts[0] = () -> {
      optionRowsBox.getChildren().clear();
      String grp = selectedGroup[0];
      if (grp == null) return;
      List<NewSetOption> opts = groupOptionMap.computeIfAbsent(grp, k -> new ArrayList<>());
      rightTitle.setText("Options for: " + grp + "  (" + opts.size() + " option" + (opts.size() == 1 ? "" : "s") + ")");
      for (int i = 0; i < opts.size(); i++) {
        NewSetOption opt = opts.get(i);
        final int idx = i;

        Label numLabel = new Label(String.valueOf(i + 1));
        numLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 9px; -fx-min-width: 16; -fx-alignment: center-right;");

        TextField fileField = new TextField(opt.relativePath);
        fileField.setEditable(false);
        fileField.setPromptText("No file selected");
        fileField.setStyle("-fx-font-size: 9px; -fx-prompt-text-fill: #666;");
        HBox.setHgrow(fileField, Priority.ALWAYS);

        Button browseBtn = new Button("Browse…");
        browseBtn.setStyle("-fx-font-size: 9px;");
        browseBtn.setOnAction(ev -> {
          FileChooser fc = new FileChooser();
          fc.setTitle("Select layer image");
          fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
          if (projectRoot != null && projectRoot.isDirectory()) fc.setInitialDirectory(projectRoot);
          File chosen = fc.showOpenDialog(optionRowsBox.getScene().getWindow());
          if (chosen == null) return;
          opt.file = chosen;
          try {
            opt.relativePath = projectRoot != null
                ? projectRoot.toPath().relativize(chosen.toPath()).toString().replace('\\', '/')
                : chosen.getAbsolutePath();
          } catch (Exception ignored) {
            opt.relativePath = chosen.getAbsolutePath();
          }
          fileField.setText(opt.relativePath);
          if (opt.label.isBlank()) {
            String inferred = inferLabelFromFilenameForGroup(chosen.getName().replaceAll("\\.[^.]+$", ""), grp);
            opt.label = inferred.isBlank() ? chosen.getName().replaceAll("\\.[^.]+$", "") : inferred;
          }
          updateSnippet.run();
          rebuildOpts[0].run();
        });

        TextField labelField = new TextField(opt.label);
        labelField.setPromptText("Label");
        labelField.setPrefWidth(100);
        labelField.setStyle("-fx-prompt-text-fill: #666;");
        labelField.textProperty().addListener((o, ov, nv) -> { opt.label = nv; updateSnippet.run(); });

        TextField layerIdField = new TextField(opt.layerId);
        layerIdField.setPromptText("Layer ID (optional)");
        layerIdField.setPrefWidth(120);
        layerIdField.setStyle("-fx-prompt-text-fill: #666;");
        layerIdField.textProperty().addListener((o, ov, nv) -> { opt.layerId = nv; updateSnippet.run(); });

        Button upBtn = new Button("↑");
        upBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; -fx-padding: 2 4;");
        upBtn.setDisable(i == 0);
        upBtn.setOnAction(ev -> {
          if (idx > 0) { Collections.swap(opts, idx, idx - 1); rebuildOpts[0].run(); updateSnippet.run(); }
        });

        Button downBtn = new Button("↓");
        downBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888; -fx-padding: 2 4;");
        downBtn.setDisable(i == opts.size() - 1);
        downBtn.setOnAction(ev -> {
          if (idx < opts.size() - 1) { Collections.swap(opts, idx, idx + 1); rebuildOpts[0].run(); updateSnippet.run(); }
        });

        Button removeBtn = new Button("×");
        removeBtn.setStyle("-fx-text-fill: #f38ba8; -fx-background-color: transparent; -fx-font-size: 12px; -fx-padding: 0 4;");
        removeBtn.setOnAction(ev -> { opts.remove(idx); rebuildOpts[0].run(); updateSnippet.run(); });

        HBox row = new HBox(4, numLabel, fileField, browseBtn, labelField, layerIdField, upBtn, downBtn, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 4 6; -fx-background-color: #1e1e1e; -fx-background-radius: 3;");
        optionRowsBox.getChildren().add(row);
      }

      Button addOptBtn = new Button("+ Add Option");
      addOptBtn.setStyle("-fx-font-size: 10px;");
      addOptBtn.setOnAction(ev -> { opts.add(new NewSetOption()); rebuildOpts[0].run(); updateSnippet.run(); });
      optionRowsBox.getChildren().add(addOptBtn);
    };

    // ── Group list ───────────────────────────────────────────────────────────
    ListView<String> groupListView = new ListView<>(groupNames);
    groupListView.setPrefWidth(200);
    VBox.setVgrow(groupListView, Priority.ALWAYS);

    groupListView.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      selectedGroup[0] = nv;
      rebuildOpts[0].run();
    });

    TextField groupNameField = new TextField();
    groupNameField.setPromptText("Group name (e.g. eyes)");
    groupNameField.setStyle(FIELD_STYLE);
    HBox.setHgrow(groupNameField, Priority.ALWAYS);

    // Clicking a group populates the name field for easy rename
    groupListView.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      if (nv != null) groupNameField.setText(nv);
    });

    Runnable doAddOrRenameGroup = () -> {
      String raw = groupNameField.getText().trim();
      String name = sanitizeId(raw.isEmpty() ? raw : raw);
      if (name.isBlank()) return;
      String current = groupListView.getSelectionModel().getSelectedItem();
      if (name.equals(current)) return;
      if (groupNames.contains(name)) {
        // Just select the existing group
        groupListView.getSelectionModel().select(name);
        return;
      }
      if (current != null && groupNames.contains(current)) {
        // Rename: swap key in map, update name in list
        List<NewSetOption> opts = groupOptionMap.remove(current);
        groupOptionMap.put(name, opts != null ? opts : new ArrayList<>());
        int idx = groupNames.indexOf(current);
        groupNames.set(idx, name);
        if (Objects.equals(selectedGroup[0], current)) selectedGroup[0] = name;
        groupListView.getSelectionModel().select(name);
      } else {
        groupNames.add(name);
        groupOptionMap.put(name, new ArrayList<>());
        groupListView.getSelectionModel().select(name);
      }
      updateSnippet.run();
    };

    Button addGroupBtn = new Button("Add / Rename");
    addGroupBtn.setOnAction(e -> doAddOrRenameGroup.run());
    groupNameField.setOnAction(e -> doAddOrRenameGroup.run());

    Button removeGroupBtn = new Button("Remove");
    removeGroupBtn.setStyle("-fx-text-fill: #f38ba8;");
    removeGroupBtn.setOnAction(e -> {
      String name = groupListView.getSelectionModel().getSelectedItem();
      if (name == null) return;
      groupNames.remove(name);
      groupOptionMap.remove(name);
      if (Objects.equals(selectedGroup[0], name)) {
        selectedGroup[0] = null;
        optionRowsBox.getChildren().clear();
        rightTitle.setText("Select a group to edit its options");
      }
      updateSnippet.run();
    });

    Button groupUpBtn = new Button("↑");
    groupUpBtn.setOnAction(e -> {
      int idx = groupListView.getSelectionModel().getSelectedIndex();
      if (idx <= 0) return;
      String item = groupNames.remove(idx);
      groupNames.add(idx - 1, item);
      groupListView.getSelectionModel().select(idx - 1);
      updateSnippet.run();
    });

    Button groupDownBtn = new Button("↓");
    groupDownBtn.setOnAction(e -> {
      int idx = groupListView.getSelectionModel().getSelectedIndex();
      if (idx < 0 || idx >= groupNames.size() - 1) return;
      String item = groupNames.remove(idx);
      groupNames.add(idx + 1, item);
      groupListView.getSelectionModel().select(idx + 1);
      updateSnippet.run();
    });

    // ── Layout ───────────────────────────────────────────────────────────────
    setIdField.textProperty().addListener((o, ov, nv) -> {
      updateSnippet.run();
      if (!nv.trim().isBlank()) {
        setIdError.setVisible(false);
        setIdError.setManaged(false);
        setIdField.setStyle(FIELD_STYLE);
      }
    });
    charIdField.textProperty().addListener((o, ov, nv) -> {
      updateSnippet.run();
      if (!nv.trim().isBlank()) {
        charIdError.setVisible(false);
        charIdError.setManaged(false);
        charIdField.setStyle(FIELD_STYLE);
      }
    });

    // ── Help button content ──────────────────────────────────────────────────
    Button setIdHelp = helpButton("Set ID", """
        A unique identifier for this collection of layers within the Layered Image Visualizer.

        This is used to:
          • Track your selections and saved presets between sessions.
          • Identify this set in the tool's Set dropdown.

        It does not appear in generated script code.

        Naming tips:
          • Use a simple lowercase identifier, e.g. char01, char01_casual, npc_shopkeeper.
          • Letters, numbers, and underscores only.
          • Use something descriptive that helps you recognise the set in the dropdown.""");

    Button charIdHelp = helpButton("Character ID", """
        The character tag that appears in every @charlayer and @charpreset line generated for this set.

        Example output:
          @charlayer char01 eyes_happy images/char01/eyes/happy.png
                     ^^^^^^
          @charpreset char01 neutral $eyes_happy $mouth_smile
                      ^^^^^^

        This must match the character tag already used in your project scripts. \
        For example, if your scripts contain  @show char01 angry,  \
        the Character ID should be  char01.

        Tip: it is usually the part before the first underscore in the Set ID \
        — for example, char01 from char01_casual.""");

    Button groupsHelp = helpButton("Layer Groups", """
        Groups represent the individual body parts or visual layers of a character \
        (e.g. body, eyes, mouth, hair).

        Each group is a slot that holds multiple image options. In the visualizer, \
        each group appears as a thumbnail grid where you pick which variant to show.

        Render order: groups at the TOP of the list are drawn first (behind everything). \
        Groups at the BOTTOM are drawn last (on top of everything). For example:
          1. arm_behind  ← drawn first  → behind the body
          2. tail        ← drawn second → behind the body too
          3. body        ← drawn third  → covers arm_behind and tail
          4. arm_front   ← drawn last   → on top of body

        Tips:
          • Name groups with simple lowercase identifiers (eyes, mouth, hair).
          • Groups named bg or background are automatically treated as background layers \
        and excluded from certain snippet exports when foreground layers are active.
          • Type a name and press Enter or 'Add / Rename' to create a group.
          • Click a group to select it, change the name field, and press Enter to rename it.""");

    Button optionsHelp = helpButton("Layer Options", """
        Options are the individual image variants within a group.

        For example, if the group is 'eyes', options might be: happy, sad, angry, closed.

        Fields:
          • Label — The display name shown in the thumbnail grid. \
        Auto-inferred from the filename if left blank.
          • Layer ID — An optional identifier used in the generated @charlayer line. \
        If blank, it is auto-generated from the group name and label (e.g. eyes_happy). \
        Use this to override the generated ID with a specific value like e1 or eyes_a.
          • Browse — Pick the PNG/JPG/WebP image file for this option from your project directory.

        The top option in each group is used as the sample choice in the snippet preview.""");

    Button snippetHelp = helpButton("Generated Snippet", """
        This snippet shows the VNS script declarations that will be created for this layer set.

        @charlayer — Declares one image layer for a character:
          @charlayer <charId> <layerId> <imagePath>

        @charpreset — Defines a preset (a named combination of layers):
          @charpreset <charId> <presetName> $<layerId1> $<layerId2> ...

        The 'neutral' preset shown is a sample using the first option from each group.

        To make this set permanent (surviving project rescans):
          1. Click 'Copy Snippet' to copy the declarations.
          2. Paste them into a .vns script file in your project.
          3. Re-scan the catalog — the set will appear in the tool automatically.

        'Add to Tool' registers the set in memory for the current session only \
        — it will not persist after restarting the editor.""");

    // ── Layout ───────────────────────────────────────────────────────────────
    VBox setIdBox = new VBox(2, setIdField, setIdError);
    HBox.setHgrow(setIdBox, Priority.ALWAYS);
    VBox charIdBox = new VBox(2, charIdField, charIdError);
    HBox.setHgrow(charIdBox, Priority.ALWAYS);

    HBox setIdLabelRow = new HBox(3, new Label("Set ID:"), setIdHelp);
    setIdLabelRow.setAlignment(Pos.CENTER_LEFT);
    HBox charIdLabelRow = new HBox(3, new Label("Character ID:"), charIdHelp);
    charIdLabelRow.setAlignment(Pos.CENTER_LEFT);

    HBox headerRow = new HBox(8, setIdLabelRow, setIdBox, charIdLabelRow, charIdBox);
    headerRow.setAlignment(Pos.TOP_CENTER);
    headerRow.setPadding(new Insets(8, 8, 4, 8));

    HBox groupInputRow = new HBox(4, groupNameField, addGroupBtn);
    groupInputRow.setAlignment(Pos.CENTER_LEFT);
    HBox groupBtnRow = new HBox(4, groupUpBtn, groupDownBtn, removeGroupBtn);
    groupBtnRow.setAlignment(Pos.CENTER_LEFT);
    Label groupsHeaderLabel = new Label("Groups  (top = behind · bottom = in front)");
    groupsHeaderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 10px;");
    HBox groupsHeader = new HBox(4, groupsHeaderLabel, groupsHelp);
    groupsHeader.setAlignment(Pos.CENTER_LEFT);
    VBox leftPanel = new VBox(4, groupsHeader, groupListView, groupInputRow, groupBtnRow);
    leftPanel.setPadding(new Insets(8));
    VBox.setVgrow(groupListView, Priority.ALWAYS);

    HBox rightTitleRow = new HBox(4, rightTitle, optionsHelp);
    rightTitleRow.setAlignment(Pos.CENTER_LEFT);
    ScrollPane optionScroll = new ScrollPane(optionRowsBox);
    optionScroll.setFitToWidth(true);
    optionScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    VBox.setVgrow(optionScroll, Priority.ALWAYS);
    VBox rightPanel = new VBox(4, rightTitleRow, optionScroll);
    rightPanel.setPadding(new Insets(8));
    VBox.setVgrow(optionScroll, Priority.ALWAYS);

    SplitPane split = new SplitPane(leftPanel, rightPanel);
    split.setDividerPositions(0.28);
    SplitPane.setResizableWithParent(leftPanel, false);

    Label snippetHint = new Label("Generated snippet — copy this into your script or use \"Add to Tool\" to preview immediately:");
    snippetHint.setStyle("-fx-font-size: 9px; -fx-text-fill: #666;");
    Button copySnippetBtn = new Button("Copy Snippet");
    copySnippetBtn.setStyle("-fx-font-size: 10px;");
    copySnippetBtn.setOnAction(e -> {
      ClipboardContent cc = new ClipboardContent();
      cc.putString(snippetArea.getText());
      Clipboard.getSystemClipboard().setContent(cc);
    });
    HBox snippetHeader = new HBox(8, snippetHint, snippetHelp, copySnippetBtn);
    snippetHeader.setAlignment(Pos.CENTER_LEFT);

    Button addToToolBtn = new Button("Add to Tool");
    addToToolBtn.setStyle("-fx-background-color: #3a5a3a; -fx-text-fill: #9ed67a; -fx-font-weight: bold;");
    Tooltip.install(addToToolBtn, new Tooltip("Register this set in the visualizer so you can preview it immediately (not saved to disk)"));
    Button closeBtn = new Button("Close");
    Region btnSpacer = new Region();
    HBox.setHgrow(btnSpacer, Priority.ALWAYS);
    HBox actionRow = new HBox(8, addToToolBtn, btnSpacer, closeBtn);
    actionRow.setAlignment(Pos.CENTER_LEFT);
    actionRow.setPadding(new Insets(8));

    VBox bottomPane = new VBox(4, snippetHeader, snippetArea, actionRow);
    bottomPane.setPadding(new Insets(0, 8, 0, 8));

    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: #1a1a1a; -fx-font-size: 11px;");
    root.setTop(headerRow);
    root.setCenter(split);
    root.setBottom(bottomPane);

    // ── Stage ────────────────────────────────────────────────────────────────
    javafx.stage.Stage dialog = new javafx.stage.Stage();
    dialog.setTitle("New Layered Set");
    dialog.initOwner(getScene() != null ? getScene().getWindow() : null);
    dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
    javafx.scene.Scene dialogScene = new javafx.scene.Scene(root, 960, 620);
    if (getScene() != null) dialogScene.getStylesheets().addAll(getScene().getStylesheets());
    dialog.setScene(dialogScene);

    // ── Add to Tool ───────────────────────────────────────────────────────────
    addToToolBtn.setOnAction(e -> {
      String setId = setIdField.getText().trim();
      String charId = charIdField.getText().trim();
      boolean valid = true;

      if (setId.isBlank()) {
        setIdError.setVisible(true);
        setIdError.setManaged(true);
        setIdField.setStyle(FIELD_ERROR_STYLE);
        if (valid) setIdField.requestFocus();
        valid = false;
      }
      if (charId.isBlank()) {
        charIdError.setVisible(true);
        charIdError.setManaged(true);
        charIdField.setStyle(FIELD_ERROR_STYLE);
        if (valid) charIdField.requestFocus();
        valid = false;
      }
      if (!valid) return;

      boolean hasOptions = groupNames.stream().anyMatch(g -> {
        List<NewSetOption> opts = groupOptionMap.get(g);
        return opts != null && opts.stream().anyMatch(o -> !o.relativePath.isBlank());
      });
      if (!hasOptions) {
        status("Add at least one option with a file before adding to tool.");
        return;
      }
      LayeredSet newSet = new LayeredSet(setId);
      newSet.characterId = sanitizeId(charId);
      for (String grp : groupNames) {
        List<NewSetOption> opts = groupOptionMap.getOrDefault(grp, List.of());
        int order = 0;
        for (NewSetOption opt : opts) {
          if (opt.relativePath.isBlank()) continue;
          newSet.add(new LayerOption(
              opt.label.isBlank() ? opt.relativePath : opt.label,
              grp, opt.relativePath, opt.file, order++, opt.layerId));
        }
        if (!opts.isEmpty()) newSet.defaultGroupOrder.add(grp);
      }
      sets.put(setId, newSet);
      refreshSetOptions();
      applyingState = true;
      setBox.getSelectionModel().select(setId);
      applyingState = false;
      onSetSelectionChanged();
      status("Added set \"" + setId + "\" to tool.");
      dialog.close();
    });

    closeBtn.setOnAction(e -> dialog.close());
    updateSnippet.run();
    dialog.show();
  }

  private String uniquePresetKey(String setId, String baseName) {
    String seed = sanitizeId(baseName);
    if (seed.isBlank()) seed = "preset";

    String key = seed;
    int idx = 2;
    while (persisted.containsKey(presetPrefix(setId, key) + "name")) {
      key = seed + "_" + idx;
      idx++;
    }
    return key;
  }

  private void persistGlobalState() {
    if (projectRoot == null) return;
    captureSidebarDividerPosition();
    persisted.setProperty("global.filter", filterField.getText() == null ? "" : filterField.getText().trim());
    persisted.setProperty("global.charactersOnly", Boolean.toString(characterAssetsOnlyScan.isSelected()));
    String selectedSet = setBox.getValue();
    if (selectedSet != null && !selectedSet.isBlank()) {
      persisted.setProperty("global.selectedSet", selectedSet);
    }
    persisted.setProperty("global.sidebarCollapsed", Boolean.toString(sidebarCollapsed));
    persisted.setProperty("global.sidebarDivider", formatDouble(sidebarDividerPosition));
    File configuredExportDirectory = configuredExportDirectory();
    if (configuredExportDirectory == null) {
      persisted.remove("global.exportDir");
    } else {
      persisted.setProperty("global.exportDir", configuredExportDirectory.getAbsolutePath());
    }
    savePersistentState();
  }

  private void persistCurrentSetState() {
    if (projectRoot == null) return;
    if (currentSetId == null || currentSetId.isBlank()) return;
    captureStateToPrefix(statePrefix(currentSetId));
    savePersistentState();
  }

  private void flushPendingStateSave() {
    if (!stateSavePending) return;
    stateSaveDebounce.stop();
    stateSavePending = false;
    savePersistentStateNow();
  }

  private void captureStateToPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) return;

    clearPrefix(prefix + "sel.");
    clearPrefix(prefix + "active.");
    clearPrefix(prefix + "swap.");

    persisted.setProperty(prefix + "charId", sanitizeId(characterIdField.getText()));
    persisted.setProperty(prefix + "expr", sanitizeId(expressionField.getText()));
    persisted.setProperty(prefix + "autoExpr", Boolean.toString(autoExpression.isSelected()));
    persisted.setProperty(prefix + "randomActiveOnly", Boolean.toString(randomizeActiveOnly.isSelected()));
    persisted.setProperty(prefix + "matchGameFraming", Boolean.toString(matchGameFraming.isSelected()));
    persisted.setProperty(prefix + "attributeFilter", attributeFilterField.getText() == null ? "" : attributeFilterField.getText().trim());
    persisted.setProperty(prefix + "typedAttributes", typedAttributesField.getText() == null ? "" : typedAttributesField.getText().trim());
    persisted.setProperty(prefix + "typedRealtime", Boolean.toString(typedRealtime.isSelected()));
    persisted.setProperty(prefix + "shortforms", shortformsArea.getText() == null ? "" : shortformsArea.getText());
    persisted.setProperty(prefix + "focusX", formatDouble(focusXSlider.getValue()));
    persisted.setProperty(prefix + "focusY", formatDouble(focusYSlider.getValue()));
    persisted.setProperty(prefix + "crop", formatDouble(cropSlider.getValue()));
    persisted.setProperty(prefix + "zoom", formatDouble(zoomSlider.getValue()));
    persisted.setProperty(prefix + "groupOrder", encodeCsv(groupOrder));
    persisted.setProperty(prefix + "snippetFormat", snippetFormatBox.getValue() == null ? "" : snippetFormatBox.getValue());
    persisted.setProperty(prefix + "showOverlayGuides", Boolean.toString(showOverlayGuides.isSelected()));
    persisted.setProperty(prefix + "exportCustomName", Boolean.toString(customExportNameCheck.isSelected()));
    persisted.setProperty(prefix + "exportName", exportNameField.getText() == null ? "" : exportNameField.getText().trim());

    for (Map.Entry<String, ComboBox<LayerOption>> entry : selectors.entrySet()) {
      String group = entry.getKey();
      String groupKey = encodeKey(group);
      LayerOption option = entry.getValue().getValue();
      if (option != null && !option.isNone()) {
        persisted.setProperty(prefix + "sel." + groupKey, option.relativePath);
      }
      CheckBox active = activeGroupChecks.get(group);
      persisted.setProperty(prefix + "active." + groupKey, Boolean.toString(active == null || active.isSelected()));
      CheckBox swap = swapGroupChecks.get(group);
      persisted.setProperty(prefix + "swap." + groupKey, Boolean.toString(swap != null && swap.isSelected()));
    }
  }

  private void applyStateFromPrefix(String prefix) {
    applyingState = true;
    try {
      LayeredSet set = currentSet();
      String fallbackChar = set != null && set.characterId != null && !set.characterId.isBlank()
          ? sanitizeId(set.characterId)
          : sanitizeId(takeLastPathToken(currentSetId));
      String charId = persisted.getProperty(prefix + "charId", fallbackChar);
      String expr = persisted.getProperty(prefix + "expr", "");

      characterIdField.setText(charId);
      expressionField.setText(expr);
      autoExpression.setSelected(parseBoolean(persisted.getProperty(prefix + "autoExpr"), true));
      randomizeActiveOnly.setSelected(parseBoolean(persisted.getProperty(prefix + "randomActiveOnly"), true));
      matchGameFraming.setSelected(parseBoolean(persisted.getProperty(prefix + "matchGameFraming"), false));
      attributeFilterField.setText(persisted.getProperty(prefix + "attributeFilter", ""));
      typedAttributesField.setText(persisted.getProperty(prefix + "typedAttributes", ""));
      typedRealtime.setSelected(parseBoolean(persisted.getProperty(prefix + "typedRealtime"), true));
      shortformsArea.setText(persisted.getProperty(prefix + "shortforms", DEFAULT_SHORTFORMS));
      refreshShortforms();

      String snippetFormat = persisted.getProperty(prefix + "snippetFormat", "");
      if (!snippetFormat.isBlank() && snippetFormatBox.getItems().contains(snippetFormat)) {
        snippetFormatBox.getSelectionModel().select(snippetFormat);
      }
      showOverlayGuides.setSelected(parseBoolean(persisted.getProperty(prefix + "showOverlayGuides"), true));
      customExportNameCheck.setSelected(parseBoolean(persisted.getProperty(prefix + "exportCustomName"), false));
      exportNameField.setText(persisted.getProperty(prefix + "exportName", ""));

      focusXSlider.setValue(parseDouble(persisted.getProperty(prefix + "focusX"), focusXSlider.getValue()));
      focusYSlider.setValue(parseDouble(persisted.getProperty(prefix + "focusY"), focusYSlider.getValue()));
      cropSlider.setValue(parseDouble(persisted.getProperty(prefix + "crop"), cropSlider.getValue()));
      zoomSlider.setValue(parseDouble(persisted.getProperty(prefix + "zoom"), zoomSlider.getValue()));

      List<String> restoredOrder = decodeCsv(persisted.getProperty(prefix + "groupOrder", ""));
      if (!restoredOrder.isEmpty()) {
        List<String> normalized = new ArrayList<>();
        for (String group : restoredOrder) {
          if (selectors.containsKey(group) && !normalized.contains(group)) normalized.add(group);
        }
        for (String group : selectors.keySet()) {
          if (!normalized.contains(group)) normalized.add(group);
        }
        groupOrder.clear();
        groupOrder.addAll(normalized);
      }

      for (String group : selectors.keySet()) {
        ComboBox<LayerOption> combo = selectors.get(group);
        String groupKey = encodeKey(group);
        String selectedPath = persisted.getProperty(prefix + "sel." + groupKey, "");
        if (!selectedPath.isBlank()) {
          LayerOption selected = findByRelativePath(combo, selectedPath);
          if (selected != null) combo.getSelectionModel().select(selected);
        }
        CheckBox active = activeGroupChecks.get(group);
        if (active != null) {
          active.setSelected(parseBoolean(persisted.getProperty(prefix + "active." + groupKey), true));
        }
        CheckBox swap = swapGroupChecks.get(group);
        if (swap != null) {
          swap.setSelected(parseBoolean(persisted.getProperty(prefix + "swap." + groupKey), false));
        }
      }

      refreshGroupRows();

      if (autoExpression.isSelected() && (expressionField.getText() == null || expressionField.getText().isBlank())) {
        updateExpressionFromSelection();
      }
    } finally {
      applyingState = false;
    }
    redrawPreview();
  }

  private boolean hasStoredLayerSelections(String prefix) {
    if (prefix == null || prefix.isBlank()) return false;
    for (String key : persisted.stringPropertyNames()) {
      if (key.startsWith(prefix + "sel.")) return true;
    }
    return false;
  }

  private void applyDefaultProjectPreset(LayeredSet set) {
    if (set == null || set.projectPresets.isEmpty()) return;
    String presetName = set.defaultProjectPresetName();
    if (presetName == null || presetName.isBlank()) return;
    Map<String, String> selection = set.projectPresets.get(presetName);
    if (selection == null || selection.isEmpty()) return;

    applyingState = true;
    try {
      for (Map.Entry<String, String> entry : selection.entrySet()) {
        ComboBox<LayerOption> combo = selectors.get(entry.getKey());
        if (combo == null) continue;
        LayerOption option = findByRelativePath(combo, entry.getValue());
        if (option != null) {
          combo.getSelectionModel().select(option);
        }
      }
    } finally {
      applyingState = false;
    }
    updateExpressionFromSelection();
    redrawPreview();
  }

  private LayerOption findByRelativePath(ComboBox<LayerOption> combo, String relativePath) {
    if (combo == null || relativePath == null || relativePath.isBlank()) return null;
    for (LayerOption option : combo.getItems()) {
      if (option != null && !option.isNone() && relativePath.equals(option.relativePath)) {
        return option;
      }
    }
    return null;
  }

  private void loadPersistentState() {
    flushPendingStateSave();
    persisted.clear();
    Path file = statePath();
    if (file == null || !Files.exists(file)) return;
    try (InputStream in = Files.newInputStream(file)) {
      persisted.load(in);
    } catch (Exception ex) {
      status("Failed to load persistent state: " + ex.getMessage());
    }
  }

  private void reloadGameFramingSettings() {
    gameCharacterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
    gameCharacterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    try {
      VnUiStyleSpec style = VnUiLayoutLoader.loadFromProjectRootWithDiagnostics(projectRoot).style();
      if (style == null) return;
      gameCharacterHeightFactor = clamp(
          style.characterHeightFactor() == null ? DEFAULT_CHARACTER_HEIGHT_FACTOR : style.characterHeightFactor(),
          0.1,
          3.0
      );
      gameCharacterBaselineY = clamp(
          style.characterBaselineY() == null ? DEFAULT_CHARACTER_BASELINE_Y : style.characterBaselineY(),
          -0.5,
          2.0
      );
    } catch (Exception ex) {
      status("Failed to load game framing settings: " + ex.getMessage());
    }
  }

  private void savePersistentState() {
    stateSavePending = true;
    stateSaveDebounce.playFromStart();
  }

  private void savePersistentStateNow() {
    Path file = statePath();
    if (file == null) return;
    try {
      Path parent = file.getParent();
      if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
      try (OutputStream out = Files.newOutputStream(file)) {
        persisted.store(out, "JVN " + toolTitle + " State");
      }
    } catch (Exception ex) {
      status("Failed to save persistent state: " + ex.getMessage());
    }
  }

  private Path statePath() {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return projectRoot.toPath().resolve(stateFile);
  }

  private void clearPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) return;
    List<String> keys = new ArrayList<>(persisted.stringPropertyNames());
    for (String key : keys) {
      if (key.startsWith(prefix)) persisted.remove(key);
    }
  }

  private static String statePrefix(String setId) {
    return "state." + encodeKey(setId) + ".";
  }

  private static String presetPrefix(String setId, String presetKey) {
    return "preset." + encodeKey(setId) + "." + presetKey + ".";
  }

  private static String encodeKey(String raw) {
    if (raw == null || raw.isBlank()) return "_";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private static String encodeCsv(List<String> values) {
    if (values == null || values.isEmpty()) return "";
    StringBuilder out = new StringBuilder();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      if (out.length() > 0) out.append(',');
      out.append(encodeKey(value));
    }
    return out.toString();
  }

  private static List<String> decodeCsv(String csv) {
    List<String> out = new ArrayList<>();
    if (csv == null || csv.isBlank()) return out;
    String[] parts = csv.split(",");
    for (String part : parts) {
      String decoded = decodeKey(part.trim());
      if (!decoded.isBlank() && !out.contains(decoded)) out.add(decoded);
    }
    return out;
  }

  private static String decodeKey(String encoded) {
    if (encoded == null || encoded.isBlank() || "_".equals(encoded)) return "";
    try {
      byte[] bytes = Base64.getUrlDecoder().decode(encoded);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return "";
    }
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    if (raw == null) return fallback;
    String v = raw.trim().toLowerCase(Locale.ROOT);
    if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v)) return true;
    if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v)) return false;
    return fallback;
  }

  private static double parseDouble(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      double parsed = Double.parseDouble(raw.trim());
      return Double.isFinite(parsed) ? parsed : fallback;
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private void copy(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void status(String text) {
    statusLabel.setText(text == null ? "" : text);
  }

  private boolean isImageFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".png")
        || name.endsWith(".jpg")
        || name.endsWith(".jpeg")
        || name.endsWith(".webp");
  }

  private boolean isIgnoredPath(String relative) {
    String path = relative.toLowerCase(Locale.ROOT);
    return path.startsWith(".git/")
        || path.startsWith("build/")
        || path.startsWith(".gradle/")
        || path.contains("/build/")
        || path.startsWith("out/")
        || path.contains("/out/")
        || path.startsWith(".jvn/");
  }

  static boolean shouldIncludePathForScan(String relative, boolean charactersOnlyMode) {
    if (!charactersOnlyMode) return true;
    return isCharacterAssetPath(relative);
  }

  static boolean isCharacterAssetPath(String relative) {
    if (relative == null || relative.isBlank()) return false;
    String normalized = relative.replace('\\', '/').toLowerCase(Locale.ROOT);
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return normalized.startsWith("characters/")
        || normalized.contains("/characters/");
  }

  private LayerOption parseOption(String relative, File file) {
    if (relative == null || file == null) return null;
    String normalizedRelative = relative.replace('\\', '/');
    if (isIgnoredLayerPreviewPath(normalizedRelative)) return null;
    String fileName = file.getName();
    int dot = fileName.lastIndexOf('.');
    String base = dot > 0 ? fileName.substring(0, dot) : fileName;
    if (base.isBlank()) return null;

    String group = inferGroupFromSetSubfolder(normalizedRelative);
    String label;
    if (!group.isBlank()) {
      label = inferLabelFromFilenameForGroup(base, group);
    } else {
      InferredGroupLabel inferred = inferGroupAndLabelFromFilename(base);
      group = inferred.group();
      label = inferred.label();
    }
    if (group.isBlank()) {
      group = sanitizeId(takeLastPathToken(parentPath(normalizedRelative)));
    }
    if (group.isBlank()) group = "layer";
    if (label.isBlank()) label = "default";

    int sortKey = Integer.MAX_VALUE / 2;
    Matcher m = LEADING_NUMBER.matcher(base);
    if (m.find()) {
      try {
        sortKey = Integer.parseInt(m.group(1));
      } catch (NumberFormatException ignore) {
      }
    }
    return new LayerOption(label, group, normalizedRelative, file, sortKey, "");
  }

  private String deriveSetId(String relative) {
    return deriveSetIdFromRelative(relative);
  }

  static String deriveSetIdFromRelative(String relative) {
    String path = relative == null ? "" : relative.replace('\\', '/');
    String parent = parentPath(path);
    if (parent.isBlank()) return "(root)";

    String[] parts = parent.split("/");
    int characterIndex = findPathSegmentIndex(parts, "characters");
    if (characterIndex >= 0 && characterIndex + 1 < parts.length) {
      return String.join("/", java.util.Arrays.copyOfRange(parts, 0, characterIndex + 2));
    }
    if (parts.length >= 3 && "assets".equals(parts[0]) && "characters".equals(parts[1])) {
      return "assets/characters/" + parts[2];
    }
    if (parts.length >= 2 && "assets".equals(parts[0])) {
      return "assets/" + parts[1];
    }
    if (parts.length >= 2) {
      return parts[0] + "/" + parts[1];
    }
    return parent;
  }

  static String chooseSetSelection(String previous, String preferred, List<String> visibleSetIds) {
    return chooseSetSelection(previous, preferred, visibleSetIds, Map.of());
  }

  static String chooseSetSelection(String previous, String preferred, List<String> visibleSetIds, Map<String, Integer> groupCounts) {
    if (visibleSetIds == null || visibleSetIds.isEmpty()) return null;

    if (previous != null && !previous.isBlank() && visibleSetIds.contains(previous)) {
      return previous;
    }

    String firstCharacterSet = findFirstCharacterSet(visibleSetIds);
    String layeredDefault = findLayeredDefaultSet(visibleSetIds, groupCounts);
    if (preferred != null && !preferred.isBlank() && visibleSetIds.contains(preferred)) {
      if (isCharacterSetId(preferred) || firstCharacterSet == null) {
        if (isCharacterSetId(preferred)) return preferred;
        if (layeredSetSize(preferred, groupCounts) > 1) return preferred;
        return layeredDefault == null ? preferred : layeredDefault;
      }
      return firstCharacterSet;
    }

    if (firstCharacterSet != null) {
      return firstCharacterSet;
    }
    if (layeredDefault != null) {
      return layeredDefault;
    }
    return visibleSetIds.get(0);
  }

  static boolean isCharacterSetId(String setId) {
    if (setId == null || setId.isBlank()) return false;
    String normalized = setId.replace('\\', '/').toLowerCase(Locale.ROOT);
    return normalized.startsWith("characters/")
        || normalized.contains("/characters/");
  }

  private static int findPathSegmentIndex(String[] parts, String segment) {
    if (parts == null || segment == null || segment.isBlank()) return -1;
    for (int i = 0; i < parts.length; i++) {
      if (segment.equalsIgnoreCase(parts[i])) return i;
    }
    return -1;
  }

  private static String findFirstCharacterSet(List<String> visibleSetIds) {
    if (visibleSetIds == null) return null;
    for (String id : visibleSetIds) {
      if (isCharacterSetId(id)) return id;
    }
    return null;
  }

  private static String findLayeredDefaultSet(List<String> visibleSetIds, Map<String, Integer> groupCounts) {
    if (visibleSetIds == null || visibleSetIds.isEmpty()) return null;
    int bestGroups = 1;
    String best = null;
    for (String id : visibleSetIds) {
      int groups = layeredSetSize(id, groupCounts);
      if (groups > bestGroups) {
        bestGroups = groups;
        best = id;
      }
    }
    return best;
  }

  private static int layeredSetSize(String setId, Map<String, Integer> groupCounts) {
    if (setId == null || groupCounts == null) return 0;
    Integer size = groupCounts.get(setId);
    return size == null ? 0 : Math.max(0, size);
  }

  static String inferGroupFromSetSubfolder(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return "";
    String normalized = relativePath.replace('\\', '/');
    String parent = parentPath(normalized);
    if (parent.isBlank()) return "";
    String setId = deriveSetIdFromRelative(normalized);
    if (setId == null || setId.isBlank() || "(root)".equals(setId)) return "";
    String prefix = setId + "/";
    if (!parent.startsWith(prefix)) return "";
    String remainder = parent.substring(prefix.length());
    if (remainder.isBlank()) return "";
    String[] rawSegments = remainder.split("/");
    List<String> segments = new ArrayList<>();
    for (String segment : rawSegments) {
      String normalizedSegment = sanitizeId(segment);
      if (!normalizedSegment.isBlank()) segments.add(normalizedSegment);
    }
    if (segments.isEmpty()) return "";
    if (segments.size() == 1) return segments.get(0);

    String first = segments.get(0);
    String second = segments.get(1);

    // Keep nested arm variants in one group to avoid accidental stacked overlays.
    if (first.startsWith("arm")) {
      return first;
    }

    // Body folders often nest tail/body-arms under body.
    if ("body".equals(first)) {
      if (second.startsWith("tail")) return second;
      if (second.contains("arm")) return "body_arms";
      return first;
    }

    // Head folders can include style buckets (normal/tilted) before actual groups.
    if ("head".equals(first)) {
      if (("normal".equals(second) || "tilted".equals(second)) && segments.size() >= 3) {
        return segments.get(2);
      }
      return second;
    }

    return first;
  }

  private Map<String, LayeredSet> buildDeclaredLayeredSets(File rootDir, LayeredCharacterProjectCatalog.Catalog catalog) {
    Map<String, LayeredSet> out = new LinkedHashMap<>();
    if (rootDir == null || catalog == null || catalog.setsById().isEmpty()) return out;
    for (LayeredCharacterProjectCatalog.DeclaredSet declaredSet : catalog.setsById().values()) {
      if (declaredSet == null || declaredSet.groups().isEmpty()) continue;
      LayeredSet set = new LayeredSet(declaredSet.setId());
      set.scriptBacked = true;
      set.characterId = declaredSet.characterId();
      set.defaultGroupOrder.addAll(declaredSet.groupOrder());
      for (List<LayeredCharacterProjectCatalog.DeclaredOption> options : declaredSet.groups().values()) {
        for (LayeredCharacterProjectCatalog.DeclaredOption option : options) {
          if (option == null || option.relativePath() == null || option.relativePath().isBlank()) continue;
          if (isIgnoredLayerPreviewPath(option.relativePath())) continue;
          File file = resolveProjectAssetFile(rootDir, option.relativePath());
          set.add(new LayerOption(
              option.label(),
              option.groupId(),
              option.relativePath(),
              file,
              option.order(),
              option.layerId()));
        }
      }
      for (LayeredCharacterProjectCatalog.DeclaredPreset preset : declaredSet.presets().values()) {
        if (preset == null || preset.name() == null || preset.name().isBlank()) continue;
        set.projectPresets.put(preset.name(), new LinkedHashMap<>(preset.selectionsByGroup()));
      }
      out.put(set.id, set);
    }
    return out;
  }

  private static File resolveProjectAssetFile(File projectRoot, String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return null;
    try {
      Path candidate = Path.of(relativePath);
      if (candidate.isAbsolute()) return candidate.normalize().toFile();
    } catch (Exception ignore) {
    }
    return projectRoot.toPath().resolve(relativePath).normalize().toFile();
  }

  private static int countLayerOptions(Map<String, LayeredSet> sets) {
    if (sets == null || sets.isEmpty()) return 0;
    int count = 0;
    for (LayeredSet set : sets.values()) {
      if (set == null) continue;
      for (List<LayerOption> options : set.groups.values()) {
        if (options != null) count += options.size();
      }
    }
    return count;
  }

  static boolean isIgnoredLayerPreviewPath(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return false;
    String normalized = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
    return normalized.contains("/null/")
        || normalized.endsWith("_null.png")
        || normalized.endsWith("_null_ref.png")
        || normalized.contains("_null_ref.")
        || normalized.contains("/null_");
  }

  static String inferLabelFromFilenameForGroup(String baseName, String group) {
    String[] tokens = splitTokens(baseName);
    if (tokens.length == 0) return "";
    String normalizedGroup = sanitizeId(group);
    int match = -1;
    for (int i = 0; i < tokens.length; i++) {
      String tokenGroup = normalizeGroupToken(tokens[i]);
      if (!tokenGroup.isBlank() && tokenGroup.equals(normalizedGroup)) {
        match = i;
      }
    }
    if (match >= 0 && match + 1 < tokens.length) {
      return sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, match + 1, tokens.length)));
    }
    if (match >= 0) {
      return sanitizeLabel(tokens[match]);
    }
    return sanitizeLabel(tokens[tokens.length - 1]);
  }

  private static InferredGroupLabel inferGroupAndLabelFromFilename(String baseName) {
    String[] tokens = splitTokens(baseName);
    if (tokens.length == 0) {
      return new InferredGroupLabel("", "");
    }

    int groupTokenIndex = -1;
    String normalizedGroup = "";
    for (int i = 0; i < tokens.length; i++) {
      String mapped = normalizeGroupToken(tokens[i]);
      if (!mapped.isBlank()) {
        groupTokenIndex = i;
        normalizedGroup = mapped;
      }
    }
    if (groupTokenIndex >= 0) {
      return new InferredGroupLabel(normalizedGroup, inferLabelFromFilenameForGroup(baseName, normalizedGroup));
    }

    if (tokens.length >= 2) {
      String group = sanitizeId(tokens[0]);
      String label = sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(tokens, 1, tokens.length)));
      return new InferredGroupLabel(group, label);
    }

    return new InferredGroupLabel("", sanitizeLabel(tokens[0]));
  }

  private static String[] splitTokens(String raw) {
    if (raw == null || raw.isBlank()) return new String[0];
    return raw.split("[\\s._-]+");
  }

  private static String normalizeGroupToken(String rawToken) {
    String token = sanitizeId(rawToken);
    if (token.isBlank()) return "";
    return GROUP_TOKEN_ALIASES.getOrDefault(token, "");
  }

  static Map<String, String> parseAttributeAssignments(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return out;
    String[] tokens = raw.split("[,\\s]+");
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      String[] kv;
      if (part.contains("=")) {
        kv = part.split("=", 2);
      } else if (part.contains(":")) {
        kv = part.split(":", 2);
      } else if (part.contains("_")) {
        kv = part.split("_", 2);
      } else {
        continue;
      }
      if (kv.length != 2) continue;
      String group = sanitizeId(kv[0]);
      String value = sanitizeId(kv[1]);
      if (group.isBlank() || value.isBlank()) continue;
      out.put(group, value);
    }
    return out;
  }

  static Map<String, String> parseAttributeShortforms(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) return out;
    String[] lines = raw.split("\\R");
    for (String line : lines) {
      if (line == null) continue;
      String work = line.trim();
      if (work.isBlank() || work.startsWith("#")) continue;
      int eq = work.indexOf('=');
      if (eq <= 0) continue;
      String key = sanitizeId(work.substring(0, eq));
      String value = work.substring(eq + 1).trim();
      if (key.isBlank() || value.isBlank()) continue;
      out.put(key, value);
    }
    return out;
  }

  private static Slider slider(double min, double max, double value) {
    Slider s = new Slider(min, max, value);
    s.setBlockIncrement(1);
    s.setMajorTickUnit((max - min) / 4.0);
    s.setMinorTickCount(4);
    return s;
  }

  private static HBox sliderRow(String label, Slider slider) {
    Label left = new Label(label);
    left.setMinWidth(52);
    Label value = new Label((int) slider.getValue() + "%");
    slider.valueProperty().addListener((o, ov, nv) -> value.setText((int) Math.round(nv.doubleValue()) + "%"));
    HBox row = new HBox(8, left, slider, value);
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(slider, Priority.ALWAYS);
    return row;
  }

  static String sanitizeId(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  static String sanitizeLabel(String raw) {
    if (raw == null) return "";
    String s = raw.trim().replaceAll("[\\s]+", "_");
    s = s.replaceAll("[^a-zA-Z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  static String parentPath(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash <= 0 ? "" : path.substring(0, slash);
  }

  static String takeLastPathToken(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }

  private static void drawCheckerBackground(GraphicsContext g, double w, double h) {
    double size = 18.0;
    Color c1 = Color.web("#171d28");
    Color c2 = Color.web("#20293a");
    for (int y = 0; y <= h / size + 1; y++) {
      for (int x = 0; x <= w / size + 1; x++) {
        boolean odd = ((x + y) & 1) == 1;
        g.setFill(odd ? c1 : c2);
        g.fillRect(x * size, y * size, size, size);
      }
    }
  }

  private static void drawCenteredText(GraphicsContext g, double w, double h, String text) {
    g.setFill(Color.color(1, 1, 1, 0.65));
    g.setTextAlign(TextAlignment.CENTER);
    g.fillText(text, w / 2.0, h / 2.0);
  }

  private record ViewportFrame(
      double maxW,
      double maxH,
      double srcX,
      double srcY,
      double srcW,
      double srcH,
      double destX,
      double destY,
      double destW,
      double destH) {
    boolean valid() {
      return maxW > 0 && maxH > 0 && srcW > 0 && srcH > 0 && destW > 0 && destH > 0;
    }
  }

  private record LayeredCatalogScanResult(
      Map<String, LayeredSet> sets,
      int imageCount,
      boolean cancelled,
      boolean charactersOnly
  ) {
    static LayeredCatalogScanResult cancelledResult(boolean charactersOnly) {
      return new LayeredCatalogScanResult(Map.of(), 0, true, charactersOnly);
    }
  }

  private static final class LayeredSet {
    final String id;
    String characterId;
    boolean scriptBacked;
    final Map<String, List<LayerOption>> groups = new LinkedHashMap<>();
    final List<String> defaultGroupOrder = new ArrayList<>();
    final Map<String, Map<String, String>> projectPresets = new LinkedHashMap<>();

    LayeredSet(String id) {
      this.id = Objects.requireNonNullElse(id, "(set)");
    }

    void add(LayerOption option) {
      if (option == null) return;
      groups.computeIfAbsent(option.group, k -> {
        if (!defaultGroupOrder.contains(option.group)) {
          defaultGroupOrder.add(option.group);
        }
        return new ArrayList<>();
      }).add(option);
    }

    String defaultProjectPresetName() {
      if (projectPresets.isEmpty()) return "";
      for (String key : List.of("neutral", "default", "idle", "normal")) {
        for (String presetName : projectPresets.keySet()) {
          if (key.equalsIgnoreCase(presetName)) return presetName;
        }
      }
      return projectPresets.keySet().iterator().next();
    }
  }

  private static final class LayerOption {
    final String label;
    final String group;
    final String relativePath;
    final File file;
    final int sortKey;
    final String layerId;

    LayerOption(String label, String group, String relativePath, File file, int sortKey, String layerId) {
      this.label = label;
      this.group = group;
      this.relativePath = relativePath;
      this.file = file;
      this.sortKey = sortKey;
      this.layerId = layerId == null ? "" : layerId;
    }

    static LayerOption none() {
      return new LayerOption(NONE_LABEL, "none", "", null, Integer.MIN_VALUE, "");
    }

    boolean isNone() {
      return file == null;
    }
  }

  private record LayerSelection(String group, LayerOption option) {}

  private record CharpresetSnippet(String declarations, String presetLine, List<String> layerRefs) {}

  private record InferredGroupLabel(String group, String label) {}

  // ── File export / import ──

  private WritableImage buildCompositeExportImage() {
    List<LayerOption> selected = selectedLayers();
    if (selected.isEmpty()) {
      status("No layers selected — nothing to export.");
      return null;
    }
    List<Image> layers = new ArrayList<>();
    double maxW = 0, maxH = 0;
    for (LayerOption option : selected) {
      Image img = loadImage(option);
      if (img == null || img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) continue;
      layers.add(img);
      if (img.getWidth() > maxW) maxW = img.getWidth();
      if (img.getHeight() > maxH) maxH = img.getHeight();
    }
    if (layers.isEmpty()) {
      status("Selected layer images could not be loaded.");
      return null;
    }
    Canvas offscreen = new Canvas(maxW, maxH);
    GraphicsContext g = offscreen.getGraphicsContext2D();
    for (Image img : layers) {
      g.drawImage(img, 0, 0, img.getWidth(), img.getHeight());
    }
    return offscreen.snapshot(new SnapshotParameters() {{
      setFill(Color.TRANSPARENT);
    }}, null);
  }

  private void quickExportCompositePng() {
    WritableImage snapshot = buildCompositeExportImage();
    if (snapshot == null) return;
    File file = resolveQuickExportFile(buildCompositePngFileName());
    if (file == null) {
      status("Choose an export folder first.");
      return;
    }
    writeCompositePng(snapshot, file);
  }

  private void exportCompositePngAs() {
    WritableImage snapshot = buildCompositeExportImage();
    if (snapshot == null) return;
    File file = chooseSaveFile("Export Composited PNG", "PNG Image", "*.png", buildCompositePngFileName());
    if (file == null) return;
    writeCompositePng(snapshot, file);
  }

  private void writeCompositePng(WritableImage snapshot, File file) {
    writeCompositePng(snapshot, file, true);
  }

  private boolean writeCompositePng(WritableImage snapshot, File file, boolean reportSuccess) {
    try {
      Path parent = file.toPath().getParent();
      if (parent != null) Files.createDirectories(parent);
      BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
      ImageIO.write(bufferedImage, "png", file);
      setConfiguredExportDirectory(file.getParentFile());
      if (reportSuccess) {
        status("Exported PNG: " + describePathRelativeToProject(file));
      }
      return true;
    } catch (Exception ex) {
      status("PNG export failed: " + ex.getMessage());
      return false;
    }
  }

  private void quickExportSetupToFile() {
    File file = resolveQuickExportFile(buildLayerSetupFileName());
    if (file == null) {
      status("Choose an export folder first.");
      return;
    }
    writeLayerSetupFile(file, buildFullSetupText());
  }

  private void exportSetupToFileAs() {
    File file = chooseSaveFile("Export Layer Setup", "Layer Setup", "*.layersetup", buildLayerSetupFileName());
    if (file == null) return;
    writeLayerSetupFile(file, buildFullSetupText());
  }

  private void writeLayerSetupFile(File file, String text) {
    writeLayerSetupFile(file, text, true);
  }

  private boolean writeLayerSetupFile(File file, String text, boolean reportSuccess) {
    try {
      Path parent = file.toPath().getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
      setConfiguredExportDirectory(file.getParentFile());
      if (reportSuccess) {
        status("Exported setup: " + describePathRelativeToProject(file));
      }
      return true;
    } catch (Exception ex) {
      status("Setup export failed: " + ex.getMessage());
      return false;
    }
  }

  private void quickExportBundle() {
    WritableImage snapshot = buildCompositeExportImage();
    if (snapshot == null) return;
    File pngFile = resolveQuickExportFile(buildCompositePngFileName());
    File setupFile = resolveQuickExportFile(buildLayerSetupFileName());
    if (pngFile == null || setupFile == null) {
      status("Choose an export folder first.");
      return;
    }
    boolean pngOk = writeCompositePng(snapshot, pngFile, false);
    boolean setupOk = writeLayerSetupFile(setupFile, buildFullSetupText(), false);
    if (pngOk && setupOk) {
      status("Exported PNG + setup to " + describePathRelativeToProject(pngFile.getParentFile()));
    } else if (pngOk || setupOk) {
      status("Partially exported bundle. Check the target folder and previous export messages.");
    }
  }

  private File chooseSaveFile(String title, String description, String pattern, String suggestedName) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(title);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, pattern));
    File initial = resolveExistingExportDirectory();
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    chooser.setInitialFileName(suggestedName);
    return chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
  }

  private void importSetupFromFile() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Import Layer Setup");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Layer Setup", "*.layersetup"));
    File initial = resolveExistingExportDirectory();
    if (initial != null && initial.isDirectory()) fc.setInitialDirectory(initial);
    File file = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
    if (file == null) return;
    try {
      List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
      Map<String, String> assignments = new LinkedHashMap<>();
      for (String line : lines) {
        if (line == null) continue;
        String work = line.trim();
        if (work.isBlank() || work.startsWith("#")) continue;
        int eq = work.indexOf('=');
        if (eq <= 0) continue;
        String key = work.substring(0, eq).trim();
        String value = work.substring(eq + 1).trim();
        if (key.isBlank() || value.isBlank()) continue;
        if (key.startsWith("layer.")) {
          String groupName = key.substring("layer.".length());
          assignments.put(groupName, value);
        }
      }
      if (assignments.isEmpty()) {
        status("No layer assignments found in file.");
        return;
      }
      int applied = 0;
      applyingState = true;
      try {
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
          String group = entry.getKey();
          ComboBox<LayerOption> combo = selectors.get(group);
          if (combo == null) {
            for (String g : selectors.keySet()) {
              if (sanitizeId(g).equals(sanitizeId(group))) {
                combo = selectors.get(g);
                break;
              }
            }
          }
          if (combo == null) continue;
          LayerOption target = findByRelativePath(combo, entry.getValue());
          if (target == null) {
            for (LayerOption opt : combo.getItems()) {
              if (opt != null && !opt.isNone() && opt.label.equals(entry.getValue())) {
                target = opt;
                break;
              }
            }
          }
          if (target != null) {
            combo.getSelectionModel().select(target);
            applied++;
          }
        }
      } finally {
        applyingState = false;
      }
      updateExpressionFromSelection();
      redrawPreview();
      persistCurrentSetState();
      setConfiguredExportDirectory(file.getParentFile());
      status("Imported " + applied + " layer(s) from " + file.getName());
    } catch (Exception ex) {
      status("Setup import failed: " + ex.getMessage());
    }
  }

  private String buildFullSetupText() {
    StringBuilder out = new StringBuilder();
    out.append("# JVN Layered Image Visualizer Setup\n");
    out.append("#\n");
    out.append("# This file records a layer selection from the Layered Image Visualizer.\n");
    out.append("# To restore this configuration, open the visualizer in the JVN editor\n");
    out.append("# and click the Import button (folder icon in the file-ops toolbar),\n");
    out.append("# then choose this .layersetup file. The visualizer will match each\n");
    out.append("# layer.<group> entry to the corresponding group selector and select\n");
    out.append("# the image whose path (or label) matches the stored value.\n");
    out.append("#\n");
    out.append("# Note: .layersetup files are editor-only; they are not used at runtime.\n");
    out.append('\n');
    out.append("set=").append(currentSetId == null ? "" : currentSetId).append('\n');
    out.append("characterId=").append(sanitizeId(characterIdField.getText())).append('\n');
    out.append("expression=").append(sanitizeId(expressionField.getText())).append('\n');
    out.append('\n');
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo != null ? combo.getValue() : null;
      String selection = (option == null || option.isNone()) ? "(none)" : option.relativePath;
      String label = (option == null || option.isNone()) ? "(none)" : option.label;
      out.append("layer.").append(group).append('=').append(selection).append('\n');
      out.append("# label: ").append(label).append('\n');
    }
    return out.toString();
  }

  private static class NewSetOption {
    File file;
    String relativePath = "";
    String label = "";
    String layerId = "";
  }

}
