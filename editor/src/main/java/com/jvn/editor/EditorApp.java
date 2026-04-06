package com.jvn.editor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;

import javax.tools.ToolProvider;

import com.jvn.audiofx.AudioFxNativeBridge;
import com.jvn.core.nativebridge.NativeLibraryLoader;
import com.jvn.core.nativebridge.NativeMathBridge;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.ui.AssetBrowserView;
import com.jvn.editor.ui.CssIcon;
import com.jvn.editor.ui.EditorPanelPlacement;
import com.jvn.editor.ui.EditorPreferences;
import com.jvn.editor.ui.EditorPreferencesStore;
import com.jvn.editor.ui.EditorSettingsView;
import com.jvn.editor.ui.EditorSidebarPanel;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.FileEditorTab;
import com.jvn.editor.ui.HelpCenterView;
import com.jvn.editor.ui.ImageAttributesToolView;
import com.jvn.editor.ui.ImageTintToolView;
import com.jvn.editor.ui.ImageToolPanel;
import com.jvn.editor.ui.InspectorView;
import com.jvn.editor.ui.LayeredImageVisualizerView;
import com.jvn.editor.ui.LayoutEditorLauncherView;
import com.jvn.editor.ui.LayoutStudioWindowManager;
import com.jvn.editor.ui.MenuFlowEditorView;
import com.jvn.editor.ui.NewProjectWizard;
import com.jvn.editor.ui.PhoneAssetsToolView;
import com.jvn.editor.ui.ProjectExplorerView;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.editor.ui.RunConsoleView;
import com.jvn.editor.ui.ScriptEditorLauncherView;
import com.jvn.editor.ui.SettingsEditorView;
import com.jvn.editor.ui.StartupSplashOverlay;
import com.jvn.editor.ui.StoryboardOverlayState;
import com.jvn.editor.ui.StoryboardOverlayView;
import com.jvn.editor.ui.StoryTimelineView;
import com.jvn.editor.ui.TilemapEditorView;
import com.jvn.editor.ui.VersionControlView;
import com.jvn.editor.ui.VnsDiagnosticsView;
import com.jvn.editor.ui.VnsFlowMapView;
import com.jvn.editor.ui.VnsScriptAnalyzer;
import com.jvn.editor.ui.WelcomeCenterView;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.CodeImporter;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.editor.ui.actioneditor.PuppeteerWindow;
import com.jvn.editor.ui.actioneditor.PropertyType;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.sun.management.OperatingSystemMXBean;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
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
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

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
  private PhoneAssetsToolView phoneAssetsToolView;
  private StoryboardOverlayView storyboardOverlayView;
  private LayeredImageVisualizerView layeredImageVisualizerView;
  private ImageAttributesToolView imageAttributesToolView;
  private ImageTintToolView imageTintToolView;
  private LayoutStudioWindowManager layoutStudioWindowManager;
  private MenuFlowEditorView menuFlowEditorView;
  private EditorSettingsView editorSettingsView;
  private SettingsEditorView settingsEditor;
  private com.jvn.editor.ui.MenuThemeEditorView menuThemeEditor;
  private TilemapEditorView mapEditorView;
  private PuppeteerLauncherPanel puppeteerLauncherPanel;
  private ScriptEditorLauncherView scriptEditorLauncherView;
  private Tab tabEditorSettings;
  private Tab tabPuppeteerLauncher;
  private Tab tabScriptEditorLauncher;
  private final CommandStack commands = new CommandStack();
  private Runnable refreshMainCommandUi = () -> {};
  private TabPane leftTabs;
  private TabPane rightTabs;
  private SplitPane centerSplit;
  private boolean editorFullscreen;
  private double[] savedCenterDividers;
  private boolean layeredVisualizerFullscreen;
  private double[] savedLayeredVisualizerDividers;
  private ImageToolPanel fullscreenImageToolView;
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
  private Tab tabPhoneAssetsTool;
  private Tab tabStoryboardOverlay;
  private Tab tabLayeredImageVisualizer;
  private Tab tabImageAttributesTool;
  private Tab tabImageTintTool;
  private Tab tabMenuFlow;
  private Tab tabLeftAdd;
  private Tab tabRightAdd;
  private StackPane leftSidebarDividerNode;
  private StackPane rightSidebarDividerNode;
  private Region leftSidebarHiddenArrow;
  private Region rightSidebarHiddenArrow;
  private ScrollPane inspectorScroll;
  private File projectRoot;
  private StoryboardOverlayState storyboardOverlayState = StoryboardOverlayState.none();
  private EditorPreferencesStore editorPreferencesStore;
  private EditorPreferences editorPreferences = EditorPreferences.defaults();
  private OperatingSystemMXBean osBean;
  private long lastPerfUpdateNs = -1L;
  private double lastFps = 0.0;
  private double smoothedProcessCpu = Double.NaN;
  private double smoothedFps = Double.NaN;
  private long lastGcCollectionCount = -1L;
  private long lastGcCollectionTimeMs = -1L;
  private static final long PERF_UPDATE_INTERVAL_NS = 300_000_000L;
  private static final double PERF_CPU_SMOOTH_ALPHA = 0.28;
  private static final double PERF_FPS_SMOOTH_ALPHA = 0.20;
  private static final double TARGET_FPS = 60.0;
  private static final Color CPU_COLOR = Color.web("#f27333");
  private static final Color GPU_COLOR = Color.web("#a855f7");
  private static final Color RAM_COLOR = Color.web("#49a5ff");
  private static final Color FPS_COLOR = Color.web("#f4f4f4");
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
  private static final String STARTUP_LOGO_RELATIVE_PATH = "docs/assets/images/jvn_logo.png";
  private static final String STARTUP_LOGO_CLASSPATH_RESOURCE = "/com/jvn/editor/images/jvn_logo.png";
  private static final long MIN_STARTUP_SPLASH_MS = 900L;
  private static final long STARTUP_STEP_DELAY_MS = 170L;
  private static final DateTimeFormatter STARTUP_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final Pattern STARTUP_PROCESS_NOISE = Pattern.compile(
      "^(> Task |> Configure |BUILD SUCCESSFUL|Deprecated Gradle|\\d+ actionable|To honour the JVM|Daemon will be stopped|\\s*$)");
  private static final int STARTUP_COMMAND_TAIL_LINES = 14;
  private static final double SIDEBAR_COLLAPSED_EPSILON = 0.01;
  private static final String PANEL_CHOOSER_TAB_ROLE = "panel-chooser";
  private static final String PANEL_CHOOSER_REFRESH_KEY = "panel-chooser-refresh";
  private static final String PANEL_WINDOW_SUPPRESS_UNLOAD_KEY = "jvn.panelWindow.suppressUnload";
  private final EnumMap<EditorSidebarPanel, Stage> panelWindows =
      new EnumMap<>(EditorSidebarPanel.class);
  private Stage editorSettingsWindow;

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
    if (!saveDirtyEditorsBeforeRun()) return;
    Properties mf = loadManifest(root);
    if (mf == null) { status.setText("jvn.project not found"); return; }
    String type = mf.getProperty("type", "gradle").trim();
    if ("gradle".equalsIgnoreCase(type)) {
      String path = mf.getProperty("path", ":runtime").trim();
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
    stopAllPreviewAudio();
    this.projectRoot = dir;
    Properties mf = loadManifest(dir);
    configureProjectContext(dir, mf);
    applyProjectRootToTabs();
    if (welcomeView != null) {
      welcomeView.setCurrentProject(dir);
      welcomeView.markProjectVisited(dir);
    }
    refreshMainCommandUi.run();
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
    if (projView != null) {
      projView.setRootDirectory(root);
      if (tabProject != null && tabProject.getContent() != projView) {
        tabProject.setContent(projView);
      }
    }
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
    if (phoneAssetsToolView != null) phoneAssetsToolView.setProjectRoot(root);
    if (storyboardOverlayView != null) storyboardOverlayView.setProjectRoot(root);
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.setProjectRoot(root);
    if (imageAttributesToolView != null) imageAttributesToolView.setProjectRoot(root);
    if (imageTintToolView != null) imageTintToolView.setProjectRoot(root);
    if (menuFlowEditorView != null) menuFlowEditorView.setProjectRoot(root);
    if (scriptEditorLauncherView != null) {
      scriptEditorLauncherView.setProjectRoot(root);
      scriptEditorLauncherView.setWorkspaceRoot(resolveWorkspaceRoot());
    }
    syncStoryboardOverlayProjectState();
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
      RunConsoleView console = new RunConsoleView(title);
      RunConsoleView.ProcessStarter starter = () -> {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(root);
        pb.redirectErrorStream(true);
        pb.environment().put("GRADLE_USER_HOME", gradleUserHome.getAbsolutePath());
        return pb.start();
      };
      console.setProcessStarter(starter);

      javafx.stage.Stage logStage = new javafx.stage.Stage();
      logStage.setTitle(title);
      javafx.scene.Scene logScene = new javafx.scene.Scene(console, 980, 620);
      EditorTheme.apply(logScene);
      logStage.setScene(logScene);
      logStage.setMinWidth(860);
      logStage.setMinHeight(520);
      applyLinuxDefaultWindowState(logStage);
      logStage.show();
      console.startProcess(starter.start());
    } catch (Exception ex) {
      status.setText("Run failed");
    }
  }

  private void runVnProjectInRuntime(File root, Properties mf) {
    String entryVns = mf.getProperty("entryVns", "").trim();
    String runtimeScript = normalizeRuntimeScriptPath(entryVns);
    File workspaceRoot = resolveWorkspaceRoot();
    if (workspaceRoot == null) {
      status.setText("Cannot locate JVN workspace root");
      return;
    }

    StringBuilder runtimeArgs = new StringBuilder();
    runtimeArgs.append("--assets ").append(quoteCliArg(root.getAbsolutePath()));
    if (runtimeScript != null && !runtimeScript.isBlank()) {
      runtimeArgs.append(" --script ").append(quoteCliArg(runtimeScript));
    }

    String title = mf.getProperty("name", "").trim();
    if (!title.isBlank()) runtimeArgs.append(" --title ").append(quoteCliArg(title));

    String width = mf.getProperty("width", "").trim();
    if (!width.isBlank()) runtimeArgs.append(" --width ").append(width);
    String height = mf.getProperty("height", "").trim();
    if (!height.isBlank()) runtimeArgs.append(" --height ").append(height);

    String runtimeUi = mf.getProperty("runtime.ui", "").trim();
    if (!runtimeUi.isBlank()) runtimeArgs.append(" --ui ").append(quoteCliArg(runtimeUi));
    String runtimeAudio = mf.getProperty("runtime.audio", "").trim();
    if (!runtimeAudio.isBlank()) runtimeArgs.append(" --audio ").append(quoteCliArg(runtimeAudio));
    String runtimeLocale = mf.getProperty("runtime.locale", "").trim();
    if (!runtimeLocale.isBlank()) runtimeArgs.append(" --locale ").append(quoteCliArg(runtimeLocale));
    runtimeArgs.append(" --perf-hud");

    runGradle(workspaceRoot, ":runtime:run", new String[] { "--args=" + runtimeArgs }, "JVN Runtime");
    status.setText("Launching runtime: " + root.getName());
  }

  private String normalizeRuntimeScriptPath(String entryVns) {
    if (entryVns == null || entryVns.isBlank()) return null;
    String script = entryVns.trim().replace('\\', '/');
    if (script.startsWith("./")) script = script.substring(2);
    if (script.startsWith("game/scripts/")) script = script.substring("game/scripts/".length());
    if (script.startsWith("scripts/")) script = script.substring("scripts/".length());
    return script.isBlank() ? null : script;
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
    EditorCrashSupport.installProcessHandler();
    Path logoPath = resolveStartupLogoPath();
    StartupSplashOverlay splash = new StartupSplashOverlay(logoPath);
    splash.show();
    launchStartupSequence(primaryStage, splash, logoPath, false);
  }

  private void launchStartupSequence(Stage primaryStage,
                                     StartupSplashOverlay splash,
                                     Path logoPath,
                                     boolean clearLogs) {
    splash.prepareForChecks(clearLogs);
    splash.setProgress(0.0);
    splash.setStatus("Running startup health checks...");
    splash.appendLog(startupLogLine("INFO", "Bootstrap",
        clearLogs ? "Retrying preflight checks" : "Launching preflight checks"));

    long splashShownNs = System.nanoTime();
    Task<Void> startupTask = createStartupHealthCheckTask(logoPath, splash);
    startupTask.messageProperty().addListener((o, ov, nv) -> {
      if (nv == null || nv.isBlank()) return;
      splash.setStatus(nv);
    });
    startupTask.progressProperty().addListener((o, ov, nv) -> {
      double progress = nv == null ? -1.0 : nv.doubleValue();
      splash.setProgress(progress);
    });
    startupTask.setOnSucceeded(e -> finalizeStartupSuccess(primaryStage, splash, splashShownNs));
    startupTask.setOnFailed(e -> finalizeStartupFailure(primaryStage, splash, logoPath, startupTask.getException()));
    Thread startupThread = new Thread(startupTask, "jvn-editor-startup-checks");
    startupThread.setDaemon(true);
    startupThread.start();
  }

  private void finalizeStartupSuccess(Stage primaryStage,
                                      StartupSplashOverlay splash,
                                      long splashShownNs) {
    long elapsedMs = (System.nanoTime() - splashShownNs) / 1_000_000L;
    long waitMs = Math.max(0L, MIN_STARTUP_SPLASH_MS - elapsedMs);
    PauseTransition delay = new PauseTransition(Duration.millis(waitMs));
    delay.setOnFinished(evt -> {
      try {
        initializeEditorStage(primaryStage);
      } catch (Exception ex) {
        Alert a = new Alert(Alert.AlertType.ERROR, "Startup failed: " + ex.getMessage());
        EditorTheme.apply(a);
        a.setHeaderText(null);
        a.setTitle("JVN Editor");
        a.showAndWait();
      } finally {
        splash.close();
      }
    });
    delay.play();
  }

  private void finalizeStartupFailure(Stage primaryStage,
                                      StartupSplashOverlay splash,
                                      Path logoPath,
                                      Throwable startupFailure) {
    StartupFailure failure = startupFailure instanceof StartupFailure sf
        ? sf
        : new StartupFailure(
            "Startup checks failed",
            startupFailure == null ? "Unknown startup error" : safeMessage(startupFailure),
            startupFailure);
    splash.appendLog(startupLogLine("ERROR", "Preflight", failure.summary()));
    if (failure.detail() != null && !failure.detail().isBlank()) {
      splash.appendLog(startupLogLine("ERROR", "Preflight", failure.detail()));
    }
    splash.showFailure(
        failure.summary(),
        failure.detail(),
        () -> launchStartupSequence(primaryStage, splash, logoPath, true),
        Platform::exit);
  }

  private Task<Void> createStartupHealthCheckTask(Path logoPath, StartupSplashOverlay splash) {
    return new Task<>() {
      @Override
      protected Void call() {
        final int totalChecks = 13;
        int step = 0;
        updateProgress(0, totalChecks);

        File workspace = resolveWorkspaceRoot();
        if (workspace == null || !workspace.isDirectory()) {
          throw new StartupFailure(
              "Workspace root not found",
              "Launch the editor from the JVN repository root so Gradle, native builds, and smoke tests can run.");
        }
        logSplash(splash, "OK", "Workspace", workspace.getAbsolutePath());
        updateMessage("Workspace root resolved");
        advance(++step, totalChecks);

        boolean logoOk = logoPath != null && Files.isRegularFile(logoPath) && Files.isReadable(logoPath);
        logSplash(splash, logoOk ? "OK" : "WARN", "Branding",
            logoOk
                ? "Logo ready: " + logoPath.toAbsolutePath()
                : "Logo not readable: " + (logoPath == null ? "<none>" : logoPath.toAbsolutePath()));
        updateMessage(logoOk ? "Brand assets loaded" : "Brand assets unavailable");
        advance(++step, totalChecks);

        Path stateDir = workspace.toPath().resolve(".jvn");
        ensureWritableStateDir(stateDir);
        logSplash(splash, "OK", "State", "Writable state path: " + stateDir.toAbsolutePath());
        updateMessage("State directory writable");
        advance(++step, totalChecks);

        int requiredJava = readRequiredJavaVersion(workspace.toPath());
        int runtimeJava = parseJavaMajor(System.getProperty("java.version", "unknown"));
        if (requiredJava > 0 && runtimeJava > 0 && runtimeJava < requiredJava) {
          throw new StartupFailure(
              "JDK version mismatch",
              "Running Java " + runtimeJava + " but Gradle toolchain requires Java " + requiredJava + ".");
        }
        if (ToolProvider.getSystemJavaCompiler() == null) {
          throw new StartupFailure(
              "Full JDK not detected",
              "Launch the editor with a JDK installation. `javac` is required for native JNI builds and startup smoke tests.");
        }
        logSplash(splash, "OK", "Runtime",
            "Java " + System.getProperty("java.version", "unknown")
                + " at " + System.getProperty("java.home", "<unknown>"));
        updateMessage("JDK toolchain verified");
        advance(++step, totalChecks);

        String fxVersion = System.getProperty("javafx.version", "unknown");
        logSplash(splash, "OK", "Runtime", "JavaFX " + fxVersion);
        updateMessage("JavaFX runtime verified");
        advance(++step, totalChecks);

        Path gradlew = workspace.toPath().resolve(isWindowsOs() ? "gradlew.bat" : "gradlew");
        Path wrapperProps = workspace.toPath().resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.isRegularFile(gradlew) || !Files.isRegularFile(wrapperProps)) {
          throw new StartupFailure(
              "Gradle wrapper is missing",
              "Ensure `gradlew` and `gradle/wrapper/gradle-wrapper.properties` exist in the workspace root.");
        }
        if (!isWindowsOs() && !Files.isExecutable(gradlew)) {
          throw new StartupFailure(
              "Gradle wrapper is not executable",
              "Run `chmod +x gradlew` and retry startup.");
        }
        logSplash(splash, "OK", "Gradle", "Wrapper ready (Gradle " + readGradleWrapperVersion(wrapperProps) + ")");
        updateMessage("Gradle wrapper located");
        advance(++step, totalChecks);

        updateMessage("Checking Gradle environment");
        runStartupProcess(
            workspace,
            splash,
            "Gradle",
            List.of(resolveGradleCommand(workspace), "--version"),
            "Gradle wrapper check failed",
            "Fix the Gradle wrapper or local JDK configuration, then retry.");
        advance(++step, totalChecks);

        updateMessage("Checking native toolchain");
        runStartupProcess(
            workspace,
            splash,
            "CMake",
            List.of("cmake", "--version"),
            "CMake not available",
            "Install CMake and a native C/C++ toolchain, then retry.");
        advance(++step, totalChecks);

        updateMessage("Building native libraries");
        runGradleStartupProcess(
            workspace,
            splash,
            "Native Build",
            List.of("buildNativeMathIfNeeded", ":audio-fx:buildAudioFxNativeIfNeeded"),
            "Native library build failed",
            "Resolve the native build errors shown above, then retry.");
        advance(++step, totalChecks);

        Path nativeMathLibrary = NativeLibraryLoader.findExisting("jvn_native_bridge");
        if (nativeMathLibrary == null) {
          throw new StartupFailure(
              "Native math bridge not found",
              "Expected `jvn_native_bridge` output was not produced under `native-math/build`.");
        }
        Path audioFxLibrary = NativeLibraryLoader.findExisting("jvn_audiofx_native");
        if (audioFxLibrary == null) {
          throw new StartupFailure(
              "Audio synth native library not found",
              "Expected `jvn_audiofx_native` output was not produced under `audio-fx/build/native`.");
        }
        logSplash(splash, "OK", "Native", "Math bridge: " + nativeMathLibrary);
        logSplash(splash, "OK", "Native", "AudioFX bridge: " + audioFxLibrary);
        updateMessage("Native libraries built");
        advance(++step, totalChecks);

        if (!NativeMathBridge.isAvailable()) {
          throw new StartupFailure(
              "Native math bridge failed to load",
              NativeMathBridge.diagnostics());
        }
        if (!AudioFxNativeBridge.isAvailable()) {
          throw new StartupFailure(
              "Audio synth bridge failed to load",
              AudioFxNativeBridge.diagnostics());
        }
        logSplash(splash, "OK", "Native", NativeMathBridge.diagnostics());
        logSplash(splash, "OK", "Native", AudioFxNativeBridge.diagnostics());
        updateMessage("Native bridges loaded");
        advance(++step, totalChecks);

        awaitStartupLaunchChoice(splash);
        logSplash(splash, "INFO", "Tests", "Full repository test suite not run during splash launch.");
        updateMessage("Launching editor");
        advance(++step, totalChecks);

        File diskRoot = workspace;
        long usableBytes = diskRoot.getUsableSpace();
        double freeGb = usableBytes / (1024.0 * 1024.0 * 1024.0);
        boolean diskOk = usableBytes >= 512L * 1024L * 1024L;
        logSplash(splash, diskOk ? "OK" : "WARN", "Storage",
            String.format(Locale.ROOT, "Free disk %.2f GB at %s", freeGb, diskRoot.getAbsolutePath()));
        updateMessage("Storage check complete");
        advance(++step, totalChecks);

        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        boolean heapOk = maxHeapMb >= 768L;
        logSplash(splash, heapOk ? "OK" : "WARN", "Memory", "Max heap " + maxHeapMb + " MB");
        updateMessage("Memory check complete");
        advance(++step, totalChecks);

        boolean projectOk = projectRoot == null || projectRoot.isDirectory();
        logSplash(splash, projectOk ? "OK" : "WARN", "Project",
            projectRoot == null
                ? "No project selected yet"
                : (projectOk ? projectRoot.getAbsolutePath() : "Missing project path: " + projectRoot.getAbsolutePath()));
        updateMessage("Startup health checks complete");
        advance(++step, totalChecks);

        logSplash(splash, "INFO", "Bootstrap", "Health checks complete");
        return null;
      }

      private void advance(int currentStep, int totalChecks) {
        updateProgress(currentStep, totalChecks);
        if (!isCancelled()) {
          startupSleep(STARTUP_STEP_DELAY_MS);
        }
      }
    };
  }

  private Path resolveStartupLogoPath() {
    File workspaceRoot = resolveWorkspaceRoot();
    if (workspaceRoot != null) {
      Path candidate = workspaceRoot.toPath().resolve(STARTUP_LOGO_RELATIVE_PATH);
      if (Files.isRegularFile(candidate)) return candidate;
    }
    Path cwdCandidate = Path.of(STARTUP_LOGO_RELATIVE_PATH);
    if (Files.isRegularFile(cwdCandidate)) return cwdCandidate;
    return extractClasspathStartupLogo();
  }

  private Path extractClasspathStartupLogo() {
    try (var in = EditorApp.class.getResourceAsStream(STARTUP_LOGO_CLASSPATH_RESOURCE)) {
      if (in == null) return null;
      Path temp = Files.createTempFile("jvn-startup-logo-", ".png");
      temp.toFile().deleteOnExit();
      Files.write(temp, in.readAllBytes());
      return temp;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String startupLogLine(String level, String category, String detail) {
    String time = LocalTime.now().format(STARTUP_TIME_FORMAT);
    String lv = level == null ? "INFO" : level.trim().toUpperCase(Locale.ROOT);
    String cat = category == null ? "Startup" : category.trim();
    String msg = detail == null ? "" : detail.trim();
    return "[" + time + "] [" + lv + "] " + cat + " - " + msg;
  }

  private static void startupSleep(long millis) {
    try {
      Thread.sleep(Math.max(0L, millis));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private static void logSplash(StartupSplashOverlay splash, String level, String category, String detail) {
    if (splash == null) return;
    splash.appendLog(startupLogLine(level, category, detail));
  }

  private void awaitStartupLaunchChoice(StartupSplashOverlay splash) {
    CountDownLatch latch = new CountDownLatch(1);
    logSplash(splash, "INFO", "Launch", "Awaiting user confirmation to launch the editor.");
    splash.showLaunchChoice(latch::countDown);
    try {
      latch.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new StartupFailure(
          "Startup interrupted",
          "Interrupted while waiting for the launch confirmation.",
          ex);
    }
  }

  private static String safeMessage(Throwable throwable) {
    if (throwable == null) return "Unknown startup error";
    String message = throwable.getMessage();
    if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();
    return message.trim();
  }

  private static void ensureWritableStateDir(Path stateDir) {
    try {
      Files.createDirectories(stateDir);
      Path probe = stateDir.resolve(".startup-health-probe");
      Files.writeString(probe, "ok-" + System.nanoTime());
      Files.deleteIfExists(probe);
    } catch (Exception ex) {
      throw new StartupFailure(
          "State directory is not writable",
          "Unable to write under " + stateDir.toAbsolutePath() + ": " + safeMessage(ex),
          ex);
    }
  }

  private int readRequiredJavaVersion(Path root) {
    String raw = readGradleProperty(root, "javaVersion");
    if (raw == null || raw.isBlank()) return -1;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ignore) {
      return -1;
    }
  }

  private String readGradleProperty(Path root, String key) {
    if (root == null || key == null || key.isBlank()) return null;
    Path propsPath = root.resolve("gradle.properties");
    if (!Files.isRegularFile(propsPath)) return null;
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(propsPath)) {
      props.load(in);
      return props.getProperty(key);
    } catch (Exception ignore) {
      return null;
    }
  }

  private String readGradleWrapperVersion(Path wrapperProps) {
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(wrapperProps)) {
      props.load(in);
      String distributionUrl = props.getProperty("distributionUrl", "");
      int gradleIndex = distributionUrl.indexOf("gradle-");
      int zipIndex = distributionUrl.indexOf(".zip");
      if (gradleIndex < 0 || zipIndex <= gradleIndex) return "unknown";
      String version = distributionUrl.substring(gradleIndex + "gradle-".length(), zipIndex);
      if (version.endsWith("-bin")) version = version.substring(0, version.length() - 4);
      if (version.endsWith("-all")) version = version.substring(0, version.length() - 4);
      return version;
    } catch (Exception ignore) {
      return "unknown";
    }
  }

  private int parseJavaMajor(String raw) {
    if (raw == null || raw.isBlank()) return -1;
    String trimmed = raw.trim();
    if (trimmed.startsWith("1.")) {
      trimmed = trimmed.substring(2);
    }
    int dot = trimmed.indexOf('.');
    int dash = trimmed.indexOf('-');
    int end = trimmed.length();
    if (dot >= 0) end = Math.min(end, dot);
    if (dash >= 0) end = Math.min(end, dash);
    try {
      return Integer.parseInt(trimmed.substring(0, end));
    } catch (Exception ex) {
      return -1;
    }
  }

  private static boolean isWindowsOs() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static String resolveGradleCommand(File workspace) {
    File gradlew = new File(workspace, isWindowsOs() ? "gradlew.bat" : "gradlew");
    if (gradlew.isFile()) {
      return gradlew.getAbsolutePath();
    }
    return "gradle";
  }

  private List<String> commonGradleStartupCommand(File workspace) {
    File gradleUserHome = new File(workspace, ".jvn-gradle-user-home");
    if (!gradleUserHome.exists()) {
      gradleUserHome.mkdirs();
    }
    String runtimeJavaHome = System.getProperty("java.home", "").trim();
    List<String> cmd = new ArrayList<>();
    cmd.add(resolveGradleCommand(workspace));
    cmd.add("--no-daemon");
    cmd.add("--console=plain");
    cmd.add("--gradle-user-home");
    cmd.add(gradleUserHome.getAbsolutePath());
    cmd.add("-Dorg.gradle.vfs.watch=false");
    if (!runtimeJavaHome.isEmpty()) {
      cmd.add("-Dorg.gradle.java.home=" + runtimeJavaHome);
      cmd.add("-PjvnNativeJavaHome=" + runtimeJavaHome);
    }
    return cmd;
  }

  private void runGradleStartupProcess(File workspace,
                                       StartupSplashOverlay splash,
                                       String category,
                                       List<String> taskArgs,
                                       String failureSummary,
                                       String failureDetail) {
    List<String> cmd = commonGradleStartupCommand(workspace);
    cmd.addAll(taskArgs);
    runStartupProcess(workspace, splash, category, cmd, failureSummary, failureDetail);
  }

  private void runStartupProcess(File workspace,
                                 StartupSplashOverlay splash,
                                 String category,
                                 List<String> command,
                                 String failureSummary,
                                 String failureDetail) {
    logSplash(splash, "INFO", category, "Running: " + String.join(" ", command));
    Deque<String> tail = new ArrayDeque<>();
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(workspace);
      pb.redirectErrorStream(true);
      if (workspace != null) {
        File gradleUserHome = new File(workspace, ".jvn-gradle-user-home");
        pb.environment().put("GRADLE_USER_HOME", gradleUserHome.getAbsolutePath());
      }
      String runtimeJavaHome = System.getProperty("java.home", "").trim();
      if (!runtimeJavaHome.isEmpty()) {
        pb.environment().put("JAVA_HOME", runtimeJavaHome);
      }
      Process process = pb.start();
      try (BufferedReader reader =
               new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line == null ? "" : line.trim();
          if (trimmed.isBlank()) continue;
          rememberStartupTail(tail, trimmed);
          if (STARTUP_PROCESS_NOISE.matcher(trimmed).matches()) continue;
          logSplash(splash, classifyStartupLine(trimmed), category, trimmed);
        }
      }
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        String detail = failureDetail;
        if (!tail.isEmpty()) {
          detail = (detail == null ? "" : detail + " ")
              + "Recent output: " + String.join(" | ", tail);
        }
        throw new StartupFailure(failureSummary, detail == null ? "Command failed." : detail.trim());
      }
    } catch (StartupFailure ex) {
      throw ex;
    } catch (Exception ex) {
      String detail = (failureDetail == null ? "" : failureDetail + " ")
          + safeMessage(ex);
      throw new StartupFailure(failureSummary, detail.trim(), ex);
    }
  }

  private static void rememberStartupTail(Deque<String> tail, String line) {
    if (line == null || line.isBlank()) return;
    while (tail.size() >= STARTUP_COMMAND_TAIL_LINES) {
      tail.removeFirst();
    }
    tail.addLast(line.trim());
  }

  private static String classifyStartupLine(String line) {
    String normalized = line == null ? "" : line.toLowerCase(Locale.ROOT);
    if (normalized.contains("error") || normalized.contains("failed") || normalized.contains("exception")) {
      return "ERROR";
    }
    if (normalized.contains("warn") || normalized.contains("deprecated")) {
      return "WARN";
    }
    return "INFO";
  }

  private static final class StartupFailure extends RuntimeException {
    private final String summary;
    private final String detail;

    private StartupFailure(String summary, String detail) {
      super((summary == null ? "Startup failure" : summary) + ": " + (detail == null ? "" : detail));
      this.summary = summary == null ? "Startup failure" : summary;
      this.detail = detail == null ? "" : detail;
    }

    private StartupFailure(String summary, String detail, Throwable cause) {
      super((summary == null ? "Startup failure" : summary) + ": " + (detail == null ? "" : detail), cause);
      this.summary = summary == null ? "Startup failure" : summary;
      this.detail = detail == null ? "" : detail;
    }

    private String summary() {
      return summary;
    }

    private String detail() {
      return detail;
    }
  }

  private void initializeEditorStage(Stage primaryStage) {
    primaryStage.setTitle("JVN Editor");
    editorPreferencesStore = new EditorPreferencesStore();
    editorPreferences = editorPreferencesStore.load();
    layoutStudioWindowManager = new LayoutStudioWindowManager(primaryStage, this::doRunProject);
    BorderPane root = new BorderPane();
    String editorVersion = resolveEditorVersion();

    // Menu
    MenuBar mb = new MenuBar();
    mb.setUseSystemMenuBar(false);
    mb.setFocusTraversable(false);
    Menu menuFile = new Menu("File");
    MenuItem miNewProject = new MenuItem("New Project...");
    miNewProject.setOnAction(e -> doNewProject(primaryStage));
    MenuItem miOpenProject = new MenuItem("Open Project...");
    miOpenProject.setOnAction(e -> doOpenProject(primaryStage));
    MenuItem miOpen = new MenuItem("Open JES...");
    miOpen.setOnAction(e -> doOpen(primaryStage));
    MenuItem miOpenVns = new MenuItem("Open VNS...");
    miOpenVns.setOnAction(e -> doOpenVns(primaryStage));
    MenuItem miOpenTextFile = new MenuItem("Open Text File...");
    miOpenTextFile.setOnAction(e -> doOpenTextFile(primaryStage));
    MenuItem miCloseTab = new MenuItem("Close Tab");
    miCloseTab.setOnAction(e -> closeActiveTab());
    MenuItem miSave = new MenuItem("Save");
    miSave.setOnAction(e -> doSave(primaryStage));
    MenuItem miSaveAs = new MenuItem("Save As...");
    miSaveAs.setOnAction(e -> doSaveAs(primaryStage));
    MenuItem miSaveAllTabs = new MenuItem("Save All Open Tabs");
    miSaveAllTabs.setOnAction(e -> saveAllOpenTabs());
    miOpenProject.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
    miOpen.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    miOpenVns.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
    miOpenTextFile.setAccelerator(new KeyCodeCombination(
        KeyCode.O,
        KeyCombination.SHORTCUT_DOWN,
        KeyCombination.SHIFT_DOWN,
        KeyCombination.ALT_DOWN));
    miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
    miSaveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    miSaveAllTabs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
    miCloseTab.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
    MenuItem miCloseAllTabs = new MenuItem("Close All Tabs");
    miCloseAllTabs.setOnAction(e -> closeAllClosableTabs());
    miCloseAllTabs.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miRevealActiveFile = new MenuItem("Reveal Active File in File Manager");
    miRevealActiveFile.setOnAction(e -> revealActiveFileInFileManager());
    MenuItem miCopyActiveFilePath = new MenuItem("Copy Active File Path");
    miCopyActiveFilePath.setOnAction(e -> copyActiveFilePathToClipboard());
    MenuItem miFileWelcome = new MenuItem("Welcome Center");
    miFileWelcome.setOnAction(e -> selectWelcomeTab());
    MenuItem miFileProjectExplorer = new MenuItem("Project Explorer");
    miFileProjectExplorer.setOnAction(e -> selectProjectTab());
    MenuItem miFileRunProject = new MenuItem("Run Project");
    miFileRunProject.setOnAction(e -> doRunProject(primaryStage));
    MenuItem miFileRevealProjectRoot = new MenuItem("Reveal Project Root in File Manager");
    miFileRevealProjectRoot.setOnAction(e -> revealProjectRootInFileManager());
    MenuItem miFileCopyProjectRoot = new MenuItem("Copy Project Root Path");
    miFileCopyProjectRoot.setOnAction(e -> copyProjectRootPathToClipboard());
    MenuItem miFileProjectDocs = new MenuItem("Open Project Docs Folder");
    miFileProjectDocs.setOnAction(e -> openProjectDocsFolder());
    MenuItem miFileWorkspaceDocs = new MenuItem("Open Workspace Docs Folder");
    miFileWorkspaceDocs.setOnAction(e -> openWorkspaceDocsFolder());
    MenuItem miExitEditor = new MenuItem("Exit JVN Editor");
    miExitEditor.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
    miExitEditor.setOnAction(e ->
        primaryStage.fireEvent(new javafx.stage.WindowEvent(primaryStage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
    Menu menuFileOpen = new Menu("Open");
    menuFileOpen.getItems().addAll(
        miNewProject,
        miOpenProject,
        new SeparatorMenuItem(),
        miOpen,
        miOpenVns,
        miOpenTextFile);
    Menu menuFileSave = new Menu("Save");
    menuFileSave.getItems().addAll(miSave, miSaveAs, miSaveAllTabs);
    Menu menuFileCurrent = new Menu("Current File");
    menuFileCurrent.getItems().addAll(miRevealActiveFile, miCopyActiveFilePath);
    Menu menuFileClose = new Menu("Close");
    menuFileClose.getItems().addAll(miCloseTab, miCloseAllTabs);
    Menu menuFileProject = new Menu("Project");
    menuFileProject.getItems().addAll(
        miFileWelcome,
        miFileProjectExplorer,
        new SeparatorMenuItem(),
        miFileRunProject,
        new SeparatorMenuItem(),
        miFileRevealProjectRoot,
        miFileCopyProjectRoot);
    Menu menuFileDocs = new Menu("Docs & Folders");
    menuFileDocs.getItems().addAll(miFileProjectDocs, miFileWorkspaceDocs);
    menuFile.getItems().addAll(
        menuFileOpen,
        new SeparatorMenuItem(),
        menuFileSave,
        new SeparatorMenuItem(),
        menuFileCurrent,
        menuFileProject,
        menuFileDocs,
        new SeparatorMenuItem(),
        menuFileClose,
        new SeparatorMenuItem(),
        miExitEditor);

    // ── Edit ──
    Menu menuEdit = new Menu("Edit");
    MenuItem miUndo = new MenuItem("Undo");
    miUndo.setOnAction(e -> executeUndo());
    miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRedo = new MenuItem("Redo");
    miRedo.setOnAction(e -> executeRedo());
    miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miFind = new MenuItem("Find / Replace");
    miFind.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
    miFind.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null) ft.showSearchBar();
    });
    MenuItem miGoToLine = new MenuItem("Go to VNS Line...");
    miGoToLine.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN));
    miGoToLine.setOnAction(e -> {
      javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog();
      dlg.setTitle("Go to Line");
      dlg.setHeaderText(null);
      dlg.setContentText("Line number:");
      dlg.showAndWait().ifPresent(text -> {
        try {
          int line = Integer.parseInt(text.trim());
          FileEditorTab ft = getActiveFileTab();
          if (ft != null) ft.navigateToLine(line);
        } catch (NumberFormatException ignored) {}
      });
    });
    MenuItem miReload = new MenuItem("Reload from Disk");
    miReload.setOnAction(e -> doReload());
    MenuItem miEditorSettings = new MenuItem("Editor Settings");
    miEditorSettings.setOnAction(e -> selectEditorSettingsTab());
    miEditorSettings.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
    MenuItem miEditApplyPreview = new MenuItem("Apply to Preview");
    miEditApplyPreview.setOnAction(e -> applyCodeFromEditor());
    MenuItem miEditResetCamera = new MenuItem("Reset Camera");
    miEditResetCamera.setOnAction(e -> resetCamera());
    MenuItem miEditFitContent = new MenuItem("Fit Content / Open Fullscreen Preview");
    miEditFitContent.setOnAction(e -> fitCameraToContent());
    MenuItem miEditRevealProjectRoot = new MenuItem("Reveal Project Root in File Manager");
    miEditRevealProjectRoot.setOnAction(e -> revealProjectRootInFileManager());
    MenuItem miEditCopyProjectRootPath = new MenuItem("Copy Project Root Path");
    miEditCopyProjectRootPath.setOnAction(e -> copyProjectRootPathToClipboard());
    Menu menuEditSearch = new Menu("Search");
    menuEditSearch.getItems().addAll(miFind, miGoToLine);
    Menu menuEditDocument = new Menu("Document");
    menuEditDocument.getItems().addAll(miReload, miEditorSettings);
    Menu menuEditPaths = new Menu("Paths & Reveal");
    menuEditPaths.getItems().addAll(
        miRevealActiveFile,
        miCopyActiveFilePath,
        new SeparatorMenuItem(),
        miEditRevealProjectRoot,
        miEditCopyProjectRootPath);
    Menu menuEditPreview = new Menu("Preview");
    menuEditPreview.getItems().addAll(miEditApplyPreview, miEditResetCamera, miEditFitContent);
    menuEdit.getItems().addAll(
        miUndo,
        miRedo,
        new SeparatorMenuItem(),
        menuEditSearch,
        menuEditPreview,
        menuEditPaths,
        new SeparatorMenuItem(),
        menuEditDocument);

    // ── View ──
    Menu menuView = new Menu("View");
    MenuItem miToggleEditorFullscreen = new MenuItem("Toggle Editor Fullscreen");
    miToggleEditorFullscreen.setOnAction(e -> toggleActiveEditorFullscreen());
    miToggleEditorFullscreen.setAccelerator(new KeyCodeCombination(KeyCode.F11));
    MenuItem miResetCamera = new MenuItem("Reset Camera");
    miResetCamera.setOnAction(e -> resetCamera());
    MenuItem miFitContent = new MenuItem("Fit Content / Open Fullscreen Preview");
    miFitContent.setOnAction(e -> fitCameraToContent());

    Menu menuPanels = new Menu("Panels");
    MenuItem miShowProject = new MenuItem("Project Explorer");
    miShowProject.setOnAction(e -> selectProjectTab());
    MenuItem miShowWelcomePanel = new MenuItem("Welcome Center");
    miShowWelcomePanel.setOnAction(e -> selectWelcomeTab());
    MenuItem miShowTimeline = new MenuItem("Story Timeline");
    miShowTimeline.setOnAction(e -> selectTimelineTab());
    MenuItem miShowInspector = new MenuItem("Inspector");
    miShowInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miShowHelpPanel = new MenuItem("Help Center");
    miShowHelpPanel.setOnAction(e -> selectHelpTab());
    MenuItem miShowVersionControlPanel = new MenuItem("Version Control");
    miShowVersionControlPanel.setOnAction(e -> selectVersionControlTab());
    MenuItem miShowAssets = new MenuItem("Asset Browser");
    miShowAssets.setOnAction(e -> selectAssetBrowserTab());
    MenuItem miShowScriptEditorWorkspace = new MenuItem("Text Editor Workspace");
    miShowScriptEditorWorkspace.setOnAction(e -> selectScriptEditorLauncherTab());
    MenuItem miShowDiagnostics = new MenuItem("VNS Diagnostics");
    miShowDiagnostics.setOnAction(e -> selectVnsDiagnosticsTab());
    MenuItem miShowFlowMap = new MenuItem("Label Flow Map");
    miShowFlowMap.setOnAction(e -> selectVnsFlowMapTab());
    MenuItem miShowMenuFlow = new MenuItem("Menu Flow Editor");
    miShowMenuFlow.setOnAction(e -> selectMenuFlowTab());
    MenuItem miShowLayoutLauncher = new MenuItem("Layout Launcher");
    miShowLayoutLauncher.setOnAction(e -> selectLayoutLauncherTab());
    MenuItem miShowPhoneAssets = new MenuItem("Phone Assets");
    miShowPhoneAssets.setOnAction(e -> selectPhoneAssetsToolTab());
    MenuItem miShowStoryboardOverlay = new MenuItem("Storyboard Overlay");
    miShowStoryboardOverlay.setOnAction(e -> selectStoryboardOverlayTab());
    MenuItem miShowLayeredVisualizer = new MenuItem("Layered Image Visualizer");
    miShowLayeredVisualizer.setOnAction(e -> selectLayeredImageVisualizerTab());
    MenuItem miShowImageAttributes = new MenuItem("Image Attributes Tool");
    miShowImageAttributes.setOnAction(e -> selectImageAttributesToolTab());
    MenuItem miShowImageTint = new MenuItem("Scene Lighting Studio");
    miShowImageTint.setOnAction(e -> selectImageTintToolTab());
    MenuItem miShowPuppeteerLauncher = new MenuItem("Puppeteer Launcher");
    miShowPuppeteerLauncher.setOnAction(e -> selectPuppeteerLauncherTab());
    MenuItem miShowEditorSettings = new MenuItem("Editor Settings");
    miShowEditorSettings.setOnAction(e -> selectEditorSettingsTab());
    MenuItem miAddLeftPanel = new MenuItem("Add Left Panel...");
    miAddLeftPanel.setOnAction(e -> showLeftAddMenu());
    MenuItem miAddRightPanel = new MenuItem("Add Right Panel...");
    miAddRightPanel.setOnAction(e -> showRightAddMenu());
    Menu menuPanelsWorkspace = new Menu("Workspace");
    menuPanelsWorkspace.getItems().addAll(
        miShowWelcomePanel, miShowProject, miShowTimeline, miShowInspector, miShowVersionControlPanel, miShowHelpPanel);
    Menu menuPanelsAuthoring = new Menu("Authoring");
    menuPanelsAuthoring.getItems().addAll(
        miShowAssets, miShowScriptEditorWorkspace, miShowPuppeteerLauncher);
    Menu menuPanelsAnalysis = new Menu("Analysis & Flow");
    menuPanelsAnalysis.getItems().addAll(miShowDiagnostics, miShowFlowMap, miShowMenuFlow, miShowLayoutLauncher);
    Menu menuPanelsVisual = new Menu("Visual Tools");
    menuPanelsVisual.getItems().addAll(
        miShowPhoneAssets, miShowStoryboardOverlay, miShowLayeredVisualizer, miShowImageAttributes, miShowImageTint);
    Menu menuPanelsSettings = new Menu("Settings");
    menuPanelsSettings.getItems().addAll(miShowEditorSettings);
    menuPanels.getItems().addAll(
        menuPanelsWorkspace,
        menuPanelsAuthoring,
        menuPanelsAnalysis,
        menuPanelsVisual,
        menuPanelsSettings,
        new SeparatorMenuItem(),
        miAddLeftPanel,
        miAddRightPanel);

    Menu menuViewport = new Menu("Viewport");
    menuViewport.getItems().addAll(miToggleEditorFullscreen, miResetCamera, miFitContent);
    menuView.getItems().addAll(menuViewport, new SeparatorMenuItem(), menuPanels);

    // ── Navigate ──
    Menu menuNavigate = new Menu("Navigate");
    Menu menuNavigateCore = new Menu("Core Panels");
    MenuItem miNavigateWelcome = new MenuItem("Welcome Center");
    miNavigateWelcome.setOnAction(e -> selectWelcomeTab());
    MenuItem miNavigateProject = new MenuItem("Project Explorer");
    miNavigateProject.setOnAction(e -> selectProjectTab());
    MenuItem miNavigateTimeline = new MenuItem("Story Timeline");
    miNavigateTimeline.setOnAction(e -> selectTimelineTab());
    MenuItem miNavigateInspector = new MenuItem("Inspector");
    miNavigateInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miNavigateHelp = new MenuItem("Help Center");
    miNavigateHelp.setOnAction(e -> selectHelpTab());
    MenuItem miNavigateVersionControl = new MenuItem("Version Control");
    miNavigateVersionControl.setOnAction(e -> selectVersionControlTab());
    menuNavigateCore.getItems().addAll(
        miNavigateWelcome, miNavigateProject, miNavigateTimeline, miNavigateInspector, miNavigateHelp, miNavigateVersionControl);

    Menu menuNavigateEditors = new Menu("Editors & Tools");
    MenuItem miNavigateAssetBrowser = new MenuItem("Asset Browser");
    miNavigateAssetBrowser.setOnAction(e -> selectAssetBrowserTab());
    MenuItem miNavigateScriptWorkspace = new MenuItem("Text Editor Workspace");
    miNavigateScriptWorkspace.setOnAction(e -> selectScriptEditorLauncherTab());
    MenuItem miNavigatePuppeteer = new MenuItem("Puppeteer Launcher");
    miNavigatePuppeteer.setOnAction(e -> selectPuppeteerLauncherTab());
    MenuItem miNavigateMenuFlow = new MenuItem("Menu Flow Editor");
    miNavigateMenuFlow.setOnAction(e -> selectMenuFlowTab());
    MenuItem miNavigateLayoutLauncher = new MenuItem("Layout Launcher");
    miNavigateLayoutLauncher.setOnAction(e -> selectLayoutLauncherTab());
    menuNavigateEditors.getItems().addAll(
        miNavigateAssetBrowser, miNavigateScriptWorkspace, miNavigatePuppeteer, miNavigateMenuFlow, miNavigateLayoutLauncher);

    Menu menuNavigateVisual = new Menu("Visual Tools");
    MenuItem miNavigatePhoneAssets = new MenuItem("Phone Assets");
    miNavigatePhoneAssets.setOnAction(e -> selectPhoneAssetsToolTab());
    MenuItem miNavigateStoryboard = new MenuItem("Storyboard Overlay");
    miNavigateStoryboard.setOnAction(e -> selectStoryboardOverlayTab());
    MenuItem miNavigateLayered = new MenuItem("Layered Image Visualizer");
    miNavigateLayered.setOnAction(e -> selectLayeredImageVisualizerTab());
    MenuItem miNavigateImageAttributes = new MenuItem("Image Attributes Tool");
    miNavigateImageAttributes.setOnAction(e -> selectImageAttributesToolTab());
    MenuItem miNavigateImageTint = new MenuItem("Scene Lighting Studio");
    miNavigateImageTint.setOnAction(e -> selectImageTintToolTab());
    menuNavigateVisual.getItems().addAll(
        miNavigatePhoneAssets, miNavigateStoryboard, miNavigateLayered, miNavigateImageAttributes, miNavigateImageTint);

    Menu menuNavigateAnalysis = new Menu("Analysis");
    MenuItem miNavigateDiagnostics = new MenuItem("VNS Diagnostics");
    miNavigateDiagnostics.setOnAction(e -> selectVnsDiagnosticsTab());
    MenuItem miNavigateFlowMap = new MenuItem("Label Flow Map");
    miNavigateFlowMap.setOnAction(e -> selectVnsFlowMapTab());
    MenuItem miNavigateSettings = new MenuItem("Editor Settings");
    miNavigateSettings.setOnAction(e -> selectEditorSettingsTab());
    menuNavigateAnalysis.getItems().addAll(miNavigateDiagnostics, miNavigateFlowMap, miNavigateSettings);
    menuNavigate.getItems().addAll(menuNavigateCore, menuNavigateEditors, menuNavigateVisual, menuNavigateAnalysis);

    // ── Run ──
    Menu menuRun = new Menu("Run");
    MenuItem miApplyCode = new MenuItem("Apply to Preview");
    miApplyCode.setOnAction(e -> applyCodeFromEditor());
    miApplyCode.setAccelerator(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRunProject = new MenuItem("Run Project");
    miRunProject.setOnAction(e -> doRunProject(primaryStage));
    MenuItem miLaunchHere = new MenuItem("Launch VNS from Here");
    miLaunchHere.setAccelerator(new KeyCodeCombination(KeyCode.F5));
    miLaunchHere.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.launchFromHere();
      } else {
        status.setText("Launch from Here is only available for VNS files");
      }
    });
    MenuItem miLaunchStart = new MenuItem("Launch VNS from Start");
    miLaunchStart.setAccelerator(new KeyCodeCombination(KeyCode.F5, KeyCombination.SHIFT_DOWN));
    miLaunchStart.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.runFromLabel(null);
      } else {
        status.setText("Launch from Start is only available for VNS files");
      }
    });
    MenuItem miApplyAndLaunchHere = new MenuItem("Apply Preview and Launch Here");
    miApplyAndLaunchHere.setOnAction(e -> {
      applyCodeFromEditor();
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.launchFromHere();
      } else {
        status.setText("Apply + Launch Here is only available for VNS files");
      }
    });
    MenuItem miApplyAndLaunchStart = new MenuItem("Apply Preview and Launch From Start");
    miApplyAndLaunchStart.setOnAction(e -> {
      applyCodeFromEditor();
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.runFromLabel(null);
      } else {
        status.setText("Apply + Launch Start is only available for VNS files");
      }
    });
    Menu menuRunPreview = new Menu("Preview");
    menuRunPreview.getItems().addAll(miApplyCode, miApplyAndLaunchHere, miApplyAndLaunchStart);
    Menu menuRunLaunchVns = new Menu("Launch Active VNS");
    menuRunLaunchVns.getItems().addAll(miLaunchHere, miLaunchStart);
    menuRun.getItems().addAll(
        menuRunPreview,
        new SeparatorMenuItem(),
        miRunProject,
        new SeparatorMenuItem(),
        menuRunLaunchVns);

    // ── Tools ──
    Menu menuTools = new Menu("Tools");
    MenuItem miActionEditor = new MenuItem("Puppeteer (Window)");
    miActionEditor.setOnAction(e -> openActionEditor());
    miActionEditor.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miPuppeteerPanel = new MenuItem("Puppeteer Launcher");
    miPuppeteerPanel.setOnAction(e -> selectPuppeteerLauncherTab());
    MenuItem miScriptEditorWorkspace = new MenuItem("Text Editor Workspace");
    miScriptEditorWorkspace.setOnAction(e -> selectScriptEditorLauncherTab());

    MenuItem miMenuFlow = new MenuItem("Menu Flow Editor");
    miMenuFlow.setOnAction(e -> selectMenuFlowTab());
    MenuItem miLayoutLauncher = new MenuItem("Layout Launcher");
    miLayoutLauncher.setOnAction(e -> selectLayoutLauncherTab());
    MenuItem miPhoneAssets = new MenuItem("Phone Assets");
    miPhoneAssets.setOnAction(e -> selectPhoneAssetsToolTab());
    MenuItem miStoryboardOverlay = new MenuItem("Storyboard Overlay");
    miStoryboardOverlay.setOnAction(e -> selectStoryboardOverlayTab());
    MenuItem miLayeredVisualizer = new MenuItem("Layered Image Visualizer");
    miLayeredVisualizer.setOnAction(e -> selectLayeredImageVisualizerTab());
    MenuItem miImageAttributes = new MenuItem("Image Attributes Tool");
    miImageAttributes.setOnAction(e -> selectImageAttributesToolTab());
    MenuItem miImageTint = new MenuItem("Scene Lighting Studio");
    miImageTint.setOnAction(e -> selectImageTintToolTab());

    Menu menuVnsTools = new Menu("VNS Analysis");
    MenuItem miToolDiagnostics = new MenuItem("VNS Diagnostics");
    miToolDiagnostics.setOnAction(e -> selectVnsDiagnosticsTab());
    MenuItem miToolFlowMap = new MenuItem("Label Flow Map");
    miToolFlowMap.setOnAction(e -> selectVnsFlowMapTab());
    menuVnsTools.getItems().addAll(miToolDiagnostics, miToolFlowMap);

    MenuItem miToolAssets = new MenuItem("Asset Browser");
    miToolAssets.setOnAction(e -> selectAssetBrowserTab());
    MenuItem miToolInspector = new MenuItem("Inspector");
    miToolInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miToolEditorSettings = new MenuItem("Editor Settings");
    miToolEditorSettings.setOnAction(e -> selectEditorSettingsTab());
    MenuItem miToolVersionControl = new MenuItem("Version Control");
    miToolVersionControl.setOnAction(e -> selectVersionControlTab());

    Menu menuAnimationTools = new Menu("Animation");
    menuAnimationTools.getItems().addAll(miActionEditor, miPuppeteerPanel);
    Menu menuScriptTools = new Menu("Scripts & Analysis");
    menuScriptTools.getItems().addAll(miScriptEditorWorkspace, menuVnsTools, miMenuFlow);
    Menu menuLayoutTools = new Menu("Layout & UI");
    menuLayoutTools.getItems().addAll(miLayoutLauncher, miPhoneAssets, miStoryboardOverlay);
    Menu menuImageTools = new Menu("Image & Assets");
    menuImageTools.getItems().addAll(miToolAssets, miLayeredVisualizer, miImageAttributes, miImageTint);
    Menu menuWorkspaceTools = new Menu("Workspace");
    menuWorkspaceTools.getItems().addAll(miToolInspector, miToolVersionControl, miToolEditorSettings);
    menuTools.getItems().addAll(menuAnimationTools, menuScriptTools, menuLayoutTools, menuImageTools, menuWorkspaceTools);

    // ── Version Control ──
    Menu menuVcs = new Menu("Version Control");
    MenuItem miOpenVcs = new MenuItem("Open Version Control");
    miOpenVcs.setOnAction(e -> selectVersionControlTab());
    miOpenVcs.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miRefreshVcs = new MenuItem("Refresh Status");
    miRefreshVcs.setOnAction(e -> {
      if (versionControlView != null) versionControlView.refreshStatus();
      selectVersionControlTab();
    });
    MenuItem miRevealProjectRoot = new MenuItem("Reveal Project Root in File Manager");
    miRevealProjectRoot.setOnAction(e -> revealProjectRootInFileManager());
    MenuItem miCopyProjectRootPath = new MenuItem("Copy Project Root Path");
    miCopyProjectRootPath.setOnAction(e -> copyProjectRootPathToClipboard());
    menuVcs.getItems().addAll(miOpenVcs, miRefreshVcs, new SeparatorMenuItem(), miRevealProjectRoot, miCopyProjectRootPath);

    // ── Window ──
    Menu menuWindow = new Menu("Window");
    MenuItem miBringMainWindowToFront = new MenuItem("Bring Main Window to Front");
    miBringMainWindowToFront.setOnAction(e -> {
      primaryStage.toFront();
      primaryStage.requestFocus();
    });
    MenuItem miCloseFloatingWindows = new MenuItem("Close All Floating Panel Windows");
    miCloseFloatingWindows.setOnAction(e -> closeAllFloatingPanelWindows());

    Menu menuWindowWorkspace = new Menu("Workspace Tabs");
    MenuItem miWindowWelcome = new MenuItem("Welcome Center");
    miWindowWelcome.setOnAction(e -> selectWelcomeTab());
    MenuItem miWindowProject = new MenuItem("Project Explorer");
    miWindowProject.setOnAction(e -> selectProjectTab());
    MenuItem miWindowTimeline = new MenuItem("Story Timeline");
    miWindowTimeline.setOnAction(e -> selectTimelineTab());
    MenuItem miWindowInspector = new MenuItem("Inspector");
    miWindowInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miWindowVersionControl = new MenuItem("Version Control");
    miWindowVersionControl.setOnAction(e -> selectVersionControlTab());
    MenuItem miWindowHelp = new MenuItem("Help Center");
    miWindowHelp.setOnAction(e -> selectHelpTab());
    MenuItem miWindowSettings = new MenuItem("Editor Settings");
    miWindowSettings.setOnAction(e -> selectEditorSettingsTab());
    menuWindowWorkspace.getItems().addAll(
        miWindowWelcome,
        miWindowProject,
        miWindowTimeline,
        miWindowInspector,
        miWindowVersionControl,
        miWindowHelp,
        miWindowSettings);

    Menu menuWindowTools = new Menu("Open Tool Window");
    MenuItem miWindowTimelineTool = new MenuItem("Timeline");
    miWindowTimelineTool.setOnAction(e ->
        launchPanelAsWindow("Story Timeline", ensureTimelineView(), 600, 700, EditorSidebarPanel.TIMELINE));
    MenuItem miWindowDiagnostics = new MenuItem("VNS Diagnostics");
    miWindowDiagnostics.setOnAction(e ->
        launchPanelAsWindow("VNS Diagnostics", ensureVnsDiagnosticsView(), 700, 600, EditorSidebarPanel.VNS_DIAGNOSTICS));
    MenuItem miWindowFlowMap = new MenuItem("Label Flow Map");
    miWindowFlowMap.setOnAction(e ->
        launchPanelAsWindow("Label Flow Map", ensureVnsFlowMapView(), 700, 600, EditorSidebarPanel.LABEL_FLOW));
    MenuItem miWindowAssets = new MenuItem("Asset Browser");
    miWindowAssets.setOnAction(e ->
        launchPanelAsWindow("Asset Browser", ensureAssetBrowserView(), 700, 600, EditorSidebarPanel.ASSETS));
    MenuItem miWindowVersionControlTool = new MenuItem("Version Control");
    miWindowVersionControlTool.setOnAction(e ->
        launchPanelAsWindow("Version Control", ensureVersionControlView(), 700, 600, EditorSidebarPanel.VERSION_CONTROL));
    MenuItem miWindowLayoutLauncher = new MenuItem("Layout Launcher");
    miWindowLayoutLauncher.setOnAction(e -> {
      LayoutEditorLauncherView view = ensureLayoutEditorLauncherView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Layout Launcher", view, 700, 700, EditorSidebarPanel.LAYOUT_LAUNCHER);
    });
    MenuItem miWindowPhoneAssets = new MenuItem("Phone Assets");
    miWindowPhoneAssets.setOnAction(e ->
        launchPanelAsWindow("Phone Assets", ensurePhoneAssetsToolView(), 920, 760, EditorSidebarPanel.PHONE_ASSETS));
    MenuItem miWindowStoryboard = new MenuItem("Storyboard Overlay");
    miWindowStoryboard.setOnAction(e -> {
      StoryboardOverlayView view = ensureStoryboardOverlayView();
      refreshStoryboardOverlayContext(getActiveFileTab());
      launchPanelAsWindow("Storyboard Overlay", view, 420, 720, EditorSidebarPanel.STORYBOARD_OVERLAY);
    });
    MenuItem miWindowLayered = new MenuItem("Layered Image Visualizer");
    miWindowLayered.setOnAction(e -> {
      LayeredImageVisualizerView view = ensureLayeredImageVisualizerView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Layered Image Visualizer", view, 900, 700, EditorSidebarPanel.LAYERED_IMAGES);
    });
    MenuItem miWindowImageAttributes = new MenuItem("Image Attributes Tool");
    miWindowImageAttributes.setOnAction(e -> {
      ImageAttributesToolView view = ensureImageAttributesToolView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Image Attributes Tool", view, 800, 650, EditorSidebarPanel.IMAGE_ATTRIBUTES);
    });
    MenuItem miWindowImageTint = new MenuItem("Scene Lighting Studio");
    miWindowImageTint.setOnAction(e -> {
      ImageTintToolView view = ensureImageTintToolView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Scene Lighting Studio", view, 800, 650, EditorSidebarPanel.IMAGE_TINT);
    });
    MenuItem miWindowMenuFlow = new MenuItem("Menu Flow Editor");
    miWindowMenuFlow.setOnAction(e -> {
      MenuFlowEditorView view = ensureMenuFlowEditorView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Menu Flow Editor", view, 900, 650, EditorSidebarPanel.MENU_FLOW);
    });
    MenuItem miWindowPuppeteer = new MenuItem("Puppeteer Launcher");
    miWindowPuppeteer.setOnAction(e ->
        launchPanelAsWindow("Puppeteer Launcher", ensurePuppeteerLauncherPanel(), 600, 500, EditorSidebarPanel.PUPPETEER_LAUNCHER));
    MenuItem miWindowTextEditor = new MenuItem("Text Editor");
    miWindowTextEditor.setOnAction(e -> {
      ScriptEditorLauncherView launcher = ensureScriptEditorLauncherView();
      launcher.setProjectRoot(projectRoot);
      launcher.setWorkspaceRoot(resolveWorkspaceRoot());
      launcher.launchEditorWindow();
    });
    MenuItem miWindowEditorSettings = new MenuItem("Editor Settings");
    miWindowEditorSettings.setOnAction(e ->
        launchPanelAsWindow("Editor Settings", ensureEditorSettingsView(), 520, 760, null));
    menuWindowTools.getItems().addAll(
        miWindowTimelineTool,
        miWindowDiagnostics,
        miWindowFlowMap,
        miWindowAssets,
        miWindowVersionControlTool,
        new SeparatorMenuItem(),
        miWindowLayoutLauncher,
        miWindowPhoneAssets,
        miWindowStoryboard,
        miWindowLayered,
        miWindowImageAttributes,
        miWindowImageTint,
        miWindowMenuFlow,
        miWindowPuppeteer,
        miWindowTextEditor,
        miWindowEditorSettings);
    menuWindow.getItems().addAll(
        miBringMainWindowToFront,
        new SeparatorMenuItem(),
        menuWindowWorkspace,
        menuWindowTools,
        new SeparatorMenuItem(),
        miCloseFloatingWindows);

    // ── Help ──
    Menu menuHelp = new Menu("Help");
    MenuItem miWelcome = new MenuItem("Welcome Center");
    miWelcome.setOnAction(e -> selectWelcomeTab());
    miWelcome.setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miHelpCenter = new MenuItem("Help Center");
    miHelpCenter.setOnAction(e -> selectHelpTab());
    miHelpCenter.setAccelerator(new KeyCodeCombination(KeyCode.F1));
    MenuItem miRefreshHelp = new MenuItem("Refresh Docs Index");
    miRefreshHelp.setOnAction(e -> {
      if (helpCenterView != null) helpCenterView.refresh();
    });
    MenuItem miOpenProjectDocs = new MenuItem("Open Project Docs Folder");
    miOpenProjectDocs.setOnAction(e -> openProjectDocsFolder());
    MenuItem miOpenWorkspaceDocs = new MenuItem("Open Workspace Docs Folder");
    miOpenWorkspaceDocs.setOnAction(e -> openWorkspaceDocsFolder());
    MenuItem miAbout = new MenuItem("About JVN Editor");
    miAbout.setOnAction(e -> {
      javafx.scene.control.Alert about = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
      about.setTitle("About JVN Editor");
      about.setHeaderText("JVN Editor " + editorVersion);
      about.setContentText("Java Vector Nexus — Visual Novel & 2D Game Toolkit");
      about.showAndWait();
    });
    menuHelp.getItems().addAll(
        miWelcome, miHelpCenter, miRefreshHelp,
        new SeparatorMenuItem(),
        miOpenProjectDocs, miOpenWorkspaceDocs,
        new SeparatorMenuItem(),
        miAbout);

    mb.getMenus().addAll(menuFile, menuEdit, menuView, menuNavigate, menuRun, menuTools, menuVcs, menuWindow, menuHelp);

    Label toolbarCommandSummary = new Label();
    toolbarCommandSummary.getStyleClass().add("main-editor-command-summary");
    toolbarCommandSummary.setWrapText(false);
    toolbarCommandSummary.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
    toolbarCommandSummary.setMaxWidth(Double.MAX_VALUE);

    Region menuSpacer = new Region();
    HBox.setHgrow(menuSpacer, Priority.ALWAYS);
    HBox commandBar = new HBox(10, mb, menuSpacer, toolbarCommandSummary);
    commandBar.getStyleClass().add("main-editor-command-bar");
    commandBar.setAlignment(Pos.CENTER_LEFT);

    // Toolbar
    osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    BorderPane toolbar = new BorderPane();
    toolbar.getStyleClass().add("master-toolbar");
    status = new Label("Ready");
    fps = new Label("");
    cpuText = new Text("CPU --");
    cpuText.setFill(CPU_COLOR);
    gpuText = new Text(" | HEAP --");
    gpuText.setFill(GPU_COLOR);
    ramText = new Text(" | RAM --");
    ramText.setFill(RAM_COLOR);
    fpsText = new Text(" | FPS --");
    fpsText.setFill(Color.WHITE);
    perf = new TextFlow(cpuText, gpuText, ramText, fpsText);
    perf.setLineSpacing(2);
    perfGraph = new PerfGraph();
    Runnable refreshChrome = () -> {
      FileEditorTab ft = getActiveFileTab();
      Tab activeTab = filesTabs != null ? filesTabs.getSelectionModel().getSelectedItem() : null;
      boolean hasFile = ft != null;
      boolean hasProject = projectRoot != null && projectRoot.isDirectory();
      boolean canFullscreen = canToggleActiveEditorLayout() || editorFullscreen || layeredVisualizerFullscreen;
      int dirtyTabs = countDirtyFileTabs();
      int closableTabs = countClosableTabs();

      miSave.setText(hasFile ? "Save " + ft.getDisplayName() : "Save");
      miSaveAs.setText(hasFile ? "Save " + ft.getDisplayName() + " As..." : "Save As...");
      miSave.setDisable(!hasFile);
      miSaveAs.setDisable(!hasFile);
      miSaveAllTabs.setText(dirtyTabs > 0 ? "Save All Open Tabs (" + dirtyTabs + " dirty)" : "Save All Open Tabs");
      miSaveAllTabs.setDisable(dirtyTabs == 0);
      String activeTitle = activeTab != null && activeTab.getText() != null
          ? activeTab.getText().replace(" *", "")
          : "";
      miCloseTab.setText(activeTab != null && activeTab.isClosable() && !activeTitle.isBlank()
          ? "Close " + activeTitle
          : "Close Tab");
      miCloseTab.setDisable(activeTab == null || !activeTab.isClosable());
      miCloseAllTabs.setText(closableTabs > 0 ? "Close All Tabs (" + closableTabs + ")" : "Close All Tabs");
      miCloseAllTabs.setDisable(closableTabs == 0);
      miRevealActiveFile.setText(hasFile ? "Reveal " + ft.getDisplayName() + " in File Manager" : "Reveal Active File in File Manager");
      miRevealActiveFile.setDisable(!hasFile);
      miCopyActiveFilePath.setText(hasFile ? "Copy " + ft.getDisplayName() + " Path" : "Copy Active File Path");
      miCopyActiveFilePath.setDisable(!hasFile);

      miUndo.setText(commands.canUndo() ? "Undo " + commands.undoDescription() : "Undo");
      miRedo.setText(commands.canRedo() ? "Redo " + commands.redoDescription() : "Redo");
      miUndo.setDisable(!commands.canUndo());
      miRedo.setDisable(!commands.canRedo());
      miFind.setDisable(!hasFile);
      miGoToLine.setDisable(ft == null || ft.getKind() != FileEditorTab.Kind.VNS);
      miReload.setDisable(!hasFile);

      miToggleEditorFullscreen.setText(editorFullscreen || layeredVisualizerFullscreen
          ? "Restore Editor Layout"
          : "Toggle Editor Fullscreen");
      miToggleEditorFullscreen.setDisable(!canFullscreen);
      miResetCamera.setDisable(!canResetActiveCamera());
      miFitContent.setDisable(!canFitActiveContent());

      miApplyCode.setDisable(!canApplyPreview());
      miLaunchHere.setDisable(!canLaunchFromActiveTab());
      miLaunchStart.setDisable(!canLaunchFromActiveTab());
      miOpenVcs.setDisable(!hasProject);
      miRefreshVcs.setDisable(!hasProject);
      miRevealProjectRoot.setDisable(!hasProject);
      miCopyProjectRootPath.setDisable(!hasProject);
      miOpenProjectDocs.setDisable(resolveDocsDirectory(projectRoot) == null);
      miOpenWorkspaceDocs.setDisable(resolveDocsDirectory(resolveWorkspaceRoot()) == null);

      toolbarCommandSummary.setText(buildMainCommandSummary());
    };
    refreshMainCommandUi = refreshChrome;
    commands.setOnChange(refreshChrome);
    menuFile.setOnShowing(e -> refreshChrome.run());
    menuEdit.setOnShowing(e -> refreshChrome.run());
    menuView.setOnShowing(e -> refreshChrome.run());
    menuNavigate.setOnShowing(e -> refreshChrome.run());
    menuRun.setOnShowing(e -> refreshChrome.run());
    menuTools.setOnShowing(e -> refreshChrome.run());
    menuVcs.setOnShowing(e -> refreshChrome.run());
    menuHelp.setOnShowing(e -> refreshChrome.run());

    Label wordmark = new Label("JVN");
    wordmark.getStyleClass().add("jvn-wordmark");
    Label verLabel = new Label("v" + editorVersion);
    verLabel.getStyleClass().add("jvn-wordmark-version");
    VBox logoBox = new VBox(2);
    logoBox.setAlignment(Pos.CENTER_RIGHT);
    logoBox.getStyleClass().add("jvn-wordmark-box");
    logoBox.getChildren().addAll(wordmark, verLabel);
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
    VBox top = new VBox(commandBar, toolbar);
    top.getStyleClass().add("master-toolbar-shell");
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
    if (editorPreferences.isShowWelcomeOnStartup()) {
      filesTabs.getTabs().add(tabWelcome);
      filesTabs.getSelectionModel().select(tabWelcome);
    }
    root.setCenter(filesTabs);
    inspectorView = new InspectorView(s -> status.setText(s));
    inspectorView.setCommandStack(commands);
    inspectorView.setMinWidth(280);
    inspectorView.setPrefWidth(320);
    inspectorScroll = new ScrollPane(inspectorView);
    inspectorScroll.setFitToWidth(true);
    rightTabs = new TabPane();
    rightTabs.getStyleClass().add("sidebar-tab-pane");
    rightTabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
    rightTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
    tabRightAdd = new Tab("", new Region()); tabRightAdd.setClosable(false);
    tabRightAdd.setGraphic(CssIcon.plus("#8cd48c"));
    tabRightAdd.getStyleClass().add("sidebar-add-tab");
    rightTabs.getTabs().addAll(tabRightAdd);
    installAddTabBehavior(rightTabs, tabRightAdd, this::showRightAddMenu);
    rightTabs.setPrefWidth(360);
    projView = new ProjectExplorerView();
    if (projectRoot != null) {
      projView.setRootDirectory(projectRoot);
    }
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
    leftTabs.getStyleClass().add("sidebar-tab-pane");
    leftTabs.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
    leftTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
    tabProject = new Tab("Project", projView); tabProject.setClosable(false);
    tabLeftAdd = new Tab("", new Region()); tabLeftAdd.setClosable(false);
    tabLeftAdd.setGraphic(CssIcon.plus("#8cd48c"));
    tabLeftAdd.getStyleClass().add("sidebar-add-tab");
    leftTabs.getTabs().addAll(tabProject, tabLeftAdd);
    installAddTabBehavior(leftTabs, tabLeftAdd, this::showLeftAddMenu);
    leftTabs.getSelectionModel().select(tabProject);
    leftTabs.setPrefWidth(300);
    centerSplit = new SplitPane();
    centerSplit.getStyleClass().add("editor-main-split-pane");
    centerSplit.getItems().addAll(leftTabs, filesTabs, rightTabs);
    centerSplit.setDividerPositions(0.22, 0.78);
    savedCenterDividers = new double[]{0.22, 0.78};
    applyEditorPreferences(editorPreferences);
    root.setLeft(null);
   root.setRight(null);
    root.setCenter(centerSplit);
    refreshMainCommandUi.run();

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
        return;
      }
      disposeAllFileTabs();
    });
    applyLinuxDefaultWindowState(primaryStage);
    primaryStage.show();
    installSidebarDividerHoverHints();
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

  private void doOpenTextFile(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Open JVN Text File");
      List<String> patterns = new ArrayList<>();
      for (String ext : EDITABLE_EXTENSIONS) {
        if (ext == null || ext.isBlank()) continue;
        patterns.add("*" + ext.trim());
      }
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JVN text files", patterns));
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
      File f = fc.showOpenDialog(stage);
      if (f == null) return;
      openFile(f);
    } catch (Exception ex) {
      status.setText("Load failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load: " + ex.getMessage());
      EditorTheme.apply(a);
      a.setHeaderText(null);
      a.setTitle("Error");
      a.showAndWait();
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
    refreshMainCommandUi.run();
  }

  private void toggleActiveEditorFullscreen() {
    if (centerSplit == null) return;
    if (layeredVisualizerFullscreen) {
      restoreLayeredImageVisualizerLayout(true);
      return;
    }
    if (!editorFullscreen) {
      // Save current divider positions and collapse sidebars
      savedCenterDividers = centerSplit.getDividerPositions().clone();
      centerSplit.setDividerPositions(0.0, 1.0);
      editorFullscreen = true;
      status.setText("Editor fullscreen — press F11 or Fullscreen to restore");
    } else {
      // Restore saved divider positions
      double left = savedCenterDividers != null && savedCenterDividers.length >= 2 ? savedCenterDividers[0] : 0.22;
      double right = savedCenterDividers != null && savedCenterDividers.length >= 2 ? savedCenterDividers[1] : 0.78;
      centerSplit.setDividerPositions(Math.max(0.05, left), Math.min(0.95, right));
      editorFullscreen = false;
      status.setText("Editor layout restored");
    }
    refreshMainCommandUi.run();
  }

  private void toggleImageToolFullscreen(ImageToolPanel tool) {
    if (tool == null) return;
    if (centerSplit == null || rightTabs == null) return;
    if (layeredVisualizerFullscreen) {
      if (fullscreenImageToolView == tool) {
        restoreLayeredImageVisualizerLayout(true);
        return;
      }
      restoreLayeredImageVisualizerLayout(false);
    }
    if (editorFullscreen) {
      toggleActiveEditorFullscreen();
    }
    Tab imageToolTab = ensureTabForImageTool(tool, rightTabs);
    if (imageToolTab == null || imageToolTab.getTabPane() == null) return;
    imageToolTab.getTabPane().getSelectionModel().select(imageToolTab);
    savedLayeredVisualizerDividers = centerSplit.getDividerPositions().clone();
    centerSplit.setDividerPositions(0.0, 0.0);
    layeredVisualizerFullscreen = true;
    fullscreenImageToolView = tool;
    setImageToolFullscreenState(tool);
    status.setText(imageToolName(tool) + " fullscreen — use the fullscreen button again to restore");
    refreshMainCommandUi.run();
  }

  private void restoreLayeredImageVisualizerLayout(boolean announce) {
    double left = savedLayeredVisualizerDividers != null && savedLayeredVisualizerDividers.length >= 2
        ? savedLayeredVisualizerDividers[0]
        : 0.22;
    double right = savedLayeredVisualizerDividers != null && savedLayeredVisualizerDividers.length >= 2
        ? savedLayeredVisualizerDividers[1]
        : 0.78;
    centerSplit.setDividerPositions(Math.max(0.05, left), Math.min(0.95, right));
    savedLayeredVisualizerDividers = null;
    layeredVisualizerFullscreen = false;
    fullscreenImageToolView = null;
    setImageToolFullscreenState(null);
    if (announce) {
      status.setText("Image tool layout restored");
    }
    refreshMainCommandUi.run();
  }

  private Tab ensureTabForImageTool(ImageToolPanel tool, TabPane targetPane) {
    if (tool == null || targetPane == null) return null;
    if (tool == layeredImageVisualizerView) return ensureLayeredImageVisualizerTab(targetPane);
    if (tool == imageAttributesToolView) return ensureImageAttributesToolTab(targetPane);
    if (tool == imageTintToolView) return ensureImageTintToolTab(targetPane);
    return null;
  }

  private void setImageToolFullscreenState(ImageToolPanel activeTool) {
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.setFullscreenActive(activeTool == layeredImageVisualizerView);
    if (imageAttributesToolView != null) imageAttributesToolView.setFullscreenActive(activeTool == imageAttributesToolView);
    if (imageTintToolView != null) imageTintToolView.setFullscreenActive(activeTool == imageTintToolView);
  }

  private String imageToolName(ImageToolPanel tool) {
    if (tool == layeredImageVisualizerView) return "Layered visualizer";
    if (tool == imageAttributesToolView) return "Image attributes tool";
    if (tool == imageTintToolView) return "Image tint tool";
    return "Image tool";
  }

  private void doSave(Stage stage) {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    File f = ft.getFile();
    if (f == null) { doSaveAs(stage); return; }
    ft.saveTo(f);
  }

  private void saveAllOpenTabs() {
    int savedFileTabs = 0;
    int failedFileTabs = 0;
    if (filesTabs != null) {
      for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
        if (!(tab.getContent() instanceof FileEditorTab ft) || !ft.isDirty()) continue;
        File target = ft.getFile();
        if (target == null) {
          failedFileTabs++;
          continue;
        }
        ft.saveTo(target);
        if (ft.isDirty()) failedFileTabs++;
        else savedFileTabs++;
      }
    }

    boolean studiosSaved = true;
    if (layoutStudioWindowManager != null) {
      studiosSaved = layoutStudioWindowManager.saveAllDirty(msg -> {
        if (status != null && msg != null && !msg.isBlank()) status.setText(msg);
      });
    }

    refreshTabDirtyIndicators();
    refreshMainCommandUi.run();

    if (status != null) {
      if (savedFileTabs == 0 && failedFileTabs == 0 && studiosSaved) {
        status.setText("Nothing to save.");
      } else {
        List<String> parts = new ArrayList<>();
        if (savedFileTabs > 0) parts.add("saved " + savedFileTabs + " tab" + (savedFileTabs == 1 ? "" : "s"));
        if (failedFileTabs > 0) parts.add(failedFileTabs + " tab" + (failedFileTabs == 1 ? "" : "s") + " still dirty");
        if (!studiosSaved) parts.add("studio windows need attention");
        status.setText(parts.isEmpty() ? "Nothing to save." : "Save all: " + String.join(", ", parts) + ".");
      }
    }
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
      if (currentTab != null && filesTabs != null) closeAndDisposeTab(currentTab);
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

  private void executeUndo() {
    if (!commands.canUndo()) return;
    commands.undo();
    if (status != null) status.setText("Undo");
    if (inspectorView != null) inspectorView.setSelection(selected);
    refreshMainCommandUi.run();
  }

  private void executeRedo() {
    if (!commands.canRedo()) return;
    commands.redo();
    if (status != null) status.setText("Redo");
    if (inspectorView != null) inspectorView.setSelection(selected);
    refreshMainCommandUi.run();
  }

  private boolean canApplyPreview() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return false;
    return ft.getKind() == FileEditorTab.Kind.JES || ft.getKind() == FileEditorTab.Kind.VNS;
  }

  private boolean canLaunchFromActiveTab() {
    FileEditorTab ft = getActiveFileTab();
    return ft != null && ft.getKind() == FileEditorTab.Kind.VNS;
  }

  private boolean canResetActiveCamera() {
    FileEditorTab ft = getActiveFileTab();
    return ft != null && ft.getKind() == FileEditorTab.Kind.JES && ft.getViewport() != null;
  }

  private boolean canFitActiveContent() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return false;
    return ft.getKind() == FileEditorTab.Kind.JES || ft.getKind() == FileEditorTab.Kind.VNS;
  }

  private boolean canToggleActiveEditorLayout() {
    FileEditorTab ft = getActiveFileTab();
    return ft != null && ft.supportsEditorFullscreenToggle();
  }

  private int countDirtyFileTabs() {
    if (filesTabs == null) return 0;
    int dirtyCount = 0;
    for (Tab tab : filesTabs.getTabs()) {
      if (tab.getContent() instanceof FileEditorTab ft && ft.isDirty()) dirtyCount++;
    }
    return dirtyCount;
  }

  private int countClosableTabs() {
    if (filesTabs == null) return 0;
    int closableCount = 0;
    for (Tab tab : filesTabs.getTabs()) {
      if (tab.isClosable()) closableCount++;
    }
    return closableCount;
  }

  private String buildMainCommandSummary() {
    List<String> parts = new ArrayList<>();
    parts.add(projectRoot != null ? "Project " + projectRoot.getName() : "No Project Open");

    Tab activeTab = filesTabs != null ? filesTabs.getSelectionModel().getSelectedItem() : null;
    FileEditorTab ft = getActiveFileTab();
    if (ft != null) {
      parts.add(kindLabel(ft.getKind()));
      parts.add(ft.getDisplayName() + (ft.isDirty() ? " Unsaved" : " Saved"));
      if (canLaunchFromActiveTab()) {
        parts.add("Launch Here Ready");
      } else if (ft.getKind() == FileEditorTab.Kind.JES) {
        parts.add("Scene Preview Ready");
      }
    } else if (activeTab == tabWelcome) {
      parts.add("Welcome Center");
    } else if (activeTab != null && activeTab.getText() != null && !activeTab.getText().isBlank()) {
      parts.add(activeTab.getText());
    } else {
      parts.add("No File Selected");
    }

    int dirtyTabs = countDirtyFileTabs();
    if (dirtyTabs > 0 && (ft == null || !ft.isDirty() || dirtyTabs > 1)) {
      parts.add(dirtyTabs + " Unsaved Tab" + (dirtyTabs == 1 ? "" : "s"));
    }
    if (commands.canUndo()) parts.add("Undo " + commands.undoDescription());
    if (commands.canRedo()) parts.add("Redo " + commands.redoDescription());
    if (layeredVisualizerFullscreen && fullscreenImageToolView != null) {
      parts.add(imageToolName(fullscreenImageToolView) + " Fullscreen");
    } else if (editorFullscreen) {
      parts.add("Editor Fullscreen");
    }
    return String.join("  •  ", parts);
  }

  private String kindLabel(FileEditorTab.Kind kind) {
    if (kind == null) return "Editor";
    return switch (kind) {
      case JES -> "JES Scene";
      case VNS -> "VNS Script";
      case JAVA -> "Java Source";
      case TIMELINE -> "Timeline";
      case THEME -> "Theme";
      case MENU_SCREEN -> "Menu Screen";
      case MENU_LAYOUT -> "Menu Layout";
      case MENU_STYLE -> "Menu Style";
      case DIALOGUE_LAYOUT -> "Dialogue Layout";
      case OTHER -> "Text File";
    };
  }

  private String mapKey(KeyCode code) { return code == null ? "" : (code.getName() == null || code.getName().isBlank() ? code.toString() : code.getName()).toUpperCase(); }
  private int mapButton(MouseButton b) { if (b == MouseButton.PRIMARY) return 1; if (b == MouseButton.MIDDLE) return 2; if (b == MouseButton.SECONDARY) return 3; return 0; }

  private void updatePerf(long nowNs) {
    if (perf == null) return;
    if (lastPerfUpdateNs > 0 && (nowNs - lastPerfUpdateNs) < PERF_UPDATE_INTERVAL_NS) return;
    lastPerfUpdateNs = nowNs;

    double processCpu = -1;
    if (osBean != null) {
      processCpu = osBean.getProcessCpuLoad();
    }

    smoothedProcessCpu = smoothRatio(smoothedProcessCpu, processCpu, PERF_CPU_SMOOTH_ALPHA);
    smoothedFps = smoothRatio(smoothedFps, lastFps / TARGET_FPS, PERF_FPS_SMOOTH_ALPHA);

    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heap = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
    double heapUsedMb = Math.max(0.0, bytesToMb(heap == null ? -1L : heap.getUsed()));
    long heapMaxBytes = heap == null ? -1L : heap.getMax();
    if (heapMaxBytes <= 0 && heap != null) {
      heapMaxBytes = heap.getCommitted();
    }
    if (heapMaxBytes <= 0) {
      heapMaxBytes = Runtime.getRuntime().maxMemory();
    }
    double heapMaxMb = Math.max(1.0, bytesToMb(heapMaxBytes));
    double nonHeapMb = Math.max(0.0, bytesToMb(nonHeap == null ? -1L : nonHeap.getUsed()));
    double heapRatio = clamp01(heapUsedMb / heapMaxMb);
    double heapUsedClampedMb = clamp(heapUsedMb, 0.0, heapMaxMb);
    double jvnUsedMb = Math.max(0.0, heapUsedMb + nonHeapMb);

    long gcCount = 0;
    long gcTimeMs = 0;
    for (var gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (gcBean == null) continue;
      long c = gcBean.getCollectionCount();
      long t = gcBean.getCollectionTime();
      if (c > 0) gcCount += c;
      if (t > 0) gcTimeMs += t;
    }
    long gcCountDelta = (lastGcCollectionCount >= 0L) ? Math.max(0L, gcCount - lastGcCollectionCount) : 0L;
    long gcTimeDelta = (lastGcCollectionTimeMs >= 0L) ? Math.max(0L, gcTimeMs - lastGcCollectionTimeMs) : 0L;
    lastGcCollectionCount = gcCount;
    lastGcCollectionTimeMs = gcTimeMs;
    String gcText = gcCountDelta > 0
        ? String.format(Locale.ROOT, "GC +%d/%dms", gcCountDelta, gcTimeDelta)
        : "GC idle";

    int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

    String cpuTextValue = isRatioValid(smoothedProcessCpu)
        ? String.format(
            Locale.ROOT,
            "CPU app %.0f%%",
            safePercent(smoothedProcessCpu))
        : "CPU --";
    String heapText = String.format(
        Locale.ROOT,
        " | HEAP %.0f/%.0f MB (%.0f%%)",
        heapUsedClampedMb,
        heapMaxMb,
        heapRatio * 100.0);
    String ramTextValue = String.format(
        Locale.ROOT,
        " | JVN %.0f MB • non-heap %.0f MB",
        jvnUsedMb,
        nonHeapMb);
    String fpsTextValue = String.format(
        Locale.ROOT,
        " | FPS %.0f | THR %d | %s",
        Math.max(0.0, smoothedFps * TARGET_FPS),
        threadCount,
        gcText);

    cpuText.setText(cpuTextValue);
    gpuText.setText(heapText);
    ramText.setText(ramTextValue);
    fpsText.setText(fpsTextValue);

    perfGraph.pushSample(
        isRatioValid(smoothedProcessCpu) ? smoothedProcessCpu : 0,
        heapRatio,
        clamp01(smoothedFps));
  }

  private static double smoothRatio(double previous, double sample, double alpha) {
    if (!Double.isFinite(sample) || sample < 0) return previous;
    double clampedSample = clamp01(sample);
    if (!Double.isFinite(previous) || previous < 0) return clampedSample;
    double a = Math.min(1.0, Math.max(0.01, alpha));
    return previous + (clampedSample - previous) * a;
  }

  private static boolean isRatioValid(double ratio) {
    return Double.isFinite(ratio) && ratio >= 0;
  }

  private static double safePercent(double ratio) {
    if (!isRatioValid(ratio)) return 0.0;
    return clamp01(ratio) * 100.0;
  }

  private static double bytesToMb(long bytes) {
    if (bytes <= 0) return 0.0;
    return bytes / (1024.0 * 1024.0);
  }

  private static double clamp(double value, double min, double max) {
    if (!Double.isFinite(value)) return min;
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private static double clamp01(double value) {
    if (!Double.isFinite(value)) return 0;
    if (value < 0) return 0;
    if (value > 1) return 1;
    return value;
  }

  /** Tiny inline graph renderer for CPU/heap/FPS trends. */
  private static class PerfGraph {
    private final Canvas canvas = new Canvas(320, 64);
    private final double[] cpu = new double[240]; // ~72s at ~300ms updates
    private final double[] ram = new double[240];
    private final double[] fps = new double[240];
    private int idx = 0;
    private boolean filled = false;

    public Canvas getCanvas() { return canvas; }
    public void setWidth(double w) {
      canvas.setWidth(sanitizeCanvasDimension(w, 160.0));
      redraw();
    }

    public void pushSample(double cpu01, double ram01, double fps01) {
      int i = idx % cpu.length;
      cpu[i] = clamp01(cpu01);
      ram[i] = clamp01(ram01);
      fps[i] = clamp01(fps01);
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

      // HEAP area
      g.setFill(GPU_COLOR.deriveColor(0, 1, 1, 0.26));
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

      // HEAP outline
      g.setStroke(GPU_COLOR.deriveColor(0, 1, 1, 0.95));
      g.setLineWidth(1.6);
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - ram[si]);
        if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
      }
      g.stroke();

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

      // FPS line
      g.setStroke(FPS_COLOR.deriveColor(0, 1, 1, 0.88));
      g.setLineWidth(2);
      g.beginPath();
      for (int i = 0; i < samples; i++) {
        int si = (idx - samples + i + cpu.length) % cpu.length;
        double x = i * scaleX;
        double y = h * (1 - fps[si]);
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
    Region r = CssIcon.prepare(new Region());
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

  private boolean saveDirtyEditorsBeforeRun() {
    int savedFileTabs = 0;
    if (filesTabs != null) {
      for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
        if (!(tab.getContent() instanceof FileEditorTab ft) || !ft.isDirty()) continue;
        File target = ft.getFile();
        if (target == null) {
          showRunBlockedAlert("Run cancelled: unsaved file",
              "Save " + ft.getDisplayName() + " before running the project.");
          return false;
        }
        ft.saveTo(target);
        if (ft.isDirty()) {
          showRunBlockedAlert("Run cancelled: save failed",
              "Failed to save " + ft.getDisplayName() + ". Resolve save errors and run again.");
          return false;
        }
        savedFileTabs++;
      }
    }

    if (layoutStudioWindowManager != null) {
      boolean studiosSaved = layoutStudioWindowManager.saveAllDirty(msg -> {
        if (status != null && msg != null && !msg.isBlank()) status.setText(msg);
      });
      if (!studiosSaved) {
        showRunBlockedAlert("Run cancelled: Studio save failed",
            "One or more open Studio windows could not be saved. Resolve the save error and run again.");
        return false;
      }
    }

    if (savedFileTabs > 0) {
      refreshTabDirtyIndicators();
      if (status != null) status.setText("Saved " + savedFileTabs + " file(s) before run.");
    }
    return true;
  }

  private void showRunBlockedAlert(String header, String content) {
    if (status != null && header != null && !header.isBlank()) status.setText(header);
    Alert alert = new Alert(Alert.AlertType.WARNING);
    EditorTheme.apply(alert);
    alert.setTitle("Run Blocked");
    alert.setHeaderText(header == null || header.isBlank() ? "Run cancelled" : header);
    alert.setContentText(content == null ? "" : content);
    alert.showAndWait();
  }

  private void closeActiveTab() {
    if (filesTabs == null) return;
    Tab active = filesTabs.getSelectionModel().getSelectedItem();
    if (active == null) return;
    if (!active.isClosable()) return;
    if (active.getContent() instanceof FileEditorTab ft && !confirmCanCloseFileTab(ft)) return;
    closeAndDisposeTab(active);
  }

  private void closeAllClosableTabs() {
    if (filesTabs == null) return;
    if (!confirmCloseAllTabs()) return;
    int closed = 0;
    for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
      if (!tab.isClosable()) continue;
      closeAndDisposeTab(tab);
      closed++;
    }
    refreshMainCommandUi.run();
    if (status != null) {
      status.setText(closed > 0 ? "Closed " + closed + " tab" + (closed == 1 ? "" : "s") + "." : "No closable tabs.");
    }
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

  private void revealActiveFileInFileManager() {
    FileEditorTab ft = getActiveFileTab();
    File target = ft != null ? ft.getFile() : null;
    if (target == null) {
      if (status != null) status.setText("No active file to reveal.");
      return;
    }
    openDirectoryInFileManager(target.isDirectory() ? target : target.getParentFile(),
        "Could not reveal " + ft.getDisplayName() + ".");
  }

  private void revealProjectRootInFileManager() {
    openDirectoryInFileManager(projectRoot, "Project root is not available.");
  }

  private void copyActiveFilePathToClipboard() {
    FileEditorTab ft = getActiveFileTab();
    File target = ft != null ? ft.getFile() : null;
    if (target == null) {
      if (status != null) status.setText("No active file path to copy.");
      return;
    }
    copyToClipboard(target.getAbsolutePath());
    if (status != null) status.setText("Copied path for " + ft.getDisplayName() + ".");
  }

  private void copyProjectRootPathToClipboard() {
    if (projectRoot == null) {
      if (status != null) status.setText("Project root is not available.");
      return;
    }
    copyToClipboard(projectRoot.getAbsolutePath());
    if (status != null) status.setText("Copied project root path.");
  }

  private void openProjectDocsFolder() {
    openDirectoryInFileManager(resolveDocsDirectory(projectRoot), "Project docs folder not found.");
  }

  private void openWorkspaceDocsFolder() {
    openDirectoryInFileManager(resolveDocsDirectory(resolveWorkspaceRoot()), "Workspace docs folder not found.");
  }

  private void openDirectoryInFileManager(File dir, String missingMessage) {
    if (dir == null || !dir.isDirectory()) {
      if (status != null && missingMessage != null && !missingMessage.isBlank()) status.setText(missingMessage);
      return;
    }
    try {
      java.awt.Desktop.getDesktop().open(dir);
      if (status != null) status.setText("Opened " + dir.getName() + ".");
    } catch (Exception ex) {
      if (status != null) status.setText("Failed to open " + dir.getAbsolutePath() + ".");
    }
  }

  private File resolveDocsDirectory(File root) {
    if (root == null) return null;
    File docs = new File(root, "docs");
    return docs.isDirectory() ? docs : null;
  }

  private void copyToClipboard(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void refreshTabDirtyIndicators() {
    if (filesTabs == null) return;
    for (Tab t : filesTabs.getTabs()) {
      if (t.getContent() instanceof FileEditorTab ft) {
        updateTabTitle(t, ft);
      }
    }
    refreshMainCommandUi.run();
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
    editor.setCodeEditorFontSize(editorPreferences.getCodeEditorFontSize());
    editor.setStoryboardOverlay(storyboardOverlayState);
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
        if (puppeteerLauncherPanel != null) {
          puppeteerLauncherPanel.setProjectRoot(projectRoot);
          puppeteerLauncherPanel.setActiveScriptFile(editor.getFile());
          puppeteerLauncherPanel.setSource(text);
        }
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
    tab.setOnClosed(e -> {
      try {
        editor.dispose();
      } catch (Exception ignored) {
      }
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
      closeAndDisposeTab(tab);
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
    if (phoneAssetsToolView != null) phoneAssetsToolView.setProjectRoot(projectRoot);
    if (storyboardOverlayView != null) storyboardOverlayView.setProjectRoot(projectRoot);
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.setProjectRoot(projectRoot);
    if (imageAttributesToolView != null) imageAttributesToolView.setProjectRoot(projectRoot);
    if (imageTintToolView != null) imageTintToolView.setProjectRoot(projectRoot);
    if (menuFlowEditorView != null) menuFlowEditorView.setProjectRoot(projectRoot);
    if (puppeteerLauncherPanel != null) puppeteerLauncherPanel.setProjectRoot(projectRoot);
    if (welcomeView != null) welcomeView.setCurrentProject(projectRoot);
    syncStoryboardOverlayProjectState();
    refreshMainCommandUi.run();
  }

  private void applyEditorPreferences(EditorPreferences preferences) {
    editorPreferences = preferences == null ? EditorPreferences.defaults() : preferences.copy();
    if (editorSettingsView != null) {
      editorSettingsView.loadIntoForm(editorPreferences);
    }
    applyCodeEditorFontSizePreference();
    applyWelcomeTabPreference();
    applyDefaultSidebarPreferences();
    if (status != null) {
      status.setText("Editor preferences applied");
    }
  }

  private void rememberPanelPlacement(EditorSidebarPanel panel, EditorPanelPlacement placement) {
    if (panel == null || placement == null) return;
    editorPreferences.setPlacement(panel, placement);
    if (editorSettingsView != null) {
      editorSettingsView.loadIntoForm(editorPreferences);
    }
    persistEditorPreferences();
    refreshOpenPanelChooserIndicators();
  }

  private void persistEditorPreferences() {
    if (editorPreferencesStore == null || editorPreferences == null) return;
    try {
      editorPreferencesStore.save(editorPreferences);
    } catch (IOException ex) {
      if (status != null) {
        status.setText("Failed to save editor preferences: " + ex.getMessage());
      }
    }
  }

  private void applyCodeEditorFontSizePreference() {
    double fontSize = editorPreferences.getCodeEditorFontSize();
    if (filesTabs != null) {
      for (Tab tab : filesTabs.getTabs()) {
        if (tab.getContent() instanceof FileEditorTab fileEditorTab) {
          fileEditorTab.setCodeEditorFontSize(fontSize);
        }
      }
    }
    if (scriptEditorLauncherView != null) {
      scriptEditorLauncherView.setCodeEditorFontSize(fontSize);
    }
  }

  private void applyWelcomeTabPreference() {
    if (filesTabs == null || tabWelcome == null) return;
    boolean wantsWelcome = editorPreferences.isShowWelcomeOnStartup();
    boolean hasWelcome = filesTabs.getTabs().contains(tabWelcome);
    if (wantsWelcome && !hasWelcome) {
      filesTabs.getTabs().add(0, tabWelcome);
      if (filesTabs.getSelectionModel().getSelectedItem() == null) {
        filesTabs.getSelectionModel().select(tabWelcome);
      }
      return;
    }
    if (!wantsWelcome && hasWelcome) {
      boolean selected = filesTabs.getSelectionModel().getSelectedItem() == tabWelcome;
      filesTabs.getTabs().remove(tabWelcome);
      if (selected && !filesTabs.getTabs().isEmpty()) {
        filesTabs.getSelectionModel().select(filesTabs.getTabs().get(0));
      }
    }
  }

  private void applyDefaultSidebarPreferences() {
    if (leftTabs == null || rightTabs == null) return;
    EnumSet<EditorSidebarPanel> attachedBeforeRefresh = EnumSet.noneOf(EditorSidebarPanel.class);
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      if (isPanelAttached(panel)) {
        attachedBeforeRefresh.add(panel);
      }
    }
    boolean loadOnDemand = editorPreferences != null && editorPreferences.isLoadSidebarExtensionsOnDemand();
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      detachConfiguredPanel(panel);
    }
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      EditorPanelPlacement placement = editorPreferences.getPlacement(panel);
      if (loadOnDemand
          && panel != EditorSidebarPanel.PROJECT
          && !attachedBeforeRefresh.contains(panel)) {
        continue;
      }
      if (placement == EditorPanelPlacement.LEFT) {
        ensureSidebarPanel(panel, leftTabs);
      } else if (placement == EditorPanelPlacement.RIGHT) {
        ensureSidebarPanel(panel, rightTabs);
      }
    }
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      releaseSidebarPanelIfUnused(panel);
    }
    releaseEditorSettingsIfUnused();
    Tab leftDefault = firstRegularTab(leftTabs, tabLeftAdd);
    if (leftDefault != null) {
      leftTabs.getSelectionModel().select(leftDefault);
    }
    Tab rightDefault = firstRegularTab(rightTabs, tabRightAdd);
    if (rightDefault != null) {
      rightTabs.getSelectionModel().select(rightDefault);
    }
    if (centerSplit != null) {
      boolean hasLeft = firstRegularTab(leftTabs, tabLeftAdd) != null;
      boolean hasRight = firstRegularTab(rightTabs, tabRightAdd) != null;
      double leftDivider = hasLeft ? 0.22 : 0.0;
      double rightDivider = hasRight ? 0.78 : 1.0;
      centerSplit.setDividerPositions(leftDivider, rightDivider);
      savedCenterDividers = new double[] { leftDivider, rightDivider };
    }
    refreshOpenPanelChooserIndicators();
  }

  private void detachConfiguredPanel(EditorSidebarPanel panel) {
    Tab tab = configuredPanelTab(panel);
    if (tab != null && tab.getTabPane() != null) {
      tab.getTabPane().getTabs().remove(tab);
      refreshOpenPanelChooserIndicators();
    }
  }

  private boolean isPanelAttached(EditorSidebarPanel panel) {
    Tab tab = configuredPanelTab(panel);
    return tab != null && tab.getTabPane() != null;
  }

  private Tab configuredPanelTab(EditorSidebarPanel panel) {
    if (panel == null) return null;
    return switch (panel) {
      case PROJECT -> tabProject;
      case TIMELINE -> tabTimeline;
      case INSPECTOR -> tabInspector;
      case VNS_DIAGNOSTICS -> tabVnsDiagnostics;
      case LABEL_FLOW -> tabVnsFlowMap;
      case ASSETS -> tabAssetBrowser;
      case LAYOUT_LAUNCHER -> tabLayoutLauncher;
      case PHONE_ASSETS -> tabPhoneAssetsTool;
      case STORYBOARD_OVERLAY -> tabStoryboardOverlay;
      case LAYERED_IMAGES -> tabLayeredImageVisualizer;
      case IMAGE_ATTRIBUTES -> tabImageAttributesTool;
      case IMAGE_TINT -> tabImageTintTool;
      case MENU_FLOW -> tabMenuFlow;
      case VERSION_CONTROL -> tabVersionControl;
      case HELP -> tabHelp;
      case PUPPETEER_LAUNCHER -> tabPuppeteerLauncher;
      case SCRIPT_EDITOR -> tabScriptEditorLauncher;
    };
  }

  private Tab ensureSidebarPanel(EditorSidebarPanel panel, TabPane targetPane) {
    if (panel == null || targetPane == null) return null;
    return switch (panel) {
      case PROJECT -> ensureProjectTab(targetPane);
      case TIMELINE -> ensureTimelineTab(targetPane);
      case INSPECTOR -> ensureInspectorTab(targetPane);
      case VNS_DIAGNOSTICS -> ensureVnsDiagnosticsTab(targetPane);
      case LABEL_FLOW -> ensureVnsFlowMapTab(targetPane);
      case ASSETS -> ensureAssetBrowserTab(targetPane);
      case LAYOUT_LAUNCHER -> ensureLayoutLauncherTab(targetPane);
      case PHONE_ASSETS -> ensurePhoneAssetsToolTab(targetPane);
      case STORYBOARD_OVERLAY -> ensureStoryboardOverlayTab(targetPane);
      case LAYERED_IMAGES -> ensureLayeredImageVisualizerTab(targetPane);
      case IMAGE_ATTRIBUTES -> ensureImageAttributesToolTab(targetPane);
      case IMAGE_TINT -> ensureImageTintToolTab(targetPane);
      case MENU_FLOW -> ensureMenuFlowTab(targetPane);
      case VERSION_CONTROL -> ensureVersionControlTab(targetPane);
      case HELP -> ensureHelpTab(targetPane);
      case PUPPETEER_LAUNCHER -> ensurePuppeteerLauncherTab(targetPane);
      case SCRIPT_EDITOR -> ensureScriptEditorLauncherTab(targetPane);
    };
  }

  private void openEditableOrExternal(File target) {
    if (target == null) return;
    if (isEditableFile(target)) {
      openFile(target);
      return;
    }
    try {
      java.awt.Desktop.getDesktop().open(target);
    } catch (Exception ignored) {
    }
  }

  private EditorSettingsView ensureEditorSettingsView() {
    if (editorSettingsView != null) return editorSettingsView;
    editorSettingsView = new EditorSettingsView(editorPreferencesStore);
    editorSettingsView.setOnPreferencesApplied(this::applyEditorPreferences);
    editorSettingsView.loadIntoForm(editorPreferences);
    return editorSettingsView;
  }

  private VnsDiagnosticsView ensureVnsDiagnosticsView() {
    if (vnsDiagnosticsView != null) return vnsDiagnosticsView;
    vnsDiagnosticsView = new VnsDiagnosticsView();
    vnsDiagnosticsView.setOnOpenTarget(this::jumpToActiveVnsDiagnostic);
    FileEditorTab ft = getActiveFileTab();
    refreshVnsToolPanels(ft, ft != null ? ft.getCurrentTextSnapshot() : null);
    return vnsDiagnosticsView;
  }

  private VnsFlowMapView ensureVnsFlowMapView() {
    if (vnsFlowMapView != null) return vnsFlowMapView;
    vnsFlowMapView = new VnsFlowMapView();
    vnsFlowMapView.setOnOpenLine(this::jumpToActiveVnsLine);
    FileEditorTab ft = getActiveFileTab();
    refreshVnsToolPanels(ft, ft != null ? ft.getCurrentTextSnapshot() : null);
    return vnsFlowMapView;
  }

  private PuppeteerLauncherPanel ensurePuppeteerLauncherPanel() {
    if (puppeteerLauncherPanel != null) return puppeteerLauncherPanel;
    puppeteerLauncherPanel = new PuppeteerLauncherPanel();
    puppeteerLauncherPanel.setProjectRoot(projectRoot);
    puppeteerLauncherPanel.setOnLaunch(this::launchPuppeteerFromLauncher);
    puppeteerLauncherPanel.setOnOpenTarget(this::openPuppeteerLauncherTarget);
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
      puppeteerLauncherPanel.setActiveScriptFile(ft.getFile());
      puppeteerLauncherPanel.setSource(ft.getCurrentTextSnapshot());
      puppeteerLauncherPanel.setCaretLine(ft.getVnsCaretLine());
    }
    return puppeteerLauncherPanel;
  }

  private AssetBrowserView ensureAssetBrowserView() {
    if (assetBrowserView != null) return assetBrowserView;
    assetBrowserView = new AssetBrowserView();
    assetBrowserView.setProjectRoot(projectRoot);
    assetBrowserView.setOnOpenAsset(this::openEditableOrExternal);
    return assetBrowserView;
  }

  private VersionControlView ensureVersionControlView() {
    if (versionControlView != null) return versionControlView;
    versionControlView = new VersionControlView();
    versionControlView.setProjectRoot(projectRoot);
    versionControlView.setOnOpenRelativePath(relativePath -> {
      if (projectRoot == null || relativePath == null || relativePath.isBlank()) return;
      File target = new File(projectRoot, relativePath);
      if (!target.exists()) return;
      if (isEditableFile(target)) openFile(target);
    });
    return versionControlView;
  }

  private LayoutEditorLauncherView ensureLayoutEditorLauncherView() {
    if (layoutEditorLauncherView != null) return layoutEditorLauncherView;
    layoutEditorLauncherView = new LayoutEditorLauncherView();
    layoutEditorLauncherView.setProjectRoot(projectRoot);
    layoutEditorLauncherView.setOnOpenFile(this::openEditableOrExternal);
    return layoutEditorLauncherView;
  }

  private PhoneAssetsToolView ensurePhoneAssetsToolView() {
    if (phoneAssetsToolView != null) return phoneAssetsToolView;
    phoneAssetsToolView = new PhoneAssetsToolView();
    phoneAssetsToolView.setProjectRoot(projectRoot);
    phoneAssetsToolView.setOnOpenFile(this::openEditableOrExternal);
    return phoneAssetsToolView;
  }

  private StoryboardOverlayView ensureStoryboardOverlayView() {
    if (storyboardOverlayView != null) return storyboardOverlayView;
    storyboardOverlayView = new StoryboardOverlayView();
    storyboardOverlayView.setOnOverlayChanged(this::setStoryboardOverlayState);
    storyboardOverlayView.setProjectRoot(projectRoot);
    refreshStoryboardOverlayContext(getActiveFileTab());
    return storyboardOverlayView;
  }

  private LayeredImageVisualizerView ensureLayeredImageVisualizerView() {
    if (layeredImageVisualizerView != null) return layeredImageVisualizerView;
    layeredImageVisualizerView = new LayeredImageVisualizerView();
    layeredImageVisualizerView.setProjectRoot(projectRoot);
    layeredImageVisualizerView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(layeredImageVisualizerView));
    layeredImageVisualizerView.setFullscreenActive(false);
    return layeredImageVisualizerView;
  }

  private ImageAttributesToolView ensureImageAttributesToolView() {
    if (imageAttributesToolView != null) return imageAttributesToolView;
    imageAttributesToolView = new ImageAttributesToolView();
    imageAttributesToolView.setProjectRoot(projectRoot);
    imageAttributesToolView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(imageAttributesToolView));
    imageAttributesToolView.setFullscreenActive(false);
    return imageAttributesToolView;
  }

  private ImageTintToolView ensureImageTintToolView() {
    if (imageTintToolView != null) return imageTintToolView;
    imageTintToolView = new ImageTintToolView();
    imageTintToolView.setProjectRoot(projectRoot);
    imageTintToolView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(imageTintToolView));
    imageTintToolView.setFullscreenActive(false);
    return imageTintToolView;
  }

  private MenuFlowEditorView ensureMenuFlowEditorView() {
    if (menuFlowEditorView != null) return menuFlowEditorView;
    menuFlowEditorView = new MenuFlowEditorView();
    menuFlowEditorView.setProjectRoot(projectRoot);
    menuFlowEditorView.setOnOpenFile(this::openEditableOrExternal);
    return menuFlowEditorView;
  }

  private ScriptEditorLauncherView ensureScriptEditorLauncherView() {
    if (scriptEditorLauncherView != null) return scriptEditorLauncherView;
    scriptEditorLauncherView = new ScriptEditorLauncherView();
    scriptEditorLauncherView.setProjectRoot(projectRoot);
    scriptEditorLauncherView.setWorkspaceRoot(resolveWorkspaceRoot());
    scriptEditorLauncherView.setCodeEditorFontSize(editorPreferences.getCodeEditorFontSize());
    scriptEditorLauncherView.setOnStatus(message -> {
      if (status != null) status.setText(message);
    });
    scriptEditorLauncherView.setOnOpenFile(this::openFile);
    scriptEditorLauncherView.setOnOpenFileAtLine((file, line) -> {
      openFile(file);
      javafx.application.Platform.runLater(() -> {
        if (filesTabs == null) return;
        for (Tab t : filesTabs.getTabs()) {
          if (t.getUserData() instanceof File ff && ff.equals(file) && t.getContent() instanceof FileEditorTab ft) {
            ft.navigateToLine(line);
            break;
          }
        }
      });
    });
    return scriptEditorLauncherView;
  }

  private HelpCenterView ensureHelpCenterView() {
    if (helpCenterView != null) return helpCenterView;
    helpCenterView = new HelpCenterView();
    helpCenterView.setWorkspaceRoot(resolveWorkspaceRoot());
    helpCenterView.setProjectRoot(projectRoot);
    helpCenterView.setOnOpenDoc(this::openFile);
    return helpCenterView;
  }

  private StoryTimelineView ensureTimelineView() {
    if (timelineView != null) return timelineView;
    timelineView = new StoryTimelineView();
    timelineView.setMinWidth(240);
    timelineView.setPrefWidth(320);
    timelineView.setOnRunArc(this::openTimelineArc);
    timelineView.setOnRunLink(this::openTimelineLinkTarget);
    if (projectRoot != null) {
      Properties mf = loadManifest(projectRoot);
      if (mf != null) {
        timelineView.setTimelineFile(resolveTimelineFile(projectRoot, mf));
      }
      timelineView.setProjectRoot(projectRoot);
    }
    return timelineView;
  }

  private void ensureSidebarVisible(TabPane targetPane) {
    if (targetPane == null || centerSplit == null) return;
    double[] positions = centerSplit.getDividerPositions();
    double leftDivider = positions.length > 0 ? positions[0] : 0.22;
    double rightDivider = positions.length > 1 ? positions[1] : 0.78;
    if (targetPane == leftTabs && firstRegularTab(leftTabs, tabLeftAdd) != null && leftDivider <= 0.01) {
      leftDivider = 0.22;
    }
    if (targetPane == rightTabs && firstRegularTab(rightTabs, tabRightAdd) != null && rightDivider >= 0.99) {
      rightDivider = 0.78;
    }
    centerSplit.setDividerPositions(leftDivider, rightDivider);
    savedCenterDividers = new double[] { leftDivider, rightDivider };
    updateSidebarDividerHoverHints();
  }

  private void installSidebarDividerHoverHints() {
    installSidebarDividerHoverHints(0);
  }

  private void installSidebarDividerHoverHints(int attempt) {
    if (centerSplit == null) return;
    Platform.runLater(() -> {
      List<StackPane> dividerNodes = centerSplit.lookupAll(".split-pane-divider").stream()
          .filter(StackPane.class::isInstance)
          .map(StackPane.class::cast)
          .sorted((a, b) -> Double.compare(a.getLayoutX(), b.getLayoutX()))
          .toList();
      if (dividerNodes.size() < 2) {
        if (attempt < 8) {
          PauseTransition retry = new PauseTransition(Duration.millis(80));
          retry.setOnFinished(e -> installSidebarDividerHoverHints(attempt + 1));
          retry.play();
        }
        return;
      }

      leftSidebarDividerNode = dividerNodes.get(0);
      rightSidebarDividerNode = dividerNodes.get(dividerNodes.size() - 1);
      leftSidebarHiddenArrow = ensureDividerArrow(leftSidebarDividerNode, true);
      rightSidebarHiddenArrow = ensureDividerArrow(rightSidebarDividerNode, false);

      leftSidebarDividerNode.hoverProperty().addListener((o, ov, nv) -> updateSidebarDividerHoverHints());
      rightSidebarDividerNode.hoverProperty().addListener((o, ov, nv) -> updateSidebarDividerHoverHints());
      if (centerSplit.getDividers().size() >= 2) {
        centerSplit.getDividers().get(0).positionProperty().addListener((o, ov, nv) -> updateSidebarDividerHoverHints());
        centerSplit.getDividers().get(1).positionProperty().addListener((o, ov, nv) -> updateSidebarDividerHoverHints());
      }
      updateSidebarDividerHoverHints();
    });
  }

  private Region ensureDividerArrow(StackPane divider, boolean leftSide) {
    if (divider == null) return null;
    for (javafx.scene.Node child : divider.getChildren()) {
      if (child instanceof Region region && region.getStyleClass().contains("sidebar-hidden-drag-arrow")) {
        return region;
      }
    }
    Region arrow = new Region();
    arrow.getStyleClass().add("sidebar-hidden-drag-arrow");
    arrow.getStyleClass().add(leftSide ? "left" : "right");
    arrow.setManaged(false);
    arrow.setVisible(false);
    arrow.setOpacity(0.0);
    arrow.setMouseTransparent(true);
    divider.getChildren().add(arrow);
    return arrow;
  }

  private void updateSidebarDividerHoverHints() {
    if (centerSplit == null || centerSplit.getDividers().size() < 2) return;
    boolean leftHidden = centerSplit.getDividers().get(0).getPosition() <= SIDEBAR_COLLAPSED_EPSILON;
    boolean rightHidden = centerSplit.getDividers().get(1).getPosition() >= (1.0 - SIDEBAR_COLLAPSED_EPSILON);
    updateSidebarDividerArrow(leftSidebarDividerNode, leftSidebarHiddenArrow, leftHidden);
    updateSidebarDividerArrow(rightSidebarDividerNode, rightSidebarHiddenArrow, rightHidden);
  }

  private void updateSidebarDividerArrow(StackPane divider, Region arrow, boolean collapsed) {
    if (arrow == null) return;
    if (!collapsed) {
      arrow.setVisible(false);
      arrow.setOpacity(0.0);
      return;
    }
    arrow.setVisible(true);
    boolean shouldShow = divider != null && divider.isHover();
    animateSidebarDividerArrow(arrow, shouldShow ? 1.0 : 0.0);
  }

  private void animateSidebarDividerArrow(javafx.scene.Node arrow, double targetOpacity) {
    if (arrow == null) return;
    Object existing = arrow.getProperties().get("sidebar-arrow-fade");
    if (existing instanceof FadeTransition fade) {
      fade.stop();
    }
    if (Math.abs(arrow.getOpacity() - targetOpacity) < 0.01) {
      arrow.setOpacity(targetOpacity);
      return;
    }
    FadeTransition fade = new FadeTransition(Duration.millis(140), arrow);
    fade.setToValue(targetOpacity);
    arrow.getProperties().put("sidebar-arrow-fade", fade);
    fade.play();
  }

  private FileEditorTab getActiveFileTab() {
    Tab t = filesTabs.getSelectionModel().getSelectedItem();
    if (t == null) return null;
    return (t.getContent() instanceof FileEditorTab) ? (FileEditorTab) t.getContent() : null;
  }

  private void updateContextForActiveTab() {
    FileEditorTab ft = getActiveFileTab();
    stopPreviewAudioInInactiveTabs(ft);
    JesScene2D scene = (ft != null) ? ft.getJesScene() : null;
    if (inspectorView != null) inspectorView.setScene(scene);
    if (mapEditorView != null) {
      if (ft != null && ft.getKind() == FileEditorTab.Kind.JES && ft.getFile() != null && projectRoot != null) {
        mapEditorView.setContext(projectRoot, ft.getFile());
      } else {
        mapEditorView.clearContext();
      }
    }
    refreshStoryboardOverlayContext(ft);
    refreshVnsToolPanels(ft, null);
    if (puppeteerLauncherPanel != null) {
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        puppeteerLauncherPanel.setProjectRoot(projectRoot);
        puppeteerLauncherPanel.setActiveScriptFile(ft.getFile());
        puppeteerLauncherPanel.setSource(ft.getCurrentTextSnapshot());
        puppeteerLauncherPanel.setCaretLine(ft.getVnsCaretLine());
      } else {
        puppeteerLauncherPanel.setActiveScriptFile(null);
        puppeteerLauncherPanel.clear();
      }
    }
    refreshMainCommandUi.run();
  }

  private void closeAndDisposeTab(Tab tab) {
    if (filesTabs == null || tab == null) return;
    if (tab.getContent() instanceof FileEditorTab ft) {
      try {
        ft.dispose();
      } catch (Exception ignored) {
      }
    }
    filesTabs.getTabs().remove(tab);
    refreshMainCommandUi.run();
  }

  private void stopPreviewAudioInInactiveTabs(FileEditorTab active) {
    if (filesTabs == null) return;
    for (Tab tab : filesTabs.getTabs()) {
      if (!(tab.getContent() instanceof FileEditorTab ft)) continue;
      if (ft == active) continue;
      try {
        ft.stopPreviewAudio();
      } catch (Exception ignored) {
      }
    }
  }

  private void stopAllPreviewAudio() {
    if (filesTabs == null) return;
    for (Tab tab : filesTabs.getTabs()) {
      if (!(tab.getContent() instanceof FileEditorTab ft)) continue;
      try {
        ft.stopPreviewAudio();
      } catch (Exception ignored) {
      }
    }
  }

  private void disposeAllFileTabs() {
    if (filesTabs == null) return;
    for (Tab tab : new ArrayList<>(filesTabs.getTabs())) {
      if (tab.getContent() instanceof FileEditorTab ft) {
        try {
          ft.dispose();
        } catch (Exception ignored) {
        }
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
    File analysisRoot = resolveVnsProjectRoot(scriptFile);

    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(source, analysisRoot, scriptFile);
    if (vnsDiagnosticsView != null) vnsDiagnosticsView.setAnalysis(scriptFile, analysis);
    if (vnsFlowMapView != null) vnsFlowMapView.setAnalysis(scriptFile, analysis);
  }

  private void jumpToActiveVnsLine(int oneBasedLine) {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null || ft.getKind() != FileEditorTab.Kind.VNS) return;
    ft.navigateToLine(oneBasedLine);
  }

  private void jumpToActiveVnsDiagnostic(VnsDiagnosticsView.OpenTarget target) {
    if (target == null) return;
    FileEditorTab ft = getActiveFileTab();
    if (ft == null || ft.getKind() != FileEditorTab.Kind.VNS) return;

    if (target.startOffset() >= 0) {
      ft.navigateToRange(target.startOffset(), target.endOffset());
      return;
    }
    ft.navigateToLine(target.oneBasedLine());
  }

  private void openPuppeteerLauncherTarget(PuppeteerLauncherPanel.OpenTarget target) {
    if (target == null || target.file() == null) return;
    File file = target.file();
    openFile(file);
    if (target.oneBasedLine() <= 0) return;
    Platform.runLater(() -> {
      if (filesTabs == null) return;
      for (Tab t : filesTabs.getTabs()) {
        if (t.getUserData() instanceof File ff && ff.equals(file) && t.getContent() instanceof FileEditorTab ft) {
          ft.navigateToLine(target.oneBasedLine());
          break;
        }
      }
    });
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
    if (sourceTab == null) return;
    // Prevent the embedded tab preview from leaking BGM while fullscreen preview is open.
    try {
      sourceTab.stopPreviewAudio();
    } catch (Exception ignored) {
    }

    // Create a new fullscreen stage with VN preview
    javafx.stage.Stage fullscreenStage = new javafx.stage.Stage();
    fullscreenStage.setTitle("VN Preview - " + (sourceTab.getFile() != null ? sourceTab.getFile().getName() : "Untitled"));
    
    // Create a new VnPreviewView for fullscreen
    com.jvn.editor.ui.VnPreviewView fullscreenPreview = new com.jvn.editor.ui.VnPreviewView();
    File previewRoot = resolveVnsProjectRoot(sourceTab.getFile());
    if (previewRoot != null) fullscreenPreview.setProjectRoot(previewRoot);
    
    // Copy the scenario from the source tab
    try {
      String code = null;
      var editorNode = sourceTab.getEditorNode();
      if (editorNode instanceof com.jvn.editor.ui.VnsCodeEditor vnsEditor) {
        code = vnsEditor.getText();
      }
      if (code != null && !code.isBlank()) {
        com.jvn.core.vn.script.VnScriptParser parser = new com.jvn.core.vn.script.VnScriptParser();
        byte[] bytes = code.getBytes(StandardCharsets.UTF_8);
        String sourceName = resolveVnsSourceName(sourceTab.getFile(), previewRoot);
        com.jvn.core.vn.VnScenario scenario;
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
          scenario = parser.parse(in, sourceName, includePath -> openVnsIncludeForEditor(sourceTab, includePath));
        }
        fullscreenPreview.setSourceScriptName(sourceName);
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

    // Ensure fullscreen preview never leaks audio/timers after close.
    final boolean[] cleaned = new boolean[] {false};
    Runnable cleanup = () -> {
      if (cleaned[0]) return;
      cleaned[0] = true;
      try {
        timer.stop();
      } catch (Exception ignored) {
      }
      try {
        fullscreenPreview.stopAudio();
      } catch (Exception ignored) {
      }
      try {
        fullscreenPreview.dispose();
      } catch (Exception ignored) {
      }
      try {
        sourceTab.stopPreviewAudio();
      } catch (Exception ignored) {
      }
    };
    fullscreenStage.setOnCloseRequest(e -> cleanup.run());
    fullscreenStage.setOnHidden(e -> cleanup.run());
    
    timer.start();
    fullscreenStage.show();
    fullscreenPreview.requestFocus();
  }

  private java.io.InputStream openVnsIncludeForEditor(FileEditorTab tab, String includePath) throws java.io.IOException {
    if (tab == null) throw new java.io.IOException("Include resolver unavailable");
    File sourceFile = tab.getFile();
    if (sourceFile == null) throw new java.io.IOException("Include resolver unavailable");
    File root = resolveVnsProjectRoot(sourceFile);
    if (root == null) throw new java.io.IOException("Include resolver unavailable");

    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = resolveVnsScriptsRoot(root);
    if (scriptsRoot == null) scriptsRoot = rootPath;

    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new java.io.IOException("Include path is empty");
    }

    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(scriptsRoot.resolve(normalized.substring(1)));
    } else {
      Path sourcePath = sourceFile.toPath().toAbsolutePath().normalize();
      Path sourceParent = sourcePath.getParent();
      if (sourceParent != null) {
        candidates.add(sourceParent.resolve(normalized));
      }
      candidates.add(scriptsRoot.resolve(normalized));
    }
    candidates.add(rootPath.resolve(normalized));

    for (Path candidate : candidates) {
      Path resolved = candidate.toAbsolutePath().normalize();
      if (!resolved.startsWith(rootPath)) continue;
      if (Files.isRegularFile(resolved)) {
        return Files.newInputStream(resolved);
      }
    }
    throw new java.io.IOException("Included script not found: " + includePath);
  }

  private File resolveVnsProjectRoot(File scriptFile) {
    if (projectRoot != null) return projectRoot;
    File inferred = inferProjectRootFromScript(scriptFile);
    if (inferred != null) return inferred;
    return scriptFile == null ? null : scriptFile.getParentFile();
  }

  private Path resolveVnsScriptsRoot(File root) {
    if (root == null) return null;
    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = rootPath.resolve("scripts").normalize();
    if (Files.isDirectory(scriptsRoot)) return scriptsRoot;
    return rootPath;
  }

  private String resolveVnsSourceName(File sourceFile, File root) {
    if (sourceFile == null) return "<editor>";
    Path filePath = sourceFile.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = resolveVnsScriptsRoot(root);
    if (scriptsRoot != null && filePath.startsWith(scriptsRoot)) {
      return scriptsRoot.relativize(filePath).toString().replace('\\', '/');
    }
    if (root != null) {
      Path rootPath = root.toPath().toAbsolutePath().normalize();
      if (filePath.startsWith(rootPath)) {
        return rootPath.relativize(filePath).toString().replace('\\', '/');
      }
    }
    return sourceFile.getName();
  }

  private File inferProjectRootFromScript(File scriptFile) {
    if (scriptFile == null) return null;
    Path current = scriptFile.toPath().toAbsolutePath().normalize().getParent();
    while (current != null) {
      Path name = current.getFileName();
      if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
        Path parent = current.getParent();
        return (parent != null ? parent : current).toFile();
      }
      current = current.getParent();
    }
    return null;
  }

  private void installAddTabBehavior(TabPane pane, Tab addTab, Runnable onAddRequested) {
    if (pane == null || addTab == null || onAddRequested == null) return;
    pane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
      if (e.getButton() != MouseButton.PRIMARY) return;
      if (!isAddTabHeaderClick(e.getTarget(), addTab)) return;
      Tab selected = pane.getSelectionModel().getSelectedItem();
      triggerAddTabChooser(pane, addTab, selected, onAddRequested);
      e.consume();
    });
  }

  private void triggerAddTabChooser(TabPane pane, Tab addTab, Tab oldTab, Runnable onAddRequested) {
    if (pane == null || addTab == null || onAddRequested == null) return;
    Platform.runLater(() -> {
      Tab fallback = (oldTab != null && oldTab != addTab && pane.getTabs().contains(oldTab))
          ? oldTab
          : firstRegularTab(pane, addTab);
      if (fallback != null && pane.getTabs().contains(fallback)) {
        pane.getSelectionModel().select(fallback);
      }
      onAddRequested.run();
    });
  }

  private boolean isAddTabHeaderClick(Object target, Tab addTab) {
    if (!(target instanceof javafx.scene.Node node)) return false;
    String addLabel = addTab == null ? "+" : addTab.getText();
    javafx.scene.Node current = node;
    while (current != null) {
      List<String> styleClasses = current.getStyleClass();
      if (styleClasses.contains("sidebar-add-tab")) {
        return true;
      }
      if (current instanceof Labeled labeled
          && addLabel != null
          && !addLabel.isBlank()
          && addLabel.equals(labeled.getText())) {
        return true;
      }
      if (styleClasses.contains("tab-content-area")) return false;
      current = current.getParent();
    }
    return false;
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
    ensureSidebarVisible(targetPane);
    refreshOpenPanelChooserIndicators();
  }

  private Tab ensureProjectTab(TabPane targetPane) {
    if (targetPane == null || projView == null) return null;
    closePanelWindow(EditorSidebarPanel.PROJECT, true);
    if (projectRoot != null) {
      projView.setRootDirectory(projectRoot);
    }
    if (tabProject == null) {
      tabProject = new Tab("Project", projView);
      tabProject.setClosable(false);
    } else if (tabProject.getContent() != projView) {
      tabProject.setContent(projView);
    }
    attachPanelTabToPane(tabProject, targetPane);
    return tabProject;
  }

  private Tab ensureTimelineTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.TIMELINE, true);
    StoryTimelineView timeline = ensureTimelineView();
    if (targetPane == null || timeline == null) return null;
    if (tabTimeline == null) {
      tabTimeline = new Tab("Timeline", timeline);
      tabTimeline.setClosable(true);
      tabTimeline.setOnClosed(e -> {
        tabTimeline = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.TIMELINE);
      });
    } else if (tabTimeline.getContent() != timeline) {
      tabTimeline.setContent(timeline);
    }
    attachPanelTabToPane(tabTimeline, targetPane);
    return tabTimeline;
  }

  private Tab ensureHelpTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.HELP, true);
    HelpCenterView help = ensureHelpCenterView();
    if (targetPane == null || help == null) return null;
    if (tabHelp == null) {
      tabHelp = new Tab("Help", help);
      tabHelp.setClosable(true);
      tabHelp.setOnClosed(e -> {
        tabHelp = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.HELP);
      });
    } else if (tabHelp.getContent() != help) {
      tabHelp.setContent(help);
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
    closePanelWindow(EditorSidebarPanel.VNS_DIAGNOSTICS, true);
    VnsDiagnosticsView diagnostics = ensureVnsDiagnosticsView();
    if (targetPane == null || diagnostics == null) return null;
    if (tabVnsDiagnostics == null) {
      tabVnsDiagnostics = new Tab("VNS Diagnostics", diagnostics);
      tabVnsDiagnostics.setClosable(true);
      tabVnsDiagnostics.setOnClosed(e -> {
        tabVnsDiagnostics = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.VNS_DIAGNOSTICS);
      });
    } else if (tabVnsDiagnostics.getContent() != diagnostics) {
      tabVnsDiagnostics.setContent(diagnostics);
    }
    attachPanelTabToPane(tabVnsDiagnostics, targetPane);
    return tabVnsDiagnostics;
  }

  private Tab ensureVnsFlowMapTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.LABEL_FLOW, true);
    VnsFlowMapView flowMap = ensureVnsFlowMapView();
    if (targetPane == null || flowMap == null) return null;
    if (tabVnsFlowMap == null) {
      tabVnsFlowMap = new Tab("Label Flow", flowMap);
      tabVnsFlowMap.setClosable(true);
      tabVnsFlowMap.setOnClosed(e -> {
        tabVnsFlowMap = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.LABEL_FLOW);
      });
    } else if (tabVnsFlowMap.getContent() != flowMap) {
      tabVnsFlowMap.setContent(flowMap);
    }
    attachPanelTabToPane(tabVnsFlowMap, targetPane);
    return tabVnsFlowMap;
  }

  private Tab ensureAssetBrowserTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.ASSETS, true);
    AssetBrowserView assets = ensureAssetBrowserView();
    if (targetPane == null || assets == null) return null;
    if (tabAssetBrowser == null) {
      tabAssetBrowser = new Tab("Assets", assets);
      tabAssetBrowser.setClosable(true);
      tabAssetBrowser.setOnClosed(e -> {
        tabAssetBrowser = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.ASSETS);
      });
    } else if (tabAssetBrowser.getContent() != assets) {
      tabAssetBrowser.setContent(assets);
    }
    attachPanelTabToPane(tabAssetBrowser, targetPane);
    return tabAssetBrowser;
  }

  private Tab ensureVersionControlTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.VERSION_CONTROL, true);
    VersionControlView vcs = ensureVersionControlView();
    if (targetPane == null || vcs == null) return null;
    if (tabVersionControl == null) {
      tabVersionControl = new Tab("Version Control", vcs);
      tabVersionControl.setClosable(true);
      tabVersionControl.setOnClosed(e -> {
        tabVersionControl = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.VERSION_CONTROL);
      });
    } else if (tabVersionControl.getContent() != vcs) {
      tabVersionControl.setContent(vcs);
    }
    attachPanelTabToPane(tabVersionControl, targetPane);
    return tabVersionControl;
  }

  private Tab ensureLayoutLauncherTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.LAYOUT_LAUNCHER, true);
    LayoutEditorLauncherView launcher = ensureLayoutEditorLauncherView();
    if (targetPane == null || launcher == null) return null;
    if (tabLayoutLauncher == null) {
      tabLayoutLauncher = new Tab("Layout Launcher", launcher);
      tabLayoutLauncher.setClosable(true);
      tabLayoutLauncher.setOnClosed(e -> {
        tabLayoutLauncher = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.LAYOUT_LAUNCHER);
      });
    } else if (tabLayoutLauncher.getContent() != launcher) {
      tabLayoutLauncher.setContent(launcher);
    }
    attachPanelTabToPane(tabLayoutLauncher, targetPane);
    return tabLayoutLauncher;
  }

  private Tab ensurePhoneAssetsToolTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.PHONE_ASSETS, true);
    PhoneAssetsToolView phoneAssets = ensurePhoneAssetsToolView();
    if (targetPane == null || phoneAssets == null) return null;
    if (tabPhoneAssetsTool == null) {
      tabPhoneAssetsTool = new Tab("Phone Assets", phoneAssets);
      tabPhoneAssetsTool.setClosable(true);
      tabPhoneAssetsTool.setOnClosed(e -> {
        tabPhoneAssetsTool = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.PHONE_ASSETS);
      });
    } else if (tabPhoneAssetsTool.getContent() != phoneAssets) {
      tabPhoneAssetsTool.setContent(phoneAssets);
    }
    attachPanelTabToPane(tabPhoneAssetsTool, targetPane);
    return tabPhoneAssetsTool;
  }

  private Tab ensureStoryboardOverlayTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.STORYBOARD_OVERLAY, true);
    StoryboardOverlayView storyboardOverlay = ensureStoryboardOverlayView();
    if (targetPane == null || storyboardOverlay == null) return null;
    if (tabStoryboardOverlay == null) {
      tabStoryboardOverlay = new Tab("Storyboard Overlay", storyboardOverlay);
      tabStoryboardOverlay.setClosable(true);
      tabStoryboardOverlay.setOnClosed(e -> {
        tabStoryboardOverlay = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.STORYBOARD_OVERLAY);
      });
    } else if (tabStoryboardOverlay.getContent() != storyboardOverlay) {
      tabStoryboardOverlay.setContent(storyboardOverlay);
    }
    attachPanelTabToPane(tabStoryboardOverlay, targetPane);
    return tabStoryboardOverlay;
  }

  private Tab ensureLayeredImageVisualizerTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.LAYERED_IMAGES, true);
    LayeredImageVisualizerView layered = ensureLayeredImageVisualizerView();
    if (targetPane == null || layered == null) return null;
    if (tabLayeredImageVisualizer == null) {
      tabLayeredImageVisualizer = new Tab("Layered Images", layered);
      tabLayeredImageVisualizer.setClosable(true);
      tabLayeredImageVisualizer.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == layered) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabLayeredImageVisualizer = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.LAYERED_IMAGES);
      });
    } else if (tabLayeredImageVisualizer.getContent() != layered) {
      tabLayeredImageVisualizer.setContent(layered);
    }
    attachPanelTabToPane(tabLayeredImageVisualizer, targetPane);
    return tabLayeredImageVisualizer;
  }

  private Tab ensureImageAttributesToolTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.IMAGE_ATTRIBUTES, true);
    ImageAttributesToolView attributes = ensureImageAttributesToolView();
    if (targetPane == null || attributes == null) return null;
    if (tabImageAttributesTool == null) {
      tabImageAttributesTool = new Tab("Image Attributes", attributes);
      tabImageAttributesTool.setClosable(true);
      tabImageAttributesTool.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == attributes) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabImageAttributesTool = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.IMAGE_ATTRIBUTES);
      });
    } else if (tabImageAttributesTool.getContent() != attributes) {
      tabImageAttributesTool.setContent(attributes);
    }
    attachPanelTabToPane(tabImageAttributesTool, targetPane);
    return tabImageAttributesTool;
  }

  private Tab ensureImageTintToolTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.IMAGE_TINT, true);
    ImageTintToolView tint = ensureImageTintToolView();
    if (targetPane == null || tint == null) return null;
    if (tabImageTintTool == null) {
      tabImageTintTool = new Tab("Image Tint", tint);
      tabImageTintTool.setClosable(true);
      tabImageTintTool.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == tint) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabImageTintTool = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.IMAGE_TINT);
      });
    } else if (tabImageTintTool.getContent() != tint) {
      tabImageTintTool.setContent(tint);
    }
    attachPanelTabToPane(tabImageTintTool, targetPane);
    return tabImageTintTool;
  }

  private Tab ensureMenuFlowTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.MENU_FLOW, true);
    MenuFlowEditorView menuFlow = ensureMenuFlowEditorView();
    if (targetPane == null || menuFlow == null) return null;
    if (tabMenuFlow == null) {
      tabMenuFlow = new Tab("Menu Flow", menuFlow);
      tabMenuFlow.setClosable(true);
      tabMenuFlow.setOnClosed(e -> {
        tabMenuFlow = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.MENU_FLOW);
      });
    } else if (tabMenuFlow.getContent() != menuFlow) {
      tabMenuFlow.setContent(menuFlow);
    }
    attachPanelTabToPane(tabMenuFlow, targetPane);
    return tabMenuFlow;
  }

  private Tab ensureScriptEditorLauncherTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.SCRIPT_EDITOR, true);
    ScriptEditorLauncherView launcher = ensureScriptEditorLauncherView();
    if (targetPane == null || launcher == null) return null;
    if (tabScriptEditorLauncher == null) {
      tabScriptEditorLauncher = new Tab("Text Editor", launcher);
      tabScriptEditorLauncher.setClosable(true);
      tabScriptEditorLauncher.setOnClosed(e -> {
        tabScriptEditorLauncher = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.SCRIPT_EDITOR);
      });
    } else if (tabScriptEditorLauncher.getContent() != launcher) {
      tabScriptEditorLauncher.setContent(launcher);
    }
    attachPanelTabToPane(tabScriptEditorLauncher, targetPane);
    return tabScriptEditorLauncher;
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

  private void addChooserActionRow(TabPane chooserPane,
      javafx.scene.layout.VBox actions, EditorSidebarPanel panel,
      EditorPanelPlacement targetPlacement, String panelName, String iconClass,
      Runnable embedAction, Runnable windowAction, Runnable removeAction) {
    if (actions == null || panelName == null) return;
    if (panel != null && !editorPreferences.isVisibleInChooser(panel)) return;

    Region panelIcon = icon("icon", iconClass);

    Label label = new Label(panelName);
    label.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(label, Priority.ALWAYS);
    label.getStyleClass().add("panel-chooser-title");
    label.setWrapText(false);

    Label placementBadge = new Label();
    placementBadge.getStyleClass().add("panel-chooser-placement-badge");
    placementBadge.setMinWidth(10);
    placementBadge.setMaxWidth(10);
    placementBadge.setAlignment(Pos.CENTER);
    Tooltip placementTooltip = new Tooltip();
    placementBadge.setTooltip(placementTooltip);
    Region memoryIcon = CssIcon.memory("#9a9a9a");
    memoryIcon.getStyleClass().add("panel-chooser-memory-icon");
    Label memoryIndicator = new Label("●");
    memoryIndicator.getStyleClass().add("panel-chooser-memory-indicator");
    Tooltip memoryTooltip = new Tooltip();
    memoryIndicator.setTooltip(memoryTooltip);
    Tooltip.install(memoryIcon, memoryTooltip);
    HBox memoryGroup = new HBox(3, memoryIcon, memoryIndicator);
    memoryGroup.getStyleClass().add("panel-chooser-memory-group");
    memoryGroup.setAlignment(Pos.CENTER_LEFT);

    Button dockBtn = new Button();
    dockBtn.setGraphic(CssIcon.plus("#d6dbe5"));
    dockBtn.setTooltip(new Tooltip("Add to sidebar"));
    dockBtn.setMinSize(26, 26); dockBtn.setPrefSize(26, 26); dockBtn.setMaxSize(26, 26);
    dockBtn.setFocusTraversable(false);
    dockBtn.getStyleClass().add("panel-chooser-icon-btn");
    if (embedAction != null) {
      dockBtn.setOnAction(e -> embedAction.run());
    } else {
      dockBtn.setDisable(true);
    }

    Button popOutBtn = new Button();
    popOutBtn.setGraphic(CssIcon.popOut("#d6dbe5"));
    popOutBtn.setTooltip(new Tooltip("Open in separate window"));
    popOutBtn.setMinSize(26, 26); popOutBtn.setPrefSize(26, 26); popOutBtn.setMaxSize(26, 26);
    popOutBtn.setFocusTraversable(false);
    popOutBtn.getStyleClass().add("panel-chooser-icon-btn");
    if (windowAction != null) {
      popOutBtn.setOnAction(e -> windowAction.run());
    } else {
      popOutBtn.setDisable(true);
    }

    Button removeBtn = new Button();
    removeBtn.setGraphic(CssIcon.minus("#f38ba8"));
    removeBtn.setTooltip(new Tooltip("Remove from sidebars"));
    removeBtn.setMinSize(26, 26); removeBtn.setPrefSize(26, 26); removeBtn.setMaxSize(26, 26);
    removeBtn.setFocusTraversable(false);
    removeBtn.getStyleClass().add("panel-chooser-icon-btn");
    if (removeAction != null) {
      removeBtn.setOnAction(e -> removeAction.run());
    } else {
      removeBtn.setDisable(true);
    }

    Runnable refreshState = () -> {
      updateChooserMemoryIndicator(memoryIndicator, panel, memoryTooltip);
      if (panel == null) {
        removeBtn.setManaged(false);
        removeBtn.setVisible(false);
        dockBtn.setGraphic(CssIcon.plus("#d6dbe5"));
        dockBtn.setTooltip(new Tooltip("Open in this panel"));
        placementBadge.setManaged(false);
        placementBadge.setVisible(false);
        memoryGroup.setManaged(true);
        memoryGroup.setVisible(true);
        return;
      }

      EditorPanelPlacement placement = editorPreferences.getPlacement(panel);
      boolean attached = isPanelAttached(panel);
      placementBadge.setManaged(true);
      placementBadge.setVisible(true);
      updateChooserPlacementBadge(placementBadge, placement, attached, placementTooltip);
      if (placement == EditorPanelPlacement.HIDDEN || !attached) {
        dockBtn.setGraphic(CssIcon.plus("#d6dbe5"));
        dockBtn.setTooltip(new Tooltip(
            "Add to " + (targetPlacement == EditorPanelPlacement.RIGHT ? "right" : "left") + " sidebar"));
      } else if (placement != targetPlacement) {
        dockBtn.setGraphic(CssIcon.dock("#d6dbe5"));
        dockBtn.setTooltip(new Tooltip(
            "Move to " + (targetPlacement == EditorPanelPlacement.RIGHT ? "right" : "left") + " sidebar"));
      } else {
        dockBtn.setGraphic(CssIcon.dock("#d6dbe5"));
        dockBtn.setTooltip(new Tooltip("Select panel"));
      }
      removeBtn.setManaged(true);
      removeBtn.setVisible(true);
      removeBtn.setDisable(!attached && placement == EditorPanelPlacement.HIDDEN);
    };

    final Runnable dockAction;
    if (embedAction != null) {
      dockAction = () -> {
        embedAction.run();
        dismissPanelChooser(chooserPane);
        refreshState.run();
      };
      dockBtn.setOnAction(e -> dockAction.run());
    } else {
      dockAction = null;
    }
    if (windowAction != null) {
      popOutBtn.setOnAction(e -> {
        windowAction.run();
        dismissPanelChooser(chooserPane);
        refreshState.run();
      });
    }
    if (removeAction != null) {
      removeBtn.setOnAction(e -> {
        removeAction.run();
        dismissPanelChooser(chooserPane);
        refreshState.run();
      });
    }

    HBox row = new HBox(6, panelIcon, label, memoryGroup, placementBadge, dockBtn, popOutBtn, removeBtn);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new javafx.geometry.Insets(4, 6, 4, 6));
    row.getStyleClass().add("panel-chooser-row");
    String filterKey = panelName.toLowerCase(Locale.ROOT)
        + (panel != null ? " " + panel.key().toLowerCase(Locale.ROOT) : "");
    row.getProperties().put("chooserFilterKey", filterKey);
    row.setOnMouseClicked(e -> {
      if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() < 2) return;
      if (isInsideChooserIconButton(e.getTarget())) return;
      if (dockAction == null) return;
      dockAction.run();
      e.consume();
    });
    row.getProperties().put(PANEL_CHOOSER_REFRESH_KEY, refreshState);
    refreshState.run();
    actions.getChildren().add(row);
  }

  private void dismissPanelChooser(TabPane pane) {
    if (pane == null) return;
    Tab chooser = null;
    Tab selected = pane.getSelectionModel().getSelectedItem();
    if (selected != null && PANEL_CHOOSER_TAB_ROLE.equals(selected.getProperties().get(PANEL_CHOOSER_TAB_ROLE))) {
      chooser = selected;
    }
    if (chooser == null) {
      chooser = findPanelChooserTab(pane, pane == leftTabs);
    }
    if (chooser == null) return;
    Tab fallback = firstRegularTab(pane, getAddTabForPane(pane));
    pane.getTabs().remove(chooser);
    if (fallback != null && pane.getTabs().contains(fallback)) {
      pane.getSelectionModel().select(fallback);
    }
  }

  private boolean isInsideChooserIconButton(Object target) {
    if (!(target instanceof javafx.scene.Node node)) return false;
    javafx.scene.Node current = node;
    while (current != null) {
      if (current.getStyleClass().contains("panel-chooser-icon-btn")) return true;
      current = current.getParent();
    }
    return false;
  }

  private void updateChooserPlacementBadge(
      Label badge, EditorPanelPlacement placement, boolean attached, Tooltip tooltip) {
    if (badge == null) return;
    badge.getStyleClass().removeAll(
        "panel-chooser-placement-hidden",
        "panel-chooser-placement-left",
        "panel-chooser-placement-right");
    badge.setText("");
    if (!attached) {
      badge.getStyleClass().add("panel-chooser-placement-hidden");
      if (tooltip != null) tooltip.setText("Detached from sidebars");
      return;
    }
    if (placement == EditorPanelPlacement.LEFT) {
      badge.getStyleClass().add("panel-chooser-placement-left");
      if (tooltip != null) tooltip.setText("Attached to left sidebar");
      return;
    }
    if (placement == EditorPanelPlacement.RIGHT) {
      badge.getStyleClass().add("panel-chooser-placement-right");
      if (tooltip != null) tooltip.setText("Attached to right sidebar");
      return;
    }
    badge.getStyleClass().add("panel-chooser-placement-hidden");
    if (tooltip != null) tooltip.setText("Hidden by default");
  }

  private void updateChooserMemoryIndicator(
      Label indicator, EditorSidebarPanel panel, Tooltip tooltip) {
    if (indicator == null) return;
    boolean loaded = isPanelLoadedInMemory(panel);
    indicator.getStyleClass().removeAll(
        "panel-chooser-memory-loaded",
        "panel-chooser-memory-unloaded");
    indicator.getStyleClass().add(
        loaded ? "panel-chooser-memory-loaded" : "panel-chooser-memory-unloaded");
    if (tooltip != null) {
      tooltip.setText(loaded ? "Loaded in memory" : "Not loaded in memory");
    }
  }

  private void installPanelChooserFilter(TextField filterField, VBox actions) {
    if (filterField == null || actions == null) return;
    Runnable apply = () -> {
      String needle = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
      for (javafx.scene.Node child : actions.getChildren()) {
        if (!(child instanceof HBox row)) continue;
        Object key = row.getProperties().get("chooserFilterKey");
        String searchable = key == null ? "" : key.toString();
        boolean visible = needle.isBlank() || searchable.contains(needle);
        row.setManaged(visible);
        row.setVisible(visible);
      }
    };
    filterField.textProperty().addListener((obs, oldText, newText) -> apply.run());
    filterField.setOnAction(e -> {
      for (javafx.scene.Node child : actions.getChildren()) {
        if (!(child instanceof HBox row) || !row.isVisible()) continue;
        row.fireEvent(new MouseEvent(
            MouseEvent.MOUSE_CLICKED,
            0, 0, 0, 0,
            MouseButton.PRIMARY,
            2,
            false, false, false, false,
            true, false, false, true, false, false,
            null));
        break;
      }
    });
    apply.run();
  }

  private Tab findPanelChooserTab(TabPane pane, boolean leftSide) {
    if (pane == null) return null;
    String side = leftSide ? "left" : "right";
    for (Tab tab : pane.getTabs()) {
      Object role = tab.getProperties().get(PANEL_CHOOSER_TAB_ROLE);
      Object tag = tab.getProperties().get("chooserSide");
      if (PANEL_CHOOSER_TAB_ROLE.equals(role) && side.equals(tag)) {
        return tab;
      }
    }
    return null;
  }

  private void refreshOpenPanelChooserIndicators() {
    refreshOpenPanelChooserIndicators(leftTabs);
    refreshOpenPanelChooserIndicators(rightTabs);
  }

  private void refreshOpenPanelChooserIndicators(TabPane pane) {
    if (pane == null) return;
    for (Tab tab : pane.getTabs()) {
      if (!PANEL_CHOOSER_TAB_ROLE.equals(tab.getProperties().get(PANEL_CHOOSER_TAB_ROLE))) {
        continue;
      }
      refreshChooserNode(tab.getContent());
    }
  }

  private void refreshChooserNode(javafx.scene.Node node) {
    if (node == null) return;
    Object refresher = node.getProperties().get(PANEL_CHOOSER_REFRESH_KEY);
    if (refresher instanceof Runnable runnable) {
      runnable.run();
    }
    if (node instanceof javafx.scene.Parent parent) {
      for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
        refreshChooserNode(child);
      }
    }
  }

  private void launchPanelAsWindow(
      String title,
      javafx.scene.Parent content,
      double width,
      double height,
      EditorSidebarPanel panel) {
    if (content == null) return;
    if (panel != null) {
      Stage existing = panelWindows.get(panel);
      if (existing != null) {
        if (existing.isShowing()) {
          existing.toFront();
          existing.requestFocus();
          return;
        }
        panelWindows.remove(panel);
      }
    } else if (content == editorSettingsView && editorSettingsWindow != null) {
      if (editorSettingsWindow.isShowing()) {
        editorSettingsWindow.toFront();
        editorSettingsWindow.requestFocus();
        return;
      }
      editorSettingsWindow = null;
    }
    // Detach from any current parent (Tab or other container)
    if (content.getParent() != null) {
      javafx.scene.Parent parent = content.getParent();
      if (parent instanceof javafx.scene.layout.Pane pane) {
        pane.getChildren().remove(content);
      }
    }
    // Remove from any sidebar tab
    detachFromSidebarTab(content);

    Stage windowStage = new Stage();
    windowStage.setTitle(title != null ? title : "Utility");
    javafx.scene.layout.BorderPane wrapper = new javafx.scene.layout.BorderPane(content);
    Scene windowScene = new Scene(wrapper, width, height);
    try {
      String css = EditorApp.class.getResource("/com/jvn/editor/editor.css").toExternalForm();
      windowScene.getStylesheets().add(css);
    } catch (Exception ignore) {}
    windowStage.setScene(windowScene);
    if (panel != null) {
      panelWindows.put(panel, windowStage);
    } else if (content == editorSettingsView) {
      editorSettingsWindow = windowStage;
    }
    windowStage.setOnHidden(e -> {
      boolean suppressUnload = Boolean.TRUE.equals(
          windowStage.getProperties().remove(PANEL_WINDOW_SUPPRESS_UNLOAD_KEY));
      if (panel != null) {
        panelWindows.remove(panel, windowStage);
        if (!suppressUnload) {
          releaseSidebarPanelIfUnused(panel);
        }
      } else if (content == editorSettingsView) {
        if (editorSettingsWindow == windowStage) {
          editorSettingsWindow = null;
        }
        if (!suppressUnload) {
          releaseEditorSettingsIfUnused();
        }
      }
      refreshOpenPanelChooserIndicators();
    });
    applyLinuxDefaultWindowState(windowStage);
    windowStage.show();
    refreshOpenPanelChooserIndicators();
  }

  private void detachFromSidebarTab(javafx.scene.Parent content) {
    Tab[] allTabs = {
        tabProject, tabTimeline, tabHelp, tabInspector, tabVnsDiagnostics,
        tabVnsFlowMap, tabAssetBrowser, tabVersionControl, tabLayoutLauncher, tabPhoneAssetsTool,
        tabLayeredImageVisualizer, tabImageAttributesTool, tabImageTintTool,
        tabMenuFlow, tabPuppeteerLauncher, tabScriptEditorLauncher,
        tabEditorSettings
    };
    for (Tab t : allTabs) {
      if (t != null && t.getContent() == content) {
        TabPane tp = t.getTabPane();
        if (tp != null) tp.getTabs().remove(t);
        t.setContent(null);
        nullifyTab(t);
        refreshOpenPanelChooserIndicators();
        break;
      }
      // Also check if content is wrapped in a ScrollPane
      if (t != null && t.getContent() instanceof ScrollPane sp && sp.getContent() == content) {
        TabPane tp = t.getTabPane();
        if (tp != null) tp.getTabs().remove(t);
        sp.setContent(null);
        t.setContent(null);
        nullifyTab(t);
        refreshOpenPanelChooserIndicators();
        break;
      }
    }
  }

  private void nullifyTab(Tab tab) {
    if (tab == tabProject) tabProject = null;
    else if (tab == tabTimeline) tabTimeline = null;
    else if (tab == tabHelp) tabHelp = null;
    else if (tab == tabInspector) tabInspector = null;
    else if (tab == tabVnsDiagnostics) tabVnsDiagnostics = null;
    else if (tab == tabVnsFlowMap) tabVnsFlowMap = null;
    else if (tab == tabAssetBrowser) tabAssetBrowser = null;
    else if (tab == tabVersionControl) tabVersionControl = null;
    else if (tab == tabLayoutLauncher) tabLayoutLauncher = null;
    else if (tab == tabPhoneAssetsTool) tabPhoneAssetsTool = null;
    else if (tab == tabLayeredImageVisualizer) tabLayeredImageVisualizer = null;
    else if (tab == tabImageAttributesTool) tabImageAttributesTool = null;
    else if (tab == tabImageTintTool) tabImageTintTool = null;
    else if (tab == tabMenuFlow) tabMenuFlow = null;
    else if (tab == tabPuppeteerLauncher) tabPuppeteerLauncher = null;
    else if (tab == tabScriptEditorLauncher) tabScriptEditorLauncher = null;
    else if (tab == tabEditorSettings) tabEditorSettings = null;
  }

  private void clearTabContent(Tab tab) {
    if (tab == null) return;
    if (tab.getContent() instanceof ScrollPane sp) {
      sp.setContent(null);
    }
    tab.setContent(null);
  }

  private void releaseSidebarPanelTab(EditorSidebarPanel panel) {
    if (panel == null) return;
    Tab tab = configuredPanelTab(panel);
    if (tab == null) return;
    TabPane pane = tab.getTabPane();
    if (pane != null) {
      pane.getTabs().remove(tab);
    }
    clearTabContent(tab);
    nullifyTab(tab);
  }

  private void closePanelWindow(EditorSidebarPanel panel, boolean suppressUnload) {
    if (panel == null) return;
    Stage stage = panelWindows.get(panel);
    if (stage == null) return;
    if (suppressUnload) {
      stage.getProperties().put(PANEL_WINDOW_SUPPRESS_UNLOAD_KEY, Boolean.TRUE);
    }
    if (stage.isShowing()) {
      stage.close();
    } else {
      panelWindows.remove(panel);
      refreshOpenPanelChooserIndicators();
    }
  }

  private void closeAllFloatingPanelWindows() {
    for (EditorSidebarPanel panel : new ArrayList<>(panelWindows.keySet())) {
      closePanelWindow(panel, true);
    }
    closeEditorSettingsWindow(true);
  }

  private void closeEditorSettingsWindow(boolean suppressUnload) {
    if (editorSettingsWindow == null) return;
    if (suppressUnload) {
      editorSettingsWindow.getProperties().put(PANEL_WINDOW_SUPPRESS_UNLOAD_KEY, Boolean.TRUE);
    }
    if (editorSettingsWindow.isShowing()) {
      editorSettingsWindow.close();
    } else {
      editorSettingsWindow = null;
      refreshOpenPanelChooserIndicators();
    }
  }

  private boolean isPanelLoadedInMemory(EditorSidebarPanel panel) {
    if (panel == null) return editorSettingsView != null;
    return switch (panel) {
      case PROJECT -> projView != null;
      case TIMELINE -> timelineView != null;
      case INSPECTOR -> inspectorView != null;
      case VNS_DIAGNOSTICS -> vnsDiagnosticsView != null;
      case LABEL_FLOW -> vnsFlowMapView != null;
      case ASSETS -> assetBrowserView != null;
      case LAYOUT_LAUNCHER -> layoutEditorLauncherView != null;
      case PHONE_ASSETS -> phoneAssetsToolView != null;
      case STORYBOARD_OVERLAY -> storyboardOverlayView != null;
      case LAYERED_IMAGES -> layeredImageVisualizerView != null;
      case IMAGE_ATTRIBUTES -> imageAttributesToolView != null;
      case IMAGE_TINT -> imageTintToolView != null;
      case MENU_FLOW -> menuFlowEditorView != null;
      case VERSION_CONTROL -> versionControlView != null;
      case HELP -> helpCenterView != null;
      case PUPPETEER_LAUNCHER -> puppeteerLauncherPanel != null;
      case SCRIPT_EDITOR -> scriptEditorLauncherView != null;
    };
  }

  private void releaseSidebarPanelIfUnused(EditorSidebarPanel panel) {
    if (panel == null || isPanelAttached(panel)) return;
    Stage window = panelWindows.get(panel);
    if (window != null && window.isShowing()) return;
    releaseSidebarPanel(panel);
    refreshOpenPanelChooserIndicators();
  }

  private void releaseSidebarPanel(EditorSidebarPanel panel) {
    if (panel == null) return;
    releaseSidebarPanelTab(panel);
    panelWindows.remove(panel);
    switch (panel) {
      case PROJECT -> {
      }
      case TIMELINE -> {
        if (timelineView != null) {
          timelineView.setProjectRoot(null);
        }
        timelineView = null;
      }
      case INSPECTOR -> {
      }
      case VNS_DIAGNOSTICS -> vnsDiagnosticsView = null;
      case LABEL_FLOW -> vnsFlowMapView = null;
      case ASSETS -> {
        if (assetBrowserView != null) {
          assetBrowserView.setProjectRoot(null);
        }
        assetBrowserView = null;
      }
      case LAYOUT_LAUNCHER -> {
        if (layoutEditorLauncherView != null) {
          layoutEditorLauncherView.setProjectRoot(null);
        }
        layoutEditorLauncherView = null;
      }
      case PHONE_ASSETS -> {
        if (phoneAssetsToolView != null) {
          phoneAssetsToolView.dispose();
        }
        phoneAssetsToolView = null;
      }
      case STORYBOARD_OVERLAY -> {
        if (storyboardOverlayView != null) {
          storyboardOverlayView.dispose();
        }
        storyboardOverlayView = null;
      }
      case LAYERED_IMAGES -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == layeredImageVisualizerView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        if (layeredImageVisualizerView != null) {
          layeredImageVisualizerView.dispose();
        }
        layeredImageVisualizerView = null;
      }
      case IMAGE_ATTRIBUTES -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == imageAttributesToolView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        if (imageAttributesToolView != null) {
          imageAttributesToolView.dispose();
        }
        imageAttributesToolView = null;
      }
      case IMAGE_TINT -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == imageTintToolView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        if (imageTintToolView != null) {
          imageTintToolView.dispose();
        }
        imageTintToolView = null;
      }
      case MENU_FLOW -> {
        if (menuFlowEditorView != null) {
          menuFlowEditorView.setProjectRoot(null);
        }
        menuFlowEditorView = null;
      }
      case VERSION_CONTROL -> {
        if (versionControlView != null) {
          versionControlView.dispose();
        }
        versionControlView = null;
      }
      case HELP -> {
        if (helpCenterView != null) {
          helpCenterView.setProjectRoot(null);
        }
        helpCenterView = null;
      }
      case PUPPETEER_LAUNCHER -> {
        if (puppeteerLauncherPanel != null) {
          puppeteerLauncherPanel.setProjectRoot(null);
        }
        puppeteerLauncherPanel = null;
      }
      case SCRIPT_EDITOR -> {
        if (scriptEditorLauncherView != null) {
          scriptEditorLauncherView.setProjectRoot(null);
          scriptEditorLauncherView.setWorkspaceRoot(null);
        }
        scriptEditorLauncherView = null;
      }
    }
    refreshOpenPanelChooserIndicators();
  }

  private void releaseEditorSettingsIfUnused() {
    if (tabEditorSettings != null && tabEditorSettings.getTabPane() != null) return;
    if (editorSettingsWindow != null && editorSettingsWindow.isShowing()) return;
    clearTabContent(tabEditorSettings);
    tabEditorSettings = null;
    editorSettingsView = null;
    refreshOpenPanelChooserIndicators();
  }

  private void openPanelChooserTab(TabPane pane, boolean leftSide) {
    if (pane == null) return;
    Tab addTab = leftSide ? tabLeftAdd : tabRightAdd;
    if (addTab == null) return;
    Tab existing = findPanelChooserTab(pane, leftSide);
    if (existing != null) {
      pane.getTabs().remove(existing);
    }
    EditorPanelPlacement targetPlacement =
        leftSide ? EditorPanelPlacement.LEFT : EditorPanelPlacement.RIGHT;

    String title = leftSide ? "Add Left Panel" : "Add Right Panel";
    String details = leftSide
      ? "Choose a panel to add on the left. This keeps the workspace focused by default."
      : "Choose a panel to add on the right. Add only the tools you need for the current workflow.";

    javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
    root.setPadding(new javafx.geometry.Insets(12));
    root.setFillWidth(true);
    Label heading = new Label(title);
    heading.getStyleClass().add("panel-chooser-heading");
    Label info = new Label(details);
    info.setWrapText(true);
    info.getStyleClass().add("panel-chooser-copy");
    TextField filter = new TextField();
    filter.setPromptText("Filter panels...");
    filter.getStyleClass().add("panel-chooser-filter");
    javafx.scene.layout.VBox actions = new javafx.scene.layout.VBox(4);
    addChooserActionRow(pane, actions, EditorSidebarPanel.PROJECT, targetPlacement, "Project", "icon-panel-project", () -> {
      rememberPanelPlacement(EditorSidebarPanel.PROJECT, targetPlacement);
      Tab t = ensureProjectTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Project", projView, 600, 700, EditorSidebarPanel.PROJECT), () -> {
      rememberPanelPlacement(EditorSidebarPanel.PROJECT, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.TIMELINE, targetPlacement, "Timeline", "icon-panel-timeline", () -> {
      rememberPanelPlacement(EditorSidebarPanel.TIMELINE, targetPlacement);
      Tab t = ensureTimelineTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Story Timeline", ensureTimelineView(), 600, 700, EditorSidebarPanel.TIMELINE), () -> {
      rememberPanelPlacement(EditorSidebarPanel.TIMELINE, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.VNS_DIAGNOSTICS, targetPlacement, "VNS Diagnostics", "icon-panel-diagnostics", () -> {
      rememberPanelPlacement(EditorSidebarPanel.VNS_DIAGNOSTICS, targetPlacement);
      Tab t = ensureVnsDiagnosticsTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("VNS Diagnostics", ensureVnsDiagnosticsView(), 700, 600, EditorSidebarPanel.VNS_DIAGNOSTICS), () -> {
      rememberPanelPlacement(EditorSidebarPanel.VNS_DIAGNOSTICS, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.LABEL_FLOW, targetPlacement, "Label Flow", "icon-panel-flow", () -> {
      rememberPanelPlacement(EditorSidebarPanel.LABEL_FLOW, targetPlacement);
      Tab t = ensureVnsFlowMapTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Label Flow Map", ensureVnsFlowMapView(), 700, 600, EditorSidebarPanel.LABEL_FLOW), () -> {
      rememberPanelPlacement(EditorSidebarPanel.LABEL_FLOW, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.ASSETS, targetPlacement, "Assets", "icon-panel-assets", () -> {
      rememberPanelPlacement(EditorSidebarPanel.ASSETS, targetPlacement);
      Tab t = ensureAssetBrowserTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Asset Browser", ensureAssetBrowserView(), 700, 600, EditorSidebarPanel.ASSETS), () -> {
      rememberPanelPlacement(EditorSidebarPanel.ASSETS, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.LAYOUT_LAUNCHER, targetPlacement, "Layout Launcher", "icon-panel-layouts", () -> {
      rememberPanelPlacement(EditorSidebarPanel.LAYOUT_LAUNCHER, targetPlacement);
      Tab t = ensureLayoutLauncherTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (layoutEditorLauncherView != null) layoutEditorLauncherView.refreshStatus();
    }, () -> {
      LayoutEditorLauncherView view = ensureLayoutEditorLauncherView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Layout Launcher", view, 700, 700, EditorSidebarPanel.LAYOUT_LAUNCHER);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.LAYOUT_LAUNCHER, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.PHONE_ASSETS, targetPlacement, "Phone Assets", "icon-panel-assets", () -> {
      rememberPanelPlacement(EditorSidebarPanel.PHONE_ASSETS, targetPlacement);
      Tab t = ensurePhoneAssetsToolTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> {
      launchPanelAsWindow("Phone Assets", ensurePhoneAssetsToolView(), 920, 760, EditorSidebarPanel.PHONE_ASSETS);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.PHONE_ASSETS, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.STORYBOARD_OVERLAY, targetPlacement, "Storyboard Overlay", "icon-panel-storyboard", () -> {
      rememberPanelPlacement(EditorSidebarPanel.STORYBOARD_OVERLAY, targetPlacement);
      Tab t = ensureStoryboardOverlayTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      refreshStoryboardOverlayContext(getActiveFileTab());
    }, () -> {
      StoryboardOverlayView view = ensureStoryboardOverlayView();
      refreshStoryboardOverlayContext(getActiveFileTab());
      launchPanelAsWindow("Storyboard Overlay", view, 420, 720, EditorSidebarPanel.STORYBOARD_OVERLAY);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.STORYBOARD_OVERLAY, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.LAYERED_IMAGES, targetPlacement, "Layered Image Visualizer", "icon-panel-layered", () -> {
      rememberPanelPlacement(EditorSidebarPanel.LAYERED_IMAGES, targetPlacement);
      Tab t = ensureLayeredImageVisualizerTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (layeredImageVisualizerView != null) layeredImageVisualizerView.refreshCatalog();
    }, () -> {
      LayeredImageVisualizerView view = ensureLayeredImageVisualizerView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Layered Image Visualizer", view, 900, 700, EditorSidebarPanel.LAYERED_IMAGES);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.LAYERED_IMAGES, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.IMAGE_ATTRIBUTES, targetPlacement, "Image Attributes Tool", "icon-panel-image-attributes", () -> {
      rememberPanelPlacement(EditorSidebarPanel.IMAGE_ATTRIBUTES, targetPlacement);
      Tab t = ensureImageAttributesToolTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (imageAttributesToolView != null) imageAttributesToolView.refreshCatalog();
    }, () -> {
      ImageAttributesToolView view = ensureImageAttributesToolView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Image Attributes Tool", view, 800, 650, EditorSidebarPanel.IMAGE_ATTRIBUTES);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.IMAGE_ATTRIBUTES, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.IMAGE_TINT, targetPlacement, "Scene Lighting Studio", "icon-panel-image-tint", () -> {
      rememberPanelPlacement(EditorSidebarPanel.IMAGE_TINT, targetPlacement);
      Tab t = ensureImageTintToolTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (imageTintToolView != null) imageTintToolView.refreshCatalog();
    }, () -> {
      ImageTintToolView view = ensureImageTintToolView();
      if (view != null) view.refreshCatalog();
      launchPanelAsWindow("Scene Lighting Studio", view, 800, 650, EditorSidebarPanel.IMAGE_TINT);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.IMAGE_TINT, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.MENU_FLOW, targetPlacement, "Menu Flow", "icon-panel-menuflow", () -> {
      rememberPanelPlacement(EditorSidebarPanel.MENU_FLOW, targetPlacement);
      Tab t = ensureMenuFlowTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (menuFlowEditorView != null) menuFlowEditorView.refreshStatus();
    }, () -> {
      MenuFlowEditorView view = ensureMenuFlowEditorView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Menu Flow Editor", view, 900, 650, EditorSidebarPanel.MENU_FLOW);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.MENU_FLOW, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.VERSION_CONTROL, targetPlacement, "Version Control", "icon-panel-vcs", () -> {
      rememberPanelPlacement(EditorSidebarPanel.VERSION_CONTROL, targetPlacement);
      Tab t = ensureVersionControlTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (versionControlView != null) versionControlView.refreshStatus();
    }, () -> {
      VersionControlView view = ensureVersionControlView();
      if (view != null) view.refreshStatus();
      launchPanelAsWindow("Version Control", view, 700, 600, EditorSidebarPanel.VERSION_CONTROL);
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.VERSION_CONTROL, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.HELP, targetPlacement, "Help", "icon-panel-help", () -> {
      rememberPanelPlacement(EditorSidebarPanel.HELP, targetPlacement);
      Tab t = ensureHelpTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Help Center", ensureHelpCenterView(), 700, 650, EditorSidebarPanel.HELP), () -> {
      rememberPanelPlacement(EditorSidebarPanel.HELP, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.PUPPETEER_LAUNCHER, targetPlacement, "Puppeteer Launcher", "icon-panel-puppeteer", () -> {
      rememberPanelPlacement(EditorSidebarPanel.PUPPETEER_LAUNCHER, targetPlacement);
      Tab t = ensurePuppeteerLauncherTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Puppeteer Launcher", ensurePuppeteerLauncherPanel(), 600, 500, EditorSidebarPanel.PUPPETEER_LAUNCHER), () -> {
      rememberPanelPlacement(EditorSidebarPanel.PUPPETEER_LAUNCHER, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, EditorSidebarPanel.SCRIPT_EDITOR, targetPlacement, "Text Editor", "icon-panel-flow", () -> {
      rememberPanelPlacement(EditorSidebarPanel.SCRIPT_EDITOR, targetPlacement);
      ScriptEditorLauncherView launcher = ensureScriptEditorLauncherView();
      launcher.setProjectRoot(projectRoot);
      launcher.setWorkspaceRoot(resolveWorkspaceRoot());
      Tab t = ensureScriptEditorLauncherTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> {
      ScriptEditorLauncherView launcher = ensureScriptEditorLauncherView();
      launcher.setProjectRoot(projectRoot);
      launcher.setWorkspaceRoot(resolveWorkspaceRoot());
      launcher.launchEditorWindow();
    }, () -> {
      rememberPanelPlacement(EditorSidebarPanel.SCRIPT_EDITOR, EditorPanelPlacement.HIDDEN);
      applyDefaultSidebarPreferences();
    });

    addChooserActionRow(pane, actions, null, targetPlacement, "Editor Settings", "icon-panel-help", () -> {
      Tab t = ensureEditorSettingsTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Editor Settings", ensureEditorSettingsView(), 520, 760, null), null);

    installPanelChooserFilter(filter, actions);
    VBox.setVgrow(actions, Priority.ALWAYS);
    ScrollPane chooserScroll = new ScrollPane(root);
    chooserScroll.setFitToWidth(true);
    chooserScroll.setFitToHeight(true);
    chooserScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    chooserScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    chooserScroll.setPannable(true);
    root.getChildren().addAll(heading, info, filter, new javafx.scene.control.Separator(), actions);
    Tab chooser = new Tab("New Panel", chooserScroll);
    chooser.setClosable(true);
    chooser.getStyleClass().add("panel-chooser-tab");
    chooser.getProperties().put(PANEL_CHOOSER_TAB_ROLE, PANEL_CHOOSER_TAB_ROLE);
    chooser.getProperties().put("chooserSide", leftSide ? "left" : "right");
    chooser.setOnClosed(e -> refreshOpenPanelChooserIndicators());
    int addIdx = pane.getTabs().indexOf(addTab);
    if (addIdx < 0) addIdx = pane.getTabs().size();
    pane.getTabs().add(addIdx, chooser);
    ensureSidebarVisible(pane);
    pane.getSelectionModel().select(chooser);
    Platform.runLater(() -> {
      pane.requestLayout();
      chooserScroll.requestLayout();
      root.requestLayout();
      filter.requestFocus();
    });
  }

  private void showLeftAddMenu() {
    openPanelChooserTab(leftTabs, true);
  }

  private void showRightAddMenu() {
    openPanelChooserTab(rightTabs, false);
  }

  private void selectProjectTab() {
    Tab t = (tabProject != null && tabProject.getTabPane() != null)
        ? tabProject
        : ensureProjectTab(leftTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
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

  private void selectScriptEditorLauncherTab() {
    Tab t = (tabScriptEditorLauncher != null && tabScriptEditorLauncher.getTabPane() != null)
        ? tabScriptEditorLauncher
        : ensureScriptEditorLauncherTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (scriptEditorLauncherView != null) {
      scriptEditorLauncherView.setProjectRoot(projectRoot);
      scriptEditorLauncherView.setWorkspaceRoot(resolveWorkspaceRoot());
    }
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

  private void selectVnsDiagnosticsTab() {
    Tab t = (tabVnsDiagnostics != null && tabVnsDiagnostics.getTabPane() != null)
        ? tabVnsDiagnostics
        : ensureVnsDiagnosticsTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectVnsFlowMapTab() {
    Tab t = (tabVnsFlowMap != null && tabVnsFlowMap.getTabPane() != null)
        ? tabVnsFlowMap
        : ensureVnsFlowMapTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectAssetBrowserTab() {
    Tab t = (tabAssetBrowser != null && tabAssetBrowser.getTabPane() != null)
        ? tabAssetBrowser
        : ensureAssetBrowserTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectLayoutLauncherTab() {
    Tab t = (tabLayoutLauncher != null && tabLayoutLauncher.getTabPane() != null)
        ? tabLayoutLauncher
        : ensureLayoutLauncherTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (layoutEditorLauncherView != null) layoutEditorLauncherView.refreshStatus();
  }

  private void selectPhoneAssetsToolTab() {
    Tab t = (tabPhoneAssetsTool != null && tabPhoneAssetsTool.getTabPane() != null)
        ? tabPhoneAssetsTool
        : ensurePhoneAssetsToolTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectStoryboardOverlayTab() {
    Tab t = (tabStoryboardOverlay != null && tabStoryboardOverlay.getTabPane() != null)
        ? tabStoryboardOverlay
        : ensureStoryboardOverlayTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    refreshStoryboardOverlayContext(getActiveFileTab());
  }

  private void selectLayeredImageVisualizerTab() {
    Tab t = (tabLayeredImageVisualizer != null && tabLayeredImageVisualizer.getTabPane() != null)
        ? tabLayeredImageVisualizer
        : ensureLayeredImageVisualizerTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.refreshCatalog();
  }

  private void selectImageAttributesToolTab() {
    Tab t = (tabImageAttributesTool != null && tabImageAttributesTool.getTabPane() != null)
        ? tabImageAttributesTool
        : ensureImageAttributesToolTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (imageAttributesToolView != null) imageAttributesToolView.refreshCatalog();
  }

  private void selectImageTintToolTab() {
    Tab t = (tabImageTintTool != null && tabImageTintTool.getTabPane() != null)
        ? tabImageTintTool
        : ensureImageTintToolTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
    if (imageTintToolView != null) imageTintToolView.refreshCatalog();
  }

  private void setStoryboardOverlayState(StoryboardOverlayState storyboardOverlayState) {
    this.storyboardOverlayState =
        storyboardOverlayState == null ? StoryboardOverlayState.none() : storyboardOverlayState;
    applyStoryboardOverlayToOpenTabs();
  }

  private void applyStoryboardOverlayToOpenTabs() {
    if (filesTabs == null) return;
    for (Tab tab : filesTabs.getTabs()) {
      if (tab.getContent() instanceof FileEditorTab fileTab) {
        fileTab.setStoryboardOverlay(storyboardOverlayState);
      }
    }
  }

  private void syncStoryboardOverlayProjectState() {
    if (storyboardOverlayView != null) {
      storyboardOverlayView.setProjectRoot(projectRoot);
      refreshStoryboardOverlayContext(getActiveFileTab());
    } else {
      setStoryboardOverlayState(StoryboardOverlayState.none());
    }
  }

  private void refreshStoryboardOverlayContext(FileEditorTab fileTab) {
    if (storyboardOverlayView == null) return;
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(projectRoot);
    String label;
    if (fileTab == null) {
      label = "Active preview: open a JES or VNS tab. Overlay fits " + dims.width() + "x" + dims.height() + ".";
    } else if (fileTab.getKind() == FileEditorTab.Kind.JES) {
      label = "Active preview: JES scene. Overlay fits " + dims.width() + "x" + dims.height() + ".";
    } else if (fileTab.getKind() == FileEditorTab.Kind.VNS) {
      label = "Active preview: VNS scene. Overlay fits " + dims.width() + "x" + dims.height() + ".";
    } else {
      label = "Active preview: " + fileTab.getDisplayName() + " has no JES/VNS preview.";
    }
    storyboardOverlayView.setActivePreviewLabel(label);
  }

  private void selectInspectorTab() {
    Tab t = (tabInspector != null && tabInspector.getTabPane() != null)
        ? tabInspector
        : ensureInspectorTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectPuppeteerLauncherTab() {
    Tab t = (tabPuppeteerLauncher != null && tabPuppeteerLauncher.getTabPane() != null)
        ? tabPuppeteerLauncher
        : ensurePuppeteerLauncherTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private void selectEditorSettingsTab() {
    Tab t = (tabEditorSettings != null && tabEditorSettings.getTabPane() != null)
        ? tabEditorSettings
        : ensureEditorSettingsTab(rightTabs);
    if (t != null && t.getTabPane() != null) {
      t.getTabPane().getSelectionModel().select(t);
    }
  }

  private Tab ensurePuppeteerLauncherTab(TabPane targetPane) {
    closePanelWindow(EditorSidebarPanel.PUPPETEER_LAUNCHER, true);
    PuppeteerLauncherPanel launcher = ensurePuppeteerLauncherPanel();
    if (targetPane == null || launcher == null) return null;
    if (tabPuppeteerLauncher == null) {
      tabPuppeteerLauncher = new Tab("Puppeteer Launcher", launcher);
      tabPuppeteerLauncher.setClosable(true);
      tabPuppeteerLauncher.setOnClosed(e -> {
        tabPuppeteerLauncher = null;
        releaseSidebarPanelIfUnused(EditorSidebarPanel.PUPPETEER_LAUNCHER);
      });
    } else if (tabPuppeteerLauncher.getContent() != launcher) {
      tabPuppeteerLauncher.setContent(launcher);
    }
    attachPanelTabToPane(tabPuppeteerLauncher, targetPane);
    return tabPuppeteerLauncher;
  }

  private Tab ensureEditorSettingsTab(TabPane targetPane) {
    closeEditorSettingsWindow(true);
    EditorSettingsView settingsView = ensureEditorSettingsView();
    if (targetPane == null || settingsView == null) return null;
    if (tabEditorSettings == null) {
      tabEditorSettings = new Tab("Editor Settings", settingsView);
      tabEditorSettings.setClosable(true);
      tabEditorSettings.setOnClosed(e -> {
        tabEditorSettings = null;
        releaseEditorSettingsIfUnused();
      });
    } else if (tabEditorSettings.getContent() != settingsView) {
      tabEditorSettings.setContent(settingsView);
    }
    attachPanelTabToPane(tabEditorSettings, targetPane);
    return tabEditorSettings;
  }

  private void launchPuppeteerFromLauncher(PuppeteerLauncherPanel.LaunchRequest request) {
    PuppeteerLauncherPanel.SceneSnapshot snapshot = request != null ? request.snapshot() : null;
    String selectedTimelineName = request != null ? request.timelineName() : null;
    String preferredTimelineName = selectedTimelineName;
    AnimationProject imported;
    if (selectedTimelineName != null && !selectedTimelineName.isBlank()) {
      imported = importNamedTimeline(selectedTimelineName, true);
    } else {
      preferredTimelineName = snapshot != null ? snapshot.preferredTimelineName() : null;
      imported = importTimelineFromSnapshot(snapshot);
    }
    PuppeteerWindow puppeteer = imported != null
        ? new PuppeteerWindow(imported)
        : new PuppeteerWindow();
    puppeteer.setOnCopyCode(code -> status.setText("Copied timeline code to clipboard"));
    if (projectRoot != null) puppeteer.setProjectRoot(projectRoot);
    if (preferredTimelineName != null && !preferredTimelineName.isBlank()) {
      puppeteer.setTimelineName(preferredTimelineName);
    }
    FileEditorTab ft = getActiveFileTab();
    if (ft != null) {
      puppeteer.setSourceScriptFile(ft.getFile());
    }

    JesScene2D launchScene = resolvePuppeteerLaunchScene(ft, imported, snapshot);
    if (launchScene != null) {
      puppeteer.setScene(launchScene);
    }

    if (snapshot != null) {
      String title = "Puppeteer";
      if (preferredTimelineName != null && !preferredTimelineName.isBlank()) {
        title += " - " + preferredTimelineName;
      }
      if (snapshot.currentLabel != null) title += " @ " + snapshot.currentLabel;
      title += " (line " + (snapshot.atLine + 1) + ")";
      puppeteer.setTitle(title);
    }
    puppeteer.show();
  }

  private JesScene2D resolvePuppeteerLaunchScene(
      FileEditorTab fileTab,
      AnimationProject imported,
      PuppeteerLauncherPanel.SceneSnapshot snapshot
  ) {
    JesScene2D scene = null;
    if (fileTab != null && fileTab.getJesScene() != null) {
      scene = fileTab.getJesScene();
    } else if (snapshot != null && (snapshot.backgroundId != null || !snapshot.characters.isEmpty())) {
      scene = buildSceneFromSnapshot(snapshot);
    } else if (imported != null) {
      scene = new JesScene2D();
    }

    if (scene != null && imported != null) {
      ensureSceneEntitiesForProject(scene, imported, snapshot);
    }
    return scene;
  }

  private void ensureSceneEntitiesForProject(
      JesScene2D scene,
      AnimationProject imported,
      PuppeteerLauncherPanel.SceneSnapshot snapshot
  ) {
    if (scene == null || imported == null) return;
    List<EntityTrack> missingTracks = new ArrayList<>();
    for (EntityTrack track : imported.getTracks()) {
      if (track == null) continue;
      String entityName = track.getEntityName();
      if (entityName == null || entityName.isBlank()) continue;
      if (scene.find(entityName) != null) continue;
      if (isCameraTrack(track)) continue;
      missingTracks.add(track);
    }

    for (int i = 0; i < missingTracks.size(); i++) {
      addMissingSceneEntity(scene, missingTracks.get(i), snapshot, i, missingTracks.size());
    }
  }

  private void addMissingSceneEntity(
      JesScene2D scene,
      EntityTrack track,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      int missingIndex,
      int missingCount
  ) {
    if (scene == null || track == null) return;
    String entityName = track.getEntityName();
    if (entityName == null || entityName.isBlank()) return;

    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    double sceneW = Math.max(1.0, viewport.width());
    double sceneH = Math.max(1.0, viewport.height());
    double characterHeight = sceneH * 0.85;
    double fallbackWidth = sceneW * 0.16;
    double fallbackHeight = sceneH * 0.62;

    PuppeteerLauncherPanel.CharacterEntry snapshotEntry = null;
    if (snapshot != null) {
      for (PuppeteerLauncherPanel.CharacterEntry entry : snapshot.characters) {
        if (entry != null && entityName.equals(entry.characterId)) {
          snapshotEntry = entry;
          break;
        }
      }
    }

    String expression = snapshotEntry != null ? snapshotEntry.expression : "neutral";
    String position = snapshotEntry != null ? snapshotEntry.position : fallbackTrackPosition(missingIndex, missingCount);
    String spritePathSpec = "";
    if (snapshot != null && snapshot.hasCharacterPathMapping(entityName, expression)) {
      spritePathSpec = resolveProjectPathSpec(snapshot.resolveCharacterPath(entityName, expression));
    } else if (snapshot != null && snapshot.hasCharacterPathMapping(entityName, "neutral")) {
      spritePathSpec = resolveProjectPathSpec(snapshot.resolveCharacterPath(entityName, "neutral"));
    }

    boolean hasTrackX = track.hasKeyframes(PropertyType.X);
    boolean hasTrackY = track.hasKeyframes(PropertyType.Y);
    double fallbackLeftX = positionToLeftX(position, sceneW, fallbackWidth);
    double fallbackCenterX = fallbackLeftX + (fallbackWidth * 0.5);
    double fallbackBottomY = sceneH;
    double x = hasTrackX ? track.getValueAt(PropertyType.X, 0.0) : fallbackCenterX;
    double y = hasTrackY ? track.getValueAt(PropertyType.Y, 0.0) : fallbackBottomY;

    if (!spritePathSpec.isBlank()) {
      double[] spriteSize = estimateSpriteSize(firstLayerPath(spritePathSpec), characterHeight);
      double charW = spriteSize[0];
      double charH = spriteSize[1];
      if (!hasTrackX) {
        x = positionToLeftX(position, sceneW, charW) + (charW * 0.5);
      }
      if (!hasTrackY) {
        y = sceneH;
      }
      com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(spritePathSpec, charW, charH);
      sprite.setOrigin(0.5, 1.0);
      sprite.setPosition(x, y);
      sprite.setZ(track.getLayerOrder());
      scene.add(sprite);
      scene.registerEntity(entityName, sprite);
      return;
    }

    com.jvn.core.scene2d.Panel2D placeholder = new com.jvn.core.scene2d.Panel2D(fallbackWidth, fallbackHeight);
    placeholder.setOrigin(0.5, 1.0);
    placeholder.setFill(0.23, 0.30, 0.40, 0.22);
    placeholder.setStroke(0.56, 0.76, 1.0, 0.88, 3.0);
    placeholder.setPosition(x, y);
    placeholder.setZ(track.getLayerOrder());
    scene.add(placeholder);
    scene.registerEntity(entityName, placeholder);
  }

  private JesScene2D buildSceneFromSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
    JesScene2D scene = new JesScene2D();
    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    double sceneW = Math.max(1.0, viewport.width());
    double sceneH = Math.max(1.0, viewport.height());
    double characterHeight = sceneH * 0.85;

    if (snapshot.backgroundId != null) {
      String bgPath = resolveProjectPathSpec(snapshot.resolveBackgroundPath());
      com.jvn.core.scene2d.Sprite2D bg = new com.jvn.core.scene2d.Sprite2D(bgPath, sceneW, sceneH);
      bg.setOrigin(0.0, 0.0);
      bg.setPosition(0.0, 0.0);
      scene.add(bg);
      scene.registerEntity("bg_" + snapshot.backgroundId, bg);
    }

    for (PuppeteerLauncherPanel.CharacterEntry ch : snapshot.characters) {
      String spritePathSpec = resolveProjectPathSpec(snapshot.resolveCharacterPath(ch.characterId, ch.expression));
      double[] spriteSize = estimateSpriteSize(firstLayerPath(spritePathSpec), characterHeight);
      double charW = spriteSize[0];
      double charH = spriteSize[1];
      double leftX = positionToLeftX(ch.position, sceneW, charW);
      double topY = (sceneH * 1.0) - charH;
      com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(spritePathSpec, charW, charH);
      // Character-friendly pivot for puppeteering: bottom-center (feet/contact point).
      sprite.setOrigin(0.5, 1.0);
      // Keep visual placement equivalent to prior top-left anchoring.
      sprite.setPosition(leftX + (charW * 0.5), topY + charH);
      scene.add(sprite);
      scene.registerEntity(ch.characterId, sprite);
    }

    return scene;
  }

  private boolean isCameraTrack(EntityTrack track) {
    if (track == null) return true;
    String entityName = track.getEntityName();
    if ("__camera__".equals(entityName)) return true;
    boolean hasAnimatedProperties = false;
    for (PropertyType property : track.getAnimatedProperties()) {
      hasAnimatedProperties = true;
      if (property != PropertyType.CAMERA_X
          && property != PropertyType.CAMERA_Y
          && property != PropertyType.CAMERA_ZOOM) {
        return false;
      }
    }
    return hasAnimatedProperties;
  }

  private String fallbackTrackPosition(int index, int total) {
    if (total <= 1) return "center";
    return switch (Math.max(0, index) % 5) {
      case 0 -> "left";
      case 1 -> "center";
      case 2 -> "right";
      case 3 -> "far_left";
      default -> "far_right";
    };
  }

  private double positionToLeftX(String position, double sceneW, double spriteW) {
    if (position == null) return (sceneW - spriteW) * 0.5;
    return switch (position) {
      case "far_left"  -> sceneW * 0.05;
      case "left"      -> sceneW * 0.20;
      case "center"    -> (sceneW - spriteW) * 0.5;
      case "right"     -> sceneW * 0.80 - spriteW;
      case "far_right" -> sceneW * 0.95 - spriteW;
      default          -> (sceneW - spriteW) * 0.5;
    };
  }

  private double[] estimateSpriteSize(String spritePath, double targetHeight) {
    double height = Math.max(1.0, targetHeight);
    double width = height * 0.5;
    if (spritePath == null || spritePath.isBlank()) return new double[] { width, height };
    try {
      javafx.scene.image.Image image = new javafx.scene.image.Image(
          new java.io.File(spritePath).toURI().toString(), 0, 0, true, false);
      if (image.getWidth() > 0.0 && image.getHeight() > 0.0) {
        width = image.getWidth() * (height / image.getHeight());
      }
    } catch (Exception ignored) {
    }
    return new double[] { width, height };
  }

  private static String firstLayerPath(String pathSpec) {
    if (pathSpec == null) return "";
    int idx = pathSpec.indexOf('|');
    String raw = idx >= 0 ? pathSpec.substring(0, idx) : pathSpec;
    return raw == null ? "" : raw.trim();
  }

  private String resolveProjectPath(String relativePath) {
    if (relativePath == null) return "";
    if (projectRoot != null) {
      java.io.File f = new java.io.File(projectRoot, relativePath);
      if (f.exists()) return f.getAbsolutePath();
    }
    return relativePath;
  }

  private String resolveProjectPathSpec(String pathSpec) {
    if (pathSpec == null || pathSpec.isBlank()) return "";
    if (pathSpec.indexOf('|') < 0) return resolveProjectPath(pathSpec.trim());
    StringBuilder out = new StringBuilder();
    for (String token : pathSpec.split("\\|")) {
      String part = token == null ? "" : token.trim();
      if (part.isEmpty()) continue;
      if (!out.isEmpty()) out.append(" | ");
      out.append(resolveProjectPath(part));
    }
    return out.toString();
  }

  private void openActionEditor() {
    AnimationProject imported = discoverAndImportTimeline();
    PuppeteerWindow puppeteer = imported != null
        ? new PuppeteerWindow(imported)
        : new PuppeteerWindow();
    puppeteer.setOnCopyCode(code -> status.setText("Copied timeline code to clipboard"));
    if (projectRoot != null) puppeteer.setProjectRoot(projectRoot);
    FileEditorTab ft = getActiveFileTab();
    if (ft != null) {
      puppeteer.setSourceScriptFile(ft.getFile());
    }
    if (ft != null && ft.getJesScene() != null) {
      puppeteer.setScene(ft.getJesScene());
    }
    puppeteer.show();
  }

  private AnimationProject importTimelineFromSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
    if (snapshot == null) return null;
    String preferredTimelineName = snapshot.preferredTimelineName();
    if (snapshot.hasInlineTimeline()) {
      try {
        return CodeImporter.importCode(
            preferredTimelineName != null && !preferredTimelineName.isBlank() ? preferredTimelineName : "inline_timeline",
            wrapInlineTimeline(snapshot.inlineTimelineBody));
      } catch (Exception ex) {
        showTimelineImportWarning("inline timeline", ex);
        return null;
      }
    }
    if (preferredTimelineName != null && !preferredTimelineName.isBlank()) {
      return importNamedTimeline(preferredTimelineName, false);
    }
    return null;
  }

  private static String wrapInlineTimeline(String body) {
    String content = body != null ? body.strip() : "";
    if (content.isEmpty()) return "timeline {\n}\n";
    return "timeline {\n" + content + "\n}\n";
  }

  /**
   * Scans scripts/timelines/ for existing .jes files and offers a choice dialog.
   * Returns the imported AnimationProject, or null if the user chose "New" or no files exist.
   */
  private AnimationProject discoverAndImportTimeline() {
    if (projectRoot == null) return null;
    java.io.File timelinesDir = new java.io.File(projectRoot, "scripts/timelines");
    if (!timelinesDir.isDirectory()) return null;
    java.io.File[] jesFiles = timelinesDir.listFiles((dir, name) ->
        name.endsWith(".jes") && !name.startsWith("."));
    if (jesFiles == null || jesFiles.length == 0) return null;

    java.util.List<String> names = new java.util.ArrayList<>();
    for (java.io.File f : jesFiles) {
      String n = f.getName();
      names.add(n.substring(0, n.length() - 4));
    }
    java.util.Collections.sort(names);

    javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(names.get(0), names);
    EditorTheme.apply(dialog);
    dialog.setTitle("Open Timeline");
    dialog.setHeaderText("Found " + names.size() + " existing timeline(s). Open one or start fresh?");
    dialog.setContentText("Timeline:");

    // Add a "New" button alongside OK/Cancel
    dialog.getDialogPane().getButtonTypes().setAll(
        new ButtonType("Open", ButtonBar.ButtonData.OK_DONE),
        new ButtonType("New (empty)", ButtonBar.ButtonData.NO),
        ButtonType.CANCEL
    );

    Optional<String> result = dialog.showAndWait();
    if (result.isEmpty()) return null; // cancelled or "New (empty)"

    String selectedName = result.get();
    if (selectedName == null || selectedName.isBlank()) return null;

    return importNamedTimeline(selectedName, true);
  }

  private AnimationProject importNamedTimeline(String selectedName, boolean showWarningOnFailure) {
    if (projectRoot == null || selectedName == null || selectedName.isBlank()) return null;
    try {
      java.io.File timelinesDir = new java.io.File(projectRoot, "scripts/timelines");
      java.io.File jesFile = new java.io.File(timelinesDir, selectedName + ".jes");
      if (!jesFile.isFile()) return null;
      String code = java.nio.file.Files.readString(jesFile.toPath());
      return CodeImporter.importCode(selectedName, code);
    } catch (Exception ex) {
      if (showWarningOnFailure) {
        showTimelineImportWarning(selectedName, ex);
      }
      return null;
    }
  }

  private void showTimelineImportWarning(String timelineName, Exception ex) {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    EditorTheme.apply(alert);
    alert.setTitle("Import Failed");
    alert.setHeaderText("Could not import timeline '" + timelineName + "'");
    alert.setContentText(ex != null ? ex.getMessage() : "Unknown error");
    alert.showAndWait();
  }

  private static void applyLinuxDefaultWindowState(Stage stage) {
    if (stage == null || !isLinux()) return;
    stage.setIconified(false);
    stage.setMaximized(true);
    Platform.runLater(() -> {
      stage.setIconified(false);
      stage.setMaximized(true);
    });
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
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
