package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Purpose-built caution badge used for warning and attention states across the editor. */
public final class AlertIcon extends StackPane {
  private static final double ARTBOARD_SIZE = 24.0;
  private final double iconSize;

  private AlertIcon(String requestedColor, double requestedSize) {
    iconSize = Math.max(12, Math.min(32, requestedSize));
    double scale = iconSize / ARTBOARD_SIZE;
    Color accent = parseColor(requestedColor);

    Rectangle artboard = new Rectangle(ARTBOARD_SIZE, ARTBOARD_SIZE, Color.TRANSPARENT);

    SVGPath plate = path(
        "M12 1.8 Q13.35 1.8 14.12 3.12 L22.45 18.42 "
            + "Q23.2 19.82 22.42 21.08 Q21.72 22.2 20.12 22.2 H3.88 "
            + "Q2.28 22.2 1.58 21.08 Q.8 19.82 1.55 18.42 L9.88 3.12 "
            + "Q10.65 1.8 12 1.8 Z");
    plate.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, accent.deriveColor(0, 0.72, 1.28, 1)),
        new Stop(0.34, accent.deriveColor(0, 1.06, 1.12, 1)),
        new Stop(0.68, accent),
        new Stop(1, accent.deriveColor(0, 1.12, 0.58, 1))));
    plate.setStroke(Color.web("#fff4d2"));
    plate.setStrokeWidth(1.15);
    plate.setEffect(new InnerShadow(1.6, 0, -0.7, Color.rgb(255, 255, 255, 0.48)));

    SVGPath inset = path(
        "M12 4.55 L20.38 19.58 Q20.58 19.98 20.08 19.98 H3.92 "
            + "Q3.42 19.98 3.62 19.58 Z");
    inset.setFill(Color.rgb(24, 22, 19, 0.94));
    inset.setStroke(accent.deriveColor(0, 0.82, 1.20, 0.88));
    inset.setStrokeWidth(0.8);

    SVGPath upperGlow = path("M7.1 12.25 L10.92 5.4 Q11.34 4.62 12.04 4.62");
    upperGlow.setFill(Color.TRANSPARENT);
    upperGlow.setStroke(Color.rgb(255, 255, 255, 0.54));
    upperGlow.setStrokeWidth(0.82);

    Rectangle stemShadow = new Rectangle(10.2, 8.05, 3.6, 7.8);
    stemShadow.setArcWidth(2.2);
    stemShadow.setArcHeight(2.2);
    stemShadow.setFill(Color.rgb(0, 0, 0, 0.62));

    Rectangle stem = new Rectangle(10.55, 7.7, 2.9, 7.65);
    stem.setArcWidth(1.8);
    stem.setArcHeight(1.8);
    stem.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#d7dce0")),
        new Stop(0.45, Color.WHITE),
        new Stop(1, Color.web("#aeb5ba"))));

    Circle dotShadow = new Circle(12, 18.25, 2.08, Color.rgb(0, 0, 0, 0.68));
    Circle dot = new Circle(12, 17.9, 1.67, new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE),
        new Stop(1, Color.web("#b9c0c4"))));

    Group artwork = new Group(
        artboard, plate, inset, upperGlow, stemShadow, stem, dotShadow, dot);
    artwork.setScaleX(scale);
    artwork.setScaleY(scale);
    artwork.setEffect(new DropShadow(
        Math.max(1.3, iconSize * 0.12), 0, Math.max(0.6, iconSize * 0.055),
        Color.rgb(0, 0, 0, 0.82)));

    setMinSize(iconSize, iconSize);
    setPrefSize(iconSize, iconSize);
    setMaxSize(iconSize, iconSize);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().addAll("jvn-fx-icon", "jvn-alert-icon");
    getChildren().setAll(artwork);
  }

  public static AlertIcon of(String color, double size) {
    return new AlertIcon(color, size);
  }

  public double iconSize() {
    return iconSize;
  }

  private static SVGPath path(String content) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }

  private static Color parseColor(String color) {
    try {
      return Color.web(color);
    } catch (RuntimeException invalidColor) {
      return Color.web("#efbd55");
    }
  }
}
