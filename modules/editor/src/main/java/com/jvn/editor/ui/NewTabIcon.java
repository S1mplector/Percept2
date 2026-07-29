package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

/** Plain plus glyph for the editor's sidebar add-tab control. */
public final class NewTabIcon extends StackPane {
  private static final double ARTBOARD_SIZE = 24.0;
  private final double iconSize;

  private NewTabIcon(double requestedSize) {
    iconSize = Math.max(16, Math.min(34, requestedSize));
    double scale = iconSize / ARTBOARD_SIZE;

    Rectangle artboard = new Rectangle(ARTBOARD_SIZE, ARTBOARD_SIZE, Color.TRANSPARENT);

    SVGPath plus = new SVGPath();
    plus.setContent("M11 19 V13 H5 V11 H11 V5 H13 V11 H19 V13 H13 V19 Z");
    plus.setFill(Color.web("#e8edf2"));

    Group artwork = new Group(artboard, plus);
    artwork.setScaleX(scale);
    artwork.setScaleY(scale);

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
}
