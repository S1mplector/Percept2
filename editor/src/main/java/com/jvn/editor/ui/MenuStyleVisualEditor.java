package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Visual editor for menu style files (*.style).
 * Keeps properties text synchronized while exposing button skin controls.
 */
public class MenuStyleVisualEditor extends BorderPane {
  private static final String[] KNOWN_KEYS = new String[] {
      "itemColor",
      "itemSelectedColor",
      "itemDisabledColor",
      "itemPrefix",
      "itemSelectedPrefix",
      "itemDisabledPrefix",
      "itemFontFamily",
      "itemFontWeight",
      "itemFontSize",
      "buttonAsset",
      "buttonSelectedAsset",
      "buttonDisabledAsset",
      "buttonTextPaddingX",
      "buttonTextPaddingY"
  };

  private final Canvas preview = new Canvas(860, 350);
  private final TextField tfItemColor = new TextField("#D3D3D3");
  private final TextField tfItemSelectedColor = new TextField("#FFFF00");
  private final TextField tfItemDisabledColor = new TextField("#808080");
  private final TextField tfItemPrefix = new TextField("  ");
  private final TextField tfItemSelectedPrefix = new TextField("> ");
  private final TextField tfItemDisabledPrefix = new TextField("- ");
  private final TextField tfItemFontFamily = new TextField("Arial");
  private final ChoiceBox<String> cbItemFontWeight = new ChoiceBox<>();
  private final Spinner<Integer> spItemFontSize = intSpinner(8, 96, 20, 1);

  private final TextField tfButtonAsset = new TextField();
  private final TextField tfButtonSelectedAsset = new TextField();
  private final TextField tfButtonDisabledAsset = new TextField();
  private final Spinner<Double> spButtonTextPaddingX = doubleSpinner(0, 240, 18, 1);
  private final Spinner<Double> spButtonTextPaddingY = doubleSpinner(-120, 120, 0, 1);

  private final Properties rawProperties = new Properties();
  private Consumer<String> onStyleTextChanged;
  private boolean suppressEvents;
  private String lastLoadedText = "";
  private String lastEmittedText = "";
  private File projectRoot;

  private Image buttonAssetImage;
  private Image buttonSelectedAssetImage;
  private Image buttonDisabledAssetImage;

  public MenuStyleVisualEditor() {
    setPadding(new Insets(8));
    cbItemFontWeight.getItems().setAll("NORMAL", "BOLD");
    cbItemFontWeight.setValue("NORMAL");

    preview.setManaged(false);
    StackPane previewPane = new StackPane(preview);
    StackPane.setAlignment(preview, Pos.TOP_LEFT);
    previewPane.getStyleClass().add("layout-studio-preview-host");
    previewPane.setPadding(new Insets(8));
    setCenter(previewPane);

    ScrollPane controls = new ScrollPane(buildControls());
    controls.setFitToWidth(true);
    controls.setPrefWidth(360);
    controls.getStyleClass().add("layout-studio-controls-pane");
    setRight(controls);

    previewPane.widthProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPane));
    previewPane.heightProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPane));
    preview.widthProperty().addListener((o, ov, nv) -> redrawPreview());
    preview.heightProperty().addListener((o, ov, nv) -> redrawPreview());

    registerListeners();
    updatePreviewSize(previewPane);
    redrawPreview();
  }

  public void setOnStyleTextChanged(Consumer<String> onStyleTextChanged) {
    this.onStyleTextChanged = onStyleTextChanged;
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    loadButtonAssets();
    redrawPreview();
  }

  public void setStyleText(String text) {
    String normalized = normalizeText(text);
    if (normalized.equals(lastLoadedText)) return;
    suppressEvents = true;
    rawProperties.clear();
    try {
      if (text != null && !text.isBlank()) rawProperties.load(new StringReader(text));
    } catch (Exception ignored) {
    }

    tfItemColor.setText(rawProperties.getProperty("itemColor", tfItemColor.getText()));
    tfItemSelectedColor.setText(rawProperties.getProperty("itemSelectedColor", tfItemSelectedColor.getText()));
    tfItemDisabledColor.setText(rawProperties.getProperty("itemDisabledColor", tfItemDisabledColor.getText()));
    tfItemPrefix.setText(rawProperties.getProperty("itemPrefix", tfItemPrefix.getText()));
    tfItemSelectedPrefix.setText(rawProperties.getProperty("itemSelectedPrefix", tfItemSelectedPrefix.getText()));
    tfItemDisabledPrefix.setText(rawProperties.getProperty("itemDisabledPrefix", tfItemDisabledPrefix.getText()));
    tfItemFontFamily.setText(rawProperties.getProperty("itemFontFamily", tfItemFontFamily.getText()));
    cbItemFontWeight.setValue(rawProperties.getProperty("itemFontWeight", cbItemFontWeight.getValue()));
    try {
      spItemFontSize.getValueFactory().setValue(Integer.parseInt(rawProperties.getProperty("itemFontSize", Integer.toString(spItemFontSize.getValue()))));
    } catch (Exception ignored) {
    }

    tfButtonAsset.setText(rawProperties.getProperty("buttonAsset", ""));
    tfButtonSelectedAsset.setText(rawProperties.getProperty("buttonSelectedAsset", ""));
    tfButtonDisabledAsset.setText(rawProperties.getProperty("buttonDisabledAsset", ""));
    setSpinnerValue(spButtonTextPaddingX, parseDouble(rawProperties.getProperty("buttonTextPaddingX"), spButtonTextPaddingX.getValue()));
    setSpinnerValue(spButtonTextPaddingY, parseDouble(rawProperties.getProperty("buttonTextPaddingY"), spButtonTextPaddingY.getValue()));

    suppressEvents = false;
    loadButtonAssets();
    redrawPreview();
    lastLoadedText = normalized;
    lastEmittedText = normalizeText(serialize());
  }

  public String getStyleText() {
    return serialize();
  }

  private GridPane buildControls() {
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(8));

    int row = 0;
    row = addHeader(grid, row, "Text Style");
    row = addRow(grid, row, "Item Color", tfItemColor);
    row = addRow(grid, row, "Selected Color", tfItemSelectedColor);
    row = addRow(grid, row, "Disabled Color", tfItemDisabledColor);
    row = addRow(grid, row, "Prefix", tfItemPrefix);
    row = addRow(grid, row, "Selected Prefix", tfItemSelectedPrefix);
    row = addRow(grid, row, "Disabled Prefix", tfItemDisabledPrefix);
    row = addRow(grid, row, "Font Family", tfItemFontFamily);
    row = addRow(grid, row, "Font Weight", cbItemFontWeight);
    row = addRow(grid, row, "Font Size", spItemFontSize);

    row = addHeader(grid, row, "Button Skins");
    row = addAssetRow(grid, row, "Button Asset", tfButtonAsset);
    row = addAssetRow(grid, row, "Selected Asset", tfButtonSelectedAsset);
    row = addAssetRow(grid, row, "Disabled Asset", tfButtonDisabledAsset);
    row = addRow(grid, row, "Text Padding X", spButtonTextPaddingX);
    row = addRow(grid, row, "Text Offset Y", spButtonTextPaddingY);

    Label hint = new Label("Values are serialized into .style properties.\nUse this editor for reusable menu button looks.");
    hint.getStyleClass().add("muted");
    hint.setWrapText(true);
    grid.add(hint, 0, row, 2, 1);
    return grid;
  }

  private int addHeader(GridPane grid, int row, String title) {
    if (row > 0) {
      javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
      grid.add(sep, 0, row++, 2, 1);
    }
    Label label = new Label(title);
    label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, 12));
    grid.add(label, 0, row++, 2, 1);
    return row;
  }

  private int addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
    Label l = new Label(label);
    GridPane.setHgrow(control, Priority.ALWAYS);
    if (control instanceof TextField tf) tf.setMaxWidth(Double.MAX_VALUE);
    if (control instanceof Spinner<?> sp) sp.setMaxWidth(Double.MAX_VALUE);
    if (control instanceof ChoiceBox<?> cb) cb.setMaxWidth(Double.MAX_VALUE);
    grid.add(l, 0, row);
    grid.add(control, 1, row);
    return row + 1;
  }

  private int addAssetRow(GridPane grid, int row, String label, TextField field) {
    Button browse = new Button("Browse...");
    browse.setOnAction(e -> browseAsset(field));
    HBox box = new HBox(6, field, browse);
    HBox.setHgrow(field, Priority.ALWAYS);
    return addRow(grid, row, label, box);
  }

  private void registerListeners() {
    List<TextField> textFields = List.of(
        tfItemColor, tfItemSelectedColor, tfItemDisabledColor,
        tfItemPrefix, tfItemSelectedPrefix, tfItemDisabledPrefix,
        tfItemFontFamily, tfButtonAsset, tfButtonSelectedAsset, tfButtonDisabledAsset
    );
    for (TextField tf : textFields) {
      tf.textProperty().addListener((o, ov, nv) -> onControlChanged());
    }
    cbItemFontWeight.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spItemFontSize.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spButtonTextPaddingX.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spButtonTextPaddingY.valueProperty().addListener((o, ov, nv) -> onControlChanged());
  }

  private void onControlChanged() {
    if (suppressEvents) return;
    loadButtonAssets();
    redrawPreview();
    emitText();
  }

  private void emitText() {
    if (onStyleTextChanged == null) return;
    String text = serialize();
    String normalized = normalizeText(text);
    if (normalized.equals(lastEmittedText)) return;
    lastEmittedText = normalized;
    onStyleTextChanged.accept(text);
  }

  private String serialize() {
    Properties merged = new Properties();
    for (String key : rawProperties.stringPropertyNames()) merged.setProperty(key, rawProperties.getProperty(key));

    merged.setProperty("itemColor", normalize(tfItemColor.getText(), "#D3D3D3"));
    merged.setProperty("itemSelectedColor", normalize(tfItemSelectedColor.getText(), "#FFFF00"));
    merged.setProperty("itemDisabledColor", normalize(tfItemDisabledColor.getText(), "#808080"));
    merged.setProperty("itemPrefix", normalize(tfItemPrefix.getText(), "  "));
    merged.setProperty("itemSelectedPrefix", normalize(tfItemSelectedPrefix.getText(), "> "));
    merged.setProperty("itemDisabledPrefix", normalize(tfItemDisabledPrefix.getText(), "- "));
    merged.setProperty("itemFontFamily", normalize(tfItemFontFamily.getText(), "Arial"));
    merged.setProperty("itemFontWeight", normalize(cbItemFontWeight.getValue(), "NORMAL"));
    merged.setProperty("itemFontSize", Integer.toString(spItemFontSize.getValue()));

    setOptionalProperty(merged, "buttonAsset", tfButtonAsset.getText());
    setOptionalProperty(merged, "buttonSelectedAsset", tfButtonSelectedAsset.getText());
    setOptionalProperty(merged, "buttonDisabledAsset", tfButtonDisabledAsset.getText());
    merged.setProperty("buttonTextPaddingX", formatDouble(spButtonTextPaddingX.getValue()));
    merged.setProperty("buttonTextPaddingY", formatDouble(spButtonTextPaddingY.getValue()));

    StringBuilder out = new StringBuilder();
    out.append("# Menu style").append(System.lineSeparator());
    out.append("# Reusable per-button visuals and typography").append(System.lineSeparator());
    for (String key : KNOWN_KEYS) {
      String value = merged.getProperty(key);
      if (value == null) continue;
      out.append(key).append("=").append(value).append(System.lineSeparator());
    }
    List<String> extras = new ArrayList<>();
    for (String key : merged.stringPropertyNames()) {
      if (isKnownKey(key)) continue;
      extras.add(key);
    }
    extras.sort(String::compareTo);
    if (!extras.isEmpty()) {
      out.append(System.lineSeparator()).append("# Additional custom keys").append(System.lineSeparator());
      for (String key : extras) {
        out.append(key).append("=").append(merged.getProperty(key, "")).append(System.lineSeparator());
      }
    }
    return out.toString();
  }

  private void redrawPreview() {
    GraphicsContext g = preview.getGraphicsContext2D();
    double w = Math.max(1, preview.getWidth());
    double h = Math.max(1, preview.getHeight());
    g.setFill(LayoutStudioPalette.CANVAS_BACKGROUND);
    g.fillRect(0, 0, w, h);

    g.setFill(LayoutStudioPalette.TEXT_PRIMARY);
    g.setFont(Font.font("Arial", FontWeight.BOLD, 20));
    g.fillText("Menu Style Preview", 20, 34);

    double buttonW = Math.min(560, w - 80);
    double buttonH = 58;
    double x = (w - buttonW) / 2.0;
    double y = 80;
    drawPreviewButton(g, x, y, buttonW, buttonH, "  New Game", false, false);
    drawPreviewButton(g, x, y + 78, buttonW, buttonH, "> Continue", true, false);
    drawPreviewButton(g, x, y + 156, buttonW, buttonH, "- Locked Option", false, true);
  }

  private void drawPreviewButton(GraphicsContext g, double x, double y, double w, double h, String text, boolean selected, boolean disabled) {
    Image bg = disabled ? buttonDisabledAssetImage : (selected ? buttonSelectedAssetImage : buttonAssetImage);
    if (bg != null && !bg.isError()) {
      g.drawImage(bg, x, y, w, h);
    } else {
      Color fill = disabled ? LayoutStudioPalette.PANEL_FILL_DISABLED : (selected ? LayoutStudioPalette.PANEL_FILL_SELECTED : LayoutStudioPalette.PANEL_FILL);
      g.setFill(fill);
      g.fillRoundRect(x, y, w, h, 10, 10);
      g.setStroke(selected ? LayoutStudioPalette.PANEL_BORDER_SELECTED : LayoutStudioPalette.PANEL_BORDER);
      g.setLineWidth(selected ? 2.0 : 1.0);
      g.strokeRoundRect(x, y, w, h, 10, 10);
    }

    Color textColor = disabled
        ? parseColor(tfItemDisabledColor.getText(), LayoutStudioPalette.ITEM_COLOR_DISABLED)
        : (selected ? parseColor(tfItemSelectedColor.getText(), LayoutStudioPalette.ITEM_COLOR_SELECTED) : parseColor(tfItemColor.getText(), LayoutStudioPalette.ITEM_COLOR_DEFAULT));
    Font font = resolvePreviewFont();
    g.setFill(textColor);
    g.setFont(font);
    double tw = textWidth(g, text);
    double padX = spButtonTextPaddingX.getValue();
    double offsetY = spButtonTextPaddingY.getValue();
    double textX = x + Math.max(padX, (w - tw) / 2.0);
    if (textX + tw > x + w - 8) textX = x + w - tw - 8;
    g.fillText(text, textX, y + h * 0.58 + offsetY);
  }

  private Font resolvePreviewFont() {
    String family = normalize(tfItemFontFamily.getText(), "Arial");
    FontWeight weight = "BOLD".equalsIgnoreCase(normalize(cbItemFontWeight.getValue(), "NORMAL"))
        ? FontWeight.BOLD : FontWeight.NORMAL;
    return Font.font(family, weight, spItemFontSize.getValue());
  }

  private void browseAsset(TextField targetField) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Select Asset");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
        "Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp"));
    File initial = resolveInitialAssetDirectory();
    if (initial != null && initial.exists() && initial.isDirectory()) chooser.setInitialDirectory(initial);
    Window owner = getScene() != null ? getScene().getWindow() : null;
    File selected = chooser.showOpenDialog(owner);
    if (selected == null) return;
    targetField.setText(toProjectRelativePath(selected));
  }

  private void loadButtonAssets() {
    buttonAssetImage = loadImage(tfButtonAsset.getText());
    buttonSelectedAssetImage = loadImage(tfButtonSelectedAsset.getText());
    buttonDisabledAssetImage = loadImage(tfButtonDisabledAsset.getText());
  }

  private Image loadImage(String path) {
    File asset = resolveAssetFile(path);
    if (asset == null || !asset.exists() || !asset.isFile()) return null;
    try {
      Image image = new Image(asset.toURI().toString(), false);
      return image.isError() ? null : image;
    } catch (Exception ignored) {
      return null;
    }
  }

  private File resolveInitialAssetDirectory() {
    if (projectRoot != null) {
      File ui = new File(projectRoot, "assets/ui");
      if (ui.exists() && ui.isDirectory()) return ui;
      if (projectRoot.isDirectory()) return projectRoot;
    }
    return new File(System.getProperty("user.home", "."));
  }

  private File resolveAssetFile(String path) {
    String value = normalize(path, "");
    if (value.isBlank()) return null;
    File direct = new File(value);
    if (direct.isAbsolute()) return direct;
    if (projectRoot != null) return new File(projectRoot, value);
    return direct;
  }

  private String toProjectRelativePath(File file) {
    if (file == null) return "";
    if (projectRoot == null) return file.getAbsolutePath().replace('\\', '/');
    try {
      Path root = projectRoot.toPath().toAbsolutePath().normalize();
      Path abs = file.toPath().toAbsolutePath().normalize();
      if (abs.startsWith(root)) {
        return root.relativize(abs).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
    }
    return file.getAbsolutePath().replace('\\', '/');
  }

  private static boolean isKnownKey(String key) {
    for (String known : KNOWN_KEYS) {
      if (known.equals(key)) return true;
    }
    return false;
  }

  private static void setOptionalProperty(Properties properties, String key, String value) {
    String normalized = normalize(value, "");
    if (normalized.isBlank()) properties.remove(key);
    else properties.setProperty(key, normalized);
  }

  private static Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
    Spinner<Integer> spinner = new Spinner<>();
    spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, step));
    spinner.setEditable(true);
    return spinner;
  }

  private static Spinner<Double> doubleSpinner(double min, double max, double initial, double step) {
    Spinner<Double> spinner = new Spinner<>();
    SpinnerValueFactory.DoubleSpinnerValueFactory vf =
        new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step);
    vf.setConverter(new javafx.util.StringConverter<>() {
      @Override
      public String toString(Double value) {
        if (value == null) return "";
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.4f", value)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
      }

      @Override
      public Double fromString(String string) {
        if (string == null || string.isBlank()) return vf.getValue();
        try {
          return Double.parseDouble(string.trim());
        } catch (Exception ignored) {
          return vf.getValue();
        }
      }
    });
    spinner.setValueFactory(vf);
    spinner.setEditable(true);
    return spinner;
  }

  private static void setSpinnerValue(Spinner<Double> spinner, double value) {
    SpinnerValueFactory<Double> vf = spinner.getValueFactory();
    if (vf instanceof SpinnerValueFactory.DoubleSpinnerValueFactory dsvf) dsvf.setValue(value);
    else vf.setValue(value);
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isBlank() ? fallback : t;
  }

  private static double parseDouble(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static String formatDouble(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static String normalizeText(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static Color parseColor(String raw, Color fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Color.web(raw.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static double textWidth(GraphicsContext g, String text) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
    helper.setFont(g.getFont());
    return helper.getLayoutBounds().getWidth();
  }

  private void updatePreviewSize(StackPane previewPane) {
    double w = sanitizeCanvasDimension(previewPane.getWidth() - 16.0);
    double h = sanitizeCanvasDimension(previewPane.getHeight() - 16.0);
    if (Math.abs(preview.getWidth() - w) >= 0.5) preview.setWidth(w);
    if (Math.abs(preview.getHeight() - h) >= 0.5) preview.setHeight(h);
    if (Math.abs(preview.getLayoutX() - 8.0) >= 0.5) preview.setLayoutX(8.0);
    if (Math.abs(preview.getLayoutY() - 8.0) >= 0.5) preview.setLayoutY(8.0);
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return Math.max(1.0, Math.min(8192.0, value));
  }
}
