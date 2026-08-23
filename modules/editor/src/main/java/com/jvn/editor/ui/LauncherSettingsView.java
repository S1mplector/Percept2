package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import javafx.scene.control.Tooltip;
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
  private static final String SETTINGS_SEARCH_TEXT_KEY = "settingsSearchText";

  private final EditorPreferencesStore store;
  private final ComboBox<String> themeCombo = new ComboBox<>();
  private final ComboBox<String> editorThemeCombo = new ComboBox<>();
  private final ComboBox<String> defaultTextEditorCombo = new ComboBox<>();
  private final TextField customTextEditorCommandField = new TextField();
  private final TextField settingsFilterField = new TextField();
  private final CheckBox restoreLastProjectCheck =
      new CheckBox("Restore the last selected project on startup");
  private final CheckBox keepLauncherOpenCheck =
      new CheckBox("Keep launcher open after opening the editor");
  private final CheckBox confirmOpenEditorCheck =
      new CheckBox("Confirm before opening a project in the editor");
  private final CheckBox confirmRunProjectCheck =
      new CheckBox("Confirm before running a project from the launcher");
  private final CheckBox launcherRuntimePerfHudCheck =
      new CheckBox("Show runtime performance HUD when launching projects");
  private final CheckBox gradleSkipTestsOnRunCheck =
      new CheckBox("Skip Gradle tests for default project runs");
  private final TextField lastProjectPathField = new TextField();
  private final Label statusLabel = new Label("Launcher settings loaded");
  private final List<VBox> filterableSections = new ArrayList<>();

  private Consumer<EditorPreferences> onPreferencesApplied;
  private File currentProject;

  public LauncherSettingsView(EditorPreferencesStore store) {
    this.store = store == null ? new EditorPreferencesStore() : store;
    setPadding(Insets.EMPTY);
    getStyleClass().add("editor-settings-view");

    ToolBar toolbar = new ToolBar();
    toolbar.getStyleClass().add("editor-settings-toolbar");
    Button reloadButton = new Button("Reload");
    reloadButton.setGraphic(RefreshIcon.compact());
    reloadButton.setOnAction(e -> reload());
    reloadButton.getStyleClass().add("editor-settings-button");
    reloadButton.setTooltip(new Tooltip("Reload launcher settings from disk"));
    Button saveButton = new Button("Save");
    saveButton.setOnAction(e -> save());
    saveButton.getStyleClass().add("editor-settings-button");
    saveButton.setTooltip(new Tooltip("Save launcher settings"));
    Button defaultsButton = new Button("Defaults");
    defaultsButton.setOnAction(e -> {
      EditorPreferences defaults = store.load();
      if (defaults == null) defaults = EditorPreferences.defaults();
      defaults.setLauncherTheme(EditorPreferences.LAUNCHER_THEME_DARK);
      defaults.setEditorTheme(EditorPreferences.LAUNCHER_THEME_DARK);
      defaults.setDefaultTextEditor(EditorPreferences.TEXT_EDITOR_JVN);
      defaults.setCustomTextEditorCommand("");
      defaults.setLauncherRestoreLastProject(true);
      defaults.setLauncherLastProjectPath("");
      defaults.setLauncherKeepOpenAfterEditorLaunch(false);
      defaults.setLauncherConfirmOpenEditor(false);
      defaults.setLauncherConfirmRunProject(false);
      defaults.setLauncherRuntimePerfHud(true);
      defaults.setGradleSkipTestsOnRun(true);
      loadIntoForm(defaults);
      statusLabel.setText("Launcher defaults restored in form");
    });
    defaultsButton.getStyleClass().add("editor-settings-button");
    defaultsButton.setTooltip(new Tooltip("Restore launcher defaults in the form"));
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
    editorThemeCombo.getItems().addAll(THEME_LABEL_DARK, THEME_LABEL_LIGHT);
    editorThemeCombo.setMaxWidth(Double.MAX_VALUE);
    editorThemeCombo.getStyleClass().add("editor-settings-combo");

    settingsFilterField.setPromptText("Filter settings...");
    settingsFilterField.getStyleClass().addAll("editor-settings-text-field", "editor-settings-search-field");
    settingsFilterField.setTooltip(new Tooltip("Filter launcher settings sections"));
    settingsFilterField.textProperty().addListener((obs, oldValue, newValue) -> applySettingsFilter());

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
    customTextEditorCommandField.setTooltip(new Tooltip("Command template for opening files in a custom text editor"));
    restoreLastProjectCheck.getStyleClass().add("editor-settings-check");
    keepLauncherOpenCheck.getStyleClass().add("editor-settings-check");
    confirmOpenEditorCheck.getStyleClass().add("editor-settings-check");
    confirmRunProjectCheck.getStyleClass().add("editor-settings-check");
    launcherRuntimePerfHudCheck.getStyleClass().add("editor-settings-check");
    gradleSkipTestsOnRunCheck.getStyleClass().add("editor-settings-check");
    lastProjectPathField.setEditable(false);
    lastProjectPathField.setPromptText("No project stored yet");
    lastProjectPathField.getStyleClass().add("editor-settings-text-field");
    lastProjectPathField.setTooltip(new Tooltip("Project path restored by the launcher at startup"));

    Button useCurrentProjectButton = new Button("Use Current");
    useCurrentProjectButton.getStyleClass().add("editor-settings-button");
    useCurrentProjectButton.setTooltip(new Tooltip("Store the current project as the launcher startup project"));
    useCurrentProjectButton.setOnAction(e -> useCurrentProject());
    Button clearProjectButton = new Button("Clear");
    clearProjectButton.getStyleClass().add("editor-settings-button");
    clearProjectButton.setTooltip(new Tooltip("Clear the stored startup project path"));
    clearProjectButton.setOnAction(e -> lastProjectPathField.clear());
    HBox lastProjectControls =
        new HBox(8, lastProjectPathField, useCurrentProjectButton, clearProjectButton);
    lastProjectControls.getStyleClass().add("editor-settings-inline-row");
    HBox.setHgrow(lastProjectPathField, Priority.ALWAYS);

    GridPane appearanceGrid = settingsGrid(190);
    appearanceGrid.addRow(0, fieldLabel("Launcher Theme"), themeCombo);
    appearanceGrid.addRow(1, fieldLabel("Editor Theme"), editorThemeCombo);
    VBox appearanceSection =
        registerSection(
            settingsSection("Appearance", "Launcher and spawned editor styling.", appearanceGrid),
            "appearance theme launcher editor dark light styling");

    GridPane startupGrid = settingsGrid(190);
    startupGrid.add(restoreLastProjectCheck, 1, 0);
    startupGrid.addRow(1, fieldLabel("Startup Project"), lastProjectControls);
    VBox startupSection =
        registerSection(
            settingsSection("Startup Project", "Project selection restored when the launcher opens.", startupGrid),
            "startup project restore last selected current path open launch");

    GridPane handoffGrid = settingsGrid(190);
    handoffGrid.addRow(0, fieldLabel("Default Text Editor"), defaultTextEditorCombo);
    handoffGrid.addRow(1, fieldLabel("Custom Command"), customTextEditorCommandField);
    handoffGrid.add(keepLauncherOpenCheck, 1, 2);
    handoffGrid.add(confirmOpenEditorCheck, 1, 3);
    VBox handoffSection =
        registerSection(
            settingsSection("Editor Handoff", "How project files and editor launches leave the launcher.", handoffGrid),
            "editor handoff default text editor custom command keep launcher open confirm project file system app");

    GridPane runGrid = settingsGrid(190);
    runGrid.add(confirmRunProjectCheck, 1, 0);
    runGrid.add(launcherRuntimePerfHudCheck, 1, 1);
    runGrid.add(gradleSkipTestsOnRunCheck, 1, 2);
    VBox runSection =
        registerSection(
            settingsSection("Run Behavior", "Defaults used when running projects directly from the launcher.", runGrid),
            "run behavior project runtime performance perf hud gradle skip tests confirm launcher");

    content.getChildren().addAll(
        header,
        intro,
        settingsFilterField,
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
    editorThemeCombo.setValue(themeLabel(model.getEditorTheme()));
    defaultTextEditorCombo.setValue(textEditorLabel(model.getDefaultTextEditor()));
    customTextEditorCommandField.setText(model.getCustomTextEditorCommand());
    restoreLastProjectCheck.setSelected(model.isLauncherRestoreLastProject());
    lastProjectPathField.setText(model.getLauncherLastProjectPath());
    keepLauncherOpenCheck.setSelected(model.isLauncherKeepOpenAfterEditorLaunch());
    confirmOpenEditorCheck.setSelected(model.isLauncherConfirmOpenEditor());
    confirmRunProjectCheck.setSelected(model.isLauncherConfirmRunProject());
    launcherRuntimePerfHudCheck.setSelected(model.isLauncherRuntimePerfHud());
    gradleSkipTestsOnRunCheck.setSelected(model.isGradleSkipTestsOnRun());
    updateCustomCommandState();
  }

  public void save() {
    EditorPreferences preferences = store.load();
    if (preferences == null) preferences = EditorPreferences.defaults();
    else preferences = preferences.copy();
    preferences.setLauncherTheme(themeValue(themeCombo.getValue()));
    preferences.setEditorTheme(themeValue(editorThemeCombo.getValue()));
    preferences.setDefaultTextEditor(textEditorValue(defaultTextEditorCombo.getValue()));
    preferences.setCustomTextEditorCommand(customTextEditorCommandField.getText());
    preferences.setLauncherRestoreLastProject(restoreLastProjectCheck.isSelected());
    preferences.setLauncherLastProjectPath(lastProjectPathField.getText());
    preferences.setLauncherKeepOpenAfterEditorLaunch(keepLauncherOpenCheck.isSelected());
    preferences.setLauncherConfirmOpenEditor(confirmOpenEditorCheck.isSelected());
    preferences.setLauncherConfirmRunProject(confirmRunProjectCheck.isSelected());
    preferences.setLauncherRuntimePerfHud(launcherRuntimePerfHudCheck.isSelected());
    preferences.setGradleSkipTestsOnRun(gradleSkipTestsOnRunCheck.isSelected());
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

  private VBox registerSection(VBox section, String searchText) {
    if (section == null) return null;
    section.getProperties().put(SETTINGS_SEARCH_TEXT_KEY, normalizeSearchText(searchText));
    filterableSections.add(section);
    return section;
  }

  private void applySettingsFilter() {
    String filter = normalizeSearchText(settingsFilterField.getText());
    for (VBox section : filterableSections) {
      Object raw = section.getProperties().get(SETTINGS_SEARCH_TEXT_KEY);
      String haystack = raw == null ? "" : raw.toString();
      boolean match = filter.isBlank() || matchesFilter(haystack, filter);
      section.setVisible(match);
      section.setManaged(match);
    }
  }

  private static boolean matchesFilter(String haystack, String filter) {
    if (filter == null || filter.isBlank()) return true;
    if (haystack == null || haystack.isBlank()) return false;
    String[] tokens = filter.split("\\s+");
    for (String token : tokens) {
      if (!token.isBlank() && !haystack.contains(token)) return false;
    }
    return true;
  }

  private static String normalizeSearchText(String text) {
    return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
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
