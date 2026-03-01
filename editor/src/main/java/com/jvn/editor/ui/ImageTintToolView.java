package com.jvn.editor.ui;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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
import javafx.scene.SnapshotParameters;
import javafx.scene.text.TextAlignment;

/**
 * Standalone image tint utility inspired by Ren'Py's Image Tint Tool.
 * This tool is intentionally independent from layered/image-attributes tools.
 */
public class ImageTintToolView extends BorderPane implements ImageToolPanel {
  private static final String STATE_FILE = ".jvn/image-tint-tool.properties";
  private static final String TOOL_TITLE = "Image Tint Tool";
  private static final String PRESET_TAG_PREFIX = "preset:";
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^\\s*@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);

  private static final String DEFAULT_EXPORT_PROFILE = "Tint Profile";
  private static final String DEFAULT_EXPORT_SETUP = "Full Setup";

  private final Label summaryLabel = new Label("Open a project to scan image tags.");
  private final Label statusLabel = new Label("");
  private final Label previewInfoLabel = new Label("No character image selected.");
  private final Label interactionHintLabel = new Label("Drag preview to pan, scroll to zoom, double-click to reset.");

  private final TextField filterField = new TextField();
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

  private final Canvas previewCanvas = new Canvas(320, 240);
  private final VBox controlsSection = new VBox(8);
  private TitledPane controlsPane;

  private final Map<String, File> imageByTag = new LinkedHashMap<>();
  private final Map<String, PresetTagEntry> presetByTag = new LinkedHashMap<>();
  private final Map<String, String> setupNameToKey = new LinkedHashMap<>();
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Properties persisted = new Properties();

  private File projectRoot;
  private Runnable fullscreenToggleHandler;
  private boolean fullscreenActive;
  private Button fullscreenButton;
  private boolean applyingState;

  private boolean dragging;
  private double dragLastX;
  private double dragLastY;
  private double zoom = 1.0;
  private double offsetX;
  private double offsetY;

  private String tintedImageTag;
  private String tintedImageKey;
  private Image tintedImage;

  public ImageTintToolView() {
    setPadding(new Insets(8));
    buildUi();
    refreshCatalog();
  }

  private void buildUi() {
    Label title = new Label(TOOL_TITLE);
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");
    interactionHintLabel.setStyle("-fx-text-fill: #aeb6c7; -fx-font-size: 11px;");
    interactionHintLabel.setWrapText(true);

    filterField.setPromptText("Filter tags...");
    filterField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshTagLists();
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

    Button refreshButton = iconButton(CssIcon.redo("#7ec8e3"), "Rescan project images", this::refreshCatalog);
    HBox filterRow = new HBox(8, new Label("Filter"), filterField, refreshButton);
    filterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);

    HBox charRow = new HBox(8, new Label("Char"), characterTagBox);
    charRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(characterTagBox, Priority.ALWAYS);

    HBox bgRow = new HBox(8, new Label("Bg"), backgroundTagBox);
    bgRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(backgroundTagBox, Priority.ALWAYS);

    Button loadSetupButton = iconButton(CssIcon.download("#8ab4f8"), "Load selected setup", this::loadSelectedSetup);
    Button deleteSetupButton = iconButton(CssIcon.clearX("#f38ba8"), "Delete selected setup", this::deleteSelectedSetup);
    HBox setupLoadRow = new HBox(8, new Label("Setup"), setupBox, loadSetupButton, deleteSetupButton);
    setupLoadRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setupBox, Priority.ALWAYS);

    Button saveSetupButton = iconButton(CssIcon.save("#9ed67a"), "Save current setup", this::saveCurrentSetup);
    HBox setupSaveRow = new HBox(8, new Label("Name"), setupNameField, saveSetupButton);
    setupSaveRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setupNameField, Priority.ALWAYS);

    VBox top = new VBox(8, title, summaryLabel, filterRow, charRow, bgRow, setupLoadRow, setupSaveRow);
    setTop(top);

    StackPane previewPane = new StackPane(previewCanvas);
    previewPane.setMinHeight(180);
    previewPane.setPrefHeight(250);
    previewPane.setStyle("-fx-background-color: #121720; -fx-border-color: #2b3445; -fx-border-radius: 6; -fx-background-radius: 6;");
    previewPane.widthProperty().addListener((o, ov, nv) -> {
      previewCanvas.setWidth(Math.max(140, nv.doubleValue() - 4));
      redrawPreview();
    });
    previewPane.heightProperty().addListener((o, ov, nv) -> {
      previewCanvas.setHeight(Math.max(140, nv.doubleValue() - 4));
      redrawPreview();
    });

    installPreviewInteractions();

    Button resetViewButton = iconButton(CssIcon.expand("#8ab4f8"), "Reset position and zoom", this::resetView);
    Button resetTintButton = iconButton(CssIcon.palette("#f5b971"), "Reset tint controls", this::resetTintControls);
    fullscreenButton = iconButton(CssIcon.expand("#f5c46b"), "Fullscreen this panel in the current editor window", this::requestFullscreenToggle);
    updateFullscreenButtonUi();
    Button copyExportButton = iconButton(CssIcon.copy("#9ad19c"), "Copy selected export format", this::copySelectedExport);
    HBox actionRow = new HBox(8, resetViewButton, resetTintButton, fullscreenButton, new Label("Export"), exportFormatBox, copyExportButton);
    actionRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(exportFormatBox, Priority.ALWAYS);

    tintColorPicker.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    tintStrengthSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    saturationSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));
    contrastSlider.valueProperty().addListener((o, ov, nv) -> onTintChanged(true));

    updateTintColorSwatch(tintColorPicker.getValue());
    controlsSection.getChildren().setAll(
        tintPickerRow("Tint color"),
        sliderRow("Tint strength", tintStrengthSlider),
        sliderRow("Saturation", saturationSlider),
        sliderRow("Contrast", contrastSlider));
    controlsPane = new TitledPane("Tint controls", controlsSection);
    controlsPane.setExpanded(true);
    controlsPane.setAnimated(false);
    controlsPane.setCollapsible(true);
    controlsPane.expandedProperty().addListener((o, ov, expanded) -> {
      if (!applyingState) persistGlobalState();
    });

    VBox center = new VBox(8, previewPane, previewInfoLabel, interactionHintLabel, actionRow, controlsPane, statusLabel);
    center.setPadding(new Insets(8, 0, 0, 0));
    setCenter(center);

    bindTagSelectionHandlers();
  }

  private void bindTagSelectionHandlers() {
    characterTagBox.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      redrawPreview();
      persistGlobalState();
    });
    characterTagBox.getEditor().textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      redrawPreview();
      persistGlobalState();
    });

    backgroundTagBox.valueProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      applyBackgroundTintIfPresent(selectedBackgroundTag());
      redrawPreview();
      persistGlobalState();
    });
    backgroundTagBox.getEditor().textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      redrawPreview();
      persistGlobalState();
    });
  }

  private void onTintChanged(boolean redraw) {
    updateTintColorSwatch(tintColorPicker.getValue());
    tintedImageTag = null;
    tintedImageKey = null;
    tintedImage = null;
    if (redraw) redrawPreview();
    if (!applyingState) {
      persistBackgroundTint(selectedBackgroundTag());
      persistGlobalState();
    }
  }

  @Override
  public void setProjectRoot(File projectRoot) {
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

  @Override
  public void refreshCatalog() {
    persistGlobalState();
    loadPersistentState();
    imageByTag.clear();
    presetByTag.clear();
    imageCache.clear();
    tintedImageTag = null;
    tintedImageKey = null;
    tintedImage = null;

    if (projectRoot == null || !projectRoot.isDirectory()) {
      summaryLabel.setText("Open a project to scan image tags.");
      characterTagBox.getItems().clear();
      backgroundTagBox.getItems().clear();
      refreshSetupOptions();
      redrawPreview();
      return;
    }

    Path root = projectRoot.toPath();
    try (Stream<Path> stream = Files.walk(root, 10)) {
      stream
          .filter(Files::isRegularFile)
          .filter(this::isImageFile)
          .map(path -> root.relativize(path).toString().replace('\\', '/'))
          .filter(relative -> !isIgnoredPath(relative))
          .sorted(Comparator.naturalOrder())
          .forEach(relative -> imageByTag.put(relative, root.resolve(relative).toFile()));
      scanScriptCharpresets(root);
    } catch (Exception ex) {
      summaryLabel.setText("Image scan failed: " + ex.getMessage());
      redrawPreview();
      return;
    }

    summaryLabel.setText("Images: " + imageByTag.size() + " | Charpresets: " + presetByTag.size());
    refreshTagLists();
    applyPersistedSelections();
    ensureDefaultSelections();
    refreshSetupOptions();
    redrawPreview();
  }

  private void refreshTagLists() {
    String keepChar = selectedCharacterTag();
    String keepBg = selectedBackgroundTag();
    String filter = normalize(filterField.getText()).toLowerCase(Locale.ROOT);
    List<String> characterTags = buildCharacterTagList();
    List<String> bgs = new ArrayList<>(imageByTag.keySet());
    if (!filter.isBlank()) {
      characterTags.removeIf(tag -> !tag.toLowerCase(Locale.ROOT).contains(filter));
      bgs.removeIf(tag -> !tag.toLowerCase(Locale.ROOT).contains(filter));
    }
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
      if (controlsPane != null) {
        boolean hide = parseBoolean(persisted.getProperty("global.hideControls"), false);
        controlsPane.setExpanded(!hide);
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
      zoom = clamp(parseDouble(persisted.getProperty("global.zoom"), 1.0), 0.1, 8.0);
      offsetX = parseDouble(persisted.getProperty("global.offsetX"), 0.0);
      offsetY = parseDouble(persisted.getProperty("global.offsetY"), 0.0);
    } finally {
      applyingState = false;
    }
    applyBackgroundTintIfPresent(selectedBackgroundTag());
  }

  private void ensureDefaultSelections() {
    boolean changed = false;
    applyingState = true;
    try {
      String currentCharacter = selectedCharacterTag();
      if (currentCharacter.isBlank() || !isKnownCharacterTag(currentCharacter)) {
        String fallbackCharacter = pickDefaultCharacterTag(buildCharacterTagList());
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
    persisted.setProperty(prefix + "zoom", formatDouble(zoom));
    persisted.setProperty(prefix + "offsetX", formatDouble(offsetX));
    persisted.setProperty(prefix + "offsetY", formatDouble(offsetY));
    persisted.setProperty("global.selectedSetup", name);
    savePersistentState();
    refreshSetupOptions();
    setupBox.getSelectionModel().select(name);
    status("Saved setup: " + name);
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
      zoom = clamp(parseDouble(persisted.getProperty(prefix + "zoom"), zoom), 0.1, 8.0);
      offsetX = parseDouble(persisted.getProperty(prefix + "offsetX"), offsetX);
      offsetY = parseDouble(persisted.getProperty(prefix + "offsetY"), offsetY);
    } finally {
      applyingState = false;
    }
    persisted.setProperty("global.selectedSetup", name);
    persistGlobalState();
    redrawPreview();
    status("Loaded setup: " + name);
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
    StringBuilder out = new StringBuilder();
    out.append("# JVN Image Tint Tool setup\n");
    out.append("character=").append(selectedCharacterTag()).append('\n');
    out.append("background=").append(selectedBackgroundTag()).append('\n');
    out.append("color=").append(colorToHex(tintColorPicker.getValue())).append('\n');
    out.append("strength=").append(formatNormalized(tintStrengthSlider.getValue() / 100.0)).append('\n');
    out.append("saturation=").append(formatNormalized(saturationSlider.getValue() / 100.0)).append('\n');
    out.append("contrast=").append(formatNormalized(contrastSlider.getValue() / 100.0)).append('\n');
    out.append("zoom=").append(formatNormalized(zoom)).append('\n');
    out.append("offsetX=").append(formatNormalized(offsetX)).append('\n');
    out.append("offsetY=").append(formatNormalized(offsetY)).append('\n');
    copy(out.toString());
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
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (e.getClickCount() < 2) return;
      resetView();
      e.consume();
    });

    previewCanvas.setOnMousePressed(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      dragging = true;
      dragLastX = e.getX();
      dragLastY = e.getY();
    });
    previewCanvas.setOnMouseDragged(e -> {
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
      drawCover(g, bg, w, h);
    } else {
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

    previewInfoLabel.setText("Char: " + shortTag(characterTag)
        + "  |  Bg: " + shortTag(selectedBackgroundTag())
        + "  |  Zoom: " + formatNormalized(zoom));
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

    for (int y = 0; y < height; y++) {
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

        // Saturation adjustment around luminance.
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        r = lum + (r - lum) * (1.0 + satAdjust);
        g = lum + (g - lum) * (1.0 + satAdjust);
        b = lum + (b - lum) * (1.0 + satAdjust);

        // Contrast adjustment around 0.5 midpoint.
        r = (r - 0.5) * (1.0 + conAdjust) + 0.5;
        g = (g - 0.5) * (1.0 + conAdjust) + 0.5;
        b = (b - 0.5) * (1.0 + conAdjust) + 0.5;

        // Blend toward tint color.
        r = r * (1.0 - tintStrength) + tr * tintStrength;
        g = g * (1.0 - tintStrength) + tg * tintStrength;
        b = b * (1.0 - tintStrength) + tb * tintStrength;

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

  private String tintKey(String tag, Image source) {
    return normalize(tag)
        + "|" + source.getWidth() + "x" + source.getHeight()
        + "|" + colorToHex(tintColorPicker.getValue())
        + "|" + formatDouble(tintStrengthSlider.getValue())
        + "|" + formatDouble(saturationSlider.getValue())
        + "|" + formatDouble(contrastSlider.getValue());
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
    persisted.setProperty(prefix + "color", colorToHex(tintColorPicker.getValue()));
    persisted.setProperty(prefix + "strength", formatDouble(tintStrengthSlider.getValue()));
    persisted.setProperty(prefix + "saturation", formatDouble(saturationSlider.getValue()));
    persisted.setProperty(prefix + "contrast", formatDouble(contrastSlider.getValue()));
    savePersistentState();
  }

  private void applyBackgroundTintIfPresent(String backgroundTag) {
    String tag = normalize(backgroundTag);
    if (tag.isBlank()) return;
    String prefix = "bg." + encodeKey(tag) + ".";
    if (!persisted.containsKey(prefix + "color")) return;
    applyingState = true;
    try {
      tintColorPicker.setValue(parseColor(persisted.getProperty(prefix + "color"), tintColorPicker.getValue()));
      tintStrengthSlider.setValue(parseDouble(persisted.getProperty(prefix + "strength"), tintStrengthSlider.getValue()));
      saturationSlider.setValue(parseDouble(persisted.getProperty(prefix + "saturation"), saturationSlider.getValue()));
      contrastSlider.setValue(parseDouble(persisted.getProperty(prefix + "contrast"), contrastSlider.getValue()));
    } finally {
      applyingState = false;
    }
    onTintChanged(true);
  }

  private void persistGlobalState() {
    persisted.setProperty("global.filter", normalize(filterField.getText()));
    persisted.setProperty("global.characterTag", selectedCharacterTag());
    persisted.setProperty("global.backgroundTag", selectedBackgroundTag());
    persisted.setProperty("global.exportFormat", normalize(exportFormatBox.getValue()));
    persisted.setProperty("global.tintColor", colorToHex(tintColorPicker.getValue()));
    persisted.setProperty("global.tintStrength", formatDouble(tintStrengthSlider.getValue()));
    persisted.setProperty("global.saturation", formatDouble(saturationSlider.getValue()));
    persisted.setProperty("global.contrast", formatDouble(contrastSlider.getValue()));
    persisted.setProperty("global.zoom", formatDouble(zoom));
    persisted.setProperty("global.offsetX", formatDouble(offsetX));
    persisted.setProperty("global.offsetY", formatDouble(offsetY));
    boolean hideControls = controlsPane != null && !controlsPane.isExpanded();
    persisted.setProperty("global.hideControls", Boolean.toString(hideControls));
    savePersistentState();
  }

  private void loadPersistentState() {
    persisted.clear();
    Path file = statePath();
    if (file == null || !Files.exists(file)) return;
    try (InputStream in = Files.newInputStream(file)) {
      persisted.load(in);
    } catch (Exception ignored) {
    }
  }

  private void savePersistentState() {
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

  private List<String> buildCharacterTagList() {
    List<String> tags = new ArrayList<>(imageByTag.keySet());
    tags.addAll(presetByTag.keySet());
    tags.sort(String.CASE_INSENSITIVE_ORDER);
    return tags;
  }

  private boolean isKnownCharacterTag(String tag) {
    String n = normalize(tag);
    return imageByTag.containsKey(n) || presetByTag.containsKey(n);
  }

  private void scanScriptCharpresets(Path root) {
    Map<String, Map<String, String>> layersByCharacter = new LinkedHashMap<>();
    List<PresetDecl> declarations = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root, 10)) {
      stream
          .filter(Files::isRegularFile)
          .filter(ImageTintToolView::isVnsFile)
          .map(path -> root.relativize(path).toString().replace('\\', '/'))
          .filter(relative -> !isIgnoredPath(relative))
          .sorted(Comparator.naturalOrder())
          .forEach(relative -> parsePresetDeclarations(root.resolve(relative), layersByCharacter, declarations));
    } catch (Exception ignored) {
      return;
    }

    for (PresetDecl declaration : declarations) {
      List<String> layers = resolvePresetLayerTags(layersByCharacter, declaration.characterId(), declaration.spec());
      if (layers.isEmpty()) continue;
      String tag = buildPresetTag(declaration.characterId(), declaration.expressionId());
      presetByTag.put(tag, new PresetTagEntry(tag, layers));
    }
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
    l.setMinWidth(90);
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

  private HBox tintPickerRow(String label) {
    Label l = new Label(label);
    l.setMinWidth(90);

    tintColorSwatch.setMinSize(26, 26);
    tintColorSwatch.setPrefSize(26, 26);
    tintColorSwatch.setMaxSize(26, 26);
    tintColorSwatch.setMouseTransparent(true);

    tintColorPicker.setMinSize(26, 26);
    tintColorPicker.setPrefSize(26, 26);
    tintColorPicker.setMaxSize(26, 26);
    tintColorPicker.setStyle(
        "-fx-color-label-visible: false;"
            + " -fx-background-radius: 999;"
            + " -fx-border-radius: 999;");
    tintColorPicker.setOpacity(0.001);

    StackPane pickerHost = new StackPane(tintColorSwatch, tintColorPicker);
    pickerHost.setMinSize(26, 26);
    pickerHost.setPrefSize(26, 26);
    pickerHost.setMaxSize(26, 26);

    HBox row = new HBox(8, l, pickerHost);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private void updateTintColorSwatch(Color color) {
    Color c = color == null ? Color.WHITE : color;
    int r = (int) Math.round(clamp(c.getRed(), 0.0, 1.0) * 255.0);
    int g = (int) Math.round(clamp(c.getGreen(), 0.0, 1.0) * 255.0);
    int b = (int) Math.round(clamp(c.getBlue(), 0.0, 1.0) * 255.0);
    String hex = String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b);
    tintColorSwatch.setStyle(
        "-fx-background-color: " + hex + ";"
            + " -fx-background-radius: 999;"
            + " -fx-border-color: rgba(255,255,255,0.40);"
            + " -fx-border-radius: 999;"
            + " -fx-border-width: 1;");
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
}
