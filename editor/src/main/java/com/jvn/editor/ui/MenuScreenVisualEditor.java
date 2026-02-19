package com.jvn.editor.ui;

import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Visual editor for menu screen definitions (*.menu).
 * Supports safe text synchronization and preserves unknown keys.
 */
public class MenuScreenVisualEditor extends BorderPane {
  private static final Set<String> TOP_LEVEL_KEYS = Set.of(
      "titleText", "hintsText", "layout", "layoutId", "defaultItemStyle", "wrapSelection", "items"
  );
  private static final Set<String> ITEM_KEYS = Set.of(
      "label", "style", "icon", "enabled", "action", "target"
  );

  private final TextField tfTitle = new TextField();
  private final TextField tfHints = new TextField();
  private final ComboBox<String> cbLayout = new ComboBox<>();
  private final ComboBox<String> cbDefaultStyle = new ComboBox<>();
  private final CheckBox cbWrap = new CheckBox("Wrap selection");
  private final Label validation = new Label();

  private final TableView<MenuItemRow> table = new TableView<>();
  private final ObservableList<MenuItemRow> rows = FXCollections.observableArrayList();
  private final Canvas preview = new Canvas(520, 320);

  private final Properties topLevelExtras = new Properties();
  private Consumer<String> onMenuTextChanged;
  private boolean suppressEvents = false;
  private File projectRoot;
  private String screenIdHint = "main";
  private int previewSelected = 0;

  public MenuScreenVisualEditor() {
    setPadding(new Insets(8));
    buildTopForm();
    buildCenter();
    registerTopListeners();
    setDefaults();
    redrawPreview();
  }

  public void setOnMenuTextChanged(Consumer<String> onMenuTextChanged) {
    this.onMenuTextChanged = onMenuTextChanged;
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    refreshSuggestions();
    validateState();
  }

  public void setScreenIdHint(String id) {
    if (id != null && !id.isBlank()) this.screenIdHint = id.trim();
  }

  public void setMenuText(String text) {
    suppressEvents = true;
    Properties p = new Properties();
    try {
      if (text != null && !text.isBlank()) p.load(new StringReader(text));
    } catch (Exception ignored) {
      // Keep defaults when parse fails.
    }

    topLevelExtras.clear();
    for (String key : p.stringPropertyNames()) {
      if (isKnownTopLevelKey(key)) continue;
      if (isItemKey(key)) continue;
      topLevelExtras.setProperty(key, p.getProperty(key, ""));
    }

    tfTitle.setText(p.getProperty("titleText", ""));
    tfHints.setText(p.getProperty("hintsText", ""));
    cbLayout.setEditable(true);
    cbLayout.getEditor().setText(normalize(p.getProperty("layout", p.getProperty("layoutId")), "default"));
    cbDefaultStyle.setEditable(true);
    cbDefaultStyle.getEditor().setText(normalize(p.getProperty("defaultItemStyle"), "default"));
    cbWrap.setSelected(parseBoolean(p.getProperty("wrapSelection"), true));

    List<String> ids = parseCsv(p.getProperty("items"));
    if (ids.isEmpty()) ids = collectItemIdsFromProperties(p);

    rows.clear();
    if (ids.isEmpty()) {
      rows.add(defaultRow("new_game", MenuActionType.NEW_GAME, null));
      rows.add(defaultRow("load", MenuActionType.LOAD_MENU, null));
      rows.add(defaultRow("settings", MenuActionType.SETTINGS_MENU, null));
      rows.add(defaultRow("quit", MenuActionType.QUIT, null));
    } else {
      for (String id : ids) {
        String key = normalize(id, "");
        if (key.isEmpty()) continue;
        String prefix = "item." + key + ".";
        MenuActionSpec action = MenuActionSpec.parse(
            p.getProperty(prefix + "action"),
            p.getProperty(prefix + "target")
        );
        MenuItemRow row = new MenuItemRow(
            key,
            p.getProperty(prefix + "label", ""),
            p.getProperty(prefix + "style", ""),
            p.getProperty(prefix + "icon", ""),
            parseBoolean(p.getProperty(prefix + "enabled"), true),
            action.type(),
            action.target()
        );
        for (String prop : p.stringPropertyNames()) {
          if (!prop.startsWith(prefix)) continue;
          String field = prop.substring(prefix.length());
          if (ITEM_KEYS.contains(field)) continue;
          row.extras.put(field, p.getProperty(prop, ""));
        }
        attachRowListeners(row);
        rows.add(row);
      }
    }

    table.setItems(rows);
    previewSelected = rows.isEmpty() ? 0 : Math.min(previewSelected, rows.size() - 1);
    suppressEvents = false;
    validateState();
    redrawPreview();
  }

  public String getMenuText() {
    return serialize();
  }

  private void buildTopForm() {
    GridPane g = new GridPane();
    g.setHgap(8);
    g.setVgap(8);

    cbLayout.setEditable(true);
    cbDefaultStyle.setEditable(true);

    addRow(g, 0, "Title", tfTitle);
    addRow(g, 1, "Hints", tfHints);
    addRow(g, 2, "Layout", cbLayout);
    addRow(g, 3, "Default Style", cbDefaultStyle);
    g.add(cbWrap, 1, 4);

    validation.getStyleClass().add("muted");
    validation.setWrapText(true);
    g.add(validation, 0, 5, 2, 1);

    setTop(g);
    BorderPane.setMargin(g, new Insets(0, 0, 8, 0));
  }

  private void buildCenter() {
    table.setEditable(true);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    buildColumns();
    table.setItems(rows);
    table.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      previewSelected = Math.max(0, nv == null ? 0 : nv.intValue());
      redrawPreview();
    });

    HBox actions = new HBox(6);
    Button bAdd = new Button("Add Item");
    Button bRemove = new Button("Remove");
    Button bUp = new Button("Up");
    Button bDown = new Button("Down");
    Button bNormalize = new Button("Normalize IDs");
    bAdd.setOnAction(e -> addRow());
    bRemove.setOnAction(e -> removeRow());
    bUp.setOnAction(e -> moveSelected(-1));
    bDown.setOnAction(e -> moveSelected(1));
    bNormalize.setOnAction(e -> normalizeIds());
    actions.getChildren().addAll(bAdd, bRemove, bUp, bDown, bNormalize);

    VBox tablePane = new VBox(6, table, actions);
    VBox.setVgrow(table, Priority.ALWAYS);

    preview.widthProperty().addListener((o, ov, nv) -> redrawPreview());
    preview.heightProperty().addListener((o, ov, nv) -> redrawPreview());
    preview.setOnMouseClicked(e -> {
      int idx = hitTestPreviewIndex(e.getX(), e.getY());
      if (idx >= 0 && idx < rows.size()) {
        table.getSelectionModel().select(idx);
      }
    });
    BorderPane previewPane = new BorderPane(preview);
    previewPane.setStyle("-fx-background-color: linear-gradient(to bottom, #0f141d, #080b10); -fx-border-color: #2a2f3a;");
    BorderPane.setMargin(preview, new Insets(8));
    preview.setWidth(520);
    preview.setHeight(320);

    SplitPane split = new SplitPane(tablePane, previewPane);
    split.setDividerPositions(0.58);
    setCenter(split);
  }

  private void buildColumns() {
    TableColumn<MenuItemRow, String> idCol = new TableColumn<>("ID");
    idCol.setCellValueFactory(v -> v.getValue().idProperty());
    idCol.setCellFactory(TextFieldTableCell.forTableColumn());
    idCol.setOnEditCommit(e -> e.getRowValue().setId(normalize(e.getNewValue(), "")));

    TableColumn<MenuItemRow, String> labelCol = new TableColumn<>("Label");
    labelCol.setCellValueFactory(v -> v.getValue().labelProperty());
    labelCol.setCellFactory(TextFieldTableCell.forTableColumn());
    labelCol.setOnEditCommit(e -> e.getRowValue().setLabel(normalize(e.getNewValue(), "")));

    TableColumn<MenuItemRow, String> styleCol = new TableColumn<>("Style");
    styleCol.setCellValueFactory(v -> v.getValue().styleProperty());
    styleCol.setCellFactory(TextFieldTableCell.forTableColumn());
    styleCol.setOnEditCommit(e -> e.getRowValue().setStyle(normalize(e.getNewValue(), "")));

    TableColumn<MenuItemRow, Boolean> enabledCol = new TableColumn<>("Enabled");
    enabledCol.setCellValueFactory(v -> v.getValue().enabledProperty());
    enabledCol.setCellFactory(CheckBoxTableCell.forTableColumn(enabledCol));

    TableColumn<MenuItemRow, MenuActionType> actionCol = new TableColumn<>("Action");
    actionCol.setCellValueFactory(v -> v.getValue().actionProperty());
    ObservableList<MenuActionType> actionValues = FXCollections.observableArrayList(MenuActionType.values());
    actionCol.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<>() {
      @Override
      public String toString(MenuActionType object) {
        if (object == null) return "";
        return canonicalActionName(object);
      }

      @Override
      public MenuActionType fromString(String string) {
        return MenuActionType.parse(string);
      }
    }, actionValues));
    actionCol.setOnEditCommit(e -> e.getRowValue().setAction(e.getNewValue()));

    TableColumn<MenuItemRow, String> targetCol = new TableColumn<>("Target");
    targetCol.setCellValueFactory(v -> v.getValue().targetProperty());
    targetCol.setCellFactory(TextFieldTableCell.forTableColumn());
    targetCol.setOnEditCommit(e -> e.getRowValue().setTarget(normalize(e.getNewValue(), "")));

    table.getColumns().setAll(idCol, labelCol, styleCol, enabledCol, actionCol, targetCol);
  }

  private void registerTopListeners() {
    tfTitle.textProperty().addListener((o, ov, nv) -> onUiChanged());
    tfHints.textProperty().addListener((o, ov, nv) -> onUiChanged());
    cbWrap.selectedProperty().addListener((o, ov, nv) -> onUiChanged());
    cbLayout.getEditor().textProperty().addListener((o, ov, nv) -> onUiChanged());
    cbDefaultStyle.getEditor().textProperty().addListener((o, ov, nv) -> onUiChanged());
  }

  private void setDefaults() {
    tfTitle.setText("");
    tfHints.setText("Select: Enter    Back: Esc");
    cbLayout.setEditable(true);
    cbLayout.getEditor().setText("default");
    cbDefaultStyle.setEditable(true);
    cbDefaultStyle.getEditor().setText("default");
    cbWrap.setSelected(true);
    rows.clear();
    MenuItemRow r1 = defaultRow("new_game", MenuActionType.NEW_GAME, null);
    MenuItemRow r2 = defaultRow("load", MenuActionType.LOAD_MENU, null);
    MenuItemRow r3 = defaultRow("settings", MenuActionType.SETTINGS_MENU, null);
    MenuItemRow r4 = defaultRow("quit", MenuActionType.QUIT, null);
    rows.addAll(r1, r2, r3, r4);
    table.setItems(rows);
  }

  private MenuItemRow defaultRow(String id, MenuActionType action, String target) {
    MenuItemRow row = new MenuItemRow(id, "", "", "", true, action, target);
    attachRowListeners(row);
    return row;
  }

  private void attachRowListeners(MenuItemRow row) {
    row.idProperty().addListener((o, ov, nv) -> onUiChanged());
    row.labelProperty().addListener((o, ov, nv) -> onUiChanged());
    row.styleProperty().addListener((o, ov, nv) -> onUiChanged());
    row.iconProperty().addListener((o, ov, nv) -> onUiChanged());
    row.enabledProperty().addListener((o, ov, nv) -> onUiChanged());
    row.actionProperty().addListener((o, ov, nv) -> onUiChanged());
    row.targetProperty().addListener((o, ov, nv) -> onUiChanged());
  }

  private void addRow() {
    String base = "item";
    int n = rows.size() + 1;
    String id = base + "_" + n;
    while (hasId(id)) {
      n++;
      id = base + "_" + n;
    }
    MenuItemRow row = defaultRow(id, MenuActionType.NOOP, null);
    rows.add(row);
    table.getSelectionModel().select(row);
    onUiChanged();
  }

  private void removeRow() {
    int idx = table.getSelectionModel().getSelectedIndex();
    if (idx < 0 || idx >= rows.size()) return;
    rows.remove(idx);
    int next = Math.max(0, Math.min(idx, rows.size() - 1));
    if (!rows.isEmpty()) table.getSelectionModel().select(next);
    onUiChanged();
  }

  private void moveSelected(int delta) {
    int idx = table.getSelectionModel().getSelectedIndex();
    int next = idx + delta;
    if (idx < 0 || idx >= rows.size() || next < 0 || next >= rows.size()) return;
    MenuItemRow row = rows.remove(idx);
    rows.add(next, row);
    table.getSelectionModel().select(next);
    onUiChanged();
  }

  private void normalizeIds() {
    Set<String> used = new LinkedHashSet<>();
    for (int i = 0; i < rows.size(); i++) {
      MenuItemRow row = rows.get(i);
      String seed = sanitizeId(row.getId());
      if (seed.isBlank()) seed = "item_" + (i + 1);
      String id = seed;
      int n = 2;
      while (used.contains(id)) {
        id = seed + "_" + n++;
      }
      used.add(id);
      row.setId(id);
    }
    onUiChanged();
  }

  private void onUiChanged() {
    if (suppressEvents) return;
    validateState();
    redrawPreview();
    emitText();
  }

  private void validateState() {
    List<String> warnings = new ArrayList<>();
    Set<String> ids = new LinkedHashSet<>();
    List<String> knownMenus = discoverMenuIds();
    for (int i = 0; i < rows.size(); i++) {
      MenuItemRow row = rows.get(i);
      String id = normalize(row.getId(), "");
      if (id.isBlank()) {
        warnings.add("Row " + (i + 1) + ": item id is empty");
      } else if (!ids.add(id)) {
        warnings.add("Duplicate item id: " + id);
      }

      MenuActionType action = row.getAction();
      if (action == MenuActionType.OPEN_MENU) {
        String target = normalize(row.getTarget(), "");
        if (target.isBlank()) {
          warnings.add("Item '" + id + "': OPEN_MENU requires target");
        } else if (!knownMenus.isEmpty() && !knownMenus.contains(target)) {
          warnings.add("Item '" + id + "': target menu '" + target + "' not found");
        }
      }
      if (action == MenuActionType.RUN_SCRIPT && normalize(row.getTarget(), "").isBlank()) {
        warnings.add("Item '" + id + "': RUN_SCRIPT requires script target");
      }
    }

    if (warnings.isEmpty()) {
      validation.setText("No issues detected.");
      validation.setTextFill(Color.web("#8bcf98"));
    } else {
      String joined = String.join(" | ", warnings);
      validation.setText(joined);
      validation.setTextFill(Color.web("#f2b26f"));
    }
  }

  private void redrawPreview() {
    GraphicsContext g = preview.getGraphicsContext2D();
    double w = preview.getWidth();
    double h = preview.getHeight();
    g.setFill(Color.web("#0c1118"));
    g.fillRect(0, 0, w, h);

    g.setStroke(Color.rgb(255, 255, 255, 0.06));
    for (int i = 1; i < 6; i++) {
      double y = h * i / 6.0;
      g.strokeLine(0, y, w, y);
    }

    String title = normalize(tfTitle.getText(), "").isBlank() ? titleize(screenIdHint) : tfTitle.getText().trim();
    String hints = normalize(tfHints.getText(), "").isBlank() ? "Select: Enter    Back: Esc" : tfHints.getText().trim();

    g.setFill(Color.WHITE);
    g.setFont(Font.font("Arial", FontWeight.BOLD, 28));
    double titleW = textWidth(g, title);
    g.fillText(title, (w - titleW) / 2.0, 52);

    double lineHeight = 34;
    double startY = 130;
    for (int i = 0; i < rows.size(); i++) {
      MenuItemRow row = rows.get(i);
      String text = displayLabel(row);
      boolean selected = (i == previewSelected);
      boolean enabled = row.isEnabled();
      String prefix = selected ? "> " : "  ";
      if (!enabled) prefix = "- ";
      text = prefix + text;

      Color color = enabled ? (selected ? Color.web("#ffe680") : Color.web("#d6d6d6")) : Color.web("#7d7d7d");
      g.setFill(color);
      g.setFont(Font.font("Arial", 20));
      double tw = textWidth(g, text);
      g.fillText(text, (w - tw) / 2.0, startY + i * lineHeight);
    }

    g.setFill(Color.web("#cfd3db"));
    g.setFont(Font.font("Arial", 14));
    double hintsW = textWidth(g, hints);
    g.fillText(hints, (w - hintsW) / 2.0, h - 18);
  }

  private int hitTestPreviewIndex(double x, double y) {
    double lineHeight = 34;
    double startY = 130;
    int idx = (int) Math.floor((y - startY + 18) / lineHeight);
    if (idx < 0 || idx >= rows.size()) return -1;
    return idx;
  }

  private void emitText() {
    if (onMenuTextChanged == null) return;
    onMenuTextChanged.accept(serialize());
  }

  private String serialize() {
    StringBuilder out = new StringBuilder();
    out.append("# Menu screen definition").append(System.lineSeparator());
    out.append("# Edited via JVN visual menu editor").append(System.lineSeparator());

    String title = normalize(tfTitle.getText(), "");
    String hints = normalize(tfHints.getText(), "");
    String layout = normalize(cbLayout.getEditor().getText(), "default");
    String defaultStyle = normalize(cbDefaultStyle.getEditor().getText(), "default");
    boolean wrap = cbWrap.isSelected();

    if (!title.isBlank()) out.append("titleText=").append(escapeValue(title)).append(System.lineSeparator());
    if (!hints.isBlank()) out.append("hintsText=").append(escapeValue(hints)).append(System.lineSeparator());
    out.append("layout=").append(escapeValue(layout)).append(System.lineSeparator());
    out.append("defaultItemStyle=").append(escapeValue(defaultStyle)).append(System.lineSeparator());
    out.append("wrapSelection=").append(wrap).append(System.lineSeparator());

    List<ResolvedItem> resolved = new ArrayList<>();
    Set<String> usedIds = new LinkedHashSet<>();
    for (MenuItemRow row : rows) {
      String base = sanitizeId(row.getId());
      if (base.isBlank()) continue;
      String id = base;
      int n = 2;
      while (usedIds.contains(id)) id = base + "_" + n++;
      usedIds.add(id);
      resolved.add(new ResolvedItem(id, row));
    }
    List<String> ids = new ArrayList<>();
    for (ResolvedItem item : resolved) ids.add(item.id());
    out.append("items=").append(String.join(",", ids)).append(System.lineSeparator());

    for (ResolvedItem item : resolved) {
      String id = item.id();
      MenuItemRow row = item.row();
      String prefix = "item." + id + ".";
      String label = normalize(row.getLabel(), "");
      String style = normalize(row.getStyle(), "");
      String icon = normalize(row.getIcon(), "");
      String action = canonicalActionName(row.getAction());
      String target = normalize(row.getTarget(), "");

      if (!label.isBlank()) out.append(prefix).append("label=").append(escapeValue(label)).append(System.lineSeparator());
      if (!style.isBlank()) out.append(prefix).append("style=").append(escapeValue(style)).append(System.lineSeparator());
      if (!icon.isBlank()) out.append(prefix).append("icon=").append(escapeValue(icon)).append(System.lineSeparator());
      out.append(prefix).append("enabled=").append(row.isEnabled()).append(System.lineSeparator());
      out.append(prefix).append("action=").append(escapeValue(action)).append(System.lineSeparator());
      if (!target.isBlank()) out.append(prefix).append("target=").append(escapeValue(target)).append(System.lineSeparator());
      if (!row.extras.isEmpty()) {
        List<String> keys = new ArrayList<>(row.extras.keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
          String value = row.extras.get(key);
          if (value == null) value = "";
          out.append(prefix).append(key).append("=").append(escapeValue(value)).append(System.lineSeparator());
        }
      }
    }

    List<String> extraTop = new ArrayList<>();
    for (String key : topLevelExtras.stringPropertyNames()) extraTop.add(key);
    extraTop.sort(String::compareTo);
    if (!extraTop.isEmpty()) {
      out.append(System.lineSeparator()).append("# Additional custom keys").append(System.lineSeparator());
      for (String key : extraTop) {
        out.append(key).append("=").append(escapeValue(topLevelExtras.getProperty(key, ""))).append(System.lineSeparator());
      }
    }
    return out.toString();
  }

  private record ResolvedItem(String id, MenuItemRow row) {}

  private void refreshSuggestions() {
    List<String> layouts = discoverIds(new File(projectRoot, "config/menu/layouts"), ".layout", ".properties");
    List<String> styles = discoverIds(new File(projectRoot, "config/menu/styles"), ".style", ".properties");
    if (!layouts.contains("default")) layouts.add(0, "default");
    if (!styles.contains("default")) styles.add(0, "default");

    String currentLayout = cbLayout.getEditor().getText();
    String currentStyle = cbDefaultStyle.getEditor().getText();
    cbLayout.getItems().setAll(layouts);
    cbDefaultStyle.getItems().setAll(styles);
    cbLayout.getEditor().setText(currentLayout == null ? "default" : currentLayout);
    cbDefaultStyle.getEditor().setText(currentStyle == null ? "default" : currentStyle);
  }

  private List<String> discoverMenuIds() {
    return discoverIds(new File(projectRoot, "config/menu/menus"), ".menu", ".properties");
  }

  private static List<String> discoverIds(File directory, String... suffixes) {
    List<String> out = new ArrayList<>();
    if (directory == null || !directory.exists() || !directory.isDirectory()) return out;
    File[] files = directory.listFiles();
    if (files == null) return out;
    for (File f : files) {
      if (!f.isFile()) continue;
      String name = f.getName();
      for (String suffix : suffixes) {
        if (name.endsWith(suffix) && name.length() > suffix.length()) {
          out.add(name.substring(0, name.length() - suffix.length()));
          break;
        }
      }
    }
    out.sort(String::compareTo);
    return out;
  }

  private boolean hasId(String id) {
    String key = sanitizeId(id);
    for (MenuItemRow row : rows) {
      if (sanitizeId(row.getId()).equals(key)) return true;
    }
    return false;
  }

  private static boolean isKnownTopLevelKey(String key) {
    return TOP_LEVEL_KEYS.contains(key);
  }

  private static boolean isItemKey(String key) {
    if (key == null) return false;
    if (!key.startsWith("item.")) return false;
    int dot = key.indexOf('.', 5);
    if (dot <= 5 || dot >= key.length() - 1) return false;
    return true;
  }

  private static List<String> collectItemIdsFromProperties(Properties p) {
    Set<String> ids = new LinkedHashSet<>();
    for (String key : p.stringPropertyNames()) {
      if (!key.startsWith("item.")) continue;
      int dot = key.indexOf('.', 5);
      if (dot <= 5) continue;
      String id = key.substring(5, dot).trim();
      if (!id.isEmpty()) ids.add(id);
    }
    return new ArrayList<>(ids);
  }

  private static List<String> parseCsv(String raw) {
    List<String> out = new ArrayList<>();
    if (raw == null || raw.isBlank()) return out;
    for (String part : raw.split(",")) {
      String t = normalize(part, "");
      if (!t.isBlank()) out.add(t);
    }
    return out;
  }

  private String displayLabel(MenuItemRow row) {
    String explicit = normalize(row.getLabel(), "");
    if (!explicit.isBlank()) return explicit;
    return switch (row.getAction()) {
      case NEW_GAME -> "New Game";
      case LOAD_MENU -> "Load";
      case SAVE_MENU -> "Save";
      case SETTINGS_MENU -> "Settings";
      case QUIT -> "Quit";
      case BACK -> "Back";
      case MAIN_MENU -> "Main Menu";
      case OPEN_MENU -> titleize(normalize(row.getTarget(), row.getId()));
      case RUN_SCRIPT -> "Run Script";
      case NOOP -> titleize(row.getId());
    };
  }

  private static String canonicalActionName(MenuActionType action) {
    if (action == null) return "noop";
    return switch (action) {
      case NEW_GAME -> "new_game";
      case LOAD_MENU -> "load_menu";
      case SAVE_MENU -> "save_menu";
      case SETTINGS_MENU -> "settings_menu";
      case MAIN_MENU -> "main_menu";
      case OPEN_MENU -> "open_menu";
      case RUN_SCRIPT -> "run_script";
      case BACK -> "back";
      case QUIT -> "quit";
      case NOOP -> "noop";
    };
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isEmpty() ? fallback : t;
  }

  private static String sanitizeId(String value) {
    String s = normalize(value, "");
    if (s.isBlank()) return "";
    s = s.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    s = s.replaceAll("[^a-z0-9_]", "");
    s = s.replaceAll("_+", "_");
    if (s.startsWith("_")) s = s.substring(1);
    if (s.endsWith("_")) s = s.substring(0, s.length() - 1);
    return s;
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    String v = raw.trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "true", "yes", "1" -> true;
      case "false", "no", "0" -> false;
      default -> fallback;
    };
  }

  private static String escapeValue(String value) {
    if (value == null) return "";
    return value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "");
  }

  private static String titleize(String raw) {
    String s = normalize(raw, "item").replace('_', ' ').replace('-', ' ');
    if (s.isBlank()) return "item";
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        upper = true;
        out.append(c);
      } else if (upper) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static void addRow(GridPane g, int row, String label, javafx.scene.Node node) {
    Label l = new Label(label);
    GridPane.setHgrow(node, Priority.ALWAYS);
    if (node instanceof TextField tf) tf.setMaxWidth(Double.MAX_VALUE);
    if (node instanceof ComboBox<?> cb) cb.setMaxWidth(Double.MAX_VALUE);
    g.add(l, 0, row);
    g.add(node, 1, row);
  }

  private static double textWidth(GraphicsContext g, String text) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
    helper.setFont(g.getFont());
    return helper.getLayoutBounds().getWidth();
  }

  private static final class MenuItemRow {
    private final StringProperty id = new SimpleStringProperty("");
    private final StringProperty label = new SimpleStringProperty("");
    private final StringProperty style = new SimpleStringProperty("");
    private final StringProperty icon = new SimpleStringProperty("");
    private final BooleanProperty enabled = new SimpleBooleanProperty(true);
    private final ObjectProperty<MenuActionType> action = new SimpleObjectProperty<>(MenuActionType.NOOP);
    private final StringProperty target = new SimpleStringProperty("");
    private final Map<String, String> extras = new LinkedHashMap<>();

    private MenuItemRow(String id, String label, String style, String icon, boolean enabled, MenuActionType action, String target) {
      setId(id);
      setLabel(label);
      setStyle(style);
      setIcon(icon);
      setEnabled(enabled);
      setAction(action == null ? MenuActionType.NOOP : action);
      setTarget(target);
    }

    public String getId() { return id.get(); }
    public StringProperty idProperty() { return id; }
    public void setId(String id) { this.id.set(id == null ? "" : id); }

    public String getLabel() { return label.get(); }
    public StringProperty labelProperty() { return label; }
    public void setLabel(String label) { this.label.set(label == null ? "" : label); }

    public String getStyle() { return style.get(); }
    public StringProperty styleProperty() { return style; }
    public void setStyle(String style) { this.style.set(style == null ? "" : style); }

    public String getIcon() { return icon.get(); }
    public StringProperty iconProperty() { return icon; }
    public void setIcon(String icon) { this.icon.set(icon == null ? "" : icon); }

    public boolean isEnabled() { return enabled.get(); }
    public BooleanProperty enabledProperty() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled.set(enabled); }

    public MenuActionType getAction() { return action.get(); }
    public ObjectProperty<MenuActionType> actionProperty() { return action; }
    public void setAction(MenuActionType action) { this.action.set(action == null ? MenuActionType.NOOP : action); }

    public String getTarget() { return target.get(); }
    public StringProperty targetProperty() { return target; }
    public void setTarget(String target) { this.target.set(target == null ? "" : target); }
  }
}
