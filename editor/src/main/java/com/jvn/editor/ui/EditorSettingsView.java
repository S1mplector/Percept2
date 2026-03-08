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
  private final Label statusLabel = new Label("Editor settings loaded");
  private Consumer<EditorPreferences> onPreferencesApplied;

  public EditorSettingsView(EditorPreferencesStore store) {
    this.store = store == null ? new EditorPreferencesStore() : store;
    setPadding(new Insets(10));

    ToolBar toolbar = new ToolBar();
    Button reloadButton = new Button("Reload");
    reloadButton.setOnAction(e -> reload());
    Button saveButton = new Button("Save");
    saveButton.setOnAction(e -> save());
    Button defaultsButton = new Button("Defaults");
    defaultsButton.setOnAction(e -> {
      loadIntoForm(EditorPreferences.defaults());
      statusLabel.setText("Defaults restored in form");
    });
    toolbar.getItems().addAll(reloadButton, saveButton, defaultsButton);
    setTop(toolbar);

    VBox content = new VBox(14);
    content.setPadding(new Insets(4));

    Label header = new Label("Editor Settings");
    header.setStyle("-fx-font-size: 15px; -fx-font-weight: 800;");
    Label intro = new Label(
        "Configure editor-wide defaults for sidebar panels and code editor scale. "
            + "These settings apply across launches.");
    intro.setWrapText(true);

    VBox generalSection = new VBox(10);
    generalSection.getChildren().add(sectionHeader("General"));
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
    generalGrid.addRow(0, fieldLabel("Code Editor Text Size"), codeEditorFontSizeSpinner);
    generalGrid.add(showWelcomeOnStartupCheck, 1, 1);
    generalSection.getChildren().add(generalGrid);

    VBox sidebarSection = new VBox(10);
    sidebarSection.getChildren().add(sectionHeader("Default Sidebar Panels"));
    Label sidebarDesc = new Label(
        "Choose whether each sidebar tool should open on the left, on the right, or stay hidden by default.");
    sidebarDesc.setWrapText(true);
    GridPane sidebarGrid = new GridPane();
    sidebarGrid.setHgap(10);
    sidebarGrid.setVgap(8);
    ColumnConstraints panelColumn = new ColumnConstraints();
    panelColumn.setMinWidth(220);
    panelColumn.setHgrow(Priority.ALWAYS);
    ColumnConstraints placementColumn = new ColumnConstraints();
    placementColumn.setMinWidth(180);
    sidebarGrid.getColumnConstraints().addAll(panelColumn, placementColumn);
    int row = 0;
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      Label label = fieldLabel(panel.displayName());
      ComboBox<EditorPanelPlacement> combo = new ComboBox<>();
      combo.getItems().addAll(EditorPanelPlacement.values());
      combo.setMaxWidth(Double.MAX_VALUE);
      panelPlacements.put(panel, combo);
      sidebarGrid.add(label, 0, row);
      sidebarGrid.add(combo, 1, row);
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
    setCenter(scrollPane);

    statusLabel.setStyle("-fx-text-fill: #9ea8b7; -fx-font-size: 11px;");
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
    }
    return preferences;
  }

  private static Label sectionHeader(String text) {
    Label label = new Label(text);
    label.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;");
    return label;
  }

  private static Label fieldLabel(String text) {
    Label label = new Label(text);
    label.setStyle("-fx-font-weight: 600;");
    return label;
  }
}
