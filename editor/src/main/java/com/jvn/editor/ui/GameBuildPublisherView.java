package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Popup panel for packaging the currently opened JVN game project.
 */
public class GameBuildPublisherView extends BorderPane {
  public record BuildRequest(String taskName, String[] args, String title) {}

  private final File workspaceRoot;
  private File projectRoot;
  private final Consumer<BuildRequest> onBuildRequested;

  private final TextField projectField = new TextField();
  private final TextField nameField = new TextField();
  private final TextField versionField = new TextField();
  private final ComboBox<TargetChoice> targetBox = new ComboBox<>();
  private final Label manifestLabel = new Label();
  private final Label outputLabel = new Label();
  private final Label statusLabel = new Label();

  private static final String STYLE_PANEL = "-fx-background-color: #111; -fx-padding: 18;";
  private static final String STYLE_CARD = "-fx-background-color: #151515; -fx-border-color: #303030; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14;";
  private static final String STYLE_TITLE = "-fx-text-fill: #f2f2f2; -fx-font-size: 20px; -fx-font-weight: bold;";
  private static final String STYLE_SUBTITLE = "-fx-text-fill: #a7a7a7; -fx-font-size: 12px;";
  private static final String STYLE_LABEL = "-fx-text-fill: #d8d8d8; -fx-font-size: 12px; -fx-font-weight: bold;";
  private static final String STYLE_HELP = "-fx-text-fill: #9d9d9d; -fx-font-size: 12px;";
  private static final String STYLE_FIELD = "-fx-background-color: #0f0f0f; -fx-text-fill: #f2f2f2; -fx-prompt-text-fill: #777; -fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-background-radius: 6;";
  private static final String STYLE_BUTTON = "-fx-background-color: #202020; -fx-text-fill: #f0f0f0; -fx-border-color: #444; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12;";
  private static final String STYLE_ACCENT = "-fx-background-color: #2f6f4e; -fx-text-fill: #f4fff7; -fx-border-color: #5dbb83; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12; -fx-font-weight: bold;";
  private static final String STYLE_STATUS = "-fx-text-fill: #f2c26b; -fx-font-size: 12px;";

  public GameBuildPublisherView(File workspaceRoot, File projectRoot, Consumer<BuildRequest> onBuildRequested) {
    this.workspaceRoot = workspaceRoot;
    this.projectRoot = projectRoot;
    this.onBuildRequested = onBuildRequested;
    setStyle(STYLE_PANEL);
    buildUi();
    loadProject(projectRoot);
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    loadProject(projectRoot);
  }

  private void buildUi() {
    Label title = new Label("Build & Publish");
    title.setStyle(STYLE_TITLE);
    Label subtitle = new Label("Package the current JVN game project into portable runtime archives.");
    subtitle.setWrapText(true);
    subtitle.setStyle(STYLE_SUBTITLE);

    VBox header = new VBox(4, title, subtitle);

    projectField.setEditable(false);
    projectField.setStyle(STYLE_FIELD);
    nameField.setPromptText("Game title");
    nameField.setStyle(STYLE_FIELD);
    versionField.setPromptText("0.1.0");
    versionField.setStyle(STYLE_FIELD);

    targetBox.getItems().setAll(
        new TargetChoice("Current machine", "assembleJvnGamePortableCurrent", "Builds only the OS/arch this editor is running on.", currentTargetToken()),
        new TargetChoice("Windows x64", "assembleJvnGamePortableWindowsX64", "Builds a Windows portable zip.", "windows-x64"),
        new TargetChoice("Linux x64", "assembleJvnGamePortableLinuxX64", "Builds a Linux portable zip.", "linux-x64"),
        new TargetChoice("macOS x64", "assembleJvnGamePortableMacosX64", "Builds an Intel macOS portable zip.", "macos-x64"),
        new TargetChoice("macOS Apple Silicon", "assembleJvnGamePortableMacosAarch64", "Builds an Apple Silicon macOS portable zip.", "macos-aarch64"),
        new TargetChoice("All supported targets", "assembleJvnGamePortable", "Builds Windows x64, Linux x64, macOS x64, and macOS Apple Silicon.", "all")
    );
    targetBox.getSelectionModel().select(0);
    targetBox.setStyle(STYLE_FIELD);
    targetBox.setMaxWidth(Double.MAX_VALUE);

    GridPane form = new GridPane();
    form.setHgap(10);
    form.setVgap(10);
    form.add(label("Project"), 0, 0);
    form.add(projectField, 1, 0);
    form.add(label("Game Name"), 0, 1);
    form.add(nameField, 1, 1);
    form.add(label("Version"), 0, 2);
    form.add(versionField, 1, 2);
    form.add(label("Target"), 0, 3);
    form.add(targetBox, 1, 3);
    form.getColumnConstraints().add(new javafx.scene.layout.ColumnConstraints(120));
    javafx.scene.layout.ColumnConstraints fill = new javafx.scene.layout.ColumnConstraints();
    fill.setHgrow(Priority.ALWAYS);
    form.getColumnConstraints().add(fill);

    manifestLabel.setWrapText(true);
    manifestLabel.setStyle(STYLE_HELP);
    outputLabel.setWrapText(true);
    outputLabel.setStyle(STYLE_HELP);
    statusLabel.setWrapText(true);
    statusLabel.setStyle(STYLE_STATUS);

    VBox projectCard = card("Game", form, manifestLabel, outputLabel);

    Label buildHelp = new Label("The archive contains the JVN runtime, JavaFX native jars for the target, and a bundled game folder copied from the selected project.");
    buildHelp.setWrapText(true);
    buildHelp.setStyle(STYLE_HELP);

    Button buildSelected = button("Build Selected Target", true);
    buildSelected.setOnAction(e -> buildSelectedTarget());
    Button buildAll = button("Build All Targets", true);
    buildAll.setOnAction(e -> buildTask("assembleJvnGamePortable", "Build Game - All Targets"));
    Button copyCli = button("Copy CLI Command", false);
    copyCli.setOnAction(e -> copyCommand());
    Button reveal = button("Reveal Builds", false);
    reveal.setOnAction(e -> revealBuilds());
    Button notes = button("Copy Publish Notes", false);
    notes.setOnAction(e -> copyPublishNotes());

    HBox buildRow = new HBox(8, buildSelected, buildAll, copyCli, reveal, notes);
    buildRow.setAlignment(Pos.CENTER_LEFT);
    buildRow.setFillHeight(false);

    VBox actionCard = card("Actions", buildHelp, buildRow, statusLabel);

    Label nativeNote = new Label("Native installers and bundled JRE images are still a later pass. This popup produces portable zip builds that require Java 21 on the player machine.");
    nativeNote.setWrapText(true);
    nativeNote.setStyle(STYLE_HELP);

    VBox content = new VBox(14, header, projectCard, actionCard, nativeNote);
    content.setFillWidth(true);
    setCenter(content);

    nameField.textProperty().addListener((obs, oldValue, newValue) -> refreshOutputPreview());
    versionField.textProperty().addListener((obs, oldValue, newValue) -> refreshOutputPreview());
    targetBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshOutputPreview());
  }

  private Label label(String text) {
    Label label = new Label(text);
    label.setStyle(STYLE_LABEL);
    return label;
  }

  private VBox card(String title, javafx.scene.Node... nodes) {
    Label label = new Label(title);
    label.setStyle("-fx-text-fill: #f2f2f2; -fx-font-size: 14px; -fx-font-weight: bold;");
    VBox box = new VBox(10);
    box.setStyle(STYLE_CARD);
    box.getChildren().add(label);
    box.getChildren().addAll(nodes);
    return box;
  }

  private Button button(String text, boolean accent) {
    Button button = new Button(text);
    button.setStyle(accent ? STYLE_ACCENT : STYLE_BUTTON);
    button.setMinHeight(34);
    return button;
  }

  private void loadProject(File root) {
    projectField.setText(root == null ? "" : root.getAbsolutePath());
    Properties manifest = loadManifest(root);
    if (manifest == null) {
      nameField.setText(root == null ? "" : root.getName());
      versionField.setText("0.1.0");
      manifestLabel.setText("No jvn.project found. Choose a JVN game project before building.");
      statusLabel.setText("Build unavailable: missing jvn.project.");
      refreshOutputPreview();
      return;
    }

    String name = manifest.getProperty("name", root == null ? "JVN Game" : root.getName()).trim();
    String version = firstNonBlank(
        manifest.getProperty("version"),
        manifest.getProperty("releaseVersion"),
        manifest.getProperty("build.version"),
        "0.1.0");
    nameField.setText(name.isBlank() ? root.getName() : name);
    versionField.setText(version);
    manifestLabel.setText("Manifest: type=" + manifest.getProperty("type", "vn")
        + "  entryVns=" + manifest.getProperty("entryVns", "(auto)")
        + "  runtime.ui=" + manifest.getProperty("runtime.ui", "fx")
        + "  runtime.audio=" + manifest.getProperty("runtime.audio", "auto"));
    statusLabel.setText("Ready to build " + nameField.getText() + ".");
    refreshOutputPreview();
  }

  private Properties loadManifest(File root) {
    if (root == null) return null;
    File manifest = new File(root, "jvn.project");
    if (!manifest.isFile()) return null;
    try (FileInputStream in = new FileInputStream(manifest)) {
      Properties props = new Properties();
      props.load(in);
      return props;
    } catch (Exception ignored) {
      return null;
    }
  }

  private void refreshOutputPreview() {
    TargetChoice target = targetBox.getValue();
    String targetId = target == null ? "current-target" : target.outputToken();
    String stem = safeToken(nameField.getText()) + "-" + safeToken(versionField.getText());
    File outDir = new File(workspaceRoot == null ? new File(".") : workspaceRoot, "build/distributions/games");
    if (target != null && "all".equals(targetId)) {
      outputLabel.setText("Output: " + new File(outDir, stem + "-*.zip").getPath());
    } else {
      outputLabel.setText("Output: " + new File(outDir, stem + "-" + targetId + ".zip").getPath());
    }
  }

  private void buildSelectedTarget() {
    TargetChoice target = targetBox.getValue();
    if (target == null) return;
    buildTask(target.taskName(), "Build Game - " + target.label());
  }

  private void buildTask(String taskName, String title) {
    if (!canBuild()) return;
    List<String> args = buildGradleArgs();
    if (onBuildRequested != null) {
      onBuildRequested.accept(new BuildRequest(taskName, args.toArray(String[]::new), title));
    }
    statusLabel.setText("Started " + title + ".");
  }

  private boolean canBuild() {
    if (workspaceRoot == null || !workspaceRoot.isDirectory()) {
      statusLabel.setText("Build unavailable: JVN workspace root was not found.");
      return false;
    }
    if (projectRoot == null || !projectRoot.isDirectory()) {
      statusLabel.setText("Build unavailable: open a JVN game project first.");
      return false;
    }
    if (!new File(projectRoot, "jvn.project").isFile()) {
      statusLabel.setText("Build unavailable: selected project has no jvn.project.");
      return false;
    }
    return true;
  }

  private List<String> buildGradleArgs() {
    List<String> args = new ArrayList<>();
    args.add("-PjvnGameProject=" + projectRoot.getAbsolutePath());
    String name = nameField.getText() == null ? "" : nameField.getText().trim();
    if (!name.isBlank()) args.add("-PjvnGameName=" + name);
    String version = versionField.getText() == null ? "" : versionField.getText().trim();
    if (!version.isBlank()) args.add("-PjvnGameVersion=" + version);
    return args;
  }

  private void copyCommand() {
    if (!canBuild()) return;
    TargetChoice target = targetBox.getValue();
    String task = target == null ? "assembleJvnGamePortableCurrent" : target.taskName();
    StringBuilder cmd = new StringBuilder("./jvnw gradle ");
    cmd.append(task);
    for (String arg : buildGradleArgs()) {
      cmd.append(' ').append(shellQuote(arg));
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(cmd.toString());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied build command.");
  }

  private void copyPublishNotes() {
    StringBuilder notes = new StringBuilder();
    notes.append("Game: ").append(nameField.getText()).append('\n');
    notes.append("Version: ").append(versionField.getText()).append('\n');
    notes.append("Artifacts: build/distributions/games/")
        .append(safeToken(nameField.getText()))
        .append('-')
        .append(safeToken(versionField.getText()))
        .append("-*.zip\n");
    notes.append("Runtime requirement: Java 21 or newer.\n");
    notes.append("Targets: windows-x64, linux-x64, macos-x64, macos-aarch64.\n");
    ClipboardContent content = new ClipboardContent();
    content.putString(notes.toString());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied publish notes.");
  }

  private void revealBuilds() {
    File outDir = new File(workspaceRoot == null ? new File(".") : workspaceRoot, "build/distributions/games");
    try {
      if (!outDir.exists()) outDir.mkdirs();
      Desktop.getDesktop().open(outDir);
      statusLabel.setText("Opened build output folder.");
    } catch (Exception ex) {
      statusLabel.setText("Could not open build folder: " + ex.getMessage());
    }
  }

  private static String firstNonBlank(String... values) {
    if (values != null) {
      for (String value : values) {
        if (value != null && !value.trim().isBlank()) return value.trim();
      }
    }
    return "";
  }

  private static String safeToken(String value) {
    String sanitized = (value == null ? "" : value.trim())
        .replaceAll("[^A-Za-z0-9._-]+", "-")
        .replaceAll("^[._-]+|[._-]+$", "");
    return sanitized.isBlank() ? "jvn-game" : sanitized;
  }

  private static String shellQuote(String value) {
    if (value == null || value.isBlank()) return "''";
    if (value.matches("[A-Za-z0-9_./:=@-]+")) return value;
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private static String currentTargetToken() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) return "windows-x64";
    if (os.contains("linux")) return "linux-x64";
    if (os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"))) return "macos-aarch64";
    if (os.contains("mac")) return "macos-x64";
    return "current-target";
  }

  private record TargetChoice(String label, String taskName, String description, String outputToken) {
    @Override
    public String toString() {
      return label;
    }
  }
}
