package com.jvn.editor.ui;

import java.util.Locale;
import java.util.function.Consumer;

import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.vn.VnParticleCommand;
import com.jvn.core.vn.VnParticlePresetLibrary;
import com.jvn.fx.scene2d.FxBlitter2D;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class ParticleFxToolView extends BorderPane {
  private static final double PREVIEW_WIDTH = 420.0;
  private static final double PREVIEW_HEIGHT = 190.0;

  private final ComboBox<VnParticleCommand.Preset> presetCombo = new ComboBox<>();
  private final Slider intensitySlider = unitSlider(0.5);
  private final Slider opacitySlider = unitSlider(1.0);
  private final Spinner<Integer> layerSpinner = new Spinner<>();
  private final Spinner<Double> speedSpinner = new Spinner<>();
  private final Spinner<Double> windSpinner = new Spinner<>();
  private final Spinner<Integer> durationSpinner = new Spinner<>();
  private final CheckBox tintCheck = new CheckBox("Tint");
  private final ColorPicker tintPicker = new ColorPicker(Color.WHITE);
  private final TextField commandField = new TextField();
  private final Label statusLabel = new Label("Particle FX command ready.");
  private final Canvas previewCanvas = new Canvas(PREVIEW_WIDTH, PREVIEW_HEIGHT);
  private final ParticleEmitter2D previewEmitter = new ParticleEmitter2D();
  private final FxBlitter2D previewBlitter = new FxBlitter2D(previewCanvas.getGraphicsContext2D());
  private final AnimationTimer previewTimer;

  private Consumer<String> onInsertCommand;
  private Consumer<String> onCopyCommand;
  private long lastPreviewNanos = 0L;

  public ParticleFxToolView() {
    // Reuse the editor settings sidebar style namespace so spinners, combos,
    // checks, and text fields render identically to the Editor Settings tool.
    getStyleClass().addAll("editor-settings-view", "particle-fx-tool-view", "sidebar-tool-root");

    presetCombo.getItems().addAll(
        VnParticleCommand.Preset.SNOW,
        VnParticleCommand.Preset.RAIN,
        VnParticleCommand.Preset.SAKURA,
        VnParticleCommand.Preset.FIREFLIES,
        VnParticleCommand.Preset.DUST,
        VnParticleCommand.Preset.LEAVES);
    presetCombo.getSelectionModel().select(VnParticleCommand.Preset.RAIN);
    presetCombo.setMaxWidth(Double.MAX_VALUE);
    presetCombo.getStyleClass().add("editor-settings-combo");

    layerSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-1000, 1000, 100, 10));
    speedSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 10.0, 1.0, 0.05));
    windSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(-1000.0, 1000.0, 0.0, 5.0));
    durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 600000, 0, 500));
    for (Spinner<?> spinner : new Spinner<?>[] { layerSpinner, speedSpinner, windSpinner, durationSpinner }) {
      spinner.setEditable(true);
      spinner.setMaxWidth(Double.MAX_VALUE);
      spinner.getStyleClass().add("editor-settings-spinner");
    }

    commandField.setEditable(false);
    commandField.getStyleClass().addAll("editor-settings-text-field", "particle-fx-command-field");
    HBox.setHgrow(commandField, Priority.ALWAYS);

    tintCheck.getStyleClass().add("editor-settings-check");
    tintPicker.disableProperty().bind(tintCheck.selectedProperty().not());

    GridPane controls = new GridPane();
    controls.setHgap(10);
    controls.setVgap(8);
    controls.addRow(0, fieldLabel("Preset"), presetCombo);
    controls.addRow(1, fieldLabel("Intensity"), intensitySlider);
    controls.addRow(2, fieldLabel("Opacity"), opacitySlider);
    controls.addRow(3, fieldLabel("Layer"), layerSpinner);
    controls.addRow(4, fieldLabel("Speed"), speedSpinner);
    controls.addRow(5, fieldLabel("Wind"), windSpinner);
    controls.addRow(6, fieldLabel("Duration ms"), durationSpinner);
    controls.addRow(7, tintCheck, tintPicker);
    controls.getColumnConstraints().addAll(
        column(100, Priority.NEVER),
        column(180, Priority.ALWAYS));

    Button insertButton = new Button("Insert");
    insertButton.setOnAction(e -> insertCommand(buildStartCommand()));
    Button copyButton = new Button("Copy");
    copyButton.setOnAction(e -> copyCommand(buildStartCommand()));
    Button stopButton = new Button("Insert Stop");
    stopButton.setOnAction(e -> insertCommand("[particles stop]"));
    HBox actions = new HBox(8, insertButton, copyButton, stopButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    Label header = new Label("Particle FX");
    header.getStyleClass().addAll("editor-settings-header", "sidebar-tool-title");
    Label intro = new Label("Build VNS particle commands for rain, snow, petals, fireflies, dust, and leaves.");
    intro.setWrapText(true);
    intro.getStyleClass().add("editor-settings-copy");
    statusLabel.getStyleClass().add("editor-settings-copy");

    StackPane previewPane = new StackPane(previewCanvas);
    previewPane.setMinHeight(PREVIEW_HEIGHT);
    previewPane.setPrefHeight(PREVIEW_HEIGHT);
    previewPane.getStyleClass().add("particle-fx-preview");
    previewCanvas.widthProperty().bind(previewPane.widthProperty());
    previewCanvas.heightProperty().bind(previewPane.heightProperty());
    previewCanvas.widthProperty().addListener((obs, oldValue, newValue) -> resetPreview());
    previewCanvas.heightProperty().addListener((obs, oldValue, newValue) -> resetPreview());

    VBox content = new VBox(12, header, intro, previewPane, controls, commandField, actions, statusLabel);
    content.setPadding(new Insets(12));
    content.getStyleClass().add("editor-settings-content");

    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    setCenter(scroll);

    registerChangeListeners();
    refreshCommand();
    resetPreview();

    previewTimer = new AnimationTimer() {
      @Override
      public void handle(long now) {
        if (lastPreviewNanos == 0L) {
          lastPreviewNanos = now;
          renderPreview(16);
          return;
        }
        long deltaMs = Math.min(48L, Math.max(1L, (now - lastPreviewNanos) / 1_000_000L));
        lastPreviewNanos = now;
        renderPreview(deltaMs);
      }
    };
    sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null) {
        previewTimer.stop();
        lastPreviewNanos = 0L;
      } else {
        resetPreview();
        previewTimer.start();
      }
    });
  }

  public void setOnInsertCommand(Consumer<String> onInsertCommand) {
    this.onInsertCommand = onInsertCommand;
  }

  public void setOnCopyCommand(Consumer<String> onCopyCommand) {
    this.onCopyCommand = onCopyCommand;
  }

  private void registerChangeListeners() {
    presetCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
    intensitySlider.valueProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
    opacitySlider.valueProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
    layerSpinner.valueProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
    speedSpinner.valueProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
    windSpinner.valueProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
    durationSpinner.valueProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
    tintCheck.selectedProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
    tintPicker.valueProperty().addListener((obs, oldValue, newValue) -> refreshCommand());
  }

  private void refreshCommand() {
    commandField.setText(buildStartCommand());
    resetPreview();
  }

  private String buildStartCommand() {
    VnParticleCommand.Preset preset = presetCombo.getValue();
    if (preset == null || preset == VnParticleCommand.Preset.NONE) {
      preset = VnParticleCommand.Preset.RAIN;
    }

    StringBuilder command = new StringBuilder("[particles");
    command.append(" preset=").append(preset.name().toLowerCase(Locale.ROOT));
    command.append(" intensity=").append(formatDouble(intensitySlider.getValue()));
    command.append(" layer=").append(layerSpinner.getValue());
    command.append(" opacity=").append(formatDouble(opacitySlider.getValue()));
    command.append(" speed=").append(formatDouble(speedSpinner.getValue()));
    command.append(" wind=").append(formatDouble(windSpinner.getValue()));
    command.append(" duration=").append(durationSpinner.getValue());
    if (tintCheck.isSelected()) {
      command.append(" tint=").append(toHexRgb(tintPicker.getValue()));
    }
    command.append(']');
    return command.toString();
  }

  private VnParticleCommand buildPreviewCommand() {
    VnParticleCommand.Preset preset = presetCombo.getValue();
    if (preset == null || preset == VnParticleCommand.Preset.NONE) {
      preset = VnParticleCommand.Preset.RAIN;
    }
    VnParticleCommand.Builder builder = VnParticleCommand.builder(preset)
        .intensity((float) intensitySlider.getValue())
        .layer(layerSpinner.getValue())
        .opacity(opacitySlider.getValue())
        .speed(speedSpinner.getValue())
        .wind(windSpinner.getValue())
        .duration(durationSpinner.getValue());
    if (tintCheck.isSelected()) {
      builder.tint(toRgbInt(tintPicker.getValue()));
    }
    return builder.build();
  }

  private void resetPreview() {
    double width = previewWidth();
    double height = previewHeight();
    previewEmitter.clear();
    VnParticlePresetLibrary.apply(previewEmitter, buildPreviewCommand(), width, height);
    int warmupCount = Math.max(12, Math.min(120, (int) Math.round(previewEmitter.getEmissionRate() * 1.5)));
    previewEmitter.burst(warmupCount);
    renderPreview(16);
  }

  private void renderPreview(long deltaMs) {
    double width = previewWidth();
    double height = previewHeight();
    GraphicsContext gc = previewCanvas.getGraphicsContext2D();
    gc.setGlobalAlpha(1.0);
    gc.setEffect(null);
    gc.setFill(Color.web("#07090d"));
    gc.fillRect(0, 0, width, height);
    gc.setFill(Color.web("#111827"));
    gc.fillRoundRect(8, 8, Math.max(0, width - 16), Math.max(0, height - 16), 10, 10);
    gc.setStroke(Color.web("#2b3342"));
    gc.setLineWidth(1.0);
    gc.strokeRoundRect(8.5, 8.5, Math.max(0, width - 17), Math.max(0, height - 17), 10, 10);
    gc.setFill(Color.web("#182234"));
    gc.fillRect(9, Math.max(9, height * 0.68), Math.max(0, width - 18), Math.max(0, height * 0.32 - 9));

    previewEmitter.update(deltaMs);
    previewBlitter.setViewport(width, height);
    previewBlitter.push();
    previewBlitter.translate(previewEmitter.getX(), previewEmitter.getY());
    previewEmitter.render(previewBlitter);
    previewBlitter.pop();
  }

  public void dispose() {
    previewTimer.stop();
    lastPreviewNanos = 0L;
  }

  private void insertCommand(String command) {
    if (onInsertCommand != null) {
      onInsertCommand.accept(command);
      statusLabel.setText("Inserted " + command + ".");
    } else {
      copyToSystemClipboard(command);
      statusLabel.setText("No script editor target. Copied " + command + " instead.");
    }
  }

  private void copyCommand(String command) {
    copyToSystemClipboard(command);
    if (onCopyCommand != null) {
      onCopyCommand.accept(command);
    }
    statusLabel.setText("Copied " + command + ".");
  }

  private static void copyToSystemClipboard(String command) {
    ClipboardContent content = new ClipboardContent();
    content.putString(command == null ? "" : command);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private static Slider unitSlider(double value) {
    Slider slider = new Slider(0.0, 1.0, value);
    slider.setShowTickLabels(true);
    slider.setShowTickMarks(true);
    slider.setMajorTickUnit(0.5);
    slider.setMinorTickCount(4);
    slider.setBlockIncrement(0.05);
    return slider;
  }

  private static Label fieldLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("editor-settings-label");
    return label;
  }

  private static javafx.scene.layout.ColumnConstraints column(double minWidth, Priority priority) {
    javafx.scene.layout.ColumnConstraints column = new javafx.scene.layout.ColumnConstraints();
    column.setMinWidth(minWidth);
    column.setHgrow(priority);
    return column;
  }

  private static String formatDouble(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static String toHexRgb(Color color) {
    Color c = color == null ? Color.WHITE : color;
    return String.format(
        Locale.ROOT,
        "#%02x%02x%02x",
        clampColor(c.getRed()),
        clampColor(c.getGreen()),
        clampColor(c.getBlue()));
  }

  private static int toRgbInt(Color color) {
    Color c = color == null ? Color.WHITE : color;
    return (clampColor(c.getRed()) << 16)
        | (clampColor(c.getGreen()) << 8)
        | clampColor(c.getBlue());
  }

  private double previewWidth() {
    return Math.max(1.0, previewCanvas.getWidth());
  }

  private double previewHeight() {
    return Math.max(1.0, previewCanvas.getHeight());
  }

  private static int clampColor(double value) {
    int v = (int) Math.round(value * 255.0);
    return Math.max(0, Math.min(255, v));
  }
}
