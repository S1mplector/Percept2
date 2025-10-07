package com.jvn.fx;

import com.jvn.core.engine.Engine;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class FxLauncher extends Application {
  private static Engine engine;
  private AnimationTimer timer;
  private Canvas canvas;
  private GraphicsContext gc;

  public static void launch(Engine eng) {
    engine = eng;
    Application.launch();
  }

  @Override
  public void start(Stage primaryStage) {
    String title = engine != null && engine.getConfig() != null ? engine.getConfig().title() : "JVN";
    int width = engine != null && engine.getConfig() != null ? engine.getConfig().width() : 960;
    int height = engine != null && engine.getConfig() != null ? engine.getConfig().height() : 540;
    primaryStage.setTitle(title);

    StackPane root = new StackPane();
    this.canvas = new Canvas(width, height);
    root.getChildren().add(this.canvas);
    Scene scene = new Scene(root, width, height);
    primaryStage.setScene(scene);
    primaryStage.show();

    // Initialize graphics context and resize canvas with scene
    this.gc = this.canvas.getGraphicsContext2D();
    scene.widthProperty().addListener((obs, ov, nv) -> this.canvas.setWidth(nv.doubleValue()));
    scene.heightProperty().addListener((obs, ov, nv) -> this.canvas.setHeight(nv.doubleValue()));

    timer = new AnimationTimer() {
      private long lastNs = -1L;

      @Override
      public void handle(long now) {
        if (lastNs < 0) { lastNs = now; return; }
        long deltaMs = (now - lastNs) / 1_000_000L;
        lastNs = now;
        if (engine != null) engine.update(deltaMs);

        // Basic render: clear and draw title text
        if (gc != null && canvas != null) {
          double w = canvas.getWidth();
          double h = canvas.getHeight();
          gc.setFill(Color.BLACK);
          gc.fillRect(0, 0, w, h);
          gc.setFill(Color.WHITE);
          gc.fillText("JVN - Java Visual Novel", 20, 30);
        }
      }
    };
    timer.start();
  }

  @Override
  public void stop() {
    if (timer != null) timer.stop();
    if (engine != null && engine.isStarted()) {
      engine.stop();
    }
  }
}
