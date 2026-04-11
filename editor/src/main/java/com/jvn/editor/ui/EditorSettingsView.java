package com.jvn.editor.ui;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class EditorSettingsView extends BorderPane {
  private static final String TEXT_EDITOR_LABEL_JVN = "JVN Editor";
  private static final String TEXT_EDITOR_LABEL_SYSTEM = "System Default App";
  private static final String TEXT_EDITOR_LABEL_CUSTOM = "Custom Command";
  private static final String THEME_LABEL_DARK = "Dark";
  private static final String THEME_LABEL_LIGHT = "Light";

  private final EditorPreferencesStore store;
  private final ComboBox<String> editorThemeCombo = new ComboBox<>();
  private final Spinner<Integer> codeEditorFontSizeSpinner = new Spinner<>();
  private final Spinner<Integer> editorMaxFpsSpinner = new Spinner<>();
  private final ComboBox<String> defaultTextEditorCombo = new ComboBox<>();
  private final TextField customTextEditorCommandField = new TextField();
  private final CheckBox showWelcomeOnStartupCheck =
      new CheckBox("Show Workspace Hub tab on startup");
  private final CheckBox loadSidebarExtensionsOnDemandCheck =
      new CheckBox("Load sidebar extensions only when opened (lower memory usage)");
  private final CheckBox autoSaveBeforeRunCheck =
      new CheckBox("Save dirty files before project runs");
  private final CheckBox editorRuntimePerfHudCheck =
      new CheckBox("Show runtime performance HUD when launching projects");
  private final Map<EditorSidebarPanel, ComboBox<EditorPanelPlacement>> panelPlacements =
      new EnumMap<>(EditorSidebarPanel.class);
  private final Map<EditorSidebarPanel, CheckBox> chooserVisibilityChecks =
      new EnumMap<>(EditorSidebarPanel.class);
  private final Label statusLabel = new Label("Editor settings loaded");
  private Consumer<EditorPreferences> onPreferencesApplied;

  public EditorSettingsView(EditorPreferencesStore store) {
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
      loadIntoForm(EditorPreferences.defaults());
      statusLabel.setText("Defaults restored in form");
    });
    defaultsButton.getStyleClass().add("editor-settings-button");
    toolbar.getItems().addAll(reloadButton, saveButton, defaultsButton);
    setTop(toolbar);

    VBox content = new VBox(14);
    content.setPadding(new Insets(12));
    content.getStyleClass().add("editor-settings-content");

    Label header = new Label("Editor Settings");
    header.getStyleClass().add("editor-settings-header");
    Label intro = new Label(
        "Configure editor-wide appearance, launch behavior, and sidebar defaults. "
            + "These settings apply across launches.");
    intro.setWrapText(true);
    intro.getStyleClass().add("editor-settings-copy");

    editorThemeCombo.getItems().addAll(THEME_LABEL_DARK, THEME_LABEL_LIGHT);
    editorThemeCombo.setMaxWidth(Double.MAX_VALUE);
    editorThemeCombo.getStyleClass().add("editor-settings-combo");
    codeEditorFontSizeSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            EditorPreferences.MIN_CODE_EDITOR_FONT_SIZE,
            EditorPreferences.MAX_CODE_EDITOR_FONT_SIZE,
            EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE));
    codeEditorFontSizeSpinner.setEditable(true);
    codeEditorFontSizeSpinner.getStyleClass().add("editor-settings-spinner");
    editorMaxFpsSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            EditorPreferences.MIN_EDITOR_MAX_FPS,
            EditorPreferences.MAX_EDITOR_MAX_FPS,
            EditorPreferences.DEFAULT_EDITOR_MAX_FPS));
    editorMaxFpsSpinner.setEditable(true);
    editorMaxFpsSpinner.getStyleClass().add("editor-settings-spinner");
    editorMaxFpsSpinner.setPromptText("0 = display rate");
    defaultTextEditorCombo.getItems().addAll(
        TEXT_EDITOR_LABEL_JVN,
        TEXT_EDITOR_LABEL_SYSTEM,
        TEXT_EDITOR_LABEL_CUSTOM);
    defaultTextEditorCombo.setMaxWidth(Double.MAX_VALUE);
    defaultTextEditorCombo.getStyleClass().add("editor-settings-combo");
    defaultTextEditorCombo.valueProperty().addListener((obs, oldValue, newValue) ->
        updateCustomTextEditorCommandState());
    customTextEditorCommandField.setPromptText("Example: code --reuse-window {file}");
    customTextEditorCommandField.getStyleClass().add("editor-settings-text-field");
    showWelcomeOnStartupCheck.getStyleClass().add("editor-settings-check");
    loadSidebarExtensionsOnDemandCheck.getStyleClass().add("editor-settings-check");
    autoSaveBeforeRunCheck.getStyleClass().add("editor-settings-check");
    editorRuntimePerfHudCheck.getStyleClass().add("editor-settings-check");

    GridPane appearanceGrid = settingsGrid(180);
    appearanceGrid.addRow(0, fieldLabel("Editor Theme"), editorThemeCombo);
    appearanceGrid.addRow(1, fieldLabel("Code Text Size"), codeEditorFontSizeSpinner);
    VBox appearanceSection =
        settingsSection("Appearance", "Theme and code editor scale.", appearanceGrid);

    GridPane runtimeGrid = settingsGrid(180);
    runtimeGrid.addRow(0, fieldLabel("Max FPS"), editorMaxFpsSpinner);
    runtimeGrid.add(editorRuntimePerfHudCheck, 1, 1);
    runtimeGrid.add(autoSaveBeforeRunCheck, 1, 2);
    VBox runtimeSection =
        settingsSection("Runtime", "Project launch and preview performance defaults.", runtimeGrid);

    GridPane startupGrid = settingsGrid(180);
    startupGrid.add(showWelcomeOnStartupCheck, 1, 0);
    startupGrid.add(loadSidebarExtensionsOnDemandCheck, 1, 1);
    VBox startupSection =
        settingsSection("Startup", "Tabs and side tools loaded when the editor opens.", startupGrid);

    GridPane fileOpeningGrid = settingsGrid(180);
    fileOpeningGrid.addRow(0, fieldLabel("Default Text Editor"), defaultTextEditorCombo);
    fileOpeningGrid.addRow(1, fieldLabel("Custom Command"), customTextEditorCommandField);
    VBox fileOpeningSection =
        settingsSection("File Opening", "Default editor used when project files are opened externally.", fileOpeningGrid);

    VBox sidebarSection = new VBox(10);
    sidebarSection.getChildren().add(sectionHeader("Default Sidebar Panels"));
    sidebarSection.getStyleClass().add("editor-settings-section");
    Label sidebarDesc = new Label(
        "Choose whether each sidebar tool should open on the left, on the right, or stay hidden by default. "
            + "You can also remove tools from the New Panel chooser entirely.");
    sidebarDesc.setWrapText(true);
    sidebarDesc.getStyleClass().add("editor-settings-copy");
    GridPane sidebarGrid = new GridPane();
    sidebarGrid.setHgap(10);
    sidebarGrid.setVgap(8);
    ColumnConstraints panelColumn = new ColumnConstraints();
    panelColumn.setMinWidth(220);
    panelColumn.setHgrow(Priority.ALWAYS);
    ColumnConstraints placementColumn = new ColumnConstraints();
    placementColumn.setMinWidth(180);
    ColumnConstraints chooserColumn = new ColumnConstraints();
    chooserColumn.setMinWidth(150);
    sidebarGrid.getColumnConstraints().addAll(panelColumn, placementColumn, chooserColumn);
    int row = 0;
    sidebarGrid.add(fieldLabel("Panel"), 0, row);
    sidebarGrid.add(fieldLabel("Default Placement"), 1, row);
    sidebarGrid.add(fieldLabel("Show In Chooser"), 2, row);
    row++;
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      if (!panel.editableInSettings()) continue;
      Label label = fieldLabel(panel.displayName());
      ComboBox<EditorPanelPlacement> combo = new ComboBox<>();
      combo.getItems().addAll(EditorPanelPlacement.values());
      combo.setMaxWidth(Double.MAX_VALUE);
      combo.getStyleClass().add("editor-settings-combo");
      CheckBox chooserVisible = new CheckBox();
      chooserVisible.getStyleClass().add("editor-settings-check");
      panelPlacements.put(panel, combo);
      chooserVisibilityChecks.put(panel, chooserVisible);
      sidebarGrid.add(label, 0, row);
      sidebarGrid.add(combo, 1, row);
      sidebarGrid.add(chooserVisible, 2, row);
      row++;
    }
    sidebarSection.getChildren().addAll(sidebarDesc, sidebarGrid);

    content.getChildren().addAll(
        header,
        intro,
        new Separator(),
        appearanceSection,
        runtimeSection,
        startupSection,
        fileOpeningSection,
        new Separator(),
        sidebarSection);

    ScrollPane scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.getStyleClass().add("editor-settings-scroll");
    setCenter(scrollPane);

    statusLabel.getStyleClass().add("editor-settings-status");
    setBottom(statusLabel);

    reload();
  }

  public void setOnPreferencesApplied(Consumer<EditorPreferences> onPreferencesApplied) {
    this.onPreferencesApplied = onPreferencesApplied;
  }

  public EditorPreferences getCurrentPreferences() {
    return buildPreferencesFromForm();
  }

  public void reload() {
    loadIntoForm(store.load());
    statusLabel.setText("Editor settings loaded");
  }

  public void loadIntoForm(EditorPreferences preferences) {
    EditorPreferences model = preferences == null ? EditorPreferences.defaults() : preferences.copy();
    editorThemeCombo.setValue(themeLabel(model.getEditorTheme()));
    codeEditorFontSizeSpinner.getValueFactory().setValue(model.getCodeEditorFontSize());
    editorMaxFpsSpinner.getValueFactory().setValue(model.getEditorMaxFps());
    defaultTextEditorCombo.setValue(textEditorLabel(model.getDefaultTextEditor()));
    customTextEditorCommandField.setText(model.getCustomTextEditorCommand());
    updateCustomTextEditorCommandState();
    showWelcomeOnStartupCheck.setSelected(model.isShowWelcomeOnStartup());
    loadSidebarExtensionsOnDemandCheck.setSelected(model.isLoadSidebarExtensionsOnDemand());
    autoSaveBeforeRunCheck.setSelected(model.isAutoSaveBeforeRun());
    editorRuntimePerfHudCheck.setSelected(model.isEditorRuntimePerfHud());
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      if (!panel.editableInSettings()) continue;
      ComboBox<EditorPanelPlacement> combo = panelPlacements.get(panel);
      if (combo != null) combo.setValue(model.getPlacement(panel));
      CheckBox chooserVisible = chooserVisibilityChecks.get(panel);
      if (chooserVisible != null) chooserVisible.setSelected(model.isVisibleInChooser(panel));
    }
  }

  public void save() {
    EditorPreferences preferences = buildPreferencesFromForm();
    try {
      store.save(preferences);
      statusLabel.setText("Editor settings saved");
      if (onPreferencesApplied != null) onPreferencesApplied.accept(preferences.copy());
    } catch (IOException ex) {
      statusLabel.setText("Failed to save editor settings: " + ex.getMessage());
    }
  }

  private EditorPreferences buildPreferencesFromForm() {
    EditorPreferences preferences = store.load();
    if (preferences == null) preferences = EditorPreferences.defaults();
    else preferences = preferences.copy();
    Integer fontSize = codeEditorFontSizeSpinner.getValue();
    preferences.setCodeEditorFontSize(
        fontSize == null
            ? EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE
            : fontSize.intValue());
    preferences.setEditorTheme(themeValue(editorThemeCombo.getValue()));
    Integer maxFps = editorMaxFpsSpinner.getValue();
    preferences.setEditorMaxFps(
        maxFps == null
            ? EditorPreferences.DEFAULT_EDITOR_MAX_FPS
            : maxFps.intValue());
    preferences.setDefaultTextEditor(textEditorValue(defaultTextEditorCombo.getValue()));
    preferences.setCustomTextEditorCommand(customTextEditorCommandField.getText());
    preferences.setShowWelcomeOnStartup(showWelcomeOnStartupCheck.isSelected());
    preferences.setLoadSidebarExtensionsOnDemand(loadSidebarExtensionsOnDemandCheck.isSelected());
    preferences.setAutoSaveBeforeRun(autoSaveBeforeRunCheck.isSelected());
    preferences.setEditorRuntimePerfHud(editorRuntimePerfHudCheck.isSelected());
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      if (!panel.editableInSettings()) continue;
      ComboBox<EditorPanelPlacement> combo = panelPlacements.get(panel);
      preferences.setPlacement(
          panel,
          combo == null || combo.getValue() == null ? panel.defaultPlacement() : combo.getValue());
      CheckBox chooserVisible = chooserVisibilityChecks.get(panel);
      preferences.setVisibleInChooser(
          panel,
          chooserVisible == null ? panel.defaultVisibleInChooser() : chooserVisible.isSelected());
    }
    return preferences;
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

  private void updateCustomTextEditorCommandState() {
    boolean custom = TEXT_EDITOR_LABEL_CUSTOM.equals(defaultTextEditorCombo.getValue());
    customTextEditorCommandField.setDisable(!custom);
  }

  private static String themeLabel(String value) {
    return EditorPreferences.LAUNCHER_THEME_LIGHT.equals(EditorPreferences.normalizeEditorTheme(value))
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
