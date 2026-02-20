package com.jvn.editor.ui;

import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dedicated flow editor for menu-to-menu navigation.
 * Lets teams wire OPEN_MENU/BACK/MAIN_MENU visually and validates navigation targets.
 */
public class MenuFlowEditorView extends BorderPane {
  private static final String DEFAULT_MENU_REGISTRY_PATH = "config/menu/registry/menu.registry";
  private static final String DEFAULT_MENU_DIR = "config/menu/menus";
  private static final String NODE_BACK = "__back__";
  private static final String NODE_MISSING_PREFIX = "__missing__::";

  private static final double NODE_WIDTH = 184;
  private static final double NODE_HEIGHT = 72;

  private final Label projectLabel = new Label("No project loaded.");
  private final Label statusLabel = new Label("Open a project to begin.");
  private final Label selectedMenuLabel = new Label("Menu: (none)");
  private final Label selectedFileLabel = new Label("File: -");
  private final Label wireModeLabel = new Label("Wire mode: Off");

  private final Button refreshButton = new Button("Refresh");
  private final Button validateButton = new Button("Validate");
  private final Button saveSelectedButton = new Button("Save Screen");
  private final Button saveAllButton = new Button("Save All");
  private final Button autoLayoutButton = new Button("Auto Layout");
  private final Button openButton = new Button("Open Screen");
  private final Button addScreenButton = new Button("Add Screen");

  private final Button wireOpenButton = new Button("Wire OPEN_MENU");
  private final Button setMainButton = new Button("Set MAIN_MENU");
  private final Button setBackButton = new Button("Set BACK");
  private final Button clearTargetButton = new Button("Clear Target");
  private final Button addItemButton = new Button("Add Item");
  private final Button removeItemButton = new Button("Remove Item");

  private final TableView<MenuItemModel> itemTable = new TableView<>();
  private final TextArea diagnosticsArea = new TextArea();

  private final Group edgeLayer = new Group();
  private final Group nodeLayer = new Group();
  private final Pane graphPane = new Pane(edgeLayer, nodeLayer);
  private final ScrollPane graphScroll = new ScrollPane(graphPane);
  private final Label graphEmptyLabel = new Label("No menu screens found. Create one with Add Screen.");

  private File projectRoot;
  private Consumer<File> onOpenFile;
  private final Map<String, MenuScreenModel> screensById = new LinkedHashMap<>();
  private final Map<String, NodeView> nodeViews = new LinkedHashMap<>();
  private final Map<String, Point2D> rememberedScreenPositions = new LinkedHashMap<>();

  private MenuScreenModel selectedScreen;
  private boolean suppressRowEvents;

  private enum WireMode { NONE, OPEN_MENU }
  private WireMode wireMode = WireMode.NONE;

  public MenuFlowEditorView() {
    setPadding(new Insets(8));
    buildUi();
    bindActions();
    rebuildGraph();
    refreshDiagnostics();
    updateUiState();
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    loadProjectMenus();
  }

  public void setOnOpenFile(Consumer<File> onOpenFile) {
    this.onOpenFile = onOpenFile;
  }

  public void refreshStatus() {
    loadProjectMenus();
  }

  private void buildUi() {
    Label title = new Label("Menu Graph / Flow");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

    HBox rowA = new HBox(8, refreshButton, validateButton, saveSelectedButton, saveAllButton, autoLayoutButton, openButton, addScreenButton);
    rowA.setAlignment(Pos.CENTER_LEFT);

    VBox top = new VBox(6, title, projectLabel, rowA, statusLabel, new Separator());
    setTop(top);

    graphScroll.setFitToWidth(true);
    graphScroll.setFitToHeight(true);
    graphScroll.setPannable(true);
    graphPane.setPrefSize(1200, 760);

    graphEmptyLabel.setStyle("-fx-text-fill: #a6adba;");
    graphEmptyLabel.setMouseTransparent(true);
    StackPane.setAlignment(graphEmptyLabel, Pos.CENTER);

    StackPane graphHost = new StackPane(graphScroll, graphEmptyLabel);
    graphHost.setStyle("-fx-background-color: #101217; -fx-border-color: #2a2f3a;");

    buildItemTable();

    diagnosticsArea.setEditable(false);
    diagnosticsArea.setWrapText(true);
    diagnosticsArea.setPrefRowCount(8);
    diagnosticsArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

    HBox wireButtons = new HBox(6, wireOpenButton, setMainButton, setBackButton, clearTargetButton);
    wireButtons.setAlignment(Pos.CENTER_LEFT);

    HBox itemButtons = new HBox(6, addItemButton, removeItemButton);
    itemButtons.setAlignment(Pos.CENTER_LEFT);

    VBox detail = new VBox(8,
        selectedMenuLabel,
        selectedFileLabel,
        wireModeLabel,
        wireButtons,
        itemButtons,
        itemTable,
        new Label("Validation"),
        diagnosticsArea
    );
    detail.setPadding(new Insets(8));
    detail.setStyle("-fx-background-color: #13161d; -fx-border-color: #2a2f3a;");
    VBox.setVgrow(itemTable, Priority.ALWAYS);
    VBox.setVgrow(diagnosticsArea, Priority.ALWAYS);

    SplitPane split = new SplitPane(graphHost, detail);
    split.setDividerPositions(0.66);
    setCenter(split);
  }

  private void buildItemTable() {
    itemTable.setEditable(true);
    itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    TableColumn<MenuItemModel, String> idCol = new TableColumn<>("Item");
    idCol.setCellValueFactory(v -> v.getValue().idProperty());
    idCol.setCellFactory(TextFieldTableCell.forTableColumn());
    idCol.setOnEditCommit(e -> {
      MenuItemModel row = e.getRowValue();
      if (row == null) return;
      row.setId(sanitizeId(e.getNewValue()));
      onCurrentScreenMutated();
    });

    TableColumn<MenuItemModel, MenuActionType> actionCol = new TableColumn<>("Action");
    actionCol.setCellValueFactory(v -> v.getValue().actionProperty());
    actionCol.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<MenuActionType>() {
      @Override
      public String toString(MenuActionType object) {
        return canonicalActionName(object);
      }

      @Override
      public MenuActionType fromString(String string) {
        return MenuActionType.parse(string);
      }
    }, FXCollections.observableArrayList(MenuActionType.values())));
    actionCol.setOnEditCommit(e -> {
      MenuItemModel row = e.getRowValue();
      if (row == null) return;
      row.setAction(e.getNewValue() == null ? MenuActionType.NOOP : e.getNewValue());
      if (!usesTarget(row.getAction())) row.setTarget("");
      onCurrentScreenMutated();
    });

    TableColumn<MenuItemModel, String> targetCol = new TableColumn<>("Target");
    targetCol.setCellValueFactory(v -> v.getValue().targetProperty());
    targetCol.setCellFactory(TextFieldTableCell.forTableColumn());
    targetCol.setOnEditCommit(e -> {
      MenuItemModel row = e.getRowValue();
      if (row == null) return;
      row.setTarget(normalize(e.getNewValue(), ""));
      onCurrentScreenMutated();
    });

    idCol.setPrefWidth(120);
    actionCol.setPrefWidth(150);
    targetCol.setPrefWidth(130);

    itemTable.getColumns().setAll(idCol, actionCol, targetCol);
    itemTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updateUiState());
  }

  private void bindActions() {
    refreshButton.setOnAction(e -> loadProjectMenus());
    validateButton.setOnAction(e -> showValidationDialog());
    autoLayoutButton.setOnAction(e -> {
      autoLayoutScreens();
      rebuildGraph();
    });
    openButton.setOnAction(e -> openSelectedScreen());
    addScreenButton.setOnAction(e -> addScreen());

    saveSelectedButton.setOnAction(e -> {
      if (selectedScreen != null) saveScreen(selectedScreen, true);
      refreshDiagnostics();
      updateUiState();
      rebuildGraph();
    });
    saveAllButton.setOnAction(e -> {
      int changed = 0;
      for (MenuScreenModel screen : screensById.values()) {
        if (!screen.dirty) continue;
        if (saveScreen(screen, false)) changed++;
      }
      statusLabel.setText(changed > 0 ? "Saved " + changed + " menu screen(s)." : "No dirty menu screens to save.");
      refreshDiagnostics();
      updateUiState();
      rebuildGraph();
    });

    wireOpenButton.setOnAction(e -> beginOpenWireMode());
    setMainButton.setOnAction(e -> applyActionToSelectedItem(MenuActionType.MAIN_MENU, ""));
    setBackButton.setOnAction(e -> applyActionToSelectedItem(MenuActionType.BACK, ""));
    clearTargetButton.setOnAction(e -> {
      MenuItemModel row = getSelectedItem();
      if (row == null) return;
      row.setTarget("");
      onCurrentScreenMutated();
    });

    addItemButton.setOnAction(e -> addItemToSelectedScreen());
    removeItemButton.setOnAction(e -> removeSelectedItem());

    graphPane.setOnMouseClicked(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (wireMode != WireMode.NONE) {
        wireMode = WireMode.NONE;
        updateUiState();
      }
    });
  }

  private void loadProjectMenus() {
    screensById.clear();
    selectedScreen = null;
    wireMode = WireMode.NONE;

    if (projectRoot == null || !projectRoot.isDirectory()) {
      projectLabel.setText("No project loaded.");
      statusLabel.setText("Open a project to inspect menu flow.");
      itemTable.setItems(FXCollections.observableArrayList());
      rebuildGraph();
      refreshDiagnostics();
      updateUiState();
      return;
    }

    projectLabel.setText("Project: " + projectRoot.getAbsolutePath());

    Properties manifest = loadManifest(projectRoot);
    Set<String> menuIds = discoverMenuIds(projectRoot, manifest);
    if (menuIds.isEmpty()) menuIds.add("main");

    for (String id : menuIds) {
      File file = resolveMenuFile(projectRoot, id);
      Properties properties = loadProperties(file);
      MenuScreenModel model = new MenuScreenModel(id, file, properties);
      loadItems(model);
      screensById.put(id, model);
    }

    autoLayoutScreensIfMissing();
    selectedScreen = screensById.values().stream().findFirst().orElse(null);
    itemTable.setItems(selectedScreen == null ? FXCollections.observableArrayList() : selectedScreen.items);
    rebuildGraph();
    refreshDiagnostics();
    updateUiState();
    statusLabel.setText("Loaded " + screensById.size() + " menu screen(s).");
  }

  private void loadItems(MenuScreenModel screen) {
    screen.items.clear();
    List<String> ids = collectItemIds(screen.properties);
    for (String itemId : ids) {
      String key = normalize(itemId, "");
      if (key.isBlank()) continue;
      String prefix = "item." + key + ".";
      MenuActionSpec action = MenuActionSpec.parse(
          screen.properties.getProperty(prefix + "action"),
          screen.properties.getProperty(prefix + "target")
      );
      MenuItemModel row = new MenuItemModel(key, action.type(), normalize(action.target(), ""));
      attachRowListeners(screen, row);
      screen.items.add(row);
    }

    if (screen.items.isEmpty() && "main".equalsIgnoreCase(screen.id)) {
      MenuItemModel row = new MenuItemModel("new_game", MenuActionType.NEW_GAME, "");
      attachRowListeners(screen, row);
      screen.items.add(row);
    }
  }

  private void attachRowListeners(MenuScreenModel screen, MenuItemModel row) {
    row.idProperty().addListener((o, ov, nv) -> {
      if (suppressRowEvents) return;
      row.setId(sanitizeId(nv));
      markDirty(screen);
    });
    row.actionProperty().addListener((o, ov, nv) -> {
      if (suppressRowEvents) return;
      if (nv == null) row.setAction(MenuActionType.NOOP);
      if (!usesTarget(row.getAction())) row.setTarget("");
      markDirty(screen);
    });
    row.targetProperty().addListener((o, ov, nv) -> {
      if (suppressRowEvents) return;
      row.setTarget(normalize(nv, ""));
      markDirty(screen);
    });
  }

  private void markDirty(MenuScreenModel screen) {
    if (screen == null) return;
    screen.dirty = true;
    onCurrentScreenMutated();
  }

  private void onCurrentScreenMutated() {
    wireMode = WireMode.NONE;
    rebuildGraph();
    refreshDiagnostics();
    updateUiState();
  }

  private void rebuildGraph() {
    for (NodeView nv : nodeViews.values()) {
      if (nv.screen != null) {
        rememberedScreenPositions.put(nv.nodeId, new Point2D(nv.getLayoutX(), nv.getLayoutY()));
      }
    }

    nodeViews.clear();
    nodeLayer.getChildren().clear();
    edgeLayer.getChildren().clear();

    if (screensById.isEmpty()) {
      graphEmptyLabel.setVisible(true);
      graphPane.setPrefSize(900, 600);
      return;
    }

    graphEmptyLabel.setVisible(false);

    List<MenuEdge> edges = collectEdges();

    for (MenuScreenModel screen : screensById.values()) {
      Point2D remembered = rememberedScreenPositions.get(screen.id);
      if (remembered != null) {
        screen.x = remembered.getX();
        screen.y = remembered.getY();
      }
      NodeView node = createNode(screen.id, titleize(screen.id), NodeKind.SCREEN, screen);
      node.setLayoutX(screen.x);
      node.setLayoutY(screen.y);
      nodeLayer.getChildren().add(node);
      nodeViews.put(screen.id, node);
    }

    boolean needsBackNode = false;
    Set<String> missingNodes = new LinkedHashSet<>();
    for (MenuEdge edge : edges) {
      if (NODE_BACK.equals(edge.toNodeId)) needsBackNode = true;
      if (edge.toNodeId.startsWith(NODE_MISSING_PREFIX)) missingNodes.add(edge.toNodeId);
    }

    if (needsBackNode) {
      NodeView back = createNode(NODE_BACK, "Back", NodeKind.SPECIAL, null);
      back.setLayoutX(30);
      back.setLayoutY(30);
      nodeLayer.getChildren().add(back);
      nodeViews.put(NODE_BACK, back);
    }

    double maxX = screensById.values().stream().mapToDouble(s -> s.x).max().orElse(0);
    double nextMissingY = 40;
    for (String missingId : missingNodes) {
      String rawTarget = missingId.substring(NODE_MISSING_PREFIX.length());
      NodeView missing = createNode(missingId, "Missing: " + rawTarget, NodeKind.MISSING, null);
      missing.setLayoutX(maxX + NODE_WIDTH + 180);
      missing.setLayoutY(nextMissingY);
      nextMissingY += NODE_HEIGHT + 18;
      nodeLayer.getChildren().add(missing);
      nodeViews.put(missingId, missing);
    }

    rebuildEdges(edges);
    refreshNodeStyles();
    updateGraphPreferredSize();
  }

  private void rebuildEdges(List<MenuEdge> edges) {
    edgeLayer.getChildren().clear();
    for (MenuEdge edge : edges) {
      NodeView from = nodeViews.get(edge.fromScreenId);
      NodeView to = nodeViews.get(edge.toNodeId);
      if (from == null || to == null) continue;
      drawEdge(from, to, edge);
    }
  }

  private void drawEdge(NodeView from, NodeView to, MenuEdge edge) {
    double sx = from.getLayoutX() + NODE_WIDTH;
    double sy = from.getLayoutY() + NODE_HEIGHT * 0.5;
    double tx = to.getLayoutX();
    double ty = to.getLayoutY() + NODE_HEIGHT * 0.5;

    Color color = switch (edge.action) {
      case OPEN_MENU -> Color.web("#6cb6ff");
      case MAIN_MENU -> Color.web("#9be19b");
      case BACK -> Color.web("#f0be76");
      case LOAD_MENU, SAVE_MENU, SETTINGS_MENU -> Color.web("#b5b9ff");
      default -> Color.web("#7f8795");
    };

    Line line = new Line(sx, sy, tx, ty);
    line.setStroke(color);
    line.setStrokeWidth(edge.missingTarget ? 1.5 : 2.0);
    if (edge.missingTarget) line.getStrokeDashArray().setAll(8.0, 6.0);

    Polygon arrow = createArrowHead(sx, sy, tx, ty, color);

    String tip = edge.fromScreenId + "." + edge.fromItemId + " -> " + canonicalActionName(edge.action)
        + (edge.targetMenuId == null || edge.targetMenuId.isBlank() ? "" : (" (" + edge.targetMenuId + ")"));
    Tooltip tooltip = new Tooltip(tip);
    Tooltip.install(line, tooltip);
    Tooltip.install(arrow, tooltip);

    edgeLayer.getChildren().addAll(line, arrow);
  }

  private Polygon createArrowHead(double sx, double sy, double tx, double ty, Color color) {
    double angle = Math.atan2(ty - sy, tx - sx);
    double len = 10;
    double wing = 5;
    double x1 = tx - len * Math.cos(angle) + wing * Math.sin(angle);
    double y1 = ty - len * Math.sin(angle) - wing * Math.cos(angle);
    double x2 = tx - len * Math.cos(angle) - wing * Math.sin(angle);
    double y2 = ty - len * Math.sin(angle) + wing * Math.cos(angle);

    Polygon p = new Polygon(tx, ty, x1, y1, x2, y2);
    p.setFill(color);
    return p;
  }

  private List<MenuEdge> collectEdges() {
    List<MenuEdge> edges = new ArrayList<>();
    for (MenuScreenModel screen : screensById.values()) {
      for (MenuItemModel row : screen.items) {
        String itemId = sanitizeId(row.getId());
        if (itemId.isBlank()) continue;
        MenuActionType action = row.getAction() == null ? MenuActionType.NOOP : row.getAction();
        String targetMenu = resolveTargetMenu(action, row.getTarget());
        if (targetMenu == null) continue;
        boolean missing = !NODE_BACK.equals(targetMenu) && !screensById.containsKey(targetMenu);
        String nodeTarget = missing ? NODE_MISSING_PREFIX + targetMenu : targetMenu;
        edges.add(new MenuEdge(screen.id, itemId, action, targetMenu, nodeTarget, missing));
      }
    }
    return edges;
  }

  private void updateGraphPreferredSize() {
    double maxX = 800;
    double maxY = 520;
    for (NodeView nv : nodeViews.values()) {
      maxX = Math.max(maxX, nv.getLayoutX() + NODE_WIDTH + 80);
      maxY = Math.max(maxY, nv.getLayoutY() + NODE_HEIGHT + 80);
    }
    graphPane.setPrefSize(maxX, maxY);
    graphPane.setMinSize(maxX, maxY);
  }

  private NodeView createNode(String nodeId, String text, NodeKind kind, MenuScreenModel screen) {
    NodeView node = new NodeView(nodeId, text, kind, screen);

    node.setOnMouseClicked(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (wireMode == WireMode.OPEN_MENU) {
        MenuItemModel selectedItem = getSelectedItem();
        if (selectedScreen != null && selectedItem != null && kind == NodeKind.SCREEN && screen != null) {
          selectedItem.setAction(MenuActionType.OPEN_MENU);
          selectedItem.setTarget(screen.id);
          markDirty(selectedScreen);
          wireMode = WireMode.NONE;
          updateUiState();
          e.consume();
          return;
        }
      }

      if (kind == NodeKind.SCREEN && screen != null) {
        selectScreen(screen);
      }

      if (e.getClickCount() >= 2 && kind == NodeKind.SCREEN && screen != null) {
        openScreen(screen);
      }

      e.consume();
    });

    if (kind == NodeKind.SCREEN && screen != null) {
      final double[] dragDelta = new double[2];
      node.setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        dragDelta[0] = e.getSceneX() - node.getLayoutX();
        dragDelta[1] = e.getSceneY() - node.getLayoutY();
      });
      node.setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        double nx = Math.max(12, e.getSceneX() - dragDelta[0]);
        double ny = Math.max(12, e.getSceneY() - dragDelta[1]);
        node.setLayoutX(nx);
        node.setLayoutY(ny);
        screen.x = nx;
        screen.y = ny;
        rememberedScreenPositions.put(screen.id, new Point2D(nx, ny));
        rebuildEdges(collectEdges());
        updateGraphPreferredSize();
      });

      ContextMenu cm = new ContextMenu();
      MenuItem open = new MenuItem("Open .menu File");
      open.setOnAction(ev -> openScreen(screen));
      MenuItem select = new MenuItem("Select");
      select.setOnAction(ev -> selectScreen(screen));
      MenuItem addItem = new MenuItem("Add Item");
      addItem.setOnAction(ev -> {
        selectScreen(screen);
        addItemToSelectedScreen();
      });
      cm.getItems().addAll(open, select, addItem);
      node.setOnContextMenuRequested(ev -> cm.show(node, ev.getScreenX(), ev.getScreenY()));
    }

    return node;
  }

  private void refreshNodeStyles() {
    for (NodeView node : nodeViews.values()) {
      if (node.kind == NodeKind.SCREEN && node.screen != null) {
        boolean selected = selectedScreen != null && selectedScreen.id.equals(node.screen.id);
        if (selected) {
          node.box.setStroke(Color.web("#77c0ff"));
          node.box.setFill(Color.web("#1e3348"));
          node.box.setStrokeWidth(2.2);
        } else if (node.screen.dirty) {
          node.box.setStroke(Color.web("#f0b777"));
          node.box.setFill(Color.web("#2b2a1f"));
          node.box.setStrokeWidth(1.8);
        } else {
          node.box.setStroke(Color.web("#4f5665"));
          node.box.setFill(Color.web("#242833"));
          node.box.setStrokeWidth(1.4);
        }
      }
    }
  }

  private void selectScreen(MenuScreenModel screen) {
    if (screen == null) return;
    selectedScreen = screen;
    itemTable.setItems(screen.items);
    if (!screen.items.isEmpty()) itemTable.getSelectionModel().select(0);
    wireMode = WireMode.NONE;
    refreshNodeStyles();
    refreshDiagnostics();
    updateUiState();
  }

  private void beginOpenWireMode() {
    MenuItemModel item = getSelectedItem();
    if (selectedScreen == null || item == null) {
      statusLabel.setText("Select a menu screen and an item first.");
      return;
    }
    wireMode = WireMode.OPEN_MENU;
    statusLabel.setText("Click a target menu node to wire OPEN_MENU from item '" + item.getId() + "'.");
    updateUiState();
  }

  private void applyActionToSelectedItem(MenuActionType action, String target) {
    MenuItemModel item = getSelectedItem();
    if (selectedScreen == null || item == null) {
      statusLabel.setText("Select a menu screen item first.");
      return;
    }
    item.setAction(action == null ? MenuActionType.NOOP : action);
    item.setTarget(normalize(target, ""));
    markDirty(selectedScreen);
    wireMode = WireMode.NONE;
    updateUiState();
  }

  private MenuItemModel getSelectedItem() {
    return itemTable.getSelectionModel().getSelectedItem();
  }

  private void addScreen() {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      statusLabel.setText("Open a project first.");
      return;
    }

    TextInputDialog dialog = new TextInputDialog();
    EditorTheme.apply(dialog);
    dialog.setTitle("Add Menu Screen");
    dialog.setHeaderText(null);
    dialog.setContentText("Menu id:");
    var result = dialog.showAndWait();
    if (result.isEmpty()) return;

    String id = sanitizeId(result.get());
    if (id.isBlank()) {
      statusLabel.setText("Menu id cannot be empty.");
      return;
    }
    if (screensById.containsKey(id)) {
      selectScreen(screensById.get(id));
      statusLabel.setText("Menu '" + id + "' already exists.");
      return;
    }

    File target = new File(projectRoot, DEFAULT_MENU_DIR + "/" + id + ".menu");
    MenuScreenModel screen = new MenuScreenModel(id, target, new Properties());
    screen.properties.setProperty("titleText", titleize(id));
    screen.properties.setProperty("layout", "default");
    screen.properties.setProperty("defaultItemStyle", "default");
    screen.properties.setProperty("wrapSelection", "true");
    MenuItemModel row = new MenuItemModel("back", MenuActionType.BACK, "");
    attachRowListeners(screen, row);
    screen.items.add(row);
    screen.dirty = true;

    screensById.put(id, screen);
    autoLayoutScreensIfMissing();
    selectScreen(screen);
    rebuildGraph();
    statusLabel.setText("Added screen '" + id + "'. Save to create file.");
  }

  private void addItemToSelectedScreen() {
    if (selectedScreen == null) {
      statusLabel.setText("Select a screen first.");
      return;
    }

    TextInputDialog dialog = new TextInputDialog();
    EditorTheme.apply(dialog);
    dialog.setTitle("Add Menu Item");
    dialog.setHeaderText(null);
    dialog.setContentText("Item id:");
    var result = dialog.showAndWait();
    if (result.isEmpty()) return;

    String id = sanitizeId(result.get());
    if (id.isBlank()) {
      statusLabel.setText("Item id cannot be empty.");
      return;
    }

    for (MenuItemModel row : selectedScreen.items) {
      if (id.equalsIgnoreCase(row.getId())) {
        statusLabel.setText("Item '" + id + "' already exists in " + selectedScreen.id + ".");
        return;
      }
    }

    MenuItemModel row = new MenuItemModel(id, MenuActionType.NOOP, "");
    attachRowListeners(selectedScreen, row);
    selectedScreen.items.add(row);
    selectedScreen.dirty = true;
    itemTable.getSelectionModel().select(row);
    onCurrentScreenMutated();
  }

  private void removeSelectedItem() {
    if (selectedScreen == null) return;
    MenuItemModel row = getSelectedItem();
    if (row == null) return;
    selectedScreen.items.remove(row);
    selectedScreen.dirty = true;
    onCurrentScreenMutated();
  }

  private void openSelectedScreen() {
    if (selectedScreen == null) return;
    openScreen(selectedScreen);
  }

  private void openScreen(MenuScreenModel screen) {
    if (screen == null) return;
    if (!screen.file.exists() && screen.dirty) {
      saveScreen(screen, false);
    }
    if (onOpenFile != null && screen.file.exists()) {
      onOpenFile.accept(screen.file);
      statusLabel.setText("Opened " + screen.file.getName());
    }
  }

  private boolean saveScreen(MenuScreenModel screen, boolean verbose) {
    if (screen == null) return false;

    try {
      File parent = screen.file.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();

      Properties out = new Properties();
      for (String key : screen.properties.stringPropertyNames()) {
        out.setProperty(key, screen.properties.getProperty(key, ""));
      }

      List<String> itemIds = new ArrayList<>();
      for (MenuItemModel row : screen.items) {
        String id = sanitizeId(row.getId());
        if (!id.isBlank()) itemIds.add(id);
      }

      if (itemIds.isEmpty()) {
        out.remove("items");
      } else {
        out.setProperty("items", String.join(",", itemIds));
      }

      Set<String> validIds = new HashSet<>(itemIds);
      List<String> keysToRemove = new ArrayList<>();
      for (String key : out.stringPropertyNames()) {
        if (!key.startsWith("item.")) continue;
        int secondDot = key.indexOf('.', 5);
        if (secondDot <= 5) continue;
        String itemId = key.substring(5, secondDot);
        String field = key.substring(secondDot + 1);
        if (("action".equals(field) || "target".equals(field)) && !validIds.contains(itemId)) {
          keysToRemove.add(key);
        }
      }
      for (String key : keysToRemove) out.remove(key);

      for (MenuItemModel row : screen.items) {
        String id = sanitizeId(row.getId());
        if (id.isBlank()) continue;
        String prefix = "item." + id + ".";
        MenuActionType action = row.getAction() == null ? MenuActionType.NOOP : row.getAction();
        out.setProperty(prefix + "action", canonicalActionName(action));
        String target = normalize(row.getTarget(), "");
        if (usesTarget(action) && !target.isBlank()) out.setProperty(prefix + "target", target);
        else out.remove(prefix + "target");
      }

      try (FileOutputStream fos = new FileOutputStream(screen.file)) {
        out.store(fos, "Edited via JVN Menu Flow Editor");
      }

      screen.properties.clear();
      for (String key : out.stringPropertyNames()) {
        screen.properties.setProperty(key, out.getProperty(key, ""));
      }
      screen.dirty = false;
      if (verbose) statusLabel.setText("Saved " + screen.file.getName());
      return true;
    } catch (Exception ex) {
      if (verbose) statusLabel.setText("Save failed: " + ex.getMessage());
      return false;
    }
  }

  private void refreshDiagnostics() {
    List<String> issues = collectValidationIssues();
    if (issues.isEmpty()) {
      diagnosticsArea.setText("No validation issues found.");
    } else {
      diagnosticsArea.setText(String.join(System.lineSeparator(), issues));
    }

    int dirty = 0;
    for (MenuScreenModel screen : screensById.values()) {
      if (screen.dirty) dirty++;
    }
    statusLabel.setText("Screens: " + screensById.size() + " | Dirty: " + dirty + " | Issues: " + issues.size());
  }

  private void showValidationDialog() {
    List<String> issues = collectValidationIssues();
    Alert alert;
    if (issues.isEmpty()) {
      alert = new Alert(Alert.AlertType.INFORMATION, "Menu flow validation passed.");
    } else {
      alert = new Alert(Alert.AlertType.WARNING, String.join(System.lineSeparator(), issues));
    }
    EditorTheme.apply(alert);
    alert.setHeaderText(null);
    alert.setTitle("Menu Flow Validation");
    alert.showAndWait();
    refreshDiagnostics();
  }

  private List<String> collectValidationIssues() {
    List<String> issues = new ArrayList<>();

    Set<String> allIds = new LinkedHashSet<>(screensById.keySet());
    for (MenuScreenModel screen : screensById.values()) {
      Set<String> seenItems = new HashSet<>();
      if (screen.items.isEmpty()) {
        issues.add("[WARN] menu '" + screen.id + "' has no items");
      }
      for (MenuItemModel row : screen.items) {
        String itemId = sanitizeId(row.getId());
        if (itemId.isBlank()) {
          issues.add("[ERROR] menu '" + screen.id + "' has an item with empty id");
          continue;
        }
        if (!seenItems.add(itemId)) {
          issues.add("[ERROR] menu '" + screen.id + "' has duplicate item id '" + itemId + "'");
        }

        MenuActionType action = row.getAction() == null ? MenuActionType.NOOP : row.getAction();
        String target = normalize(row.getTarget(), "");
        if (action == MenuActionType.OPEN_MENU) {
          if (target.isBlank()) {
            issues.add("[ERROR] menu '" + screen.id + "' item '" + itemId + "' OPEN_MENU missing target");
          } else if (!allIds.contains(target)) {
            issues.add("[ERROR] menu '" + screen.id + "' item '" + itemId + "' points to unknown menu '" + target + "'");
          }
        }
        if (action == MenuActionType.MAIN_MENU && !allIds.contains("main")) {
          issues.add("[ERROR] menu '" + screen.id + "' item '" + itemId + "' uses MAIN_MENU but 'main' screen is missing");
        }
        if (action == MenuActionType.LOAD_MENU && !allIds.contains("load")) {
          issues.add("[WARN] menu '" + screen.id + "' item '" + itemId + "' uses LOAD_MENU but 'load' screen is missing");
        }
        if (action == MenuActionType.SAVE_MENU && !allIds.contains("save")) {
          issues.add("[WARN] menu '" + screen.id + "' item '" + itemId + "' uses SAVE_MENU but 'save' screen is missing");
        }
        if (action == MenuActionType.SETTINGS_MENU && !allIds.contains("settings")) {
          issues.add("[WARN] menu '" + screen.id + "' item '" + itemId + "' uses SETTINGS_MENU but 'settings' screen is missing");
        }
      }
    }

    if (!screensById.isEmpty() && screensById.containsKey("main")) {
      Set<String> reachable = computeReachableMenus("main");
      for (String id : screensById.keySet()) {
        if (!reachable.contains(id)) {
          issues.add("[WARN] menu '" + id + "' is unreachable from main");
        }
      }
    }

    return issues;
  }

  private Set<String> computeReachableMenus(String start) {
    Set<String> visited = new LinkedHashSet<>();
    if (start == null || !screensById.containsKey(start)) return visited;

    List<String> queue = new ArrayList<>();
    queue.add(start);
    visited.add(start);

    while (!queue.isEmpty()) {
      String current = queue.remove(0);
      MenuScreenModel screen = screensById.get(current);
      if (screen == null) continue;
      for (MenuItemModel row : screen.items) {
        String next = resolveTargetMenu(row.getAction(), row.getTarget());
        if (next == null || NODE_BACK.equals(next)) continue;
        if (!screensById.containsKey(next)) continue;
        if (visited.add(next)) queue.add(next);
      }
    }

    return visited;
  }

  private void updateUiState() {
    boolean hasProject = projectRoot != null && projectRoot.isDirectory();
    boolean hasSelection = selectedScreen != null;
    boolean hasItem = getSelectedItem() != null;

    saveSelectedButton.setDisable(!hasSelection);
    saveAllButton.setDisable(!hasProject);
    validateButton.setDisable(!hasProject);
    autoLayoutButton.setDisable(screensById.isEmpty());
    openButton.setDisable(!hasSelection);
    addScreenButton.setDisable(!hasProject);

    wireOpenButton.setDisable(!(hasSelection && hasItem));
    setMainButton.setDisable(!(hasSelection && hasItem));
    setBackButton.setDisable(!(hasSelection && hasItem));
    clearTargetButton.setDisable(!(hasSelection && hasItem));
    addItemButton.setDisable(!hasSelection);
    removeItemButton.setDisable(!(hasSelection && hasItem));

    if (selectedScreen == null) {
      selectedMenuLabel.setText("Menu: (none)");
      selectedFileLabel.setText("File: -");
    } else {
      selectedMenuLabel.setText("Menu: " + selectedScreen.id + (selectedScreen.dirty ? " *" : ""));
      selectedFileLabel.setText("File: " + toRelativeProjectPath(selectedScreen.file));
    }

    wireModeLabel.setText(wireMode == WireMode.OPEN_MENU
        ? "Wire mode: OPEN_MENU (click target node)"
        : "Wire mode: Off");
  }

  private Set<String> discoverMenuIds(File root, Properties manifest) {
    Set<String> ids = new LinkedHashSet<>();
    ids.add("main");

    String registryPath = manifestPath(manifest, "menuRegistry", DEFAULT_MENU_REGISTRY_PATH);
    Properties registry = loadProperties(new File(root, registryPath));
    ids.addAll(parseCsv(registry.getProperty("menus")));

    ids.addAll(scanMenuIds(new File(root, "config/menu/menus")));
    ids.addAll(scanMenuIds(new File(root, "config/menu")));

    return ids;
  }

  private Set<String> scanMenuIds(File dir) {
    Set<String> ids = new LinkedHashSet<>();
    if (dir == null || !dir.exists() || !dir.isDirectory()) return ids;
    File[] files = dir.listFiles();
    if (files == null) return ids;
    for (File file : files) {
      if (file == null || !file.isFile()) continue;
      String name = file.getName().toLowerCase(Locale.ROOT);
      if (name.endsWith(".menu") && name.length() > ".menu".length()) {
        ids.add(file.getName().substring(0, file.getName().length() - ".menu".length()));
      }
    }
    return ids;
  }

  private File resolveMenuFile(File root, String id) {
    List<File> candidates = new ArrayList<>();
    candidates.add(new File(root, "config/menu/menus/" + id + ".menu"));
    candidates.add(new File(root, "config/menu/" + id + ".menu"));
    for (File file : candidates) {
      if (file.exists()) return file;
    }
    return candidates.get(0);
  }

  private void autoLayoutScreensIfMissing() {
    boolean needsLayout = false;
    for (MenuScreenModel screen : screensById.values()) {
      if (!Double.isFinite(screen.x) || !Double.isFinite(screen.y)) {
        needsLayout = true;
        break;
      }
    }
    if (needsLayout) autoLayoutScreens();
  }

  private void autoLayoutScreens() {
    if (screensById.isEmpty()) return;
    int count = screensById.size();
    int cols = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
    int idx = 0;
    for (MenuScreenModel screen : screensById.values()) {
      int row = idx / cols;
      int col = idx % cols;
      screen.x = 40 + col * (NODE_WIDTH + 86);
      screen.y = 40 + row * (NODE_HEIGHT + 78);
      rememberedScreenPositions.put(screen.id, new Point2D(screen.x, screen.y));
      idx++;
    }
  }

  private String resolveTargetMenu(MenuActionType action, String rawTarget) {
    if (action == null) return null;
    return switch (action) {
      case OPEN_MENU -> {
        String t = sanitizeId(rawTarget);
        yield t.isBlank() ? null : t;
      }
      case MAIN_MENU -> "main";
      case LOAD_MENU -> "load";
      case SAVE_MENU -> "save";
      case SETTINGS_MENU -> "settings";
      case BACK -> NODE_BACK;
      default -> null;
    };
  }

  private static boolean usesTarget(MenuActionType action) {
    return action == MenuActionType.OPEN_MENU || action == MenuActionType.RUN_SCRIPT;
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

  private static List<String> collectItemIds(Properties properties) {
    Set<String> ids = new LinkedHashSet<>(parseCsv(properties.getProperty("items")));
    for (String key : properties.stringPropertyNames()) {
      if (!key.startsWith("item.")) continue;
      int dot = key.indexOf('.', 5);
      if (dot <= 5) continue;
      String id = key.substring(5, dot).trim();
      if (!id.isBlank()) ids.add(id);
    }
    return new ArrayList<>(ids);
  }

  private static List<String> parseCsv(String raw) {
    List<String> out = new ArrayList<>();
    if (raw == null || raw.isBlank()) return out;
    String[] parts = raw.split(",");
    for (String part : parts) {
      String id = sanitizeId(part);
      if (!id.isBlank()) out.add(id);
    }
    return out;
  }

  private static Properties loadManifest(File root) {
    return loadProperties(new File(root, "jvn.project"));
  }

  private static Properties loadProperties(File file) {
    Properties properties = new Properties();
    if (file == null || !file.exists() || !file.isFile()) return properties;
    try (FileInputStream in = new FileInputStream(file)) {
      properties.load(in);
    } catch (Exception ignored) {
    }
    return properties;
  }

  private static String manifestPath(Properties manifest, String key, String fallback) {
    if (manifest == null) return fallback;
    String value = normalize(manifest.getProperty(key), fallback);
    return value.replace('\\', '/');
  }

  private String toRelativeProjectPath(File file) {
    if (file == null) return "-";
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

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isBlank() ? fallback : t;
  }

  private static String sanitizeId(String raw) {
    if (raw == null) return "";
    String t = raw.trim().toLowerCase(Locale.ROOT);
    if (t.isBlank()) return "";
    t = t.replace('-', '_').replace(' ', '_');
    t = t.replaceAll("[^a-z0-9_]", "");
    t = t.replaceAll("_+", "_");
    if (t.startsWith("_")) t = t.substring(1);
    if (t.endsWith("_")) t = t.substring(0, t.length() - 1);
    return t;
  }

  private static String titleize(String raw) {
    String source = normalize(raw, "Menu").replace('_', ' ').replace('-', ' ');
    if (source.isBlank()) return "Menu";
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
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

  private enum NodeKind { SCREEN, SPECIAL, MISSING }

  private static final class MenuEdge {
    final String fromScreenId;
    final String fromItemId;
    final MenuActionType action;
    final String targetMenuId;
    final String toNodeId;
    final boolean missingTarget;

    MenuEdge(String fromScreenId, String fromItemId, MenuActionType action, String targetMenuId, String toNodeId, boolean missingTarget) {
      this.fromScreenId = fromScreenId;
      this.fromItemId = fromItemId;
      this.action = action;
      this.targetMenuId = targetMenuId;
      this.toNodeId = toNodeId;
      this.missingTarget = missingTarget;
    }
  }

  private static final class NodeView extends StackPane {
    final String nodeId;
    final NodeKind kind;
    final MenuScreenModel screen;
    final Rectangle box;

    NodeView(String nodeId, String label, NodeKind kind, MenuScreenModel screen) {
      this.nodeId = nodeId;
      this.kind = kind;
      this.screen = screen;
      this.box = new Rectangle(NODE_WIDTH, NODE_HEIGHT);
      box.setArcWidth(10);
      box.setArcHeight(10);

      Label title = new Label(label);
      title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #e4e8ef;");
      Label subtitle = new Label(kind == NodeKind.SCREEN ? "screen" : (kind == NodeKind.SPECIAL ? "special" : "missing"));
      subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #a5acb9;");

      VBox content = new VBox(4, title, subtitle);
      content.setAlignment(Pos.CENTER_LEFT);
      content.setPadding(new Insets(8, 10, 8, 10));

      getChildren().addAll(box, content);
      setAlignment(Pos.CENTER_LEFT);

      switch (kind) {
        case SCREEN -> {
          box.setFill(Color.web("#242833"));
          box.setStroke(Color.web("#4f5665"));
        }
        case SPECIAL -> {
          box.setFill(Color.web("#2e2a1f"));
          box.setStroke(Color.web("#8a7454"));
        }
        case MISSING -> {
          box.setFill(Color.web("#3a2020"));
          box.setStroke(Color.web("#d87676"));
        }
      }
      box.setStrokeWidth(1.4);
    }
  }

  private static final class MenuScreenModel {
    final String id;
    final File file;
    final Properties properties;
    final ObservableList<MenuItemModel> items = FXCollections.observableArrayList();
    boolean dirty;
    double x = Double.NaN;
    double y = Double.NaN;

    MenuScreenModel(String id, File file, Properties properties) {
      this.id = id;
      this.file = file;
      this.properties = properties == null ? new Properties() : properties;
      this.dirty = false;
    }
  }

  private static final class MenuItemModel {
    private final StringProperty id = new SimpleStringProperty("");
    private final ObjectProperty<MenuActionType> action = new SimpleObjectProperty<>(MenuActionType.NOOP);
    private final StringProperty target = new SimpleStringProperty("");

    MenuItemModel(String id, MenuActionType action, String target) {
      setId(id);
      setAction(action);
      setTarget(target);
    }

    String getId() { return id.get(); }
    void setId(String value) { id.set(normalize(value, "")); }
    StringProperty idProperty() { return id; }

    MenuActionType getAction() { return action.get(); }
    void setAction(MenuActionType value) { action.set(value == null ? MenuActionType.NOOP : value); }
    ObjectProperty<MenuActionType> actionProperty() { return action; }

    String getTarget() { return target.get(); }
    void setTarget(String value) { target.set(normalize(value, "")); }
    StringProperty targetProperty() { return target; }
  }
}
