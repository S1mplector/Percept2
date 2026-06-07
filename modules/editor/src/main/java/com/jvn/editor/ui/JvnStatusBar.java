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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Compact application-wide status bar inspired by desktop IDE chrome.
 */
public final class JvnStatusBar extends HBox {
  private static final String DARK_ICON_COLOR = "#d6d6d6";
  private static final String LIGHT_ICON_COLOR = "#3f3f3f";

  private final List<Region> icons = new ArrayList<>();
  private final Label productLabel = segmentLabel("");
  private final Label branchLabel = segmentLabel("No branch");
  private final Label messageLabel = segmentLabel("Ready");
  private final Label projectLabel = segmentLabel("No project");
  private final Label activeFileLabel = segmentLabel("No file");
  private final Label positionLabel = segmentLabel("Ln --");
  private final Label encodingLabel = segmentLabel("UTF-8");
  private final Label lineEndingLabel = segmentLabel("LF");
  private final Label javaLabel = segmentLabel("Java " + javaFeatureVersion());
  private final Label themeLabel = segmentLabel("Dark");
  private final Label versionLabel = segmentLabel("");

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

    getChildren().addAll(
        segment(icon(CssIcon.home(DARK_ICON_COLOR)), productLabel, "jvn-status-segment-strong"),
        separator(),
        segment(icon(CssIcon.branchPlus(DARK_ICON_COLOR)), branchLabel),
        separator(),
        segment(icon(CssIcon.speech(DARK_ICON_COLOR)), messageLabel, "jvn-status-message"),
        spacer,
        segment(icon(CssIcon.folder(DARK_ICON_COLOR)), projectLabel),
        separator(),
        segment(icon(CssIcon.document(DARK_ICON_COLOR)), activeFileLabel),
        separator(),
        segment(icon(CssIcon.edit(DARK_ICON_COLOR)), positionLabel),
        separator(),
        segment(encodingLabel),
        separator(),
        segment(lineEndingLabel),
        separator(),
        segment(icon(CssIcon.memory(DARK_ICON_COLOR)), javaLabel),
        separator(),
        segment(icon(CssIcon.palette(DARK_ICON_COLOR)), themeLabel),
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

  private static HBox segment(Label label) {
    return segment(label, "");
  }

  private Region icon(Region region) {
    icons.add(region);
    return region;
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
