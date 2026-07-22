package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
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

/** Purpose-built stacked-tab artwork for the editor's sidebar add-tab control. */
public final class NewTabIcon extends StackPane {
  private static final double ARTBOARD_SIZE = 24.0;
  private final double iconSize;

  private NewTabIcon(double requestedSize) {
    iconSize = Math.max(16, Math.min(34, requestedSize));
    double scale = iconSize / ARTBOARD_SIZE;

    Rectangle artboard = new Rectangle(ARTBOARD_SIZE, ARTBOARD_SIZE, Color.TRANSPARENT);

    SVGPath rearTab = path(
        "M3 7 V5.4 Q3 3.6 4.8 3.6 H9.2 L11.2 5.6 H18.6 Q20.2 5.6 20.2 7.2 "
            + "V17.4 Q20.2 19 18.6 19 H4.8 Q3 19 3 17.2 Z");
    rearTab.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#869198")),
        new Stop(0.46, Color.web("#454e53")),
        new Stop(1, Color.web("#1c2225"))));
    rearTab.setStroke(Color.web("#bfc8cc"));
    rearTab.setStrokeWidth(1.15);

    SVGPath frontTab = path(
        "M5.6 9 V7.3 Q5.6 5.5 7.4 5.5 H11.8 L13.8 7.5 H19.3 Q21 7.5 21 9.2 "
            + "V18.8 Q21 20.5 19.3 20.5 H7.4 Q5.6 20.5 5.6 18.7 Z");
    frontTab.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#f7fafb")),
        new Stop(0.18, Color.web("#bec8cc")),
        new Stop(0.52, Color.web("#566268")),
        new Stop(1, Color.web("#20272a"))));
    frontTab.setStroke(Color.web("#f1f5f6"));
    frontTab.setStrokeWidth(1.05);

    SVGPath inset = path(
        "M7.8 10.1 H18.8 V17.8 Q18.8 18.4 18.2 18.4 H8.4 Q7.8 18.4 7.8 17.8 Z");
    inset.setFill(Color.web("#111719"));
    inset.setStroke(Color.rgb(196, 213, 219, 0.48));
    inset.setStrokeWidth(0.75);

    SVGPath tabLine = path("M8.8 12.6 H15.7 M8.8 15.4 H13.8");
    tabLine.setFill(Color.TRANSPARENT);
    tabLine.setStroke(Color.web("#c9d4d8"));
    tabLine.setStrokeWidth(1.0);

    SVGPath creationSpark = path(
        "M18.4 3.3 L19.2 5.2 L21.1 6 L19.2 6.8 L18.4 8.7 L17.6 6.8 L15.7 6 "
            + "L17.6 5.2 Z");
    creationSpark.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#fff1d2")),
        new Stop(0.42, Color.web("#ffb053")),
        new Stop(1, Color.web("#d9630d"))));
    creationSpark.setStroke(Color.web("#fff6e5"));
    creationSpark.setStrokeWidth(0.55);
    creationSpark.setEffect(new DropShadow(2.5, Color.rgb(255, 142, 43, 0.62)));

    Circle statusLamp = new Circle(18.4, 17.4, 0.95, Color.web("#ff9833"));
    statusLamp.setStroke(Color.web("#ffe0bd"));
    statusLamp.setStrokeWidth(0.45);

    Group artwork = new Group(artboard, rearTab, frontTab, inset, tabLine, creationSpark, statusLamp);
    artwork.setScaleX(scale);
    artwork.setScaleY(scale);
    artwork.setEffect(new DropShadow(Math.max(1.4, iconSize * 0.10), 0, 1,
        Color.rgb(0, 0, 0, 0.72)));

    setMinSize(iconSize, iconSize);
    setPrefSize(iconSize, iconSize);
    setMaxSize(iconSize, iconSize);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("new-tab-icon");
    getChildren().setAll(artwork);
  }

  public static NewTabIcon of(double size) {
    return new NewTabIcon(size);
  }

  public static NewTabIcon compact() {
    return of(20);
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
}
