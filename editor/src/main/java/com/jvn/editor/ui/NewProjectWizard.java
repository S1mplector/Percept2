package com.jvn.editor.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
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

  // Form fields
  private TextField txtProjectName;
  private TextField txtAuthor;
  private TextField txtLocation;
  private ComboBox<String> cmbResolution;
  private ComboBox<String> cmbTheme;
  private CheckBox chkSampleContent;
  private CheckBox chkTitleScreen;
  private CheckBox chkSaveSystem;
  private CheckBox chkSettingsMenu;
  private CheckBox chkHistoryBacklog;
  private CheckBox chkGitInit;
  private CheckBox chkGitLfs;
  private CheckBox chkInitialCommit;
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
  private static final String MENU_LOAD_PATH = "config/menu/menus/load.menu";
  private static final String MENU_SAVE_PATH = "config/menu/menus/save.menu";
  private static final String MENU_SETTINGS_PATH = "config/menu/menus/settings.menu";
  private static final String MENU_LAYOUT_DEFAULT_PATH = "config/menu/layouts/default.layout";
  private static final String MENU_STYLE_DEFAULT_PATH = "config/menu/styles/default.style";
  private static final String BUNDLED_DEMO_ASSETS_DIR = "demo-assets";
  private static final String BUNDLED_DEMO_BG_DIR = "demo_bg_field";
  private static final String BUNDLED_DEMO_SPRITE_DIR = "demo_sprite_codel";
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
    title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
    title.setTextFill(Color.web(TEXT_PRIMARY));

    Label subtitle = new Label("Set up a clean engine-ready project structure with scripts, config, and visual editor files.");
    subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
    subtitle.setTextFill(Color.web(TEXT_SECONDARY));

    Label hint = new Label("Recommended: start with sample content, then customize layouts visually in the editor.");
    hint.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
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
        createSection("Feature Modules", "Choose the base modules to scaffold.", createFeatureModulesPane()),
        createSection("Version Control", "Initialize Git/Git LFS so multi-person collaboration works from day one.", createVersionControlPane()),
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
    titleLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 16));
    titleLabel.setTextFill(Color.web(ACCENT));

    Label subtitleLabel = new Label(subtitle);
    subtitleLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
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
    slugHint.setFont(Font.font("Segoe UI", 11));

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
        "1920x1080 (Full HD)",
        "1600x900 (HD+)",
        "1366x768 (WXGA)",
        "1280x720 (HD)",
        "960x540 (qHD)"
    );
    cmbResolution.setValue("1280x720 (HD)");
    cmbResolution.setPrefWidth(230);
    cmbResolution.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: " + TEXT_PRIMARY + ";");

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

    cmbResolution.setOnAction(e -> updateDerivedFields());
    cmbTheme.setOnAction(e -> updateDerivedFields());

    lblPreview = new Label();
    lblPreview.setTextFill(Color.web(TEXT_SECONDARY));
    lblPreview.setFont(Font.font("Segoe UI", 12));

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
    grid.add(createLabel("Menu Theme"), 0, 1);
    grid.add(cmbTheme, 1, 1);
    grid.add(createLabel("Preset"), 0, 2);
    grid.add(lblPreview, 1, 2);
    grid.add(new Label(""), 0, 3);
    grid.add(entryInfo, 1, 3);

    return grid;
  }

  private Region createFeatureModulesPane() {
    VBox box = new VBox(10);

    Label intro = new Label("These options control both scaffolding and starter content.");
    intro.setTextFill(Color.web(TEXT_SECONDARY));
    intro.setFont(Font.font("Segoe UI", 12));

    chkSampleContent = createCheckBox("Sample Prologue Script", true);
    chkTitleScreen = createCheckBox("Main Menu Profile Pack", true);
    chkSaveSystem = createCheckBox("Load/Save Menu Profiles", true);
    chkSettingsMenu = createCheckBox("Settings Menu Profile", true);
    chkHistoryBacklog = createCheckBox("History/Backlog Defaults", true);

    chkSampleContent.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
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

    GridPane options = new GridPane();
    options.setHgap(28);
    options.setVgap(8);
    options.add(chkSampleContent, 0, 0);
    options.add(chkTitleScreen, 1, 0);
    options.add(chkSaveSystem, 0, 1);
    options.add(chkSettingsMenu, 1, 1);
    options.add(chkHistoryBacklog, 0, 2);

    FlowPane details = new FlowPane();
    details.setVgap(4);
    details.setHgap(16);
    details.getChildren().addAll(
        detailTag("Sample Prologue", "Rich starter VNS with choices and state."),
        detailTag("Menu Profiles", "Creates config/menu registry, screens, layout and style."),
        detailTag("Save/Load", "Adds load.menu and save.menu defaults."),
        detailTag("Settings", "Adds settings.menu profile entries."),
        detailTag("History Defaults", "Marks backlog defaults in vn.settings.")
    );

    box.getChildren().addAll(intro, options, details);
    return box;
  }

  private Region detailTag(String title, String subtitle) {
    VBox tag = new VBox(2);
    tag.setPadding(new Insets(8, 10, 8, 10));
    tag.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-background-radius: 6;");

    Label t = new Label(title);
    t.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
    t.setTextFill(Color.web(TEXT_PRIMARY));

    Label s = new Label(subtitle);
    s.setFont(Font.font("Segoe UI", 10));
    s.setTextFill(Color.web(TEXT_MUTED));

    tag.getChildren().addAll(t, s);
    return tag;
  }

  private Region createVersionControlPane() {
    VBox box = new VBox(10);

    Label intro = new Label(
        "Prerequisite: `git` and `git lfs` installed/configured on the machine. " +
        "Wizard will scaffold `.gitignore` and `.gitattributes` defaults."
    );
    intro.setWrapText(true);
    intro.setTextFill(Color.web(TEXT_SECONDARY));
    intro.setFont(Font.font("Segoe UI", 12));

    chkGitInit = createCheckBox("Initialize Git repository", true);
    chkGitLfs = createCheckBox("Enable Git LFS tracking for binary assets", true);
    chkInitialCommit = createCheckBox("Create initial commit", true);

    chkGitInit.selectedProperty().addListener((o, ov, nv) -> {
      boolean enabled = nv != null && nv;
      chkGitLfs.setDisable(!enabled);
      chkInitialCommit.setDisable(!enabled);
      if (!enabled) {
        chkGitLfs.setSelected(false);
        chkInitialCommit.setSelected(false);
      } else {
        if (!chkGitLfs.isSelected()) chkGitLfs.setSelected(true);
        if (!chkInitialCommit.isSelected()) chkInitialCommit.setSelected(true);
      }
      updateDerivedFields();
    });

    chkGitLfs.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
    chkInitialCommit.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());

    Label note = new Label(
        "Default LFS patterns include common image/audio/video/font formats used by VN teams."
    );
    note.setWrapText(true);
    note.setTextFill(Color.web(TEXT_MUTED));
    note.setFont(Font.font("Segoe UI", 11));

    box.getChildren().addAll(intro, chkGitInit, chkGitLfs, chkInitialCommit, note);
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
    note.setFont(Font.font("Segoe UI", 11));

    box.getChildren().addAll(txtStructurePreview, note);
    return box;
  }

  private Region createDescriptionArea() {
    VBox box = new VBox(8);

    Label info = new Label("Optional project description:");
    info.setTextFill(Color.web(TEXT_SECONDARY));
    info.setFont(Font.font("Segoe UI", 12));

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
    lblEstimatedSize.setFont(Font.font("Segoe UI", 11));

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
    label.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
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
    cb.setFont(Font.font("Segoe UI", 12));
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
    String res = cmbResolution.getValue() == null ? "1280x720" : cmbResolution.getValue().split(" ")[0];
    String theme = cmbTheme.getValue() == null ? "Dark Elegant" : cmbTheme.getValue();
    lblPreview.setText("\"" + name + "\" • " + res + " • " + theme);
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
    long kb = 36 + estimateBundledDemoAssetsKb();
    if (chkSampleContent != null && chkSampleContent.isSelected()) kb += 8;
    if (shouldCreateMenuPack()) kb += 8;
    if (chkSaveSystem != null && chkSaveSystem.isSelected()) kb += 3;
    if (chkSettingsMenu != null && chkSettingsMenu.isSelected()) kb += 2;
    if (chkHistoryBacklog != null && chkHistoryBacklog.isSelected()) kb += 1;
    if (shouldSetupGit()) kb += 1;
    if (shouldSetupGitLfs()) kb += 1;
    return kb;
  }

  private String buildStructurePreviewText(String projectFolderName) {
    boolean includeMenuPack = shouldCreateMenuPack();
    boolean includeSave = chkSaveSystem != null && chkSaveSystem.isSelected();
    boolean includeSettings = chkSettingsMenu != null && chkSettingsMenu.isSelected();

    StringBuilder sb = new StringBuilder();
    sb.append(projectFolderName).append("/\n");
    sb.append("|-- config/\n");
    sb.append("|   |-- settings/\n");
    sb.append("|   |   `-- vn.settings\n");
    sb.append("|   |-- timeline/\n");
    sb.append("|   |   `-- story.timeline\n");
    sb.append("|   |-- ui/\n");
    sb.append("|   |   `-- dialogue.layout\n");
    if (includeMenuPack) {
      sb.append("|   `-- menu/\n");
      sb.append("|       |-- registry/\n");
      sb.append("|       |   `-- menu.registry\n");
      sb.append("|       |-- theme/\n");
      sb.append("|       |   `-- menu.theme\n");
      sb.append("|       |-- menus/\n");
      sb.append("|       |   |-- main.menu\n");
      if (includeSave) {
        sb.append("|       |   |-- load.menu\n");
        sb.append("|       |   `-- save.menu\n");
      }
      if (includeSettings) {
        sb.append("|       |   `-- settings.menu\n");
      }
      sb.append("|       |-- layouts/\n");
      sb.append("|       |   `-- default.layout\n");
      sb.append("|       |-- styles/\n");
      sb.append("|       |   `-- default.style\n");
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
    sb.append("|   |-- demo/\n");
    sb.append("|   |   |-- backgrounds/\n");
    sb.append("|   |   |   `-- field/\n");
    sb.append("|   |   `-- characters/\n");
    sb.append("|   |       `-- codel/\n");
    sb.append("|   |-- ui/\n");
    sb.append("|   |-- fonts/\n");
    sb.append("|   `-- audio/\n");
    sb.append("|       |-- bgm/\n");
    sb.append("|       |-- sfx/\n");
    sb.append("|       `-- voices/\n");
    sb.append("|-- save/\n");
    if (shouldSetupGit()) {
      sb.append("|-- .gitignore\n");
      if (shouldSetupGitLfs()) sb.append("|-- .gitattributes\n");
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
        + computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_SPRITE_DIR));
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

  private boolean shouldCreateMenuPack() {
    return (chkTitleScreen != null && chkTitleScreen.isSelected())
        || (chkSaveSystem != null && chkSaveSystem.isSelected())
        || (chkSettingsMenu != null && chkSettingsMenu.isSelected());
  }

  private boolean shouldSetupGit() {
    return chkGitInit != null && chkGitInit.isSelected();
  }

  private boolean shouldSetupGitLfs() {
    return shouldSetupGit() && chkGitLfs != null && chkGitLfs.isSelected();
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
    if (!location.exists() && !location.mkdirs()) {
      showError("Failed to create base location: " + location.getAbsolutePath());
      return;
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
      showError("Failed to create project: " + ex.getMessage());
    }
  }

  private void createProjectStructure(File dir, String displayName) throws Exception {
    boolean includeMenuPack = shouldCreateMenuPack();
    boolean includeSave = chkSaveSystem.isSelected();
    boolean includeSettings = chkSettingsMenu.isSelected();

    dir.mkdirs();
    createDirectories(dir, includeMenuPack);
    copyBundledDemoAssets(dir);

    int[] resolution = parseResolution();
    createManifest(
        dir,
        displayName,
        resolution[0],
        resolution[1],
        includeMenuPack,
        includeSave,
        includeSettings,
        shouldSetupGit(),
        shouldSetupGitLfs(),
        shouldCreateInitialCommit()
    );

    if (chkSampleContent.isSelected()) createSampleScript(dir, displayName);
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
    }

    createReadme(
        dir,
        displayName,
        includeMenuPack,
        includeSave,
        includeSettings,
        shouldSetupGit(),
        shouldSetupGitLfs(),
        shouldCreateInitialCommit()
    );

    if (shouldSetupGit()) {
      GitVcsService vcs = new GitVcsService();
      vcs.bootstrapRepository(dir, shouldSetupGitLfs(), shouldCreateInitialCommit(),
          "Initialize " + displayName + " project scaffold");
    }
  }

  private void createDirectories(File dir, boolean includeMenuPack) throws Exception {
    // Config
    ensureDirectory(dir, "config/settings");
    ensureDirectory(dir, "config/timeline");
    ensureDirectory(dir, "config/ui");
    if (includeMenuPack) {
      ensureDirectory(dir, "config/menu/registry");
      ensureDirectory(dir, "config/menu/theme");
      ensureDirectory(dir, "config/menu/menus");
      ensureDirectory(dir, "config/menu/layouts");
      ensureDirectory(dir, "config/menu/styles");
      ensureDirectory(dir, "config/menu/assets/buttons");
      ensureDirectory(dir, "config/menu/assets/icons");
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
    ensureDirectory(dir, "assets/demo/backgrounds");
    ensureDirectory(dir, "assets/demo/characters");
    ensureDirectory(dir, "assets/ui");
    ensureDirectory(dir, "assets/fonts");
    ensureDirectory(dir, "assets/audio/bgm");
    ensureDirectory(dir, "assets/audio/sfx");
    ensureDirectory(dir, "assets/audio/voices");

    // Save location
    ensureDirectory(dir, "save");
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

  private void copyBundledDemoAssets(File projectRoot) throws Exception {
    File sourceRoot = resolveBundledDemoAssetsRoot();
    if (sourceRoot == null || !sourceRoot.isDirectory()) return;

    copyDirectoryContents(
        new File(sourceRoot, BUNDLED_DEMO_BG_DIR),
        new File(projectRoot, "assets/demo/backgrounds/field")
    );
    copyDirectoryContents(
        new File(sourceRoot, BUNDLED_DEMO_SPRITE_DIR),
        new File(projectRoot, "assets/demo/characters/codel")
    );
    copyDirectoryContents(
        new File(sourceRoot, BUNDLED_DEMO_BGM_DIR),
        new File(projectRoot, "assets/demo/audio")
    );
  }

  private File resolveBundledDemoAssetsRoot() {
    File cwd = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
    File cursor = cwd;
    for (int i = 0; i < 6 && cursor != null; i++) {
      File candidate = new File(cursor, BUNDLED_DEMO_ASSETS_DIR);
      if (candidate.isDirectory()) return candidate;
      File legacyBg = new File(cursor, BUNDLED_DEMO_BG_DIR);
      File legacySprites = new File(cursor, BUNDLED_DEMO_SPRITE_DIR);
      if (legacyBg.isDirectory() && legacySprites.isDirectory()) return cursor;
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
    String raw = cmbResolution.getValue();
    if (raw == null || raw.isBlank()) return new int[] {1280, 720};
    String[] first = raw.split(" ");
    String[] parts = first[0].split("x");
    if (parts.length != 2) return new int[] {1280, 720};
    try {
      return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    } catch (Exception ignored) {
      return new int[] {1280, 720};
    }
  }

  private void createManifest(File dir,
                              String displayName,
                              int width,
                              int height,
                              boolean includeMenuPack,
                              boolean includeSave,
                              boolean includeSettings,
                              boolean gitEnabled,
                              boolean gitLfsEnabled,
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
    }
    manifest.setProperty("width", String.valueOf(width));
    manifest.setProperty("height", String.valueOf(height));
    manifest.setProperty("feature.sampleContent", Boolean.toString(chkSampleContent.isSelected()));
    manifest.setProperty("feature.titleScreen", Boolean.toString(chkTitleScreen.isSelected() && includeMenuPack));
    manifest.setProperty("feature.menuProfiles", Boolean.toString(includeMenuPack));
    manifest.setProperty("feature.saveSystem", Boolean.toString(includeSave));
    manifest.setProperty("feature.settingsMenu", Boolean.toString(includeSettings));
    manifest.setProperty("feature.historyBacklog", Boolean.toString(chkHistoryBacklog.isSelected()));
    manifest.setProperty("vcs.git.enabled", Boolean.toString(gitEnabled));
    manifest.setProperty("vcs.gitLfs.enabled", Boolean.toString(gitLfsEnabled));
    manifest.setProperty("vcs.git.initialCommit", Boolean.toString(gitInitialCommit));
    manifest.setProperty("createdBy", "jvn-editor-wizard");

    if (!txtDescription.getText().isBlank()) {
      manifest.setProperty("description", txtDescription.getText().trim());
    }

    try (FileOutputStream fos = new FileOutputStream(new File(dir, "jvn.project"))) {
      manifest.store(fos, "JVN Visual Novel Project");
    }
  }

  private void createSampleScript(File dir, String name) throws Exception {
    String scenarioId = sanitizeName(name).toLowerCase() + "_prologue";
    String script = """
        # %s - Prologue
        # Demo game created with JVN Engine
        # Tutorial-style showcase inspired by classic onboarding flows:
        # A friendly tutorial that teaches the basics of JVN scripting.

        @scenario %s

        @character codel "Codel"

        @charimg codel talking assets/demo/characters/codel/Codel1.png
        @charimg codel idle assets/demo/characters/codel/Codel2.png

        @background field_day assets/demo/backgrounds/field/field.jpg
        @background field_evening assets/demo/backgrounds/field/field.jpg

        @label start
        [bg field_day]
        [transition fade 500]
        [textspeed 28]
        [autodelay 1800]
        [bgm assets/demo/audio/softbreeze.mp3]

        [show codel center talking]
        [wait 240]

        Codel: Hi! My name is Codel, and I'd like to welcome you to the JVN tutorial.
        Codel: In this tutorial, we'll teach you the basics of JVN, so you can make visual novels of your own.
        Codel: We'll also demonstrate many features, so you can see what JVN is capable of.
        Codel: This is {b}%s{/b}, by the way. Feel free to poke around the scripts when we're done!
        [jump tutorials_hub]

        @label tutorials_hub
        [show codel left talking]
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
        Codel: You define images once with @charimg and @background, then use them by name.
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
        [show codel center talking]
        [wait 220]
        Codel: Choices let players shape the story! Let me show you.
        Codel: Quick - pick your favorite season!
        > Spring -> choice_spring
        > Summer -> choice_summer
        > Autumn -> choice_autumn
        > Winter -> choice_winter

        @label choice_spring
        Codel: Spring! New beginnings and cherry blossoms. Nice choice!
        [jump menus_done]

        @label choice_summer
        Codel: Summer! Beach episodes and festivals. Classic!
        [jump menus_done]

        @label choice_autumn
        Codel: Autumn! Cozy vibes and falling leaves. I like your style!
        [jump menus_done]

        @label choice_winter
        Codel: Winter! Hot cocoa and snow scenes. Very atmospheric!
        [jump menus_done]

        @label menus_done
        Codel: See how that worked? Each choice can lead to different dialogue or even different story paths.
        [jump tutorials_hub]

        @label end_early
        [show codel center talking]
        [wait 200]
        Codel: Thank you for checking out this tutorial!
        Codel: If you'd like to see how this demo works, take a look at {b}%s{/b}.
        Codel: You can edit it, break it, rebuild it - that's how you learn!
        Codel: We look forward to seeing what you create with JVN. Have fun!
        [end]

        """.formatted(name, scenarioId, name, ENTRY_SCRIPT_PATH);

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
      sp.setProperty("textSpeed", "35");
      sp.setProperty("bgm", "0.7");
      sp.setProperty("sfx", "0.8");
      sp.setProperty("voice", "1.0");
      sp.setProperty("autoPlayDelay", "2000");
      sp.setProperty("skipUnread", "false");
      sp.setProperty("skipAfterChoices", "false");
      sp.setProperty("historyBacklogEnabled", Boolean.toString(chkHistoryBacklog.isSelected()));
      sp.setProperty("saveProfilesEnabled", Boolean.toString(chkSaveSystem.isSelected()));
      sp.setProperty("settingsProfileEnabled", Boolean.toString(chkSettingsMenu.isSelected()));
      sp.store(fos, "VN Settings - Edit in Settings panel");
    }
  }

  private void createDialogueLayout(File dir) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, DIALOGUE_LAYOUT_PATH))) {
      fw.write("# Dialogue UI layout\n");
      fw.write("# Use the editor's visual layout tools to adjust these values.\n");
      fw.write("# choiceYStart: -1 means auto-center choices\n");
      fw.write("textBoxX=0\n");
      fw.write("textBoxY=0.75\n");
      fw.write("textBoxWidth=1\n");
      fw.write("textBoxHeight=0.25\n");
      fw.write("textBoxPadding=20\n");
      fw.write("nameBoxXOffset=20\n");
      fw.write("nameBoxYOffset=-40\n");
      fw.write("nameBoxWidth=200\n");
      fw.write("nameBoxHeight=40\n");
      fw.write("nameTextXOffset=10\n");
      fw.write("nameTextBaselineOffset=25\n");
      fw.write("dialogueTextHorizontalPadding=20\n");
      fw.write("dialogueTextTopPadding=40\n");
      fw.write("choiceXCenter=0.5\n");
      fw.write("choiceYStart=-1\n");
      fw.write("choiceWidthFactor=0.6\n");
      fw.write("choiceHeight=50\n");
      fw.write("choiceGap=10\n");
      fw.write("choiceTextXPadding=20\n");
      fw.write("\n");
      fw.write("# Optional choice button skin/style keys:\n");
      fw.write("# choiceButtonAsset=assets/ui/choice_button.png\n");
      fw.write("# choiceButtonHoverAsset=assets/ui/choice_button_hover.png\n");
      fw.write("# choiceButtonDisabledAsset=assets/ui/choice_button_disabled.png\n");
      fw.write("# choiceBackgroundColor=#323246E6\n");
      fw.write("# choiceHoverColor=#464664E6\n");
      fw.write("# choiceTextColor=#FFFFFFFF\n");
      fw.write("# choiceBorderColor=#FFFFFFFF\n");
      fw.write("# choiceCornerRadius=10\n");
      fw.write("# choiceBorderWidth=2\n");
      fw.write("# choiceTextBaselineOffset=5\n");
      fw.write("\n");
      fw.write("# Optional clickable textbox action buttons:\n");
      fw.write("# textBoxButton.ids=save,load,settings\n");
      fw.write("# textBoxButton.save.label=Save\n");
      fw.write("# textBoxButton.save.action=save_menu\n");
      fw.write("# textBoxButton.save.x=0.74\n");
      fw.write("# textBoxButton.save.y=0.08\n");
      fw.write("# textBoxButton.save.width=0.1\n");
      fw.write("# textBoxButton.save.height=0.24\n");
      fw.write("# textBoxButton.save.asset=assets/ui/save_btn.png\n");
      fw.write("# textBoxButton.load.label=Load\n");
      fw.write("# textBoxButton.load.action=load_menu\n");
      fw.write("# textBoxButton.load.x=0.85\n");
      fw.write("# textBoxButton.load.y=0.08\n");
      fw.write("# textBoxButton.load.width=0.1\n");
      fw.write("# textBoxButton.load.height=0.24\n");
    }
  }

  private void createMenuTheme(File dir, String name) throws Exception {
    String theme = cmbTheme.getValue();
    Properties tp = new Properties();

    switch (theme) {
      case "Light Clean" -> {
        tp.setProperty("backgroundColor", "#F5F5F5");
        tp.setProperty("titleColor", "#333333");
        tp.setProperty("itemColor", "#666666");
        tp.setProperty("itemSelectedColor", "#0066CC");
        tp.setProperty("accentColor", "#0066CC");
      }
      case "Retro Game" -> {
        tp.setProperty("backgroundColor", "#000000");
        tp.setProperty("titleColor", "#00FF00");
        tp.setProperty("itemColor", "#00CC00");
        tp.setProperty("itemSelectedColor", "#FFFF00");
        tp.setProperty("accentColor", "#FFFF00");
      }
      case "Nature Green" -> {
        tp.setProperty("backgroundColor", "#1A2F1A");
        tp.setProperty("titleColor", "#90EE90");
        tp.setProperty("itemColor", "#8FBC8F");
        tp.setProperty("itemSelectedColor", "#32CD32");
        tp.setProperty("accentColor", "#32CD32");
      }
      default -> {
        tp.setProperty("backgroundColor", "#0A0C12");
        tp.setProperty("titleColor", "#FFFFFF");
        tp.setProperty("itemColor", "#C0C0C0");
        tp.setProperty("itemSelectedColor", "#FFD700");
        tp.setProperty("accentColor", "#FFD700");
      }
    }

    tp.setProperty("titleFontFamily", "Arial");
    tp.setProperty("titleFontWeight", "BOLD");
    tp.setProperty("titleFontSize", "32");
    tp.setProperty("itemFontFamily", "Arial");
    tp.setProperty("itemFontWeight", "NORMAL");
    tp.setProperty("itemFontSize", "20");
    tp.setProperty("hintFontFamily", "Arial");
    tp.setProperty("hintFontSize", "14");
    tp.setProperty("titleY", "60");
    tp.setProperty("listYStart", "0.35");
    tp.setProperty("lineHeight", "40");
    tp.setProperty("itemPrefix", "  ");
    tp.setProperty("itemSelectedPrefix", "> ");
    tp.setProperty("titleText", name);

    try (FileOutputStream fos = new FileOutputStream(new File(dir, MENU_THEME_PATH))) {
      tp.store(fos, "Menu Theme for " + name + " - " + theme);
    }
  }

  private void createMenuCustomizationScaffold(File dir, String name, boolean includeSave, boolean includeSettings)
      throws Exception {

    List<String> menus = new ArrayList<>();
    menus.add("main");
    if (includeSave) {
      menus.add("load");
      menus.add("save");
    }
    if (includeSettings) {
      menus.add("settings");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_REGISTRY_PATH))) {
      fw.write("# Menu customization registry\n");
      fw.write("defaultMenu=main\n");
      fw.write("menus=" + String.join(",", menus) + "\n");
      fw.write("layouts=default\n");
      fw.write("styles=default\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_LAYOUT_DEFAULT_PATH))) {
      fw.write("# Layout for vertical menu lists\n");
      fw.write("listYStart=0.35\n");
      fw.write("lineHeight=40\n");
      fw.write("listWidthFactor=1.0\n");
      fw.write("textAlign=center\n");
      fw.write("hintsBottomMargin=20\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_STYLE_DEFAULT_PATH))) {
      fw.write("# Optional per-item style overrides (falls back to menu.theme)\n");
      fw.write("itemPrefix=  \n");
      fw.write("itemSelectedPrefix=> \n");
      fw.write("itemDisabledPrefix=- \n");
      fw.write("itemDisabledColor=#808080\n");
      fw.write("# buttonAsset=assets/ui/menu/button.png\n");
      fw.write("# buttonSelectedAsset=assets/ui/menu/button_selected.png\n");
      fw.write("# buttonDisabledAsset=assets/ui/menu/button_disabled.png\n");
      fw.write("buttonTextPaddingX=18\n");
      fw.write("buttonTextPaddingY=0\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_MAIN_PATH))) {
      fw.write("# Main menu screen definition\n");
      fw.write("titleText=" + name + "\n");
      fw.write("hintsText=Select: Enter    Back: Esc\n");
      fw.write("layout=default\n");
      fw.write("defaultItemStyle=default\n");
      fw.write("wrapSelection=true\n");

      List<String> items = new ArrayList<>();
      items.add("new_game");
      if (includeSave) items.add("load");
      if (includeSettings) items.add("settings");
      items.add("quit");
      fw.write("items=" + String.join(",", items) + "\n");

      fw.write("item.new_game.action=new_game\n");
      if (includeSave) fw.write("item.load.action=load_menu\n");
      if (includeSettings) fw.write("item.settings.action=settings_menu\n");
      fw.write("item.quit.action=quit\n");
    }

    if (includeSave) {
      try (FileWriter fw = new FileWriter(new File(dir, MENU_LOAD_PATH))) {
        fw.write("# Load menu presentation profile\n");
        fw.write("titleText=Load Game\n");
        fw.write("hintsText=Select: Enter    Back: Esc    Delete: Del    Rename: R\n");
        fw.write("layout=default\n");
        fw.write("defaultItemStyle=default\n");
        fw.write("wrapSelection=true\n");
        fw.write("item.save_slot.slotPreviewEnabled=true\n");
        fw.write("# item.save_slot.bgAsset=config/menu/assets/buttons/slot.png\n");
        fw.write("# item.save_slot.bgSelectedAsset=config/menu/assets/buttons/slot_hover.png\n");
        fw.write("# item.save_slot.slotPreviewPlaceholderAsset=config/menu/assets/buttons/slot_empty.png\n");
        fw.write("# item.save_slot.slotPreviewFrameAsset=config/menu/assets/buttons/slot_frame.png\n");
        fw.write("# item.save_slot.slotPreviewX=0.62\n");
        fw.write("# item.save_slot.slotPreviewY=0.1\n");
        fw.write("# item.save_slot.slotPreviewWidth=0.34\n");
        fw.write("# item.save_slot.slotPreviewHeight=0.8\n");
      }

      try (FileWriter fw = new FileWriter(new File(dir, MENU_SAVE_PATH))) {
        fw.write("# Save menu presentation profile\n");
        fw.write("titleText=Save Game\n");
        fw.write("hintsText=Select: Enter    Back: Esc    Delete: Del    Rename: R\n");
        fw.write("layout=default\n");
        fw.write("defaultItemStyle=default\n");
        fw.write("wrapSelection=true\n");
        fw.write("item.new_slot.label=New Save...\n");
        fw.write("item.new_slot.slotPreviewEnabled=true\n");
        fw.write("item.save_slot.slotPreviewEnabled=true\n");
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
        fw.write("titleText=Settings\n");
        fw.write("hintsText=Up/Down, Left/Right, Enter    Back: Esc\n");
        fw.write("layout=default\n");
        fw.write("defaultItemStyle=default\n");
        fw.write("wrapSelection=true\n");
        fw.write("items=text_speed,bgm_volume,sfx_volume,voice_volume,auto_play_delay,skip_unread,skip_after_choices,physics_fixed_step,physics_max_substeps,physics_default_friction,input_profile,back\n");
        fw.write("item.back.label=Back\n");
        fw.write("item.back.action=back\n");
      }
    }
  }

  private void createReadme(File dir,
                            String name,
                            boolean includeMenuPack,
                            boolean includeSave,
                            boolean includeSettings,
                            boolean gitEnabled,
                            boolean gitLfsEnabled,
                            boolean gitInitialCommit)
      throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, "README.md"))) {
      fw.write("# " + name + "\n\n");
      fw.write("A visual novel project scaffolded by the JVN editor wizard.\n\n");
      if (!txtAuthor.getText().isBlank()) fw.write("**Author:** " + txtAuthor.getText().trim() + "\n\n");
      fw.write("## Enabled Modules\n\n");
      fw.write("- Sample prologue: " + (chkSampleContent.isSelected() ? "yes" : "no") + "\n");
      fw.write("- Bundled demo assets: yes (`assets/demo/...`)\n");
      fw.write("- Menu profile pack: " + (includeMenuPack ? "yes" : "no") + "\n");
      fw.write("- Save/load profiles: " + (includeSave ? "yes" : "no") + "\n");
      fw.write("- Settings profile: " + (includeSettings ? "yes" : "no") + "\n");
      fw.write("- History defaults: " + (chkHistoryBacklog.isSelected() ? "yes" : "no") + "\n\n");
      fw.write("- Git repository: " + (gitEnabled ? "yes" : "no") + "\n");
      fw.write("- Git LFS defaults: " + (gitLfsEnabled ? "yes" : "no") + "\n");
      fw.write("- Initial commit: " + (gitInitialCommit ? "yes" : "no") + "\n\n");

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
        if (gitLfsEnabled) fw.write("- Git LFS tracking defaults added via `.gitattributes`.\n");
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
      fw.write(step++ + ". Tune `" + DIALOGUE_LAYOUT_PATH + "` with the visual layout editor.\n");
      if (includeMenuPack) {
        fw.write(step++ + ". Edit `config/menu/menus/*.menu` and `config/menu/layouts/*.layout` in visual editors.\n");
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
