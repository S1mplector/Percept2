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
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;
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
      "label", "style", "icon", "enabled", "action", "target",
      "bgAsset", "bgSelectedAsset", "bgDisabledAsset",
      "boundsX", "boundsY", "boundsWidth", "boundsHeight",
      "slotPreviewEnabled",
      "slotPreviewPlaceholderAsset",
      "slotPreviewFrameAsset",
      "slotPreviewX",
      "slotPreviewY",
      "slotPreviewWidth",
      "slotPreviewHeight"
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
  private final java.util.Map<String, Image> imageCache = new LinkedHashMap<>();

  private final Properties topLevelExtras = new Properties();
  private Consumer<String> onMenuTextChanged;
  private boolean suppressEvents = false;
  private String lastLoadedText = "";
  private String lastEmittedText = "";
  private File projectRoot;
  private String screenIdHint = "main";
  private int previewSelected = 0;
  private Rect[] previewRects = new Rect[0];
  private int dragRowIndex = -1;
  private DragMode dragMode = DragMode.NONE;
  private double dragStartMouseX;
  private double dragStartMouseY;
  private Double dragStartX;
  private Double dragStartY;
  private Double dragStartW;
  private Double dragStartH;

  private enum DragMode { NONE, MOVE, RESIZE }

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
    imageCache.clear();
    refreshSuggestions();
    validateState();
    redrawPreview();
  }

  public void setScreenIdHint(String id) {
    if (id != null && !id.isBlank()) this.screenIdHint = id.trim();
  }

  public void setMenuText(String text) {
    String normalizedInput = normalizeLineEndings(text);
    if (normalizedInput.equals(lastLoadedText)) return;
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
            action.target(),
            p.getProperty(prefix + "bgAsset", ""),
            p.getProperty(prefix + "bgSelectedAsset", ""),
            p.getProperty(prefix + "bgDisabledAsset", ""),
            parseOptionalDouble(p.getProperty(prefix + "boundsX")),
            parseOptionalDouble(p.getProperty(prefix + "boundsY")),
            parseOptionalDouble(p.getProperty(prefix + "boundsWidth")),
            parseOptionalDouble(p.getProperty(prefix + "boundsHeight")),
            parseBoolean(p.getProperty(prefix + "slotPreviewEnabled"), isSlotTemplateId(key)),
            p.getProperty(prefix + "slotPreviewPlaceholderAsset", ""),
            p.getProperty(prefix + "slotPreviewFrameAsset", ""),
            parseOptionalDouble(p.getProperty(prefix + "slotPreviewX")),
            parseOptionalDouble(p.getProperty(prefix + "slotPreviewY")),
            parseOptionalDouble(p.getProperty(prefix + "slotPreviewWidth")),
            parseOptionalDouble(p.getProperty(prefix + "slotPreviewHeight"))
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
    lastLoadedText = normalizedInput;
    lastEmittedText = normalizeLineEndings(serialize());
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
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    buildColumns();
    table.setItems(rows);
    table.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      previewSelected = Math.max(0, nv == null ? 0 : nv.intValue());
      redrawPreview();
    });

    FlowPane actions = new FlowPane(Orientation.HORIZONTAL, 6, 6);
    actions.getStyleClass().add("layout-studio-action-bar");
    actions.setPrefWrapLength(560);

    Button bAdd = new Button("Add Item");
    Button bRemove = new Button("Remove");
    Button bUp = new Button("Up");
    Button bDown = new Button("Down");
    Button bNormalize = new Button("Normalize IDs");
    Button bAssignBg = new Button("Assign BG...");
    Button bAssignPreviewPlaceholder = new Button("Preview Placeholder...");
    Button bAssignPreviewFrame = new Button("Preview Frame...");
    Button bEnableSlotPreview = new Button("Enable Slot Preview");
    Button bClearBounds = new Button("Clear Bounds");
    List<Button> actionButtons = List.of(
        bAdd, bRemove, bUp, bDown, bNormalize, bAssignBg, bAssignPreviewPlaceholder, bAssignPreviewFrame, bEnableSlotPreview, bClearBounds
    );
    for (Button actionButton : actionButtons) {
      actionButton.getStyleClass().add("layout-studio-action-button");
      actionButton.setMinWidth(Region.USE_PREF_SIZE);
      actionButton.setTooltip(new Tooltip(actionButton.getText()));
    }

    bAdd.setOnAction(e -> addRow());
    bRemove.setOnAction(e -> removeRow());
    bUp.setOnAction(e -> moveSelected(-1));
    bDown.setOnAction(e -> moveSelected(1));
    bNormalize.setOnAction(e -> normalizeIds());
    bAssignBg.setOnAction(e -> assignBgToSelection());
    bAssignPreviewPlaceholder.setOnAction(e -> assignSlotPreviewPlaceholderToSelection());
    bAssignPreviewFrame.setOnAction(e -> assignSlotPreviewFrameToSelection());
    bEnableSlotPreview.setOnAction(e -> enableSlotPreviewForSelection());
    bClearBounds.setOnAction(e -> clearBoundsForSelection());
    actions.getChildren().addAll(
        bAdd, bRemove, bUp, bDown, bNormalize, bAssignBg, bAssignPreviewPlaceholder, bAssignPreviewFrame, bEnableSlotPreview, bClearBounds
    );

    table.getStyleClass().add("layout-studio-table");
    VBox tablePane = new VBox(6, table, actions);
    actions.prefWrapLengthProperty().bind(tablePane.widthProperty().subtract(12));
    VBox.setVgrow(table, Priority.ALWAYS);

    preview.widthProperty().addListener((o, ov, nv) -> redrawPreview());
    preview.heightProperty().addListener((o, ov, nv) -> redrawPreview());
    installPreviewInteractions();
    BorderPane previewPane = new BorderPane(preview);
    previewPane.getStyleClass().add("layout-studio-preview-host");
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

    TableColumn<MenuItemRow, String> iconCol = new TableColumn<>("Icon");
    iconCol.setCellValueFactory(v -> v.getValue().iconProperty());
    iconCol.setCellFactory(TextFieldTableCell.forTableColumn());
    iconCol.setOnEditCommit(e -> e.getRowValue().setIcon(normalize(e.getNewValue(), "")));

    TableColumn<MenuItemRow, String> bgCol = new TableColumn<>("BG");
    bgCol.setCellValueFactory(v -> v.getValue().bgAssetProperty());
    bgCol.setCellFactory(TextFieldTableCell.forTableColumn());
    bgCol.setOnEditCommit(e -> e.getRowValue().setBgAsset(normalize(e.getNewValue(), "")));

    TableColumn<MenuItemRow, String> bgSelCol = new TableColumn<>("BG Selected");
    bgSelCol.setCellValueFactory(v -> v.getValue().bgSelectedAssetProperty());
    bgSelCol.setCellFactory(TextFieldTableCell.forTableColumn());
    bgSelCol.setOnEditCommit(e -> e.getRowValue().setBgSelectedAsset(normalize(e.getNewValue(), "")));

    TableColumn<MenuItemRow, String> bgDisCol = new TableColumn<>("BG Disabled");
    bgDisCol.setCellValueFactory(v -> v.getValue().bgDisabledAssetProperty());
    bgDisCol.setCellFactory(TextFieldTableCell.forTableColumn());
    bgDisCol.setOnEditCommit(e -> e.getRowValue().setBgDisabledAsset(normalize(e.getNewValue(), "")));

    TableColumn<MenuItemRow, Boolean> slotPreviewEnabledCol = new TableColumn<>("Slot Prev");
    slotPreviewEnabledCol.setCellValueFactory(v -> v.getValue().slotPreviewEnabledProperty());
    slotPreviewEnabledCol.setCellFactory(CheckBoxTableCell.forTableColumn(slotPreviewEnabledCol));

    TableColumn<MenuItemRow, String> slotPreviewPlaceholderCol = new TableColumn<>("Preview Placeholder");
    slotPreviewPlaceholderCol.setCellValueFactory(v -> v.getValue().slotPreviewPlaceholderAssetProperty());
    slotPreviewPlaceholderCol.setCellFactory(TextFieldTableCell.forTableColumn());
    slotPreviewPlaceholderCol.setOnEditCommit(e -> e.getRowValue().setSlotPreviewPlaceholderAsset(normalize(e.getNewValue(), "")));

    TableColumn<MenuItemRow, String> slotPreviewFrameCol = new TableColumn<>("Preview Frame");
    slotPreviewFrameCol.setCellValueFactory(v -> v.getValue().slotPreviewFrameAssetProperty());
    slotPreviewFrameCol.setCellFactory(TextFieldTableCell.forTableColumn());
    slotPreviewFrameCol.setOnEditCommit(e -> e.getRowValue().setSlotPreviewFrameAsset(normalize(e.getNewValue(), "")));

    StringConverter<Double> doubleStringConverter = new StringConverter<>() {
      @Override
      public String toString(Double object) {
        if (object == null) return "";
        if (!Double.isFinite(object)) return "";
        if (Math.rint(object) == object) return Long.toString(Math.round(object));
        return String.format(Locale.ROOT, "%.4f", object)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
      }

      @Override
      public Double fromString(String string) {
        if (string == null || string.isBlank()) return null;
        try {
          return Double.parseDouble(string.trim());
        } catch (Exception ignored) {
          return null;
        }
      }
    };

    TableColumn<MenuItemRow, Double> boundsXCol = new TableColumn<>("X");
    boundsXCol.setCellValueFactory(v -> v.getValue().boundsXProperty());
    boundsXCol.setCellFactory(TextFieldTableCell.forTableColumn(doubleStringConverter));
    boundsXCol.setOnEditCommit(e -> e.getRowValue().setBoundsX(e.getNewValue()));

    TableColumn<MenuItemRow, Double> boundsYCol = new TableColumn<>("Y");
    boundsYCol.setCellValueFactory(v -> v.getValue().boundsYProperty());
    boundsYCol.setCellFactory(TextFieldTableCell.forTableColumn(doubleStringConverter));
    boundsYCol.setOnEditCommit(e -> e.getRowValue().setBoundsY(e.getNewValue()));

    TableColumn<MenuItemRow, Double> boundsWCol = new TableColumn<>("W");
    boundsWCol.setCellValueFactory(v -> v.getValue().boundsWProperty());
    boundsWCol.setCellFactory(TextFieldTableCell.forTableColumn(doubleStringConverter));
    boundsWCol.setOnEditCommit(e -> e.getRowValue().setBoundsW(e.getNewValue()));

    TableColumn<MenuItemRow, Double> boundsHCol = new TableColumn<>("H");
    boundsHCol.setCellValueFactory(v -> v.getValue().boundsHProperty());
    boundsHCol.setCellFactory(TextFieldTableCell.forTableColumn(doubleStringConverter));
    boundsHCol.setOnEditCommit(e -> e.getRowValue().setBoundsH(e.getNewValue()));

    TableColumn<MenuItemRow, Double> slotPreviewXCol = new TableColumn<>("Prev X");
    slotPreviewXCol.setCellValueFactory(v -> v.getValue().slotPreviewXProperty());
    slotPreviewXCol.setCellFactory(TextFieldTableCell.forTableColumn(doubleStringConverter));
    slotPreviewXCol.setOnEditCommit(e -> e.getRowValue().setSlotPreviewX(e.getNewValue()));

    TableColumn<MenuItemRow, Double> slotPreviewYCol = new TableColumn<>("Prev Y");
    slotPreviewYCol.setCellValueFactory(v -> v.getValue().slotPreviewYProperty());
    slotPreviewYCol.setCellFactory(TextFieldTableCell.forTableColumn(doubleStringConverter));
    slotPreviewYCol.setOnEditCommit(e -> e.getRowValue().setSlotPreviewY(e.getNewValue()));

    TableColumn<MenuItemRow, Double> slotPreviewWCol = new TableColumn<>("Prev W");
    slotPreviewWCol.setCellValueFactory(v -> v.getValue().slotPreviewWProperty());
    slotPreviewWCol.setCellFactory(TextFieldTableCell.forTableColumn(doubleStringConverter));
    slotPreviewWCol.setOnEditCommit(e -> e.getRowValue().setSlotPreviewW(e.getNewValue()));

    TableColumn<MenuItemRow, Double> slotPreviewHCol = new TableColumn<>("Prev H");
    slotPreviewHCol.setCellValueFactory(v -> v.getValue().slotPreviewHProperty());
    slotPreviewHCol.setCellFactory(TextFieldTableCell.forTableColumn(doubleStringConverter));
    slotPreviewHCol.setOnEditCommit(e -> e.getRowValue().setSlotPreviewH(e.getNewValue()));

    table.getColumns().setAll(
        idCol, labelCol, styleCol, enabledCol, actionCol, targetCol,
        iconCol, bgCol, bgSelCol, bgDisCol,
        slotPreviewEnabledCol, slotPreviewPlaceholderCol, slotPreviewFrameCol,
        boundsXCol, boundsYCol, boundsWCol, boundsHCol,
        slotPreviewXCol, slotPreviewYCol, slotPreviewWCol, slotPreviewHCol
    );
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
    MenuItemRow row = new MenuItemRow(
        id,
        "",
        "",
        "",
        true,
        action,
        target,
        "",
        "",
        "",
        null,
        null,
        null,
        null,
        isSlotTemplateId(id),
        "",
        "",
        null,
        null,
        null,
        null
    );
    attachRowListeners(row);
    return row;
  }

  private void attachRowListeners(MenuItemRow row) {
    row.idProperty().addListener((o, ov, nv) -> onUiChanged());
    row.labelProperty().addListener((o, ov, nv) -> onUiChanged());
    row.styleProperty().addListener((o, ov, nv) -> onUiChanged());
    row.iconProperty().addListener((o, ov, nv) -> onUiChanged());
    row.bgAssetProperty().addListener((o, ov, nv) -> onUiChanged());
    row.bgSelectedAssetProperty().addListener((o, ov, nv) -> onUiChanged());
    row.bgDisabledAssetProperty().addListener((o, ov, nv) -> onUiChanged());
    row.enabledProperty().addListener((o, ov, nv) -> onUiChanged());
    row.actionProperty().addListener((o, ov, nv) -> onUiChanged());
    row.targetProperty().addListener((o, ov, nv) -> onUiChanged());
    row.boundsXProperty().addListener((o, ov, nv) -> onUiChanged());
    row.boundsYProperty().addListener((o, ov, nv) -> onUiChanged());
    row.boundsWProperty().addListener((o, ov, nv) -> onUiChanged());
    row.boundsHProperty().addListener((o, ov, nv) -> onUiChanged());
    row.slotPreviewEnabledProperty().addListener((o, ov, nv) -> onUiChanged());
    row.slotPreviewPlaceholderAssetProperty().addListener((o, ov, nv) -> onUiChanged());
    row.slotPreviewFrameAssetProperty().addListener((o, ov, nv) -> onUiChanged());
    row.slotPreviewXProperty().addListener((o, ov, nv) -> onUiChanged());
    row.slotPreviewYProperty().addListener((o, ov, nv) -> onUiChanged());
    row.slotPreviewWProperty().addListener((o, ov, nv) -> onUiChanged());
    row.slotPreviewHProperty().addListener((o, ov, nv) -> onUiChanged());
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
    if (!rows.isEmpty()) {
      previewSelected = Math.max(0, Math.min(previewSelected, rows.size() - 1));
    } else {
      previewSelected = 0;
    }
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

      int boundsParts = 0;
      if (row.getBoundsX() != null) boundsParts++;
      if (row.getBoundsY() != null) boundsParts++;
      if (row.getBoundsW() != null) boundsParts++;
      if (row.getBoundsH() != null) boundsParts++;
      if (boundsParts > 0 && boundsParts < 4) {
        warnings.add("Item '" + id + "': bounds require X/Y/W/H together");
      }
      if (row.getBoundsW() != null && row.getBoundsW() <= 0) {
        warnings.add("Item '" + id + "': boundsWidth must be > 0");
      }
      if (row.getBoundsH() != null && row.getBoundsH() <= 0) {
        warnings.add("Item '" + id + "': boundsHeight must be > 0");
      }

      int previewBoundsParts = 0;
      if (row.getSlotPreviewX() != null) previewBoundsParts++;
      if (row.getSlotPreviewY() != null) previewBoundsParts++;
      if (row.getSlotPreviewW() != null) previewBoundsParts++;
      if (row.getSlotPreviewH() != null) previewBoundsParts++;
      if (previewBoundsParts > 0 && previewBoundsParts < 4) {
        warnings.add("Item '" + id + "': slot preview bounds require X/Y/W/H together");
      }
      if (row.getSlotPreviewW() != null && row.getSlotPreviewW() <= 0) {
        warnings.add("Item '" + id + "': slotPreviewWidth must be > 0");
      }
      if (row.getSlotPreviewH() != null && row.getSlotPreviewH() <= 0) {
        warnings.add("Item '" + id + "': slotPreviewHeight must be > 0");
      }
    }

    if (warnings.isEmpty()) {
      validation.setText("No issues detected.");
      validation.setTextFill(LayoutStudioPalette.TEXT_SUCCESS);
    } else {
      String joined = String.join(" | ", warnings);
      validation.setText(joined);
      validation.setTextFill(LayoutStudioPalette.TEXT_WARNING);
    }
  }

  private void redrawPreview() {
    GraphicsContext g = preview.getGraphicsContext2D();
    double w = preview.getWidth();
    double h = preview.getHeight();
    g.setFill(LayoutStudioPalette.CANVAS_BACKGROUND);
    g.fillRect(0, 0, w, h);

    g.setStroke(LayoutStudioPalette.GRID_LINE);
    for (int i = 1; i < 6; i++) {
      double y = h * i / 6.0;
      g.strokeLine(0, y, w, y);
    }

    String title = normalize(tfTitle.getText(), "").isBlank() ? titleize(screenIdHint) : tfTitle.getText().trim();
    String hints = normalize(tfHints.getText(), "").isBlank() ? "Select: Enter    Back: Esc" : tfHints.getText().trim();

    g.setFill(LayoutStudioPalette.TEXT_PRIMARY);
    g.setFont(Font.font("Arial", FontWeight.BOLD, 28));
    double titleW = textWidth(g, title);
    g.fillText(title, (w - titleW) / 2.0, 52);

    double lineHeight = 38;
    double startY = 130;
    previewRects = new Rect[rows.size()];
    for (int i = 0; i < rows.size(); i++) {
      MenuItemRow row = rows.get(i);
      String text = displayLabel(row);
      boolean selected = (i == previewSelected);
      boolean enabled = row.isEnabled();
      Rect rect = resolvePreviewRect(row, i, w, h, startY, lineHeight);
      previewRects[i] = rect;
      boolean slotPreviewEnabled = isInlineSlotPreviewEnabled(row);
      Rect slotPreviewRect = slotPreviewEnabled ? resolveSlotPreviewRect(row, rect) : null;
      double reservedRightSpace = slotPreviewRect != null
          ? Math.max(0, rect.x() + rect.w() - slotPreviewRect.x() + 8)
          : 0;

      Image bg = loadPreviewAsset(resolveButtonAsset(row, selected, enabled));
      if (bg != null) {
        g.drawImage(bg, rect.x(), rect.y(), rect.w(), rect.h());
      } else {
        g.setFill(enabled ? (selected ? LayoutStudioPalette.PANEL_FILL_SELECTED : LayoutStudioPalette.PANEL_FILL) : LayoutStudioPalette.PANEL_FILL_DISABLED);
        g.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
        g.setStroke(selected ? LayoutStudioPalette.PANEL_BORDER_SELECTED : LayoutStudioPalette.PANEL_BORDER);
        g.setLineWidth(selected ? 2.0 : 1.1);
        g.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 10, 10);
      }

      String prefix = selected ? "> " : "  ";
      if (!enabled) prefix = "- ";
      text = prefix + text;
      g.setFill(enabled ? (selected ? LayoutStudioPalette.ACCENT_GOLD : LayoutStudioPalette.TEXT_SECONDARY) : LayoutStudioPalette.TEXT_DISABLED);
      g.setFont(Font.font("Arial", 18));
      double tw = textWidth(g, text);
      double leftInset = rect.x() + 16;
      double rightInset = rect.x() + Math.max(16, rect.w() - 16 - reservedRightSpace);
      double x = leftInset + Math.max(0, (rightInset - leftInset - tw) / 2.0);
      g.fillText(text, x, rect.y() + rect.h() * 0.62);

      if (slotPreviewRect != null) {
        drawInlineSlotPreview(g, row, slotPreviewRect, selected);
      }

      if (selected) {
        g.setFill(LayoutStudioPalette.ACCENT_GREEN);
        g.fillOval(rect.x() + rect.w() - 10, rect.y() + rect.h() - 10, 10, 10);
      }
    }

    g.setFill(LayoutStudioPalette.TEXT_MUTED);
    g.setFont(Font.font("Arial", 14));
    double hintsW = textWidth(g, hints);
    g.fillText(hints, (w - hintsW) / 2.0, h - 18);

    if (previewSelected >= 0 && previewSelected < previewRects.length) {
      Rect sel = previewRects[previewSelected];
      drawBoundsTag(g, sel.x() + 8, sel.y() - 8, "Drag to move, handle to resize");
    }
  }

  private int hitTestPreviewIndex(double x, double y) {
    for (int i = previewRects.length - 1; i >= 0; i--) {
      Rect r = previewRects[i];
      if (r != null && r.contains(x, y)) return i;
    }
    return -1;
  }

  private void installPreviewInteractions() {
    preview.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
      int idx = hitTestPreviewIndex(e.getX(), e.getY());
      dragRowIndex = idx;
      dragMode = DragMode.NONE;
      if (idx < 0 || idx >= rows.size()) return;

      table.getSelectionModel().select(idx);
      previewSelected = idx;
      Rect rect = idx < previewRects.length ? previewRects[idx] : null;
      if (rect == null) return;

      dragStartMouseX = e.getX();
      dragStartMouseY = e.getY();
      MenuItemRow row = rows.get(idx);
      ensureBoundsInitialized(row, rect, preview.getWidth(), preview.getHeight());
      dragStartX = row.getBoundsX();
      dragStartY = row.getBoundsY();
      dragStartW = row.getBoundsW();
      dragStartH = row.getBoundsH();

      dragMode = inResizeHandle(rect, e.getX(), e.getY()) ? DragMode.RESIZE : DragMode.MOVE;
      e.consume();
    });

    preview.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
      if (dragRowIndex < 0 || dragRowIndex >= rows.size()) return;
      if (dragMode == DragMode.NONE) return;
      MenuItemRow row = rows.get(dragRowIndex);
      double w = Math.max(1, preview.getWidth());
      double h = Math.max(1, preview.getHeight());
      double dxNorm = (e.getX() - dragStartMouseX) / w;
      double dyNorm = (e.getY() - dragStartMouseY) / h;

      suppressEvents = true;
      if (dragMode == DragMode.MOVE) {
        row.setBoundsX(clamp01((dragStartX == null ? 0 : dragStartX) + dxNorm));
        row.setBoundsY(clamp01((dragStartY == null ? 0 : dragStartY) + dyNorm));
      } else if (dragMode == DragMode.RESIZE) {
        row.setBoundsW(clamp((dragStartW == null ? 0.2 : dragStartW) + dxNorm, 0.05, 1.0));
        row.setBoundsH(clamp((dragStartH == null ? 0.1 : dragStartH) + dyNorm, 0.04, 1.0));
      }
      suppressEvents = false;
      onUiChanged();
      e.consume();
    });

    preview.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
      dragRowIndex = -1;
      dragMode = DragMode.NONE;
    });

    preview.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
      int idx = hitTestPreviewIndex(e.getX(), e.getY());
      if (idx >= 0 && idx < rows.size()) {
        table.getSelectionModel().select(idx);
        previewSelected = idx;
        redrawPreview();
      }
    });
  }

  private void ensureBoundsInitialized(MenuItemRow row, Rect rect, double w, double h) {
    if (row.getBoundsX() != null && row.getBoundsY() != null && row.getBoundsW() != null && row.getBoundsH() != null) return;
    row.setBoundsX(clamp01(rect.x() / Math.max(1, w)));
    row.setBoundsY(clamp01(rect.y() / Math.max(1, h)));
    row.setBoundsW(clamp(rect.w() / Math.max(1, w), 0.05, 1));
    row.setBoundsH(clamp(rect.h() / Math.max(1, h), 0.04, 1));
  }

  private Rect resolvePreviewRect(MenuItemRow row, int index, double w, double h, double startY, double lineHeight) {
    if (row != null && row.getBoundsX() != null && row.getBoundsY() != null && row.getBoundsW() != null && row.getBoundsH() != null) {
      double x = clamp(resolveCoordinate(row.getBoundsX(), w), 0, w - 10);
      double y = clamp(resolveCoordinate(row.getBoundsY(), h), 0, h - 10);
      double rw = clamp(resolveSize(row.getBoundsW(), w), 40, Math.max(40, w - x));
      double rh = clamp(resolveSize(row.getBoundsH(), h), 20, Math.max(20, h - y));
      return new Rect(x, y, rw, rh);
    }
    double defaultW = w * 0.62;
    double x = (w - defaultW) / 2.0;
    double baseline = startY + index * lineHeight;
    double rh = Math.max(30, lineHeight * 0.9);
    double y = baseline - rh * 0.72;
    return new Rect(x, y, defaultW, rh);
  }

  private boolean isInlineSlotPreviewEnabled(MenuItemRow row) {
    if (row == null) return false;
    if (row.isSlotPreviewEnabled()) return true;
    return isSaveOrLoadScreen() && isSlotTemplateId(row.getId());
  }

  private Rect resolveSlotPreviewRect(MenuItemRow row, Rect itemRect) {
    if (row == null || itemRect == null) return null;
    if (row.getSlotPreviewX() != null && row.getSlotPreviewY() != null
        && row.getSlotPreviewW() != null && row.getSlotPreviewH() != null) {
      double x = itemRect.x() + resolveCoordinate(row.getSlotPreviewX(), itemRect.w());
      double y = itemRect.y() + resolveCoordinate(row.getSlotPreviewY(), itemRect.h());
      double w = resolveSize(row.getSlotPreviewW(), itemRect.w());
      double h = resolveSize(row.getSlotPreviewH(), itemRect.h());
      w = clamp(w, 8, Math.max(8, itemRect.w()));
      h = clamp(h, 8, Math.max(8, itemRect.h()));
      x = clamp(x, itemRect.x(), itemRect.x() + Math.max(0, itemRect.w() - w));
      y = clamp(y, itemRect.y(), itemRect.y() + Math.max(0, itemRect.h() - h));
      return new Rect(x, y, w, h);
    }

    double margin = 6;
    double h = clamp(itemRect.h() - margin * 2, 14, Math.max(14, itemRect.h() - margin * 2));
    double w = clamp(Math.min(itemRect.w() * 0.34, h * 1.6), 24, Math.max(24, itemRect.w() - margin * 2));
    double x = itemRect.x() + itemRect.w() - w - margin;
    double y = itemRect.y() + (itemRect.h() - h) / 2.0;
    return new Rect(x, y, w, h);
  }

  private void drawInlineSlotPreview(GraphicsContext g, MenuItemRow row, Rect rect, boolean selected) {
    if (g == null || rect == null || row == null) return;
    g.setFill(Color.rgb(6, 9, 14, 0.92));
    g.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 7, 7);

    Image previewAsset = loadPreviewAsset(normalize(row.getSlotPreviewPlaceholderAsset(), ""));
    if (previewAsset != null) {
      drawImageCover(g, previewAsset, rect);
    } else {
      g.setFill(Color.rgb(36, 40, 52, 0.95));
      g.fillRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 7, 7);
      g.setFill(Color.rgb(215, 222, 235, 0.85));
      g.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
      String text = "Live Preview";
      double tw = textWidth(g, text);
      g.fillText(text, rect.x() + Math.max(6, (rect.w() - tw) / 2.0), rect.y() + rect.h() * 0.56);
    }

    Image frameAsset = loadPreviewAsset(normalize(row.getSlotPreviewFrameAsset(), ""));
    if (frameAsset != null) {
      g.drawImage(frameAsset, rect.x(), rect.y(), rect.w(), rect.h());
    } else {
      g.setStroke(selected ? LayoutStudioPalette.ACCENT_BLUE : LayoutStudioPalette.PANEL_BORDER);
      g.setLineWidth(selected ? 1.8 : 1.0);
      g.strokeRoundRect(rect.x(), rect.y(), rect.w(), rect.h(), 7, 7);
    }
  }

  private void drawImageCover(GraphicsContext g, Image image, Rect rect) {
    if (g == null || image == null || rect == null) return;
    double iw = image.getWidth();
    double ih = image.getHeight();
    if (iw <= 0 || ih <= 0) return;
    double targetRatio = rect.w() / rect.h();
    double imageRatio = iw / ih;
    double sx = 0;
    double sy = 0;
    double sw = iw;
    double sh = ih;
    if (imageRatio > targetRatio) {
      sw = ih * targetRatio;
      sx = (iw - sw) / 2.0;
    } else {
      sh = iw / targetRatio;
      sy = (ih - sh) / 2.0;
    }
    g.drawImage(image, sx, sy, sw, sh, rect.x(), rect.y(), rect.w(), rect.h());
  }

  private String resolveButtonAsset(MenuItemRow row, boolean selected, boolean enabled) {
    if (row == null) return null;
    if (!enabled) return firstNonBlank(row.getBgDisabledAsset(), row.getBgAsset());
    if (selected) return firstNonBlank(row.getBgSelectedAsset(), row.getBgAsset());
    return row.getBgAsset();
  }

  private Image loadPreviewAsset(String path) {
    String p = normalize(path, "");
    if (p.isBlank()) return null;
    Image cached = imageCache.get(p);
    if (cached != null) return cached;
    try {
      File f = resolveAssetFile(p);
      Image image = null;
      if (f != null && f.exists()) {
        image = new Image(f.toURI().toString(), false);
      } else {
        var url = getClass().getClassLoader().getResource(p);
        if (url != null) image = new Image(url.toExternalForm(), false);
      }
      if (image != null && !image.isError()) {
        imageCache.put(p, image);
        return image;
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private void drawBoundsTag(GraphicsContext g, double x, double y, String text) {
    double w = Math.max(120, text.length() * 6.2 + 12);
    g.setFill(LayoutStudioPalette.TAG_BG);
    g.fillRoundRect(x, y - 12, w, 16, 6, 6);
    g.setStroke(LayoutStudioPalette.TAG_BORDER);
    g.strokeRoundRect(x, y - 12, w, 16, 6, 6);
    g.setFill(LayoutStudioPalette.TAG_TEXT);
    g.setFont(Font.font("Arial", FontWeight.BOLD, 10));
    g.fillText(text, x + 6, y);
  }

  private boolean inResizeHandle(Rect rect, double x, double y) {
    double hx = rect.x() + rect.w() - 14;
    double hy = rect.y() + rect.h() - 14;
    return x >= hx && x <= hx + 14 && y >= hy && y <= hy + 14;
  }

  private void assignBgToSelection() {
    MenuItemRow row = table.getSelectionModel().getSelectedItem();
    if (row == null) return;
    String asset = chooseImageAsset("Select Menu Button Background");
    if (asset == null || asset.isBlank()) return;
    row.setBgAsset(asset);
    onUiChanged();
  }

  private void assignSlotPreviewPlaceholderToSelection() {
    MenuItemRow row = table.getSelectionModel().getSelectedItem();
    if (row == null) return;
    String asset = chooseImageAsset("Select Slot Preview Placeholder Asset");
    if (asset == null || asset.isBlank()) return;
    row.setSlotPreviewPlaceholderAsset(asset);
    row.setSlotPreviewEnabled(true);
    onUiChanged();
  }

  private void assignSlotPreviewFrameToSelection() {
    MenuItemRow row = table.getSelectionModel().getSelectedItem();
    if (row == null) return;
    String asset = chooseImageAsset("Select Slot Preview Frame Asset");
    if (asset == null || asset.isBlank()) return;
    row.setSlotPreviewFrameAsset(asset);
    row.setSlotPreviewEnabled(true);
    onUiChanged();
  }

  private void enableSlotPreviewForSelection() {
    MenuItemRow row = table.getSelectionModel().getSelectedItem();
    if (row == null) return;
    row.setSlotPreviewEnabled(true);
    onUiChanged();
  }

  private String chooseImageAsset(String title) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(title);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
        "Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp", "*.svg"));
    if (projectRoot != null && projectRoot.exists()) chooser.setInitialDirectory(projectRoot);
    Window owner = getScene() != null ? getScene().getWindow() : null;
    File selected = chooser.showOpenDialog(owner);
    if (selected == null) return null;
    return toProjectRelativePath(selected);
  }

  private void clearBoundsForSelection() {
    MenuItemRow row = table.getSelectionModel().getSelectedItem();
    if (row == null) return;
    row.setBoundsX(null);
    row.setBoundsY(null);
    row.setBoundsW(null);
    row.setBoundsH(null);
    onUiChanged();
  }

  private void emitText() {
    if (onMenuTextChanged == null) return;
    String text = serialize();
    String normalized = normalizeLineEndings(text);
    if (normalized.equals(lastEmittedText)) return;
    lastEmittedText = normalized;
    onMenuTextChanged.accept(text);
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
      String bgAsset = normalize(row.getBgAsset(), "");
      String bgSelectedAsset = normalize(row.getBgSelectedAsset(), "");
      String bgDisabledAsset = normalize(row.getBgDisabledAsset(), "");
      String slotPreviewPlaceholderAsset = normalize(row.getSlotPreviewPlaceholderAsset(), "");
      String slotPreviewFrameAsset = normalize(row.getSlotPreviewFrameAsset(), "");
      String action = canonicalActionName(row.getAction());
      String target = normalize(row.getTarget(), "");

      if (!label.isBlank()) out.append(prefix).append("label=").append(escapeValue(label)).append(System.lineSeparator());
      if (!style.isBlank()) out.append(prefix).append("style=").append(escapeValue(style)).append(System.lineSeparator());
      if (!icon.isBlank()) out.append(prefix).append("icon=").append(escapeValue(icon)).append(System.lineSeparator());
      if (!bgAsset.isBlank()) out.append(prefix).append("bgAsset=").append(escapeValue(bgAsset)).append(System.lineSeparator());
      if (!bgSelectedAsset.isBlank()) out.append(prefix).append("bgSelectedAsset=").append(escapeValue(bgSelectedAsset)).append(System.lineSeparator());
      if (!bgDisabledAsset.isBlank()) out.append(prefix).append("bgDisabledAsset=").append(escapeValue(bgDisabledAsset)).append(System.lineSeparator());
      if (row.isSlotPreviewEnabled() || isSlotTemplateId(id) || !slotPreviewPlaceholderAsset.isBlank() || !slotPreviewFrameAsset.isBlank()
          || row.getSlotPreviewX() != null || row.getSlotPreviewY() != null || row.getSlotPreviewW() != null || row.getSlotPreviewH() != null) {
        out.append(prefix).append("slotPreviewEnabled=").append(row.isSlotPreviewEnabled()).append(System.lineSeparator());
      }
      if (!slotPreviewPlaceholderAsset.isBlank()) {
        out.append(prefix).append("slotPreviewPlaceholderAsset=").append(escapeValue(slotPreviewPlaceholderAsset)).append(System.lineSeparator());
      }
      if (!slotPreviewFrameAsset.isBlank()) {
        out.append(prefix).append("slotPreviewFrameAsset=").append(escapeValue(slotPreviewFrameAsset)).append(System.lineSeparator());
      }
      out.append(prefix).append("enabled=").append(row.isEnabled()).append(System.lineSeparator());
      out.append(prefix).append("action=").append(escapeValue(action)).append(System.lineSeparator());
      if (!target.isBlank()) out.append(prefix).append("target=").append(escapeValue(target)).append(System.lineSeparator());
      if (row.getBoundsX() != null) out.append(prefix).append("boundsX=").append(formatDouble(row.getBoundsX())).append(System.lineSeparator());
      if (row.getBoundsY() != null) out.append(prefix).append("boundsY=").append(formatDouble(row.getBoundsY())).append(System.lineSeparator());
      if (row.getBoundsW() != null) out.append(prefix).append("boundsWidth=").append(formatDouble(row.getBoundsW())).append(System.lineSeparator());
      if (row.getBoundsH() != null) out.append(prefix).append("boundsHeight=").append(formatDouble(row.getBoundsH())).append(System.lineSeparator());
      if (row.getSlotPreviewX() != null) out.append(prefix).append("slotPreviewX=").append(formatDouble(row.getSlotPreviewX())).append(System.lineSeparator());
      if (row.getSlotPreviewY() != null) out.append(prefix).append("slotPreviewY=").append(formatDouble(row.getSlotPreviewY())).append(System.lineSeparator());
      if (row.getSlotPreviewW() != null) out.append(prefix).append("slotPreviewWidth=").append(formatDouble(row.getSlotPreviewW())).append(System.lineSeparator());
      if (row.getSlotPreviewH() != null) out.append(prefix).append("slotPreviewHeight=").append(formatDouble(row.getSlotPreviewH())).append(System.lineSeparator());
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

  private boolean isSaveOrLoadScreen() {
    String id = normalize(screenIdHint, "").toLowerCase(Locale.ROOT);
    return "save".equals(id) || "load".equals(id);
  }

  private static boolean isSlotTemplateId(String idRaw) {
    String id = normalize(idRaw, "").toLowerCase(Locale.ROOT);
    return "save_slot".equals(id) || "slot".equals(id) || "entry".equals(id) || "new_slot".equals(id) || "new_save".equals(id) || "new".equals(id);
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

  private static Double parseOptionalDouble(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String formatDouble(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private static double resolveCoordinate(double value, double total) {
    return value <= 1.0 ? total * value : value;
  }

  private static double resolveSize(double value, double total) {
    return value <= 1.0 ? Math.max(0, value) * total : value;
  }

  private File resolveAssetFile(String path) {
    if (path == null || path.isBlank()) return null;
    File direct = new File(path);
    if (direct.isAbsolute()) return direct;
    if (projectRoot != null) return new File(projectRoot, path);
    return direct;
  }

  private String toProjectRelativePath(File file) {
    if (file == null) return "";
    if (projectRoot == null) return file.getAbsolutePath().replace('\\', '/');
    try {
      java.nio.file.Path root = projectRoot.toPath().toAbsolutePath().normalize();
      java.nio.file.Path abs = file.toPath().toAbsolutePath().normalize();
      if (abs.startsWith(root)) {
        return root.relativize(abs).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
    }
    return file.getAbsolutePath().replace('\\', '/');
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

  private static String normalizeLineEndings(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
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

  private static double clamp(double value, double min, double max) {
    if (!Double.isFinite(value)) return min;
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private static double clamp01(double value) {
    return clamp(value, 0, 1);
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

  private record Rect(double x, double y, double w, double h) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + w && py >= y && py <= y + h;
    }
  }

  private static final class MenuItemRow {
    private final StringProperty id = new SimpleStringProperty("");
    private final StringProperty label = new SimpleStringProperty("");
    private final StringProperty style = new SimpleStringProperty("");
    private final StringProperty icon = new SimpleStringProperty("");
    private final StringProperty bgAsset = new SimpleStringProperty("");
    private final StringProperty bgSelectedAsset = new SimpleStringProperty("");
    private final StringProperty bgDisabledAsset = new SimpleStringProperty("");
    private final BooleanProperty enabled = new SimpleBooleanProperty(true);
    private final ObjectProperty<MenuActionType> action = new SimpleObjectProperty<>(MenuActionType.NOOP);
    private final StringProperty target = new SimpleStringProperty("");
    private final ObjectProperty<Double> boundsX = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Double> boundsY = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Double> boundsW = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Double> boundsH = new SimpleObjectProperty<>(null);
    private final BooleanProperty slotPreviewEnabled = new SimpleBooleanProperty(false);
    private final StringProperty slotPreviewPlaceholderAsset = new SimpleStringProperty("");
    private final StringProperty slotPreviewFrameAsset = new SimpleStringProperty("");
    private final ObjectProperty<Double> slotPreviewX = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Double> slotPreviewY = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Double> slotPreviewW = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Double> slotPreviewH = new SimpleObjectProperty<>(null);
    private final Map<String, String> extras = new LinkedHashMap<>();

    private MenuItemRow(
        String id,
        String label,
        String style,
        String icon,
        boolean enabled,
        MenuActionType action,
        String target,
        String bgAsset,
        String bgSelectedAsset,
        String bgDisabledAsset,
        Double boundsX,
        Double boundsY,
        Double boundsW,
        Double boundsH,
        boolean slotPreviewEnabled,
        String slotPreviewPlaceholderAsset,
        String slotPreviewFrameAsset,
        Double slotPreviewX,
        Double slotPreviewY,
        Double slotPreviewW,
        Double slotPreviewH
    ) {
      setId(id);
      setLabel(label);
      setStyle(style);
      setIcon(icon);
      setBgAsset(bgAsset);
      setBgSelectedAsset(bgSelectedAsset);
      setBgDisabledAsset(bgDisabledAsset);
      setEnabled(enabled);
      setAction(action == null ? MenuActionType.NOOP : action);
      setTarget(target);
      setBoundsX(boundsX);
      setBoundsY(boundsY);
      setBoundsW(boundsW);
      setBoundsH(boundsH);
      setSlotPreviewEnabled(slotPreviewEnabled);
      setSlotPreviewPlaceholderAsset(slotPreviewPlaceholderAsset);
      setSlotPreviewFrameAsset(slotPreviewFrameAsset);
      setSlotPreviewX(slotPreviewX);
      setSlotPreviewY(slotPreviewY);
      setSlotPreviewW(slotPreviewW);
      setSlotPreviewH(slotPreviewH);
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

    public String getBgAsset() { return bgAsset.get(); }
    public StringProperty bgAssetProperty() { return bgAsset; }
    public void setBgAsset(String value) { this.bgAsset.set(value == null ? "" : value); }

    public String getBgSelectedAsset() { return bgSelectedAsset.get(); }
    public StringProperty bgSelectedAssetProperty() { return bgSelectedAsset; }
    public void setBgSelectedAsset(String value) { this.bgSelectedAsset.set(value == null ? "" : value); }

    public String getBgDisabledAsset() { return bgDisabledAsset.get(); }
    public StringProperty bgDisabledAssetProperty() { return bgDisabledAsset; }
    public void setBgDisabledAsset(String value) { this.bgDisabledAsset.set(value == null ? "" : value); }

    public boolean isEnabled() { return enabled.get(); }
    public BooleanProperty enabledProperty() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled.set(enabled); }

    public MenuActionType getAction() { return action.get(); }
    public ObjectProperty<MenuActionType> actionProperty() { return action; }
    public void setAction(MenuActionType action) { this.action.set(action == null ? MenuActionType.NOOP : action); }

    public String getTarget() { return target.get(); }
    public StringProperty targetProperty() { return target; }
    public void setTarget(String target) { this.target.set(target == null ? "" : target); }

    public Double getBoundsX() { return boundsX.get(); }
    public ObjectProperty<Double> boundsXProperty() { return boundsX; }
    public void setBoundsX(Double value) { this.boundsX.set(value); }

    public Double getBoundsY() { return boundsY.get(); }
    public ObjectProperty<Double> boundsYProperty() { return boundsY; }
    public void setBoundsY(Double value) { this.boundsY.set(value); }

    public Double getBoundsW() { return boundsW.get(); }
    public ObjectProperty<Double> boundsWProperty() { return boundsW; }
    public void setBoundsW(Double value) { this.boundsW.set(value); }

    public Double getBoundsH() { return boundsH.get(); }
    public ObjectProperty<Double> boundsHProperty() { return boundsH; }
    public void setBoundsH(Double value) { this.boundsH.set(value); }

    public boolean isSlotPreviewEnabled() { return slotPreviewEnabled.get(); }
    public BooleanProperty slotPreviewEnabledProperty() { return slotPreviewEnabled; }
    public void setSlotPreviewEnabled(boolean value) { this.slotPreviewEnabled.set(value); }

    public String getSlotPreviewPlaceholderAsset() { return slotPreviewPlaceholderAsset.get(); }
    public StringProperty slotPreviewPlaceholderAssetProperty() { return slotPreviewPlaceholderAsset; }
    public void setSlotPreviewPlaceholderAsset(String value) { this.slotPreviewPlaceholderAsset.set(value == null ? "" : value); }

    public String getSlotPreviewFrameAsset() { return slotPreviewFrameAsset.get(); }
    public StringProperty slotPreviewFrameAssetProperty() { return slotPreviewFrameAsset; }
    public void setSlotPreviewFrameAsset(String value) { this.slotPreviewFrameAsset.set(value == null ? "" : value); }

    public Double getSlotPreviewX() { return slotPreviewX.get(); }
    public ObjectProperty<Double> slotPreviewXProperty() { return slotPreviewX; }
    public void setSlotPreviewX(Double value) { this.slotPreviewX.set(value); }

    public Double getSlotPreviewY() { return slotPreviewY.get(); }
    public ObjectProperty<Double> slotPreviewYProperty() { return slotPreviewY; }
    public void setSlotPreviewY(Double value) { this.slotPreviewY.set(value); }

    public Double getSlotPreviewW() { return slotPreviewW.get(); }
    public ObjectProperty<Double> slotPreviewWProperty() { return slotPreviewW; }
    public void setSlotPreviewW(Double value) { this.slotPreviewW.set(value); }

    public Double getSlotPreviewH() { return slotPreviewH.get(); }
    public ObjectProperty<Double> slotPreviewHProperty() { return slotPreviewH; }
    public void setSlotPreviewH(Double value) { this.slotPreviewH.set(value); }
  }
}
