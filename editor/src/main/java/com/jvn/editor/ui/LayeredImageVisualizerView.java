package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.scene.image.Image;
import javafx.util.StringConverter;

/**
 * Sidebar utility that helps build and preview layered character images.
 * This is intended as JVN's first pass of a "Layered Image Visualizer" workflow.
 */
public class LayeredImageVisualizerView extends BorderPane {
  private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");
  private static final String NONE_LABEL = "(none)";

  private final Label summaryLabel = new Label("Open a project to inspect layered image sets.");
  private final Label statusLabel = new Label("");
  private final TextField filterField = new TextField();
  private final ComboBox<String> setBox = new ComboBox<>();
  private final TextField characterIdField = new TextField();
  private final TextField expressionField = new TextField();
  private final CheckBox autoExpression = new CheckBox("Auto expression from selected layers");

  private final Canvas previewCanvas = new Canvas(320, 250);
  private final Label previewInfoLabel = new Label("No layers selected.");
  private final Slider focusXSlider = slider(0, 100, 50);
  private final Slider focusYSlider = slider(0, 100, 50);
  private final Slider cropSlider = slider(20, 100, 100);
  private final Slider zoomSlider = slider(50, 300, 100);

  private final VBox groupBox = new VBox(8);
  private final Map<String, ComboBox<LayerOption>> selectors = new LinkedHashMap<>();
  private final Map<String, LayeredSet> sets = new LinkedHashMap<>();
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Random random = new Random();

  private File projectRoot;

  public LayeredImageVisualizerView() {
    setPadding(new Insets(8));

    Label title = new Label("Layered Image Visualizer");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

    filterField.setPromptText("Filter sets...");
    filterField.textProperty().addListener((o, ov, nv) -> refreshSetOptions());
    setBox.setPromptText("Select layered set");
    setBox.setConverter(new StringConverter<>() {
      @Override public String toString(String object) { return object == null ? "" : object; }
      @Override public String fromString(String string) { return string; }
    });
    setBox.setOnAction(e -> onSetSelectionChanged());

    Button refreshButton = new Button("Refresh");
    refreshButton.setGraphic(CssIcon.redo("#7ec8e3"));
    refreshButton.setOnAction(e -> refreshCatalog());

    HBox setRow = new HBox(8, new Label("Set"), setBox, refreshButton);
    setRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(setBox, Priority.ALWAYS);

    HBox filterRow = new HBox(8, new Label("Filter"), filterField);
    filterRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);

    VBox top = new VBox(8, title, summaryLabel, filterRow, setRow, new Separator());
    setTop(top);

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

    focusXSlider.valueProperty().addListener((o, ov, nv) -> redrawPreview());
    focusYSlider.valueProperty().addListener((o, ov, nv) -> redrawPreview());
    cropSlider.valueProperty().addListener((o, ov, nv) -> redrawPreview());
    zoomSlider.valueProperty().addListener((o, ov, nv) -> redrawPreview());

    autoExpression.setSelected(true);
    characterIdField.setPromptText("Character/tag id");
    expressionField.setPromptText("Expression id");
    autoExpression.selectedProperty().addListener((o, ov, nv) -> {
      if (Boolean.TRUE.equals(nv)) updateExpressionFromSelection();
    });

    HBox idRow = new HBox(8, new Label("Char"), characterIdField, new Label("Expr"), expressionField);
    idRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(characterIdField, Priority.ALWAYS);
    HBox.setHgrow(expressionField, Priority.ALWAYS);

    Button randomizeButton = new Button("Randomize");
    randomizeButton.setGraphic(CssIcon.sort("#f0b673"));
    randomizeButton.setOnAction(e -> randomizeSelection());

    Button resetViewButton = new Button("Reset View");
    resetViewButton.setGraphic(CssIcon.expand("#8ab4f8"));
    resetViewButton.setOnAction(e -> {
      focusXSlider.setValue(50);
      focusYSlider.setValue(50);
      cropSlider.setValue(100);
      zoomSlider.setValue(100);
      redrawPreview();
    });

    Button copyShowButton = new Button("Copy [show]");
    copyShowButton.setGraphic(CssIcon.copy("#9ad19c"));
    copyShowButton.setOnAction(e -> copyShowCommand());

    Button copyRecipeButton = new Button("Copy Layer Recipe");
    copyRecipeButton.setGraphic(CssIcon.copy("#d6b4ff"));
    copyRecipeButton.setOnAction(e -> copyLayerRecipe());

    HBox toolRow = new HBox(8, randomizeButton, resetViewButton, copyShowButton, copyRecipeButton);
    toolRow.setAlignment(Pos.CENTER_LEFT);

    VBox controls = new VBox(
        6,
        sliderRow("Focus X", focusXSlider),
        sliderRow("Focus Y", focusYSlider),
        sliderRow("Crop", cropSlider),
        sliderRow("Zoom", zoomSlider));

    VBox previewSection = new VBox(
        8,
        previewPane,
        previewInfoLabel,
        controls,
        idRow,
        autoExpression,
        toolRow,
        statusLabel);
    previewSection.setPadding(new Insets(0, 0, 4, 0));

    Label groupsLabel = new Label("Layer Groups");
    groupsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 700;");
    VBox groupsRoot = new VBox(8, groupsLabel, groupBox);
    groupsRoot.setPadding(new Insets(2));
    ScrollPane groupsScroll = new ScrollPane(groupsRoot);
    groupsScroll.setFitToWidth(true);

    SplitPane split = new SplitPane(previewSection, groupsScroll);
    split.setOrientation(Orientation.VERTICAL);
    split.setDividerPositions(0.58);

    setCenter(split);
    redrawPreview();
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    refreshCatalog();
  }

  public void refreshCatalog() {
    sets.clear();
    selectors.clear();
    groupBox.getChildren().clear();
    imageCache.clear();

    if (projectRoot == null || !projectRoot.isDirectory()) {
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
      summaryLabel.setText("Failed to scan project assets: " + ex.getMessage());
      setBox.getItems().clear();
      redrawPreview();
      return;
    }

    int setCount = sets.size();
    summaryLabel.setText("Layer sets: " + setCount + "  |  Images: " + imageCount);
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
    if (previous != null && visible.contains(previous)) {
      setBox.getSelectionModel().select(previous);
    } else if (!visible.isEmpty()) {
      setBox.getSelectionModel().select(0);
    } else {
      selectors.clear();
      groupBox.getChildren().clear();
      redrawPreview();
    }
  }

  private void onSetSelectionChanged() {
    selectors.clear();
    groupBox.getChildren().clear();
    String selectedSet = setBox.getValue();
    if (selectedSet == null || selectedSet.isBlank()) {
      redrawPreview();
      return;
    }
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
      combo.valueProperty().addListener((o, ov, nv) -> {
        updateExpressionFromSelection();
        redrawPreview();
      });

      Button nextButton = new Button("↻");
      nextButton.setTooltip(new javafx.scene.control.Tooltip("Cycle this group"));
      nextButton.setOnAction(e -> {
        int idx = combo.getSelectionModel().getSelectedIndex();
        if (idx < 1) idx = 0;
        int next = idx + 1;
        if (next >= combo.getItems().size()) next = 1;
        combo.getSelectionModel().select(next);
      });

      Label groupLabel = new Label(groupName);
      groupLabel.setMinWidth(110);
      groupLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

      HBox row = new HBox(8, groupLabel, combo, nextButton);
      row.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(combo, Priority.ALWAYS);
      groupBox.getChildren().add(row);
      selectors.put(groupName, combo);
    }

    if (characterIdField.getText() == null || characterIdField.getText().isBlank()) {
      characterIdField.setText(sanitizeId(takeLastPathToken(selectedSet)));
    }
    updateExpressionFromSelection();
    redrawPreview();
  }

  private void randomizeSelection() {
    for (ComboBox<LayerOption> combo : selectors.values()) {
      int size = combo.getItems().size();
      if (size <= 1) continue;
      int idx = 1 + random.nextInt(size - 1);
      combo.getSelectionModel().select(idx);
    }
    updateExpressionFromSelection();
    redrawPreview();
    status("Randomized selected layers.");
  }

  private void redrawPreview() {
    double w = previewCanvas.getWidth();
    double h = previewCanvas.getHeight();
    GraphicsContext g = previewCanvas.getGraphicsContext2D();
    g.setFill(Color.web("#121720"));
    g.fillRect(0, 0, w, h);

    List<LayerOption> active = selectedLayers();
    if (active.isEmpty()) {
      drawCenteredText(g, w, h, "Select layer options to preview");
      previewInfoLabel.setText("No layers selected.");
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
      previewInfoLabel.setText("Selected images could not be decoded.");
      return;
    }

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

    double fit = Math.min(w / srcW, h / srcH);
    double destW = srcW * fit;
    double destH = srcH * fit;
    double destX = (w - destW) * 0.5;
    double destY = (h - destH) * 0.5;

    for (Image img : layers) {
      double sx = srcX * img.getWidth() / maxW;
      double sy = srcY * img.getHeight() / maxH;
      double sw = srcW * img.getWidth() / maxW;
      double sh = srcH * img.getHeight() / maxH;
      g.drawImage(img, sx, sy, sw, sh, destX, destY, destW, destH);
    }

    g.setStroke(Color.color(1, 1, 1, 0.2));
    g.strokeRect(destX + 0.5, destY + 0.5, destW - 1, destH - 1);
    g.strokeLine(w / 2, destY, w / 2, destY + destH);
    g.strokeLine(destX, h / 2, destX + destW, h / 2);

    previewInfoLabel.setText("Layers: " + active.size() + "  |  Virtual size: " + (int) maxW + "x" + (int) maxH);
  }

  private void copyShowCommand() {
    String characterId = sanitizeId(characterIdField.getText());
    String expression = sanitizeId(expressionField.getText());
    if (characterId.isBlank()) {
      status("Set a character id first.");
      return;
    }
    if (expression.isBlank()) expression = "neutral";
    copy("[show " + characterId + " center " + expression + "]");
    status("Copied [show] command.");
  }

  private void copyLayerRecipe() {
    String characterId = sanitizeId(characterIdField.getText());
    String expression = sanitizeId(expressionField.getText());
    if (characterId.isBlank()) characterId = "character_id";
    if (expression.isBlank()) expression = "neutral";

    String compositePath = "assets/characters/" + characterId + "/" + expression + ".png";
    StringBuilder out = new StringBuilder();
    out.append("# Layer recipe generated by JVN Layered Image Visualizer\n");
    String selectedSet = setBox.getValue();
    if (selectedSet != null && !selectedSet.isBlank()) {
      out.append("# Source set: ").append(selectedSet).append('\n');
    }
    for (Map.Entry<String, ComboBox<LayerOption>> entry : selectors.entrySet()) {
      LayerOption option = entry.getValue().getValue();
      if (option == null || option.isNone()) continue;
      out.append("# ").append(entry.getKey()).append(" -> ").append(option.relativePath).append('\n');
    }
    out.append("@charimg ").append(characterId).append(' ').append(expression).append(' ').append(compositePath).append('\n');
    out.append("[show ").append(characterId).append(" center ").append(expression).append("]\n");
    copy(out.toString());
    status("Copied layer recipe snippet.");
  }

  private void updateExpressionFromSelection() {
    if (!autoExpression.isSelected()) return;
    List<String> names = new ArrayList<>();
    for (Map.Entry<String, ComboBox<LayerOption>> entry : selectors.entrySet()) {
      LayerOption option = entry.getValue().getValue();
      if (option == null || option.isNone()) continue;
      names.add(sanitizeId(option.label));
    }
    String expr = names.isEmpty() ? "neutral" : String.join("_", names);
    expressionField.setText(expr);
  }

  private List<LayerOption> selectedLayers() {
    List<LayerOption> out = new ArrayList<>();
    for (ComboBox<LayerOption> combo : selectors.values()) {
      LayerOption option = combo.getValue();
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
        || path.contains("/out/");
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

  private static String sanitizeId(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  private static String sanitizeLabel(String raw) {
    if (raw == null) return "";
    String s = raw.trim().replaceAll("[\\s]+", "_");
    s = s.replaceAll("[^a-zA-Z0-9_]+", "_");
    s = s.replaceAll("^_+", "").replaceAll("_+$", "");
    return s;
  }

  private static String parentPath(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash <= 0 ? "" : path.substring(0, slash);
  }

  private static String takeLastPathToken(String path) {
    if (path == null || path.isBlank()) return "";
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
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
