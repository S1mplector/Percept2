import sys

# 2. Update PuppeteerLauncherPanel.java
panel_file = "editor/src/main/java/com/jvn/editor/ui/PuppeteerLauncherPanel.java"
with open(panel_file, "r") as f:
    panel_content = f.read()

# Add fields
fields = """  private long cachedRegisteredAnimationsStamp = Long.MIN_VALUE;
  private String activeEditingTimeline;
  private String selectedTimelineId;
  private java.io.File selectedTimelineFile;"""
panel_content = panel_content.replace('  private long cachedRegisteredAnimationsStamp = Long.MIN_VALUE;\n  private String activeEditingTimeline;', fields)

# Update btnOpenTimeline action
old_btn_action = """    btnOpenTimeline.setOnAction(e -> {
      if (onOpenTarget == null) return;
      OpenTarget target = resolveTimelineOpenTarget(buildSnapshot(currentLine));
      if (target != null) onOpenTarget.accept(target);
    });"""
new_btn_action = """    btnOpenTimeline.setOnAction(e -> {
      if (onOpenTarget == null) return;
      if (selectedTimelineFile != null) {
          onOpenTarget.accept(new OpenTarget(selectedTimelineFile, 0));
          return;
      }
      OpenTarget target = resolveTimelineOpenTarget(buildSnapshot(currentLine));
      if (target != null) onOpenTarget.accept(target);
    });"""
panel_content = panel_content.replace(old_btn_action, new_btn_action)

# Update btnOpenTimeline disable state in refresh()
old_btn_disable = """    btnOpenTimeline.setDisable(resolveTimelineOpenTarget(snap) == null);"""
new_btn_disable = """    btnOpenTimeline.setDisable(selectedTimelineId == null && resolveTimelineOpenTarget(snap) == null);"""
panel_content = panel_content.replace(old_btn_disable, new_btn_disable)

# Update empty state
old_empty = """    btnOpenTimeline.setDisable(true);"""
new_empty = """    selectedTimelineId = null;
    selectedTimelineFile = null;
    btnOpenTimeline.setDisable(true);"""
panel_content = panel_content.replace(old_empty, new_empty)


# modify createRegisteredAnimationCard signature
old_sig = "private VBox createRegisteredAnimationCard"
new_sig = "private javafx.scene.layout.Region createRegisteredAnimationCard"
panel_content = panel_content.replace(old_sig, new_sig)

# modify the card body
old_card_return = """    body.setOnMouseClicked(e -> {
      if (!isEditing && importable && e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.getClickCount() == 2) {
        if (onLaunch != null) {
          onLaunch.accept(new LaunchRequest(snapshot, timelineId));
        }
      }
    });

    return body;"""

new_card_return = """    javafx.scene.layout.StackPane cardRoot = new javafx.scene.layout.StackPane(body);
    
    boolean isSelected = timelineId != null && timelineId.equals(selectedTimelineId);
    if (isSelected) {
        selectedTimelineFile = animation.file();
        javafx.scene.canvas.Canvas lassoCanvas = new javafx.scene.canvas.Canvas();
        lassoCanvas.setMouseTransparent(true);
        lassoCanvas.widthProperty().bind(body.widthProperty());
        lassoCanvas.heightProperty().bind(body.heightProperty());
        
        double[] offset = {0.0};
        javafx.animation.Timeline lassoTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(40), evt -> {
                offset[0] -= 2.2;
                javafx.scene.canvas.GraphicsContext gc = lassoCanvas.getGraphicsContext2D();
                double w = lassoCanvas.getWidth();
                double h = lassoCanvas.getHeight();
                gc.clearRect(0, 0, w, h);
                gc.setStroke(javafx.scene.paint.Color.web("#a0d0f0"));
                gc.setLineWidth(1.5);
                gc.setLineDashes(4, 4);
                gc.setLineDashOffset(offset[0]);
                gc.strokeRoundRect(1, 1, w - 2, h - 2, 8, 8);
            })
        );
        lassoTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        
        cardRoot.getChildren().add(lassoCanvas);
        
        lassoCanvas.sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) lassoTimeline.stop();
            else lassoTimeline.play();
        });
    }

    body.setOnMouseClicked(e -> {
      if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
          if (e.getClickCount() == 2 && !isEditing && importable) {
              if (onLaunch != null) {
                  onLaunch.accept(new LaunchRequest(snapshot, timelineId));
              }
          } else if (e.getClickCount() == 1) {
              if (timelineId != null && !timelineId.equals(selectedTimelineId)) {
                  selectedTimelineId = timelineId;
                  selectedTimelineFile = animation.file();
                  refresh();
              }
          }
      }
    });

    return cardRoot;"""
panel_content = panel_content.replace(old_card_return, new_card_return)

with open(panel_file, "w") as f:
    f.write(panel_content)

