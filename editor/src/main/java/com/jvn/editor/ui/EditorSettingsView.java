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
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class EditorSettingsView extends BorderPane {
  private final EditorPreferencesStore store;
  private final Spinner<Integer> codeEditorFontSizeSpinner = new Spinner<>();
  private final CheckBox showWelcomeOnStartupCheck =
      new CheckBox("Show Welcome tab on startup");
  private final Map<EditorSidebarPanel, ComboBox<EditorPanelPlacement>> panelPlacements =
      new EnumMap<>(EditorSidebarPanel.class);
  private final Map<EditorSidebarPanel, CheckBox> chooserVisibilityChecks =
      new EnumMap<>(EditorSidebarPanel.class);
  private final Label statusLabel = new Label("Editor settings loaded");
  private Consumer<EditorPreferences> onPreferencesApplied;

  public EditorSettingsView(EditorPreferencesStore store) {
    this.store = store == null ? new EditorPreferencesStore() : store;
    setPadding(new Insets(10));
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
    content.setPadding(new Insets(4));
    content.getStyleClass().add("editor-settings-content");

    Label header = new Label("Editor Settings");
    header.getStyleClass().add("editor-settings-header");
    Label intro = new Label(
        "Configure editor-wide defaults for sidebar panels and code editor scale. "
            + "These settings apply across launches.");
    intro.setWrapText(true);
    intro.getStyleClass().add("editor-settings-copy");

    VBox generalSection = new VBox(10);
    generalSection.getChildren().add(sectionHeader("General"));
    generalSection.getStyleClass().add("editor-settings-section");
    GridPane generalGrid = new GridPane();
    generalGrid.setHgap(10);
    generalGrid.setVgap(10);
    ColumnConstraints labelColumn = new ColumnConstraints();
    labelColumn.setMinWidth(180);
    ColumnConstraints fieldColumn = new ColumnConstraints();
    fieldColumn.setHgrow(Priority.ALWAYS);
    generalGrid.getColumnConstraints().addAll(labelColumn, fieldColumn);

    codeEditorFontSizeSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            EditorPreferences.MIN_CODE_EDITOR_FONT_SIZE,
            EditorPreferences.MAX_CODE_EDITOR_FONT_SIZE,
            EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE));
    codeEditorFontSizeSpinner.setEditable(true);
    codeEditorFontSizeSpinner.getStyleClass().add("editor-settings-spinner");
    showWelcomeOnStartupCheck.getStyleClass().add("editor-settings-check");
    generalGrid.addRow(0, fieldLabel("Code Editor Text Size"), codeEditorFontSizeSpinner);
    generalGrid.add(showWelcomeOnStartupCheck, 1, 1);
    generalSection.getChildren().add(generalGrid);

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
        generalSection,
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
    codeEditorFontSizeSpinner.getValueFactory().setValue(model.getCodeEditorFontSize());
    showWelcomeOnStartupCheck.setSelected(model.isShowWelcomeOnStartup());
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
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
    EditorPreferences preferences = EditorPreferences.defaults();
    Integer fontSize = codeEditorFontSizeSpinner.getValue();
    preferences.setCodeEditorFontSize(
        fontSize == null
            ? EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE
            : fontSize.intValue());
    preferences.setShowWelcomeOnStartup(showWelcomeOnStartupCheck.isSelected());
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
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
}
