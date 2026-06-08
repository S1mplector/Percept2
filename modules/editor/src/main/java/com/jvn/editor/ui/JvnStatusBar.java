package com.jvn.editor.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.jvn.editor.AppBuildInfo;

import javafx.geometry.Side;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Compact application-wide status bar inspired by desktop IDE chrome.
 */
public final class JvnStatusBar extends HBox {
  private static final String DARK_ICON_COLOR = "#d6d6d6";
  private static final String LIGHT_ICON_COLOR = "#3f3f3f";
  private static final Runnable NO_OP = () -> {};

  private final List<Region> icons = new ArrayList<>();
  private final Label productLabel = segmentLabel("");
  private final Label branchLabel = segmentLabel("No branch");
  private final Label messageLabel = segmentLabel("Ready");
  private final Label projectLabel = segmentLabel("No project");
  private final Label activeFileLabel = segmentLabel("No file");
  private final Label positionLabel = segmentLabel("Ln --");
  private final Label dirtyLabel = segmentLabel("Saved");
  private final Label diagnosticsLabel = segmentLabel("0 Problems");
  private final Label encodingLabel = segmentLabel("UTF-8");
  private final Label lineEndingLabel = segmentLabel("LF");
  private final Label javaLabel = segmentLabel("Java " + javaFeatureVersion());
  private final Label themeLabel = segmentLabel("Dark");
  private final Label versionLabel = segmentLabel("");
  private final HBox productSegment;
  private final HBox branchSegment;
  private final HBox messageSegment;
  private final HBox projectSegment;
  private final HBox activeFileSegment;
  private final HBox positionSegment;
  private final HBox dirtySegment;
  private final HBox diagnosticsSegment;
  private final HBox themeSegment;
  private Runnable onRevealProjectRoot = NO_OP;
  private Runnable onCopyProjectRootPath = NO_OP;
  private Runnable onRunProject = NO_OP;
  private Runnable onOpenVersionControl = NO_OP;
  private Runnable onOpenSettings = NO_OP;
  private Runnable onRevealActiveFile = NO_OP;
  private Runnable onCopyActiveFilePath = NO_OP;
  private Runnable onSaveAll = NO_OP;
  private Runnable onOpenDiagnostics = NO_OP;

  public JvnStatusBar(String productName, AppBuildInfo.BuildInfo buildInfo) {
    getStyleClass().add("jvn-status-bar");
    setAlignment(Pos.CENTER_LEFT);
    setMinHeight(25);
    setPrefHeight(25);
    setPadding(new Insets(0, 8, 0, 8));

    productLabel.setText(clean(productName, "JVN"));
    versionLabel.setText(buildInfo == null ? "" : buildInfo.versionLabel());
    versionLabel.setVisible(!versionLabel.getText().isBlank());
    versionLabel.setManaged(!versionLabel.getText().isBlank());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    productSegment = segment(icon(CssIcon.home(DARK_ICON_COLOR)), productLabel, "jvn-status-segment-strong");
    branchSegment = segment(icon(CssIcon.branchPlus(DARK_ICON_COLOR)), branchLabel);
    messageSegment = segment(icon(CssIcon.speech(DARK_ICON_COLOR)), messageLabel, "jvn-status-message");
    projectSegment = segment(icon(CssIcon.folder(DARK_ICON_COLOR)), projectLabel);
    activeFileSegment = segment(icon(CssIcon.document(DARK_ICON_COLOR)), activeFileLabel);
    positionSegment = segment(icon(CssIcon.edit(DARK_ICON_COLOR)), positionLabel);
    dirtySegment = segment(icon(CssIcon.save(DARK_ICON_COLOR)), dirtyLabel);
    diagnosticsSegment = segment(icon(CssIcon.warning(DARK_ICON_COLOR)), diagnosticsLabel, "jvn-status-diagnostics-ok");
    themeSegment = segment(icon(CssIcon.palette(DARK_ICON_COLOR)), themeLabel);

    installMenus();

    getChildren().addAll(
        productSegment,
        separator(),
        branchSegment,
        separator(),
        messageSegment,
        spacer,
        projectSegment,
        separator(),
        activeFileSegment,
        separator(),
        positionSegment,
        separator(),
        dirtySegment,
        separator(),
        diagnosticsSegment,
        separator(),
        segment(encodingLabel),
        separator(),
        segment(lineEndingLabel),
        separator(),
        segment(icon(CssIcon.memory(DARK_ICON_COLOR)), javaLabel),
        separator(),
        themeSegment,
        separator(),
        segment(versionLabel, "jvn-status-version"));
  }

  public Label messageLabel() {
    return messageLabel;
  }

  public void setProjectRoot(File projectRoot) {
    if (projectRoot == null) {
      projectLabel.setText("No project");
      branchLabel.setText("No branch");
      setTooltip(projectLabel, "No project is open.");
      setTooltip(branchLabel, "No Git repository detected.");
      return;
    }
    projectLabel.setText(projectRoot.getName().isBlank() ? projectRoot.getAbsolutePath() : projectRoot.getName());
    setTooltip(projectLabel, projectRoot.getAbsolutePath());
    String branch = resolveBranch(projectRoot.toPath());
    branchLabel.setText(branch);
    setTooltip(branchLabel, branch.equals("No branch") ? "No Git repository detected." : "Git branch: " + branch);
  }

  public void setActiveFile(String fileName, String kind, int oneBasedLine) {
    String cleanName = clean(fileName, "No file");
    String cleanKind = clean(kind, "");
    activeFileLabel.setText(cleanKind.isBlank() || cleanName.equals("No file")
        ? cleanName
        : cleanName + " (" + cleanKind + ")");
    setTooltip(activeFileLabel, activeFileLabel.getText());
    positionLabel.setText(oneBasedLine > 0 ? "Ln " + oneBasedLine : "Ln --");
  }

  public void setWorkspaceState(int dirtyTabs, int closableTabs, boolean canUndo, boolean canRedo) {
    dirtyTabs = Math.max(0, dirtyTabs);
    closableTabs = Math.max(0, closableTabs);
    dirtyLabel.setText(dirtyTabs == 0 ? "Saved" : dirtyTabs + " Unsaved");
    setTooltip(dirtyLabel, dirtyTabs == 0
        ? "No unsaved editor tabs."
        : dirtyTabs + " unsaved tab" + (dirtyTabs == 1 ? "" : "s") + ". Click for save options.");
    setSegmentState(dirtySegment, dirtyTabs > 0 ? "jvn-status-dirty" : "jvn-status-clean",
        "jvn-status-clean", "jvn-status-dirty");
    StringBuilder tip = new StringBuilder();
    tip.append(closableTabs).append(" closable tab").append(closableTabs == 1 ? "" : "s");
    if (canUndo) tip.append(" • undo available");
    if (canRedo) tip.append(" • redo available");
    setTooltip(dirtySegment, tip.toString());
  }

  public void setDiagnostics(int errors, int warnings) {
    errors = Math.max(0, errors);
    warnings = Math.max(0, warnings);
    if (errors > 0) {
      diagnosticsLabel.setText(errors + " Error" + (errors == 1 ? "" : "s"));
    } else if (warnings > 0) {
      diagnosticsLabel.setText(warnings + " Warning" + (warnings == 1 ? "" : "s"));
    } else {
      diagnosticsLabel.setText("0 Problems");
    }
    setTooltip(diagnosticsLabel, errors == 0 && warnings == 0
        ? "No active-file diagnostics."
        : errors + " error" + (errors == 1 ? "" : "s") + ", "
            + warnings + " warning" + (warnings == 1 ? "" : "s") + ". Click to open Diagnostics.");
    setSegmentState(
        diagnosticsSegment,
        errors > 0 ? "jvn-status-diagnostics-error" : warnings > 0 ? "jvn-status-diagnostics-warn" : "jvn-status-diagnostics-ok",
        "jvn-status-diagnostics-ok",
        "jvn-status-diagnostics-warn",
        "jvn-status-diagnostics-error");
  }

  public void setTheme(EditorTheme.Theme theme) {
    boolean light = theme == EditorTheme.Theme.LIGHT;
    themeLabel.setText(light ? "Light" : "Dark");
    recolorIcons(light ? LIGHT_ICON_COLOR : DARK_ICON_COLOR);
  }

  public void setLineEnding(String lineEnding) {
    lineEndingLabel.setText(clean(lineEnding, "LF"));
  }

  public void setEncoding(String encoding) {
    encodingLabel.setText(clean(encoding, StandardCharsets.UTF_8.name()));
  }

  public void setOnRevealProjectRoot(Runnable action) {
    onRevealProjectRoot = action == null ? NO_OP : action;
  }

  public void setOnCopyProjectRootPath(Runnable action) {
    onCopyProjectRootPath = action == null ? NO_OP : action;
  }

  public void setOnRunProject(Runnable action) {
    onRunProject = action == null ? NO_OP : action;
  }

  public void setOnOpenVersionControl(Runnable action) {
    onOpenVersionControl = action == null ? NO_OP : action;
  }

  public void setOnOpenSettings(Runnable action) {
    onOpenSettings = action == null ? NO_OP : action;
  }

  public void setOnRevealActiveFile(Runnable action) {
    onRevealActiveFile = action == null ? NO_OP : action;
  }

  public void setOnCopyActiveFilePath(Runnable action) {
    onCopyActiveFilePath = action == null ? NO_OP : action;
  }

  public void setOnSaveAll(Runnable action) {
    onSaveAll = action == null ? NO_OP : action;
  }

  public void setOnOpenDiagnostics(Runnable action) {
    onOpenDiagnostics = action == null ? NO_OP : action;
  }

  private static HBox segment(Label label) {
    return segment(label, "");
  }

  private Region icon(Region region) {
    icons.add(region);
    return region;
  }

  private void installMenus() {
    installMenu(productSegment, () -> menu(
        item("Editor Settings", onOpenSettings),
        item("Version Control", onOpenVersionControl)));
    installMenu(branchSegment, () -> menu(
        item("Open Version Control", onOpenVersionControl),
        item("Copy Project Path", onCopyProjectRootPath)));
    installMenu(messageSegment, () -> menu(
        item("Open Settings", onOpenSettings),
        item("Open Diagnostics", onOpenDiagnostics)));
    installMenu(projectSegment, () -> menu(
        item("Reveal Project Root", onRevealProjectRoot),
        item("Copy Project Path", onCopyProjectRootPath),
        separatorItem(),
        item("Run Project", onRunProject),
        item("Version Control", onOpenVersionControl)));
    installMenu(activeFileSegment, () -> menu(
        item("Reveal Active File", onRevealActiveFile),
        item("Copy Active File Path", onCopyActiveFilePath),
        separatorItem(),
        item("Save All", onSaveAll)));
    installMenu(positionSegment, () -> menu(
        item("Reveal Active File", onRevealActiveFile),
        item("Copy Active File Path", onCopyActiveFilePath)));
    installMenu(dirtySegment, () -> menu(
        item("Save All", onSaveAll),
        item("Copy Active File Path", onCopyActiveFilePath)));
    installMenu(diagnosticsSegment, () -> menu(
        item("Open Diagnostics", onOpenDiagnostics),
        item("Open Settings", onOpenSettings)));
    installMenu(themeSegment, () -> menu(
        item("Editor Settings", onOpenSettings)));
  }

  private static void installMenu(Node node, java.util.function.Supplier<ContextMenu> menuSupplier) {
    node.setOnMouseClicked(event -> {
      if (event.getButton() != MouseButton.PRIMARY && event.getButton() != MouseButton.SECONDARY) return;
      ContextMenu menu = menuSupplier.get();
      if (menu == null || menu.getItems().isEmpty()) return;
      menu.show(node, Side.TOP, 0, 0);
      event.consume();
    });
  }

  private static ContextMenu menu(MenuItem... items) {
    ContextMenu menu = new ContextMenu();
    for (MenuItem item : items) {
      if (item != null) menu.getItems().add(item);
    }
    return menu;
  }

  private static MenuItem item(String label, Runnable action) {
    MenuItem item = new MenuItem(label);
    item.setDisable(action == NO_OP);
    if (action != NO_OP) item.setOnAction(e -> action.run());
    return item;
  }

  private static MenuItem separatorItem() {
    return new SeparatorMenuItem();
  }

  private static void setSegmentState(HBox segment, String active, String... states) {
    segment.getStyleClass().removeAll(states);
    if (active != null && !active.isBlank() && !segment.getStyleClass().contains(active)) {
      segment.getStyleClass().add(active);
    }
  }

  private void recolorIcons(String color) {
    for (Region icon : icons) {
      if (icon == null) continue;
      String style = icon.getStyle();
      if (style == null || style.isBlank()) continue;
      icon.setStyle(style.replaceAll("-fx-background-color:\\s*[^;]+;", "-fx-background-color: " + color + ";"));
    }
  }

  private static HBox segment(Label label, String extraStyleClass) {
    HBox box = baseSegment(extraStyleClass);
    box.getChildren().add(label);
    return box;
  }

  private static HBox segment(Node icon, Label label) {
    return segment(icon, label, "");
  }

  private static HBox segment(Node icon, Label label, String extraStyleClass) {
    HBox box = baseSegment(extraStyleClass);
    box.getChildren().add(icon);
    box.getChildren().add(label);
    return box;
  }

  private static HBox baseSegment(String extraStyleClass) {
    HBox box = new HBox(5);
    box.getStyleClass().add("jvn-status-segment");
    if (!extraStyleClass.isBlank()) {
      box.getStyleClass().add(extraStyleClass);
    }
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  private static Label segmentLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("jvn-status-label");
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setMaxWidth(260);
    return label;
  }

  private static Region separator() {
    Region separator = new Region();
    separator.getStyleClass().add("jvn-status-separator");
    separator.setMinWidth(1);
    separator.setPrefWidth(1);
    separator.setMaxWidth(1);
    return separator;
  }

  private static void setTooltip(Label label, String text) {
    if (label == null) return;
    if (text == null || text.isBlank()) {
      label.setTooltip(null);
    } else {
      label.setTooltip(new Tooltip(text));
    }
  }

  private static void setTooltip(Node node, String text) {
    if (node == null) return;
    if (text == null || text.isBlank()) {
      Tooltip.uninstall(node, null);
    } else {
      Tooltip.install(node, new Tooltip(text));
    }
  }

  private static String clean(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String javaFeatureVersion() {
    String version = System.getProperty("java.version", "");
    if (version.isBlank()) return "--";
    if (version.startsWith("1.")) {
      int next = version.indexOf('.', 2);
      return next > 0 ? version.substring(2, next) : version.substring(2);
    }
    int dot = version.indexOf('.');
    int dash = version.indexOf('-');
    int end = dot > 0 ? dot : (dash > 0 ? dash : version.length());
    return version.substring(0, end);
  }

  private static String resolveBranch(Path start) {
    Path dir = start == null ? null : start.toAbsolutePath().normalize();
    for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
      Path gitPath = dir.resolve(".git");
      Optional<Path> gitDirOpt = resolveGitDir(gitPath);
      if (gitDirOpt.isEmpty()) continue;
      Path gitDir = gitDirOpt.get();
      Path head = gitDir.resolve("HEAD");
      if (!Files.isRegularFile(head)) continue;
      try {
        String value = Files.readString(head).trim();
        if (value.startsWith("ref:")) {
          String ref = value.substring(4).trim();
          int slash = ref.lastIndexOf('/');
          return slash >= 0 ? ref.substring(slash + 1) : ref;
        }
        return value.length() > 7 ? value.substring(0, 7).toLowerCase(Locale.ROOT) : value;
      } catch (Exception ignored) {
        return "No branch";
      }
    }
    return "No branch";
  }

  private static Optional<Path> resolveGitDir(Path gitPath) {
    try {
      if (gitPath == null) return Optional.empty();
      if (Files.isDirectory(gitPath)) return Optional.of(gitPath);
      if (Files.isRegularFile(gitPath)) {
        String text = Files.readString(gitPath).trim();
        if (text.startsWith("gitdir:")) {
          Path target = Path.of(text.substring("gitdir:".length()).trim());
          Path parent = gitPath.getParent();
          if (parent == null) return Optional.empty();
          return Optional.of(target.isAbsolute()
              ? target.normalize()
              : parent.resolve(target).normalize());
        }
      }
    } catch (Exception ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }
}
