package com.jvn.editor.ui;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import com.jvn.core.menu.config.MenuLayoutSpec;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Visual editor for menu layout files (*.layout).
 * Syncs with plain properties text via callback.
 */
public class MenuLayoutVisualEditor extends BorderPane {
  private static final double PREVIEW_PADDING = 8.0;
  private static final String[] KNOWN_KEYS = new String[] {
      "listYStart",
      "lineHeight",
      "listWidthFactor",
      "textAlign",
      "hintsBottomMargin",
      "titleY"
  };
  private static final Set<String> KNOWN_KEY_SET = Set.copyOf(Arrays.asList(KNOWN_KEYS));

  private final Canvas preview = new Canvas(900, 410);
  private StackPane previewPaneHost;
  private java.io.File projectRoot;
  private final Properties rawProperties = new Properties();
  private MenuLayoutSpec spec = MenuLayoutSpecDefaults.DEFAULT;
  private Consumer<String> onLayoutTextChanged;
  private boolean suppressEvents = false;
  private String lastLoadedText = "";
  private String lastEmittedText = "";

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
  private final UndoManager undoManager = new UndoManager();
  private Button btnUndo;
  private Button btnRedo;
  private boolean applyingHistory = false;
  private final Label validation = new Label("No issues detected.");
  private final List<String> parseDiagnostics = new ArrayList<>();
  private final List<String> lineDiagnostics = new ArrayList<>();
  private final TableView<CustomProperty> customPropsTable = new TableView<>();
  private final ObservableList<CustomProperty> customProps = FXCollections.observableArrayList();
  private String previewTitle = "Menu Title";
  private List<String> previewItems = List.of("> New Game", "  Load", "  Settings", "  Quit");

  private enum DragTarget {
    NONE,
    LIST,
    TITLE,
    WIDTH_HANDLE
  }

  public MenuLayoutVisualEditor() {
    setPadding(new Insets(8));

    preview.setManaged(false);
    previewPaneHost = new StackPane(preview);
    StackPane.setAlignment(preview, Pos.TOP_LEFT);
    previewPaneHost.getStyleClass().add("layout-studio-preview-host");
    previewPaneHost.setPadding(new Insets(PREVIEW_PADDING));
    setCenter(previewPaneHost);

    ScrollPane controls = new ScrollPane(buildControls());
    controls.setFitToWidth(true);
    controls.setPrefWidth(320);
    controls.getStyleClass().add("layout-studio-controls-pane");
    setRight(controls);

    previewPaneHost.widthProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPaneHost));
    previewPaneHost.heightProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPaneHost));
    preview.widthProperty().addListener((o, ov, nv) -> redraw());
    preview.heightProperty().addListener((o, ov, nv) -> redraw());

    registerPreviewDrag();
    registerListeners();
    updatePreviewSize(previewPaneHost);
    refreshValidation();
    redraw();
  }

  public void setProjectRoot(java.io.File root) {
    this.projectRoot = root;
    updatePreviewSize(previewPaneHost);
    redraw();
  }

  public void setOnLayoutTextChanged(Consumer<String> onLayoutTextChanged) {
    this.onLayoutTextChanged = onLayoutTextChanged;
  }

  public void setLayoutText(String text) {
    String normalizedInput = normalizeText(text);
    if (normalizedInput.equals(lastLoadedText)) return;
    lineDiagnostics.clear();
    lineDiagnostics.addAll(DslPropertyDiagnostics.menuLayoutIssues(text, KNOWN_KEY_SET));
    parseDiagnostics.clear();
    suppressEvents = true;
    rawProperties.clear();
    try {
      if (text != null && !text.isBlank()) rawProperties.load(new StringReader(text));
    } catch (Exception ex) {
      parseDiagnostics.add("Failed to parse layout properties: " + ex.getMessage());
      // Invalid properties fall back to defaults.
    }
    spec = parse(rawProperties, parseDiagnostics);
    applySpecToControls(spec);
    customProps.clear();
    for (String key : rawProperties.stringPropertyNames()) {
      if (isKnownKey(key)) continue;
      customProps.add(new CustomProperty(key, rawProperties.getProperty(key, "")));
    }
    suppressEvents = false;
    refreshValidation();
    redraw();
    lastLoadedText = normalizedInput;
    String serialized = serialize(spec, rawProperties, cbTitleY.isSelected(), customProps);
    lastEmittedText = normalizeText(serialized);
    if (!applyingHistory) {
      undoManager.setInitialState(serialized);
    }
  }

  public String getLayoutText() {
    return serialize(spec, rawProperties, cbTitleY.isSelected(), customProps);
  }

  public void setPreviewContent(String title, List<String> items) {
    this.previewTitle = title != null ? title : "Menu Title";
    this.previewItems = items != null && !items.isEmpty() ? items : List.of("> New Game", "  Load", "  Settings", "  Quit");
    redraw();
  }

  private VBox buildControls() {
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
    grid.add(hint, 0, row++, 2, 1);
    validation.getStyleClass().add("muted");
    validation.setWrapText(true);
    grid.add(validation, 0, row++, 2, 1);

    Label historyHeader = new Label("History");
    historyHeader.setStyle("-fx-font-weight: bold;");
    grid.add(historyHeader, 0, row++, 2, 1);
    btnUndo = iconButton(CssIcon.undo(), "Undo");
    btnRedo = iconButton(CssIcon.redo(), "Redo");
    btnUndo.setDisable(true);
    btnRedo.setDisable(true);
    btnUndo.setOnAction(e -> performUndo());
    btnRedo.setOnAction(e -> performRedo());
    undoManager.setOnUndoAvailableChanged(available -> btnUndo.setDisable(!available));
    undoManager.setOnRedoAvailableChanged(available -> btnRedo.setDisable(!available));
    HBox historyButtons = new HBox(8, btnUndo, btnRedo);
    grid.add(historyButtons, 0, row, 2, 1);

    UndoManager.installKeyboardShortcuts(this, this::performUndo, this::performRedo);

    VBox customSection = buildCustomPropertiesSection();

    VBox wrapper = new VBox(8, grid, customSection);
    wrapper.setPadding(new Insets(0));
    return wrapper;
  }

  @SuppressWarnings("unchecked")
  private VBox buildCustomPropertiesSection() {
    Label header = new Label("Custom Properties");
    header.setStyle("-fx-font-weight: bold;");

    customPropsTable.setEditable(true);
    customPropsTable.setItems(customProps);
    customPropsTable.setPrefHeight(160);
    customPropsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    TableColumn<CustomProperty, String> keyCol = new TableColumn<>("Key");
    keyCol.setCellValueFactory(v -> v.getValue().keyProperty());
    keyCol.setCellFactory(TextFieldTableCell.forTableColumn());
    keyCol.setOnEditCommit(e -> {
      e.getRowValue().setKey(e.getNewValue() != null ? e.getNewValue().trim() : "");
      onControlChanged();
    });

    TableColumn<CustomProperty, String> valCol = new TableColumn<>("Value");
    valCol.setCellValueFactory(v -> v.getValue().valueProperty());
    valCol.setCellFactory(TextFieldTableCell.forTableColumn());
    valCol.setOnEditCommit(e -> {
      e.getRowValue().setValue(e.getNewValue() != null ? e.getNewValue().trim() : "");
      onControlChanged();
    });

    customPropsTable.getColumns().setAll(keyCol, valCol);

    Button addBtn = iconButton(CssIcon.plus("#8cd48c"), "Add custom property");
    addBtn.setOnAction(e -> {
      customProps.add(new CustomProperty("custom_key", ""));
      onControlChanged();
    });
    Button removeBtn = iconButton(CssIcon.minus("#e07070"), "Remove selected property");
    removeBtn.setOnAction(e -> {
      int idx = customPropsTable.getSelectionModel().getSelectedIndex();
      if (idx >= 0 && idx < customProps.size()) {
        customProps.remove(idx);
        onControlChanged();
      }
    });
    HBox buttons = new HBox(8, addBtn, removeBtn);

    VBox section = new VBox(4, header, customPropsTable, buttons);
    section.setPadding(new Insets(8));
    return section;
  }

  private int addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
    Label l = new Label(label);
    GridPane.setHgrow(control, Priority.ALWAYS);
    if (control instanceof Spinner<?> s) s.setMaxWidth(Double.MAX_VALUE);
    grid.add(l, 0, row);
    grid.add(control, 1, row);
    return row + 1;
  }

  private static Button iconButton(javafx.scene.Node icon, String tooltip) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltip));
    button.getStyleClass().addAll("layout-studio-action-button", "layout-studio-icon-button");
    return button;
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
    if (!parseDiagnostics.isEmpty()) parseDiagnostics.clear();
    lineDiagnostics.clear();
    lineDiagnostics.addAll(DslPropertyDiagnostics.menuLayoutIssues(
        serialize(spec, rawProperties, cbTitleY.isSelected(), customProps),
        KNOWN_KEY_SET
    ));
    refreshValidation();
    redraw();
    emitText();
  }

  private void refreshValidation() {
    LinkedHashSet<String> warnings = new LinkedHashSet<>();
    warnings.addAll(lineDiagnostics);
    warnings.addAll(parseDiagnostics);
    if (!Double.isFinite(spec.listYStart()) || spec.listYStart() < 0) {
      warnings.add("List Y start must be a finite value >= 0.");
    }
    if (!Double.isFinite(spec.lineHeight()) || spec.lineHeight() <= 0) {
      warnings.add("Line height must be > 0.");
    }
    if (!Double.isFinite(spec.listWidthFactor()) || spec.listWidthFactor() <= 0) {
      warnings.add("List width factor must be > 0.");
    }
    if (!Double.isFinite(spec.hintsBottomMargin()) || spec.hintsBottomMargin() < 0) {
      warnings.add("Hints bottom margin must be >= 0.");
    }
    String align = spec.textAlign() == null ? "" : spec.textAlign().trim().toLowerCase(Locale.ROOT);
    if (!Set.of("left", "center", "right").contains(align)) {
      warnings.add("Text align should be left, center, or right.");
    }
    Set<String> seen = new LinkedHashSet<>();
    for (CustomProperty prop : customProps) {
      String key = prop.getKey();
      if (key == null || key.isBlank()) {
        warnings.add("Custom property key cannot be empty.");
        continue;
      }
      if (isKnownKey(key)) {
        warnings.add("Custom property key conflicts with built-in key: " + key);
        continue;
      }
      if (!seen.add(key)) {
        warnings.add("Duplicate custom property key: " + key);
      }
    }
    if (warnings.isEmpty()) {
      validation.setText("No issues detected.");
      validation.setTextFill(LayoutStudioPalette.TEXT_SUCCESS);
    } else {
      validation.setText(String.join("\n", warnings));
      validation.setTextFill(LayoutStudioPalette.TEXT_WARNING);
    }
  }

  private void registerPreviewDrag() {
    preview.setOnMousePressed(e -> {
      dragStartX = e.getX();
      dragStartY = e.getY();
      dragStartSpec = spec;
      dragTarget = hitTest(e.getX(), e.getY());
    });
    preview.setOnMouseReleased(e -> {
      boolean wasDragging = dragTarget != DragTarget.NONE;
      dragTarget = DragTarget.NONE;
      if (wasDragging) emitText();
    });
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
    g.setFill(LayoutStudioPalette.CANVAS_BACKGROUND);
    g.fillRect(0, 0, w, h);

    g.setStroke(LayoutStudioPalette.GRID_LINE);
    g.setLineWidth(1);
    for (int i = 1; i < 6; i++) {
      double yy = (h / 6.0) * i;
      g.strokeLine(0, yy, w, yy);
    }

    PreviewRects r = computePreviewRects(spec, w, h);

    // Title guide.
    g.setStroke(LayoutStudioPalette.ACCENT_BLUE_LIGHT);
    g.setLineWidth(1.5);
    g.strokeLine(0, r.titleY(), w, r.titleY());
    g.setFill(LayoutStudioPalette.TEXT_PRIMARY);
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 24));
    double titleW = textWidth(g, previewTitle);
    g.fillText(previewTitle, (w - titleW) / 2.0, r.titleY() - 8);

    // List area and sample entries.
    g.setFill(LayoutStudioPalette.PANEL_FILL_SOFT);
    g.fillRect(r.listArea().x(), r.listArea().y() - 26, r.listArea().w(), r.listArea().h() + 34);
    g.setStroke(LayoutStudioPalette.ACCENT_BLUE);
    g.setLineWidth(2);
    g.strokeRect(r.listArea().x(), r.listArea().y() - 26, r.listArea().w(), r.listArea().h() + 34);

    g.setFont(Font.font(Font.getDefault().getFamily(), 20));
    g.setFill(LayoutStudioPalette.TEXT_SECONDARY);
    for (int i = 0; i < previewItems.size(); i++) {
      double y = r.listArea().y() + i * spec.lineHeight();
      String text = previewItems.get(i);
      double textWidth = textWidth(g, text);
      double x = switch (spec.textAlign().toLowerCase(Locale.ROOT)) {
        case "left" -> r.listArea().x();
        case "right" -> r.listArea().x() + Math.max(0, r.listArea().w() - textWidth);
        default -> r.listArea().x() + (r.listArea().w() - textWidth) / 2.0;
      };
      g.fillText(text, x, y);
    }

    // Width handle.
    g.setFill(LayoutStudioPalette.ACCENT_GREEN);
    g.fillOval(r.widthHandle().x(), r.widthHandle().y(), r.widthHandle().w(), r.widthHandle().h());
    g.setStroke(LayoutStudioPalette.ACCENT_GREEN_DARK);
    g.strokeOval(r.widthHandle().x(), r.widthHandle().y(), r.widthHandle().w(), r.widthHandle().h());

    // Hints.
    double hintsY = h - Math.max(0, spec.hintsBottomMargin());
    g.setStroke(LayoutStudioPalette.ACCENT_GOLD);
    g.strokeLine(0, hintsY, w, hintsY);
    g.setFill(LayoutStudioPalette.TEXT_SECONDARY);
    g.setFont(Font.font(Font.getDefault().getFamily(), 14));

    drawTag(g, r.listArea().x() + 6, r.listArea().y() - 30, "List");
    drawTag(g, 8, r.titleY() - 4, "Title Y");
    drawTag(g, 8, hintsY - 4, "Hints");
  }

  private void drawTag(GraphicsContext g, double x, double y, String text) {
    double w = Math.max(44, text.length() * 7.2 + 12);
    g.setFill(LayoutStudioPalette.TAG_BG);
    g.fillRoundRect(x, y - 12, w, 16, 6, 6);
    g.setStroke(LayoutStudioPalette.TAG_BORDER);
    g.strokeRoundRect(x, y - 12, w, 16, 6, 6);
    g.setFill(LayoutStudioPalette.TAG_TEXT);
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 11));
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
    int itemCount = Math.max(1, previewItems.size());
    double listHeight = Math.max(1, lineHeight * itemCount);
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
    String text = serialize(spec, rawProperties, cbTitleY.isSelected(), customProps);
    String normalized = normalizeText(text);
    if (normalized.equals(lastEmittedText)) return;
    undoManager.captureState(text);
    lastEmittedText = normalized;
    onLayoutTextChanged.accept(text);
  }

  private void performUndo() {
    String previous = undoManager.undo();
    if (previous == null) return;
    applyingHistory = true;
    suppressEvents = true;
    try {
      setLayoutText(previous);
    } finally {
      suppressEvents = false;
      applyingHistory = false;
    }
    if (onLayoutTextChanged != null) onLayoutTextChanged.accept(previous);
  }

  private void performRedo() {
    String next = undoManager.redo();
    if (next == null) return;
    applyingHistory = true;
    suppressEvents = true;
    try {
      setLayoutText(next);
    } finally {
      suppressEvents = false;
      applyingHistory = false;
    }
    if (onLayoutTextChanged != null) onLayoutTextChanged.accept(next);
  }

  private static MenuLayoutSpec parse(Properties properties, List<String> diagnostics) {
    MenuLayoutSpec base = MenuLayoutSpecDefaults.DEFAULT;
    if (properties == null) return base;
    Double titleY = null;
    String titleYRaw = properties.getProperty("titleY");
    if (titleYRaw != null && !titleYRaw.isBlank()) {
      titleY = parseDouble(titleYRaw, 60, diagnostics, "titleY");
    }
    return new MenuLayoutSpec(
        "default",
        parseDouble(properties.getProperty("listYStart"), base.listYStart(), diagnostics, "listYStart"),
        parseDouble(properties.getProperty("lineHeight"), base.lineHeight(), diagnostics, "lineHeight"),
        parseDouble(properties.getProperty("listWidthFactor"), base.listWidthFactor(), diagnostics, "listWidthFactor"),
        normalize(properties.getProperty("textAlign"), base.textAlign()),
        parseDouble(properties.getProperty("hintsBottomMargin"), base.hintsBottomMargin(), diagnostics, "hintsBottomMargin"),
        titleY
    );
  }

  private static String serialize(MenuLayoutSpec spec, Properties base, boolean includeTitleY, List<CustomProperty> customPropsList) {
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
    for (String key : new ArrayList<>(merged.stringPropertyNames())) {
      if (!isKnownKey(key)) merged.remove(key);
    }
    if (customPropsList != null) {
      for (CustomProperty cp : customPropsList) {
        String k = cp.getKey();
        if (k != null && !k.isBlank() && !isKnownKey(k)) {
          merged.setProperty(k, cp.getValue());
        }
      }
    }

    StringBuilder out = new StringBuilder();
    out.append("# Menu layout (.layout)").append(System.lineSeparator());
    out.append("# Text-first workflow: edit -> save -> run runtime -> verify -> iterate.").append(System.lineSeparator());
    out.append("# Format: key=value (Java .properties)").append(System.lineSeparator());
    out.append("# Units:").append(System.lineSeparator());
    out.append("# - listYStart/titleY: <=1 means viewport fraction, >1 means pixels.").append(System.lineSeparator());
    out.append("# - lineHeight/hintsBottomMargin: pixels.").append(System.lineSeparator());
    out.append("# - listWidthFactor: viewport fraction (0.1..1.0).").append(System.lineSeparator());
    out.append("# - textAlign: left|center|right.").append(System.lineSeparator());
    for (String key : KNOWN_KEYS) {
      String value = merged.getProperty(key);
      if (value == null) continue;
      if ("listYStart".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Vertical flow ---").append(System.lineSeparator());
        out.append("# listYStart: first row anchor.").append(System.lineSeparator());
        out.append("# lineHeight: spacing between consecutive rows.").append(System.lineSeparator());
      } else if ("listWidthFactor".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Horizontal flow ---").append(System.lineSeparator());
        out.append("# listWidthFactor + textAlign define row region width and text placement.").append(System.lineSeparator());
      } else if ("hintsBottomMargin".equals(key)) {
        out.append(System.lineSeparator()).append("# --- Footer / title offsets ---").append(System.lineSeparator());
        out.append("# hintsBottomMargin shifts hint text above viewport bottom.").append(System.lineSeparator());
      }
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

  private static double parseDouble(String value, double fallback, List<String> diagnostics, String key) {
    if (value == null || value.isBlank()) return fallback;
    try {
      double parsed = Double.parseDouble(value.trim());
      if (!Double.isFinite(parsed)) throw new NumberFormatException("non-finite");
      return parsed;
    } catch (Exception ignored) {
      if (diagnostics != null && key != null) {
        diagnostics.add("Invalid number for '" + key + "': '" + value + "' (using " + fallback + ")");
      }
      return fallback;
    }
  }

  private static String format(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static String normalizeText(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private void updatePreviewSize(StackPane previewPane) {
    if (previewPane == null) return;
    double availableW = sanitizeCanvasDimension(previewPane.getWidth() - PREVIEW_PADDING * 2.0);
    double availableH = sanitizeCanvasDimension(previewPane.getHeight() - PREVIEW_PADDING * 2.0);
    double aspect = ProjectViewportSpec.resolve(projectRoot).aspect();
    double w = availableW;
    double h = w / Math.max(0.0001, aspect);
    if (h > availableH) {
      h = availableH;
      w = h * aspect;
    }
    if (Math.abs(preview.getWidth() - w) >= 0.5) preview.setWidth(w);
    if (Math.abs(preview.getHeight() - h) >= 0.5) preview.setHeight(h);
    double x = PREVIEW_PADDING + (availableW - w) * 0.5;
    double y = PREVIEW_PADDING + (availableH - h) * 0.5;
    if (Math.abs(preview.getLayoutX() - x) >= 0.5) preview.setLayoutX(x);
    if (Math.abs(preview.getLayoutY() - y) >= 0.5) preview.setLayoutY(y);
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return clamp(value, 1.0, 8192.0);
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

  static final class CustomProperty {
    private final StringProperty key = new SimpleStringProperty("");
    private final StringProperty value = new SimpleStringProperty("");

    CustomProperty(String key, String value) {
      setKey(key);
      setValue(value);
    }

    String getKey() { return key.get(); }
    void setKey(String v) { key.set(v == null ? "" : v.trim()); }
    StringProperty keyProperty() { return key; }

    String getValue() { return value.get(); }
    void setValue(String v) { value.set(v == null ? "" : v.trim()); }
    StringProperty valueProperty() { return value; }
  }
}
