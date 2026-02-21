package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.jvn.editor.vcs.GitVcsService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Startup workspace dashboard for editor users.
 */
public class WelcomeCenterView extends BorderPane {
  private static final int RECENT_LIMIT = 10;
  private static final int RECENT_HISTORY_LIMIT = 40;
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final GitVcsService vcs = new GitVcsService();
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "jvn-welcome-health-worker");
    t.setDaemon(true);
    return t;
  });

  private final Label headingLabel = new Label("Welcome back to JVN");
  private final Label versionLabel = new Label("Version: --");
  private final Label workspaceLabel = new Label("Workspace: --");
  private final Label projectLabel = new Label("Project: none");
  private final Label statusLabel = new Label("Ready");

  private final Button btnNewProject = new Button("New Project");
  private final Button btnOpenProject = new Button("Open Project");
  private final Button btnRefresh = new Button("Refresh Health");

  private final ObservableList<ProjectEntry> recentProjects = FXCollections.observableArrayList();
  private final ListView<ProjectEntry> recentList = new ListView<>(recentProjects);

  private final VBox healthContainer = new VBox(8);

  private File workspaceRoot;
  private File projectRoot;
  private Runnable onCreateProject;
  private Runnable onOpenProjectDialog;
  private Consumer<File> onOpenRecentProject;

  private boolean busy;

  public WelcomeCenterView() {
    buildUi();
    refresh();
  }

  public void setEditorVersion(String version) {
    if (version == null || version.isBlank()) version = "dev";
    versionLabel.setText("Version: " + version.trim());
  }

  public void setWorkspaceRoot(File workspaceRoot) {
    this.workspaceRoot = normalizeDir(workspaceRoot);
    workspaceLabel.setText("Workspace: " + (this.workspaceRoot == null ? "--" : this.workspaceRoot.getAbsolutePath()));
    refresh();
  }

  public void setCurrentProject(File projectRoot) {
    this.projectRoot = normalizeDir(projectRoot);
    projectLabel.setText("Project: " + (this.projectRoot == null ? "none" : this.projectRoot.getAbsolutePath()));
    refresh();
  }

  public void setOnCreateProject(Runnable onCreateProject) {
    this.onCreateProject = onCreateProject;
  }

  public void setOnOpenProjectDialog(Runnable onOpenProjectDialog) {
    this.onOpenProjectDialog = onOpenProjectDialog;
  }

  public void setOnOpenRecentProject(Consumer<File> onOpenRecentProject) {
    this.onOpenRecentProject = onOpenRecentProject;
  }

  public void markProjectVisited(File projectDir) {
    Path project = normalizeProjectDir(projectDir == null ? null : projectDir.toPath());
    if (project == null) return;
    rememberRecentProject(project);
    refresh();
  }

  public void refresh() {
    if (busy) return;
    setBusy(true);
    worker.submit(() -> {
      List<ProjectEntry> projects = collectRecentProjects();
      List<HealthRow> healthRows = collectHealthRows();
      Platform.runLater(() -> {
        recentProjects.setAll(projects);
        renderHealthRows(healthRows);
        statusLabel.setText("Last refreshed: " + formatTimestamp(System.currentTimeMillis()));
        setBusy(false);
      });
    });
  }

  private void buildUi() {
    getStyleClass().add("welcome-center-root");
    setPadding(new Insets(14));

    Label logoLabel = new Label("JVN");
    logoLabel.getStyleClass().add("jvn-wordmark");
    headingLabel.setStyle("-fx-font-size: 21px; -fx-font-weight: 700;");
    versionLabel.setStyle("-fx-text-fill: #aeb4bf;");
    workspaceLabel.setStyle("-fx-text-fill: #9aa0a6;");
    projectLabel.setStyle("-fx-text-fill: #9aa0a6;");
    statusLabel.setStyle("-fx-text-fill: #7f858b;");

    btnNewProject.setOnAction(e -> {
      if (onCreateProject != null) onCreateProject.run();
    });
    btnOpenProject.setOnAction(e -> {
      if (onOpenProjectDialog != null) onOpenProjectDialog.run();
    });
    btnRefresh.setOnAction(e -> refresh());

    HBox actions = new HBox(8, btnNewProject, btnOpenProject, btnRefresh);
    actions.setAlignment(Pos.CENTER_LEFT);

    VBox hero = new VBox(6, logoLabel, headingLabel, versionLabel, workspaceLabel, projectLabel, actions, statusLabel);
    hero.setPadding(new Insets(12));
    hero.setStyle("-fx-background-color: #17181a; -fx-background-radius: 8;");

    Label recentHeader = new Label("Recent Projects");
    recentHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
    recentList.setPlaceholder(new Label("No projects found yet. Create one with New Project."));
    recentList.setCellFactory(list -> new ProjectCell());
    recentList.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      ProjectEntry selected = recentList.getSelectionModel().getSelectedItem();
      if (selected != null) openRecentProject(selected);
    });
    recentList.setOnKeyPressed(e -> {
      if (e.getCode() != KeyCode.ENTER) return;
      ProjectEntry selected = recentList.getSelectionModel().getSelectedItem();
      if (selected != null) openRecentProject(selected);
    });

    VBox left = new VBox(8, recentHeader, recentList);
    left.setPadding(new Insets(10));
    left.setStyle("-fx-background-color: #141518; -fx-background-radius: 8;");
    VBox.setVgrow(recentList, Priority.ALWAYS);

    Label healthHeader = new Label("Environment Health");
    healthHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
    healthContainer.setPadding(new Insets(4, 0, 0, 0));
    ScrollPane healthScroll = new ScrollPane(healthContainer);
    healthScroll.setFitToWidth(true);
    healthScroll.setFitToHeight(true);
    VBox right = new VBox(8, healthHeader, healthScroll);
    right.setPadding(new Insets(10));
    right.setStyle("-fx-background-color: #141518; -fx-background-radius: 8;");
    VBox.setVgrow(healthScroll, Priority.ALWAYS);

    SplitPane split = new SplitPane(left, right);
    split.setDividerPositions(0.44);
    VBox.setVgrow(split, Priority.ALWAYS);

    VBox center = new VBox(10, hero, split);
    VBox.setVgrow(split, Priority.ALWAYS);
    setCenter(center);
  }

  private void setBusy(boolean busy) {
    this.busy = busy;
    btnNewProject.setDisable(busy);
    btnOpenProject.setDisable(busy);
    btnRefresh.setDisable(busy);
  }

  private void openRecentProject(ProjectEntry entry) {
    if (entry == null || onOpenRecentProject == null) return;
    File dir = entry.projectDir();
    if (dir == null || !dir.isDirectory()) return;
    rememberRecentProject(dir.toPath());
    onOpenRecentProject.accept(dir);
  }

  private List<ProjectEntry> collectRecentProjects() {
    List<Path> candidates = new ArrayList<>();
    if (projectRoot != null) candidates.add(projectRoot.toPath());
    candidates.addAll(readRecentHistory());
    candidates.addAll(scanDefaultProjectRoots());
    candidates.addAll(scanWorkspaceForProjects());

    List<ProjectEntry> entries = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (Path candidate : candidates) {
      Path project = normalizeProjectDir(candidate);
      if (project == null) continue;
      String key = canonicalPath(project);
      if (!seen.add(key)) continue;
      long modified = projectTimestamp(project);
      entries.add(new ProjectEntry(project.toFile(), modified));
    }

    entries.sort((a, b) -> Long.compare(b.modifiedMillis(), a.modifiedMillis()));
    if (entries.size() > RECENT_LIMIT) {
      return new ArrayList<>(entries.subList(0, RECENT_LIMIT));
    }
    return entries;
  }

  private List<HealthRow> collectHealthRows() {
    List<HealthRow> rows = new ArrayList<>();
    rows.add(checkJavaHealth());
    rows.add(checkGradleHealth());
    rows.add(checkGitHealth());
    rows.add(checkOptionalArtifacts());
    rows.add(checkProjectArtifactHealth());
    return rows;
  }

  private HealthRow checkJavaHealth() {
    String runtimeRaw = System.getProperty("java.version", "unknown");
    int runtimeMajor = parseJavaMajor(runtimeRaw);
    int required = readRequiredJavaVersion();
    if (required <= 0 || runtimeMajor <= 0) {
      return new HealthRow(
          Severity.INFO,
          "Java Runtime",
          "Detected: " + runtimeRaw,
          "Unable to compare against required toolchain version."
      );
    }
    if (runtimeMajor < required) {
      return new HealthRow(
          Severity.ERROR,
          "Java Runtime",
          "Running Java " + runtimeMajor + ", required " + required,
          "Upgrade Java runtime or run editor with a matching JDK."
      );
    }
    if (runtimeMajor != required) {
      return new HealthRow(
          Severity.WARN,
          "Java Runtime",
          "Running Java " + runtimeMajor + ", project toolchain " + required,
          "Version mismatch is allowed but can cause subtle behavior differences."
      );
    }
    return new HealthRow(
        Severity.OK,
        "Java Runtime",
        "Java " + runtimeMajor + " matches toolchain",
        "Runtime and Gradle toolchain are aligned."
    );
  }

  private HealthRow checkGradleHealth() {
    Path root = workspaceRoot == null ? null : workspaceRoot.toPath();
    if (root == null) {
      return new HealthRow(
          Severity.INFO,
          "Gradle",
          "Workspace root is not set",
          "Open a workspace to validate Gradle wrapper state."
      );
    }

    Path gradlew = root.resolve(isWindows() ? "gradlew.bat" : "gradlew");
    Path wrapperProps = root.resolve("gradle/wrapper/gradle-wrapper.properties");
    boolean hasWrapperScript = Files.isRegularFile(gradlew);
    boolean hasWrapperProps = Files.isRegularFile(wrapperProps);
    String wrapperVersion = hasWrapperProps ? readGradleWrapperVersion(wrapperProps) : "unknown";
    String vfsWatch = readGradleProperty("org.gradle.vfs.watch");

    if (!hasWrapperScript || !hasWrapperProps) {
      return new HealthRow(
          Severity.ERROR,
          "Gradle Wrapper",
          "Missing wrapper script or wrapper properties",
          "Ensure `gradlew` and `gradle/wrapper/gradle-wrapper.properties` are present."
      );
    }

    if (!isWindows() && !Files.isExecutable(gradlew)) {
      return new HealthRow(
          Severity.WARN,
          "Gradle Wrapper",
          "Wrapper found (Gradle " + wrapperVersion + "), but script is not executable",
          "Run `chmod +x gradlew`."
      );
    }

    String detail = "Wrapper version: " + wrapperVersion;
    if ("false".equalsIgnoreCase(vfsWatch)) {
      detail += " • VFS watch disabled (lock-safe setting enabled)";
    }
    return new HealthRow(
        Severity.OK,
        "Gradle Wrapper",
        "Wrapper is present and usable",
        detail
    );
  }

  private HealthRow checkGitHealth() {
    boolean gitAvailable = vcs.isGitAvailable();
    if (!gitAvailable) {
      return new HealthRow(
          Severity.ERROR,
          "Git",
          "Git is not available on PATH",
          "Install Git to enable team workflows and repository operations."
      );
    }
    return new HealthRow(
        Severity.OK,
        "Git",
        "Git is available",
        "Version-control prerequisites are satisfied."
    );
  }

  private HealthRow checkOptionalArtifacts() {
    Path workspace = workspaceRoot == null ? null : workspaceRoot.toPath();
    boolean simp3Source = workspace != null && Files.isRegularFile(workspace.resolve("Simp3/pom.xml"));
    boolean simp3Installed = Files.isRegularFile(Path.of(
        System.getProperty("user.home", "."),
        ".m2", "repository", "com", "musicplayer", "simp3", "1.0.0", "simp3-1.0.0.jar"
    ));

    if (simp3Source || simp3Installed) {
      String source = simp3Installed ? "local Maven artifact installed" : "Simp3 source clone detected";
      return new HealthRow(
          Severity.OK,
          "Optional Artifacts",
          "Simp3 backend available",
          source + ". Optional audio backend can be enabled with `-PuseSimp3=true`."
      );
    }
    return new HealthRow(
        Severity.WARN,
        "Optional Artifacts",
        "Simp3 backend not found",
        "Optional audio backend is missing. Build with `-PuseSimp3=true` will fail until installed."
    );
  }

  private HealthRow checkProjectArtifactHealth() {
    Path project = projectRoot == null ? null : projectRoot.toPath();
    if (project == null) {
      return new HealthRow(
          Severity.INFO,
          "Project Artifacts",
          "No active project selected",
          "Open a project to validate `jvn.project` references and generated files."
      );
    }

    Path manifestPath = project.resolve("jvn.project");
    if (!Files.isRegularFile(manifestPath)) {
      return new HealthRow(
          Severity.ERROR,
          "Project Artifacts",
          "Missing `jvn.project` manifest",
          "Create/open a valid JVN project so runtime/editor can resolve config and script entry points."
      );
    }

    Properties manifest = new Properties();
    try (InputStream in = Files.newInputStream(manifestPath)) {
      manifest.load(in);
    } catch (Exception ex) {
      return new HealthRow(
          Severity.ERROR,
          "Project Artifacts",
          "Manifest read failed",
          ex.getMessage()
      );
    }

    List<String> missing = new ArrayList<>();
    requireProjectFile(project, manifest.getProperty("entryVns", "scripts/story/prologue.vns"), missing);
    requireProjectFile(project, manifest.getProperty("timeline", "config/timeline/story.timeline"), missing);
    requireProjectFile(project, manifest.getProperty("settingsFile", "config/settings/vn.settings"), missing);
    requireProjectFile(project, manifest.getProperty("dialogueLayout", "config/ui/dialogue.layout"), missing);

    String menuRegistry = manifest.getProperty("menuRegistry");
    if (menuRegistry != null && !menuRegistry.isBlank()) requireProjectFile(project, menuRegistry, missing);
    String menuTheme = manifest.getProperty("menuTheme");
    if (menuTheme != null && !menuTheme.isBlank()) requireProjectFile(project, menuTheme, missing);

    boolean gitEnabled = Boolean.parseBoolean(manifest.getProperty("vcs.git.enabled", "false"));
    if (gitEnabled && !Files.isDirectory(project.resolve(".git"))) {
      missing.add(".git (repository requested by manifest)");
    }

    if (missing.isEmpty()) {
      return new HealthRow(
          Severity.OK,
          "Project Artifacts",
          "All referenced project artifacts are present",
          "Manifest paths and generated config/script files resolve correctly."
      );
    }

    String detail = "Missing: " + String.join(", ", missing);
    return new HealthRow(
        Severity.ERROR,
        "Project Artifacts",
        missing.size() + " required files are missing",
        detail
    );
  }

  private void renderHealthRows(List<HealthRow> rows) {
    healthContainer.getChildren().clear();
    if (rows == null || rows.isEmpty()) {
      Label empty = new Label("No health checks available.");
      empty.setStyle("-fx-text-fill: #9aa0a6;");
      healthContainer.getChildren().add(empty);
      return;
    }
    for (HealthRow row : rows) {
      healthContainer.getChildren().add(buildHealthCard(row));
    }
  }

  private VBox buildHealthCard(HealthRow row) {
    VBox box = new VBox(3);
    box.setPadding(new Insets(8));
    box.setStyle("-fx-background-color: #17181a; -fx-background-radius: 6;");

    Label title = new Label(row.title() + "  •  " + row.summary());
    title.setStyle("-fx-font-weight: 700; -fx-text-fill: " + severityColor(row.severity()) + ";");
    Label detail = new Label(row.detail());
    detail.setWrapText(true);
    detail.setStyle("-fx-text-fill: #aeb4bf;");

    box.getChildren().addAll(title, detail);
    return box;
  }

  private String severityColor(Severity severity) {
    if (severity == Severity.OK) return "#4dd17a";
    if (severity == Severity.WARN) return "#f0b673";
    if (severity == Severity.ERROR) return "#f38ba8";
    return "#9aa0a6";
  }

  private void requireProjectFile(Path projectRoot, String relativePath, List<String> missing) {
    if (relativePath == null || relativePath.isBlank()) return;
    String normalized = relativePath.trim().replace('\\', '/');
    Path file = projectRoot.resolve(normalized).normalize();
    if (!Files.isRegularFile(file)) missing.add(normalized);
  }

  private int readRequiredJavaVersion() {
    String raw = readGradleProperty("javaVersion");
    if (raw == null || raw.isBlank()) return -1;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ignore) {
      return -1;
    }
  }

  private String readGradleProperty(String key) {
    if (key == null || key.isBlank()) return null;
    Path root = workspaceRoot == null ? null : workspaceRoot.toPath();
    if (root == null) return null;
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

  private List<Path> readRecentHistory() {
    Path history = historyPath();
    if (!Files.isRegularFile(history)) return List.of();
    try {
      List<String> lines = Files.readAllLines(history, StandardCharsets.UTF_8);
      List<Path> paths = new ArrayList<>();
      for (String line : lines) {
        if (line == null || line.isBlank()) continue;
        paths.add(Path.of(line.trim()));
      }
      return paths;
    } catch (Exception ignore) {
      return List.of();
    }
  }

  private void rememberRecentProject(Path projectDir) {
    if (projectDir == null) return;
    Path normalized = normalizeProjectDir(projectDir);
    if (normalized == null) return;
    Path history = historyPath();
    try {
      List<String> existing = Files.isRegularFile(history)
          ? new ArrayList<>(Files.readAllLines(history, StandardCharsets.UTF_8))
          : new ArrayList<>();

      String raw = normalized.toAbsolutePath().normalize().toString();
      existing.removeIf(line -> line == null || line.isBlank() || line.trim().equals(raw));
      existing.add(0, raw);
      if (existing.size() > RECENT_HISTORY_LIMIT) {
        existing = new ArrayList<>(existing.subList(0, RECENT_HISTORY_LIMIT));
      }

      Files.createDirectories(history.getParent());
      Files.write(history, existing, StandardCharsets.UTF_8);
    } catch (Exception ignore) {
    }
  }

  private List<Path> scanDefaultProjectRoots() {
    Path home = Path.of(System.getProperty("user.home", "."));
    Path defaultRoot = home.resolve("JVN Projects");
    return scanProjectDirsUnder(defaultRoot, 1);
  }

  private List<Path> scanWorkspaceForProjects() {
    if (workspaceRoot == null) return List.of();
    List<Path> result = new ArrayList<>();
    Path ws = workspaceRoot.toPath();
    if (Files.isRegularFile(ws.resolve("jvn.project"))) result.add(ws);
    result.addAll(scanProjectDirsUnder(ws, 1));
    return result;
  }

  private List<Path> scanProjectDirsUnder(Path base, int depth) {
    if (base == null || !Files.isDirectory(base)) return List.of();
    try (Stream<Path> stream = Files.walk(base, depth)) {
      return stream
          .filter(Files::isDirectory)
          .filter(path -> Files.isRegularFile(path.resolve("jvn.project")))
          .toList();
    } catch (Exception ignore) {
      return List.of();
    }
  }

  private Path normalizeProjectDir(Path raw) {
    if (raw == null) return null;
    Path dir = raw;
    if (Files.isRegularFile(dir) && "jvn.project".equalsIgnoreCase(String.valueOf(dir.getFileName()))) {
      dir = dir.getParent();
    }
    if (dir == null || !Files.isDirectory(dir)) return null;
    if (!Files.isRegularFile(dir.resolve("jvn.project"))) return null;
    return dir.toAbsolutePath().normalize();
  }

  private long projectTimestamp(Path projectDir) {
    try {
      Path manifest = projectDir.resolve("jvn.project");
      long manifestTs = Files.isRegularFile(manifest) ? Files.getLastModifiedTime(manifest).toMillis() : 0L;
      Path entry = projectDir.resolve("scripts/story/prologue.vns");
      long entryTs = Files.isRegularFile(entry) ? Files.getLastModifiedTime(entry).toMillis() : 0L;
      return Math.max(manifestTs, entryTs);
    } catch (IOException ex) {
      return 0L;
    }
  }

  private String canonicalPath(Path path) {
    if (path == null) return "";
    try {
      return path.toRealPath().toString();
    } catch (Exception ignore) {
      return path.toAbsolutePath().normalize().toString();
    }
  }

  private String formatTimestamp(long ts) {
    if (ts <= 0) return "unknown";
    return DATE_FORMAT.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()));
  }

  private int parseJavaMajor(String raw) {
    if (raw == null || raw.isBlank()) return -1;
    String value = raw.trim();
    if (value.startsWith("1.")) value = value.substring(2);
    int dot = value.indexOf('.');
    if (dot > 0) value = value.substring(0, dot);
    int dash = value.indexOf('-');
    if (dash > 0) value = value.substring(0, dash);
    try {
      return Integer.parseInt(value);
    } catch (Exception ex) {
      return -1;
    }
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "")
        .toLowerCase(Locale.ROOT)
        .contains("win");
  }

  private Path historyPath() {
    return Path.of(System.getProperty("user.home", "."), ".jvn-editor", "recent-projects.txt");
  }

  private File normalizeDir(File dir) {
    if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
    return dir.getAbsoluteFile();
  }

  private static final class ProjectEntry {
    private final File projectDir;
    private final long modifiedMillis;

    private ProjectEntry(File projectDir, long modifiedMillis) {
      this.projectDir = projectDir;
      this.modifiedMillis = modifiedMillis;
    }

    public File projectDir() {
      return projectDir;
    }

    public long modifiedMillis() {
      return modifiedMillis;
    }
  }

  private enum Severity {
    OK,
    WARN,
    ERROR,
    INFO
  }

  private record HealthRow(Severity severity, String title, String summary, String detail) {
  }

  private final class ProjectCell extends ListCell<ProjectEntry> {
    private final Label nameLabel = new Label();
    private final Label pathLabel = new Label();
    private final Label timeLabel = new Label();
    private final Button openButton = new Button("Open");
    private final Region spacer = new Region();
    private final HBox footerRow = new HBox(8, timeLabel, spacer, openButton);
    private final VBox content = new VBox(2, nameLabel, pathLabel, footerRow);

    private ProjectCell() {
      HBox.setHgrow(spacer, Priority.ALWAYS);
      openButton.setOnAction(e -> {
        ProjectEntry item = getItem();
        if (item != null) openRecentProject(item);
        e.consume();
      });
      nameLabel.setStyle("-fx-font-weight: 700;");
      pathLabel.setStyle("-fx-text-fill: #9aa0a6;");
      timeLabel.setStyle("-fx-text-fill: #7f858b;");
    }

    @Override
    protected void updateItem(ProjectEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null || item.projectDir() == null) {
        setText(null);
        setGraphic(null);
        return;
      }
      File dir = item.projectDir();
      nameLabel.setText(dir.getName().isBlank() ? dir.getAbsolutePath() : dir.getName());
      pathLabel.setText(dir.getAbsolutePath());
      timeLabel.setText("Updated: " + formatTimestamp(item.modifiedMillis()));
      setText(null);
      setGraphic(content);
    }
  }
}
