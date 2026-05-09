package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Clean, background-free metallic JVN wordmark shared by editor chrome, launcher, and splashes. */
public final class MetallicJvnLogo extends StackPane {
  public MetallicJvnLogo(double width, double height) {
    double safeWidth = Math.max(72.0, width);
    double safeHeight = Math.max(36.0, height);
    double fontSize = Math.max(30.0, safeHeight * 0.76);

    Pane brushLayer = new Pane();
    brushLayer.setMouseTransparent(true);
    brushLayer.setMinSize(safeWidth, safeHeight);
    brushLayer.setPrefSize(safeWidth, safeHeight);
    brushLayer.setMaxSize(safeWidth, safeHeight);
    brushLayer.getChildren().addAll(
        brushStroke(
            safeWidth * 0.08, safeHeight * 0.76,
            safeWidth * 0.24, safeHeight * 0.58,
            safeWidth * 0.47, safeHeight * 0.66,
            safeWidth * 0.88, safeHeight * 0.44,
            Math.max(9.0, safeHeight * 0.22),
            0.58),
        brushStroke(
            safeWidth * 0.17, safeHeight * 0.84,
            safeWidth * 0.36, safeHeight * 0.70,
            safeWidth * 0.56, safeHeight * 0.74,
            safeWidth * 0.80, safeHeight * 0.58,
            Math.max(2.2, safeHeight * 0.045),
            0.22),
        brushStroke(
            safeWidth * 0.12, safeHeight * 0.64,
            safeWidth * 0.33, safeHeight * 0.50,
            safeWidth * 0.57, safeHeight * 0.55,
            safeWidth * 0.92, safeHeight * 0.32,
            Math.max(3.0, safeHeight * 0.07),
            0.16));

    Text wordmark = new Text("JVN");
    wordmark.setFont(Font.font("System", FontWeight.BLACK, fontSize));
    wordmark.setSmooth(true);
    wordmark.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0.0, Color.web("#ffffff")),
        new Stop(0.14, Color.web("#cfd8df")),
        new Stop(0.27, Color.web("#7c8791")),
        new Stop(0.42, Color.web("#f9fbfc")),
        new Stop(0.58, Color.web("#9da8b2")),
        new Stop(0.76, Color.web("#eef3f7")),
        new Stop(1.0, Color.web("#59636d"))));
    wordmark.setStroke(null);
    wordmark.setStrokeWidth(0);
    DropShadow shadow = new DropShadow();
    shadow.setRadius(Math.max(4.0, fontSize * 0.14));
    shadow.setOffsetY(Math.max(2.0, fontSize * 0.05));
    shadow.setColor(Color.web("#000000", 0.48));
    wordmark.setEffect(shadow);

    getChildren().addAll(brushLayer, wordmark);
    setAlignment(Pos.CENTER);
    setMinSize(safeWidth, safeHeight);
    setPrefSize(safeWidth, safeHeight);
    setMaxSize(safeWidth, safeHeight);
    setStyle("-fx-background-color: transparent;");
  }

  private static Path brushStroke(
      double startX,
      double startY,
      double controlX1,
      double controlY1,
      double controlX2,
      double controlY2,
      double endX,
      double endY,
      double width,
      double opacity
  ) {
    Path path = new Path(
        new MoveTo(startX, startY),
        new CubicCurveTo(controlX1, controlY1, controlX2, controlY2, endX, endY));
    path.setFill(null);
    path.setStroke(new LinearGradient(
        0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
        new Stop(0.0, Color.web("#ff7a1a", 0.06)),
        new Stop(0.18, Color.web("#ff8f24", opacity * 0.78)),
        new Stop(0.56, Color.web("#f26a21", opacity)),
        new Stop(0.84, Color.web("#ffb347", opacity * 0.55)),
        new Stop(1.0, Color.web("#ff7a1a", 0.04))));
    path.setStrokeWidth(width);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setMouseTransparent(true);
    return path;
  }
}
