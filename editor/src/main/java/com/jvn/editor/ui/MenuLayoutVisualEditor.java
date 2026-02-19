package com.jvn.editor.ui;

import com.jvn.core.menu.config.MenuLayoutSpec;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Visual editor for menu layout files (*.layout).
 * Syncs with plain properties text via callback.
 */
public class MenuLayoutVisualEditor extends BorderPane {
  private static final String[] KNOWN_KEYS = new String[] {
      "listYStart",
      "lineHeight",
      "listWidthFactor",
      "textAlign",
      "hintsBottomMargin",
      "titleY"
  };

  private final Canvas preview = new Canvas(900, 410);
  private final Properties rawProperties = new Properties();
  private MenuLayoutSpec spec = MenuLayoutSpecDefaults.DEFAULT;
  private Consumer<String> onLayoutTextChanged;
  private boolean suppressEvents = false;

  private final Spinner<Double> spListYStart = spinner(0, 600, 0.35, 0.01);
  private final Spinner<Double> spLineHeight = spinner(10, 260, 40, 1);
  private final Spinner<Double> spListWidthFactor = spinner(0.1, 1, 1, 0.01);
  private final ChoiceBox<String> cbAlign = new ChoiceBox<>();
  private final Spinner<Double> spHintsBottomMargin = spinner(0, 400, 20, 1);
  private final CheckBox cbTitleY = new CheckBox("Override title Y");
  private final Spinner<Double> spTitleY = spinner(0, 600, 60, 1);

  private DragTarget dragTarget = DragTarget.NONE;
  private double dragStartX;
  private double dragStartY;
  private MenuLayoutSpec dragStartSpec = MenuLayoutSpecDefaults.DEFAULT;

  private enum DragTarget {
    NONE,
    LIST,
    TITLE,
    WIDTH_HANDLE
  }

  public MenuLayoutVisualEditor() {
    setPadding(new Insets(8));

    StackPane previewPane = new StackPane(preview);
    previewPane.setStyle("-fx-background-color: linear-gradient(to bottom, #0f141d, #080b10); -fx-border-color: #2a2f3a;");
    previewPane.setPadding(new Insets(8));
    setCenter(previewPane);

    ScrollPane controls = new ScrollPane(buildControls());
    controls.setFitToWidth(true);
    controls.setPrefWidth(320);
    setRight(controls);

    preview.widthProperty().bind(previewPane.widthProperty().subtract(16));
    preview.heightProperty().bind(previewPane.heightProperty().subtract(16));
    preview.widthProperty().addListener((o, ov, nv) -> redraw());
    preview.heightProperty().addListener((o, ov, nv) -> redraw());

    registerPreviewDrag();
    registerListeners();
    redraw();
  }

  public void setOnLayoutTextChanged(Consumer<String> onLayoutTextChanged) {
    this.onLayoutTextChanged = onLayoutTextChanged;
  }

  public void setLayoutText(String text) {
    suppressEvents = true;
    rawProperties.clear();
    try {
      if (text != null && !text.isBlank()) rawProperties.load(new StringReader(text));
    } catch (Exception ignored) {
      // Invalid properties fall back to defaults.
    }
    spec = parse(rawProperties);
    applySpecToControls(spec);
    suppressEvents = false;
    redraw();
  }

  public String getLayoutText() {
    return serialize(spec, rawProperties, cbTitleY.isSelected());
  }

  private GridPane buildControls() {
    GridPane grid = new GridPane();
    grid.setPadding(new Insets(8));
    grid.setHgap(8);
    grid.setVgap(8);

    cbAlign.getItems().setAll("left", "center", "right");
    cbAlign.setValue("center");
    spTitleY.setDisable(true);

    int row = 0;
    row = addRow(grid, row, "List Y Start", spListYStart);
    row = addRow(grid, row, "Line Height", spLineHeight);
    row = addRow(grid, row, "List Width Factor", spListWidthFactor);
    row = addRow(grid, row, "Text Align", cbAlign);
    row = addRow(grid, row, "Hints Bottom Margin", spHintsBottomMargin);

    cbTitleY.selectedProperty().addListener((o, ov, nv) -> {
      spTitleY.setDisable(!nv);
      if (!suppressEvents) {
        redraw();
        emitText();
      }
    });
    grid.add(cbTitleY, 0, row++, 2, 1);
    row = addRow(grid, row, "Title Y (<=1 frac, >1 px)", spTitleY);

    Label hint = new Label("Drag title/list in preview. Drag handle on list edge for width.");
    hint.getStyleClass().add("muted");
    hint.setWrapText(true);
    grid.add(hint, 0, row, 2, 1);
    return grid;
  }

  private int addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
    Label l = new Label(label);
    GridPane.setHgrow(control, Priority.ALWAYS);
    if (control instanceof Spinner<?> s) s.setMaxWidth(Double.MAX_VALUE);
    grid.add(l, 0, row);
    grid.add(control, 1, row);
    return row + 1;
  }

  private void registerListeners() {
    spListYStart.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spLineHeight.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spListWidthFactor.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spHintsBottomMargin.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    spTitleY.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    cbAlign.valueProperty().addListener((o, ov, nv) -> onControlChanged());
  }

  private void onControlChanged() {
    if (suppressEvents) return;
    spec = readSpecFromControls();
    redraw();
    emitText();
  }

  private void registerPreviewDrag() {
    preview.setOnMousePressed(e -> {
      dragStartX = e.getX();
      dragStartY = e.getY();
      dragStartSpec = spec;
      dragTarget = hitTest(e.getX(), e.getY());
    });
    preview.setOnMouseReleased(e -> dragTarget = DragTarget.NONE);
    preview.setOnMouseDragged(e -> {
      if (dragTarget == DragTarget.NONE) return;
      double w = Math.max(1, preview.getWidth());
      double h = Math.max(1, preview.getHeight());
      double dx = e.getX() - dragStartX;
      double dy = e.getY() - dragStartY;

      double listYStart = dragStartSpec.listYStart();
      double listWidth = dragStartSpec.listWidthFactor();
      Double titleY = cbTitleY.isSelected() ? normalizeNullableTitleY(dragStartSpec.titleY()) : null;

      if (dragTarget == DragTarget.LIST) {
        double startPx = resolve(listYStart, h);
        listYStart = clamp01((startPx + dy) / h);
      } else if (dragTarget == DragTarget.TITLE) {
        double titlePx = resolve(titleY != null ? titleY : 0.12, h);
        titleY = clamp01((titlePx + dy) / h);
      } else if (dragTarget == DragTarget.WIDTH_HANDLE) {
        listWidth = computeWidthFactorFromPointer(dragStartSpec.textAlign(), w, e.getX());
      }

      MenuLayoutSpec next = new MenuLayoutSpec(
          dragStartSpec.id(),
          listYStart,
          dragStartSpec.lineHeight(),
          listWidth,
          dragStartSpec.textAlign(),
          dragStartSpec.hintsBottomMargin(),
          titleY
      );
      spec = next;

      suppressEvents = true;
      applySpecToControls(next);
      if (titleY != null && !cbTitleY.isSelected()) cbTitleY.setSelected(true);
      suppressEvents = false;
      redraw();
      emitText();
    });
  }

  private DragTarget hitTest(double x, double y) {
    PreviewRects rects = computePreviewRects(spec, preview.getWidth(), preview.getHeight());
    if (rects.widthHandle().contains(x, y)) return DragTarget.WIDTH_HANDLE;
    if (Math.abs(y - rects.titleY()) <= 12) return DragTarget.TITLE;
    if (rects.listArea().contains(x, y)) return DragTarget.LIST;
    return DragTarget.NONE;
  }

  private void redraw() {
    double w = Math.max(1, preview.getWidth());
    double h = Math.max(1, preview.getHeight());
    GraphicsContext g = preview.getGraphicsContext2D();
    g.setFill(Color.web("#0d1219"));
    g.fillRect(0, 0, w, h);

    g.setStroke(Color.rgb(255, 255, 255, 0.07));
    g.setLineWidth(1);
    for (int i = 1; i < 6; i++) {
      double yy = (h / 6.0) * i;
      g.strokeLine(0, yy, w, yy);
    }

    PreviewRects r = computePreviewRects(spec, w, h);

    // Title guide.
    g.setStroke(Color.rgb(180, 210, 255, 0.9));
    g.setLineWidth(1.5);
    g.strokeLine(0, r.titleY(), w, r.titleY());
    g.setFill(Color.WHITE);
    g.setFont(Font.font("Arial", FontWeight.BOLD, 24));
    g.fillText("Menu Title", (w - 120) / 2.0, r.titleY() - 8);

    // List area and sample entries.
    g.setFill(Color.rgb(34, 40, 52, 0.25));
    g.fillRect(r.listArea().x(), r.listArea().y() - 26, r.listArea().w(), r.listArea().h() + 34);
    g.setStroke(Color.rgb(110, 170, 255, 0.92));
    g.setLineWidth(2);
    g.strokeRect(r.listArea().x(), r.listArea().y() - 26, r.listArea().w(), r.listArea().h() + 34);

    String[] items = new String[] {"> New Game", "  Load", "  Settings", "  Quit"};
    g.setFont(Font.font("Arial", 20));
    g.setFill(Color.rgb(240, 240, 240, 0.98));
    for (int i = 0; i < items.length; i++) {
      double y = r.listArea().y() + i * spec.lineHeight();
      String text = items[i];
      double textWidth = textWidth(g, text);
      double x = switch (spec.textAlign().toLowerCase(Locale.ROOT)) {
        case "left" -> r.listArea().x();
        case "right" -> r.listArea().x() + Math.max(0, r.listArea().w() - textWidth);
        default -> r.listArea().x() + (r.listArea().w() - textWidth) / 2.0;
      };
      g.fillText(text, x, y);
    }

    // Width handle.
    g.setFill(Color.rgb(84, 210, 136, 0.95));
    g.fillOval(r.widthHandle().x(), r.widthHandle().y(), r.widthHandle().w(), r.widthHandle().h());
    g.setStroke(Color.rgb(10, 30, 18, 0.9));
    g.strokeOval(r.widthHandle().x(), r.widthHandle().y(), r.widthHandle().w(), r.widthHandle().h());

    // Hints.
    double hintsY = h - Math.max(0, spec.hintsBottomMargin());
    g.setStroke(Color.rgb(255, 198, 110, 0.9));
    g.strokeLine(0, hintsY, w, hintsY);
    g.setFill(Color.rgb(220, 220, 230, 0.95));
    g.setFont(Font.font("Arial", 14));
    g.fillText("Select: Enter   Back: Esc", (w - 170) / 2.0, hintsY - 6);

    drawTag(g, r.listArea().x() + 6, r.listArea().y() - 30, "List");
    drawTag(g, 8, r.titleY() - 4, "Title Y");
    drawTag(g, 8, hintsY - 4, "Hints");
  }

  private void drawTag(GraphicsContext g, double x, double y, String text) {
    double w = Math.max(44, text.length() * 7.2 + 12);
    g.setFill(Color.rgb(12, 16, 22, 0.9));
    g.fillRoundRect(x, y - 12, w, 16, 6, 6);
    g.setStroke(Color.rgb(100, 130, 180, 0.85));
    g.strokeRoundRect(x, y - 12, w, 16, 6, 6);
    g.setFill(Color.rgb(225, 235, 255, 0.95));
    g.setFont(Font.font("Arial", FontWeight.BOLD, 11));
    g.fillText(text, x + 6, y);
  }

  private PreviewRects computePreviewRects(MenuLayoutSpec s, double w, double h) {
    double listY = resolve(s.listYStart(), h);
    double lineHeight = s.lineHeight();
    double listW = w * clamp(s.listWidthFactor(), 0.1, 1.0);
    String align = s.textAlign() == null ? "center" : s.textAlign().toLowerCase(Locale.ROOT);
    double listX = switch (align) {
      case "left" -> 0;
      case "right" -> w - listW;
      default -> (w - listW) / 2.0;
    };
    double listHeight = Math.max(1, lineHeight * 4);
    double titleY = resolve((s.titleY() != null ? s.titleY() : 0.12), h);
    double handleX = listX + listW - 6;
    double handleY = listY + listHeight / 2.0 - 6;
    return new PreviewRects(
        new Rect(listX, listY, listW, listHeight),
        titleY,
        new Rect(handleX, handleY, 12, 12)
    );
  }

  private double computeWidthFactorFromPointer(String align, double totalWidth, double pointerX) {
    String a = align == null ? "center" : align.toLowerCase(Locale.ROOT);
    double factor;
    if ("left".equals(a)) {
      factor = pointerX / totalWidth;
    } else if ("right".equals(a)) {
      factor = (totalWidth - pointerX) / totalWidth;
    } else {
      double dist = Math.abs(pointerX - totalWidth / 2.0);
      factor = (dist * 2.0) / totalWidth;
    }
    return clamp(factor, 0.1, 1.0);
  }

  private MenuLayoutSpec readSpecFromControls() {
    Double titleY = cbTitleY.isSelected() ? value(spTitleY) : null;
    return new MenuLayoutSpec(
        spec.id(),
        value(spListYStart),
        value(spLineHeight),
        value(spListWidthFactor),
        cbAlign.getValue(),
        value(spHintsBottomMargin),
        titleY
    );
  }

  private void applySpecToControls(MenuLayoutSpec s) {
    setValue(spListYStart, s.listYStart());
    setValue(spLineHeight, s.lineHeight());
    setValue(spListWidthFactor, s.listWidthFactor());
    cbAlign.setValue(s.textAlign());
    setValue(spHintsBottomMargin, s.hintsBottomMargin());
    if (s.titleY() != null) {
      cbTitleY.setSelected(true);
      spTitleY.setDisable(false);
      setValue(spTitleY, s.titleY());
    } else {
      cbTitleY.setSelected(false);
      spTitleY.setDisable(true);
      setValue(spTitleY, 60);
    }
  }

  private void emitText() {
    if (onLayoutTextChanged == null) return;
    onLayoutTextChanged.accept(serialize(spec, rawProperties, cbTitleY.isSelected()));
  }

  private static MenuLayoutSpec parse(Properties properties) {
    MenuLayoutSpec base = MenuLayoutSpecDefaults.DEFAULT;
    if (properties == null) return base;
    Double titleY = null;
    String titleYRaw = properties.getProperty("titleY");
    if (titleYRaw != null && !titleYRaw.isBlank()) {
      titleY = parseDouble(titleYRaw, 60);
    }
    return new MenuLayoutSpec(
        "default",
        parseDouble(properties.getProperty("listYStart"), base.listYStart()),
        parseDouble(properties.getProperty("lineHeight"), base.lineHeight()),
        parseDouble(properties.getProperty("listWidthFactor"), base.listWidthFactor()),
        normalize(properties.getProperty("textAlign"), base.textAlign()),
        parseDouble(properties.getProperty("hintsBottomMargin"), base.hintsBottomMargin()),
        titleY
    );
  }

  private static String serialize(MenuLayoutSpec spec, Properties base, boolean includeTitleY) {
    Properties merged = new Properties();
    if (base != null) {
      for (String key : base.stringPropertyNames()) merged.setProperty(key, base.getProperty(key));
    }
    merged.setProperty("listYStart", format(spec.listYStart()));
    merged.setProperty("lineHeight", format(spec.lineHeight()));
    merged.setProperty("listWidthFactor", format(spec.listWidthFactor()));
    merged.setProperty("textAlign", spec.textAlign());
    merged.setProperty("hintsBottomMargin", format(spec.hintsBottomMargin()));
    if (includeTitleY && spec.titleY() != null) {
      merged.setProperty("titleY", format(spec.titleY()));
    } else {
      merged.remove("titleY");
    }

    StringBuilder out = new StringBuilder();
    out.append("# Menu layout").append(System.lineSeparator());
    out.append("# values <= 1 are treated as fractions; > 1 are pixels").append(System.lineSeparator());
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

  private static boolean isKnownKey(String key) {
    for (String known : KNOWN_KEYS) {
      if (known.equals(key)) return true;
    }
    return false;
  }

  private static Spinner<Double> spinner(double min, double max, double initial, double step) {
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

  private static double value(Spinner<Double> spinner) {
    Double v = spinner.getValue();
    return v == null ? 0 : v;
  }

  private static void setValue(Spinner<Double> spinner, double value) {
    SpinnerValueFactory<Double> vf = spinner.getValueFactory();
    if (vf instanceof SpinnerValueFactory.DoubleSpinnerValueFactory dsvf) {
      dsvf.setValue(value);
    } else {
      vf.setValue(value);
    }
  }

  private static String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private static double parseDouble(String value, double fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return Double.parseDouble(value.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static String format(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static double resolve(double value, double total) {
    return value <= 1.0 ? (total * value) : value;
  }

  private static double clamp(double value, double min, double max) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return min;
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private static double clamp01(double value) {
    return clamp(value, 0, 1);
  }

  private static double normalizeNullableTitleY(Double titleY) {
    if (titleY == null) return 0.12;
    return titleY;
  }

  private static double textWidth(GraphicsContext g, String text) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
    helper.setFont(g.getFont());
    return helper.getLayoutBounds().getWidth();
  }

  private record Rect(double x, double y, double w, double h) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + w && py >= y && py <= y + h;
    }
  }

  private record PreviewRects(Rect listArea, double titleY, Rect widthHandle) {}

  private static final class MenuLayoutSpecDefaults {
    private static final MenuLayoutSpec DEFAULT = new MenuLayoutSpec("default", 0.35, 40.0, 1.0, "center", 20.0, null);
  }
}
