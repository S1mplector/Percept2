package com.jvn.editor;

import java.io.File;
import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.ui.AssetBrowserView;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.FileEditorTab;
import com.jvn.editor.ui.HelpCenterView;
import com.jvn.editor.ui.InspectorView;
import com.jvn.editor.ui.LayoutEditorLauncherView;
import com.jvn.editor.ui.LayoutStudioWindowManager;
import com.jvn.editor.ui.MenuFlowEditorView;
import com.jvn.editor.ui.NewProjectWizard;
import com.jvn.editor.ui.ProjectExplorerView;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.editor.ui.SettingsEditorView;
import com.jvn.editor.ui.StoryTimelineView;
import com.jvn.editor.ui.TilemapEditorView;
import com.jvn.editor.ui.VersionControlView;
import com.jvn.editor.ui.VnsDiagnosticsView;
import com.jvn.editor.ui.VnsFlowMapView;
import com.jvn.editor.ui.VnsScriptAnalyzer;
import com.jvn.editor.ui.WelcomeCenterView;
import com.jvn.editor.ui.actioneditor.PuppeteerWindow;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.sun.management.OperatingSystemMXBean;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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
  
  private ProjectExplorerView projView;
  private HelpCenterView helpCenterView;
  private StoryTimelineView timelineView;
  private VnsDiagnosticsView vnsDiagnosticsView;
  private VnsFlowMapView vnsFlowMapView;
  private AssetBrowserView assetBrowserView;
  private VersionControlView versionControlView;
  private LayoutEditorLauncherView layoutEditorLauncherView;
  private LayoutStudioWindowManager layoutStudioWindowManager;
  private MenuFlowEditorView menuFlowEditorView;
  private SettingsEditorView settingsEditor;
  private com.jvn.editor.ui.MenuThemeEditorView menuThemeEditor;
  private TilemapEditorView mapEditorView;
  private PuppeteerLauncherPanel puppeteerLauncherPanel;
  private Tab tabPuppeteerLauncher;
  private final CommandStack commands = new CommandStack();
  private TabPane leftTabs;
  private TabPane rightTabs;
  private WelcomeCenterView welcomeView;
  private Tab tabWelcome;
  private Tab tabProject;
  private Tab tabTimeline;
  private Tab tabInspector;
  private Tab tabHelp;
  private Tab tabVnsDiagnostics;
  private Tab tabVnsFlowMap;
  private Tab tabAssetBrowser;
  private Tab tabVersionControl;
  private Tab tabLayoutLauncher;
  private Tab tabMenuFlow;
  private Tab tabLeftAdd;
  private Tab tabRightAdd;
  private ScrollPane inspectorScroll;
  private File projectRoot;
  private OperatingSystemMXBean osBean;
  private long lastPerfUpdateNs = -1L;
  private double lastFps = 0.0;
  private static final Color CPU_COLOR = Color.web("#f27333");
  private static final Color GPU_COLOR = Color.web("#a855f7");
  private static final Color RAM_COLOR = Color.web("#49a5ff");
  private static final Color GRID_BG = Color.color(0.08, 0.08, 0.08, 0.8);
  private static final Color GRID_LINE = Color.color(1, 1, 1, 0.08);
  private static final String[] EDITABLE_EXTENSIONS = new String[] {
      ".jes", ".txt", ".vns", ".java", ".timeline", ".theme", ".menu", ".layout", ".style", ".registry",
      ".settings", ".project", ".properties", ".md", ".json",
      ".yaml", ".yml", ".toml", ".ini", ".cfg", ".xml", ".csv", ".tsv"
  };
  private static final String DEFAULT_TIMELINE_PATH = "config/timeline/story.timeline";
  private static final String LEGACY_TIMELINE_PATH = "story/story.timeline";
  private static final String LEGACY_TIMELINE_ROOT_PATH = "story.timeline";

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
      EditorTheme.apply(a);
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doRunProject(Stage stage) {
    File root = ensureProjectRoot(stage);
    if (root == null) return;
    doRunProject(root);
  }

  private void doRunProject(File root) {
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
      runVnProjectInRuntime(root, mf);
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
      openProjectDirectory(root);
      selectProjectTab();
    }
    return root;
  }

  private void openProjectDirectory(File dir) {
    if (dir == null || !dir.exists() || !dir.isDirectory()) return;
    this.projectRoot = dir;
    Properties mf = loadManifest(dir);
    configureProjectContext(dir, mf);
    applyProjectRootToTabs();
    if (welcomeView != null) {
      welcomeView.setCurrentProject(dir);
      welcomeView.markProjectVisited(dir);
    }
  }

  private Properties loadManifest(File dir) {
    try (FileInputStream fis = new FileInputStream(new File(dir, "jvn.project"))) {
      Properties p = new Properties();
      p.load(fis);
      return p;
    } catch (Exception ignore) { return null; }
  }

  private File resolveTimelineFile(File root, Properties mf) {
    if (root == null) return null;
    if (mf != null) {
      String configured = mf.getProperty("timeline");
      if (configured != null && !configured.isBlank()) {
        return new File(root, configured.trim());
      }
    }
    File modern = new File(root, DEFAULT_TIMELINE_PATH);
    if (modern.exists()) return modern;
    File legacyStoryDir = new File(root, LEGACY_TIMELINE_PATH);
    if (legacyStoryDir.exists()) return legacyStoryDir;
    File legacyRoot = new File(root, LEGACY_TIMELINE_ROOT_PATH);
    if (legacyRoot.exists()) return legacyRoot;
    return modern;
  }

  private void configureProjectContext(File root, Properties mf) {
    if (root == null) return;
    if (projView != null) projView.setRootDirectory(root);
    if (timelineView != null) {
      timelineView.setTimelineFile(resolveTimelineFile(root, mf));
      timelineView.setProjectRoot(root);
    }
    if (settingsEditor != null) settingsEditor.setProjectRoot(root);
    if (menuThemeEditor != null) menuThemeEditor.setProjectRoot(root);
    if (mapEditorView != null) mapEditorView.setProjectRoot(root);
    if (helpCenterView != null) helpCenterView.setProjectRoot(root);
    if (assetBrowserView != null) assetBrowserView.setProjectRoot(root);
    if (versionControlView != null) versionControlView.setProjectRoot(root);
    if (layoutEditorLauncherView != null) layoutEditorLauncherView.setProjectRoot(root);
    if (menuFlowEditorView != null) menuFlowEditorView.setProjectRoot(root);
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
    boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
    File gradlew = new File(root, windows ? "gradlew.bat" : "gradlew");
    File gradleUserHome = new File(root, ".jvn-gradle-user-home");
    try {
      if (!gradleUserHome.exists()) gradleUserHome.mkdirs();
      java.util.List<String> cmd = new java.util.ArrayList<>();
      if (gradlew.exists()) cmd.add(gradlew.getAbsolutePath()); else cmd.add("gradle");
      cmd.add("--no-daemon");
      cmd.add("--console=plain");
      cmd.add("--gradle-user-home");
      cmd.add(gradleUserHome.getAbsolutePath());
      cmd.add("-Dorg.gradle.vfs.watch=false");
      cmd.add(task);
      if (args != null) {
        for (String arg : args) {
          if (arg != null && !arg.isBlank()) cmd.add(arg);
        }
      }
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(root);
      pb.redirectErrorStream(true);
      pb.environment().put("GRADLE_USER_HOME", gradleUserHome.getAbsolutePath());
      Process p = pb.start();
      javafx.stage.Stage logStage = new javafx.stage.Stage();
      javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea();
      ta.setEditable(false);
      logStage.setTitle(title);
      javafx.scene.Scene logScene = new javafx.scene.Scene(new javafx.scene.layout.BorderPane(ta), 800, 500);
      EditorTheme.apply(logScene);
      logStage.setScene(logScene);
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

  private void runVnProjectInRuntime(File root, Properties mf) {
    String entryVns = mf.getProperty("entryVns", "scripts/story/prologue.vns").trim();
    String runtimeScript = normalizeRuntimeScriptPath(entryVns);
    File workspaceRoot = resolveWorkspaceRoot();
    if (workspaceRoot == null) {
      status.setText("Cannot locate JVN workspace root");
      return;
    }

    StringBuilder runtimeArgs = new StringBuilder();
    runtimeArgs.append("--assets ").append(quoteCliArg(root.getAbsolutePath()));
    runtimeArgs.append(" --script ").append(quoteCliArg(runtimeScript));

    String title = mf.getProperty("name", "").trim();
    if (!title.isBlank()) runtimeArgs.append(" --title ").append(quoteCliArg(title));

    String width = mf.getProperty("width", "").trim();
    if (!width.isBlank()) runtimeArgs.append(" --width ").append(width);
    String height = mf.getProperty("height", "").trim();
    if (!height.isBlank()) runtimeArgs.append(" --height ").append(height);

    runGradle(workspaceRoot, ":runtime:run", new String[] { "--args=" + runtimeArgs }, "JVN Runtime");
    status.setText("Launching runtime: " + root.getName());
  }

  private String normalizeRuntimeScriptPath(String entryVns) {
    if (entryVns == null || entryVns.isBlank()) return "story/prologue.vns";
    String script = entryVns.trim().replace('\\', '/');
    if (script.startsWith("./")) script = script.substring(2);
    if (script.startsWith("game/scripts/")) script = script.substring("game/scripts/".length());
    if (script.startsWith("scripts/")) script = script.substring("scripts/".length());
    return script;
  }

  private String quoteCliArg(String raw) {
    String v = raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    return "\"" + v + "\"";
  }

  private File resolveWorkspaceRoot() {
    File fromCwd = findGradleRoot(new File(System.getProperty("user.dir", ".")));
    if (fromCwd != null) return fromCwd;
    return findGradleRoot(new File("."));
  }

  private File findGradleRoot(File start) {
    File cur = start;
    while (cur != null) {
      File gradlew = new File(cur, "gradlew");
      File gradlewBat = new File(cur, "gradlew.bat");
      File settings = new File(cur, "settings.gradle.kts");
      File settingsGroovy = new File(cur, "settings.gradle");
      if ((gradlew.exists() || gradlewBat.exists()) && (settings.exists() || settingsGroovy.exists())) {
        return cur;
      }
      cur = cur.getParentFile();
    }
    return null;
  }

  private void doNewProject(Stage stage) {
    File projectDir = NewProjectWizard.showAndWait(stage);
    if (projectDir == null) return;

    openProjectDirectory(projectDir);
    Properties mf = loadManifest(projectDir);
    
    // Open the entry script
    String entryRel = (mf != null) ? mf.getProperty("entryVns", "scripts/story/prologue.vns") : "scripts/story/prologue.vns";
    File entryScript = new File(projectDir, entryRel);
    if (!entryScript.exists() && "scripts/story/prologue.vns".equals(entryRel)) {
      entryScript = new File(projectDir, "scripts/prologue.vns");
    }
    if (entryScript.exists()) {
      openFile(entryScript);
    }
    
    selectProjectTab();
    status.setText("Created project: " + projectDir.getName());
  }

  private void openSample(String absolutePath) {
    File f = new File(absolutePath);
    if (!f.exists()) {
      Alert a = new Alert(Alert.AlertType.ERROR, "Sample not found: " + absolutePath);
      EditorTheme.apply(a);
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
      return;
    }
    openFile(f);
  }

  private void openTimelineArc(StoryTimelineView.Arc arc) {
    if (arc == null || arc.script == null || arc.script.isBlank()) return;
    File target = resolveProjectFile(arc.script);
    if (target == null || !target.exists()) {
      status.setText("Arc script not found: " + arc.script);
      return;
    }
    openFile(target);
    if (arc.entryLabel != null && !arc.entryLabel.isBlank()) {
      status.setText("Opened arc " + arc.name + " @ " + arc.entryLabel);
    } else {
      status.setText("Opened arc " + arc.name);
    }
  }

  private void openTimelineLinkTarget(StoryTimelineView.Link link) {
    if (link == null || timelineView == null || link.toArc == null || link.toArc.isBlank()) return;
    StoryTimelineView.Arc target = timelineView.findArc(link.toArc);
    if (target == null) {
      status.setText("Unknown target arc: " + link.toArc);
      return;
    }
    openTimelineArc(target);
  }

  // legacy helpers removed; ViewportView now owned by per-file tabs

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("JVN Editor");
    layoutStudioWindowManager = new LayoutStudioWindowManager(primaryStage);
    BorderPane root = new BorderPane();
    String editorVersion = resolveEditorVersion();

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
    MenuItem miCloseTab = new MenuItem("Close Tab");
    miCloseTab.setOnAction(e -> closeActiveTab());
    MenuItem miSave = new MenuItem("Save");
    miSave.setOnAction(e -> doSave(primaryStage));
    MenuItem miSaveAs = new MenuItem("Save As...");
    miSaveAs.setOnAction(e -> doSaveAs(primaryStage));
    miOpenProject.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
    miOpen.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    miOpenVns.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
    miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
    miSaveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    miCloseTab.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
    menuFile.getItems().addAll(miNewProject, miOpenProject, new SeparatorMenuItem(), miOpen, miOpenVns, miSave, miSaveAs, miCloseTab);

    Menu menuCode = new Menu("Code");
    MenuItem miApplyCode = new MenuItem("Apply Code");
    miApplyCode.setOnAction(e -> applyCodeFromEditor());
    miApplyCode.setAccelerator(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN));
    MenuItem miToggleEditorFullscreen = new MenuItem("Toggle Editor Fullscreen");
    miToggleEditorFullscreen.setOnAction(e -> toggleActiveEditorFullscreen());
    miToggleEditorFullscreen.setAccelerator(new KeyCodeCombination(KeyCode.F11));
    menuCode.getItems().addAll(miApplyCode, miToggleEditorFullscreen);

    Menu menuProject = new Menu("Project");
    MenuItem miRun = new MenuItem("Run Project");
    miRun.setOnAction(e -> doRunProject(primaryStage));
    MenuItem miMenuFlow = new MenuItem("Menu Flow Editor");
    miMenuFlow.setOnAction(e -> selectMenuFlowTab());
    menuProject.getItems().addAll(miRun, miMenuFlow);
    Menu menuVcs = new Menu("Version Control");
    MenuItem miOpenVcs = new MenuItem("Open Version Control");
    miOpenVcs.setOnAction(e -> selectVersionControlTab());
    miOpenVcs.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miRefreshVcs = new MenuItem("Refresh Status");
    miRefreshVcs.setOnAction(e -> {
      if (versionControlView != null) versionControlView.refreshStatus();
      selectVersionControlTab();
    });
    menuVcs.getItems().addAll(miOpenVcs, miRefreshVcs);
    Menu menuHelp = new Menu("Help");
    MenuItem miWelcome = new MenuItem("Welcome");
    miWelcome.setOnAction(e -> selectWelcomeTab());
    miWelcome.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miHelpCenter = new MenuItem("Help Center");
    miHelpCenter.setOnAction(e -> selectHelpTab());
    miHelpCenter.setAccelerator(new KeyCodeCombination(KeyCode.F1));
    MenuItem miRefreshHelp = new MenuItem("Refresh Docs Index");
    miRefreshHelp.setOnAction(e -> {
      if (helpCenterView != null) helpCenterView.refresh();
    });
    menuHelp.getItems().addAll(miWelcome, miHelpCenter, miRefreshHelp);
    Menu menuEdit = new Menu("Edit");
    MenuItem miUndo = new MenuItem("Undo");
    miUndo.setOnAction(e -> { commands.undo(); status.setText("Undo"); inspectorView.setSelection(selected); });
    miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRedo = new MenuItem("Redo");
    miRedo.setOnAction(e -> { commands.redo(); status.setText("Redo"); inspectorView.setSelection(selected); });
    miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    menuEdit.getItems().addAll(miUndo, miRedo);
    
    Menu menuTools = new Menu("Tools");
    MenuItem miActionEditor = new MenuItem("Puppeteer");
    miActionEditor.setOnAction(e -> openActionEditor());
    miActionEditor.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    menuTools.getItems().addAll(miActionEditor);
    
    mb.getMenus().addAll(menuFile, menuEdit, menuCode, menuProject, menuTools, menuVcs, menuHelp);

    // Toolbar
    osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    BorderPane toolbar = new BorderPane();
    toolbar.getStyleClass().add("master-toolbar");
    Button btnOpen = new Button("Open"); btnOpen.setOnAction(e -> doOpenProject(primaryStage));
    Button btnSave = new Button("Save"); btnSave.setOnAction(e -> doSave(primaryStage));
    Button btnUndo = new Button("Undo"); btnUndo.setOnAction(e -> { commands.undo(); status.setText("Undo"); inspectorView.setSelection(selected); });
    Button btnRedo = new Button("Redo"); btnRedo.setOnAction(e -> { commands.redo(); status.setText("Redo"); inspectorView.setSelection(selected); });
    Button btnApply = new Button("Apply Code"); btnApply.setOnAction(e -> applyCodeFromEditor());
    Button btnFullscreen = new Button("Fullscreen"); btnFullscreen.setOnAction(e -> toggleActiveEditorFullscreen());
    // Icons to the right of text
    btnOpen.setGraphic(icon("icon", "icon-open"));
    btnOpen.setContentDisplay(ContentDisplay.RIGHT);
    btnOpen.setGraphicTextGap(6);
    btnSave.setGraphic(icon("icon", "icon-save"));
    btnSave.setContentDisplay(ContentDisplay.RIGHT);
    btnSave.setGraphicTextGap(6);
    btnUndo.setGraphic(icon("icon", "icon-undo"));
    btnUndo.setContentDisplay(ContentDisplay.RIGHT);
    btnUndo.setGraphicTextGap(6);
    btnRedo.setGraphic(icon("icon", "icon-redo"));
    btnRedo.setContentDisplay(ContentDisplay.RIGHT);
    btnRedo.setGraphicTextGap(6);
    btnApply.setGraphic(icon("icon", "icon-apply"));
    btnApply.setContentDisplay(ContentDisplay.RIGHT);
    btnApply.setGraphicTextGap(6);
    btnFullscreen.setGraphic(icon("icon", "icon-fullscreen"));
    btnFullscreen.setContentDisplay(ContentDisplay.RIGHT);
    btnFullscreen.setGraphicTextGap(6);
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
    btnOpen.setTooltip(new Tooltip("Open Project (Cmd+O)"));
    btnSave.setTooltip(new Tooltip("Save (Cmd+S)"));
    btnUndo.setTooltip(new Tooltip("Undo (Cmd+Z)"));
    btnRedo.setTooltip(new Tooltip("Redo (Shift+Cmd+Z)"));
    btnApply.setTooltip(new Tooltip("Apply Code (Cmd+Enter)"));
    btnFullscreen.setTooltip(new Tooltip("Toggle Editor Fullscreen (F11)"));
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox row = new HBox(8);
    row.getChildren().addAll(btnOpen, btnSave, btnUndo, btnRedo, btnApply, btnFullscreen, spacer);
    VBox toolRows = new VBox(6, row);
    HBox.setHgrow(toolRows, Priority.ALWAYS);
    Label wordmark = new Label("JVN");
    wordmark.getStyleClass().add("jvn-wordmark");
    Label verLabel = new Label("v" + editorVersion);
    verLabel.getStyleClass().add("jvn-wordmark-version");
    VBox logoBox = new VBox(2);
    logoBox.setAlignment(Pos.CENTER_RIGHT);
    logoBox.getStyleClass().add("jvn-wordmark-box");
    logoBox.getChildren().addAll(wordmark, verLabel);
    toolbar.setLeft(toolRows);

    VBox perfBox = new VBox(4, perf, perfGraph.getCanvas());
    perfBox.setAlignment(Pos.CENTER);
    perfBox.setFillWidth(true);
    HBox.setHgrow(perfBox, Priority.ALWAYS);
    perfBox.widthProperty().addListener((o, ov, nv) -> {
      perfGraph.setWidth(Math.max(180.0, nv.doubleValue() - 22.0));
    });
    toolbar.setCenter(perfBox);
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
    welcomeView = new WelcomeCenterView();
    welcomeView.setEditorVersion(editorVersion);
    welcomeView.setWorkspaceRoot(resolveWorkspaceRoot());
    welcomeView.setCurrentProject(projectRoot);
    welcomeView.setOnCreateProject(() -> doNewProject(primaryStage));
    welcomeView.setOnOpenProjectDialog(() -> doOpenProject(primaryStage));
    welcomeView.setOnOpenRecentProject(projectDir -> {
      if (projectDir == null || !projectDir.isDirectory()) return;
      openProjectDirectory(projectDir);
      status.setText("Project: " + projectDir.getName());
      selectProjectTab();
    });
    tabWelcome = new Tab("Welcome", welcomeView);
    tabWelcome.setClosable(false);
    filesTabs.getTabs().add(tabWelcome);
    filesTabs.getSelectionModel().select(tabWelcome);
    root.setCenter(filesTabs);
    inspectorView = new InspectorView(s -> status.setText(s));
    inspectorView.setCommandStack(commands);
    inspectorView.setMinWidth(280);
    inspectorView.setPrefWidth(320);
    inspectorScroll = new ScrollPane(inspectorView);
    inspectorScroll.setFitToWidth(true);
    vnsDiagnosticsView = new VnsDiagnosticsView();
    vnsDiagnosticsView.setOnOpenLine(this::jumpToActiveVnsLine);
    vnsFlowMapView = new VnsFlowMapView();
    vnsFlowMapView.setOnOpenLine(this::jumpToActiveVnsLine);
    puppeteerLauncherPanel = new PuppeteerLauncherPanel();
    puppeteerLauncherPanel.setOnLaunch(snapshot -> launchPuppeteerFromSnapshot(snapshot));
    assetBrowserView = new AssetBrowserView();
    assetBrowserView.setProjectRoot(projectRoot);
    assetBrowserView.setOnOpenAsset(asset -> {
      if (asset == null) return;
      if (isEditableFile(asset)) {
        openFile(asset);
      } else {
        try { java.awt.Desktop.getDesktop().open(asset); } catch (Exception ignored) {}
      }
    });
    versionControlView = new VersionControlView();
    versionControlView.setProjectRoot(projectRoot);
    versionControlView.setOnOpenRelativePath(relativePath -> {
      if (projectRoot == null || relativePath == null || relativePath.isBlank()) return;
      File target = new File(projectRoot, relativePath);
      if (!target.exists()) return;
      if (isEditableFile(target)) openFile(target);
    });
    layoutEditorLauncherView = new LayoutEditorLauncherView();
    layoutEditorLauncherView.setProjectRoot(projectRoot);
    layoutEditorLauncherView.setOnOpenFile(target -> {
      if (target == null) return;
      if (isEditableFile(target)) {
        openFile(target);
      } else {
        try { java.awt.Desktop.getDesktop().open(target); } catch (Exception ignored) {}
      }
    });
    menuFlowEditorView = new MenuFlowEditorView();
    menuFlowEditorView.setProjectRoot(projectRoot);
    menuFlowEditorView.setOnOpenFile(target -> {
      if (target == null) return;
      if (isEditableFile(target)) {
        openFile(target);
      } else {
        try { java.awt.Desktop.getDesktop().open(target); } catch (Exception ignored) {}
      }
    });
    rightTabs = new TabPane();
    helpCenterView = new HelpCenterView();
    helpCenterView.setWorkspaceRoot(resolveWorkspaceRoot());
    helpCenterView.setProjectRoot(projectRoot);
    helpCenterView.setOnOpenDoc(this::openFile);
    tabRightAdd = new Tab("+", new Region()); tabRightAdd.setClosable(false);
    rightTabs.getTabs().addAll(tabRightAdd);
    installAddTabBehavior(rightTabs, tabRightAdd, this::showRightAddMenu);
    rightTabs.setPrefWidth(360);
    showRightAddMenu();
    timelineView = new StoryTimelineView();
    timelineView.setMinWidth(240);
    timelineView.setPrefWidth(320);
    timelineView.setOnRunArc(this::openTimelineArc);
    timelineView.setOnRunLink(this::openTimelineLinkTarget);
    projView = new ProjectExplorerView();
    projView.setOnOpenFile(f -> {
      if (f == null) return;
      if (isEditableFile(f)) {
        openFile(f);
      } else {
        try { java.awt.Desktop.getDesktop().open(f); } catch (Exception ignored) {}
      }
    });
    projView.setOnRunProject(projectDir -> {
      if (projectDir == null) return;
      openProjectDirectory(projectDir);
      selectProjectTab();
      doRunProject(projectDir);
    });
    leftTabs = new TabPane();
    tabProject = new Tab("Project", projView); tabProject.setClosable(false);
    tabLeftAdd = new Tab("+", new Region()); tabLeftAdd.setClosable(false);
    leftTabs.getTabs().addAll(tabProject, tabLeftAdd);
    installAddTabBehavior(leftTabs, tabLeftAdd, this::showLeftAddMenu);
    leftTabs.getSelectionModel().select(tabProject);
    leftTabs.setPrefWidth(300);
    SplitPane centerSplit = new SplitPane();
    centerSplit.getItems().addAll(leftTabs, filesTabs, rightTabs);
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
    primaryStage.setOnCloseRequest(e -> {
      if (!confirmCloseAllTabs()) {
        e.consume();
        return;
      }
      if (layoutStudioWindowManager != null && !layoutStudioWindowManager.requestCloseAll()) {
        e.consume();
      }
    });
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
        refreshTabDirtyIndicators();
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
      EditorTheme.apply(a);
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doOpenProject(Stage stage) {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setTitle("Open Project Directory");
    File dir = dc.showDialog(stage);
    if (dir == null) return;
    openProjectDirectory(dir);
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
      EditorTheme.apply(a);
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void toggleActiveEditorFullscreen() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    if (!ft.supportsEditorFullscreenToggle()) {
      status.setText("Editor already uses full area");
      return;
    }
    ft.toggleEditorFullscreen();
    status.setText(ft.isEditorFullscreen() ? "Editor fullscreen enabled" : "Editor split layout restored");
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
      Tab currentTab = filesTabs != null ? filesTabs.getSelectionModel().getSelectedItem() : null;
      FileChooser fc = new FileChooser();
      fc.setTitle("Save File");
      if (ft.getFile() != null) fc.setInitialFileName(ft.getFile().getName());
      File f = fc.showSaveDialog(stage);
      if (f == null) return;
      ft.saveTo(f);
      openFile(f);
      if (currentTab != null && filesTabs != null) filesTabs.getTabs().remove(currentTab);
    } catch (Exception ex) {
      status.setText("Save As failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to save as: " + ex.getMessage());
      EditorTheme.apply(a);
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
    public void setWidth(double w) {
      canvas.setWidth(sanitizeCanvasDimension(w, 160.0));
      redraw();
    }

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
      if (!Double.isFinite(w) || !Double.isFinite(h) || w <= 0 || h <= 0) return;
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

    private static double sanitizeCanvasDimension(double value, double minimum) {
      if (!Double.isFinite(value)) return minimum;
      double min = Math.max(1.0, minimum);
      if (value < min) return min;
      if (value > 8192.0) return 8192.0;
      return value;
    }
  }

  private Region icon(String... styleClasses) {
    Region r = new Region();
    if (styleClasses != null) r.getStyleClass().addAll(styleClasses);
    return r;
  }

  private boolean isEditableFile(File f) {
    if (f == null || !f.isFile()) return false;
    String name = f.getName().toLowerCase();
    if ("menu.theme".equals(name) || "jvn.project".equals(name) || "vn.settings".equals(name)) return true;
    for (String ext : EDITABLE_EXTENSIONS) {
      if (name.endsWith(ext)) return true;
    }
    return false;
  }

  private void closeActiveTab() {
    if (filesTabs == null) return;
    Tab active = filesTabs.getSelectionModel().getSelectedItem();
    if (active == null) return;
    if (!active.isClosable()) return;
    if (active.getContent() instanceof FileEditorTab ft && !confirmCanCloseFileTab(ft)) return;
    filesTabs.getTabs().remove(active);
  }

  private boolean confirmCloseAllTabs() {
    if (filesTabs == null) return true;
    for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
      if (tab.getContent() instanceof FileEditorTab ft) {
        if (!confirmCanCloseFileTab(ft)) return false;
      }
    }
    return true;
  }

  private boolean confirmCanCloseFileTab(FileEditorTab ft) {
    if (ft == null || !ft.isDirty()) return true;
    ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
    ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
    Alert a = new Alert(Alert.AlertType.CONFIRMATION);
    EditorTheme.apply(a);
    a.setTitle("Unsaved Changes");
    a.setHeaderText("Save changes to " + ft.getDisplayName() + "?");
    a.setContentText("Your changes will be lost if you discard.");
    a.getButtonTypes().setAll(save, discard, ButtonType.CANCEL);
    Optional<ButtonType> r = a.showAndWait();
    if (r.isEmpty() || r.get() == ButtonType.CANCEL) return false;
    if (r.get() == discard) return true;
    File f = ft.getFile();
    if (f == null) return false;
    ft.saveTo(f);
    refreshTabDirtyIndicators();
    return !ft.isDirty();
  }

  private void refreshTabDirtyIndicators() {
    if (filesTabs == null) return;
    for (Tab t : filesTabs.getTabs()) {
      if (t.getContent() instanceof FileEditorTab ft) {
        updateTabTitle(t, ft);
      }
    }
  }

  private void updateTabTitle(Tab tab, FileEditorTab ft) {
    if (tab == null || ft == null) return;
    String base = ft.getDisplayName();
    tab.setText(ft.isDirty() ? (base + " *") : base);
  }

  private void openFile(File f) {
    if (f == null) return;

    if (layoutStudioWindowManager != null && layoutStudioWindowManager.supports(f)) {
      if (!closeFileTabIfOpen(f)) return;
      layoutStudioWindowManager.open(f, projectRoot, s -> {
        if (status != null && s != null && !s.isBlank()) status.setText(s);
      });
      return;
    }

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
    if (editor.getKind() == FileEditorTab.Kind.VNS) {
      editor.setOnVnsTextChanged(text -> {
        if (editor != getActiveFileTab()) return;
        refreshVnsToolPanels(editor, text);
        if (puppeteerLauncherPanel != null) puppeteerLauncherPanel.setSource(text);
      });
      editor.setOnVnsCaretLineChanged(line -> {
        if (editor != getActiveFileTab()) return;
        if (puppeteerLauncherPanel != null) puppeteerLauncherPanel.setCaretLine(line);
      });
    }
    Tab tab = new Tab(editor.getDisplayName(), editor);
    tab.setClosable(true);
    tab.setUserData(f);
    updateTabTitle(tab, editor);
    tab.setOnCloseRequest(e -> {
      if (!confirmCanCloseFileTab(editor)) e.consume();
    });
    filesTabs.getTabs().add(tab);
    filesTabs.getSelectionModel().select(tab);
    lastOpened = f;
    status.setText("Loaded: " + f.getName());
    updateContextForActiveTab();
    if (editor.getKind() == FileEditorTab.Kind.VNS || editor.getKind() == FileEditorTab.Kind.TIMELINE) {
      selectTimelineTab();
    }
  }

  private boolean closeFileTabIfOpen(File file) {
    if (filesTabs == null || file == null) return true;
    for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
      if (!(tab.getUserData() instanceof File openFile) || !openFile.equals(file)) continue;
      if (tab.getContent() instanceof FileEditorTab ft && !confirmCanCloseFileTab(ft)) {
        return false;
      }
      filesTabs.getTabs().remove(tab);
    }
    return true;
  }

  private void applyProjectRootToTabs() {
    if (filesTabs == null) return;
    for (Tab t : filesTabs.getTabs()) {
      if (t.getContent() instanceof com.jvn.editor.ui.FileEditorTab fet) {
        fet.setProjectRoot(projectRoot);
      }
    }
    if (mapEditorView != null) mapEditorView.setProjectRoot(projectRoot);
    if (helpCenterView != null) helpCenterView.setProjectRoot(projectRoot);
    if (assetBrowserView != null) assetBrowserView.setProjectRoot(projectRoot);
    if (versionControlView != null) versionControlView.setProjectRoot(projectRoot);
    if (layoutEditorLauncherView != null) layoutEditorLauncherView.setProjectRoot(projectRoot);
    if (menuFlowEditorView != null) menuFlowEditorView.setProjectRoot(projectRoot);
    if (welcomeView != null) welcomeView.setCurrentProject(projectRoot);
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
    if (mapEditorView != null) {
      if (ft != null && ft.getKind() == FileEditorTab.Kind.JES && ft.getFile() != null && projectRoot != null) {
        mapEditorView.setContext(projectRoot, ft.getFile());
      } else {
        mapEditorView.clearContext();
      }
    }
    refreshVnsToolPanels(ft, null);
    if (puppeteerLauncherPanel != null) {
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        puppeteerLauncherPanel.setSource(ft.getCurrentTextSnapshot());
        puppeteerLauncherPanel.setCaretLine(ft.getVnsCaretLine());
      } else {
        puppeteerLauncherPanel.clear();
      }
    }
  }

  private void refreshVnsToolPanels(FileEditorTab fileTab, String currentText) {
    if (vnsDiagnosticsView == null && vnsFlowMapView == null) return;
    if (fileTab == null || fileTab.getKind() != FileEditorTab.Kind.VNS) {
      if (vnsDiagnosticsView != null) vnsDiagnosticsView.clear();
      if (vnsFlowMapView != null) vnsFlowMapView.clear();
      return;
    }

    File scriptFile = fileTab.getFile();
    String source = currentText != null ? currentText : fileTab.getCurrentTextSnapshot();
    File analysisRoot = projectRoot;
    if (analysisRoot == null && scriptFile != null) {
      analysisRoot = scriptFile.getParentFile();
    }

    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(source, analysisRoot);
    if (vnsDiagnosticsView != null) vnsDiagnosticsView.setAnalysis(scriptFile, analysis);
    if (vnsFlowMapView != null) vnsFlowMapView.setAnalysis(scriptFile, analysis);
  }

  private void jumpToActiveVnsLine(int oneBasedLine) {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null || ft.getKind() != FileEditorTab.Kind.VNS) return;
    ft.navigateToLine(oneBasedLine);
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

  private void installAddTabBehavior(TabPane pane, Tab addTab, Runnable onAddRequested) {
    if (pane == null || addTab == null || onAddRequested == null) return;
    pane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
      if (newTab != addTab) return;
      Platform.runLater(() -> {
        Tab fallback = (oldTab != null && oldTab != addTab && pane.getTabs().contains(oldTab))
          ? oldTab
          : firstRegularTab(pane, addTab);
        if (fallback != null && pane.getTabs().contains(fallback)) {
          pane.getSelectionModel().select(fallback);
        }
        onAddRequested.run();
      });
    });
  }

  private Tab firstRegularTab(TabPane pane, Tab addTab) {
    if (pane == null) return null;
    for (Tab t : pane.getTabs()) {
      if (t != addTab) return t;
    }
    return null;
  }

  private Tab getAddTabForPane(TabPane pane) {
    if (pane == null) return null;
    if (pane == leftTabs) return tabLeftAdd;
    if (pane == rightTabs) return tabRightAdd;
    return null;
  }

  private void attachPanelTabToPane(Tab tab, TabPane targetPane) {
    if (tab == null || targetPane == null) return;
    TabPane current = tab.getTabPane();
    if (current == targetPane) return;
    if (current != null) current.getTabs().remove(tab);
    Tab addTab = getAddTabForPane(targetPane);
    int idx = (addTab != null) ? targetPane.getTabs().indexOf(addTab) : -1;
    if (idx < 0) idx = targetPane.getTabs().size();
    if (!targetPane.getTabs().contains(tab)) targetPane.getTabs().add(idx, tab);
  }

  private Tab ensureProjectTab(TabPane targetPane) {
    if (targetPane == null || projView == null) return null;
    if (tabProject == null) {
      tabProject = new Tab("Project", projView);
      tabProject.setClosable(false);
    }
    attachPanelTabToPane(tabProject, targetPane);
    return tabProject;
  }

  private Tab ensureTimelineTab(TabPane targetPane) {
    if (targetPane == null || timelineView == null) return null;
    if (tabTimeline == null) {
      tabTimeline = new Tab("Timeline", timelineView);
      tabTimeline.setClosable(true);
      tabTimeline.setOnClosed(e -> tabTimeline = null);
    }
    attachPanelTabToPane(tabTimeline, targetPane);
    return tabTimeline;
  }

  private Tab ensureHelpTab(TabPane targetPane) {
    if (targetPane == null || helpCenterView == null) return null;
    if (tabHelp == null) {
      tabHelp = new Tab("Help", helpCenterView);
      tabHelp.setClosable(true);
      tabHelp.setOnClosed(e -> tabHelp = null);
    }
    attachPanelTabToPane(tabHelp, targetPane);
    return tabHelp;
  }

  private Tab ensureInspectorTab(TabPane targetPane) {
    if (targetPane == null || inspectorScroll == null) return null;
    if (tabInspector == null) {
      tabInspector = new Tab("Inspector", inspectorScroll);
      tabInspector.setClosable(true);
      tabInspector.setOnClosed(e -> tabInspector = null);
    }
    attachPanelTabToPane(tabInspector, targetPane);
    return tabInspector;
  }

  private Tab ensureVnsDiagnosticsTab(TabPane targetPane) {
    if (targetPane == null || vnsDiagnosticsView == null) return null;
    if (tabVnsDiagnostics == null) {
      tabVnsDiagnostics = new Tab("VNS Diagnostics", vnsDiagnosticsView);
      tabVnsDiagnostics.setClosable(true);
      tabVnsDiagnostics.setOnClosed(e -> tabVnsDiagnostics = null);
    }
    attachPanelTabToPane(tabVnsDiagnostics, targetPane);
    return tabVnsDiagnostics;
  }

  private Tab ensureVnsFlowMapTab(TabPane targetPane) {
    if (targetPane == null || vnsFlowMapView == null) return null;
    if (tabVnsFlowMap == null) {
      tabVnsFlowMap = new Tab("Label Flow", vnsFlowMapView);
      tabVnsFlowMap.setClosable(true);
      tabVnsFlowMap.setOnClosed(e -> tabVnsFlowMap = null);
    }
    attachPanelTabToPane(tabVnsFlowMap, targetPane);
    return tabVnsFlowMap;
  }

  private Tab ensureAssetBrowserTab(TabPane targetPane) {
    if (targetPane == null || assetBrowserView == null) return null;
    if (tabAssetBrowser == null) {
      tabAssetBrowser = new Tab("Assets", assetBrowserView);
      tabAssetBrowser.setClosable(true);
      tabAssetBrowser.setOnClosed(e -> tabAssetBrowser = null);
    }
    attachPanelTabToPane(tabAssetBrowser, targetPane);
    return tabAssetBrowser;
  }

  private Tab ensureVersionControlTab(TabPane targetPane) {
    if (targetPane == null || versionControlView == null) return null;
    if (tabVersionControl == null) {
      tabVersionControl = new Tab("Version Control", versionControlView);
      tabVersionControl.setClosable(true);
      tabVersionControl.setOnClosed(e -> tabVersionControl = null);
    }
    attachPanelTabToPane(tabVersionControl, targetPane);
    return tabVersionControl;
  }

  private Tab ensureLayoutLauncherTab(TabPane targetPane) {
    if (targetPane == null || layoutEditorLauncherView == null) return null;
    if (tabLayoutLauncher == null) {
      tabLayoutLauncher = new Tab("Layout Launcher", layoutEditorLauncherView);
      tabLayoutLauncher.setClosable(true);
      tabLayoutLauncher.setOnClosed(e -> tabLayoutLauncher = null);
    }
    attachPanelTabToPane(tabLayoutLauncher, targetPane);
    return tabLayoutLauncher;
  }

  private Tab ensureMenuFlowTab(TabPane targetPane) {
    if (targetPane == null || menuFlowEditorView == null) return null;
    if (tabMenuFlow == null) {
      tabMenuFlow = new Tab("Menu Flow", menuFlowEditorView);
      tabMenuFlow.setClosable(true);
      tabMenuFlow.setOnClosed(e -> tabMenuFlow = null);
    }
    attachPanelTabToPane(tabMenuFlow, targetPane);
    return tabMenuFlow;
  }

  private String panelActionLabel(String panelName, Tab tab, TabPane targetPane) {
    if (tab != null && tab.getTabPane() == targetPane) return "Open " + panelName;
    if (tab != null && tab.getTabPane() != null) return "Move " + panelName + " Here";
    return "Add " + panelName;
  }

  private void addChooserActionButton(javafx.scene.layout.VBox actions, String text, String iconClass, Runnable action) {
    if (actions == null || text == null || action == null) return;
    Button button = new Button(text);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setAlignment(Pos.CENTER_LEFT);
    if (iconClass != null && !iconClass.isBlank()) {
      button.setGraphic(icon("icon", iconClass));
      button.setContentDisplay(ContentDisplay.LEFT);
      button.setGraphicTextGap(8);
    }
    button.setOnAction(e -> action.run());
    actions.getChildren().add(button);
  }

  private void openPanelChooserTab(TabPane pane, boolean leftSide) {
    if (pane == null) return;
    Tab addTab = leftSide ? tabLeftAdd : tabRightAdd;
    if (addTab == null) return;

    String title = leftSide ? "Add Left Panel" : "Add Right Panel";
    String details = leftSide
      ? "Choose a panel to add on the left. This keeps the workspace focused by default."
      : "Choose a panel to add on the right. Add only the tools you need for the current workflow.";

    javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
    root.setPadding(new javafx.geometry.Insets(12));
    Label heading = new Label(title);
    heading.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");
    Label info = new Label(details);
    info.setWrapText(true);
    javafx.scene.layout.VBox actions = new javafx.scene.layout.VBox(8);
    addChooserActionButton(actions, panelActionLabel("Project", tabProject, pane), "icon-panel-project", () -> {
      Tab t = ensureProjectTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
    });
    addChooserActionButton(actions, panelActionLabel("Timeline", tabTimeline, pane), "icon-panel-timeline", () -> {
      Tab t = ensureTimelineTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
    });
    addChooserActionButton(actions, panelActionLabel("VNS Diagnostics", tabVnsDiagnostics, pane), "icon-panel-diagnostics", () -> {
      Tab t = ensureVnsDiagnosticsTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
    });
    addChooserActionButton(actions, panelActionLabel("Label Flow", tabVnsFlowMap, pane), "icon-panel-flow", () -> {
      Tab t = ensureVnsFlowMapTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
    });
    addChooserActionButton(actions, panelActionLabel("Assets", tabAssetBrowser, pane), "icon-panel-assets", () -> {
      Tab t = ensureAssetBrowserTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
    });
    addChooserActionButton(actions, panelActionLabel("Layout Launcher", tabLayoutLauncher, pane), "icon-panel-layouts", () -> {
      Tab t = ensureLayoutLauncherTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
      if (layoutEditorLauncherView != null) layoutEditorLauncherView.refreshStatus();
    });
    addChooserActionButton(actions, panelActionLabel("Menu Flow", tabMenuFlow, pane), "icon-panel-menuflow", () -> {
      Tab t = ensureMenuFlowTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
      if (menuFlowEditorView != null) menuFlowEditorView.refreshStatus();
    });
    addChooserActionButton(actions, panelActionLabel("Version Control", tabVersionControl, pane), "icon-panel-vcs", () -> {
      Tab t = ensureVersionControlTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
      if (versionControlView != null) versionControlView.refreshStatus();
    });
    addChooserActionButton(actions, panelActionLabel("Help", tabHelp, pane), "icon-panel-help", () -> {
      Tab t = ensureHelpTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
    });
    addChooserActionButton(actions, panelActionLabel("Inspector", tabInspector, pane), "icon-panel-inspector", () -> {
      Tab t = ensureInspectorTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
    });
    addChooserActionButton(actions, panelActionLabel("Puppeteer Launcher", tabPuppeteerLauncher, pane), "icon-panel-puppeteer", () -> {
      Tab t = ensurePuppeteerLauncherTab(pane);
      if (t != null && pane != null) pane.getSelectionModel().select(t);
    });

    root.getChildren().addAll(heading, info, new javafx.scene.control.Separator(), actions);
    Tab chooser = new Tab("New Panel", root);
    chooser.setClosable(true);
    int addIdx = pane.getTabs().indexOf(addTab);
    if (addIdx < 0) addIdx = pane.getTabs().size();
    pane.getTabs().add(addIdx, chooser);
    pane.getSelectionModel().select(chooser);
  }

  private void showLeftAddMenu() {
    openPanelChooserTab(leftTabs, true);
  }

  private void showRightAddMenu() {
    openPanelChooserTab(rightTabs, false);
  }

  private void selectProjectTab() {
    if (tabProject != null && leftTabs != null && leftTabs.getTabs().contains(tabProject)) {
      leftTabs.getSelectionModel().select(tabProject);
    }
  }

  private void selectWelcomeTab() {
    if (filesTabs == null || tabWelcome == null) return;
    if (!filesTabs.getTabs().contains(tabWelcome)) {
      filesTabs.getTabs().add(0, tabWelcome);
    }
    filesTabs.getSelectionModel().select(tabWelcome);
    if (welcomeView != null) welcomeView.refresh();
  }

  private void selectTimelineTab() {
    Tab t = (tabTimeline != null && tabTimeline.getTabPane() != null) ? tabTimeline : ensureTimelineTab(leftTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectHelpTab() {
    Tab t = (tabHelp != null && tabHelp.getTabPane() != null) ? tabHelp : ensureHelpTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectVersionControlTab() {
    Tab t = (tabVersionControl != null && tabVersionControl.getTabPane() != null)
        ? tabVersionControl
        : ensureVersionControlTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (versionControlView != null) versionControlView.refreshStatus();
  }

  private void selectMenuFlowTab() {
    Tab t = (tabMenuFlow != null && tabMenuFlow.getTabPane() != null)
        ? tabMenuFlow
        : ensureMenuFlowTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (menuFlowEditorView != null) menuFlowEditorView.refreshStatus();
  }

  private Tab ensurePuppeteerLauncherTab(TabPane targetPane) {
    if (targetPane == null || puppeteerLauncherPanel == null) return null;
    if (tabPuppeteerLauncher == null) {
      tabPuppeteerLauncher = new Tab("Puppeteer Launcher", puppeteerLauncherPanel);
      tabPuppeteerLauncher.setClosable(true);
      tabPuppeteerLauncher.setOnClosed(e -> tabPuppeteerLauncher = null);
    }
    attachPanelTabToPane(tabPuppeteerLauncher, targetPane);
    return tabPuppeteerLauncher;
  }

  private void launchPuppeteerFromSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
    PuppeteerWindow puppeteer = new PuppeteerWindow();
    puppeteer.setOnCopyCode(code -> status.setText("Copied timeline code to clipboard"));
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getJesScene() != null) {
      puppeteer.setScene(ft.getJesScene());
    }
    if (snapshot != null) {
      String title = "Puppeteer";
      if (snapshot.currentLabel != null) title += " @ " + snapshot.currentLabel;
      title += " (line " + (snapshot.atLine + 1) + ")";
      puppeteer.setTitle(title);
    }
    puppeteer.show();
  }

  private void openActionEditor() {
    PuppeteerWindow puppeteer = new PuppeteerWindow();
    puppeteer.setOnCopyCode(code -> status.setText("Copied timeline code to clipboard"));
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getJesScene() != null) {
      puppeteer.setScene(ft.getJesScene());
    }
    puppeteer.show();
  }

  private String resolveEditorVersion() {
    String version = System.getProperty("jvn.version");
    if (version == null || version.isBlank()) {
      Package pkg = EditorApp.class.getPackage();
      version = (pkg != null && pkg.getImplementationVersion() != null)
          ? pkg.getImplementationVersion()
          : "dev";
    }
    return version;
  }
}
