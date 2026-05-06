package com.jvn.editor.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Startup splash window used while the editor performs preflight checks.
 * Designed to be shown before the main editor stage is created.
 */
public final class StartupSplashOverlay {
  private static final String BG = "#101010";
  private static final String BG_TOP = "#151515";
  private static final String BG_BOTTOM = "#101010";
  private static final String SURFACE = "#1c1c1c";
  private static final String SURFACE_TOP = "#262626";
  private static final String SURFACE_BOTTOM = "#1c1c1c";
  private static final String BORDER = "#3a3a3a";

  private final Stage stage = new Stage(StageStyle.UNDECORATED);
  private final Label statusLabel = new Label("Starting JVN Editor...");
  private final Label subtitleLabel = new Label("Loading editor environment");
  private final Label detailLabel = new Label();
  private final TextArea logArea = new TextArea();
  private final ProgressBar progressBar = new ProgressBar(0);
  private final Button retryButton = createActionButton("Retry");
  private final Button quitButton = createActionButton("Quit");
  private final HBox actionBar = new HBox(8);
  private String progressAccent = "#6ea8ff";

  public StartupSplashOverlay() {
    BorderPane root = new BorderPane();
    root.setPadding(new Insets(16));
    root.setStyle("-fx-background-color: linear-gradient(to bottom, " + BG_TOP + ", " + BG_BOTTOM + ");");

    Node logoView = createVectorLogo();

    Label title = new Label("Java Vector Nexus");
    title.setTextFill(Color.web("#e6ebf5"));
    title.setFont(Font.font("System", 20));
    title.setStyle("-fx-font-weight: 700;");

    subtitleLabel.setTextFill(Color.web("#9caac0"));
    subtitleLabel.setFont(Font.font("System", 12));

    VBox titleBox = new VBox(2, title, subtitleLabel);
    titleBox.setAlignment(Pos.CENTER_LEFT);

    HBox header = new HBox(12, logoView, titleBox);
    header.setAlignment(Pos.CENTER_LEFT);
    root.setTop(header);

    logArea.setEditable(false);
    logArea.setFocusTraversable(false);
    logArea.setWrapText(true);
    logArea.setPrefRowCount(8);
    logArea.setStyle(
        "-fx-control-inner-background: " + SURFACE + ";"
            + " -fx-background-color: linear-gradient(to bottom, " + SURFACE_TOP + ", " + SURFACE_BOTTOM + ");"
            + " -fx-background-radius: 6;"
            + " -fx-border-color: " + BORDER + ";"
            + " -fx-border-radius: 6;"
            + " -fx-text-fill: #cfcfcf;"
            + " -fx-highlight-fill: #294a73;"
            + " -fx-font-family: 'Menlo';"
            + " -fx-font-size: 11px;");
    logArea.skinProperty().addListener((obs, oldSkin, newSkin) -> hideLogScrollBars());
    VBox.setVgrow(logArea, Priority.ALWAYS);

    statusLabel.setTextFill(Color.web("#b7c3d9"));
    statusLabel.setStyle("-fx-font-size: 11px;");

    detailLabel.setTextFill(Color.web("#75829a"));
    detailLabel.setStyle("-fx-font-size: 11px;");
    detailLabel.setWrapText(true);
    detailLabel.setVisible(false);
    detailLabel.setManaged(false);

    actionBar.setAlignment(Pos.CENTER_LEFT);
    actionBar.setVisible(false);
    actionBar.setManaged(false);
    actionBar.getChildren().setAll(retryButton, quitButton);

    progressBar.setMaxWidth(Double.MAX_VALUE);
    progressBar.setPrefHeight(10);
    progressBar.setStyle(
        "-fx-accent: " + progressAccent + ";"
            + " -fx-control-inner-background: " + BG + ";"
            + " -fx-background-color: " + BG + ";"
            + " -fx-box-border: " + BG + ";"
            + " -fx-border-color: " + BG + ";");
    progressBar.skinProperty().addListener((obs, oldSkin, newSkin) -> styleProgressBarForBlackChrome());
    VBox footer = new VBox(6, statusLabel, detailLabel, actionBar, progressBar);

    VBox center = new VBox(10, logArea, footer);
    center.setPadding(new Insets(12, 0, 0, 0));
    root.setCenter(center);

    Scene scene = new Scene(root, 560, 320, Color.web(BG));
    stage.setScene(scene);
    Platform.runLater(this::hideLogScrollBars);
    Platform.runLater(this::styleProgressBarForBlackChrome);
    stage.setResizable(false);
    stage.setAlwaysOnTop(true);
  }

  public void show() {
    runOnFx(() -> {
      if (!stage.isShowing()) {
        stage.show();
      }
      hideLogScrollBars();
      styleProgressBarForBlackChrome();
      stage.centerOnScreen();
    });
  }

  public void close() {
    runOnFx(stage::hide);
  }

  public void prepareForChecks(boolean clearLogs) {
    runOnFx(() -> {
      if (clearLogs) {
        logArea.clear();
      }
      subtitleLabel.setText("Loading editor environment");
      statusLabel.setTextFill(Color.web("#b7c3d9"));
      detailLabel.setText("");
      detailLabel.setTextFill(Color.web("#75829a"));
      detailLabel.setVisible(false);
      detailLabel.setManaged(false);
      progressAccent = "#6ea8ff";
      retryButton.setDisable(true);
      quitButton.setDisable(true);
      hideActions();
      styleProgressBarForBlackChrome();
    });
  }

  public void setProgress(double progress) {
    runOnFx(() -> progressBar.setProgress(progress < 0 ? ProgressBar.INDETERMINATE_PROGRESS : progress));
  }

  public void setStatus(String status) {
    runOnFx(() -> statusLabel.setText(status == null ? "" : status));
  }

  public void setSubtitle(String subtitle) {
    runOnFx(() -> subtitleLabel.setText(subtitle == null ? "" : subtitle));
  }

  public void showFailure(String summary, String detail, Runnable onRetry, Runnable onQuit) {
    runOnFx(() -> {
      subtitleLabel.setText("Startup blocked");
      statusLabel.setText(summary == null ? "Startup checks failed" : summary);
      statusLabel.setTextFill(Color.web("#f38ba8"));
      if (detail != null && !detail.isBlank()) {
        detailLabel.setText(detail.trim());
        detailLabel.setVisible(true);
        detailLabel.setManaged(true);
      } else {
        detailLabel.setText("");
        detailLabel.setVisible(false);
        detailLabel.setManaged(false);
      }
      detailLabel.setTextFill(Color.web("#d6a5b5"));
      progressAccent = "#f38ba8";
      styleProgressBarForBlackChrome();
      showActions(retryButton, quitButton);
      retryButton.setDisable(false);
      quitButton.setDisable(false);
      retryButton.setOnAction(evt -> {
        retryButton.setDisable(true);
        quitButton.setDisable(true);
        if (onRetry != null) onRetry.run();
      });
      quitButton.setOnAction(evt -> {
        if (onQuit != null) onQuit.run();
      });
    });
  }

  public void showLaunchingEditor() {
    runOnFx(() -> {
      subtitleLabel.setText("Startup preflight complete");
      statusLabel.setText("Launching editor");
      statusLabel.setTextFill(Color.web("#b7c3d9"));
      detailLabel.setText("Startup checks passed. Opening the editor now.");
      detailLabel.setTextFill(Color.web("#9caac0"));
      detailLabel.setVisible(true);
      detailLabel.setManaged(true);
      progressAccent = "#d9b36a";
      styleProgressBarForBlackChrome();
      hideActions();
      retryButton.setDisable(true);
      quitButton.setDisable(true);
    });
  }

  public void appendLog(String line) {
    if (line == null || line.isBlank()) return;
    runOnFx(() -> {
      String text = logArea.getText();
      if (text == null || text.isBlank()) {
        logArea.setText(line.trim());
      } else {
        logArea.appendText(System.lineSeparator() + line.trim());
      }
      logArea.setScrollTop(Double.MAX_VALUE);
    });
  }

  private void hideLogScrollBars() {
    for (Node node : logArea.lookupAll(".scroll-bar")) {
      if (node == null) continue;
      node.setVisible(false);
      node.setManaged(false);
      node.setMouseTransparent(true);
      node.setStyle("-fx-pref-width: 0; -fx-pref-height: 0; -fx-opacity: 0;");
    }
  }

  private void styleProgressBarForBlackChrome() {
    Node track = progressBar.lookup(".track");
    if (track instanceof Region region) {
      region.setStyle("-fx-background-color: linear-gradient(to bottom, #181818, #111111);"
          + " -fx-border-color: #2a2a2a;"
          + " -fx-background-radius: 5;"
          + " -fx-border-radius: 5;");
    }
    Node bar = progressBar.lookup(".bar");
    if (bar instanceof Region region) {
      region.setStyle("-fx-background-color: " + progressAccent + ";");
    }
  }

  private void hideActions() {
    actionBar.setVisible(false);
    actionBar.setManaged(false);
  }

  private void showActions(Button... buttons) {
    actionBar.getChildren().setAll(buttons);
    actionBar.setVisible(true);
    actionBar.setManaged(true);
  }

  private static Button createActionButton(String label) {
    return createActionButton(label, null, false);
  }

  private static Node createVectorLogo() {
    Text wordmark = new Text("JVN");
    wordmark.setFont(Font.font("System", FontWeight.BOLD, 42));
    wordmark.setFill(new LinearGradient(
        0,
        0,
        0,
        1,
        true,
        CycleMethod.NO_CYCLE,
        new Stop(0.0, Color.web("#ffffff")),
        new Stop(0.42, Color.web("#dedede")),
        new Stop(1.0, Color.web("#9a9a9a"))));
    wordmark.setSmooth(true);

    StackPane mark = new StackPane(wordmark);
    mark.setMinSize(96, 72);
    mark.setPrefSize(96, 72);
    mark.setMaxSize(96, 72);
    mark.setAlignment(Pos.CENTER);
    return mark;
  }

  private static Button createActionButton(String label, Region icon, boolean accent) {
    Button button = new Button(label);
    button.setFocusTraversable(false);
    if (icon != null) {
      button.setGraphic(icon);
      button.setContentDisplay(ContentDisplay.LEFT);
      button.setGraphicTextGap(8);
      button.setAlignment(Pos.CENTER_LEFT);
    }
    button.setStyle(
        (accent
            ? "-fx-background-color: linear-gradient(to bottom, #245939, #1a412a);"
            : "-fx-background-color: linear-gradient(to bottom, " + SURFACE_TOP + ", " + SURFACE_BOTTOM + ");")
            + (accent ? " -fx-text-fill: #e8fff2;" : " -fx-text-fill: #d8e0ec;")
            + (accent ? " -fx-border-color: #68b385;" : " -fx-border-color: " + BORDER + ";")
            + " -fx-border-radius: 6;"
            + " -fx-background-radius: 6;"
            + " -fx-padding: 6 14 6 14;");
    return button;
  }

  private static void runOnFx(Runnable task) {
    if (task == null) return;
    if (Platform.isFxApplicationThread()) {
      task.run();
    } else {
      Platform.runLater(task);
    }
  }
}
