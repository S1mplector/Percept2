package com.jvn.editor;

import java.io.File;
import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.jvn.editor.ui.EditorDialogs;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.NewProjectWizard;
import com.jvn.editor.ui.RunConsoleView;
import com.jvn.editor.ui.WelcomeCenterView;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Standalone launcher app for project discovery and runtime/editor entry.
 */
public class JvnLauncherApp extends Application {
  private static final String EDITOR_OPEN_PROJECT_PROPERTY = "jvn.editor.openProject";
  private static final String LAUNCHER_START_PROJECT_PROPERTY = "jvn.launcher.project";

  private Stage primaryStage;
  private WelcomeCenterView welcomeView;
  private File workspaceRoot;
  private File currentProject;

  private final Label statusLabel = new Label("Ready");
  private MenuItem runProjectMenuItem;
  private MenuItem copyProjectPathMenuItem;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    primaryStage = stage;
    workspaceRoot = resolveWorkspaceRoot();

    BorderPane root = new BorderPane();
    root.getStyleClass().add("jvn-launcher-root");

    welcomeView = new WelcomeCenterView();
    welcomeView.setWelcomeHeading("Welcome to JVN Launcher");
    welcomeView.setWelcomeIntro("");
    welcomeView.setVersionChipVisible(false);
    welcomeView.setEditorVersion(resolveVersionLabel());
    welcomeView.setWorkspaceRoot(workspaceRoot);
    welcomeView.setOnCreateProject(this::createNewProject);
    welcomeView.setOnOpenProjectDialog(this::chooseProjectDirectory);
    welcomeView.setOnOpenSelectedProject(() -> launchEditor(currentProject));
    welcomeView.setOnRunSelectedProject(this::runSelectedProject);
    welcomeView.setOnOpenRecentProject(projectDir -> {
      if (projectDir == null || !projectDir.isDirectory()) return;
      setCurrentProject(projectDir, false);
      statusLabel.setText("Selected project: " + displayProjectName(projectDir));
    });

    statusLabel.getStyleClass().add("jvn-launcher-status");
    HBox statusBar = new HBox(statusLabel);
    statusBar.getStyleClass().add("jvn-launcher-status-bar");
    statusBar.setPadding(new Insets(8, 4, 2, 4));

    MenuBar menuBar = buildMenuBar();
    root.setTop(menuBar);
    root.setCenter(welcomeView);
    root.setBottom(statusBar);
    BorderPane.setMargin(welcomeView, new Insets(12, 12, 0, 12));
    BorderPane.setMargin(statusBar, new Insets(0, 12, 8, 12));

    Scene scene = new Scene(root, 1320, 860);
    EditorTheme.apply(scene);

    stage.setTitle("JVN Launcher");
    stage.setScene(scene);
    stage.setMinWidth(1160);
    stage.setMinHeight(760);
    stage.show();

    setCurrentProject(resolveStartupProject(), false);
    refreshButtonState();
    statusLabel.setText("Workspace: " + displayPath(workspaceRoot));
  }

  private MenuBar buildMenuBar() {
    MenuBar menuBar = new MenuBar();
    menuBar.getStyleClass().add("jvn-launcher-menubar");

    Menu menuFile = new Menu("File");
    MenuItem miNewProject = new MenuItem("New Project...");
    miNewProject.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
    miNewProject.setOnAction(e -> createNewProject());
    MenuItem miChooseProject = new MenuItem("Choose Project...");
    miChooseProject.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
    miChooseProject.setOnAction(e -> chooseProjectDirectory());
    MenuItem miOpenEditor = new MenuItem("Open Editor");
    miOpenEditor.setAccelerator(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN));
    miOpenEditor.setOnAction(e -> launchEditor(currentProject));
    MenuItem miExit = new MenuItem("Exit Launcher");
    miExit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
    miExit.setOnAction(e -> primaryStage.close());
    menuFile.getItems().addAll(miNewProject, miChooseProject, miOpenEditor, new SeparatorMenuItem(), miExit);

    Menu menuEdit = new Menu("Edit");
    MenuItem miRefresh = new MenuItem("Refresh Projects");
    miRefresh.setAccelerator(new KeyCodeCombination(KeyCode.F5));
    miRefresh.setOnAction(e -> refreshWelcomeProjects());
    copyProjectPathMenuItem = new MenuItem("Copy Selected Project Path");
    copyProjectPathMenuItem.setAccelerator(new KeyCodeCombination(
        KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    copyProjectPathMenuItem.setOnAction(e -> copySelectedProjectPath());
    menuEdit.getItems().addAll(miRefresh, copyProjectPathMenuItem);

    Menu menuProject = new Menu("Project");
    runProjectMenuItem = new MenuItem("Run Selected Project");
    runProjectMenuItem.setAccelerator(new KeyCodeCombination(
        KeyCode.R, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    runProjectMenuItem.setOnAction(e -> runSelectedProject());
    MenuItem miProjectOpenInEditor = new MenuItem("Open Selected Project in Editor");
    miProjectOpenInEditor.setOnAction(e -> launchEditor(currentProject));
    MenuItem miProjectClearSelection = new MenuItem("Clear Selected Project");
    miProjectClearSelection.setOnAction(e -> {
      setCurrentProject(null, false);
      statusLabel.setText("Selected project cleared");
    });
    menuProject.getItems().addAll(runProjectMenuItem, miProjectOpenInEditor, new SeparatorMenuItem(), miProjectClearSelection);

    Menu menuView = new Menu("View");
    Menu menuTheme = new Menu("Theme");
    ToggleGroup themeGroup = new ToggleGroup();
    RadioMenuItem miThemeDark = new RadioMenuItem("Dark");
    miThemeDark.setToggleGroup(themeGroup);
    RadioMenuItem miThemeLight = new RadioMenuItem("Light");
    miThemeLight.setToggleGroup(themeGroup);
    Runnable syncTheme = () -> {
      boolean light = EditorTheme.theme() == EditorTheme.Theme.LIGHT;
      miThemeDark.setSelected(!light);
      miThemeLight.setSelected(light);
    };
    syncTheme.run();
    miThemeDark.setOnAction(e -> setTheme(EditorTheme.Theme.DARK));
    miThemeLight.setOnAction(e -> setTheme(EditorTheme.Theme.LIGHT));
    menuTheme.getItems().addAll(miThemeDark, miThemeLight);
    MenuItem miViewRefresh = new MenuItem("Refresh Projects");
    miViewRefresh.setOnAction(e -> refreshWelcomeProjects());
    menuView.getItems().addAll(menuTheme, new SeparatorMenuItem(), miViewRefresh);
    menuView.setOnShowing(e -> syncTheme.run());

    Menu menuHelp = new Menu("Help");
    MenuItem miAbout = new MenuItem("About JVN Launcher");
    miAbout.setOnAction(e -> EditorDialogs.info(primaryStage,
        "About JVN Launcher",
        "JVN Launcher " + resolveVersionLabel()));
    menuHelp.getItems().add(miAbout);

    menuBar.getMenus().addAll(menuFile, menuEdit, menuProject, menuView, menuHelp);
    return menuBar;
  }

  private void chooseProjectDirectory() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Choose JVN Project Directory");
    File initial = currentProject != null && currentProject.isDirectory()
        ? currentProject
        : workspaceRoot;
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File dir = chooser.showDialog(primaryStage);
    if (dir == null) return;
    if (!isProjectDirectory(dir)) {
      EditorDialogs.warning(primaryStage,
          "Invalid Project",
          "Selected folder does not contain a jvn.project manifest.");
      return;
    }
    setCurrentProject(dir, true);
    statusLabel.setText("Selected project: " + dir.getName());
  }

  private void createNewProject() {
    File projectDir = NewProjectWizard.showAndWait(primaryStage);
    if (projectDir == null) return;
    setCurrentProject(projectDir, true);
    statusLabel.setText("Created project: " + projectDir.getName());
  }

  private void setCurrentProject(File projectDir, boolean rememberVisit) {
    File resolved = normalizeProjectDirectory(projectDir);
    currentProject = resolved;
    if (welcomeView != null) {
      welcomeView.setCurrentProject(resolved);
      if (rememberVisit && resolved != null) {
        welcomeView.markProjectVisited(resolved);
      }
    }
    refreshButtonState();
  }

  private void refreshButtonState() {
    boolean hasProject = currentProject != null && currentProject.isDirectory();
    if (runProjectMenuItem != null) runProjectMenuItem.setDisable(!hasProject);
    if (copyProjectPathMenuItem != null) copyProjectPathMenuItem.setDisable(!hasProject);
  }

  private void refreshWelcomeProjects() {
    if (welcomeView != null) {
      welcomeView.refresh();
      statusLabel.setText("Project list refreshed");
    }
  }

  private void copySelectedProjectPath() {
    if (currentProject == null || !currentProject.isDirectory()) {
      statusLabel.setText("No selected project to copy");
      return;
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(currentProject.getAbsolutePath());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied project path: " + displayProjectName(currentProject));
  }

  private void setTheme(EditorTheme.Theme theme) {
    if (theme == null || theme == EditorTheme.theme()) return;
    EditorTheme.setTheme(theme);
    for (Window window : Window.getWindows()) {
      if (window != null && window.getScene() != null) {
        EditorTheme.apply(window.getScene());
      }
    }
    statusLabel.setText("Theme: " + (theme == EditorTheme.Theme.LIGHT ? "Light" : "Dark"));
  }

  private void runSelectedProject() {
    if (currentProject == null || !currentProject.isDirectory()) {
      EditorDialogs.info(primaryStage, "Run Project", "Select a project first.");
      return;
    }

    Properties manifest = loadManifest(currentProject);
    if (manifest == null) {
      EditorDialogs.error(primaryStage, "Run Project", "Could not read jvn.project from selected project.");
      return;
    }

    String type = manifest.getProperty("type", "gradle").trim().toLowerCase(Locale.ROOT);
    switch (type) {
      case "gradle" -> {
        String path = manifest.getProperty("path", ":runtime").trim();
        String task = manifest.getProperty("task", "run").trim();
        String args = manifest.getProperty("args", "-x test");
        runGradle(currentProject, composeGradleTask(path, task), splitArgs(args), "Run Project");
      }
      case "vn" -> runVnProjectInRuntime(currentProject, manifest);
      case "jes" -> {
        statusLabel.setText("JES projects run from the editor");
        launchEditor(currentProject);
      }
      default -> EditorDialogs.warning(primaryStage,
          "Unknown Project Type",
          "Unsupported project type `" + type + "` in jvn.project.");
    }
  }

  private void runGradle(File root, String task, String[] args, String title) {
    if (root == null || !root.isDirectory()) {
      EditorDialogs.error(primaryStage, title, "Invalid project/workspace directory.");
      return;
    }

    File executionRoot = findGradleRoot(root);
    if (executionRoot == null) executionRoot = root;
    final File runRoot = executionRoot;

    boolean windows = isWindowsOs();
    File gradlew = new File(runRoot, windows ? "gradlew.bat" : "gradlew");
    File gradleUserHome = new File(runRoot, ".jvn-gradle-user-home");

    try {
      if (!gradleUserHome.exists()) gradleUserHome.mkdirs();
      List<String> cmd = new ArrayList<>();
      cmd.add(gradlew.exists() ? gradlew.getAbsolutePath() : "gradle");
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
        pb.directory(runRoot);
        pb.redirectErrorStream(true);
        pb.environment().put("GRADLE_USER_HOME", gradleUserHome.getAbsolutePath());
        return pb.start();
      };
      console.setProcessStarter(starter);

      Stage logStage = new Stage();
      logStage.initOwner(primaryStage);
      logStage.setTitle(title);
      Scene logScene = new Scene(console, 980, 620);
      EditorTheme.apply(logScene);
      logStage.setScene(logScene);
      logStage.setMinWidth(860);
      logStage.setMinHeight(520);
      logStage.show();

      console.startProcess(starter.start());
      statusLabel.setText("Running " + task + " for " + runRoot.getName());
    } catch (Exception ex) {
      EditorDialogs.error(primaryStage, title, "Failed to start process: " + ex.getMessage());
    }
  }

  private void runVnProjectInRuntime(File root, Properties manifest) {
    if (root == null || manifest == null) return;
    File workspace = workspaceRoot != null ? workspaceRoot : resolveWorkspaceRoot();
    if (workspace == null || !workspace.isDirectory()) {
      EditorDialogs.error(primaryStage, "Run Project", "Workspace root not found for :runtime:run.");
      return;
    }

    String entryVns = manifest.getProperty("entryVns", "").trim();
    String runtimeScript = normalizeRuntimeScriptPath(entryVns);
    StringBuilder runtimeArgs = new StringBuilder();
    runtimeArgs.append("--assets ").append(quoteCliArg(root.getAbsolutePath()));
    if (runtimeScript != null && !runtimeScript.isBlank()) {
      runtimeArgs.append(" --script ").append(quoteCliArg(runtimeScript));
    }
    String title = manifest.getProperty("name", "").trim();
    if (!title.isBlank()) runtimeArgs.append(" --title ").append(quoteCliArg(title));
    String width = manifest.getProperty("width", "").trim();
    if (!width.isBlank()) runtimeArgs.append(" --width ").append(width);
    String height = manifest.getProperty("height", "").trim();
    if (!height.isBlank()) runtimeArgs.append(" --height ").append(height);
    String runtimeUi = manifest.getProperty("runtime.ui", "").trim();
    if (!runtimeUi.isBlank()) runtimeArgs.append(" --ui ").append(quoteCliArg(runtimeUi));
    String runtimeAudio = manifest.getProperty("runtime.audio", "").trim();
    if (!runtimeAudio.isBlank()) runtimeArgs.append(" --audio ").append(quoteCliArg(runtimeAudio));
    String runtimeLocale = manifest.getProperty("runtime.locale", "").trim();
    if (!runtimeLocale.isBlank()) runtimeArgs.append(" --locale ").append(quoteCliArg(runtimeLocale));
    runtimeArgs.append(" --perf-hud");

    runGradle(workspace, ":runtime:run", new String[] {"--args=" + runtimeArgs}, "JVN Runtime");
  }

  private void launchEditor(File projectDir) {
    try {
      List<String> command = new ArrayList<>();
      command.add(resolveJavaExecutable());
      command.addAll(resolveForwardedJvmArgs());
      command.add("-cp");
      command.add(System.getProperty("java.class.path", ""));
      command.add("-Djvn.editor.theme=" + (EditorTheme.theme() == EditorTheme.Theme.LIGHT ? "light" : "dark"));
      if (projectDir != null && projectDir.isDirectory()) {
        command.add("-D" + EDITOR_OPEN_PROJECT_PROPERTY + "=" + projectDir.getAbsolutePath());
      }
      command.add(EditorApp.class.getName());

      ProcessBuilder pb = new ProcessBuilder(command);
      if (workspaceRoot != null && workspaceRoot.isDirectory()) {
        pb.directory(workspaceRoot);
      }
      pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
      pb.redirectError(ProcessBuilder.Redirect.DISCARD);
      pb.start();

      statusLabel.setText(projectDir == null
          ? "Editor launched"
          : "Editor launched for " + projectDir.getName());
    } catch (Exception ex) {
      EditorDialogs.error(primaryStage, "Open Editor", "Failed to launch editor: " + ex.getMessage());
    }
  }

  private String resolveJavaExecutable() {
    String javaHome = System.getProperty("java.home", "").trim();
    if (!javaHome.isBlank()) {
      File java = new File(javaHome, isWindowsOs() ? "bin/java.exe" : "bin/java");
      if (java.isFile()) return java.getAbsolutePath();
    }
    return "java";
  }

  private List<String> resolveForwardedJvmArgs() {
    List<String> inputArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
    List<String> forwarded = new ArrayList<>();
    for (int i = 0; i < inputArgs.size(); i++) {
      String arg = inputArgs.get(i);
      if ("-XstartOnFirstThread".equals(arg)) {
        forwarded.add(arg);
        continue;
      }
      if ("--module-path".equals(arg) || "--add-modules".equals(arg) || "--add-opens".equals(arg)
          || "--add-exports".equals(arg) || "--patch-module".equals(arg)) {
        forwarded.add(arg);
        if (i + 1 < inputArgs.size()) forwarded.add(inputArgs.get(++i));
        continue;
      }
      if (arg.startsWith("--module-path=") || arg.startsWith("--add-modules=")
          || arg.startsWith("--add-opens=") || arg.startsWith("--add-exports=")
          || arg.startsWith("--patch-module=")) {
        forwarded.add(arg);
      }
    }
    return forwarded;
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
      File settingsKts = new File(cur, "settings.gradle.kts");
      File settingsGroovy = new File(cur, "settings.gradle");
      if ((gradlew.exists() || gradlewBat.exists()) && (settingsKts.exists() || settingsGroovy.exists())) {
        return cur;
      }
      cur = cur.getParentFile();
    }
    return null;
  }

  private File resolveStartupProject() {
    String value = System.getProperty(LAUNCHER_START_PROJECT_PROPERTY, "").trim();
    if (value.isBlank()) return null;
    return normalizeProjectDirectory(new File(value));
  }

  private File normalizeProjectDirectory(File dir) {
    if (dir == null) return null;
    File candidate = dir.isFile() && "jvn.project".equalsIgnoreCase(dir.getName())
        ? dir.getParentFile()
        : dir;
    if (!isProjectDirectory(candidate)) return null;
    try {
      return candidate.getCanonicalFile();
    } catch (Exception ignore) {
      return candidate.getAbsoluteFile();
    }
  }

  private boolean isProjectDirectory(File dir) {
    return dir != null && dir.isDirectory() && new File(dir, "jvn.project").isFile();
  }

  private Properties loadManifest(File dir) {
    if (dir == null) return null;
    try (FileInputStream in = new FileInputStream(new File(dir, "jvn.project"))) {
      Properties props = new Properties();
      props.load(in);
      return props;
    } catch (Exception ignore) {
      return null;
    }
  }

  private String[] splitArgs(String raw) {
    if (raw == null || raw.isBlank()) return new String[0];
    return raw.trim().split("\\s+");
  }

  private String composeGradleTask(String path, String task) {
    String p = path == null ? "" : path.trim();
    String t = task == null ? "run" : task.trim();
    if (t.startsWith(":")) return t;
    if (p.isBlank()) return t;
    if (!p.startsWith(":")) p = ":" + p;
    return p + ":" + t;
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
    String value = raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    return "\"" + value + "\"";
  }

  private String resolveVersionLabel() {
    String version = System.getProperty("jvn.version");
    if (version != null && !version.isBlank()) return version.trim();
    Package pkg = EditorApp.class.getPackage();
    if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
      return pkg.getImplementationVersion().trim();
    }
    return "dev";
  }

  private String displayProjectName(File dir) {
    if (dir == null) return "none selected";
    String name = dir.getName();
    if (name == null || name.isBlank()) return dir.getAbsolutePath();
    return name;
  }

  private String displayPath(File dir) {
    if (dir == null) return "--";
    String path = dir.getAbsolutePath();
    String home = System.getProperty("user.home", "").trim();
    if (!home.isBlank() && path.startsWith(home)) return "~" + path.substring(home.length());
    return path;
  }

  private boolean isWindowsOs() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}
