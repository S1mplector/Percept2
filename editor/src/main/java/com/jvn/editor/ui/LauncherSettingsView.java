package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class LauncherSettingsView extends BorderPane {
  private static final String TEXT_EDITOR_LABEL_JVN = "JVN Editor";
  private static final String TEXT_EDITOR_LABEL_SYSTEM = "System Default App";
  private static final String TEXT_EDITOR_LABEL_CUSTOM = "Custom Command";
  private static final String THEME_LABEL_DARK = "Dark";
  private static final String THEME_LABEL_LIGHT = "Light";

  private final EditorPreferencesStore store;
  private final ComboBox<String> themeCombo = new ComboBox<>();
  private final ComboBox<String> defaultTextEditorCombo = new ComboBox<>();
  private final TextField customTextEditorCommandField = new TextField();
  private final CheckBox restoreLastProjectCheck =
      new CheckBox("Restore the last selected project on startup");
  private final CheckBox keepLauncherOpenCheck =
      new CheckBox("Keep launcher open after opening the editor");
  private final CheckBox confirmRunProjectCheck =
      new CheckBox("Confirm before running a project from the launcher");
  private final CheckBox launcherRuntimePerfHudCheck =
      new CheckBox("Show runtime performance HUD when launching projects");
  private final TextField lastProjectPathField = new TextField();
  private final Label statusLabel = new Label("Launcher settings loaded");

  private Consumer<EditorPreferences> onPreferencesApplied;
  private File currentProject;

  public LauncherSettingsView(EditorPreferencesStore store) {
    this.store = store == null ? new EditorPreferencesStore() : store;
    setPadding(Insets.EMPTY);
    getStyleClass().add("editor-settings-view");

    ToolBar toolbar = new ToolBar();
    toolbar.getStyleClass().add("editor-settings-toolbar");
    Button reloadButton = new Button("Reload");
    reloadButton.setOnAction(e -> reload());
    reloadButton.getStyleClass().add("editor-settings-button");
    Button saveButton = new Button("Save");
    saveButton.setOnAction(e -> save());
    saveButton.getStyleClass().add("editor-settings-button");
    Button defaultsButton = new Button("Defaults");
    defaultsButton.setOnAction(e -> {
      EditorPreferences defaults = store.load();
      if (defaults == null) defaults = EditorPreferences.defaults();
      defaults.setLauncherTheme(EditorPreferences.LAUNCHER_THEME_DARK);
      defaults.setDefaultTextEditor(EditorPreferences.TEXT_EDITOR_JVN);
      defaults.setCustomTextEditorCommand("");
      defaults.setLauncherRestoreLastProject(true);
      defaults.setLauncherLastProjectPath("");
      defaults.setLauncherKeepOpenAfterEditorLaunch(false);
      defaults.setLauncherConfirmRunProject(false);
      defaults.setLauncherRuntimePerfHud(true);
      loadIntoForm(defaults);
      statusLabel.setText("Launcher defaults restored in form");
    });
    defaultsButton.getStyleClass().add("editor-settings-button");
    toolbar.getItems().addAll(reloadButton, saveButton, defaultsButton);
    setTop(toolbar);

    VBox content = new VBox(14);
    content.setPadding(new Insets(12));
    content.getStyleClass().add("editor-settings-content");

    Label header = new Label("Launcher Settings");
    header.getStyleClass().add("editor-settings-header");
    Label intro = new Label(
        "Configure launcher appearance, startup project behavior, and editor handoff.");
    intro.setWrapText(true);
    intro.getStyleClass().add("editor-settings-copy");

    themeCombo.getItems().addAll(THEME_LABEL_DARK, THEME_LABEL_LIGHT);
    themeCombo.setMaxWidth(Double.MAX_VALUE);
    themeCombo.getStyleClass().add("editor-settings-combo");

    defaultTextEditorCombo.getItems().addAll(
        TEXT_EDITOR_LABEL_JVN,
        TEXT_EDITOR_LABEL_SYSTEM,
        TEXT_EDITOR_LABEL_CUSTOM);
    defaultTextEditorCombo.setMaxWidth(Double.MAX_VALUE);
    defaultTextEditorCombo.getStyleClass().add("editor-settings-combo");
    defaultTextEditorCombo.valueProperty().addListener((obs, oldValue, newValue) ->
        updateCustomCommandState());

    customTextEditorCommandField.setPromptText("Example: code --reuse-window {file}");
    customTextEditorCommandField.getStyleClass().add("editor-settings-text-field");
    restoreLastProjectCheck.getStyleClass().add("editor-settings-check");
    keepLauncherOpenCheck.getStyleClass().add("editor-settings-check");
    confirmRunProjectCheck.getStyleClass().add("editor-settings-check");
    launcherRuntimePerfHudCheck.getStyleClass().add("editor-settings-check");
    lastProjectPathField.setEditable(false);
    lastProjectPathField.setPromptText("No project stored yet");
    lastProjectPathField.getStyleClass().add("editor-settings-text-field");

    Button useCurrentProjectButton = new Button("Use Current");
    useCurrentProjectButton.getStyleClass().add("editor-settings-button");
    useCurrentProjectButton.setOnAction(e -> useCurrentProject());
    Button clearProjectButton = new Button("Clear");
    clearProjectButton.getStyleClass().add("editor-settings-button");
    clearProjectButton.setOnAction(e -> lastProjectPathField.clear());
    HBox lastProjectControls =
        new HBox(8, lastProjectPathField, useCurrentProjectButton, clearProjectButton);
    lastProjectControls.getStyleClass().add("editor-settings-inline-row");
    HBox.setHgrow(lastProjectPathField, Priority.ALWAYS);

    GridPane appearanceGrid = settingsGrid(190);
    appearanceGrid.addRow(0, fieldLabel("Launcher Theme"), themeCombo);
    VBox appearanceSection =
        settingsSection("Appearance", "Launcher window styling.", appearanceGrid);

    GridPane startupGrid = settingsGrid(190);
    startupGrid.add(restoreLastProjectCheck, 1, 0);
    startupGrid.addRow(1, fieldLabel("Startup Project"), lastProjectControls);
    VBox startupSection =
        settingsSection("Startup Project", "Project selection restored when the launcher opens.", startupGrid);

    GridPane handoffGrid = settingsGrid(190);
    handoffGrid.addRow(0, fieldLabel("Default Text Editor"), defaultTextEditorCombo);
    handoffGrid.addRow(1, fieldLabel("Custom Command"), customTextEditorCommandField);
    handoffGrid.add(keepLauncherOpenCheck, 1, 2);
    VBox handoffSection =
        settingsSection("Editor Handoff", "How project files and editor launches leave the launcher.", handoffGrid);

    GridPane runGrid = settingsGrid(190);
    runGrid.add(confirmRunProjectCheck, 1, 0);
    runGrid.add(launcherRuntimePerfHudCheck, 1, 1);
    VBox runSection =
        settingsSection("Run Behavior", "Defaults used when running projects directly from the launcher.", runGrid);

    content.getChildren().addAll(
        header,
        intro,
        new Separator(),
        appearanceSection,
        startupSection,
        handoffSection,
        runSection);

    ScrollPane scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.getStyleClass().add("editor-settings-scroll");
    setCenter(scrollPane);
    statusLabel.getStyleClass().add("editor-settings-status");
    setBottom(statusLabel);

    reload();
  }

  public void setCurrentProject(File currentProject) {
    this.currentProject = currentProject == null ? null : currentProject.getAbsoluteFile();
  }

  public void setOnPreferencesApplied(Consumer<EditorPreferences> onPreferencesApplied) {
    this.onPreferencesApplied = onPreferencesApplied;
  }

  public void reload() {
    loadIntoForm(store.load());
    statusLabel.setText("Launcher settings loaded");
  }

  public void loadIntoForm(EditorPreferences preferences) {
    EditorPreferences model = preferences == null ? EditorPreferences.defaults() : preferences.copy();
    themeCombo.setValue(themeLabel(model.getLauncherTheme()));
    defaultTextEditorCombo.setValue(textEditorLabel(model.getDefaultTextEditor()));
    customTextEditorCommandField.setText(model.getCustomTextEditorCommand());
    restoreLastProjectCheck.setSelected(model.isLauncherRestoreLastProject());
    lastProjectPathField.setText(model.getLauncherLastProjectPath());
    keepLauncherOpenCheck.setSelected(model.isLauncherKeepOpenAfterEditorLaunch());
    confirmRunProjectCheck.setSelected(model.isLauncherConfirmRunProject());
    launcherRuntimePerfHudCheck.setSelected(model.isLauncherRuntimePerfHud());
    updateCustomCommandState();
  }

  public void save() {
    EditorPreferences preferences = store.load();
    if (preferences == null) preferences = EditorPreferences.defaults();
    else preferences = preferences.copy();
    preferences.setLauncherTheme(themeValue(themeCombo.getValue()));
    preferences.setDefaultTextEditor(textEditorValue(defaultTextEditorCombo.getValue()));
    preferences.setCustomTextEditorCommand(customTextEditorCommandField.getText());
    preferences.setLauncherRestoreLastProject(restoreLastProjectCheck.isSelected());
    preferences.setLauncherLastProjectPath(lastProjectPathField.getText());
    preferences.setLauncherKeepOpenAfterEditorLaunch(keepLauncherOpenCheck.isSelected());
    preferences.setLauncherConfirmRunProject(confirmRunProjectCheck.isSelected());
    preferences.setLauncherRuntimePerfHud(launcherRuntimePerfHudCheck.isSelected());
    try {
      store.save(preferences);
      statusLabel.setText("Launcher settings saved");
      if (onPreferencesApplied != null) onPreferencesApplied.accept(preferences.copy());
    } catch (IOException ex) {
      statusLabel.setText("Failed to save launcher settings: " + ex.getMessage());
    }
  }

  private void useCurrentProject() {
    if (currentProject == null || !currentProject.isDirectory()) {
      statusLabel.setText("No current project is selected");
      return;
    }
    lastProjectPathField.setText(currentProject.getAbsolutePath());
    statusLabel.setText("Current project staged as startup project");
  }

  private void updateCustomCommandState() {
    boolean custom = TEXT_EDITOR_LABEL_CUSTOM.equals(defaultTextEditorCombo.getValue());
    customTextEditorCommandField.setDisable(!custom);
  }

  private static Label sectionHeader(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("editor-settings-section-title");
    return label;
  }

  private static Label fieldLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("editor-settings-label");
    return label;
  }

  private static GridPane settingsGrid(double labelWidth) {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    ColumnConstraints labelColumn = new ColumnConstraints();
    labelColumn.setMinWidth(labelWidth);
    ColumnConstraints fieldColumn = new ColumnConstraints();
    fieldColumn.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().addAll(labelColumn, fieldColumn);
    return grid;
  }

  private static VBox settingsSection(String title, String description, GridPane grid) {
    VBox section = new VBox(10);
    section.getStyleClass().add("editor-settings-section");
    section.getChildren().add(sectionHeader(title));
    if (description != null && !description.isBlank()) {
      Label copy = new Label(description);
      copy.setWrapText(true);
      copy.getStyleClass().add("editor-settings-copy");
      section.getChildren().add(copy);
    }
    section.getChildren().add(grid);
    return section;
  }

  private static String themeLabel(String value) {
    return EditorPreferences.LAUNCHER_THEME_LIGHT.equals(EditorPreferences.normalizeLauncherTheme(value))
        ? THEME_LABEL_LIGHT
        : THEME_LABEL_DARK;
  }

  private static String themeValue(String label) {
    return THEME_LABEL_LIGHT.equals(label)
        ? EditorPreferences.LAUNCHER_THEME_LIGHT
        : EditorPreferences.LAUNCHER_THEME_DARK;
  }

  private static String textEditorLabel(String value) {
    return switch (EditorPreferences.normalizeTextEditor(value)) {
      case EditorPreferences.TEXT_EDITOR_SYSTEM -> TEXT_EDITOR_LABEL_SYSTEM;
      case EditorPreferences.TEXT_EDITOR_CUSTOM -> TEXT_EDITOR_LABEL_CUSTOM;
      default -> TEXT_EDITOR_LABEL_JVN;
    };
  }

  private static String textEditorValue(String label) {
    if (TEXT_EDITOR_LABEL_SYSTEM.equals(label)) return EditorPreferences.TEXT_EDITOR_SYSTEM;
    if (TEXT_EDITOR_LABEL_CUSTOM.equals(label)) return EditorPreferences.TEXT_EDITOR_CUSTOM;
    return EditorPreferences.TEXT_EDITOR_JVN;
  }
}
