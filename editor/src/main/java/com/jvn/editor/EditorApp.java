package com.jvn.editor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import java.util.Properties;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.ui.FileEditorTab;
import com.jvn.editor.ui.InspectorView;
import com.jvn.editor.ui.ProjectExplorerView;
import com.jvn.editor.ui.SceneGraphView;
import com.jvn.editor.ui.SettingsEditorView;
import com.jvn.editor.ui.StoryTimelineView;
import com.jvn.editor.ui.TilemapEditorView;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.sun.management.OperatingSystemMXBean;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class EditorApp extends Application {
  // UI
  private AnimationTimer timer;
  private Label status;
  private Label fps;
  private TextFlow perf;
  private Text cpuText;
  private Text gpuText;
  private Text ramText;
  private Text fpsText;
  private PerfGraph perfGraph;
  private File lastOpened;
  private Entity2D selected;
  private String lastSelectedName;
  private InspectorView inspectorView;
  private TabPane filesTabs;
  private boolean showGrid = true;
  
  private SceneGraphView sgView;
  private ProjectExplorerView projView;
  private StoryTimelineView timelineView;
  private SettingsEditorView settingsEditor;
  private com.jvn.editor.ui.MenuThemeEditorView menuThemeEditor;
  private TilemapEditorView mapEditorView;
  private final CommandStack commands = new CommandStack();
  private Tab tabProject;
  private Tab tabScene;
  private File projectRoot;
  private OperatingSystemMXBean osBean;
  private long lastPerfUpdateNs = -1L;
  private double lastFps = 0.0;
  private static final Color CPU_COLOR = Color.web("#f27333");
  private static final Color GPU_COLOR = Color.web("#a855f7");
  private static final Color RAM_COLOR = Color.web("#49a5ff");
  private static final Color GRID_BG = Color.color(0.08, 0.08, 0.08, 0.8);
  private static final Color GRID_LINE = Color.color(1, 1, 1, 0.08);

  public static void main(String[] args) {
    launch(args);
  }

  private void doOpenVns(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Open VNS Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("VNS scripts", "*.vns"));
      File f = fc.showOpenDialog(stage);
      if (f == null) return;
      openVnsFile(f);
    } catch (Exception ex) {
      status.setText("Load failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doRunProject(Stage stage) {
    File root = ensureProjectRoot(stage);
    if (root == null) return;
    Properties mf = loadManifest(root);
    if (mf == null) { status.setText("jvn.project not found"); return; }
    String type = mf.getProperty("type", "gradle").trim();
    if ("gradle".equalsIgnoreCase(type)) {
      String path = mf.getProperty("path", ":billiards-game").trim();
      String task = mf.getProperty("task", "run").trim();
      String args = mf.getProperty("args", "-x test");
      runGradle(root, composeGradleTask(path, task), args == null ? new String[]{} : args.split("\\s+"), "Run Project");
    } else if ("vn".equalsIgnoreCase(type)) {
      // Open entry VNS and start preview from its label
      String entryVns = mf.getProperty("entryVns", "scripts/prologue.vns");
      String entryLabel = mf.getProperty("entryLabel", "start");
      File f = new File(root, entryVns);
      if (f.exists()) {
        // Set project root first so newly opened tabs inherit it
        this.projectRoot = root;
        if (projView != null) projView.setRootDirectory(root);
        if (timelineView != null) timelineView.setProjectRoot(root);
        if (settingsEditor != null) settingsEditor.setProjectRoot(root);
        openVnsFile(f);
        applyProjectRootToTabs();
        javafx.application.Platform.runLater(() -> { FileEditorTab ft = getActiveFileTab(); if (ft != null) ft.runFromLabel(entryLabel); });
        status.setText("Opened VN project: " + root.getName());
      } else {
        status.setText("Entry VNS not found: " + entryVns);
      }
    } else if ("jes".equalsIgnoreCase(type)) {
      // For JES-only projects: open the entry script and set as project
      String entry = mf.getProperty("entry", "scripts/main.jes");
      File f = new File(root, entry);
      if (f.exists()) openJesFile(f);
      this.projectRoot = root;
      if (projView != null) projView.setRootDirectory(root);
      status.setText("Opened JES project: " + root.getName());
    } else {
      status.setText("Unknown project type: " + type);
    }
  }

  private File ensureProjectRoot(Stage stage) {
    File root = this.projectRoot;
    if (root == null) {
      DirectoryChooser dc = new DirectoryChooser();
      dc.setTitle("Select Project Root");
      root = dc.showDialog(stage);
      if (root == null) return null;
      this.projectRoot = root;
      if (projView != null) projView.setRootDirectory(root);
      if (timelineView != null) timelineView.setProjectRoot(root);
      if (settingsEditor != null) settingsEditor.setProjectRoot(root);
      if (menuThemeEditor != null) menuThemeEditor.setProjectRoot(root);
      if (mapEditorView != null) mapEditorView.setProjectRoot(root);
      selectProjectTab();
    }
    return root;
  }

  private Properties loadManifest(File dir) {
    try (FileInputStream fis = new FileInputStream(new File(dir, "jvn.project"))) {
      Properties p = new Properties();
      p.load(fis);
      return p;
    } catch (Exception ignore) { return null; }
  }

  private String composeGradleTask(String path, String task) {
    String p = path == null ? "" : path.trim();
    String t = task == null ? "run" : task.trim();
    if (t.startsWith(":")) return t; // full task provided
    if (p.isEmpty()) return t;
    if (!p.startsWith(":")) p = ":" + p;
    return p + ":" + t;
  }

  private void runGradle(File root, String task, String[] args, String title) {
    File gradlew = new File(root, "gradlew");
    try {
      java.util.List<String> cmd = new java.util.ArrayList<>();
      if (gradlew.exists()) cmd.add(gradlew.getAbsolutePath()); else cmd.add("gradle");
      cmd.add(task);
      if (args != null) java.util.Collections.addAll(cmd, args);
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(root);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      javafx.stage.Stage logStage = new javafx.stage.Stage();
      javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea();
      ta.setEditable(false);
      logStage.setTitle(title);
      logStage.setScene(new javafx.scene.Scene(new javafx.scene.layout.BorderPane(ta), 800, 500));
      logStage.show();
      Thread t = new Thread(() -> {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
          String line;
          while ((line = r.readLine()) != null) {
            String ln = line;
            javafx.application.Platform.runLater(() -> ta.appendText(ln + "\n"));
          }
        } catch (Exception ignored) {}
      });
      t.setDaemon(true);
      t.start();
    } catch (Exception ex) {
      status.setText("Run failed");
    }
  }

  private void doNewProject(Stage stage) {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setTitle("Choose Location for New Project");
    File base = dc.showDialog(stage);
    if (base == null) return;
    javafx.scene.control.TextInputDialog nameDlg = new javafx.scene.control.TextInputDialog("MyProject");
    nameDlg.setHeaderText(null); nameDlg.setTitle("New Project"); nameDlg.setContentText("Project name:");
    var nameRes = nameDlg.showAndWait(); if (nameRes.isEmpty()) return; String name = nameRes.get().trim(); if (name.isEmpty()) return;
    javafx.scene.control.ChoiceDialog<String> typeDlg = new javafx.scene.control.ChoiceDialog<>("vn", java.util.List.of("vn","jes","gradle"));
    typeDlg.setHeaderText(null); typeDlg.setTitle("Project Type"); typeDlg.setContentText("Type:");
    var typeRes = typeDlg.showAndWait(); if (typeRes.isEmpty()) return; String type = typeRes.get();
    File dir = new File(base, name); dir.mkdirs();
    try {
      Properties p = new Properties();
      p.setProperty("name", name);
      if ("vn".equalsIgnoreCase(type)) {
        p.setProperty("type", "vn");
        p.setProperty("entryVns", "scripts/prologue.vns");
        p.setProperty("entryLabel", "start");
        p.setProperty("timeline", "story.timeline");
        // directories
        new File(dir, "scripts").mkdirs();
        new File(dir, "assets/characters").mkdirs();
        new File(dir, "assets/backgrounds").mkdirs();
        new File(dir, "assets/cg").mkdirs();
        new File(dir, "assets/ui").mkdirs();
        new File(dir, "assets/bgm").mkdirs();
        new File(dir, "assets/sfx").mkdirs();
        new File(dir, "assets/voices").mkdirs();
        // sample script
        try (FileWriter fw = new FileWriter(new File(dir, "scripts/prologue.vns"))) {
          fw.write("label start\n\n\"Welcome to " + name + "!\"\n\n[choice Begin->start]\n");
        }
        // sample timeline (DSL)
        try (FileWriter fw = new FileWriter(new File(dir, "story.timeline"))) {
          fw.write("# Timeline for " + name + "\n");
          fw.write("arc \"Prologue\" script \"scripts/prologue.vns\" entry \"start\" at 40,40\n");
        }
        // default settings
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "vn.settings"))) {
          Properties sp = new Properties();
          sp.setProperty("textSpeed", "40");
          sp.setProperty("bgm", "0.7");
          sp.setProperty("sfx", "0.7");
          sp.setProperty("voice", "0.7");
          sp.setProperty("autoPlayDelay", "1500");
          sp.setProperty("skipUnread", "false");
          sp.setProperty("skipAfterChoices", "false");
          sp.store(fos, "VN Settings");
        }
        // default menu theme
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "scripts/menu.theme"))) {
          Properties tp = new Properties();
          tp.setProperty("backgroundColor", "#0A0C12");
          tp.setProperty("titleColor", "#FFFFFF");
          tp.setProperty("itemColor", "#D3D3D3");
          tp.setProperty("itemSelectedColor", "#FFFF00");
          tp.setProperty("hintColor", "rgba(200,200,200,0.8)");
          tp.setProperty("accentColor", "#FFFF00");
          tp.setProperty("titleFontFamily", "Arial");
          tp.setProperty("titleFontWeight", "BOLD");
          tp.setProperty("titleFontSize", "32");
          tp.setProperty("itemFontFamily", "Arial");
          tp.setProperty("itemFontWeight", "NORMAL");
          tp.setProperty("itemFontSize", "20");
          tp.setProperty("hintFontFamily", "Arial");
          tp.setProperty("hintFontWeight", "NORMAL");
          tp.setProperty("hintFontSize", "14");
          // layout defaults
          tp.setProperty("titleY", "60");
          tp.setProperty("listYStart", "0.35");
          tp.setProperty("lineHeight", "40");
          tp.setProperty("itemPrefix", "  ");
          tp.setProperty("itemSelectedPrefix", "> ");
          tp.store(fos, "Menu Theme");
        }
      } else if ("jes".equalsIgnoreCase(type)) {
        p.setProperty("type", "jes");
        p.setProperty("entry", "scripts/main.jes");
        new File(dir, "scripts").mkdirs();
        try (FileWriter fw = new FileWriter(new File(dir, "scripts/main.jes"))) {
          fw.write("scene \"" + name + "\" {\n  entity \"title\" {\n    component Label2D { text: \"Hello, JVN!\" x: 20 y: 24 size: 18 bold: true color: rgb(1,1,1,1) }\n  }\n  on key \"D\" do toggleDebug\n}\n");
        }
      } else {
        p.setProperty("type", "gradle");
        javafx.scene.control.TextInputDialog pathDlg = new javafx.scene.control.TextInputDialog(":app");
        pathDlg.setHeaderText(null); pathDlg.setTitle("Gradle Module Path"); pathDlg.setContentText("Module path (e.g. :billiards-game):");
        var pathRes = pathDlg.showAndWait(); if (pathRes.isEmpty()) return; String path = pathRes.get().trim();
        p.setProperty("path", path);
        p.setProperty("task", "run");
        p.setProperty("args", "-x test");
      }
      try (FileOutputStream fos = new FileOutputStream(new File(dir, "jvn.project"))) { p.store(fos, "JVN Project Manifest"); }
      this.projectRoot = dir; if (projView != null) projView.setRootDirectory(dir);
      if (timelineView != null) { timelineView.setProjectRoot(dir); timelineView.setTimelineFile(new File(dir, "story.timeline")); }
      if (settingsEditor != null) settingsEditor.setProjectRoot(dir);
      if (menuThemeEditor != null) menuThemeEditor.setProjectRoot(dir);
      if (mapEditorView != null) mapEditorView.setProjectRoot(dir);
      status.setText("Created project: " + name);
    } catch (Exception ex) {
      status.setText("Create project failed");
    }
  }

  private void openSample(String absolutePath) {
    File f = new File(absolutePath);
    if (!f.exists()) {
      Alert a = new Alert(Alert.AlertType.ERROR, "Sample not found: " + absolutePath);
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
      return;
    }
    openFile(f);
  }

  // legacy helpers removed; ViewportView now owned by per-file tabs

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("JVN Editor");
    BorderPane root = new BorderPane();

    // Menu
    MenuBar mb = new MenuBar();
    Menu menuFile = new Menu("File");
    MenuItem miNewProject = new MenuItem("New Project...");
    miNewProject.setOnAction(e -> doNewProject(primaryStage));
    MenuItem miOpenProject = new MenuItem("Open Project...");
    miOpenProject.setOnAction(e -> doOpenProject(primaryStage));
    MenuItem miOpen = new MenuItem("Open JES...");
    miOpen.setOnAction(e -> doOpen(primaryStage));
    MenuItem miOpenVns = new MenuItem("Open VNS...");
    miOpenVns.setOnAction(e -> doOpenVns(primaryStage));
    MenuItem miReload = new MenuItem("Reload");
    miReload.setOnAction(e -> doReload());
    MenuItem miSave = new MenuItem("Save");
    miSave.setOnAction(e -> doSave(primaryStage));
    MenuItem miSaveAs = new MenuItem("Save As...");
    miSaveAs.setOnAction(e -> doSaveAs(primaryStage));
    miOpen.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
    miReload.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));
    miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
    miSaveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    menuFile.getItems().addAll(miNewProject, miOpenProject, new SeparatorMenuItem(), miOpen, miOpenVns, miReload, miSave, miSaveAs);

    Menu menuCode = new Menu("Code");
    MenuItem miApplyCode = new MenuItem("Apply Code");
    miApplyCode.setOnAction(e -> applyCodeFromEditor());
    miApplyCode.setAccelerator(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN));
    menuCode.getItems().addAll(miApplyCode);

    Menu menuView = new Menu("View");
    MenuItem miFit = new MenuItem("Fit to Content");
    miFit.setOnAction(e -> fitCameraToContent());
    miFit.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
    MenuItem miReset = new MenuItem("Reset Camera");
    miReset.setOnAction(e -> resetCamera());
    miReset.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));
    MenuItem miToggleGrid = new MenuItem("Toggle Grid");
    miToggleGrid.setOnAction(e -> { showGrid = !showGrid; FileEditorTab ft = getActiveFileTab(); if (ft != null) ft.setShowGrid(showGrid); });
    miToggleGrid.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN));
    menuView.getItems().addAll(miFit, miReset, miToggleGrid);

    Menu menuSamples = new Menu("Samples");
    Menu menuProject = new Menu("Project");
    MenuItem miRun = new MenuItem("Run Project");
    miRun.setOnAction(e -> doRunProject(primaryStage));
    menuProject.getItems().addAll(miRun);
    Menu menuEdit = new Menu("Edit");
    MenuItem miUndo = new MenuItem("Undo");
    miUndo.setOnAction(e -> { commands.undo(); status.setText("Undo"); inspectorView.setSelection(selected); });
    miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRedo = new MenuItem("Redo");
    miRedo.setOnAction(e -> { commands.redo(); status.setText("Redo"); inspectorView.setSelection(selected); });
    miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    menuEdit.getItems().addAll(miUndo, miRedo);
    MenuItem miBilliards = new MenuItem("Open billiards.jes");
    miBilliards.setOnAction(e -> openSample("/Users/ilgazmehmetoglu/Desktop/Projects/Percept2 Engine/samples/billiards.jes"));
    MenuItem miShowcase = new MenuItem("Open showcase.jes");
    miShowcase.setOnAction(e -> openSample("/Users/ilgazmehmetoglu/Desktop/Projects/Percept2 Engine/samples/showcase.jes"));
    menuSamples.getItems().addAll(miBilliards, miShowcase);

    mb.getMenus().addAll(menuFile, menuEdit, menuCode, menuView, menuProject, menuSamples);

    // Toolbar
    osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    BorderPane toolbar = new BorderPane();
    toolbar.getStyleClass().add("master-toolbar");
    Button btnOpen = new Button("Open"); btnOpen.setOnAction(e -> doOpen(primaryStage));
    Button btnOpenProject = new Button("Open Project"); btnOpenProject.setOnAction(e -> doOpenProject(primaryStage));
    Button btnReload = new Button("Reload"); btnReload.setOnAction(e -> doReload());
    Button btnApply = new Button("Apply Code"); btnApply.setOnAction(e -> applyCodeFromEditor());
    Button btnFit = new Button("Fit"); btnFit.setOnAction(e -> fitCameraToContent());
    Button btnReset = new Button("Reset"); btnReset.setOnAction(e -> resetCamera());
    Button btnRun = new Button("Run"); btnRun.setOnAction(e -> doRunProject(primaryStage));
    // Icons to the right of text
    btnOpen.setGraphic(icon("icon", "icon-open"));
    btnOpen.setContentDisplay(ContentDisplay.RIGHT);
    btnOpen.setGraphicTextGap(6);
    btnReload.setGraphic(icon("icon", "icon-reload"));
    btnReload.setContentDisplay(ContentDisplay.RIGHT);
    btnReload.setGraphicTextGap(6);
    btnApply.setGraphic(icon("icon", "icon-apply"));
    btnApply.setContentDisplay(ContentDisplay.RIGHT);
    btnApply.setGraphicTextGap(6);
    btnFit.setGraphic(icon("icon", "icon-fit"));
    btnFit.setContentDisplay(ContentDisplay.RIGHT);
    btnFit.setGraphicTextGap(6);
    btnReset.setGraphic(icon("icon", "icon-reset"));
    btnReset.setContentDisplay(ContentDisplay.RIGHT);
    btnReset.setGraphicTextGap(6);
    btnApply.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()) applyCodeFromEditor(); });
    status = new Label("Ready");
    fps = new Label("");
    cpuText = new Text("CPU --");
    cpuText.setFill(CPU_COLOR);
    gpuText = new Text(" | GPU n/a");
    gpuText.setFill(GPU_COLOR);
    ramText = new Text(" | RAM --");
    ramText.setFill(RAM_COLOR);
    fpsText = new Text(" | FPS --");
    fpsText.setFill(Color.WHITE);
    perf = new TextFlow(cpuText, gpuText, ramText, fpsText);
    perf.setLineSpacing(2);
    perfGraph = new PerfGraph();
    Button btnSave = new Button("Save");
    Button btnUndo = new Button("Undo");
    Button btnRedo = new Button("Redo");
    Button btnZoomIn = new Button("Zoom In");
    Button btnZoomOut = new Button("Zoom Out");
    Button btnZoomReset = new Button("100%");
    Button btnPlay = new Button("Play");
    Button btnStop = new Button("Stop");
    Button btnSettings = new Button("Settings");
    btnSave.setOnAction(e -> doSave(primaryStage));
    btnUndo.setOnAction(e -> { commands.undo(); status.setText("Undo"); inspectorView.setSelection(selected); });
    btnRedo.setOnAction(e -> { commands.redo(); status.setText("Redo"); inspectorView.setSelection(selected); });
    btnZoomIn.setOnAction(e -> status.setText("Zoom In"));
    btnZoomOut.setOnAction(e -> status.setText("Zoom Out"));
    btnZoomReset.setOnAction(e -> status.setText("Zoom 100%"));
    btnPlay.setOnAction(e -> doRunProject(primaryStage));
    btnStop.setOnAction(e -> status.setText("Stop"));
    btnSettings.setOnAction(e -> status.setText("Settings"));
    btnOpen.setTooltip(new Tooltip("Open JES (Cmd+O)"));
    btnOpenProject.setTooltip(new Tooltip("Open Project"));
    btnReload.setTooltip(new Tooltip("Reload (Cmd+R)"));
    btnApply.setTooltip(new Tooltip("Apply Code (Cmd+Enter)"));
    btnFit.setTooltip(new Tooltip("Fit to Content / Fullscreen VN (Cmd+F)"));
    btnReset.setTooltip(new Tooltip("Reset Camera (Cmd+0)"));
    btnRun.setTooltip(new Tooltip("Run Project"));
    btnSave.setTooltip(new Tooltip("Save (Cmd+S)"));
    btnUndo.setTooltip(new Tooltip("Undo (Cmd+Z)"));
    btnRedo.setTooltip(new Tooltip("Redo (Shift+Cmd+Z)"));
    // Icons for second-row controls
    btnSave.setGraphic(icon("icon", "icon-save"));
    btnSave.setContentDisplay(ContentDisplay.RIGHT);
    btnSave.setGraphicTextGap(6);
    btnUndo.setGraphic(icon("icon", "icon-undo"));
    btnUndo.setContentDisplay(ContentDisplay.RIGHT);
    btnUndo.setGraphicTextGap(6);
    btnRedo.setGraphic(icon("icon", "icon-redo"));
    btnRedo.setContentDisplay(ContentDisplay.RIGHT);
    btnRedo.setGraphicTextGap(6);
    ImageView logo = new ImageView();
    try {
      // Try several likely filesystem locations relative to working dir
      String[] rels = new String[] {
        "docs/images/jvn_logo.png",
        "../docs/images/jvn_logo.png",
        "../../docs/images/jvn_logo.png",
        "../../../docs/images/jvn_logo.png"
      };
      Image found = null;
      for (String rp : rels) {
        java.io.File f = new java.io.File(rp);
        if (f.exists()) { found = new Image(f.toURI().toString()); break; }
      }
      if (found == null && projectRoot != null) {
        // If a project root is known, search upward from it
        java.io.File cur = projectRoot;
        for (int i = 0; i < 4 && cur != null; i++) {
          java.io.File candidate = new java.io.File(cur, "docs/images/jvn_logo.png");
          if (candidate.exists()) { found = new Image(candidate.toURI().toString()); break; }
          cur = cur.getParentFile();
        }
      }
      if (found == null) {
        var url = EditorApp.class.getResource("/docs/images/jvn_logo.png");
        if (url != null) found = new Image(url.toExternalForm());
      }
      if (found != null) {
        logo.setImage(found);
        logo.setFitHeight(80);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        // Subtle light glow so it remains visible on black background
        logo.setEffect(null);
        HBox.setMargin(logo, new javafx.geometry.Insets(0, 10, 0, 0));
      } else {
        logo.setManaged(false);
        logo.setVisible(false);
      }
    } catch (Exception ignored) {
      logo.setManaged(false);
      logo.setVisible(false);
    }
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row1 = new HBox(8);
    HBox row2 = new HBox(8);
    row1.getChildren().addAll(btnOpen, btnOpenProject, btnReload, btnApply, btnFit, btnReset, btnRun);
    row2.getChildren().addAll(btnSave, btnUndo, btnRedo, spacer, status);
    VBox toolRows = new VBox(6);
    toolRows.getChildren().addAll(row1, row2);
    HBox.setHgrow(toolRows, Priority.ALWAYS);
    String ver = System.getProperty("jvn.version");
    if (ver == null || ver.isBlank()) {
      Package pkg = EditorApp.class.getPackage();
      ver = (pkg != null && pkg.getImplementationVersion() != null) ? pkg.getImplementationVersion() : "dev";
    }
    Label verLabel = new Label("JVN Engine v" + ver);
    VBox logoBox = new VBox(2);
    logoBox.setAlignment(Pos.CENTER);
    logoBox.getChildren().addAll(logo, verLabel);
    toolbar.setLeft(toolRows);
    VBox perfBox = new VBox(4, perf, perfGraph.getCanvas());
    perfBox.setAlignment(Pos.CENTER);
    perfBox.setFillWidth(true);
    HBox perfWrapper = new HBox(perfBox);
    perfWrapper.setAlignment(Pos.CENTER);
    HBox.setHgrow(perfWrapper, Priority.ALWAYS);
    perfBox.widthProperty().addListener((o, ov, nv) -> perfGraph.setWidth(nv.doubleValue()));
    toolbar.setCenter(perfWrapper);
    BorderPane.setAlignment(logoBox, Pos.TOP_RIGHT);
    toolbar.setRight(logoBox);

    // Layout
    BorderPane top = new BorderPane();
    top.getStyleClass().add("master-toolbar");
    top.setTop(mb);
    top.setCenter(toolbar);
    root.setTop(top);
    // Center: per-file tabs with embedded preview
    filesTabs = new TabPane();
    filesTabs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      updateContextForActiveTab();
    });
    root.setCenter(filesTabs);
    inspectorView = new InspectorView(s -> status.setText(s));
    inspectorView.setCommandStack(commands);
    inspectorView.setMinWidth(280);
    inspectorView.setPrefWidth(320);
    ScrollPane inspectorScroll = new ScrollPane(inspectorView);
    inspectorScroll.setFitToWidth(true);
    timelineView = new StoryTimelineView();
    settingsEditor = new SettingsEditorView();
    menuThemeEditor = new com.jvn.editor.ui.MenuThemeEditorView();
    mapEditorView = new TilemapEditorView();
    TabPane rightTabs = new TabPane();
    Tab tabInspectorRight = new Tab("Inspector", inspectorScroll); tabInspectorRight.setClosable(false);
    Tab tabMapEditor = new Tab("Map Editor", mapEditorView); tabMapEditor.setClosable(false);
    Tab tabTimeline = new Tab("Timeline", timelineView); tabTimeline.setClosable(false);
    Tab tabSettings = new Tab("Settings", settingsEditor); tabSettings.setClosable(false);
    Tab tabMenuTheme = new Tab("Menu Theme", menuThemeEditor); tabMenuTheme.setClosable(false);
    rightTabs.getTabs().addAll(tabInspectorRight, tabMapEditor, tabTimeline, tabSettings, tabMenuTheme);
    rightTabs.setPrefWidth(360);
    timelineView.setOnRunArc(a -> {
      if (a == null || a.script == null) return;
      File f = resolveProjectFile(a.script);
      if (f != null && f.exists()) {
        openFile(f);
        FileEditorTab ft = getActiveFileTab();
        if (ft != null) ft.runFromLabel((a.entryLabel == null || a.entryLabel.isBlank()) ? null : a.entryLabel);
      } else {
        status.setText("Arc script not found: " + a.script);
      }
    });
    timelineView.setOnRunLink(l -> {
      if (l == null) return;
      StoryTimelineView.Arc ta = timelineView.findArc(l.toArc);
      if (ta == null) { status.setText("Arc not found: " + l.toArc); return; }
      File f = resolveProjectFile(ta.script);
      if (f != null && f.exists()) {
        openFile(f);
        FileEditorTab ft = getActiveFileTab();
        String label = (l.toLabel != null && !l.toLabel.isBlank()) ? l.toLabel : ta.entryLabel;
        if (ft != null) ft.runFromLabel((label == null || label.isBlank()) ? null : label);
      } else {
        status.setText("Arc script not found: " + ta.script);
      }
    });
    sgView = new SceneGraphView();
    sgView.setMinWidth(200);
    sgView.setPrefWidth(240);
    projView = new ProjectExplorerView();
    projView.setOnOpenFile(f -> {
      if (f == null) return;
      String name = f.getName().toLowerCase();
      if (name.endsWith(".jes") || name.endsWith(".txt") || name.endsWith(".vns") || name.endsWith(".java") || name.endsWith(".timeline") || name.endsWith(".theme") || "menu.theme".equals(name)) {
        openFile(f);
      } else {
        try { java.awt.Desktop.getDesktop().open(f); } catch (Exception ignored) {}
      }
    });
    TabPane sideTabs = new TabPane();
    tabProject = new Tab("Project", projView); tabProject.setClosable(false);
    tabScene = new Tab("Scene", sgView); tabScene.setClosable(false);
    sideTabs.getTabs().addAll(tabProject, tabScene);
    sideTabs.setPrefWidth(260);
    SplitPane centerSplit = new SplitPane();
    centerSplit.getItems().addAll(sideTabs, filesTabs, rightTabs);
    centerSplit.setDividerPositions(0.22, 0.78);
    root.setLeft(null);
    root.setRight(null);
    root.setCenter(centerSplit);

    Scene scene = new Scene(root, 1200, 800);
    // Load editor stylesheet (icons, theme, etc.)
    try {
      String css = EditorApp.class.getResource("/com/jvn/editor/editor.css").toExternalForm();
      scene.getStylesheets().add(css);
    } catch (Exception ignore) {}
    primaryStage.setScene(scene);
    primaryStage.show();
    scene.setOnDragOver((DragEvent e) -> {
      Dragboard db = e.getDragboard();
      if (db != null && db.hasFiles()) e.acceptTransferModes(TransferMode.COPY);
      e.consume();
    });
    scene.setOnDragDropped((DragEvent e) -> {
      Dragboard db = e.getDragboard();
      boolean success = false;
      if (db != null && db.hasFiles()) {
        File file = db.getFiles().get(0);
        if (file != null) openFile(file);
        success = true;
      }
      e.setDropCompleted(success);
      e.consume();
    });

    // Timer
    timer = new AnimationTimer() {
      long last = -1;
      @Override public void handle(long now) {
        if (last < 0) { last = now; return; }
        long dt = (now - last) / 1_000_000L;
        last = now;
        FileEditorTab ft = getActiveFileTab();
        if (ft != null) {
          ft.setSize(filesTabs.getWidth(), filesTabs.getHeight());
          ft.render(dt);
        }
    if (fps != null) {
      double f = (dt > 0) ? (1000.0 / dt) : 0.0;
      lastFps = f;
    }
        updatePerf(now);
      }
    };
    timer.start();
  }

  private void doOpen(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Open JES Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JES scripts", "*.jes", "*.txt"));
      File f = fc.showOpenDialog(stage);
      if (f == null) return;
      openJesFile(f);
    } catch (Exception ex) {
      status.setText("Load failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doOpenProject(Stage stage) {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setTitle("Open Project Directory");
    File dir = dc.showDialog(stage);
    if (dir == null) return;
    this.projectRoot = dir;
    if (projView != null) projView.setRootDirectory(dir);
    if (timelineView != null) { timelineView.setProjectRoot(dir); String tf = loadManifest(dir) != null ? loadManifest(dir).getProperty("timeline", "story.timeline") : "story.timeline"; timelineView.setTimelineFile(new File(dir, tf)); }
    if (settingsEditor != null) settingsEditor.setProjectRoot(dir);
    if (menuThemeEditor != null) menuThemeEditor.setProjectRoot(dir);
    if (mapEditorView != null) mapEditorView.setProjectRoot(dir);
    applyProjectRootToTabs();
    status.setText("Project: " + dir.getName());
    selectProjectTab();
  }

  private void openJesFile(File f) { openFile(f); }

  private void openVnsFile(File f) { openFile(f); }

  private File resolveProjectFile(String p) {
    if (p == null) return null;
    File f = new File(p);
    if (f.isAbsolute() || projectRoot == null) return f;
    return new File(projectRoot, p);
  }

  private void doReload() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    ft.reloadFromDisk();
    updateContextForActiveTab();
  }

  private void applyCodeFromEditor() {
    try {
      FileEditorTab ft = getActiveFileTab();
      if (ft == null) return;
      ft.apply();
      status.setText("Applied");
      updateContextForActiveTab();
      if (lastSelectedName != null && !lastSelectedName.isBlank()) {
        JesScene2D scene = ft.getJesScene();
        if (scene != null) {
          Entity2D ent = scene.find(lastSelectedName);
          if (ent != null) {
            selected = ent;
            if (inspectorView != null) inspectorView.setSelection(ent);
          }
        }
      }
    } catch (Exception ex) {
      status.setText("Apply failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to apply code: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doSave(Stage stage) {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    File f = ft.getFile();
    if (f == null) { doSaveAs(stage); return; }
    ft.saveTo(f);
  }

  private void doSaveAs(Stage stage) {
    try {
      FileEditorTab ft = getActiveFileTab(); if (ft == null) return;
      FileChooser fc = new FileChooser();
      fc.setTitle("Save File");
      if (ft.getFile() != null) fc.setInitialFileName(ft.getFile().getName());
      File f = fc.showSaveDialog(stage);
      if (f == null) return;
      ft.saveTo(f);
      openFile(f);
    } catch (Exception ex) {
      status.setText("Save As failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to save as: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void openJavaFile(File f) { openFile(f); }

  private static String stripExt(String name) {
    if (name == null) return "scene";
    int i = name.lastIndexOf('.');
    return (i > 0) ? name.substring(0, i) : name;
  }

  private void buildSceneGraph() {
    updateContextForActiveTab();
  }

  private String mapKey(KeyCode code) { return code == null ? "" : (code.getName() == null || code.getName().isBlank() ? code.toString() : code.getName()).toUpperCase(); }
  private int mapButton(MouseButton b) { if (b == MouseButton.PRIMARY) return 1; if (b == MouseButton.MIDDLE) return 2; if (b == MouseButton.SECONDARY) return 3; return 0; }

  private void updatePerf(long nowNs) {
    if (perf == null) return;
    if (lastPerfUpdateNs > 0 && (nowNs - lastPerfUpdateNs) < 500_000_000L) return; // 0.5s throttle
    lastPerfUpdateNs = nowNs;

    double sysCpu = -1;
    double procCpu = -1;
    if (osBean != null) {
      sysCpu = osBean.getSystemCpuLoad();
      procCpu = osBean.getProcessCpuLoad();
    }
    Runtime rt = Runtime.getRuntime();
    double usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0);
    double maxMb = rt.maxMemory() / (1024.0 * 1024.0);

    String cpuStr = (sysCpu >= 0)
        ? String.format("CPU %.0f%% sys / %.0f%% app", sysCpu * 100.0, procCpu >= 0 ? procCpu * 100.0 : 0.0)
        : "CPU --";
    String ramStr = String.format(" | RAM %.0f / %.0f MB", usedMb, maxMb);
    String gpuStr = " | GPU n/a";
    String fpsStr = String.format(" | FPS %.0f", lastFps);

    cpuText.setText(cpuStr);
    ramText.setText(ramStr);
    gpuText.setText(gpuStr);
    fpsText.setText(fpsStr);

    perfGraph.pushSample(sysCpu >= 0 ? sysCpu : 0, maxMb > 0 ? (usedMb / maxMb) : 0, 0);
  }

  /** Tiny inline graph renderer for CPU/RAM usage. */
  private static class PerfGraph {
    private final Canvas canvas = new Canvas(320, 64);
    private final double[] cpu = new double[240]; // ~4s at 60fps
    private final double[] ram = new double[240];
    private final double[] gpu = new double[240];
    private int idx = 0;
    private boolean filled = false;

    public Canvas getCanvas() { return canvas; }
    public void setWidth(double w) { canvas.setWidth(Math.max(160, w)); redraw(); }

    public void pushSample(double cpu01, double ram01, double gpu01) {
      int i = idx % cpu.length;
      cpu[i] = clamp01(cpu01);
      ram[i] = clamp01(ram01);
      gpu[i] = clamp01(gpu01);
      idx++;
      if (idx >= cpu.length) filled = true;
      redraw();
    }

    private void redraw() {
      GraphicsContext g = canvas.getGraphicsContext2D();
      double w = canvas.getWidth();
      double h = canvas.getHeight();
      g.setFill(GRID_BG);
      g.fillRect(0, 0, w, h);

      // grid
      g.setStroke(GRID_LINE);
      g.setLineWidth(1);
      int rows = 4;
      for (int r = 1; r < rows; r++) {
        double y = h * r / rows;
        g.strokeLine(0, y, w, y);
      }
      double stepX = w / 6.0;
      for (double x = stepX; x < w; x += stepX) {
        g.strokeLine(x, 0, x, h);
      }

      int samples = filled ? cpu.length : Math.min(idx, cpu.length);
      if (samples <= 1) return;
      double scaleX = w / (cpu.length - 1);

      // RAM area
      g.setFill(RAM_COLOR.deriveColor(0, 1, 1, 0.18));
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - ram[si]);
        if (i == 0) g.moveTo(x, h);
        g.lineTo(x, y);
      }
      g.lineTo((samples - 1) * scaleX, h);
      g.closePath();
      g.fill();

      // CPU line
      g.setStroke(CPU_COLOR.deriveColor(0, 1, 1, 0.9));
      g.setLineWidth(2);
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - cpu[si]);
        if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
      }
      g.stroke();

      // GPU line
      g.setStroke(GPU_COLOR.deriveColor(0, 1, 1, 0.9));
      g.setLineWidth(2);
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - gpu[si]);
        if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
      }
      g.stroke();
    }

    private double clamp01(double v) {
      if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
      if (v < 0) return 0;
      if (v > 1) return 1;
      return v;
    }
  }

  private Region icon(String... styleClasses) {
    Region r = new Region();
    if (styleClasses != null) r.getStyleClass().addAll(styleClasses);
    return r;
  }

  private void openFile(File f) {
    if (f == null) return;
    // Find existing tab
    for (Tab t : filesTabs.getTabs()) {
      if (t.getUserData() instanceof File ff && ff.equals(f)) {
        filesTabs.getSelectionModel().select(t);
        return;
      }
    }
    // Create new tab
    FileEditorTab editor = new FileEditorTab(f);
    if (projectRoot != null) editor.setProjectRoot(projectRoot);
    editor.setOnSelected(ent -> {
      selected = ent;
      if (inspectorView != null) inspectorView.setSelection(ent);
      FileEditorTab ft = getActiveFileTab();
      if (ft != null) {
        JesScene2D scene = ft.getJesScene();
        if (scene != null && ent != null) {
          for (var e : scene.exportNamed().entrySet()) {
            if (e.getValue() == ent) { lastSelectedName = e.getKey(); break; }
          }
        }
      }
    });
    editor.setOnStatus(s -> status.setText(s));
    editor.setCommandStack(commands);
    Tab tab = new Tab(f.getName(), editor);
    tab.setClosable(true);
    tab.setUserData(f);
    tab.setOnClosed(e -> { /* nothing special for now */ });
    filesTabs.getTabs().add(tab);
    filesTabs.getSelectionModel().select(tab);
    lastOpened = f;
    status.setText("Loaded: " + f.getName());
    updateContextForActiveTab();
    if (editor.getKind() == FileEditorTab.Kind.JES) {
      selectSceneTab();
    }
  }

  private void applyProjectRootToTabs() {
    if (filesTabs == null) return;
    for (Tab t : filesTabs.getTabs()) {
      if (t.getContent() instanceof com.jvn.editor.ui.FileEditorTab fet) {
        fet.setProjectRoot(projectRoot);
      }
    }
    if (mapEditorView != null) mapEditorView.setProjectRoot(projectRoot);
  }

  private FileEditorTab getActiveFileTab() {
    Tab t = filesTabs.getSelectionModel().getSelectedItem();
    if (t == null) return null;
    return (t.getContent() instanceof FileEditorTab) ? (FileEditorTab) t.getContent() : null;
  }

  private void updateContextForActiveTab() {
    FileEditorTab ft = getActiveFileTab();
    JesScene2D scene = (ft != null) ? ft.getJesScene() : null;
    if (inspectorView != null) inspectorView.setScene(scene);
    if (sgView != null && inspectorView != null) {
      sgView.setContext(
        scene,
        ent -> { selected = ent; inspectorView.setSelection(ent); },
        this::fitCameraToEntity,
        s -> status.setText(s)
      );
      sgView.refresh();
    }
    if (mapEditorView != null) {
      if (ft != null && ft.getKind() == FileEditorTab.Kind.JES && ft.getFile() != null && projectRoot != null) {
        mapEditorView.setContext(projectRoot, ft.getFile());
      } else {
        mapEditorView.clearContext();
      }
    }
  }

  private void fitCameraToEntity(Entity2D e) {
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getViewport() != null) {
      ft.getViewport().fitToEntity(e);
    }
  }

  private void resetCamera() {
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getViewport() != null) {
      ft.getViewport().getCamera().setPosition(0,0);
      ft.getViewport().getCamera().setZoom(1.0);
    }
  }

  private void fitCameraToContent() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    
    // For VNS files, open fullscreen preview
    if (ft.getKind() == FileEditorTab.Kind.VNS && ft.getVnPreview() != null) {
      openFullscreenVnPreview(ft);
      return;
    }
    
    // For JES files, fit camera to content
    ft.fitToContent();
  }

  private void openFullscreenVnPreview(FileEditorTab sourceTab) {
    // Create a new fullscreen stage with VN preview
    javafx.stage.Stage fullscreenStage = new javafx.stage.Stage();
    fullscreenStage.setTitle("VN Preview - " + (sourceTab.getFile() != null ? sourceTab.getFile().getName() : "Untitled"));
    
    // Create a new VnPreviewView for fullscreen
    com.jvn.editor.ui.VnPreviewView fullscreenPreview = new com.jvn.editor.ui.VnPreviewView();
    if (projectRoot != null) fullscreenPreview.setProjectRoot(projectRoot);
    
    // Copy the scenario from the source tab
    try {
      String code = null;
      var editorNode = sourceTab.getEditorNode();
      if (editorNode instanceof com.jvn.editor.ui.VnsCodeEditor vnsEditor) {
        code = vnsEditor.getText();
      }
      if (code != null && !code.isBlank()) {
        com.jvn.core.vn.script.VnScriptParser parser = new com.jvn.core.vn.script.VnScriptParser();
        com.jvn.core.vn.VnScenario scenario = parser.parseFromString(code);
        fullscreenPreview.setScenario(scenario);
      }
    } catch (Exception ex) {
      status.setText("Failed to load VN for fullscreen: " + ex.getMessage());
      return;
    }
    
    javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(fullscreenPreview);
    root.setStyle("-fx-background-color: black;");
    javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 720);
    
    // Animation timer for rendering
    javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
      long last = -1;
      @Override
      public void handle(long now) {
        if (last < 0) { last = now; return; }
        long dt = (now - last) / 1_000_000L;
        last = now;
        fullscreenPreview.setSize(scene.getWidth(), scene.getHeight());
        fullscreenPreview.render(dt);
      }
    };
    
    fullscreenStage.setScene(scene);
    fullscreenStage.setFullScreen(true);
    fullscreenStage.setFullScreenExitHint("Press ESC to exit fullscreen");
    
    // Stop timer when window closes
    fullscreenStage.setOnHidden(e -> timer.stop());
    
    timer.start();
    fullscreenStage.show();
    fullscreenPreview.requestFocus();
  }

  private void selectProjectTab() {
    if (tabProject != null && tabProject.getTabPane() != null) {
      tabProject.getTabPane().getSelectionModel().select(tabProject);
    }
  }

  private void selectSceneTab() {
    if (tabScene != null && tabScene.getTabPane() != null) {
      tabScene.getTabPane().getSelectionModel().select(tabScene);
    }
  }
}
