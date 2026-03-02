package com.jvn.editor.ui;

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

import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.ui.BoundsPointCodec;

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
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

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
  private javafx.scene.layout.StackPane previewHost;
  private final java.util.Map<String, Image> imageCache = new LinkedHashMap<>();

  private final Properties topLevelExtras = new Properties();
  private final TableView<ExtrasEntry> extrasTable = new TableView<>();
  private final ObservableList<ExtrasEntry> extrasRows = FXCollections.observableArrayList();
  private Consumer<String> onMenuTextChanged;
  private boolean suppressEvents = false;
  private String lastLoadedText = "";
  private String lastEmittedText = "";
  private final List<String> parseDiagnostics = new ArrayList<>();
  private final List<String> lineDiagnostics = new ArrayList<>();
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
  private final CheckBox cbSnapBounds = new CheckBox("Snap");
  private static final double SNAP_STEP = 0.02;

  private enum DragMode { NONE, MOVE, RESIZE }

  // ── Item Inspector fields ──
  private final VBox inspectorBox = new VBox(6);
  private final ScrollPane inspectorScroll = new ScrollPane(inspectorBox);
  private final Label inspHeader = new Label("No item selected");
  private boolean suppressInspector = false;

  private final TextField inspLabel = new TextField();
  private final TextField inspStyle = new TextField();
  private final TextField inspIcon = new TextField();
  private final CheckBox inspEnabled = new CheckBox("Enabled");
  private final ComboBox<MenuActionType> inspAction = new ComboBox<>();
  private final TextField inspActionKey = new TextField();
  private final TextField inspTarget = new TextField();
  private final TextField inspBgAsset = new TextField();
  private final TextField inspBgSelected = new TextField();
  private final TextField inspBgDisabled = new TextField();
  private final TextField inspBoundsX = new TextField();
  private final TextField inspBoundsY = new TextField();
  private final TextField inspBoundsW = new TextField();
  private final TextField inspBoundsH = new TextField();
  private final CheckBox inspSlotEnabled = new CheckBox("Slot Preview");
  private final TextField inspSlotPlaceholder = new TextField();
  private final TextField inspSlotFrame = new TextField();
  private final TextField inspSlotX = new TextField();
  private final TextField inspSlotY = new TextField();
  private final TextField inspSlotW = new TextField();
  private final TextField inspSlotH = new TextField();

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
    updatePreviewSize();
    validateState();
    redrawPreview();
  }

  public void setScreenIdHint(String id) {
    if (id != null && !id.isBlank()) this.screenIdHint = id.trim();
  }

  public void setMenuText(String text) {
    String normalizedInput = normalizeLineEndings(text);
    if (normalizedInput.equals(lastLoadedText)) return;
    lineDiagnostics.clear();
    lineDiagnostics.addAll(DslPropertyDiagnostics.menuScreenIssues(text, TOP_LEVEL_KEYS, ITEM_KEYS));
    parseDiagnostics.clear();
    suppressEvents = true;
    Properties p = new Properties();
    try {
      if (text != null && !text.isBlank()) p.load(new StringReader(text));
    } catch (Exception ex) {
      parseDiagnostics.add("Failed to parse menu properties: " + ex.getMessage());
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
    cbLayout.getEditor().setText(normalize(p.getProperty("layout", p.getProperty("layoutId")), ""));
    cbDefaultStyle.setEditable(true);
    cbDefaultStyle.getEditor().setText(normalize(p.getProperty("defaultItemStyle"), ""));
    cbWrap.setSelected(parseBooleanForLoad(p.getProperty("wrapSelection"), true, "wrapSelection"));

    List<String> ids = parseCsv(p.getProperty("items"));
    if (ids.isEmpty()) ids = collectItemIdsFromProperties(p);

    rows.clear();
    if (!ids.isEmpty()) {
      for (String id : ids) {
        String key = normalize(id, "");
        if (key.isEmpty()) continue;
        String prefix = "item." + key + ".";
        MenuActionSpec action = parseActionForLoad(
            p.getProperty(prefix + "action"),
            p.getProperty(prefix + "target"),
            prefix + "action"
        );
        MenuItemRow row = new MenuItemRow(
            key,
            p.getProperty(prefix + "label", ""),
            p.getProperty(prefix + "style", ""),
            p.getProperty(prefix + "icon", ""),
            parseBooleanForLoad(p.getProperty(prefix + "enabled"), true, prefix + "enabled"),
            action.type(),
            action.actionKey(),
            action.target(),
            p.getProperty(prefix + "bgAsset", ""),
            p.getProperty(prefix + "bgSelectedAsset", ""),
            p.getProperty(prefix + "bgDisabledAsset", ""),
            parseOptionalDoubleForLoad(p.getProperty(prefix + "boundsX"), prefix + "boundsX"),
            parseOptionalDoubleForLoad(p.getProperty(prefix + "boundsY"), prefix + "boundsY"),
            parseOptionalDoubleForLoad(p.getProperty(prefix + "boundsWidth"), prefix + "boundsWidth"),
            parseOptionalDoubleForLoad(p.getProperty(prefix + "boundsHeight"), prefix + "boundsHeight"),
            parseBooleanForLoad(p.getProperty(prefix + "slotPreviewEnabled"), isSlotTemplateId(key), prefix + "slotPreviewEnabled"),
            p.getProperty(prefix + "slotPreviewPlaceholderAsset", ""),
            p.getProperty(prefix + "slotPreviewFrameAsset", ""),
            parseOptionalDoubleForLoad(p.getProperty(prefix + "slotPreviewX"), prefix + "slotPreviewX"),
            parseOptionalDoubleForLoad(p.getProperty(prefix + "slotPreviewY"), prefix + "slotPreviewY"),
            parseOptionalDoubleForLoad(p.getProperty(prefix + "slotPreviewWidth"), prefix + "slotPreviewWidth"),
            parseOptionalDoubleForLoad(p.getProperty(prefix + "slotPreviewHeight"), prefix + "slotPreviewHeight")
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

  public String getTitleText() {
    return tfTitle.getText();
  }

  public List<String> getItemLabels() {
    List<String> labels = new ArrayList<>();
    for (MenuItemRow row : rows) {
      String label = row.getLabel();
      if (label == null || label.isBlank()) {
        label = row.getId();
      }
      labels.add(label);
    }
    return labels;
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
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    buildColumns();
    table.setItems(rows);
    table.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      previewSelected = Math.max(0, nv == null ? 0 : nv.intValue());
      populateInspector();
      syncExtrasFromSelectedRow();
      redrawPreview();
    });

    // Simplified action buttons — asset/bounds editing moved to inspector
    HBox actions = new HBox(6);
    actions.getStyleClass().add("layout-studio-action-bar");

    Button bAdd = iconActionButton(CssIcon.plus("#8cd48c"), "Add item");
    Button bDuplicate = iconActionButton(CssIcon.copy("#9cc7ff"), "Duplicate selected item");
    Button bRemove = iconActionButton(CssIcon.minus("#e07070"), "Remove selected item");
    Button bUp = iconActionButton(CssIcon.arrowUp(), "Move Up");
    Button bDown = iconActionButton(CssIcon.arrowDown(), "Move Down");
    Button bNormalize = iconActionButton(CssIcon.sort(), "Normalize IDs");

    for (Button b : List.of(bAdd, bDuplicate, bRemove, bUp, bDown, bNormalize)) {
      b.setMinWidth(Region.USE_PREF_SIZE);
    }

    bAdd.setOnAction(e -> addRow());
    bDuplicate.setOnAction(e -> duplicateSelectedRow());
    bRemove.setOnAction(e -> removeRow());
    bUp.setOnAction(e -> moveSelected(-1));
    bDown.setOnAction(e -> moveSelected(1));
    bNormalize.setOnAction(e -> normalizeIds());
    cbSnapBounds.setSelected(true);
    cbSnapBounds.setTooltip(new Tooltip("Snap dragged bounds to a 2% grid. Hold Ctrl while dragging to bypass."));
    actions.getChildren().addAll(bAdd, bDuplicate, bRemove, bUp, bDown, bNormalize, cbSnapBounds);

    table.getStyleClass().add("layout-studio-table");
    VBox tablePane = new VBox(6, table, actions);
    VBox.setVgrow(table, Priority.ALWAYS);

    preview.widthProperty().addListener((o, ov, nv) -> redrawPreview());
    preview.heightProperty().addListener((o, ov, nv) -> redrawPreview());
    preview.setFocusTraversable(true);
    installPreviewInteractions();
    previewHost = new javafx.scene.layout.StackPane(preview);
    previewHost.getStyleClass().add("layout-studio-preview-host");
    previewHost.setPadding(new Insets(8));
    preview.setManaged(false);
    previewHost.widthProperty().addListener((o, ov, nv) -> updatePreviewSize());
    previewHost.heightProperty().addListener((o, ov, nv) -> updatePreviewSize());

    ScrollPane inspPane = buildItemInspector();

    SplitPane split = new SplitPane(tablePane, previewHost, inspPane);
    split.setDividerPositions(0.30, 0.65);
    SplitPane.setResizableWithParent(inspPane, false);
    setCenter(split);
    updatePreviewSize();
  }

  private void updatePreviewSize() {
    if (previewHost == null) return;
    double availableW = Math.max(1.0, previewHost.getWidth() - 16.0);
    double availableH = Math.max(1.0, previewHost.getHeight() - 16.0);
    double aspect = ProjectViewportSpec.resolve(projectRoot).aspect();
    double w = availableW;
    double h = w / Math.max(0.0001, aspect);
    if (h > availableH) {
      h = availableH;
      w = h * aspect;
    }
    if (Math.abs(preview.getWidth() - w) >= 0.5) preview.setWidth(w);
    if (Math.abs(preview.getHeight() - h) >= 0.5) preview.setHeight(h);
    double x = 8.0 + (availableW - w) * 0.5;
    double y = 8.0 + (availableH - h) * 0.5;
    if (Math.abs(preview.getLayoutX() - x) >= 0.5) preview.setLayoutX(x);
    if (Math.abs(preview.getLayoutY() - y) >= 0.5) preview.setLayoutY(y);
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
    actionCol.setOnEditCommit(e -> {
      MenuItemRow row = e.getRowValue();
      if (row == null) return;
      row.setAction(e.getNewValue());
      if (!usesTarget(row.getAction()) && !row.isCustomAction()) {
        row.setTarget("");
      }
    });

    TableColumn<MenuItemRow, String> actionKeyCol = new TableColumn<>("Action Key");
    actionKeyCol.setCellValueFactory(v -> v.getValue().actionKeyProperty());
    actionKeyCol.setCellFactory(TextFieldTableCell.forTableColumn());
    actionKeyCol.setOnEditCommit(e -> {
      MenuItemRow row = e.getRowValue();
      if (row == null) return;
      row.setActionKey(normalize(e.getNewValue(), "noop"));
      if (!usesTarget(row.getAction()) && !row.isCustomAction()) {
        row.setTarget("");
      }
    });

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

    // Core columns always visible
    table.getColumns().setAll(
        idCol, labelCol, styleCol, enabledCol, actionCol, actionKeyCol, targetCol
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
    cbLayout.getEditor().setText("");
    cbDefaultStyle.setEditable(true);
    cbDefaultStyle.getEditor().setText("");
    cbWrap.setSelected(true);
    rows.clear();
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
        canonicalActionName(action),
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

  // ── Item Inspector ──

  private ScrollPane buildItemInspector() {
    inspectorBox.setPadding(new Insets(8));
    inspectorBox.setStyle("-fx-background-color: #13161d;");

    inspHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e6e6e6;");

    // Identity section
    inspLabel.setPromptText("Display label");
    inspStyle.setPromptText("Style override");
    inspIcon.setPromptText("assets/ui/icon.png");
    inspEnabled.setSelected(true);
    VBox identitySection = inspectorSection("Identity",
        inspectorRow("Label", inspLabel),
        inspectorRow("Style", inspStyle),
        inspectorAssetRow("Icon", inspIcon),
        inspEnabled
    );

    // Action section
    inspAction.getItems().setAll(MenuActionType.values());
    inspAction.setValue(MenuActionType.NOOP);
    inspActionKey.setPromptText("action key");
    inspTarget.setPromptText("target menu / script");
    VBox actionSection = inspectorSection("Action",
        inspectorRow("Type", inspAction),
        inspectorRow("Action Key", inspActionKey),
        inspectorRow("Target", inspTarget)
    );

    // Background Assets section
    inspBgAsset.setPromptText("assets/ui/button.png");
    inspBgSelected.setPromptText("assets/ui/button_sel.png");
    inspBgDisabled.setPromptText("assets/ui/button_dis.png");
    VBox bgSection = inspectorSection("Background Assets",
        inspectorAssetRow("Normal", inspBgAsset),
        inspectorAssetRow("Selected", inspBgSelected),
        inspectorAssetRow("Disabled", inspBgDisabled)
    );

    // Bounds section
    inspBoundsX.setPromptText("X"); inspBoundsY.setPromptText("Y");
    inspBoundsW.setPromptText("Width"); inspBoundsH.setPromptText("Height");
    GridPane boundsGrid = new GridPane();
    boundsGrid.setHgap(4); boundsGrid.setVgap(4);
    boundsGrid.add(new Label("X"), 0, 0); boundsGrid.add(inspBoundsX, 1, 0);
    boundsGrid.add(new Label("Y"), 2, 0); boundsGrid.add(inspBoundsY, 3, 0);
    boundsGrid.add(new Label("W"), 0, 1); boundsGrid.add(inspBoundsW, 1, 1);
    boundsGrid.add(new Label("H"), 2, 1); boundsGrid.add(inspBoundsH, 3, 1);
    inspBoundsX.setPrefWidth(70); inspBoundsY.setPrefWidth(70);
    inspBoundsW.setPrefWidth(70); inspBoundsH.setPrefWidth(70);
    Button clearBoundsBtn = iconActionButton(CssIcon.clearX("#e07070"), "Clear bounds");
    clearBoundsBtn.setOnAction(e -> clearBoundsForSelection());
    Button openBoundsStudioBtn = iconActionButton(CssIcon.grid("#7ec8e3"), "Open visual bounds drawing tool (rect-draw, point-nail)");
    openBoundsStudioBtn.setOnAction(e -> openBoundsStudio());
    HBox boundsActions = new HBox(6, clearBoundsBtn, openBoundsStudioBtn);
    boundsActions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    VBox boundsSection = inspectorSection("Item Bounds", boundsGrid, boundsActions);

    // Slot Preview section
    inspSlotPlaceholder.setPromptText("placeholder asset");
    inspSlotFrame.setPromptText("frame asset");
    inspSlotX.setPromptText("X"); inspSlotY.setPromptText("Y");
    inspSlotW.setPromptText("Width"); inspSlotH.setPromptText("Height");
    GridPane slotBoundsGrid = new GridPane();
    slotBoundsGrid.setHgap(4); slotBoundsGrid.setVgap(4);
    slotBoundsGrid.add(new Label("X"), 0, 0); slotBoundsGrid.add(inspSlotX, 1, 0);
    slotBoundsGrid.add(new Label("Y"), 2, 0); slotBoundsGrid.add(inspSlotY, 3, 0);
    slotBoundsGrid.add(new Label("W"), 0, 1); slotBoundsGrid.add(inspSlotW, 1, 1);
    slotBoundsGrid.add(new Label("H"), 2, 1); slotBoundsGrid.add(inspSlotH, 3, 1);
    inspSlotX.setPrefWidth(70); inspSlotY.setPrefWidth(70);
    inspSlotW.setPrefWidth(70); inspSlotH.setPrefWidth(70);
    VBox slotSection = inspectorSection("Slot Preview",
        inspSlotEnabled,
        inspectorAssetRow("Placeholder", inspSlotPlaceholder),
        inspectorAssetRow("Frame", inspSlotFrame),
        slotBoundsGrid
    );

    // Extras section (per-item custom properties)
    VBox extrasSection = buildExtrasPanel();

    inspectorBox.getChildren().setAll(
        inspHeader,
        identitySection, actionSection, bgSection,
        boundsSection, slotSection, extrasSection
    );

    inspectorScroll.setContent(inspectorBox);
    inspectorScroll.setFitToWidth(true);
    inspectorScroll.setStyle("-fx-background-color: #13161d;");
    inspectorScroll.setPrefWidth(300);

    registerInspectorListeners();
    return inspectorScroll;
  }

  private VBox inspectorSection(String title, javafx.scene.Node... children) {
    Label header = new Label(title);
    header.setStyle("-fx-font-weight: bold; -fx-text-fill: #a8b0c0; -fx-font-size: 11px;");
    VBox section = new VBox(4);
    section.getChildren().add(header);
    section.getChildren().addAll(children);
    section.setPadding(new Insets(4, 0, 4, 0));
    section.setStyle("-fx-border-color: #2a2f3a; -fx-border-width: 0 0 1 0;");
    return section;
  }

  private HBox inspectorRow(String label, javafx.scene.Node field) {
    Label l = new Label(label);
    l.setMinWidth(70);
    l.setStyle("-fx-text-fill: #c0c0c0; -fx-font-size: 11px;");
    HBox row = new HBox(6, l, field);
    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    if (field instanceof TextField tf) { HBox.setHgrow(tf, Priority.ALWAYS); tf.setMaxWidth(Double.MAX_VALUE); }
    if (field instanceof ComboBox<?> cb) { HBox.setHgrow(cb, Priority.ALWAYS); cb.setMaxWidth(Double.MAX_VALUE); }
    return row;
  }

  private Button iconActionButton(javafx.scene.Node icon, String tooltip) {
    Button button = new Button();
    button.setGraphic(icon);
    if (tooltip != null && !tooltip.isBlank()) {
      button.setTooltip(new Tooltip(tooltip));
    }
    button.getStyleClass().addAll("layout-studio-action-button", "layout-studio-icon-button");
    return button;
  }

  private HBox inspectorAssetRow(String label, TextField field) {
    Label l = new Label(label);
    l.setMinWidth(70);
    l.setStyle("-fx-text-fill: #c0c0c0; -fx-font-size: 11px;");
    AssetPickerSupport.installAssetDrop(field, this::toProjectRelativePath);
    Button browse = iconActionButton(CssIcon.folder("#b0b8c8"), "Browse project assets");
    browse.setOnAction(e -> {
      String asset = chooseImageAsset("Select " + label);
      if (asset != null && !asset.isBlank()) {
        field.setText(asset);
      }
    });
    Button importBtn = iconActionButton(CssIcon.download("#8cd48c"), "Import external file into project");
    importBtn.setOnAction(e -> {
      String asset = importImageAsset("Import " + label);
      if (asset != null && !asset.isBlank()) {
        field.setText(asset);
      }
    });
    Button revealBtn = iconActionButton(CssIcon.link("#9cc7ff"), "Reveal in file manager");
    revealBtn.setOnAction(e -> revealAsset(field.getText()));
    Button clearBtn = iconActionButton(CssIcon.clearX("#e07070"), "Clear asset path");
    clearBtn.setOnAction(e -> field.clear());
    HBox.setHgrow(field, Priority.ALWAYS);
    field.setMaxWidth(Double.MAX_VALUE);
    HBox row = new HBox(4, l, field, browse, importBtn, revealBtn, clearBtn);
    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    return row;
  }

  private void populateInspector() {
    int idx = table.getSelectionModel().getSelectedIndex();
    if (idx < 0 || idx >= rows.size()) {
      inspHeader.setText("No item selected");
      inspectorBox.setDisable(true);
      return;
    }
    inspectorBox.setDisable(false);
    MenuItemRow row = rows.get(idx);
    suppressInspector = true;

    inspHeader.setText("Item: " + row.getId());
    inspLabel.setText(row.getLabel());
    inspStyle.setText(row.getStyle());
    inspIcon.setText(row.getIcon());
    inspEnabled.setSelected(row.isEnabled());
    inspAction.setValue(row.getAction());
    inspActionKey.setText(row.getActionKey());
    inspTarget.setText(row.getTarget());
    inspBgAsset.setText(row.getBgAsset());
    inspBgSelected.setText(row.getBgSelectedAsset());
    inspBgDisabled.setText(row.getBgDisabledAsset());
    inspBoundsX.setText(row.getBoundsX() != null ? formatDouble(row.getBoundsX()) : "");
    inspBoundsY.setText(row.getBoundsY() != null ? formatDouble(row.getBoundsY()) : "");
    inspBoundsW.setText(row.getBoundsW() != null ? formatDouble(row.getBoundsW()) : "");
    inspBoundsH.setText(row.getBoundsH() != null ? formatDouble(row.getBoundsH()) : "");
    inspSlotEnabled.setSelected(row.isSlotPreviewEnabled());
    inspSlotPlaceholder.setText(row.getSlotPreviewPlaceholderAsset());
    inspSlotFrame.setText(row.getSlotPreviewFrameAsset());
    inspSlotX.setText(row.getSlotPreviewX() != null ? formatDouble(row.getSlotPreviewX()) : "");
    inspSlotY.setText(row.getSlotPreviewY() != null ? formatDouble(row.getSlotPreviewY()) : "");
    inspSlotW.setText(row.getSlotPreviewW() != null ? formatDouble(row.getSlotPreviewW()) : "");
    inspSlotH.setText(row.getSlotPreviewH() != null ? formatDouble(row.getSlotPreviewH()) : "");

    suppressInspector = false;
  }

  private void pushInspectorToRow() {
    if (suppressInspector) return;
    int idx = table.getSelectionModel().getSelectedIndex();
    if (idx < 0 || idx >= rows.size()) return;
    MenuItemRow row = rows.get(idx);

    row.setLabel(inspLabel.getText());
    row.setStyle(inspStyle.getText());
    row.setIcon(inspIcon.getText());
    row.setEnabled(inspEnabled.isSelected());
    row.setAction(inspAction.getValue());
    row.setActionKey(inspActionKey.getText());
    row.setTarget(inspTarget.getText());
    row.setBgAsset(inspBgAsset.getText());
    row.setBgSelectedAsset(inspBgSelected.getText());
    row.setBgDisabledAsset(inspBgDisabled.getText());
    row.setBoundsX(parseOptionalDouble(inspBoundsX.getText()));
    row.setBoundsY(parseOptionalDouble(inspBoundsY.getText()));
    row.setBoundsW(parseOptionalDouble(inspBoundsW.getText()));
    row.setBoundsH(parseOptionalDouble(inspBoundsH.getText()));
    row.setSlotPreviewEnabled(inspSlotEnabled.isSelected());
    row.setSlotPreviewPlaceholderAsset(inspSlotPlaceholder.getText());
    row.setSlotPreviewFrameAsset(inspSlotFrame.getText());
    row.setSlotPreviewX(parseOptionalDouble(inspSlotX.getText()));
    row.setSlotPreviewY(parseOptionalDouble(inspSlotY.getText()));
    row.setSlotPreviewW(parseOptionalDouble(inspSlotW.getText()));
    row.setSlotPreviewH(parseOptionalDouble(inspSlotH.getText()));
  }

  private void registerInspectorListeners() {
    Runnable push = this::pushInspectorToRow;
    inspLabel.textProperty().addListener((o, ov, nv) -> push.run());
    inspStyle.textProperty().addListener((o, ov, nv) -> push.run());
    inspIcon.textProperty().addListener((o, ov, nv) -> push.run());
    inspEnabled.selectedProperty().addListener((o, ov, nv) -> push.run());
    inspAction.valueProperty().addListener((o, ov, nv) -> push.run());
    inspActionKey.textProperty().addListener((o, ov, nv) -> push.run());
    inspTarget.textProperty().addListener((o, ov, nv) -> push.run());
    inspBgAsset.textProperty().addListener((o, ov, nv) -> push.run());
    inspBgSelected.textProperty().addListener((o, ov, nv) -> push.run());
    inspBgDisabled.textProperty().addListener((o, ov, nv) -> push.run());
    inspBoundsX.textProperty().addListener((o, ov, nv) -> push.run());
    inspBoundsY.textProperty().addListener((o, ov, nv) -> push.run());
    inspBoundsW.textProperty().addListener((o, ov, nv) -> push.run());
    inspBoundsH.textProperty().addListener((o, ov, nv) -> push.run());
    inspSlotEnabled.selectedProperty().addListener((o, ov, nv) -> push.run());
    inspSlotPlaceholder.textProperty().addListener((o, ov, nv) -> push.run());
    inspSlotFrame.textProperty().addListener((o, ov, nv) -> push.run());
    inspSlotX.textProperty().addListener((o, ov, nv) -> push.run());
    inspSlotY.textProperty().addListener((o, ov, nv) -> push.run());
    inspSlotW.textProperty().addListener((o, ov, nv) -> push.run());
    inspSlotH.textProperty().addListener((o, ov, nv) -> push.run());
  }

  @SuppressWarnings("unchecked")
  private VBox buildExtrasPanel() {
    Label header = new Label("Per-Item Custom Properties");
    header.setStyle("-fx-font-weight: bold;");

    extrasTable.setEditable(true);
    extrasTable.setItems(extrasRows);
    extrasTable.setPrefHeight(120);
    extrasTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    TableColumn<ExtrasEntry, String> keyCol = new TableColumn<>("Key");
    keyCol.setCellValueFactory(v -> v.getValue().keyProperty());
    keyCol.setCellFactory(TextFieldTableCell.forTableColumn());
    keyCol.setOnEditCommit(e -> {
      e.getRowValue().setKey(e.getNewValue() != null ? e.getNewValue().trim() : "");
      syncExtrasToSelectedRow();
    });

    TableColumn<ExtrasEntry, String> valCol = new TableColumn<>("Value");
    valCol.setCellValueFactory(v -> v.getValue().valueProperty());
    valCol.setCellFactory(TextFieldTableCell.forTableColumn());
    valCol.setOnEditCommit(e -> {
      e.getRowValue().setValue(e.getNewValue() != null ? e.getNewValue().trim() : "");
      syncExtrasToSelectedRow();
    });

    extrasTable.getColumns().setAll(keyCol, valCol);

    Button addBtn = iconActionButton(CssIcon.plus("#8cd48c"), "Add custom property");
    addBtn.setOnAction(e -> {
      extrasRows.add(new ExtrasEntry("custom_key", ""));
      syncExtrasToSelectedRow();
    });
    Button removeBtn = iconActionButton(CssIcon.minus("#e07070"), "Remove selected custom property");
    removeBtn.setOnAction(e -> {
      int idx = extrasTable.getSelectionModel().getSelectedIndex();
      if (idx >= 0 && idx < extrasRows.size()) {
        extrasRows.remove(idx);
        syncExtrasToSelectedRow();
      }
    });
    javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(8, addBtn, removeBtn);

    VBox panel = new VBox(4, header, extrasTable, buttons);
    panel.setPadding(new Insets(4, 0, 0, 0));
    return panel;
  }

  private void syncExtrasFromSelectedRow() {
    extrasRows.clear();
    int idx = table.getSelectionModel().getSelectedIndex();
    if (idx < 0 || idx >= rows.size()) return;
    MenuItemRow row = rows.get(idx);
    for (Map.Entry<String, String> entry : row.extras.entrySet()) {
      extrasRows.add(new ExtrasEntry(entry.getKey(), entry.getValue()));
    }
  }

  private void syncExtrasToSelectedRow() {
    int idx = table.getSelectionModel().getSelectedIndex();
    if (idx < 0 || idx >= rows.size()) return;
    MenuItemRow row = rows.get(idx);
    row.extras.clear();
    for (ExtrasEntry entry : extrasRows) {
      String k = entry.getKey();
      if (k != null && !k.isBlank()) {
        row.extras.put(k, entry.getValue());
      }
    }
    onUiChanged();
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

  private void duplicateSelectedRow() {
    int idx = table.getSelectionModel().getSelectedIndex();
    if (idx < 0 || idx >= rows.size()) return;
    MenuItemRow source = rows.get(idx);
    String baseId = sanitizeId(source.getId());
    if (baseId.isBlank()) baseId = "item";
    String id = baseId + "_copy";
    int n = 2;
    while (hasId(id)) {
      id = baseId + "_copy_" + n++;
    }
    MenuItemRow copy = new MenuItemRow(
        id,
        source.getLabel(),
        source.getStyle(),
        source.getIcon(),
        source.isEnabled(),
        source.getAction(),
        source.getActionKey(),
        source.getTarget(),
        source.getBgAsset(),
        source.getBgSelectedAsset(),
        source.getBgDisabledAsset(),
        source.getBoundsX(),
        source.getBoundsY(),
        source.getBoundsW(),
        source.getBoundsH(),
        source.isSlotPreviewEnabled(),
        source.getSlotPreviewPlaceholderAsset(),
        source.getSlotPreviewFrameAsset(),
        source.getSlotPreviewX(),
        source.getSlotPreviewY(),
        source.getSlotPreviewW(),
        source.getSlotPreviewH()
    );
    copy.extras.putAll(source.extras);
    attachRowListeners(copy);
    int insertIndex = Math.min(idx + 1, rows.size());
    rows.add(insertIndex, copy);
    table.getSelectionModel().select(insertIndex);
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
    if (!parseDiagnostics.isEmpty()) parseDiagnostics.clear();
    lineDiagnostics.clear();
    lineDiagnostics.addAll(DslPropertyDiagnostics.menuScreenIssues(serialize(), TOP_LEVEL_KEYS, ITEM_KEYS));
    validateState();
    redrawPreview();
    emitText();
  }

  private void validateState() {
    LinkedHashSet<String> warnings = new LinkedHashSet<>();
    warnings.addAll(lineDiagnostics);
    warnings.addAll(parseDiagnostics);
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
      if (!row.isCustomAction()) {
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
      String boundsPointsRaw = normalize(row.extras.get("boundsPoints"), "");
      if (!boundsPointsRaw.isBlank()) {
        List<BoundsPointCodec.Point> points = parseBoundsPoints(boundsPointsRaw);
        if (points.size() < 3) {
          warnings.add("Item '" + id + "': boundsPoints requires at least 3 valid points");
        }
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

      if (projectRoot != null) {
        warnMissingAsset(warnings, id, "bgAsset", row.getBgAsset());
        warnMissingAsset(warnings, id, "bgSelectedAsset", row.getBgSelectedAsset());
        warnMissingAsset(warnings, id, "bgDisabledAsset", row.getBgDisabledAsset());
        warnMissingAsset(warnings, id, "slotPreviewPlaceholderAsset", row.getSlotPreviewPlaceholderAsset());
        warnMissingAsset(warnings, id, "slotPreviewFrameAsset", row.getSlotPreviewFrameAsset());
      }
    }

    if (warnings.isEmpty()) {
      validation.setText("No issues detected.");
      validation.setTextFill(LayoutStudioPalette.TEXT_SUCCESS);
    } else {
      String joined = String.join("\n", warnings);
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
    String hints = normalize(tfHints.getText(), "");

    g.setFill(LayoutStudioPalette.TEXT_PRIMARY);
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 28));
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
      g.setFont(Font.font(Font.getDefault().getFamily(), 18));
      double tw = textWidth(g, text);
      double leftInset = rect.x() + 16;
      double rightInset = rect.x() + Math.max(16, rect.w() - 16 - reservedRightSpace);
      double x = leftInset + Math.max(0, (rightInset - leftInset - tw) / 2.0);
      g.fillText(text, x, rect.y() + rect.h() * 0.62);

      if (slotPreviewRect != null) {
        drawInlineSlotPreview(g, row, slotPreviewRect, selected);
      }

      drawCustomBoundsOverlay(g, row, rect, selected);

      if (selected) {
        g.setFill(LayoutStudioPalette.ACCENT_GREEN);
        g.fillOval(rect.x() + rect.w() - 10, rect.y() + rect.h() - 10, 10, 10);
      }
    }

    g.setFill(LayoutStudioPalette.TEXT_MUTED);
    g.setFont(Font.font(Font.getDefault().getFamily(), 14));
    double hintsW = textWidth(g, hints);
    g.fillText(hints, (w - hintsW) / 2.0, h - 18);

    if (previewSelected >= 0 && previewSelected < previewRects.length) {
      Rect sel = previewRects[previewSelected];
      drawBoundsTag(g, sel.x() + 8, sel.y() - 8, "Drag move/resize | Arrows nudge | Alt+Arrows resize");
    }
  }

  private int hitTestPreviewIndex(double x, double y) {
    for (int i = previewRects.length - 1; i >= 0; i--) {
      Rect r = previewRects[i];
      if (r == null) continue;
      MenuItemRow row = i >= 0 && i < rows.size() ? rows.get(i) : null;
      if (containsInCustomBounds(row, r, x, y) || r.contains(x, y)) return i;
    }
    return -1;
  }

  private boolean containsInCustomBounds(MenuItemRow row, Rect rect, double x, double y) {
    if (row == null || rect == null) return false;
    List<BoundsPointCodec.Point> points = parseBoundsPoints(row.extras.get("boundsPoints"));
    if (points.size() < 3) return false;
    return BoundsPointCodec.containsInRect(points, rect.x(), rect.y(), rect.w(), rect.h(), x, y);
  }

  private List<BoundsPointCodec.Point> parseBoundsPoints(String raw) {
    return BoundsPointCodec.parse(normalize(raw, ""));
  }

  private void installPreviewInteractions() {
    preview.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
      preview.requestFocus();
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
      boolean snapEnabled = cbSnapBounds.isSelected() && !e.isControlDown();

      suppressEvents = true;
      if (dragMode == DragMode.MOVE) {
        row.setBoundsX(snapNormalized(clamp01((dragStartX == null ? 0 : dragStartX) + dxNorm), snapEnabled));
        row.setBoundsY(snapNormalized(clamp01((dragStartY == null ? 0 : dragStartY) + dyNorm), snapEnabled));
      } else if (dragMode == DragMode.RESIZE) {
        row.setBoundsW(snapNormalized(clamp((dragStartW == null ? 0.2 : dragStartW) + dxNorm, 0.05, 1.0), snapEnabled));
        row.setBoundsH(snapNormalized(clamp((dragStartH == null ? 0.1 : dragStartH) + dyNorm, 0.04, 1.0), snapEnabled));
      }
      suppressEvents = false;
      populateInspector();
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
        preview.requestFocus();
        table.getSelectionModel().select(idx);
        previewSelected = idx;
        redrawPreview();
      }
    });

    preview.addEventHandler(KeyEvent.KEY_PRESSED, this::handlePreviewKeyPressed);
  }

  private void handlePreviewKeyPressed(KeyEvent event) {
    if (event == null) return;
    KeyCode code = event.getCode();
    if (code == null) return;
    if (!(code == KeyCode.LEFT || code == KeyCode.RIGHT || code == KeyCode.UP || code == KeyCode.DOWN)) return;
    int idx = table.getSelectionModel().getSelectedIndex();
    if (idx < 0 || idx >= rows.size()) return;

    MenuItemRow row = rows.get(idx);
    Rect rect = idx < previewRects.length ? previewRects[idx] : null;
    if (rect == null) rect = resolvePreviewRect(row, idx, preview.getWidth(), preview.getHeight(), 130, 38);
    ensureBoundsInitialized(row, rect, preview.getWidth(), preview.getHeight());

    double step = event.isShiftDown() ? 0.002 : 0.01;
    boolean resize = event.isAltDown();
    boolean snapEnabled = cbSnapBounds.isSelected() && !event.isControlDown();

    Double x = row.getBoundsX();
    Double y = row.getBoundsY();
    Double bw = row.getBoundsW();
    Double bh = row.getBoundsH();
    if (x == null) x = 0.0;
    if (y == null) y = 0.0;
    if (bw == null) bw = 0.2;
    if (bh == null) bh = 0.1;

    suppressEvents = true;
    switch (code) {
      case LEFT -> {
        if (resize) bw = clamp(bw - step, 0.05, 1.0);
        else x = clamp01(x - step);
      }
      case RIGHT -> {
        if (resize) bw = clamp(bw + step, 0.05, 1.0);
        else x = clamp01(x + step);
      }
      case UP -> {
        if (resize) bh = clamp(bh - step, 0.04, 1.0);
        else y = clamp01(y - step);
      }
      case DOWN -> {
        if (resize) bh = clamp(bh + step, 0.04, 1.0);
        else y = clamp01(y + step);
      }
      default -> {
        suppressEvents = false;
        return;
      }
    }
    row.setBoundsX(snapNormalized(x, snapEnabled));
    row.setBoundsY(snapNormalized(y, snapEnabled));
    row.setBoundsW(snapNormalized(bw, snapEnabled));
    row.setBoundsH(snapNormalized(bh, snapEnabled));
    suppressEvents = false;
    populateInspector();
    onUiChanged();
    event.consume();
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
      g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.NORMAL, 11));
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
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 10));
    g.fillText(text, x + 6, y);
  }

  private void drawCustomBoundsOverlay(GraphicsContext g, MenuItemRow row, Rect rect, boolean selected) {
    if (g == null || row == null || rect == null) return;
    List<BoundsPointCodec.Point> points = parseBoundsPoints(row.extras.get("boundsPoints"));
    if (points.size() < 3) return;
    double[] xs = new double[points.size()];
    double[] ys = new double[points.size()];
    for (int i = 0; i < points.size(); i++) {
      BoundsPointCodec.Point p = points.get(i);
      xs[i] = rect.x() + rect.w() * p.x();
      ys[i] = rect.y() + rect.h() * p.y();
    }
    g.setStroke(selected ? LayoutStudioPalette.ACCENT_GOLD : LayoutStudioPalette.ACCENT_BLUE_LIGHT);
    g.setLineWidth(selected ? 1.8 : 1.2);
    g.strokePolygon(xs, ys, points.size());
    g.setFill(selected ? Color.rgb(255, 210, 90, 0.6) : Color.rgb(120, 190, 255, 0.55));
    for (int i = 0; i < points.size(); i++) {
      g.fillOval(xs[i] - 2.5, ys[i] - 2.5, 5, 5);
    }
  }

  private boolean inResizeHandle(Rect rect, double x, double y) {
    double hx = rect.x() + rect.w() - 14;
    double hy = rect.y() + rect.h() - 14;
    return x >= hx && x <= hx + 14 && y >= hy && y <= hy + 14;
  }

  private String chooseImageAsset(String title) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(title);
    AssetPickerSupport.addAssetFilters(chooser);
    if (projectRoot != null && projectRoot.exists()) chooser.setInitialDirectory(projectRoot);
    Window owner = getScene() != null ? getScene().getWindow() : null;
    File selected = chooser.showOpenDialog(owner);
    if (selected == null) return null;
    return toProjectRelativePath(selected);
  }

  private String importImageAsset(String title) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(title);
    AssetPickerSupport.addAssetFilters(chooser);
    Window owner = getScene() != null ? getScene().getWindow() : null;
    File selected = chooser.showOpenDialog(owner);
    if (selected == null) return null;
    if (projectRoot == null) return toProjectRelativePath(selected);
    try {
      File destDir = new File(projectRoot, "config/menu/assets");
      if (!destDir.exists()) destDir.mkdirs();
      File dest = new File(destDir, selected.getName());
      // Avoid overwriting — generate unique name if needed
      if (dest.exists()) {
        String stem = selected.getName();
        String ext = "";
        int dot = stem.lastIndexOf('.');
        if (dot > 0) { ext = stem.substring(dot); stem = stem.substring(0, dot); }
        int counter = 1;
        while (dest.exists()) {
          dest = new File(destDir, stem + "_" + counter + ext);
          counter++;
        }
      }
      java.nio.file.Files.copy(selected.toPath(), dest.toPath());
      return toProjectRelativePath(dest);
    } catch (Exception ex) {
      return null;
    }
  }

  private void revealAsset(String path) {
    File file = resolveAssetFile(path);
    AssetPickerSupport.revealFile(file);
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

  private void openBoundsStudio() {
    BoundsDrawingTool tool = new BoundsDrawingTool();
    tool.setWorkspaceAspect(ProjectViewportSpec.resolve(projectRoot).aspect());

    // Load background asset from the first item that has a bgAsset, or the screen bg
    if (projectRoot != null) {
      for (MenuItemRow row : rows) {
        String asset = normalize(row.getBgAsset(), "");
        if (!asset.isBlank()) {
          File f = resolveAssetFile(asset);
          if (f != null && f.exists()) {
            tool.setBackgroundImage(f);
            break;
          }
        }
      }
    }

    // Pre-populate with existing item bounds
    List<BoundsDrawingTool.BoundEntry> entries = new ArrayList<>();
    for (MenuItemRow row : rows) {
      String id = normalize(row.getId(), "item");
      String label = normalize(row.getLabel(), null);
      double bx = row.getBoundsX() != null ? row.getBoundsX() : 0;
      double by = row.getBoundsY() != null ? row.getBoundsY() : 0;
      double bw = row.getBoundsW() != null ? row.getBoundsW() : 0;
      double bh = row.getBoundsH() != null ? row.getBoundsH() : 0;
      entries.add(new BoundsDrawingTool.BoundEntry(
          id,
          label,
          bx,
          by,
          bw,
          bh,
          parseBoundsPoints(row.extras.get("boundsPoints"))
      ));
    }
    tool.setBounds(entries);

    // Show in a dialog
    javafx.scene.Scene dialogScene = new javafx.scene.Scene(tool, 960, 620);
    EditorTheme.apply(dialogScene);
    javafx.stage.Stage dialog = new javafx.stage.Stage();
    dialog.setTitle("Bounds Studio — " + screenIdHint);
    dialog.setScene(dialogScene);
    dialog.initOwner(getScene() != null ? getScene().getWindow() : null);
    dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);

    // Sync bounds back to menu items.
    Runnable applyBounds = () -> {
      List<BoundsDrawingTool.BoundEntry> result = tool.getBounds();
      // Match by id — update existing rows, create new entries for unmatched
      java.util.Map<String, BoundsDrawingTool.BoundEntry> byId = new LinkedHashMap<>();
      for (BoundsDrawingTool.BoundEntry be : result) byId.put(be.getId(), be);

      suppressEvents = true;
      for (MenuItemRow row : rows) {
        BoundsDrawingTool.BoundEntry match = byId.remove(normalize(row.getId(), ""));
        if (match != null && (match.getW() > 0.005 && match.getH() > 0.005)) {
          row.setBoundsX(match.getX());
          row.setBoundsY(match.getY());
          row.setBoundsW(match.getW());
          row.setBoundsH(match.getH());
          List<BoundsPointCodec.Point> points = match.getLocalPoints();
          if (points != null && points.size() >= 3) {
            row.extras.put("boundsPoints", BoundsPointCodec.encode(points));
          } else {
            row.extras.remove("boundsPoints");
          }
        }
      }
      // Any unmatched entries become new rows
      for (BoundsDrawingTool.BoundEntry extra : byId.values()) {
        if (extra.getW() < 0.005 || extra.getH() < 0.005) continue;
        MenuItemRow newRow = new MenuItemRow(
            extra.getId(),
            extra.getLabel() != null ? extra.getLabel() : "",
            "", "", true,
            MenuActionType.NOOP, "", "",
            "", "", "",
            extra.getX(), extra.getY(), extra.getW(), extra.getH(),
            false, "", "", null, null, null, null
        );
        List<BoundsPointCodec.Point> points = extra.getLocalPoints();
        if (points != null && points.size() >= 3) {
          newRow.extras.put("boundsPoints", BoundsPointCodec.encode(points));
        }
        attachRowListeners(newRow);
        rows.add(newRow);
      }
      suppressEvents = false;
      populateInspector();
      onUiChanged();
    };
    tool.setOnSaveRequested(applyBounds);
    dialog.setOnHidden(ev -> applyBounds.run());

    if (isLinux()) {
      dialog.setIconified(false);
      dialog.setMaximized(true);
    }
    dialog.show();
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
    out.append("# Menu screen definition (.menu)").append(System.lineSeparator());
    out.append("# Text-first workflow: edit -> save -> run runtime -> validate navigation/actions.").append(System.lineSeparator());
    out.append("# Format: key=value (Java .properties)").append(System.lineSeparator());
    out.append("# Core keys: titleText, hintsText, layout, defaultItemStyle, wrapSelection, items").append(System.lineSeparator());
    out.append("# Per-item schema: item.<id>.label/action/target/style/enabled plus optional bounds and skin keys.").append(System.lineSeparator());

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
    out.append(System.lineSeparator()).append("# Ordered item ids shown by this screen").append(System.lineSeparator());
    out.append("items=").append(String.join(",", ids)).append(System.lineSeparator());

    boolean wroteItemHeader = false;
    for (ResolvedItem item : resolved) {
      String id = item.id();
      MenuItemRow row = item.row();
      String prefix = "item." + id + ".";
      if (!wroteItemHeader) {
        out.append(System.lineSeparator()).append("# --- Per-item declarations ---").append(System.lineSeparator());
        out.append("# action examples: open_menu, back, main_menu, save_menu, load_menu, settings_menu, quit, noop").append(System.lineSeparator());
        out.append("# boundsX/Y/Width/Height are normalized viewport fractions.").append(System.lineSeparator());
        out.append("# slotPreview* keys configure embedded save/load thumbnails inside custom row skins.").append(System.lineSeparator());
        wroteItemHeader = true;
      }
      String label = normalize(row.getLabel(), "");
      String style = normalize(row.getStyle(), "");
      String icon = normalize(row.getIcon(), "");
      String bgAsset = normalize(row.getBgAsset(), "");
      String bgSelectedAsset = normalize(row.getBgSelectedAsset(), "");
      String bgDisabledAsset = normalize(row.getBgDisabledAsset(), "");
      String slotPreviewPlaceholderAsset = normalize(row.getSlotPreviewPlaceholderAsset(), "");
      String slotPreviewFrameAsset = normalize(row.getSlotPreviewFrameAsset(), "");
      String action = normalize(row.getActionKey(), canonicalActionName(row.getAction()));
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
      if (!target.isBlank() && (usesTarget(row.getAction()) || row.isCustomAction())) {
        out.append(prefix).append("target=").append(escapeValue(target)).append(System.lineSeparator());
      }
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

  private MenuActionSpec parseActionForLoad(String rawAction, String rawTarget, String key) {
    MenuActionSpec action = MenuActionSpec.parse(rawAction, rawTarget);
    String raw = normalize(rawAction, "");
    String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_');
    if (action.type() == MenuActionType.NOOP
        && !raw.isBlank()
        && !"noop".equals(normalized)
        && !"no_op".equals(normalized)
        && !"none".equals(normalized)) {
      parseDiagnostics.add("Unknown action '" + raw + "' at " + key + "; treated as custom/noop");
    }
    return action;
  }

  private boolean parseBooleanForLoad(String raw, boolean fallback, String key) {
    if (raw == null || raw.isBlank()) return fallback;
    String v = raw.trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "true", "yes", "1" -> true;
      case "false", "no", "0" -> false;
      default -> {
        parseDiagnostics.add("Invalid boolean for '" + key + "': '" + raw + "' (using " + fallback + ")");
        yield fallback;
      }
    };
  }

  private Double parseOptionalDoubleForLoad(String raw, String key) {
    if (raw == null || raw.isBlank()) return null;
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value)) throw new NumberFormatException("non-finite");
      return value;
    } catch (Exception ignored) {
      parseDiagnostics.add("Invalid number for '" + key + "': '" + raw + "'");
      return null;
    }
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

  private void warnMissingAsset(Set<String> warnings, String itemId, String field, String path) {
    if (path == null || path.isBlank()) return;
    File resolved = new File(projectRoot, path.trim());
    if (!resolved.exists()) {
      warnings.add("Item '" + itemId + "': " + field + " not found: " + path.trim());
    }
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

  private static boolean usesTarget(MenuActionType action) {
    return action == MenuActionType.OPEN_MENU || action == MenuActionType.RUN_SCRIPT;
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

  private static double snapNormalized(double value, boolean enabled) {
    if (!enabled) return value;
    double snapped = Math.round(value / SNAP_STEP) * SNAP_STEP;
    return clamp01(snapped);
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

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
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
    private final StringProperty actionKey = new SimpleStringProperty("noop");
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
        String actionKey,
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
      setActionKey(actionKey == null ? canonicalActionName(action) : actionKey);
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
    public void setAction(MenuActionType action) {
      MenuActionType normalizedAction = action == null ? MenuActionType.NOOP : action;
      this.action.set(normalizedAction);
      this.actionKey.set(canonicalActionName(normalizedAction));
    }

    public String getActionKey() { return actionKey.get(); }
    public StringProperty actionKeyProperty() { return actionKey; }
    public void setActionKey(String actionKey) {
      String normalizedActionKey = normalize(actionKey, "noop");
      this.actionKey.set(normalizedActionKey);
      this.action.set(MenuActionType.parse(normalizedActionKey));
    }

    public boolean isCustomAction() {
      String key = normalize(getActionKey(), "noop").toLowerCase(Locale.ROOT).replace('-', '_');
      return getAction() == MenuActionType.NOOP
          && !"noop".equals(key)
          && !"no_op".equals(key)
          && !"none".equals(key);
    }

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

  static final class ExtrasEntry {
    private final StringProperty key = new SimpleStringProperty("");
    private final StringProperty value = new SimpleStringProperty("");

    ExtrasEntry(String key, String value) {
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
