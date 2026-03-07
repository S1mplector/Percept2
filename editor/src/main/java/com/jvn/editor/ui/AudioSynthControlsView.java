package com.jvn.editor.ui;

import com.jvn.audiofx.AudioFxController;
import com.jvn.audiofx.SynthPreviewSettings;
import com.jvn.audiofx.SynthPreviewSettings.SynthType;
import com.jvn.audiofx.VnsCommandBuilder;
import com.jvn.audiofx.WaveformAnalyzer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Professional editor sidebar panel for synthesizer authoring and preview.
 * <ul>
 *   <li>Type toggle: ambience / chiptune</li>
 *   <li>Conditional parameter controls per synth type</li>
 *   <li>Play/Stop live preview through the real audio runtime</li>
 *   <li>Waveform visualization with RMS/peak meters</li>
 *   <li>VNS command generation, copy, and insert-into-script</li>
 *   <li>Audio backend diagnostics</li>
 *   <li>Persists last-used settings via java.util.prefs</li>
 * </ul>
 */
public class AudioSynthControlsView extends BorderPane {

  // --- Style constants ---
  private static final String S_SECTION =
      "-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: #c8d0e0;";
  private static final String S_PARAM =
      "-fx-font-size: 11px; -fx-text-fill: #a0a8b8;";
  private static final String S_VAL =
      "-fx-font-size: 11px; -fx-text-fill: #78b0ff; -fx-min-width: 32;";
  private static final String S_DIAG_K =
      "-fx-font-size: 11px; -fx-text-fill: #8090a8;";
  private static final String S_DIAG_V =
      "-fx-font-size: 11px; -fx-text-fill: #c0c8d4;";
  private static final String S_OK =
      "-fx-font-size: 11px; -fx-text-fill: #5ee072; -fx-font-weight: 700;";
  private static final String S_FAIL =
      "-fx-font-size: 11px; -fx-text-fill: #f06060; -fx-font-weight: 700;";
  private static final String S_SNIPPET =
      "-fx-font-family: 'Monospace'; -fx-font-size: 10px; -fx-text-fill: #9cc7ff; "
          + "-fx-background-color: #181c28; -fx-padding: 6; -fx-background-radius: 3;";

  private static final int WAVEFORM_BINS = 128;
  private static final Preferences PREFS =
      Preferences.userNodeForPackage(AudioSynthControlsView.class);

  // --- Model ---
  private final SynthPreviewSettings settings = new SynthPreviewSettings();
  private AudioFxController controller;
  private volatile boolean playing;

  // --- Callbacks ---
  private Consumer<String> onInsertSnippet;

  // --- Type toggle ---
  private final ToggleGroup typeGroup = new ToggleGroup();
  private final ToggleButton btnAmbience = new ToggleButton("Ambience");
  private final ToggleButton btnChiptune = new ToggleButton("Chiptune");

  // --- Ambience controls ---
  private final ComboBox<String> presetCombo = new ComboBox<>();
  private final VBox ambienceParamsBox = new VBox(6);
  private final Slider detailSlider = createSlider(0.5);
  private final Slider motionSlider = createSlider(0.5);
  private final Slider spreadSlider = createSlider(0.5);
  private final Slider accentSlider = createSlider(0.5);
  private final Label detailValue = new Label("0.50");
  private final Label motionValue = new Label("0.50");
  private final Label spreadValue = new Label("0.50");
  private final Label accentValue = new Label("0.50");

  // --- Chiptune controls ---
  private final ComboBox<String> cueCombo = new ComboBox<>();
  private final VBox chiptuneParamsBox = new VBox(6);

  // --- Shared controls ---
  private final Slider intensitySlider = createSlider(0.65);
  private final Slider volumeSlider = createSlider(0.45);
  private final Label intensityValue = new Label("0.65");
  private final Label volumeValue = new Label("0.45");
  private final CheckBox loopCheck = new CheckBox("Loop");

  // --- Preview / waveform ---
  private final Button btnPlay = new Button("\u25B6 Play");
  private final Button btnStop = new Button("\u25A0 Stop");
  private final Canvas waveformCanvas = new Canvas(300, 64);
  private final Label lblRms = new Label("RMS: —");
  private final Label lblPeak = new Label("Peak: —");

  // --- Snippet ---
  private final Label snippetPreview = new Label("");
  private final Button btnCopy = new Button("Copy");
  private final Button btnInsert = new Button("Insert into Script");

  // --- Diagnostics ---
  private final Label diagBridgeStatus = new Label("—");
  private final Label diagAmbienceProvider = new Label("—");
  private final Label diagChiptuneProvider = new Label("—");
  private final Label diagBridgeInfo = new Label("—");

  public AudioSynthControlsView() {
    loadPersistedSettings();
    buildUi();
    wireListeners();
    syncUiFromSettings();
    refreshWaveform();
  }

  // --- Public API ---

  /** Set the AudioFxController for live preview playback. */
  public void setController(AudioFxController controller) {
    this.controller = controller;
    refreshDiagnosticsFromController();
  }

  /** Set a callback invoked when the user clicks "Insert into Script". */
  public void setOnInsertSnippet(Consumer<String> callback) {
    this.onInsertSnippet = callback;
  }

  /** Refresh diagnostics display from the controller. */
  public void refreshDiagnosticsFromController() {
    if (controller == null) {
      diagBridgeStatus.setText("No controller");
      diagBridgeStatus.setStyle(S_FAIL);
      diagAmbienceProvider.setText("—");
      diagChiptuneProvider.setText("—");
      diagBridgeInfo.setText("—");
      return;
    }
    boolean bridgeOk = controller.nativeBridgeAvailable();
    diagBridgeStatus.setText(bridgeOk ? "Loaded" : "Unavailable");
    diagBridgeStatus.setStyle(bridgeOk ? S_OK : S_FAIL);
    diagAmbienceProvider.setText(controller.ambienceProviderId());
    diagChiptuneProvider.setText(controller.beezProviderId());
    diagBridgeInfo.setText(controller.diagnosticsSummary());
  }

  /** Refresh diagnostics from explicit values (legacy API). */
  public void refreshDiagnostics(boolean bridgeAvailable, String ambienceId,
      String chiptuneId, String bridgeDiagnostic) {
    diagBridgeStatus.setText(bridgeAvailable ? "Loaded" : "Unavailable");
    diagBridgeStatus.setStyle(bridgeAvailable ? S_OK : S_FAIL);
    diagAmbienceProvider.setText(ambienceId != null ? ambienceId : "—");
    diagChiptuneProvider.setText(chiptuneId != null ? chiptuneId : "—");
    diagBridgeInfo.setText(bridgeDiagnostic != null ? bridgeDiagnostic : "—");
  }

  // --- UI construction ---

  private void buildUi() {
    VBox root = new VBox(8);
    root.setPadding(new Insets(10));

    Label title = new Label("Synth Authoring");
    title.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: #d0d8e8;");

    // Type toggle
    btnAmbience.setToggleGroup(typeGroup);
    btnChiptune.setToggleGroup(typeGroup);
    btnAmbience.setMaxWidth(Double.MAX_VALUE);
    btnChiptune.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(btnAmbience, Priority.ALWAYS);
    HBox.setHgrow(btnChiptune, Priority.ALWAYS);
    HBox typeRow = new HBox(4, btnAmbience, btnChiptune);
    typeRow.setAlignment(Pos.CENTER);

    // Ambience params
    presetCombo.getItems().addAll("wind", "rain", "ocean", "thunder", "fireplace", "night_insects");
    presetCombo.setValue(settings.preset());
    presetCombo.setMaxWidth(Double.MAX_VALUE);
    presetCombo.setTooltip(new Tooltip("Ambience preset (mode:\"...\")"));

    GridPane ambienceGrid = new GridPane();
    ambienceGrid.setHgap(6);
    ambienceGrid.setVgap(5);
    addParam(ambienceGrid, 0, "Detail", detailSlider, detailValue, "High-frequency texture richness");
    addParam(ambienceGrid, 1, "Motion", motionSlider, motionValue, "Temporal variation speed");
    addParam(ambienceGrid, 2, "Spread", spreadSlider, spreadValue, "Stereo width");
    addParam(ambienceGrid, 3, "Accent", accentSlider, accentValue, "Preset-specific character emphasis");
    ambienceParamsBox.getChildren().addAll(
        sectionLabel("Preset"), presetCombo,
        sectionLabel("Ambience Shaping"), ambienceGrid);

    // Chiptune params
    cueCombo.getItems().addAll("blip", "confirm", "error", "pickup");
    cueCombo.setValue(settings.cueId());
    cueCombo.setMaxWidth(Double.MAX_VALUE);
    cueCombo.setTooltip(new Tooltip("Chiptune cue pattern (cue:\"...\")"));
    chiptuneParamsBox.getChildren().addAll(sectionLabel("Cue"), cueCombo);

    // Shared controls
    GridPane sharedGrid = new GridPane();
    sharedGrid.setHgap(6);
    sharedGrid.setVgap(5);
    addParam(sharedGrid, 0, "Intensity", intensitySlider, intensityValue, "Energy level");
    addParam(sharedGrid, 1, "Volume", volumeSlider, volumeValue, "Output volume");
    loopCheck.setSelected(settings.loop());
    loopCheck.setTooltip(new Tooltip("Enable looping playback"));

    // Preview controls
    btnPlay.setMaxWidth(Double.MAX_VALUE);
    btnStop.setMaxWidth(Double.MAX_VALUE);
    btnStop.setDisable(true);
    HBox.setHgrow(btnPlay, Priority.ALWAYS);
    HBox.setHgrow(btnStop, Priority.ALWAYS);
    HBox previewRow = new HBox(4, btnPlay, btnStop);
    previewRow.setAlignment(Pos.CENTER);

    // Waveform
    waveformCanvas.setStyle("-fx-background-color: #0e1018;");
    lblRms.setStyle("-fx-font-size: 10px; -fx-text-fill: #68a0d0;");
    lblPeak.setStyle("-fx-font-size: 10px; -fx-text-fill: #d08868;");
    HBox metersRow = new HBox(12, lblRms, lblPeak);
    metersRow.setAlignment(Pos.CENTER_LEFT);

    // Snippet
    snippetPreview.setWrapText(true);
    snippetPreview.setStyle(S_SNIPPET);
    snippetPreview.setMaxWidth(Double.MAX_VALUE);
    btnCopy.setMaxWidth(Double.MAX_VALUE);
    btnInsert.setMaxWidth(Double.MAX_VALUE);
    btnCopy.setTooltip(new Tooltip("Copy VNS command to clipboard"));
    btnInsert.setTooltip(new Tooltip("Insert VNS command at caret in active VNS script"));
    HBox snippetBtns = new HBox(4, btnCopy, btnInsert);
    HBox.setHgrow(btnCopy, Priority.ALWAYS);
    HBox.setHgrow(btnInsert, Priority.ALWAYS);

    // Diagnostics
    GridPane diagGrid = new GridPane();
    diagGrid.setHgap(8);
    diagGrid.setVgap(3);
    addDiagRow(diagGrid, 0, "JNI Bridge", diagBridgeStatus);
    addDiagRow(diagGrid, 1, "Ambience", diagAmbienceProvider);
    addDiagRow(diagGrid, 2, "Chiptune", diagChiptuneProvider);
    addDiagRow(diagGrid, 3, "Info", diagBridgeInfo);

    root.getChildren().addAll(
        title,
        new Separator(),
        sectionLabel("Synth Type"), typeRow,
        ambienceParamsBox,
        chiptuneParamsBox,
        new Separator(),
        sectionLabel("Common"), sharedGrid, loopCheck,
        new Separator(),
        sectionLabel("Preview"), previewRow,
        waveformCanvas, metersRow,
        new Separator(),
        sectionLabel("VNS Command"), snippetPreview, snippetBtns,
        new Separator(),
        sectionLabel("Audio Backend"), diagGrid
    );

    ScrollPane scroll = new ScrollPane(root);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
    setCenter(scroll);

    // Responsive canvas width
    root.widthProperty().addListener((obs, o, n) -> {
      double w = n.doubleValue() - 20;
      if (w > 40) {
        waveformCanvas.setWidth(w);
        refreshWaveform();
      }
    });
  }

  private void wireListeners() {
    typeGroup.selectedToggleProperty().addListener((obs, o, n) -> {
      if (n == btnChiptune) {
        settings.setType(SynthType.CHIPTUNE);
      } else {
        settings.setType(SynthType.AMBIENCE);
      }
      updateTypeVisibility();
      updateSnippetPreview();
      refreshWaveform();
      persistSettings();
    });

    presetCombo.valueProperty().addListener((obs, o, n) -> {
      settings.setPreset(n);
      updateSnippetPreview();
      refreshWaveform();
      persistSettings();
    });

    cueCombo.valueProperty().addListener((obs, o, n) -> {
      settings.setCueId(n);
      updateSnippetPreview();
      refreshWaveform();
      persistSettings();
    });

    wireSlider(intensitySlider, intensityValue, v -> settings.setIntensity((float) v));
    wireSlider(volumeSlider, volumeValue, v -> settings.setVolume((float) v));
    wireSlider(detailSlider, detailValue, v -> settings.setDetail((float) v));
    wireSlider(motionSlider, motionValue, v -> settings.setMotion((float) v));
    wireSlider(spreadSlider, spreadValue, v -> settings.setSpread((float) v));
    wireSlider(accentSlider, accentValue, v -> settings.setAccent((float) v));

    loopCheck.selectedProperty().addListener((obs, o, n) -> {
      settings.setLoop(n);
      updateSnippetPreview();
      persistSettings();
    });

    btnPlay.setOnAction(e -> doPlay());
    btnStop.setOnAction(e -> doStop());

    btnCopy.setOnAction(e -> {
      ClipboardContent cc = new ClipboardContent();
      cc.putString(VnsCommandBuilder.buildOnCommand(settings));
      Clipboard.getSystemClipboard().setContent(cc);
    });

    btnInsert.setOnAction(e -> {
      if (onInsertSnippet != null) {
        onInsertSnippet.accept(VnsCommandBuilder.buildOnCommand(settings));
      }
    });
  }

  private void wireSlider(Slider slider, Label valueLabel, java.util.function.DoubleConsumer setter) {
    slider.valueProperty().addListener((obs, o, n) -> {
      double v = n.doubleValue();
      valueLabel.setText(String.format("%.2f", v));
      setter.accept(v);
      updateSnippetPreview();
      refreshWaveform();
      persistSettings();
    });
  }

  private void syncUiFromSettings() {
    if (settings.type() == SynthType.CHIPTUNE) {
      btnChiptune.setSelected(true);
    } else {
      btnAmbience.setSelected(true);
    }
    presetCombo.setValue(settings.preset());
    cueCombo.setValue(settings.cueId());
    intensitySlider.setValue(settings.intensity());
    volumeSlider.setValue(settings.volume());
    detailSlider.setValue(settings.detail());
    motionSlider.setValue(settings.motion());
    spreadSlider.setValue(settings.spread());
    accentSlider.setValue(settings.accent());
    loopCheck.setSelected(settings.loop());
    updateTypeVisibility();
    updateSnippetPreview();
  }

  private void updateTypeVisibility() {
    boolean isAmbience = settings.type() == SynthType.AMBIENCE;
    ambienceParamsBox.setVisible(isAmbience);
    ambienceParamsBox.setManaged(isAmbience);
    chiptuneParamsBox.setVisible(!isAmbience);
    chiptuneParamsBox.setManaged(!isAmbience);
  }

  private void updateSnippetPreview() {
    snippetPreview.setText(VnsCommandBuilder.buildOnCommand(settings));
  }

  // --- Live preview ---

  private void doPlay() {
    if (controller == null) return;
    playing = true;
    btnPlay.setDisable(true);
    btnStop.setDisable(false);

    if (settings.type() == SynthType.CHIPTUNE) {
      controller.playBeez(settings.cueId(), settings.intensity(), settings.volume(), settings.loop());
    } else {
      controller.playAmbience(settings.preset(), settings.intensity(), settings.volume(),
          settings.toAmbienceProfile());
    }
  }

  private void doStop() {
    playing = false;
    btnPlay.setDisable(false);
    btnStop.setDisable(true);
    if (controller == null) return;
    if (settings.type() == SynthType.CHIPTUNE) {
      controller.stopBeez();
    } else {
      controller.stopAmbience();
    }
  }

  // --- Waveform visualization ---

  private void refreshWaveform() {
    Thread t = new Thread(() -> {
      WaveformAnalyzer.Analysis analysis = WaveformAnalyzer.analyze(settings, WAVEFORM_BINS);
      Platform.runLater(() -> drawWaveform(analysis));
    }, "synth-waveform-render");
    t.setDaemon(true);
    t.start();
  }

  private void drawWaveform(WaveformAnalyzer.Analysis analysis) {
    double w = waveformCanvas.getWidth();
    double h = waveformCanvas.getHeight();
    GraphicsContext gc = waveformCanvas.getGraphicsContext2D();

    // Background
    gc.setFill(Color.web("#0e1018"));
    gc.fillRect(0, 0, w, h);

    // Grid lines
    gc.setStroke(Color.web("#1a2030"));
    gc.setLineWidth(0.5);
    double midY = h / 2.0;
    gc.strokeLine(0, midY, w, midY);
    gc.strokeLine(0, h * 0.25, w, h * 0.25);
    gc.strokeLine(0, h * 0.75, w, h * 0.75);

    if (analysis == null || analysis.envelope().length == 0) {
      gc.setFill(Color.web("#404858"));
      gc.fillText("No data", w / 2 - 20, midY + 4);
      lblRms.setText("RMS: —");
      lblPeak.setText("Peak: —");
      return;
    }

    float[] env = analysis.envelope();
    int bins = env.length;
    double binW = w / bins;

    // Waveform bars (mirrored)
    gc.setFill(Color.web("#3080d0", 0.7));
    gc.setStroke(Color.web("#50a0f0", 0.9));
    gc.setLineWidth(1.0);

    gc.beginPath();
    for (int i = 0; i < bins; i++) {
      double x = i * binW;
      double ampH = env[i] * midY * 0.92;
      gc.moveTo(x + binW * 0.5, midY - ampH);
      gc.lineTo(x + binW * 0.5, midY + ampH);
    }
    gc.stroke();

    // Fill envelope area
    gc.setFill(Color.web("#2060a0", 0.25));
    gc.beginPath();
    gc.moveTo(0, midY);
    for (int i = 0; i < bins; i++) {
      double x = i * binW + binW * 0.5;
      double ampH = env[i] * midY * 0.92;
      gc.lineTo(x, midY - ampH);
    }
    gc.lineTo(w, midY);
    for (int i = bins - 1; i >= 0; i--) {
      double x = i * binW + binW * 0.5;
      double ampH = env[i] * midY * 0.92;
      gc.lineTo(x, midY + ampH);
    }
    gc.closePath();
    gc.fill();

    // RMS line
    double rmsY = analysis.rms() * midY * 0.92;
    gc.setStroke(Color.web("#68a0d0", 0.5));
    gc.setLineWidth(0.8);
    gc.setLineDashes(3, 3);
    gc.strokeLine(0, midY - rmsY, w, midY - rmsY);
    gc.strokeLine(0, midY + rmsY, w, midY + rmsY);
    gc.setLineDashes();

    // Peak line
    double peakY = analysis.peak() * midY * 0.92;
    gc.setStroke(Color.web("#d08868", 0.4));
    gc.setLineWidth(0.8);
    gc.setLineDashes(2, 4);
    gc.strokeLine(0, midY - peakY, w, midY - peakY);
    gc.strokeLine(0, midY + peakY, w, midY + peakY);
    gc.setLineDashes();

    // Native indicator
    if (!analysis.nativeAvailable()) {
      gc.setFill(Color.web("#f0a050", 0.7));
      gc.fillText("Java fallback", 4, 12);
    }

    lblRms.setText(String.format("RMS: %.4f", analysis.rms()));
    lblPeak.setText(String.format("Peak: %.4f", analysis.peak()));
  }

  // --- Persistence ---

  private void persistSettings() {
    try {
      PREFS.put("synthType", settings.type().name());
      PREFS.put("preset", settings.preset());
      PREFS.put("cueId", settings.cueId());
      PREFS.putFloat("intensity", settings.intensity());
      PREFS.putFloat("volume", settings.volume());
      PREFS.putBoolean("loop", settings.loop());
      PREFS.putFloat("detail", settings.detail());
      PREFS.putFloat("motion", settings.motion());
      PREFS.putFloat("spread", settings.spread());
      PREFS.putFloat("accent", settings.accent());
      PREFS.flush();
    } catch (Exception ignored) {
    }
  }

  private void loadPersistedSettings() {
    try {
      String type = PREFS.get("synthType", "AMBIENCE");
      settings.setType("CHIPTUNE".equals(type) ? SynthType.CHIPTUNE : SynthType.AMBIENCE);
      settings.setPreset(PREFS.get("preset", "wind"));
      settings.setCueId(PREFS.get("cueId", "blip"));
      settings.setIntensity(PREFS.getFloat("intensity", 0.65f));
      settings.setVolume(PREFS.getFloat("volume", 0.45f));
      settings.setLoop(PREFS.getBoolean("loop", true));
      settings.setDetail(PREFS.getFloat("detail", 0.50f));
      settings.setMotion(PREFS.getFloat("motion", 0.50f));
      settings.setSpread(PREFS.getFloat("spread", 0.50f));
      settings.setAccent(PREFS.getFloat("accent", 0.50f));
    } catch (Exception ignored) {
    }
  }

  // --- Helpers ---

  private static Label sectionLabel(String text) {
    Label l = new Label(text);
    l.setStyle(S_SECTION);
    return l;
  }

  private static Slider createSlider(double initial) {
    Slider slider = new Slider(0.0, 1.0, initial);
    slider.setBlockIncrement(0.01);
    slider.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(slider, Priority.ALWAYS);
    return slider;
  }

  private static void addParam(GridPane grid, int row, String name,
      Slider slider, Label valueLabel, String tooltip) {
    Label label = new Label(name);
    label.setStyle(S_PARAM);
    label.setTooltip(new Tooltip(tooltip));
    valueLabel.setStyle(S_VAL);
    valueLabel.setAlignment(Pos.CENTER_RIGHT);
    valueLabel.setMinWidth(32);
    grid.add(label, 0, row);
    grid.add(slider, 1, row);
    grid.add(valueLabel, 2, row);
    GridPane.setHgrow(slider, Priority.ALWAYS);
  }

  private static void addDiagRow(GridPane grid, int row, String key, Label valueLabel) {
    Label k = new Label(key);
    k.setStyle(S_DIAG_K);
    valueLabel.setStyle(S_DIAG_V);
    valueLabel.setWrapText(true);
    grid.add(k, 0, row);
    grid.add(valueLabel, 1, row);
  }
}
