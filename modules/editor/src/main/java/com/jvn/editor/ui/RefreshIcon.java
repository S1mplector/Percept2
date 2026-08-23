package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.control.ButtonBase;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Standard scalable Windows 7-style refresh icon shared by every editor surface. */
public final class RefreshIcon extends Pane {
  private static final double ARTBOARD_SIZE = 24.0;
  private static final Color GLOW = Color.web("#63c9f2");
  private final double iconSize;

  private RefreshIcon(double requestedSize) {
    iconSize = Math.max(14, Math.min(32, requestedSize));
    Group artwork = artwork();
    double scale = iconSize / ARTBOARD_SIZE;
    artwork.setScaleX(scale);
    artwork.setScaleY(scale);
    artwork.setTranslateX((iconSize - ARTBOARD_SIZE) * 0.5);
    artwork.setTranslateY((iconSize - ARTBOARD_SIZE) * 0.5);
    artwork.setEffect(new DropShadow(
        Math.max(1.6, iconSize * 0.11), 0, Math.max(0.7, iconSize * 0.05),
        Color.rgb(0, 0, 0, 0.78)));
    artwork.setCache(true);
    artwork.setCacheHint(CacheHint.SPEED);

    setMinSize(iconSize, iconSize);
    setPrefSize(iconSize, iconSize);
    setMaxSize(iconSize, iconSize);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().addAll("jvn-fx-icon", "jvn-standard-refresh-icon");
    getChildren().setAll(artwork);
    parentProperty().addListener((obs, oldParent, newParent) -> {
      if (newParent instanceof ButtonBase button) installButtonTreatment(button);
    });
  }

  public static RefreshIcon of(double size) {
    return new RefreshIcon(size);
  }

  public static RefreshIcon compact() {
    return of(18);
  }

  public double iconSize() {
    return iconSize;
  }

  private void installButtonTreatment(ButtonBase button) {
    button.hoverProperty().addListener((obs, wasHovered, hovered) -> updateButtonEffect(button));
    button.pressedProperty().addListener((obs, wasPressed, pressed) -> updateButtonEffect(button));
    updateButtonEffect(button);
  }

  private void updateButtonEffect(ButtonBase button) {
    if (button.isPressed()) {
      setScaleX(0.94);
      setScaleY(0.94);
      setEffect(new DropShadow(Math.max(2.4, iconSize * 0.14), GLOW.deriveColor(0, 0.8, 0.8, 0.72)));
    } else if (button.isHover()) {
      setScaleX(1.05);
      setScaleY(1.05);
      setEffect(new DropShadow(Math.max(5.0, iconSize * 0.3), GLOW));
    } else {
      setScaleX(1.0);
      setScaleY(1.0);
      setEffect(null);
    }
  }

  private static Group artwork() {
    Rectangle artboard = new Rectangle(ARTBOARD_SIZE, ARTBOARD_SIZE, Color.TRANSPARENT);
    Circle plate = new Circle(12, 12, 9.1, new RadialGradient(
        0, 0, 9.3, 8.5, 10.75, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#d9f5ff")),
        new Stop(0.34, Color.web("#54bce9")),
        new Stop(0.72, Color.web("#176a9c")),
        new Stop(1, Color.web("#0c385c"))));
    plate.setStroke(Color.rgb(240, 250, 255, 0.9));
    plate.setStrokeWidth(0.9);
    plate.setEffect(new InnerShadow(1.5, 0, 0.7, Color.rgb(5, 24, 39, 0.72)));

    SVGPath shadow = stroked(
        "M18.4 9.2 A7 7 0 0 0 6.2 7.2 M6.2 7.2 H10 M6.2 7.2 V3.5 "
            + "M5.6 14.8 A7 7 0 0 0 17.8 16.8 M17.8 16.8 H14 M17.8 16.8 V20.5",
        Color.rgb(0, 35, 61, 0.82), 3.2);
    shadow.setTranslateY(0.55);
    SVGPath arrows = stroked(
        "M18.4 8.8 A7 7 0 0 0 6.2 6.8 M6.2 6.8 H10 M6.2 6.8 V3.1 "
            + "M5.6 14.4 A7 7 0 0 0 17.8 16.4 M17.8 16.4 H14 M17.8 16.4 V20.1",
        Color.web("#f6fdff"), 1.8);
    SVGPath shine = stroked(
        "M5.5 9 C7.3 4.5 14.1 2.7 18.4 6.4",
        Color.rgb(255, 255, 255, 0.62), 0.8);
    return new Group(artboard, plate, shadow, arrows, shine);
  }

  private static SVGPath stroked(String content, Color stroke, double width) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(Color.TRANSPARENT);
    path.setStroke(stroke);
    path.setStrokeWidth(width);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }
}
