package com.jvn.editor.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class StoryTimelineView extends BorderPane {
  public static class Arc {
    public String name;
    public String script;
    public String entryLabel;
    public String cluster;
    public double x = 40;
    public double y = 40;
    public String toLine() { return "ARC|" + nn(name) + "|" + nn(script) + "|" + nn(entryLabel) + "|" + x + "|" + y; }
  }

  private void validate() {
    StringBuilder sb = new StringBuilder();
    // Check arcs
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      File f = resolveFile(a.script);
      if (f == null || !f.exists()) {
        sb.append("Missing script for arc '").append(a.name).append("': ").append(a.script).append("\n");
        continue;
      }
      if (a.entryLabel != null && !a.entryLabel.isBlank()) {
        boolean ok = hasLabel(f, a.entryLabel);
        if (!ok) sb.append("Arc '").append(a.name).append("' missing entry label '").append(a.entryLabel).append("'\n");
      }
    }
    // Check links
    for (Link l : links.getItems()) {
      if (l == null) continue;
      Arc ta = findArc(l.toArc);
      if (ta == null) { sb.append("Link to unknown arc: ").append(l.toArc).append("\n"); continue; }
      File f = resolveFile(ta.script);
      if (f == null || !f.exists()) { sb.append("Link target arc script missing: ").append(ta.script).append("\n"); continue; }
      String lab = (l.toLabel != null && !l.toLabel.isBlank()) ? l.toLabel : ta.entryLabel;
      if (lab != null && !lab.isBlank()) {
        boolean ok = hasLabel(f, lab);
        if (!ok) sb.append("Link target label missing: ").append(l.toArc).append(":").append(lab).append("\n");
      }
    }
    if (sb.length() == 0) {
      Alert a = new Alert(Alert.AlertType.INFORMATION, "Timeline OK"); EditorTheme.apply(a); a.setHeaderText(null); a.setTitle("Validate"); a.showAndWait();
    } else {
      TextArea ta = new TextArea(sb.toString()); ta.setEditable(false); ta.setWrapText(true);
      Dialog<Void> dlg = new Dialog<>(); EditorTheme.apply(dlg); dlg.setTitle("Validation Issues"); dlg.getDialogPane().setContent(ta); dlg.getDialogPane().getButtonTypes().add(ButtonType.OK); dlg.showAndWait();
    }
  }

  private boolean hasLabel(File vnsFile, String label) {
    try (FileInputStream in = new FileInputStream(vnsFile)) {
      com.jvn.core.vn.script.VnScriptParser p = new com.jvn.core.vn.script.VnScriptParser();
      com.jvn.core.vn.VnScenario sc = p.parse(in);
      return sc.getLabelIndex(label) != null;
    } catch (Exception e) {
      return false;
    }
  }
  public static class Link {
    public String fromArc;
    public String fromLabel;
    public String toArc;
    public String toLabel;
    public String toLine() { return "LINK|" + nn(fromArc) + "|" + nn(fromLabel) + "|" + nn(toArc) + "|" + nn(toLabel); }
  }

  private final ListView<Arc> arcs = new ListView<>();
  private final ListView<Link> links = new ListView<>();
  private final StoryGraphPane graph = new StoryGraphPane();
  private final ScrollPane graphScroll = new ScrollPane(graph);
  private final Label graphHint = new Label("Add an arc or drag a .vns script here to start your timeline.");
  private final List<Button> toolbarIconButtons = new ArrayList<>();
  private static final double MIN_ZOOM = 0.6;
  private static final double MAX_ZOOM = 2.0;
  private double zoomLevel = 1.0;
  private ComboBox<String> clusterFilter;
  private File projectRoot;
  private File timelineFile;
  private Consumer<Arc> onRunArc;
  private Consumer<Link> onRunLink;
  private Runnable onChanged;
  private boolean toolbarIconOnly = false;
  private double toolbarTextModeMinWidth = -1;

  public StoryTimelineView() {
    getStyleClass().add("timeline-root");
    arcs.getStyleClass().add("timeline-list");
    links.getStyleClass().add("timeline-list");
    graphHint.getStyleClass().add("timeline-empty-hint");
    graphHint.setWrapText(true);
    graphHint.setMaxWidth(440);
    graphHint.setMouseTransparent(true);
    StackPane.setAlignment(graphHint, Pos.CENTER);

    arcs.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Arc a, boolean empty) {
        super.updateItem(a, empty);
        setText(empty || a == null ? null : a.name + "  [" + a.script + (a.entryLabel == null || a.entryLabel.isBlank() ? "" : (" :: " + a.entryLabel)) + "]");
      }
    });
    links.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Link l, boolean empty) {
        super.updateItem(l, empty);
        setText(empty || l == null ? null : l.fromArc + ":" + nn(l.fromLabel) + "  ->  " + l.toArc + ":" + nn(l.toLabel));
      }
    });
    arcs.setPlaceholder(new Label("No arcs yet."));
    links.setPlaceholder(new Label("No links yet."));
    arcs.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) openArc();
    });
    links.setOnMouseClicked(e -> {
      if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) runSelectedLink();
    });
    arcs.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
        deleteSelected();
      } else if (e.getCode() == KeyCode.ENTER) {
        openArc();
      }
    });
    links.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
        deleteSelected();
      } else if (e.getCode() == KeyCode.ENTER) {
        runSelectedLink();
      }
    });
    ContextMenu arcMenu = new ContextMenu();
    MenuItem miArcOpen = new MenuItem("Open Script");
    miArcOpen.setOnAction(e -> openArc());
    MenuItem miArcEdit = new MenuItem("Edit Arc...");
    miArcEdit.setOnAction(e -> editArc());
    MenuItem miArcDelete = new MenuItem("Delete Arc");
    miArcDelete.setOnAction(e -> deleteSelected());
    arcMenu.getItems().addAll(miArcOpen, miArcEdit, miArcDelete);
    arcs.setContextMenu(arcMenu);
    ContextMenu linkMenu = new ContextMenu();
    MenuItem miLinkRun = new MenuItem("Open Target Arc");
    miLinkRun.setOnAction(e -> runSelectedLink());
    MenuItem miLinkEdit = new MenuItem("Edit Link...");
    miLinkEdit.setOnAction(e -> editLink());
    MenuItem miLinkCopy = new MenuItem("Copy Goto");
    miLinkCopy.setOnAction(e -> copyGoto());
    MenuItem miLinkDelete = new MenuItem("Delete Link");
    miLinkDelete.setOnAction(e -> deleteSelected());
    linkMenu.getItems().addAll(miLinkRun, miLinkEdit, miLinkCopy, miLinkDelete);
    links.setContextMenu(linkMenu);

    // Graph area
    graphScroll.setFitToWidth(true);
    graphScroll.setFitToHeight(true);
    graphScroll.setPannable(true);
    StackPane graphPane = new StackPane(graphScroll, graphHint);
    graphPane.getStyleClass().add("timeline-graph-pane");

    // Bottom tabs
    TabPane listTabs = new TabPane();
    listTabs.getStyleClass().add("timeline-lists-tabs");
    Tab arcsTab = new Tab("Arcs", arcs);
    arcsTab.setClosable(false);
    Tab linksTab = new Tab("Links", links);
    linksTab.setClosable(false);
    listTabs.getTabs().addAll(arcsTab, linksTab);
    listTabs.setMinHeight(170);
    listTabs.setPrefHeight(220);

    // Split layout
    SplitPane rootSplit = new SplitPane();
    rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
    rootSplit.getItems().addAll(graphPane, listTabs);
    rootSplit.setDividerPositions(0.76);
    setCenter(rootSplit);

    // Toolbar
    Button bAddArc = iconButton("Add Arc", "icon-timeline-add-arc", e -> addArc());
    bAddArc.getStyleClass().add("timeline-primary-button");
    Button bAddLink = iconButton("Add Link", "icon-timeline-add-link", e -> addLink());
    bAddLink.getStyleClass().add("timeline-primary-button");
    Button bEdit = iconButton("Edit Selected", "icon-timeline-edit", e -> editSelected());
    Button bOpen = iconButton("Open", "icon-timeline-open", e -> openArc());
    Button bDelete = iconButton("Delete Selected", "icon-timeline-delete", e -> deleteSelected());
    Button bCopyGoto = iconButton("Copy Goto", "icon-timeline-copy", e -> copyGoto());
    Button bAuto = iconButton("Auto Layout", "icon-timeline-auto", e -> { graph.autoLayout(); onGraphChanged(); });
    Button bFit = iconButton("Fit", "icon-timeline-fit", e -> zoomToFit());
    Button bValidate = iconButton("Validate", "icon-timeline-validate", e -> validate());
    TextField tfSearch = new TextField();
    tfSearch.setPromptText("Find arc...");
    tfSearch.setPrefWidth(180);
    tfSearch.textProperty().addListener((o, ov, nv) -> graph.highlight(nv));
    HBox.setHgrow(tfSearch, Priority.ALWAYS);
    bAddArc.setTooltip(new Tooltip("Create a story arc from a .vns script"));
    bAddLink.setTooltip(new Tooltip("Connect two arcs"));
    bEdit.setTooltip(new Tooltip("Edit selected arc or link"));
    bOpen.setTooltip(new Tooltip("Open selected arc script"));
    bDelete.setTooltip(new Tooltip("Delete selected arc or link"));
    bCopyGoto.setTooltip(new Tooltip("Copy [goto arc:label] snippet"));
    bAuto.setTooltip(new Tooltip("Auto-arrange arc nodes"));
    bFit.setTooltip(new Tooltip("Fit graph to viewport"));
    bValidate.setTooltip(new Tooltip("Validate scripts and entry labels"));

    clusterFilter = new ComboBox<>();
    clusterFilter.setPrefWidth(170);
    clusterFilter.setPromptText("All");
    clusterFilter.valueProperty().addListener((o,ov,nv) -> {
      if (nv == null || nv.equals("All")) graph.setFilterCluster(null); else graph.setFilterCluster(nv);
    });

    Separator sepA = new Separator(javafx.geometry.Orientation.VERTICAL);
    Separator sepB = new Separator(javafx.geometry.Orientation.VERTICAL);
    Region rowSpacer = new Region();
    HBox.setHgrow(rowSpacer, Priority.ALWAYS);

    HBox rowPrimary = new HBox(6,
      bAddArc, bAddLink, sepA, bEdit, bOpen, bDelete, rowSpacer, new Label("Find"), tfSearch
    );
    rowPrimary.setAlignment(Pos.CENTER_LEFT);
    HBox rowSecondary = new HBox(8,
      new Label("Cluster"), clusterFilter, sepB, bCopyGoto, bAuto, bFit, bValidate
    );
    rowSecondary.setAlignment(Pos.CENTER_LEFT);

    VBox toolbar = new VBox(6, rowPrimary, rowSecondary);
    toolbar.getStyleClass().add("timeline-toolbar");
    toolbar.setPadding(new Insets(8, 8, 6, 8));
    setTop(toolbar);
    setupResponsiveToolbar(toolbar, rowPrimary, rowSecondary);
    setGraphZoom(1.0);

    // Graph actions wiring
    graph.setOnRunArc(a -> { if (onRunArc != null) onRunArc.accept(a); });
    graph.setOnRunLink(l -> { if (onRunLink != null) onRunLink.accept(l); });
    graph.setOnGraphChanged(this::onGraphChanged);
    graph.setOnLayoutCommitted(this::onGraphChanged);
    graph.setOnDeleteArc(a -> { if (a != null) { removeArcAndLinks(a.name); onGraphChanged(); } });
    graph.setSimpleLinkMode(true);
    arcs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      if (nv != null) graph.highlight(nv.name);
    });
    links.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      if (nv != null && nv.toArc != null) graph.highlight(nv.toArc);
    });

    // Wheel zoom with Ctrl/Cmd
    graphScroll.addEventFilter(ScrollEvent.SCROLL, e -> {
      if (e.isControlDown() || e.isShortcutDown()) {
        double step = (e.getDeltaY() > 0) ? 0.1 : -0.1;
        setGraphZoom(zoomLevel + step);
        e.consume();
      }
    });

    // Drag-and-drop .vns files onto graph to create arcs
    graph.setOnDragOver(e -> {
      Dragboard db = e.getDragboard();
      if (db.hasFiles()) {
        boolean ok = db.getFiles().stream().anyMatch(f -> f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".vns"));
        if (ok) { e.acceptTransferModes(TransferMode.COPY); e.consume(); }
      }
    });
    graph.setOnDragDropped(e -> {
      Dragboard db = e.getDragboard();
      boolean success = false;
      if (db.hasFiles()) {
        for (File f : db.getFiles()) {
          String name = f.getName().toLowerCase(java.util.Locale.ROOT);
          if (!name.endsWith(".vns")) continue;
          addArcFromFile(f);
          success = true;
        }
      }
      e.setDropCompleted(success); e.consume();
    });
    updateGraphHint();
  }

  private Button iconButton(String text,
                            String iconClass,
                            javafx.event.EventHandler<javafx.event.ActionEvent> onAction) {
    Button button = new Button(text);
    button.getStyleClass().add("timeline-toolbar-button");
    button.getProperties().put("fullText", text);
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setGraphicTextGap(6);
    button.setOnAction(onAction);

    Label icon = new Label();
    icon.getStyleClass().addAll("icon", iconClass);
    icon.setMouseTransparent(true);
    button.setGraphic(icon);

    toolbarIconButtons.add(button);
    return button;
  }

  private void setupResponsiveToolbar(VBox toolbar, HBox rowPrimary, HBox rowSecondary) {
    Runnable refresh = () -> {
      double width = toolbar.getWidth();
      if (width <= 0) return;
      if (toolbarTextModeMinWidth <= 0) {
        toolbarTextModeMinWidth = Math.max(rowPrimary.prefWidth(-1), rowSecondary.prefWidth(-1));
      }
      // Keep a small breathing room so the switch happens before clipping.
      boolean iconOnly = width < (toolbarTextModeMinWidth + 24);
      applyToolbarIconMode(iconOnly);
    };

    toolbar.widthProperty().addListener((o, ov, nv) -> refresh.run());
    rowPrimary.widthProperty().addListener((o, ov, nv) -> refresh.run());
    rowSecondary.widthProperty().addListener((o, ov, nv) -> refresh.run());
    Platform.runLater(refresh);
  }

  private void applyToolbarIconMode(boolean iconOnly) {
    if (toolbarIconOnly == iconOnly) return;
    toolbarIconOnly = iconOnly;
    for (Button button : toolbarIconButtons) {
      if (button == null) continue;
      Object full = button.getProperties().get("fullText");
      String fullText = full == null ? "" : full.toString();
      if (iconOnly) {
        button.getStyleClass().add("icon-only");
        button.setText(null);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      } else {
        button.getStyleClass().remove("icon-only");
        button.setText(fullText);
        button.setContentDisplay(ContentDisplay.LEFT);
      }
    }
  }

  public void setProjectRoot(File dir) {
    this.projectRoot = dir;
    load();
    refreshGraph();
    updateGraphHint();
  }

  public void setOnRunArc(Consumer<Arc> c) { this.onRunArc = c; }
  public void setOnRunLink(Consumer<Link> c) { this.onRunLink = c; }
  public void setOnChanged(Runnable r) { this.onChanged = r; }
  public void setTimelineFile(File f) { this.timelineFile = f; load(); refreshGraph(); updateGraphHint(); }
  public List<Arc> getArcs() { return new ArrayList<>(arcs.getItems()); }
  public List<Link> getLinks() { return new ArrayList<>(links.getItems()); }
  public Arc findArc(String name) {
    for (Arc a : arcs.getItems()) if (a != null && name != null && name.equals(a.name)) return a; return null;
  }

  private void editSelected() {
    if (arcs.getSelectionModel().getSelectedItem() != null) {
      editArc();
      return;
    }
    if (links.getSelectionModel().getSelectedItem() != null) {
      editLink();
    }
  }

  private void editArc() {
    Arc arc = arcs.getSelectionModel().getSelectedItem();
    if (arc == null) return;
    GridPane g = new GridPane();
    g.setHgap(6); g.setVgap(6); g.setPadding(new Insets(8));
    TextField tfName = new TextField(nn(arc.name));
    TextField tfScript = new TextField(nn(arc.script));
    TextField tfEntry = new TextField(nn(arc.entryLabel));
    TextField tfCluster = new TextField(nn(arc.cluster));
    Button bBrowse = new Button("Browse...");
    bBrowse.setOnAction(e -> {
      FileChooser fc = new FileChooser();
      fc.setTitle("Select VNS Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("VNS scripts", "*.vns"));
      File f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
      if (f != null) tfScript.setText(toRelative(f));
    });
    javafx.scene.layout.HBox scriptRow = new javafx.scene.layout.HBox(6, tfScript, bBrowse);
    javafx.scene.layout.HBox.setHgrow(tfScript, javafx.scene.layout.Priority.ALWAYS);
    g.addRow(0, new Label("Name"), tfName);
    g.addRow(1, new Label("Script"), scriptRow);
    g.addRow(2, new Label("Entry Label"), tfEntry);
    g.addRow(3, new Label("Cluster"), tfCluster);
    Dialog<ButtonType> dlg = new Dialog<>();
    EditorTheme.apply(dlg);
    dlg.setTitle("Edit Arc");
    dlg.getDialogPane().setContent(g);
    dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    var res = dlg.showAndWait();
    if (res.isEmpty() || res.get() != ButtonType.OK) return;
    String newName = tfName.getText() == null ? "" : tfName.getText().trim();
    if (newName.isEmpty()) return;
    String oldName = arc.name;
    arc.name = newName;
    arc.script = tfScript.getText() == null ? "" : tfScript.getText().trim();
    arc.entryLabel = tfEntry.getText() == null ? "" : tfEntry.getText().trim();
    arc.cluster = tfCluster.getText() == null ? "" : tfCluster.getText().trim();
    if (oldName != null && !oldName.equals(newName)) renameArcReferences(oldName, newName);
    onGraphChanged();
  }

  private void editLink() {
    Link l = links.getSelectionModel().getSelectedItem();
    if (l == null || arcs.getItems().isEmpty()) return;
    GridPane g = new GridPane();
    g.setHgap(6); g.setVgap(6); g.setPadding(new Insets(8));
    ComboBox<Arc> fromArc = new ComboBox<>();
    fromArc.getItems().setAll(arcs.getItems());
    configureArcCombo(fromArc);
    TextField fromLabel = new TextField(nn(l.fromLabel));
    ComboBox<Arc> toArc = new ComboBox<>();
    toArc.getItems().setAll(arcs.getItems());
    configureArcCombo(toArc);
    TextField toLabel = new TextField(nn(l.toLabel));
    fromArc.setValue(findArc(l.fromArc));
    toArc.setValue(findArc(l.toArc));
    g.addRow(0, new Label("From Arc"), fromArc);
    g.addRow(1, new Label("From Label"), fromLabel);
    g.addRow(2, new Label("To Arc"), toArc);
    g.addRow(3, new Label("To Label"), toLabel);
    Dialog<ButtonType> dlg = new Dialog<>();
    EditorTheme.apply(dlg);
    dlg.setTitle("Edit Link");
    dlg.getDialogPane().setContent(g);
    dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    var res = dlg.showAndWait();
    if (res.isEmpty() || res.get() != ButtonType.OK) return;
    Arc fa = fromArc.getValue();
    Arc ta = toArc.getValue();
    if (fa == null || ta == null) return;
    l.fromArc = fa.name;
    l.fromLabel = fromLabel.getText() == null ? "" : fromLabel.getText().trim();
    l.toArc = ta.name;
    l.toLabel = toLabel.getText() == null ? "" : toLabel.getText().trim();
    onGraphChanged();
  }

  private void configureArcCombo(ComboBox<Arc> combo) {
    combo.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Arc item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : nn(item.name));
      }
    });
    combo.setButtonCell(new ListCell<>() {
      @Override protected void updateItem(Arc item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : nn(item.name));
      }
    });
  }

  private void renameArcReferences(String oldName, String newName) {
    for (Link li : links.getItems()) {
      if (li == null) continue;
      if (oldName.equals(li.fromArc)) li.fromArc = newName;
      if (oldName.equals(li.toArc)) li.toArc = newName;
    }
  }

  private void addArc() {
    TextInputDialog dlg = new TextInputDialog("Arc");
    EditorTheme.apply(dlg);
    dlg.setHeaderText(null); dlg.setTitle("Arc Name"); dlg.setContentText("Name:");
    var res = dlg.showAndWait(); if (res.isEmpty()) return; String name = res.get().trim(); if (name.isEmpty()) return;
    FileChooser fc = new FileChooser();
    fc.setTitle("Select VNS Script");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("VNS scripts", "*.vns"));
    File f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (f == null) return;
    TextInputDialog ldlg = new TextInputDialog("");
    EditorTheme.apply(ldlg);
    ldlg.setHeaderText(null); ldlg.setTitle("Entry Label"); ldlg.setContentText("Label (optional):");
    var lres = ldlg.showAndWait(); String label = lres.isEmpty() ? "" : lres.get().trim();
    Arc a = new Arc(); a.name = name; a.script = toRelative(f); a.entryLabel = label;
    arcs.getItems().add(a);
    arcs.getSelectionModel().select(a);
    onGraphChanged();
  }

  private void addLink() {
    if (arcs.getItems().isEmpty()) return;
    GridPane g = new GridPane();
    g.setHgap(6); g.setVgap(6); g.setPadding(new Insets(8));
    ComboBox<Arc> fromArc = new ComboBox<>(); fromArc.getItems().setAll(arcs.getItems()); configureArcCombo(fromArc);
    TextField fromLabel = new TextField();
    ComboBox<Arc> toArc = new ComboBox<>(); toArc.getItems().setAll(arcs.getItems()); configureArcCombo(toArc);
    TextField toLabel = new TextField();
    Arc selectedArc = arcs.getSelectionModel().getSelectedItem();
    if (selectedArc != null) {
      fromArc.setValue(selectedArc);
      for (Arc a : arcs.getItems()) {
        if (a != null && a != selectedArc) {
          toArc.setValue(a);
          break;
        }
      }
    }
    if (fromArc.getValue() == null && !arcs.getItems().isEmpty()) fromArc.setValue(arcs.getItems().get(0));
    if (toArc.getValue() == null && !arcs.getItems().isEmpty()) toArc.setValue(arcs.getItems().get(0));
    g.addRow(0, new Label("From Arc"), fromArc);
    g.addRow(1, new Label("From Label"), fromLabel);
    g.addRow(2, new Label("To Arc"), toArc);
    g.addRow(3, new Label("To Label"), toLabel);
    Dialog<ButtonType> dlg = new Dialog<>(); EditorTheme.apply(dlg); dlg.setTitle("Add Link"); dlg.getDialogPane().setContent(g); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    var res = dlg.showAndWait(); if (res.isEmpty() || res.get() != ButtonType.OK) return;
    Arc fa = fromArc.getValue(); Arc ta = toArc.getValue(); if (fa == null || ta == null) return;
    Link l = new Link(); l.fromArc = fa.name; l.fromLabel = fromLabel.getText(); l.toArc = ta.name; l.toLabel = toLabel.getText();
    links.getItems().add(l);
    links.getSelectionModel().select(l);
    onGraphChanged();
  }

  private void deleteSelected() {
    Link link = links.getSelectionModel().getSelectedItem();
    if (link != null) {
      links.getItems().remove(link);
      onGraphChanged();
      return;
    }
    Arc arc = arcs.getSelectionModel().getSelectedItem();
    if (arc != null) {
      removeArcAndLinks(arc.name);
      onGraphChanged();
    }
  }

  private void openArc() {
    Arc a = arcs.getSelectionModel().getSelectedItem();
    if (a == null) return;
    if (onRunArc != null) {
      onRunArc.accept(a);
      return;
    }
    try { java.awt.Desktop.getDesktop().open(resolveFile(a.script)); } catch (Exception ignored) {}
  }

  private void runSelectedLink() {
    Link l = links.getSelectionModel().getSelectedItem();
    if (l == null) return;
    if (onRunLink != null) {
      onRunLink.accept(l);
      return;
    }
    Arc target = findArc(l.toArc);
    if (target != null) {
      arcs.getSelectionModel().select(target);
      openArc();
    }
  }

  private void copyGoto() {
    String snip = null;
    Link l = links.getSelectionModel().getSelectedItem();
    if (l != null) {
      snip = "[goto " + l.toArc + ":" + nn(l.toLabel) + "]";
    } else {
      Arc a = arcs.getSelectionModel().getSelectedItem();
      if (a != null) snip = "[goto " + nn(a.name) + ":" + nn(a.entryLabel) + "]";
    }
    if (snip == null) return;
    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
    cc.putString(snip);
    javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
  }

  private void save() {
    File f = timelineFile != null ? timelineFile : defaultTimelineFile();
    if (f == null) return;
    File parent = f.getParentFile();
    if (parent != null && !parent.exists()) parent.mkdirs();
    try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
      pw.print(toDsl());
    } catch (Exception ignored) {}
  }

  private void load() {
    File f = timelineFile != null ? timelineFile : defaultTimelineFile();
    if (f == null) return;
    if (!f.exists()) return;
    try {
      String text = java.nio.file.Files.readString(f.toPath());
      fromText(text);
    } catch (Exception ignored) {}
  }

  private File defaultTimelineFile() {
    if (projectRoot == null) return null;
    File modern = new File(projectRoot, "config/timeline/story.timeline");
    if (modern.exists()) return modern;
    File legacyStoryDir = new File(projectRoot, "story/story.timeline");
    if (legacyStoryDir.exists()) return legacyStoryDir;
    File legacyRoot = new File(projectRoot, "story.timeline");
    if (legacyRoot.exists()) return legacyRoot;
    return modern;
  }

  private static String nn(String s) { return s == null ? "" : s; }
  private static String n(String s) { return s == null ? "" : s; }

  private String toRelative(File f) {
    try {
      if (projectRoot == null) return f.getAbsolutePath();
      String root = projectRoot.getCanonicalPath();
      String abs = f.getCanonicalPath();
      if (abs.startsWith(root)) {
        String rel = abs.substring(root.length());
        if (rel.startsWith(File.separator)) rel = rel.substring(1);
        return rel.replace('\\', '/');
      }
      return abs;
    } catch (Exception e) {
      return f.getPath();
    }
  }

  private File resolveFile(String p) {
    if (p == null) return null;
    File f = new File(p);
    if (f.isAbsolute() || projectRoot == null) return f;
    return new File(projectRoot, p);
  }

  private void refreshGraph() {
    graph.setModel(arcs.getItems(), links.getItems());
    updateClusterFilter();
  }

  private void updateGraphHint() {
    boolean empty = arcs.getItems().isEmpty();
    graphHint.setVisible(empty);
    graphHint.setManaged(empty);
  }

  private void onGraphChanged() {
    // sync list views/graph model and persist
    refreshGraph();
    arcs.refresh(); links.refresh();
    updateGraphHint();
    save();
    updateClusterFilter();
    if (onChanged != null) onChanged.run();
  }

  private void updateClusterFilter() {
    if (clusterFilter == null) return;
    java.util.Set<String> names = graph.getClusterNames();
    String sel = clusterFilter.getValue();
    clusterFilter.getItems().setAll(new java.util.ArrayList<>());
    clusterFilter.getItems().add("All");
    for (String n : names) clusterFilter.getItems().add(n);
    if (sel != null && clusterFilter.getItems().contains(sel)) {
      clusterFilter.setValue(sel);
    } else {
      clusterFilter.setValue("All");
    }
  }

  private void removeArcAndLinks(String arcName) {
    if (arcName == null) return;
    Arc target = null;
    for (Arc a : arcs.getItems()) { if (a != null && arcName.equals(a.name)) { target = a; break; } }
    if (target != null) arcs.getItems().remove(target);
    links.getItems().removeIf(l -> arcName.equals(l.fromArc) || arcName.equals(l.toArc));
  }

  private void addArcFromFile(File f) {
    if (f == null) return;
    TextInputDialog dlg = new TextInputDialog(stripExt(f.getName()));
    EditorTheme.apply(dlg);
    dlg.setHeaderText(null); dlg.setTitle("Arc Name"); dlg.setContentText("Name:");
    var res = dlg.showAndWait(); if (res.isEmpty()) return; String name = res.get().trim(); if (name.isEmpty()) return;
    TextInputDialog ldlg = new TextInputDialog("");
    EditorTheme.apply(ldlg);
    ldlg.setHeaderText(null); ldlg.setTitle("Entry Label"); ldlg.setContentText("Label (optional):");
    var lres = ldlg.showAndWait(); String label = lres.isEmpty() ? "" : lres.get().trim();
    Arc a = new Arc(); a.name = name; a.script = toRelative(f); a.entryLabel = label;
    arcs.getItems().add(a);
    arcs.getSelectionModel().select(a);
    onGraphChanged();
  }

  private static String stripExt(String n) {
    if (n == null) return "Arc"; int i = n.lastIndexOf('.'); return (i>0) ? n.substring(0,i) : n;
  }

  public String toDsl() {
    StringBuilder sb = new StringBuilder();
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      sb.append("arc \"").append(nn(a.name)).append("\"");
      if (a.script != null && !a.script.isBlank()) sb.append(" script \"").append(nn(a.script)).append("\"");
      if (a.entryLabel != null && !a.entryLabel.isBlank()) sb.append(" entry \"").append(nn(a.entryLabel)).append("\"");
      if (a.cluster != null && !a.cluster.isBlank()) sb.append(" cluster \"").append(nn(a.cluster)).append("\"");
      sb.append(" at ").append(a.x).append(",").append(a.y);
      sb.append("\n");
    }
    for (Link l : links.getItems()) {
      if (l == null) continue;
      String fl = (l.fromLabel == null || l.fromLabel.isBlank()) ? nn(l.fromArc) : (nn(l.fromArc) + ":" + nn(l.fromLabel));
      String tl = (l.toLabel == null || l.toLabel.isBlank()) ? nn(l.toArc) : (nn(l.toArc) + ":" + nn(l.toLabel));
      sb.append("link ").append(fl).append(" -> ").append(tl).append("\n");
    }
    return sb.toString();
  }

  public void fromText(String text) {
    List<Arc> alist = new ArrayList<>();
    List<Link> llist = new ArrayList<>();
    if (text == null) text = "";
    String[] lines = text.split("\r?\n");
    Pattern parc = Pattern.compile("^\\s*arc\\s+(?:\"([^\"]+)\"|(\\S+))(?:\\s+script\\s+\"([^\"]+)\")?(?:\\s+entry\\s+\"([^\"]*)\")?(?:\\s+cluster\\s+\"([^\"]+)\")?(?:\\s+at\\s+(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?))?\\s*$", Pattern.CASE_INSENSITIVE);
    Pattern plink = Pattern.compile("^\\s*link\\s+([^\\s]+)\\s*->\\s*([^\\s]+)\\s*$", Pattern.CASE_INSENSITIVE);
    for (String line : lines) {
      if (line == null) continue;
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      if (s.startsWith("ARC|")) {
        String[] t = s.split("\\|", -1);
        if (t.length >= 4) {
          Arc a = new Arc(); a.name = n(t[1]); a.script = n(t[2]); a.entryLabel = n(t[3]);
          if (t.length >= 6) {
            try { a.x = Double.parseDouble(t[4]); } catch (Exception ignore) {}
            try { a.y = Double.parseDouble(t[5]); } catch (Exception ignore) {}
          }
          alist.add(a);
        }
        continue;
      }
      if (s.startsWith("LINK|")) {
        String[] t = s.split("\\|", -1);
        if (t.length >= 5) { Link l = new Link(); l.fromArc = n(t[1]); l.fromLabel = n(t[2]); l.toArc = n(t[3]); l.toLabel = n(t[4]); llist.add(l); }
        continue;
      }
      Matcher ma = parc.matcher(s);
      if (ma.matches()) {
        Arc a = new Arc();
        a.name = ma.group(1) != null ? ma.group(1) : ma.group(2);
        a.script = nn(ma.group(3));
        a.entryLabel = nn(ma.group(4));
        a.cluster = nn(ma.group(5));
        if (ma.group(6) != null && ma.group(7) != null) {
          try { a.x = Double.parseDouble(ma.group(6)); } catch (Exception ignore) {}
          try { a.y = Double.parseDouble(ma.group(7)); } catch (Exception ignore) {}
        }
        alist.add(a);
        continue;
      }
      Matcher ml = plink.matcher(s);
      if (ml.matches()) {
        String left = ml.group(1);
        String right = ml.group(2);
        Link l = new Link();
        int ci = left.indexOf(':');
        if (ci >= 0) { l.fromArc = left.substring(0,ci); l.fromLabel = left.substring(ci+1); } else { l.fromArc = left; l.fromLabel = ""; }
        ci = right.indexOf(':');
        if (ci >= 0) { l.toArc = right.substring(0,ci); l.toLabel = right.substring(ci+1); } else { l.toArc = right; l.toLabel = ""; }
        llist.add(l);
      }
    }
    arcs.getItems().setAll(alist);
    links.getItems().setAll(llist);
    refreshGraph();
    updateGraphHint();
  }

  private void zoomToFit() {
    if (arcs.getItems().isEmpty()) return;
    double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      minX = Math.min(minX, a.x);
      minY = Math.min(minY, a.y);
      maxX = Math.max(maxX, a.x + 140);
      maxY = Math.max(maxY, a.y + 44);
    }
    double pad = 80;
    double widthNeeded = (maxX - minX) + pad;
    double heightNeeded = (maxY - minY) + pad;
    double vw = graphScroll.getViewportBounds().getWidth();
    double vh = graphScroll.getViewportBounds().getHeight();
    if (vw <= 0 || vh <= 0) return;
    double s = Math.min(vw / widthNeeded, vh / heightNeeded);
    setGraphZoom(s);
    double contentW = Math.max(widthNeeded * s, vw);
    double contentH = Math.max(heightNeeded * s, vh);
    double centerX = (minX + maxX) / 2 * s;
    double centerY = (minY + maxY) / 2 * s;
    double hx = (centerX - vw / 2) / Math.max(1, (contentW - vw));
    double vy = (centerY - vh / 2) / Math.max(1, (contentH - vh));
    graphScroll.setHvalue(Math.max(0, Math.min(1, hx)));
    graphScroll.setVvalue(Math.max(0, Math.min(1, vy)));
  }

  private void setGraphZoom(double zoom) {
    zoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    graph.setScaleX(zoomLevel);
    graph.setScaleY(zoomLevel);
  }
}
