package com.jvn.editor.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.jvn.editor.vcs.GitVcsService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Project creation wizard for VN projects.
 * Keeps setup compact and aligned with the current engine/editor workflow.
 */
public class NewProjectWizard extends Stage {

  // Result
  private File createdProjectDir = null;
  private final boolean gitAvailable;

  // Form fields
  private TextField txtProjectName;
  private TextField txtAuthor;
  private TextField txtLocation;
  private ComboBox<String> cmbResolution;
  private ComboBox<String> cmbTheme;
  private CheckBox chkCustomResolution;
  private TextField txtCustomWidth;
  private TextField txtCustomHeight;
  private Label lblAspectRatio;
  private ComboBox<String> cmbRuntimeUi;
  private ComboBox<String> cmbAudioBackend;
  private ComboBox<String> cmbLocale;
  private Spinner<Integer> spTextSpeed;
  private Spinner<Double> spBgmVolume;
  private Spinner<Double> spSfxVolume;
  private Spinner<Double> spVoiceVolume;
  private Spinner<Integer> spAutoDelay;
  private CheckBox chkSkipUnreadDefault;
  private CheckBox chkSkipAfterChoicesDefault;
  private Spinner<Integer> spPhysicsFixedStep;
  private Spinner<Integer> spPhysicsMaxSubsteps;
  private Spinner<Double> spPhysicsFriction;
  private TextField txtInputProfilePath;
  private CheckBox chkSampleContent;
  private CheckBox chkBundledDemoAssets;
  private CheckBox chkTitleScreen;
  private CheckBox chkSaveSystem;
  private CheckBox chkSettingsMenu;
  private CheckBox chkHistoryBacklog;
  private CheckBox chkBlankMenus;
  private CheckBox chkGitInit;
  private CheckBox chkInitialCommit;
  private Label lblBlankMenuWarning;
  private TextArea txtDescription;
  private TextArea txtStructurePreview;
  private Label lblPreview;
  private Label lblTargetPath;
  private Label lblEstimatedSize;

  // Theme colors
  private static final String BG_DARK = "#0f0f10";
  private static final String BG_CARD = "#17181a";
  private static final String BG_FIELD = "#222326";
  private static final String BG_MONO = "#141518";
  private static final String ACCENT = "#4a9eff";
  private static final String TEXT_PRIMARY = "#f1f3f4";
  private static final String TEXT_SECONDARY = "#9aa0a6";
  private static final String TEXT_MUTED = "#7f858b";

  // Project paths
  private static final String ENTRY_SCRIPT_PATH = "scripts/story/prologue.vns";
  private static final String TIMELINE_PATH = "config/timeline/story.timeline";
  private static final String SETTINGS_PATH = "config/settings/vn.settings";
  private static final String DIALOGUE_LAYOUT_PATH = "config/ui/dialogue.layout";
  private static final String MENU_THEME_PATH = "config/menu/theme/menu.theme";
  private static final String MENU_REGISTRY_PATH = "config/menu/registry/menu.registry";
  private static final String MENU_MAIN_PATH = "config/menu/menus/main.menu";
  private static final String MENU_EXTRAS_PATH = "config/menu/menus/extras.menu";
  private static final String MENU_CREDITS_PATH = "config/menu/menus/credits.menu";
  private static final String MENU_CONFIRM_EXIT_PATH = "config/menu/menus/confirm_exit.menu";
  private static final String MENU_LOAD_PATH = "config/menu/menus/load.menu";
  private static final String MENU_SAVE_PATH = "config/menu/menus/save.menu";
  private static final String MENU_SETTINGS_PATH = "config/menu/menus/settings.menu";
  private static final String MENU_LAYOUT_DEFAULT_PATH = "config/menu/layouts/default.layout";
  private static final String MENU_LAYOUT_SUBMENU_PATH = "config/menu/layouts/submenu.layout";
  private static final String MENU_LAYOUT_SLOTS_PATH = "config/menu/layouts/slots.layout";
  private static final String MENU_STYLE_DEFAULT_PATH = "config/menu/styles/default.style";
  private static final String MENU_STYLE_SUBMENU_PATH = "config/menu/styles/submenu.style";
  private static final String MENU_STYLE_SLOT_PATH = "config/menu/styles/slot.style";
  private static final String DEFAULT_MENU_BG_ASSET_PATH = "assets/demo/backgrounds/field/glorious_ricefield_day.png";
  private static final String BUNDLED_DEMO_ASSETS_DIR = "demo-assets";
  private static final String BUNDLED_DEMO_BG_DIR = "demo_bg_field";
  private static final String BUNDLED_DEMO_SPRITE_LAYERED_DIR = "Lavender_test_sprite";
  private static final String BUNDLED_DEMO_SPRITE_LEGACY_DIR = "demo_sprite_codel";
  private static final String BUNDLED_DEMO_BGM_DIR = "demo_bgm";

  public NewProjectWizard(Stage owner) {
    initOwner(owner);
    initModality(Modality.APPLICATION_MODAL);
    initStyle(StageStyle.DECORATED);
    setTitle("Create New Visual Novel Project");
    setWidth(920);
    setHeight(760);
    setMinWidth(860);
    setMinHeight(680);
    setResizable(true);
    gitAvailable = new GitVcsService().isGitAvailable();

    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: " + BG_DARK + ";");

    VBox header = createHeader();
    root.setTop(header);

    ScrollPane scrollPane = new ScrollPane(createMainContent());
    scrollPane.setFitToWidth(true);
    scrollPane.setStyle("-fx-background: " + BG_DARK + "; -fx-background-color: " + BG_DARK + ";");
    root.setCenter(scrollPane);

    HBox footer = createFooter();
    root.setBottom(footer);

    Scene scene = new Scene(root);
    EditorTheme.apply(scene);
    setScene(scene);

    updateDerivedFields();
  }

  private VBox createHeader() {
    VBox header = new VBox(8);
    header.setPadding(new Insets(20, 28, 14, 28));
    header.setStyle("-fx-background-color: " + BG_CARD + ";");

    Label title = new Label("Create New Visual Novel");
    title.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 24));
    title.setTextFill(Color.web(TEXT_PRIMARY));

    Label subtitle = new Label("Set up a clean engine-ready project structure with scripts, config, and visual editor files.");
    subtitle.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.NORMAL, 13));
    subtitle.setTextFill(Color.web(TEXT_SECONDARY));

    Label hint = new Label("All settings can be changed later in the editor.");
    hint.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.NORMAL, 11));
    hint.setTextFill(Color.web(TEXT_MUTED));

    header.getChildren().addAll(title, subtitle, hint);
    return header;
  }

  private VBox createMainContent() {
    VBox content = new VBox(16);
    content.setPadding(new Insets(18, 28, 18, 28));

    content.getChildren().addAll(
        createSection("Project Basics", "Name, author, target directory, and output path.", createProjectBasicsGrid()),
        createSection("Engine Profile", "Runtime defaults and entry points for this project.", createEngineProfileGrid()),
        createSection("Playback Defaults", "Initial text/audio/physics/input settings for this project profile.", createPlaybackDefaultsPane()),
        createSection("Feature Modules", "Choose the base modules to scaffold.", createFeatureModulesPane()),
        createSection("Version Control", "Initialize Git so multi-person collaboration works from day one.", createVersionControlPane()),
        createSection("Generated Layout", "Preview the exact folders/files that will be created.", createGeneratedLayoutPane()),
        createSection("Project Notes", "Optional description saved to the project manifest and README.", createDescriptionArea())
    );

    return content;
  }

  private VBox createSection(String title, String subtitle, Region content) {
    VBox section = new VBox(10);
    section.setPadding(new Insets(14));
    section.setStyle("-fx-background-color: " + BG_CARD + "; -fx-background-radius: 8;");

    Label titleLabel = new Label(title);
    titleLabel.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.SEMI_BOLD, 16));
    titleLabel.setTextFill(Color.web(ACCENT));

    Label subtitleLabel = new Label(subtitle);
    subtitleLabel.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.NORMAL, 12));
    subtitleLabel.setTextFill(Color.web(TEXT_SECONDARY));

    Separator sep = new Separator();

    section.getChildren().addAll(titleLabel, subtitleLabel, sep, content);
    return section;
  }

  private Region createProjectBasicsGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(14);
    grid.setVgap(10);

    txtProjectName = createTextField("My Visual Novel");
    txtAuthor = createTextField("Anonymous");
    txtLocation = createTextField(System.getProperty("user.home") + "/JVN Projects");
    txtLocation.setPrefWidth(440);

    txtProjectName.textProperty().addListener((o, ov, nv) -> updateDerivedFields());
    txtLocation.textProperty().addListener((o, ov, nv) -> updateDerivedFields());

    Button btnBrowse = new Button("Browse...");
    btnBrowse.setOnAction(e -> browseLocation());
    btnBrowse.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");

    HBox locationRow = new HBox(8, txtLocation, btnBrowse);
    HBox.setHgrow(txtLocation, Priority.ALWAYS);

    lblTargetPath = new Label();
    lblTargetPath.setWrapText(true);
    lblTargetPath.setTextFill(Color.web(TEXT_SECONDARY));
    lblTargetPath.setFont(Font.font("Consolas", 11));

    Label slugHint = new Label("Folder name is sanitized automatically for cross-platform safety.");
    slugHint.setTextFill(Color.web(TEXT_MUTED));
    slugHint.setFont(Font.font(Font.getDefault().getFamily(), 11));

    grid.add(createLabel("Project Name"), 0, 0);
    grid.add(txtProjectName, 1, 0);
    grid.add(createLabel("Author"), 0, 1);
    grid.add(txtAuthor, 1, 1);
    grid.add(createLabel("Location"), 0, 2);
    grid.add(locationRow, 1, 2);
    grid.add(createLabel("Output Path"), 0, 3);
    grid.add(lblTargetPath, 1, 3);
    grid.add(slugHint, 1, 4);

    GridPane.setHgrow(txtProjectName, Priority.ALWAYS);
    GridPane.setHgrow(txtAuthor, Priority.ALWAYS);
    GridPane.setHgrow(locationRow, Priority.ALWAYS);

    return grid;
  }

  private Region createEngineProfileGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(14);
    grid.setVgap(10);

    cmbResolution = new ComboBox<>();
    cmbResolution.getItems().addAll(
        "7680x4320 (8K UHD)",
        "5120x2880 (5K)",
        "5120x1440 (Super UltraWide)",
        "3840x2160 (4K UHD)",
        "3440x1440 (UltraWide QHD)",
        "2560x1440 (QHD)",
        "2560x1080 (UltraWide FHD)",
        "1920x1080 (Full HD)",
        "1600x900 (HD+)",
        "1366x768 (WXGA)",
        "1280x720 (HD)",
        "960x540 (qHD)"
    );
    cmbResolution.setValue("1920x1080 (Full HD)");
    cmbResolution.setPrefWidth(230);
    cmbResolution.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");

    chkCustomResolution = createCheckBox("Custom Resolution", false);
    txtCustomWidth = createTextField("2560");
    txtCustomWidth.setPrefWidth(90);
    txtCustomHeight = createTextField("1440");
    txtCustomHeight.setPrefWidth(90);
    txtCustomWidth.setDisable(true);
    txtCustomHeight.setDisable(true);
    Label resolutionSeparator = new Label("x");
    resolutionSeparator.setTextFill(Color.web(TEXT_SECONDARY));
    HBox customRow = new HBox(8, chkCustomResolution, txtCustomWidth, resolutionSeparator, txtCustomHeight);
    customRow.setAlignment(Pos.CENTER_LEFT);

    lblAspectRatio = new Label();
    lblAspectRatio.setTextFill(Color.web(TEXT_MUTED));
    lblAspectRatio.setFont(Font.font(Font.getDefault().getFamily(), 11));

    cmbTheme = new ComboBox<>();
    cmbTheme.getItems().addAll(
        "Dark Elegant",
        "Light Clean",
        "Retro Game",
        "Nature Green",
        "Custom"
    );
    cmbTheme.setValue("Dark Elegant");
    cmbTheme.setPrefWidth(230);
    cmbTheme.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");

    cmbRuntimeUi = new ComboBox<>();
    cmbRuntimeUi.getItems().addAll("fx", "swing");
    cmbRuntimeUi.setValue("fx");
    cmbRuntimeUi.setPrefWidth(230);
    cmbRuntimeUi.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");

    cmbAudioBackend = new ComboBox<>();
    cmbAudioBackend.getItems().addAll("auto", "simp3", "fx");
    cmbAudioBackend.setValue("auto");
    cmbAudioBackend.setPrefWidth(230);
    cmbAudioBackend.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");

    cmbLocale = new ComboBox<>();
    cmbLocale.getItems().addAll("en", "de", "es", "fr", "it", "ja", "ko", "pt-BR", "tr", "zh-CN");
    cmbLocale.setValue("en");
    cmbLocale.setPrefWidth(230);
    cmbLocale.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");

    cmbResolution.setOnAction(e -> updateDerivedFields());
    cmbTheme.setOnAction(e -> updateDerivedFields());
    cmbRuntimeUi.setOnAction(e -> updateDerivedFields());
    cmbAudioBackend.setOnAction(e -> updateDerivedFields());
    cmbLocale.setOnAction(e -> updateDerivedFields());
    chkCustomResolution.selectedProperty().addListener((o, ov, nv) -> {
      boolean custom = nv != null && nv;
      txtCustomWidth.setDisable(!custom);
      txtCustomHeight.setDisable(!custom);
      cmbResolution.setDisable(custom);
      updateDerivedFields();
    });
    txtCustomWidth.textProperty().addListener((o, ov, nv) -> updateDerivedFields());
    txtCustomHeight.textProperty().addListener((o, ov, nv) -> updateDerivedFields());

    lblPreview = new Label();
    lblPreview.setTextFill(Color.web(TEXT_SECONDARY));
    lblPreview.setFont(Font.font(Font.getDefault().getFamily(), 12));

    Label entryInfo = new Label(
        "Entry script: " + ENTRY_SCRIPT_PATH + "\n" +
        "Timeline file: " + TIMELINE_PATH + "\n" +
        "Dialogue layout: " + DIALOGUE_LAYOUT_PATH + "\n" +
        "Menu registry: " + MENU_REGISTRY_PATH
    );
    entryInfo.setTextFill(Color.web(TEXT_MUTED));
    entryInfo.setFont(Font.font("Consolas", 11));

    grid.add(createLabel("Resolution"), 0, 0);
    grid.add(cmbResolution, 1, 0);
    grid.add(createLabel("Custom"), 0, 1);
    grid.add(customRow, 1, 1);
    grid.add(createLabel("Aspect"), 0, 2);
    grid.add(lblAspectRatio, 1, 2);
    grid.add(createLabel("Menu Theme"), 0, 3);
    grid.add(cmbTheme, 1, 3);
    grid.add(createLabel("Runtime UI"), 0, 4);
    grid.add(cmbRuntimeUi, 1, 4);
    grid.add(createLabel("Audio Backend"), 0, 5);
    grid.add(cmbAudioBackend, 1, 5);
    grid.add(createLabel("Locale"), 0, 6);
    grid.add(cmbLocale, 1, 6);
    grid.add(createLabel("Preset"), 0, 7);
    grid.add(lblPreview, 1, 7);
    grid.add(new Label(""), 0, 8);
    grid.add(entryInfo, 1, 8);

    return grid;
  }

  private Region createPlaybackDefaultsPane() {
    GridPane grid = new GridPane();
    grid.setHgap(14);
    grid.setVgap(10);

    spTextSpeed = createIntSpinner(10, 120, 35, 1);
    spAutoDelay = createIntSpinner(500, 5000, 2000, 100);
    spBgmVolume = createDoubleSpinner(0.0, 1.0, 0.70, 0.05);
    spSfxVolume = createDoubleSpinner(0.0, 1.0, 0.80, 0.05);
    spVoiceVolume = createDoubleSpinner(0.0, 1.0, 1.00, 0.05);
    chkSkipUnreadDefault = createCheckBox("Skip unread text by default", false);
    chkSkipAfterChoicesDefault = createCheckBox("Skip after choices by default", false);
    spPhysicsFixedStep = createIntSpinner(0, 50, 0, 5);
    spPhysicsMaxSubsteps = createIntSpinner(1, 8, 4, 1);
    spPhysicsFriction = createDoubleSpinner(0.0, 1.0, 0.20, 0.05);
    txtInputProfilePath = createTextField(System.getProperty("user.home") + "/.jvn/input-bindings.properties");

    Label note = new Label(
        "These defaults are written to config/settings/vn.settings and can be changed later in Settings Editor.\n"
        + "Physics fixed step: 0 means variable timestep."
    );
    note.setWrapText(true);
    note.setTextFill(Color.web(TEXT_MUTED));
    note.setFont(Font.font(Font.getDefault().getFamily(), 11));

    grid.add(createLabel("Text Speed"), 0, 0);
    grid.add(spTextSpeed, 1, 0);
    grid.add(createLabel("Auto Delay"), 0, 1);
    grid.add(spAutoDelay, 1, 1);
    grid.add(createLabel("BGM Volume"), 0, 2);
    grid.add(spBgmVolume, 1, 2);
    grid.add(createLabel("SFX Volume"), 0, 3);
    grid.add(spSfxVolume, 1, 3);
    grid.add(createLabel("Voice Volume"), 0, 4);
    grid.add(spVoiceVolume, 1, 4);
    grid.add(createLabel("Skip Defaults"), 0, 5);
    grid.add(new VBox(6, chkSkipUnreadDefault, chkSkipAfterChoicesDefault), 1, 5);
    grid.add(createLabel("Physics Step"), 0, 6);
    grid.add(spPhysicsFixedStep, 1, 6);
    grid.add(createLabel("Physics Substeps"), 0, 7);
    grid.add(spPhysicsMaxSubsteps, 1, 7);
    grid.add(createLabel("Default Friction"), 0, 8);
    grid.add(spPhysicsFriction, 1, 8);
    grid.add(createLabel("Input Profile"), 0, 9);
    grid.add(txtInputProfilePath, 1, 9);
    grid.add(new Label(""), 0, 10);
    grid.add(note, 1, 10);

    GridPane.setHgrow(txtInputProfilePath, Priority.ALWAYS);
    return grid;
  }

  private Region createFeatureModulesPane() {
    VBox box = new VBox(10);

    Label intro = new Label("These options control both scaffolding and starter content.");
    intro.setTextFill(Color.web(TEXT_SECONDARY));
    intro.setFont(Font.font(Font.getDefault().getFamily(), 12));

    chkSampleContent = createCheckBox("Sample Prologue Script", true);
    chkBundledDemoAssets = createCheckBox("Bundled Demo Assets (Lavender/Field/BGM)", true);
    chkTitleScreen = createCheckBox("Main Menu Profile Pack", true);
    chkSaveSystem = createCheckBox("Load/Save Menu Profiles", true);
    chkSettingsMenu = createCheckBox("Settings Menu Profile", true);
    chkHistoryBacklog = createCheckBox("History/Backlog Defaults", true);
    chkBlankMenus = createCheckBox("Start from Zero (Custom Menus)", false);

    lblBlankMenuWarning = new Label(
        "Recommended for projects with extensive custom menu plans or assets.\n"
        + "Until menu layouts are configured and wired in the registry, the following\n"
        + "in-game GUI features will be unavailable:\n"
        + "  \u2022 Save Game / Load Game menus\n"
        + "  \u2022 Rollback / state restore from menus\n"
        + "  \u2022 Settings menu\n"
        + "  \u2022 Main menu / title screen navigation\n"
        + "  \u2022 In-game pause overlay\n"
        + "Game progress can only be viewed through VNS file preview until menus are set up."
    );
    lblBlankMenuWarning.setWrapText(true);
    lblBlankMenuWarning.setTextFill(Color.web("#e8a840"));
    lblBlankMenuWarning.setFont(Font.font(Font.getDefault().getFamily(), 11));
    lblBlankMenuWarning.setPadding(new Insets(8, 12, 8, 12));
    lblBlankMenuWarning.setStyle("-fx-background-color: #2a2210; -fx-background-radius: 6; -fx-border-color: #5c4a1a; -fx-border-radius: 6;");
    lblBlankMenuWarning.setVisible(false);
    lblBlankMenuWarning.setManaged(false);

    chkSampleContent.selectedProperty().addListener((o, ov, nv) -> {
      if (nv != null && nv && chkBundledDemoAssets != null && !chkBundledDemoAssets.isSelected()) {
        chkBundledDemoAssets.setSelected(true);
      }
      updateDerivedFields();
    });
    chkBundledDemoAssets.selectedProperty().addListener((o, ov, nv) -> {
      if (nv != null && !nv && chkSampleContent != null && chkSampleContent.isSelected()) {
        chkSampleContent.setSelected(false);
      }
      updateDerivedFields();
    });
    chkTitleScreen.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
    chkHistoryBacklog.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
    chkSaveSystem.selectedProperty().addListener((o, ov, nv) -> {
      if (nv != null && nv && !chkTitleScreen.isSelected()) chkTitleScreen.setSelected(true);
      updateDerivedFields();
    });
    chkSettingsMenu.selectedProperty().addListener((o, ov, nv) -> {
      if (nv != null && nv && !chkTitleScreen.isSelected()) chkTitleScreen.setSelected(true);
      updateDerivedFields();
    });
    chkBlankMenus.selectedProperty().addListener((o, ov, nv) -> {
      boolean blank = nv != null && nv;
      chkTitleScreen.setDisable(blank);
      chkSaveSystem.setDisable(blank);
      chkSettingsMenu.setDisable(blank);
      if (blank) {
        chkTitleScreen.setSelected(false);
        chkSaveSystem.setSelected(false);
        chkSettingsMenu.setSelected(false);
      }
      lblBlankMenuWarning.setVisible(blank);
      lblBlankMenuWarning.setManaged(blank);
      updateDerivedFields();
    });

    GridPane options = new GridPane();
    options.setHgap(28);
    options.setVgap(8);
    options.add(chkSampleContent, 0, 0);
    options.add(chkBundledDemoAssets, 1, 0);
    options.add(chkTitleScreen, 0, 1);
    options.add(chkSaveSystem, 1, 1);
    options.add(chkSettingsMenu, 0, 2);
    options.add(chkHistoryBacklog, 1, 2);
    options.add(chkBlankMenus, 0, 3);

    FlowPane details = new FlowPane();
    details.setVgap(4);
    details.setHgap(16);
    details.getChildren().addAll(
        detailTag("Sample Prologue", "Rich starter VNS with choices and state."),
        detailTag("Demo Assets", "Copies bundled field/lavender/audio starter assets."),
        detailTag("Menu Profiles", "Creates config/menu registry, screens, layout and style."),
        detailTag("Save/Load", "Adds load.menu and save.menu defaults."),
        detailTag("Settings", "Adds settings.menu profile entries."),
        detailTag("History Defaults", "Marks backlog defaults in vn.settings."),
        detailTag("Blank Menus", "No default menus. Build everything from scratch.")
    );

    box.getChildren().addAll(intro, options, lblBlankMenuWarning, details);
    return box;
  }

  private Region detailTag(String title, String subtitle) {
    VBox tag = new VBox(2);
    tag.setPadding(new Insets(8, 10, 8, 10));
    tag.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-background-radius: 6;");

    Label t = new Label(title);
    t.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.SEMI_BOLD, 11));
    t.setTextFill(Color.web(TEXT_PRIMARY));

    Label s = new Label(subtitle);
    s.setFont(Font.font(Font.getDefault().getFamily(), 10));
    s.setTextFill(Color.web(TEXT_MUTED));

    tag.getChildren().addAll(t, s);
    return tag;
  }

  private Region createVersionControlPane() {
    VBox box = new VBox(10);

    Label intro = new Label(
        "Optional: enable Git initialization for team workflows. " +
        "If disabled (or unavailable), project creation is unaffected."
    );
    intro.setWrapText(true);
    intro.setTextFill(Color.web(TEXT_SECONDARY));
    intro.setFont(Font.font(Font.getDefault().getFamily(), 12));

    chkGitInit = createCheckBox("Initialize Git repository", false);
    chkInitialCommit = createCheckBox("Create initial commit", false);

    if (!gitAvailable) {
      chkGitInit.setSelected(false);
      chkInitialCommit.setSelected(false);
      chkGitInit.setDisable(true);
      chkInitialCommit.setDisable(true);
    }

    chkGitInit.selectedProperty().addListener((o, ov, nv) -> {
      boolean enabled = nv != null && nv;
      chkInitialCommit.setDisable(!enabled);
      if (!enabled) {
        chkInitialCommit.setSelected(false);
      } else {
        if (!chkInitialCommit.isSelected()) chkInitialCommit.setSelected(true);
      }
      updateDerivedFields();
    });

    chkInitialCommit.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());

    Label note = new Label(
        gitAvailable
            ? "Default ignore patterns are optimized for JVN projects and generated runtime files."
            : "Git is not detected on PATH. You can still create and run projects normally."
    );
    note.setWrapText(true);
    note.setTextFill(Color.web(TEXT_MUTED));
    note.setFont(Font.font(Font.getDefault().getFamily(), 11));

    box.getChildren().addAll(intro, chkGitInit, chkInitialCommit, note);
    return box;
  }

  private Region createGeneratedLayoutPane() {
    VBox box = new VBox(8);

    txtStructurePreview = new TextArea();
    txtStructurePreview.setEditable(false);
    txtStructurePreview.setWrapText(false);
    txtStructurePreview.setPrefRowCount(16);
    txtStructurePreview.setStyle(
        "-fx-control-inner-background: " + BG_MONO + ";" +
        "-fx-font-family: 'Consolas';" +
        "-fx-font-size: 11px;" +
        "-fx-text-fill: " + TEXT_PRIMARY + ";"
    );

    Label note = new Label("This preview updates live based on your selected modules.");
    note.setTextFill(Color.web(TEXT_MUTED));
    note.setFont(Font.font(Font.getDefault().getFamily(), 11));

    box.getChildren().addAll(txtStructurePreview, note);
    return box;
  }

  private Region createDescriptionArea() {
    VBox box = new VBox(8);

    Label info = new Label("Optional project description:");
    info.setTextFill(Color.web(TEXT_SECONDARY));
    info.setFont(Font.font(Font.getDefault().getFamily(), 12));

    txtDescription = new TextArea();
    txtDescription.setPromptText("Example: A sci-fi mystery told across branching routes.");
    txtDescription.setPrefRowCount(3);
    txtDescription.setWrapText(true);
    txtDescription.setStyle(
        "-fx-control-inner-background: " + BG_FIELD + ";" +
        "-fx-text-fill: " + TEXT_PRIMARY + ";"
    );

    box.getChildren().addAll(info, txtDescription);
    return box;
  }

  private HBox createFooter() {
    HBox footer = new HBox(12);
    footer.setPadding(new Insets(14, 28, 18, 28));
    footer.setAlignment(Pos.CENTER_RIGHT);
    footer.setStyle("-fx-background-color: " + BG_CARD + ";");

    lblEstimatedSize = new Label();
    lblEstimatedSize.setTextFill(Color.web(TEXT_SECONDARY));
    lblEstimatedSize.setFont(Font.font(Font.getDefault().getFamily(), 11));

    Button btnCancel = new Button("Cancel");
    btnCancel.setPrefWidth(110);
    btnCancel.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");
    btnCancel.setOnAction(e -> close());

    Button btnCreate = new Button("Create Project");
    btnCreate.setPrefWidth(150);
    btnCreate.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-font-weight: bold;");
    btnCreate.setOnAction(e -> createProject());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    footer.getChildren().addAll(lblEstimatedSize, spacer, btnCancel, btnCreate);
    return footer;
  }

  private Label createLabel(String text) {
    Label label = new Label(text);
    label.setTextFill(Color.web(TEXT_PRIMARY));
    label.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.SEMI_BOLD, 12));
    label.setMinWidth(110);
    return label;
  }

  private TextField createTextField(String defaultValue) {
    TextField tf = new TextField(defaultValue);
    tf.setPrefWidth(280);
    tf.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");
    return tf;
  }

  private CheckBox createCheckBox(String text, boolean selected) {
    CheckBox cb = new CheckBox(text);
    cb.setSelected(selected);
    cb.setTextFill(Color.web(TEXT_PRIMARY));
    cb.setFont(Font.font(Font.getDefault().getFamily(), 12));
    return cb;
  }

  private void browseLocation() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Choose Project Location");
    File initial = new File(txtLocation.getText().trim());
    if (initial.exists() && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File dir = chooser.showDialog(this);
    if (dir != null) {
      txtLocation.setText(dir.getAbsolutePath());
      updateDerivedFields();
    }
  }

  private void updateDerivedFields() {
    updatePresetPreview();
    updateTargetPathLabel();
    updateStructurePreview();
    updateEstimatedSize();
  }

  private void updatePresetPreview() {
    if (lblPreview == null || cmbTheme == null || cmbResolution == null) return;
    String name = txtProjectName == null ? "" : txtProjectName.getText().trim();
    if (name.isBlank()) name = "Untitled";
    int[] resolution = parseResolution();
    String res = resolution[0] + "x" + resolution[1];
    String ratio = formatAspectRatio(resolution[0], resolution[1]);
    String theme = cmbTheme.getValue() == null ? "Dark Elegant" : cmbTheme.getValue();
    String runtimeUi = cmbRuntimeUi == null || cmbRuntimeUi.getValue() == null ? "fx" : cmbRuntimeUi.getValue();
    String audioBackend = cmbAudioBackend == null || cmbAudioBackend.getValue() == null ? "auto" : cmbAudioBackend.getValue();
    String locale = cmbLocale == null || cmbLocale.getValue() == null ? "en" : cmbLocale.getValue();
    String source = chkCustomResolution != null && chkCustomResolution.isSelected() ? "custom" : "preset";
    if (lblAspectRatio != null) {
      lblAspectRatio.setText(ratio + " (" + source + ")");
    }
    lblPreview.setText("\"" + name + "\" • " + res + " • " + ratio + " • " + theme
        + " • " + runtimeUi + "/" + audioBackend + " • " + locale);
  }

  private void updateTargetPathLabel() {
    if (lblTargetPath == null) return;
    String location = txtLocation == null ? "" : txtLocation.getText().trim();
    String projectName = txtProjectName == null ? "" : txtProjectName.getText().trim();
    String sanitized = sanitizeName(projectName);
    if (location.isBlank()) {
      lblTargetPath.setText("(select a location)");
      return;
    }
    if (sanitized.isBlank()) {
      lblTargetPath.setText(new File(location).getAbsolutePath());
      return;
    }
    lblTargetPath.setText(new File(location, sanitized).getAbsolutePath());
  }

  private void updateStructurePreview() {
    if (txtStructurePreview == null) return;
    String folderName = sanitizeName(txtProjectName == null ? "" : txtProjectName.getText().trim());
    if (folderName.isBlank()) folderName = "my_visual_novel";
    txtStructurePreview.setText(buildStructurePreviewText(folderName));
  }

  private void updateEstimatedSize() {
    if (lblEstimatedSize == null) return;
    lblEstimatedSize.setText("Estimated scaffold size: ~" + estimateProjectSizeKb() + " KB");
  }

  private long estimateProjectSizeKb() {
    long kb = 36;
    if (chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected()) {
      kb += estimateBundledDemoAssetsKb();
    }
    if (chkSampleContent != null && chkSampleContent.isSelected()) kb += 8;
    if (shouldCreateMenuPack()) kb += 8;
    if (chkSaveSystem != null && chkSaveSystem.isSelected()) kb += 3;
    if (chkSettingsMenu != null && chkSettingsMenu.isSelected()) kb += 2;
    if (chkHistoryBacklog != null && chkHistoryBacklog.isSelected()) kb += 1;
    if (shouldSetupGit()) kb += 1;
    return kb;
  }

  private String buildStructurePreviewText(String projectFolderName) {
    boolean includeMenuPack = shouldCreateMenuPack();
    boolean includeSave = chkSaveSystem != null && chkSaveSystem.isSelected();
    boolean includeSettings = chkSettingsMenu != null && chkSettingsMenu.isSelected();
    boolean includeDemoAssets = chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected();

    StringBuilder sb = new StringBuilder();
    sb.append(projectFolderName).append("/\n");
    sb.append("|-- config/\n");
    sb.append("|   |-- settings/\n");
    sb.append("|   |   `-- vn.settings\n");
    sb.append("|   |-- timeline/\n");
    sb.append("|   |   `-- story.timeline\n");
    sb.append("|   |-- ui/\n");
    sb.append("|   |   `-- dialogue.layout\n");
    boolean blankMenus = shouldStartBlankMenus();
    if (includeMenuPack) {
      sb.append("|   `-- menu/\n");
      sb.append("|       |-- registry/\n");
      sb.append("|       |   `-- menu.registry\n");
      sb.append("|       |-- theme/\n");
      sb.append("|       |   `-- menu.theme\n");
      sb.append("|       |-- menus/\n");
      sb.append("|       |   |-- main.menu\n");
      sb.append("|       |   |-- extras.menu\n");
      sb.append("|       |   |-- credits.menu\n");
      sb.append("|       |   |-- confirm_exit.menu\n");
      if (includeSave) {
        sb.append("|       |   |-- load.menu\n");
        sb.append("|       |   `-- save.menu\n");
      }
      if (includeSettings) {
        sb.append("|       |   `-- settings.menu\n");
      }
      sb.append("|       |-- layouts/\n");
      sb.append("|       |   |-- default.layout\n");
      sb.append("|       |   |-- submenu.layout\n");
      sb.append("|       |   `-- slots.layout\n");
      sb.append("|       |-- styles/\n");
      sb.append("|       |   |-- default.style\n");
      sb.append("|       |   |-- submenu.style\n");
      sb.append("|       |   `-- slot.style\n");
      sb.append("|       `-- assets/\n");
      sb.append("|           |-- buttons/\n");
      sb.append("|           `-- icons/\n");
    } else if (blankMenus) {
      sb.append("|   `-- menu/                    (blank - build from scratch)\n");
      sb.append("|       |-- registry/\n");
      sb.append("|       |   `-- menu.registry    (empty)\n");
      sb.append("|       |-- menus/               (add .menu files here)\n");
      sb.append("|       |-- layouts/             (add .layout files here)\n");
      sb.append("|       |-- styles/              (add .style files here)\n");
      sb.append("|       `-- assets/\n");
      sb.append("|           |-- buttons/\n");
      sb.append("|           `-- icons/\n");
    }
    sb.append("|-- scripts/\n");
    sb.append("|   |-- story/\n");
    sb.append("|   |   `-- prologue.vns\n");
    sb.append("|   |-- common/\n");
    sb.append("|   `-- system/\n");
    sb.append("|-- assets/\n");
    sb.append("|   |-- backgrounds/\n");
    sb.append("|   |-- characters/\n");
    sb.append("|   |-- portraits/\n");
    sb.append("|   |-- cg/\n");
    if (includeDemoAssets) {
      sb.append("|   |-- demo/\n");
      sb.append("|   |   |-- backgrounds/\n");
      sb.append("|   |   |   `-- field/\n");
      sb.append("|   |   |-- characters/\n");
      sb.append("|   |   |   `-- lavender/\n");
      sb.append("|   |   `-- audio/\n");
    }
    sb.append("|   |-- ui/\n");
    sb.append("|   |-- fonts/\n");
    sb.append("|   `-- audio/\n");
    sb.append("|       |-- bgm/\n");
    sb.append("|       |-- sfx/\n");
    sb.append("|       `-- voices/\n");
    sb.append("|-- save/\n");
    if (shouldSetupGit()) {
      sb.append("|-- .gitignore\n");
    }
    sb.append("|-- README.md\n");
    sb.append("`-- jvn.project\n");

    return sb.toString();
  }

  private long estimateBundledDemoAssetsKb() {
    File sourceRoot = resolveBundledDemoAssetsRoot();
    if (sourceRoot == null || !sourceRoot.isDirectory()) {
      // Fallback for packaged builds where source folders are not directly discoverable.
      return 20480;
    }
    long bytes = computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_BG_DIR))
        + computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_SPRITE_LAYERED_DIR))
        + computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_SPRITE_LEGACY_DIR))
        + computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_BGM_DIR));
    if (bytes <= 0) return 20480;
    return Math.max(1, (bytes + 1023) / 1024);
  }

  private long computeDirectorySize(File dir) {
    if (dir == null || !dir.exists()) return 0;
    if (dir.isFile()) return dir.length();
    File[] children = dir.listFiles();
    if (children == null) return 0;
    long total = 0;
    for (File child : children) {
      total += computeDirectorySize(child);
    }
    return total;
  }

  private boolean shouldStartBlankMenus() {
    return chkBlankMenus != null && chkBlankMenus.isSelected();
  }

  private boolean shouldCreateMenuPack() {
    if (shouldStartBlankMenus()) return false;
    return (chkTitleScreen != null && chkTitleScreen.isSelected())
        || (chkSaveSystem != null && chkSaveSystem.isSelected())
        || (chkSettingsMenu != null && chkSettingsMenu.isSelected());
  }

  private boolean shouldSetupGit() {
    return gitAvailable && chkGitInit != null && chkGitInit.isSelected();
  }

  private boolean shouldCreateInitialCommit() {
    return shouldSetupGit() && chkInitialCommit != null && chkInitialCommit.isSelected();
  }

  private void createProject() {
    String displayName = txtProjectName.getText().trim();
    if (displayName.isEmpty()) {
      showError("Please enter a project name.");
      return;
    }

    String folderName = sanitizeName(displayName);
    if (folderName.isBlank()) {
      showError("Project name must contain at least one letter or number.");
      return;
    }

    String locationRaw = txtLocation.getText().trim();
    if (locationRaw.isEmpty()) {
      showError("Please choose a project location.");
      return;
    }

    File location = new File(locationRaw);
    boolean createdBaseLocation = false;
    if (!location.exists()) {
      if (!location.mkdirs()) {
        showError("Failed to create base location: " + location.getAbsolutePath());
        return;
      }
      createdBaseLocation = true;
    }

    File projectDir = new File(location, folderName);
    if (projectDir.exists()) {
      showError("A project with this name already exists at the selected location.");
      return;
    }

    try {
      createProjectStructure(projectDir, displayName);
      createdProjectDir = projectDir;
      close();
    } catch (Exception ex) {
      cleanupFailedProjectCreation(projectDir, location, createdBaseLocation);
      showError("Failed to create project: " + ex.getMessage());
    }
  }

  private void createProjectStructure(File dir, String displayName) throws Exception {
    boolean includeMenuPack = shouldCreateMenuPack();
    boolean includeSave = chkSaveSystem.isSelected();
    boolean includeSettings = chkSettingsMenu.isSelected();
    boolean includeDemoAssets = chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected();
    boolean gitRequested = shouldSetupGit();
    boolean gitCommitRequested = shouldCreateInitialCommit();
    boolean gitEnabled = false;
    boolean gitInitialCommit = false;

    if (!dir.exists() && !dir.mkdirs()) {
      throw new Exception("Failed to create project directory: " + dir.getAbsolutePath());
    }
    if (!dir.isDirectory()) {
      throw new Exception("Project path is not a directory: " + dir.getAbsolutePath());
    }
    createDirectories(dir, includeMenuPack, includeDemoAssets);
    boolean useLayeredLavenderDemo = false;
    if (includeDemoAssets) {
      useLayeredLavenderDemo = copyBundledDemoAssets(dir);
    }

    if (chkSampleContent.isSelected() && includeDemoAssets) createSampleScript(dir, displayName, useLayeredLavenderDemo);
    else createEmptyScript(dir, displayName);

    try (FileWriter fw = new FileWriter(new File(dir, TIMELINE_PATH))) {
      fw.write("# Story Timeline for " + displayName + "\n");
      fw.write("# Author: " + txtAuthor.getText().trim() + "\n\n");
      fw.write("arc \"Prologue\" script \"" + ENTRY_SCRIPT_PATH + "\" entry \"start\" at 40,40\n");
    }

    createSettings(dir);
    createDialogueLayout(dir);

    if (includeMenuPack) {
      createMenuTheme(dir, displayName);
      createMenuCustomizationScaffold(dir, displayName, includeSave, includeSettings);
    } else if (shouldStartBlankMenus()) {
      createBlankMenuScaffold(dir);
    }

    if (gitRequested) {
      GitVcsService vcs = new GitVcsService();
      String commitMessage = "Initialize " + displayName + " project scaffold";
      try {
        vcs.bootstrapRepository(dir, gitCommitRequested, commitMessage);
        gitEnabled = true;
        gitInitialCommit = gitCommitRequested;
      } catch (GitVcsService.GitVcsException ex) {
        if (gitCommitRequested) {
          try {
            // Fallback: keep repo init even if commit fails due to missing user identity.
            vcs.bootstrapRepository(dir, false, commitMessage);
            gitEnabled = true;
            gitInitialCommit = false;
          } catch (GitVcsService.GitVcsException ignored) {
            // Non-fatal: project creation should never fail due to Git state.
          }
        }
      }
    }

    int[] resolution = parseResolution();
    createManifest(
        dir,
        displayName,
        resolution[0],
        resolution[1],
        includeMenuPack,
        includeSave,
        includeSettings,
        gitEnabled,
        gitInitialCommit
    );

    createReadme(
        dir,
        displayName,
        includeMenuPack,
        includeSave,
        includeSettings,
        includeDemoAssets,
        gitEnabled,
        gitInitialCommit
    );
  }

  private void cleanupFailedProjectCreation(File projectDir, File baseLocation, boolean createdBaseLocation) {
    if (projectDir != null && projectDir.exists()) {
      try {
        deleteDirectoryRecursively(projectDir.toPath());
      } catch (Exception ignored) {
        // Best-effort rollback. The original creation exception is shown to the user.
      }
    }
    if (createdBaseLocation && baseLocation != null) {
      try {
        if (baseLocation.isDirectory()) {
          String[] children = baseLocation.list();
          if (children != null && children.length == 0) {
            baseLocation.delete();
          }
        }
      } catch (Exception ignored) {
        // Non-fatal cleanup path.
      }
    }
  }

  private void deleteDirectoryRecursively(Path root) throws Exception {
    if (root == null || !Files.exists(root)) return;
    try (var walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      });
    } catch (RuntimeException ex) {
      if (ex.getCause() instanceof Exception cause) throw cause;
      throw ex;
    }
  }

  private void createDirectories(File dir, boolean includeMenuPack, boolean includeDemoAssets) throws Exception {
    // Config
    ensureDirectory(dir, "config/settings");
    ensureDirectory(dir, "config/timeline");
    ensureDirectory(dir, "config/ui");
    if (includeMenuPack || shouldStartBlankMenus()) {
      ensureDirectory(dir, "config/menu/registry");
      ensureDirectory(dir, "config/menu/menus");
      ensureDirectory(dir, "config/menu/layouts");
      ensureDirectory(dir, "config/menu/styles");
      ensureDirectory(dir, "config/menu/assets/buttons");
      ensureDirectory(dir, "config/menu/assets/icons");
    }
    if (includeMenuPack) {
      ensureDirectory(dir, "config/menu/theme");
    }

    // Scripts
    ensureDirectory(dir, "scripts/story");
    ensureDirectory(dir, "scripts/common");
    ensureDirectory(dir, "scripts/system");

    // Assets
    ensureDirectory(dir, "assets/backgrounds");
    ensureDirectory(dir, "assets/characters");
    ensureDirectory(dir, "assets/portraits");
    ensureDirectory(dir, "assets/cg");
    if (includeDemoAssets) {
      ensureDirectory(dir, "assets/demo/backgrounds");
      ensureDirectory(dir, "assets/demo/characters");
      ensureDirectory(dir, "assets/demo/audio");
    }
    ensureDirectory(dir, "assets/ui");
    ensureDirectory(dir, "assets/fonts");
    ensureDirectory(dir, "assets/audio/bgm");
    ensureDirectory(dir, "assets/audio/sfx");
    ensureDirectory(dir, "assets/audio/voices");

    // Save location
    ensureDirectory(dir, "save");
  }

  private void createBlankMenuScaffold(File dir) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, MENU_REGISTRY_PATH))) {
      fw.write("# Menu registry (blank project scaffold)\n");
      fw.write("# File format: Java properties (key=value)\n");
      fw.write("# This file registers which menu DSL files runtime should discover.\n");
      fw.write("#\n");
      fw.write("# defaultMenu: initial menu id opened by [mainmenu] and title flow.\n");
      fw.write("# menus: comma-separated menu screen ids (.menu files in config/menu/menus/).\n");
      fw.write("# layouts: comma-separated layout ids (.layout files in config/menu/layouts/).\n");
      fw.write("# styles: comma-separated style ids (.style files in config/menu/styles/).\n");
      fw.write("#\n");
      fw.write("# Example:\n");
      fw.write("# defaultMenu=main\n");
      fw.write("# menus=main,load,save,settings\n");
      fw.write("# layouts=default,submenu,slots\n");
      fw.write("# styles=default,submenu,slot\n");
    }
  }

  private void ensureDirectory(File root, String relativePath) throws Exception {
    File directory = new File(root, relativePath);
    if (directory.exists()) {
      if (!directory.isDirectory()) {
        throw new Exception("Expected directory but found file: " + directory.getAbsolutePath());
      }
      return;
    }
    if (!directory.mkdirs()) {
      throw new Exception("Failed to create directory: " + directory.getAbsolutePath());
    }
  }

  private boolean copyBundledDemoAssets(File projectRoot) throws Exception {
    File sourceRoot = resolveBundledDemoAssetsRoot();
    if (sourceRoot == null || !sourceRoot.isDirectory()) return false;

    copyDirectoryContents(
        new File(sourceRoot, BUNDLED_DEMO_BG_DIR),
        new File(projectRoot, "assets/demo/backgrounds/field")
    );
    boolean copiedLayeredLavender = false;
    File layeredSource = new File(sourceRoot, BUNDLED_DEMO_SPRITE_LAYERED_DIR);
    if (layeredSource.isDirectory()) {
      copyDirectoryContents(layeredSource, new File(projectRoot, "assets/demo/characters/lavender"));
      copiedLayeredLavender = true;
    }
    copyDirectoryContents(
        new File(sourceRoot, BUNDLED_DEMO_SPRITE_LEGACY_DIR),
        new File(projectRoot, "assets/demo/characters/codel")
    );
    copyDirectoryContents(
        new File(sourceRoot, BUNDLED_DEMO_BGM_DIR),
        new File(projectRoot, "assets/demo/audio")
    );
    return copiedLayeredLavender;
  }

  private File resolveBundledDemoAssetsRoot() {
    File cwd = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
    File cursor = cwd;
    for (int i = 0; i < 6 && cursor != null; i++) {
      File candidate = new File(cursor, BUNDLED_DEMO_ASSETS_DIR);
      if (candidate.isDirectory()) return candidate;
      File legacyBg = new File(cursor, BUNDLED_DEMO_BG_DIR);
      File layeredSprites = new File(cursor, BUNDLED_DEMO_SPRITE_LAYERED_DIR);
      File legacySprites = new File(cursor, BUNDLED_DEMO_SPRITE_LEGACY_DIR);
      if (legacyBg.isDirectory() && (layeredSprites.isDirectory() || legacySprites.isDirectory())) return cursor;
      cursor = cursor.getParentFile();
    }
    return null;
  }

  private void copyDirectoryContents(File source, File destination) throws Exception {
    if (source == null || !source.exists() || !source.isDirectory()) return;
    Path sourcePath = source.toPath();
    try (var stream = Files.walk(sourcePath)) {
      stream.forEach(path -> {
        try {
          Path relative = sourcePath.relativize(path);
          Path target = destination.toPath().resolve(relative);
          if (Files.isDirectory(path)) {
            Files.createDirectories(target);
          } else {
            Files.createDirectories(target.getParent());
            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
          }
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      });
    } catch (RuntimeException ex) {
      if (ex.getCause() instanceof Exception cause) throw cause;
      throw ex;
    }
  }

  private int[] parseResolution() {
    if (chkCustomResolution != null && chkCustomResolution.isSelected()) {
      int width = parseDimension(txtCustomWidth == null ? null : txtCustomWidth.getText(), 2560, "width");
      int height = parseDimension(txtCustomHeight == null ? null : txtCustomHeight.getText(), 1440, "height");
      return new int[] {width, height};
    }
    String raw = cmbResolution == null ? null : cmbResolution.getValue();
    if (raw == null || raw.isBlank()) return new int[] {1920, 1080};
    String[] first = raw.split(" ");
    String[] parts = first[0].split("x");
    if (parts.length != 2) return new int[] {1920, 1080};
    int width = parseDimension(parts[0], 1920, "width");
    int height = parseDimension(parts[1], 1080, "height");
    return new int[] {width, height};
  }

  private int parseDimension(String raw, int fallback, String axis) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      int value = Integer.parseInt(raw.trim());
      if (value < 320) return 320;
      if (value > 8192) return 8192;
      return value;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private String formatAspectRatio(int width, int height) {
    if (width <= 0 || height <= 0) return "unknown";
    int gcd = greatestCommonDivisor(width, height);
    return (width / gcd) + ":" + (height / gcd);
  }

  private int greatestCommonDivisor(int a, int b) {
    int x = Math.abs(a);
    int y = Math.abs(b);
    while (y != 0) {
      int tmp = x % y;
      x = y;
      y = tmp;
    }
    return x == 0 ? 1 : x;
  }

  private void createManifest(File dir,
                              String displayName,
                              int width,
                              int height,
                              boolean includeMenuPack,
                              boolean includeSave,
                              boolean includeSettings,
                              boolean gitEnabled,
                              boolean gitInitialCommit) throws Exception {
    Properties manifest = new Properties();
    manifest.setProperty("name", displayName);
    manifest.setProperty("author", txtAuthor.getText().trim());
    manifest.setProperty("type", "vn");
    manifest.setProperty("entryVns", ENTRY_SCRIPT_PATH);
    manifest.setProperty("entryLabel", "start");
    manifest.setProperty("timeline", TIMELINE_PATH);
    manifest.setProperty("settingsFile", SETTINGS_PATH);
    manifest.setProperty("dialogueLayout", DIALOGUE_LAYOUT_PATH);
    if (includeMenuPack) {
      manifest.setProperty("menuTheme", MENU_THEME_PATH);
      manifest.setProperty("menuRegistry", MENU_REGISTRY_PATH);
      manifest.setProperty("menuDefaultLayout", MENU_LAYOUT_DEFAULT_PATH);
      manifest.setProperty("menuDefaultStyle", MENU_STYLE_DEFAULT_PATH);
    } else if (shouldStartBlankMenus()) {
      manifest.setProperty("menuRegistry", MENU_REGISTRY_PATH);
    }
    manifest.setProperty("feature.blankMenus", Boolean.toString(shouldStartBlankMenus()));
    manifest.setProperty("width", String.valueOf(width));
    manifest.setProperty("height", String.valueOf(height));
    manifest.setProperty("display.customResolution", Boolean.toString(chkCustomResolution != null && chkCustomResolution.isSelected()));
    manifest.setProperty("theme", cmbTheme == null || cmbTheme.getValue() == null ? "Dark Elegant" : cmbTheme.getValue());
    manifest.setProperty("runtime.ui", cmbRuntimeUi == null || cmbRuntimeUi.getValue() == null ? "fx" : cmbRuntimeUi.getValue());
    manifest.setProperty("runtime.audio", cmbAudioBackend == null || cmbAudioBackend.getValue() == null ? "auto" : cmbAudioBackend.getValue());
    manifest.setProperty("runtime.locale", cmbLocale == null || cmbLocale.getValue() == null ? "en" : cmbLocale.getValue());
    manifest.setProperty("feature.sampleContent", Boolean.toString(chkSampleContent.isSelected()));
    manifest.setProperty("feature.demoAssets", Boolean.toString(chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected()));
    manifest.setProperty("feature.titleScreen", Boolean.toString(chkTitleScreen.isSelected() && includeMenuPack));
    manifest.setProperty("feature.menuProfiles", Boolean.toString(includeMenuPack));
    manifest.setProperty("feature.saveSystem", Boolean.toString(includeSave));
    manifest.setProperty("feature.settingsMenu", Boolean.toString(includeSettings));
    manifest.setProperty("feature.historyBacklog", Boolean.toString(chkHistoryBacklog.isSelected()));
    manifest.setProperty("vcs.git.enabled", Boolean.toString(gitEnabled));
    manifest.setProperty("vcs.git.initialCommit", Boolean.toString(gitInitialCommit));
    manifest.setProperty("createdBy", "jvn-editor-wizard");

    if (!txtDescription.getText().isBlank()) {
      manifest.setProperty("description", txtDescription.getText().trim());
    }

    try (FileOutputStream fos = new FileOutputStream(new File(dir, "jvn.project"))) {
      manifest.store(fos, "JVN Visual Novel Project");
    }
  }

  private void createSampleScript(File dir, String name, boolean useLayeredLavenderDemo) throws Exception {
    String scenarioId = sanitizeName(name).toLowerCase() + "_prologue";
    String characterId = useLayeredLavenderDemo ? "lavender" : "codel";
    String characterName = useLayeredLavenderDemo ? "Lavender" : "Codel";
    String characterDecls = useLayeredLavenderDemo
        ? """
        @charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
        @charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
        @charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png
        @charlayer lavender eyes_angry assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_angry.png
        @charlayer lavender mouth_neutral assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_neutral.png
        @charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
        @charlayer lavender mouth_happy assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_happy.png
        @charlayer lavender mouth_o assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_o.png
        
        @charpreset lavender idle $base | $eyes_neutral | $mouth_neutral
        @charpreset lavender talking $base | $eyes_half_closed | $mouth_smile
        @charpreset lavender happy $base | $eyes_neutral | $mouth_happy
        @charpreset lavender emphasis $base | $eyes_angry | $mouth_o
        """
        : """
        @charimg codel talking assets/demo/characters/codel/Codel1.png
        @charimg codel idle assets/demo/characters/codel/Codel2.png
        """;
    String framingCommands = useLayeredLavenderDemo
        ? """
        [set ui.characterHeightFactor 1.28]
        [set ui.characterBaselineY 1.42]
        """
        : "";
    String imageDslLine = useLayeredLavenderDemo
        ? "Codel: You can build layered expressions with @charlayer + @charpreset, then show them like normal expressions."
        : "Codel: You define images once with @charimg and @background, then use them by name.";

    String script = """
        # %s - Prologue
        # Demo game created with JVN Engine
        # Tutorial-style showcase inspired by classic onboarding flows:
        # A friendly tutorial that teaches the basics of JVN scripting.

        @scenario %s

        @character codel "Codel"

        __CHAR_DECLS__

        @background field_day assets/demo/backgrounds/field/field.jpg
        @background field_evening assets/demo/backgrounds/field/field.jpg

        @label start
        [bg field_day]
        [transition fade 500]
        [textspeed 28]
        [autodelay 1800]
        __FRAMING_COMMANDS__
        [bgm "assets/demo/audio/03 - Definitely Our Town.mp3"]

        [show codel center talking]
        [wait 240]

        Codel: Hi! My name is Codel, and I'd like to welcome you to the JVN tutorial.
        Codel: In this tutorial, we'll teach you the basics of JVN, so you can make visual novels of your own.
        Codel: We'll also demonstrate many features, so you can see what JVN is capable of.
        Codel: This is {b}%s{/b}, by the way. Feel free to poke around the scripts when we're done!
        [jump tutorials_hub]

        @label tutorials_hub
        [show codel far_left talking]
        [wait 170]
        Codel: What would you like to see?
        > Writing Dialogue -> tutorial_dialogue
        > Images and Backgrounds -> tutorial_images
        > Transitions and Effects -> tutorial_transitions
        > Choices -> tutorial_menus
        > That's enough for now -> end_early

        @label tutorial_dialogue
        [show codel center talking]
        [wait 200]
        Codel: Writing dialogue is the heart of any visual novel!
        Codel: You can use tags like {b}bold{/b}, {i}italic{/i}, and {color=#4a9eff}color{/color} to style your text.
        Codel: There's even {wave}wavy text{/wave} for when things get dramatic!
        Codel: Just remember - a little formatting goes a long way.
        [jump tutorials_hub]

        @label tutorial_images
        [show codel center talking]
        [wait 170]
        Codel: Images bring your story to life! You'll mainly work with two types.
        Codel: Character sprites - that's me! - and backgrounds, like this field behind us.
        [show codel right talking]
        [wait 320]
        Codel: Characters can appear in different spots on screen. See? I just moved!
        [show codel center talking]
        __IMAGE_DSL_LINE__
        [jump tutorials_hub]

        @label tutorial_transitions
        [show codel center talking]
        Codel: Transitions make scene changes feel smooth and polished.
        [transition crossfade 650 field_evening]
        Codel: Like that crossfade! The background just changed to evening.
        [screen flash 0.28 140 1 1 1]
        Codel: And effects like screen flashes add dramatic impact.
        [transition fade 450 field_day]
        Codel: Back to daytime! Transitions help set the mood of each scene.
        [jump tutorials_hub]

        @label tutorial_menus
        [show codel far_left talking]
        [wait 220]
        Codel: Choices let players shape the story! Each option can branch to different paths.
        Codel: JVN also has built-in menu integration. Want to try the save system?
        > Yes, open save menu -> menus_save
        > No, tell me about it -> menus_explain

        @label menus_save
        Codel: Opening the save menu now!
        [save]
        Codel: That was the [save] command. Players can save their progress anytime.
        [jump menus_done]

        @label menus_explain
        Codel: No problem! The [save] command opens the save menu.
        Codel: There's also [mainmenu] to return to the title screen.
        [jump menus_done]

        @label menus_done
        Codel: You can trigger menus from scripts or let players use keyboard shortcuts.
        [jump tutorials_hub]

        @label end_early
        [show codel center talking]
        [wait 200]
        Codel: Thank you for checking out this tutorial!
        Codel: If you'd like to see how this demo works, take a look at {b}%s{/b}.
        Codel: You can edit it, break it, rebuild it - that's how you learn!
        Codel: We look forward to seeing what you create with JVN. Have fun!
        [end]

        """.formatted(name, scenarioId, name, ENTRY_SCRIPT_PATH)
        .replace("__CHAR_DECLS__", characterDecls.stripTrailing())
        .replace("__FRAMING_COMMANDS__", framingCommands.stripTrailing())
        .replace("__IMAGE_DSL_LINE__", imageDslLine)
        .replace("codel", characterId)
        .replace("Codel", characterName);

    try (FileWriter fw = new FileWriter(new File(dir, ENTRY_SCRIPT_PATH))) {
      fw.write(script);
    }
  }

  private void createEmptyScript(File dir, String name) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, ENTRY_SCRIPT_PATH))) {
      fw.write("# " + name + " - Prologue\n");
      fw.write("@scenario " + sanitizeName(name).toLowerCase() + "_prologue\n");
      fw.write("@character narrator \"Narrator\"\n\n");
      fw.write("@label start\n\n");
      fw.write("Narrator: " + name + " begins here...\n\n");
      fw.write("[end]\n");
    }
  }

  private void createSettings(File dir) throws Exception {
    try (FileOutputStream fos = new FileOutputStream(new File(dir, SETTINGS_PATH))) {
      Properties sp = new Properties();
      int textSpeed = spTextSpeed == null ? 35 : spTextSpeed.getValue();
      int autoDelay = spAutoDelay == null ? 2000 : spAutoDelay.getValue();
      double bgm = spBgmVolume == null ? 0.7 : spBgmVolume.getValue();
      double sfx = spSfxVolume == null ? 0.8 : spSfxVolume.getValue();
      double voice = spVoiceVolume == null ? 1.0 : spVoiceVolume.getValue();
      boolean skipUnread = chkSkipUnreadDefault != null && chkSkipUnreadDefault.isSelected();
      boolean skipAfterChoices = chkSkipAfterChoicesDefault != null && chkSkipAfterChoicesDefault.isSelected();
      int physicsStep = spPhysicsFixedStep == null ? 0 : spPhysicsFixedStep.getValue();
      int physicsSubsteps = spPhysicsMaxSubsteps == null ? 4 : spPhysicsMaxSubsteps.getValue();
      double physicsFriction = spPhysicsFriction == null ? 0.2 : spPhysicsFriction.getValue();
      String inputProfile = txtInputProfilePath == null || txtInputProfilePath.getText().isBlank()
          ? System.getProperty("user.home") + "/.jvn/input-bindings.properties"
          : txtInputProfilePath.getText().trim();

      // Legacy project settings keys used by the current settings editor.
      sp.setProperty("textSpeed", Integer.toString(textSpeed));
      sp.setProperty("bgm", Double.toString(bgm));
      sp.setProperty("sfx", Double.toString(sfx));
      sp.setProperty("voice", Double.toString(voice));
      sp.setProperty("autoPlayDelay", Integer.toString(autoDelay));
      sp.setProperty("skipUnread", Boolean.toString(skipUnread));
      sp.setProperty("skipAfterChoices", Boolean.toString(skipAfterChoices));
      sp.setProperty("clickRevealBeforeAdvance", Boolean.toString(true));

      // Runtime settings keys used by VnSettingsStore.
      sp.setProperty("text_speed", Integer.toString(textSpeed));
      sp.setProperty("bgm_volume", Double.toString(bgm));
      sp.setProperty("sfx_volume", Double.toString(sfx));
      sp.setProperty("voice_volume", Double.toString(voice));
      sp.setProperty("auto_play_delay", Integer.toString(autoDelay));
      sp.setProperty("skip_unread_text", Boolean.toString(skipUnread));
      sp.setProperty("skip_after_choices", Boolean.toString(skipAfterChoices));
      sp.setProperty("click_reveal_before_advance", Boolean.toString(true));
      sp.setProperty("physics_fixed_step_ms", Integer.toString(physicsStep));
      sp.setProperty("physics_max_substeps", Integer.toString(physicsSubsteps));
      sp.setProperty("physics_default_friction", Double.toString(physicsFriction));
      sp.setProperty("input_profile_path", inputProfile);

      // Project module hints.
      sp.setProperty("historyBacklogEnabled", Boolean.toString(chkHistoryBacklog.isSelected()));
      sp.setProperty("saveProfilesEnabled", Boolean.toString(chkSaveSystem.isSelected()));
      sp.setProperty("settingsProfileEnabled", Boolean.toString(chkSettingsMenu.isSelected()));
      sp.store(fos, "VN Settings - Edit in Settings panel");
    }
  }

  private void createDialogueLayout(File dir) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, DIALOGUE_LAYOUT_PATH))) {
      fw.write(LayoutDslTemplates.defaultDialogueLayoutTemplate().replace("\n", System.lineSeparator()));
    }
  }

  private void createMenuTheme(File dir, String name) throws Exception {
    String theme = cmbTheme.getValue();
    Properties tp = new Properties();

    switch (theme) {
      case "Light Clean" -> {
        tp.setProperty("backgroundColor", "#F4F7FB");
        tp.setProperty("titleColor", "#142033");
        tp.setProperty("itemColor", "#3C4A63");
        tp.setProperty("itemSelectedColor", "#1D5CFF");
        tp.setProperty("hintColor", "#5D6A86");
        tp.setProperty("accentColor", "#1D5CFF");
      }
      case "Retro Game" -> {
        tp.setProperty("backgroundColor", "#070707");
        tp.setProperty("titleColor", "#7CFF64");
        tp.setProperty("itemColor", "#65DB56");
        tp.setProperty("itemSelectedColor", "#FFE36B");
        tp.setProperty("hintColor", "#8FCA7E");
        tp.setProperty("accentColor", "#FFE36B");
      }
      case "Nature Green" -> {
        tp.setProperty("backgroundColor", "#122616");
        tp.setProperty("titleColor", "#D2F7DA");
        tp.setProperty("itemColor", "#A9DDB1");
        tp.setProperty("itemSelectedColor", "#5CE385");
        tp.setProperty("hintColor", "#88C995");
        tp.setProperty("accentColor", "#5CE385");
      }
      default -> {
        tp.setProperty("backgroundColor", "#050B16");
        tp.setProperty("titleColor", "#F3F8FF");
        tp.setProperty("itemColor", "#D9E3F5");
        tp.setProperty("itemSelectedColor", "#FFE18A");
        tp.setProperty("hintColor", "#A8B6D2");
        tp.setProperty("accentColor", "#7CC4FF");
      }
    }

    String systemFont = Font.getDefault().getFamily();
    tp.setProperty("titleFontFamily", systemFont);
    tp.setProperty("titleFontWeight", "BOLD");
    tp.setProperty("titleFontSize", "50");
    tp.setProperty("itemFontFamily", systemFont);
    tp.setProperty("itemFontWeight", "SEMI_BOLD");
    tp.setProperty("itemFontSize", "28");
    tp.setProperty("hintFontFamily", systemFont);
    tp.setProperty("hintFontSize", "16");
    tp.setProperty("titleY", "0.12");
    tp.setProperty("listYStart", "0.33");
    tp.setProperty("lineHeight", "66");
    tp.setProperty("itemPrefix", "");
    tp.setProperty("itemSelectedPrefix", "▶ ");
    tp.setProperty("hintsText", "Enter/Click: Select    Esc: Back");
    tp.setProperty("titleText", name);
    tp.setProperty("backgroundImage", DEFAULT_MENU_BG_ASSET_PATH);

    try (FileOutputStream fos = new FileOutputStream(new File(dir, MENU_THEME_PATH))) {
      tp.store(fos, "Menu Theme for " + name + " - " + theme);
    }
  }

  private void createMenuCustomizationScaffold(File dir, String name, boolean includeSave, boolean includeSettings)
      throws Exception {

    List<String> menus = new ArrayList<>();
    menus.add("main");
    menus.add("extras");
    menus.add("credits");
    menus.add("confirm_exit");
    if (includeSave) {
      menus.add("load");
      menus.add("save");
    }
    if (includeSettings) {
      menus.add("settings");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_REGISTRY_PATH))) {
      fw.write("# Menu customization registry\n");
      fw.write("# File format: Java properties (key=value)\n");
      fw.write("# Text-first workflow: edit ids here, save, then run runtime to verify discoverability.\n");
      fw.write("#\n");
      fw.write("# defaultMenu: first menu id shown when opening main menu flow.\n");
      fw.write("# menus: all discoverable .menu files by id.\n");
      fw.write("# layouts: all discoverable .layout files by id.\n");
      fw.write("# styles: all discoverable .style files by id.\n");
      fw.write("#\n");
      fw.write("# Keep ids lowercase/snake_case and match file names.\n");
      fw.write("defaultMenu=main\n");
      fw.write("menus=" + String.join(",", menus) + "\n");
      fw.write("layouts=default,submenu,slots\n");
      fw.write("styles=default,submenu,slot\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_LAYOUT_DEFAULT_PATH))) {
      fw.write("# Main menu layout (.layout)\n");
      fw.write("# Text-first workflow: edit values, save, run runtime, iterate.\n");
      fw.write("# listYStart: Y position of first row (fraction if <=1, px if >1)\n");
      fw.write("# lineHeight: vertical spacing between rows (pixels)\n");
      fw.write("# listWidthFactor: width of list area as viewport fraction (0..1)\n");
      fw.write("# textAlign: left|center|right alignment inside list area\n");
      fw.write("# hintsBottomMargin: bottom margin for hint text (pixels)\n");
      fw.write("# titleY: title Y position (fraction if <=1, px if >1)\n");
      fw.write("listYStart=0.34\n");
      fw.write("lineHeight=68\n");
      fw.write("listWidthFactor=0.44\n");
      fw.write("textAlign=center\n");
      fw.write("hintsBottomMargin=36\n");
      fw.write("titleY=0.14\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_LAYOUT_SUBMENU_PATH))) {
      fw.write("# Submenu layout (.layout)\n");
      fw.write("# Text-first workflow: edit values, save, run runtime, iterate.\n");
      fw.write("# Reused by extras/settings/credits style menus.\n");
      fw.write("# Keys use same semantics as default.layout.\n");
      fw.write("listYStart=0.24\n");
      fw.write("lineHeight=62\n");
      fw.write("listWidthFactor=0.64\n");
      fw.write("textAlign=left\n");
      fw.write("hintsBottomMargin=30\n");
      fw.write("titleY=0.11\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_LAYOUT_SLOTS_PATH))) {
      fw.write("# Save/Load slot layout (.layout)\n");
      fw.write("# Text-first workflow: edit values, save, run runtime, iterate.\n");
      fw.write("# Tuned for wider save slot cards and preview thumbnails.\n");
      fw.write("# Keys use same semantics as default.layout.\n");
      fw.write("listYStart=0.20\n");
      fw.write("lineHeight=74\n");
      fw.write("listWidthFactor=0.58\n");
      fw.write("textAlign=left\n");
      fw.write("hintsBottomMargin=30\n");
      fw.write("titleY=0.10\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_STYLE_DEFAULT_PATH))) {
      fw.write("# Main menu visual style (.style)\n");
      fw.write("# Text-first workflow: edit colors/fonts/asset paths, save, run runtime.\n");
      fw.write("# Colors accept #RRGGBB or #RRGGBBAA.\n");
      fw.write("# Prefix values are prepended to rendered labels.\n");
      fw.write("# Font keys map to JavaFX font family/weight/size.\n");
      fw.write("# Uncomment buttonAsset keys to use textured row backgrounds.\n");
      fw.write("itemColor=#DCE6F8\n");
      fw.write("itemSelectedColor=#FFE8A3\n");
      fw.write("itemHoverColor=#F4F8FF\n");
      fw.write("itemDisabledColor=#7D8CA8\n");
      fw.write("itemPrefix=\n");
      fw.write("itemSelectedPrefix=▶ \n");
      fw.write("itemDisabledPrefix=• \n");
      fw.write("itemFontFamily=Segoe UI\n");
      fw.write("itemFontWeight=SEMI_BOLD\n");
      fw.write("itemFontSize=28\n");
      fw.write("itemShadowColor=#000000AA\n");
      fw.write("itemShadowOffsetX=1\n");
      fw.write("itemShadowOffsetY=1\n");
      fw.write("titleColor=#F2F7FF\n");
      fw.write("titleFontFamily=Segoe UI\n");
      fw.write("titleFontWeight=BOLD\n");
      fw.write("titleFontSize=56\n");
      fw.write("titleShadowColor=#000000A8\n");
      fw.write("hintsColor=#A8B6D2\n");
      fw.write("hintsFontFamily=Segoe UI\n");
      fw.write("hintsFontSize=18\n");
      fw.write("backgroundAsset=" + DEFAULT_MENU_BG_ASSET_PATH + "\n");
      fw.write("backgroundColor=#050B16\n");
      fw.write("backgroundOpacity=1.0\n");
      fw.write("# buttonAsset=assets/ui/menu/button.png\n");
      fw.write("# buttonSelectedAsset=assets/ui/menu/button_selected.png\n");
      fw.write("# buttonHoverAsset=assets/ui/menu/button_hover.png\n");
      fw.write("# buttonDisabledAsset=assets/ui/menu/button_disabled.png\n");
      fw.write("buttonTextPaddingX=28\n");
      fw.write("buttonTextPaddingY=2\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_STYLE_SUBMENU_PATH))) {
      fw.write("# Submenu visual style (.style)\n");
      fw.write("# Text-first workflow: edit colors/fonts/asset paths, save, run runtime.\n");
      fw.write("# Shared by extras/settings/credits-oriented menus.\n");
      fw.write("# Keys use the same semantics as default.style.\n");
      fw.write("itemColor=#D6E0F4\n");
      fw.write("itemSelectedColor=#B8EAFF\n");
      fw.write("itemHoverColor=#EAF4FF\n");
      fw.write("itemDisabledColor=#7387AA\n");
      fw.write("itemPrefix=  \n");
      fw.write("itemSelectedPrefix=▸ \n");
      fw.write("itemDisabledPrefix=  \n");
      fw.write("itemFontFamily=Segoe UI\n");
      fw.write("itemFontWeight=NORMAL\n");
      fw.write("itemFontSize=24\n");
      fw.write("titleColor=#EAF2FF\n");
      fw.write("titleFontFamily=Segoe UI\n");
      fw.write("titleFontWeight=BOLD\n");
      fw.write("titleFontSize=42\n");
      fw.write("hintsColor=#97AACE\n");
      fw.write("hintsFontFamily=Segoe UI\n");
      fw.write("hintsFontSize=16\n");
      fw.write("backgroundColor=#091222\n");
      fw.write("backgroundOpacity=1.0\n");
      fw.write("buttonTextPaddingX=24\n");
      fw.write("buttonTextPaddingY=1\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_STYLE_SLOT_PATH))) {
      fw.write("# Save/load slot row visual style (.style)\n");
      fw.write("# Text-first workflow: edit colors/fonts/asset paths, save, run runtime.\n");
      fw.write("# Designed for save slot card rows and metadata-heavy labels.\n");
      fw.write("# Keys use the same semantics as default.style.\n");
      fw.write("itemColor=#E4EDF8\n");
      fw.write("itemSelectedColor=#FFF1B5\n");
      fw.write("itemHoverColor=#F0F7FF\n");
      fw.write("itemDisabledColor=#7B87A0\n");
      fw.write("itemPrefix=\n");
      fw.write("itemSelectedPrefix=▶ \n");
      fw.write("itemFontFamily=Segoe UI\n");
      fw.write("itemFontWeight=SEMI_BOLD\n");
      fw.write("itemFontSize=22\n");
      fw.write("backgroundColor=#060F1D\n");
      fw.write("backgroundOpacity=1.0\n");
      fw.write("buttonTextPaddingX=22\n");
      fw.write("buttonTextPaddingY=0\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_MAIN_PATH))) {
      fw.write("# Main menu screen definition (redesigned)\n");
      fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
      fw.write("#\n");
      fw.write("# Core keys:\n");
      fw.write("# titleText: menu title line.\n");
      fw.write("# hintsText: helper controls line shown near footer.\n");
      fw.write("# layout: layout id from config/menu/layouts/*.layout.\n");
      fw.write("# defaultItemStyle: style id from config/menu/styles/*.style.\n");
      fw.write("# wrapSelection: true loops keyboard selection from end->start.\n");
      fw.write("# items: ordered list of item ids rendered by this screen.\n");
      fw.write("#\n");
      fw.write("# Per-item keys:\n");
      fw.write("# item.<id>.label, item.<id>.action, item.<id>.target, item.<id>.style,\n");
      fw.write("# item.<id>.enabled, item.<id>.boundsX/Y/Width/Height (optional custom hit box).\n");
      fw.write("titleText=" + name + "\n");
      fw.write("hintsText=Enter/Click: Select    Esc: Back\n");
      fw.write("layout=default\n");
      fw.write("defaultItemStyle=default\n");
      fw.write("wrapSelection=true\n");

      List<String> items = new ArrayList<>();
      items.add("new_game");
      if (includeSave) items.add("continue");
      if (includeSettings) items.add("settings");
      items.add("extras");
      items.add("quit");
      fw.write("items=" + String.join(",", items) + "\n");

      fw.write("item.new_game.label=Start New Game\n");
      fw.write("item.new_game.action=new_game\n");
      if (includeSave) {
        fw.write("item.continue.label=Continue\n");
        fw.write("item.continue.action=load_menu\n");
      }
      if (includeSettings) {
        fw.write("item.settings.label=Settings\n");
        fw.write("item.settings.action=settings_menu\n");
      }
      fw.write("item.extras.label=Extras\n");
      fw.write("item.extras.action=open_menu\n");
      fw.write("item.extras.target=extras\n");
      fw.write("item.quit.label=Quit\n");
      fw.write("item.quit.action=open_menu\n");
      fw.write("item.quit.target=confirm_exit\n");

      double y = 0.34;
      for (String itemId : items) {
        writeCardBounds(fw, itemId, 0.28, y, 0.44, 0.072);
        y += 0.082;
      }
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_EXTRAS_PATH))) {
      fw.write("# Extras submenu\n");
      fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
      fw.write("# Uses submenu layout/style and forwards to nested screens.\n");
      fw.write("# Demonstrates disabled item rows and open_menu transitions.\n");
      fw.write("titleText=Extras\n");
      fw.write("hintsText=Enter/Click: Select    Esc: Back\n");
      fw.write("layout=submenu\n");
      fw.write("defaultItemStyle=submenu\n");
      fw.write("wrapSelection=true\n");
      fw.write("items=music_room,credits,back\n");
      fw.write("item.music_room.label=Music Room (Soon)\n");
      fw.write("item.music_room.enabled=false\n");
      fw.write("item.music_room.action=noop\n");
      fw.write("item.credits.label=Credits\n");
      fw.write("item.credits.action=open_menu\n");
      fw.write("item.credits.target=credits\n");
      fw.write("item.back.label=Return to Main Menu\n");
      fw.write("item.back.action=main_menu\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_CREDITS_PATH))) {
      fw.write("# Credits submenu\n");
      fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
      fw.write("# Simple informational menu: mostly disabled text rows + one back action.\n");
      fw.write("titleText=Credits\n");
      fw.write("hintsText=Esc: Back\n");
      fw.write("layout=submenu\n");
      fw.write("defaultItemStyle=submenu\n");
      fw.write("wrapSelection=true\n");
      fw.write("items=line_engine,line_editor,line_thanks,back\n");
      fw.write("item.line_engine.label=JVN Engine Team\n");
      fw.write("item.line_engine.enabled=false\n");
      fw.write("item.line_engine.action=noop\n");
      fw.write("item.line_editor.label=Runtime, Editor, and VNS by JVN contributors\n");
      fw.write("item.line_editor.enabled=false\n");
      fw.write("item.line_editor.action=noop\n");
      fw.write("item.line_thanks.label=Thanks for building with JVN.\n");
      fw.write("item.line_thanks.enabled=false\n");
      fw.write("item.line_thanks.action=noop\n");
      fw.write("item.back.label=Back\n");
      fw.write("item.back.style=slot\n");
      fw.write("item.back.action=open_menu\n");
      fw.write("item.back.target=extras\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_CONFIRM_EXIT_PATH))) {
      fw.write("# Quit confirmation submenu\n");
      fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
      fw.write("# Shows a disabled prompt row with two actionable choices.\n");
      fw.write("# item.<id>.style overrides row style when needed.\n");
      fw.write("titleText=Exit Game\n");
      fw.write("hintsText=Enter: Confirm    Esc: Cancel\n");
      fw.write("layout=submenu\n");
      fw.write("defaultItemStyle=submenu\n");
      fw.write("wrapSelection=true\n");
      fw.write("items=prompt,quit_yes,quit_no\n");
      fw.write("item.prompt.label=Leave this session?\n");
      fw.write("item.prompt.enabled=false\n");
      fw.write("item.prompt.action=noop\n");
      fw.write("item.quit_yes.label=Yes, Quit\n");
      fw.write("item.quit_yes.style=slot\n");
      fw.write("item.quit_yes.action=quit\n");
      fw.write("item.quit_no.label=No, Return\n");
      fw.write("item.quit_no.action=main_menu\n");
    }

    if (includeSave) {
      try (FileWriter fw = new FileWriter(new File(dir, MENU_LOAD_PATH))) {
        fw.write("# Load menu presentation profile (slot template)\n");
        fw.write("# Text-first workflow: edit slot profile keys, save, run runtime.\n");
        fw.write("# Slot keys:\n");
        fw.write("# item.<id>.slotPreviewEnabled=true enables embedded save thumbnail.\n");
        fw.write("# slotPreviewX/Y/Width/Height are normalized inside each row card.\n");
        fw.write("# Optional bgAsset/bgSelectedAsset/slotPreview*Asset tune row skinning.\n");
        fw.write("titleText=Load Journey\n");
        fw.write("hintsText=Enter: Load    Esc: Back    Del: Delete    R: Rename\n");
        fw.write("layout=slots\n");
        fw.write("defaultItemStyle=slot\n");
        fw.write("wrapSelection=true\n");
        fw.write("items=save_slot\n");
        fw.write("item.save_slot.style=slot\n");
        fw.write("item.save_slot.action=load_menu\n");
        fw.write("item.save_slot.slotPreviewEnabled=true\n");
        fw.write("item.save_slot.slotPreviewX=0.63\n");
        fw.write("item.save_slot.slotPreviewY=0.10\n");
        fw.write("item.save_slot.slotPreviewWidth=0.33\n");
        fw.write("item.save_slot.slotPreviewHeight=0.80\n");
        fw.write("# item.save_slot.bgAsset=config/menu/assets/buttons/slot.png\n");
        fw.write("# item.save_slot.bgSelectedAsset=config/menu/assets/buttons/slot_hover.png\n");
        fw.write("# item.save_slot.slotPreviewPlaceholderAsset=config/menu/assets/buttons/slot_empty.png\n");
        fw.write("# item.save_slot.slotPreviewFrameAsset=config/menu/assets/buttons/slot_frame.png\n");
      }

      try (FileWriter fw = new FileWriter(new File(dir, MENU_SAVE_PATH))) {
        fw.write("# Save menu presentation profile (new slot + slot template)\n");
        fw.write("# Text-first workflow: edit slot profile keys, save, run runtime.\n");
        fw.write("# Includes a dedicated new_slot row plus templated save_slot rows.\n");
        fw.write("# Slot preview keys share the same semantics as load.menu.\n");
        fw.write("titleText=Save Journey\n");
        fw.write("hintsText=Enter: Save    Esc: Back    Del: Delete    R: Rename\n");
        fw.write("layout=slots\n");
        fw.write("defaultItemStyle=slot\n");
        fw.write("wrapSelection=true\n");
        fw.write("items=new_slot,save_slot\n");
        fw.write("item.new_slot.label=Create New Save\n");
        fw.write("item.new_slot.style=submenu\n");
        fw.write("item.new_slot.action=save_menu\n");
        fw.write("item.new_slot.slotPreviewEnabled=true\n");
        fw.write("item.new_slot.slotPreviewX=0.63\n");
        fw.write("item.new_slot.slotPreviewY=0.10\n");
        fw.write("item.new_slot.slotPreviewWidth=0.33\n");
        fw.write("item.new_slot.slotPreviewHeight=0.80\n");
        fw.write("item.save_slot.style=slot\n");
        fw.write("item.save_slot.action=save_menu\n");
        fw.write("item.save_slot.slotPreviewEnabled=true\n");
        fw.write("item.save_slot.slotPreviewX=0.63\n");
        fw.write("item.save_slot.slotPreviewY=0.10\n");
        fw.write("item.save_slot.slotPreviewWidth=0.33\n");
        fw.write("item.save_slot.slotPreviewHeight=0.80\n");
        fw.write("# item.new_slot.bgAsset=config/menu/assets/buttons/new_slot.png\n");
        fw.write("# item.save_slot.bgAsset=config/menu/assets/buttons/slot.png\n");
        fw.write("# item.save_slot.bgSelectedAsset=config/menu/assets/buttons/slot_hover.png\n");
        fw.write("# item.save_slot.slotPreviewPlaceholderAsset=config/menu/assets/buttons/slot_empty.png\n");
        fw.write("# item.save_slot.slotPreviewFrameAsset=config/menu/assets/buttons/slot_frame.png\n");
      }
    }

    if (includeSettings) {
      try (FileWriter fw = new FileWriter(new File(dir, MENU_SETTINGS_PATH))) {
        fw.write("# Settings menu profile (keys map to engine settings)\n");
        fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
        fw.write("# Each item.<id>.label supports {value} placeholder expansion.\n");
        fw.write("# Runtime maps known setting ids to user settings store values.\n");
        fw.write("# 'back' row uses action=back to return to previous screen.\n");
        fw.write("titleText=Settings\n");
        fw.write("hintsText=Up/Down: Select    Left/Right: Adjust    Esc: Back\n");
        fw.write("layout=submenu\n");
        fw.write("defaultItemStyle=submenu\n");
        fw.write("wrapSelection=true\n");
        fw.write("items=text_speed,bgm_volume,sfx_volume,voice_volume,auto_play_delay,skip_unread,skip_after_choices,click_reveal_before_advance,physics_fixed_step,physics_max_substeps,physics_default_friction,input_profile,back\n");
        fw.write("item.text_speed.label=Text Speed: {value}\n");
        fw.write("item.bgm_volume.label=BGM Volume: {value}\n");
        fw.write("item.sfx_volume.label=SFX Volume: {value}\n");
        fw.write("item.voice_volume.label=Voice Volume: {value}\n");
        fw.write("item.auto_play_delay.label=Auto Advance Delay: {value}\n");
        fw.write("item.skip_unread.label=Skip Unread Text: {value}\n");
        fw.write("item.skip_after_choices.label=Skip After Choices: {value}\n");
        fw.write("item.click_reveal_before_advance.label=Click Reveals Before Advance: {value}\n");
        fw.write("item.physics_fixed_step.label=Physics Fixed Step: {value}\n");
        fw.write("item.physics_max_substeps.label=Physics Max Substeps: {value}\n");
        fw.write("item.physics_default_friction.label=Physics Friction: {value}\n");
        fw.write("item.input_profile.label=Input Profile: {value}\n");
        fw.write("item.back.label=Back\n");
        fw.write("item.back.action=back\n");
        fw.write("item.back.style=slot\n");
      }
    }
  }

  private void writeCardBounds(FileWriter fw, String itemId, double x, double y, double width, double height)
      throws Exception {
    fw.write("item." + itemId + ".boundsX=" + String.format(Locale.US, "%.4f", x) + "\n");
    fw.write("item." + itemId + ".boundsY=" + String.format(Locale.US, "%.4f", y) + "\n");
    fw.write("item." + itemId + ".boundsWidth=" + String.format(Locale.US, "%.4f", width) + "\n");
    fw.write("item." + itemId + ".boundsHeight=" + String.format(Locale.US, "%.4f", height) + "\n");
  }

  private void createReadme(File dir,
                            String name,
                            boolean includeMenuPack,
                            boolean includeSave,
                            boolean includeSettings,
                            boolean includeDemoAssets,
                            boolean gitEnabled,
                            boolean gitInitialCommit)
      throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, "README.md"))) {
      fw.write("# " + name + "\n\n");
      fw.write("A visual novel project scaffolded by the JVN editor wizard.\n\n");
      if (!txtAuthor.getText().isBlank()) fw.write("**Author:** " + txtAuthor.getText().trim() + "\n\n");
      fw.write("## Enabled Modules\n\n");
      fw.write("- Sample prologue: " + (chkSampleContent.isSelected() ? "yes" : "no") + "\n");
      fw.write("- Bundled demo assets: " + (includeDemoAssets ? "yes (`assets/demo/...`)" : "no") + "\n");
      fw.write("- Menu profile pack: " + (includeMenuPack ? "yes" : "no") + "\n");
      fw.write("- Blank menus (custom): " + (shouldStartBlankMenus() ? "yes" : "no") + "\n");
      fw.write("- Save/load profiles: " + (includeSave ? "yes" : "no") + "\n");
      fw.write("- Settings profile: " + (includeSettings ? "yes" : "no") + "\n");
      fw.write("- History defaults: " + (chkHistoryBacklog.isSelected() ? "yes" : "no") + "\n\n");
      fw.write("- Git repository: " + (gitEnabled ? "yes" : "no") + "\n");
      fw.write("- Initial commit: " + (gitInitialCommit ? "yes" : "no") + "\n\n");

      fw.write("## Runtime Profile\n\n");
      int[] resolution = parseResolution();
      fw.write("- Resolution: " + resolution[0] + "x" + resolution[1] + " (" + formatAspectRatio(resolution[0], resolution[1]) + ")\n");
      fw.write("- Theme preset: " + (cmbTheme.getValue() == null ? "Dark Elegant" : cmbTheme.getValue()) + "\n");
      fw.write("- UI backend: " + (cmbRuntimeUi.getValue() == null ? "fx" : cmbRuntimeUi.getValue()) + "\n");
      fw.write("- Audio backend: " + (cmbAudioBackend.getValue() == null ? "auto" : cmbAudioBackend.getValue()) + "\n");
      fw.write("- Locale: " + (cmbLocale.getValue() == null ? "en" : cmbLocale.getValue()) + "\n");
      fw.write("- Text speed: " + (spTextSpeed == null ? 35 : spTextSpeed.getValue()) + "\n");
      fw.write("- Auto delay: " + (spAutoDelay == null ? 2000 : spAutoDelay.getValue()) + " ms\n");
      fw.write("- Volumes (bgm/sfx/voice): "
          + (spBgmVolume == null ? 0.7 : spBgmVolume.getValue()) + "/"
          + (spSfxVolume == null ? 0.8 : spSfxVolume.getValue()) + "/"
          + (spVoiceVolume == null ? 1.0 : spVoiceVolume.getValue()) + "\n\n");

      if (!txtDescription.getText().isBlank()) {
        fw.write("## Description\n\n");
        fw.write(txtDescription.getText().trim() + "\n\n");
      }

      fw.write("## Entry Points\n\n");
      fw.write("- Script: `" + ENTRY_SCRIPT_PATH + "`\n");
      fw.write("- Timeline: `" + TIMELINE_PATH + "`\n");
      fw.write("- Settings: `" + SETTINGS_PATH + "`\n");
      fw.write("- Dialogue layout: `" + DIALOGUE_LAYOUT_PATH + "`\n\n");
      if (includeMenuPack) {
        fw.write("- Menu registry: `" + MENU_REGISTRY_PATH + "`\n");
        fw.write("- Menu theme: `" + MENU_THEME_PATH + "`\n\n");
      }

      if (gitEnabled) {
        fw.write("## Version Control\n\n");
        fw.write("- Repo initialized with Git.\n");
        fw.write("- Default ignore rules added via `.gitignore`.\n\n");
      }

      fw.write("## Project Structure\n\n");
      fw.write("```\n");
      fw.write(buildStructurePreviewText(sanitizeName(name)));
      fw.write("```\n\n");

      fw.write("## First Steps\n\n");
      int step = 1;
      fw.write(step++ + ". Open this folder in the JVN Editor.\n");
      fw.write(step++ + ". Edit `" + ENTRY_SCRIPT_PATH + "`.\n");
      fw.write(step++ + ". Edit `" + DIALOGUE_LAYOUT_PATH + "` in text first, then run runtime to validate.\n");
      if (includeMenuPack) {
        fw.write(step++ + ". Edit `config/menu/menus/*.menu`, `config/menu/layouts/*.layout`, and `config/menu/styles/*.style` in text first.\n");
        fw.write(step++ + ". Use visual Layout Studio tools only when needed (bounds drawing, color picking, quick sanity preview).\n");
      }
      if (shouldStartBlankMenus()) {
        fw.write(step++ + ". Create menu screens, layouts, and styles in `config/menu/` using the Layout Studio.\n");
        fw.write(step++ + ". Wire them in `config/menu/registry/menu.registry` to enable in-game save/load/settings.\n");
      }
      fw.write(step + ". Add content into `assets/` and run the project.\n");
    }
  }

  private String sanitizeName(String name) {
    if (name == null) return "";
    String s = name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    s = s.replaceAll("_+", "_");
    s = s.replaceAll("^[._-]+", "");
    s = s.replaceAll("[._-]+$", "");
    return s;
  }

  private Spinner<Integer> createIntSpinner(int min, int max, int initial, int step) {
    Spinner<Integer> spinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, step));
    spinner.setEditable(true);
    spinner.setPrefWidth(160);
    spinner.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");
    spinner.valueProperty().addListener((o, ov, nv) -> updateDerivedFields());
    return spinner;
  }

  private Spinner<Double> createDoubleSpinner(double min, double max, double initial, double step) {
    Spinner<Double> spinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step));
    spinner.setEditable(true);
    spinner.setPrefWidth(160);
    spinner.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");
    spinner.valueProperty().addListener((o, ov, nv) -> updateDerivedFields());
    return spinner;
  }

  private void showError(String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    EditorTheme.apply(alert);
    alert.setTitle("Error");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  /**
   * Returns the created project directory, or null if cancelled.
   */
  public File getCreatedProjectDir() {
    return createdProjectDir;
  }

  /**
   * Show the wizard and return the created project directory.
   */
  public static File showAndWait(Stage owner) {
    NewProjectWizard wizard = new NewProjectWizard(owner);
    wizard.showAndWait();
    return wizard.getCreatedProjectDir();
  }
}
