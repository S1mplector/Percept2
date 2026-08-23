package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Compact Windows 7-style vector artwork for the New Panel action buttons. */
public final class PanelChooserActionIcon extends Pane {
  public enum Kind {
    ADD_HERE,
    POP_OUT
  }

  private static final double SIZE = 20.0;
  private final Kind kind;

  private PanelChooserActionIcon(Kind requestedKind) {
    kind = requestedKind == null ? Kind.ADD_HERE : requestedKind;
    setMinSize(SIZE, SIZE);
    setPrefSize(SIZE, SIZE);
    setMaxSize(SIZE, SIZE);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().addAll("jvn-fx-icon", "panel-chooser-action-icon");

    Group artwork = switch (kind) {
      case ADD_HERE -> addHereArtwork();
      case POP_OUT -> popOutArtwork();
    };
    artwork.setEffect(new DropShadow(2.2, 0, 1.0, Color.rgb(0, 0, 0, 0.82)));
    getChildren().setAll(artwork);
  }

  public static PanelChooserActionIcon of(Kind kind) {
    return new PanelChooserActionIcon(kind);
  }

  public Kind kind() {
    return kind;
  }

  private static Group addHereArtwork() {
    Circle rim = new Circle(10, 10, 8.25, new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#ffffff")),
        new Stop(0.42, Color.web("#dfe8ed")),
        new Stop(1, Color.web("#788894"))));
    rim.setStroke(Color.web("#35434c"));
    rim.setStrokeWidth(0.8);

    Circle glass = new Circle(10, 10, 6.85, new RadialGradient(
        0, 0, 7.4, 6.7, 8.4, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#d8ffb7")),
        new Stop(0.32, Color.web("#7edb47")),
        new Stop(0.7, Color.web("#35a91d")),
        new Stop(1, Color.web("#176d12"))));
    glass.setStroke(Color.web("#e9ffd8"));
    glass.setStrokeWidth(0.72);
    glass.setEffect(new InnerShadow(1.25, 0, 0.65, Color.rgb(15, 67, 10, 0.76)));

    SVGPath plusShadow = filled("M8.2 4.9 H11.8 V8.2 H15.1 V11.8 H11.8 V15.1 H8.2 V11.8 H4.9 V8.2 H8.2 Z",
        Color.rgb(16, 64, 13, 0.72));
    plusShadow.setTranslateY(0.65);
    SVGPath plus = filled("M8.35 4.65 H11.65 V8.35 H15.35 V11.65 H11.65 V15.35 H8.35 V11.65 H4.65 V8.35 H8.35 Z",
        new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.WHITE),
            new Stop(0.52, Color.web("#f7fff1")),
            new Stop(1, Color.web("#bfd8b2"))));
    plus.setStroke(Color.rgb(32, 91, 25, 0.85));
    plus.setStrokeWidth(0.52);

    SVGPath shine = stroked("M4.8 8.1 C6.3 3.9 12.1 2.4 15.4 5.7", Color.rgb(255, 255, 255, 0.72), 0.78);
    return new Group(rim, glass, plusShadow, plus, shine);
  }

  private static Group popOutArtwork() {
    Rectangle rear = roundedRect(2.1, 5.7, 12.4, 11.6, 1.5,
        new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#f7fbfd")),
            new Stop(1, Color.web("#aebbc4"))),
        Color.web("#4d5b65"), 0.9);
    Rectangle rearGlass = roundedRect(3.45, 8.0, 9.7, 7.75, 0.75,
        Color.web("#d9edf5"), Color.web("#718895"), 0.55);

    Rectangle window = roundedRect(5.1, 2.2, 12.75, 11.9, 1.65,
        new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#fcfeff")),
            new Stop(0.45, Color.web("#dce7ec")),
            new Stop(1, Color.web("#8d9da8"))),
        Color.web("#3a4852"), 0.95);
    window.setEffect(new InnerShadow(0.9, 0, 0.5, Color.rgb(36, 54, 65, 0.48)));

    Rectangle titleBar = roundedRect(6.15, 3.25, 10.65, 2.65, 0.85,
        new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#bce9ff")),
            new Stop(0.45, Color.web("#47aee0")),
            new Stop(1, Color.web("#126697"))),
        Color.web("#e5f7ff"), 0.45);
    Circle button = new Circle(15.25, 4.55, 0.58, Color.web("#f4fbff"));
    Rectangle pane = roundedRect(6.25, 6.45, 10.45, 6.35, 0.65,
        new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#eefaff")),
            new Stop(1, Color.web("#9bc5d9"))),
        Color.web("#6c8998"), 0.48);

    SVGPath arrowShadow = stroked("M10.2 15.9 L16.6 9.5 M12.9 9.5 H16.6 V13.2", Color.rgb(20, 47, 63, 0.78), 3.15);
    SVGPath arrow = stroked("M10.2 15.4 L16.2 9.4 M12.7 9.4 H16.2 V12.9",
        new LinearGradient(0, 1, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#2f83b5")),
            new Stop(0.55, Color.web("#7fd5ff")),
            new Stop(1, Color.web("#eefaff"))), 1.85);
    SVGPath highlight = stroked("M6.4 3.75 H14.2", Color.rgb(255, 255, 255, 0.72), 0.58);
    return new Group(rear, rearGlass, window, titleBar, button, pane, arrowShadow, arrow, highlight);
  }

  private static Rectangle roundedRect(
      double x,
      double y,
      double width,
      double height,
      double radius,
      javafx.scene.paint.Paint fill,
      Color stroke,
      double strokeWidth) {
    Rectangle rectangle = new Rectangle(x, y, width, height);
    rectangle.setArcWidth(radius * 2);
    rectangle.setArcHeight(radius * 2);
    rectangle.setFill(fill);
    rectangle.setStroke(stroke);
    rectangle.setStrokeWidth(strokeWidth);
    return rectangle;
  }

  private static SVGPath filled(String content, javafx.scene.paint.Paint fill) {
    SVGPath path = path(content);
    path.setFill(fill);
    return path;
  }

  private static SVGPath stroked(String content, javafx.scene.paint.Paint stroke, double width) {
    SVGPath path = path(content);
    path.setFill(Color.TRANSPARENT);
    path.setStroke(stroke);
    path.setStrokeWidth(width);
    return path;
  }

  private static SVGPath path(String content) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }
}
