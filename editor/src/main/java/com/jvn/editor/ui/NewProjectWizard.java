package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Properties;

/**
 * Sophisticated project creation wizard for VN projects.
 * Replaces the simple popup dialogs with a modern multi-section interface.
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
  private TextArea txtDescription;
  private Label lblPreview;
  
  // Theme colors
  private static final String BG_DARK = "#0f0f10";
  private static final String BG_CARD = "#17181a";
  private static final String BG_FIELD = "#2a2a2a";
  private static final String ACCENT = "#4a9eff";
  private static final String TEXT_SECONDARY = "#9aa0a6";
  private static final String ENTRY_SCRIPT_PATH = "scripts/story/prologue.vns";
  private static final String TIMELINE_PATH = "config/timeline/story.timeline";
  private static final String SETTINGS_PATH = "config/settings/vn.settings";
  private static final String MENU_THEME_PATH = "config/menu/menu.theme";
  private static final String MENU_REGISTRY_PATH = "config/menu/menu.registry";
  private static final String MENU_MAIN_PATH = "config/menu/menus/main.menu";
  private static final String MENU_LAYOUT_DEFAULT_PATH = "config/menu/layouts/default.layout";
  private static final String MENU_STYLE_DEFAULT_PATH = "config/menu/styles/default.style";
  
  public NewProjectWizard(Stage owner) {
    initOwner(owner);
    initModality(Modality.APPLICATION_MODAL);
    initStyle(StageStyle.DECORATED);
    setTitle("Create New Visual Novel Project");
    setWidth(800);
    setHeight(650);
    setResizable(false);
    
    BorderPane root = new BorderPane();
    root.setStyle("-fx-background-color: " + BG_DARK + ";");
    
    // Header
    VBox header = createHeader();
    root.setTop(header);
    
    // Main content - scrollable form
    ScrollPane scrollPane = new ScrollPane(createMainContent());
    scrollPane.setFitToWidth(true);
    scrollPane.setStyle("-fx-background: " + BG_DARK + "; -fx-background-color: " + BG_DARK + ";");
    root.setCenter(scrollPane);
    
    // Footer with buttons
    HBox footer = createFooter();
    root.setBottom(footer);
    
    Scene scene = new Scene(root);
    EditorTheme.apply(scene);
    setScene(scene);
  }
  
  private VBox createHeader() {
    VBox header = new VBox(8);
    header.setPadding(new Insets(20, 30, 15, 30));
    header.setStyle("-fx-background-color: " + BG_CARD + ";");
    
    Label title = new Label("Create New Visual Novel");
    title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
    title.setTextFill(Color.WHITE);
    
    Label subtitle = new Label("Configure your project settings and choose which features to include.");
    subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
    subtitle.setTextFill(Color.web(TEXT_SECONDARY));
    
    header.getChildren().addAll(title, subtitle);
    return header;
  }
  
  private VBox createMainContent() {
    VBox content = new VBox(20);
    content.setPadding(new Insets(20, 30, 20, 30));
    
    // Project Info Section
    VBox projectInfo = createSection("Project Information", createProjectInfoGrid());
    
    // Display Settings Section
    VBox displaySettings = createSection("Display Settings", createDisplaySettingsGrid());
    
    // Features Section
    VBox features = createSection("Features", createFeaturesGrid());
    
    // Description Section
    VBox description = createSection("Project Description", createDescriptionArea());
    
    content.getChildren().addAll(projectInfo, displaySettings, features, description);
    return content;
  }
  
  private VBox createSection(String title, Region content) {
    VBox section = new VBox(12);
    section.setPadding(new Insets(15));
    section.setStyle("-fx-background-color: " + BG_CARD + "; -fx-background-radius: 8;");
    
    Label titleLabel = new Label(title);
    titleLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 16));
    titleLabel.setTextFill(Color.web(ACCENT));
    
    Separator sep = new Separator();
    sep.setStyle("-fx-background-color: #333333;");
    
    section.getChildren().addAll(titleLabel, sep, content);
    return section;
  }
  
  private GridPane createProjectInfoGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(15);
    grid.setVgap(12);
    
    // Project Name
    Label lblName = createLabel("Project Name:");
    txtProjectName = createTextField("My Visual Novel");
    txtProjectName.textProperty().addListener((o, ov, nv) -> updatePreview());
    
    // Author
    Label lblAuthor = createLabel("Author:");
    txtAuthor = createTextField("Anonymous");
    
    // Location
    Label lblLocation = createLabel("Location:");
    txtLocation = createTextField(System.getProperty("user.home") + "/JVN Projects");
    txtLocation.setPrefWidth(350);
    Button btnBrowse = new Button("Browse...");
    btnBrowse.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: white;");
    btnBrowse.setOnAction(e -> browseLocation());
    HBox locationBox = new HBox(8, txtLocation, btnBrowse);
    HBox.setHgrow(txtLocation, Priority.ALWAYS);
    
    grid.add(lblName, 0, 0);
    grid.add(txtProjectName, 1, 0);
    grid.add(lblAuthor, 0, 1);
    grid.add(txtAuthor, 1, 1);
    grid.add(lblLocation, 0, 2);
    grid.add(locationBox, 1, 2);
    
    GridPane.setHgrow(txtProjectName, Priority.ALWAYS);
    GridPane.setHgrow(txtAuthor, Priority.ALWAYS);
    GridPane.setHgrow(locationBox, Priority.ALWAYS);
    
    return grid;
  }
  
  private GridPane createDisplaySettingsGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(15);
    grid.setVgap(12);
    
    // Resolution
    Label lblRes = createLabel("Resolution:");
    cmbResolution = new ComboBox<>();
    cmbResolution.getItems().addAll(
      "1920x1080 (Full HD)",
      "1280x720 (HD)",
      "1600x900 (HD+)",
      "960x540 (qHD)",
      "800x600 (SVGA)"
    );
    cmbResolution.setValue("1280x720 (HD)");
    cmbResolution.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: white;");
    cmbResolution.setPrefWidth(200);
    
    // Theme
    Label lblTheme = createLabel("Color Theme:");
    cmbTheme = new ComboBox<>();
    cmbTheme.getItems().addAll(
      "Dark Elegant",
      "Light Clean",
      "Retro Game",
      "Romantic Pink",
      "Cyberpunk Neon",
      "Nature Green",
      "Custom"
    );
    cmbTheme.setValue("Dark Elegant");
    cmbTheme.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: white;");
    cmbTheme.setPrefWidth(200);
    
    // Preview
    lblPreview = new Label("Preview: Dark Elegant theme at 1280x720");
    lblPreview.setTextFill(Color.web(TEXT_SECONDARY));
    lblPreview.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
    
    cmbResolution.setOnAction(e -> updatePreview());
    cmbTheme.setOnAction(e -> updatePreview());
    
    grid.add(lblRes, 0, 0);
    grid.add(cmbResolution, 1, 0);
    grid.add(lblTheme, 0, 1);
    grid.add(cmbTheme, 1, 1);
    grid.add(lblPreview, 0, 2, 2, 1);
    
    return grid;
  }
  
  private VBox createFeaturesGrid() {
    VBox features = new VBox(10);
    
    Label info = new Label("Select which features to include in your project:");
    info.setTextFill(Color.web(TEXT_SECONDARY));
    info.setFont(Font.font("Segoe UI", 12));
    
    // Feature checkboxes in a flow layout
    FlowPane checkboxes = new FlowPane(15, 10);
    
    chkSampleContent = createCheckBox("Sample Scene", true);
    chkTitleScreen = createCheckBox("Title Screen", true);
    chkSaveSystem = createCheckBox("Save/Load System", true);
    chkSettingsMenu = createCheckBox("Settings Menu", true);
    chkHistoryBacklog = createCheckBox("History Backlog", true);
    
    checkboxes.getChildren().addAll(
      chkSampleContent,
      chkTitleScreen,
      chkSaveSystem,
      chkSettingsMenu,
      chkHistoryBacklog
    );
    
    // Feature descriptions
    VBox descriptions = new VBox(4);
    descriptions.setPadding(new Insets(10, 0, 0, 0));
    
    Label descTitle = new Label("Feature Details:");
    descTitle.setTextFill(Color.web(TEXT_SECONDARY));
    descTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
    
    Label desc = new Label(
      "• Sample Scene: Creates a starter prologue.vns script with example dialogue\n" +
      "• Title Screen: Configurable main menu with background, logo, and music support\n" +
      "• Save/Load: Multiple save slots with thumbnails and timestamps\n" +
      "• Settings Menu: In-game settings for text speed, volume, and more\n" +
      "• History Backlog: Scrollable dialogue history (press B in-game)"
    );
    desc.setTextFill(Color.web(TEXT_SECONDARY));
    desc.setFont(Font.font("Segoe UI", 11));
    desc.setWrapText(true);
    
    descriptions.getChildren().addAll(descTitle, desc);
    
    features.getChildren().addAll(info, checkboxes, descriptions);
    return features;
  }
  
  private VBox createDescriptionArea() {
    VBox box = new VBox(8);
    
    Label info = new Label("Optional description for your project (shown in project info):");
    info.setTextFill(Color.web(TEXT_SECONDARY));
    info.setFont(Font.font("Segoe UI", 12));
    
    txtDescription = new TextArea();
    txtDescription.setPromptText("A visual novel about...");
    txtDescription.setPrefRowCount(3);
    txtDescription.setWrapText(true);
    txtDescription.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: white; -fx-control-inner-background: " + BG_FIELD + ";");
    
    box.getChildren().addAll(info, txtDescription);
    return box;
  }
  
  private HBox createFooter() {
    HBox footer = new HBox(15);
    footer.setPadding(new Insets(15, 30, 20, 30));
    footer.setAlignment(Pos.CENTER_RIGHT);
    footer.setStyle("-fx-background-color: " + BG_CARD + ";");
    
    Button btnCancel = new Button("Cancel");
    btnCancel.setPrefWidth(100);
    btnCancel.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: white;");
    btnCancel.setOnAction(e -> close());
    
    Button btnCreate = new Button("Create Project");
    btnCreate.setPrefWidth(140);
    btnCreate.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-font-weight: bold;");
    btnCreate.setOnAction(e -> createProject());
    
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    
    // Project size estimate
    Label lblSize = new Label("Estimated size: ~50 KB");
    lblSize.setTextFill(Color.web(TEXT_SECONDARY));
    lblSize.setFont(Font.font("Segoe UI", 11));
    
    footer.getChildren().addAll(lblSize, spacer, btnCancel, btnCreate);
    return footer;
  }
  
  private Label createLabel(String text) {
    Label lbl = new Label(text);
    lbl.setTextFill(Color.WHITE);
    lbl.setFont(Font.font("Segoe UI", 13));
    lbl.setMinWidth(100);
    return lbl;
  }
  
  private TextField createTextField(String defaultValue) {
    TextField tf = new TextField(defaultValue);
    tf.setStyle("-fx-background-color: " + BG_FIELD + "; -fx-text-fill: white;");
    tf.setPrefWidth(250);
    return tf;
  }
  
  private CheckBox createCheckBox(String text, boolean selected) {
    CheckBox cb = new CheckBox(text);
    cb.setSelected(selected);
    cb.setTextFill(Color.WHITE);
    cb.setFont(Font.font("Segoe UI", 13));
    return cb;
  }
  
  private void browseLocation() {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setTitle("Choose Project Location");
    File initial = new File(txtLocation.getText());
    if (initial.exists()) dc.setInitialDirectory(initial);
    File dir = dc.showDialog(this);
    if (dir != null) txtLocation.setText(dir.getAbsolutePath());
  }
  
  private void updatePreview() {
    String theme = cmbTheme.getValue();
    String res = cmbResolution.getValue();
    String name = txtProjectName.getText();
    if (name.isBlank()) name = "Untitled";
    lblPreview.setText("Preview: \"" + name + "\" • " + theme + " theme • " + res.split(" ")[0]);
  }
  
  private void createProject() {
    String name = txtProjectName.getText().trim();
    if (name.isEmpty()) {
      showError("Please enter a project name.");
      return;
    }
    
    File location = new File(txtLocation.getText().trim());
    if (!location.exists()) {
      location.mkdirs();
    }
    
    File projectDir = new File(location, sanitizeName(name));
    if (projectDir.exists()) {
      showError("A project with this name already exists at the specified location.");
      return;
    }
    
    try {
      createProjectStructure(projectDir, name);
      createdProjectDir = projectDir;
      close();
    } catch (Exception e) {
      showError("Failed to create project: " + e.getMessage());
    }
  }
  
  private void createProjectStructure(File dir, String name) throws Exception {
    dir.mkdirs();
    
    // Parse resolution
    String resStr = cmbResolution.getValue().split(" ")[0];
    String[] resParts = resStr.split("x");
    int width = Integer.parseInt(resParts[0]);
    int height = Integer.parseInt(resParts[1]);
    
    // Create directories
    new File(dir, "config").mkdirs();
    new File(dir, "config/settings").mkdirs();
    new File(dir, "config/timeline").mkdirs();
    new File(dir, "config/menu").mkdirs();
    new File(dir, "config/menu/assets").mkdirs();
    new File(dir, "config/menu/assets/buttons").mkdirs();
    new File(dir, "config/menu/menus").mkdirs();
    new File(dir, "config/menu/layouts").mkdirs();
    new File(dir, "config/menu/styles").mkdirs();
    new File(dir, "scripts/story").mkdirs();
    new File(dir, "scripts/system").mkdirs();
    new File(dir, "assets/characters").mkdirs();
    new File(dir, "assets/backgrounds").mkdirs();
    new File(dir, "assets/cg").mkdirs();
    new File(dir, "assets/ui").mkdirs();
    new File(dir, "assets/audio/bgm").mkdirs();
    new File(dir, "assets/audio/sfx").mkdirs();
    new File(dir, "assets/audio/voices").mkdirs();
    
    // Project manifest
    Properties manifest = new Properties();
    manifest.setProperty("name", name);
    manifest.setProperty("author", txtAuthor.getText().trim());
    manifest.setProperty("type", "vn");
    manifest.setProperty("entryVns", ENTRY_SCRIPT_PATH);
    manifest.setProperty("entryLabel", "start");
    manifest.setProperty("timeline", TIMELINE_PATH);
    manifest.setProperty("settingsFile", SETTINGS_PATH);
    manifest.setProperty("menuTheme", MENU_THEME_PATH);
    manifest.setProperty("width", String.valueOf(width));
    manifest.setProperty("height", String.valueOf(height));
    if (!txtDescription.getText().isBlank()) {
      manifest.setProperty("description", txtDescription.getText().trim());
    }
    try (FileOutputStream fos = new FileOutputStream(new File(dir, "jvn.project"))) {
      manifest.store(fos, "JVN Visual Novel Project");
    }
    
    // Sample script
    if (chkSampleContent.isSelected()) {
      createSampleScript(dir, name);
    } else {
      createEmptyScript(dir, name);
    }
    
    // Timeline
    try (FileWriter fw = new FileWriter(new File(dir, TIMELINE_PATH))) {
      fw.write("# Story Timeline for " + name + "\n");
      fw.write("# Author: " + txtAuthor.getText().trim() + "\n\n");
      fw.write("arc \"Prologue\" script \"" + ENTRY_SCRIPT_PATH + "\" entry \"start\" at 40,40\n");
    }
    
    // Settings
    createSettings(dir);
    
    // Menu theme
    createMenuTheme(dir, name);
    createMenuCustomizationScaffold(dir, name);
    
    // README
    createReadme(dir, name);
  }
  
  private void createSampleScript(File dir, String name) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, ENTRY_SCRIPT_PATH))) {
      fw.write("# " + name + " - Prologue\n");
      fw.write("# Created with JVN Engine\n\n");
      fw.write("@scenario " + sanitizeName(name).toLowerCase() + "_prologue\n");
      fw.write("@character narrator \"Narrator\"\n");
      fw.write("@character hero \"Hero\"\n");
      fw.write("@character guide \"Guide\"\n");
      fw.write("@background classroom assets/backgrounds/classroom_day.png\n");
      fw.write("@background sunset assets/backgrounds/sunset_street.png\n\n");

      fw.write("@label start\n");
      fw.write("[bg classroom]\n");
      fw.write("[transition fade 400]\n");
      fw.write("[set playerName Player]\n");
      fw.write("[set courage 0]\n\n");

      fw.write("Narrator: Welcome to {b}" + name + "{/b}.\n");
      fw.write("Narrator: This starter scene showcases choices, variables, transitions, and text effects.\n");
      fw.write("Hero: I'm {color=#4a9eff}${playerName}{/color}. Ready to test the engine?\n");
      fw.write("[show hero center neutral]\n\n");

      fw.write("> Ask for a quick tour -> tour\n");
      fw.write("> Skip ahead to the ending -> speedrun\n\n");

      fw.write("@label tour\n");
      fw.write("[inc courage 1]\n");
      fw.write("[show guide right smile]\n");
      fw.write("Guide: Nice! Watch this: {wave}smooth motion{/wave} and {shake}dramatic impact{/shake}.\n");
      fw.write("[screen flash 0.35 160 1 1 1]\n");
      fw.write("Narrator: You can branch story flow and track state with variables.\n\n");

      fw.write("> Step forward confidently [if courage >= 1] -> brave\n");
      fw.write("> Stay cautious -> cautious\n\n");

      fw.write("@label brave\n");
      fw.write("[transition crossfade 700 sunset]\n");
      fw.write("Hero: Then let's make a bold first chapter.\n");
      fw.write("Narrator: Add your own sprites, music, and branching paths in " + ENTRY_SCRIPT_PATH + ".\n");
      fw.write("[jump ending]\n\n");

      fw.write("@label cautious\n");
      fw.write("Hero: Let's keep it simple for now.\n");
      fw.write("Narrator: Good call. You can build this scene step-by-step in the editor.\n");
      fw.write("[jump ending]\n\n");

      fw.write("@label speedrun\n");
      fw.write("Narrator: No problem. Sometimes a short route is the best route.\n");
      fw.write("[jump ending]\n\n");

      fw.write("@label ending\n");
      fw.write("Narrator: {wave}The End{/wave} - now make this story your own.\n\n");
      fw.write("[end]\n");
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
      sp.store(fos, "VN Settings - Edit in Settings panel");
    }
  }
  
  private void createMenuTheme(File dir, String name) throws Exception {
    String theme = cmbTheme.getValue();
    Properties tp = new Properties();
    
    // Apply theme preset
    switch (theme) {
      case "Dark Elegant" -> {
        tp.setProperty("backgroundColor", "#0A0C12");
        tp.setProperty("titleColor", "#FFFFFF");
        tp.setProperty("itemColor", "#C0C0C0");
        tp.setProperty("itemSelectedColor", "#FFD700");
        tp.setProperty("accentColor", "#FFD700");
      }
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
      case "Romantic Pink" -> {
        tp.setProperty("backgroundColor", "#2D1F2D");
        tp.setProperty("titleColor", "#FFB6C1");
        tp.setProperty("itemColor", "#DDA0DD");
        tp.setProperty("itemSelectedColor", "#FF69B4");
        tp.setProperty("accentColor", "#FF69B4");
      }
      case "Cyberpunk Neon" -> {
        tp.setProperty("backgroundColor", "#0D0221");
        tp.setProperty("titleColor", "#00FFFF");
        tp.setProperty("itemColor", "#FF00FF");
        tp.setProperty("itemSelectedColor", "#00FF00");
        tp.setProperty("accentColor", "#FF00FF");
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
    
    // Common settings
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
    
    // Title text
    tp.setProperty("titleText", name);
    
    try (FileOutputStream fos = new FileOutputStream(new File(dir, MENU_THEME_PATH))) {
      tp.store(fos, "Menu Theme for " + name + " - " + theme);
    }
  }

  private void createMenuCustomizationScaffold(File dir, String name) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, MENU_REGISTRY_PATH))) {
      fw.write("# Menu customization registry\n");
      fw.write("defaultMenu=main\n");
      fw.write("menus=main\n");
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
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_MAIN_PATH))) {
      fw.write("# Main menu screen definition\n");
      fw.write("titleText=" + name + "\n");
      fw.write("hintsText=Select: Enter    Back: Esc\n");
      fw.write("layout=default\n");
      fw.write("defaultItemStyle=default\n");
      fw.write("wrapSelection=true\n");
      fw.write("items=new_game,load,settings,quit\n");
      fw.write("item.new_game.action=new_game\n");
      fw.write("item.load.action=load_menu\n");
      fw.write("item.settings.action=settings_menu\n");
      fw.write("item.quit.action=quit\n");
    }
  }
  
  private void createReadme(File dir, String name) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, "README.md"))) {
      fw.write("# " + name + "\n\n");
      fw.write("A visual novel created with the JVN Engine.\n\n");
      if (!txtAuthor.getText().isBlank()) {
        fw.write("**Author:** " + txtAuthor.getText().trim() + "\n\n");
      }
      if (!txtDescription.getText().isBlank()) {
        fw.write("## Description\n\n");
        fw.write(txtDescription.getText().trim() + "\n\n");
      }
      fw.write("## Project Structure\n\n");
      fw.write("```\n");
      fw.write(name + "/\n");
      fw.write("├── config/\n");
      fw.write("│   ├── settings/\n");
      fw.write("│   │   └── vn.settings      # Project-local VN settings\n");
      fw.write("│   ├── timeline/\n");
      fw.write("│   │   └── story.timeline   # Visual story graph\n");
      fw.write("│   └── menu/\n");
      fw.write("│       ├── menu.theme       # Global title/menu visual theme\n");
      fw.write("│       ├── menu.registry    # Declares screens/layouts/styles to load\n");
      fw.write("│       ├── menus/\n");
      fw.write("│       │   └── main.menu    # Menu items/actions/screen text\n");
      fw.write("│       ├── layouts/\n");
      fw.write("│       │   └── default.layout # Placement/spacing/alignment rules\n");
      fw.write("│       ├── styles/\n");
      fw.write("│       │   └── default.style # Per-button style overrides\n");
      fw.write("│       └── assets/\n");
      fw.write("│           └── buttons/     # Menu button sprites/icons\n");
      fw.write("├── scripts/\n");
      fw.write("│   ├── story/\n");
      fw.write("│   │   └── prologue.vns # Entry point script\n");
      fw.write("│   └── system/            # Reserved for helper scripts\n");
      fw.write("├── assets/\n");
      fw.write("│   ├── characters/    # Character sprites\n");
      fw.write("│   ├── backgrounds/   # Background images\n");
      fw.write("│   ├── cg/            # CG/event images\n");
      fw.write("│   ├── ui/            # UI elements\n");
      fw.write("│   └── audio/\n");
      fw.write("│       ├── bgm/       # Background music\n");
      fw.write("│       ├── sfx/       # Sound effects\n");
      fw.write("│       └── voices/    # Voice clips\n");
      fw.write("└── jvn.project        # Project manifest\n");
      fw.write("```\n\n");
      fw.write("## Getting Started\n\n");
      fw.write("1. Open this project in the JVN Editor\n");
      fw.write("2. Edit `scripts/story/prologue.vns` to write your story\n");
      fw.write("3. Configure VN settings in `config/settings/vn.settings`\n");
      fw.write("4. Customize title/menu theme in `config/menu/menu.theme`\n");
      fw.write("5. Add menu screens in `config/menu/menus/*.menu`\n");
      fw.write("6. Add character sprites to `assets/characters/`\n");
      fw.write("7. Add backgrounds to `assets/backgrounds/`\n");
      fw.write("8. Press **Run Project** to preview\n\n");
      fw.write("## Documentation\n\n");
      fw.write("- [VNS Scripting Guide](docs/VNS%20Scripting/)\n");
      fw.write("- [Title Screen Configuration](docs/TitleScreen.md)\n");
      fw.write("- [Text Effects](docs/TextEffects.md)\n");
    }
  }
  
  private String sanitizeName(String name) {
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
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
