package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Sidebar utility that launches layout-related editors and reports whether
 * project files are still default or have been customized.
 */
public class LayoutEditorLauncherView extends BorderPane {
  private static final String DEFAULT_DIALOGUE_LAYOUT_PATH = "config/ui/dialogue.layout";
  private static final String DEFAULT_MENU_LAYOUT_PATH = "config/menu/layouts/default.layout";
  private static final String DEFAULT_MENU_STYLE_PATH = "config/menu/styles/default.style";
  private static final String DEFAULT_MENU_REGISTRY_PATH = "config/menu/registry/menu.registry";

  private final TextField filterField = new TextField();
  private final Label summaryLabel = new Label("No project loaded.");
  private final VBox itemList = new VBox(6);
  private final VBox registryPanel = new VBox(6);

  private File projectRoot;
  private Consumer<File> onOpenFile;
  private final List<LayoutItem> cachedItems = new ArrayList<>();

  // Known IDs for validation and quick-assign
  private final Set<String> knownLayoutIds = new LinkedHashSet<>();
  private final Set<String> knownStyleIds = new LinkedHashSet<>();
  private final Set<String> knownScreenIds = new LinkedHashSet<>();

  // Registry state
  private String registryDefaultMenu = "";
  private final Set<String> registryMenus = new LinkedHashSet<>();
  private final Set<String> registryLayouts = new LinkedHashSet<>();
  private final Set<String> registryStyles = new LinkedHashSet<>();

  private enum ItemType {
    DIALOGUE_LAYOUT,
    MENU_LAYOUT,
    MENU_STYLE,
    MENU_SCREEN
  }

  private enum StatusKind {
    DEFAULT,
    CUSTOMIZED,
    MISSING
  }

  private record LayoutItem(
      String title,
      String relativePath,
      ItemType type,
      StatusKind status,
      String detail,
      String layoutRef,
      String styleRef,
      List<String> actionTargets,
      List<String> warnings
  ) {
    LayoutItem(String title, String relativePath, ItemType type, StatusKind status, String detail) {
      this(title, relativePath, type, status, detail, null, null, List.of(), List.of());
    }
  }

  public LayoutEditorLauncherView() {
    setPadding(new Insets(8));

    Label title = new Label("Layout Editors");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

    filterField.setPromptText("Filter layout files...");
    filterField.textProperty().addListener((o, ov, nv) -> renderItemList());

    Button refreshButton = new Button("Refresh");
    refreshButton.setGraphic(CssIcon.redo("#7ec8e3"));
    refreshButton.setOnAction(e -> refreshStatus());

    HBox topActions = new HBox(8, filterField, refreshButton);
    topActions.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(filterField, Priority.ALWAYS);

    VBox top = new VBox(8, title, summaryLabel, topActions, new Separator());
    setTop(top);

    ScrollPane scroll = new ScrollPane(itemList);
    scroll.setFitToWidth(true);
    setCenter(scroll);
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    refreshStatus();
  }

  public void setOnOpenFile(Consumer<File> onOpenFile) {
    this.onOpenFile = onOpenFile;
  }

  public void refreshStatus() {
    cachedItems.clear();
    knownLayoutIds.clear();
    knownStyleIds.clear();
    knownScreenIds.clear();
    registryMenus.clear();
    registryLayouts.clear();
    registryStyles.clear();
    registryDefaultMenu = "";
    if (projectRoot == null || !projectRoot.isDirectory()) {
      summaryLabel.setText("Open a project to inspect layout customization status.");
      itemList.getChildren().clear();
      return;
    }

    cachedItems.addAll(collectItems(projectRoot));
    updateSummary();
    renderItemList();
  }

  private void updateSummary() {
    int customized = 0;
    int defaults = 0;
    int missing = 0;
    int warnings = 0;
    for (LayoutItem item : cachedItems) {
      if (item.status() == StatusKind.CUSTOMIZED) customized++;
      else if (item.status() == StatusKind.DEFAULT) defaults++;
      else missing++;
      warnings += item.warnings().size();
    }
    String text = "Customized: " + customized + "  |  Defaults: " + defaults + "  |  Missing: " + missing;
    if (warnings > 0) text += "  |  Warnings: " + warnings;
    summaryLabel.setText(text);
  }

  private void renderItemList() {
    itemList.getChildren().clear();

    boolean blankMode = isBlankMenuProject();
    if (blankMode) {
      itemList.getChildren().add(buildOnboardingPanel());
    }

    // Registry editor panel (always shown when project is loaded)
    if (projectRoot != null && projectRoot.isDirectory()) {
      itemList.getChildren().add(buildRegistryEditorPanel());
      itemList.getChildren().add(new Separator());
    }

    if (cachedItems.isEmpty() && !blankMode) return;

    String filter = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
    List<LayoutItem> visible = cachedItems;
    if (!filter.isEmpty()) {
      visible = cachedItems.stream()
          .filter(it -> (it.title() + " " + it.relativePath()).toLowerCase(Locale.ROOT).contains(filter))
          .collect(Collectors.toList());
    }

    // Group by type with section headers (CSS icons)
    renderSection(visible, ItemType.DIALOGUE_LAYOUT, CssIcon.speech("#7ec8e3"), "Dialogue Layout");
    renderSection(visible, ItemType.MENU_SCREEN,     CssIcon.list("#a8d8a8"),   "Menu Screens");
    renderSection(visible, ItemType.MENU_LAYOUT,     CssIcon.grid("#d4a8e8"),   "Menu Layouts");
    renderSection(visible, ItemType.MENU_STYLE,      CssIcon.palette("#e8c8a8"),"Menu Styles");

    // Always show Create New buttons when in blank mode or when project has menu dirs
    if (projectRoot != null && projectRoot.isDirectory()) {
      itemList.getChildren().add(buildCreateNewPanel());
    }
  }

  private void renderSection(List<LayoutItem> visible, ItemType type, Region icon, String sectionTitle) {
    List<LayoutItem> group = visible.stream()
        .filter(it -> it.type() == type)
        .collect(Collectors.toList());
    if (group.isEmpty()) return;

    HBox header = CssIcon.iconLabel(icon, sectionTitle + "  (" + group.size() + ")",
        "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #a8b0c0;");
    header.setPadding(new Insets(8, 0, 4, 0));
    itemList.getChildren().add(header);
    for (LayoutItem item : group) {
      itemList.getChildren().add(createItemRow(item));
    }
    itemList.getChildren().add(new Separator());
  }

  private VBox createItemRow(LayoutItem item) {
    Label title = new Label(item.title());
    title.setStyle("-fx-font-weight: 700;");

    Label path = new Label(item.relativePath());
    path.getStyleClass().add("muted");
    path.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");

    Label status = new Label(statusText(item.status()));
    status.setStyle("-fx-padding: 2 8 2 8; -fx-background-radius: 10; " + statusStyle(item.status()));

    Label detail = new Label(item.detail());
    detail.getStyleClass().add("muted");
    detail.setStyle("-fx-font-size: 11px;");

    Button openButton = new Button("Open Studio");
    openButton.setOnAction(e -> openItem(item));

    Button cloneButton = new Button("Clone");
    cloneButton.setGraphic(CssIcon.plus("#7ec8e3"));
    cloneButton.setStyle("-fx-font-size: 10px;");
    cloneButton.setOnAction(e -> cloneItem(item));

    HBox head = new HBox(8, title, status);
    head.setAlignment(Pos.CENTER_LEFT);
    HBox actions = new HBox(6, openButton, cloneButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    VBox box = new VBox(4, head, path, detail);

    // Wiring info for menu screens
    if (item.type() == ItemType.MENU_SCREEN) {
      VBox wiringBox = new VBox(4);
      wiringBox.setPadding(new Insets(4, 0, 2, 0));

      // Quick-assign layout ComboBox
      javafx.scene.control.ComboBox<String> layoutCombo = new javafx.scene.control.ComboBox<>();
      layoutCombo.setEditable(true);
      layoutCombo.getItems().add("default");
      layoutCombo.getItems().addAll(knownLayoutIds);
      layoutCombo.setValue(item.layoutRef() != null ? item.layoutRef() : "default");
      layoutCombo.setMaxWidth(Double.MAX_VALUE);
      layoutCombo.setStyle("-fx-font-size: 11px;");
      boolean layoutValid = item.layoutRef() == null || "default".equals(item.layoutRef()) || knownLayoutIds.contains(item.layoutRef());
      if (!layoutValid) {
        layoutCombo.setStyle("-fx-font-size: 11px; -fx-border-color: #f0a060;");
      }
      layoutCombo.valueProperty().addListener((o, ov, nv) -> {
        if (nv != null && !nv.equals(ov)) {
          quickAssignScreenProperty(item.relativePath(), "layout", nv);
        }
      });
      Label layoutLbl = new Label("Layout");
      layoutLbl.setMinWidth(42);
      layoutLbl.setStyle("-fx-text-fill: #90a0b0; -fx-font-size: 10px;");
      HBox.setHgrow(layoutCombo, Priority.ALWAYS);
      HBox layoutRow = new HBox(4, layoutLbl, layoutCombo);
      layoutRow.setAlignment(Pos.CENTER_LEFT);
      wiringBox.getChildren().add(layoutRow);

      // Quick-assign style ComboBox
      javafx.scene.control.ComboBox<String> styleCombo = new javafx.scene.control.ComboBox<>();
      styleCombo.setEditable(true);
      styleCombo.getItems().add("default");
      styleCombo.getItems().addAll(knownStyleIds);
      styleCombo.setValue(item.styleRef() != null ? item.styleRef() : "default");
      styleCombo.setMaxWidth(Double.MAX_VALUE);
      styleCombo.setStyle("-fx-font-size: 11px;");
      boolean styleValid = item.styleRef() == null || "default".equals(item.styleRef()) || knownStyleIds.contains(item.styleRef());
      if (!styleValid) {
        styleCombo.setStyle("-fx-font-size: 11px; -fx-border-color: #f0a060;");
      }
      styleCombo.valueProperty().addListener((o, ov, nv) -> {
        if (nv != null && !nv.equals(ov)) {
          quickAssignScreenProperty(item.relativePath(), "defaultItemStyle", nv);
        }
      });
      Label styleLbl = new Label("Style");
      styleLbl.setMinWidth(42);
      styleLbl.setStyle("-fx-text-fill: #90a0b0; -fx-font-size: 10px;");
      HBox.setHgrow(styleCombo, Priority.ALWAYS);
      HBox styleRow = new HBox(4, styleLbl, styleCombo);
      styleRow.setAlignment(Pos.CENTER_LEFT);
      wiringBox.getChildren().add(styleRow);

      // Navigation flow
      if (!item.actionTargets().isEmpty()) {
        StringBuilder flowText = new StringBuilder("Navigates to: ");
        for (int i = 0; i < item.actionTargets().size(); i++) {
          if (i > 0) flowText.append(", ");
          String target = item.actionTargets().get(i);
          boolean valid = knownScreenIds.contains(target);
          flowText.append(target);
          if (!valid) flowText.append(" [?]");
        }
        Label flowLabel = new Label(flowText.toString());
        flowLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #c8c8a8;");
        flowLabel.setWrapText(true);
        wiringBox.getChildren().add(flowLabel);
      }

      box.getChildren().add(wiringBox);
    }

    // Validation warnings
    if (!item.warnings().isEmpty()) {
      for (String warn : item.warnings()) {
        Label warnLabel = new Label(warn);
        warnLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #f0a060; -fx-font-weight: bold;");
        warnLabel.setWrapText(true);
        box.getChildren().add(warnLabel);
      }
    }

    box.getChildren().add(actions);
    box.setPadding(new Insets(8));
    String borderColor = item.warnings().isEmpty() ? "#2a2f3a" : "#6a4a2a";
    box.setStyle("-fx-background-color: #1a1c22; -fx-background-radius: 8; -fx-border-color: " + borderColor + "; -fx-border-radius: 8;");
    return box;
  }

  private void openItem(LayoutItem item) {
    if (projectRoot == null || item == null || onOpenFile == null) return;
    File file = new File(projectRoot, item.relativePath());
    if (!file.exists()) {
      createMissingTemplate(file, item.type());
      refreshStatus();
    }
    onOpenFile.accept(file);
  }

  private void createMissingTemplate(File file, ItemType type) {
    try {
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();
      if (type == ItemType.DIALOGUE_LAYOUT) {
        Properties p = VnUiLayoutLoader.toProperties(VnUiLayoutSpec.defaults());
        try (FileWriter fw = new FileWriter(file)) {
          fw.write("# Dialogue UI layout\n");
          for (String key : p.stringPropertyNames()) {
            fw.write(key + "=" + p.getProperty(key) + System.lineSeparator());
          }
        }
      } else if (type == ItemType.MENU_LAYOUT) {
        MenuLayoutSpec s = MenuProfile.defaultLayout();
        try (FileWriter fw = new FileWriter(file)) {
          fw.write("# Menu layout\n");
          fw.write("listYStart=" + formatDouble(s.listYStart()) + System.lineSeparator());
          fw.write("lineHeight=" + formatDouble(s.lineHeight()) + System.lineSeparator());
          fw.write("listWidthFactor=" + formatDouble(s.listWidthFactor()) + System.lineSeparator());
          fw.write("textAlign=" + s.textAlign() + System.lineSeparator());
          fw.write("hintsBottomMargin=" + formatDouble(s.hintsBottomMargin()) + System.lineSeparator());
        }
      } else if (type == ItemType.MENU_STYLE) {
        MenuStyleSpec s = MenuProfile.defaultStyle();
        try (FileWriter fw = new FileWriter(file)) {
          fw.write("# Menu style\n");
          fw.write("itemPrefix=" + valueOrDefault(s.itemPrefix(), "  ") + System.lineSeparator());
          fw.write("itemSelectedPrefix=" + valueOrDefault(s.itemSelectedPrefix(), "> ") + System.lineSeparator());
          fw.write("itemDisabledPrefix=" + valueOrDefault(s.itemDisabledPrefix(), "- ") + System.lineSeparator());
          fw.write("itemDisabledColor=" + valueOrDefault(s.itemDisabledColor(), "#808080") + System.lineSeparator());
          fw.write("buttonTextPaddingX=18" + System.lineSeparator());
          fw.write("buttonTextPaddingY=0" + System.lineSeparator());
        }
      } else if (type == ItemType.MENU_SCREEN) {
        String screenId = file.getName().replace(".menu", "");
        try (FileWriter fw = new FileWriter(file)) {
          fw.write("# Menu screen definition" + System.lineSeparator());
          fw.write("titleText=" + titleize(screenId) + System.lineSeparator());
          fw.write("wrapSelection=true" + System.lineSeparator());
          fw.write("items=" + System.lineSeparator());
        }
      }
    } catch (Exception ignored) {
    }
  }

  private boolean isBlankMenuProject() {
    if (projectRoot == null) return false;
    Properties manifest = loadManifest(projectRoot);
    return "true".equalsIgnoreCase(normalize(manifest.getProperty("feature.blankMenus"), "false"));
  }

  private List<LayoutItem> collectItems(File root) {
    List<LayoutItem> items = new ArrayList<>();
    Properties manifest = loadManifest(root);
    boolean blankMenus = "true".equalsIgnoreCase(normalize(manifest.getProperty("feature.blankMenus"), "false"));

    String dialoguePath = manifestPath(manifest, "dialogueLayout", DEFAULT_DIALOGUE_LAYOUT_PATH);
    String menuRegistryPath = manifestPath(manifest, "menuRegistry", DEFAULT_MENU_REGISTRY_PATH);

    Set<String> layoutPaths = new LinkedHashSet<>();
    Set<String> stylePaths = new LinkedHashSet<>();
    Set<String> screenPaths = new LinkedHashSet<>();

    // Only inject default layout/style paths if not a blank-menu project
    if (!blankMenus) {
      String defaultMenuLayoutPath = manifestPath(manifest, "menuDefaultLayout", DEFAULT_MENU_LAYOUT_PATH);
      String defaultMenuStylePath = manifestPath(manifest, "menuDefaultStyle", DEFAULT_MENU_STYLE_PATH);
      layoutPaths.add(defaultMenuLayoutPath);
      stylePaths.add(defaultMenuStylePath);
    }

    layoutPaths.addAll(scanPaths(root, "config/menu/layouts", ".layout"));
    layoutPaths.addAll(scanPaths(root, "config/menu", ".layout"));
    stylePaths.addAll(scanPaths(root, "config/menu/styles", ".style"));
    stylePaths.addAll(scanPaths(root, "config/menu", ".style"));
    screenPaths.addAll(scanPaths(root, "config/menu/menus", ".menu"));
    screenPaths.addAll(scanPaths(root, "config/menu", ".menu"));

    // Load registry state for validation and inline editing
    loadRegistryState(root, menuRegistryPath);
    for (String menuId : registryMenus) {
      screenPaths.add("config/menu/menus/" + menuId + ".menu");
    }
    // Only inject main.menu fallback if NOT blank-menu project
    if (!blankMenus && screenPaths.isEmpty()) {
      screenPaths.add("config/menu/menus/main.menu");
    }

    // Populate known IDs for cross-reference validation
    for (String rel : layoutPaths) knownLayoutIds.add(fileStem(rel));
    for (String rel : stylePaths) knownStyleIds.add(fileStem(rel));
    for (String rel : screenPaths) knownScreenIds.add(fileStem(rel));

    items.add(buildDialogueItem(root, dialoguePath));
    for (String rel : sortPaths(layoutPaths)) {
      items.add(buildMenuLayoutItem(root, rel));
    }
    for (String rel : sortPaths(stylePaths)) {
      items.add(buildMenuStyleItem(root, rel));
    }
    for (String rel : sortPaths(screenPaths)) {
      items.add(buildMenuScreenItem(root, rel));
    }
    return items;
  }

  private void loadRegistryState(File root, String registryRelativePath) {
    registryMenus.clear();
    registryLayouts.clear();
    registryStyles.clear();
    registryDefaultMenu = "";
    File registry = new File(root, registryRelativePath);
    if (!registry.exists()) return;
    Properties p = loadProperties(registry);
    registryDefaultMenu = normalize(p.getProperty("defaultMenu", p.getProperty("defaultScreen")), "");
    for (String part : parseCsv(p.getProperty("menus"))) registryMenus.add(part);
    for (String part : parseCsv(p.getProperty("layouts"))) registryLayouts.add(part);
    for (String part : parseCsv(p.getProperty("styles"))) registryStyles.add(part);
  }

  private static List<String> parseCsv(String value) {
    List<String> result = new ArrayList<>();
    if (value == null || value.isBlank()) return result;
    for (String part : value.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) result.add(trimmed);
    }
    return result;
  }

  private LayoutItem buildDialogueItem(File root, String relPath) {
    File file = new File(root, relPath);
    if (!file.exists()) {
      return new LayoutItem("Dialogue Layout", relPath, ItemType.DIALOGUE_LAYOUT, StatusKind.MISSING, "Using engine defaults.");
    }
    Properties p = loadProperties(file);
    VnUiLayoutSpec actual = VnUiLayoutLoader.parse(p, VnUiLayoutSpec.defaults());
    boolean changed = !actual.equals(VnUiLayoutSpec.defaults());
    String textBoxAsset = normalize(p.getProperty("textBoxAsset"), "");
    if (!textBoxAsset.isBlank()) changed = true;
    if (hasAnyNonBlank(
        p,
        "choiceButtonAsset",
        "choiceButtonHoverAsset",
        "choiceButtonSelectedAsset",
        "choiceButtonDisabledAsset",
        "choiceBackgroundColor",
        "choiceHoverColor",
        "choiceSelectedColor",
        "choiceDisabledColor",
        "choiceTextColor",
        "choiceHoverTextColor",
        "choiceSelectedTextColor",
        "choiceDisabledTextColor",
        "choiceBorderColor",
        "choiceHoverBorderColor",
        "choiceSelectedBorderColor",
        "choiceDisabledBorderColor",
        "choiceCornerRadius",
        "choiceBorderWidth",
        "choiceTextBaselineOffset")) {
      changed = true;
    }
    return new LayoutItem(
        "Dialogue Layout",
        relPath,
        ItemType.DIALOGUE_LAYOUT,
        changed ? StatusKind.CUSTOMIZED : StatusKind.DEFAULT,
        changed ? "Dialogue bounds or asset changed." : "Still at default dialogue layout."
    );
  }

  private LayoutItem buildMenuLayoutItem(File root, String relPath) {
    String name = "Menu Layout: " + fileStem(relPath);
    File file = new File(root, relPath);
    if (!file.exists()) {
      return new LayoutItem(name, relPath, ItemType.MENU_LAYOUT, StatusKind.MISSING, "Using default menu layout.");
    }
    Properties p = loadProperties(file);
    MenuLayoutSpec base = MenuProfile.defaultLayout();
    double listYStart = parseDouble(p.getProperty("listYStart"), base.listYStart());
    double lineHeight = parseDouble(p.getProperty("lineHeight"), base.lineHeight());
    double listWidthFactor = parseDouble(firstNonBlank(p.getProperty("listWidthFactor"), p.getProperty("listWidth")), base.listWidthFactor());
    String align = normalize(p.getProperty("textAlign"), base.textAlign()).toLowerCase(Locale.ROOT);
    double hintsBottom = parseDouble(p.getProperty("hintsBottomMargin"), base.hintsBottomMargin());
    Double titleY = parseOptionalDouble(p.getProperty("titleY"));
    boolean changed = !approxEqual(listYStart, base.listYStart())
        || !approxEqual(lineHeight, base.lineHeight())
        || !approxEqual(listWidthFactor, base.listWidthFactor())
        || !align.equalsIgnoreCase(base.textAlign())
        || !approxEqual(hintsBottom, base.hintsBottomMargin())
        || titleY != null;
    return new LayoutItem(
        name,
        relPath,
        ItemType.MENU_LAYOUT,
        changed ? StatusKind.CUSTOMIZED : StatusKind.DEFAULT,
        changed ? "Layout geometry customized." : "Matches default menu layout."
    );
  }

  private LayoutItem buildMenuStyleItem(File root, String relPath) {
    String name = "Menu Style: " + fileStem(relPath);
    File file = new File(root, relPath);
    if (!file.exists()) {
      return new LayoutItem(name, relPath, ItemType.MENU_STYLE, StatusKind.MISSING, "Using default menu style.");
    }

    Properties p = loadProperties(file);
    boolean changed = false;
    String itemColor = normalize(p.getProperty("itemColor"), "");
    String selectedColor = normalize(p.getProperty("itemSelectedColor"), "");
    String disabledColor = normalize(p.getProperty("itemDisabledColor"), "");
    String itemPrefix = p.getProperty("itemPrefix");
    String selectedPrefix = p.getProperty("itemSelectedPrefix");
    String disabledPrefix = p.getProperty("itemDisabledPrefix");

    if (!itemColor.isBlank()) changed = true;
    if (!selectedColor.isBlank()) changed = true;
    if (!disabledColor.isBlank() && !disabledColor.equalsIgnoreCase("#808080")) changed = true;
    if (itemPrefix != null && !itemPrefix.equals("  ")) changed = true;
    if (selectedPrefix != null && !selectedPrefix.equals("> ")) changed = true;
    if (disabledPrefix != null && !disabledPrefix.equals("- ")) changed = true;
    if (!normalize(p.getProperty("itemFontFamily"), "").isBlank()) changed = true;
    if (!normalize(p.getProperty("itemFontWeight"), "").isBlank()) changed = true;
    if (parseOptionalDouble(p.getProperty("itemFontSize")) != null) changed = true;
    if (!normalize(p.getProperty("buttonAsset"), "").isBlank()) changed = true;
    if (!normalize(p.getProperty("buttonSelectedAsset"), "").isBlank()) changed = true;
    if (!normalize(p.getProperty("buttonDisabledAsset"), "").isBlank()) changed = true;

    Double padX = parseOptionalDouble(p.getProperty("buttonTextPaddingX"));
    Double padY = parseOptionalDouble(p.getProperty("buttonTextPaddingY"));
    if (padX != null && !approxEqual(padX, 18.0)) changed = true;
    if (padY != null && !approxEqual(padY, 0.0)) changed = true;

    return new LayoutItem(
        name,
        relPath,
        ItemType.MENU_STYLE,
        changed ? StatusKind.CUSTOMIZED : StatusKind.DEFAULT,
        changed ? "Typography/button skin customized." : "Matches default style baseline."
    );
  }

  private LayoutItem buildMenuScreenItem(File root, String relPath) {
    String name = "Menu Screen: " + fileStem(relPath);
    String screenId = fileStem(relPath);
    File file = new File(root, relPath);
    if (!file.exists()) {
      List<String> warnings = new ArrayList<>();
      if (registryMenus.contains(screenId)) {
        warnings.add("\u26A0 Registered in menu.registry but file does not exist.");
      }
      return new LayoutItem(name, relPath, ItemType.MENU_SCREEN, StatusKind.MISSING,
          "Screen file not created yet.", null, null, List.of(), warnings);
    }
    Properties p = loadProperties(file);
    boolean customized = false;
    String layoutId = normalize(firstNonBlank(p.getProperty("layout"), p.getProperty("layoutId")), "default");
    String styleId = normalize(p.getProperty("defaultItemStyle"), "default");
    if (!"default".equalsIgnoreCase(layoutId)) customized = true;
    if (!"default".equalsIgnoreCase(styleId)) customized = true;

    // Collect action targets (navigate_to, open_menu targets)
    List<String> actionTargets = new ArrayList<>();
    for (String key : p.stringPropertyNames()) {
      if (key.startsWith("item.") && key.endsWith(".target")) {
        String target = normalize(p.getProperty(key), "");
        if (!target.isBlank()) actionTargets.add(target);
      }
      if (key.startsWith("item.") && (
          key.endsWith(".bgAsset")
              || key.endsWith(".bgSelectedAsset")
              || key.endsWith(".bgDisabledAsset")
              || key.endsWith(".slotPreviewEnabled")
              || key.endsWith(".slotPreviewPlaceholderAsset")
              || key.endsWith(".slotPreviewFrameAsset")
              || key.endsWith(".slotPreviewX")
              || key.endsWith(".slotPreviewY")
              || key.endsWith(".slotPreviewWidth")
              || key.endsWith(".slotPreviewHeight")
              || key.endsWith(".boundsX")
              || key.endsWith(".boundsY")
              || key.endsWith(".boundsWidth")
              || key.endsWith(".boundsHeight")
      )) {
        customized = true;
      }
    }

    // Deduplicate action targets
    List<String> uniqueTargets = new ArrayList<>(new LinkedHashSet<>(actionTargets));

    // Validation warnings
    List<String> warnings = new ArrayList<>();
    if (!"default".equals(layoutId) && !knownLayoutIds.contains(layoutId)) {
      warnings.add("\u26A0 Layout '" + layoutId + "' not found in project.");
    }
    if (!"default".equals(styleId) && !knownStyleIds.contains(styleId)) {
      warnings.add("\u26A0 Style '" + styleId + "' not found in project.");
    }
    for (String target : uniqueTargets) {
      if (!knownScreenIds.contains(target)) {
        warnings.add("\u26A0 Navigation target '" + target + "' not found.");
      }
    }
    if (!registryMenus.contains(screenId) && !screenId.equals("main")) {
      warnings.add("\u26A0 Not registered in menu.registry — won't be discovered at runtime.");
    }

    String detail = customized ? "Custom item bounds/skins or non-default style/layout." : "Default menu wiring.";
    return new LayoutItem(name, relPath, ItemType.MENU_SCREEN,
        customized ? StatusKind.CUSTOMIZED : StatusKind.DEFAULT,
        detail, layoutId, styleId, uniqueTargets, warnings);
  }

  private List<String> scanPaths(File root, String directory, String extension) {
    File dir = new File(root, directory);
    if (!dir.exists() || !dir.isDirectory()) return List.of();
    try (var stream = Files.walk(dir.toPath(), 2)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
          .map(path -> toRelativePath(root, path))
          .filter(path -> !path.isBlank())
          .collect(Collectors.toCollection(LinkedHashSet::new))
          .stream()
          .toList();
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private List<String> sortPaths(Set<String> paths) {
    return paths.stream().sorted(String::compareTo).toList();
  }

  private String statusText(StatusKind kind) {
    return switch (kind) {
      case DEFAULT -> "Default";
      case CUSTOMIZED -> "Customized";
      case MISSING -> "Missing";
    };
  }

  private String statusStyle(StatusKind kind) {
    return switch (kind) {
      case DEFAULT -> "-fx-background-color: rgba(86, 163, 255, 0.16); -fx-text-fill: #9ecbff;";
      case CUSTOMIZED -> "-fx-background-color: rgba(114, 214, 145, 0.16); -fx-text-fill: #8be2a5;";
      case MISSING -> "-fx-background-color: rgba(255, 190, 100, 0.16); -fx-text-fill: #f0c180;";
    };
  }

  private Properties loadManifest(File root) {
    if (root == null) return new Properties();
    File manifest = new File(root, "jvn.project");
    if (!manifest.exists()) return new Properties();
    return loadProperties(manifest);
  }

  private Properties loadProperties(File file) {
    Properties p = new Properties();
    if (file == null || !file.exists()) return p;
    try (FileInputStream fis = new FileInputStream(file)) {
      p.load(fis);
    } catch (Exception ignored) {
    }
    return p;
  }

  private String manifestPath(Properties manifest, String key, String fallback) {
    if (manifest == null) return fallback;
    return normalize(manifest.getProperty(key), fallback).replace('\\', '/');
  }

  private String toRelativePath(File root, Path path) {
    try {
      Path rootPath = root.toPath().toAbsolutePath().normalize();
      Path abs = path.toAbsolutePath().normalize();
      if (abs.startsWith(rootPath)) {
        return rootPath.relativize(abs).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  private String fileStem(String relativePath) {
    String name = new File(relativePath).getName();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private static boolean approxEqual(double a, double b) {
    return Math.abs(a - b) < 1e-6;
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isBlank() ? fallback : t;
  }

  private static Double parseOptionalDouble(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static double parseDouble(String raw, double fallback) {
    Double parsed = parseOptionalDouble(raw);
    return parsed == null ? fallback : parsed;
  }

  private static String firstNonBlank(String a, String b) {
    String first = normalize(a, "");
    if (!first.isBlank()) return first;
    return normalize(b, "");
  }

  private static boolean hasAnyNonBlank(Properties p, String... keys) {
    if (p == null || keys == null) return false;
    for (String key : keys) {
      if (!normalize(p.getProperty(key), "").isBlank()) return true;
    }
    return false;
  }

  private static String formatDouble(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static String valueOrDefault(String value, String fallback) {
    String normalized = normalize(value, "");
    return normalized.isBlank() ? fallback : normalized;
  }

  // ── Registry inline editor ──

  private VBox buildRegistryEditorPanel() {
    VBox panel = new VBox(6);
    panel.setPadding(new Insets(10, 12, 10, 12));
    panel.setStyle("-fx-background-color: #1a2030; -fx-background-radius: 8; -fx-border-color: #2a3a5a; -fx-border-radius: 8;");

    Label heading = new Label("Menu Registry");
    heading.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #a8c8f0;");

    Label hint = new Label("config/menu/registry/menu.registry");
    hint.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 10px; -fx-text-fill: #70788a;");

    // Default menu field
    TextField tfDefaultMenu = new TextField(registryDefaultMenu);
    tfDefaultMenu.setPromptText("main");
    tfDefaultMenu.setPrefWidth(160);
    HBox defaultRow = labeledField("Default Menu", tfDefaultMenu);

    // Menus field
    TextField tfMenus = new TextField(String.join(", ", registryMenus));
    tfMenus.setPromptText("main, load, save, settings");
    HBox menusRow = labeledField("Menus", tfMenus);

    // Layouts field
    TextField tfLayouts = new TextField(String.join(", ", registryLayouts));
    tfLayouts.setPromptText("default, compact");
    HBox layoutsRow = labeledField("Layouts", tfLayouts);

    // Styles field
    TextField tfStyles = new TextField(String.join(", ", registryStyles));
    tfStyles.setPromptText("default, neon");
    HBox stylesRow = labeledField("Styles", tfStyles);

    Button saveRegistry = new Button("Save Registry");
    saveRegistry.setGraphic(CssIcon.save("#8cd48c"));
    saveRegistry.setOnAction(e -> {
      if (projectRoot == null) return;
      File registryFile = new File(projectRoot, DEFAULT_MENU_REGISTRY_PATH);
      Properties p = new Properties();
      String dm = normalize(tfDefaultMenu.getText(), "");
      if (!dm.isBlank()) p.setProperty("defaultMenu", dm);
      String menus = normalize(tfMenus.getText(), "");
      if (!menus.isBlank()) p.setProperty("menus", menus.replace(" ", ""));
      String layouts = normalize(tfLayouts.getText(), "");
      if (!layouts.isBlank()) p.setProperty("layouts", layouts.replace(" ", ""));
      String styles = normalize(tfStyles.getText(), "");
      if (!styles.isBlank()) p.setProperty("styles", styles.replace(" ", ""));
      try {
        File parent = registryFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(registryFile)) {
          p.store(fos, "Menu registry - edited via Layout Editor");
        }
      } catch (Exception ignored) {
      }
      refreshStatus();
    });

    Button openRegistryFile = new Button("Open File");
    openRegistryFile.setGraphic(CssIcon.expand("#7ec8e3"));
    openRegistryFile.setOnAction(e -> {
      if (projectRoot == null || onOpenFile == null) return;
      File registryFile = new File(projectRoot, DEFAULT_MENU_REGISTRY_PATH);
      if (!registryFile.exists()) {
        try {
          File parent = registryFile.getParentFile();
          if (parent != null && !parent.exists()) parent.mkdirs();
          try (FileWriter fw = new FileWriter(registryFile)) {
            fw.write("# Menu registry\ndefaultMenu=main\nmenus=main\n");
          }
        } catch (Exception ignored) {
        }
      }
      onOpenFile.accept(registryFile);
    });

    HBox registryActions = new HBox(6, saveRegistry, openRegistryFile);
    registryActions.setAlignment(Pos.CENTER_LEFT);

    panel.getChildren().addAll(heading, hint, defaultRow, menusRow, layoutsRow, stylesRow, registryActions);
    return panel;
  }

  private HBox labeledField(String label, TextField field) {
    Label l = new Label(label);
    l.setMinWidth(80);
    l.setStyle("-fx-text-fill: #b0b8c8; -fx-font-size: 11px;");
    HBox.setHgrow(field, Priority.ALWAYS);
    field.setMaxWidth(Double.MAX_VALUE);
    field.setStyle("-fx-font-size: 11px;");
    HBox row = new HBox(6, l, field);
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  // ── Clone ──

  private void cloneItem(LayoutItem item) {
    if (projectRoot == null || item == null) return;
    File source = new File(projectRoot, item.relativePath());
    if (!source.exists()) return;

    String extension = switch (item.type()) {
      case MENU_SCREEN -> ".menu";
      case MENU_LAYOUT -> ".layout";
      case MENU_STYLE -> ".style";
      case DIALOGUE_LAYOUT -> ".layout";
    };
    String label = switch (item.type()) {
      case MENU_SCREEN -> "Menu Screen";
      case MENU_LAYOUT -> "Menu Layout";
      case MENU_STYLE -> "Menu Style";
      case DIALOGUE_LAYOUT -> "Dialogue Layout";
    };

    TextInputDialog dialog = new TextInputDialog(fileStem(item.relativePath()) + "_copy");
    dialog.setTitle("Clone " + label);
    dialog.setHeaderText("Enter a name for the cloned " + label.toLowerCase(Locale.ROOT) + ":");
    dialog.setContentText("Name (no extension):");
    EditorTheme.apply(dialog);
    dialog.showAndWait().ifPresent(rawName -> {
      String name = sanitizeFileName(rawName);
      if (name.isBlank()) return;
      File destDir = source.getParentFile();
      File dest = new File(destDir, name + extension);
      if (dest.exists()) {
        if (onOpenFile != null) onOpenFile.accept(dest);
        return;
      }
      try {
        Files.copy(source.toPath(), dest.toPath());
      } catch (Exception ignored) {
        return;
      }
      updateRegistryForNewFile(name, item.type());
      refreshStatus();
      if (onOpenFile != null) onOpenFile.accept(dest);
    });
  }

  // ── Quick-assign ──

  private void quickAssignScreenProperty(String relativePath, String key, String value) {
    if (projectRoot == null) return;
    File file = new File(projectRoot, relativePath);
    if (!file.exists()) return;
    Properties p = loadProperties(file);
    if (value == null || value.isBlank() || "default".equalsIgnoreCase(value)) {
      p.remove(key);
    } else {
      p.setProperty(key, value);
    }
    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
      p.store(fos, "Menu screen - quick-assign via Layout Editor");
    } catch (Exception ignored) {
    }
    // Don't full-refresh to avoid losing focus, but update the cached data
  }

  // ── Onboarding guidance for blank-menu projects ──

  private VBox buildOnboardingPanel() {
    VBox panel = new VBox(6);
    panel.setPadding(new Insets(10, 12, 10, 12));
    panel.setStyle("-fx-background-color: #1c2230; -fx-background-radius: 8; -fx-border-color: #2a3a5a; -fx-border-radius: 8;");

    Label heading = new Label("Custom Menu Project");
    heading.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #7ec8e3;");

    boolean hasLayouts = cachedItems.stream().anyMatch(it -> it.type() == ItemType.MENU_LAYOUT);
    boolean hasStyles = cachedItems.stream().anyMatch(it -> it.type() == ItemType.MENU_STYLE);
    boolean hasScreens = cachedItems.stream().anyMatch(it -> it.type() == ItemType.MENU_SCREEN);

    StringBuilder guide = new StringBuilder();
    guide.append("This project uses custom menus (Start from Zero).\n");
    guide.append("Follow these steps to wire your menu system:\n\n");

    int step = 1;
    guide.append(step++).append(". ");
    if (hasLayouts) guide.append("\u2713 ");
    guide.append("Create a Menu Layout (.layout) - defines list position, spacing, alignment\n");

    guide.append(step++).append(". ");
    if (hasStyles) guide.append("\u2713 ");
    guide.append("Create a Menu Style (.style) - defines button skins, fonts, colors\n");

    guide.append(step++).append(". ");
    if (hasScreens) guide.append("\u2713 ");
    guide.append("Create Menu Screens (.menu) - main, load, save, settings, etc.\n");

    guide.append(step++).append(". Wire screens in the registry (config/menu/registry/menu.registry)\n");
    guide.append("   Set: defaultMenu=main  menus=main,load,save  layouts=<name>  styles=<name>\n\n");

    guide.append("Until wired: save/load, rollback, settings, pause overlay are unavailable in-game.\n");
    guide.append("Use VNS file preview to test game progress in the meantime.");

    Label body = new Label(guide.toString());
    body.setWrapText(true);
    body.setStyle("-fx-font-size: 11px; -fx-text-fill: #b0b8c8;");

    panel.getChildren().addAll(heading, body);
    return panel;
  }

  private VBox buildCreateNewPanel() {
    VBox panel = new VBox(6);
    panel.setPadding(new Insets(8, 0, 4, 0));

    HBox header = CssIcon.iconLabel(CssIcon.plus("#8cd48c"), "Create New File",
        "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #a8b0c0;");
    header.setPadding(new Insets(4, 0, 4, 0));

    Button newScreen = new Button("New Menu Screen");
    newScreen.setGraphic(CssIcon.list("#a8d8a8"));
    newScreen.setMaxWidth(Double.MAX_VALUE);
    newScreen.setOnAction(e -> promptCreateFile("Menu Screen", "config/menu/menus", ".menu", ItemType.MENU_SCREEN));

    Button newLayout = new Button("New Menu Layout");
    newLayout.setGraphic(CssIcon.grid("#d4a8e8"));
    newLayout.setMaxWidth(Double.MAX_VALUE);
    newLayout.setOnAction(e -> promptCreateFile("Menu Layout", "config/menu/layouts", ".layout", ItemType.MENU_LAYOUT));

    Button newStyle = new Button("New Menu Style");
    newStyle.setGraphic(CssIcon.palette("#e8c8a8"));
    newStyle.setMaxWidth(Double.MAX_VALUE);
    newStyle.setOnAction(e -> promptCreateFile("Menu Style", "config/menu/styles", ".style", ItemType.MENU_STYLE));

    panel.getChildren().addAll(header, newScreen, newLayout, newStyle);
    return panel;
  }

  private void promptCreateFile(String label, String relDir, String extension, ItemType type) {
    if (projectRoot == null) return;
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("New " + label);
    dialog.setHeaderText("Enter a name for the new " + label.toLowerCase(Locale.ROOT) + ":");
    dialog.setContentText("Name (no extension):");
    EditorTheme.apply(dialog);
    dialog.showAndWait().ifPresent(rawName -> {
      String name = sanitizeFileName(rawName);
      if (name.isBlank()) return;
      File dir = new File(projectRoot, relDir);
      if (!dir.exists()) dir.mkdirs();
      File file = new File(dir, name + extension);
      if (file.exists()) {
        // Already exists - just open it
        if (onOpenFile != null) onOpenFile.accept(file);
        return;
      }
      createMissingTemplate(file, type);
      updateRegistryForNewFile(name, type);
      refreshStatus();
      if (onOpenFile != null) onOpenFile.accept(file);
    });
  }

  private void updateRegistryForNewFile(String name, ItemType type) {
    if (projectRoot == null) return;
    if (type == ItemType.DIALOGUE_LAYOUT) return;
    File registryFile = new File(projectRoot, DEFAULT_MENU_REGISTRY_PATH);
    Properties registry = loadProperties(registryFile);

    String key = switch (type) {
      case MENU_SCREEN -> "menus";
      case MENU_LAYOUT -> "layouts";
      case MENU_STYLE  -> "styles";
      default -> null;
    };
    if (key == null) return;

    String existing = normalize(registry.getProperty(key), "");
    Set<String> ids = new LinkedHashSet<>();
    if (!existing.isBlank()) {
      for (String part : existing.split(",")) {
        String id = part.trim();
        if (!id.isBlank()) ids.add(id);
      }
    }
    if (ids.contains(name)) return;
    ids.add(name);
    registry.setProperty(key, String.join(",", ids));

    if (type == ItemType.MENU_SCREEN && !registry.containsKey("defaultMenu")) {
      registry.setProperty("defaultMenu", name);
    }

    try {
      File parent = registryFile.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();
      try (java.io.FileOutputStream fos = new java.io.FileOutputStream(registryFile)) {
        registry.store(fos, "Menu registry - auto-updated by Layout Editor");
      }
    } catch (Exception ignored) {
    }
  }

  private static String sanitizeFileName(String raw) {
    if (raw == null) return "";
    String s = raw.trim().toLowerCase(Locale.ROOT);
    s = s.replace(' ', '_').replace('-', '_');
    s = s.replaceAll("[^a-z0-9_]", "");
    s = s.replaceAll("_+", "_");
    if (s.startsWith("_")) s = s.substring(1);
    if (s.endsWith("_")) s = s.substring(0, s.length() - 1);
    return s;
  }

  private static String titleize(String raw) {
    String source = normalize(raw, "Menu").replace('_', ' ').replace('-', ' ');
    if (source.isBlank()) return "Menu";
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        upper = true;
        out.append(c);
      } else if (upper) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
