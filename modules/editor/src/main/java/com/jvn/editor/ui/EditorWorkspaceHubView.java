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

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import javafx.util.Duration;

/**
 * Lightweight in-editor hub tab that replaces the full launcher-style welcome view.
 */
public class EditorWorkspaceHubView extends BorderPane {
  private static final int COUNT_LIMIT = 999;
  private static final double HEADER_META_MIN_TITLE_WIDTH = 300.0;
  private static final double HEADER_META_EXTRA_SPACE = 28.0;
  private static final Duration HEADER_META_ANIMATION_DURATION = Duration.millis(145);
  private static final DateTimeFormatter MODIFIED_FORMAT =
      DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

  private final Label kickerLabel = new Label("JVN EDITOR");
  private final Label headingLabel = new Label("Welcome to JVN");
  private final Label introLabel = new Label("Create, open, or continue a visual novel project.");
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
  private final Label statusLabel = new Label();
  private HBox headerMetaChips;
  private Timeline headerMetaAnimation;
  private boolean headerMetaVisible = true;
  private VBox manifestSummaryCard;
  private VBox contentSummaryCard;

  private final Button btnNewProject = new Button();
  private final Button btnOpenProject = new Button();
  private final Button btnRunProject = new Button();
  private final Button btnSettings = new Button();

  private File workspaceRoot;
  private File projectRoot;

  private Runnable onCreateProject;
  private Runnable onOpenProjectDialog;
  private Runnable onRunProject;
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
    btnRunProject.setVisible(this.projectRoot != null);
    btnRunProject.setManaged(this.projectRoot != null);
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

  public void setOnShowSettings(Runnable onShowSettings) {
    this.onShowSettings = onShowSettings;
    btnSettings.setDisable(onShowSettings == null);
  }

  public void setFirstRunIssues(Collection<SetupIssue> issues) {
    // Startup checks still feed the splash sequence, but are intentionally omitted here.
  }

  private static void configureHeaderChip(Label label) {
    if (label == null) return;
    label.setTextOverrun(OverrunStyle.CLIP);
    label.setMinWidth(Region.USE_PREF_SIZE);
    label.setMaxWidth(Region.USE_PREF_SIZE);
  }

  private void installHeaderMetaAutoHide(HBox headingRow, Region titleBlock) {
    Runnable update = () -> updateHeaderMetaVisibility(headingRow, titleBlock);
    headingRow.widthProperty().addListener((obs, oldValue, newValue) -> update.run());
    titleBlock.widthProperty().addListener((obs, oldValue, newValue) -> update.run());
    widthProperty().addListener((obs, oldValue, newValue) -> update.run());
    Platform.runLater(update);
  }

  private void updateHeaderMetaVisibility(HBox headingRow, Region titleBlock) {
    if (headingRow == null || titleBlock == null || headerMetaChips == null) return;
    double rowWidth = headingRow.getWidth();
    double chipsWidth = headerMetaChips.prefWidth(-1);
    if (rowWidth <= 0.0 || chipsWidth <= 0.0) return;

    double settingsWidth = btnSettings.isManaged() ? btnSettings.prefWidth(-1) : 0.0;
    double titleWidth = Math.max(HEADER_META_MIN_TITLE_WIDTH, Math.min(titleBlock.prefWidth(-1), 390.0));
    double requiredWidth = titleWidth + chipsWidth + settingsWidth + headingRow.getSpacing() * 3.0 + HEADER_META_EXTRA_SPACE;
    setHeaderMetaVisible(rowWidth >= requiredWidth);
  }

  private void setHeaderMetaVisible(boolean visible) {
    if (headerMetaChips == null || headerMetaVisible == visible) return;
    headerMetaVisible = visible;
    if (headerMetaAnimation != null) {
      headerMetaAnimation.stop();
    }

    if (visible) {
      headerMetaChips.setManaged(true);
      headerMetaChips.setVisible(true);
    }
    headerMetaChips.setMouseTransparent(!visible);

    double targetOpacity = visible ? 1.0 : 0.0;
    double targetScale = visible ? 1.0 : 0.9;
    headerMetaAnimation = new Timeline(
        new KeyFrame(
            HEADER_META_ANIMATION_DURATION,
            new KeyValue(headerMetaChips.opacityProperty(), targetOpacity, Interpolator.EASE_BOTH),
            new KeyValue(headerMetaChips.scaleXProperty(), targetScale, Interpolator.EASE_BOTH),
            new KeyValue(headerMetaChips.scaleYProperty(), targetScale, Interpolator.EASE_BOTH)));
    headerMetaAnimation.setOnFinished(event -> {
      if (!headerMetaVisible) {
        headerMetaChips.setVisible(false);
        headerMetaChips.setManaged(false);
      }
    });
    headerMetaAnimation.play();
  }

  private void buildUi() {
    getStyleClass().add("editor-workspace-hub-root");
    setPadding(new Insets(14));

    kickerLabel.getStyleClass().add("editor-workspace-kicker");
    headingLabel.getStyleClass().addAll("welcome-heading", "editor-workspace-heading");
    introLabel.getStyleClass().add("welcome-intro-text");
    runtimeChipLabel.getStyleClass().add("welcome-version-chip");
    statusLabel.getStyleClass().add("welcome-status-text");
    configureHeaderChip(healthChipLabel);
    configureHeaderChip(runtimeChipLabel);

    configureActionButton(
        btnNewProject,
        AeroIcon.of(AeroIcon.Kind.NEW_PROJECT, 22),
        "New Project",
        "Create a new project",
        "welcome-action-button-primary",
        () -> runAction(onCreateProject, "New Project"));
    configureActionButton(
        btnOpenProject,
        AeroIcon.of(AeroIcon.Kind.OPEN_PROJECT, 22),
        "Open Project",
        "Open an existing project folder",
        "welcome-action-button-secondary",
        () -> runAction(onOpenProjectDialog, "Open Project"));
    configureActionButton(
        btnRunProject,
        AeroIcon.of(AeroIcon.Kind.RUN, 22),
        "Run Project",
        "Run currently selected project",
        "welcome-action-button-secondary",
        () -> runAction(onRunProject, "Run Project"));
    btnRunProject.setDisable(true);
    btnRunProject.setVisible(false);
    btnRunProject.setManaged(false);

    configureIconButton(
        btnSettings,
        AeroIcon.of(AeroIcon.Kind.SETTINGS, 22),
        "Settings",
        "Configure editor defaults",
        () -> runAction(onShowSettings, "Editor Settings"));
    btnSettings.setDisable(true);

    HBox rowPrimary = new HBox(8, btnNewProject, btnOpenProject, btnRunProject);
    rowPrimary.getStyleClass().add("welcome-action-row");
    rowPrimary.setAlignment(Pos.CENTER_LEFT);

    Region headingSpacer = new Region();
    HBox.setHgrow(headingSpacer, Priority.ALWAYS);
    VBox titleBlock = new VBox(3, kickerLabel, headingLabel, introLabel);
    titleBlock.setMinWidth(0);
    titleBlock.getStyleClass().add("editor-workspace-title-block");
    headerMetaChips = new HBox(runtimeChipLabel);
    headerMetaChips.getStyleClass().add("editor-workspace-header-meta");
    headerMetaChips.setAlignment(Pos.CENTER_LEFT);
    headerMetaChips.setMinWidth(Region.USE_PREF_SIZE);
    HBox headingRow = new HBox(10, titleBlock, headingSpacer, headerMetaChips, btnSettings);
    headingRow.setAlignment(Pos.CENTER_LEFT);
    installHeaderMetaAutoHide(headingRow, titleBlock);

    Label contextTitle = new Label("CURRENT CONTEXT");
    contextTitle.getStyleClass().add("editor-workspace-context-title");
    manifestSummaryCard = summaryCard("Entry", manifestValueLabel, manifestDetailLabel);
    contentSummaryCard = summaryCard("Content", contentValueLabel, contentDetailLabel);
    FlowPane summaryGrid = new FlowPane(14, 10,
        summaryCard("Workspace", workspaceValueLabel, workspaceDetailLabel),
        summaryCard("Project", projectValueLabel, projectDetailLabel),
        manifestSummaryCard,
        contentSummaryCard);
    summaryGrid.getStyleClass().add("editor-workspace-summary-grid");
    summaryGrid.setAlignment(Pos.CENTER_LEFT);

    VBox contextPanel = new VBox(10, contextTitle, summaryGrid);
    contextPanel.getStyleClass().add("editor-workspace-context-panel");

    VBox actionPanel = new VBox(rowPrimary);
    actionPanel.getStyleClass().add("editor-workspace-action-panel");

    VBox hero = new VBox(20, headingRow, actionPanel, contextPanel, statusLabel);
    hero.setPadding(new Insets(24));
    hero.getStyleClass().addAll("welcome-hero-card", "editor-workspace-panel");

    Region fill = new Region();
    VBox.setVgrow(fill, Priority.ALWAYS);
    VBox content = new VBox(10, hero, fill);
    content.getStyleClass().add("welcome-center-body");
    setCenter(content);
    refreshSummary();
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
    card.setMinWidth(190);
    card.setPrefWidth(230);
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
      setSummaryCardVisible(manifestSummaryCard, false);
      setSummaryCardVisible(contentSummaryCard, false);
      projectValueLabel.setText("No project selected");
      projectDetailLabel.setText("Choose New Project or Open Project to begin");
      statusLabel.setText("");
      statusLabel.setVisible(false);
      statusLabel.setManaged(false);
      return;
    }

    setSummaryCardVisible(manifestSummaryCard, true);
    setSummaryCardVisible(contentSummaryCard, true);
    statusLabel.setVisible(true);
    statusLabel.setManaged(true);

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

  private static void setSummaryCardVisible(Region card, boolean visible) {
    if (card == null) return;
    card.setVisible(visible);
    card.setManaged(visible);
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
    if (icon instanceof AeroIcon) button.getStyleClass().add("aero-icon-button");
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setMinHeight(42);
    button.setPrefHeight(42);
    button.setMaxHeight(42);
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
    if (icon instanceof AeroIcon) button.getStyleClass().add("aero-icon-button");
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
