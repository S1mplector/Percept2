package com.jvn.editor.ui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import com.jvn.audiofx.AudioFxController;
import com.jvn.audiofx.SynthPreviewSettings;
import com.jvn.audiofx.SynthPreviewSettings.SynthType;
import com.jvn.audiofx.VnsCommandBuilder;
import com.jvn.audiofx.WaveformAnalyzer;

import javafx.animation.AnimationTimer;
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
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Professional editor sidebar panel for synthesizer authoring and preview.
 * <ul>
 *   <li>Ambience-first parameter controls</li>
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

  // --- Streaming waveform ---
  private final WaveformAnalyzer.StreamingAnalyzer streamingAnalyzer =
      new WaveformAnalyzer.StreamingAnalyzer();
  private AnimationTimer waveformTimer;
  private final ScheduledExecutorService snapshotExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "synth-snapshot");
        t.setDaemon(true);
        return t;
      });
  private ScheduledFuture<?> pendingSnapshot;

  // --- Callbacks ---
  private Consumer<String> onInsertSnippet;

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

  // --- Shared controls ---
  private final Slider intensitySlider = createSlider(0.65);
  private final Slider volumeSlider = createSlider(0.45);
  private final Label intensityValue = new Label("0.65");
  private final Label volumeValue = new Label("0.45");
  private final CheckBox loopCheck = new CheckBox("Loop");

  // --- Preview / visualizers ---
  private final Button btnPlay = new Button("\u25B6 Play");
  private final Button btnStop = new Button("\u25A0 Stop");
  private final Canvas spectrumCanvas = new Canvas(300, 80);
  private final Canvas waveformCanvas = new Canvas(300, 48);
  private final Label lblRms = new Label("RMS: —");
  private final Label lblPeak = new Label("Peak: —");

  // --- Spectrum animation state ---
  private static final int SPECTRUM_BANDS = 64;
  private final float[] smoothedSpectrum = new float[SPECTRUM_BANDS];
  private final float[] spectrumPeaks = new float[SPECTRUM_BANDS];
  private final float[] spectrumPeakVel = new float[SPECTRUM_BANDS];
  // --- Waveform scrolling buffer ---
  private static final int WAVE_SAMPLES = 256;
  private final float[] waveBuffer = new float[WAVE_SAMPLES];
  private int waveWriteIdx = 0;
  {
    java.util.Arrays.fill(smoothedSpectrum, -60f);
    java.util.Arrays.fill(spectrumPeaks, -60f);
  }

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
    settings.setType(SynthType.AMBIENCE);
    buildUi();
    wireListeners();
    initWaveformTimer();
    syncUiFromSettings();
    requestSnapshot();
  }

  /** Clean up background threads. Call when this view is permanently removed. */
  public void dispose() {
    stopStreaming();
    snapshotExecutor.shutdownNow();
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

    Label title = new Label("Ambience Authoring");
    title.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: #d0d8e8;");

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

    // Spectrum + Waveform visualizers
    spectrumCanvas.setStyle("-fx-background-color: #0a0e14;");
    waveformCanvas.setStyle("-fx-background-color: #0a0e14;");
    Label spectrumLabel = new Label("Spectrum");
    spectrumLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #4a5568;");
    Label waveformLabel = new Label("Waveform");
    waveformLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #4a5568;");
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
        ambienceParamsBox,
        new Separator(),
        sectionLabel("Common"), sharedGrid, loopCheck,
        new Separator(),
        sectionLabel("Preview"), previewRow,
        spectrumLabel, spectrumCanvas,
        waveformLabel, waveformCanvas, metersRow,
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
        spectrumCanvas.setWidth(w);
        waveformCanvas.setWidth(w);
        if (!playing) requestSnapshot();
      }
    });
  }

  private void wireListeners() {
    presetCombo.valueProperty().addListener((obs, o, n) -> {
      settings.setPreset(n);
      updateSnippetPreview();
      onSettingsChanged();
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
      onSettingsChanged();
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
      onSettingsChanged();
      persistSettings();
    });
  }

  private void syncUiFromSettings() {
    settings.setType(SynthType.AMBIENCE);
    presetCombo.setValue(settings.preset());
    intensitySlider.setValue(settings.intensity());
    volumeSlider.setValue(settings.volume());
    detailSlider.setValue(settings.detail());
    motionSlider.setValue(settings.motion());
    spreadSlider.setValue(settings.spread());
    accentSlider.setValue(settings.accent());
    loopCheck.setSelected(settings.loop());
    updateSnippetPreview();
  }

  private void updateSnippetPreview() {
    snippetPreview.setText(VnsCommandBuilder.buildOnCommand(settings));
  }

  // --- Settings change dispatch ---

  private void onSettingsChanged() {
    if (playing) {
      // Retune the live ambience bed in place instead of hard-restarting it.
      startControllerPlayback();
      streamingAnalyzer.reconfigure(settings);
    } else {
      requestSnapshot();
    }
  }

  // --- Live preview ---

  private void doPlay() {
    if (controller == null) return;
    try {
      playing = true;
      btnPlay.setDisable(true);
      btnStop.setDisable(false);
      startControllerPlayback();
      startStreaming();
    } catch (RuntimeException ex) {
      playing = false;
      btnPlay.setDisable(false);
      btnStop.setDisable(true);
      stopStreaming();
      drawVisualizerError(errorSummary(ex));
      refreshDiagnosticsFromController();
    }
  }

  private void doStop() {
    playing = false;
    btnPlay.setDisable(false);
    btnStop.setDisable(true);
    stopStreaming();
    stopControllerPlayback();
    // Show final static snapshot
    requestSnapshot();
  }

  private void startControllerPlayback() {
    if (controller == null) return;
    controller.playAmbience(settings.preset(), settings.intensity(), settings.volume(),
        settings.toAmbienceProfile());
  }

  private void stopControllerPlayback() {
    if (controller == null) return;
    controller.stopAmbience();
  }

  // --- Streaming waveform ---

  private void startStreaming() {
    cancelPendingSnapshot();
    streamingAnalyzer.start(settings);
    if (waveformTimer != null) waveformTimer.start();
  }

  private void stopStreaming() {
    if (waveformTimer != null) waveformTimer.stop();
    streamingAnalyzer.stop();
  }

  private void initWaveformTimer() {
    waveformTimer = new AnimationTimer() {
      @Override
      public void handle(long now) {
        WaveformAnalyzer.Analysis a = streamingAnalyzer.latest();
        if (a != null && a.envelope().length > 0) {
          drawAnalysis(a);
        }
      }
    };
  }

  /**
   * Debounced static snapshot — used when NOT playing. Cancels any pending
   * render and schedules a new one 50ms out so rapid slider drags only
   * trigger a single native renderer creation.
   */
  private void requestSnapshot() {
    if (playing) return;
    cancelPendingSnapshot();
    SynthPreviewSettings snap = settings.copy();
    pendingSnapshot = snapshotExecutor.schedule(() -> {
      try {
        WaveformAnalyzer.Analysis a = WaveformAnalyzer.analyze(snap, WAVEFORM_BINS);
        Platform.runLater(() -> drawAnalysis(a));
      } catch (RuntimeException ex) {
        Platform.runLater(() -> drawVisualizerError(errorSummary(ex)));
      }
    }, 50, TimeUnit.MILLISECONDS);
  }

  private void cancelPendingSnapshot() {
    if (pendingSnapshot != null) {
      pendingSnapshot.cancel(false);
      pendingSnapshot = null;
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  Drawing — dual spectrum + waveform visualizer
  // ═══════════════════════════════════════════════════════════════════

  private void drawAnalysis(WaveformAnalyzer.Analysis analysis) {
    if (analysis == null) {
      drawVisualizerError("No data");
      return;
    }
    drawSpectrum(analysis);
    drawWaveform(analysis);

    lblRms.setText(String.format("RMS: %.4f", analysis.rms()));
    lblPeak.setText(String.format("Peak: %.4f", analysis.peak()));
  }

  // --- Spectrum bars (top canvas) ---

  private void drawSpectrum(WaveformAnalyzer.Analysis analysis) {
    double w = spectrumCanvas.getWidth();
    double h = spectrumCanvas.getHeight();
    GraphicsContext gc = spectrumCanvas.getGraphicsContext2D();

    // Background
    gc.setFill(Color.web("#0a0e14"));
    gc.fillRect(0, 0, w, h);

    // Subtle grid lines
    gc.setStroke(Color.web("#141a24"));
    gc.setLineWidth(0.5);
    for (double frac : new double[]{0.25, 0.5, 0.75}) {
      gc.strokeLine(0, h * frac, w, h * frac);
    }

    float[] spec = analysis.spectrum();
    if (spec == null || spec.length == 0) {
      gc.setFill(Color.web("#404858"));
      gc.fillText(!analysis.nativeAvailable() ? "Native unavailable" : "No spectrum data", 8, h / 2);
      return;
    }

    int bands = Math.min(spec.length, SPECTRUM_BANDS);
    double bandW = w / bands;
    double barW = Math.max(1, bandW * 0.75);
    double gap = (bandW - barW) / 2.0;

    // Smooth spectrum and animate peaks
    for (int i = 0; i < bands; i++) {
      float target = spec[i];
      // Exponential smoothing — fast attack, slower decay
      if (target > smoothedSpectrum[i]) {
        smoothedSpectrum[i] += (target - smoothedSpectrum[i]) * 0.45f;
      } else {
        smoothedSpectrum[i] += (target - smoothedSpectrum[i]) * 0.12f;
      }
      // Peak hold with gravity fall
      if (smoothedSpectrum[i] > spectrumPeaks[i]) {
        spectrumPeaks[i] = smoothedSpectrum[i];
        spectrumPeakVel[i] = 0;
      } else {
        spectrumPeakVel[i] += 0.08f;
        spectrumPeaks[i] -= spectrumPeakVel[i];
        if (spectrumPeaks[i] < -60f) spectrumPeaks[i] = -60f;
      }
    }

    // Draw bars with gradient
    for (int i = 0; i < bands; i++) {
      double norm = (60 + smoothedSpectrum[i]) / 60.0;
      norm = Math.max(0, Math.min(1, norm));
      double barH = norm * h * 0.92;
      double x = i * bandW + gap;
      double y = h - barH;

      // Frequency-dependent color: bass=cyan, mid=blue, treble=purple
      double freqT = (double) i / bands;
      Color barBase = interpolateSpectrumColor(freqT);
      Color barTop = barBase.brighter();
      Color barBottom = barBase.darker().darker();

      javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
          0, y, 0, h, false, null,
          new javafx.scene.paint.Stop(0, barTop),
          new javafx.scene.paint.Stop(0.5, barBase),
          new javafx.scene.paint.Stop(1, barBottom)
      );
      gc.setFill(gradient);
      gc.fillRect(x, y, barW, barH);

      // Subtle inner highlight
      gc.setFill(Color.color(1, 1, 1, 0.06 * norm));
      gc.fillRect(x, y, barW * 0.4, barH);

      // Peak cap
      double peakNorm = (60 + spectrumPeaks[i]) / 60.0;
      peakNorm = Math.max(0, Math.min(1, peakNorm));
      if (peakNorm > norm + 0.01) {
        double peakY = h - peakNorm * h * 0.92;
        gc.setFill(Color.color(1, 1, 1, 0.8));
        gc.fillRect(x - 0.5, peakY - 1, barW + 1, 2);
      }
    }

    // dB scale labels
    gc.setFill(Color.web("#2a3444", 0.6));
    gc.fillText("-20dB", 2, h * 0.33 + 3);
    gc.fillText("-40dB", 2, h * 0.67 + 3);
  }

  private static Color interpolateSpectrumColor(double t) {
    // Bass: warm cyan (#40c8e0) → Mid: blue (#4080f0) → Treble: purple (#9060e0)
    if (t < 0.5) {
      double f = t * 2.0;
      return Color.web("#40c8e0").interpolate(Color.web("#4080f0"), f);
    } else {
      double f = (t - 0.5) * 2.0;
      return Color.web("#4080f0").interpolate(Color.web("#9060e0"), f);
    }
  }

  // --- Waveform oscilloscope (bottom canvas) ---

  private void drawWaveform(WaveformAnalyzer.Analysis analysis) {
    double w = waveformCanvas.getWidth();
    double h = waveformCanvas.getHeight();
    GraphicsContext gc = waveformCanvas.getGraphicsContext2D();

    // Background
    gc.setFill(Color.web("#0a0e14"));
    gc.fillRect(0, 0, w, h);

    float[] env = analysis.envelope();
    if (env == null || env.length == 0) return;

    // Feed envelope into scrolling buffer
    int step = Math.max(1, env.length / 8);
    for (int i = 0; i < env.length; i += step) {
      waveBuffer[waveWriteIdx % WAVE_SAMPLES] = env[i];
      waveWriteIdx++;
    }

    double midY = h / 2.0;

    // Center line
    gc.setStroke(Color.web("#141a24"));
    gc.setLineWidth(0.5);
    gc.strokeLine(0, midY, w, midY);

    // Draw mirrored waveform from scrolling buffer
    int readStart = (waveWriteIdx - WAVE_SAMPLES + WAVE_SAMPLES * 100) % WAVE_SAMPLES;
    double xStep = w / (WAVE_SAMPLES - 1);

    // Fill area (mirrored)
    gc.setFill(Color.web("#3080d0", 0.15));
    gc.beginPath();
    gc.moveTo(0, midY);
    for (int i = 0; i < WAVE_SAMPLES; i++) {
      double x = i * xStep;
      float amp = waveBuffer[(readStart + i) % WAVE_SAMPLES];
      double ampH = amp * midY * 0.88;
      gc.lineTo(x, midY - ampH);
    }
    gc.lineTo(w, midY);
    for (int i = WAVE_SAMPLES - 1; i >= 0; i--) {
      double x = i * xStep;
      float amp = waveBuffer[(readStart + i) % WAVE_SAMPLES];
      double ampH = amp * midY * 0.88;
      gc.lineTo(x, midY + ampH);
    }
    gc.closePath();
    gc.fill();

    // Stroke upper line
    gc.setStroke(Color.web("#50a0f0", 0.8));
    gc.setLineWidth(1.2);
    gc.beginPath();
    for (int i = 0; i < WAVE_SAMPLES; i++) {
      double x = i * xStep;
      float amp = waveBuffer[(readStart + i) % WAVE_SAMPLES];
      double y = midY - amp * midY * 0.88;
      if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
    }
    gc.stroke();

    // Stroke lower line (mirrored)
    gc.setStroke(Color.web("#50a0f0", 0.4));
    gc.setLineWidth(0.8);
    gc.beginPath();
    for (int i = 0; i < WAVE_SAMPLES; i++) {
      double x = i * xStep;
      float amp = waveBuffer[(readStart + i) % WAVE_SAMPLES];
      double y = midY + amp * midY * 0.88;
      if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
    }
    gc.stroke();

    // RMS dashed line
    double rmsH = analysis.rms() * midY * 0.88;
    gc.setStroke(Color.web("#68a0d0", 0.35));
    gc.setLineWidth(0.6);
    gc.setLineDashes(3, 3);
    gc.strokeLine(0, midY - rmsH, w, midY - rmsH);
    gc.strokeLine(0, midY + rmsH, w, midY + rmsH);
    gc.setLineDashes();

    // Peak dashed line
    double peakH = analysis.peak() * midY * 0.88;
    gc.setStroke(Color.web("#d08868", 0.25));
    gc.setLineWidth(0.6);
    gc.setLineDashes(2, 4);
    gc.strokeLine(0, midY - peakH, w, midY - peakH);
    gc.strokeLine(0, midY + peakH, w, midY + peakH);
    gc.setLineDashes();
  }

  private void drawVisualizerError(String message) {
    // Clear both canvases
    for (Canvas c : new Canvas[]{spectrumCanvas, waveformCanvas}) {
      double w = c.getWidth();
      double h = c.getHeight();
      GraphicsContext gc = c.getGraphicsContext2D();
      gc.setFill(Color.web("#0a0e14"));
      gc.fillRect(0, 0, w, h);
    }
    // Show error on spectrum canvas
    GraphicsContext gc = spectrumCanvas.getGraphicsContext2D();
    gc.setFill(Color.web("#f08868"));
    gc.fillText(message == null || message.isBlank() ? "Native renderer unavailable" : message,
        8, spectrumCanvas.getHeight() / 2.0);
    lblRms.setText("RMS: —");
    lblPeak.setText("Peak: —");
  }

  private static String errorSummary(Throwable ex) {
    if (ex == null) return "Native renderer unavailable";
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    return message.length() > 64 ? message.substring(0, 61) + "..." : message;
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
      settings.setType(SynthType.AMBIENCE);
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
