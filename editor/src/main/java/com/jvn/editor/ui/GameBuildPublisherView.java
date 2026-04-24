package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
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
  private final ComboBox<PackageMode> formatBox = new ComboBox<>();
  private final ComboBox<NativeTypeChoice> nativeTypeBox = new ComboBox<>();
  private final ComboBox<String> releaseProfileBox = new ComboBox<>();
  private final Label nativeTypeFieldLabel = new Label("Native Type");
  private final Label manifestLabel = new Label();
  private final Label outputLabel = new Label();
  private final Label validationLabel = new Label();
  private final Label releaseConfigLabel = new Label();
  private final Label buildPlanTitleLabel = new Label();
  private final Label buildPlanBodyLabel = new Label();
  private final Label buildPlanHintLabel = new Label();
  private final Label formatBadgeLabel = new Label();
  private final Label targetBadgeLabel = new Label();
  private final Label runtimeBadgeLabel = new Label();
  private final Label releaseBadgeLabel = new Label();
  private final Label statusLabel = new Label();
  private Button buildSelectedButton;
  private Button buildAllButton;
  private Button releaseButton;
  private Button copyCliButton;
  private Button openProjectButton;
  private Button openReleaseConfigButton;
  private Button revealRuntimeCacheButton;
  private Button clearRuntimeCacheButton;
  private Button presetPortableButton;
  private Button presetDesktopButton;
  private Button presetNativeButton;

  public GameBuildPublisherView(File workspaceRoot, File projectRoot, Consumer<BuildRequest> onBuildRequested) {
    this.workspaceRoot = workspaceRoot;
    this.projectRoot = projectRoot;
    this.onBuildRequested = onBuildRequested;
    getStyleClass().add("build-publisher-root");
    buildUi();
    loadProject(projectRoot);
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    loadProject(projectRoot);
  }

  private void buildUi() {
    Label title = new Label("Build & Publish");
    title.getStyleClass().add("build-publisher-title");
    Label subtitle = new Label("Package and release the current JVN game project as portable zips, cross-target desktop bundles, or host-native installers.");
    subtitle.setWrapText(true);
    subtitle.getStyleClass().add("build-publisher-subtitle");

    Label quickModesLabel = new Label("Quick Modes");
    quickModesLabel.getStyleClass().add("build-publisher-section-label");
    Label quickModesHelp = new Label("Switch the popup to the most common release flows without re-entering the whole plan.");
    quickModesHelp.setWrapText(true);
    quickModesHelp.getStyleClass().add("build-publisher-copy");

    presetPortableButton = button("Current Portable", ButtonTone.SECONDARY, true);
    presetPortableButton.setOnAction(e -> applyPreset(PackageMode.PORTABLE_ZIP, currentTargetToken()));
    presetDesktopButton = button("Desktop Bundles", ButtonTone.PRIMARY, true);
    presetDesktopButton.setOnAction(e -> applyPreset(PackageMode.BUNDLED_RUNTIME_ZIP, "all"));
    presetNativeButton = button("Native Current", ButtonTone.SECONDARY, true);
    presetNativeButton.setOnAction(e -> applyPreset(PackageMode.NATIVE_PACKAGE, currentTargetToken()));

    FlowPane presetRow = new FlowPane(8, 8, presetPortableButton, presetDesktopButton, presetNativeButton);
    presetRow.setAlignment(Pos.CENTER_LEFT);

    VBox titleBlock = new VBox(4, title, subtitle);
    VBox quickModesBlock = new VBox(6, quickModesLabel, quickModesHelp, presetRow);
    VBox header = new VBox(12, titleBlock, quickModesBlock);
    header.getStyleClass().add("build-publisher-header");

    projectField.setEditable(false);
    styleField(projectField);
    nameField.setPromptText("Game title");
    styleField(nameField);
    versionField.setPromptText("0.1.0");
    styleField(versionField);

    targetBox.getItems().setAll(
        new TargetChoice("Current machine", "assembleJvnGamePortableCurrent", currentTargetDescription(), currentTargetToken()),
        new TargetChoice("Windows x64", "assembleJvnGamePortableWindowsX64", "Builds a Windows portable zip.", "windows-x64"),
        new TargetChoice("Linux x64", "assembleJvnGamePortableLinuxX64", "Builds a Linux portable zip.", "linux-x64"),
        new TargetChoice("macOS x64", "assembleJvnGamePortableMacosX64", "Builds an Intel macOS portable zip.", "macos-x64"),
        new TargetChoice("macOS Apple Silicon", "assembleJvnGamePortableMacosAarch64", "Builds an Apple Silicon macOS portable zip.", "macos-aarch64"),
        new TargetChoice("All supported targets", "assembleJvnGamePortable", "Builds Windows x64, Linux x64, macOS x64, and macOS Apple Silicon.", "all")
    );
    targetBox.getSelectionModel().select(0);
    styleField(targetBox);
    targetBox.setMaxWidth(Double.MAX_VALUE);

    formatBox.getItems().setAll(PackageMode.values());
    formatBox.getSelectionModel().select(PackageMode.PORTABLE_ZIP);
    styleField(formatBox);
    formatBox.setMaxWidth(Double.MAX_VALUE);

    nativeTypeBox.getItems().setAll(currentNativeTypeChoices());
    nativeTypeBox.getSelectionModel().selectFirst();
    styleField(nativeTypeBox);
    nativeTypeBox.setMaxWidth(Double.MAX_VALUE);

    releaseProfileBox.setEditable(true);
    styleField(releaseProfileBox);
    releaseProfileBox.setMaxWidth(Double.MAX_VALUE);
    releaseProfileBox.getEditor().setPromptText("default");

    GridPane form = new GridPane();
    form.setHgap(10);
    form.setVgap(10);
    form.getStyleClass().add("build-publisher-form");
    form.add(label("Project"), 0, 0);
    form.add(projectField, 1, 0);
    form.add(label("Game Name"), 0, 1);
    form.add(nameField, 1, 1);
    form.add(label("Version"), 0, 2);
    form.add(versionField, 1, 2);
    form.add(label("Target"), 0, 3);
    form.add(targetBox, 1, 3);
    form.add(label("Format"), 0, 4);
    form.add(formatBox, 1, 4);
    nativeTypeFieldLabel.getStyleClass().add("layout-launcher-field-label");
    form.add(nativeTypeFieldLabel, 0, 5);
    form.add(nativeTypeBox, 1, 5);
    form.add(label("Release Profile"), 0, 6);
    form.add(releaseProfileBox, 1, 6);
    form.getColumnConstraints().add(new javafx.scene.layout.ColumnConstraints(120));
    javafx.scene.layout.ColumnConstraints fill = new javafx.scene.layout.ColumnConstraints();
    fill.setHgrow(Priority.ALWAYS);
    form.getColumnConstraints().add(fill);

    manifestLabel.setWrapText(true);
    manifestLabel.getStyleClass().addAll("build-publisher-meta-line", "build-publisher-copy");
    outputLabel.setWrapText(true);
    outputLabel.getStyleClass().addAll("build-publisher-meta-line", "build-publisher-copy", "build-publisher-path");
    validationLabel.setWrapText(true);
    validationLabel.getStyleClass().addAll("build-publisher-note", "build-publisher-note-status");
    releaseConfigLabel.setWrapText(true);
    releaseConfigLabel.getStyleClass().addAll("build-publisher-meta-line", "build-publisher-copy");
    buildPlanTitleLabel.setWrapText(true);
    buildPlanTitleLabel.getStyleClass().add("build-publisher-plan-title");
    buildPlanBodyLabel.setWrapText(true);
    buildPlanBodyLabel.getStyleClass().add("build-publisher-copy");
    buildPlanHintLabel.setWrapText(true);
    buildPlanHintLabel.getStyleClass().addAll("build-publisher-note", "build-publisher-note-status");
    styleBadge(formatBadgeLabel);
    styleBadge(targetBadgeLabel);
    styleBadge(runtimeBadgeLabel);
    styleBadge(releaseBadgeLabel);
    statusLabel.setWrapText(true);
    statusLabel.getStyleClass().addAll("build-publisher-note", "build-publisher-note-status");

    VBox projectCard = card("Game", form, manifestLabel);

    FlowPane badgeRow = new FlowPane(6, 6, formatBadgeLabel, targetBadgeLabel, runtimeBadgeLabel, releaseBadgeLabel);
    badgeRow.setAlignment(Pos.CENTER_LEFT);

    openProjectButton = button("Open Project", ButtonTone.SECONDARY, false);
    openProjectButton.setOnAction(e -> openProjectFolder());
    openReleaseConfigButton = button("Open Release Config", ButtonTone.SECONDARY, false);
    openReleaseConfigButton.setOnAction(e -> openReleaseConfig());
    revealRuntimeCacheButton = button("Reveal Runtime Cache", ButtonTone.SECONDARY, false);
    revealRuntimeCacheButton.setOnAction(e -> revealRuntimeCache());
    clearRuntimeCacheButton = button("Clear Runtime Cache", ButtonTone.DANGER, false);
    clearRuntimeCacheButton.setOnAction(e -> clearRuntimeCache());

    FlowPane utilitiesRow = new FlowPane(8, 8, openProjectButton, openReleaseConfigButton, revealRuntimeCacheButton, clearRuntimeCacheButton);
    utilitiesRow.setAlignment(Pos.CENTER_LEFT);

    VBox planCard = card("Build Plan", badgeRow, buildPlanTitleLabel, buildPlanBodyLabel, buildPlanHintLabel, outputLabel, validationLabel, releaseConfigLabel, utilitiesRow);

    Label buildHelp = new Label("Portable zips still need Java on the player machine. Desktop bundles include a prebuilt runtime for the selected target, so players do not need Java installed. The first bundle build for a target downloads and caches that runtime locally. Native packages use jpackage and stay host-specific.");
    buildHelp.setWrapText(true);
    buildHelp.getStyleClass().add("build-publisher-copy");

    buildSelectedButton = button("Build Selected", ButtonTone.PRIMARY, false);
    buildSelectedButton.setOnAction(e -> buildSelectedTarget());
    buildAllButton = button("Build All Targets", ButtonTone.PRIMARY, false);
    buildAllButton.setOnAction(e -> buildAllTargets());
    releaseButton = button("Run Release Hooks", ButtonTone.SECONDARY, false);
    releaseButton.setOnAction(e -> runReleaseProfile());
    copyCliButton = button("Copy Build Command", ButtonTone.SECONDARY, false);
    copyCliButton.setOnAction(e -> copyCommand());
    Button reveal = button("Reveal Builds", ButtonTone.SECONDARY, false);
    reveal.setOnAction(e -> revealBuilds());
    Button notes = button("Copy Publish Notes", ButtonTone.SECONDARY, false);
    notes.setOnAction(e -> copyPublishNotes());

    FlowPane buildRow = new FlowPane(8, 8, buildSelectedButton, buildAllButton, releaseButton, copyCliButton, reveal, notes);
    buildRow.setAlignment(Pos.CENTER_LEFT);

    VBox actionCard = card("Actions", buildHelp, buildRow, statusLabel);

    Label nativeNote = new Label("Desktop bundles build locally for Windows, Linux, macOS Intel, and macOS Apple Silicon. Native installers still build on the matching host OS only.");
    nativeNote.setWrapText(true);
    nativeNote.getStyleClass().add("build-publisher-copy");

    HBox topRow = new HBox(14, projectCard, planCard);
    topRow.setAlignment(Pos.TOP_LEFT);
    HBox.setHgrow(projectCard, Priority.ALWAYS);
    HBox.setHgrow(planCard, Priority.ALWAYS);
    projectCard.setMaxWidth(Double.MAX_VALUE);
    planCard.setMaxWidth(Double.MAX_VALUE);

    VBox content = new VBox(14, header, topRow, actionCard, nativeNote);
    content.getStyleClass().add("build-publisher-content");
    content.setFillWidth(true);

    ScrollPane scroller = new ScrollPane(content);
    scroller.setFitToWidth(true);
    scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroller.getStyleClass().add("build-publisher-scroll");
    setCenter(scroller);

    nameField.textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    versionField.textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    targetBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    formatBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    nativeTypeBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    releaseProfileBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
    releaseProfileBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> refreshFormState());
  }

  private Label label(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("layout-launcher-field-label");
    return label;
  }

  private VBox card(String title, Node... nodes) {
    Label label = new Label(title);
    label.getStyleClass().add("build-publisher-card-title");
    VBox box = new VBox(12);
    box.getStyleClass().addAll("build-publisher-card", "layout-launcher-card");
    box.getChildren().add(label);
    box.getChildren().addAll(nodes);
    return box;
  }

  private Button button(String text, ButtonTone tone, boolean pill) {
    Button button = new Button(text);
    button.getStyleClass().add("build-publisher-button");
    switch (tone) {
      case PRIMARY -> button.getStyleClass().add("build-publisher-button-primary");
      case DANGER -> button.getStyleClass().add("build-publisher-button-danger");
      case SECONDARY -> button.getStyleClass().add("build-publisher-button-secondary");
    }
    if (pill) button.getStyleClass().add("build-publisher-button-pill");
    button.setMinHeight(34);
    return button;
  }

  private void styleBadge(Label label) {
    label.getStyleClass().add("build-publisher-badge");
    label.setMinHeight(24);
  }

  private void styleField(TextField field) {
    field.getStyleClass().add("layout-launcher-field");
  }

  private void styleField(ComboBox<?> box) {
    box.getStyleClass().add("layout-launcher-field");
  }

  private void loadProject(File root) {
    projectField.setText(root == null ? "" : root.getAbsolutePath());
    Properties manifest = loadManifest(root);
    releaseProfileBox.getItems().setAll(availableReleaseProfiles(root));
    if (manifest == null) {
      nameField.setText(root == null ? "" : root.getName());
      versionField.setText("0.1.0");
      setReleaseProfileSelection(defaultReleaseProfile(root));
      releaseConfigLabel.setText(releaseConfigText(root));
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
    setReleaseProfileSelection(defaultReleaseProfile(root));
    releaseConfigLabel.setText(releaseConfigText(root));
    manifestLabel.setText("Manifest: type=" + manifest.getProperty("type", "vn")
        + "  " + manifestEntryText(manifest)
        + "  runtime.ui=" + manifest.getProperty("runtime.ui", "fx")
        + "  runtime.audio=" + manifest.getProperty("runtime.audio", "auto"));
    statusLabel.setText("Ready to build " + nameField.getText() + ".");
    refreshFormState();
  }

  private void setReleaseProfileSelection(String profile) {
    String value = profile == null || profile.isBlank() ? "default" : profile.trim();
    if (!releaseProfileBox.getItems().contains(value)) {
      releaseProfileBox.getItems().add(value);
    }
    releaseProfileBox.setValue(value);
    releaseProfileBox.getEditor().setText(value);
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
    refreshBuildPlan(result);
    refreshActionButtons(result);
    refreshUtilityButtons(result);
    return result;
  }

  private void applyValidation(ValidationResult result) {
    PackageMode mode = selectedPackageMode();
    boolean supportsBuildAll = mode != PackageMode.NATIVE_PACKAGE;
    nativeTypeBox.setDisable(mode != PackageMode.NATIVE_PACKAGE);
    nativeTypeBox.setManaged(mode == PackageMode.NATIVE_PACKAGE);
    nativeTypeBox.setVisible(mode == PackageMode.NATIVE_PACKAGE);
    nativeTypeFieldLabel.setManaged(mode == PackageMode.NATIVE_PACKAGE);
    nativeTypeFieldLabel.setVisible(mode == PackageMode.NATIVE_PACKAGE);

    boolean canBuild = result != null && result.errors().isEmpty();
    if (buildSelectedButton != null) buildSelectedButton.setDisable(!canBuild);
    if (buildAllButton != null) buildAllButton.setDisable(!canBuild || !supportsBuildAll);
    if (buildAllButton != null) {
      buildAllButton.setManaged(supportsBuildAll);
      buildAllButton.setVisible(supportsBuildAll);
    }
    boolean canRelease = canBuild && releaseTaskForSelection() != null;
    if (releaseButton != null) releaseButton.setDisable(!canRelease);
    if (copyCliButton != null) copyCliButton.setDisable(!canBuild);

    if (result == null) {
      validationLabel.setText("");
      setNoteTone(validationLabel, "status");
      return;
    }
    if (!result.errors().isEmpty()) {
      validationLabel.setText("Blocked: " + result.errors().get(0));
      setNoteTone(validationLabel, "error");
      statusLabel.setText("Build unavailable: " + result.errors().get(0));
      setNoteTone(statusLabel, "error");
      return;
    }
    if (!result.warnings().isEmpty()) {
      validationLabel.setText("Warning: " + result.warnings().get(0));
      setNoteTone(validationLabel, "warn");
      setNoteTone(statusLabel, "status");
      return;
    }
    validationLabel.setText("Validated: project, manifest, entry file, target, and output folder are ready.");
    setNoteTone(validationLabel, "ok");
    setNoteTone(statusLabel, "status");
  }

  private void refreshBuildPlan(ValidationResult result) {
    PackageMode mode = selectedPackageMode();
    TargetChoice target = targetBox.getValue();
    String targetText = target == null ? "No target selected" : target.label();
    buildPlanTitleLabel.setText(selectionTitle(mode, target));
    buildPlanBodyLabel.setText(selectionBody(mode, target));
    buildPlanHintLabel.setText(selectionHint(mode, target, result));

    formatBadgeLabel.setText(mode.toString());
    targetBadgeLabel.setText(targetText);
    runtimeBadgeLabel.setText(mode == PackageMode.PORTABLE_ZIP ? "Java 21 Required" : "Java Bundled");

    File releaseConfig = findReleaseConfig(projectRoot);
    String profile = selectedReleaseProfile();
    if (mode == PackageMode.NATIVE_PACKAGE && releaseConfig == null) {
      releaseBadgeLabel.setText("Unsigned");
      setBadgeTone(releaseBadgeLabel, "warn");
    } else if (releaseConfig == null) {
      releaseBadgeLabel.setText("No Release Config");
      setBadgeTone(releaseBadgeLabel, "default");
    } else {
      releaseBadgeLabel.setText("Profile " + profile);
      setBadgeTone(releaseBadgeLabel, "accent");
    }

    setBadgeTone(formatBadgeLabel, mode == PackageMode.BUNDLED_RUNTIME_ZIP ? "accent" : "default");
    setBadgeTone(targetBadgeLabel, "all".equals(target == null ? "" : target.outputToken()) ? "accent" : "default");
    if (result != null && !result.errors().isEmpty()) setBadgeTone(runtimeBadgeLabel, "error");
    else if (result != null && !result.warnings().isEmpty()) setBadgeTone(runtimeBadgeLabel, "warn");
    else setBadgeTone(runtimeBadgeLabel, mode == PackageMode.PORTABLE_ZIP ? "default" : "accent");
  }

  private void refreshActionButtons(ValidationResult result) {
    BuildTaskSelection buildSelection = buildTaskForSelection();
    if (buildSelectedButton != null) {
      buildSelectedButton.setText(buildSelection == null ? "Build Selected" : buildActionLabel(buildSelection));
    }
    if (buildAllButton != null) {
      buildAllButton.setText(switch (selectedPackageMode()) {
        case PORTABLE_ZIP -> "Build All Portable Targets";
        case BUNDLED_RUNTIME_ZIP -> "Build All Desktop Bundles";
        case NATIVE_PACKAGE -> "Build All";
      });
    }
    BuildTaskSelection releaseSelection = releaseTaskForSelection();
    if (releaseButton != null) {
      releaseButton.setText(releaseSelection == null ? "Run Release Profile" : releaseActionLabel(releaseSelection));
    }
    if (copyCliButton != null) {
      copyCliButton.setText(result != null && result.errors().isEmpty() ? "Copy Build Command" : "Copy CLI Command");
    }
  }

  private void refreshUtilityButtons(ValidationResult result) {
    if (openProjectButton != null) openProjectButton.setDisable(projectRoot == null || !projectRoot.isDirectory());
    if (openReleaseConfigButton != null) openReleaseConfigButton.setDisable(findReleaseConfig(projectRoot) == null);
    boolean allowCache = workspaceRoot != null && workspaceRoot.isDirectory();
    if (revealRuntimeCacheButton != null) revealRuntimeCacheButton.setDisable(!allowCache);
    if (clearRuntimeCacheButton != null) clearRuntimeCacheButton.setDisable(!allowCache || (result != null && !result.errors().isEmpty() && selectedPackageMode() == PackageMode.NATIVE_PACKAGE));
  }

  private ValidationResult validateForm() {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    PackageMode mode = selectedPackageMode();

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
    } else if (mode == PackageMode.NATIVE_PACKAGE && !"windows-x64".equals(target.outputToken())
        && !"linux-x64".equals(target.outputToken()) && !"macos-x64".equals(target.outputToken())
        && !"macos-aarch64".equals(target.outputToken())) {
      errors.add("Native packages are only available for supported desktop targets.");
    } else if (mode == PackageMode.NATIVE_PACKAGE && !target.outputToken().equals(currentTargetToken())) {
      errors.add("Native packages are host-only. Switch target to Current machine.");
    }

    if (mode == PackageMode.NATIVE_PACKAGE) {
      NativeTypeChoice nativeType = nativeTypeBox.getValue();
      if (nativeType == null) {
        errors.add("Choose a native package type.");
      } else if (nativeType.token().startsWith("unsupported")) {
        errors.add(nativeType.description());
      }
    }

    File outDir = buildDistributionsDir();
    File writableProbe = outDir.exists() ? outDir : outDir.getParentFile();
    if (writableProbe != null && writableProbe.exists() && !writableProbe.canWrite()) {
      errors.add("Build output folder is not writable: " + outDir.getPath());
    }

    String profile = selectedReleaseProfile();
    File releaseConfig = findReleaseConfig(projectRoot);
    if (!profile.isBlank() && !"default".equalsIgnoreCase(profile)) {
      if (releaseConfig == null) {
        errors.add("Release profile '" + profile + "' was requested, but no jvn-release.properties file was found.");
      } else if (!availableReleaseProfiles(projectRoot).contains(profile)) {
        errors.add("Release profile '" + profile + "' was not found in " + releaseConfig.getPath() + ".");
      }
    } else if (releaseConfig == null && mode == PackageMode.NATIVE_PACKAGE) {
      warnings.add("No release profile config found. Native packaging will build unsigned packages without publish commands.");
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
    PackageMode mode = selectedPackageMode();
    String targetId = target == null ? "current-target" : target.outputToken();
    String stem = safeToken(nameField.getText()) + "-" + safeToken(versionField.getText());
    File outDir = buildDistributionsDir();
    switch (mode) {
      case PORTABLE_ZIP -> {
        if (target != null && "all".equals(targetId)) {
          outputLabel.setText("Output: " + new File(outDir, stem + "-*.zip").getPath());
        } else {
          outputLabel.setText("Output: " + new File(outDir, stem + "-" + targetId + ".zip").getPath());
        }
      }
      case BUNDLED_RUNTIME_ZIP -> {
        if (target != null && "all".equals(targetId)) {
          outputLabel.setText("Output: " + new File(outDir, stem + "-*-runtime.zip").getPath());
        } else {
          outputLabel.setText("Output: " + new File(outDir, stem + "-" + targetId + "-runtime.zip").getPath());
        }
      }
      case NATIVE_PACKAGE -> {
        NativeTypeChoice nativeType = nativeTypeBox.getValue();
        String ext = nativeType == null ? ".pkg" : nativeType.artifactSuffix();
        outputLabel.setText("Output: " + new File(outDir,
            safeToken(nameField.getText()) + "-"
                + safeNativeVersionToken(versionField.getText()) + "-"
                + currentTargetToken() + "-"
                + (nativeType == null ? "native" : nativeType.token()) + ext).getPath());
      }
    }
  }

  private void buildSelectedTarget() {
    BuildTaskSelection selection = buildTaskForSelection();
    if (selection == null) return;
    buildTask(selection.taskName(), selection.title());
  }

  private String selectionTitle(PackageMode mode, TargetChoice target) {
    if (target == null) return "Choose a target and format";
    return switch (mode) {
      case PORTABLE_ZIP -> "Portable zip for " + target.label();
      case BUNDLED_RUNTIME_ZIP -> "Desktop bundle for " + target.label();
      case NATIVE_PACKAGE -> {
        NativeTypeChoice nativeType = nativeTypeBox.getValue();
        String nativeLabel = nativeType == null ? "native package" : nativeType.label().toLowerCase(Locale.ROOT);
        yield nativeLabel.substring(0, 1).toUpperCase(Locale.ROOT) + nativeLabel.substring(1) + " for " + target.label();
      }
    };
  }

  private String selectionBody(PackageMode mode, TargetChoice target) {
    if (target == null) return "Pick a build target to see the release plan.";
    return switch (mode) {
      case PORTABLE_ZIP -> "Fastest export path. Good for internal drops and teams that already control the Java runtime.";
      case BUNDLED_RUNTIME_ZIP -> {
        if ("all".equals(target.outputToken())) {
          yield "Builds the full desktop-bundle set so the game is ready to upload for Windows, Linux, and both macOS variants.";
        }
        yield "Self-contained build with a packaged runtime for " + target.label() + ". Players can unzip and launch without installing Java.";
      }
      case NATIVE_PACKAGE -> "Host-native installer path with release-profile support for signing, notarization, and publish hooks.";
    };
  }

  private String selectionHint(PackageMode mode, TargetChoice target, ValidationResult result) {
    if (result != null && !result.errors().isEmpty()) {
      return "Resolve the blocking issue above before starting the build.";
    }
    if (result != null && !result.warnings().isEmpty()) {
      return "Current note: " + result.warnings().get(0);
    }
    if (target == null) return "No target selected.";
    String targetDescription = target.description();
    return switch (mode) {
      case PORTABLE_ZIP -> targetDescription;
      case BUNDLED_RUNTIME_ZIP -> "all".equals(target.outputToken())
          ? "First bundle builds may download and verify runtimes for each target, then reuse the local cache."
          : targetDescription + " The first build for this target downloads and verifies its runtime cache.";
      case NATIVE_PACKAGE -> targetDescription + " Release hooks only run for a single selected artifact.";
    };
  }

  private String buildActionLabel(BuildTaskSelection selection) {
    String title = selection.title();
    if (title.startsWith("Build Game - ")) {
      return title.replace("Build Game - ", "Build ");
    }
    return title;
  }

  private String releaseActionLabel(BuildTaskSelection selection) {
    String title = selection.title();
    if (title.startsWith("Release Game - ")) {
      return title.replace("Release Game - ", "Release ");
    }
    return title;
  }

  private void buildAllTargets() {
    if (!canBuild()) return;
    BuildTaskSelection selection = switch (selectedPackageMode()) {
      case PORTABLE_ZIP -> new BuildTaskSelection("assembleJvnGamePortable", "Build Game - All Portable Targets");
      case BUNDLED_RUNTIME_ZIP -> new BuildTaskSelection("assembleJvnGameBundledRuntime", "Build Game - All Desktop Bundles");
      case NATIVE_PACKAGE -> null;
    };
    if (selection == null) return;
    buildTask(selection.taskName(), selection.title());
  }

  private void runReleaseProfile() {
    if (!canBuild()) return;
    BuildTaskSelection selection = releaseTaskForSelection();
    if (selection == null) {
      statusLabel.setText("Release profile needs a single selected artifact, not an all-target build.");
      setNoteTone(statusLabel, "warn");
      return;
    }
    List<String> args = buildGradleArgs();
    if (onBuildRequested != null) {
      onBuildRequested.accept(new BuildRequest(selection.taskName(), args.toArray(String[]::new), selection.title()));
    }
    statusLabel.setText("Started " + selection.title() + ".");
    setNoteTone(statusLabel, "status");
  }

  private void buildTask(String taskName, String title) {
    if (!canBuild()) return;
    List<String> args = buildGradleArgs();
    if (onBuildRequested != null) {
      onBuildRequested.accept(new BuildRequest(taskName, args.toArray(String[]::new), title));
    }
    statusLabel.setText("Started " + title + ".");
    setNoteTone(statusLabel, "status");
  }

  private boolean canBuild() {
    ValidationResult result = refreshFormState();
    if (result.errors().isEmpty()) return true;
    statusLabel.setText("Build unavailable: " + result.errors().get(0));
    setNoteTone(statusLabel, "error");
    return false;
  }

  private List<String> buildGradleArgs() {
    List<String> args = new ArrayList<>();
    args.add("-PjvnGameProject=" + projectRoot.getAbsolutePath());
    String name = nameField.getText() == null ? "" : nameField.getText().trim();
    if (!name.isBlank()) args.add("-PjvnGameName=" + name);
    String version = versionField.getText() == null ? "" : versionField.getText().trim();
    if (!version.isBlank()) args.add("-PjvnGameVersion=" + version);
    String profile = selectedReleaseProfile();
    if (!profile.isBlank()) args.add("-PjvnReleaseProfile=" + profile);
    if (selectedPackageMode() == PackageMode.NATIVE_PACKAGE) {
      NativeTypeChoice nativeType = nativeTypeBox.getValue();
      if (nativeType != null && !nativeType.token().isBlank() && !nativeType.token().startsWith("unsupported")) {
        args.add("-PjvnNativePackageType=" + nativeType.token());
      }
    }
    return args;
  }

  private void copyCommand() {
    if (!canBuild()) return;
    BuildTaskSelection selection = buildTaskForSelection();
    if (selection == null) return;
    String task = selection.taskName();
    StringBuilder cmd = new StringBuilder("./jvnw gradle ");
    cmd.append(task);
    for (String arg : buildGradleArgs()) {
      cmd.append(' ').append(shellQuote(arg));
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(cmd.toString());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied build command.");
    setNoteTone(statusLabel, "status");
  }

  private void copyPublishNotes() {
    StringBuilder notes = new StringBuilder();
    notes.append("Game: ").append(nameField.getText()).append('\n');
    notes.append("Version: ").append(versionField.getText()).append('\n');
    notes.append("Format: ").append(selectedPackageMode().label).append('\n');
    notes.append("Release profile: ").append(selectedReleaseProfile()).append('\n');
    notes.append("Artifacts: ").append(outputLabel.getText().replace("Output: ", "")).append('\n');
    switch (selectedPackageMode()) {
      case PORTABLE_ZIP -> {
        notes.append("Runtime requirement: Java 21 or newer on the player machine.\n");
        notes.append("Targets: windows-x64, linux-x64, macos-x64, macos-aarch64.\n");
      }
      case BUNDLED_RUNTIME_ZIP -> {
        notes.append("Runtime requirement: bundled target runtime, no external Java install needed.\n");
        notes.append("Targets: windows-x64, linux-x64, macos-x64, macos-aarch64.\n");
        notes.append("Runtime cache: the first build for a target downloads and verifies a prebuilt runtime.\n");
      }
      case NATIVE_PACKAGE -> {
        NativeTypeChoice nativeType = nativeTypeBox.getValue();
        notes.append("Native package type: ").append(nativeType == null ? "(unset)" : nativeType.token()).append('\n');
        notes.append("Runtime requirement: bundled runtime image, no external Java install needed.\n");
      }
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(notes.toString());
    Clipboard.getSystemClipboard().setContent(content);
    statusLabel.setText("Copied publish notes.");
    setNoteTone(statusLabel, "status");
  }

  private void revealBuilds() {
    File outDir = buildDistributionsDir();
    try {
      if (!outDir.exists()) outDir.mkdirs();
      Desktop.getDesktop().open(outDir);
      statusLabel.setText("Opened build output folder.");
      setNoteTone(statusLabel, "status");
    } catch (Exception ex) {
      statusLabel.setText("Could not open build folder: " + ex.getMessage());
      setNoteTone(statusLabel, "error");
    }
  }

  private void openProjectFolder() {
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    try {
      Desktop.getDesktop().open(projectRoot);
      statusLabel.setText("Opened project folder.");
      setNoteTone(statusLabel, "status");
    } catch (Exception ex) {
      statusLabel.setText("Could not open project folder: " + ex.getMessage());
      setNoteTone(statusLabel, "error");
    }
  }

  private void openReleaseConfig() {
    File config = findReleaseConfig(projectRoot);
    if (config == null) {
      statusLabel.setText("No release config file found.");
      setNoteTone(statusLabel, "warn");
      return;
    }
    try {
      Desktop.getDesktop().open(config);
      statusLabel.setText("Opened release config.");
      setNoteTone(statusLabel, "status");
    } catch (Exception ex) {
      statusLabel.setText("Could not open release config: " + ex.getMessage());
      setNoteTone(statusLabel, "error");
    }
  }

  private void revealRuntimeCache() {
    File cacheDir = bundledRuntimeDownloadDir();
    File extractDir = bundledRuntimeExtractDir();
    try {
      if (!cacheDir.exists()) cacheDir.mkdirs();
      if (!extractDir.exists()) extractDir.mkdirs();
      Desktop.getDesktop().open(cacheDir);
      statusLabel.setText("Opened runtime cache folder.");
      setNoteTone(statusLabel, "status");
    } catch (Exception ex) {
      statusLabel.setText("Could not open runtime cache: " + ex.getMessage());
      setNoteTone(statusLabel, "error");
    }
  }

  private void clearRuntimeCache() {
    if (workspaceRoot == null || onBuildRequested == null) return;
    onBuildRequested.accept(new BuildRequest("clearJvnBundledRuntimeCache", new String[0], "Clear Desktop Runtime Cache"));
    statusLabel.setText("Started runtime cache cleanup.");
    setNoteTone(statusLabel, "status");
  }

  private void applyPreset(PackageMode mode, String targetToken) {
    formatBox.getSelectionModel().select(mode);
    selectTargetByToken(targetToken);
    if (mode == PackageMode.NATIVE_PACKAGE) {
      nativeTypeBox.getSelectionModel().selectFirst();
    }
    statusLabel.setText("Preset applied: " + mode + ".");
    setNoteTone(statusLabel, "status");
    refreshFormState();
  }

  private File workspaceBuildDir() {
    return GradleWorkspaceLayout.buildDir(workspaceRoot == null ? null : workspaceRoot.toPath()).toFile();
  }

  private File buildDistributionsDir() {
    return new File(workspaceBuildDir(), "distributions/games");
  }

  private File bundledRuntimeDownloadDir() {
    return new File(workspaceBuildDir(), "downloads/jvnRuntime");
  }

  private File bundledRuntimeExtractDir() {
    return new File(workspaceBuildDir(), "vendor-runtimes");
  }

  private void setNoteTone(Label label, String tone) {
    label.getStyleClass().removeAll(
        "build-publisher-note-ok",
        "build-publisher-note-warn",
        "build-publisher-note-error",
        "build-publisher-note-status");
    label.getStyleClass().add(switch (tone) {
      case "ok" -> "build-publisher-note-ok";
      case "warn" -> "build-publisher-note-warn";
      case "error" -> "build-publisher-note-error";
      default -> "build-publisher-note-status";
    });
  }

  private void setBadgeTone(Label label, String tone) {
    label.getStyleClass().removeAll(
        "build-publisher-badge-accent",
        "build-publisher-badge-warn",
        "build-publisher-badge-error");
    switch (tone) {
      case "accent" -> label.getStyleClass().add("build-publisher-badge-accent");
      case "warn" -> label.getStyleClass().add("build-publisher-badge-warn");
      case "error" -> label.getStyleClass().add("build-publisher-badge-error");
      default -> {
      }
    }
  }

  private void selectTargetByToken(String targetToken) {
    if (targetToken == null || targetToken.isBlank()) return;
    for (TargetChoice choice : targetBox.getItems()) {
      if (targetToken.equals(choice.outputToken())) {
        targetBox.getSelectionModel().select(choice);
        return;
      }
    }
  }

  private PackageMode selectedPackageMode() {
    PackageMode mode = formatBox.getValue();
    return mode == null ? PackageMode.PORTABLE_ZIP : mode;
  }

  private String selectedReleaseProfile() {
    String value = releaseProfileBox.isEditable() ? releaseProfileBox.getEditor().getText() : releaseProfileBox.getValue();
    return value == null || value.trim().isBlank() ? "default" : value.trim();
  }

  private BuildTaskSelection buildTaskForSelection() {
    PackageMode mode = selectedPackageMode();
    TargetChoice target = targetBox.getValue();
    return switch (mode) {
      case PORTABLE_ZIP -> {
        if (target == null) yield null;
        if ("all".equals(target.outputToken())) {
          yield new BuildTaskSelection("assembleJvnGamePortable", "Build Game - All Portable Targets");
        }
        yield new BuildTaskSelection(target.taskName(), "Build Game - " + target.label());
      }
      case BUNDLED_RUNTIME_ZIP -> {
        if (target == null) yield null;
        if ("all".equals(target.outputToken())) {
          yield new BuildTaskSelection("assembleJvnGameBundledRuntime", "Build Game - All Desktop Bundles");
        }
        yield new BuildTaskSelection(
            "assembleJvnGameBundledRuntime" + targetTaskSuffix(target),
            "Build Game - Desktop Bundle - " + target.label());
      }
      case NATIVE_PACKAGE -> {
        NativeTypeChoice nativeType = nativeTypeBox.getValue();
        String label = nativeType == null ? "Native Package" : nativeType.label();
        yield new BuildTaskSelection("packageJvnGameNativeCurrent", "Build Game - " + label);
      }
    };
  }

  private BuildTaskSelection releaseTaskForSelection() {
    return switch (selectedPackageMode()) {
      case PORTABLE_ZIP -> {
        TargetChoice target = targetBox.getValue();
        if (target == null || "all".equals(target.outputToken()) || !target.outputToken().equals(currentTargetToken())) yield null;
        yield new BuildTaskSelection("releaseJvnGamePortableCurrent", "Release Game - Portable Current Host");
      }
      case BUNDLED_RUNTIME_ZIP -> {
        TargetChoice target = targetBox.getValue();
        if (target == null || "all".equals(target.outputToken())) yield null;
        yield new BuildTaskSelection(
            "releaseJvnGameBundledRuntime" + targetTaskSuffix(target),
            "Release Game - Desktop Bundle - " + target.label());
      }
      case NATIVE_PACKAGE ->
          new BuildTaskSelection("releaseJvnGameNativeCurrent", "Release Game - Native Package");
    };
  }

  private File findReleaseConfig(File root) {
    if (root == null) return null;
    File[] candidates = {
        new File(root, "config/release/jvn-release.properties"),
        new File(root, "config/release/release.properties"),
        new File(root, "release/jvn-release.properties"),
        new File(root, "jvn-release.properties")
    };
    for (File candidate : candidates) {
      if (candidate.isFile()) return candidate;
    }
    return null;
  }

  private Properties loadReleaseConfig(File root) {
    File file = findReleaseConfig(root);
    Properties props = new Properties();
    if (file == null) return props;
    try (FileInputStream in = new FileInputStream(file)) {
      props.load(in);
    } catch (Exception ignored) {
      return new Properties();
    }
    return props;
  }

  private List<String> availableReleaseProfiles(File root) {
    Properties props = loadReleaseConfig(root);
    List<String> profiles = new ArrayList<>();
    profiles.add("default");
    for (String key : props.stringPropertyNames()) {
      if (!key.startsWith("profile.")) continue;
      String suffix = key.substring("profile.".length());
      String profile = suffix.contains(".") ? suffix.substring(0, suffix.indexOf('.')) : suffix;
      if (profile.isBlank() || "default".equalsIgnoreCase(profile) || profiles.contains(profile)) continue;
      profiles.add(profile);
    }
    return profiles;
  }

  private String defaultReleaseProfile(File root) {
    Properties props = loadReleaseConfig(root);
    String configured = props.getProperty("defaultProfile", "").trim();
    return configured.isBlank() ? "default" : configured;
  }

  private String releaseConfigText(File root) {
    File config = findReleaseConfig(root);
    if (config == null) return "Release profile config: none";
    List<String> profiles = availableReleaseProfiles(root);
    return "Release profile config: " + config.getPath() + "  profiles=" + String.join(", ", profiles);
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

  private static String safeNativeVersionToken(String value) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(value == null ? "" : value);
    List<String> parts = new ArrayList<>();
    while (matcher.find() && parts.size() < 3) {
      parts.add(matcher.group());
    }
    while (parts.size() < 3) {
      parts.add("0");
    }
    if (parts.isEmpty() || "0".equals(parts.get(0))) {
      parts.set(0, "1");
    }
    return String.join(".", parts);
  }

  private static String targetTaskSuffix(TargetChoice target) {
    if (target == null) return "";
    return switch (target.outputToken()) {
      case "windows-x64" -> "WindowsX64";
      case "linux-x64" -> "LinuxX64";
      case "macos-x64" -> "MacosX64";
      case "macos-aarch64" -> "MacosAarch64";
      default -> "";
    };
  }

  private static String shellQuote(String value) {
    if (value == null || value.isBlank()) return "''";
    if (value.matches("[A-Za-z0-9_./:=@-]+")) return value;
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private static List<NativeTypeChoice> currentNativeTypeChoices() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    List<NativeTypeChoice> types = new ArrayList<>();
    if (os.contains("mac")) {
      types.add(new NativeTypeChoice("DMG Installer", "dmg", ".dmg", "Creates a signed/notarizable macOS disk image."));
      types.add(new NativeTypeChoice("PKG Installer", "pkg", ".pkg", "Creates a macOS installer package."));
      types.add(new NativeTypeChoice("App Image", "app-image", ".zip", "Creates a zipped macOS app bundle."));
      return types;
    }
    if (os.contains("win")) {
      types.add(new NativeTypeChoice("EXE Installer", "exe", ".exe", "Creates a Windows installer executable."));
      types.add(new NativeTypeChoice("MSI Installer", "msi", ".msi", "Creates a Windows MSI installer."));
      types.add(new NativeTypeChoice("App Image", "app-image", ".zip", "Creates a zipped self-contained Windows app image."));
      return types;
    }
    if (os.contains("linux")) {
      types.add(new NativeTypeChoice("DEB Package", "deb", ".deb", "Creates a Debian package for the current Linux host."));
      types.add(new NativeTypeChoice("RPM Package", "rpm", ".rpm", "Creates an RPM package when host tooling is available."));
      types.add(new NativeTypeChoice("App Image", "app-image", ".zip", "Creates a zipped self-contained Linux app image."));
      return types;
    }
    types.add(new NativeTypeChoice("Unsupported", "unsupported-native", ".bin", "Native packaging is not supported on this host OS."));
    return types;
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

  private enum PackageMode {
    PORTABLE_ZIP("Portable Zip"),
    BUNDLED_RUNTIME_ZIP("Desktop Bundle"),
    NATIVE_PACKAGE("Native Package");

    private final String label;

    PackageMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private record ValidationResult(List<String> errors, List<String> warnings) {
    private ValidationResult {
      errors = errors == null ? List.of() : List.copyOf(errors);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  private record BuildTaskSelection(String taskName, String title) {
  }

  private record TargetChoice(String label, String taskName, String description, String outputToken) {
    @Override
    public String toString() {
      return label;
    }
  }

  private record NativeTypeChoice(String label, String token, String artifactSuffix, String description) {
    @Override
    public String toString() {
      return label;
    }
  }

  private enum ButtonTone {
    PRIMARY,
    SECONDARY,
    DANGER
  }
}
