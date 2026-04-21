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
  private final Label validationLabel = new Label();
  private final Label statusLabel = new Label();
  private Button buildSelectedButton;
  private Button buildAllButton;
  private Button copyCliButton;

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
  private static final String STYLE_OK = "-fx-text-fill: #8fd694; -fx-font-size: 12px;";
  private static final String STYLE_WARN = "-fx-text-fill: #f2c26b; -fx-font-size: 12px;";
  private static final String STYLE_ERROR = "-fx-text-fill: #ff8a8a; -fx-font-size: 12px;";

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
        new TargetChoice("Current machine", "assembleJvnGamePortableCurrent", currentTargetDescription(), currentTargetToken()),
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
    validationLabel.setWrapText(true);
    validationLabel.setStyle(STYLE_HELP);
    statusLabel.setWrapText(true);
    statusLabel.setStyle(STYLE_STATUS);

    VBox projectCard = card("Game", form, manifestLabel, outputLabel, validationLabel);

    Label buildHelp = new Label("The archive contains the JVN runtime, JavaFX native jars for the target, and a bundled game folder copied from the selected project.");
    buildHelp.setWrapText(true);
    buildHelp.setStyle(STYLE_HELP);

    buildSelectedButton = button("Build Selected Target", true);
    buildSelectedButton.setOnAction(e -> buildSelectedTarget());
    buildAllButton = button("Build All Targets", true);
    buildAllButton.setOnAction(e -> buildTask("assembleJvnGamePortable", "Build Game - All Targets"));
    copyCliButton = button("Copy CLI Command", false);
    copyCliButton.setOnAction(e -> copyCommand());
    Button reveal = button("Reveal Builds", false);
    reveal.setOnAction(e -> revealBuilds());
    Button notes = button("Copy Publish Notes", false);
    notes.setOnAction(e -> copyPublishNotes());

    HBox buildRow = new HBox(8, buildSelectedButton, buildAllButton, copyCliButton, reveal, notes);
    buildRow.setAlignment(Pos.CENTER_LEFT);
    buildRow.setFillHeight(false);

    VBox actionCard = card("Actions", buildHelp, buildRow, statusLabel);

    Label nativeNote = new Label("Portable zip builds support VN and JES game projects. Native installers and bundled JRE images are still a later pass, so players need Java 21 on their machine.");
    nativeNote.setWrapText(true);
    nativeNote.setStyle(STYLE_HELP);

    VBox content = new VBox(14, header, projectCard, actionCard, nativeNote);
    content.setFillWidth(true);
    setCenter(content);

    nameField.textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    versionField.textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    targetBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
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
      refreshFormState();
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
        + "  " + manifestEntryText(manifest)
        + "  runtime.ui=" + manifest.getProperty("runtime.ui", "fx")
        + "  runtime.audio=" + manifest.getProperty("runtime.audio", "auto"));
    statusLabel.setText("Ready to build " + nameField.getText() + ".");
    refreshFormState();
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

  private String manifestEntryText(Properties manifest) {
    String type = manifest.getProperty("type", "vn").trim().toLowerCase(Locale.ROOT);
    if ("jes".equals(type)) {
      return "entry=" + manifest.getProperty("entry", "scripts/main.jes");
    }
    return "entryVns=" + manifest.getProperty("entryVns", "(auto)");
  }

  private ValidationResult refreshFormState() {
    refreshOutputPreview();
    ValidationResult result = validateForm();
    applyValidation(result);
    return result;
  }

  private void applyValidation(ValidationResult result) {
    boolean canBuild = result != null && result.errors().isEmpty();
    if (buildSelectedButton != null) buildSelectedButton.setDisable(!canBuild);
    if (buildAllButton != null) buildAllButton.setDisable(!canBuild);
    if (copyCliButton != null) copyCliButton.setDisable(!canBuild);

    if (result == null) {
      validationLabel.setText("");
      validationLabel.setStyle(STYLE_HELP);
      return;
    }
    if (!result.errors().isEmpty()) {
      validationLabel.setText("Blocked: " + result.errors().get(0));
      validationLabel.setStyle(STYLE_ERROR);
      statusLabel.setText("Build unavailable: " + result.errors().get(0));
      return;
    }
    if (!result.warnings().isEmpty()) {
      validationLabel.setText("Warning: " + result.warnings().get(0));
      validationLabel.setStyle(STYLE_WARN);
      return;
    }
    validationLabel.setText("Validated: project, manifest, entry file, target, and output folder are ready.");
    validationLabel.setStyle(STYLE_OK);
  }

  private ValidationResult validateForm() {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    if (workspaceRoot == null || !workspaceRoot.isDirectory()) {
      errors.add("JVN workspace root was not found.");
    }
    if (projectRoot == null || !projectRoot.isDirectory()) {
      errors.add("Open a JVN game project first.");
    } else {
      if (sameCanonical(projectRoot, workspaceRoot)) {
        errors.add("Selected project is the JVN engine workspace, not a game project.");
      }
      if (!projectRoot.getName().equals(projectRoot.getName().trim())) {
        warnings.add("Project folder name has leading or trailing spaces; the build preserves it, but CLI paths are easy to mistype.");
      }
      Properties manifest = loadManifest(projectRoot);
      if (manifest == null) {
        errors.add("Selected project has no readable jvn.project.");
      } else {
        validateManifest(projectRoot, manifest, errors, warnings);
      }
    }

    TargetChoice target = targetBox.getValue();
    if (target == null) {
      errors.add("Choose a build target.");
    } else if (target.outputToken().startsWith("unsupported")) {
      errors.add(target.description());
    }

    File outDir = new File(workspaceRoot == null ? new File(".") : workspaceRoot, "build/distributions/games");
    File writableProbe = outDir.exists() ? outDir : outDir.getParentFile();
    if (writableProbe != null && writableProbe.exists() && !writableProbe.canWrite()) {
      errors.add("Build output folder is not writable: " + outDir.getPath());
    }

    return new ValidationResult(errors, warnings);
  }

  private void validateManifest(File root, Properties manifest, List<String> errors, List<String> warnings) {
    String type = manifest.getProperty("type", "vn").trim().toLowerCase(Locale.ROOT);
    if (type.isBlank()) type = "vn";
    switch (type) {
      case "vn" -> {
        String entry = normalizeScriptKey(manifest.getProperty("entryVns"));
        if (entry == null) {
          File discovered = discoverScript(root, "vns");
          if (discovered == null) {
            errors.add("No VN entry script could be resolved. Set entryVns or add a .vns file under scripts/.");
          } else {
            warnings.add("entryVns is not set; runtime will use discovered script " + relativeTo(root, discovered) + ".");
          }
        } else if (resolveScriptFile(root, entry) == null) {
          errors.add("Configured entryVns is missing: " + manifest.getProperty("entryVns"));
        }
      }
      case "jes" -> {
        String entry = normalizeProjectPath(manifest.getProperty("entry", "scripts/main.jes"));
        if (entry == null) {
          errors.add("JES projects must define entry=<path-to-jes> in jvn.project.");
        } else if (resolveScriptFile(root, entry) == null) {
          errors.add("Configured JES entry is missing: " + entry);
        }
      }
      case "gradle" -> errors.add("type=gradle describes a workspace run command, not a distributable game package.");
      default -> errors.add("Unsupported jvn.project type for packaging: " + type + ". Supported types: vn, jes.");
    }

    if (!new File(root, "scripts").isDirectory() && !new File(root, "game/scripts").isDirectory()) {
      warnings.add("No scripts/ or game/scripts/ directory was found.");
    }
    if (!new File(root, "assets").isDirectory() && !new File(root, "game").isDirectory()) {
      warnings.add("No assets/ or game/ directory was found; package may be script-only.");
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
    ValidationResult result = refreshFormState();
    if (result.errors().isEmpty()) return true;
    statusLabel.setText("Build unavailable: " + result.errors().get(0));
    return false;
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

  private static boolean sameCanonical(File a, File b) {
    if (a == null || b == null) return false;
    try {
      return a.getCanonicalFile().equals(b.getCanonicalFile());
    } catch (Exception ignored) {
      return a.getAbsoluteFile().equals(b.getAbsoluteFile());
    }
  }

  private static File resolveScriptFile(File root, String raw) {
    if (root == null) return null;
    String normalized = normalizeProjectPath(raw);
    if (normalized == null) return null;
    String scriptKey = normalizeScriptKey(normalized);
    if (scriptKey == null) scriptKey = normalized;

    List<File> candidates = new ArrayList<>();
    addCandidate(candidates, new File(root, normalized));
    addCandidate(candidates, new File(root, scriptKey));
    addCandidate(candidates, new File(root, "scripts/" + scriptKey));
    addCandidate(candidates, new File(root, "game/scripts/" + scriptKey));
    if (normalized.startsWith("game/") && !normalized.startsWith("game/scripts/")) {
      addCandidate(candidates, new File(root, "scripts/" + normalized.substring("game/".length())));
    }

    for (File candidate : candidates) {
      if (candidate.isFile()) return candidate;
    }
    return null;
  }

  private static void addCandidate(List<File> candidates, File candidate) {
    if (candidate != null && !candidates.contains(candidate)) {
      candidates.add(candidate);
    }
  }

  private static File discoverScript(File root, String extension) {
    if (root == null) return null;
    File scripts = new File(root, "scripts");
    if (!scripts.isDirectory()) scripts = new File(root, "game/scripts");
    if (!scripts.isDirectory()) return null;
    File[] files = scripts.listFiles();
    if (files == null) return null;
    List<File> matches = new ArrayList<>();
    collectScripts(scripts, extension.startsWith(".") ? extension : "." + extension, matches);
    matches.sort((a, b) -> scoreScript(a.getName()) == scoreScript(b.getName())
        ? a.getPath().compareToIgnoreCase(b.getPath())
        : Integer.compare(scoreScript(a.getName()), scoreScript(b.getName())));
    return matches.isEmpty() ? null : matches.get(0);
  }

  private static void collectScripts(File dir, String extension, List<File> out) {
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file.isDirectory()) {
        collectScripts(file, extension, out);
      } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
        out.add(file);
      }
    }
  }

  private static int scoreScript(String name) {
    String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (lower.equals("prologue.vns") || lower.equals("prologue.jes")) return 0;
    if (lower.equals("main.vns") || lower.equals("main.jes")) return 1;
    if (lower.equals("start.vns") || lower.equals("start.jes")) return 2;
    if (lower.contains("prologue")) return 10;
    if (lower.contains("start")) return 11;
    if (lower.contains("main")) return 12;
    return 100;
  }

  private static String relativeTo(File root, File file) {
    if (root == null || file == null) return "";
    try {
      return root.toPath().toAbsolutePath().normalize()
          .relativize(file.toPath().toAbsolutePath().normalize())
          .toString()
          .replace('\\', '/');
    } catch (Exception ignored) {
      return file.getPath();
    }
  }

  private static String normalizeProjectPath(String raw) {
    if (raw == null) return null;
    String value = raw.trim().replace('\\', '/');
    if (value.isBlank()) return null;
    while (value.startsWith("./")) value = value.substring(2);
    while (value.startsWith("/")) value = value.substring(1);
    return value.isBlank() ? null : value;
  }

  private static String normalizeScriptKey(String raw) {
    String value = normalizeProjectPath(raw);
    if (value == null) return null;
    if (value.startsWith("game/scripts/")) value = value.substring("game/scripts/".length());
    if (value.startsWith("scripts/")) value = value.substring("scripts/".length());
    return value.isBlank() ? null : value;
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

  private static String currentTargetDescription() {
    String token = currentTargetToken();
    if ("unsupported-linux-aarch64".equals(token)) {
      return "Linux aarch64 portable builds are not supported by the current JavaFX runtime classifiers. Choose a specific supported target instead.";
    }
    if (token.startsWith("unsupported")) {
      return "This host OS/arch is not supported as a current-machine portable target. Choose a specific supported target instead.";
    }
    return "Builds only the OS/arch this editor is running on.";
  }

  private static String currentTargetToken() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) return "windows-x64";
    if (os.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64"))) return "unsupported-linux-aarch64";
    if (os.contains("linux")) return "linux-x64";
    if (os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"))) return "macos-aarch64";
    if (os.contains("mac")) return "macos-x64";
    return "unsupported-current";
  }

  private record ValidationResult(List<String> errors, List<String> warnings) {
    private ValidationResult {
      errors = errors == null ? List.of() : List.copyOf(errors);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  private record TargetChoice(String label, String taskName, String description, String outputToken) {
    @Override
    public String toString() {
      return label;
    }
  }
}
