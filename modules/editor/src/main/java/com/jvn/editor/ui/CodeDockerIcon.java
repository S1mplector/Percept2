package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
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
    Color quiet = Color.web("#66869a");
    Color primary = Color.web("#9edcff");
    Color bright = Color.web("#eefaff");

    Rectangle rear = roundedRect(3.0, 2.5, 12.5, 14.0, 2.2, Color.rgb(24, 43, 55, 0.72), quiet, 1.25);
    Rectangle front = roundedRect(6.5, 5.5, 13.0, 14.0, 2.2, Color.rgb(14, 28, 38, 0.96), primary, 1.55);
    SVGPath brackets = stroked("M11 9 L8.8 12.5 L11 16 M15 9 L17.2 12.5 L15 16", bright, 1.45);
    Line slash = line(13.8, 8.7, 12.2, 16.2, primary, 1.25);
    return new Group(rear, front, brackets, slash);
  }

  private static Group regenerate() {
    Color primary = Color.web("#ffc978");
    Color bright = Color.web("#fff3d5");
    SVGPath orbit = stroked(
        "M18.2 8.4 A7.2 7.2 0 0 0 6.2 5.8 L3.8 8.1 M6.2 5.8 L6.1 2.8 "
            + "M3.8 13.6 A7.2 7.2 0 0 0 15.8 16.2 L18.2 13.9 M15.8 16.2 L15.9 19.2",
        primary,
        1.65);
    SVGPath code = stroked("M9.4 8.2 L7.4 11 L9.4 13.8 M12.6 8.2 L14.6 11 L12.6 13.8", bright, 1.35);
    Circle core = new Circle(11, 11, 1.15, Color.web("#ffdf9f"));
    return new Group(orbit, code, core);
  }

  private static Group stagePreview() {
    Color primary = Color.web("#7ce4d2");
    Color bright = Color.web("#effffb");
    Rectangle screen = roundedRect(2.5, 3.5, 17.0, 13.5, 2.4, Color.rgb(14, 43, 43, 0.88), primary, 1.5);
    SVGPath code = stroked("M7.5 7.3 L5.3 10.2 L7.5 13.1 M10.8 7.3 L13 10.2 L10.8 13.1", bright, 1.35);
    SVGPath play = filled("M14.9 7.6 L18.2 10.25 L14.9 12.9 Z", Color.web("#5ff0b6"));
    Line stem = line(11, 17.2, 11, 19.3, primary, 1.35);
    Line foot = line(7.7, 19.5, 14.3, 19.5, primary, 1.35);
    return new Group(screen, code, play, stem, foot);
  }

  private static Group commit() {
    Color primary = Color.web("#7ee5a8");
    Color bright = Color.web("#f2fff6");
    SVGPath gate = stroked("M3.5 14.7 V18.7 H18.5 V14.7", primary, 1.6);
    SVGPath arrow = stroked("M11 2.8 V12.8 M7.5 9.6 L11 13.1 L14.5 9.6", bright, 1.75);
    SVGPath check = stroked("M14.4 5.7 L16.1 7.4 L19.3 3.8", primary, 1.45);
    Circle node = new Circle(11, 17.2, 1.15, Color.web("#baffd0"));
    return new Group(gate, arrow, check, node);
  }

  private static Group rollback() {
    Color primary = Color.web("#ef8b8b");
    Color bright = Color.web("#ffe4e4");
    Rectangle document = roundedRect(7.0, 3.0, 11.5, 15.5, 2.0, Color.rgb(48, 24, 29, 0.75), primary, 1.35);
    Line top = line(10, 7, 15.7, 7, primary.deriveColor(0, 0.85, 1.15, 0.8), 1.15);
    Line middle = line(10, 10, 14.5, 10, primary.deriveColor(0, 0.85, 1.15, 0.65), 1.15);
    SVGPath returnArrow = stroked("M12.3 16.1 H7.3 A4.2 4.2 0 0 1 3.1 11.9 V9.8 M3.1 9.8 L0.9 12 M3.1 9.8 L5.3 12", bright, 1.65);
    return new Group(document, top, middle, returnArrow);
  }

  private static Group diagnostics() {
    Color primary = Color.web("#f5bd69");
    Color bright = Color.web("#fff0c8");
    Rectangle panel = roundedRect(2.5, 4.0, 17.0, 13.5, 2.4, Color.rgb(47, 35, 18, 0.82), primary, 1.4);
    SVGPath pulse = stroked("M5 11.5 H7.4 L9.2 7.7 L11.7 15 L13.6 10.2 L15.1 11.5 H17", bright, 1.45);
    Circle indicator = new Circle(17.2, 6.6, 1.0, Color.web("#ffd47f"));
    return new Group(panel, pulse, indicator);
  }

  private static Rectangle roundedRect(
      double x,
      double y,
      double width,
      double height,
      double radius,
      Color fill,
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

  private static SVGPath stroked(String content, Color color, double width) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(Color.TRANSPARENT);
    path.setStroke(color);
    path.setStrokeWidth(width);
    path.setStrokeLineCap(StrokeLineCap.ROUND);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }

  private static SVGPath filled(String content, Color color) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(color);
    return path;
  }

  private static Line line(double startX, double startY, double endX, double endY, Color color, double width) {
    Line line = new Line(startX, startY, endX, endY);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }
}
