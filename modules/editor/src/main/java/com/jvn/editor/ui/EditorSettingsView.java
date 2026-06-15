package com.jvn.editor.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class EditorSettingsView extends BorderPane {
  private static final String TEXT_EDITOR_LABEL_JVN = "JVN Editor";
  private static final String TEXT_EDITOR_LABEL_SYSTEM = "System Default App";
  private static final String TEXT_EDITOR_LABEL_CUSTOM = "Custom Command";
  private static final String THEME_LABEL_DARK = "Dark";
  private static final String THEME_LABEL_LIGHT = "Light";
  private static final String SETTINGS_SEARCH_TEXT_KEY = "settingsSearchText";
  private static final Set<EditorStatusBarSegment> COMPACT_STATUS_BAR_SEGMENTS =
      EnumSet.of(
          EditorStatusBarSegment.PRODUCT,
          EditorStatusBarSegment.MESSAGE,
          EditorStatusBarSegment.ACTIVE_FILE,
          EditorStatusBarSegment.DIRTY,
          EditorStatusBarSegment.DIAGNOSTICS);

  private final EditorPreferencesStore store;
  private final ComboBox<String> editorThemeCombo = new ComboBox<>();
  private final Spinner<Integer> codeEditorFontSizeSpinner = new Spinner<>();
  private final Spinner<Integer> editorMaxFpsSpinner = new Spinner<>();
  private final ComboBox<String> defaultTextEditorCombo = new ComboBox<>();
  private final TextField customTextEditorCommandField = new TextField();
  private final TextField settingsFilterField = new TextField();
  private final CheckBox showWelcomeOnStartupCheck =
      new CheckBox("Show Workspace Hub tab on startup");
  private final CheckBox loadSidebarExtensionsOnDemandCheck =
      new CheckBox("Load sidebar extensions only when opened (lower memory usage)");
  private final CheckBox autoSaveBeforeRunCheck =
      new CheckBox("Save dirty files before project runs");
  private final CheckBox editorRuntimePerfHudCheck =
      new CheckBox("Show runtime performance HUD when launching projects");
  private final CheckBox editorConfirmRunProjectCheck =
      new CheckBox("Confirm before running a project from the editor");
  private final CheckBox gradleSkipTestsOnRunCheck =
      new CheckBox("Skip Gradle tests for default project runs");
  private final Map<EditorSidebarPanel, ComboBox<EditorPanelPlacement>> panelPlacements =
      new EnumMap<>(EditorSidebarPanel.class);
  private final Map<EditorSidebarPanel, CheckBox> chooserVisibilityChecks =
      new EnumMap<>(EditorSidebarPanel.class);
  private final Map<EditorStatusBarSegment, CheckBox> statusBarSegmentChecks =
      new EnumMap<>(EditorStatusBarSegment.class);
  private final Label statusLabel = new Label("Editor settings loaded");
  private final List<VBox> filterableSections = new ArrayList<>();
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
    reloadButton.setTooltip(new Tooltip("Reload settings from disk"));
    Button saveButton = new Button("Save");
    saveButton.setOnAction(e -> save());
    saveButton.getStyleClass().add("editor-settings-button");
    saveButton.setTooltip(new Tooltip("Save editor settings"));
    Button defaultsButton = new Button("Defaults");
    defaultsButton.setOnAction(e -> {
      loadIntoForm(EditorPreferences.defaults());
      statusLabel.setText("Defaults restored in form");
    });
    defaultsButton.getStyleClass().add("editor-settings-button");
    defaultsButton.setTooltip(new Tooltip("Restore default editor settings in the form"));
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
    settingsFilterField.setPromptText("Filter settings...");
    settingsFilterField.getStyleClass().addAll("editor-settings-text-field", "editor-settings-search-field");
    settingsFilterField.setTooltip(new Tooltip("Filter editor settings sections"));
    settingsFilterField.textProperty().addListener((obs, oldValue, newValue) -> applySettingsFilter());
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
    editorConfirmRunProjectCheck.getStyleClass().add("editor-settings-check");
    gradleSkipTestsOnRunCheck.getStyleClass().add("editor-settings-check");

    GridPane appearanceGrid = settingsGrid(180);
    appearanceGrid.addRow(0, fieldLabel("Editor Theme"), editorThemeCombo);
    appearanceGrid.addRow(1, fieldLabel("Code Text Size"), codeEditorFontSizeSpinner);
    VBox appearanceSection =
        registerSection(
            settingsSection("Appearance", "Theme and code editor scale.", appearanceGrid),
            "appearance theme code text size font editor dark light scale");

    GridPane runtimeGrid = settingsGrid(180);
    runtimeGrid.addRow(0, fieldLabel("Max FPS"), editorMaxFpsSpinner);
    runtimeGrid.add(editorRuntimePerfHudCheck, 1, 1);
    runtimeGrid.add(autoSaveBeforeRunCheck, 1, 2);
    runtimeGrid.add(editorConfirmRunProjectCheck, 1, 3);
    runtimeGrid.add(gradleSkipTestsOnRunCheck, 1, 4);
    VBox runtimeSection =
        registerSection(
            settingsSection("Runtime", "Project launch and preview performance defaults.", runtimeGrid),
            "runtime run project launch preview performance max fps perf hud save dirty confirm gradle skip tests");

    GridPane startupGrid = settingsGrid(180);
    startupGrid.add(showWelcomeOnStartupCheck, 1, 0);
    startupGrid.add(loadSidebarExtensionsOnDemandCheck, 1, 1);
    VBox startupSection =
        registerSection(
            settingsSection("Startup", "Tabs and side tools loaded when the editor opens.", startupGrid),
            "startup workspace hub tab sidebar extensions on demand memory tools");

    VBox statusBarSection = new VBox(10);
    statusBarSection.getStyleClass().add("editor-settings-section");
    statusBarSection.getChildren().add(sectionHeader("Status Bar"));
    Label statusBarDesc = new Label(
        "Choose which bottom-bar segments are shown. Segments that depend on runtime data still stay hidden "
            + "until they have something useful to report.");
    statusBarDesc.setWrapText(true);
    statusBarDesc.getStyleClass().add("editor-settings-copy");
    HBox statusBarActions = new HBox(8);
    Button showAllStatusButton = new Button("Show all");
    showAllStatusButton.getStyleClass().add("editor-settings-button");
    showAllStatusButton.setOnAction(e -> setAllStatusBarSegments(true));
    Button compactStatusButton = new Button("Compact");
    compactStatusButton.getStyleClass().add("editor-settings-button");
    compactStatusButton.setTooltip(new Tooltip("Keep only the highest-signal bottom-bar segments enabled."));
    compactStatusButton.setOnAction(e -> applyCompactStatusBarPreset());
    statusBarActions.getChildren().addAll(showAllStatusButton, compactStatusButton);
    GridPane statusBarGrid = new GridPane();
    statusBarGrid.setHgap(16);
    statusBarGrid.setVgap(8);
    ColumnConstraints statusLeftColumn = new ColumnConstraints();
    statusLeftColumn.setPercentWidth(50);
    statusLeftColumn.setHgrow(Priority.ALWAYS);
    ColumnConstraints statusRightColumn = new ColumnConstraints();
    statusRightColumn.setPercentWidth(50);
    statusRightColumn.setHgrow(Priority.ALWAYS);
    statusBarGrid.getColumnConstraints().addAll(statusLeftColumn, statusRightColumn);
    int statusIndex = 0;
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      CheckBox check = new CheckBox(segment.displayName());
      check.getStyleClass().add("editor-settings-check");
      check.setTooltip(new Tooltip(segment.description()));
      statusBarSegmentChecks.put(segment, check);
      statusBarGrid.add(check, statusIndex % 2, statusIndex / 2);
      statusIndex++;
    }
    statusBarSection.getChildren().addAll(statusBarDesc, statusBarActions, statusBarGrid);
    registerSection(
        statusBarSection,
        "status bar bottom footer segments chips product branch git state message project active file cursor line metadata tabs saved diagnostics encoding line ending heap memory java runtime theme version compact");

    GridPane fileOpeningGrid = settingsGrid(180);
    fileOpeningGrid.addRow(0, fieldLabel("Default Text Editor"), defaultTextEditorCombo);
    fileOpeningGrid.addRow(1, fieldLabel("Custom Command"), customTextEditorCommandField);
    VBox fileOpeningSection =
        registerSection(
            settingsSection("File Opening", "Default editor used when project files are opened externally.", fileOpeningGrid),
            "file opening default text editor custom command external system app");

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
      if (panel.supportsDocking()) {
        combo.getItems().addAll(EditorPanelPlacement.values());
      } else {
        combo.getItems().add(EditorPanelPlacement.HIDDEN);
        combo.setDisable(true);
        combo.setTooltip(new Tooltip(panel.displayName() + " opens only as a pop-out tool."));
      }
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
    registerSection(
        sidebarSection,
        "default sidebar panels project inspector timeline help assets placement left right hidden chooser tools");

    content.getChildren().addAll(
        header,
        intro,
        settingsFilterField,
        new Separator(),
        appearanceSection,
        runtimeSection,
        startupSection,
        statusBarSection,
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
    editorConfirmRunProjectCheck.setSelected(model.isEditorConfirmRunProject());
    gradleSkipTestsOnRunCheck.setSelected(model.isGradleSkipTestsOnRun());
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      CheckBox check = statusBarSegmentChecks.get(segment);
      if (check != null) check.setSelected(model.isStatusBarSegmentVisible(segment));
    }
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
    preferences.setEditorConfirmRunProject(editorConfirmRunProjectCheck.isSelected());
    preferences.setGradleSkipTestsOnRun(gradleSkipTestsOnRunCheck.isSelected());
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      CheckBox check = statusBarSegmentChecks.get(segment);
      preferences.setStatusBarSegmentVisible(
          segment,
          check == null ? segment.defaultVisible() : check.isSelected());
    }
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

  private void setAllStatusBarSegments(boolean visible) {
    for (CheckBox check : statusBarSegmentChecks.values()) {
      if (check != null) check.setSelected(visible);
    }
  }

  private void applyCompactStatusBarPreset() {
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      CheckBox check = statusBarSegmentChecks.get(segment);
      if (check != null) check.setSelected(COMPACT_STATUS_BAR_SEGMENTS.contains(segment));
    }
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
