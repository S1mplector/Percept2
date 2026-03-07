package com.jvn.editor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.nativebridge.NativeLibraryLoader;
import com.jvn.core.nativebridge.NativeMathBridge;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.ui.AssetBrowserView;
import com.jvn.audiofx.AudioFxController;
import com.jvn.audiofx.AudioFxNativeBridge;
import com.jvn.editor.ui.AudioSynthControlsView;
import com.jvn.editor.ui.CssIcon;
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
import com.jvn.editor.ui.ProjectExplorerView;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.editor.ui.RunConsoleView;
import com.jvn.editor.ui.ScriptEditorLauncherView;
import com.jvn.editor.ui.SettingsEditorView;
import com.jvn.editor.ui.StartupSplashOverlay;
import com.jvn.editor.ui.StoryTimelineView;
import com.jvn.editor.ui.TilemapEditorView;
import com.jvn.editor.ui.VersionControlView;
import com.jvn.editor.ui.VnsDiagnosticsView;
import com.jvn.editor.ui.VnsFlowMapView;
import com.jvn.editor.ui.VnsScriptAnalyzer;
import com.jvn.editor.ui.WelcomeCenterView;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.CodeImporter;
import com.jvn.editor.ui.actioneditor.PuppeteerWindow;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.sun.management.OperatingSystemMXBean;

import javafx.animation.AnimationTimer;
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
import javafx.util.Duration;
import javax.tools.ToolProvider;

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
  private LayeredImageVisualizerView layeredImageVisualizerView;
  private ImageAttributesToolView imageAttributesToolView;
  private ImageTintToolView imageTintToolView;
  private LayoutStudioWindowManager layoutStudioWindowManager;
  private MenuFlowEditorView menuFlowEditorView;
  private SettingsEditorView settingsEditor;
  private com.jvn.editor.ui.MenuThemeEditorView menuThemeEditor;
  private TilemapEditorView mapEditorView;
  private PuppeteerLauncherPanel puppeteerLauncherPanel;
  private ScriptEditorLauncherView scriptEditorLauncherView;
  private AudioSynthControlsView audioSynthControlsView;
  private AudioFxController audioFxController;
  private Tab tabAudioSynthControls;
  private Tab tabPuppeteerLauncher;
  private final CommandStack commands = new CommandStack();
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
  private Tab tabLayeredImageVisualizer;
  private Tab tabImageAttributesTool;
  private Tab tabImageTintTool;
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
  private static final String STARTUP_LOGO_RELATIVE_PATH = "docs/assets/images/jvn_logo.png";
  private static final String STARTUP_LOGO_CLASSPATH_RESOURCE = "/com/jvn/editor/images/jvn_logo.png";
  private static final long MIN_STARTUP_SPLASH_MS = 900L;
  private static final long STARTUP_STEP_DELAY_MS = 170L;
  private static final DateTimeFormatter STARTUP_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final Pattern STARTUP_PROCESS_NOISE = Pattern.compile(
      "^(> Task |> Configure |BUILD SUCCESSFUL|Deprecated Gradle|\\d+ actionable|To honour the JVM|Daemon will be stopped|\\s*$)");
  private static final int STARTUP_COMMAND_TAIL_LINES = 14;

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
    stopAllPreviewAudio();
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
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.setProjectRoot(root);
    if (imageAttributesToolView != null) imageAttributesToolView.setProjectRoot(root);
    if (imageTintToolView != null) imageTintToolView.setProjectRoot(root);
    if (menuFlowEditorView != null) menuFlowEditorView.setProjectRoot(root);
    if (scriptEditorLauncherView != null) scriptEditorLauncherView.setProjectRoot(root);
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
      javafx.scene.Scene logScene = new javafx.scene.Scene(console, 800, 500);
      EditorTheme.apply(logScene);
      logStage.setScene(logScene);
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

        updateMessage("Running native smoke tests");
        runGradleStartupProcess(
            workspace,
            splash,
            "Smoke Tests",
            List.of(":core:test", "--tests", "com.jvn.core.nativebridge.NativeMathBridgeTest"),
            "Native math smoke tests failed",
            "Resolve the `NativeMathBridgeTest` failure and retry.");
        runGradleStartupProcess(
            workspace,
            splash,
            "Smoke Tests",
            List.of(
                ":audio-fx:test",
                "--tests", "com.jvn.audiofx.AudioFxNativeBridgeTest",
                "--tests", "com.jvn.audiofx.AudioFxControllerTest",
                "--tests", "com.jvn.audiofx.VnsCommandBuilderTest",
                "--tests", "com.jvn.audiofx.WaveformAnalyzerTest"),
            "Audio synth smoke tests failed",
            "Resolve the `audio-fx` smoke test failures and retry.");
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
    List<String> cmd = new ArrayList<>();
    cmd.add(resolveGradleCommand(workspace));
    cmd.add("--no-daemon");
    cmd.add("--console=plain");
    cmd.add("--gradle-user-home");
    cmd.add(gradleUserHome.getAbsolutePath());
    cmd.add("-Dorg.gradle.vfs.watch=false");
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
    layoutStudioWindowManager = new LayoutStudioWindowManager(primaryStage, this::doRunProject);
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

    // ── Edit ──
    Menu menuEdit = new Menu("Edit");
    MenuItem miUndo = new MenuItem("Undo");
    miUndo.setOnAction(e -> { commands.undo(); status.setText("Undo"); inspectorView.setSelection(selected); });
    miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRedo = new MenuItem("Redo");
    miRedo.setOnAction(e -> { commands.redo(); status.setText("Redo"); inspectorView.setSelection(selected); });
    miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miFind = new MenuItem("Find / Replace");
    miFind.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
    miFind.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null) ft.showSearchBar();
    });
    MenuItem miGoToLine = new MenuItem("Go to Line...");
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
    menuEdit.getItems().addAll(miUndo, miRedo, new SeparatorMenuItem(),
        miFind, miGoToLine, new SeparatorMenuItem(), miReload);

    // ── View ──
    Menu menuView = new Menu("View");
    MenuItem miToggleEditorFullscreen = new MenuItem("Toggle Editor Fullscreen");
    miToggleEditorFullscreen.setOnAction(e -> toggleActiveEditorFullscreen());
    miToggleEditorFullscreen.setAccelerator(new KeyCodeCombination(KeyCode.F11));
    MenuItem miResetCamera = new MenuItem("Reset Camera");
    miResetCamera.setOnAction(e -> resetCamera());
    MenuItem miFitContent = new MenuItem("Fit to Content / Fullscreen Preview");
    miFitContent.setOnAction(e -> fitCameraToContent());

    Menu menuPanels = new Menu("Panels");
    MenuItem miShowProject = new MenuItem("Project Explorer");
    miShowProject.setOnAction(e -> selectProjectTab());
    MenuItem miShowTimeline = new MenuItem("Story Timeline");
    miShowTimeline.setOnAction(e -> selectTimelineTab());
    MenuItem miShowInspector = new MenuItem("Inspector");
    miShowInspector.setOnAction(e -> selectInspectorTab());
    MenuItem miShowAssets = new MenuItem("Asset Browser");
    miShowAssets.setOnAction(e -> selectAssetBrowserTab());
    MenuItem miShowDiagnostics = new MenuItem("VNS Diagnostics");
    miShowDiagnostics.setOnAction(e -> selectVnsDiagnosticsTab());
    MenuItem miShowFlowMap = new MenuItem("Label Flow Map");
    miShowFlowMap.setOnAction(e -> selectVnsFlowMapTab());
    MenuItem miShowLayeredVisualizer = new MenuItem("Layered Image Visualizer");
    miShowLayeredVisualizer.setOnAction(e -> selectLayeredImageVisualizerTab());
    MenuItem miShowImageAttributes = new MenuItem("Image Attributes Tool");
    miShowImageAttributes.setOnAction(e -> selectImageAttributesToolTab());
    MenuItem miShowImageTint = new MenuItem("Image Tint Tool");
    miShowImageTint.setOnAction(e -> selectImageTintToolTab());
    MenuItem miShowPuppeteerLauncher = new MenuItem("Puppeteer Launcher");
    miShowPuppeteerLauncher.setOnAction(e -> selectPuppeteerLauncherTab());
    menuPanels.getItems().addAll(miShowProject, miShowTimeline, miShowInspector,
        miShowAssets, new SeparatorMenuItem(),
        miShowDiagnostics, miShowFlowMap, miShowLayeredVisualizer, miShowImageAttributes, miShowImageTint, miShowPuppeteerLauncher);

    menuView.getItems().addAll(miToggleEditorFullscreen, new SeparatorMenuItem(),
        miResetCamera, miFitContent, new SeparatorMenuItem(),
        menuPanels);

    // ── Run ──
    Menu menuRun = new Menu("Run");
    MenuItem miApplyCode = new MenuItem("Apply Code");
    miApplyCode.setOnAction(e -> applyCodeFromEditor());
    miApplyCode.setAccelerator(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRunProject = new MenuItem("Run Project");
    miRunProject.setOnAction(e -> doRunProject(primaryStage));
    MenuItem miLaunchHere = new MenuItem("Launch from Here");
    miLaunchHere.setAccelerator(new KeyCodeCombination(KeyCode.F5));
    miLaunchHere.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.launchFromHere();
      } else {
        status.setText("Launch from Here is only available for VNS files");
      }
    });
    MenuItem miLaunchStart = new MenuItem("Launch from Start");
    miLaunchStart.setAccelerator(new KeyCodeCombination(KeyCode.F5, KeyCombination.SHIFT_DOWN));
    miLaunchStart.setOnAction(e -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.runFromLabel(null);
      } else {
        status.setText("Launch from Start is only available for VNS files");
      }
    });
    menuRun.getItems().addAll(miApplyCode, new SeparatorMenuItem(),
        miRunProject, new SeparatorMenuItem(),
        miLaunchHere, miLaunchStart);

    // ── Tools ──
    Menu menuTools = new Menu("Tools");
    MenuItem miActionEditor = new MenuItem("Puppeteer (Window)");
    miActionEditor.setOnAction(e -> openActionEditor());
    miActionEditor.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    MenuItem miPuppeteerPanel = new MenuItem("Puppeteer Launcher");
    miPuppeteerPanel.setOnAction(e -> selectPuppeteerLauncherTab());

    MenuItem miMenuFlow = new MenuItem("Menu Flow Editor");
    miMenuFlow.setOnAction(e -> selectMenuFlowTab());
    MenuItem miLayoutLauncher = new MenuItem("Layout Launcher");
    miLayoutLauncher.setOnAction(e -> selectLayoutLauncherTab());
    MenuItem miLayeredVisualizer = new MenuItem("Layered Image Visualizer");
    miLayeredVisualizer.setOnAction(e -> selectLayeredImageVisualizerTab());
    MenuItem miImageAttributes = new MenuItem("Image Attributes Tool");
    miImageAttributes.setOnAction(e -> selectImageAttributesToolTab());
    MenuItem miImageTint = new MenuItem("Image Tint Tool");
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

    menuTools.getItems().addAll(miActionEditor, miPuppeteerPanel, new SeparatorMenuItem(),
        miMenuFlow, miLayoutLauncher, miLayeredVisualizer, miImageAttributes, miImageTint, new SeparatorMenuItem(),
        menuVnsTools, new SeparatorMenuItem(),
        miToolAssets, miToolInspector);

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
    menuVcs.getItems().addAll(miOpenVcs, miRefreshVcs);

    // ── Help ──
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
    MenuItem miAbout = new MenuItem("About JVN Editor");
    miAbout.setOnAction(e -> {
      javafx.scene.control.Alert about = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
      about.setTitle("About JVN Editor");
      about.setHeaderText("JVN Editor " + editorVersion);
      about.setContentText("Java Vector Nexus — Visual Novel & 2D Game Toolkit");
      about.showAndWait();
    });
    menuHelp.getItems().addAll(miWelcome, miHelpCenter, miRefreshHelp, new SeparatorMenuItem(), miAbout);

    mb.getMenus().addAll(menuFile, menuEdit, menuView, menuRun, menuTools, menuVcs, menuHelp);

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
    vnsDiagnosticsView.setOnOpenTarget(this::jumpToActiveVnsDiagnostic);
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
    layeredImageVisualizerView = new LayeredImageVisualizerView();
    layeredImageVisualizerView.setProjectRoot(projectRoot);
    layeredImageVisualizerView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(layeredImageVisualizerView));
    layeredImageVisualizerView.setFullscreenActive(false);
    imageAttributesToolView = new ImageAttributesToolView();
    imageAttributesToolView.setProjectRoot(projectRoot);
    imageAttributesToolView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(imageAttributesToolView));
    imageAttributesToolView.setFullscreenActive(false);
    imageTintToolView = new ImageTintToolView();
    imageTintToolView.setProjectRoot(projectRoot);
    imageTintToolView.setOnToggleFullscreen(() -> toggleImageToolFullscreen(imageTintToolView));
    imageTintToolView.setFullscreenActive(false);
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
    scriptEditorLauncherView = new ScriptEditorLauncherView();
    scriptEditorLauncherView.setProjectRoot(projectRoot);
    audioFxController = new AudioFxController();
    audioSynthControlsView = new AudioSynthControlsView();
    audioSynthControlsView.setController(audioFxController);
    audioSynthControlsView.setOnInsertSnippet(snippet -> {
      FileEditorTab ft = getActiveFileTab();
      if (ft != null && ft.getKind() == FileEditorTab.Kind.VNS) {
        ft.insertVnsSnippet(snippet);
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
    centerSplit = new SplitPane();
    centerSplit.getItems().addAll(leftTabs, filesTabs, rightTabs);
    centerSplit.setDividerPositions(0.22, 0.78);
    savedCenterDividers = new double[]{0.22, 0.78};
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
        return;
      }
      disposeAllFileTabs();
    });
    applyLinuxDefaultWindowState(primaryStage);
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
    if (layeredImageVisualizerView != null) layeredImageVisualizerView.setProjectRoot(projectRoot);
    if (imageAttributesToolView != null) imageAttributesToolView.setProjectRoot(projectRoot);
    if (imageTintToolView != null) imageTintToolView.setProjectRoot(projectRoot);
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

  private void closeAndDisposeTab(Tab tab) {
    if (filesTabs == null || tab == null) return;
    if (tab.getContent() instanceof FileEditorTab ft) {
      try {
        ft.dispose();
      } catch (Exception ignored) {
      }
    }
    filesTabs.getTabs().remove(tab);
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

  private Tab ensureLayeredImageVisualizerTab(TabPane targetPane) {
    if (targetPane == null || layeredImageVisualizerView == null) return null;
    if (tabLayeredImageVisualizer == null) {
      tabLayeredImageVisualizer = new Tab("Layered Images", layeredImageVisualizerView);
      tabLayeredImageVisualizer.setClosable(true);
      tabLayeredImageVisualizer.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == layeredImageVisualizerView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabLayeredImageVisualizer = null;
      });
    }
    attachPanelTabToPane(tabLayeredImageVisualizer, targetPane);
    return tabLayeredImageVisualizer;
  }

  private Tab ensureImageAttributesToolTab(TabPane targetPane) {
    if (targetPane == null || imageAttributesToolView == null) return null;
    if (tabImageAttributesTool == null) {
      tabImageAttributesTool = new Tab("Image Attributes", imageAttributesToolView);
      tabImageAttributesTool.setClosable(true);
      tabImageAttributesTool.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == imageAttributesToolView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabImageAttributesTool = null;
      });
    }
    attachPanelTabToPane(tabImageAttributesTool, targetPane);
    return tabImageAttributesTool;
  }

  private Tab ensureImageTintToolTab(TabPane targetPane) {
    if (targetPane == null || imageTintToolView == null) return null;
    if (tabImageTintTool == null) {
      tabImageTintTool = new Tab("Image Tint", imageTintToolView);
      tabImageTintTool.setClosable(true);
      tabImageTintTool.setOnClosed(e -> {
        if (layeredVisualizerFullscreen && fullscreenImageToolView == imageTintToolView) {
          restoreLayeredImageVisualizerLayout(false);
        }
        tabImageTintTool = null;
      });
    }
    attachPanelTabToPane(tabImageTintTool, targetPane);
    return tabImageTintTool;
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

  private void addChooserActionRow(javafx.scene.layout.VBox actions, String panelName, String iconClass,
      Runnable embedAction, Runnable windowAction) {
    if (actions == null || panelName == null) return;

    Region panelIcon = icon("icon", iconClass);

    Label label = new Label(panelName);
    label.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(label, Priority.ALWAYS);
    label.setStyle("-fx-font-size: 12px; -fx-text-fill: #d0d8e8;");

    Button dockBtn = new Button();
    dockBtn.setGraphic(CssIcon.dock("#9cc7ff"));
    dockBtn.setTooltip(new Tooltip("Add to sidebar panel"));
    dockBtn.setMinSize(26, 26); dockBtn.setPrefSize(26, 26); dockBtn.setMaxSize(26, 26);
    dockBtn.setFocusTraversable(false);
    dockBtn.getStyleClass().add("panel-chooser-icon-btn");
    if (embedAction != null) {
      dockBtn.setOnAction(e -> embedAction.run());
    } else {
      dockBtn.setDisable(true);
    }

    Button popOutBtn = new Button();
    popOutBtn.setGraphic(CssIcon.popOut("#f5c46b"));
    popOutBtn.setTooltip(new Tooltip("Open in separate window"));
    popOutBtn.setMinSize(26, 26); popOutBtn.setPrefSize(26, 26); popOutBtn.setMaxSize(26, 26);
    popOutBtn.setFocusTraversable(false);
    popOutBtn.getStyleClass().add("panel-chooser-icon-btn");
    if (windowAction != null) {
      popOutBtn.setOnAction(e -> windowAction.run());
    } else {
      popOutBtn.setDisable(true);
    }

    HBox row = new HBox(6, panelIcon, label, dockBtn, popOutBtn);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new javafx.geometry.Insets(4, 6, 4, 6));
    row.setStyle("-fx-background-color: #1e2230; -fx-background-radius: 4;");
    actions.getChildren().add(row);
  }

  private void launchPanelAsWindow(String title, javafx.scene.Parent content, double width, double height) {
    if (content == null) return;
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
    windowStage.setOnCloseRequest(e -> {
      // On window close, do NOT re-attach — user can re-add via chooser
    });
    applyLinuxDefaultWindowState(windowStage);
    windowStage.show();
  }

  private void detachFromSidebarTab(javafx.scene.Parent content) {
    Tab[] allTabs = {
        tabProject, tabTimeline, tabHelp, tabInspector, tabVnsDiagnostics,
        tabVnsFlowMap, tabAssetBrowser, tabVersionControl, tabLayoutLauncher,
        tabLayeredImageVisualizer, tabImageAttributesTool, tabImageTintTool,
        tabMenuFlow, tabPuppeteerLauncher
    };
    for (Tab t : allTabs) {
      if (t != null && t.getContent() == content) {
        TabPane tp = t.getTabPane();
        if (tp != null) tp.getTabs().remove(t);
        t.setContent(null);
        nullifyTab(t);
        break;
      }
      // Also check if content is wrapped in a ScrollPane
      if (t != null && t.getContent() instanceof ScrollPane sp && sp.getContent() == content) {
        TabPane tp = t.getTabPane();
        if (tp != null) tp.getTabs().remove(t);
        sp.setContent(null);
        t.setContent(null);
        nullifyTab(t);
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
    else if (tab == tabLayeredImageVisualizer) tabLayeredImageVisualizer = null;
    else if (tab == tabImageAttributesTool) tabImageAttributesTool = null;
    else if (tab == tabImageTintTool) tabImageTintTool = null;
    else if (tab == tabMenuFlow) tabMenuFlow = null;
    else if (tab == tabPuppeteerLauncher) tabPuppeteerLauncher = null;
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
    javafx.scene.layout.VBox actions = new javafx.scene.layout.VBox(4);
    addChooserActionRow(actions, "Project", "icon-panel-project", () -> {
      Tab t = ensureProjectTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Project", projView, 600, 700));

    addChooserActionRow(actions, "Timeline", "icon-panel-timeline", () -> {
      Tab t = ensureTimelineTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Story Timeline", timelineView, 600, 700));

    addChooserActionRow(actions, "VNS Diagnostics", "icon-panel-diagnostics", () -> {
      Tab t = ensureVnsDiagnosticsTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("VNS Diagnostics", vnsDiagnosticsView, 700, 600));

    addChooserActionRow(actions, "Label Flow", "icon-panel-flow", () -> {
      Tab t = ensureVnsFlowMapTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Label Flow Map", vnsFlowMapView, 700, 600));

    addChooserActionRow(actions, "Assets", "icon-panel-assets", () -> {
      Tab t = ensureAssetBrowserTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Asset Browser", assetBrowserView, 700, 600));

    addChooserActionRow(actions, "Layout Launcher", "icon-panel-layouts", () -> {
      Tab t = ensureLayoutLauncherTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (layoutEditorLauncherView != null) layoutEditorLauncherView.refreshStatus();
    }, () -> {
      if (layoutEditorLauncherView != null) layoutEditorLauncherView.refreshStatus();
      launchPanelAsWindow("Layout Launcher", layoutEditorLauncherView, 700, 700);
    });

    addChooserActionRow(actions, "Layered Image Visualizer", "icon-panel-layered", () -> {
      Tab t = ensureLayeredImageVisualizerTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (layeredImageVisualizerView != null) layeredImageVisualizerView.refreshCatalog();
    }, () -> {
      if (layeredImageVisualizerView != null) layeredImageVisualizerView.refreshCatalog();
      launchPanelAsWindow("Layered Image Visualizer", layeredImageVisualizerView, 900, 700);
    });

    addChooserActionRow(actions, "Image Attributes Tool", "icon-panel-image-attributes", () -> {
      Tab t = ensureImageAttributesToolTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (imageAttributesToolView != null) imageAttributesToolView.refreshCatalog();
    }, () -> {
      if (imageAttributesToolView != null) imageAttributesToolView.refreshCatalog();
      launchPanelAsWindow("Image Attributes Tool", imageAttributesToolView, 800, 650);
    });

    addChooserActionRow(actions, "Image Tint Tool", "icon-panel-image-tint", () -> {
      Tab t = ensureImageTintToolTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (imageTintToolView != null) imageTintToolView.refreshCatalog();
    }, () -> {
      if (imageTintToolView != null) imageTintToolView.refreshCatalog();
      launchPanelAsWindow("Image Tint Tool", imageTintToolView, 800, 650);
    });

    addChooserActionRow(actions, "Menu Flow", "icon-panel-menuflow", () -> {
      Tab t = ensureMenuFlowTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (menuFlowEditorView != null) menuFlowEditorView.refreshStatus();
    }, () -> {
      if (menuFlowEditorView != null) menuFlowEditorView.refreshStatus();
      launchPanelAsWindow("Menu Flow Editor", menuFlowEditorView, 900, 650);
    });

    addChooserActionRow(actions, "Version Control", "icon-panel-vcs", () -> {
      Tab t = ensureVersionControlTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
      if (versionControlView != null) versionControlView.refreshStatus();
    }, () -> {
      if (versionControlView != null) versionControlView.refreshStatus();
      launchPanelAsWindow("Version Control", versionControlView, 700, 600);
    });

    addChooserActionRow(actions, "Help", "icon-panel-help", () -> {
      Tab t = ensureHelpTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Help Center", helpCenterView, 700, 650));

    addChooserActionRow(actions, "Puppeteer Launcher", "icon-panel-puppeteer", () -> {
      Tab t = ensurePuppeteerLauncherTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Puppeteer Launcher", puppeteerLauncherPanel, 600, 500));

    addChooserActionRow(actions, "Audio Synth Controls", "icon-panel-diagnostics", () -> {
      Tab t = ensureAudioSynthControlsTab(pane);
      if (t != null) pane.getSelectionModel().select(t);
    }, () -> launchPanelAsWindow("Audio Synth Controls", audioSynthControlsView, 380, 650));

    addChooserActionRow(actions, "Script Editor", "icon-panel-flow", null, () -> {
      if (scriptEditorLauncherView != null) {
        scriptEditorLauncherView.setProjectRoot(projectRoot);
        scriptEditorLauncherView.launchEditorWindow();
      }
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

  private Tab ensureAudioSynthControlsTab(TabPane targetPane) {
    if (targetPane == null || audioSynthControlsView == null) return null;
    if (tabAudioSynthControls == null) {
      tabAudioSynthControls = new Tab("Audio Synth", audioSynthControlsView);
      tabAudioSynthControls.setClosable(true);
      tabAudioSynthControls.setOnClosed(e -> tabAudioSynthControls = null);
    }
    attachPanelTabToPane(tabAudioSynthControls, targetPane);
    return tabAudioSynthControls;
  }

  private void launchPuppeteerFromSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
    AnimationProject imported = discoverAndImportTimeline();
    PuppeteerWindow puppeteer = imported != null
        ? new PuppeteerWindow(imported)
        : new PuppeteerWindow();
    puppeteer.setOnCopyCode(code -> status.setText("Copied timeline code to clipboard"));
    if (projectRoot != null) puppeteer.setProjectRoot(projectRoot);
    FileEditorTab ft = getActiveFileTab();

    if (ft != null && ft.getJesScene() != null) {
      puppeteer.setScene(ft.getJesScene());
    } else if (snapshot != null && (snapshot.backgroundId != null || !snapshot.characters.isEmpty())) {
      JesScene2D scene = buildSceneFromSnapshot(snapshot);
      puppeteer.setScene(scene);
    }

    if (snapshot != null) {
      String title = "Puppeteer";
      if (snapshot.currentLabel != null) title += " @ " + snapshot.currentLabel;
      title += " (line " + (snapshot.atLine + 1) + ")";
      puppeteer.setTitle(title);
    }
    puppeteer.show();
  }

  private JesScene2D buildSceneFromSnapshot(PuppeteerLauncherPanel.SceneSnapshot snapshot) {
    JesScene2D scene = new JesScene2D();
    ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
    double sceneW = Math.max(1.0, viewport.width());
    double sceneH = Math.max(1.0, viewport.height());
    double characterHeight = sceneH * 0.85;

    if (snapshot.backgroundId != null) {
      String bgPath = resolveProjectPath(firstLayerPath(snapshot.resolveBackgroundPath()));
      com.jvn.core.scene2d.Sprite2D bg = new com.jvn.core.scene2d.Sprite2D(bgPath, sceneW, sceneH);
      bg.setOrigin(0.0, 0.0);
      bg.setPosition(0.0, 0.0);
      scene.add(bg);
      scene.registerEntity("bg_" + snapshot.backgroundId, bg);
    }

    for (PuppeteerLauncherPanel.CharacterEntry ch : snapshot.characters) {
      String spritePath = resolveProjectPath(firstLayerPath(snapshot.resolveCharacterPath(ch.characterId, ch.expression)));
      double[] spriteSize = estimateSpriteSize(spritePath, characterHeight);
      double charW = spriteSize[0];
      double charH = spriteSize[1];
      double leftX = positionToLeftX(ch.position, sceneW, charW);
      double topY = (sceneH * 1.0) - charH;
      com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(spritePath, charW, charH);
      // Character-friendly pivot for puppeteering: bottom-center (feet/contact point).
      sprite.setOrigin(0.5, 1.0);
      // Keep visual placement equivalent to prior top-left anchoring.
      sprite.setPosition(leftX + (charW * 0.5), topY + charH);
      scene.add(sprite);
      scene.registerEntity(ch.characterId, sprite);
    }

    return scene;
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

  private void openActionEditor() {
    AnimationProject imported = discoverAndImportTimeline();
    PuppeteerWindow puppeteer = imported != null
        ? new PuppeteerWindow(imported)
        : new PuppeteerWindow();
    puppeteer.setOnCopyCode(code -> status.setText("Copied timeline code to clipboard"));
    if (projectRoot != null) puppeteer.setProjectRoot(projectRoot);
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getJesScene() != null) {
      puppeteer.setScene(ft.getJesScene());
    }
    puppeteer.show();
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

    try {
      java.io.File jesFile = new java.io.File(timelinesDir, selectedName + ".jes");
      String code = java.nio.file.Files.readString(jesFile.toPath());
      return CodeImporter.importCode(selectedName, code);
    } catch (Exception ex) {
      Alert alert = new Alert(Alert.AlertType.WARNING);
      EditorTheme.apply(alert);
      alert.setTitle("Import Failed");
      alert.setHeaderText("Could not import timeline '" + selectedName + "'");
      alert.setContentText(ex.getMessage());
      alert.showAndWait();
      return null;
    }
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
