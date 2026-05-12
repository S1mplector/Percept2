package com.jvn.editor.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Animated "under maintenance" overlay that can be layered over any sidebar tool.
 *
 * <p>Usage — wrap the view node before placing it in its tab or window:
 * <pre>
 *   tabPhoneAssets = new Tab("Phone Assets", MaintenanceOverlay.wrap(phoneAssetsView, "Late May 2026"));
 * </pre>
 * To remove the overlay, simply stop wrapping the node.
 */
public final class MaintenanceOverlay extends StackPane {

  private static final int   STRIPE_WIDTH = 28;
  private static final Color STRIPE_COLOR = Color.rgb(255, 130, 0, 0.20);
  private static final Color BG_TINT      = Color.rgb(20,  15,  0, 0.60);

  private final Canvas stripeCanvas = new Canvas();
  private double stripeOffset = 0;

  private MaintenanceOverlay(Node content, String estimatedAvailability) {
    // Underlying tool is visible but non-interactive.
    content.setMouseTransparent(true);
    getChildren().add(content);

    stripeCanvas.widthProperty().bind(widthProperty());
    stripeCanvas.heightProperty().bind(heightProperty());
    stripeCanvas.widthProperty().addListener(o -> drawStripes());
    stripeCanvas.heightProperty().addListener(o -> drawStripes());
    stripeCanvas.setMouseTransparent(true);

    // ── Icon + title row ────────────────────────────────────────────────
    Label icon = new Label("⚙");
    icon.setStyle("-fx-font-size: 20px; -fx-text-fill: #ff9933;");

    Label title = new Label("Under Maintenance");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ff9933;");

    HBox iconTitle = new HBox(8, icon, title);
    iconTitle.setAlignment(Pos.CENTER_LEFT);

    // ── Body ────────────────────────────────────────────────────────────
    Label subtitle = new Label("This tool seems to be temporarily unavailable.");
    subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #cccccc;");
    subtitle.setWrapText(true);

    VBox messageBox = new VBox(6, iconTitle, subtitle);

    // ── Optional estimated availability ─────────────────────────────────
    if (estimatedAvailability != null && !estimatedAvailability.isBlank()) {
      Label etaLabel = new Label("Expected back: " + estimatedAvailability.trim());
      etaLabel.setStyle(
          "-fx-font-size: 11px; -fx-text-fill: #ffbb66;"
          + "-fx-font-style: italic;");
      messageBox.getChildren().add(etaLabel);
    }

    messageBox.setAlignment(Pos.TOP_LEFT);
    messageBox.setMaxWidth(Double.MAX_VALUE);
    messageBox.setMouseTransparent(true);
    messageBox.setStyle(
        "-fx-background-color: rgba(18,12,0,0.82);"
        + "-fx-border-color: rgba(255,130,0,0.45);"
        + "-fx-border-width: 0 0 2 0;"
        + "-fx-padding: 12 16 12 16;");

    getChildren().addAll(stripeCanvas, messageBox);
    StackPane.setAlignment(messageBox, Pos.TOP_LEFT);

    AnimationTimer timer = new AnimationTimer() {
      @Override public void handle(long now) {
        stripeOffset = (stripeOffset + 0.35) % (STRIPE_WIDTH * 2.0);
        drawStripes();
      }
    };

    // Tie timer lifecycle to scene presence so it doesn't run off-screen.
    sceneProperty().addListener((obs, old, scene) -> {
      if (scene != null) timer.start(); else timer.stop();
    });
  }

  private void drawStripes() {
    double w = stripeCanvas.getWidth();
    double h = stripeCanvas.getHeight();
    if (w <= 0 || h <= 0) return;

    GraphicsContext gc = stripeCanvas.getGraphicsContext2D();
    gc.clearRect(0, 0, w, h);

    gc.setFill(BG_TINT);
    gc.fillRect(0, 0, w, h);

    // 45-degree diagonal stripes marching right.
    double period = STRIPE_WIDTH * 2.0;
    gc.setFill(STRIPE_COLOR);
    for (double x = -h - period + stripeOffset; x < w + period; x += period) {
      gc.fillPolygon(
          new double[]{x,              x + STRIPE_WIDTH,     x + STRIPE_WIDTH + h, x + h},
          new double[]{0,              0,                    h,                    h},
          4);
    }
  }

  /**
   * Wraps {@code content} in an animated maintenance overlay showing an estimated
   * availability date. To remove the overlay, stop wrapping the node.
   */
  public static MaintenanceOverlay wrap(Node content, String estimatedAvailability) {
    return new MaintenanceOverlay(content, estimatedAvailability);
  }

  /**
   * Wraps {@code content} in an animated maintenance overlay with no ETA shown.
   */
  public static MaintenanceOverlay wrap(Node content) {
    return new MaintenanceOverlay(content, null);
  }
}
