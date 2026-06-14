package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class EngineHealthCenterView extends BorderPane {
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final long FAST_COMMAND_TIMEOUT_MS = 1_500L;
  private static final long SLOW_COMMAND_TIMEOUT_MS = 4_000L;

  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "jvn-health-center");
    t.setDaemon(true);
    return t;
  });

  private final Label summaryLabel = new Label("Run checks to inspect the editor, project, and toolchain.");
  private final Label lastRunLabel = new Label("Last run: never");
  private final Label scoreLabel = new Label("Score --");
  private final Label passChip = new Label("OK 0");
  private final Label warnChip = new Label("CHECK 0");
  private final Label errorChip = new Label("FIX 0");
  private final ProgressIndicator busy = new ProgressIndicator();
  private final VBox resultsBox = new VBox(8);
  private final TextArea reportArea = new TextArea();

  private final CheckBox chkRuntime = option("Runtime", true, "Java runtime, JDK, JavaFX, JVM flags, memory, and GC.");
  private final CheckBox chkGradle = option("Gradle", true, "Wrapper files, executable bit, wrapper version, and optional process check.");
  private final CheckBox chkGit = option("Git", true, "Git command, repository, branch, remotes, identity, and changed files.");
  private final CheckBox chkGitHub = option("GitHub", true, "GitHub CLI installation and authentication status.");
  private final CheckBox chkProject = option("Project", true, "Project structure, modules, source files, assets, scripts, and build files.");
  private final CheckBox chkStorage = option("Storage", true, "Workspace writability, disk headroom, cache folders, and log locations.");
  private final CheckBox chkDeepGradle = option("Run Gradle --version", false, "Starts the Gradle wrapper. Slower, but catches JVM/toolchain process issues.");
  private final CheckBox chkInventory = option("Deep inventory", true, "Scans the project tree for Java, VNS, JES, Gradle, asset, and docs counts.");

  private volatile Task<List<HealthCheck>> activeTask;
  private List<HealthCheck> currentChecks = List.of();
  private List<EditorWorkspaceHubView.SetupIssue> startupIssues = List.of();
  private File workspaceRoot;
  private File projectRoot;
  private Consumer<File> onOpenPath;

  public EngineHealthCenterView() {
    getStyleClass().addAll("engine-health-root", "sidebar-tool-root");
    buildUi();
  }

  public void setWorkspaceRoot(File workspaceRoot) {
    this.workspaceRoot = normalizeDir(workspaceRoot);
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = normalizeDir(projectRoot);
  }

  public void setStartupIssues(Collection<EditorWorkspaceHubView.SetupIssue> issues) {
    this.startupIssues = issues == null ? List.of() : new ArrayList<>(issues);
  }

  public void setOnOpenPath(Consumer<File> onOpenPath) {
    this.onOpenPath = onOpenPath;
  }

  public void refreshStatus() {
    Task<List<HealthCheck>> previous = activeTask;
    if (previous != null && previous.isRunning()) previous.cancel();

    busy.setVisible(true);
    busy.setManaged(true);
    summaryLabel.setText("Running selected health checks...");
    resultsBox.getChildren().setAll(new Label("Collecting diagnostics..."));

    Task<List<HealthCheck>> task = new Task<>() {
      @Override
      protected List<HealthCheck> call() {
        return collectChecks();
      }
    };
    activeTask = task;
    task.setOnSucceeded(e -> {
      List<HealthCheck> checks = task.getValue() == null ? List.of() : task.getValue();
      currentChecks = checks;
      renderChecks(checks);
      busy.setVisible(false);
      busy.setManaged(false);
      lastRunLabel.setText("Last run: " + LocalTime.now().format(TIME_FORMAT));
    });
    task.setOnFailed(e -> {
      Throwable ex = task.getException();
      currentChecks = List.of(new HealthCheck(
          "Health Center",
          Severity.ERROR,
          "Health scan crashed",
          ex == null ? "Unknown failure." : ex.getClass().getSimpleName(),
          ex == null ? "" : String.valueOf(ex.getMessage()),
          "Copy the report and check the editor log for the stack trace.",
          "",
          null,
          List.of("health", "internal")));
      renderChecks(currentChecks);
      busy.setVisible(false);
      busy.setManaged(false);
      lastRunLabel.setText("Last run failed: " + LocalTime.now().format(TIME_FORMAT));
    });
    worker.submit(task);
  }

  public void dispose() {
    Task<List<HealthCheck>> task = activeTask;
    if (task != null) task.cancel();
    worker.shutdownNow();
  }

  private void buildUi() {
    Label title = new Label("Engine Health Center");
    title.getStyleClass().add("engine-health-title");
    Label subtitle = new Label("Detailed diagnostics for Java, Gradle, Git, GitHub auth, project shape, storage, memory, and editor runtime.");
    subtitle.getStyleClass().add("engine-health-subtitle");
    subtitle.setWrapText(true);
    HBox titleRow = new HBox(6, title, SidebarToolHelp.button(this, "Engine Health Center", """
        The Engine Health Center runs environment and project checks from inside the editor.
        Use it when builds fail, Git/GitHub actions behave oddly, Java/Gradle disagree, or a project looks incomplete.
        Safe fixes are offered as buttons; system-level installs and authentication steps are shown as explicit commands to copy.
        """));
    titleRow.setAlignment(Pos.CENTER_LEFT);
    Region titleSpacer = new Region();
    HBox.setHgrow(titleSpacer, Priority.ALWAYS);
    busy.setMaxSize(18, 18);
    busy.setVisible(false);
    busy.setManaged(false);
    HBox headerRow = new HBox(8, titleRow, titleSpacer, busy);
    headerRow.setAlignment(Pos.CENTER_LEFT);

    Button refresh = actionButton("Run Checks", CssIcon.redo("#8ecaff"), "Run the selected health checks.");
    refresh.setOnAction(e -> refreshStatus());
    Button selectAll = actionButton("All", CssIcon.plusBold("#8bcf98"), "Enable all check groups.");
    selectAll.setOnAction(e -> setAllOptions(true));
    Button fast = actionButton("Fast", CssIcon.play("#f2c86b"), "Use fast checks and skip Gradle process launch.");
    fast.setOnAction(e -> {
      setAllOptions(true);
      chkDeepGradle.setSelected(false);
      refreshStatus();
    });
    Button copyReport = actionButton("Copy Report", CssIcon.copy("#d6cab8"), "Copy the full health report.");
    copyReport.setOnAction(e -> copyText(reportText(currentChecks)));
    Button copyFixes = actionButton("Copy Fixes", CssIcon.save("#f0b673"), "Copy every suggested command or fix.");
    copyFixes.setOnAction(e -> copyText(fixText(currentChecks)));

    HBox actions = new HBox(6, refresh, fast, selectAll, copyReport, copyFixes);
    actions.setAlignment(Pos.CENTER_LEFT);

    passChip.getStyleClass().addAll("engine-health-count-chip", "engine-health-ok");
    warnChip.getStyleClass().addAll("engine-health-count-chip", "engine-health-warn");
    errorChip.getStyleClass().addAll("engine-health-count-chip", "engine-health-error");
    scoreLabel.getStyleClass().add("engine-health-score");
    FlowPane scoreRow = new FlowPane(6, 6, scoreLabel, passChip, warnChip, errorChip);
    scoreRow.setAlignment(Pos.CENTER_LEFT);

    FlowPane options = new FlowPane(8, 7,
        chkRuntime, chkGradle, chkGit, chkGitHub, chkProject, chkStorage, chkDeepGradle, chkInventory);
    options.getStyleClass().add("engine-health-options");

    summaryLabel.getStyleClass().add("engine-health-summary");
    summaryLabel.setWrapText(true);
    lastRunLabel.getStyleClass().add("engine-health-muted");

    VBox header = new VBox(9, headerRow, subtitle, actions, scoreRow, summaryLabel, lastRunLabel, options);
    header.getStyleClass().add("engine-health-header");
    setTop(header);

    resultsBox.getStyleClass().add("engine-health-results");
    ScrollPane scroll = new ScrollPane(resultsBox);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("engine-health-scroll");

    reportArea.getStyleClass().add("engine-health-report");
    reportArea.setEditable(false);
    reportArea.setWrapText(false);
    reportArea.setPrefRowCount(7);

    VBox center = new VBox(8, scroll, reportArea);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    setCenter(center);
    renderChecks(List.of());
  }

  private List<HealthCheck> collectChecks() {
    List<HealthCheck> checks = new ArrayList<>();
    checks.addAll(startupIssueChecks());
    if (chkRuntime.isSelected()) collectRuntimeChecks(checks);
    if (chkGradle.isSelected()) collectGradleChecks(checks);
    if (chkGit.isSelected()) collectGitChecks(checks);
    if (chkGitHub.isSelected()) collectGitHubChecks(checks);
    if (chkProject.isSelected()) collectProjectChecks(checks);
    if (chkStorage.isSelected()) collectStorageChecks(checks);
    checks.sort(Comparator
        .comparingInt((HealthCheck c) -> c.severity().rank())
        .thenComparing(HealthCheck::category)
        .thenComparing(HealthCheck::title));
    return checks;
  }

  private List<HealthCheck> startupIssueChecks() {
    if (startupIssues == null || startupIssues.isEmpty()) return List.of();
    List<HealthCheck> checks = new ArrayList<>();
    for (EditorWorkspaceHubView.SetupIssue issue : startupIssues) {
      if (issue == null || issue.title() == null || issue.title().isBlank()) continue;
      Severity severity = switch (safe(issue.severity()).toLowerCase(Locale.ROOT)) {
        case "error", "fix" -> Severity.ERROR;
        case "warn", "warning", "check" -> Severity.WARN;
        default -> Severity.INFO;
      };
      checks.add(new HealthCheck(
          "Startup Preflight",
          severity,
          issue.title(),
          safe(issue.detail()),
          "Captured during editor startup.",
          safe(issue.fix()),
          extractCommand(issue.fix()),
          null,
          List.of("startup", "onboarding")));
    }
    return checks;
  }

  private void collectRuntimeChecks(List<HealthCheck> checks) {
    String javaVersion = System.getProperty("java.version", "unknown");
    String javaHome = System.getProperty("java.home", "unknown");
    String vendor = System.getProperty("java.vendor", "unknown");
    String vm = System.getProperty("java.vm.name", "unknown");
    String fx = System.getProperty("javafx.version", "unknown");
    boolean hasJavac = ToolProvider.getSystemJavaCompiler() != null;
    checks.add(new HealthCheck(
        "Java Runtime",
        hasJavac ? Severity.OK : Severity.ERROR,
        hasJavac ? "Full JDK is available" : "Full JDK is missing",
        "Java " + javaVersion + " from " + vendor + ". JavaFX " + fx + ".",
        "java.home=" + javaHome + "\njava.vm.name=" + vm + "\njava.class.version=" + System.getProperty("java.class.version", "unknown"),
        hasJavac ? "No action needed." : "Install JDK 21 and launch JVN with JAVA_HOME pointing at that JDK.",
        hasJavac ? "" : "export JAVA_HOME=/path/to/jdk-21",
        fileOrNull(javaHome),
        List.of("java", "jdk", "javac")));

    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    MemoryUsage heap = memory.getHeapMemoryUsage();
    long usedMb = heap.getUsed() / (1024L * 1024L);
    long maxMb = heap.getMax() / (1024L * 1024L);
    Severity heapSeverity = maxMb < 768 ? Severity.WARN : Severity.OK;
    checks.add(new HealthCheck(
        "Java Runtime",
        heapSeverity,
        "Editor heap budget",
        usedMb + " MB used / " + maxMb + " MB max.",
        "Non-heap used: " + memory.getNonHeapMemoryUsage().getUsed() / (1024L * 1024L) + " MB\n"
            + "Processors: " + Runtime.getRuntime().availableProcessors(),
        heapSeverity == Severity.OK ? "No action needed." : "Launch with a larger heap, for example -Xmx2g.",
        heapSeverity == Severity.OK ? "" : "-Xmx2g",
        null,
        List.of("memory", "heap", "jvm")));

    long gcCount = 0L;
    long gcTime = 0L;
    List<String> gcNames = new ArrayList<>();
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      gcNames.add(gc.getName());
      if (gc.getCollectionCount() > 0) gcCount += gc.getCollectionCount();
      if (gc.getCollectionTime() > 0) gcTime += gc.getCollectionTime();
    }
    checks.add(new HealthCheck(
        "Java Runtime",
        Severity.INFO,
        "Garbage collectors",
        String.join(", ", gcNames),
        "Collections: " + gcCount + "\nCollection time: " + gcTime + " ms",
        "Useful when editor stutter correlates with GC spikes.",
        "",
        null,
        List.of("gc", "performance")));
  }

  private void collectGradleChecks(List<HealthCheck> checks) {
    File workspace = effectiveWorkspace();
    if (workspace == null) {
      checks.add(problem("Gradle", "Workspace not resolved", "Gradle wrapper checks need a workspace root.", "Open JVN from its repository root."));
      return;
    }
    Path gradlew = workspace.toPath().resolve(isWindows() ? "gradlew.bat" : "gradlew");
    Path props = workspace.toPath().resolve("gradle/wrapper/gradle-wrapper.properties");
    boolean wrapperPresent = Files.isRegularFile(gradlew);
    boolean propsPresent = Files.isRegularFile(props);
    checks.add(new HealthCheck(
        "Gradle",
        wrapperPresent && propsPresent ? Severity.OK : Severity.ERROR,
        wrapperPresent && propsPresent ? "Gradle wrapper files found" : "Gradle wrapper files missing",
        "gradlew=" + pathText(gradlew) + "\nwrapper properties=" + pathText(props),
        propsPresent ? readGradleDistribution(props) : "No wrapper properties file.",
        wrapperPresent && propsPresent ? "No action needed." : "Restore gradlew and gradle/wrapper/gradle-wrapper.properties.",
        "",
        propsPresent ? props.toFile() : workspace,
        List.of("gradle", "wrapper")));

    if (wrapperPresent && !isWindows()) {
      boolean executable = Files.isExecutable(gradlew);
      checks.add(new HealthCheck(
          "Gradle",
          executable ? Severity.OK : Severity.WARN,
          executable ? "Gradle wrapper is executable" : "Gradle wrapper is not executable",
          gradlew.toAbsolutePath().toString(),
          "POSIX executable bit controls whether the editor can launch ./gradlew directly.",
          executable ? "No action needed." : "Run chmod +x gradlew or use the in-app Fix button.",
          executable ? "" : "chmod +x gradlew",
          gradlew.toFile(),
          executable ? List.of("gradle", "permission") : List.of("gradle", "permission", "fix:chmod")));
    }

    if (chkDeepGradle.isSelected() && wrapperPresent) {
      CommandResult result = runCommand(workspace, List.of(gradlew.toAbsolutePath().toString(), "--version"), SLOW_COMMAND_TIMEOUT_MS);
      checks.add(new HealthCheck(
          "Gradle",
          result.success() ? Severity.OK : Severity.ERROR,
          result.success() ? "Gradle process starts" : "Gradle process failed",
          result.firstLine(),
          result.output(),
          result.success() ? "No action needed." : "Check JAVA_HOME, wrapper permissions, and Gradle cache state.",
          result.success() ? "" : "./gradlew --version",
          workspace,
          List.of("gradle", "process", "java")));
    } else if (!chkDeepGradle.isSelected()) {
      checks.add(new HealthCheck(
          "Gradle",
          Severity.INFO,
          "Gradle process check skipped",
          "Enable 'Run Gradle --version' for a deeper launch check.",
          "Skipping keeps the health center fast and avoids waking the Gradle daemon.",
          "Run the deep check when builds fail before reaching task execution.",
          "./gradlew --version",
          workspace,
          List.of("gradle", "optional")));
    }
  }

  private void collectGitChecks(List<HealthCheck> checks) {
    File root = effectiveProjectOrWorkspace();
    CommandResult git = runCommand(root, List.of("git", "--version"), FAST_COMMAND_TIMEOUT_MS);
    if (!git.success()) {
      checks.add(new HealthCheck(
          "Git",
          Severity.WARN,
          "Git command not available",
          "Version Control features need git on PATH.",
          git.output(),
          "Install Git or launch JVN from an environment where git is on PATH.",
          "git --version",
          null,
          List.of("git", "path")));
      return;
    }
    checks.add(new HealthCheck("Git", Severity.OK, "Git command available", git.firstLine(), git.output(), "No action needed.", "", null, List.of("git")));

    CommandResult inside = runCommand(root, List.of("git", "rev-parse", "--is-inside-work-tree"), FAST_COMMAND_TIMEOUT_MS);
    boolean inRepo = inside.success() && "true".equalsIgnoreCase(inside.firstLine());
    checks.add(new HealthCheck(
        "Git",
        inRepo ? Severity.OK : Severity.INFO,
        inRepo ? "Repository detected" : "No Git repository detected",
        inRepo ? "Git work tree is active." : "The current project/workspace is not inside a Git work tree.",
        inside.output(),
        inRepo ? "No action needed." : "Initialize a repository from Version Control if this project should be tracked.",
        inRepo ? "" : "git init",
        root,
        List.of("git", "repo")));
    if (!inRepo) return;

    addGitCommandCheck(checks, root, "Current branch", List.of("git", "branch", "--show-current"), Severity.INFO, "Create or switch branches from Version Control.");
    addGitCommandCheck(checks, root, "Remote URLs", List.of("git", "remote", "-v"), Severity.INFO, "Add origin when you are ready to publish.");
    CommandResult status = runCommand(root, List.of("git", "status", "--short"), FAST_COMMAND_TIMEOUT_MS);
    int changed = status.output().isBlank() ? 0 : status.output().split("\\R").length;
    checks.add(new HealthCheck(
        "Git",
        Severity.INFO,
        "Working tree changes",
        changed + " changed file(s).",
        status.output().isBlank() ? "Working tree is clean." : status.output(),
        changed == 0 ? "No action needed." : "Review and commit from Version Control when ready.",
        "git status --short",
        root,
        List.of("git", "status")));

    CommandResult name = runCommand(root, List.of("git", "config", "--global", "--get", "user.name"), FAST_COMMAND_TIMEOUT_MS);
    CommandResult email = runCommand(root, List.of("git", "config", "--global", "--get", "user.email"), FAST_COMMAND_TIMEOUT_MS);
    boolean identityOk = name.success() && !name.output().isBlank() && email.success() && !email.output().isBlank();
    checks.add(new HealthCheck(
        "Git",
        identityOk ? Severity.OK : Severity.WARN,
        identityOk ? "Global Git identity configured" : "Global Git identity incomplete",
        identityOk ? name.output() + " <" + email.output() + ">" : "Missing user.name or user.email.",
        "user.name=" + valueOrMissing(name.output()) + "\nuser.email=" + valueOrMissing(email.output()),
        identityOk ? "No action needed." : "Set both values before committing.",
        identityOk ? "" : "git config --global user.name \"Your Name\" && git config --global user.email \"you@example.com\"",
        null,
        List.of("git", "identity")));
  }

  private void collectGitHubChecks(List<HealthCheck> checks) {
    File root = effectiveProjectOrWorkspace();
    CommandResult gh = runCommand(root, List.of("gh", "--version"), FAST_COMMAND_TIMEOUT_MS);
    if (!gh.success()) {
      checks.add(new HealthCheck(
          "GitHub",
          Severity.INFO,
          "GitHub CLI not available",
          "Publishing and PR helpers need gh.",
          gh.output(),
          "Install GitHub CLI, then authenticate.",
          "gh auth login",
          null,
          List.of("github", "gh", "auth")));
      return;
    }
    checks.add(new HealthCheck("GitHub", Severity.OK, "GitHub CLI available", gh.firstLine(), gh.output(), "No action needed.", "", null, List.of("github", "gh")));
    CommandResult auth = runCommand(root, List.of("gh", "auth", "status"), FAST_COMMAND_TIMEOUT_MS);
    checks.add(new HealthCheck(
        "GitHub",
        auth.success() ? Severity.OK : Severity.WARN,
        auth.success() ? "GitHub auth is ready" : "GitHub auth missing",
        auth.success() ? "gh has an authenticated session." : "gh is installed but not authenticated.",
        auth.output(),
        auth.success() ? "No action needed." : "Run gh auth login in your shell.",
        auth.success() ? "" : "gh auth login",
        null,
        List.of("github", "auth")));
  }

  private void collectProjectChecks(List<HealthCheck> checks) {
    File root = effectiveProjectOrWorkspace();
    if (root == null || !root.isDirectory()) {
      checks.add(problem("Project", "No project or workspace loaded", "Project checks need an open folder.", "Open a project directory."));
      return;
    }
    Path path = root.toPath();
    checks.add(fileCheck("Project", "settings.gradle", path.resolve("settings.gradle"), "Root Gradle settings identify modules."));
    checks.add(fileCheck("Project", "build.gradle", path.resolve("build.gradle"), "Root Gradle build file configures shared build logic."));
    checks.add(fileCheck("Project", "src directory", path.resolve("src"), "Source sets usually live under src."));

    if (chkInventory.isSelected()) {
      Inventory inventory = scanInventory(path);
      checks.add(new HealthCheck(
          "Project",
          Severity.INFO,
          "Project inventory",
          inventory.summary(),
          inventory.details(),
          "Use this to spot missing source, script, asset, docs, or Gradle files.",
          "",
          root,
          List.of("project", "inventory", "assets", "scripts")));
    }
  }

  private void collectStorageChecks(List<HealthCheck> checks) {
    File workspace = effectiveWorkspace();
    if (workspace == null) {
      checks.add(problem("Storage", "Workspace not resolved", "Storage checks need a workspace root.", "Launch from the repository root."));
      return;
    }
    boolean writable = workspace.canWrite();
    checks.add(new HealthCheck(
        "Storage",
        writable ? Severity.OK : Severity.ERROR,
        writable ? "Workspace is writable" : "Workspace is not writable",
        workspace.getAbsolutePath(),
        ".jvn state, generated reports, and caches need write access.",
        writable ? "No action needed." : "Fix folder permissions or move the workspace to a writable location.",
        "",
        workspace,
        List.of("storage", "permissions")));
    long usable = workspace.getUsableSpace();
    double gb = usable / (1024.0 * 1024.0 * 1024.0);
    Severity diskSeverity = usable < 512L * 1024L * 1024L ? Severity.WARN : Severity.OK;
    checks.add(new HealthCheck(
        "Storage",
        diskSeverity,
        "Disk headroom",
        String.format(Locale.ROOT, "%.2f GB free", gb),
        workspace.getAbsolutePath(),
        diskSeverity == Severity.OK ? "No action needed." : "Free at least 1 GB before running builds or exports.",
        "",
        workspace,
        List.of("storage", "disk")));
    Path state = workspace.toPath().resolve(".jvn");
    checks.add(new HealthCheck(
        "Storage",
        Files.isDirectory(state) ? Severity.OK : Severity.INFO,
        Files.isDirectory(state) ? "Editor state directory exists" : "Editor state directory not created yet",
        state.toAbsolutePath().toString(),
        "Used for editor-local state and generated diagnostics.",
        Files.isDirectory(state) ? "No action needed." : "It will be created when the editor needs it.",
        "",
        state.toFile(),
        List.of("storage", "state")));
  }

  private void addGitCommandCheck(List<HealthCheck> checks, File root, String title, List<String> command, Severity severity, String fix) {
    CommandResult result = runCommand(root, command, FAST_COMMAND_TIMEOUT_MS);
    checks.add(new HealthCheck(
        "Git",
        result.success() && !result.output().isBlank() ? severity : Severity.INFO,
        title,
        result.output().isBlank() ? "(none)" : result.firstLine(),
        result.output(),
        fix,
        String.join(" ", command),
        root,
        List.of("git")));
  }

  private HealthCheck fileCheck(String category, String title, Path path, String detail) {
    boolean exists = Files.exists(path);
    return new HealthCheck(
        category,
        exists ? Severity.OK : Severity.INFO,
        exists ? title + " found" : title + " missing",
        detail,
        path.toAbsolutePath().toString(),
        exists ? "No action needed." : "This may be fine for subprojects, but root projects usually include it.",
        "",
        path.toFile(),
        List.of("project", "files"));
  }

  private Inventory scanInventory(Path root) {
    int java = 0;
    int vns = 0;
    int jes = 0;
    int gradle = 0;
    int images = 0;
    int audio = 0;
    int docs = 0;
    int dirs = 0;
    int files = 0;
    List<String> modules = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root, 7)) {
      List<Path> paths = stream.limit(10_000).toList();
      for (Path p : paths) {
        if (Files.isDirectory(p)) {
          dirs++;
          if (Files.isRegularFile(p.resolve("build.gradle"))) {
            modules.add(root.relativize(p).toString().isBlank() ? "." : root.relativize(p).toString());
          }
          continue;
        }
        if (!Files.isRegularFile(p)) continue;
        files++;
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) java++;
        else if (name.endsWith(".vns")) vns++;
        else if (name.endsWith(".jes")) jes++;
        else if (name.endsWith(".gradle") || name.endsWith(".gradle.kts")) gradle++;
        else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) images++;
        else if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".ogg")) audio++;
        else if (name.endsWith(".md") || name.endsWith(".txt")) docs++;
      }
    } catch (Exception ex) {
      return new Inventory(files, dirs, java, vns, jes, gradle, images, audio, docs, List.of("Scan failed: " + ex.getMessage()));
    }
    return new Inventory(files, dirs, java, vns, jes, gradle, images, audio, docs, modules);
  }

  private void renderChecks(List<HealthCheck> checks) {
    int ok = 0;
    int warn = 0;
    int error = 0;
    for (HealthCheck check : checks) {
      if (check.severity() == Severity.ERROR) error++;
      else if (check.severity() == Severity.WARN) warn++;
      else if (check.severity() == Severity.OK) ok++;
    }
    passChip.setText("OK " + ok);
    warnChip.setText("CHECK " + warn);
    errorChip.setText("FIX " + error);
    int score = checks.isEmpty() ? 0 : Math.max(0, 100 - error * 18 - warn * 7);
    scoreLabel.setText(checks.isEmpty() ? "Score --" : "Score " + score);
    summaryLabel.setText(checks.isEmpty()
        ? "No checks have run yet."
        : checks.size() + " checks complete across " + categoryCount(checks) + " categories.");

    resultsBox.getChildren().clear();
    if (checks.isEmpty()) {
      Label empty = new Label("Choose health check groups and press Run Checks.");
      empty.getStyleClass().add("engine-health-muted");
      resultsBox.getChildren().add(empty);
    } else {
      Map<String, List<HealthCheck>> byCategory = new LinkedHashMap<>();
      for (HealthCheck check : checks) {
        byCategory.computeIfAbsent(check.category(), k -> new ArrayList<>()).add(check);
      }
      boolean first = true;
      for (Map.Entry<String, List<HealthCheck>> entry : byCategory.entrySet()) {
        if (!first) {
          Separator divider = new Separator();
          divider.getStyleClass().add("engine-health-section-divider");
          resultsBox.getChildren().add(divider);
        }
        resultsBox.getChildren().add(section(entry.getKey(), entry.getValue()));
        first = false;
      }
    }
    reportArea.setText(reportText(checks));
  }

  private Node section(String category, List<HealthCheck> checks) {
    Label title = new Label(category);
    title.getStyleClass().add("engine-health-section-title");
    Label count = new Label(checks.size() + " checks");
    count.getStyleClass().addAll("engine-health-count-chip", "engine-health-info");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(8, title, spacer, count);
    header.setAlignment(Pos.CENTER_LEFT);
    VBox rows = new VBox(6);
    for (int i = 0; i < checks.size(); i++) {
      rows.getChildren().add(checkRow(checks.get(i)));
      if (i < checks.size() - 1) {
        Separator divider = new Separator();
        divider.getStyleClass().add("engine-health-check-divider");
        rows.getChildren().add(divider);
      }
    }
    VBox section = new VBox(8, header, rows);
    section.getStyleClass().add("engine-health-section");
    return section;
  }

  private Node checkRow(HealthCheck check) {
    Label chip = new Label(check.severity().label());
    chip.getStyleClass().addAll("engine-health-severity", "engine-health-" + check.severity().css());
    Label title = new Label(check.title());
    title.getStyleClass().addAll("engine-health-check-title", "engine-health-check-title-" + check.severity().css());
    title.setWrapText(true);
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button copy = miniButton("Copy", "Copy details and fix.");
    copy.setOnAction(e -> copyText(checkReport(check)));
    Button fix = miniButton("Fix", "Copy or apply the suggested fix.");
    fix.setOnAction(e -> applyFix(check));
    HBox top = new HBox(7, chip, title, spacer, copy, fix);
    top.setAlignment(Pos.CENTER_LEFT);

    Label detail = new Label(check.detail());
    detail.getStyleClass().addAll("engine-health-check-detail", "engine-health-check-detail-" + check.severity().css());
    detail.setWrapText(true);
    Label evidence = new Label(check.evidence().isBlank() ? "Evidence: none" : "Evidence: " + trimMiddle(check.evidence(), 520));
    evidence.getStyleClass().add("engine-health-evidence");
    evidence.setWrapText(true);
    FlowPane tags = new FlowPane(5, 5);
    for (String tag : check.tags()) {
      Label tagLabel = new Label(tag);
      tagLabel.getStyleClass().addAll("engine-health-tag", "engine-health-tag-" + check.severity().css());
      tags.getChildren().add(tagLabel);
    }
    Label fixText = new Label("Fix: " + valueOrMissing(check.fix()));
    fixText.getStyleClass().add("engine-health-fix");
    fixText.setWrapText(true);

    VBox row = new VBox(5, top, detail, evidence, tags, fixText);
    row.getStyleClass().addAll("engine-health-check", "engine-health-check-" + check.severity().css());
    return row;
  }

  private void applyFix(HealthCheck check) {
    if (check.tags().contains("fix:chmod") && check.path() != null) {
      boolean ok = check.path().setExecutable(true, false);
      copyText(check.command().isBlank() ? check.fix() : check.command());
      summaryLabel.setText(ok ? "Applied executable bit and copied the command." : "Could not change executable bit; command copied.");
      refreshStatus();
      return;
    }
    if (check.path() != null && check.path().exists()) {
      openPath(check.path());
      if (!check.command().isBlank()) copyText(check.command());
      return;
    }
    copyText(!check.command().isBlank() ? check.command() : check.fix());
    summaryLabel.setText("Copied suggested fix.");
  }

  private String reportText(List<HealthCheck> checks) {
    if (checks == null || checks.isEmpty()) return "No health report yet.";
    StringBuilder out = new StringBuilder("# JVN Engine Health Report\n");
    out.append("Generated: ").append(LocalTime.now().format(TIME_FORMAT)).append('\n');
    out.append("Workspace: ").append(workspaceRoot == null ? "(none)" : workspaceRoot.getAbsolutePath()).append('\n');
    out.append("Project: ").append(projectRoot == null ? "(none)" : projectRoot.getAbsolutePath()).append("\n\n");
    for (HealthCheck check : checks) {
      out.append(checkReport(check)).append('\n');
    }
    return out.toString();
  }

  private String checkReport(HealthCheck check) {
    return "[" + check.severity().label() + "] " + check.category() + " - " + check.title() + "\n"
        + "Detail: " + check.detail() + "\n"
        + "Evidence: " + valueOrMissing(check.evidence()) + "\n"
        + "Fix: " + valueOrMissing(check.fix()) + "\n"
        + (check.command().isBlank() ? "" : "Command: " + check.command() + "\n");
  }

  private String fixText(List<HealthCheck> checks) {
    if (checks == null || checks.isEmpty()) return "No fixes available.";
    StringBuilder out = new StringBuilder();
    for (HealthCheck check : checks) {
      if (check.fix().isBlank() || "No action needed.".equals(check.fix())) continue;
      out.append("# ").append(check.category()).append(": ").append(check.title()).append('\n');
      out.append(check.command().isBlank() ? check.fix() : check.command()).append("\n\n");
    }
    return out.isEmpty() ? "No fixes available." : out.toString();
  }

  private void setAllOptions(boolean selected) {
    chkRuntime.setSelected(selected);
    chkGradle.setSelected(selected);
    chkGit.setSelected(selected);
    chkGitHub.setSelected(selected);
    chkProject.setSelected(selected);
    chkStorage.setSelected(selected);
    chkInventory.setSelected(selected);
    chkDeepGradle.setSelected(false);
  }

  private static CheckBox option(String text, boolean selected, String tooltip) {
    CheckBox box = new CheckBox(text);
    box.setSelected(selected);
    box.setTooltip(new Tooltip(tooltip));
    return box;
  }

  private Button actionButton(String text, Region icon, String tooltip) {
    Button button = new Button(text);
    button.getStyleClass().add("engine-health-action");
    button.setGraphic(icon);
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private Button miniButton(String text, String tooltip) {
    Button button = new Button(text);
    button.getStyleClass().add("engine-health-mini-button");
    button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private void copyText(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private void openPath(File path) {
    if (path == null) return;
    if (onOpenPath != null && path.isFile()) {
      onOpenPath.accept(path);
      return;
    }
    try {
      Desktop.getDesktop().open(path);
    } catch (Exception ex) {
      summaryLabel.setText("Could not open path: " + ex.getMessage());
    }
  }

  private CommandResult runCommand(File cwd, List<String> command, long timeoutMs) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      if (cwd != null && cwd.isDirectory()) pb.directory(cwd);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      boolean finished = process.waitFor(Math.max(250L, timeoutMs), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new CommandResult(-1, "Timed out after " + timeoutMs + " ms");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      return new CommandResult(process.exitValue(), output);
    } catch (Exception ex) {
      return new CommandResult(-1, ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }
  }

  private File effectiveWorkspace() {
    if (workspaceRoot != null && workspaceRoot.isDirectory()) return workspaceRoot;
    File cwd = new File(System.getProperty("user.dir", "."));
    return cwd.isDirectory() ? cwd : null;
  }

  private File effectiveProjectOrWorkspace() {
    if (projectRoot != null && projectRoot.isDirectory()) return projectRoot;
    return effectiveWorkspace();
  }

  private static File normalizeDir(File file) {
    return file != null && file.isDirectory() ? file : null;
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static File fileOrNull(String path) {
    if (path == null || path.isBlank()) return null;
    File file = new File(path);
    return file.exists() ? file : null;
  }

  private static String pathText(Path path) {
    return path.toAbsolutePath() + " (" + (Files.exists(path) ? "exists" : "missing") + ")";
  }

  private static String readGradleDistribution(Path props) {
    try {
      for (String line : Files.readAllLines(props)) {
        if (line.startsWith("distributionUrl=")) return line;
      }
      return "distributionUrl not found.";
    } catch (Exception ex) {
      return "Could not read wrapper properties: " + ex.getMessage();
    }
  }

  private static String extractCommand(String fix) {
    if (fix == null) return "";
    int idx = fix.indexOf("Run:");
    return idx >= 0 ? fix.substring(idx + 4).trim() : "";
  }

  private static int categoryCount(List<HealthCheck> checks) {
    return (int) checks.stream().map(HealthCheck::category).distinct().count();
  }

  private static HealthCheck problem(String category, String title, String detail, String fix) {
    return new HealthCheck(category, Severity.WARN, title, detail, "", fix, "", null, List.of("setup"));
  }

  private static String safe(String text) {
    return text == null ? "" : text.trim();
  }

  private static String valueOrMissing(String text) {
    return text == null || text.isBlank() ? "(missing)" : text;
  }

  private static String trimMiddle(String text, int max) {
    if (text == null || text.length() <= max) return text == null ? "" : text;
    int head = Math.max(0, max / 2 - 8);
    int tail = Math.max(0, max - head - 15);
    return text.substring(0, head) + "\n...\n" + text.substring(text.length() - tail);
  }

  private record CommandResult(int exitCode, String output) {
    boolean success() {
      return exitCode == 0;
    }

    String firstLine() {
      if (output == null || output.isBlank()) return "(no output)";
      return output.lines().findFirst().orElse("(no output)").trim();
    }
  }

  private record HealthCheck(
      String category,
      Severity severity,
      String title,
      String detail,
      String evidence,
      String fix,
      String command,
      File path,
      List<String> tags) {
    HealthCheck {
      category = valueOrMissing(category);
      severity = severity == null ? Severity.INFO : severity;
      title = valueOrMissing(title);
      detail = safe(detail);
      evidence = safe(evidence);
      fix = safe(fix);
      command = safe(command);
      tags = tags == null ? List.of() : List.copyOf(tags);
    }
  }

  private enum Severity {
    ERROR("FIX", "error", 0),
    WARN("CHECK", "warn", 1),
    OK("OK", "ok", 2),
    INFO("INFO", "info", 3);

    private final String label;
    private final String css;
    private final int rank;

    Severity(String label, String css, int rank) {
      this.label = label;
      this.css = css;
      this.rank = rank;
    }

    String label() {
      return label;
    }

    String css() {
      return css;
    }

    int rank() {
      return rank;
    }
  }

  private record Inventory(
      int files,
      int dirs,
      int javaFiles,
      int vnsFiles,
      int jesFiles,
      int gradleFiles,
      int images,
      int audio,
      int docs,
      List<String> modules) {
    String summary() {
      return files + " files, " + dirs + " folders, " + modules.size() + " Gradle module(s).";
    }

    String details() {
      return "Java: " + javaFiles
          + "\nVNS: " + vnsFiles
          + "\nJES: " + jesFiles
          + "\nGradle files: " + gradleFiles
          + "\nImages: " + images
          + "\nAudio: " + audio
          + "\nDocs: " + docs
          + "\nModules: " + String.join(", ", modules);
    }
  }
}
