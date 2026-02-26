package com.jvn.editor.ui;

import java.awt.Desktop;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiStyleSpec;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Sidebar utility for layered sprite exploration and snippet generation.
 *
 * Hardening goals:
 * - per-set persistence (selected layers, crop/focus/zoom, ids)
 * - save/load/delete presets
 * - active-group randomization and manual render-order controls
 * - multiple snippet export formats
 */
public class LayeredImageVisualizerView extends BorderPane {
  private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");
  private static final String NONE_LABEL = "(none)";
  private static final String STATE_FILE = ".jvn/layered-image-visualizer.properties";
  private static final double DEFAULT_CHARACTER_HEIGHT_FACTOR = 0.85;
  private static final double DEFAULT_CHARACTER_BASELINE_Y = 1.0;

  private static final String SNIPPET_COMBINED = "@charimg + [show]";
  private static final String SNIPPET_CHARIMG = "@charimg only";
  private static final String SNIPPET_SHOW = "[show] only";
  private static final String SNIPPET_RECIPE = "Recipe comments";

  private final Label summaryLabel = new Label("Open a project to inspect layered image sets.");
  private final Label statusLabel = new Label("");
  private final Label previewInfoLabel = new Label("No layers selected.");

  private final TextField filterField = new TextField();
  private final ComboBox<String> setBox = new ComboBox<>();
  private final TextField characterIdField = new TextField();
  private final TextField expressionField = new TextField();
  private final CheckBox autoExpression = new CheckBox("Auto expression from selected layers");

  private final ComboBox<String> presetBox = new ComboBox<>();
  private final TextField presetNameField = new TextField();

  private final ComboBox<String> snippetFormatBox = new ComboBox<>();
  private final CheckBox randomizeActiveOnly = new CheckBox("Randomize active groups only");
  private final CheckBox matchGameFraming = new CheckBox("Match game framing");

  private final Canvas previewCanvas = new Canvas(320, 250);
  private final Slider focusXSlider = slider(0, 100, 50);
  private final Slider focusYSlider = slider(0, 100, 50);
  private final Slider cropSlider = slider(20, 100, 100);
  private final Slider zoomSlider = slider(50, 300, 100);

  private final VBox groupBox = new VBox(8);

  private final Map<String, ComboBox<LayerOption>> selectors = new LinkedHashMap<>();
  private final Map<String, CheckBox> activeGroupChecks = new LinkedHashMap<>();
  private final Map<String, HBox> groupRows = new LinkedHashMap<>();
  private final List<String> groupOrder = new ArrayList<>();
  private final Map<String, LayeredSet> sets = new LinkedHashMap<>();
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Map<String, String> presetNameToKey = new LinkedHashMap<>();

  private final Properties persisted = new Properties();
  private final Random random = new Random();

  private File projectRoot;
  private String currentSetId;
  private String preferredSetId;
  private boolean applyingState;
  private Stage fullscreenStage;
  private Canvas fullscreenCanvas;
  private double gameCharacterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double gameCharacterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;

  public LayeredImageVisualizerView() {
    setPadding(new Insets(8));

    Label title = new Label("Layered Image Visualizer");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

    filterField.setPromptText("Filter sets...");
    filterField.textProperty().addListener((o, ov, nv) -> {
      if (applyingState) return;
      refreshSetOptions();
    });
    filterField.setOnAction(e -> persistGlobalState());
    filterField.focusedProperty().addListener((o, ov, nv) -> {
      if (!nv) persistGlobalState();
    });

    setBox.setPromptText("Select layered set");
    setBox.setConverter(new StringConverter<>() {
      @Override public String toString(String object) { return object == null ? "" : object; }
      @Override public String fromString(String string) { return string; }
    });
    setBox.setOnAction(e -> onSetSelectionChanged());

    Button refreshButton = iconButton(CssIcon.redo("#7ec8e3"), "Refresh set scan", this::refreshCatalog);

    HBox setRow = new HBox(8, new Label("Set"), setBox, refreshButton);
    setRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setBox, Priority.ALWAYS);

    HBox filterRow = new HBox(8, new Label("Filter"), filterField);
    filterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);

    // Presets toolbar
    presetBox.setPromptText("Preset");
    HBox.setHgrow(presetBox, Priority.ALWAYS);

    Button loadPresetButton = iconButton(CssIcon.download("#8ab4f8"), "Load selected preset", this::loadSelectedPreset);
    Button deletePresetButton = iconButton(CssIcon.clearX("#f38ba8"), "Delete selected preset", this::deleteSelectedPreset);
    HBox presetLoadRow = new HBox(6, new Label("Preset"), presetBox, loadPresetButton, deletePresetButton);
    presetLoadRow.setAlignment(Pos.CENTER_LEFT);

    presetNameField.setPromptText("Preset name");
    HBox.setHgrow(presetNameField, Priority.ALWAYS);
    Button savePresetButton = iconButton(CssIcon.save("#9ed67a"), "Save preset", this::savePreset);
    HBox presetSaveRow = new HBox(6, new Label("Name"), presetNameField, savePresetButton);
    presetSaveRow.setAlignment(Pos.CENTER_LEFT);

    VBox top = new VBox(8, title, summaryLabel, filterRow, setRow, presetLoadRow, presetSaveRow, new Separator());
    setTop(top);

    // Preview pane
    StackPane previewPane = new StackPane(previewCanvas);
    previewPane.setMinHeight(180);
    previewPane.setPrefHeight(260);
    previewPane.setStyle("-fx-background-color: #121720; -fx-border-color: #2b3445; -fx-border-radius: 6; -fx-background-radius: 6;");
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

    characterIdField.setPromptText("Character id");
    expressionField.setPromptText("Expression id");
    characterIdField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistCurrentSetState();
    });
    expressionField.textProperty().addListener((o, ov, nv) -> {
      if (!applyingState) persistCurrentSetState();
    });

    HBox idRow = new HBox(8, new Label("Char"), characterIdField, new Label("Expr"), expressionField);
    idRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(characterIdField, Priority.ALWAYS);
    HBox.setHgrow(expressionField, Priority.ALWAYS);

    // Snippet export
    snippetFormatBox.getItems().setAll(SNIPPET_COMBINED, SNIPPET_CHARIMG, SNIPPET_SHOW, SNIPPET_RECIPE);
    snippetFormatBox.getSelectionModel().select(SNIPPET_COMBINED);
    HBox.setHgrow(snippetFormatBox, Priority.ALWAYS);

    Button copySnippetButton = iconButton(CssIcon.copy("#9ad19c"), "Copy selected snippet format", this::copySnippet);
    Button copyRecipeButton = iconButton(CssIcon.copy("#d6b4ff"), "Copy detailed layer recipe comments", this::copyLayerRecipe);

    HBox snippetRow = new HBox(8, new Label("Export"), snippetFormatBox, copySnippetButton, copyRecipeButton);
    snippetRow.setAlignment(Pos.CENTER_LEFT);

    // Action row
    Button randomizeButton = iconButton(CssIcon.sort("#f0b673"), "Randomize layer choices", this::randomizeSelection);
    Button defaultsButton = iconButton(CssIcon.home("#9ed67a"), "Restore default first option per group", this::applyDefaultSelection);
    Button noneButton = iconButton(CssIcon.clearX("#f38ba8"), "Clear all selected layers", this::applyNoneSelection);
    Button resetViewButton = iconButton(CssIcon.expand("#8ab4f8"), "Reset preview focus and zoom", () -> {
      applyingState = true;
      focusXSlider.setValue(50);
      focusYSlider.setValue(50);
      cropSlider.setValue(100);
      zoomSlider.setValue(100);
      applyingState = false;
      redrawPreview();
      persistCurrentSetState();
    });
    Button fullscreenButton = iconButton(CssIcon.expand("#f5c46b"), "Open fullscreen preview", this::openFullscreenPreview);

    HBox toolRow = new HBox(8, randomizeButton, defaultsButton, noneButton, resetViewButton, fullscreenButton, randomizeActiveOnly);
    toolRow.setAlignment(Pos.CENTER_LEFT);
    HBox framingRow = new HBox(8, matchGameFraming);
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

    VBox previewSection = new VBox(
        8,
        previewPane,
        previewInfoLabel,
        viewControlsPane,
        idRow,
        autoExpression,
        toolRow,
        framingRow,
        snippetRow,
        statusLabel);
    previewSection.setPadding(new Insets(0, 0, 4, 0));

    Label groupsLabel = new Label("Layer Groups (up/down changes render order)");
    groupsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");

    Button activeAllButton = iconButton(CssIcon.check("#9ed67a"), "Mark all groups active for randomization", () -> setAllGroupsActive(true));
    Button activeNoneButton = iconButton(CssIcon.minus("#f0b673"), "Mark all groups inactive for randomization", () -> setAllGroupsActive(false));
    HBox groupTools = new HBox(6, activeAllButton, activeNoneButton);
    groupTools.setAlignment(Pos.CENTER_LEFT);

    VBox groupsRoot = new VBox(8, groupsLabel, groupTools, groupBox);
    groupsRoot.setPadding(new Insets(2));
    ScrollPane groupsScroll = new ScrollPane(groupsRoot);
    groupsScroll.setFitToWidth(true);

    SplitPane split = new SplitPane(previewSection, groupsScroll);
    split.setOrientation(Orientation.VERTICAL);
    split.setDividerPositions(0.58);

    setCenter(split);
    updateViewportControlState();
    redrawPreview();
  }

  public void setProjectRoot(File projectRoot) {
    if (Objects.equals(this.projectRoot, projectRoot)) return;
    persistCurrentSetState();
    persistGlobalState();
    this.projectRoot = projectRoot;
    refreshCatalog();
  }

  public void refreshCatalog() {
    persistCurrentSetState();
    persistGlobalState();

    loadPersistentState();
    reloadGameFramingSettings();
    preferredSetId = persisted.getProperty("global.selectedSet", "");

    applyingState = true;
    filterField.setText(persisted.getProperty("global.filter", ""));
    applyingState = false;

    sets.clear();
    selectors.clear();
    activeGroupChecks.clear();
    groupRows.clear();
    groupOrder.clear();
    groupBox.getChildren().clear();
    imageCache.clear();
    presetNameToKey.clear();
    presetBox.getItems().clear();

    if (projectRoot == null || !projectRoot.isDirectory()) {
      currentSetId = null;
      summaryLabel.setText("Open a project to inspect layered image sets.");
      setBox.getItems().clear();
      redrawPreview();
      return;
    }

    Path root = projectRoot.toPath();
    int imageCount = 0;
    try (Stream<Path> stream = Files.walk(root, 10)) {
      List<Path> files = stream
          .filter(Files::isRegularFile)
          .filter(this::isImageFile)
          .filter(path -> !isIgnoredPath(root.relativize(path).toString().replace('\\', '/')))
          .sorted()
          .toList();
      for (Path p : files) {
        String relative = root.relativize(p).toString().replace('\\', '/');
        LayerOption option = parseOption(relative, p.toFile());
        if (option == null) continue;
        String setId = deriveSetId(relative);
        sets.computeIfAbsent(setId, LayeredSet::new).add(option);
        imageCount++;
      }
    } catch (IOException ex) {
      currentSetId = null;
      summaryLabel.setText("Failed to scan project assets: " + ex.getMessage());
      setBox.getItems().clear();
      redrawPreview();
      return;
    }

    summaryLabel.setText("Layer sets: " + sets.size() + "  |  Images: " + imageCount);
    refreshSetOptions();
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

    String target = chooseSetSelection(previous, preferredSetId, visible);

    if (target != null) {
      applyingState = true;
      setBox.getSelectionModel().select(target);
      applyingState = false;
      onSetSelectionChanged();
    } else {
      currentSetId = null;
      selectors.clear();
      activeGroupChecks.clear();
      groupRows.clear();
      groupOrder.clear();
      groupBox.getChildren().clear();
      presetNameToKey.clear();
      presetBox.getItems().clear();
      redrawPreview();
    }
  }

  private void onSetSelectionChanged() {
    String selectedSet = setBox.getValue();
    if (selectedSet == null || selectedSet.isBlank()) return;

    if (currentSetId != null && !currentSetId.equals(selectedSet)) {
      persistCurrentSetState();
    }

    currentSetId = selectedSet;

    selectors.clear();
    activeGroupChecks.clear();
    groupRows.clear();
    groupOrder.clear();
    groupBox.getChildren().clear();

    LayeredSet set = sets.get(selectedSet);
    if (set == null || set.groups.isEmpty()) {
      redrawPreview();
      return;
    }

    List<String> groupNames = new ArrayList<>(set.groups.keySet());
    groupNames.sort(Comparator.naturalOrder());

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
      combo.getSelectionModel().select(options.isEmpty() ? 0 : 1);
      combo.setMaxWidth(Double.MAX_VALUE);

      CheckBox activeCheck = new CheckBox();
      activeCheck.setSelected(true);
      activeCheck.setTooltip(new Tooltip("Include this group when randomizing with active-only mode"));
      activeCheck.selectedProperty().addListener((o, ov, nv) -> {
        if (applyingState) return;
        persistCurrentSetState();
      });

      Button nextButton = iconButton(CssIcon.redo("#8ab4f8"), "Cycle this group", () -> {
        int idx = combo.getSelectionModel().getSelectedIndex();
        if (idx < 1) idx = 0;
        int next = idx + 1;
        if (next >= combo.getItems().size()) next = 1;
        combo.getSelectionModel().select(next);
      });

      Button openButton = iconButton(CssIcon.folder("#7ec8e3"), "Open selected image in OS viewer", () -> openSelectedImage(combo.getValue()));

      Button upButton = iconButton(CssIcon.arrowUp("#b0b8c8"), "Move this group up in render order", () -> moveGroup(groupName, -1));

      Button downButton = iconButton(CssIcon.arrowDown("#b0b8c8"), "Move this group down in render order", () -> moveGroup(groupName, 1));

      combo.valueProperty().addListener((o, ov, nv) -> {
        if (applyingState) {
          redrawPreview();
          return;
        }
        updateExpressionFromSelection();
        redrawPreview();
        persistCurrentSetState();
      });

      Label groupLabel = new Label(groupName);
      groupLabel.setMinWidth(110);
      groupLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

      HBox row = new HBox(6, activeCheck, groupLabel, combo, nextButton, openButton, upButton, downButton);
      row.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(combo, Priority.ALWAYS);

      selectors.put(groupName, combo);
      activeGroupChecks.put(groupName, activeCheck);
      groupRows.put(groupName, row);
      groupOrder.add(groupName);
    }

    if (characterIdField.getText() == null || characterIdField.getText().isBlank()) {
      characterIdField.setText(sanitizeId(takeLastPathToken(selectedSet)));
    }

    applyStateFromPrefix(statePrefix(selectedSet));
    refreshPresetList();

    persistGlobalState();
    persistCurrentSetState();
    redrawPreview();
  }

  private void refreshGroupRows() {
    groupBox.getChildren().clear();
    for (String groupName : groupOrder) {
      HBox row = groupRows.get(groupName);
      if (row != null) groupBox.getChildren().add(row);
    }
  }

  private void moveGroup(String groupName, int delta) {
    int idx = groupOrder.indexOf(groupName);
    if (idx < 0) return;
    int next = idx + delta;
    if (next < 0 || next >= groupOrder.size()) return;
    Collections.swap(groupOrder, idx, next);
    refreshGroupRows();
    redrawPreview();
    persistCurrentSetState();
  }

  private void setAllGroupsActive(boolean active) {
    for (CheckBox check : activeGroupChecks.values()) {
      check.setSelected(active);
    }
    persistCurrentSetState();
  }

  private void applyDefaultSelection() {
    applyingState = true;
    for (ComboBox<LayerOption> combo : selectors.values()) {
      combo.getSelectionModel().select(combo.getItems().size() > 1 ? 1 : 0);
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

  private void redrawPreview() {
    renderPreviewToCanvas(previewCanvas, true);
    if (fullscreenCanvas != null) {
      renderPreviewToCanvas(fullscreenCanvas, false);
    }
  }

  private void renderPreviewToCanvas(Canvas canvas, boolean updateInfoLabel) {
    if (canvas == null) return;
    double w = Math.max(1, canvas.getWidth());
    double h = Math.max(1, canvas.getHeight());
    GraphicsContext g = canvas.getGraphicsContext2D();

    drawCheckerBackground(g, w, h);

    List<LayerOption> active = selectedLayers();
    if (active.isEmpty()) {
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
      drawCenteredText(g, w, h, "Selected images failed to load");
      if (updateInfoLabel) previewInfoLabel.setText("Selected images could not be decoded.");
      return;
    }

    if (matchGameFraming.isSelected()) {
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

    renderViewportPreview(g, layers, w, h, maxW, maxH);
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

    g.setStroke(Color.color(1, 1, 1, 0.22));
    g.strokeRect(x + 0.5, y + 0.5, spriteWidth - 1, spriteHeight - 1);
    g.strokeLine(canvasWidth / 2.0, y, canvasWidth / 2.0, y + spriteHeight);
    g.strokeLine(x, canvasHeight / 2.0, x + spriteWidth, canvasHeight / 2.0);
  }

  private void renderViewportPreview(GraphicsContext g, List<Image> layers, double canvasWidth, double canvasHeight, double maxW, double maxH) {
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

    g.setStroke(Color.color(1, 1, 1, 0.22));
    g.strokeRect(destX + 0.5, destY + 0.5, destW - 1, destH - 1);
    g.strokeLine(canvasWidth / 2.0, destY, canvasWidth / 2.0, destY + destH);
    g.strokeLine(destX, canvasHeight / 2.0, destX + destW, canvasHeight / 2.0);
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

  private void openFullscreenPreview() {
    if (fullscreenStage != null && fullscreenStage.isShowing()) {
      fullscreenStage.toFront();
      return;
    }

    fullscreenCanvas = new Canvas(1280, 720);
    StackPane previewHost = new StackPane(fullscreenCanvas);
    previewHost.setStyle("-fx-background-color: #0f141d;");

    BorderPane root = new BorderPane(previewHost);
    HBox topBar = new HBox();
    topBar.setPadding(new Insets(8));
    topBar.setAlignment(Pos.CENTER_RIGHT);
    Button closeButton = iconButton(CssIcon.clearX("#f38ba8"), "Close fullscreen preview", () -> {
      if (fullscreenStage != null) fullscreenStage.close();
    });
    topBar.getChildren().add(closeButton);
    root.setTop(topBar);

    javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 720);
    scene.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ESCAPE && fullscreenStage != null) {
        fullscreenStage.close();
      }
    });

    fullscreenStage = new Stage();
    fullscreenStage.setTitle("Layered Image Preview");
    fullscreenStage.setScene(scene);
    fullscreenStage.setFullScreenExitHint("Press ESC to exit fullscreen");
    fullscreenStage.setOnHidden(e -> {
      fullscreenCanvas = null;
      fullscreenStage = null;
      redrawPreview();
    });

    scene.widthProperty().addListener((o, ov, nv) -> {
      if (fullscreenCanvas != null) {
        fullscreenCanvas.setWidth(Math.max(1, nv.doubleValue()));
        redrawPreview();
      }
    });
    scene.heightProperty().addListener((o, ov, nv) -> {
      if (fullscreenCanvas != null) {
        double top = topBar.getHeight() + topBar.getPadding().getTop() + topBar.getPadding().getBottom();
        fullscreenCanvas.setHeight(Math.max(1, nv.doubleValue() - top));
        redrawPreview();
      }
    });

    fullscreenStage.show();
    fullscreenStage.setFullScreen(true);
    redrawPreview();
  }

  private void copySnippet() {
    String format = snippetFormatBox.getValue();
    if (format == null || format.isBlank()) format = SNIPPET_COMBINED;
    String snippet = buildSnippet(format);
    copy(snippet);
    status("Copied snippet: " + format + ".");
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
    String show = "[show " + characterId + " center " + expression + "]";

    if (SNIPPET_CHARIMG.equals(format)) return charimg + "\n";
    if (SNIPPET_SHOW.equals(format)) return show + "\n";
    if (SNIPPET_RECIPE.equals(format)) {
      StringBuilder out = new StringBuilder();
      out.append("# Layer recipe generated by JVN Layered Image Visualizer\n");
      out.append("# Multi-layer @charimg is supported via: pathA | pathB | pathC\n");
      if (currentSetId != null && !currentSetId.isBlank()) {
        out.append("# Source set: ").append(currentSetId).append('\n');
      }
      for (String group : groupOrder) {
        ComboBox<LayerOption> combo = selectors.get(group);
        LayerOption option = combo != null ? combo.getValue() : null;
        if (option == null || option.isNone()) continue;
        out.append("# ").append(group).append(" -> ").append(option.relativePath).append('\n');
      }
      out.append(charimg).append('\n').append(show).append('\n');
      return out.toString();
    }

    return charimg + "\n" + show + "\n";
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
    for (String group : groupOrder) {
      ComboBox<LayerOption> combo = selectors.get(group);
      LayerOption option = combo != null ? combo.getValue() : null;
      if (option == null || option.isNone()) continue;
      out.add(option);
    }
    return out;
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
    persisted.setProperty("global.filter", filterField.getText() == null ? "" : filterField.getText().trim());
    String selectedSet = setBox.getValue();
    if (selectedSet != null && !selectedSet.isBlank()) {
      persisted.setProperty("global.selectedSet", selectedSet);
    }
    savePersistentState();
  }

  private void persistCurrentSetState() {
    if (projectRoot == null) return;
    if (currentSetId == null || currentSetId.isBlank()) return;
    captureStateToPrefix(statePrefix(currentSetId));
    savePersistentState();
  }

  private void captureStateToPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) return;

    clearPrefix(prefix + "sel.");
    clearPrefix(prefix + "active.");

    persisted.setProperty(prefix + "charId", sanitizeId(characterIdField.getText()));
    persisted.setProperty(prefix + "expr", sanitizeId(expressionField.getText()));
    persisted.setProperty(prefix + "autoExpr", Boolean.toString(autoExpression.isSelected()));
    persisted.setProperty(prefix + "randomActiveOnly", Boolean.toString(randomizeActiveOnly.isSelected()));
    persisted.setProperty(prefix + "matchGameFraming", Boolean.toString(matchGameFraming.isSelected()));
    persisted.setProperty(prefix + "focusX", formatDouble(focusXSlider.getValue()));
    persisted.setProperty(prefix + "focusY", formatDouble(focusYSlider.getValue()));
    persisted.setProperty(prefix + "crop", formatDouble(cropSlider.getValue()));
    persisted.setProperty(prefix + "zoom", formatDouble(zoomSlider.getValue()));
    persisted.setProperty(prefix + "groupOrder", encodeCsv(groupOrder));

    for (Map.Entry<String, ComboBox<LayerOption>> entry : selectors.entrySet()) {
      String group = entry.getKey();
      String groupKey = encodeKey(group);
      LayerOption option = entry.getValue().getValue();
      if (option != null && !option.isNone()) {
        persisted.setProperty(prefix + "sel." + groupKey, option.relativePath);
      }
      CheckBox active = activeGroupChecks.get(group);
      persisted.setProperty(prefix + "active." + groupKey, Boolean.toString(active == null || active.isSelected()));
    }
  }

  private void applyStateFromPrefix(String prefix) {
    applyingState = true;
    try {
      String fallbackChar = sanitizeId(takeLastPathToken(currentSetId));
      String charId = persisted.getProperty(prefix + "charId", fallbackChar);
      String expr = persisted.getProperty(prefix + "expr", "");

      characterIdField.setText(charId);
      expressionField.setText(expr);
      autoExpression.setSelected(parseBoolean(persisted.getProperty(prefix + "autoExpr"), true));
      randomizeActiveOnly.setSelected(parseBoolean(persisted.getProperty(prefix + "randomActiveOnly"), true));
      matchGameFraming.setSelected(parseBoolean(persisted.getProperty(prefix + "matchGameFraming"), false));

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
    persisted.clear();
    Path file = statePath();
    if (file == null || !Files.exists(file)) return;
    try (InputStream in = Files.newInputStream(file)) {
      persisted.load(in);
    } catch (Exception ignored) {
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
        persisted.store(out, "JVN Layered Image Visualizer State");
      }
    } catch (Exception ignored) {
    }
  }

  private Path statePath() {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return projectRoot.toPath().resolve(STATE_FILE);
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
      return Double.parseDouble(raw.trim());
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

  private LayerOption parseOption(String relative, File file) {
    if (relative == null || file == null) return null;
    String fileName = file.getName();
    int dot = fileName.lastIndexOf('.');
    String base = dot > 0 ? fileName.substring(0, dot) : fileName;
    if (base.isBlank()) return null;

    String[] parts = base.split("[\\s._-]+");
    String group;
    String label;
    if (parts.length >= 2) {
      group = sanitizeId(parts[0]);
      label = sanitizeLabel(String.join("_", java.util.Arrays.copyOfRange(parts, 1, parts.length)));
    } else {
      group = sanitizeId(takeLastPathToken(parentPath(relative)));
      if (group.isBlank()) group = "layer";
      label = sanitizeLabel(base);
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
    return new LayerOption(label, group, relative, file, sortKey);
  }

  private String deriveSetId(String relative) {
    return deriveSetIdFromRelative(relative);
  }

  static String deriveSetIdFromRelative(String relative) {
    String path = relative == null ? "" : relative.replace('\\', '/');
    String parent = parentPath(path);
    if (parent.isBlank()) return "(root)";

    String[] parts = parent.split("/");
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
    if (visibleSetIds == null || visibleSetIds.isEmpty()) return null;

    if (previous != null && !previous.isBlank() && visibleSetIds.contains(previous)) {
      return previous;
    }

    String firstCharacterSet = findFirstCharacterSet(visibleSetIds);
    if (preferred != null && !preferred.isBlank() && visibleSetIds.contains(preferred)) {
      if (isCharacterSetId(preferred) || firstCharacterSet == null) {
        return preferred;
      }
      return firstCharacterSet;
    }

    if (firstCharacterSet != null) {
      return firstCharacterSet;
    }
    return visibleSetIds.get(0);
  }

  static boolean isCharacterSetId(String setId) {
    return setId != null && setId.startsWith("assets/characters/");
  }

  private static String findFirstCharacterSet(List<String> visibleSetIds) {
    if (visibleSetIds == null) return null;
    for (String id : visibleSetIds) {
      if (isCharacterSetId(id)) return id;
    }
    return null;
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

  private static final class LayeredSet {
    final String id;
    final Map<String, List<LayerOption>> groups = new LinkedHashMap<>();

    LayeredSet(String id) {
      this.id = Objects.requireNonNullElse(id, "(set)");
    }

    void add(LayerOption option) {
      if (option == null) return;
      groups.computeIfAbsent(option.group, k -> new ArrayList<>()).add(option);
    }
  }

  private static final class LayerOption {
    final String label;
    final String group;
    final String relativePath;
    final File file;
    final int sortKey;

    LayerOption(String label, String group, String relativePath, File file, int sortKey) {
      this.label = label;
      this.group = group;
      this.relativePath = relativePath;
      this.file = file;
      this.sortKey = sortKey;
    }

    static LayerOption none() {
      return new LayerOption(NONE_LABEL, "none", "", null, Integer.MIN_VALUE);
    }

    boolean isNone() {
      return file == null;
    }
  }
}
