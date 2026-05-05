import sys

# 1. Update PuppeteerLauncherPanel.java
panel_file = "editor/src/main/java/com/jvn/editor/ui/PuppeteerLauncherPanel.java"
with open(panel_file, "r") as f:
    panel_content = f.read()

# Add activeEditingTimeline field
if "private String activeEditingTimeline;" not in panel_content:
    panel_content = panel_content.replace('private long cachedRegisteredAnimationsStamp = Long.MIN_VALUE;', 'private long cachedRegisteredAnimationsStamp = Long.MIN_VALUE;\n  private String activeEditingTimeline;')

# Add setActiveEditingTimeline method
set_active_method = """
  public void setActiveEditingTimeline(String timelineId) {
    this.activeEditingTimeline = timelineId;
    refreshRegisteredAnimations(null);
  }
"""
if "public void setActiveEditingTimeline" not in panel_content:
    panel_content = panel_content.replace('public void setProjectRoot(File projectRoot)', set_active_method + '  public void setProjectRoot(File projectRoot)')

# Modify createRegisteredAnimationCard
card_mod = """  private VBox createRegisteredAnimationCard(RegisteredAnimation animation, SceneSnapshot snapshot) {
    String preferredTimelineName = snapshot != null ? snapshot.preferredTimelineName() : null;
    String timelineId = resolveRelativeTimelineStem(animation);
    boolean suggested = preferredTimelineName != null && preferredTimelineName.equals(timelineId);
    boolean importable = animation.importable();
    boolean isEditing = timelineId != null && timelineId.equals(activeEditingTimeline);
    
    String scenePreviewText = shouldShowScenePreview(animation, suggested)
        ? describeScenePreview(snapshot)
        : "";
    String resolvedPreviewText = resolveRegisteredAnimationPreviewText(animation, snapshot);

    Label title = new Label(animation.name());
    title.setStyle("-fx-text-fill: #f2f4f8; -fx-font-size: 11px; -fx-font-weight: bold;");

    HBox titleRow = new HBox(6);
    titleRow.getChildren().add(title);
    
    if (isEditing) {
      javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
      spinner.setPrefSize(12, 12);
      spinner.setStyle("-fx-progress-color: #f0a0d0;");
      Label badge = new Label("Currently being edited", spinner);
      badge.setGraphicTextGap(4);
      badge.setStyle("-fx-background-color: #2b2027; -fx-border-color: #f0a0d0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 6 2 4; -fx-text-fill: #f0a0d0; -fx-font-size: 10px; -fx-font-weight: bold;");
      titleRow.getChildren().add(badge);
    } else if (suggested) {"""

if "boolean isEditing = timelineId != null && timelineId.equals(activeEditingTimeline);" not in panel_content:
    old_card_start = """  private VBox createRegisteredAnimationCard(RegisteredAnimation animation, SceneSnapshot snapshot) {
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

    if (suggested) {"""
    panel_content = panel_content.replace(old_card_start, card_mod)

# Grey out buttons and add double click
body_mod = """    VBox body = new VBox(3, titleRow, meta);
    if (!scenePreviewText.isBlank()) {
      Label scenePreview = new Label(scenePreviewText);
      scenePreview.setStyle("-fx-text-fill: #bdbdbd; -fx-font-size: 9px;");
      scenePreview.setWrapText(true);
      body.getChildren().add(scenePreview);
    }
    body.getChildren().add(preview);
    if (!importable && animation.warningMessage() != null && !animation.warningMessage().isBlank()) {
      Label warning = new Label(animation.warningMessage());
      warning.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 9px;");
      warning.setWrapText(true);
      body.getChildren().add(warning);
    }
    body.setMaxWidth(Double.MAX_VALUE);
    body.setPadding(new Insets(8, 10, 8, 10));
    Tooltip.install(body, new Tooltip(animation.file().getName()));
    
    if (isEditing) {
        copyButton.setDisable(true);
        openButton.setDisable(true);
        renameButton.setDisable(true);
        deleteButton.setDisable(true);
        body.getStyleClass().add("sidebar-tool-card-disabled");
    } else if (!importable) {
      body.getStyleClass().add("sidebar-tool-card-disabled");
    } else if (suggested) {
      body.getStyleClass().add("sidebar-tool-card-suggested");
    } else {
      body.getStyleClass().add("sidebar-tool-card");
    }

    body.setOnMouseClicked(e -> {
      if (!isEditing && importable && e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.getClickCount() == 2) {
        if (onLaunch != null) {
          onLaunch.accept(new LaunchRequest(snapshot, timelineId));
        }
      }
    });

    return body;"""

if "body.setOnMouseClicked(e -> {" not in panel_content:
    old_body_end = """    VBox body = new VBox(3, titleRow, meta);
    if (!scenePreviewText.isBlank()) {
      Label scenePreview = new Label(scenePreviewText);
      scenePreview.setStyle("-fx-text-fill: #bdbdbd; -fx-font-size: 9px;");
      scenePreview.setWrapText(true);
      body.getChildren().add(scenePreview);
    }
    body.getChildren().add(preview);
    if (!importable && animation.warningMessage() != null && !animation.warningMessage().isBlank()) {
      Label warning = new Label(animation.warningMessage());
      warning.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 9px;");
      warning.setWrapText(true);
      body.getChildren().add(warning);
    }
    body.setMaxWidth(Double.MAX_VALUE);
    body.setPadding(new Insets(8, 10, 8, 10));
    Tooltip.install(body, new Tooltip(animation.file().getName()));
    if (!importable) {
      body.getStyleClass().add("sidebar-tool-card-disabled");
    } else if (suggested) {
      body.getStyleClass().add("sidebar-tool-card-suggested");
    } else {
      body.getStyleClass().add("sidebar-tool-card");
    }

    return body;"""
    panel_content = panel_content.replace(old_body_end, body_mod)

with open(panel_file, "w") as f:
    f.write(panel_content)

# 2. Update EditorApp.java
app_file = "editor/src/main/java/com/jvn/editor/EditorApp.java"
with open(app_file, "r") as f:
    app_content = f.read()

app_mod = """    puppeteer.show();
    if (puppeteerLauncherPanel != null && preferredTimelineName != null && !preferredTimelineName.isBlank()) {
      puppeteerLauncherPanel.setActiveEditingTimeline(preferredTimelineName);
      puppeteer.hiddenProperty().addListener((obs, oldVal, newVal) -> {
        if (newVal && puppeteerLauncherPanel != null) {
          puppeteerLauncherPanel.setActiveEditingTimeline(null);
        }
      });
    }"""

if "puppeteer.hiddenProperty().addListener" not in app_content:
    app_content = app_content.replace('    puppeteer.show();', app_mod)

with open(app_file, "w") as f:
    f.write(app_content)
