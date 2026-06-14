package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Lightweight in-editor hub tab that replaces the full launcher-style welcome view.
 */
public class EditorWorkspaceHubView extends BorderPane {
  private static final int COUNT_LIMIT = 999;
  private static final DateTimeFormatter MODIFIED_FORMAT =
      DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

  private final Label kickerLabel = new Label("WORKSPACE");
  private final Label headingLabel = new Label("JVN Editor");
  private final Label introLabel = new Label("Project context, entry status, and common editor actions.");
  private final Label healthChipLabel = new Label("NO PROJECT");
  private final Label runtimeChipLabel = new Label();
  private final Label workspaceValueLabel = new Label("--");
  private final Label workspaceDetailLabel = new Label("No workspace resolved");
  private final Label projectValueLabel = new Label("No project selected");
  private final Label projectDetailLabel = new Label("Open or create a project to enable project tools");
  private final Label manifestValueLabel = new Label("No manifest");
  private final Label manifestDetailLabel = new Label("jvn.project not loaded");
  private final Label contentValueLabel = new Label("--");
  private final Label contentDetailLabel = new Label("Scripts and assets unavailable");
  private final Label statusLabel = new Label("Choose an action to continue.");
  private final VBox firstRunPanel = new VBox(8);
  private final VBox firstRunItems = new VBox(6);

  private final Button btnNewProject = new Button();
  private final Button btnOpenProject = new Button();
  private final Button btnRunProject = new Button();
  private final Button btnOpenProjectExplorer = new Button();
  private final Button btnOpenHelpCenter = new Button();
  private final Button btnSettings = new Button();

  private File workspaceRoot;
  private File projectRoot;

  private Runnable onCreateProject;
  private Runnable onOpenProjectDialog;
  private Runnable onRunProject;
  private Runnable onShowProjectExplorer;
  private Runnable onShowHelpCenter;
  private Runnable onShowSettings;

  public EditorWorkspaceHubView() {
    buildUi();
  }

  public void setWorkspaceRoot(File workspaceRoot) {
    this.workspaceRoot = normalizeDir(workspaceRoot);
    refreshSummary();
  }

  public void setCurrentProject(File projectRoot) {
    this.projectRoot = normalizeDir(projectRoot);
    btnRunProject.setDisable(this.projectRoot == null);
    refreshSummary();
  }

  public void setOnCreateProject(Runnable onCreateProject) {
    this.onCreateProject = onCreateProject;
  }

  public void setOnOpenProjectDialog(Runnable onOpenProjectDialog) {
    this.onOpenProjectDialog = onOpenProjectDialog;
  }

  public void setOnRunProject(Runnable onRunProject) {
    this.onRunProject = onRunProject;
  }

  public void setOnShowProjectExplorer(Runnable onShowProjectExplorer) {
    this.onShowProjectExplorer = onShowProjectExplorer;
  }

  public void setOnShowHelpCenter(Runnable onShowHelpCenter) {
    this.onShowHelpCenter = onShowHelpCenter;
  }

  public void setOnShowSettings(Runnable onShowSettings) {
    this.onShowSettings = onShowSettings;
    btnSettings.setDisable(onShowSettings == null);
  }

  public void setFirstRunIssues(Collection<SetupIssue> issues) {
    List<SetupIssue> visibleIssues = issues == null
        ? List.of()
        : issues.stream()
            .filter(issue -> issue != null && issue.isVisible())
            .toList();
    firstRunItems.getChildren().clear();
    for (SetupIssue issue : visibleIssues) {
      firstRunItems.getChildren().add(setupIssueRow(issue));
    }
    boolean visible = !visibleIssues.isEmpty();
    firstRunPanel.setVisible(visible);
    firstRunPanel.setManaged(visible);
  }

  private void buildUi() {
    getStyleClass().add("editor-workspace-hub-root");
    setPadding(new Insets(14));

    kickerLabel.getStyleClass().add("editor-workspace-kicker");
    headingLabel.getStyleClass().addAll("welcome-heading", "editor-workspace-heading");
    introLabel.getStyleClass().add("welcome-intro-text");
    healthChipLabel.getStyleClass().addAll("editor-workspace-health-chip", "editor-workspace-health-info");
    runtimeChipLabel.getStyleClass().add("welcome-version-chip");
    statusLabel.getStyleClass().add("welcome-status-text");

    configureActionButton(
        btnNewProject,
        CssIcon.plus("#8bcf98"),
        "New Project",
        "Create a new project",
        "welcome-action-button-primary",
        () -> runAction(onCreateProject, "New Project"));
    configureActionButton(
        btnOpenProject,
        CssIcon.folder("#d5b36a"),
        "Open Project",
        "Open an existing project folder",
        "welcome-action-button-secondary",
        () -> runAction(onOpenProjectDialog, "Open Project"));
    configureActionButton(
        btnRunProject,
        CssIcon.play("#8bcf98"),
        "Run Project",
        "Run currently selected project",
        "welcome-action-button-secondary",
        () -> runAction(onRunProject, "Run Project"));
    btnRunProject.setDisable(true);

    configureActionButton(
        btnOpenProjectExplorer,
        CssIcon.list("#d6cab8"),
        "Project Explorer",
        "Open Project Explorer tab",
        "welcome-action-button-secondary",
        () -> runAction(onShowProjectExplorer, "Project Explorer"));
    configureActionButton(
        btnOpenHelpCenter,
        CssIcon.search("#d6cab8"),
        "Help Center",
        "Open Help Center tab",
        "welcome-action-button-secondary",
        () -> runAction(onShowHelpCenter, "Help Center"));

    configureIconButton(
        btnSettings,
        CssIcon.settings("#d6cab8"),
        "Settings",
        "Configure editor defaults",
        () -> runAction(onShowSettings, "Editor Settings"));
    btnSettings.setDisable(true);

    HBox rowPrimary = new HBox(8, btnNewProject, btnOpenProject, btnRunProject);
    rowPrimary.getStyleClass().add("welcome-action-row");
    rowPrimary.setAlignment(Pos.CENTER_LEFT);

    HBox rowSecondary = new HBox(8, btnOpenProjectExplorer, btnOpenHelpCenter);
    rowSecondary.getStyleClass().add("welcome-action-row");
    rowSecondary.setAlignment(Pos.CENTER_LEFT);

    Region headingSpacer = new Region();
    HBox.setHgrow(headingSpacer, Priority.ALWAYS);
    VBox titleBlock = new VBox(3, kickerLabel, headingLabel, introLabel);
    titleBlock.getStyleClass().add("editor-workspace-title-block");
    HBox headingRow = new HBox(10, titleBlock, headingSpacer, healthChipLabel, runtimeChipLabel, btnSettings);
    headingRow.setAlignment(Pos.CENTER_LEFT);

    FlowPane summaryGrid = new FlowPane(10, 10,
        summaryCard("Workspace", workspaceValueLabel, workspaceDetailLabel),
        summaryCard("Project", projectValueLabel, projectDetailLabel),
        summaryCard("Entry", manifestValueLabel, manifestDetailLabel),
        summaryCard("Content", contentValueLabel, contentDetailLabel));
    summaryGrid.getStyleClass().add("editor-workspace-summary-grid");
    summaryGrid.setAlignment(Pos.CENTER_LEFT);

    VBox actionPanel = new VBox(8, rowPrimary, rowSecondary);
    actionPanel.getStyleClass().add("editor-workspace-action-panel");

    Label setupTitle = new Label("First-run setup");
    setupTitle.getStyleClass().add("editor-workspace-setup-title");
    Label setupSummary = new Label("A few environment checks need attention before builds, publishing, or GitHub workflows feel smooth.");
    setupSummary.getStyleClass().add("editor-workspace-setup-summary");
    setupSummary.setWrapText(true);
    firstRunPanel.getStyleClass().add("editor-workspace-setup-panel");
    firstRunPanel.getChildren().addAll(setupTitle, setupSummary, firstRunItems);
    firstRunPanel.setVisible(false);
    firstRunPanel.setManaged(false);

    VBox hero = new VBox(14, headingRow, summaryGrid, actionPanel, statusLabel);
    hero.setPadding(new Insets(18));
    hero.getStyleClass().addAll("welcome-hero-card", "editor-workspace-panel");

    Region fill = new Region();
    VBox.setVgrow(fill, Priority.ALWAYS);
    VBox content = new VBox(10, hero, firstRunPanel, fill);
    content.getStyleClass().add("welcome-center-body");
    setCenter(content);
    refreshSummary();
  }

  private VBox setupIssueRow(SetupIssue issue) {
    Label severity = new Label(issue.severityLabel());
    severity.getStyleClass().addAll("editor-workspace-setup-chip", "editor-workspace-setup-" + issue.severityClass());

    Label title = new Label(issue.title());
    title.getStyleClass().add("editor-workspace-setup-item-title");
    title.setWrapText(true);

    Label detail = new Label(issue.detail());
    detail.getStyleClass().add("editor-workspace-setup-item-detail");
    detail.setWrapText(true);

    Label fix = new Label(issue.fix());
    fix.getStyleClass().add("editor-workspace-setup-item-fix");
    fix.setWrapText(true);

    HBox heading = new HBox(8, severity, title);
    heading.setAlignment(Pos.CENTER_LEFT);
    VBox row = new VBox(4, heading, detail, fix);
    row.getStyleClass().add("editor-workspace-setup-item");
    row.setMaxWidth(Double.MAX_VALUE);
    return row;
  }

  private VBox summaryCard(String title, Label value, Label detail) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("editor-workspace-summary-title");
    value.getStyleClass().add("editor-workspace-summary-value");
    value.setWrapText(false);
    value.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
    value.setMaxWidth(198);
    detail.getStyleClass().add("editor-workspace-summary-detail");
    detail.setWrapText(true);
    detail.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
    detail.setMaxWidth(198);

    VBox card = new VBox(4, titleLabel, value, detail);
    card.getStyleClass().add("editor-workspace-summary-card");
    card.setMinWidth(170);
    card.setPrefWidth(210);
    return card;
  }

  private void refreshSummary() {
    runtimeChipLabel.setText(runtimeChipText());

    if (workspaceRoot == null) {
      workspaceValueLabel.setText("No workspace");
      workspaceDetailLabel.setText("Launch from a JVN checkout to enable workspace tasks");
    } else {
      workspaceValueLabel.setText(displayName(workspaceRoot));
      workspaceDetailLabel.setText(displayPath(workspaceRoot));
    }

    if (projectRoot == null) {
      setHealthChip("NO PROJECT", "info");
      projectValueLabel.setText("No project selected");
      projectDetailLabel.setText("Open or create a project to enable run/build tools");
      manifestValueLabel.setText("No manifest");
      manifestDetailLabel.setText("jvn.project not loaded");
      contentValueLabel.setText("--");
      contentDetailLabel.setText("Scripts and assets unavailable");
      statusLabel.setText("Open a project or create a new one to start authoring.");
      return;
    }

    projectValueLabel.setText(displayName(projectRoot));
    projectDetailLabel.setText(displayPath(projectRoot));

    Properties manifest = loadManifest(projectRoot);
    if (manifest == null) {
      setHealthChip("SETUP NEEDED", "warn");
      manifestValueLabel.setText("Missing manifest");
      manifestDetailLabel.setText("Create or open a valid JVN project manifest");
    } else {
      ManifestSummary summary = summarizeManifest(projectRoot, manifest);
      setHealthChip(summary.healthText(), summary.healthTone());
      manifestValueLabel.setText(summary.title());
      manifestDetailLabel.setText(summary.detail());
    }

    int scriptCount = countFiles(new File(projectRoot, "scripts"), 0, ".vns", ".jes");
    int assetCount = countFiles(new File(projectRoot, "assets"), 0);
    contentValueLabel.setText(compactCount(scriptCount, "script") + " / " + compactCount(assetCount, "asset"));
    contentDetailLabel.setText("Modified " + MODIFIED_FORMAT.format(Instant.ofEpochMilli(projectRoot.lastModified())));
    statusLabel.setText("Ready: project tools are available for " + displayName(projectRoot) + ".");
  }

  private ManifestSummary summarizeManifest(File root, Properties manifest) {
    String type = manifest.getProperty("type", "vn").trim().toLowerCase(Locale.ROOT);
    String typeLabel = type.isBlank() ? "JVN" : type.toUpperCase(Locale.ROOT);
    if (!"vn".equals(type) && !"jes".equals(type)) {
      return new ManifestSummary(
          typeLabel + " workspace",
          "type=" + (type.isBlank() ? "(unspecified)" : type),
          "READY",
          "ok");
    }

    String entryKey = "jes".equals(type) ? "entry" : "entryVns";
    String fallback = "jes".equals(type) ? "scripts/main.jes" : "(auto)";
    String entry = manifest.getProperty(entryKey, fallback).trim();
    if (entry.isBlank() || "(auto)".equalsIgnoreCase(entry)) {
      return new ManifestSummary(typeLabel + " auto entry", entryKey + "=(auto)", "READY", "ok");
    }

    File entryFile = resolveProjectFile(root, entry);
    if (entryFile != null && entryFile.isFile()) {
      return new ManifestSummary(typeLabel + " entry ready", entryKey + "=" + entry, "READY", "ok");
    }
    return new ManifestSummary(typeLabel + " entry missing", entryKey + "=" + entry, "CHECK ENTRY", "warn");
  }

  private File resolveProjectFile(File root, String rawPath) {
    if (root == null || rawPath == null || rawPath.isBlank()) return null;
    String normalized = rawPath.trim().replace('\\', '/');
    File direct = new File(root, normalized);
    if (direct.exists()) return direct;
    if (!normalized.startsWith("scripts/") && !normalized.startsWith("game/scripts/")) {
      File script = new File(root, "scripts/" + normalized);
      if (script.exists()) return script;
    }
    if (normalized.startsWith("game/")) {
      File stripped = new File(root, normalized.substring("game/".length()));
      if (stripped.exists()) return stripped;
    }
    return direct;
  }

  private void setHealthChip(String text, String tone) {
    healthChipLabel.setText(text == null || text.isBlank() ? "STATUS" : text);
    healthChipLabel.getStyleClass().removeAll(
        "editor-workspace-health-ok",
        "editor-workspace-health-warn",
        "editor-workspace-health-error",
        "editor-workspace-health-info");
    healthChipLabel.getStyleClass().add(switch (tone == null ? "" : tone) {
      case "ok" -> "editor-workspace-health-ok";
      case "warn" -> "editor-workspace-health-warn";
      case "error" -> "editor-workspace-health-error";
      default -> "editor-workspace-health-info";
    });
  }

  private static void configureActionButton(Button button,
                                            Region icon,
                                            String text,
                                            String tooltipText,
                                            String styleClass,
                                            Runnable action) {
    if (button == null) return;
    button.setText(text == null ? "" : text);
    button.setGraphic(icon);
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setMinHeight(34);
    button.setPrefHeight(34);
    button.setMaxHeight(34);
    button.setFocusTraversable(false);
    if (styleClass != null && !styleClass.isBlank()) {
      button.getStyleClass().add(styleClass);
    }
    if (tooltipText != null && !tooltipText.isBlank()) {
      button.setTooltip(new Tooltip(tooltipText));
      button.setAccessibleText(tooltipText);
    }
    button.setOnAction(e -> {
      if (action != null) action.run();
      e.consume();
    });
  }

  private static void configureIconButton(Button button,
                                          Region icon,
                                          String accessibleText,
                                          String tooltipText,
                                          Runnable action) {
    if (button == null) return;
    button.setText("");
    button.setGraphic(icon);
    button.setMinSize(34, 34);
    button.setPrefSize(34, 34);
    button.setMaxSize(34, 34);
    button.setFocusTraversable(false);
    button.getStyleClass().add("welcome-settings-button");
    button.setAccessibleText(accessibleText == null ? tooltipText : accessibleText);
    if (tooltipText != null && !tooltipText.isBlank()) {
      button.setTooltip(new Tooltip(tooltipText));
    }
    button.setOnAction(e -> {
      if (action != null) action.run();
      e.consume();
    });
  }

  private void runAction(Runnable action, String actionLabel) {
    if (action == null) return;
    action.run();
    if (actionLabel != null && !actionLabel.isBlank()) {
      statusLabel.setText("Opened: " + actionLabel);
    }
  }

  private File normalizeDir(File dir) {
    if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
    return dir.getAbsoluteFile();
  }

  private Properties loadManifest(File dir) {
    if (dir == null) return null;
    File manifest = new File(dir, "jvn.project");
    if (!manifest.isFile()) return null;
    try (FileInputStream in = new FileInputStream(manifest)) {
      Properties props = new Properties();
      props.load(in);
      return props;
    } catch (Exception ignored) {
      return null;
    }
  }

  private int countFiles(File dir, int current, String... extensions) {
    if (dir == null || !dir.isDirectory() || current >= COUNT_LIMIT) return current;
    File[] files = dir.listFiles();
    if (files == null) return current;
    int total = current;
    for (File file : files) {
      if (file == null || file.isHidden()) continue;
      if (file.isDirectory()) {
        total = countFiles(file, total, extensions);
      } else if (matchesExtension(file, extensions)) {
        total++;
      }
      if (total >= COUNT_LIMIT) return COUNT_LIMIT;
    }
    return total;
  }

  private boolean matchesExtension(File file, String... extensions) {
    if (file == null || !file.isFile()) return false;
    if (extensions == null || extensions.length == 0) return true;
    String name = file.getName().toLowerCase(Locale.ROOT);
    for (String extension : extensions) {
      if (extension != null && name.endsWith(extension.toLowerCase(Locale.ROOT))) return true;
    }
    return false;
  }

  private String compactCount(int count, String noun) {
    String value = count >= COUNT_LIMIT ? COUNT_LIMIT + "+" : Integer.toString(Math.max(0, count));
    return value + " " + noun + (count == 1 ? "" : "s");
  }

  private String runtimeChipText() {
    String version = System.getProperty("jvn.version", "").trim();
    String javaVersion = System.getProperty("java.version", "").trim();
    String build = version.isBlank() ? "dev" : version;
    String javaMajor = javaVersion.isBlank() ? "Java ?" : "Java " + javaVersion.split("\\.")[0];
    return "JVN " + build + " | " + javaMajor;
  }

  private String displayPath(File dir) {
    if (dir == null) return "--";
    String path = dir.getAbsolutePath();
    String home = System.getProperty("user.home", "").trim();
    if (!home.isBlank() && path.startsWith(home)) {
      return "~" + path.substring(home.length());
    }
    return path;
  }

  private String displayName(File dir) {
    if (dir == null) return "no project selected";
    String name = dir.getName();
    return name == null || name.isBlank() ? dir.getAbsolutePath() : name;
  }

  private record ManifestSummary(String title, String detail, String healthText, String healthTone) {
  }

  public record SetupIssue(String severity, String title, String detail, String fix) {
    private boolean isVisible() {
      return title != null && !title.isBlank();
    }

    private String severityLabel() {
      return switch (severityClass()) {
        case "error" -> "FIX";
        case "warn" -> "CHECK";
        default -> "INFO";
      };
    }

    private String severityClass() {
      String normalized = severity == null ? "" : severity.trim().toLowerCase(Locale.ROOT);
      return switch (normalized) {
        case "error", "fix" -> "error";
        case "warn", "warning", "check" -> "warn";
        default -> "info";
      };
    }

    public static List<SetupIssue> copyOf(Collection<SetupIssue> issues) {
      if (issues == null || issues.isEmpty()) return List.of();
      return new ArrayList<>(issues);
    }
  }
}
