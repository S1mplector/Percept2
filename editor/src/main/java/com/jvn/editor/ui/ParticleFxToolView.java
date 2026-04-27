package com.jvn.editor.ui;

import java.util.Locale;
import java.util.function.Consumer;

import com.jvn.core.vn.VnParticleCommand;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class ParticleFxToolView extends BorderPane {
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

  private Consumer<String> onInsertCommand;
  private Consumer<String> onCopyCommand;

  public ParticleFxToolView() {
    getStyleClass().add("particle-fx-tool-view");

    presetCombo.getItems().addAll(
        VnParticleCommand.Preset.SNOW,
        VnParticleCommand.Preset.RAIN,
        VnParticleCommand.Preset.SAKURA,
        VnParticleCommand.Preset.FIREFLIES,
        VnParticleCommand.Preset.DUST,
        VnParticleCommand.Preset.LEAVES);
    presetCombo.getSelectionModel().select(VnParticleCommand.Preset.RAIN);
    presetCombo.setMaxWidth(Double.MAX_VALUE);

    layerSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-1000, 1000, 100, 10));
    speedSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 10.0, 1.0, 0.05));
    windSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(-1000.0, 1000.0, 0.0, 5.0));
    durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 600000, 0, 500));
    layerSpinner.setEditable(true);
    speedSpinner.setEditable(true);
    windSpinner.setEditable(true);
    durationSpinner.setEditable(true);

    commandField.setEditable(false);
    commandField.getStyleClass().add("particle-fx-command-field");
    HBox.setHgrow(commandField, Priority.ALWAYS);

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
    header.getStyleClass().add("editor-settings-header");
    Label intro = new Label("Build VNS particle commands for rain, snow, petals, fireflies, dust, and leaves.");
    intro.setWrapText(true);
    intro.getStyleClass().add("editor-settings-copy");
    statusLabel.getStyleClass().add("editor-settings-copy");

    VBox content = new VBox(12, header, intro, controls, commandField, actions, statusLabel);
    content.setPadding(new Insets(12));
    content.getStyleClass().add("editor-settings-content");

    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    setCenter(scroll);

    registerChangeListeners();
    refreshCommand();
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

  private void insertCommand(String command) {
    if (onInsertCommand != null) {
      onInsertCommand.accept(command);
      statusLabel.setText("Inserted " + command + ".");
    }
  }

  private void copyCommand(String command) {
    if (onCopyCommand != null) {
      onCopyCommand.accept(command);
      statusLabel.setText("Copied " + command + ".");
    }
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

  private static int clampColor(double value) {
    int v = (int) Math.round(value * 255.0);
    return Math.max(0, Math.min(255, v));
  }
}
