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

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

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

  private final Label headingLabel = new Label("Welcome to JVN Editor");
  private final Label introLabel = new Label("");
  private final Label versionLabel = new Label("Version: --");
  private final Label workspaceLabel = new Label("No workspace root configured.");
  private final Label projectLabel = new Label("No project is currently open.");
  private final Label statusLabel = new Label("Ready");
  private final Label recentMetaLabel = new Label("0 projects");
  private final Label healthMetaLabel = new Label("0 checks");
  private final Label workspaceValueLabel = new Label("No workspace");
  private final Label projectValueLabel = new Label("No active project");
  private final Label recentOverviewValueLabel = new Label("0");
  private final Label recentOverviewDetailLabel = new Label("Tracked projects");
  private final Label healthOverviewValueLabel = new Label("Pending");
  private final Label healthOverviewDetailLabel = new Label("Run checks to inspect the environment");

  private final Button btnNewProject = new Button();
  private final Button btnOpenProject = new Button();
  private final Button btnRunProject = new Button();
  private final Button btnOpenLast = new Button();
  private final Button btnRefresh = new Button();

  private final ObservableList<ProjectEntry> recentProjects = FXCollections.observableArrayList();
  private final ListView<ProjectEntry> recentList = new ListView<>(recentProjects);
  private final TextField recentFilterField = new TextField();

  private final VBox healthContainer = new VBox(8);
  private List<ProjectEntry> allRecentProjects = List.of();

  private File workspaceRoot;
  private File projectRoot;
  private Runnable onCreateProject;
  private Runnable onOpenProjectDialog;
  private Runnable onOpenSelectedProject;
  private Runnable onRunSelectedProject;
  private Consumer<File> onOpenRecentProject;
  private Consumer<File> onOpenProject;
  private Consumer<File> onRunProject;
  private Consumer<File> onRevealProject;
  private Consumer<File> onOpenProjectFile;
  private Runnable onShowSettings;

  private boolean busy;

  public WelcomeCenterView() {
    buildUi();
    refresh();
  }

  public void setEditorVersion(String version) {
    if (version == null || version.isBlank()) version = "dev";
    versionLabel.setText("Version " + version.trim());
  }

  public void setWelcomeHeading(String heading) {
    if (heading == null || heading.isBlank()) return;
    headingLabel.setText(heading.trim());
  }

  public void setWelcomeIntro(String intro) {
    if (intro == null || intro.isBlank()) return;
    introLabel.setText(intro.trim());
  }

  public void setVersionChipVisible(boolean visible) {
    versionLabel.setVisible(visible);
    versionLabel.setManaged(visible);
  }

  public void setWorkspaceRoot(File workspaceRoot) {
    this.workspaceRoot = normalizeDir(workspaceRoot);
    workspaceValueLabel.setText(this.workspaceRoot == null ? "No workspace" : displayName(this.workspaceRoot));
    workspaceLabel.setText(this.workspaceRoot == null
        ? "Set a workspace root to scan project health and discover recent work."
        : abbreviatePath(this.workspaceRoot.getAbsolutePath()));
    refresh();
  }

  public void setCurrentProject(File projectRoot) {
    this.projectRoot = normalizeDir(projectRoot);
    projectValueLabel.setText(this.projectRoot == null ? "No active project" : displayName(this.projectRoot));
    projectLabel.setText(this.projectRoot == null
        ? "Open a project to validate runtime artifacts and continue where you left off."
        : abbreviatePath(this.projectRoot.getAbsolutePath()));
    updateProjectActionButtons();
    refresh();
  }

  public void setOnCreateProject(Runnable onCreateProject) {
    this.onCreateProject = onCreateProject;
  }

  public void setOnOpenProjectDialog(Runnable onOpenProjectDialog) {
    this.onOpenProjectDialog = onOpenProjectDialog;
  }

  public void setOnOpenSelectedProject(Runnable onOpenSelectedProject) {
    this.onOpenSelectedProject = onOpenSelectedProject;
  }

  public void setOnRunSelectedProject(Runnable onRunSelectedProject) {
    this.onRunSelectedProject = onRunSelectedProject;
  }

  public void setOnOpenRecentProject(Consumer<File> onOpenRecentProject) {
    this.onOpenRecentProject = onOpenRecentProject;
  }

  public void setOnOpenProject(Consumer<File> onOpenProject) {
    this.onOpenProject = onOpenProject;
  }

  public void setOnRunProject(Consumer<File> onRunProject) {
    this.onRunProject = onRunProject;
  }

  public void setOnRevealProject(Consumer<File> onRevealProject) {
    this.onRevealProject = onRevealProject;
  }

  public void setOnOpenProjectFile(Consumer<File> onOpenProjectFile) {
    this.onOpenProjectFile = onOpenProjectFile;
  }

  public void setOnShowSettings(Runnable onShowSettings) {
    this.onShowSettings = onShowSettings;
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
        allRecentProjects = projects == null ? List.of() : projects;
        applyRecentFilter();
        renderHealthRows(healthRows);
        healthMetaLabel.setText(buildHealthSummary(healthRows));
        statusLabel.setText("Last refreshed: " + formatTimestamp(System.currentTimeMillis()));
        setBusy(false);
      });
    });
  }

  private void buildUi() {
    getStyleClass().add("welcome-center-root");
    setPadding(new Insets(14));

    headingLabel.getStyleClass().add("welcome-heading");
    introLabel.getStyleClass().add("welcome-intro-text");
    versionLabel.getStyleClass().add("welcome-version-chip");
    workspaceLabel.getStyleClass().add("welcome-overview-detail");
    workspaceLabel.setWrapText(true);
    projectLabel.getStyleClass().add("welcome-overview-detail");
    projectLabel.setWrapText(true);
    statusLabel.getStyleClass().add("welcome-status-text");
    recentMetaLabel.getStyleClass().add("welcome-section-meta");
    healthMetaLabel.getStyleClass().add("welcome-section-meta");
    workspaceValueLabel.getStyleClass().add("welcome-overview-value");
    projectValueLabel.getStyleClass().add("welcome-overview-value");
    recentOverviewValueLabel.getStyleClass().add("welcome-overview-value");
    recentOverviewDetailLabel.getStyleClass().add("welcome-overview-detail");
    healthOverviewValueLabel.getStyleClass().addAll("welcome-overview-value", "welcome-overview-value-info");
    healthOverviewDetailLabel.getStyleClass().add("welcome-overview-detail");

    btnNewProject.setOnAction(e -> {
      if (onCreateProject != null) onCreateProject.run();
    });
    btnOpenProject.setOnAction(e -> {
      if (hasSelectedProject()) {
        if (onOpenProject != null) onOpenProject.accept(projectRoot);
      } else if (onOpenProjectDialog != null) {
        onOpenProjectDialog.run();
      }
    });
    btnRunProject.setOnAction(e -> {
      if (hasSelectedProject() && onRunProject != null) {
        onRunProject.accept(projectRoot);
      }
    });
    btnOpenLast.setOnAction(e -> {
      ProjectEntry first = recentProjects.isEmpty() ? null : recentProjects.get(0);
      if (first != null) openRecentProject(first);
    });
    btnRefresh.setOnAction(e -> refresh());
    configureActionButton(btnNewProject, CssIcon.plus("#8bcf98"), "New Project", "Create a new project", "welcome-action-button-primary");
    configureActionButton(btnOpenProject, CssIcon.folder("#d5b36a"), "Open Project", "Choose an existing project", "welcome-action-button-secondary");
    configureActionButton(btnRunProject, CssIcon.play("#dd9a48"), "Run Project", "Run the selected project with the runtime", "welcome-action-button-secondary");
    configureActionButton(btnOpenLast, CssIcon.arrowRight("#dccba2"), "Select Latest", "Select the most recent project", "welcome-action-button-secondary");
    configureActionButton(btnRefresh, CssIcon.redo("#d6cab8"), "Refresh Checks", "Refresh Welcome Center data and health checks", "welcome-action-button-secondary");
    updateProjectActionButtons();

    HBox actions = new HBox(8, btnNewProject, btnOpenProject, btnRunProject, btnOpenLast, btnRefresh);
    actions.getStyleClass().add("welcome-action-row");
    actions.setAlignment(Pos.CENTER_LEFT);

    HBox headingRow = new HBox(8, headingLabel, versionLabel);
    headingRow.setAlignment(Pos.BASELINE_LEFT);

    VBox hero = new VBox(12, headingRow, introLabel, actions, statusLabel);
    hero.setPadding(new Insets(10, 12, 10, 12));
    hero.getStyleClass().add("welcome-hero-card");

    Label recentHeader = new Label("Recent Projects");
    recentHeader.getStyleClass().add("welcome-section-title");
    recentFilterField.setPromptText("Filter by project name or path...");
    recentFilterField.getStyleClass().add("welcome-filter-field");
    recentFilterField.textProperty().addListener((obs, oldValue, newValue) -> applyRecentFilter());
    Label recentPlaceholder = new Label("No projects found yet. Create one with New Project.");
    recentPlaceholder.getStyleClass().add("welcome-placeholder-text");
    recentList.setPlaceholder(recentPlaceholder);
    recentList.getStyleClass().add("welcome-project-list");
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
    MenuItem miReveal = new MenuItem("Reveal in Explorer");
    MenuItem miOpenFile = new MenuItem("Open Project File...");
    MenuItem miSettings = new MenuItem("Settings...");
    miReveal.setOnAction(e -> {
      ProjectEntry sel = recentList.getSelectionModel().getSelectedItem();
      if (sel != null && onRevealProject != null) onRevealProject.accept(sel.projectDir());
    });
    miOpenFile.setOnAction(e -> {
      ProjectEntry sel = recentList.getSelectionModel().getSelectedItem();
      if (sel != null && onOpenProjectFile != null) onOpenProjectFile.accept(sel.projectDir());
    });
    miSettings.setOnAction(e -> {
      if (onShowSettings != null) onShowSettings.run();
    });
    ContextMenu listMenu = new ContextMenu();
    listMenu.getItems().addAll(miReveal, miOpenFile, new SeparatorMenuItem(), miSettings);
    recentList.setContextMenu(listMenu);

    Region recentSpacer = new Region();
    HBox.setHgrow(recentSpacer, Priority.ALWAYS);
    HBox recentHeaderRow = new HBox(8, recentHeader, recentSpacer, recentMetaLabel);
    recentHeaderRow.setAlignment(Pos.CENTER_LEFT);

    VBox left = new VBox(8, recentHeaderRow, recentFilterField, recentList);
    left.setPadding(new Insets(10));
    left.getStyleClass().add("welcome-section-card");
    VBox.setVgrow(recentList, Priority.ALWAYS);

    Label healthHeader = new Label("Environment Health");
    healthHeader.getStyleClass().add("welcome-section-title");
    Region healthSpacer = new Region();
    HBox.setHgrow(healthSpacer, Priority.ALWAYS);
    HBox healthHeaderRow = new HBox(8, healthHeader, healthSpacer, healthMetaLabel);
    healthHeaderRow.setAlignment(Pos.CENTER_LEFT);
    healthContainer.setPadding(new Insets(4, 0, 0, 0));
    ScrollPane healthScroll = new ScrollPane(healthContainer);
    healthScroll.setFitToWidth(true);
    healthScroll.setFitToHeight(true);
    rightPanelStyling(healthScroll);
    VBox right = new VBox(8, healthHeaderRow, healthScroll);
    right.setPadding(new Insets(10));
    right.getStyleClass().add("welcome-section-card");
    VBox.setVgrow(healthScroll, Priority.ALWAYS);

    SplitPane split = new SplitPane(left, right);
    split.getStyleClass().add("welcome-center-split");
    split.setDividerPositions(0.47);
    VBox.setVgrow(split, Priority.ALWAYS);

    VBox center = new VBox(10, hero, split);
    center.getStyleClass().add("welcome-center-body");
    VBox.setVgrow(split, Priority.ALWAYS);
    setCenter(center);
    applyRecentFilter();
  }

  private void setBusy(boolean busy) {
    this.busy = busy;
    btnNewProject.setDisable(busy);
    btnOpenProject.setDisable(busy);
    btnRunProject.setDisable(busy || !hasSelectedProject());
    btnOpenLast.setDisable(busy || recentProjects.isEmpty());
    btnRefresh.setDisable(busy);
    recentFilterField.setDisable(busy);
  }

  private void applyRecentFilter() {
    String query = recentFilterField.getText();
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (allRecentProjects == null || allRecentProjects.isEmpty()) {
      recentProjects.setAll(List.of());
      recentMetaLabel.setText("0 projects");
      recentOverviewValueLabel.setText("0");
      recentOverviewDetailLabel.setText("No tracked projects");
      btnOpenLast.setDisable(true);
      btnRunProject.setDisable(busy || !hasSelectedProject());
      return;
    }
    if (needle.isEmpty()) {
      recentProjects.setAll(allRecentProjects);
    } else {
      List<ProjectEntry> filtered = new ArrayList<>();
      for (ProjectEntry entry : allRecentProjects) {
        File dir = entry.projectDir();
        if (dir == null) continue;
        String name = dir.getName() == null ? "" : dir.getName().toLowerCase(Locale.ROOT);
        String path = dir.getAbsolutePath() == null ? "" : dir.getAbsolutePath().toLowerCase(Locale.ROOT);
        if (name.contains(needle) || path.contains(needle)) filtered.add(entry);
      }
      recentProjects.setAll(filtered);
    }
    int shown = recentProjects.size();
    int total = allRecentProjects.size();
    recentMetaLabel.setText(needle.isEmpty() ? (shown + " projects") : (shown + " / " + total + " shown"));
    recentOverviewValueLabel.setText(String.valueOf(total));
    recentOverviewDetailLabel.setText(total == 1 ? "1 tracked project" : total + " tracked projects");
    btnOpenLast.setDisable(busy || recentProjects.isEmpty());
    btnRunProject.setDisable(busy || !hasSelectedProject());
  }

  private boolean hasSelectedProject() {
    return projectRoot != null && projectRoot.isDirectory();
  }

  private void updateProjectActionButtons() {
    if (hasSelectedProject()) {
      setActionButtonContent(
          btnOpenProject,
          CssIcon.popOut("#d5b36a"),
          "Open in Editor",
          "Open the selected project in the editor");
    } else {
      setActionButtonContent(
          btnOpenProject,
          CssIcon.folder("#d5b36a"),
          "Open Project",
          "Choose an existing project");
    }
    btnRunProject.setDisable(busy || !hasSelectedProject());
  }

  private void rightPanelStyling(ScrollPane scroll) {
    if (scroll == null) return;
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("welcome-health-scroll");
  }

  private static void configureActionButton(Button button,
                                            Region icon,
                                            String text,
                                            String tooltipText,
                                            String styleClass) {
    if (button == null) return;
    button.setMinHeight(34);
    button.setPrefHeight(34);
    button.setMaxHeight(34);
    button.setFocusTraversable(false);
    if (styleClass != null && !styleClass.isBlank()) {
      if (!button.getStyleClass().contains(styleClass)) {
        button.getStyleClass().add(styleClass);
      }
    }
    setActionButtonContent(button, icon, text, tooltipText);
  }

  private static void setActionButtonContent(Button button,
                                             Region icon,
                                             String text,
                                             String tooltipText) {
    if (button == null) return;
    button.setText(text);
    button.setGraphic(icon);
    if (tooltipText != null && !tooltipText.isBlank()) {
      button.setTooltip(new Tooltip(tooltipText));
      button.setAccessibleText(tooltipText);
    } else {
      button.setTooltip(null);
      button.setAccessibleText(null);
    }
  }

  private String buildHealthSummary(List<HealthRow> rows) {
    if (rows == null || rows.isEmpty()) return "0 checks";
    int ok = 0;
    int warn = 0;
    int error = 0;
    int info = 0;
    for (HealthRow row : rows) {
      if (row == null || row.severity() == null) continue;
      switch (row.severity()) {
        case OK -> ok++;
        case WARN -> warn++;
        case ERROR -> error++;
        case INFO -> info++;
      }
    }
    return rows.size() + " checks  •  " + ok + " ok  •  " + warn + " warn  •  " + error + " error  •  " + info + " info";
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
          Severity.INFO,
          "Git",
          "Git is not available on PATH",
          "Optional feature: install Git only if you plan to use version-control workflows."
      );
    }
    return new HealthRow(
      Severity.OK,
        "Git",
        "Git is available",
        "Version-control prerequisites are satisfied."
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
    boolean gitRepoMissing = gitEnabled && !Files.isDirectory(project.resolve(".git"));

    if (missing.isEmpty() && !gitRepoMissing) {
      return new HealthRow(
          Severity.OK,
          "Project Artifacts",
          "All referenced project artifacts are present",
          "Manifest paths and generated config/script files resolve correctly."
      );
    }

    if (missing.isEmpty()) {
      return new HealthRow(
          Severity.WARN,
          "Project Artifacts",
          "Core project files are present; Git repository not initialized",
          "Version-control metadata is optional. Initialize Git from the Version Control panel if needed."
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
      empty.getStyleClass().add("welcome-placeholder-text");
      healthContainer.getChildren().add(empty);
      updateHealthOverview(rows);
      return;
    }
    for (HealthRow row : rows) {
      healthContainer.getChildren().add(buildHealthCard(row));
    }
    updateHealthOverview(rows);
  }

  private VBox buildHealthCard(HealthRow row) {
    VBox box = new VBox(6);
    box.setPadding(new Insets(8));
    box.getStyleClass().add("welcome-health-card");

    Label badge = new Label();
    badge.getStyleClass().addAll("welcome-health-badge", severityBadgeClass(row.severity()));
    configureSeverityBadge(badge, row.severity());
    Label title = new Label(row.title());
    title.getStyleClass().add("welcome-health-title");
    Label summary = new Label(row.summary());
    summary.getStyleClass().add("welcome-health-summary");
    Label detail = new Label(row.detail());
    detail.setWrapText(true);
    detail.getStyleClass().add("welcome-health-detail");

    HBox titleRow = new HBox(8, badge, title);
    titleRow.setAlignment(Pos.CENTER_LEFT);

    box.getChildren().addAll(titleRow, summary, detail);
    return box;
  }

  private String severityLabel(Severity severity) {
    return switch (severity) {
      case OK -> "OK";
      case WARN -> "WARN";
      case ERROR -> "ERROR";
      case INFO -> "INFO";
    };
  }

  private void configureSeverityBadge(Label badge, Severity severity) {
    if (badge == null || severity == null) return;
    badge.setGraphic(null);
    badge.setText(null);
    badge.setContentDisplay(ContentDisplay.LEFT);
    switch (severity) {
      case OK -> {
        Node okIcon = CssIcon.check("#8bcf98");
        badge.setGraphic(okIcon);
        badge.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        badge.setAccessibleText("OK");
      }
      case WARN -> {
        Node warnIcon = CssIcon.warning("#efbf82");
        badge.setGraphic(warnIcon);
        badge.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        badge.setAccessibleText("WARN");
      }
      case ERROR -> {
        Node errorIcon = CssIcon.error("#f0a1b2");
        badge.setGraphic(errorIcon);
        badge.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        badge.setAccessibleText("ERROR");
      }
      case INFO -> badge.setText(severityLabel(severity));
    }
  }

  private String severityBadgeClass(Severity severity) {
    return switch (severity) {
      case OK -> "welcome-health-badge-ok";
      case WARN -> "welcome-health-badge-warn";
      case ERROR -> "welcome-health-badge-error";
      case INFO -> "welcome-health-badge-info";
    };
  }

  private void updateHealthOverview(List<HealthRow> rows) {
    healthOverviewValueLabel.getStyleClass().removeAll(
        "welcome-overview-value-ok",
        "welcome-overview-value-warn",
        "welcome-overview-value-error",
        "welcome-overview-value-info");
    if (rows == null || rows.isEmpty()) {
      healthOverviewValueLabel.setText("Pending");
      healthOverviewDetailLabel.setText("Run checks to inspect the environment");
      healthOverviewValueLabel.getStyleClass().add("welcome-overview-value-info");
      return;
    }

    int ok = 0;
    int warn = 0;
    int error = 0;
    int info = 0;
    for (HealthRow row : rows) {
      if (row == null || row.severity() == null) continue;
      switch (row.severity()) {
        case OK -> ok++;
        case WARN -> warn++;
        case ERROR -> error++;
        case INFO -> info++;
      }
    }

    if (error > 0) {
      healthOverviewValueLabel.setText("Errors detected");
      healthOverviewDetailLabel.setText(error + " blocking issue" + (error == 1 ? "" : "s") + " across " + rows.size() + " checks");
      healthOverviewValueLabel.getStyleClass().add("welcome-overview-value-error");
    } else if (warn > 0) {
      healthOverviewValueLabel.setText("Needs attention");
      healthOverviewDetailLabel.setText(warn + " warning" + (warn == 1 ? "" : "s") + " • " + ok + " checks passing");
      healthOverviewValueLabel.getStyleClass().add("welcome-overview-value-warn");
    } else {
      healthOverviewValueLabel.setText("All clear");
      healthOverviewDetailLabel.setText(rows.size() + " checks passing" + (info > 0 ? " • " + info + " informational" : ""));
      healthOverviewValueLabel.getStyleClass().add("welcome-overview-value-ok");
    }
  }

  private String abbreviatePath(String path) {
    if (path == null || path.isBlank()) return "--";
    String normalized = path.trim();
    String home = System.getProperty("user.home", "").trim();
    if (!home.isBlank() && normalized.startsWith(home)) {
      return "~" + normalized.substring(home.length());
    }
    return normalized;
  }

  private String displayName(File dir) {
    if (dir == null) return "--";
    String name = dir.getName();
    return name == null || name.isBlank() ? dir.getAbsolutePath() : name;
  }

  private boolean sameDirectory(File a, File b) {
    if (a == null || b == null) return false;
    try {
      return a.getCanonicalFile().equals(b.getCanonicalFile());
    } catch (Exception ex) {
      return a.getAbsoluteFile().equals(b.getAbsoluteFile());
    }
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
    private final Label stateBadge = new Label();
    private final AnimatedArrowIndicator currentProjectIndicator = new AnimatedArrowIndicator();
    private final Button openButton = new Button();
    private final Region spacer = new Region();
    private final HBox titleRow = new HBox(8, nameLabel, stateBadge, spacer, openButton);
    private final VBox content = new VBox(4, titleRow, pathLabel, timeLabel);

    private ProjectCell() {
      HBox.setHgrow(spacer, Priority.ALWAYS);
      content.getStyleClass().add("welcome-project-cell");
      titleRow.setAlignment(Pos.CENTER_LEFT);
      openButton.setGraphic(CssIcon.popOut("#d5b36a"));
      openButton.setText("");
      openButton.getStyleClass().add("welcome-open-button");
      openButton.setFocusTraversable(false);
      openButton.setTooltip(new Tooltip("Select project"));
      openButton.setAccessibleText("Select project");
      openButton.setOnAction(e -> {
        ProjectEntry item = getItem();
        if (item != null) openRecentProject(item);
        e.consume();
      });
      nameLabel.getStyleClass().add("welcome-project-name");
      pathLabel.getStyleClass().add("welcome-project-path");
      timeLabel.getStyleClass().add("welcome-project-time");
      stateBadge.getStyleClass().add("welcome-project-badge");
    }

    @Override
    protected void updateItem(ProjectEntry item, boolean empty) {
      super.updateItem(item, empty);
      currentProjectIndicator.stop();
      if (empty || item == null || item.projectDir() == null) {
        setText(null);
        setGraphic(null);
        return;
      }
      File dir = item.projectDir();
      boolean exists = dir.isDirectory();
      String baseName = dir.getName().isBlank() ? dir.getAbsolutePath() : dir.getName();
      nameLabel.setText(exists ? baseName : (baseName + " (missing)"));
      pathLabel.setText(abbreviatePath(dir.getAbsolutePath()));
      timeLabel.setText("Updated: " + formatTimestamp(item.modifiedMillis()));
      stateBadge.getStyleClass().removeAll(
          "welcome-project-badge-current",
          "welcome-project-badge-missing",
          "welcome-project-badge-current-icon",
          "welcome-project-badge-current-arrow");
      stateBadge.setGraphic(null);
      stateBadge.setContentDisplay(ContentDisplay.LEFT);
      stateBadge.setAccessibleText(null);
      boolean current = sameDirectory(dir, projectRoot);
      if (!exists) {
        stateBadge.setText("MISSING");
        stateBadge.getStyleClass().add("welcome-project-badge-missing");
        stateBadge.setVisible(true);
        stateBadge.setManaged(true);
      } else if (current) {
        stateBadge.setText("");
        stateBadge.setGraphic(currentProjectIndicator);
        stateBadge.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        stateBadge.setAccessibleText("Selected project");
        stateBadge.getStyleClass().addAll(
            "welcome-project-badge-current-icon",
            "welcome-project-badge-current-arrow");
        stateBadge.setVisible(true);
        stateBadge.setManaged(true);
        currentProjectIndicator.play();
      } else {
        stateBadge.setText("");
        stateBadge.setVisible(false);
        stateBadge.setManaged(false);
      }
      openButton.setDisable(!exists);
      setText(null);
      setGraphic(content);
    }
  }

  private static final class AnimatedArrowIndicator extends StackPane {
    private final Region arrow = CssIcon.arrowLeft("#e08a2e");
    private final Timeline timeline;

    private AnimatedArrowIndicator() {
      getStyleClass().add("welcome-project-current-arrow");
      setMinSize(14, 12);
      setPrefSize(14, 12);
      setMaxSize(14, 12);
      setPickOnBounds(false);

      arrow.setScaleX(1.1);
      arrow.setScaleY(1.1);
      getChildren().add(arrow);

      resetAnimationState();
      timeline = new Timeline(
          new KeyFrame(Duration.ZERO,
              new KeyValue(arrow.translateXProperty(), 1.4, Interpolator.EASE_BOTH),
              new KeyValue(arrow.opacityProperty(), 0.74, Interpolator.EASE_BOTH)),
          new KeyFrame(Duration.millis(320),
              new KeyValue(arrow.translateXProperty(), -2.2, Interpolator.EASE_BOTH),
              new KeyValue(arrow.opacityProperty(), 1.0, Interpolator.EASE_BOTH)),
          new KeyFrame(Duration.millis(760),
              new KeyValue(arrow.translateXProperty(), 1.4, Interpolator.EASE_BOTH),
              new KeyValue(arrow.opacityProperty(), 0.74, Interpolator.EASE_BOTH)));
      timeline.setCycleCount(Animation.INDEFINITE);
    }

    private void play() {
      timeline.playFromStart();
    }

    private void stop() {
      timeline.stop();
      resetAnimationState();
    }

    private void resetAnimationState() {
      arrow.setTranslateX(1.4);
      arrow.setOpacity(0.74);
    }
  }
}
