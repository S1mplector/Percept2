package com.jvn.editor.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.jvn.editor.AppBuildInfo;

import javafx.application.Platform;
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
  private static final long GIT_REFRESH_INTERVAL_MS = 5_000L;
  private static final long GIT_STATUS_TIMEOUT_MS = 650L;
  private static final String SEGMENT_TOOLTIP_KEY = "jvn.status.tooltip";

  private final List<Region> icons = new ArrayList<>();
  private final Label productLabel = segmentLabel("");
  private final Label branchLabel = segmentLabel("No branch");
  private final Label gitStateLabel = segmentLabel("Git --");
  private final Label messageLabel = segmentLabel("Ready");
  private final Label projectLabel = segmentLabel("No project");
  private final Label activeFileLabel = segmentLabel("No file");
  private final Label positionLabel = segmentLabel("Ln --");
  private final Label fileMetaLabel = segmentLabel("");
  private final Label workspaceLabel = segmentLabel("No tabs");
  private final Label dirtyLabel = segmentLabel("Saved");
  private final Label diagnosticsLabel = segmentLabel("0 Problems");
  private final Label encodingLabel = segmentLabel("UTF-8");
  private final Label lineEndingLabel = segmentLabel("LF");
  private final Label memoryLabel = segmentLabel("Heap --");
  private final Label javaLabel = segmentLabel("Java " + javaFeatureVersion());
  private final Label themeLabel = segmentLabel("Dark");
  private final Label versionLabel = segmentLabel("");
  private final HBox productSegment;
  private final HBox branchSegment;
  private final HBox gitStateSegment;
  private final HBox messageSegment;
  private final HBox projectSegment;
  private final HBox activeFileSegment;
  private final HBox positionSegment;
  private final HBox fileMetaSegment;
  private final HBox workspaceSegment;
  private final HBox dirtySegment;
  private final HBox diagnosticsSegment;
  private final HBox memorySegment;
  private final HBox themeSegment;
  private final Region gitStateSeparator;
  private final Region fileMetaSeparator;
  private final Region workspaceSeparator;
  private final Region memorySeparator;
  private Path activeProjectPath;
  private Path activeGitRoot;
  private GitState cachedGitState = GitState.noRepo();
  private long lastGitProbeMillis = -1L;
  private boolean gitProbeInFlight;
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
    gitStateSegment = segment(icon(CssIcon.check(DARK_ICON_COLOR)), gitStateLabel);
    messageSegment = segment(icon(CssIcon.speech(DARK_ICON_COLOR)), messageLabel, "jvn-status-message");
    projectSegment = segment(icon(CssIcon.folder(DARK_ICON_COLOR)), projectLabel);
    activeFileSegment = segment(icon(CssIcon.document(DARK_ICON_COLOR)), activeFileLabel);
    positionSegment = segment(icon(CssIcon.edit(DARK_ICON_COLOR)), positionLabel);
    fileMetaSegment = segment(icon(CssIcon.label(DARK_ICON_COLOR)), fileMetaLabel);
    workspaceSegment = segment(icon(CssIcon.dock(DARK_ICON_COLOR)), workspaceLabel);
    dirtySegment = segment(icon(CssIcon.save(DARK_ICON_COLOR)), dirtyLabel);
    diagnosticsSegment = segment(icon(CssIcon.warning(DARK_ICON_COLOR)), diagnosticsLabel, "jvn-status-diagnostics-ok");
    memorySegment = segment(icon(CssIcon.memory(DARK_ICON_COLOR)), memoryLabel);
    themeSegment = segment(icon(CssIcon.palette(DARK_ICON_COLOR)), themeLabel);
    gitStateSeparator = separator();
    fileMetaSeparator = separator();
    workspaceSeparator = separator();
    memorySeparator = separator();
    setOptionalSegmentVisible(gitStateSeparator, gitStateSegment, false);
    setOptionalSegmentVisible(fileMetaSeparator, fileMetaSegment, false);
    setOptionalSegmentVisible(workspaceSeparator, workspaceSegment, false);
    setOptionalSegmentVisible(memorySeparator, memorySegment, false);

    installMenus();

    getChildren().addAll(
        productSegment,
        separator(),
        branchSegment,
        gitStateSeparator,
        gitStateSegment,
        separator(),
        messageSegment,
        spacer,
        projectSegment,
        separator(),
        activeFileSegment,
        separator(),
        positionSegment,
        fileMetaSeparator,
        fileMetaSegment,
        workspaceSeparator,
        workspaceSegment,
        separator(),
        dirtySegment,
        separator(),
        diagnosticsSegment,
        separator(),
        segment(encodingLabel),
        separator(),
        segment(lineEndingLabel),
        memorySeparator,
        memorySegment,
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
      activeProjectPath = null;
      activeGitRoot = null;
      cachedGitState = GitState.noRepo();
      applyGitState(cachedGitState);
      setTooltip(projectLabel, "No project is open.");
      setTooltip(branchLabel, "No Git repository detected.");
      return;
    }
    activeProjectPath = projectRoot.toPath().toAbsolutePath().normalize();
    projectLabel.setText(projectRoot.getName().isBlank() ? projectRoot.getAbsolutePath() : projectRoot.getName());
    setTooltip(projectLabel, projectRoot.getAbsolutePath());
    Optional<GitRepository> repoOpt = resolveGitRepository(activeProjectPath);
    if (repoOpt.isEmpty()) {
      activeGitRoot = null;
      cachedGitState = GitState.noRepo();
      branchLabel.setText("No branch");
      setTooltip(branchLabel, "No Git repository detected.");
      applyGitState(cachedGitState);
      return;
    }
    GitRepository repo = repoOpt.get();
    if (!Objects.equals(activeGitRoot, repo.workTreeRoot())) {
      activeGitRoot = repo.workTreeRoot();
      cachedGitState = GitState.probing();
      lastGitProbeMillis = -1L;
    }
    String branch = resolveBranch(repo.gitDir());
    branchLabel.setText(branch);
    setTooltip(branchLabel, branch.equals("No branch") ? "No Git repository detected." : "Git branch: " + branch);
    applyGitState(cachedGitState);
    refreshGitStateAsync(repo.workTreeRoot());
  }

  public void setActiveFile(String fileName, String kind, int oneBasedLine) {
    setActiveFile(fileName, kind, oneBasedLine, null);
  }

  public void setActiveFile(String fileName, String kind, int oneBasedLine, File file) {
    String cleanName = clean(fileName, "No file");
    String cleanKind = clean(kind, "");
    activeFileLabel.setText(cleanKind.isBlank() || cleanName.equals("No file")
        ? cleanName
        : cleanName + " (" + cleanKind + ")");
    setTooltip(activeFileLabel, activeFileLabel.getText());
    positionLabel.setText(oneBasedLine > 0 ? "Ln " + oneBasedLine : "Ln --");
    updateFileMetadata(file);
  }

  public void setWorkspaceState(int dirtyTabs, int closableTabs, boolean canUndo, boolean canRedo) {
    dirtyTabs = Math.max(0, dirtyTabs);
    closableTabs = Math.max(0, closableTabs);
    workspaceLabel.setText(closableTabs == 0
        ? "No tabs"
        : closableTabs + " tab" + (closableTabs == 1 ? "" : "s"));
    setOptionalSegmentVisible(workspaceSeparator, workspaceSegment, true);
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
    setTooltip(workspaceSegment, tip.toString());
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

  public void setMemoryUsage(long usedBytes, long committedBytes, long maxBytes) {
    if (usedBytes < 0) {
      setOptionalSegmentVisible(memorySeparator, memorySegment, false);
      return;
    }
    setOptionalSegmentVisible(memorySeparator, memorySegment, true);
    String used = formatBytes(usedBytes);
    String max = maxBytes > 0 ? formatBytes(maxBytes) : formatBytes(committedBytes);
    memoryLabel.setText(maxBytes > 0 || committedBytes > 0 ? "Heap " + used + "/" + max : "Heap " + used);
    double ratio = maxBytes > 0 ? (double) usedBytes / Math.max(1L, maxBytes) : 0.0;
    setTooltip(memorySegment, maxBytes > 0
        ? "Heap memory: " + used + " used of " + max + " max."
        : "Heap memory: " + used + " used, " + max + " committed.");
    setSegmentState(memorySegment,
        ratio >= 0.90 ? "jvn-status-diagnostics-error" : ratio >= 0.75 ? "jvn-status-diagnostics-warn" : "",
        "jvn-status-diagnostics-warn",
        "jvn-status-diagnostics-error");
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
    installMenu(gitStateSegment, () -> menu(
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
    installMenu(fileMetaSegment, () -> menu(
        item("Reveal Active File", onRevealActiveFile),
        item("Copy Active File Path", onCopyActiveFilePath)));
    installMenu(workspaceSegment, () -> menu(
        item("Save All", onSaveAll),
        item("Editor Settings", onOpenSettings)));
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

  private static void setSegmentVisible(Node segment, boolean visible) {
    if (segment == null) return;
    segment.setVisible(visible);
    segment.setManaged(visible);
  }

  private static void setOptionalSegmentVisible(Node separator, Node segment, boolean visible) {
    setSegmentVisible(separator, visible);
    setSegmentVisible(segment, visible);
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
    Object existing = node.getProperties().remove(SEGMENT_TOOLTIP_KEY);
    if (existing instanceof Tooltip tooltip) {
      Tooltip.uninstall(node, tooltip);
    }
    if (text == null || text.isBlank()) {
      return;
    } else {
      Tooltip tooltip = new Tooltip(text);
      node.getProperties().put(SEGMENT_TOOLTIP_KEY, tooltip);
      Tooltip.install(node, tooltip);
    }
  }

  private void updateFileMetadata(File file) {
    if (file == null) {
      setOptionalSegmentVisible(fileMetaSeparator, fileMetaSegment, false);
      return;
    }
    try {
      Path path = file.toPath();
      if (!Files.isRegularFile(path)) {
        setOptionalSegmentVisible(fileMetaSeparator, fileMetaSegment, false);
        return;
      }
      long size = Files.size(path);
      boolean writable = Files.isWritable(path);
      String modified = Files.getLastModifiedTime(path).toString();
      fileMetaLabel.setText(formatBytes(size) + (writable ? "" : " RO"));
      setTooltip(fileMetaSegment, "Size: " + formatBytes(size)
          + "\nModified: " + modified
          + "\nWritable: " + (writable ? "yes" : "no"));
      setOptionalSegmentVisible(fileMetaSeparator, fileMetaSegment, true);
    } catch (Exception ignored) {
      setOptionalSegmentVisible(fileMetaSeparator, fileMetaSegment, false);
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

  private void applyGitState(GitState state) {
    if (state == null || !state.available()) {
      gitStateLabel.setText("Git --");
      setTooltip(gitStateSegment, state == null ? "No Git repository detected." : state.tooltip());
      setOptionalSegmentVisible(gitStateSeparator, gitStateSegment, false);
      setSegmentState(gitStateSegment, "", "jvn-status-clean", "jvn-status-dirty", "jvn-status-diagnostics-warn");
      return;
    }
    gitStateLabel.setText(state.text());
    setTooltip(gitStateSegment, state.tooltip());
    setOptionalSegmentVisible(gitStateSeparator, gitStateSegment, true);
    setSegmentState(gitStateSegment,
        state.clean() ? "jvn-status-clean" : "jvn-status-dirty",
        "jvn-status-clean",
        "jvn-status-dirty",
        "jvn-status-diagnostics-warn");
  }

  private void refreshGitStateAsync(Path root) {
    if (root == null) return;
    long now = System.currentTimeMillis();
    if (gitProbeInFlight || (lastGitProbeMillis >= 0 && now - lastGitProbeMillis < GIT_REFRESH_INTERVAL_MS)) {
      return;
    }
    gitProbeInFlight = true;
    lastGitProbeMillis = now;
    Thread worker = new Thread(() -> {
      GitState state = probeGitState(root);
      Platform.runLater(() -> {
        try {
          if (Objects.equals(activeGitRoot, root)) {
            cachedGitState = state;
            applyGitState(state);
          }
        } finally {
          gitProbeInFlight = false;
        }
      });
    }, "jvn-status-git");
    worker.setDaemon(true);
    worker.start();
  }

  private static GitState probeGitState(Path root) {
    try {
      Process process = new ProcessBuilder(
          "git", "-C", root.toString(), "status", "--porcelain=v1", "--branch", "--untracked-files=no")
          .redirectErrorStream(true)
          .start();
      if (!process.waitFor(GIT_STATUS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        return GitState.unavailable("Git status check timed out.");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.exitValue() != 0) {
        return GitState.unavailable("Git status unavailable.");
      }
      int changes = 0;
      String branchLine = "";
      for (String line : output.split("\\R")) {
        if (line.isBlank()) continue;
        if (line.startsWith("##")) {
          branchLine = line;
          continue;
        }
        changes++;
      }
      int ahead = parseMarker(branchLine, "ahead ");
      int behind = parseMarker(branchLine, "behind ");
      String sync = "";
      if (ahead > 0) sync += " ↑" + ahead;
      if (behind > 0) sync += " ↓" + behind;
      String text = changes == 0
          ? "Clean" + sync
          : changes + " change" + (changes == 1 ? "" : "s") + sync;
      String tip = changes == 0
          ? "Tracked working tree is clean."
          : changes + " tracked file change" + (changes == 1 ? "" : "s") + ".";
      if (ahead > 0 || behind > 0) {
        tip += " Branch sync:" + (ahead > 0 ? " ahead " + ahead : "") + (behind > 0 ? " behind " + behind : "") + ".";
      }
      tip += " Untracked files are not counted in this lightweight footer check.";
      return new GitState(text, tip, changes, changes == 0, true);
    } catch (Exception ex) {
      return GitState.unavailable("Git status unavailable: " + ex.getClass().getSimpleName());
    }
  }

  private static int parseMarker(String text, String marker) {
    if (text == null || marker == null) return 0;
    int idx = text.indexOf(marker);
    if (idx < 0) return 0;
    idx += marker.length();
    int end = idx;
    while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
    if (end == idx) return 0;
    try {
      return Integer.parseInt(text.substring(idx, end));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static String resolveBranch(Path gitDir) {
    if (gitDir == null) return "No branch";
    Path head = gitDir.resolve("HEAD");
    if (!Files.isRegularFile(head)) return "No branch";
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

  private static Optional<GitRepository> resolveGitRepository(Path start) {
    Path dir = start == null ? null : start.toAbsolutePath().normalize();
    for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
      Path gitPath = dir.resolve(".git");
      Optional<Path> gitDirOpt = resolveGitDir(gitPath);
      if (gitDirOpt.isEmpty()) continue;
      return Optional.of(new GitRepository(dir, gitDirOpt.get()));
    }
    return Optional.empty();
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

  private static String formatBytes(long bytes) {
    if (bytes < 0) return "--";
    double value = bytes;
    String[] units = {"B", "KB", "MB", "GB", "TB"};
    int unit = 0;
    while (value >= 1024.0 && unit < units.length - 1) {
      value /= 1024.0;
      unit++;
    }
    if (unit == 0) return bytes + " B";
    return String.format(Locale.ROOT, value >= 10.0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
  }

  private record GitRepository(Path workTreeRoot, Path gitDir) {}

  private record GitState(String text, String tooltip, int changes, boolean clean, boolean available) {
    static GitState noRepo() {
      return new GitState("Git --", "No Git repository detected.", 0, true, false);
    }

    static GitState probing() {
      return new GitState("Checking", "Checking Git working tree state.", 0, true, true);
    }

    static GitState unavailable(String tooltip) {
      return new GitState("Git ?", tooltip, 0, true, false);
    }
  }
}
