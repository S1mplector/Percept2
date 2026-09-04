package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Purpose-built vector artwork for the Puppeteer code docker command row. */
public final class CodeDockerIcon extends Pane {
  public enum Kind {
    COPY_CODE,
    REGENERATE,
    STAGE_PREVIEW,
    COMMIT,
    ROLLBACK,
    DIAGNOSTICS
  }

  private static final double SIZE = 22.0;
  private final Kind kind;

  private CodeDockerIcon(Kind kind) {
    this.kind = kind == null ? Kind.COPY_CODE : kind;
    setMinSize(SIZE, SIZE);
    setPrefSize(SIZE, SIZE);
    setMaxSize(SIZE, SIZE);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("code-docker-command-icon");

    Group artwork = switch (this.kind) {
      case COPY_CODE -> copyCode();
      case REGENERATE -> regenerate();
      case STAGE_PREVIEW -> stagePreview();
      case COMMIT -> commit();
      case ROLLBACK -> rollback();
      case DIAGNOSTICS -> diagnostics();
    };
    artwork.setEffect(new DropShadow(2.4, 0, 1, Color.rgb(0, 0, 0, 0.72)));
    getChildren().setAll(artwork);
  }

  public static CodeDockerIcon of(Kind kind) {
    return new CodeDockerIcon(kind);
  }

  public Kind kind() {
    return kind;
  }

  private static Group copyCode() {
    Color accent = Color.web("#65bee6");
    Rectangle rear = roundedRect(2.4, 2.2, 12.8, 14.8, 2.0,
        metal(), Color.web("#3d515e"), 0.7);
    Rectangle front = roundedRect(5.7, 5.2, 13.4, 14.2, 2.0,
        glass(Color.web("#effbff"), accent.darker()), Color.web("#dff8ff"), 0.72);
    front.setEffect(new InnerShadow(1.0, Color.rgb(0, 27, 44, 0.66)));
    Rectangle title = roundedRect(7.1, 6.6, 10.4, 2.1, 0.7,
        enamel(accent), accent.darker(), 0.35);
    SVGPath brackets = stroked("M11 10 L8.8 12.8 L11 15.6 M14.3 10 L16.5 12.8 L14.3 15.6",
        Color.web("#f0fbff"), 1.35);
    Line slash = line(13.4, 9.7, 11.9, 15.9, accent.brighter(), 1.15);
    return new Group(rear, front, title, brackets, slash);
  }

  private static Group regenerate() {
    Color primary = Color.web("#e8a449");
    Circle chrome = new Circle(11, 11, 9.1, metal());
    chrome.setStroke(Color.web("#4e4538"));
    chrome.setStrokeWidth(0.7);
    Circle glass = new Circle(11, 11, 7.7, radial(primary));
    glass.setEffect(new InnerShadow(1.2, Color.rgb(59, 31, 3, 0.68)));
    SVGPath orbit = stroked(
        "M18.2 8.4 A7.2 7.2 0 0 0 6.2 5.8 L3.8 8.1 M6.2 5.8 L6.1 2.8 "
            + "M3.8 13.6 A7.2 7.2 0 0 0 15.8 16.2 L18.2 13.9 M15.8 16.2 L15.9 19.2",
        Color.web("#fff8e7"),
        1.65);
    SVGPath code = stroked("M9.4 8.2 L7.4 11 L9.4 13.8 M12.6 8.2 L14.6 11 L12.6 13.8",
        Color.web("#fff3cb"), 1.3);
    Circle core = new Circle(11, 11, 1.15, radial(Color.web("#ffe08a")));
    return new Group(chrome, glass, orbit, code, core);
  }

  private static Group stagePreview() {
    Color primary = Color.web("#55cdb5");
    Rectangle chassis = roundedRect(1.8, 2.6, 18.4, 14.7, 2.3,
        metal(), Color.web("#354f4d"), 0.75);
    Rectangle screen = roundedRect(3.2, 4.0, 15.6, 11.7, 1.5,
        glass(Color.web("#eafff9"), primary.darker()), Color.web("#e8fff9"), 0.55);
    screen.setEffect(new InnerShadow(1.1, Color.rgb(0, 45, 39, 0.68)));
    SVGPath code = stroked("M7.5 7.3 L5.3 10.2 L7.5 13.1 M10.8 7.3 L13 10.2 L10.8 13.1",
        Color.web("#effffb"), 1.25);
    Circle orb = new Circle(16.3, 11.0, 3.05, radial(Color.web("#51dc8b")));
    orb.setStroke(Color.web("#eafff0"));
    orb.setStrokeWidth(0.45);
    SVGPath play = filled("M15.4 9.2 L18.1 11 L15.4 12.8 Z", Color.WHITE);
    Rectangle stem = roundedRect(9.4, 17.1, 3.2, 2.0, 0.3, metal(), Color.TRANSPARENT, 0);
    Ellipse foot = new Ellipse(11, 20.0, 4.8, 0.9);
    foot.setFill(metal());
    return new Group(stem, foot, chassis, screen, code, orb, play);
  }

  private static Group commit() {
    Color primary = Color.web("#55c77b");
    Rectangle tray = roundedRect(2.3, 14.0, 17.4, 5.5, 1.3,
        metal(), Color.web("#3b5143"), 0.72);
    Rectangle inset = roundedRect(4.2, 15.0, 13.6, 2.0, 0.6,
        glass(Color.web("#eafff0"), primary.darker()), Color.web("#dffff0"), 0.35);
    Polygon arrow = new Polygon(8.1, 2.4, 13.9, 2.4, 13.9, 9.7, 17.0, 9.7,
        11.0, 15.0, 5.0, 9.7, 8.1, 9.7);
    arrow.setFill(enamel(primary));
    arrow.setStroke(Color.web("#28643f"));
    arrow.setStrokeWidth(0.62);
    SVGPath check = stroked("M14.8 5.8 L16.4 7.4 L19.5 3.7", Color.WHITE, 1.35);
    return new Group(tray, inset, arrow, check);
  }

  private static Group rollback() {
    Color primary = Color.web("#df6d73");
    Rectangle rear = roundedRect(6.0, 2.2, 12.8, 16.6, 1.9,
        metal(), Color.web("#5a4548"), 0.7);
    Rectangle document = roundedRect(7.4, 3.6, 11.4, 15.0, 1.5,
        glass(Color.web("#fff0f1"), primary.darker()), Color.web("#ffe9ea"), 0.55);
    Line top = line(9.5, 7.2, 16.5, 7.2, Color.web("#f6c6c8"), 0.9);
    Line middle = line(9.5, 10.1, 15.2, 10.1, Color.web("#eda5aa"), 0.9);
    Circle orb = new Circle(6.2, 14.7, 4.4, radial(primary));
    orb.setStroke(Color.web("#fff0f1"));
    orb.setStrokeWidth(0.55);
    SVGPath returnArrow = stroked("M8.4 15.9 H4.6 A2.7 2.7 0 0 1 2 13.2 V11.6 M2 11.6 L0.6 13 M2 11.6 L3.4 13",
        Color.WHITE, 1.45);
    return new Group(rear, document, top, middle, orb, returnArrow);
  }

  private static Group diagnostics() {
    Color primary = Color.web("#e5a84f");
    Rectangle chassis = roundedRect(1.8, 3.1, 18.4, 14.8, 2.2,
        metal(), Color.web("#554936"), 0.72);
    Rectangle panel = roundedRect(3.2, 4.5, 15.6, 12.0, 1.4,
        glass(Color.web("#fff7dc"), Color.web("#6e4b1f")), Color.web("#fff0c8"), 0.5);
    panel.setEffect(new InnerShadow(1.0, Color.rgb(47, 28, 4, 0.68)));
    SVGPath pulse = stroked("M5 11.5 H7.4 L9.2 7.7 L11.7 15 L13.6 10.2 L15.1 11.5 H17",
        Color.web("#fff0c8"), 1.35);
    Circle indicator = new Circle(16.8, 6.5, 1.25, radial(primary));
    Rectangle stem = roundedRect(9.4, 17.5, 3.2, 1.8, 0.2, metal(), Color.TRANSPARENT, 0);
    Ellipse foot = new Ellipse(11, 20.1, 4.7, 0.8);
    foot.setFill(metal());
    return new Group(stem, foot, chassis, panel, pulse, indicator);
  }

  private static Rectangle roundedRect(
      double x,
      double y,
      double width,
      double height,
      double radius,
      Paint fill,
      Paint stroke,
      double strokeWidth) {
    Rectangle rectangle = new Rectangle(x, y, width, height);
    rectangle.setArcWidth(radius * 2);
    rectangle.setArcHeight(radius * 2);
    rectangle.setFill(fill);
    rectangle.setStroke(stroke);
    rectangle.setStrokeWidth(strokeWidth);
    return rectangle;
  }

  private static SVGPath stroked(String content, Paint color, double width) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(Color.TRANSPARENT);
    path.setStroke(color);
    path.setStrokeWidth(width);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }

  private static SVGPath filled(String content, Paint color) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(color);
    return path;
  }

  private static Line line(double startX, double startY, double endX, double endY, Paint color, double width) {
    Line line = new Line(startX, startY, endX, endY);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static Paint metal() {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.18, Color.web("#edf3f6")),
        new Stop(0.48, Color.web("#87959e")), new Stop(0.70, Color.web("#dce5e9")),
        new Stop(1, Color.web("#56646d")));
  }

  private static Paint enamel(Color accent) {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, accent.brighter().brighter()), new Stop(0.2, accent.brighter()),
        new Stop(0.5, accent), new Stop(0.75, accent.darker()),
        new Stop(1, accent.darker().darker()));
  }

  private static Paint glass(Color top, Color bottom) {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, top), new Stop(0.22, top.deriveColor(0, 0.8, 0.94, 0.95)),
        new Stop(0.5, bottom.brighter()), new Stop(1, bottom));
  }

  private static Paint radial(Color accent) {
    return new RadialGradient(-35, 0.25, 0.36, 0.30, 0.65, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.2, accent.brighter()),
        new Stop(0.52, accent), new Stop(1, accent.darker().darker()));
  }
}
