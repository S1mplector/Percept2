package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Editor sidebar panel for synthesizer ambience controls and native audio diagnostics.
 * Allows previewing ambience presets with detail/motion/spread/accent parameters,
 * and shows a concise diagnostics summary of the active audio backend.
 */
public class AudioSynthControlsView extends BorderPane {

  private static final String STYLE_SECTION_TITLE =
      "-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: #c8d0e0;";
  private static final String STYLE_PARAM_LABEL =
      "-fx-font-size: 11px; -fx-text-fill: #a0a8b8;";
  private static final String STYLE_VALUE_LABEL =
      "-fx-font-size: 11px; -fx-text-fill: #78b0ff; -fx-min-width: 32;";
  private static final String STYLE_DIAG_KEY =
      "-fx-font-size: 11px; -fx-text-fill: #8090a8;";
  private static final String STYLE_DIAG_VALUE =
      "-fx-font-size: 11px; -fx-text-fill: #c0c8d4;";
  private static final String STYLE_STATUS_OK =
      "-fx-font-size: 11px; -fx-text-fill: #5ee072; -fx-font-weight: 700;";
  private static final String STYLE_STATUS_FAIL =
      "-fx-font-size: 11px; -fx-text-fill: #f06060; -fx-font-weight: 700;";

  private final ComboBox<String> presetCombo = new ComboBox<>();
  private final Slider intensitySlider = createSlider(0.65);
  private final Slider volumeSlider = createSlider(0.45);
  private final Slider detailSlider = createSlider(0.5);
  private final Slider motionSlider = createSlider(0.5);
  private final Slider spreadSlider = createSlider(0.5);
  private final Slider accentSlider = createSlider(0.5);

  private final Label intensityValue = new Label("0.65");
  private final Label volumeValue = new Label("0.45");
  private final Label detailValue = new Label("0.50");
  private final Label motionValue = new Label("0.50");
  private final Label spreadValue = new Label("0.50");
  private final Label accentValue = new Label("0.50");

  private final Label diagBridgeStatus = new Label("—");
  private final Label diagAmbienceProvider = new Label("—");
  private final Label diagChiptuneProvider = new Label("—");
  private final Label diagBridgeInfo = new Label("—");
  private final Label snippetPreview = new Label("");

  public AudioSynthControlsView() {
    buildUi();
    wireListeners();
    updateSnippetPreview();
  }

  /** Refresh diagnostics from the audio-fx controller. Call after project load or backend change. */
  public void refreshDiagnostics(boolean bridgeAvailable, String ambienceId,
      String chiptuneId, String bridgeDiagnostic) {
    if (bridgeAvailable) {
      diagBridgeStatus.setText("Loaded");
      diagBridgeStatus.setStyle(STYLE_STATUS_OK);
    } else {
      diagBridgeStatus.setText("Unavailable");
      diagBridgeStatus.setStyle(STYLE_STATUS_FAIL);
    }
    diagAmbienceProvider.setText(ambienceId != null ? ambienceId : "—");
    diagChiptuneProvider.setText(chiptuneId != null ? chiptuneId : "—");
    diagBridgeInfo.setText(bridgeDiagnostic != null ? bridgeDiagnostic : "—");
  }

  private void buildUi() {
    VBox root = new VBox(10);
    root.setPadding(new Insets(10));

    // --- Title ---
    Label title = new Label("Synthesizer Ambience");
    title.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: #d0d8e8;");

    // --- Preset selector ---
    Label presetLabel = sectionTitle("Preset");
    presetCombo.getItems().addAll("wind", "rain", "ocean", "thunder", "fireplace", "night_insects");
    presetCombo.setValue("wind");
    presetCombo.setMaxWidth(Double.MAX_VALUE);
    presetCombo.setTooltip(new Tooltip("Ambience preset name passed to [synthesizer on mode:\"...\"]"));

    // --- Parameters grid ---
    Label paramsTitle = sectionTitle("Parameters");
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(6);
    addParamRow(grid, 0, "Intensity", intensitySlider, intensityValue,
        "Overall energy level of the ambience (maps to intensity: in VNS)");
    addParamRow(grid, 1, "Volume", volumeSlider, volumeValue,
        "Output volume (maps to volume: in VNS)");
    addParamRow(grid, 2, "Detail", detailSlider, detailValue,
        "High-frequency texture richness (maps to detail: in VNS)");
    addParamRow(grid, 3, "Motion", motionSlider, motionValue,
        "Temporal variation and movement speed (maps to motion: in VNS)");
    addParamRow(grid, 4, "Spread", spreadSlider, spreadValue,
        "Stereo width and spatial panning (maps to spread: in VNS)");
    addParamRow(grid, 5, "Accent", accentSlider, accentValue,
        "Preset-specific character emphasis (maps to accent: in VNS)");

    // --- Snippet preview ---
    Label snippetTitle = sectionTitle("VNS Snippet");
    snippetPreview.setWrapText(true);
    snippetPreview.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 10px; -fx-text-fill: #9cc7ff; "
        + "-fx-background-color: #181c28; -fx-padding: 6; -fx-background-radius: 3;");
    snippetPreview.setMaxWidth(Double.MAX_VALUE);

    Button copySnippet = new Button("Copy to Clipboard");
    copySnippet.setMaxWidth(Double.MAX_VALUE);
    copySnippet.setOnAction(e -> {
      ClipboardContent content = new ClipboardContent();
      content.putString(buildVnsSnippet());
      Clipboard.getSystemClipboard().setContent(content);
    });
    copySnippet.setTooltip(new Tooltip("Copy the VNS [synthesizer ...] command to clipboard"));

    // --- Diagnostics section ---
    Label diagTitle = sectionTitle("Audio Backend");
    GridPane diagGrid = new GridPane();
    diagGrid.setHgap(8);
    diagGrid.setVgap(4);
    addDiagRow(diagGrid, 0, "JNI Bridge", diagBridgeStatus);
    addDiagRow(diagGrid, 1, "Ambience", diagAmbienceProvider);
    addDiagRow(diagGrid, 2, "Chiptune", diagChiptuneProvider);
    addDiagRow(diagGrid, 3, "Bridge Info", diagBridgeInfo);
    diagBridgeStatus.setStyle(STYLE_DIAG_VALUE);

    root.getChildren().addAll(
        title,
        new Separator(),
        presetLabel, presetCombo,
        paramsTitle, grid,
        new Separator(),
        snippetTitle, snippetPreview, copySnippet,
        new Separator(),
        diagTitle, diagGrid
    );

    setCenter(root);
  }

  private void wireListeners() {
    presetCombo.valueProperty().addListener((obs, o, n) -> updateSnippetPreview());
    wireSlider(intensitySlider, intensityValue);
    wireSlider(volumeSlider, volumeValue);
    wireSlider(detailSlider, detailValue);
    wireSlider(motionSlider, motionValue);
    wireSlider(spreadSlider, spreadValue);
    wireSlider(accentSlider, accentValue);
  }

  private void wireSlider(Slider slider, Label valueLabel) {
    slider.valueProperty().addListener((obs, o, n) -> {
      valueLabel.setText(String.format("%.2f", n.doubleValue()));
      updateSnippetPreview();
    });
  }

  private void updateSnippetPreview() {
    snippetPreview.setText(buildVnsSnippet());
  }

  private String buildVnsSnippet() {
    String preset = presetCombo.getValue();
    if (preset == null) preset = "wind";
    StringBuilder sb = new StringBuilder();
    sb.append("[synthesizer on mode:\"").append(preset).append("\"");
    appendIfNotDefault(sb, "intensity", intensitySlider.getValue(), 0.65);
    appendIfNotDefault(sb, "volume", volumeSlider.getValue(), 0.45);
    appendIfNotDefault(sb, "detail", detailSlider.getValue(), 0.5);
    appendIfNotDefault(sb, "motion", motionSlider.getValue(), 0.5);
    appendIfNotDefault(sb, "spread", spreadSlider.getValue(), 0.5);
    appendIfNotDefault(sb, "accent", accentSlider.getValue(), 0.5);
    sb.append("]");
    return sb.toString();
  }

  private static void appendIfNotDefault(StringBuilder sb, String key, double value, double defaultValue) {
    if (Math.abs(value - defaultValue) > 0.005) {
      sb.append(" ").append(key).append(":").append(String.format("%.2f", value));
    }
  }

  private static Label sectionTitle(String text) {
    Label label = new Label(text);
    label.setStyle(STYLE_SECTION_TITLE);
    return label;
  }

  private static Slider createSlider(double initial) {
    Slider slider = new Slider(0.0, 1.0, initial);
    slider.setBlockIncrement(0.01);
    slider.setMajorTickUnit(0.25);
    slider.setMinorTickCount(4);
    slider.setShowTickMarks(false);
    slider.setShowTickLabels(false);
    slider.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(slider, Priority.ALWAYS);
    return slider;
  }

  private static void addParamRow(GridPane grid, int row, String name,
      Slider slider, Label valueLabel, String tooltip) {
    Label label = new Label(name);
    label.setStyle(STYLE_PARAM_LABEL);
    label.setTooltip(new Tooltip(tooltip));
    valueLabel.setStyle(STYLE_VALUE_LABEL);
    valueLabel.setAlignment(Pos.CENTER_RIGHT);
    valueLabel.setMinWidth(32);
    grid.add(label, 0, row);
    grid.add(slider, 1, row);
    grid.add(valueLabel, 2, row);
    GridPane.setHgrow(slider, Priority.ALWAYS);
  }

  private static void addDiagRow(GridPane grid, int row, String key, Label valueLabel) {
    Label keyLabel = new Label(key);
    keyLabel.setStyle(STYLE_DIAG_KEY);
    valueLabel.setStyle(STYLE_DIAG_VALUE);
    valueLabel.setWrapText(true);
    grid.add(keyLabel, 0, row);
    grid.add(valueLabel, 1, row);
  }
}
