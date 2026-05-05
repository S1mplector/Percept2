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
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
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
  private final Label selectedItemLabel = new Label("Item: (none)");
  private final Label wireModeLabel = new Label("Wire mode: Off");
  private final Label wireHintLabel = new Label("Select an item, then wire on canvas or use quick target.");
  private final Label registryPathLabel = new Label("Registry: " + DEFAULT_MENU_REGISTRY_PATH);
  private final Label registryMenusLabel = new Label("Menus: (auto-discovery)");
  private final Label registrySelectionLabel = new Label("Selected screen: (none)");
  private final Label graphLegendLabel = new Label("OPEN_MENU   MAIN_MENU   BACK   missing target");

  private final Button refreshButton = iconBtn("Refresh", CssIcon.redo("#7ec8e3"));
  private final Button validateButton = iconBtn("Validate", CssIcon.check("#8cd48c"));
  private final Button saveSelectedButton = iconBtn("Save Screen", CssIcon.save("#a8d0f0"));
  private final Button saveAllButton = iconBtn("Save All", CssIcon.save("#d4a8e8"));
  private final Button autoLayoutButton = iconBtn("Auto Layout", CssIcon.grid("#e8c8a8"));
  private final Button openButton = iconBtn("Open Screen", CssIcon.expand("#7ec8e3"));
  private final Button addScreenButton = iconBtn("Add Screen", CssIcon.plus("#8cd48c"));

  private final Button wireOpenButton = iconBtn("Wire on Graph", CssIcon.link("#e8c8a8"));
  private final Button cancelWireButton = iconBtn("Cancel Wire", CssIcon.clearX("#f0a080"));
  private final Button setMainButton = iconBtn("Set MAIN_MENU", CssIcon.home("#a8d0f0"));
  private final Button setBackButton = iconBtn("Set BACK", CssIcon.undo("#d4a8e8"));
  private final Button clearTargetButton = iconBtn("Clear Target", CssIcon.clearX("#f0a080"));
  private final ComboBox<String> quickTargetCombo = new ComboBox<>();
  private final Button applyQuickTargetButton = iconBtn("Set OPEN_MENU", CssIcon.check("#8cd48c"));
  private final TextField quickAddItemField = new TextField();
  private final Button addItemButton = iconBtn("Add Item", CssIcon.plus("#8cd48c"));
  private final Button duplicateItemButton = iconBtn("Duplicate", CssIcon.copy("#a8d0f0"));
  private final Button removeItemButton = iconBtn("Remove Item", CssIcon.minus("#f0a080"));
  private final ComboBox<String> defaultMenuCombo = new ComboBox<>();
  private final TextField registryLayoutsField = new TextField();
  private final TextField registryStylesField = new TextField();
  private final Button registerSelectedButton = iconBtn("Register Selected", CssIcon.plus("#8cd48c"));
  private final Button unregisterSelectedButton = iconBtn("Unregister Selected", CssIcon.minus("#f0a080"));
  private final Button syncRegistryMenusButton = iconBtn("Sync Screens", CssIcon.redo("#a8d0f0"));
  private final Button saveRegistryButton = iconBtn("Save Registry", CssIcon.save("#8cd48c"));
  private final Button openRegistryButton = iconBtn("Open Registry", CssIcon.expand("#7ec8e3"));

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
  private final LinkedHashSet<String> registryMenus = new LinkedHashSet<>();
  private final LinkedHashSet<String> registryLayouts = new LinkedHashSet<>();
  private final LinkedHashSet<String> registryStyles = new LinkedHashSet<>();

  private MenuScreenModel selectedScreen;
  private boolean suppressRowEvents;
  private boolean suppressRegistryEvents;
  private boolean registryFileExists;
  private boolean registryDirty;
  private String registryRelativePath = DEFAULT_MENU_REGISTRY_PATH;
  private String registryDefaultMenu = "";

  private enum WireMode { NONE, OPEN_MENU }
  private WireMode wireMode = WireMode.NONE;

  public MenuFlowEditorView() {
    setPadding(new Insets(8));
    getStyleClass().addAll("menu-flow-editor", "sidebar-tool-root");
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
    Label title = new Label("Menu Flow Editor");
    title.getStyleClass().addAll("menu-flow-title", "sidebar-tool-title");

    projectLabel.getStyleClass().add("menu-flow-project-label");
    projectLabel.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
    projectLabel.setMaxWidth(Double.MAX_VALUE);

    statusLabel.getStyleClass().add("menu-flow-status-label");

    FlowPane toolbar = new FlowPane(8, 8,
        refreshButton,
        validateButton,
        saveSelectedButton,
        saveAllButton,
        autoLayoutButton,
        openButton,
        addScreenButton);
    toolbar.getStyleClass().add("menu-flow-toolbar");
    toolbar.setAlignment(Pos.CENTER_LEFT);

    VBox top = new VBox(8, title, projectLabel, toolbar, statusLabel);
    top.getStyleClass().addAll("menu-flow-header", "sidebar-tool-header");
    setTop(top);

    graphScroll.getStyleClass().add("menu-flow-graph-scroll");
    graphScroll.setFitToWidth(false);
    graphScroll.setFitToHeight(false);
    graphScroll.setPannable(true);
    graphScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    graphScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    graphPane.setPrefSize(1200, 760);
    graphPane.getStyleClass().add("menu-flow-graph-pane");
    graphPane.setFocusTraversable(true);

    graphEmptyLabel.getStyleClass().add("menu-flow-empty-label");
    graphEmptyLabel.setMouseTransparent(true);
    StackPane.setAlignment(graphEmptyLabel, Pos.CENTER);

    graphLegendLabel.getStyleClass().add("menu-flow-graph-legend");
    graphLegendLabel.setMouseTransparent(true);
    StackPane.setAlignment(graphLegendLabel, Pos.TOP_LEFT);
    StackPane.setMargin(graphLegendLabel, new Insets(12));

    StackPane graphHost = new StackPane(graphScroll, graphEmptyLabel, graphLegendLabel);
    graphHost.getStyleClass().add("menu-flow-graph-host");

    buildItemTable();

    diagnosticsArea.setEditable(false);
    diagnosticsArea.setWrapText(true);
    diagnosticsArea.setPrefRowCount(5);
    diagnosticsArea.setMinHeight(96);
    diagnosticsArea.getStyleClass().add("menu-flow-diagnostics");

    for (Label label : List.of(selectedMenuLabel, selectedFileLabel, selectedItemLabel, wireModeLabel,
        wireHintLabel, registryPathLabel, registryMenusLabel, registrySelectionLabel)) {
      label.getStyleClass().add("menu-flow-meta-label");
      label.setWrapText(true);
    }
    wireHintLabel.setWrapText(true);
    registryPathLabel.setWrapText(true);
    registryPathLabel.getStyleClass().add("menu-flow-mono-label");
    registryMenusLabel.setWrapText(true);
    registrySelectionLabel.setWrapText(true);

    quickTargetCombo.setEditable(true);
    quickTargetCombo.setPromptText("target menu id");
    quickTargetCombo.setMaxWidth(Double.MAX_VALUE);
    quickTargetCombo.valueProperty().addListener((o, ov, nv) -> updateUiState());
    quickTargetCombo.getEditor().textProperty().addListener((o, ov, nv) -> updateUiState());

    defaultMenuCombo.setEditable(true);
    defaultMenuCombo.setPromptText("main");
    defaultMenuCombo.setMaxWidth(Double.MAX_VALUE);
    defaultMenuCombo.valueProperty().addListener((o, ov, nv) -> {
      if (suppressRegistryEvents) return;
      registryDirty = true;
      updateUiState();
    });
    defaultMenuCombo.getEditor().textProperty().addListener((o, ov, nv) -> {
      if (suppressRegistryEvents) return;
      registryDirty = true;
      updateUiState();
    });

    registryLayoutsField.setPromptText("default, compact");
    registryLayoutsField.textProperty().addListener((o, ov, nv) -> {
      if (suppressRegistryEvents) return;
      registryDirty = true;
      updateUiState();
    });
    registryStylesField.setPromptText("default, neon");
    registryStylesField.textProperty().addListener((o, ov, nv) -> {
      if (suppressRegistryEvents) return;
      registryDirty = true;
      updateUiState();
    });

    quickAddItemField.setPromptText("new_item_id");

    wireOpenButton.setTooltip(new Tooltip("Select an item, then click a target node in the graph."));
    cancelWireButton.setTooltip(new Tooltip("Exit wiring mode (Esc also works)."));
    applyQuickTargetButton.setTooltip(new Tooltip("Set selected item to OPEN_MENU and assign the chosen target."));
    addItemButton.setTooltip(new Tooltip("Add item from the field (or open prompt when empty)."));
    duplicateItemButton.setTooltip(new Tooltip("Duplicate the selected item."));

    FlowPane wireButtons = new FlowPane(6, 6, wireOpenButton, cancelWireButton, setMainButton, setBackButton, clearTargetButton);
    wireButtons.getStyleClass().add("menu-flow-button-flow");
    wireButtons.setAlignment(Pos.CENTER_LEFT);

    HBox targetRow = new HBox(6, quickTargetCombo, applyQuickTargetButton);
    targetRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(quickTargetCombo, Priority.ALWAYS);

    HBox quickAddRow = new HBox(6, quickAddItemField, addItemButton);
    quickAddRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(quickAddItemField, Priority.ALWAYS);

    FlowPane itemButtons = new FlowPane(6, 6, duplicateItemButton, removeItemButton);
    itemButtons.getStyleClass().add("menu-flow-button-flow");
    itemButtons.setAlignment(Pos.CENTER_LEFT);

    HBox defaultMenuRow = labeledInput("Default", defaultMenuCombo);
    HBox layoutsRow = labeledInput("Layouts", registryLayoutsField);
    HBox stylesRow = labeledInput("Styles", registryStylesField);
    FlowPane registryButtons = new FlowPane(6, 6,
        registerSelectedButton, unregisterSelectedButton, syncRegistryMenusButton, saveRegistryButton, openRegistryButton);
    registryButtons.getStyleClass().add("menu-flow-button-flow");
    registryButtons.setAlignment(Pos.CENTER_LEFT);

    VBox selectionCard = inspectorCard("Selection",
        selectedMenuLabel,
        selectedFileLabel,
        selectedItemLabel);

    VBox registryCard = inspectorCard("Registry Wiring",
        registryPathLabel,
        registryMenusLabel,
        registrySelectionLabel,
        defaultMenuRow,
        layoutsRow,
        stylesRow,
        registryButtons);

    VBox wiringCard = inspectorCard("Wiring",
        wireModeLabel,
        wireHintLabel,
        wireButtons,
        targetRow);

    VBox controlsStack = new VBox(10, selectionCard, registryCard, wiringCard);
    controlsStack.getStyleClass().add("menu-flow-inspector-stack");

    ScrollPane controlsScroll = new ScrollPane(controlsStack);
    controlsScroll.getStyleClass().add("menu-flow-inspector-scroll");
    controlsScroll.setFitToWidth(true);
    controlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    controlsScroll.setMinHeight(220);

    VBox itemsCard = inspectorCard("Items",
        quickAddRow,
        itemButtons,
        itemTable);
    VBox.setVgrow(itemTable, Priority.ALWAYS);

    VBox validationCard = inspectorCard("Validation", diagnosticsArea);
    VBox.setVgrow(diagnosticsArea, Priority.ALWAYS);

    SplitPane detailSplit = new SplitPane(controlsScroll, itemsCard, validationCard);
    detailSplit.setOrientation(Orientation.VERTICAL);
    detailSplit.setDividerPositions(0.48, 0.76);
    detailSplit.getStyleClass().add("menu-flow-inspector-split");

    BorderPane detail = new BorderPane(detailSplit);
    detail.getStyleClass().add("menu-flow-inspector");
    detail.setMinWidth(360);
    detail.setPrefWidth(460);

    SplitPane split = new SplitPane(graphHost, detail);
    split.getStyleClass().add("menu-flow-main-split");
    split.setDividerPositions(0.70);
    setCenter(split);
  }

  private void buildItemTable() {
    itemTable.setEditable(true);
    itemTable.getStyleClass().add("menu-flow-table");
    itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    itemTable.setFixedCellSize(30);
    itemTable.setMinHeight(150);
    itemTable.setPrefHeight(220);

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
      if (!usesTarget(row.getAction()) && !row.isCustomAction()) row.setTarget("");
      onCurrentScreenMutated();
    });

    TableColumn<MenuItemModel, String> actionKeyCol = new TableColumn<>("Action Key");
    actionKeyCol.setCellValueFactory(v -> v.getValue().actionKeyProperty());
    actionKeyCol.setCellFactory(TextFieldTableCell.forTableColumn());
    actionKeyCol.setOnEditCommit(e -> {
      MenuItemModel row = e.getRowValue();
      if (row == null) return;
      row.setActionKey(normalize(e.getNewValue(), "noop"));
      if (!usesTarget(row.getAction()) && !row.isCustomAction()) row.setTarget("");
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
    actionCol.setPrefWidth(140);
    actionKeyCol.setPrefWidth(160);
    targetCol.setPrefWidth(130);

    itemTable.getColumns().setAll(idCol, actionCol, actionKeyCol, targetCol);
    itemTable.setPlaceholder(new Label("No items. Add one from the field above."));
    itemTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      if (nv != null && usesTarget(nv.getAction())) {
        quickTargetCombo.getEditor().setText(normalize(nv.getTarget(), ""));
      }
      updateUiState();
    });
    itemTable.setRowFactory(tv -> {
      TableRow<MenuItemModel> row = new TableRow<>();
      ContextMenu menu = new ContextMenu();
      MenuItem wire = new MenuItem("Wire OPEN_MENU on Graph");
      wire.setOnAction(e -> {
        itemTable.getSelectionModel().select(row.getItem());
        beginOpenWireMode();
      });
      MenuItem duplicate = new MenuItem("Duplicate Item");
      duplicate.setOnAction(e -> {
        itemTable.getSelectionModel().select(row.getItem());
        duplicateSelectedItem();
      });
      MenuItem remove = new MenuItem("Remove Item");
      remove.setOnAction(e -> {
        itemTable.getSelectionModel().select(row.getItem());
        removeSelectedItem();
      });
      menu.getItems().addAll(wire, duplicate, remove);
      row.emptyProperty().addListener((o, ov, nv) -> row.setContextMenu(nv ? null : menu));
      return row;
    });
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

    registerSelectedButton.setOnAction(e -> registerSelectedScreen());
    unregisterSelectedButton.setOnAction(e -> unregisterSelectedScreen());
    syncRegistryMenusButton.setOnAction(e -> syncRegistryMenusToDiscoveredScreens());
    saveRegistryButton.setOnAction(e -> saveRegistryState(true));
    openRegistryButton.setOnAction(e -> openRegistryFile());

    wireOpenButton.setOnAction(e -> {
      if (wireMode == WireMode.OPEN_MENU) {
        cancelWireMode("Wire mode canceled.");
      } else {
        beginOpenWireMode();
      }
    });
    cancelWireButton.setOnAction(e -> cancelWireMode("Wire mode canceled."));
    setMainButton.setOnAction(e -> applyActionToSelectedItem(MenuActionType.MAIN_MENU, ""));
    setBackButton.setOnAction(e -> applyActionToSelectedItem(MenuActionType.BACK, ""));
    clearTargetButton.setOnAction(e -> {
      MenuItemModel row = getSelectedItem();
      if (row == null) return;
      row.setTarget("");
      onCurrentScreenMutated();
    });
    applyQuickTargetButton.setOnAction(e -> applyQuickOpenTarget());

    addItemButton.setOnAction(e -> addItemFromSidebarInput());
    quickAddItemField.setOnAction(e -> addItemFromSidebarInput());
    duplicateItemButton.setOnAction(e -> duplicateSelectedItem());
    removeItemButton.setOnAction(e -> removeSelectedItem());

    itemTable.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
        removeSelectedItem();
        e.consume();
        return;
      }
      if (e.isShortcutDown() && e.getCode() == KeyCode.D) {
        duplicateSelectedItem();
        e.consume();
      }
    });

    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (e.getCode() == KeyCode.ESCAPE && wireMode != WireMode.NONE) {
        cancelWireMode("Wire mode canceled.");
        e.consume();
      }
    });

    graphPane.setOnMouseClicked(e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (wireMode != WireMode.NONE) {
        cancelWireMode("Wire mode canceled.");
      }
    });
  }

  private void loadProjectMenus() {
    screensById.clear();
    selectedScreen = null;
    wireMode = WireMode.NONE;

    if (projectRoot == null || !projectRoot.isDirectory()) {
      clearRegistryState();
      projectLabel.setText("No project loaded.");
      statusLabel.setText("Open a project to inspect menu flow.");
      itemTable.setItems(FXCollections.observableArrayList());
      refreshQuickTargetOptions();
      refreshRegistryEditorState();
      rebuildGraph();
      refreshDiagnostics();
      updateUiState();
      return;
    }

    projectLabel.setText("Project: " + projectRoot.getAbsolutePath());

    Properties manifest = loadManifest(projectRoot);
    loadRegistryState(projectRoot, manifest);
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
    refreshQuickTargetOptions();
    refreshRegistryEditorState();
    rebuildGraph();
    refreshDiagnostics();
    updateUiState();
    statusLabel.setText("Loaded " + screensById.size() + " menu screen(s).");
  }

  private void clearRegistryState() {
    registryFileExists = false;
    registryDirty = false;
    registryRelativePath = DEFAULT_MENU_REGISTRY_PATH;
    registryDefaultMenu = "";
    registryMenus.clear();
    registryLayouts.clear();
    registryStyles.clear();
  }

  private void loadRegistryState(File root, Properties manifest) {
    clearRegistryState();
    if (root == null) return;
    registryRelativePath = manifestPath(manifest, "menuRegistry", DEFAULT_MENU_REGISTRY_PATH);
    File registryFile = new File(root, registryRelativePath);
    registryFileExists = registryFile.exists() && registryFile.isFile();
    Properties registry = loadProperties(registryFile);
    registryDefaultMenu = sanitizeId(normalize(registry.getProperty("defaultMenu", registry.getProperty("defaultScreen")), ""));
    registryMenus.addAll(parseCsv(registry.getProperty("menus")));
    registryLayouts.addAll(parseCsv(registry.getProperty("layouts")));
    registryStyles.addAll(parseCsv(registry.getProperty("styles")));
  }

  private void refreshRegistryEditorState() {
    suppressRegistryEvents = true;
    try {
      registryPathLabel.setText("Registry: " + registryRelativePath
          + (registryFileExists ? "" : " (missing, runtime auto-discovers)"));
      registryMenusLabel.setText("Menus: "
          + (registryMenus.isEmpty() ? "(auto-discovery / none declared)" : String.join(", ", registryMenus)));
      defaultMenuCombo.getItems().setAll(screensById.keySet());
      defaultMenuCombo.getEditor().setText(registryDefaultMenu);
      registryLayoutsField.setText(String.join(", ", registryLayouts));
      registryStylesField.setText(String.join(", ", registryStyles));
      updateRegistrySelectionLabel();
    } finally {
      suppressRegistryEvents = false;
    }
  }

  private void updateRegistrySelectionLabel() {
    if (selectedScreen == null) {
      registrySelectionLabel.setText("Selected screen: (none)");
      return;
    }
    boolean registered = registryMenus.contains(selectedScreen.id);
    String prefix = registered ? "Selected screen: registered" : "Selected screen: not registered";
    String suffix = registryDefaultMenu.equals(selectedScreen.id) ? " • defaultMenu" : "";
    registrySelectionLabel.setText(prefix + " (" + selectedScreen.id + ")" + suffix);
  }

  private void captureRegistryStateFromUi() {
    registryDefaultMenu = sanitizeId(normalize(defaultMenuCombo.getEditor().getText(), ""));
    registryLayouts.clear();
    registryLayouts.addAll(parseCsv(registryLayoutsField.getText()));
    registryStyles.clear();
    registryStyles.addAll(parseCsv(registryStylesField.getText()));
  }

  private void registerSelectedScreen() {
    if (selectedScreen == null) {
      statusLabel.setText("Select a menu screen first.");
      return;
    }
    captureRegistryStateFromUi();
    if (registryMenus.add(selectedScreen.id)) {
      registryDirty = true;
      if (registryDefaultMenu.isBlank()) {
        registryDefaultMenu = selectedScreen.id;
      }
      refreshRegistryEditorState();
      refreshDiagnostics();
      updateUiState();
      statusLabel.setText("Registered '" + selectedScreen.id + "' in menu.registry.");
      return;
    }
    statusLabel.setText("Menu '" + selectedScreen.id + "' is already registered.");
  }

  private void unregisterSelectedScreen() {
    if (selectedScreen == null) {
      statusLabel.setText("Select a menu screen first.");
      return;
    }
    captureRegistryStateFromUi();
    if (!registryMenus.remove(selectedScreen.id)) {
      statusLabel.setText("Menu '" + selectedScreen.id + "' is not registered.");
      return;
    }
    if (selectedScreen.id.equals(registryDefaultMenu)) {
      registryDefaultMenu = registryMenus.stream().findFirst().orElse("");
    }
    registryDirty = true;
    refreshRegistryEditorState();
    refreshDiagnostics();
    updateUiState();
    statusLabel.setText("Unregistered '" + selectedScreen.id + "' from menu.registry.");
  }

  private void syncRegistryMenusToDiscoveredScreens() {
    captureRegistryStateFromUi();
    registryMenus.clear();
    registryMenus.addAll(screensById.keySet());
    if (registryDefaultMenu.isBlank() || !registryMenus.contains(registryDefaultMenu)) {
      registryDefaultMenu = screensById.containsKey("main")
          ? "main"
          : screensById.keySet().stream().findFirst().orElse("");
    }
    registryDirty = true;
    refreshRegistryEditorState();
    refreshDiagnostics();
    updateUiState();
    statusLabel.setText("Registry menus synced to discovered screens.");
  }

  private void saveRegistryState(boolean verbose) {
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    captureRegistryStateFromUi();
    File registryFile = new File(projectRoot, registryRelativePath);
    Properties out = new Properties();
    if (!registryDefaultMenu.isBlank()) out.setProperty("defaultMenu", registryDefaultMenu);
    if (!registryMenus.isEmpty()) out.setProperty("menus", String.join(",", registryMenus));
    if (!registryLayouts.isEmpty()) out.setProperty("layouts", String.join(",", registryLayouts));
    if (!registryStyles.isEmpty()) out.setProperty("styles", String.join(",", registryStyles));
    try {
      File parent = registryFile.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();
      try (FileOutputStream fos = new FileOutputStream(registryFile)) {
        out.store(fos, "Edited via JVN Menu Flow Editor");
      }
      registryFileExists = true;
      registryDirty = false;
      refreshRegistryEditorState();
      refreshDiagnostics();
      updateUiState();
      if (verbose) statusLabel.setText("Saved " + toRelativeProjectPath(registryFile));
    } catch (Exception ex) {
      if (verbose) statusLabel.setText("Registry save failed: " + ex.getMessage());
    }
  }

  private void openRegistryFile() {
    if (projectRoot == null || onOpenFile == null) return;
    File registryFile = new File(projectRoot, registryRelativePath);
    if (!registryFile.exists()) {
      try {
        File parent = registryFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(registryFile)) {
          Properties template = new Properties();
          template.setProperty("defaultMenu", "main");
          template.setProperty("menus", "main");
          template.store(fos, "Menu registry");
        }
      } catch (Exception ex) {
        statusLabel.setText("Failed to create registry file: " + ex.getMessage());
        return;
      }
      registryFileExists = true;
      loadRegistryState(projectRoot, loadManifest(projectRoot));
      refreshRegistryEditorState();
      refreshDiagnostics();
      updateUiState();
    }
    onOpenFile.accept(registryFile);
    statusLabel.setText("Opened " + toRelativeProjectPath(registryFile));
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
      MenuItemModel row = new MenuItemModel(key, action.actionKey(), normalize(action.target(), ""));
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
      if (!usesTarget(row.getAction()) && !row.isCustomAction()) row.setTarget("");
      markDirty(screen);
    });
    row.actionKeyProperty().addListener((o, ov, nv) -> {
      if (suppressRowEvents) return;
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
      double backY = screensById.values().stream()
          .mapToDouble(s -> Double.isFinite(s.y) ? s.y : 40)
          .max()
          .orElse(40) + NODE_HEIGHT + 58;
      back.setLayoutX(44);
      back.setLayoutY(backY);
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

    MenuItemModel selectedItem = getSelectedItem();
    boolean fromSelectedScreen = selectedScreen != null && selectedScreen.id.equals(edge.fromScreenId);
    boolean selectedItemEdge = fromSelectedScreen
        && selectedItem != null
        && sanitizeId(selectedItem.getId()).equals(edge.fromItemId);
    boolean dimForSelection = selectedScreen != null && !fromSelectedScreen;

    boolean forward = tx >= sx;
    double bend = Math.max(72, Math.abs(tx - sx) * 0.34);
    double c1x = sx + (forward ? bend : -bend);
    double c2x = tx + (forward ? -bend : bend);
    CubicCurve curve = new CubicCurve(sx, sy, c1x, sy, c2x, ty, tx, ty);
    curve.setFill(Color.TRANSPARENT);
    curve.setStroke(color);
    curve.setStrokeWidth(selectedItemEdge ? 3.2 : (edge.missingTarget ? 1.4 : 2.0));
    curve.setOpacity(selectedItemEdge ? 1.0 : (dimForSelection ? 0.26 : (fromSelectedScreen ? 0.88 : 0.58)));
    if (edge.missingTarget) curve.getStrokeDashArray().setAll(8.0, 6.0);

    Polygon arrow = createArrowHead(c2x, ty, tx, ty, color);
    arrow.setOpacity(curve.getOpacity());

    String tip = edge.fromScreenId + "." + edge.fromItemId + " -> " + canonicalActionName(edge.action)
        + (edge.targetMenuId == null || edge.targetMenuId.isBlank() ? "" : (" (" + edge.targetMenuId + ")"));
    Tooltip tooltip = new Tooltip(tip);
    Tooltip.install(curve, tooltip);
    Tooltip.install(arrow, tooltip);

    edgeLayer.getChildren().addAll(curve, arrow);
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
          quickTargetCombo.getEditor().setText(screen.id);
          statusLabel.setText("Wired '" + selectedItem.getId() + "' to '" + screen.id + "'.");
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
        Point2D parentPoint = node.getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        dragDelta[0] = parentPoint.getX() - node.getLayoutX();
        dragDelta[1] = parentPoint.getY() - node.getLayoutY();
      });
      node.setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        Point2D parentPoint = node.getParent().sceneToLocal(e.getSceneX(), e.getSceneY());
        double nx = Math.max(12, parentPoint.getX() - dragDelta[0]);
        double ny = Math.max(12, parentPoint.getY() - dragDelta[1]);
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
    boolean wireCandidates = wireMode == WireMode.OPEN_MENU && selectedScreen != null && getSelectedItem() != null;
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
        if (!selected && wireCandidates) {
          node.box.setStroke(Color.web("#78d0ff"));
          node.box.setFill(Color.web("#213247"));
          node.box.setStrokeWidth(1.9);
        }
        node.setCursor(wireCandidates ? Cursor.CROSSHAIR : Cursor.HAND);
      } else {
        node.setCursor(Cursor.DEFAULT);
      }
    }
  }

  private void selectScreen(MenuScreenModel screen) {
    if (screen == null) return;
    selectedScreen = screen;
    itemTable.setItems(screen.items);
    if (!screen.items.isEmpty()) itemTable.getSelectionModel().select(0);
    wireMode = WireMode.NONE;
    MenuItemModel item = getSelectedItem();
    if (item != null && usesTarget(item.getAction())) {
      quickTargetCombo.getEditor().setText(normalize(item.getTarget(), ""));
    } else if (item == null) {
      quickTargetCombo.getEditor().clear();
    }
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
    statusLabel.setText("Wiring '" + item.getId() + "': click a target menu node (Esc to cancel).");
    graphPane.requestFocus();
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
    if (usesTarget(item.getAction())) {
      quickTargetCombo.getEditor().setText(normalize(item.getTarget(), ""));
    }
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

    var result = EditorDialogs.promptText(
        getScene() == null ? null : getScene().getWindow(),
        "Add Menu Screen",
        "Create a new menu screen.",
        "Menu id",
        "",
        "main",
        "Add");
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
    refreshQuickTargetOptions();
    autoLayoutScreensIfMissing();
    selectScreen(screen);
    rebuildGraph();
    statusLabel.setText("Added screen '" + id + "'. Save to create file.");
  }

  private void addItemFromSidebarInput() {
    if (selectedScreen == null) {
      statusLabel.setText("Select a screen first.");
      return;
    }
    String typed = normalize(quickAddItemField.getText(), "");
    if (typed.isBlank()) {
      addItemToSelectedScreen();
      return;
    }
    if (addItemToSelectedScreen(typed)) {
      quickAddItemField.clear();
    }
  }

  private void addItemToSelectedScreen() {
    if (selectedScreen == null) {
      statusLabel.setText("Select a screen first.");
      return;
    }

    var result = EditorDialogs.promptText(
        getScene() == null ? null : getScene().getWindow(),
        "Add Menu Item",
        "Add a new item to the selected menu.",
        "Item id",
        "",
        "new_item",
        "Add");
    if (result.isEmpty()) return;
    addItemToSelectedScreen(result.get());
  }

  private boolean addItemToSelectedScreen(String rawId) {
    if (selectedScreen == null) return false;

    String id = sanitizeId(rawId);
    if (id.isBlank()) {
      statusLabel.setText("Item id cannot be empty.");
      return false;
    }

    for (MenuItemModel row : selectedScreen.items) {
      if (id.equalsIgnoreCase(row.getId())) {
        statusLabel.setText("Item '" + id + "' already exists in " + selectedScreen.id + ".");
        return false;
      }
    }

    MenuItemModel row = new MenuItemModel(id, MenuActionType.NOOP, "");
    attachRowListeners(selectedScreen, row);
    selectedScreen.items.add(row);
    selectedScreen.dirty = true;
    itemTable.getSelectionModel().select(row);
    onCurrentScreenMutated();
    statusLabel.setText("Added item '" + id + "' to menu '" + selectedScreen.id + "'.");
    return true;
  }

  private void removeSelectedItem() {
    if (selectedScreen == null) return;
    MenuItemModel row = getSelectedItem();
    if (row == null) return;
    String id = normalize(row.getId(), "(unnamed)");
    selectedScreen.items.remove(row);
    selectedScreen.dirty = true;
    onCurrentScreenMutated();
    statusLabel.setText("Removed item '" + id + "'.");
  }

  private void duplicateSelectedItem() {
    if (selectedScreen == null) return;
    MenuItemModel source = getSelectedItem();
    if (source == null) {
      statusLabel.setText("Select an item to duplicate.");
      return;
    }

    String base = sanitizeId(source.getId());
    if (base.isBlank()) base = "item";
    String copyId = nextAvailableItemId(base + "_copy");

    MenuItemModel copy = new MenuItemModel(copyId, source.getActionKey(), source.getTarget());
    attachRowListeners(selectedScreen, copy);
    int idx = selectedScreen.items.indexOf(source);
    if (idx < 0 || idx + 1 >= selectedScreen.items.size()) {
      selectedScreen.items.add(copy);
    } else {
      selectedScreen.items.add(idx + 1, copy);
    }
    selectedScreen.dirty = true;
    itemTable.getSelectionModel().select(copy);
    onCurrentScreenMutated();
    statusLabel.setText("Duplicated item '" + source.getId() + "' as '" + copyId + "'.");
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
        out.setProperty(prefix + "action", normalize(row.getActionKey(), canonicalActionName(action)));
        String target = normalize(row.getTarget(), "");
        if ((usesTarget(action) || row.isCustomAction()) && !target.isBlank()) out.setProperty(prefix + "target", target);
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
    if (issues.isEmpty()) {
      EditorDialogs.info(getScene() == null ? null : getScene().getWindow(),
          "Menu Flow Validation",
          "Menu flow validation passed.");
    } else {
      EditorDialogs.showTextBlock(
          getScene() == null ? null : getScene().getWindow(),
          "Menu Flow Validation",
          "Validation issues found.",
          String.join(System.lineSeparator(), issues),
          "Close");
    }
    refreshDiagnostics();
  }

  private List<String> collectValidationIssues() {
    List<String> issues = new ArrayList<>();

    Set<String> allIds = new LinkedHashSet<>(screensById.keySet());
    String effectiveDefaultMenu = sanitizeId(normalize(defaultMenuCombo.getEditor().getText(), registryDefaultMenu));
    Set<String> declaredMenus = new LinkedHashSet<>(registryMenus);
    boolean registryWiringActive = registryFileExists || !declaredMenus.isEmpty() || !effectiveDefaultMenu.isBlank();

    for (MenuScreenModel screen : screensById.values()) {
      Set<String> seenItems = new HashSet<>();
      if (screen.items.isEmpty()) {
        issues.add("[WARN] menu '" + screen.id + "' has no items");
      }
      if (registryWiringActive && !declaredMenus.isEmpty() && !declaredMenus.contains(screen.id)) {
        issues.add("[WARN] menu '" + screen.id + "' exists but is not listed in menu.registry");
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
        if (row.isCustomAction()) {
          continue;
        }
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

    if (registryWiringActive) {
      if (!effectiveDefaultMenu.isBlank() && !allIds.contains(effectiveDefaultMenu)) {
        issues.add("[ERROR] menu.registry defaultMenu '" + effectiveDefaultMenu + "' does not exist");
      }
      for (String id : declaredMenus) {
        if (!allIds.contains(id)) {
          issues.add("[WARN] menu.registry lists '" + id + "' but no matching .menu file was found");
        }
      }
    }

    String reachabilityRoot = !effectiveDefaultMenu.isBlank() ? effectiveDefaultMenu : (screensById.containsKey("main") ? "main" : "");
    if (!reachabilityRoot.isBlank() && screensById.containsKey(reachabilityRoot)) {
      Set<String> reachable = computeReachableMenus(reachabilityRoot);
      for (String id : screensById.keySet()) {
        if (!reachable.contains(id)) {
          issues.add("[WARN] menu '" + id + "' is unreachable from " + reachabilityRoot);
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

  private void applyQuickOpenTarget() {
    MenuItemModel item = getSelectedItem();
    if (selectedScreen == null || item == null) {
      statusLabel.setText("Select a menu screen item first.");
      return;
    }
    String target = quickTargetInput();
    if (target.isBlank()) {
      statusLabel.setText("Choose or type a target menu id first.");
      return;
    }
    item.setAction(MenuActionType.OPEN_MENU);
    item.setTarget(target);
    markDirty(selectedScreen);
    wireMode = WireMode.NONE;
    quickTargetCombo.getEditor().setText(target);
    statusLabel.setText("Set '" + item.getId() + "' -> OPEN_MENU(" + target + ").");
    updateUiState();
  }

  private void cancelWireMode(String message) {
    if (wireMode == WireMode.NONE) return;
    wireMode = WireMode.NONE;
    if (message != null && !message.isBlank()) statusLabel.setText(message);
    updateUiState();
  }

  private void refreshQuickTargetOptions() {
    String current = quickTargetInput();
    quickTargetCombo.getItems().setAll(screensById.keySet());
    if (screensById.isEmpty()) {
      quickTargetCombo.getEditor().clear();
      quickTargetCombo.setValue(null);
    } else if (!current.isBlank()) quickTargetCombo.getEditor().setText(current);
  }

  private String quickTargetInput() {
    String value = quickTargetCombo.isEditable()
        ? normalize(quickTargetCombo.getEditor().getText(), normalize(quickTargetCombo.getValue(), ""))
        : normalize(quickTargetCombo.getValue(), "");
    return sanitizeId(value);
  }

  private String nextAvailableItemId(String base) {
    String normalizedBase = sanitizeId(base);
    if (normalizedBase.isBlank()) normalizedBase = "item";
    Set<String> ids = new HashSet<>();
    if (selectedScreen != null) {
      for (MenuItemModel row : selectedScreen.items) {
        ids.add(sanitizeId(row.getId()));
      }
    }
    if (!ids.contains(normalizedBase)) return normalizedBase;
    int suffix = 2;
    while (ids.contains(normalizedBase + "_" + suffix)) suffix++;
    return normalizedBase + "_" + suffix;
  }

  private void updateUiState() {
    boolean hasProject = projectRoot != null && projectRoot.isDirectory();
    boolean hasSelection = selectedScreen != null;
    MenuItemModel selectedItem = getSelectedItem();
    boolean hasItem = selectedItem != null;
    boolean selectedRegistered = hasSelection && registryMenus.contains(selectedScreen.id);

    saveSelectedButton.setDisable(!hasSelection);
    saveAllButton.setDisable(!hasProject);
    validateButton.setDisable(!hasProject);
    autoLayoutButton.setDisable(screensById.isEmpty());
    openButton.setDisable(!hasSelection);
    addScreenButton.setDisable(!hasProject);
    registerSelectedButton.setDisable(!hasSelection || selectedRegistered);
    unregisterSelectedButton.setDisable(!hasSelection || !selectedRegistered);
    syncRegistryMenusButton.setDisable(!hasProject || screensById.isEmpty());
    saveRegistryButton.setDisable(!hasProject || !registryDirty);
    openRegistryButton.setDisable(!hasProject);
    defaultMenuCombo.setDisable(!hasProject);
    registryLayoutsField.setDisable(!hasProject);
    registryStylesField.setDisable(!hasProject);

    wireOpenButton.setDisable(!(hasSelection && hasItem));
    cancelWireButton.setDisable(wireMode == WireMode.NONE);
    setMainButton.setDisable(!(hasSelection && hasItem));
    setBackButton.setDisable(!(hasSelection && hasItem));
    clearTargetButton.setDisable(!(hasSelection && hasItem));
    applyQuickTargetButton.setDisable(!(hasSelection && hasItem) || quickTargetInput().isBlank());
    quickTargetCombo.setDisable(!(hasSelection && hasItem));

    quickAddItemField.setDisable(!hasSelection);
    addItemButton.setDisable(!hasSelection);
    duplicateItemButton.setDisable(!(hasSelection && hasItem));
    removeItemButton.setDisable(!(hasSelection && hasItem));

    if (selectedScreen == null) {
      selectedMenuLabel.setText("Menu: (none)");
      selectedFileLabel.setText("File: -");
    } else {
      selectedMenuLabel.setText("Menu: " + selectedScreen.id + (selectedScreen.dirty ? " *" : ""));
      selectedFileLabel.setText("File: " + toRelativeProjectPath(selectedScreen.file));
    }
    updateRegistrySelectionLabel();
    registryMenusLabel.setText("Menus: "
        + (registryMenus.isEmpty() ? "(auto-discovery / none declared)" : String.join(", ", registryMenus))
        + (registryDirty ? " *" : ""));

    if (selectedItem == null) {
      selectedItemLabel.setText("Item: (none)");
    } else {
      String target = normalize(selectedItem.getTarget(), "");
      String tail = target.isBlank() ? "" : " -> " + target;
      selectedItemLabel.setText("Item: " + selectedItem.getId() + " [" + canonicalActionName(selectedItem.getAction()) + "]" + tail);
    }

    if (wireMode == WireMode.OPEN_MENU) {
      wireModeLabel.setText("Wire mode: OPEN_MENU (click target node)");
      if (!wireModeLabel.getStyleClass().contains("menu-flow-wire-active")) {
        wireModeLabel.getStyleClass().add("menu-flow-wire-active");
      }
      wireHintLabel.setText("Canvas wiring active for the selected item. Click a menu node or press Esc.");
      wireOpenButton.setText("Wiring Active");
    } else {
      wireModeLabel.setText("Wire mode: Off");
      wireModeLabel.getStyleClass().remove("menu-flow-wire-active");
      wireOpenButton.setText("Wire on Graph");
      if (!hasSelection) {
        wireHintLabel.setText("Select a menu and item to wire.");
      } else if (!hasItem) {
        wireHintLabel.setText("Select an item in the table, then wire on graph or set quick target.");
      } else {
        wireHintLabel.setText("Use graph wiring, presets, or quick target for OPEN_MENU.");
      }
    }

    refreshNodeStyles();
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
    String root = sanitizeId(normalize(registryDefaultMenu, ""));
    if (root.isBlank() || !screensById.containsKey(root)) {
      root = screensById.containsKey("main") ? "main" : screensById.keySet().stream().findFirst().orElse("");
    }

    Map<String, Integer> depthById = new LinkedHashMap<>();
    if (!root.isBlank()) {
      List<String> queue = new ArrayList<>();
      queue.add(root);
      depthById.put(root, 0);
      while (!queue.isEmpty()) {
        String current = queue.remove(0);
        int nextDepth = depthById.getOrDefault(current, 0) + 1;
        MenuScreenModel screen = screensById.get(current);
        if (screen == null) continue;
        for (MenuItemModel item : screen.items) {
          String target = resolveTargetMenu(item.getAction(), item.getTarget());
          if (target == null || NODE_BACK.equals(target) || !screensById.containsKey(target)) continue;
          Integer knownDepth = depthById.get(target);
          if (knownDepth != null && knownDepth <= nextDepth) continue;
          depthById.put(target, nextDepth);
          if (!queue.contains(target)) queue.add(target);
        }
      }
    }

    int overflowDepth = depthById.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    Map<Integer, List<MenuScreenModel>> columns = new LinkedHashMap<>();
    for (MenuScreenModel screen : screensById.values()) {
      int depth = depthById.getOrDefault(screen.id, overflowDepth);
      columns.computeIfAbsent(depth, ignored -> new ArrayList<>()).add(screen);
    }

    List<Integer> depths = new ArrayList<>(columns.keySet());
    depths.sort(Integer::compareTo);
    int colIndex = 0;
    for (Integer depth : depths) {
      List<MenuScreenModel> column = columns.getOrDefault(depth, List.of());
      double baseY = 52;
      if (column.size() <= 2) baseY = 92;
      for (int row = 0; row < column.size(); row++) {
        MenuScreenModel screen = column.get(row);
        screen.x = 64 + colIndex * (NODE_WIDTH + 154);
        screen.y = baseY + row * (NODE_HEIGHT + 66);
        rememberedScreenPositions.put(screen.id, new Point2D(screen.x, screen.y));
      }
      colIndex++;
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
      case GALLERY -> "gallery";
      case MUSIC_ROOM -> "music_room";
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

  private static VBox inspectorCard(String title, javafx.scene.Node... children) {
    VBox card = new VBox(8);
    card.getStyleClass().add("menu-flow-inspector-card");
    card.getChildren().add(sectionLabel(title));
    card.getChildren().addAll(children);
    return card;
  }

  private static Label sectionLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("menu-flow-section-label");
    return label;
  }

  private static HBox labeledInput(String label, javafx.scene.Node input) {
    Label l = new Label(label);
    l.setMinWidth(64);
    l.getStyleClass().add("menu-flow-input-label");
    if (input instanceof Region region) {
      region.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(region, Priority.ALWAYS);
    }
    HBox row = new HBox(6, l, input);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
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
      setMinSize(NODE_WIDTH, NODE_HEIGHT);
      setPrefSize(NODE_WIDTH, NODE_HEIGHT);
      setMaxSize(NODE_WIDTH, NODE_HEIGHT);
      box.setArcWidth(10);
      box.setArcHeight(10);

      Label title = new Label(label);
      title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #e4e8ef;");
      title.setMaxWidth(NODE_WIDTH - 22);
      title.setTextOverrun(OverrunStyle.ELLIPSIS);
      String subtitleText = switch (kind) {
        case SCREEN -> {
          int count = screen == null ? 0 : screen.items.size();
          yield count + " item" + (count == 1 ? "" : "s");
        }
        case SPECIAL -> "navigation action";
        case MISSING -> "missing target";
      };
      Label subtitle = new Label(subtitleText);
      subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #a5acb9;");
      subtitle.setMaxWidth(NODE_WIDTH - 22);
      subtitle.setTextOverrun(OverrunStyle.ELLIPSIS);

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
    private final StringProperty actionKey = new SimpleStringProperty("noop");
    private final StringProperty target = new SimpleStringProperty("");

    MenuItemModel(String id, MenuActionType action, String target) {
      this(id, canonicalActionName(action), target);
    }

    MenuItemModel(String id, String actionKey, String target) {
      setId(id);
      setActionKey(actionKey);
      setTarget(target);
    }

    String getId() { return id.get(); }
    void setId(String value) { id.set(normalize(value, "")); }
    StringProperty idProperty() { return id; }

    MenuActionType getAction() { return action.get(); }
    void setAction(MenuActionType value) {
      MenuActionType normalized = value == null ? MenuActionType.NOOP : value;
      action.set(normalized);
      actionKey.set(canonicalActionName(normalized));
    }
    ObjectProperty<MenuActionType> actionProperty() { return action; }

    String getActionKey() { return actionKey.get(); }
    void setActionKey(String value) {
      String key = normalize(value, "noop");
      actionKey.set(key);
      action.set(MenuActionType.parse(key));
    }
    StringProperty actionKeyProperty() { return actionKey; }

    boolean isCustomAction() {
      String key = normalize(getActionKey(), "noop").toLowerCase(Locale.ROOT).replace('-', '_');
      return getAction() == MenuActionType.NOOP && !"noop".equals(key) && !"no_op".equals(key) && !"none".equals(key);
    }

    String getTarget() { return target.get(); }
    void setTarget(String value) { target.set(normalize(value, "")); }
    StringProperty targetProperty() { return target; }
  }

  private static Button iconBtn(String text, Region icon) {
    Button btn = new Button(text);
    btn.setGraphic(icon);
    btn.getStyleClass().add("menu-flow-button");
    btn.setGraphicTextGap(7);
    btn.setMinHeight(34);
    btn.setMnemonicParsing(false);
    btn.setTooltip(new Tooltip(text));
    btn.setAccessibleText(text);
    return btn;
  }
}
