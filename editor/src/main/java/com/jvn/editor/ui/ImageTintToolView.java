package com.jvn.editor.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/**
 * Standalone image tint utility inspired by Ren'Py's Image Tint Tool.
 * This tool is intentionally independent from layered/image-attributes tools.
 * It focuses on per-image tinting and zone-based tint areas, which can be used in combination with layered setups but do not require them.
 * The tool scans the project for image files and charpreset tags, allowing you to quickly apply tints to character images and export the results
 * as PNG files or tint setup files. The tint setups can be loaded back into the tool or used as a reference for manual tinting in layered/image-attributes tools.
 */

public class ImageTintToolView extends BorderPane implements ImageToolPanel {
  private static final String STATE_FILE = ".jvn/image-tint-tool.properties";
  private static final String TOOL_TITLE = "Image Tint Tool";
  private static final String PRESET_TAG_PREFIX = "preset:";
  private static final String ASSET_SCOPE_CHARACTER_PRESETS_ONLY = "Charpresets only";
  private static final String ASSET_SCOPE_CHARACTER_ASSETS_ONLY = "Character assets only";
  private static final String ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS = "Character assets + charpresets";
  private static final String ASSET_SCOPE_ALL_ASSETS_AND_PRESETS = "All image assets + charpresets";
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^\\s*@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

  private static final String DEFAULT_EXPORT_PROFILE = "Tint Profile";
  private static final String DEFAULT_EXPORT_SETUP = "Full Setup";
  private static final String LEGACY_ZONE_PREFIX = "zone.";
  private static final String ZONE_PROFILE_PREFIX = "zone.profile.";
  private static final String GLOBAL_ZONE_PROFILE_KEY = "_global_";

  private final Label summaryLabel = new Label("Open a project to scan image tags.");
  private final Label statusLabel = new Label("");
  private final Label previewInfoLabel = new Label("No character image selected.");
  private final Label interactionHintLabel = new Label("Drag preview to pan, scroll to zoom, double-click to reset.");

  private final TextField filterField = new TextField();
  private final ComboBox<String> assetScopeBox = new ComboBox<>();
  private final ComboBox<String> characterTagBox = new ComboBox<>();
  private final ComboBox<String> backgroundTagBox = new ComboBox<>();
  private final ComboBox<String> setupBox = new ComboBox<>();
  private final TextField setupNameField = new TextField();
  private final ComboBox<String> exportFormatBox = new ComboBox<>();
  private final ColorPicker tintColorPicker = new ColorPicker(Color.web("#ffffff"));
  private final Slider tintStrengthSlider = slider(0, 100, 30);
  private final Slider saturationSlider = slider(-100, 100, 0);
  private final Slider contrastSlider = slider(-100, 100, 0);
  private final Region tintColorSwatch = new Region();
  private final ColorPicker bgTintColorPicker = new ColorPicker(Color.web("#ffffff"));
  private final Slider bgTintStrengthSlider = slider(0, 100, 0);
  private final Slider bgSaturationSlider = slider(-100, 100, 0);
  private final Slider bgContrastSlider = slider(-100, 100, 0);
  private final ComboBox<String> bgTintBlendModeBox = new ComboBox<>();
  private final ColorPicker bgOverlayColorPicker = new ColorPicker(Color.web("#000000"));
  private final Slider bgOverlayOpacitySlider = slider(0, 100, 0);
  private final ComboBox<String> bgOverlayBlendModeBox = new ComboBox<>();
  private final Region bgTintColorSwatch = new Region();
  private final Region bgOverlayColorSwatch = new Region();
  private final VBox backgroundControlsSection = new VBox(8);
  private TitledPane backgroundPane;

  private final Canvas previewCanvas = new Canvas(320, 240);
  private final LoadingProgressOverlay previewLoadingOverlay = new LoadingProgressOverlay();
  private final VBox controlsSection = new VBox(8);
  private TitledPane controlsPane;

  private final Map<String, File> imageByTag = new LinkedHashMap<>();
  private final Map<String, PresetTagEntry> presetByTag = new LinkedHashMap<>();
  private final Map<String, String> setupNameToKey = new LinkedHashMap<>();
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Properties persisted = new Properties();
  private final PauseTransition stateSaveDebounce = new PauseTransition(Duration.millis(250));

  private File projectRoot;
  private Runnable fullscreenToggleHandler;
  private boolean fullscreenActive;
  private Button fullscreenButton;
  private Button refreshCatalogButton;
  private boolean applyingState;
  private boolean stateSavePending;
  private Task<TintCatalogScanResult> scanTask;
  private Task<Image> backgroundTintTask;
  private String backgroundTintTaskTag;
  private String backgroundTintTaskKey;
  private long backgroundTintTaskSerial;
  private boolean disposed;
  private boolean backgroundFxSliderDragging;
  private boolean backgroundFxSliderCommitPending;
  private String activeZoneProfileTag = "";

  private boolean dragging;
  private double dragLastX;
  private double dragLastY;
  private double zoom = 1.0;
  private double offsetX;
  private double offsetY;

  private String tintedImageTag;
  private String tintedImageKey;
  private Image tintedImage;
  private String tintedBackgroundTag;
  private String tintedBackgroundKey;
  private Image tintedBackground;

  private record BackgroundTintParams(
      double tintStrength,
      double satAdjust,
      double conAdjust,
      double tr,
      double tg,
      double tb,
      String tintBlendMode,
      int tintBlendIndex,
      double overlayOpacity,
      double or,
      double og,
      double ob,
      String overlayBlendMode,
      int overlayBlendIndex
  ) {
    boolean isIdentity() {
      return Math.abs(tintStrength) < 1e-8
          && Math.abs(satAdjust) < 1e-8
          && Math.abs(conAdjust) < 1e-8
          && Math.abs(overlayOpacity) < 1e-8;
    }
  }

  // ── Zone area selector ──
  private static final String[] BLEND_MODES = {
      "Normal",
      "Multiply",
      "Screen",
      "Overlay",
      "Soft Light",
      "Hard Light",
      "Color Dodge",
      "Color Burn",
      "Difference",
      "Exclusion",
      "Lighten",
      "Darken",
      "Add",
      "Subtract"
  };
  private static final double FREEHAND_CAPTURE_MIN_DISTANCE = 1.4;
  private static final double FREEHAND_CLOSE_DISTANCE = 12.0;
  private static final double FREEHAND_RESAMPLE_STEP = 2.4;
  private static final double FREEHAND_SPLINE_STEP = 1.8;
  private static final double FREEHAND_SIMPLIFY_EPSILON = 0.9;
  private static final int FREEHAND_MAX_VERTICES = 220;
  private final List<TintZone> tintZones = new ArrayList<>();
  private int selectedZoneIndex = -1;
  private boolean zoneDrawMode;       // rectangle draw mode
  private boolean polyDrawMode;       // point-nail polygon draw mode
  private boolean freehandDrawMode;   // hold-drag freehand/lasso draw mode
  private boolean drawingZone;
  private boolean drawingFreehand;
  private double zoneDrawStartX;
  private double zoneDrawStartY;
  private double zoneDrawEndX;
  private double zoneDrawEndY;
  private final List<double[]> nailPoints = new ArrayList<>(); // canvas coords for polygon nailing
  private final List<double[]> freehandPoints = new ArrayList<>(); // canvas coords for freehand stroke

  private final ListView<TintZone> zoneListView = new ListView<>();
  private final ColorPicker zoneColorPicker = new ColorPicker(Color.web("#ff8844"));
  private final Slider zoneStrengthSlider = slider(0, 100, 50);
  private final Slider zoneSaturationSlider = slider(-100, 100, 0);
  private final Slider zoneContrastSlider = slider(-100, 100, 0);
  private final Slider zoneFeatherSlider = slider(0, 100, 15);
  private final Slider zoneRotationSlider = slider(-180, 180, 0);
  private final ComboBox<String> zoneBlendModeBox = new ComboBox<>();
  private final TextField zoneNameField = new TextField();
  private final Region zoneColorSwatch = new Region();
  private final VBox zoneControlsSection = new VBox(6);
  private TitledPane zonesPane;
  private Button zoneDrawToggleButton;
  private Button polyDrawToggleButton;
  private Button freehandDrawToggleButton;

  public ImageTintToolView() {

    stateSaveDebounce.setOnFinished(e -> flushPendingStateSave());
    setPadding(new Insets(8));
    buildUi();
    refreshCatalog();
  }

  private void buildUi() {
    Label title = new Label(TOOL_TITLE);
    title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
    summaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #8899aa;");
    summaryLabel.setWrapText(true);
    interactionHintLabel.setStyle("-fx-text-fill: #aeb6c7; -fx-font-size: 10px;");
    interactionHintLabel.setWrapText(true);
    previewInfoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #99aabb;");
    statusLabel.setStyle("-fx-font-size: 10px;");
    statusLabel.setWrapText(true);

    filterField.setPromptText("Filter tags...");
    filterField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshTagLists();
      persistGlobalState();
    });
    assetScopeBox.getItems().setAll(
        ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS,
        ASSET_SCOPE_CHARACTER_ASSETS_ONLY,
        ASSET_SCOPE_CHARACTER_PRESETS_ONLY,
        ASSET_SCOPE_ALL_ASSETS_AND_PRESETS);
    assetScopeBox.getSelectionModel().select(ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS);
    assetScopeBox.setTooltip(new Tooltip("Choose which assets appear in the character tag loader."));
    assetScopeBox.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshTagLists();
      ensureDefaultSelections();
      redrawPreview();
      persistGlobalState();
    });

    characterTagBox.setEditable(true);
    characterTagBox.setPromptText("Character image tag");
    backgroundTagBox.setEditable(true);
    backgroundTagBox.setPromptText("Background image tag");

    setupBox.setPromptText("Saved setup");
    setupNameField.setPromptText("Setup name");
    exportFormatBox.getItems().setAll(DEFAULT_EXPORT_PROFILE, DEFAULT_EXPORT_SETUP);
    exportFormatBox.getSelectionModel().select(DEFAULT_EXPORT_PROFILE);
    exportFormatBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistGlobalState();
    });

    refreshCatalogButton = iconButton(CssIcon.redo("#7ec8e3"), "Rescan project images", this::onCatalogRefreshRequested);
    updateRefreshButtonUi(false);

    // ── Compact sidebar rows ──
    HBox filterRow = new HBox(4, new Label("Filter"), filterField, refreshCatalogButton);
    filterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);

    HBox scopeRow = new HBox(4, new Label("Scope"), assetScopeBox);
    scopeRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(assetScopeBox, Priority.ALWAYS);

    HBox charRow = new HBox(4, new Label("Char"), characterTagBox);
    charRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(characterTagBox, Priority.ALWAYS);

    HBox bgRow = new HBox(4, new Label("Bg"), backgroundTagBox);
    bgRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(backgroundTagBox, Priority.ALWAYS);

    Button loadSetupButton = iconButton(CssIcon.download("#8ab4f8"), "Load selected setup", this::loadSelectedSetup);
    Button deleteSetupButton = iconButton(CssIcon.clearX("#f38ba8"), "Delete selected setup", this::deleteSelectedSetup);
    HBox setupLoadRow = new HBox(4, new Label("Setup"), setupBox, loadSetupButton, deleteSetupButton);
    setupLoadRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setupBox, Priority.ALWAYS);

    Button saveSetupButton = iconButton(CssIcon.save("#9ed67a"), "Save current setup", this::saveCurrentSetup);
    HBox setupSaveRow = new HBox(4, new Label("Save"), setupNameField, saveSetupButton);
    setupSaveRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setupNameField, Priority.ALWAYS);

    // Tags / setup section
    TitledPane tagsPane = new TitledPane("Tags & Setup",
        new VBox(4, filterRow, scopeRow, charRow, bgRow, setupLoadRow, setupSaveRow));
    tagsPane.setExpanded(true);
    tagsPane.setAnimated(false);
    tagsPane.setCollapsible(true);

    // ── Preview canvas fills the center ──
    StackPane previewPane = new StackPane(previewCanvas, previewLoadingOverlay);
    StackPane.setAlignment(previewLoadingOverlay, Pos.CENTER);
    previewLoadingOverlay.hideOverlay();
    previewPane.setStyle("-fx-background-color: #121720; -fx-border-color: #2b3445; -fx-border-radius: 4; -fx-background-radius: 4;");
    previewPane.widthProperty().addListener((o, ov, nv) -> {
      previewCanvas.setWidth(Math.max(140, nv.doubleValue() - 4));
      redrawPreview();
    });
    previewPane.heightProperty().addListener((o, ov, nv) -> {
      previewCanvas.setHeight(Math.max(140, nv.doubleValue() - 4));
      redrawPreview();
    });

    installPreviewInteractions();

    // ── Action buttons ──
    Button resetViewButton = iconButton(CssIcon.expand("#8ab4f8"), "Reset position and zoom", this::resetView);
    Button resetTintButton = iconButton(CssIcon.palette("#f5b971"), "Reset tint controls", this::resetTintControls);
    fullscreenButton = iconButton(CssIcon.expand("#f5c46b"), "Fullscreen", this::requestFullscreenToggle);
    updateFullscreenButtonUi();
    Button copyExportButton = iconButton(CssIcon.copy("#9ad19c"), "Copy selected export format", this::copySelectedExport);
    Button exportPngButton = iconButton(CssIcon.download("#8ab4f8"), "Export tinted image as PNG file", this::exportTintedPng);
    Button exportSetupBtn = iconButton(CssIcon.save("#9ed67a"), "Export setup to .tintsetup file", this::exportSetupToFile);
    Button importSetupBtn = iconButton(CssIcon.folder("#f5c46b"), "Import setup from .tintsetup file", this::importSetupFromFile);
    HBox actionRow = new HBox(4, resetViewButton, resetTintButton, fullscreenButton, copyExportButton);
    actionRow.setAlignment(Pos.CENTER_LEFT);
    HBox exportRow = new HBox(4, new Label("Export"), exportFormatBox);
    exportRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportFormatBox, Priority.ALWAYS);
    HBox fileRow = new HBox(4, exportPngButton, exportSetupBtn, importSetupBtn);
    fileRow.setAlignment(Pos.CENTER_LEFT);

    // ── Tint controls ──
    tintColorPicker.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    tintStrengthSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    saturationSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    contrastSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));

    updateTintColorSwatch(tintColorPicker.getValue());
    controlsSection.getChildren().setAll(
        tintPickerRow("Color", tintColorPicker, tintColorSwatch, Color.WHITE),
        sliderRow("Strength", tintStrengthSlider),
        sliderRow("Saturation", saturationSlider),
        sliderRow("Contrast", contrastSlider));
    controlsPane = new TitledPane("Global Tint", controlsSection);
    controlsPane.setExpanded(true);
    controlsPane.setAnimated(false);
    controlsPane.setCollapsible(true);
    controlsPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });

    buildBackgroundSection();

    // ── Zone area selector section ──
    buildZoneSection();

    // ── Right sidebar ──
    VBox sidebar = new VBox(6,
        title, summaryLabel,
        tagsPane,
        actionRow, exportRow, fileRow,
        controlsPane,
        backgroundPane,
        zonesPane,
        previewInfoLabel, interactionHintLabel,
        statusLabel);
    sidebar.setPadding(new Insets(6));
    sidebar.setStyle("-fx-font-size: 11px;");

    ScrollPane sidebarScroll = new ScrollPane(sidebar);
    sidebarScroll.setFitToWidth(true);
    sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    sidebarScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    sidebarScroll.setStyle("-fx-background: #1a1f2e; -fx-background-color: #1a1f2e; -fx-border-color: #2b3445; -fx-border-width: 0 0 0 1;");
    sidebarScroll.setPrefWidth(280);
    sidebarScroll.setMinWidth(230);

    setCenter(previewPane);
    setRight(sidebarScroll);

    bindTagSelectionHandlers();
  }

  private void buildBackgroundSection() {
    bgTintBlendModeBox.getItems().setAll(BLEND_MODES);
    bgTintBlendModeBox.getSelectionModel().select("Normal");
    bgOverlayBlendModeBox.getItems().setAll(BLEND_MODES);
    bgOverlayBlendModeBox.getSelectionModel().select("Overlay");

    bgTintColorPicker.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) onBackgroundFxChanged(true);
    });
    bindBackgroundFxSlider(bgTintStrengthSlider);
    bindBackgroundFxSlider(bgSaturationSlider);
    bindBackgroundFxSlider(bgContrastSlider);
    bgTintBlendModeBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) onBackgroundFxChanged(true);
    });
    bgOverlayColorPicker.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) onBackgroundFxChanged(true);
    });
    bindBackgroundFxSlider(bgOverlayOpacitySlider);
    bgOverlayBlendModeBox.valueProperty().addListener((o, ov, nv) -> {
      if (!applyingState) onBackgroundFxChanged(true);
    });

    updateBackgroundSwatches();
    backgroundControlsSection.getChildren().setAll(
        tintPickerRow("Tint Color", bgTintColorPicker, bgTintColorSwatch, Color.WHITE),
        sliderRow("Tint %", bgTintStrengthSlider),
        sliderRow("Saturation", bgSaturationSlider),
        sliderRow("Contrast", bgContrastSlider),
        comboRow("Tint Blend", bgTintBlendModeBox),
        tintPickerRow("Overlay", bgOverlayColorPicker, bgOverlayColorSwatch, Color.BLACK),
        sliderRow("Overlay %", bgOverlayOpacitySlider),
        comboRow("Overlay Blend", bgOverlayBlendModeBox));

    backgroundPane = new TitledPane("Background FX", backgroundControlsSection);
    backgroundPane.setExpanded(false);
    backgroundPane.setAnimated(false);
    backgroundPane.setCollapsible(true);
    backgroundPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });
  }

  private void bindTagSelectionHandlers() {
    characterTagBox.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      onCharacterTagChanged(false);
    });
    characterTagBox.getEditor().textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      onCharacterTagChanged(true);
    });

    backgroundTagBox.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      applyBackgroundTintIfPresent(selectedBackgroundTag());
      persistGlobalState();
    });
    backgroundTagBox.getEditor().textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      redrawPreview();
      persistGlobalState();
    });
  }

  private void onCharacterTagChanged(boolean fromEditorTyping) {
    if (!fromEditorTyping || isKnownCharacterTag(selectedCharacterTag())) {
      switchZoneProfileForCharacter(selectedCharacterTag(), true);
    }
    redrawPreview();
    persistGlobalState();
  }

  private void onTintChanged(boolean redraw) {
    updateTintColorSwatch(tintColorPicker.getValue());
    invalidateTintCache();
    if (redraw) redrawPreview();
    if (!applyingState) {
      persistGlobalState();
    }
  }

  private void onBackgroundFxChanged(boolean redraw) {
    updateBackgroundSwatches();
    invalidateBackgroundTintCache();
    if (redraw) redrawPreview();
    if (!applyingState) {
      persistBackgroundTint(selectedBackgroundTag());
      persistGlobalState();
    }
  }

  private void bindBackgroundFxSlider(Slider slider) {
    if (slider == null) return;
    slider.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      if (backgroundFxSliderDragging || slider.isValueChanging()) {
        backgroundFxSliderCommitPending = true;
        onBackgroundFxChanged(false);
        return;
      }
      onBackgroundFxChanged(true);
    });
    slider.valueChangingProperty().addListener((o, wasChanging, changing) -> {
      if (applyingState) return;
      if (!Boolean.TRUE.equals(changing)) {
        backgroundFxSliderDragging = false;
        commitBackgroundFxSliderChange();
      }
    });
    slider.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      backgroundFxSliderDragging = true;
      backgroundFxSliderCommitPending = false;
    });
    slider.setOnMouseReleased(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      backgroundFxSliderDragging = false;
      commitBackgroundFxSliderChange();
    });
  }

  private void commitBackgroundFxSliderChange() {
    if (applyingState || !backgroundFxSliderCommitPending) return;
    backgroundFxSliderCommitPending = false;
    onBackgroundFxChanged(true);
  }

  private void resetBackgroundFxControls() {
    applyingState = true;
    try {
      bgTintColorPicker.setValue(Color.WHITE);
      bgTintStrengthSlider.setValue(0.0);
      bgSaturationSlider.setValue(0.0);
      bgContrastSlider.setValue(0.0);
      bgTintBlendModeBox.getSelectionModel().select("Normal");
      bgOverlayColorPicker.setValue(Color.BLACK);
      bgOverlayOpacitySlider.setValue(0.0);
      bgOverlayBlendModeBox.getSelectionModel().select("Overlay");
    } finally {
      applyingState = false;
    }
    updateBackgroundSwatches();
    invalidateBackgroundTintCache();
    redrawPreview();
  }

  @Override
  public void setProjectRoot(File projectRoot) {
    if (disposed) return;
    persistGlobalState();
    this.projectRoot = projectRoot;
    refreshCatalog();
  }

  @Override
  public void setOnToggleFullscreen(Runnable handler) {
    fullscreenToggleHandler = handler;
  }

  @Override
  public void setFullscreenActive(boolean active) {
    if (fullscreenActive == active) return;
    fullscreenActive = active;
    updateFullscreenButtonUi();
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    stateSaveDebounce.stop();
    Task<TintCatalogScanResult> task = scanTask;
    scanTask = null;
    if (task != null && task.isRunning()) {
      task.cancel();
    }
    backgroundTintTaskSerial++;
    cancelBackgroundTintTask();
    setupNameToKey.clear();
    tintZones.clear();
    zoneListView.getItems().clear();
    clearCatalogUi("Image tint tool disposed.");
    projectRoot = null;
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
      refreshCatalogButton.setTooltip(new Tooltip("Cancel image scan"));
    } else {
      refreshCatalogButton.setGraphic(CssIcon.redo("#7ec8e3"));
      refreshCatalogButton.setTooltip(new Tooltip("Rescan project images"));
    }
  }

  private void clearCatalogUi(String message) {
    imageByTag.clear();
    presetByTag.clear();
    imageCache.clear();
    invalidateTintCache();
    invalidateBackgroundTintCache();
    summaryLabel.setText(message);
    characterTagBox.getItems().clear();
    backgroundTagBox.getItems().clear();
    refreshSetupOptions();
    redrawPreview();
  }

  @Override
  public void refreshCatalog() {
    if (disposed) return;
    loadPersistentState();
    if (scanTask != null && scanTask.isRunning()) {
      scanTask.cancel();
    }
    updateRefreshButtonUi(false);

    if (projectRoot == null || !projectRoot.isDirectory()) {
      clearCatalogUi("Open a project to scan image tags.");
      return;
    }

    File rootDir = projectRoot;
    clearCatalogUi("Scanning image tags...");
    status("Scanning assets...");
    updateRefreshButtonUi(true);

    Task<TintCatalogScanResult> task = new Task<>() {
      @Override
      protected TintCatalogScanResult call() throws Exception {
        Map<String, File> scannedImages = new LinkedHashMap<>();
        Path root = rootDir.toPath();
        List<String> imagePaths;
        try (Stream<Path> stream = Files.walk(root, 10)) {
          imagePaths = stream
              .filter(Files::isRegularFile)
              .filter(ImageTintToolView.this::isImageFile)
              .map(path -> root.relativize(path).toString().replace('\\', '/'))
              .filter(relative -> !isIgnoredPath(relative))
              .sorted(Comparator.naturalOrder())
              .toList();
        }
        int total = imagePaths.size();
        int index = 0;
        for (String relative : imagePaths) {
          if (isCancelled()) return TintCatalogScanResult.cancelledResult();
          scannedImages.put(relative, root.resolve(relative).toFile());
          index++;
          if (index == total || (index % 200) == 0) {
            updateProgress(index, Math.max(total, 1));
          }
        }
        if (isCancelled()) return TintCatalogScanResult.cancelledResult();
        Map<String, PresetTagEntry> scannedPresets = scanScriptCharpresets(root, this);
        if (isCancelled()) return TintCatalogScanResult.cancelledResult();
        return new TintCatalogScanResult(scannedImages, scannedPresets, false);
      }
    };
    scanTask = task;

    task.setOnSucceeded(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      TintCatalogScanResult result = task.getValue();
      if (result == null || result.cancelled()) {
        summaryLabel.setText("Image scan cancelled.");
        status("Scan cancelled.");
        return;
      }
      imageByTag.clear();
      imageByTag.putAll(result.images());
      presetByTag.clear();
      presetByTag.putAll(result.presets());
      imageCache.clear();
      invalidateTintCache();
      invalidateBackgroundTintCache();

      summaryLabel.setText("Images: " + imageByTag.size() + " | Charpresets: " + presetByTag.size());
      refreshTagLists();
      applyPersistedSelections();
      ensureDefaultSelections();
      refreshSetupOptions();
      switchZoneProfileForCharacter(selectedCharacterTag(), false);
      redrawPreview();
      status("Scan complete.");
    });

    task.setOnCancelled(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      summaryLabel.setText("Image scan cancelled.");
      status("Scan cancelled.");
    });

    task.setOnFailed(e -> {
      if (disposed || scanTask != task) return;
      scanTask = null;
      updateRefreshButtonUi(false);
      Throwable ex = task.getException();
      clearCatalogUi("Image scan failed: " + (ex == null ? "Unknown error" : ex.getMessage()));
      status("Scan failed.");
    });

    Thread scanThread = new Thread(task, "jvn-image-tint-scan");
    scanThread.setDaemon(true);
    scanThread.start();
  }

  private void refreshTagLists() {
    String keepChar = selectedCharacterTag();
    String keepBg = selectedBackgroundTag();
    String filter = normalize(filterField.getText());
    String scope = normalize(assetScopeBox.getValue());
    List<String> characterTags = buildCharacterTagList(scope);
    List<String> bgs = new ArrayList<>(imageByTag.keySet());
    characterTags.removeIf(tag -> !matchesTagSearch(tag, filter));
    bgs.removeIf(tag -> !matchesTagSearch(tag, filter));
    characterTagBox.getItems().setAll(characterTags);

    bgs.sort(Comparator.comparingInt(tag -> isLikelyBackgroundTag(tag) ? 0 : 1));
    backgroundTagBox.getItems().setAll(bgs);

    boolean previousApplying = applyingState;
    applyingState = true;
    try {
      restoreComboSelection(characterTagBox, keepChar);
      restoreComboSelection(backgroundTagBox, keepBg);
    } finally {
      applyingState = previousApplying;
    }
  }

  private void applyPersistedSelections() {
    applyingState = true;
    try {
      filterField.setText(persisted.getProperty("global.filter", ""));
      String scope = normalize(persisted.getProperty("global.assetScope", ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS));
      if (!assetScopeBox.getItems().contains(scope)) {
        scope = ASSET_SCOPE_CHARACTER_ASSETS_AND_PRESETS;
      }
      assetScopeBox.getSelectionModel().select(scope);
      if (controlsPane != null) {
        boolean hide = parseBoolean(persisted.getProperty("global.hideControls"), false);
        controlsPane.setExpanded(!hide);
      }
      if (backgroundPane != null) {
        boolean hideBackground = parseBoolean(persisted.getProperty("global.hideBackgroundFx"), true);
        backgroundPane.setExpanded(!hideBackground);
      }

      String savedChar = persisted.getProperty("global.characterTag", "");
      String savedBg = persisted.getProperty("global.backgroundTag", "");
      characterTagBox.getSelectionModel().select(savedChar);
      characterTagBox.getEditor().setText(savedChar);
      backgroundTagBox.getSelectionModel().select(savedBg);
      backgroundTagBox.getEditor().setText(savedBg);
      String exportFormat = persisted.getProperty("global.exportFormat", DEFAULT_EXPORT_PROFILE);
      if (!exportFormatBox.getItems().contains(exportFormat)) exportFormat = DEFAULT_EXPORT_PROFILE;
      exportFormatBox.getSelectionModel().select(exportFormat);

      tintColorPicker.setValue(parseColor(persisted.getProperty("global.tintColor"), Color.WHITE));
      tintStrengthSlider.setValue(parseDouble(persisted.getProperty("global.tintStrength"), 30.0));
      saturationSlider.setValue(parseDouble(persisted.getProperty("global.saturation"), 0.0));
      contrastSlider.setValue(parseDouble(persisted.getProperty("global.contrast"), 0.0));
      bgTintColorPicker.setValue(parseColor(persisted.getProperty("global.bgTintColor"), Color.WHITE));
      bgTintStrengthSlider.setValue(parseDouble(persisted.getProperty("global.bgTintStrength"), 0.0));
      bgSaturationSlider.setValue(parseDouble(persisted.getProperty("global.bgSaturation"), 0.0));
      bgContrastSlider.setValue(parseDouble(persisted.getProperty("global.bgContrast"), 0.0));
      bgTintBlendModeBox.getSelectionModel().select(canonicalBlendMode(persisted.getProperty("global.bgTintBlend"), "Normal"));
      bgOverlayColorPicker.setValue(parseColor(persisted.getProperty("global.bgOverlayColor"), Color.BLACK));
      bgOverlayOpacitySlider.setValue(parseDouble(persisted.getProperty("global.bgOverlayOpacity"), 0.0));
      bgOverlayBlendModeBox.getSelectionModel().select(canonicalBlendMode(persisted.getProperty("global.bgOverlayBlend"), "Overlay"));
      updateBackgroundSwatches();
      zoom = clamp(parseDouble(persisted.getProperty("global.zoom"), 1.0), 0.1, 8.0);
      offsetX = parseDouble(persisted.getProperty("global.offsetX"), 0.0);
      offsetY = parseDouble(persisted.getProperty("global.offsetY"), 0.0);

      if (zonesPane != null) {
        boolean hideZones = parseBoolean(persisted.getProperty("global.hideZones"), true);
        zonesPane.setExpanded(!hideZones);
      }
    } finally {
      applyingState = false;
    }
    activeZoneProfileTag = normalize(selectedCharacterTag());
    loadPersistedZones();
    applyBackgroundTintIfPresent(selectedBackgroundTag());
  }

  private void ensureDefaultSelections() {
    boolean changed = false;
    applyingState = true;
    try {
      String currentCharacter = selectedCharacterTag();
      if (currentCharacter.isBlank() || !isKnownCharacterTag(currentCharacter)) {
        String fallbackCharacter = pickDefaultCharacterTag(buildCharacterTagList(normalize(assetScopeBox.getValue())));
        if (!fallbackCharacter.isBlank()) {
          characterTagBox.getSelectionModel().select(fallbackCharacter);
          characterTagBox.getEditor().setText(fallbackCharacter);
          changed = true;
        }
      }

      String currentBackground = selectedBackgroundTag();
      if (currentBackground.isBlank() || !imageByTag.containsKey(currentBackground)) {
        String fallbackBackground = pickDefaultBackgroundTag(new ArrayList<>(imageByTag.keySet()));
        if (!fallbackBackground.isBlank()) {
          backgroundTagBox.getSelectionModel().select(fallbackBackground);
          backgroundTagBox.getEditor().setText(fallbackBackground);
          changed = true;
        }
      }
    } finally {
      applyingState = false;
    }

    if (changed) {
      applyBackgroundTintIfPresent(selectedBackgroundTag());
      switchZoneProfileForCharacter(selectedCharacterTag(), false);
      persistGlobalState();
    }
  }

  private void restoreComboSelection(ComboBox<String> box, String value) {
    if (box == null) return;
    String normalized = normalize(value);
    if (normalized.isBlank()) return;
    if (box.getItems().contains(normalized)) {
      box.getSelectionModel().select(normalized);
    }
    if (box.getEditor() != null) {
      box.getEditor().setText(normalized);
    }
  }

  private void refreshSetupOptions() {
    setupNameToKey.clear();
    List<String> names = new ArrayList<>();
    for (String key : persisted.stringPropertyNames()) {
      if (!key.startsWith("setup.") || !key.endsWith(".name")) continue;
      String setupKey = key.substring("setup.".length(), key.length() - ".name".length());
      String name = persisted.getProperty(key, "");
      if (name.isBlank()) continue;
      setupNameToKey.put(name, setupKey);
      names.add(name);
    }
    names.sort(String.CASE_INSENSITIVE_ORDER);
    setupBox.getItems().setAll(names);
    String selected = persisted.getProperty("global.selectedSetup", "");
    if (!selected.isBlank() && names.contains(selected)) {
      setupBox.getSelectionModel().select(selected);
    }
  }

  private void saveCurrentSetup() {
    String rawName = setupNameField.getText();
    String name = rawName == null ? "" : rawName.trim();
    if (name.isBlank()) {
      status("Setup name is required.");
      return;
    }
    String key = encodeKey(name);
    String prefix = "setup." + key + ".";
    persisted.setProperty(prefix + "name", name);
    persisted.setProperty(prefix + "characterTag", selectedCharacterTag());
    persisted.setProperty(prefix + "backgroundTag", selectedBackgroundTag());
    persisted.setProperty(prefix + "tintColor", colorToHex(tintColorPicker.getValue()));
    persisted.setProperty(prefix + "tintStrength", formatDouble(tintStrengthSlider.getValue()));
    persisted.setProperty(prefix + "saturation", formatDouble(saturationSlider.getValue()));
    persisted.setProperty(prefix + "contrast", formatDouble(contrastSlider.getValue()));
    persisted.setProperty(prefix + "bgTintColor", colorToHex(bgTintColorPicker.getValue()));
    persisted.setProperty(prefix + "bgTintStrength", formatDouble(bgTintStrengthSlider.getValue()));
    persisted.setProperty(prefix + "bgSaturation", formatDouble(bgSaturationSlider.getValue()));
    persisted.setProperty(prefix + "bgContrast", formatDouble(bgContrastSlider.getValue()));
    persisted.setProperty(prefix + "bgTintBlend", canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal"));
    persisted.setProperty(prefix + "bgOverlayColor", colorToHex(bgOverlayColorPicker.getValue()));
    persisted.setProperty(prefix + "bgOverlayOpacity", formatDouble(bgOverlayOpacitySlider.getValue()));
    persisted.setProperty(prefix + "bgOverlayBlend", canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay"));
    persisted.setProperty(prefix + "zoom", formatDouble(zoom));
    persisted.setProperty(prefix + "offsetX", formatDouble(offsetX));
    persisted.setProperty(prefix + "offsetY", formatDouble(offsetY));

    // A: Save zone data with setup.
    clearPrefix(prefix + "zone.");
    persisted.setProperty(prefix + "zone.count", Integer.toString(tintZones.size()));
    for (int i = 0; i < tintZones.size(); i++) {
      TintZone z = tintZones.get(i);
      String zp = prefix + "zone." + i + ".";
      persisted.setProperty(zp + "name", z.name == null ? "" : z.name);
      persisted.setProperty(zp + "boundsX", formatDouble(z.boundsX));
      persisted.setProperty(zp + "boundsY", formatDouble(z.boundsY));
      persisted.setProperty(zp + "boundsW", formatDouble(z.boundsW));
      persisted.setProperty(zp + "boundsH", formatDouble(z.boundsH));
      persisted.setProperty(zp + "color", colorToHex(z.color));
      persisted.setProperty(zp + "strength", formatDouble(z.strength));
      persisted.setProperty(zp + "saturation", formatDouble(z.saturation));
      persisted.setProperty(zp + "contrast", formatDouble(z.contrast));
      persisted.setProperty(zp + "feather", formatDouble(z.feather));
      persisted.setProperty(zp + "rotation", formatDouble(z.rotation));
      persisted.setProperty(zp + "blendMode", canonicalBlendMode(z.blendMode, "Normal"));
      persisted.setProperty(zp + "overlayVisible", Boolean.toString(z.overlayVisible));
      if (z.isPolygon()) {
        StringBuilder polyStr = new StringBuilder();
        for (int p = 0; p < z.polygon.size(); p++) {
          if (p > 0) polyStr.append(";");
          polyStr.append(formatDouble(z.polygon.get(p)[0])).append(",").append(formatDouble(z.polygon.get(p)[1]));
        }
        persisted.setProperty(zp + "polygon", polyStr.toString());
      }
    }

    // E: Sync background per-tag tint.
    persistBackgroundTint(selectedBackgroundTag());

    persisted.setProperty("global.selectedSetup", name);
    savePersistentState();
    refreshSetupOptions();
    setupBox.getSelectionModel().select(name);
    status("Saved setup: " + name + " (" + tintZones.size() + " zones)");
  }

  private void loadSelectedSetup() {
    String name = setupBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a setup first.");
      return;
    }
    String key = setupNameToKey.get(name);
    if (key == null || key.isBlank()) {
      status("Setup not found.");
      return;
    }
    String prefix = "setup." + key + ".";
    applyingState = true;
    try {
      String characterTag = persisted.getProperty(prefix + "characterTag", "");
      String backgroundTag = persisted.getProperty(prefix + "backgroundTag", "");
      characterTagBox.getSelectionModel().select(characterTag);
      characterTagBox.getEditor().setText(characterTag);
      backgroundTagBox.getSelectionModel().select(backgroundTag);
      backgroundTagBox.getEditor().setText(backgroundTag);

      tintColorPicker.setValue(parseColor(persisted.getProperty(prefix + "tintColor"), Color.WHITE));
      tintStrengthSlider.setValue(parseDouble(persisted.getProperty(prefix + "tintStrength"), tintStrengthSlider.getValue()));
      saturationSlider.setValue(parseDouble(persisted.getProperty(prefix + "saturation"), saturationSlider.getValue()));
      contrastSlider.setValue(parseDouble(persisted.getProperty(prefix + "contrast"), contrastSlider.getValue()));
      bgTintColorPicker.setValue(parseColor(persisted.getProperty(prefix + "bgTintColor"), bgTintColorPicker.getValue()));
      bgTintStrengthSlider.setValue(parseDouble(persisted.getProperty(prefix + "bgTintStrength"), bgTintStrengthSlider.getValue()));
      bgSaturationSlider.setValue(parseDouble(persisted.getProperty(prefix + "bgSaturation"), bgSaturationSlider.getValue()));
      bgContrastSlider.setValue(parseDouble(persisted.getProperty(prefix + "bgContrast"), bgContrastSlider.getValue()));
      bgTintBlendModeBox.getSelectionModel().select(canonicalBlendMode(persisted.getProperty(prefix + "bgTintBlend"), bgTintBlendModeBox.getValue()));
      bgOverlayColorPicker.setValue(parseColor(persisted.getProperty(prefix + "bgOverlayColor"), bgOverlayColorPicker.getValue()));
      bgOverlayOpacitySlider.setValue(parseDouble(persisted.getProperty(prefix + "bgOverlayOpacity"), bgOverlayOpacitySlider.getValue()));
      bgOverlayBlendModeBox.getSelectionModel().select(canonicalBlendMode(persisted.getProperty(prefix + "bgOverlayBlend"), bgOverlayBlendModeBox.getValue()));
      zoom = clamp(parseDouble(persisted.getProperty(prefix + "zoom"), zoom), 0.1, 8.0);
      offsetX = parseDouble(persisted.getProperty(prefix + "offsetX"), offsetX);
      offsetY = parseDouble(persisted.getProperty(prefix + "offsetY"), offsetY);

      // A: Load zone data from setup.
      int zoneCount = (int) parseDouble(persisted.getProperty(prefix + "zone.count"), -1);
      if (zoneCount >= 0) {
        tintZones.clear();
        selectedZoneIndex = -1;
        int loaded = 0;
        for (int i = 0; i < zoneCount; i++) {
          try {
            TintZone z = loadZoneFromPrefix(persisted, prefix + "zone." + i + ".", i);
            tintZones.add(z);
            loaded++;
          } catch (Exception ignored) { /* F: skip malformed */ }
        }
        refreshZoneList();
        if (!tintZones.isEmpty()) selectZone(0);
        else zoneControlsSection.setDisable(true);
        if (loaded < zoneCount) {
          status("Loaded setup: " + name + " (" + loaded + "/" + zoneCount + " zones, " + (zoneCount - loaded) + " skipped)");
        }
      }
    } finally {
      applyingState = false;
    }

    // E: Sync background per-tag tint with loaded values.
    updateBackgroundSwatches();
    invalidateBackgroundTintCache();
    persistBackgroundTint(selectedBackgroundTag());
    activeZoneProfileTag = normalize(selectedCharacterTag());
    persistZones();

    persisted.setProperty("global.selectedSetup", name);
    persistGlobalState();
    invalidateTintCache();
    invalidateBackgroundTintCache();
    redrawPreview();
    if (statusLabel.getText().isBlank() || !statusLabel.getText().contains("skipped"))
      status("Loaded setup: " + name + " (" + tintZones.size() + " zones)");
  }

  private void deleteSelectedSetup() {
    String name = setupBox.getValue();
    if (name == null || name.isBlank()) {
      status("Select a setup first.");
      return;
    }
    String key = setupNameToKey.get(name);
    if (key == null || key.isBlank()) {
      status("Setup not found.");
      return;
    }
    clearPrefix("setup." + key + ".");
    if (name.equals(persisted.getProperty("global.selectedSetup", ""))) {
      persisted.remove("global.selectedSetup");
    }
    savePersistentState();
    refreshSetupOptions();
    status("Deleted setup: " + name);
  }

  private void resetView() {
    zoom = 1.0;
    offsetX = 0.0;
    offsetY = 0.0;
    persistGlobalState();
    redrawPreview();
    status("Reset view.");
  }

  private void resetTintControls() {
    applyingState = true;
    try {
      tintColorPicker.setValue(Color.WHITE);
      tintStrengthSlider.setValue(30.0);
      saturationSlider.setValue(0.0);
      contrastSlider.setValue(0.0);
    } finally {
      applyingState = false;
    }
    onTintChanged(true);
    status("Reset tint controls.");
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

  private void copyTintProfile() {
    String payload = "color=" + colorToHex(tintColorPicker.getValue())
        + " strength=" + formatNormalized(tintStrengthSlider.getValue() / 100.0)
        + " saturation=" + formatNormalized(saturationSlider.getValue() / 100.0)
        + " contrast=" + formatNormalized(contrastSlider.getValue() / 100.0);
    copy(payload);
    status("Copied tint profile.");
  }

  private void copyFullSetup() {
    copy(buildFullSetupText());
    status("Copied full setup.");
  }

  private void copySelectedExport() {
    String format = exportFormatBox.getValue();
    if (DEFAULT_EXPORT_SETUP.equals(format)) {
      copyFullSetup();
      return;
    }
    copyTintProfile();
  }

  private void installPreviewInteractions() {
    previewCanvas.setOnMouseClicked(e -> {
      if (e.getButton() == MouseButton.SECONDARY && polyDrawMode) {
        // Right-click removes last nail point.
        if (!nailPoints.isEmpty()) {
          nailPoints.remove(nailPoints.size() - 1);
          redrawPreview();
        }
        e.consume();
        return;
      }
      if (e.getButton() == MouseButton.SECONDARY && freehandDrawMode) {
        if (!freehandPoints.isEmpty()) {
          freehandPoints.clear();
          drawingFreehand = false;
          redrawPreview();
          status("Freehand stroke cleared.");
        }
        e.consume();
        return;
      }
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (zoneDrawMode || freehandDrawMode) return;
      if (polyDrawMode) {
        double mx = e.getX(), my = e.getY();
        // Close polygon on double-click or clicking near first point.
        if (nailPoints.size() >= 3 && (e.getClickCount() >= 2 || isNearFirstNail(mx, my))) {
          commitPolygonZone();
          e.consume();
          return;
        }
        nailPoints.add(new double[]{mx, my});
        redrawPreview();
        int n = nailPoints.size();
        status("Polygon: " + n + " point(s) — " + (n >= 3 ? "click near first point to close" : "place more points"));
        e.consume();
        return;
      }
      if (e.getClickCount() < 2) return;
      resetView();
      e.consume();
    });

    previewCanvas.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (polyDrawMode) return; // handled by onMouseClicked
      if (freehandDrawMode) {
        drawingFreehand = true;
        freehandPoints.clear();
        freehandPoints.add(new double[]{e.getX(), e.getY()});
        redrawPreview();
        return;
      }
      if (zoneDrawMode) {
        drawingZone = true;
        zoneDrawStartX = e.getX();
        zoneDrawStartY = e.getY();
        zoneDrawEndX = e.getX();
        zoneDrawEndY = e.getY();
        redrawPreview();
        return;
      }
      dragging = true;
      dragLastX = e.getX();
      dragLastY = e.getY();
    });
    previewCanvas.setOnMouseDragged(e -> {
      if (polyDrawMode) return;
      if (freehandDrawMode && drawingFreehand) {
        appendFreehandPoint(e.getX(), e.getY());
        redrawPreview();
        return;
      }
      if (zoneDrawMode && drawingZone) {
        zoneDrawEndX = e.getX();
        zoneDrawEndY = e.getY();
        redrawPreview();
        return;
      }
      if (!dragging) return;
      double dx = e.getX() - dragLastX;
      double dy = e.getY() - dragLastY;
      dragLastX = e.getX();
      dragLastY = e.getY();
      offsetX += dx;
      offsetY += dy;
      redrawPreview();
    });
    previewCanvas.setOnMouseReleased(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (polyDrawMode) return;
      if (freehandDrawMode && drawingFreehand) {
        appendFreehandPoint(e.getX(), e.getY());
        drawingFreehand = false;
        commitFreehandZone();
        redrawPreview();
        return;
      }
      if (zoneDrawMode && drawingZone) {
        drawingZone = false;
        commitDrawnZone();
        redrawPreview();
        return;
      }
      dragging = false;
      persistGlobalState();
    });
    previewCanvas.setOnScroll(e -> {
      Image img = loadImage(selectedCharacterTag());
      if (img == null) return;
      double oldZoom = zoom;
      double nextZoom = clamp(oldZoom * Math.pow(1.10, e.getDeltaY() / 40.0), 0.1, 8.0);
      if (Math.abs(nextZoom - oldZoom) < 0.0001) return;

      double cw = previewCanvas.getWidth();
      double ch = previewCanvas.getHeight();
      double oldW = img.getWidth() * oldZoom;
      double oldH = img.getHeight() * oldZoom;
      double oldX = (cw - oldW) * 0.5 + offsetX;
      double oldY = (ch - oldH) * 0.5 + offsetY;
      double localX = (e.getX() - oldX) / Math.max(oldZoom, 0.0001);
      double localY = (e.getY() - oldY) / Math.max(oldZoom, 0.0001);

      zoom = nextZoom;
      double newW = img.getWidth() * nextZoom;
      double newH = img.getHeight() * nextZoom;
      double newX = e.getX() - localX * nextZoom;
      double newY = e.getY() - localY * nextZoom;
      offsetX = newX - (cw - newW) * 0.5;
      offsetY = newY - (ch - newH) * 0.5;
      redrawPreview();
      persistGlobalState();
      e.consume();
    });
  }

  private void redrawPreview() {
    GraphicsContext g = previewCanvas.getGraphicsContext2D();
    double w = previewCanvas.getWidth();
    double h = previewCanvas.getHeight();
    g.setFill(Color.web("#121720"));
    g.fillRect(0, 0, w, h);

    Image bg = loadImage(selectedBackgroundTag());
    if (bg != null) {
      Image previewBg = resolvePreviewBackgroundImage(selectedBackgroundTag(), bg);
      drawCover(g, previewBg == null ? bg : previewBg, w, h);
    } else {
      cancelBackgroundTintTask();
      drawChecker(g, w, h);
    }

    String characterTag = selectedCharacterTag();
    Image rawCharacter = loadImage(characterTag);
    if (rawCharacter == null) {
      drawHint(g, w, h, "Select a character image tag");
      previewInfoLabel.setText("No character image selected.");
      return;
    }

    Image tintedCharacter = buildTintedImage(characterTag, rawCharacter);
    double drawW = rawCharacter.getWidth() * zoom;
    double drawH = rawCharacter.getHeight() * zoom;
    double drawX = (w - drawW) * 0.5 + offsetX;
    double drawY = (h - drawH) * 0.5 + offsetY;
    g.drawImage(tintedCharacter, drawX, drawY, drawW, drawH);

    // Draw zone overlays on the character image region.
    drawZoneOverlays(g, rawCharacter, drawX, drawY, drawW, drawH);

    // Draw in-progress zone rectangle while user is dragging.
    if (zoneDrawMode && drawingZone) {
      double rx = Math.min(zoneDrawStartX, zoneDrawEndX);
      double ry = Math.min(zoneDrawStartY, zoneDrawEndY);
      double rw = Math.abs(zoneDrawEndX - zoneDrawStartX);
      double rh = Math.abs(zoneDrawEndY - zoneDrawStartY);
      g.setStroke(Color.web("#ffcc00"));
      g.setLineDashes(6, 4);
      g.setLineWidth(2);
      g.strokeRect(rx, ry, rw, rh);
      g.setFill(Color.color(1, 0.8, 0, 0.12));
      g.fillRect(rx, ry, rw, rh);
      g.setLineDashes((double[]) null);
    }

    // Draw in-progress polygon nail points.
    if (polyDrawMode && !nailPoints.isEmpty()) {
      g.setStroke(Color.rgb(255, 120, 120, 0.85));
      g.setLineWidth(1.5);
      g.setLineDashes((double[]) null);
      // Connect placed points.
      if (nailPoints.size() >= 2) {
        for (int i = 0; i < nailPoints.size() - 1; i++) {
          double[] a = nailPoints.get(i);
          double[] b = nailPoints.get(i + 1);
          g.strokeLine(a[0], a[1], b[0], b[1]);
        }
      }
      // Dashed closing line preview.
      if (nailPoints.size() >= 3) {
        double[] first = nailPoints.get(0);
        double[] last = nailPoints.get(nailPoints.size() - 1);
        g.setLineDashes(4, 3);
        g.strokeLine(last[0], last[1], first[0], first[1]);
        g.setLineDashes((double[]) null);
      }
      // Red dots at each nail.
      g.setFill(Color.rgb(255, 80, 80, 0.95));
      for (double[] pt : nailPoints) {
        g.fillOval(pt[0] - 5, pt[1] - 5, 10, 10);
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.2);
        g.strokeOval(pt[0] - 5, pt[1] - 5, 10, 10);
      }
      // Hint label at bottom.
      g.setFill(Color.color(1, 1, 1, 0.8));
      g.setTextAlign(TextAlignment.LEFT);
      String hint = nailPoints.size() >= 3
          ? nailPoints.size() + " pts — click near first point to close polygon"
          : nailPoints.size() + " pts — place more points";
      g.fillText(hint, 8, h - 8);
    }

    if (freehandDrawMode && !freehandPoints.isEmpty()) {
      g.setStroke(Color.color(0.43, 0.90, 0.95, 0.95));
      g.setLineWidth(2.4);
      g.setLineDashes((double[]) null);
      for (int i = 0; i < freehandPoints.size() - 1; i++) {
        double[] a = freehandPoints.get(i);
        double[] b = freehandPoints.get(i + 1);
        g.strokeLine(a[0], a[1], b[0], b[1]);
      }
      if (!drawingFreehand && freehandPoints.size() >= 3) {
        double[] first = freehandPoints.get(0);
        double[] last = freehandPoints.get(freehandPoints.size() - 1);
        g.setLineDashes(4, 3);
        g.strokeLine(last[0], last[1], first[0], first[1]);
        g.setLineDashes((double[]) null);
      }
      g.setFill(Color.color(0.65, 0.95, 1.0, 0.9));
      for (double[] pt : freehandPoints) {
        g.fillOval(pt[0] - 2.0, pt[1] - 2.0, 4.0, 4.0);
      }
      g.setFill(Color.color(1, 1, 1, 0.82));
      g.setTextAlign(TextAlignment.LEFT);
      String hint = drawingFreehand
          ? "Freehand: " + freehandPoints.size() + " pts — release to finish"
          : "Freehand: " + freehandPoints.size() + " pts — draw and release";
      g.fillText(hint, 8, h - 8);
    }

    previewInfoLabel.setText("Char: " + shortTag(characterTag)
        + "  |  Bg: " + shortTag(selectedBackgroundTag())
        + "  |  Zoom: " + formatNormalized(zoom)
        + "  |  Zones: " + tintZones.size());
  }

  private Image resolvePreviewBackgroundImage(String tag, Image source) {
    if (source == null) return null;
    BackgroundTintParams params = snapshotBackgroundTintParams();
    String key = backgroundTintKey(tag, source, params);

    if (params.isIdentity()) {
      cancelBackgroundTintTask();
      tintedBackgroundTag = tag;
      tintedBackgroundKey = key;
      tintedBackground = source;
      return source;
    }

    if (Objects.equals(tintedBackgroundTag, tag) && Objects.equals(tintedBackgroundKey, key) && tintedBackground != null) {
      if (backgroundTintTask == null || !backgroundTintTask.isRunning()) {
        previewLoadingOverlay.hideOverlay();
      }
      return tintedBackground;
    }

    ensureBackgroundTintTask(tag, source, key, params);
    return source;
  }

  private BackgroundTintParams snapshotBackgroundTintParams() {
    Color tint = bgTintColorPicker.getValue() == null ? Color.WHITE : bgTintColorPicker.getValue();
    Color overlay = bgOverlayColorPicker.getValue() == null ? Color.BLACK : bgOverlayColorPicker.getValue();
    String tintBlendMode = canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal");
    String overlayBlendMode = canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay");
    return new BackgroundTintParams(
        clamp(bgTintStrengthSlider.getValue() / 100.0, 0.0, 1.0),
        clamp(bgSaturationSlider.getValue() / 100.0, -1.0, 1.0),
        clamp(bgContrastSlider.getValue() / 100.0, -1.0, 1.0),
        tint.getRed(),
        tint.getGreen(),
        tint.getBlue(),
        tintBlendMode,
        blendModeIndex(tintBlendMode),
        clamp(bgOverlayOpacitySlider.getValue() / 100.0, 0.0, 1.0),
        overlay.getRed(),
        overlay.getGreen(),
        overlay.getBlue(),
        overlayBlendMode,
        blendModeIndex(overlayBlendMode)
    );
  }

  private void ensureBackgroundTintTask(String tag, Image source, String key, BackgroundTintParams params) {
    if (backgroundTintTask != null
        && backgroundTintTask.isRunning()
        && Objects.equals(backgroundTintTaskTag, tag)
        && Objects.equals(backgroundTintTaskKey, key)) {
      previewLoadingOverlay.showDeterminate("Applying background tint...", backgroundTintTask.getProgress());
      return;
    }

    cancelBackgroundTintTask();
    final long serial = ++backgroundTintTaskSerial;
    Task<Image> task = new Task<>() {
      @Override
      protected Image call() {
        return renderTintedBackgroundImage(
            source,
            params,
            this::isCancelled,
            progress -> updateProgress(progress, 1.0)
        );
      }
    };
    backgroundTintTask = task;
    backgroundTintTaskTag = tag;
    backgroundTintTaskKey = key;
    previewLoadingOverlay.showDeterminate("Applying background tint...", 0.0);
    task.progressProperty().addListener((o, ov, nv) -> {
      double progress = nv == null ? -1.0 : nv.doubleValue();
      previewLoadingOverlay.setProgress(progress);
    });
    task.setOnSucceeded(e -> {
      if (disposed || serial != backgroundTintTaskSerial) return;
      Image rendered = task.getValue();
      if (rendered != null) {
        tintedBackgroundTag = tag;
        tintedBackgroundKey = key;
        tintedBackground = rendered;
      }
      backgroundTintTask = null;
      backgroundTintTaskTag = null;
      backgroundTintTaskKey = null;
      previewLoadingOverlay.hideOverlay();
      redrawPreview();
    });
    task.setOnCancelled(e -> {
      if (disposed || serial != backgroundTintTaskSerial) return;
      backgroundTintTask = null;
      backgroundTintTaskTag = null;
      backgroundTintTaskKey = null;
      previewLoadingOverlay.hideOverlay();
      redrawPreview();
    });
    task.setOnFailed(e -> {
      if (disposed || serial != backgroundTintTaskSerial) return;
      backgroundTintTask = null;
      backgroundTintTaskTag = null;
      backgroundTintTaskKey = null;
      previewLoadingOverlay.hideOverlay();
      Throwable ex = task.getException();
      status("Background tint render failed: " + (ex == null ? "unknown error" : ex.getMessage()));
      redrawPreview();
    });
    Thread renderThread = new Thread(task, "jvn-bg-tint-render");
    renderThread.setDaemon(true);
    renderThread.start();
  }

  private void cancelBackgroundTintTask() {
    if (backgroundTintTask != null && backgroundTintTask.isRunning()) {
      backgroundTintTask.cancel();
    }
    backgroundTintTask = null;
    backgroundTintTaskTag = null;
    backgroundTintTaskKey = null;
    previewLoadingOverlay.hideOverlay();
  }

  private static Image renderTintedBackgroundImage(Image source,
                                                   BackgroundTintParams params,
                                                   BooleanSupplier isCancelled,
                                                   DoubleConsumer progressSink) {
    if (source == null || params == null) return source;
    int width = (int) Math.max(1, Math.round(source.getWidth()));
    int height = (int) Math.max(1, Math.round(source.getHeight()));
    PixelReader reader = source.getPixelReader();
    if (reader == null) return source;
    WritableImage out = new WritableImage(width, height);
    PixelWriter writer = out.getPixelWriter();

    for (int y = 0; y < height; y++) {
      if (isCancelled != null && isCancelled.getAsBoolean()) return null;
      for (int x = 0; x < width; x++) {
        int argb = reader.getArgb(x, y);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
          writer.setArgb(x, y, argb);
          continue;
        }
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;

        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double zr = lum + (r - lum) * (1.0 + params.satAdjust());
        double zg = lum + (g - lum) * (1.0 + params.satAdjust());
        double zb = lum + (b - lum) * (1.0 + params.satAdjust());
        zr = (zr - 0.5) * (1.0 + params.conAdjust()) + 0.5;
        zg = (zg - 0.5) * (1.0 + params.conAdjust()) + 0.5;
        zb = (zb - 0.5) * (1.0 + params.conAdjust()) + 0.5;
        zr = zr * (1.0 - params.tintStrength()) + params.tr() * params.tintStrength();
        zg = zg * (1.0 - params.tintStrength()) + params.tg() * params.tintStrength();
        zb = zb * (1.0 - params.tintStrength()) + params.tb() * params.tintStrength();

        r = applyBlend(r, zr, 1.0, params.tintBlendIndex());
        g = applyBlend(g, zg, 1.0, params.tintBlendIndex());
        b = applyBlend(b, zb, 1.0, params.tintBlendIndex());

        if (params.overlayOpacity() > 0.0) {
          r = applyBlend(r, params.or(), params.overlayOpacity(), params.overlayBlendIndex());
          g = applyBlend(g, params.og(), params.overlayOpacity(), params.overlayBlendIndex());
          b = applyBlend(b, params.ob(), params.overlayOpacity(), params.overlayBlendIndex());
        }

        int rr = (int) Math.round(clamp(r, 0.0, 1.0) * 255.0);
        int gg = (int) Math.round(clamp(g, 0.0, 1.0) * 255.0);
        int bb = (int) Math.round(clamp(b, 0.0, 1.0) * 255.0);
        writer.setArgb(x, y, (a << 24) | (rr << 16) | (gg << 8) | bb);
      }
      if (progressSink != null && ((y & 7) == 0 || y == height - 1)) {
        progressSink.accept((y + 1) / (double) height);
      }
    }
    return out;
  }

  private Image buildTintedImage(String tag, Image source) {
    if (source == null) return null;
    String key = tintKey(tag, source);
    if (Objects.equals(tintedImageTag, tag) && Objects.equals(tintedImageKey, key) && tintedImage != null) {
      return tintedImage;
    }
    int width = (int) Math.max(1, Math.round(source.getWidth()));
    int height = (int) Math.max(1, Math.round(source.getHeight()));
    WritableImage out = new WritableImage(width, height);
    PixelReader reader = source.getPixelReader();
    PixelWriter writer = out.getPixelWriter();
    if (reader == null) return source;

    double tintStrength = clamp(tintStrengthSlider.getValue() / 100.0, 0.0, 1.0);
    double satAdjust = clamp(saturationSlider.getValue() / 100.0, -1.0, 1.0);
    double conAdjust = clamp(contrastSlider.getValue() / 100.0, -1.0, 1.0);
    Color tint = tintColorPicker.getValue() == null ? Color.WHITE : tintColorPicker.getValue();
    double tr = tint.getRed();
    double tg = tint.getGreen();
    double tb = tint.getBlue();

    // Pre-compute zone parameters for performance.
    int zoneCount = tintZones.size();
    double[] zx = new double[zoneCount], zy = new double[zoneCount];
    double[] zw = new double[zoneCount], zh = new double[zoneCount];
    double[] zStr = new double[zoneCount], zSat = new double[zoneCount], zCon = new double[zoneCount];
    double[] zFeath = new double[zoneCount];
    double[] zRot = new double[zoneCount]; // rotation in radians
    double[] zcr = new double[zoneCount], zcg = new double[zoneCount], zcb = new double[zoneCount];
    int[] zBlend = new int[zoneCount];
    @SuppressWarnings("unchecked")
    List<double[]>[] zPoly = new List[zoneCount]; // null = rectangle, non-null = polygon
    for (int i = 0; i < zoneCount; i++) {
      TintZone zone = tintZones.get(i);
      zx[i] = zone.boundsX;
      zy[i] = zone.boundsY;
      zw[i] = zone.boundsW;
      zh[i] = zone.boundsH;
      zStr[i] = clamp(zone.strength / 100.0, 0.0, 1.0);
      zSat[i] = clamp(zone.saturation / 100.0, -1.0, 1.0);
      zCon[i] = clamp(zone.contrast / 100.0, -1.0, 1.0);
      zFeath[i] = clamp(zone.feather / 100.0, 0.0, 1.0);
      zRot[i] = Math.toRadians(zone.rotation);
      zcr[i] = zone.color.getRed();
      zcg[i] = zone.color.getGreen();
      zcb[i] = zone.color.getBlue();
      zBlend[i] = blendModeIndex(zone.blendMode);
      zPoly[i] = zone.isPolygon() ? zone.polygon : null;
    }

    double imgAspect = (double) width / height; // for aspect-correct rotation

    for (int y = 0; y < height; y++) {
      double ny = (double) y / height;
      for (int x = 0; x < width; x++) {
        int argb = reader.getArgb(x, y);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
          writer.setArgb(x, y, argb);
          continue;
        }
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;

        // Global tint pass.
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        r = lum + (r - lum) * (1.0 + satAdjust);
        g = lum + (g - lum) * (1.0 + satAdjust);
        b = lum + (b - lum) * (1.0 + satAdjust);
        r = (r - 0.5) * (1.0 + conAdjust) + 0.5;
        g = (g - 0.5) * (1.0 + conAdjust) + 0.5;
        b = (b - 0.5) * (1.0 + conAdjust) + 0.5;
        r = r * (1.0 - tintStrength) + tr * tintStrength;
        g = g * (1.0 - tintStrength) + tg * tintStrength;
        b = b * (1.0 - tintStrength) + tb * tintStrength;

        // Per-zone tint passes (applied in order, composited on top).
        double nx = (double) x / width;
        for (int i = 0; i < zoneCount; i++) {
          double weight = zPoly[i] != null
              ? polyZoneWeight(nx, ny, zPoly[i], zFeath[i])
              : zoneWeight(nx, ny, zx[i], zy[i], zw[i], zh[i], zFeath[i], zRot[i], imgAspect);
          if (weight <= 0.0) continue;
          double effStr = zStr[i];

          // Compute zone-tinted pixel from the current (already globally-tinted) color.
          double zl = 0.2126 * r + 0.7152 * g + 0.0722 * b;
          double zr = zl + (r - zl) * (1.0 + zSat[i]);
          double zgg = zl + (g - zl) * (1.0 + zSat[i]);
          double zb = zl + (b - zl) * (1.0 + zSat[i]);
          zr = (zr - 0.5) * (1.0 + zCon[i]) + 0.5;
          zgg = (zgg - 0.5) * (1.0 + zCon[i]) + 0.5;
          zb = (zb - 0.5) * (1.0 + zCon[i]) + 0.5;
          zr = zr * (1.0 - effStr) + zcr[i] * effStr;
          zgg = zgg * (1.0 - effStr) + zcg[i] * effStr;
          zb = zb * (1.0 - effStr) + zcb[i] * effStr;

          // Apply blend mode.
          r = applyBlend(r, zr, weight, zBlend[i]);
          g = applyBlend(g, zgg, weight, zBlend[i]);
          b = applyBlend(b, zb, weight, zBlend[i]);
        }

        int rr = (int) Math.round(clamp(r, 0.0, 1.0) * 255.0);
        int gg = (int) Math.round(clamp(g, 0.0, 1.0) * 255.0);
        int bb = (int) Math.round(clamp(b, 0.0, 1.0) * 255.0);
        writer.setArgb(x, y, (a << 24) | (rr << 16) | (gg << 8) | bb);
      }
    }

    tintedImageTag = tag;
    tintedImageKey = key;
    tintedImage = out;
    return out;
  }

  private static double zoneWeight(double nx, double ny, double zx, double zy, double zw, double zh, double feather, double rotRad, double imgAspect) {
    if (zw <= 0 || zh <= 0) return 0.0;
    // Rotate test point into the rectangle's local (un-rotated) coordinate frame.
    // We must rotate in pixel-equivalent space (aspect-corrected) so that the
    // tinting matches the overlay which rotates in pixel space via g.rotate().
    if (rotRad != 0.0) {
      double cx = zx + zw * 0.5, cy = zy + zh * 0.5;
      double cosR = Math.cos(-rotRad), sinR = Math.sin(-rotRad);
      double dx = nx - cx;
      double dy = (ny - cy) * imgAspect; // scale to pixel-proportional space
      nx = cx + dx * cosR - dy * sinR;
      ny = cy + (dx * sinR + dy * cosR) / imgAspect; // scale back
    }
    double x1 = zx, y1 = zy, x2 = zx + zw, y2 = zy + zh;
    if (nx < x1 || nx > x2 || ny < y1 || ny > y2) {
      if (feather <= 0.0) return 0.0;
      // Outside: compute distance-based falloff.
      double dx = nx < x1 ? x1 - nx : (nx > x2 ? nx - x2 : 0.0);
      double dy = ny < y1 ? y1 - ny : (ny > y2 ? ny - y2 : 0.0);
      double dist = Math.sqrt(dx * dx + dy * dy);
      if (dist >= feather) return 0.0;
      return 1.0 - dist / feather;
    }
    // Inside: always fully tinted. Feather applies outward from the boundary only.
    return 1.0;
  }

  private static double polyZoneWeight(double px, double py, List<double[]> poly, double feather) {
    int n = poly.size();
    if (n < 3) return 0.0;
    // Ray-casting point-in-polygon test.
    boolean inside = false;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      double yi = poly.get(i)[1], yj = poly.get(j)[1];
      double xi = poly.get(i)[0], xj = poly.get(j)[0];
      if ((yi > py) != (yj > py) &&
          px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
        inside = !inside;
      }
    }
    if (inside) {
      // Inside: always fully tinted. Feather applies outward from the boundary only.
      return 1.0;
    } else {
      if (feather <= 0.0) return 0.0;
      // Outside: distance-based falloff from polygon edges.
      double minDist = Double.MAX_VALUE;
      for (int i = 0, j = n - 1; i < n; j = i++) {
        double d = pointToSegmentDist(px, py,
            poly.get(j)[0], poly.get(j)[1],
            poly.get(i)[0], poly.get(i)[1]);
        if (d < minDist) minDist = d;
      }
      if (minDist >= feather) return 0.0;
      return 1.0 - minDist / feather;
    }
  }

  private static double pointToSegmentDist(double px, double py, double ax, double ay, double bx, double by) {
    double dx = bx - ax, dy = by - ay;
    double lenSq = dx * dx + dy * dy;
    if (lenSq < 1e-12) return Math.sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay));
    double t = clamp(((px - ax) * dx + (py - ay) * dy) / lenSq, 0.0, 1.0);
    double cx = ax + t * dx, cy = ay + t * dy;
    return Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
  }

  private static double applyBlend(double base, double zone, double weight, int mode) {
    double b = clamp(base, 0.0, 1.0);
    double s = clamp(zone, 0.0, 1.0);
    double w = clamp(weight, 0.0, 1.0);
    if (w <= 0.0) return b;
    if (mode == 0) {
      return b * (1.0 - w) + s * w;
    }

    double bLin = srgbToLinear(b);
    double sLin = srgbToLinear(s);
    double blended;
    switch (mode) {
      case 1: // Multiply
        blended = bLin * sLin;
        break;
      case 2: // Screen
        blended = 1.0 - (1.0 - bLin) * (1.0 - sLin);
        break;
      case 3: // Overlay
        blended = bLin < 0.5 ? 2.0 * bLin * sLin : 1.0 - 2.0 * (1.0 - bLin) * (1.0 - sLin);
        break;
      case 4: // Soft Light
        blended = sLin <= 0.5
            ? bLin - (1.0 - 2.0 * sLin) * bLin * (1.0 - bLin)
            : bLin + (2.0 * sLin - 1.0) * (softLightCurve(bLin) - bLin);
        break;
      case 5: // Hard Light
        blended = sLin < 0.5 ? 2.0 * bLin * sLin : 1.0 - 2.0 * (1.0 - bLin) * (1.0 - sLin);
        break;
      case 6: // Color Dodge
        blended = sLin >= (1.0 - 1e-6) ? 1.0 : clamp(bLin / (1.0 - sLin), 0.0, 1.0);
        break;
      case 7: // Color Burn
        blended = sLin <= 1e-6 ? 0.0 : 1.0 - clamp((1.0 - bLin) / sLin, 0.0, 1.0);
        break;
      case 8: // Difference
        blended = Math.abs(bLin - sLin);
        break;
      case 9: // Exclusion
        blended = bLin + sLin - (2.0 * bLin * sLin);
        break;
      case 10: // Lighten
        blended = Math.max(bLin, sLin);
        break;
      case 11: // Darken
        blended = Math.min(bLin, sLin);
        break;
      case 12: // Add
        blended = Math.min(1.0, bLin + sLin);
        break;
      case 13: // Subtract
        blended = Math.max(0.0, bLin - sLin);
        break;
      default: // Normal
        blended = sLin;
        break;
    }
    double blendedSrgb = linearToSrgb(clamp(blended, 0.0, 1.0));
    return b * (1.0 - w) + blendedSrgb * w;
  }

  private static int blendModeIndex(String mode) {
    return switch (canonicalBlendMode(mode, "Normal")) {
      case "Multiply" -> 1;
      case "Screen" -> 2;
      case "Overlay" -> 3;
      case "Soft Light" -> 4;
      case "Hard Light" -> 5;
      case "Color Dodge" -> 6;
      case "Color Burn" -> 7;
      case "Difference" -> 8;
      case "Exclusion" -> 9;
      case "Lighten" -> 10;
      case "Darken" -> 11;
      case "Add" -> 12;
      case "Subtract" -> 13;
      default -> 0;
    };
  }

  private static double softLightCurve(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.25) {
      return ((16.0 * v - 12.0) * v + 4.0) * v;
    }
    return Math.sqrt(v);
  }

  private static double srgbToLinear(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.04045) return v / 12.92;
    return Math.pow((v + 0.055) / 1.055, 2.4);
  }

  private static double linearToSrgb(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.0031308) return v * 12.92;
    return 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
  }

  private String tintKey(String tag, Image source) {
    StringBuilder sb = new StringBuilder();
    sb.append(normalize(tag))
        .append("|").append(source.getWidth()).append("x").append(source.getHeight())
        .append("|").append(colorToHex(tintColorPicker.getValue()))
        .append("|").append(formatDouble(tintStrengthSlider.getValue()))
        .append("|").append(formatDouble(saturationSlider.getValue()))
        .append("|").append(formatDouble(contrastSlider.getValue()));
    for (int i = 0; i < tintZones.size(); i++) {
      TintZone z = tintZones.get(i);
      sb.append("|z").append(i).append(":")
          .append(formatDouble(z.boundsX)).append(",").append(formatDouble(z.boundsY)).append(",")
          .append(formatDouble(z.boundsW)).append(",").append(formatDouble(z.boundsH)).append(",")
          .append(colorToHex(z.color)).append(",")
          .append(formatDouble(z.strength)).append(",").append(formatDouble(z.saturation)).append(",")
          .append(formatDouble(z.contrast)).append(",").append(formatDouble(z.feather)).append(",")
          .append(formatDouble(z.rotation)).append(",").append(canonicalBlendMode(z.blendMode, "Normal"));
      if (z.isPolygon()) {
        sb.append(",poly");
        for (double[] pt : z.polygon) {
          sb.append(";").append(formatDouble(pt[0])).append(",").append(formatDouble(pt[1]));
        }
      }
    }
    return sb.toString();
  }

  private String backgroundTintKey(String tag, Image source, BackgroundTintParams params) {
    BackgroundTintParams p = params == null ? snapshotBackgroundTintParams() : params;
    return normalize(tag)
        + "|" + source.getWidth() + "x" + source.getHeight()
        + "|" + formatDouble(p.tr()) + "," + formatDouble(p.tg()) + "," + formatDouble(p.tb())
        + "|" + formatDouble(p.tintStrength())
        + "|" + formatDouble(p.satAdjust())
        + "|" + formatDouble(p.conAdjust())
        + "|" + p.tintBlendMode()
        + "|" + formatDouble(p.or()) + "," + formatDouble(p.og()) + "," + formatDouble(p.ob())
        + "|" + formatDouble(p.overlayOpacity())
        + "|" + p.overlayBlendMode();
  }

  private Image loadImage(String tag) {
    if (tag == null || tag.isBlank()) return null;
    String normalized = tag.trim().replace('\\', '/');
    if (normalized.isBlank()) return null;
    Image cached = imageCache.get(normalized);
    if (cached != null) return cached;

    PresetTagEntry preset = presetByTag.get(normalized);
    if (preset != null) {
      Image composed = composePresetImage(preset);
      if (composed != null) {
        imageCache.put(normalized, composed);
      }
      return composed;
    }

    File file = imageByTag.get(normalized);
    if (file == null && projectRoot != null) {
      file = projectRoot.toPath().resolve(normalized).toFile();
    }
    if (file == null || !file.isFile()) return null;
    try (InputStream in = Files.newInputStream(file.toPath())) {
      Image image = new Image(in);
      if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) return null;
      imageCache.put(normalized, image);
      return image;
    } catch (Exception ignored) {
      return null;
    }
  }

  private Image composePresetImage(PresetTagEntry preset) {
    if (preset == null || preset.layerTags().isEmpty()) return null;
    List<Image> layers = new ArrayList<>();
    int width = 1;
    int height = 1;
    for (String layerTag : preset.layerTags()) {
      String normalizedLayerTag = normalize(layerTag);
      if (normalizedLayerTag.isBlank() || normalizedLayerTag.equals(preset.tag())) continue;
      Image layer = loadImage(normalizedLayerTag);
      if (layer == null) continue;
      layers.add(layer);
      width = Math.max(width, (int) Math.round(layer.getWidth()));
      height = Math.max(height, (int) Math.round(layer.getHeight()));
    }
    if (layers.isEmpty()) return null;
    Canvas canvas = new Canvas(width, height);
    GraphicsContext gc = canvas.getGraphicsContext2D();
    for (Image layer : layers) {
      gc.drawImage(layer, 0, 0);
    }
    SnapshotParameters snapshotParameters = new SnapshotParameters();
    snapshotParameters.setFill(Color.TRANSPARENT);
    WritableImage out = new WritableImage(width, height);
    canvas.snapshot(snapshotParameters, out);
    return out;
  }

  private void drawCover(GraphicsContext g, Image image, double w, double h) {
    double iw = image.getWidth();
    double ih = image.getHeight();
    if (iw <= 0 || ih <= 0) return;
    double scale = Math.max(w / iw, h / ih);
    double drawW = iw * scale;
    double drawH = ih * scale;
    double x = (w - drawW) * 0.5;
    double y = (h - drawH) * 0.5;
    g.drawImage(image, x, y, drawW, drawH);
  }

  private static void drawChecker(GraphicsContext g, double w, double h) {
    g.setFill(Color.web("#17243c"));
    g.fillRect(0, 0, w, h);
    double tile = 24.0;
    for (double y = 0; y < h; y += tile) {
      for (double x = 0; x < w; x += tile) {
        boolean alt = (((int) (x / tile)) + ((int) (y / tile))) % 2 == 0;
        g.setFill(alt ? Color.web("#1d2f4d") : Color.web("#162740"));
        g.fillRect(x, y, tile, tile);
      }
    }
  }

  private static void drawHint(GraphicsContext g, double w, double h, String text) {
    g.setFill(Color.color(0, 0, 0, 0.55));
    g.fillRect(0, h - 38, w, 38);
    g.setFill(Color.web("#d4d9e2"));
    g.setTextAlign(TextAlignment.LEFT);
    g.fillText(text, 12, h - 14);
  }

  private void persistBackgroundTint(String backgroundTag) {
    String tag = normalize(backgroundTag);
    if (tag.isBlank()) return;
    String prefix = "bg." + encodeKey(tag) + ".";
    String tintColor = colorToHex(bgTintColorPicker.getValue());
    String tintStrength = formatDouble(bgTintStrengthSlider.getValue());
    String saturation = formatDouble(bgSaturationSlider.getValue());
    String contrast = formatDouble(bgContrastSlider.getValue());
    persisted.setProperty(prefix + "tintColor", tintColor);
    persisted.setProperty(prefix + "tintStrength", tintStrength);
    persisted.setProperty(prefix + "saturation", saturation);
    persisted.setProperty(prefix + "contrast", contrast);
    persisted.setProperty(prefix + "tintBlend", canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal"));
    persisted.setProperty(prefix + "overlayColor", colorToHex(bgOverlayColorPicker.getValue()));
    persisted.setProperty(prefix + "overlayOpacity", formatDouble(bgOverlayOpacitySlider.getValue()));
    persisted.setProperty(prefix + "overlayBlend", canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay"));
    // Backward compatibility for older keys used by previous tool versions.
    persisted.setProperty(prefix + "color", tintColor);
    persisted.setProperty(prefix + "strength", tintStrength);
    savePersistentState();
  }

  private void applyBackgroundTintIfPresent(String backgroundTag) {
    String tag = normalize(backgroundTag);
    if (tag.isBlank()) {
      resetBackgroundFxControls();
      return;
    }
    String prefix = "bg." + encodeKey(tag) + ".";
    boolean hasBackgroundFx = persisted.containsKey(prefix + "tintColor")
        || persisted.containsKey(prefix + "color")
        || persisted.containsKey(prefix + "tintBlend")
        || persisted.containsKey(prefix + "overlayColor")
        || persisted.containsKey(prefix + "overlayOpacity");
    if (!hasBackgroundFx) {
      resetBackgroundFxControls();
      return;
    }
    applyingState = true;
    try {
      bgTintColorPicker.setValue(parseColor(
          persisted.getProperty(prefix + "tintColor", persisted.getProperty(prefix + "color")),
          bgTintColorPicker.getValue()));
      bgTintStrengthSlider.setValue(parseDouble(
          persisted.getProperty(prefix + "tintStrength", persisted.getProperty(prefix + "strength")),
          bgTintStrengthSlider.getValue()));
      bgSaturationSlider.setValue(parseDouble(persisted.getProperty(prefix + "saturation"), bgSaturationSlider.getValue()));
      bgContrastSlider.setValue(parseDouble(persisted.getProperty(prefix + "contrast"), bgContrastSlider.getValue()));
      bgTintBlendModeBox.getSelectionModel().select(
          canonicalBlendMode(persisted.getProperty(prefix + "tintBlend"), bgTintBlendModeBox.getValue()));
      bgOverlayColorPicker.setValue(parseColor(persisted.getProperty(prefix + "overlayColor"), bgOverlayColorPicker.getValue()));
      bgOverlayOpacitySlider.setValue(parseDouble(persisted.getProperty(prefix + "overlayOpacity"), bgOverlayOpacitySlider.getValue()));
      bgOverlayBlendModeBox.getSelectionModel().select(
          canonicalBlendMode(persisted.getProperty(prefix + "overlayBlend"), bgOverlayBlendModeBox.getValue()));
    } finally {
      applyingState = false;
    }
    onBackgroundFxChanged(true);
  }

  private void persistGlobalState() {
    persisted.setProperty("global.filter", normalize(filterField.getText()));
    persisted.setProperty("global.assetScope", normalize(assetScopeBox.getValue()));
    persisted.setProperty("global.characterTag", selectedCharacterTag());
    persisted.setProperty("global.backgroundTag", selectedBackgroundTag());
    persisted.setProperty("global.exportFormat", normalize(exportFormatBox.getValue()));
    persisted.setProperty("global.tintColor", colorToHex(tintColorPicker.getValue()));
    persisted.setProperty("global.tintStrength", formatDouble(tintStrengthSlider.getValue()));
    persisted.setProperty("global.saturation", formatDouble(saturationSlider.getValue()));
    persisted.setProperty("global.contrast", formatDouble(contrastSlider.getValue()));
    persisted.setProperty("global.bgTintColor", colorToHex(bgTintColorPicker.getValue()));
    persisted.setProperty("global.bgTintStrength", formatDouble(bgTintStrengthSlider.getValue()));
    persisted.setProperty("global.bgSaturation", formatDouble(bgSaturationSlider.getValue()));
    persisted.setProperty("global.bgContrast", formatDouble(bgContrastSlider.getValue()));
    persisted.setProperty("global.bgTintBlend", canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal"));
    persisted.setProperty("global.bgOverlayColor", colorToHex(bgOverlayColorPicker.getValue()));
    persisted.setProperty("global.bgOverlayOpacity", formatDouble(bgOverlayOpacitySlider.getValue()));
    persisted.setProperty("global.bgOverlayBlend", canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay"));
    persisted.setProperty("global.zoom", formatDouble(zoom));
    persisted.setProperty("global.offsetX", formatDouble(offsetX));
    persisted.setProperty("global.offsetY", formatDouble(offsetY));
    boolean hideControls = controlsPane != null && !controlsPane.isExpanded();
    persisted.setProperty("global.hideControls", Boolean.toString(hideControls));
    boolean hideBackgroundFx = backgroundPane != null && !backgroundPane.isExpanded();
    persisted.setProperty("global.hideBackgroundFx", Boolean.toString(hideBackgroundFx));
    boolean hideZones = zonesPane != null && !zonesPane.isExpanded();
    persisted.setProperty("global.hideZones", Boolean.toString(hideZones));
    savePersistentState();
  }

  private void loadPersistentState() {
    flushPendingStateSave();
    persisted.clear();
    Path file = statePath();
    if (file == null || !Files.exists(file)) return;
    try (InputStream in = Files.newInputStream(file)) {
      persisted.load(in);
    } catch (Exception ignored) {
    }
  }

  private void savePersistentState() {
    stateSavePending = true;
    stateSaveDebounce.playFromStart();
  }

  private void flushPendingStateSave() {
    if (!stateSavePending) return;
    stateSaveDebounce.stop();
    stateSavePending = false;
    savePersistentStateNow();
  }

  private void savePersistentStateNow() {
    Path file = statePath();
    if (file == null) return;
    try {
      Path parent = file.getParent();
      if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
      try (OutputStream out = Files.newOutputStream(file)) {
        persisted.store(out, "JVN Image Tint Tool State");
      }
    } catch (Exception ignored) {
    }
  }

  private Path statePath() {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return projectRoot.toPath().resolve(STATE_FILE);
  }

  private void clearPrefix(String prefix) {
    List<String> keys = new ArrayList<>(persisted.stringPropertyNames());
    for (String key : keys) {
      if (key.startsWith(prefix)) persisted.remove(key);
    }
  }

  private boolean isImageFile(Path path) {
    String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return n.endsWith(".png")
        || n.endsWith(".jpg")
        || n.endsWith(".jpeg")
        || n.endsWith(".webp")
        || n.endsWith(".gif");
  }

  private static boolean isIgnoredPath(String relative) {
    String n = normalize(relative);
    return n.startsWith(".git/")
        || n.startsWith(".gradle/")
        || n.startsWith("build/")
        || n.startsWith("out/");
  }

  private static boolean isVnsFile(Path path) {
    String n = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
    return n.endsWith(".vns");
  }

  private static boolean isLikelyBackgroundTag(String tag) {
    String n = normalize(tag);
    return n.contains("/background")
        || n.contains("/backgrounds/")
        || n.contains("/bg/")
        || n.contains("/scene/")
        || n.contains("/scenes/")
        || n.contains("bg_")
        || n.contains("background_")
        || n.contains("field")
        || n.contains("mainmenu");
  }

  static boolean matchesTagSearch(String tag, String queryRaw) {
    String normalizedTag = normalize(tag).toLowerCase(Locale.ROOT);
    if (normalizedTag.isBlank()) return false;
    String query = normalize(queryRaw).toLowerCase(Locale.ROOT);
    if (query.isBlank()) return true;

    boolean presetTag = normalizedTag.startsWith(PRESET_TAG_PREFIX);
    boolean backgroundTag = !presetTag && isLikelyBackgroundTag(normalizedTag);

    String[] terms = query.split("\\s+");
    for (String term : terms) {
      String t = normalize(term).toLowerCase(Locale.ROOT);
      if (t.isBlank()) continue;
      if (!matchesSearchTerm(normalizedTag, t, presetTag, backgroundTag)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesSearchTerm(String normalizedTag, String term, boolean presetTag, boolean backgroundTag) {
    int sep = term.indexOf(':');
    if (sep <= 0) {
      return normalizedTag.contains(term);
    }
    String key = term.substring(0, sep);
    String value = term.substring(sep + 1);
    boolean valueMatches = value.isBlank() || normalizedTag.contains(value);

    switch (key) {
      case "preset":
      case "charpreset":
        return presetTag && valueMatches;
      case "char":
      case "character":
        return (presetTag || !backgroundTag) && valueMatches;
      case "asset":
      case "image":
      case "file":
        return !presetTag && valueMatches;
      case "bg":
      case "background":
        return backgroundTag && valueMatches;
      default:
        return normalizedTag.contains(term);
    }
  }

  static String pickDefaultCharacterTag(List<String> tags) {
    if (tags == null || tags.isEmpty()) return "";
    for (String tag : tags) {
      if (!isLikelyBackgroundTag(tag)) return normalize(tag);
    }
    return normalize(tags.get(0));
  }

  static String pickDefaultBackgroundTag(List<String> tags) {
    if (tags == null || tags.isEmpty()) return "";
    for (String tag : tags) {
      if (isLikelyBackgroundTag(tag)) return normalize(tag);
    }
    return "";
  }

  private String selectedCharacterTag() {
    return selectedTag(characterTagBox);
  }

  private String selectedBackgroundTag() {
    return selectedTag(backgroundTagBox);
  }

  private static String selectedTag(ComboBox<String> box) {
    if (box == null) return "";
    String selected = normalize(box.getValue());
    if (!selected.isBlank()) return selected;
    return normalize(box.getEditor() == null ? "" : box.getEditor().getText());
  }

  private static String shortTag(String tag) {
    String value = normalize(tag);
    if (value.isBlank()) return "(none)";
    if (value.length() <= 48) return value;
    return "..." + value.substring(value.length() - 45);
  }

  private List<String> buildCharacterTagList(String scopeRaw) {
    return filterCharacterTagsForScope(
        new ArrayList<>(imageByTag.keySet()),
        new ArrayList<>(presetByTag.keySet()),
        scopeRaw);
  }

  static List<String> filterCharacterTagsForScope(List<String> imageTags, List<String> presetTags, String scopeRaw) {
    String scope = normalize(scopeRaw);
    List<String> tags = new ArrayList<>();
    boolean includePresets = false;
    boolean includeAllImages = false;
    boolean includeCharacterImages = false;

    if (ASSET_SCOPE_CHARACTER_PRESETS_ONLY.equals(scope)) {
      includePresets = true;
    } else if (ASSET_SCOPE_CHARACTER_ASSETS_ONLY.equals(scope)) {
      includeCharacterImages = true;
    } else if (ASSET_SCOPE_ALL_ASSETS_AND_PRESETS.equals(scope)) {
      includePresets = true;
      includeAllImages = true;
    } else {
      includePresets = true;
      includeCharacterImages = true;
    }

    if (includeAllImages && imageTags != null) {
      tags.addAll(imageTags);
    } else if (includeCharacterImages && imageTags != null) {
      for (String tag : imageTags) {
        if (!isLikelyBackgroundTag(tag)) {
          tags.add(tag);
        }
      }
    }
    if (includePresets && presetTags != null) {
      tags.addAll(presetTags);
    }

    tags.sort(String.CASE_INSENSITIVE_ORDER);
    return tags;
  }

  private boolean isKnownCharacterTag(String tag) {
    String n = normalize(tag);
    return imageByTag.containsKey(n) || presetByTag.containsKey(n);
  }

  private Map<String, PresetTagEntry> scanScriptCharpresets(Path root, Task<?> scanTask) {
    Map<String, PresetTagEntry> out = new LinkedHashMap<>();
    if (root == null) return out;
    Map<String, Map<String, String>> layersByCharacter = new LinkedHashMap<>();
    List<PresetDecl> declarations = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root, 10)) {
      List<String> scriptFiles = stream
          .filter(Files::isRegularFile)
          .filter(ImageTintToolView::isVnsFile)
          .map(path -> root.relativize(path).toString().replace('\\', '/'))
          .filter(relative -> !isIgnoredPath(relative))
          .sorted(Comparator.naturalOrder())
          .toList();
      for (String relative : scriptFiles) {
        if (scanTask != null && scanTask.isCancelled()) return out;
        parsePresetDeclarations(root.resolve(relative), layersByCharacter, declarations);
      }
    } catch (Exception ignored) {
      return out;
    }

    for (PresetDecl declaration : declarations) {
      if (scanTask != null && scanTask.isCancelled()) return out;
      List<String> layers = resolvePresetLayerTags(layersByCharacter, declaration.characterId(), declaration.spec());
      if (layers.isEmpty()) continue;
      String tag = buildPresetTag(declaration.characterId(), declaration.expressionId());
      out.put(tag, new PresetTagEntry(tag, layers));
    }
    return out;
  }

  private void parsePresetDeclarations(Path scriptFile,
                                       Map<String, Map<String, String>> layersByCharacter,
                                       List<PresetDecl> declarations) {
    if (scriptFile == null) return;
    List<String> lines;
    try {
      lines = Files.readAllLines(scriptFile, StandardCharsets.UTF_8);
    } catch (Exception ignored) {
      return;
    }
    for (String rawLine : lines) {
      if (rawLine == null) continue;
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;

      Matcher layerMatcher = CHARLAYER_PATTERN.matcher(rawLine);
      if (layerMatcher.find()) {
        String characterId = normalize(layerMatcher.group(1));
        String layerId = normalize(layerMatcher.group(2));
        String path = normalize(unquote(layerMatcher.group(3)));
        if (characterId.isBlank() || layerId.isBlank() || path.isBlank()) continue;
        layersByCharacter.computeIfAbsent(characterId, k -> new LinkedHashMap<>()).put(layerId, path);
        continue;
      }

      Matcher presetMatcher = CHARPRESET_PATTERN.matcher(rawLine);
      if (presetMatcher.find()) {
        String characterId = normalize(presetMatcher.group(1));
        String expressionId = normalize(presetMatcher.group(2));
        String spec = normalize(presetMatcher.group(3));
        if (characterId.isBlank() || expressionId.isBlank() || spec.isBlank()) continue;
        declarations.add(new PresetDecl(characterId, expressionId, spec));
      }
    }
  }

  static String buildPresetTag(String characterId, String expressionId) {
    String character = normalize(characterId);
    String expression = normalize(expressionId);
    if (character.isBlank() || expression.isBlank()) return "";
    return PRESET_TAG_PREFIX + character + "/" + expression;
  }

  static List<String> resolvePresetLayerTags(Map<String, Map<String, String>> layersByCharacter,
                                             String defaultCharacterId,
                                             String spec) {
    List<String> resolved = new ArrayList<>();
    if (layersByCharacter == null) return resolved;
    String rawSpec = normalize(spec);
    if (rawSpec.isBlank()) return resolved;

    String[] tokens = rawSpec.split("\\|");
    for (String token : tokens) {
      String part = normalize(unquote(token));
      if (part.isBlank()) continue;
      if (part.startsWith("$")) {
        LayerRef layerRef = parseLayerRef(part.substring(1), defaultCharacterId);
        if (layerRef.characterId().isBlank() || layerRef.layerId().isBlank()) continue;
        Map<String, String> characterLayers = layersByCharacter.get(layerRef.characterId());
        if (characterLayers == null) continue;
        String path = normalize(characterLayers.get(layerRef.layerId()));
        if (path.isBlank()) continue;
        resolved.add(path);
      } else {
        resolved.add(part);
      }
    }
    return resolved;
  }

  private static LayerRef parseLayerRef(String rawRef, String defaultCharacterId) {
    String ref = normalize(rawRef);
    if (ref.isBlank()) return new LayerRef(normalize(defaultCharacterId), "");
    String characterId = normalize(defaultCharacterId);
    String layerId = ref;

    int colon = ref.indexOf(':');
    int dot = ref.indexOf('.');
    int sep = colon >= 0 ? colon : dot;
    if (colon >= 0 && dot >= 0) sep = Math.min(colon, dot);
    if (sep > 0 && sep < ref.length() - 1) {
      characterId = normalize(ref.substring(0, sep));
      layerId = normalize(ref.substring(sep + 1));
    }
    return new LayerRef(characterId, layerId);
  }

  private static String unquote(String raw) {
    String value = normalize(raw);
    if (value.length() < 2) return value;
    char first = value.charAt(0);
    char last = value.charAt(value.length() - 1);
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
      return value.substring(1, value.length() - 1).trim();
    }
    return value;
  }

  private static Slider slider(double min, double max, double value) {
    Slider slider = new Slider(min, max, value);
    slider.setShowTickMarks(false);
    slider.setShowTickLabels(false);
    return slider;
  }

  private static HBox sliderRow(String label, Slider slider) {
    Label l = new Label(label);
    l.setMinWidth(60);
    TextField valueField = new TextField(String.format(Locale.ROOT, "%.0f", slider.getValue()));
    valueField.setPrefWidth(58);
    slider.valueProperty().addListener((o, ov, nv) -> valueField.setText(String.format(Locale.ROOT, "%.0f", nv.doubleValue())));
    valueField.setOnAction(e -> {
      try {
        slider.setValue(Double.parseDouble(valueField.getText().trim()));
      } catch (Exception ignored) {
      }
    });
    HBox row = new HBox(8, l, slider, valueField);
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(slider, Priority.ALWAYS);
    return row;
  }

  private HBox tintPickerRow(String label, ColorPicker picker, Region swatch, Color fallback) {
    Label l = new Label(label);
    l.setMinWidth(90);

    swatch.setMinSize(26, 26);
    swatch.setPrefSize(26, 26);
    swatch.setMaxSize(26, 26);
    swatch.setMouseTransparent(true);

    picker.setMinSize(26, 26);
    picker.setPrefSize(26, 26);
    picker.setMaxSize(26, 26);
    picker.setStyle(
        "-fx-color-label-visible: false;"
            + " -fx-background-radius: 999;"
            + " -fx-border-radius: 999;");
    picker.setOpacity(0.001);

    updateColorSwatch(swatch, picker.getValue(), fallback);

    StackPane pickerHost = new StackPane(swatch, picker);
    pickerHost.setMinSize(26, 26);
    pickerHost.setPrefSize(26, 26);
    pickerHost.setMaxSize(26, 26);

    HBox row = new HBox(8, l, pickerHost);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private void updateTintColorSwatch(Color color) {
    updateColorSwatch(tintColorSwatch, color, Color.WHITE);
  }

  private void updateBackgroundSwatches() {
    updateColorSwatch(bgTintColorSwatch, bgTintColorPicker.getValue(), Color.WHITE);
    updateColorSwatch(bgOverlayColorSwatch, bgOverlayColorPicker.getValue(), Color.BLACK);
  }

  private static void updateColorSwatch(Region swatch, Color color, Color fallback) {
    if (swatch == null) return;
    Color base = fallback == null ? Color.WHITE : fallback;
    Color c = color == null ? base : color;
    int r = (int) Math.round(clamp(c.getRed(), 0.0, 1.0) * 255.0);
    int g = (int) Math.round(clamp(c.getGreen(), 0.0, 1.0) * 255.0);
    int b = (int) Math.round(clamp(c.getBlue(), 0.0, 1.0) * 255.0);
    String hex = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    swatch.setStyle(
        "-fx-background-color: " + hex + ";"
            + " -fx-background-radius: 999;"
            + " -fx-border-color: rgba(255,255,255,0.40);"
            + " -fx-border-radius: 999;"
            + " -fx-border-width: 1;");
  }

  private static HBox comboRow(String label, ComboBox<String> box) {
    Label l = new Label(label);
    l.setMinWidth(60);
    HBox row = new HBox(8, l, box);
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(box, Priority.ALWAYS);
    return row;
  }

  private Button iconButton(javafx.scene.layout.Region icon, String tooltip, Runnable action) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setMinSize(28, 28);
    button.setPrefSize(28, 28);
    button.setMaxSize(28, 28);
    button.setFocusTraversable(false);
    if (tooltip != null && !tooltip.isBlank()) button.setTooltip(new Tooltip(tooltip));
    button.setOnAction(e -> {
      if (action != null) action.run();
    });
    return button;
  }

  private static String colorToHex(Color color) {
    Color c = color == null ? Color.WHITE : color;
    int r = (int) Math.round(clamp(c.getRed(), 0.0, 1.0) * 255.0);
    int g = (int) Math.round(clamp(c.getGreen(), 0.0, 1.0) * 255.0);
    int b = (int) Math.round(clamp(c.getBlue(), 0.0, 1.0) * 255.0);
    return String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
  }

  private static Color parseColor(String raw, Color fallback) {
    String value = normalize(raw);
    if (value.isBlank()) return fallback == null ? Color.WHITE : fallback;
    try {
      return Color.web(value);
    } catch (Exception ignored) {
      return fallback == null ? Color.WHITE : fallback;
    }
  }

  private static String encodeKey(String raw) {
    String n = normalize(raw);
    if (n.isBlank()) return "_";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(n.getBytes(StandardCharsets.UTF_8));
  }

  private static String normalize(String raw) {
    if (raw == null) return "";
    return raw.trim().replace('\\', '/');
  }

  private static String canonicalBlendMode(String raw, String fallback) {
    String mode = normalize(raw);
    if (!mode.isBlank()) {
      for (String candidate : BLEND_MODES) {
        if (candidate.equalsIgnoreCase(mode)) return candidate;
      }
    }
    String fb = normalize(fallback);
    if (!fb.isBlank()) {
      for (String candidate : BLEND_MODES) {
        if (candidate.equalsIgnoreCase(fb)) return candidate;
      }
    }
    return BLEND_MODES[0];
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    String n = normalize(raw).toLowerCase(Locale.ROOT);
    if (n.isBlank()) return fallback;
    if ("true".equals(n) || "1".equals(n) || "yes".equals(n) || "on".equals(n)) return true;
    if ("false".equals(n) || "0".equals(n) || "no".equals(n) || "off".equals(n)) return false;
    return fallback;
  }

  private static double parseDouble(String raw, double fallback) {
    String n = normalize(raw);
    if (n.isBlank()) return fallback;
    try {
      double parsed = Double.parseDouble(n);
      return Double.isFinite(parsed) ? parsed : fallback;
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }

  private static String formatNormalized(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private void copy(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void status(String message) {
    statusLabel.setText(message == null ? "" : message);
  }

  private record PresetDecl(String characterId, String expressionId, String spec) {}

  private record PresetTagEntry(String tag, List<String> layerTags) {}

  private record LayerRef(String characterId, String layerId) {}

  private record TintCatalogScanResult(
      Map<String, File> images,
      Map<String, PresetTagEntry> presets,
      boolean cancelled
  ) {
    static TintCatalogScanResult cancelledResult() {
      return new TintCatalogScanResult(Map.of(), Map.of(), true);
    }
  }

  // ── TintZone model ──

  private static final class TintZone {
    String name;
    double boundsX, boundsY, boundsW, boundsH; // fractional 0..1 bounding rect
    Color color;
    double strength;   // 0..100
    double saturation; // -100..100
    double contrast;   // -100..100
    double feather;    // 0..100
    double rotation;   // degrees, -180..180 (only for rectangles)
    String blendMode;  // one of BLEND_MODES
    boolean overlayVisible; // preview overlay visibility only; tint effect remains active
    List<double[]> polygon; // fractional image coords (x,y pairs); null or <3 = rectangle

    TintZone(String name, double bx, double by, double bw, double bh) {
      this.name = name;
      this.boundsX = bx;
      this.boundsY = by;
      this.boundsW = bw;
      this.boundsH = bh;
      this.color = Color.web("#ff8844");
      this.strength = 50;
      this.saturation = 0;
      this.contrast = 0;
      this.feather = 15;
      this.rotation = 0;
      this.blendMode = "Normal";
      this.overlayVisible = true;
      this.polygon = null;
    }

    boolean isPolygon() {
      return polygon != null && polygon.size() >= 3;
    }

    @Override public String toString() {
      String shape = isPolygon()
          ? " poly(" + polygon.size() + ")"
          : String.format(Locale.ROOT, " [%.0f%%,%.0f%% %.0f%%x%.0f%%]",
              boundsX * 100, boundsY * 100, boundsW * 100, boundsH * 100);
      return (name == null || name.isBlank() ? "Zone" : name) + shape;
    }
  }

  // ── Zone UI builder ──

  private void buildZoneSection() {
    zoneBlendModeBox.getItems().setAll(BLEND_MODES);
    zoneBlendModeBox.getSelectionModel().select("Normal");
    zoneNameField.setPromptText("Zone name");

    zoneColorSwatch.setMinSize(18, 18);
    zoneColorSwatch.setMaxSize(18, 18);
    zoneColorSwatch.setPrefSize(18, 18);
    updateZoneColorSwatch(zoneColorPicker.getValue());

    zoneListView.setPrefHeight(100);
    zoneListView.setMaxHeight(140);
    zoneListView.setStyle("-fx-background-color: #1a2233; -fx-border-color: #2b3445;");
    zoneListView.setCellFactory(lv -> new ListCell<TintZone>() {
      private final Label textLabel = new Label();
      private final Region spacer = new Region();
      private final Button visibilityButton = new Button();
      private final HBox row = new HBox(6, textLabel, spacer, visibilityButton);

      {
        textLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        visibilityButton.setFocusTraversable(false);
        visibilityButton.setMinSize(22, 22);
        visibilityButton.setPrefSize(22, 22);
        visibilityButton.setMaxSize(22, 22);
        visibilityButton.setStyle("-fx-background-color: transparent; -fx-padding: 2;");
        visibilityButton.setOnAction(e -> {
          TintZone zone = getItem();
          if (zone == null) return;
          zone.overlayVisible = !zone.overlayVisible;
          updateVisibilityButton(zone.overlayVisible);
          zoneListView.refresh();
          persistZones();
          redrawPreview();
          status((zone.overlayVisible ? "Showing" : "Hiding") + " bounds for " + (zone.name == null ? "zone" : zone.name) + ".");
          e.consume();
        });
      }

      private void updateVisibilityButton(boolean visible) {
        visibilityButton.setGraphic(visible ? CssIcon.visibility("#8ab4f8") : CssIcon.visibilityOff("#6b7280"));
        visibilityButton.setTooltip(new Tooltip(visible ? "Hide bounds overlay" : "Show bounds overlay"));
      }

      @Override protected void updateItem(TintZone item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          setStyle("");
        } else {
          setText(null);
          textLabel.setText(item.toString());
          updateVisibilityButton(item.overlayVisible);
          setGraphic(row);
          boolean sel = getIndex() == selectedZoneIndex;
          textLabel.setStyle(sel ? "-fx-text-fill: #e2e8f0;" : "-fx-text-fill: #c8d0dc;");
          setStyle(sel
              ? "-fx-background-color: #2a4a6b; -fx-text-fill: #e2e8f0;"
              : "-fx-text-fill: #c8d0dc;");
        }
      }
    });
    zoneListView.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      int idx = nv == null ? -1 : nv.intValue();
      selectZone(idx);
    });

    zoneDrawToggleButton = iconButton(CssIcon.rectSelect("#8ab4f8"), "Drag on preview to draw a rectangular zone", this::toggleZoneDrawMode);
    polyDrawToggleButton = iconButton(CssIcon.polygon("#c49cf8"), "Click on preview to place polygon vertices; click near first point or double-click to close", this::togglePolyDrawMode);
    freehandDrawToggleButton = iconButton(CssIcon.freehand("#7dd3fc"), "Hold and draw to create a smoothed freehand zone", this::toggleFreehandDrawMode);

    Button addZoneButton = iconButton(CssIcon.plus("#9ed67a"), "Add zone at center", this::addDefaultZone);
    Button removeZoneButton = iconButton(CssIcon.minus("#f38ba8"), "Remove selected zone", this::removeSelectedZone);
    Button clearZonesButton = iconButton(CssIcon.clearX("#f5b971"), "Remove all zones", this::clearAllZones);
    Button moveUpButton = iconButton(CssIcon.arrowUp("#8ab4f8"), "Move zone up in order", this::moveZoneUp);
    Button moveDownButton = iconButton(CssIcon.arrowDown("#8ab4f8"), "Move zone down in order", this::moveZoneDown);

    HBox zoneActions = new HBox(6, zoneDrawToggleButton, polyDrawToggleButton, freehandDrawToggleButton, addZoneButton, removeZoneButton, clearZonesButton, moveUpButton, moveDownButton);
    zoneActions.setAlignment(Pos.CENTER_LEFT);

    // Per-zone controls
    zoneColorPicker.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      updateZoneColorSwatch(nv);
      applyZoneControlsToSelected();
    });
    zoneStrengthSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneSaturationSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneContrastSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneFeatherSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneRotationSlider.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneBlendModeBox.valueProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneControlsToSelected(); });
    zoneNameField.textProperty().addListener((o, ov, nv) -> { if (!applyingState) applyZoneNameToSelected(); });

    HBox zoneNameRow = new HBox(8, new Label("Name"), zoneNameField);
    zoneNameRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(zoneNameField, Priority.ALWAYS);

    HBox zoneColorRow = new HBox(8, new Label("Color"), zoneColorSwatch, zoneColorPicker);
    zoneColorRow.setAlignment(Pos.CENTER_LEFT);

    HBox zoneBlendRow = new HBox(8, new Label("Blend"), zoneBlendModeBox);
    zoneBlendRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(zoneBlendModeBox, Priority.ALWAYS);

    zoneControlsSection.getChildren().setAll(
        zoneNameRow,
        zoneColorRow,
        sliderRow("Strength", zoneStrengthSlider),
        sliderRow("Saturation", zoneSaturationSlider),
        sliderRow("Contrast", zoneContrastSlider),
        sliderRow("Feather", zoneFeatherSlider),
        sliderRow("Rotation", zoneRotationSlider),
        zoneBlendRow);
    zoneControlsSection.setDisable(true);

    VBox zonesContent = new VBox(6, zoneActions, zoneListView, zoneControlsSection);
    zonesPane = new TitledPane("Zone Area Selector", zonesContent);
    zonesPane.setExpanded(false);
    zonesPane.setAnimated(false);
    zonesPane.setCollapsible(true);
    zonesPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });
  }

  // ── Zone drawing ──

  private void toggleZoneDrawMode() {
    // Turn off other draw modes if active.
    if (polyDrawMode) {
      polyDrawMode = false;
      nailPoints.clear();
    }
    if (freehandDrawMode) {
      freehandDrawMode = false;
      drawingFreehand = false;
      freehandPoints.clear();
    }
    zoneDrawMode = !zoneDrawMode;
    drawingZone = false;
    resetDrawButtons();
    if (zoneDrawMode) {
      zoneDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      zoneDrawToggleButton.setGraphic(CssIcon.rectSelect("#1a1a2e"));
      status("Rect draw mode ON — drag on preview to create a rectangular zone.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void togglePolyDrawMode() {
    // Turn off other draw modes if active.
    if (zoneDrawMode) {
      zoneDrawMode = false;
      drawingZone = false;
    }
    if (freehandDrawMode) {
      freehandDrawMode = false;
      drawingFreehand = false;
      freehandPoints.clear();
    }
    polyDrawMode = !polyDrawMode;
    nailPoints.clear();
    resetDrawButtons();
    if (polyDrawMode) {
      polyDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      polyDrawToggleButton.setGraphic(CssIcon.polygon("#1a1a2e"));
      status("Polygon draw mode ON — click to place vertices, click first point or double-click to close.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void toggleFreehandDrawMode() {
    // Turn off other draw modes if active.
    if (zoneDrawMode) {
      zoneDrawMode = false;
      drawingZone = false;
    }
    if (polyDrawMode) {
      polyDrawMode = false;
      nailPoints.clear();
    }
    freehandDrawMode = !freehandDrawMode;
    drawingFreehand = false;
    freehandPoints.clear();
    resetDrawButtons();
    if (freehandDrawMode) {
      freehandDrawToggleButton.setStyle("-fx-background-color: #d4a017; -fx-padding: 4;");
      freehandDrawToggleButton.setGraphic(CssIcon.freehand("#1a1a2e"));
      status("Freehand draw mode ON — hold and draw a zone boundary, then release to auto-smooth.");
    } else {
      status("Draw mode OFF.");
    }
    redrawPreview();
  }

  private void resetDrawButtons() {
    zoneDrawToggleButton.setStyle("-fx-background-color: #2b3445; -fx-padding: 4;");
    zoneDrawToggleButton.setGraphic(CssIcon.rectSelect("#8ab4f8"));
    polyDrawToggleButton.setStyle("-fx-background-color: #2b3445; -fx-padding: 4;");
    polyDrawToggleButton.setGraphic(CssIcon.polygon("#c49cf8"));
    freehandDrawToggleButton.setStyle("-fx-background-color: #2b3445; -fx-padding: 4;");
    freehandDrawToggleButton.setGraphic(CssIcon.freehand("#7dd3fc"));
  }

  private void appendFreehandPoint(double x, double y) {
    if (freehandPoints.isEmpty()) {
      freehandPoints.add(new double[]{x, y});
      return;
    }
    double[] last = freehandPoints.get(freehandPoints.size() - 1);
    double dx = x - last[0];
    double dy = y - last[1];
    if ((dx * dx + dy * dy) < (FREEHAND_CAPTURE_MIN_DISTANCE * FREEHAND_CAPTURE_MIN_DISTANCE)) {
      return;
    }
    freehandPoints.add(new double[]{x, y});
  }

  private boolean isNearFirstNail(double mx, double my) {
    if (nailPoints.isEmpty()) return false;
    double[] first = nailPoints.get(0);
    double dx = mx - first[0];
    double dy = my - first[1];
    return (dx * dx + dy * dy) <= (8.0 * 8.0); // 8px snap radius
  }

  private void commitPolygonZone() {
    if (nailPoints.size() < 3) {
      status("Need at least 3 points for a polygon zone.");
      nailPoints.clear();
      redrawPreview();
      return;
    }
    Image img = loadImage(selectedCharacterTag());
    if (img == null) {
      status("No character image to place zone on.");
      nailPoints.clear();
      redrawPreview();
      return;
    }
    double cw = previewCanvas.getWidth();
    double ch = previewCanvas.getHeight();
    double imgDrawW = img.getWidth() * zoom;
    double imgDrawH = img.getHeight() * zoom;
    double imgDrawX = (cw - imgDrawW) * 0.5 + offsetX;
    double imgDrawY = (ch - imgDrawH) * 0.5 + offsetY;

    // Convert canvas coords to fractional image coords and compute bounding rect.
    List<double[]> fracPoly = new ArrayList<>();
    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
    for (double[] pt : nailPoints) {
      double fx = clamp((pt[0] - imgDrawX) / imgDrawW, 0.0, 1.0);
      double fy = clamp((pt[1] - imgDrawY) / imgDrawH, 0.0, 1.0);
      fracPoly.add(new double[]{fx, fy});
      if (fx < minX) minX = fx;
      if (fy < minY) minY = fy;
      if (fx > maxX) maxX = fx;
      if (fy > maxY) maxY = fy;
    }
    double bw = maxX - minX;
    double bh = maxY - minY;
    if (bw < 0.005 || bh < 0.005) {
      status("Polygon too small.");
      nailPoints.clear();
      redrawPreview();
      return;
    }

    TintZone zone = new TintZone("Poly " + (tintZones.size() + 1), minX, minY, bw, bh);
    zone.polygon = fracPoly;
    zone.color = zoneColorPicker.getValue() == null ? Color.web("#ff8844") : zoneColorPicker.getValue();
    zone.strength = zoneStrengthSlider.getValue();
    zone.saturation = zoneSaturationSlider.getValue();
    zone.contrast = zoneContrastSlider.getValue();
    zone.feather = zoneFeatherSlider.getValue();
    zone.blendMode = canonicalBlendMode(zoneBlendModeBox.getValue(), "Normal");
    tintZones.add(zone);
    nailPoints.clear();
    invalidateTintCache();
    refreshZoneList();
    selectZone(tintZones.size() - 1);
    persistZones();
    redrawPreview();
    status("Created polygon " + zone.name + " with " + zone.polygon.size() + " vertices.");
  }

  private void commitDrawnZone() {
    Image img = loadImage(selectedCharacterTag());
    if (img == null) {
      status("No character image to place zone on.");
      return;
    }
    double cw = previewCanvas.getWidth();
    double ch = previewCanvas.getHeight();
    double drawW = img.getWidth() * zoom;
    double drawH = img.getHeight() * zoom;
    double drawX = (cw - drawW) * 0.5 + offsetX;
    double drawY = (ch - drawH) * 0.5 + offsetY;

    // Convert canvas coords to fractional image coords.
    double rx = Math.min(zoneDrawStartX, zoneDrawEndX);
    double ry = Math.min(zoneDrawStartY, zoneDrawEndY);
    double rw = Math.abs(zoneDrawEndX - zoneDrawStartX);
    double rh = Math.abs(zoneDrawEndY - zoneDrawStartY);
    if (rw < 4 || rh < 4) {
      status("Zone too small — drag a larger rectangle.");
      return;
    }

    double fx = clamp((rx - drawX) / drawW, 0.0, 1.0);
    double fy = clamp((ry - drawY) / drawH, 0.0, 1.0);
    double fw = clamp(rw / drawW, 0.001, 1.0 - fx);
    double fh = clamp(rh / drawH, 0.001, 1.0 - fy);

    TintZone zone = new TintZone("Zone " + (tintZones.size() + 1), fx, fy, fw, fh);
    zone.color = zoneColorPicker.getValue() == null ? Color.web("#ff8844") : zoneColorPicker.getValue();
    zone.strength = zoneStrengthSlider.getValue();
    zone.saturation = zoneSaturationSlider.getValue();
    zone.contrast = zoneContrastSlider.getValue();
    zone.feather = zoneFeatherSlider.getValue();
    zone.blendMode = canonicalBlendMode(zoneBlendModeBox.getValue(), "Normal");
    tintZones.add(zone);
    invalidateTintCache();
    refreshZoneList();
    selectZone(tintZones.size() - 1);
    persistZones();
    status("Created " + zone.name + ".");
  }

  private void commitFreehandZone() {
    if (freehandPoints.size() < 3) {
      freehandPoints.clear();
      status("Freehand stroke too short.");
      return;
    }
    Image img = loadImage(selectedCharacterTag());
    if (img == null) {
      freehandPoints.clear();
      status("No character image to place zone on.");
      return;
    }
    double iw = Math.max(1.0, img.getWidth());
    double ih = Math.max(1.0, img.getHeight());
    double cw = previewCanvas.getWidth();
    double ch = previewCanvas.getHeight();
    double drawW = iw * zoom;
    double drawH = ih * zoom;
    double drawX = (cw - drawW) * 0.5 + offsetX;
    double drawY = (ch - drawH) * 0.5 + offsetY;

    // Convert canvas coords to image pixel coordinates for stroke processing.
    List<double[]> strokePx = new ArrayList<>();
    for (double[] pt : freehandPoints) {
      double px = clamp((pt[0] - drawX) / drawW, 0.0, 1.0) * iw;
      double py = clamp((pt[1] - drawY) / drawH, 0.0, 1.0) * ih;
      strokePx.add(new double[]{px, py});
    }
    List<double[]> smoothPx = smoothFreehandStroke(
        strokePx,
        FREEHAND_CLOSE_DISTANCE,
        FREEHAND_RESAMPLE_STEP,
        FREEHAND_SPLINE_STEP,
        FREEHAND_SIMPLIFY_EPSILON,
        FREEHAND_MAX_VERTICES
    );
    if (smoothPx.size() < 3) {
      freehandPoints.clear();
      status("Freehand stroke did not form a valid zone.");
      return;
    }

    List<double[]> fracPoly = new ArrayList<>();
    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
    for (double[] pt : smoothPx) {
      double fx = clamp(pt[0] / iw, 0.0, 1.0);
      double fy = clamp(pt[1] / ih, 0.0, 1.0);
      fracPoly.add(new double[]{fx, fy});
      if (fx < minX) minX = fx;
      if (fy < minY) minY = fy;
      if (fx > maxX) maxX = fx;
      if (fy > maxY) maxY = fy;
    }
    double bw = maxX - minX;
    double bh = maxY - minY;
    double area = polygonAreaAbs(fracPoly);
    if (bw < 0.005 || bh < 0.005 || area < 0.00005) {
      freehandPoints.clear();
      status("Freehand zone too small.");
      return;
    }

    TintZone zone = new TintZone("Stroke " + (tintZones.size() + 1), minX, minY, bw, bh);
    zone.polygon = fracPoly;
    zone.color = zoneColorPicker.getValue() == null ? Color.web("#ff8844") : zoneColorPicker.getValue();
    zone.strength = zoneStrengthSlider.getValue();
    zone.saturation = zoneSaturationSlider.getValue();
    zone.contrast = zoneContrastSlider.getValue();
    zone.feather = zoneFeatherSlider.getValue();
    zone.blendMode = canonicalBlendMode(zoneBlendModeBox.getValue(), "Normal");
    tintZones.add(zone);
    freehandPoints.clear();
    invalidateTintCache();
    refreshZoneList();
    selectZone(tintZones.size() - 1);
    persistZones();
    status("Created freehand " + zone.name + " (" + zone.polygon.size() + " vertices from " + strokePx.size() + " samples).");
  }

  static List<double[]> smoothFreehandStroke(List<double[]> rawStroke,
                                             double closeDistancePx,
                                             double resampleStepPx,
                                             double splineStepPx,
                                             double simplifyEpsilonPx,
                                             int maxVertices) {
    if (rawStroke == null || rawStroke.size() < 3) return List.of();
    List<double[]> deduped = dedupeSequentialPoints(rawStroke, 0.75);
    if (deduped.size() < 3) return List.of();

    List<double[]> ring = toClosedRing(deduped, closeDistancePx);
    if (ring.size() < 3) return List.of();

    List<double[]> resampled = resampleClosedRing(ring, Math.max(0.75, resampleStepPx));
    if (resampled.size() < 3) return List.of();

    List<double[]> spline = catmullRomClosed(resampled, Math.max(0.6, splineStepPx));
    if (spline.size() < 3) return List.of();

    double epsilon = Math.max(0.2, simplifyEpsilonPx);
    List<double[]> simplified = simplifyClosedRingRdp(spline, epsilon);
    while (simplified.size() > Math.max(3, maxVertices) && epsilon < 20.0) {
      epsilon *= 1.25;
      simplified = simplifyClosedRingRdp(spline, epsilon);
    }
    if (simplified.size() > Math.max(3, maxVertices)) {
      simplified = downsampleRing(simplified, Math.max(3, maxVertices));
    }
    return simplified;
  }

  private static List<double[]> dedupeSequentialPoints(List<double[]> points, double minDistance) {
    if (points == null || points.isEmpty()) return List.of();
    double minDistSq = minDistance * minDistance;
    List<double[]> out = new ArrayList<>();
    for (double[] src : points) {
      if (src == null || src.length < 2) continue;
      double[] p = new double[]{src[0], src[1]};
      if (out.isEmpty()) {
        out.add(p);
        continue;
      }
      double[] last = out.get(out.size() - 1);
      double dx = p[0] - last[0];
      double dy = p[1] - last[1];
      if ((dx * dx + dy * dy) >= minDistSq) {
        out.add(p);
      }
    }
    return out;
  }

  private static List<double[]> toClosedRing(List<double[]> points, double closeDistance) {
    List<double[]> ring = dedupeSequentialPoints(points, 0.0001);
    if (ring.size() < 3) return List.of();
    double[] first = ring.get(0);
    double[] last = ring.get(ring.size() - 1);
    double dx = first[0] - last[0];
    double dy = first[1] - last[1];
    double closeDistSq = closeDistance * closeDistance;
    if ((dx * dx + dy * dy) <= closeDistSq) {
      ring.set(ring.size() - 1, new double[]{first[0], first[1]});
    } else {
      ring.add(new double[]{first[0], first[1]});
    }
    return ring;
  }

  private static List<double[]> normalizeRing(List<double[]> closedPoints) {
    if (closedPoints == null || closedPoints.isEmpty()) return List.of();
    List<double[]> out = new ArrayList<>();
    for (double[] p : closedPoints) {
      if (p == null || p.length < 2) continue;
      out.add(new double[]{p[0], p[1]});
    }
    if (out.size() < 3) return List.of();
    if (out.size() > 1) {
      double[] first = out.get(0);
      double[] last = out.get(out.size() - 1);
      if (distanceSq(first, last) < 1e-6) out.remove(out.size() - 1);
    }
    if (out.size() < 3) return List.of();
    return out;
  }

  private static List<double[]> resampleClosedRing(List<double[]> closedPoints, double step) {
    List<double[]> ring = normalizeRing(closedPoints);
    int n = ring.size();
    if (n < 3) return List.of();
    if (step <= 0.0) return ring;

    double[] cumulative = new double[n + 1];
    cumulative[0] = 0.0;
    for (int i = 0; i < n; i++) {
      double[] a = ring.get(i);
      double[] b = ring.get((i + 1) % n);
      cumulative[i + 1] = cumulative[i] + Math.sqrt(distanceSq(a, b));
    }
    double totalLength = cumulative[n];
    if (totalLength <= 1e-6) return ring;

    int samples = Math.max(3, (int) Math.round(totalLength / step));
    List<double[]> out = new ArrayList<>(samples);
    int seg = 0;
    for (int i = 0; i < samples; i++) {
      double target = (i * totalLength) / samples;
      while (seg < n - 1 && target > cumulative[seg + 1]) seg++;
      double segStart = cumulative[seg];
      double segEnd = cumulative[seg + 1];
      double segLen = Math.max(segEnd - segStart, 1e-6);
      double t = clamp((target - segStart) / segLen, 0.0, 1.0);
      double[] a = ring.get(seg);
      double[] b = ring.get((seg + 1) % n);
      out.add(new double[]{
          a[0] + (b[0] - a[0]) * t,
          a[1] + (b[1] - a[1]) * t
      });
    }
    return out;
  }

  private static List<double[]> catmullRomClosed(List<double[]> closedPoints, double targetStep) {
    List<double[]> ring = normalizeRing(closedPoints);
    int n = ring.size();
    if (n < 3) return List.of();

    List<double[]> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      double[] p0 = ring.get((i - 1 + n) % n);
      double[] p1 = ring.get(i);
      double[] p2 = ring.get((i + 1) % n);
      double[] p3 = ring.get((i + 2) % n);
      double segmentLength = Math.sqrt(distanceSq(p1, p2));
      int subdivisions = Math.max(1, Math.min(16, (int) Math.ceil(segmentLength / Math.max(0.4, targetStep))));
      for (int s = 0; s < subdivisions; s++) {
        double t = s / (double) subdivisions;
        out.add(catmullRomPoint(p0, p1, p2, p3, t));
      }
    }
    return dedupeSequentialPoints(out, 0.35);
  }

  private static double[] catmullRomPoint(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
    final double alpha = 0.5;
    double t0 = 0.0;
    double t1 = t0 + Math.pow(Math.sqrt(distanceSq(p0, p1)), alpha);
    double t2 = t1 + Math.pow(Math.sqrt(distanceSq(p1, p2)), alpha);
    double t3 = t2 + Math.pow(Math.sqrt(distanceSq(p2, p3)), alpha);
    if (Math.abs(t1 - t0) < 1e-6) t1 = t0 + 1e-6;
    if (Math.abs(t2 - t1) < 1e-6) t2 = t1 + 1e-6;
    if (Math.abs(t3 - t2) < 1e-6) t3 = t2 + 1e-6;

    double tt = t1 + (t2 - t1) * clamp(t, 0.0, 1.0);
    double[] a1 = lerp(p0, p1, (tt - t0) / (t1 - t0));
    double[] a2 = lerp(p1, p2, (tt - t1) / (t2 - t1));
    double[] a3 = lerp(p2, p3, (tt - t2) / (t3 - t2));
    double[] b1 = lerp(a1, a2, (tt - t0) / (t2 - t0));
    double[] b2 = lerp(a2, a3, (tt - t1) / (t3 - t1));
    return lerp(b1, b2, (tt - t1) / (t2 - t1));
  }

  private static double[] lerp(double[] a, double[] b, double t) {
    double clamped = clamp(t, 0.0, 1.0);
    return new double[]{
        a[0] + (b[0] - a[0]) * clamped,
        a[1] + (b[1] - a[1]) * clamped
    };
  }

  private static List<double[]> simplifyClosedRingRdp(List<double[]> closedPoints, double epsilon) {
    List<double[]> ring = normalizeRing(closedPoints);
    if (ring.size() < 4 || epsilon <= 0.0) return ring;

    int start = farthestFromCentroidIndex(ring);
    List<double[]> rotated = rotateRing(ring, start);
    List<double[]> open = new ArrayList<>(rotated);
    open.add(new double[]{rotated.get(0)[0], rotated.get(0)[1]});

    List<double[]> simplifiedOpen = simplifyPolylineRdp(open, epsilon);
    if (!simplifiedOpen.isEmpty()) simplifiedOpen.remove(simplifiedOpen.size() - 1);
    if (simplifiedOpen.size() < 3) return ring;
    return simplifiedOpen;
  }

  private static int farthestFromCentroidIndex(List<double[]> points) {
    double cx = 0.0;
    double cy = 0.0;
    for (double[] p : points) {
      cx += p[0];
      cy += p[1];
    }
    cx /= points.size();
    cy /= points.size();
    int bestIndex = 0;
    double bestDistSq = -1.0;
    for (int i = 0; i < points.size(); i++) {
      double[] p = points.get(i);
      double dx = p[0] - cx;
      double dy = p[1] - cy;
      double d = dx * dx + dy * dy;
      if (d > bestDistSq) {
        bestDistSq = d;
        bestIndex = i;
      }
    }
    return bestIndex;
  }

  private static List<double[]> rotateRing(List<double[]> points, int startIndex) {
    int n = points.size();
    List<double[]> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      double[] p = points.get((startIndex + i) % n);
      out.add(new double[]{p[0], p[1]});
    }
    return out;
  }

  private static List<double[]> simplifyPolylineRdp(List<double[]> points, double epsilon) {
    int n = points.size();
    if (n <= 2) return points;
    boolean[] keep = new boolean[n];
    keep[0] = true;
    keep[n - 1] = true;
    rdpMark(points, 0, n - 1, epsilon * epsilon, keep);
    List<double[]> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      if (keep[i]) {
        double[] p = points.get(i);
        out.add(new double[]{p[0], p[1]});
      }
    }
    return out;
  }

  private static void rdpMark(List<double[]> points, int start, int end, double epsilonSq, boolean[] keep) {
    if (end <= start + 1) return;
    double[] a = points.get(start);
    double[] b = points.get(end);
    int farthest = -1;
    double maxDistSq = -1.0;
    for (int i = start + 1; i < end; i++) {
      double d = pointToSegmentDistSq(points.get(i), a, b);
      if (d > maxDistSq) {
        maxDistSq = d;
        farthest = i;
      }
    }
    if (farthest >= 0 && maxDistSq > epsilonSq) {
      keep[farthest] = true;
      rdpMark(points, start, farthest, epsilonSq, keep);
      rdpMark(points, farthest, end, epsilonSq, keep);
    }
  }

  private static double pointToSegmentDistSq(double[] p, double[] a, double[] b) {
    double dx = b[0] - a[0];
    double dy = b[1] - a[1];
    double lenSq = dx * dx + dy * dy;
    if (lenSq < 1e-12) {
      double px = p[0] - a[0];
      double py = p[1] - a[1];
      return px * px + py * py;
    }
    double t = clamp(((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / lenSq, 0.0, 1.0);
    double cx = a[0] + t * dx;
    double cy = a[1] + t * dy;
    double px = p[0] - cx;
    double py = p[1] - cy;
    return px * px + py * py;
  }

  private static List<double[]> downsampleRing(List<double[]> points, int maxVertices) {
    if (points.size() <= maxVertices) return points;
    List<double[]> out = new ArrayList<>(maxVertices);
    double step = points.size() / (double) maxVertices;
    for (int i = 0; i < maxVertices; i++) {
      int idx = Math.min(points.size() - 1, (int) Math.floor(i * step));
      double[] p = points.get(idx);
      out.add(new double[]{p[0], p[1]});
    }
    return out;
  }

  private static double polygonAreaAbs(List<double[]> polygon) {
    if (polygon == null || polygon.size() < 3) return 0.0;
    double area = 0.0;
    int n = polygon.size();
    for (int i = 0; i < n; i++) {
      double[] a = polygon.get(i);
      double[] b = polygon.get((i + 1) % n);
      area += a[0] * b[1] - b[0] * a[1];
    }
    return Math.abs(area) * 0.5;
  }

  private static double distanceSq(double[] a, double[] b) {
    double dx = a[0] - b[0];
    double dy = a[1] - b[1];
    return dx * dx + dy * dy;
  }

  // ── Zone management ──

  private void addDefaultZone() {
    // If polygon nail points are active, commit them as a polygon zone.
    if (polyDrawMode && nailPoints.size() >= 3) {
      commitPolygonZone();
      return;
    }
    if (freehandDrawMode && freehandPoints.size() >= 3) {
      drawingFreehand = false;
      commitFreehandZone();
      redrawPreview();
      return;
    }
    TintZone zone = new TintZone("Zone " + (tintZones.size() + 1), 0.25, 0.25, 0.5, 0.5);
    tintZones.add(zone);
    invalidateTintCache();
    refreshZoneList();
    selectZone(tintZones.size() - 1);
    persistZones();
    redrawPreview();
    status("Added " + zone.name + " at center.");
  }

  private void removeSelectedZone() {
    if (selectedZoneIndex < 0 || selectedZoneIndex >= tintZones.size()) {
      status("Select a zone first.");
      return;
    }
    String name = tintZones.get(selectedZoneIndex).name;
    tintZones.remove(selectedZoneIndex);
    invalidateTintCache();
    selectedZoneIndex = Math.min(selectedZoneIndex, tintZones.size() - 1);
    refreshZoneList();
    if (selectedZoneIndex >= 0) selectZone(selectedZoneIndex);
    else zoneControlsSection.setDisable(true);
    persistZones();
    redrawPreview();
    status("Removed " + name + ".");
  }

  private void clearAllZones() {
    if (tintZones.isEmpty()) return;
    tintZones.clear();
    selectedZoneIndex = -1;
    invalidateTintCache();
    refreshZoneList();
    zoneControlsSection.setDisable(true);
    persistZones();
    redrawPreview();
    status("All zones cleared.");
  }

  private void moveZoneUp() {
    if (selectedZoneIndex <= 0 || selectedZoneIndex >= tintZones.size()) return;
    TintZone z = tintZones.remove(selectedZoneIndex);
    tintZones.add(selectedZoneIndex - 1, z);
    invalidateTintCache();
    selectedZoneIndex--;
    refreshZoneList();
    selectZone(selectedZoneIndex);
    persistZones();
    redrawPreview();
  }

  private void moveZoneDown() {
    if (selectedZoneIndex < 0 || selectedZoneIndex >= tintZones.size() - 1) return;
    TintZone z = tintZones.remove(selectedZoneIndex);
    tintZones.add(selectedZoneIndex + 1, z);
    invalidateTintCache();
    selectedZoneIndex++;
    refreshZoneList();
    selectZone(selectedZoneIndex);
    persistZones();
    redrawPreview();
  }

  private void selectZone(int index) {
    selectedZoneIndex = (index >= 0 && index < tintZones.size()) ? index : -1;
    if (selectedZoneIndex < 0) {
      zoneControlsSection.setDisable(true);
      return;
    }
    zoneControlsSection.setDisable(false);
    TintZone zone = tintZones.get(selectedZoneIndex);
    applyingState = true;
    try {
      zoneNameField.setText(zone.name == null ? "" : zone.name);
      zoneColorPicker.setValue(zone.color == null ? Color.web("#ff8844") : zone.color);
      updateZoneColorSwatch(zoneColorPicker.getValue());
      zoneStrengthSlider.setValue(zone.strength);
      zoneSaturationSlider.setValue(zone.saturation);
      zoneContrastSlider.setValue(zone.contrast);
      zoneFeatherSlider.setValue(zone.feather);
      zoneRotationSlider.setValue(zone.rotation);
      zoneBlendModeBox.getSelectionModel().select(canonicalBlendMode(zone.blendMode, "Normal"));
    } finally {
      applyingState = false;
    }
    zoneListView.getSelectionModel().select(selectedZoneIndex);
    redrawPreview();
  }

  private void applyZoneControlsToSelected() {
    if (selectedZoneIndex < 0 || selectedZoneIndex >= tintZones.size()) return;
    TintZone zone = tintZones.get(selectedZoneIndex);
    zone.color = zoneColorPicker.getValue() == null ? Color.web("#ff8844") : zoneColorPicker.getValue();
    zone.strength = zoneStrengthSlider.getValue();
    zone.saturation = zoneSaturationSlider.getValue();
    zone.contrast = zoneContrastSlider.getValue();
    zone.feather = zoneFeatherSlider.getValue();
    zone.rotation = zoneRotationSlider.getValue();
    zone.blendMode = canonicalBlendMode(zoneBlendModeBox.getValue(), "Normal");
    invalidateTintCache();
    persistZones();
    redrawPreview();
  }

  private void applyZoneNameToSelected() {
    if (selectedZoneIndex < 0 || selectedZoneIndex >= tintZones.size()) return;
    tintZones.get(selectedZoneIndex).name = normalize(zoneNameField.getText());
    refreshZoneList();
    persistZones();
  }

  private void refreshZoneList() {
    applyingState = true;
    try {
      zoneListView.getItems().setAll(tintZones);
      if (selectedZoneIndex >= 0 && selectedZoneIndex < tintZones.size()) {
        zoneListView.getSelectionModel().select(selectedZoneIndex);
      }
    } finally {
      applyingState = false;
    }
  }

  private void invalidateTintCache() {
    tintedImageTag = null;
    tintedImageKey = null;
    tintedImage = null;
  }

  private void invalidateBackgroundTintCache() {
    cancelBackgroundTintTask();
    tintedBackgroundTag = null;
    tintedBackgroundKey = null;
    tintedBackground = null;
  }

  // ── Zone overlay drawing ──

  private void drawZoneOverlays(GraphicsContext g, Image img, double drawX, double drawY, double drawW, double drawH) {
    for (int i = 0; i < tintZones.size(); i++) {
      TintZone zone = tintZones.get(i);
      if (!zone.overlayVisible) continue;
      boolean selected = (i == selectedZoneIndex);
      Color zc = zone.color == null ? Color.web("#ff8844") : zone.color;

      if (zone.isPolygon()) {
        // Draw polygon overlay.
        List<double[]> poly = zone.polygon;
        double[] xs = new double[poly.size()];
        double[] ys = new double[poly.size()];
        for (int p = 0; p < poly.size(); p++) {
          xs[p] = drawX + poly.get(p)[0] * drawW;
          ys[p] = drawY + poly.get(p)[1] * drawH;
        }
        g.setFill(Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), selected ? 0.20 : 0.12));
        g.fillPolygon(xs, ys, poly.size());
        g.setStroke(selected
            ? Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), 0.9)
            : Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), 0.55));
        g.setLineWidth(selected ? 2.2 : 1.3);
        g.setLineDashes((double[]) null);
        g.strokePolygon(xs, ys, poly.size());
        // Vertex dots.
        g.setFill(selected ? Color.rgb(255, 240, 140, 0.95) : Color.rgb(255, 140, 140, 0.85));
        for (int p = 0; p < poly.size(); p++) {
          g.fillOval(xs[p] - 3, ys[p] - 3, 6, 6);
        }
        // Label at first vertex.
        String label = (zone.name == null || zone.name.isBlank() ? "Zone " + (i + 1) : zone.name);
        g.setFill(Color.color(1, 1, 1, 0.85));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(label, xs[0] + 6, ys[0] - 4);
      } else {
        // Draw rectangle overlay (with optional rotation).
        double zx = drawX + zone.boundsX * drawW;
        double zy = drawY + zone.boundsY * drawH;
        double zw = zone.boundsW * drawW;
        double zh = zone.boundsH * drawH;

        boolean rotated = zone.rotation != 0.0;
        if (rotated) {
          g.save();
          double rcx = zx + zw * 0.5, rcy = zy + zh * 0.5;
          g.translate(rcx, rcy);
          g.rotate(zone.rotation);
          g.translate(-rcx, -rcy);
        }

        g.setFill(Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), selected ? 0.18 : 0.10));
        g.fillRect(zx, zy, zw, zh);
        g.setStroke(selected
            ? Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), 0.9)
            : Color.color(zc.getRed(), zc.getGreen(), zc.getBlue(), 0.5));
        g.setLineWidth(selected ? 2.0 : 1.0);
        g.setLineDashes(selected ? null : new double[]{5, 3});
        g.strokeRect(zx, zy, zw, zh);
        g.setLineDashes((double[]) null);

        String label = (zone.name == null || zone.name.isBlank() ? "Zone " + (i + 1) : zone.name);
        g.setFill(Color.color(1, 1, 1, 0.85));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(label, zx + 4, zy + 13);

        if (rotated) g.restore();
      }
    }
  }

  private void updateZoneColorSwatch(Color color) {
    Color c = color == null ? Color.web("#ff8844") : color;
    int r = (int) Math.round(clamp(c.getRed(), 0.0, 1.0) * 255.0);
    int g = (int) Math.round(clamp(c.getGreen(), 0.0, 1.0) * 255.0);
    int b = (int) Math.round(clamp(c.getBlue(), 0.0, 1.0) * 255.0);
    String hex = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    zoneColorSwatch.setStyle(
        "-fx-background-color: " + hex + ";"
            + " -fx-background-radius: 999;"
            + " -fx-border-color: rgba(255,255,255,0.40);"
            + " -fx-border-radius: 999;"
            + " -fx-border-width: 1;");
  }

  // ── Zone persistence ──

  private String zoneProfilePrefix(String characterTag) {
    String tag = normalize(characterTag);
    if (tag.isBlank()) return ZONE_PROFILE_PREFIX + GLOBAL_ZONE_PROFILE_KEY + ".";
    return ZONE_PROFILE_PREFIX + encodeKey(tag) + ".";
  }

  private void writeZonesToPrefix(String prefix) {
    clearPrefix(prefix);
    persisted.setProperty(prefix + "count", Integer.toString(tintZones.size()));
    for (int i = 0; i < tintZones.size(); i++) {
      TintZone z = tintZones.get(i);
      String zonePrefix = prefix + i + ".";
      persisted.setProperty(zonePrefix + "name", z.name == null ? "" : z.name);
      persisted.setProperty(zonePrefix + "boundsX", formatDouble(z.boundsX));
      persisted.setProperty(zonePrefix + "boundsY", formatDouble(z.boundsY));
      persisted.setProperty(zonePrefix + "boundsW", formatDouble(z.boundsW));
      persisted.setProperty(zonePrefix + "boundsH", formatDouble(z.boundsH));
      persisted.setProperty(zonePrefix + "color", colorToHex(z.color));
      persisted.setProperty(zonePrefix + "strength", formatDouble(z.strength));
      persisted.setProperty(zonePrefix + "saturation", formatDouble(z.saturation));
      persisted.setProperty(zonePrefix + "contrast", formatDouble(z.contrast));
      persisted.setProperty(zonePrefix + "feather", formatDouble(z.feather));
      persisted.setProperty(zonePrefix + "rotation", formatDouble(z.rotation));
      persisted.setProperty(zonePrefix + "blendMode", canonicalBlendMode(z.blendMode, "Normal"));
      persisted.setProperty(zonePrefix + "overlayVisible", Boolean.toString(z.overlayVisible));
      if (z.isPolygon()) {
        StringBuilder polyStr = new StringBuilder();
        for (int p = 0; p < z.polygon.size(); p++) {
          if (p > 0) polyStr.append(";");
          polyStr.append(formatDouble(z.polygon.get(p)[0])).append(",").append(formatDouble(z.polygon.get(p)[1]));
        }
        persisted.setProperty(zonePrefix + "polygon", polyStr.toString());
      }
    }
  }

  private int loadZonesFromPrefix(String prefix) {
    tintZones.clear();
    selectedZoneIndex = -1;
    int count = (int) parseDouble(persisted.getProperty(prefix + "count"), 0);
    int skipped = 0;
    for (int i = 0; i < count; i++) {
      try {
        TintZone z = loadZoneFromPrefix(persisted, prefix + i + ".", i);
        tintZones.add(z);
      } catch (Exception e) {
        skipped++;
      }
    }
    refreshZoneList();
    if (!tintZones.isEmpty()) selectZone(0);
    else zoneControlsSection.setDisable(true);
    return skipped;
  }

  private void switchZoneProfileForCharacter(String characterTag, boolean persistCurrentFirst) {
    String nextTag = normalize(characterTag);
    if (Objects.equals(nextTag, activeZoneProfileTag)) return;
    if (persistCurrentFirst && !activeZoneProfileTag.isBlank()) {
      writeZonesToPrefix(zoneProfilePrefix(activeZoneProfileTag));
      savePersistentState();
    }
    activeZoneProfileTag = nextTag;
    loadPersistedZones();
  }

  private void persistZones() {
    String selectedTag = normalize(selectedCharacterTag());
    if (!selectedTag.isBlank()) {
      activeZoneProfileTag = selectedTag;
    }
    writeZonesToPrefix(zoneProfilePrefix(activeZoneProfileTag));
    savePersistentState();
  }

  private void loadPersistedZones() {
    if (activeZoneProfileTag.isBlank()) {
      activeZoneProfileTag = normalize(selectedCharacterTag());
    }

    String profilePrefix = zoneProfilePrefix(activeZoneProfileTag);
    boolean hasProfile = persisted.containsKey(profilePrefix + "count");
    boolean hasLegacy = persisted.containsKey(LEGACY_ZONE_PREFIX + "count");
    int skipped;
    if (hasProfile) {
      skipped = loadZonesFromPrefix(profilePrefix);
    } else if (hasLegacy) {
      skipped = loadZonesFromPrefix(LEGACY_ZONE_PREFIX);
      writeZonesToPrefix(profilePrefix);
      savePersistentState();
    } else {
      tintZones.clear();
      selectedZoneIndex = -1;
      refreshZoneList();
      zoneControlsSection.setDisable(true);
      return;
    }
    if (skipped > 0) {
      status("Loaded " + tintZones.size() + " zones (" + skipped + " malformed entries skipped).");
    }
  }

  /** Shared helper: parse a single TintZone from a Properties source with the given prefix. */
  private static TintZone loadZoneFromPrefix(Properties props, String prefix, int index) {
    double bx = parseDouble(props.getProperty(prefix + "boundsX"), 0.25);
    double by = parseDouble(props.getProperty(prefix + "boundsY"), 0.25);
    double bw = parseDouble(props.getProperty(prefix + "boundsW"), 0.5);
    double bh = parseDouble(props.getProperty(prefix + "boundsH"), 0.5);
    String name = props.getProperty(prefix + "name", "Zone " + (index + 1));
    TintZone z = new TintZone(name, bx, by, bw, bh);
    z.color = parseColor(props.getProperty(prefix + "color"), Color.web("#ff8844"));
    z.strength = parseDouble(props.getProperty(prefix + "strength"), 50);
    z.saturation = parseDouble(props.getProperty(prefix + "saturation"), 0);
    z.contrast = parseDouble(props.getProperty(prefix + "contrast"), 0);
    z.feather = parseDouble(props.getProperty(prefix + "feather"), 15);
    z.rotation = parseDouble(props.getProperty(prefix + "rotation"), 0);
    String bm = props.getProperty(prefix + "blendMode", "Normal");
    z.blendMode = canonicalBlendMode(bm, "Normal");
    z.overlayVisible = parseBoolean(props.getProperty(prefix + "overlayVisible"), true);
    String polyRaw = props.getProperty(prefix + "polygon", "");
    if (polyRaw != null && !polyRaw.isBlank()) {
      List<double[]> pts = new ArrayList<>();
      for (String pair : polyRaw.split(";")) {
        String[] xy = pair.split(",");
        if (xy.length == 2) {
          try {
            pts.add(new double[]{Double.parseDouble(xy[0].trim()), Double.parseDouble(xy[1].trim())});
          } catch (NumberFormatException ignored) {}
        }
      }
      if (pts.size() >= 3) z.polygon = pts;
    }
    return z;
  }

  // ── B: Export tinted PNG ──

  private void exportTintedPng() {
    Image source = loadImage(selectedCharacterTag());
    if (source == null) { status("No character image loaded."); return; }
    Image tinted = buildTintedImage(selectedCharacterTag(), source);
    if (tinted == null) { status("No tinted image available."); return; }
    FileChooser fc = new FileChooser();
    fc.setTitle("Export Tinted PNG");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
    fc.setInitialFileName("tinted_" + sanitizeFilename(selectedCharacterTag()) + ".png");
    if (projectRoot != null && projectRoot.isDirectory()) fc.setInitialDirectory(projectRoot);
    File file = fc.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
    if (file == null) return;
    try {
      BufferedImage bImg = SwingFXUtils.fromFXImage(tinted, null);
      ImageIO.write(bImg, "png", file);
      status("Exported PNG: " + file.getName());
    } catch (Exception e) {
      status("PNG export failed: " + e.getMessage());
    }
  }

  // ── C: File-based setup export/import (.tintsetup) ──

  private void exportSetupToFile() {
    String content = buildFullSetupText();
    FileChooser fc = new FileChooser();
    fc.setTitle("Export Tint Setup");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tint Setup", "*.tintsetup"));
    String tag = selectedCharacterTag();
    fc.setInitialFileName(sanitizeFilename(tag.isBlank() ? "setup" : tag) + ".tintsetup");
    if (projectRoot != null && projectRoot.isDirectory()) fc.setInitialDirectory(projectRoot);
    File file = fc.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
    if (file == null) return;
    try {
      Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
      status("Exported setup: " + file.getName());
    } catch (Exception e) {
      status("Setup export failed: " + e.getMessage());
    }
  }

  private void importSetupFromFile() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Import Tint Setup");
    fc.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("Tint Setup", "*.tintsetup"),
        new FileChooser.ExtensionFilter("All Files", "*.*"));
    if (projectRoot != null && projectRoot.isDirectory()) fc.setInitialDirectory(projectRoot);
    File file = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
    if (file == null) return;
    try {
      Properties props = new Properties();
      try (InputStream in = Files.newInputStream(file.toPath())) {
        props.load(in);
      }
      applyingState = true;
      try {
        // D: Import uses normalized values (0.0–1.0) — denormalize to slider ranges.
        String characterTag = props.getProperty("character", "");
        String backgroundTag = props.getProperty("background", "");
        if (!characterTag.isBlank()) {
          characterTagBox.getSelectionModel().select(characterTag);
          characterTagBox.getEditor().setText(characterTag);
        }
        if (!backgroundTag.isBlank()) {
          backgroundTagBox.getSelectionModel().select(backgroundTag);
          backgroundTagBox.getEditor().setText(backgroundTag);
        }

        tintColorPicker.setValue(parseColor(props.getProperty("color"), tintColorPicker.getValue()));
        // Values in export are normalized 0.0–1.0; convert to slider range.
        double str = parseDouble(props.getProperty("strength"), -1);
        if (str >= 0 && str <= 1.0) tintStrengthSlider.setValue(str * 100.0);
        double sat = parseDouble(props.getProperty("saturation"), -999);
        if (sat >= -1.0 && sat <= 1.0 && sat != -999) saturationSlider.setValue(sat * 100.0);
        double con = parseDouble(props.getProperty("contrast"), -999);
        if (con >= -1.0 && con <= 1.0 && con != -999) contrastSlider.setValue(con * 100.0);
        bgTintColorPicker.setValue(parseColor(
            props.getProperty("bgTintColor", props.getProperty("bg.color")),
            bgTintColorPicker.getValue()));
        double bgStr = parseDouble(props.getProperty("bgTintStrength", props.getProperty("bg.strength")), -1);
        if (bgStr >= 0.0 && bgStr <= 1.0) bgTintStrengthSlider.setValue(bgStr * 100.0);
        else if (bgStr > 1.0 && bgStr <= 100.0) bgTintStrengthSlider.setValue(bgStr);
        double bgSat = parseDouble(props.getProperty("bgSaturation", props.getProperty("bg.saturation")), Double.NaN);
        if (!Double.isNaN(bgSat)) {
          if (bgSat >= -1.0 && bgSat <= 1.0) bgSaturationSlider.setValue(bgSat * 100.0);
          else if (bgSat >= -100.0 && bgSat <= 100.0) bgSaturationSlider.setValue(bgSat);
        }
        double bgCon = parseDouble(props.getProperty("bgContrast", props.getProperty("bg.contrast")), Double.NaN);
        if (!Double.isNaN(bgCon)) {
          if (bgCon >= -1.0 && bgCon <= 1.0) bgContrastSlider.setValue(bgCon * 100.0);
          else if (bgCon >= -100.0 && bgCon <= 100.0) bgContrastSlider.setValue(bgCon);
        }
        bgTintBlendModeBox.getSelectionModel().select(canonicalBlendMode(
            props.getProperty("bgTintBlend", props.getProperty("bg.tintBlend")),
            bgTintBlendModeBox.getValue()));
        bgOverlayColorPicker.setValue(parseColor(
            props.getProperty("bgOverlayColor", props.getProperty("bg.overlayColor")),
            bgOverlayColorPicker.getValue()));
        double bgOverlay = parseDouble(props.getProperty("bgOverlayOpacity", props.getProperty("bg.overlayOpacity")), -1);
        if (bgOverlay >= 0.0 && bgOverlay <= 1.0) bgOverlayOpacitySlider.setValue(bgOverlay * 100.0);
        else if (bgOverlay > 1.0 && bgOverlay <= 100.0) bgOverlayOpacitySlider.setValue(bgOverlay);
        bgOverlayBlendModeBox.getSelectionModel().select(canonicalBlendMode(
            props.getProperty("bgOverlayBlend", props.getProperty("bg.overlayBlend")),
            bgOverlayBlendModeBox.getValue()));
        double z = parseDouble(props.getProperty("zoom"), -1);
        if (z > 0) zoom = clamp(z, 0.1, 8.0);
        double ox = parseDouble(props.getProperty("offsetX"), Double.NaN);
        if (!Double.isNaN(ox)) offsetX = ox;
        double oy = parseDouble(props.getProperty("offsetY"), Double.NaN);
        if (!Double.isNaN(oy)) offsetY = oy;

        // Import zones.
        int zoneCount = (int) parseDouble(props.getProperty("zones"), 0);
        if (zoneCount > 0) {
          tintZones.clear();
          selectedZoneIndex = -1;
          int loaded = 0, skipped = 0;
          for (int i = 0; i < zoneCount; i++) {
            try {
              TintZone tz = loadZoneFromNormalized(props, "zone." + i + ".", i);
              tintZones.add(tz);
              loaded++;
            } catch (Exception ignored) { skipped++; }
          }
          refreshZoneList();
          if (!tintZones.isEmpty()) selectZone(0);
          else zoneControlsSection.setDisable(true);
          if (skipped > 0) {
            status("Imported " + loaded + "/" + zoneCount + " zones (" + skipped + " skipped) from " + file.getName());
            return;
          }
        }
      } finally {
        applyingState = false;
      }
      updateBackgroundSwatches();
      invalidateBackgroundTintCache();
      persistBackgroundTint(selectedBackgroundTag());
      persistGlobalState();
      activeZoneProfileTag = normalize(selectedCharacterTag());
      persistZones();
      invalidateTintCache();
      invalidateBackgroundTintCache();
      redrawPreview();
      status("Imported setup from " + file.getName() + " (" + tintZones.size() + " zones)");
    } catch (Exception e) {
      status("Import failed: " + e.getMessage());
    }
  }

  /** Parse zone from normalized export format (strength/sat/contrast/feather as 0.0–1.0). */
  private static TintZone loadZoneFromNormalized(Properties props, String prefix, int index) {
    String boundsRaw = props.getProperty(prefix + "bounds", "");
    double bx = 0.25, by = 0.25, bw = 0.5, bh = 0.5;
    if (!boundsRaw.isBlank()) {
      String[] parts = boundsRaw.split(",");
      if (parts.length == 4) {
        bx = Double.parseDouble(parts[0].trim());
        by = Double.parseDouble(parts[1].trim());
        bw = Double.parseDouble(parts[2].trim());
        bh = Double.parseDouble(parts[3].trim());
      }
    }
    String name = props.getProperty(prefix + "name", "Zone " + (index + 1));
    TintZone z = new TintZone(name, bx, by, bw, bh);
    z.color = parseColor(props.getProperty(prefix + "color"), Color.web("#ff8844"));
    // D: Denormalize from 0.0–1.0 to slider ranges.
    z.strength = parseDouble(props.getProperty(prefix + "strength"), 0.5) * 100.0;
    z.saturation = parseDouble(props.getProperty(prefix + "saturation"), 0) * 100.0;
    z.contrast = parseDouble(props.getProperty(prefix + "contrast"), 0) * 100.0;
    z.feather = parseDouble(props.getProperty(prefix + "feather"), 0.15) * 100.0;
    z.rotation = parseDouble(props.getProperty(prefix + "rotation"), 0);
    String bm = props.getProperty(prefix + "blend", "Normal");
    z.blendMode = canonicalBlendMode(bm, "Normal");
    z.overlayVisible = parseBoolean(
        props.getProperty(prefix + "overlayVisible", props.getProperty(prefix + "visible")),
        true
    );
    String polyRaw = props.getProperty(prefix + "polygon", "");
    if (polyRaw != null && !polyRaw.isBlank()) {
      List<double[]> pts = new ArrayList<>();
      for (String pair : polyRaw.split(";")) {
        String[] xy = pair.split(",");
        if (xy.length == 2) {
          try {
            pts.add(new double[]{Double.parseDouble(xy[0].trim()), Double.parseDouble(xy[1].trim())});
          } catch (NumberFormatException ignored) {}
        }
      }
      if (pts.size() >= 3) z.polygon = pts;
    }
    return z;
  }

  /** Build the full setup text (same format for clipboard and file export). D: Uses normalized values. */
  private String buildFullSetupText() {
    StringBuilder out = new StringBuilder();
    out.append("# JVN Image Tint Tool setup\n");
    out.append("character=").append(selectedCharacterTag()).append('\n');
    out.append("background=").append(selectedBackgroundTag()).append('\n');
    out.append("color=").append(colorToHex(tintColorPicker.getValue())).append('\n');
    out.append("strength=").append(formatNormalized(tintStrengthSlider.getValue() / 100.0)).append('\n');
    out.append("saturation=").append(formatNormalized(saturationSlider.getValue() / 100.0)).append('\n');
    out.append("contrast=").append(formatNormalized(contrastSlider.getValue() / 100.0)).append('\n');
    out.append("bgTintColor=").append(colorToHex(bgTintColorPicker.getValue())).append('\n');
    out.append("bgTintStrength=").append(formatNormalized(bgTintStrengthSlider.getValue() / 100.0)).append('\n');
    out.append("bgSaturation=").append(formatNormalized(bgSaturationSlider.getValue() / 100.0)).append('\n');
    out.append("bgContrast=").append(formatNormalized(bgContrastSlider.getValue() / 100.0)).append('\n');
    out.append("bgTintBlend=").append(canonicalBlendMode(bgTintBlendModeBox.getValue(), "Normal")).append('\n');
    out.append("bgOverlayColor=").append(colorToHex(bgOverlayColorPicker.getValue())).append('\n');
    out.append("bgOverlayOpacity=").append(formatNormalized(bgOverlayOpacitySlider.getValue() / 100.0)).append('\n');
    out.append("bgOverlayBlend=").append(canonicalBlendMode(bgOverlayBlendModeBox.getValue(), "Overlay")).append('\n');
    out.append("zoom=").append(formatNormalized(zoom)).append('\n');
    out.append("offsetX=").append(formatNormalized(offsetX)).append('\n');
    out.append("offsetY=").append(formatNormalized(offsetY)).append('\n');
    if (!tintZones.isEmpty()) {
      out.append("zones=").append(tintZones.size()).append('\n');
      for (int i = 0; i < tintZones.size(); i++) {
        TintZone tz = tintZones.get(i);
        String p = "zone." + i + ".";
        out.append(p).append("name=").append(tz.name == null ? "" : tz.name).append('\n');
        out.append(p).append("bounds=").append(formatNormalized(tz.boundsX)).append(",")
            .append(formatNormalized(tz.boundsY)).append(",")
            .append(formatNormalized(tz.boundsW)).append(",")
            .append(formatNormalized(tz.boundsH)).append('\n');
        out.append(p).append("color=").append(colorToHex(tz.color)).append('\n');
        out.append(p).append("strength=").append(formatNormalized(tz.strength / 100.0)).append('\n');
        out.append(p).append("saturation=").append(formatNormalized(tz.saturation / 100.0)).append('\n');
        out.append(p).append("contrast=").append(formatNormalized(tz.contrast / 100.0)).append('\n');
        out.append(p).append("feather=").append(formatNormalized(tz.feather / 100.0)).append('\n');
        out.append(p).append("rotation=").append(formatNormalized(tz.rotation)).append('\n');
        out.append(p).append("blend=").append(canonicalBlendMode(tz.blendMode, "Normal")).append('\n');
        out.append(p).append("visible=").append(tz.overlayVisible).append('\n');
        if (tz.isPolygon()) {
          StringBuilder polyStr = new StringBuilder();
          for (int pi = 0; pi < tz.polygon.size(); pi++) {
            if (pi > 0) polyStr.append(";");
            polyStr.append(formatNormalized(tz.polygon.get(pi)[0])).append(",")
                .append(formatNormalized(tz.polygon.get(pi)[1]));
          }
          out.append(p).append("polygon=").append(polyStr).append('\n');
        }
      }
    }
    return out.toString();
  }

  private static String sanitizeFilename(String input) {
    if (input == null || input.isBlank()) return "untitled";
    String s = input.replace('/', '_').replace('\\', '_').replace(':', '_')
        .replace('*', '_').replace('?', '_').replace('"', '_')
        .replace('<', '_').replace('>', '_').replace('|', '_');
    // Use only the last segment if it looks like a path.
    int lastSlash = s.lastIndexOf('_');
    if (lastSlash >= 0 && lastSlash < s.length() - 1) s = s.substring(lastSlash + 1);
    return s.isBlank() ? "untitled" : s;
  }
}
