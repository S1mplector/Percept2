package com.jvn.editor.ui;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Lightweight in-editor hub tab that replaces the full launcher-style welcome view.
 */
public class EditorWorkspaceHubView extends BorderPane {
  private final Label headingLabel = new Label("Welcome to JVN Editor");
  private final Label workspaceLabel = new Label("Workspace: --");
  private final Label projectLabel = new Label("Project: no project selected");
  private final Label statusLabel = new Label("Choose an action to continue.");

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
    workspaceLabel.setText("Workspace: " + displayPath(this.workspaceRoot));
  }

  public void setCurrentProject(File projectRoot) {
    this.projectRoot = normalizeDir(projectRoot);
    projectLabel.setText("Project: " + displayName(this.projectRoot));
    btnRunProject.setDisable(this.projectRoot == null);
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

  private void buildUi() {
    getStyleClass().add("editor-workspace-hub-root");
    setPadding(new Insets(14));

    headingLabel.getStyleClass().add("welcome-heading");
    workspaceLabel.getStyleClass().add("welcome-overview-detail");
    projectLabel.getStyleClass().add("welcome-overview-detail");
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
    HBox headingRow = new HBox(8, headingLabel, headingSpacer, btnSettings);
    headingRow.setAlignment(Pos.CENTER_LEFT);

    VBox hero = new VBox(12, headingRow, workspaceLabel, projectLabel, rowPrimary, rowSecondary, statusLabel);
    hero.setPadding(new Insets(12));
    hero.getStyleClass().add("welcome-hero-card");

    Region fill = new Region();
    VBox.setVgrow(fill, Priority.ALWAYS);
    VBox content = new VBox(10, hero, fill);
    content.getStyleClass().add("welcome-center-body");
    setCenter(content);
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
}
