package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;

/** Compact Windows 7 glass-and-chrome add-tab control. */
public final class NewTabIcon extends StackPane {
  private static final double ARTBOARD_SIZE = 24.0;
  private final double iconSize;

  private NewTabIcon(double requestedSize) {
    iconSize = Math.max(16, Math.min(34, requestedSize));
    double scale = iconSize / ARTBOARD_SIZE;

    Circle chrome = new Circle(12, 12, 9.4, new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.2, Color.web("#e8eff3")),
        new Stop(0.52, Color.web("#83919a")), new Stop(0.72, Color.web("#dce4e8")),
        new Stop(1, Color.web("#52616a"))));
    chrome.setStroke(Color.web("#35444d"));
    chrome.setStrokeWidth(0.8);
    Circle glass = new Circle(12, 12, 7.9, new RadialGradient(
        -35, 0.28, 9.3, 8.7, 10.2, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#ecfff0")), new Stop(0.3, Color.web("#77dc83")),
        new Stop(0.68, Color.web("#2b9c48")), new Stop(1, Color.web("#125d2b"))));
    glass.setStroke(Color.web("#dffff0"));
    glass.setStrokeWidth(0.65);
    glass.setEffect(new InnerShadow(1.3, 0, 0.7, Color.rgb(4, 54, 23, 0.72)));
    Line plusShadowH = plusLine(7.2, 12.6, 16.8, 12.6, Color.rgb(4, 55, 24, 0.8), 3.0);
    Line plusShadowV = plusLine(12, 7.8, 12, 17.4, Color.rgb(4, 55, 24, 0.8), 3.0);
    Line plusH = plusLine(7.2, 12, 16.8, 12, Color.WHITE, 1.75);
    Line plusV = plusLine(12, 7.2, 12, 16.8, Color.WHITE, 1.75);

    Group artwork = new Group(chrome, glass, plusShadowH, plusShadowV, plusH, plusV);
    artwork.setScaleX(scale);
    artwork.setScaleY(scale);
    artwork.setEffect(new DropShadow(Math.max(1.4, iconSize * 0.11), 0,
        Math.max(0.6, iconSize * 0.05), Color.rgb(0, 0, 0, 0.82)));

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

  private static Line plusLine(double x1, double y1, double x2, double y2,
      Color color, double width) {
    Line line = new Line(x1, y1, x2, y2);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }
}
