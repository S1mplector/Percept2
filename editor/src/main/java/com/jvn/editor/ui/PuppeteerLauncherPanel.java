package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.CodeImporter;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.editor.ui.actioneditor.PropertyType;

import javafx.scene.control.Alert;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Right-panel extension that shows the VNS scene snapshot at the current cursor line
 * and provides a button to launch Puppeteer with that context.
 */
public class PuppeteerLauncherPanel extends VBox {

  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*(?:@label|label)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*@include\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_CMD_PATTERN = Pattern.compile("^\\s*\\[(?:bg|background)\\s+(\\S+)]", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARIMG_PATTERN = Pattern.compile("^\\s*@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^\\s*@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^\\s*@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern JES_TIMELINE_PATTERN = Pattern.compile("^\\s*@external\\s+jes_timeline\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_SHOW = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+show\\s+(\\S+)(?:\\s+(\\S+))?", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_HIDE = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+hide", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_MOVE = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+move\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXT_CHAR_EXPR = Pattern.compile("^\\s*@external\\s+char(?:acter)?\\s+(\\S+)\\s+(?:expression|expr)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
  private static final Set<String> KNOWN_POSITIONS = Set.of("far_left", "left", "center", "right", "far_right");

  private final Label lblHeader;
  private final Label lblLine;
  private final Label lblLineText;
  private final Label lblLabel;
  private final Label lblBackground;
  private final Label lblTimeline;
  private final Label lblSummary;
  private final VBox characterList;
  private final VBox diagnosticsList;
  private final Label lblRegisteredHeader;
  private final Label lblRegisteredSummary;
  private final VBox registeredTimelineCards;
  private final ScrollPane registeredTimelineScroll;
  private final Button btnLaunch;
  private final Button btnLaunchLabelStart;
  private final Button btnLaunchSceneStart;
  private final Button btnOpenTimeline;
  private final Button btnOpenIssue;

  private String currentSource = "";
  private int currentLine = 0;
  private File projectRoot;
  private File activeScriptFile;
  private Consumer<LaunchRequest> onLaunch;
  private Consumer<OpenTarget> onOpenTarget;
  private List<RegisteredAnimation> cachedRegisteredAnimations = List.of();
  private String cachedRegisteredAnimationsRoot = "";
  private long cachedRegisteredAnimationsStamp = Long.MIN_VALUE;

  public PuppeteerLauncherPanel() {
    setSpacing(8);
    setPadding(new Insets(12));
    setStyle("-fx-background-color: #1a1a1a;");

    lblHeader = new Label("Puppeteer Launcher");
    lblHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #e6e6e6;");

    lblLine = new Label("Line: —");
    lblLine.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    lblLineText = new Label("");
    lblLineText.setStyle("-fx-text-fill: #e6e6e6; -fx-font-family: monospace; -fx-font-size: 11px;");
    lblLineText.setWrapText(true);
    lblLineText.setMaxWidth(260);

    Label snapshotHeader = new Label("Scene Snapshot at Cursor");
    snapshotHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #4da3ff; -fx-font-size: 12px;");

    lblLabel = new Label("Label: —");
    lblLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    lblBackground = new Label("Background: —");
    lblBackground.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    lblTimeline = new Label("Timeline: —");
    lblTimeline.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    lblSummary = new Label("Snapshot: —");
    lblSummary.setStyle("-fx-text-fill: #8fc0ff; -fx-font-size: 11px;");

    Label charsHeader = new Label("Visible Characters:");
    charsHeader.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");

    characterList = new VBox(2);
    characterList.setPadding(new Insets(0, 0, 0, 8));

    Label diagnosticsHeader = new Label("Snapshot Diagnostics:");
    diagnosticsHeader.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");

    diagnosticsList = new VBox(2);
    diagnosticsList.setPadding(new Insets(0, 0, 0, 8));

    lblRegisteredHeader = new Label("Registered Animations");
    lblRegisteredHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #e6e6e6; -fx-font-size: 12px;");

    lblRegisteredSummary = new Label("No registered timelines found yet.");
    lblRegisteredSummary.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

    registeredTimelineCards = new VBox(8);
    registeredTimelineCards.setFillWidth(true);
    registeredTimelineCards.setPadding(new Insets(2, 2, 2, 2));
    registeredTimelineCards.setStyle("-fx-background-color: transparent;");

    registeredTimelineScroll = new ScrollPane(registeredTimelineCards);
    registeredTimelineScroll.setFitToWidth(true);
    registeredTimelineScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    registeredTimelineScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    registeredTimelineScroll.setPannable(true);
    registeredTimelineScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-background-insets: 0;");
    registeredTimelineScroll.setPrefViewportHeight(260);
    configureTransparentScrollPaneViewport(registeredTimelineScroll);
    VBox.setVgrow(registeredTimelineScroll, Priority.ALWAYS);

    btnLaunch = createActionButton(
        "Launch @ Cursor",
        "icon-puppeteer-launch-cursor",
        "-fx-background-color: #4da3ff; -fx-text-fill: #121212; -fx-font-weight: bold;",
        "Launch Puppeteer with scene snapshot from the current cursor line");
    btnLaunch.setOnAction(e -> {
      if (onLaunch != null) {
        onLaunch.accept(new LaunchRequest(buildSnapshot(currentLine), null));
      }
    });

    btnLaunchLabelStart = createActionButton(
        "Launch @ Label Start",
        "icon-puppeteer-launch-label",
        "-fx-background-color: #2d3240; -fx-text-fill: #d2e6ff; -fx-font-weight: bold;",
        "Launch Puppeteer from the active label start line");
    btnLaunchLabelStart.setOnAction(e -> {
      if (onLaunch == null) return;
      int labelStartLine = resolveActiveLabelStartLine(currentSource, currentLine);
      onLaunch.accept(new LaunchRequest(buildSnapshot(labelStartLine), null));
    });

    btnLaunchSceneStart = createActionButton(
        "Launch @ Scene Start",
        "icon-puppeteer-launch-scene",
        "-fx-background-color: #1f2d25; -fx-text-fill: #c8f0d0; -fx-font-weight: bold;",
        "Launch Puppeteer from the most recent background change in the active label");
    btnLaunchSceneStart.setOnAction(e -> {
      if (onLaunch == null) return;
      int sceneStartLine = resolveSceneStartLine(currentSource, currentLine);
      onLaunch.accept(new LaunchRequest(buildSnapshot(sceneStartLine), null));
    });

    btnOpenTimeline = createActionButton(
        "Open Timeline",
        "icon-puppeteer-open-timeline",
        "-fx-background-color: #2d3240; -fx-text-fill: #d2e6ff;",
        "Open the related timeline file or inline block");
    btnOpenTimeline.setOnAction(e -> {
      if (onOpenTarget == null) return;
      OpenTarget target = resolveTimelineOpenTarget(buildSnapshot(currentLine));
      if (target != null) onOpenTarget.accept(target);
    });

    btnOpenIssue = createActionButton(
        "Jump To Issue",
        "icon-puppeteer-jump-issue",
        "-fx-background-color: #403225; -fx-text-fill: #f0d2b8;",
        "Jump to the first launcher issue in the active VNS source");
    btnOpenIssue.setOnAction(e -> {
      if (onOpenTarget == null) return;
      OpenTarget target = resolvePrimaryIssueOpenTarget(buildSnapshot(currentLine));
      if (target != null) onOpenTarget.accept(target);
    });

    HBox actionRow = new HBox(6, btnLaunch, btnLaunchLabelStart, btnLaunchSceneStart);
    HBox.setHgrow(btnLaunch, Priority.ALWAYS);
    HBox.setHgrow(btnLaunchLabelStart, Priority.ALWAYS);
    HBox.setHgrow(btnLaunchSceneStart, Priority.ALWAYS);

    HBox openRow = new HBox(6, btnOpenTimeline, btnOpenIssue);
    HBox.setHgrow(btnOpenTimeline, Priority.ALWAYS);
    HBox.setHgrow(btnOpenIssue, Priority.ALWAYS);

    getChildren().addAll(
        lblHeader,
        new Separator(),
        lblLine,
        lblLineText,
        new Separator(),
        snapshotHeader,
        lblLabel,
        lblBackground,
        lblTimeline,
        lblSummary,
        charsHeader,
        characterList,
        new Separator(),
        diagnosticsHeader,
        diagnosticsList,
        new Separator(),
        openRow,
        actionRow,
        new Separator(),
        lblRegisteredHeader,
        lblRegisteredSummary,
        registeredTimelineScroll
    );

    updateEmpty();
  }

  public void setOnLaunch(Consumer<LaunchRequest> handler) {
    this.onLaunch = handler;
  }

  public void setOnOpenTarget(Consumer<OpenTarget> handler) {
    this.onOpenTarget = handler;
  }

  public void setProjectRoot(File projectRoot) {
    this.projectRoot = projectRoot;
    invalidateRegisteredAnimationsCache();
    refresh();
  }

  public void setActiveScriptFile(File activeScriptFile) {
    this.activeScriptFile = activeScriptFile;
  }

  public void setSource(String source) {
    this.currentSource = source != null ? source : "";
    refresh();
  }

  public void setCaretLine(int zeroBased) {
    this.currentLine = zeroBased;
    refresh();
  }

  public void clear() {
    currentSource = "";
    currentLine = 0;
    updateEmpty();
  }

  private void updateEmpty() {
    lblLine.setText("Line: —");
    lblLineText.setText("(no VNS file active)");
    lblLabel.setText("Label: —");
    lblBackground.setText("Background: —");
    lblTimeline.setText("Timeline: —");
    lblSummary.setText("Snapshot: —");
    characterList.getChildren().clear();
    Label none = new Label("—");
    none.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    characterList.getChildren().add(none);
    diagnosticsList.getChildren().clear();
    Label hint = new Label("Open a .vns file to enable launch actions.");
    hint.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    diagnosticsList.getChildren().add(hint);
    btnLaunch.setDisable(true);
    btnLaunchLabelStart.setDisable(true);
    btnLaunchSceneStart.setDisable(true);
    btnOpenTimeline.setDisable(true);
    btnOpenIssue.setDisable(true);
    refreshRegisteredAnimations(null);
  }

  private void refresh() {
    if (currentSource.isEmpty()) {
      updateEmpty();
      return;
    }

    String[] lines = currentSource.split("\n", -1);
    int lineIdx = Math.min(currentLine, lines.length - 1);
    lineIdx = Math.max(0, lineIdx);

    lblLine.setText("Line: " + (lineIdx + 1));
    String lineText = lines[lineIdx].trim();
    lblLineText.setText(lineText.length() > 80 ? lineText.substring(0, 80) + "…" : lineText);

    SceneSnapshot snap = buildSnapshot(lineIdx);

    lblLabel.setText("Label: " + (snap.currentLabel != null ? snap.currentLabel : "(before first label)"));
    lblBackground.setText("Background: " + (snap.backgroundId != null ? snap.backgroundId : "—"));
    lblTimeline.setText("Timeline: " + describeTimelineContext(snap));
    lblSummary.setText(
        "Snapshot: " + snap.characters.size() + " character(s)"
            + " • scene start " + (resolveSceneStartLine(currentSource, lineIdx) + 1)
            + " • source line " + (lineIdx + 1));

    characterList.getChildren().clear();
    if (snap.characters.isEmpty()) {
      Label none = new Label("(none visible)");
      none.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
      characterList.getChildren().add(none);
    } else {
      for (CharacterEntry ch : snap.characters) {
        String text = ch.characterId + " @ " + ch.position;
        if (ch.expression != null && !ch.expression.equals("neutral")) {
          text += " [" + ch.expression + "]";
        }
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px; -fx-font-family: monospace;");
        characterList.getChildren().add(lbl);
      }
    }

    diagnosticsList.getChildren().clear();
    List<String> diagnostics = buildDiagnostics(snap, projectRoot);
    if (diagnostics.isEmpty()) {
      Label ok = new Label("All visible snapshot assets are mapped.");
      ok.setStyle("-fx-text-fill: #8bd17c; -fx-font-size: 11px;");
      diagnosticsList.getChildren().add(ok);
    } else {
      for (String msg : diagnostics) {
        Label warn = new Label("• " + msg);
        warn.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px;");
        warn.setWrapText(true);
        diagnosticsList.getChildren().add(warn);
      }
    }

    btnLaunch.setDisable(false);
    btnLaunchLabelStart.setDisable(false);
    btnLaunchSceneStart.setDisable(false);
    btnOpenTimeline.setDisable(resolveTimelineOpenTarget(snap) == null);
    btnOpenIssue.setDisable(resolvePrimaryIssueOpenTarget(snap) == null);
    refreshRegisteredAnimations(snap);
  }

  private static Button createActionButton(String text, String iconClass, String style, String tooltip) {
    Button button = new Button(text);
    button.setStyle(style);
    button.setMaxWidth(Double.MAX_VALUE);
    button.setGraphic(makeIcon(iconClass));
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setGraphicTextGap(8);
    button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private static Label makeIcon(String iconClass) {
    Label icon = new Label();
    icon.getStyleClass().addAll("icon", iconClass);
    icon.setMouseTransparent(true);
    return icon;
  }

  private static void configureTransparentScrollPaneViewport(ScrollPane scrollPane) {
    if (scrollPane == null) return;
    Runnable applyViewportStyle = () -> {
      if (scrollPane.lookup(".viewport") instanceof Region viewport) {
        viewport.setStyle("-fx-background-color: transparent;");
      }
    };
    scrollPane.skinProperty().addListener((obs, oldSkin, newSkin) -> applyViewportStyle.run());
    applyViewportStyle.run();
  }

  private void refreshRegisteredAnimations(SceneSnapshot snapshot) {
    registeredTimelineCards.getChildren().clear();

    if (projectRoot == null) {
      lblRegisteredSummary.setText("Open a project to browse scripts/timelines.");
      registeredTimelineCards.getChildren().add(createPlaceholderCard(
          "No project root is active.",
          "Open a project or VNS file to browse registered Puppeteer timelines."));
      return;
    }

    List<RegisteredAnimation> animations = getRegisteredAnimations();
    String preferredTimelineName = snapshot != null ? snapshot.preferredTimelineName() : null;
    int count = animations.size();
    int clusterCount = countClusters(animations);
    String summary = count == 1
        ? "1 timeline found"
        : count + " timelines found";
    if (clusterCount > 1) {
      summary += " across " + clusterCount + " clusters";
    }
    summary += " in scripts/timelines.";
    if (preferredTimelineName != null && !preferredTimelineName.isBlank()) {
      boolean matched = animations.stream()
          .anyMatch(animation -> preferredTimelineName.equals(resolveRelativeTimelineStem(animation)));
      summary += matched
          ? " Current cursor references one of them."
          : " Current cursor timeline is not registered on disk.";
    }
    lblRegisteredSummary.setText(summary);

    if (animations.isEmpty()) {
      registeredTimelineCards.getChildren().add(createPlaceholderCard(
          "No timelines registered yet.",
          "Save a Puppeteer timeline to scripts/timelines to reopen it directly from this launcher."));
      return;
    }

    Map<String, List<RegisteredAnimation>> byCluster = new LinkedHashMap<>();
    for (RegisteredAnimation animation : animations) {
      byCluster.computeIfAbsent(animation.clusterName(), ignored -> new ArrayList<>()).add(animation);
    }

    boolean showClusterHeaders = byCluster.size() > 1
        || !byCluster.keySet().stream().allMatch(cluster -> "Root".equals(cluster));
    for (Map.Entry<String, List<RegisteredAnimation>> entry : byCluster.entrySet()) {
      if (showClusterHeaders) {
        Label clusterHeader = new Label(entry.getKey());
        clusterHeader.setStyle("-fx-text-fill: #8fc0ff; -fx-font-size: 10px; -fx-font-weight: bold;");
        registeredTimelineCards.getChildren().add(clusterHeader);
      }
      for (RegisteredAnimation animation : entry.getValue()) {
        registeredTimelineCards.getChildren().add(createRegisteredAnimationCard(animation, snapshot));
      }
    }
  }

  private VBox createRegisteredAnimationCard(RegisteredAnimation animation, SceneSnapshot snapshot) {
    String preferredTimelineName = snapshot != null ? snapshot.preferredTimelineName() : null;
    String timelineId = resolveRelativeTimelineStem(animation);
    boolean suggested = preferredTimelineName != null && preferredTimelineName.equals(timelineId);
    boolean importable = animation.importable();
    String scenePreviewText = shouldShowScenePreview(animation, suggested)
        ? describeScenePreview(snapshot)
        : "";
    String resolvedPreviewText = resolveRegisteredAnimationPreviewText(animation, snapshot);

    Label title = new Label(animation.name());
    title.setStyle("-fx-text-fill: #f2f4f8; -fx-font-size: 11px; -fx-font-weight: bold;");

    HBox titleRow = new HBox(6);
    titleRow.getChildren().add(title);

    if (suggested) {
      Label badge = new Label("Cursor Match");
      badge.setStyle("-fx-background-color: #20416a; -fx-background-radius: 999; -fx-padding: 1 7 1 7; -fx-text-fill: #cfe6ff; -fx-font-size: 9px; -fx-font-weight: bold;");
      titleRow.getChildren().add(badge);
    }

    if (!importable) {
      Label badge = new Label("Import Issue");
      badge.setStyle("-fx-background-color: #5a2a2a; -fx-background-radius: 999; -fx-padding: 1 7 1 7; -fx-text-fill: #ffd6d6; -fx-font-size: 9px; -fx-font-weight: bold;");
      titleRow.getChildren().add(badge);
    }

    if (animation.relativePath() != null
        && !animation.relativePath().isBlank()
        && !animation.relativePath().equals(animation.name() + ".jes")) {
      Label badge = new Label(animation.relativePath());
      badge.setStyle("-fx-background-color: #323643; -fx-background-radius: 999; -fx-padding: 1 7 1 7; -fx-text-fill: #c6cad2; -fx-font-size: 9px;");
      titleRow.getChildren().add(badge);
    }

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    titleRow.getChildren().add(spacer);

    Button openButton = createCardActionButton(
        "icon-timeline-open",
        "Open timeline",
        "-fx-background-color: #323643; -fx-background-radius: 8;");
    openButton.setDisable(!importable);
    openButton.setOnAction(e -> {
      if (onLaunch != null && importable) {
        onLaunch.accept(new LaunchRequest(snapshot, timelineId));
      }
    });

    Button renameButton = createCardActionButton(
        "icon-timeline-edit",
        "Rename timeline",
        "-fx-background-color: #323643; -fx-background-radius: 8;");
    renameButton.setOnAction(e -> renameRegisteredAnimation(animation));

    Button deleteButton = createCardActionButton(
        "icon-timeline-delete",
        "Delete timeline",
        "-fx-background-color: #402727; -fx-background-radius: 8;");
    deleteButton.setOnAction(e -> deleteRegisteredAnimation(animation));

    titleRow.getChildren().addAll(openButton, renameButton, deleteButton);

    Label meta = new Label(animation.statsText());
    meta.setStyle("-fx-text-fill: #9cadc7; -fx-font-size: 9px;");
    meta.setWrapText(true);

    Label preview = new Label(resolvedPreviewText);
    preview.setStyle("-fx-text-fill: #d5d9e0; -fx-font-size: 9px; -fx-font-family: monospace;");
    preview.setWrapText(true);

    VBox body = new VBox(3, titleRow, meta);
    if (!scenePreviewText.isBlank()) {
      Label scenePreview = new Label(scenePreviewText);
      scenePreview.setStyle("-fx-text-fill: #8fc0ff; -fx-font-size: 9px;");
      scenePreview.setWrapText(true);
      body.getChildren().add(scenePreview);
    }
    body.getChildren().add(preview);
    if (!importable && animation.warningMessage() != null && !animation.warningMessage().isBlank()) {
      Label warning = new Label(animation.warningMessage());
      warning.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 9px;");
      warning.setWrapText(true);
      body.getChildren().add(warning);
    }
    body.setMaxWidth(Double.MAX_VALUE);
    body.setPadding(new Insets(8, 10, 8, 10));
    Tooltip.install(body, new Tooltip(animation.file().getName()));
    String style = suggested
        ? "-fx-background-color: #1f2b38; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #4da3ff;"
        : "-fx-background-color: #24262c; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #3a3d46;";
    if (!importable) {
      style = "-fx-background-color: #2a2323; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #694242;";
    }
    body.setStyle(style);
    return body;
  }

  private VBox createPlaceholderCard(String title, String detail) {
    Label titleLabel = new Label(title);
    titleLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");

    Label detailLabel = new Label(detail);
    detailLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    detailLabel.setWrapText(true);

    VBox box = new VBox(4, titleLabel, detailLabel);
    box.setPadding(new Insets(12));
    box.setStyle("-fx-background-color: #202226; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #333740;");
    return box;
  }

  private static int countClusters(List<RegisteredAnimation> animations) {
    Set<String> clusters = new HashSet<>();
    for (RegisteredAnimation animation : animations) {
      if (animation == null) continue;
      clusters.add(animation.clusterName());
    }
    return clusters.isEmpty() ? 0 : clusters.size();
  }

  private Button createCardActionButton(String iconClass, String tooltip, String style) {
    Button button = new Button();
    button.setGraphic(makeIcon(iconClass));
    button.setTooltip(new Tooltip(tooltip));
    button.setAccessibleText(tooltip);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.getStyleClass().add("layout-studio-icon-button");
    button.setStyle(style);
    button.setFocusTraversable(false);
    return button;
  }

  private void renameRegisteredAnimation(RegisteredAnimation animation) {
    if (animation == null || projectRoot == null) return;
    TextInputDialog dialog = new TextInputDialog(resolveRelativeTimelineStem(animation));
    EditorTheme.apply(dialog);
    dialog.setTitle("Rename Timeline");
    dialog.setHeaderText("Rename or move '" + animation.name() + "'");
    dialog.setContentText("Timeline path:");
    dialog.showAndWait().ifPresent(rawName -> {
      try {
        String normalized = normalizeTimelineRelativeStem(rawName);
        Path timelinesRoot = resolveTimelinesDir(projectRoot).toPath().toAbsolutePath().normalize();
        Path source = animation.file().toPath().toAbsolutePath().normalize();
        Path target = timelinesRoot.resolve(normalized + ".jes").normalize();
        if (!target.startsWith(timelinesRoot)) {
          throw new IOException("Timeline path must stay inside scripts/timelines.");
        }
        if (source.equals(target)) return;
        if (Files.exists(target)) {
          throw new IOException("A timeline with that name already exists.");
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target);
        pruneEmptyTimelineDirectories(source.getParent(), timelinesRoot);
        invalidateRegisteredAnimationsCache();
        refresh();
      } catch (IOException ex) {
        showTimelineFileError("Rename Failed", "Could not rename timeline.", ex.getMessage());
      }
    });
  }

  private void deleteRegisteredAnimation(RegisteredAnimation animation) {
    if (animation == null || projectRoot == null) return;
    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    EditorTheme.apply(confirm);
    confirm.setTitle("Delete Timeline");
    confirm.setHeaderText("Delete '" + animation.name() + "'?");
    confirm.setContentText("This removes " + animation.relativePath() + " from scripts/timelines.");
    confirm.showAndWait().ifPresent(result -> {
      if (result != ButtonType.OK) return;
      try {
        Path timelinesRoot = resolveTimelinesDir(projectRoot).toPath().toAbsolutePath().normalize();
        Path source = animation.file().toPath().toAbsolutePath().normalize();
        Files.deleteIfExists(source);
        pruneEmptyTimelineDirectories(source.getParent(), timelinesRoot);
        invalidateRegisteredAnimationsCache();
        refresh();
      } catch (IOException ex) {
        showTimelineFileError("Delete Failed", "Could not delete timeline.", ex.getMessage());
      }
    });
  }

  private void showTimelineFileError(String title, String header, String detail) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    EditorTheme.apply(alert);
    alert.setTitle(title);
    alert.setHeaderText(header);
    alert.setContentText(detail != null ? detail : "Unknown error");
    alert.showAndWait();
  }

  private static String resolveRelativeTimelineStem(RegisteredAnimation animation) {
    if (animation == null || animation.relativePath() == null || animation.relativePath().isBlank()) {
      return "";
    }
    String relativePath = animation.relativePath().replace('\\', '/');
    return relativePath.endsWith(".jes")
        ? relativePath.substring(0, relativePath.length() - 4)
        : relativePath;
  }

  private static boolean shouldShowScenePreview(RegisteredAnimation animation, boolean suggested) {
    if (animation == null) return false;
    return suggested || isEmptyTimelinePreview(animation.previewText());
  }

  private static String normalizeTimelineRelativeStem(String rawName) throws IOException {
    String normalized = rawName == null ? "" : rawName.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalized.endsWith(".jes")) {
      normalized = normalized.substring(0, normalized.length() - 4);
    }
    normalized = normalized.replaceAll("/+", "/");
    normalized = normalized.replaceAll("^/+|/+$", "");
    if (normalized.isBlank()) {
      throw new IOException("Timeline path is empty.");
    }
    if (normalized.contains("..")) {
      throw new IOException("Parent path segments are not allowed.");
    }
    return normalized;
  }

  private static void pruneEmptyTimelineDirectories(Path start, Path timelinesRoot) throws IOException {
    if (start == null || timelinesRoot == null) return;
    Path current = start;
    while (current != null && !current.equals(timelinesRoot)) {
      try (var stream = Files.list(current)) {
        if (stream.findAny().isPresent()) {
          return;
        }
      }
      Files.deleteIfExists(current);
      current = current.getParent();
    }
  }

  private static List<File> collectTimelineFiles(File timelinesDir) {
    if (timelinesDir == null || !timelinesDir.isDirectory()) return List.of();
    List<File> files = new ArrayList<>();
    try (var stream = Files.walk(timelinesDir.toPath())) {
      stream.filter(Files::isRegularFile)
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.endsWith(".jes") && !name.startsWith(".");
          })
          .sorted((left, right) -> left.toString().compareToIgnoreCase(right.toString()))
          .forEach(path -> files.add(path.toFile()));
    } catch (IOException ignored) {
    }
    return files;
  }

  private static String relativizeTimelinePath(File timelinesDir, File jesFile) {
    if (timelinesDir == null || jesFile == null) return jesFile == null ? "" : jesFile.getName();
    Path root = timelinesDir.toPath().toAbsolutePath().normalize();
    Path file = jesFile.toPath().toAbsolutePath().normalize();
    if (!file.startsWith(root)) return jesFile.getName();
    return root.relativize(file).toString().replace('\\', '/');
  }

  private static String resolveClusterName(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return "Root";
    int slash = relativePath.lastIndexOf('/');
    if (slash <= 0) return "Root";
    return relativePath.substring(0, slash);
  }

  private void invalidateRegisteredAnimationsCache() {
    cachedRegisteredAnimations = List.of();
    cachedRegisteredAnimationsRoot = "";
    cachedRegisteredAnimationsStamp = Long.MIN_VALUE;
  }

  private List<RegisteredAnimation> getRegisteredAnimations() {
    if (projectRoot == null) return List.of();
    String rootKey = projectRoot.getAbsoluteFile().toPath().normalize().toString();
    long stamp = computeRegisteredAnimationsStamp(projectRoot);
    if (!rootKey.equals(cachedRegisteredAnimationsRoot) || stamp != cachedRegisteredAnimationsStamp) {
      cachedRegisteredAnimations = discoverRegisteredAnimations(projectRoot);
      cachedRegisteredAnimationsRoot = rootKey;
      cachedRegisteredAnimationsStamp = stamp;
    }
    return cachedRegisteredAnimations;
  }

  static List<RegisteredAnimation> discoverRegisteredAnimations(File projectRoot) {
    File timelinesDir = resolveTimelinesDir(projectRoot);
    if (!timelinesDir.isDirectory()) return List.of();
    List<File> jesFiles = collectTimelineFiles(timelinesDir);
    if (jesFiles.isEmpty()) return List.of();

    List<RegisteredAnimation> animations = new ArrayList<>();
    for (File jesFile : jesFiles) {
      animations.add(summarizeRegisteredAnimation(timelinesDir, jesFile));
    }
    return List.copyOf(animations);
  }

  private static RegisteredAnimation summarizeRegisteredAnimation(File timelinesDir, File jesFile) {
    String filename = jesFile.getName();
    String timelineName = filename.endsWith(".jes") ? filename.substring(0, filename.length() - 4) : filename;
    String relativePath = relativizeTimelinePath(timelinesDir, jesFile);
    String clusterName = resolveClusterName(relativePath);
    String code;
    try {
      code = Files.readString(jesFile.toPath(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      return new RegisteredAnimation(
          timelineName,
          relativePath,
          clusterName,
          jesFile,
          "Could not read timeline file.",
          "Read failed",
          0,
          0,
          0,
          0,
          false,
          ex.getMessage());
    }

    String previewText = extractTimelinePreview(code);
    try {
      AnimationProject project = CodeImporter.importCode(timelineName, code);
      int trackCount = project.getTrackCount();
      int keyframeCount = countKeyframes(project);
      int audioCueCount = project.getAudioCues().size();
      int eventCueCount = project.getEditorEventCues().size();
      String stats = trackCount + " track(s) • "
          + keyframeCount + " keyframe(s) • "
          + formatDuration(project.getTotalDurationMs());
      if (audioCueCount > 0 || eventCueCount > 0) {
        stats += " • " + audioCueCount + " audio • " + eventCueCount + " cue(s)";
      }
      return new RegisteredAnimation(
          timelineName,
          relativePath,
          clusterName,
          jesFile,
          previewText,
          stats,
          trackCount,
          keyframeCount,
          audioCueCount,
          eventCueCount,
          true,
          "");
    } catch (Exception ex) {
      return new RegisteredAnimation(
          timelineName,
          relativePath,
          clusterName,
          jesFile,
          previewText,
          "Import warning • raw preview only",
          0,
          0,
          0,
          0,
          false,
          ex.getMessage());
    }
  }

  static String extractTimelinePreview(String code) {
    if (code == null || code.isBlank()) return "No timeline content yet.";
    List<String> previewLines = new ArrayList<>();
    boolean truncated = false;
    for (String rawLine : code.split("\n")) {
      String trimmed = rawLine == null ? "" : rawLine.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.startsWith("//")) continue;
      if ("{".equals(trimmed) || "}".equals(trimmed)) continue;
      if (trimmed.startsWith("timeline")) continue;
      previewLines.add(trimmed);
      if (previewLines.size() >= 2) {
        truncated = true;
        break;
      }
    }
    if (previewLines.isEmpty()) {
      return "Timeline block is empty.";
    }
    if (truncated) {
      int lastIndex = previewLines.size() - 1;
      previewLines.set(lastIndex, previewLines.get(lastIndex) + " …");
    }
    return String.join("\n", previewLines);
  }

  static String describeScenePreview(SceneSnapshot snapshot) {
    if (snapshot == null) return "";
    List<String> lines = new ArrayList<>();
    List<String> sceneBits = new ArrayList<>();
    if (snapshot.currentLabel != null && !snapshot.currentLabel.isBlank()) {
      sceneBits.add("label " + snapshot.currentLabel);
    }
    if (snapshot.backgroundId != null && !snapshot.backgroundId.isBlank()) {
      sceneBits.add("bg " + snapshot.backgroundId);
    }
    if (!sceneBits.isEmpty()) {
      lines.add("Scene • " + String.join(" • ", sceneBits));
    }
    if (!snapshot.characters.isEmpty()) {
      List<String> entities = new ArrayList<>();
      int shown = Math.min(snapshot.characters.size(), 3);
      for (int i = 0; i < shown; i++) {
        CharacterEntry ch = snapshot.characters.get(i);
        entities.add(formatCharacterPreview(ch));
      }
      if (snapshot.characters.size() > shown) {
        entities.add("+" + (snapshot.characters.size() - shown) + " more");
      }
      lines.add("Entities • " + String.join(", ", entities));
    } else if (!lines.isEmpty()) {
      lines.add("Entities • none visible");
    }
    return String.join("\n", lines);
  }

  static String resolveRegisteredAnimationPreviewText(RegisteredAnimation animation, SceneSnapshot snapshot) {
    String previewText = animation != null ? animation.previewText() : "";
    if (isEmptyTimelinePreview(previewText)) {
      String scenePreview = describeScenePreview(snapshot);
      if (!scenePreview.isBlank()) {
        return "No authored timeline actions yet.\nLaunch uses the scene preview above.";
      }
    }
    return previewText == null || previewText.isBlank() ? "No timeline content yet." : previewText;
  }

  private static boolean isEmptyTimelinePreview(String previewText) {
    if (previewText == null || previewText.isBlank()) return true;
    return "Timeline block is empty.".equals(previewText)
        || "No timeline content yet.".equals(previewText);
  }

  private static String formatCharacterPreview(CharacterEntry character) {
    if (character == null) return "unknown";
    String text = character.characterId + " @ " + character.position;
    if (character.expression != null && !character.expression.isBlank() && !"neutral".equals(character.expression)) {
      text += " [" + character.expression + "]";
    }
    return text;
  }

  private static int countKeyframes(AnimationProject project) {
    if (project == null) return 0;
    int total = 0;
    for (EntityTrack track : project.getTracks()) {
      if (track == null) continue;
      for (PropertyType property : track.getAnimatedProperties()) {
        total += track.getKeyframes(property).size();
      }
    }
    return total;
  }

  private static String formatDuration(double totalDurationMs) {
    if (!Double.isFinite(totalDurationMs) || totalDurationMs <= 0.0) {
      return "0.0s";
    }
    double seconds = totalDurationMs / 1000.0;
    if (seconds >= 10.0) {
      return String.format(Locale.ROOT, "%.0fs", seconds);
    }
    return String.format(Locale.ROOT, "%.1fs", seconds);
  }

  private static File resolveTimelinesDir(File projectRoot) {
    return projectRoot == null ? new File("scripts/timelines") : new File(projectRoot, "scripts/timelines");
  }

  private static long computeRegisteredAnimationsStamp(File projectRoot) {
    File timelinesDir = resolveTimelinesDir(projectRoot);
    if (!timelinesDir.isDirectory()) return Long.MIN_VALUE;
    List<File> jesFiles = collectTimelineFiles(timelinesDir);
    if (jesFiles.isEmpty()) return 0L;
    long stamp = 17L;
    for (File jesFile : jesFiles) {
      stamp = stamp * 31L + jesFile.getAbsolutePath().toLowerCase(Locale.ROOT).hashCode();
      stamp = stamp * 31L + jesFile.length();
      stamp = stamp * 31L + jesFile.lastModified();
    }
    return stamp;
  }

  // --- VNS Scene State Resolver ---

  static SceneSnapshot resolveSnapshot(String source, int upToLine) {
    return resolveSnapshot(source, upToLine, null, null);
  }

  static SceneSnapshot resolveSnapshot(
      String source,
      int upToLine,
      String sourceName,
      IncludeSourceResolver includeResolver
  ) {
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(upToLine, lines.length - 1));

    String currentLabel = null;
    String backgroundId = null;
    int backgroundLine = -1;
    Map<String, CharacterEntry> visible = new LinkedHashMap<>();
    Map<String, String> bgPaths = new LinkedHashMap<>();
    Map<String, String> charImgPaths = new LinkedHashMap<>();
    Map<String, Map<String, String>> charLayerPaths = new LinkedHashMap<>();
    String referencedTimelineName = null;
    int referencedTimelineLine = -1;

    collectDeclarations(
        source,
        limit,
        sourceName,
        includeResolver,
        bgPaths,
        charImgPaths,
        charLayerPaths,
        new HashSet<>());

    for (int i = 0; i <= limit; i++) {
      String line = lines[i];
      String commandLine = stripInlineComment(line);
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

      Matcher m;

      // Label
      m = LABEL_PATTERN.matcher(line);
      if (m.find()) {
        currentLabel = m.group(1);
        continue;
      }

      // Background command [bg id] or [background id]
      m = BG_CMD_PATTERN.matcher(line);
      if (m.find()) {
        backgroundId = m.group(1);
        backgroundLine = i;
        continue;
      }

      // @background declaration — capture id → path mapping
      m = BG_DECL_PATTERN.matcher(line);
      if (m.find()) {
        bgPaths.put(m.group(1), m.group(2).trim());
        continue;
      }

      // @charimg declaration — capture charId+expression → path mapping
      m = CHARIMG_PATTERN.matcher(line);
      if (m.find()) {
        charImgPaths.put(m.group(1) + "/" + m.group(2), m.group(3).trim());
        continue;
      }

      // @charlayer declaration — capture charId/layerId -> path mapping
      m = CHARLAYER_PATTERN.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String layerId = m.group(2);
        String path = m.group(3).trim();
        charLayerPaths.computeIfAbsent(charId, k -> new LinkedHashMap<>()).put(layerId, path);
        continue;
      }

      // @charpreset declaration — resolve $layer references into an @charimg-style mapping
      m = CHARPRESET_PATTERN.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String expr = m.group(2);
        String spec = m.group(3).trim();
        String resolved = resolvePresetSpec(charLayerPaths, charImgPaths, charId, spec);
        if (!resolved.isBlank()) {
          charImgPaths.put(charId + "/" + expr, resolved);
        }
        continue;
      }

      // [show charId pos expression?]
      CharacterEntry showEntry = parseShowCommand(commandLine, i);
      if (showEntry != null) {
        visible.put(showEntry.characterId, showEntry);
        continue;
      }

      // [hide charId]
      String hideCharacterId = parseHideCommand(commandLine);
      if (hideCharacterId != null) {
        visible.remove(hideCharacterId);
        continue;
      }

      m = JES_TIMELINE_PATTERN.matcher(line);
      if (m.find()) {
        referencedTimelineName = m.group(1);
        referencedTimelineLine = i;
        continue;
      }

      // @external character <id> show <pos> [expr]
      m = EXT_CHAR_SHOW.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String pos = m.group(2).toLowerCase(Locale.ROOT);
        String expr = m.group(3) != null ? m.group(3) : "neutral";
        visible.put(charId, new CharacterEntry(charId, pos, expr, i));
        continue;
      }

      // @external character <id> hide
      m = EXT_CHAR_HIDE.matcher(line);
      if (m.find()) {
        visible.remove(m.group(1));
        continue;
      }

      // @external character <id> move <pos>
      m = EXT_CHAR_MOVE.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String pos = m.group(2).toLowerCase(Locale.ROOT);
        CharacterEntry existing = visible.get(charId);
        String expr = existing != null ? existing.expression : "neutral";
        visible.put(charId, new CharacterEntry(charId, pos, expr, i));
        continue;
      }

      // @external character <id> expr <expression>
      m = EXT_CHAR_EXPR.matcher(line);
      if (m.find()) {
        String charId = m.group(1);
        String expr = m.group(2);
        CharacterEntry existing = visible.get(charId);
        String pos = existing != null ? existing.position : "center";
        visible.put(charId, new CharacterEntry(charId, pos, expr, i));
      }
    }

    InlineTimelineContext inlineTimeline = resolveInlineTimelineContext(source, limit);
    String inlineTimelineName = inlineTimeline != null ? deriveInlineTimelineName(currentLabel, inlineTimeline.startLine()) : null;

    return new SceneSnapshot(
        currentLabel,
        backgroundId,
        backgroundLine,
        new ArrayList<>(visible.values()),
        limit,
        bgPaths,
        charImgPaths,
        referencedTimelineName,
        referencedTimelineLine,
        inlineTimeline != null ? inlineTimeline.body() : null,
        inlineTimeline != null ? inlineTimeline.startLine() : -1,
        inlineTimelineName);
  }

  private SceneSnapshot buildSnapshot(int lineIdx) {
    return resolveSnapshot(
        currentSource,
        lineIdx,
        activeScriptFile == null ? null : activeScriptFile.getAbsolutePath(),
        this::resolveIncludeSource);
  }

  static int resolveActiveLabelStartLine(String source, int upToLine) {
    if (source == null || source.isBlank()) return 0;
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(upToLine, lines.length - 1));
    int latestLabelLine = 0;
    for (int i = 0; i <= limit; i++) {
      Matcher m = LABEL_PATTERN.matcher(lines[i]);
      if (m.find()) latestLabelLine = i;
    }
    return latestLabelLine;
  }

  static int resolveSceneStartLine(String source, int upToLine) {
    if (source == null || source.isBlank()) return 0;
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(upToLine, lines.length - 1));
    int labelStart = resolveActiveLabelStartLine(source, limit);
    int sceneStart = labelStart;
    for (int i = labelStart; i <= limit; i++) {
      String trimmed = stripInlineComment(lines[i]).trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      if (BG_CMD_PATTERN.matcher(trimmed).find()) {
        sceneStart = i;
      }
    }
    return sceneStart;
  }

  private OpenTarget resolvePrimaryIssueOpenTarget(SceneSnapshot snapshot) {
    if (snapshot == null || activeScriptFile == null) return null;
    if (snapshot.backgroundId != null && !snapshot.hasBackgroundPathMapping() && snapshot.backgroundLine >= 0) {
      return new OpenTarget(activeScriptFile, snapshot.backgroundLine + 1);
    }
    for (CharacterEntry ch : snapshot.characters) {
      if (ch == null) continue;
      if (!snapshot.hasCharacterPathMapping(ch.characterId, ch.expression) && ch.atLine >= 0) {
        return new OpenTarget(activeScriptFile, ch.atLine + 1);
      }
      if (ch.position == null || !KNOWN_POSITIONS.contains(ch.position.toLowerCase(Locale.ROOT))) {
        return new OpenTarget(activeScriptFile, ch.atLine + 1);
      }
    }
    if (snapshot.referencedTimelineLine >= 0 && snapshot.resolveTimelineFile(projectRoot) == null) {
      return new OpenTarget(activeScriptFile, snapshot.referencedTimelineLine + 1);
    }
    return null;
  }

  private OpenTarget resolveTimelineOpenTarget(SceneSnapshot snapshot) {
    if (snapshot == null) return null;
    if (snapshot.hasInlineTimeline() && activeScriptFile != null && snapshot.inlineTimelineStartLine >= 0) {
      return new OpenTarget(activeScriptFile, snapshot.inlineTimelineStartLine + 1);
    }
    File timelineFile = snapshot.resolveTimelineFile(projectRoot);
    if (timelineFile != null) {
      return new OpenTarget(timelineFile, 1);
    }
    if (snapshot.referencedTimelineLine >= 0 && activeScriptFile != null) {
      return new OpenTarget(activeScriptFile, snapshot.referencedTimelineLine + 1);
    }
    return null;
  }

  private static String describeTimelineContext(SceneSnapshot snapshot) {
    if (snapshot == null) return "—";
    if (snapshot.hasInlineTimeline()) {
      return snapshot.inlineTimelineName + " (inline block)";
    }
    if (snapshot.referencedTimelineName != null && !snapshot.referencedTimelineName.isBlank()) {
      return snapshot.referencedTimelineName;
    }
    return "—";
  }

  private static List<String> buildDiagnostics(SceneSnapshot snapshot, File projectRoot) {
    List<String> out = new ArrayList<>();
    if (snapshot == null) return out;

    if (snapshot.backgroundId != null && !snapshot.hasBackgroundPathMapping()) {
      out.add("Background '" + snapshot.backgroundId + "' has no @background path mapping.");
    }

    for (CharacterEntry ch : snapshot.characters) {
      if (ch == null || ch.characterId == null || ch.characterId.isBlank()) continue;
      if (!snapshot.hasCharacterPathMapping(ch.characterId, ch.expression)) {
        String expr = (ch.expression == null || ch.expression.isBlank()) ? "neutral" : ch.expression;
        out.add("Character '" + ch.characterId + "' expression '" + expr + "' has no @charimg/@charpreset mapping.");
      }
      if (ch.position == null || !KNOWN_POSITIONS.contains(ch.position.toLowerCase(Locale.ROOT))) {
        out.add("Character '" + ch.characterId + "' uses position '" + ch.position + "' (Puppeteer launch falls back to center).");
      }
    }
    if (snapshot.referencedTimelineName != null && !snapshot.referencedTimelineName.isBlank()
        && !snapshot.hasInlineTimeline() && snapshot.resolveTimelineFile(projectRoot) == null) {
      out.add("Timeline '" + snapshot.referencedTimelineName + "' is referenced here, but scripts/timelines/" + snapshot.referencedTimelineName + ".jes was not found.");
    }
    return out;
  }

  private static CharacterEntry parseShowCommand(String line, int atLine) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !"show".equalsIgnoreCase(tokens.get(0))) return null;
    String charId = tokens.get(1);
    if (charId == null || charId.isBlank()) return null;

    String position = null;
    String expression = null;
    for (int i = 2; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (token == null || token.isBlank()) continue;
      String lower = token.toLowerCase(Locale.ROOT);
      if ("at".equals(lower)) {
        if (i + 1 < tokens.size()) {
          String atValue = tokens.get(++i);
          if (atValue != null && !atValue.isBlank()) {
            String atLower = atValue.toLowerCase(Locale.ROOT);
            if (isKnownPosition(atLower) && position == null) {
              position = atLower;
            } else if (expression == null) {
              expression = atValue;
            }
          }
        }
        continue;
      }
      if (position == null && isKnownPosition(lower)) {
        position = lower;
        continue;
      }
      if (expression == null) expression = token;
    }

    if (position == null) position = "center";
    if (expression == null || expression.isBlank()) expression = "neutral";
    return new CharacterEntry(charId, position, expression, atLine);
  }

  private static String parseHideCommand(String line) {
    List<String> tokens = bracketTokens(line);
    if (tokens.size() < 2 || !"hide".equalsIgnoreCase(tokens.get(0))) return null;
    String charId = tokens.get(1);
    return (charId == null || charId.isBlank()) ? null : charId;
  }

  private static List<String> bracketTokens(String line) {
    String raw = stripInlineComment(line).trim();
    if (raw.length() < 2 || raw.charAt(0) != '[' || raw.charAt(raw.length() - 1) != ']') {
      return List.of();
    }
    String inner = raw.substring(1, raw.length() - 1).trim();
    if (inner.isEmpty()) return List.of();
    return Arrays.stream(inner.split("\\s+"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static String stripInlineComment(String line) {
    if (line == null || line.isBlank()) return "";
    int comment = line.indexOf('#');
    return comment >= 0 ? line.substring(0, comment) : line;
  }

  private static boolean isKnownPosition(String value) {
    return value != null && KNOWN_POSITIONS.contains(value.toLowerCase(Locale.ROOT));
  }

  private static InlineTimelineContext resolveInlineTimelineContext(String source, int cursorLine) {
    if (source == null || source.isBlank()) return null;
    String[] lines = source.split("\n", -1);
    int limit = Math.max(0, Math.min(cursorLine, lines.length - 1));
    for (int i = 0; i <= limit; i++) {
      String trimmed = stripInlineComment(lines[i]).trim();
      if (!startsInlineTimeline(trimmed)) continue;
      int openingLine = i;
      int braceDepth = countChar(trimmed, '{');
      int j = i;
      if (braceDepth == 0) {
        boolean foundOpeningBrace = false;
        while (++j < lines.length) {
          String nextTrimmed = stripInlineComment(lines[j]).trim();
          if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("#")) continue;
          if ("{".equals(nextTrimmed)) {
            braceDepth = 1;
            foundOpeningBrace = true;
            break;
          }
          break;
        }
        if (!foundOpeningBrace) {
          continue;
        }
      }
      StringBuilder block = new StringBuilder();
      int endLine = j;
      while (++endLine < lines.length && braceDepth > 0) {
        String line = lines[endLine];
        for (char c : line.toCharArray()) {
          if (c == '{') braceDepth++;
          else if (c == '}') braceDepth--;
        }
        if (braceDepth > 0) {
          block.append(line).append('\n');
        } else {
          int lastBrace = line.lastIndexOf('}');
          if (lastBrace > 0) block.append(line, 0, lastBrace).append('\n');
        }
      }
      if (braceDepth == 0 && limit >= openingLine && limit <= endLine) {
        return new InlineTimelineContext(openingLine, endLine, block.toString());
      }
      i = Math.max(i, endLine);
    }
    return null;
  }

  private static boolean startsInlineTimeline(String trimmed) {
    return trimmed.startsWith("timeline") && (trimmed.endsWith("{") || trimmed.equals("timeline"));
  }

  private static int countChar(String value, char ch) {
    int count = 0;
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) == ch) count++;
    }
    return count;
  }

  private static String deriveInlineTimelineName(String currentLabel, int startLine) {
    String base = currentLabel != null && !currentLabel.isBlank() ? currentLabel : "inline_timeline";
    String normalized = base.replaceAll("[^A-Za-z0-9_]+", "_");
    normalized = normalized.replaceAll("_+", "_");
    normalized = normalized.replaceAll("^_+|_+$", "");
    if (normalized.isBlank()) normalized = "inline_timeline";
    return normalized + "_inline_" + Math.max(1, startLine + 1);
  }

  private static void collectDeclarations(
      String source,
      int maxLineInclusive,
      String sourceName,
      IncludeSourceResolver includeResolver,
      Map<String, String> bgPaths,
      Map<String, String> charImgPaths,
      Map<String, Map<String, String>> charLayerPaths,
      Set<String> includeStack
  ) {
    if (source == null || source.isBlank()) return;
    String normalizedSource = normalizeSourceName(sourceName);
    if (!includeStack.add(normalizedSource)) return;
    try {
      String[] lines = source.split("\n", -1);
      int limit = maxLineInclusive < 0 ? lines.length - 1 : Math.min(maxLineInclusive, lines.length - 1);
      for (int i = 0; i <= limit; i++) {
        String line = lines[i];
        String trimmed = stripInlineComment(line).trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

        Matcher includeMatcher = INCLUDE_PATTERN.matcher(trimmed);
        if (includeMatcher.matches() && includeResolver != null) {
          String includePath = stripQuotes(includeMatcher.group(1).trim());
          if (!includePath.isEmpty()) {
            try {
              ResolvedInclude resolved = includeResolver.resolve(normalizedSource, includePath);
              if (resolved != null && resolved.sourceText() != null && !resolved.sourceText().isBlank()) {
                collectDeclarations(
                    resolved.sourceText(),
                    -1,
                    resolved.sourceName(),
                    includeResolver,
                    bgPaths,
                    charImgPaths,
                    charLayerPaths,
                    includeStack);
              }
            } catch (IOException ignored) {
            }
          }
          continue;
        }

        Matcher backgroundMatcher = BG_DECL_PATTERN.matcher(trimmed);
        if (backgroundMatcher.matches()) {
          bgPaths.put(backgroundMatcher.group(1), backgroundMatcher.group(2).trim());
          continue;
        }

        Matcher charImgMatcher = CHARIMG_PATTERN.matcher(trimmed);
        if (charImgMatcher.matches()) {
          charImgPaths.put(
              charImgMatcher.group(1) + "/" + charImgMatcher.group(2),
              charImgMatcher.group(3).trim());
          continue;
        }

        Matcher charLayerMatcher = CHARLAYER_PATTERN.matcher(trimmed);
        if (charLayerMatcher.matches()) {
          String charId = charLayerMatcher.group(1);
          String layerId = charLayerMatcher.group(2);
          String path = charLayerMatcher.group(3).trim();
          charLayerPaths.computeIfAbsent(charId, k -> new LinkedHashMap<>()).put(layerId, path);
          continue;
        }

        Matcher charPresetMatcher = CHARPRESET_PATTERN.matcher(trimmed);
        if (charPresetMatcher.matches()) {
          String charId = charPresetMatcher.group(1);
          String expr = charPresetMatcher.group(2);
          String spec = charPresetMatcher.group(3).trim();
          String resolved = resolvePresetSpec(charLayerPaths, charImgPaths, charId, spec);
          if (!resolved.isBlank()) {
            charImgPaths.put(charId + "/" + expr, resolved);
          }
        }
      }
    } finally {
      includeStack.remove(normalizedSource);
    }
  }

  private static String resolvePresetSpec(Map<String, Map<String, String>> layersByCharacter,
                                          Map<String, String> expressionsByCharacter,
                                          String characterId,
                                          String spec) {
    if (spec == null || spec.isBlank()) return "";
    String[] tokens = spec.split("\\|");
    List<String> resolved = new ArrayList<>();
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      if (part.startsWith("$")) {
        String path = LayeredCharacterResolver.resolveLayerPath(layersByCharacter, characterId, part.substring(1).trim());
        if (path == null || path.isBlank()) continue;
        resolved.add(path.trim());
      } else if (part.startsWith("@")) {
        LayeredCharacterResolver.CharacterRef ref =
            LayeredCharacterResolver.parseReference(part.substring(1).trim(), characterId);
        String presetPath = expressionsByCharacter.get(ref.characterId() + "/" + ref.localId());
        if (presetPath == null || presetPath.isBlank()) continue;
        resolved.addAll(splitResolvedLayerSpec(presetPath));
      } else {
        resolved.add(part);
      }
    }
    return String.join(" | ", resolved);
  }

  private static List<String> splitResolvedLayerSpec(String spec) {
    List<String> resolved = new ArrayList<>();
    if (spec == null || spec.isBlank()) return resolved;
    for (String token : spec.split("\\|")) {
      if (token == null) continue;
      String trimmed = token.trim();
      if (!trimmed.isEmpty()) {
        resolved.add(trimmed);
      }
    }
    return resolved;
  }

  private ResolvedInclude resolveIncludeSource(String sourceName, String includePath) throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new IOException("Include path is empty");
    }

    File anchorFile = activeScriptFile;
    if (sourceName != null && !sourceName.isBlank() && !"<script>".equals(sourceName)) {
      File candidate = new File(sourceName);
      if (candidate.isFile()) anchorFile = candidate;
    }
    if (anchorFile == null) {
      throw new IOException("Include resolver unavailable");
    }

    File root = projectRoot;
    if (root == null) {
      root = resolveWorkspaceRoot(anchorFile);
    }
    if (root == null) {
      throw new IOException("Project root unavailable");
    }

    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = ScriptEditorWorkspaceModel.resolveScriptsRoot(root);
    if (scriptsRoot == null) {
      scriptsRoot = rootPath.resolve("scripts");
    }
    scriptsRoot = scriptsRoot.toAbsolutePath().normalize();

    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(scriptsRoot.resolve(normalized.substring(1)));
    } else {
      Path sourcePath = anchorFile.toPath().toAbsolutePath().normalize();
      Path sourceParent = sourcePath.getParent();
      if (sourceParent != null) {
        candidates.add(sourceParent.resolve(normalized));
      }
      candidates.add(scriptsRoot.resolve(normalized));
    }
    candidates.add(rootPath.resolve(normalized));

    for (Path candidate : candidates) {
      Path resolved = candidate.toAbsolutePath().normalize();
      if (!resolved.startsWith(rootPath)) continue;
      if (Files.isRegularFile(resolved)) {
        return new ResolvedInclude(resolved.toString(), Files.readString(resolved, StandardCharsets.UTF_8));
      }
    }
    throw new IOException("Included script not found: " + includePath);
  }

  private static File resolveWorkspaceRoot(File scriptFile) {
    if (scriptFile == null) return null;
    Path current = scriptFile.toPath().toAbsolutePath().normalize().getParent();
    while (current != null) {
      Path name = current.getFileName();
      if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
        return current.getParent() == null ? current.toFile() : current.getParent().toFile();
      }
      current = current.getParent();
    }
    return scriptFile.getParentFile();
  }

  private static String normalizeSourceName(String sourceName) {
    if (sourceName == null || sourceName.isBlank()) return "<script>";
    return sourceName.replace('\\', '/');
  }

  private static String stripQuotes(String value) {
    if (value == null) return "";
    String trimmed = value.trim();
    if (trimmed.length() >= 2) {
      char first = trimmed.charAt(0);
      char last = trimmed.charAt(trimmed.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return trimmed.substring(1, trimmed.length() - 1).trim();
      }
    }
    return trimmed;
  }

  // --- Data classes ---

  public static class CharacterEntry {
    public final String characterId;
    public final String position;
    public final String expression;
    public final int atLine;

    public CharacterEntry(String characterId, String position, String expression, int atLine) {
      this.characterId = characterId;
      this.position = position;
      this.expression = expression;
      this.atLine = atLine;
    }
  }

  public static class SceneSnapshot {
    public final String currentLabel;
    public final String backgroundId;
    public final int backgroundLine;
    public final List<CharacterEntry> characters;
    public final int atLine;
    public final Map<String, String> backgroundPaths;
    public final Map<String, String> characterImagePaths;
    public final String referencedTimelineName;
    public final int referencedTimelineLine;
    public final String inlineTimelineBody;
    public final int inlineTimelineStartLine;
    public final String inlineTimelineName;

    public SceneSnapshot(String currentLabel,
                         String backgroundId,
                         int backgroundLine,
                         List<CharacterEntry> characters,
                         int atLine,
                         Map<String, String> backgroundPaths,
                         Map<String, String> characterImagePaths,
                         String referencedTimelineName,
                         int referencedTimelineLine,
                         String inlineTimelineBody,
                         int inlineTimelineStartLine,
                         String inlineTimelineName) {
      this.currentLabel = currentLabel;
      this.backgroundId = backgroundId;
      this.backgroundLine = backgroundLine;
      this.characters = characters;
      this.atLine = atLine;
      this.backgroundPaths = backgroundPaths;
      this.characterImagePaths = characterImagePaths;
      this.referencedTimelineName = referencedTimelineName;
      this.referencedTimelineLine = referencedTimelineLine;
      this.inlineTimelineBody = inlineTimelineBody;
      this.inlineTimelineStartLine = inlineTimelineStartLine;
      this.inlineTimelineName = inlineTimelineName;
    }

    public String resolveBackgroundPath() {
      return backgroundId != null ? backgroundPaths.getOrDefault(backgroundId, backgroundId) : null;
    }

    public boolean hasBackgroundPathMapping() {
      return backgroundId != null && backgroundPaths.containsKey(backgroundId);
    }

    public boolean hasCharacterPathMapping(String characterId, String expression) {
      if (characterId == null || characterId.isBlank()) return false;
      if (expression != null && characterImagePaths.containsKey(characterId + "/" + expression)) {
        return true;
      }
      if (characterImagePaths.containsKey(characterId + "/neutral")) {
        return true;
      }
      for (String key : characterImagePaths.keySet()) {
        if (key != null && key.startsWith(characterId + "/")) return true;
      }
      return false;
    }

    public String resolveCharacterPath(String characterId, String expression) {
      if (expression != null) {
        String key = characterId + "/" + expression;
        if (characterImagePaths.containsKey(key)) return characterImagePaths.get(key);
      }
      String neutralKey = characterId + "/neutral";
      if (characterImagePaths.containsKey(neutralKey)) return characterImagePaths.get(neutralKey);
      for (Map.Entry<String, String> e : characterImagePaths.entrySet()) {
        if (e.getKey().startsWith(characterId + "/")) return e.getValue();
      }
      return characterId;
    }

    public boolean hasInlineTimeline() {
      return inlineTimelineBody != null && !inlineTimelineBody.isBlank();
    }

    public String preferredTimelineName() {
      if (hasInlineTimeline() && inlineTimelineName != null && !inlineTimelineName.isBlank()) {
        return inlineTimelineName;
      }
      return referencedTimelineName;
    }

    public File resolveTimelineFile(File projectRoot) {
      if (projectRoot == null || referencedTimelineName == null || referencedTimelineName.isBlank()) {
        return null;
      }
      File file = new File(new File(projectRoot, "scripts/timelines"), referencedTimelineName + ".jes");
      return file.isFile() ? file : null;
    }
  }

  public record LaunchRequest(SceneSnapshot snapshot, String timelineName) {}

  public record RegisteredAnimation(
      String name,
      String relativePath,
      String clusterName,
      File file,
      String previewText,
      String statsText,
      int trackCount,
      int keyframeCount,
      int audioCueCount,
      int eventCueCount,
      boolean importable,
      String warningMessage) {}

  public record OpenTarget(File file, int oneBasedLine) {}

  @FunctionalInterface
  interface IncludeSourceResolver {
    ResolvedInclude resolve(String sourceName, String includePath) throws IOException;
  }

  record ResolvedInclude(String sourceName, String sourceText) {}
  record InlineTimelineContext(int startLine, int endLine, String body) {}

}
