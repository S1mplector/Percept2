package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Reusable dimmed loading overlay with a compact progress bar and message.
 */
public final class LoadingProgressOverlay extends StackPane {
  private final Label messageLabel = new Label("Loading...");
  private final ProgressBar progressBar = new ProgressBar(ProgressIndicator.INDETERMINATE_PROGRESS);
  private final VBox card = new VBox(8, messageLabel, progressBar);

  public LoadingProgressOverlay() {
    setManaged(false);
    setVisible(false);
    setAlignment(Pos.CENTER);
    setStyle("-fx-background-color: rgba(18, 24, 33, 0.48);");
    setMouseTransparent(false);
    setPickOnBounds(true);

    messageLabel.setStyle("-fx-text-fill: #dbe2ea; -fx-font-size: 11px; -fx-font-weight: 600;");
    progressBar.setMinWidth(180);
    progressBar.setPrefWidth(180);
    progressBar.setMaxWidth(220);
    progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
    progressBar.setStyle(
        "-fx-accent: #8ab4f8;"
            + " -fx-control-inner-background: rgba(255,255,255,0.12);");

    card.setAlignment(Pos.CENTER);
    card.setStyle(
        "-fx-background-color: rgba(40, 46, 58, 0.88);"
            + " -fx-background-radius: 8;"
            + " -fx-padding: 10 12 10 12;"
            + " -fx-border-color: rgba(255,255,255,0.12);"
            + " -fx-border-radius: 8;");

    getChildren().setAll(card);
  }

  public void showIndeterminate(String message) {
    messageLabel.setText((message == null || message.isBlank()) ? "Loading..." : message);
    progressBar.progressProperty().unbind();
    progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
    setVisible(true);
    setManaged(true);
    toFront();
  }

  public void showDeterminate(String message, double initialProgress) {
    messageLabel.setText((message == null || message.isBlank()) ? "Loading..." : message);
    progressBar.progressProperty().unbind();
    progressBar.setProgress(clampProgress(initialProgress));
    setVisible(true);
    setManaged(true);
    toFront();
  }

  public void setProgress(double progress) {
    if (!isVisible()) return;
    progressBar.progressProperty().unbind();
    progressBar.setProgress(clampProgress(progress));
  }

  public void hideOverlay() {
    progressBar.progressProperty().unbind();
    setVisible(false);
    setManaged(false);
  }

  private static double clampProgress(double progress) {
    if (Double.isNaN(progress)) return ProgressIndicator.INDETERMINATE_PROGRESS;
    if (progress < 0.0) return ProgressIndicator.INDETERMINATE_PROGRESS;
    if (progress > 1.0) return 1.0;
    return progress;
  }
}
