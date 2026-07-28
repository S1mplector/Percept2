package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Compact Aero-style vector artwork for the diagnostics command buttons. */
public final class DiagnosticsToolbarIcon extends Pane {
  public enum Kind {
    RESCAN,
    OPEN,
    PREVIOUS,
    NEXT,
    COPY_REPORT,
    CLEAR_FILTER,
    SORT_LINE,
    SORT_SEVERITY
  }

  private static final double SIZE = 20.0;
  private static final Color WHITE = Color.web("#f7fcff");
  private final Kind kind;

  private DiagnosticsToolbarIcon(Kind requestedKind) {
    kind = requestedKind == null ? Kind.RESCAN : requestedKind;
    setMinSize(SIZE, SIZE);
    setPrefSize(SIZE, SIZE);
    setMaxSize(SIZE, SIZE);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("vns-diagnostics-command-icon");

    Group artwork = switch (kind) {
      case RESCAN -> rescan();
      case OPEN -> open();
      case PREVIOUS -> navigation(true);
      case NEXT -> navigation(false);
      case COPY_REPORT -> copyReport();
      case CLEAR_FILTER -> clearFilter();
      case SORT_LINE -> sortLine();
      case SORT_SEVERITY -> sortSeverity();
    };
    artwork.setEffect(new DropShadow(2.2, 0, 1.0, Color.rgb(0, 0, 0, 0.52)));
    getChildren().setAll(artwork);
  }

  public static DiagnosticsToolbarIcon of(Kind kind) {
    return new DiagnosticsToolbarIcon(kind);
  }

  public Kind kind() {
    return kind;
  }

  private static Group rescan() {
    Circle bezel = jewel(10, 10, 8.2, "#225877", "#88d3f1");
    SVGPath arrow = stroked(
        "M14.9 7.2 A5.6 5.6 0 1 0 15 13.1 M14.9 7.2 L14.7 3.7 M14.9 7.2 L11.5 7.1",
        WHITE,
        1.65);
    Circle status = new Circle(15.9, 15.5, 2.25, gradient("#b9f7bd", "#3f9d4a"));
    status.setStroke(Color.web("#e8ffe9"));
    status.setStrokeWidth(0.7);
    return new Group(bezel, arrow, status);
  }

  private static Group open() {
    Rectangle window = roundedRect(
        2.1, 3.0, 14.8, 13.8, 2.0, gradient("#eaf8ff", "#5da8d2"), Color.web("#2f6c91"), 1.0);
    Rectangle title = roundedRect(
        3.3, 4.1, 12.3, 2.4, 0.8, gradient("#ffffff", "#a8d7ed"), Color.TRANSPARENT, 0);
    Rectangle pane = roundedRect(
        3.8, 7.4, 9.1, 7.3, 0.8, gradient("#d7f0fb", "#73b3d4"), Color.web("#dff7ff"), 0.55);
    SVGPath arrow = stroked("M10.8 10.7 L17.5 4 M13.3 4 H17.5 V8.2", WHITE, 1.7);
    arrow.setEffect(new DropShadow(2.2, Color.web("#214d69")));
    return new Group(window, title, pane, arrow);
  }

  private static Group navigation(boolean previous) {
    Circle bezel = jewel(10, 10, 8.0, "#334e67", "#9bc7e7");
    String path = previous
        ? "M5.8 11.4 L10 7.2 L14.2 11.4 M10 7.6 V14.7"
        : "M5.8 8.6 L10 12.8 L14.2 8.6 M10 5.3 V12.4";
    SVGPath arrow = stroked(path, WHITE, 1.7);
    return new Group(bezel, arrow);
  }

  private static Group copyReport() {
    Rectangle rear = roundedRect(
        3.0, 2.1, 11.5, 14.2, 1.7, gradient("#dcebf3", "#7794a8"), Color.web("#435e70"), 0.9);
    Rectangle page = roundedRect(
        6.0, 4.7, 11.1, 13.1, 1.7, gradient("#ffffff", "#c6dfec"), Color.web("#4f84a2"), 1.0);
    Circle tickOne = new Circle(8.5, 8.4, 1.15, gradient("#baf1bd", "#40924a"));
    Circle tickTwo = new Circle(8.5, 12.4, 1.15, gradient("#baf1bd", "#40924a"));
    Line lineOne = line(11, 8.4, 15.0, 8.4, Color.web("#4d7890"), 1.0);
    Line lineTwo = line(11, 12.4, 15.0, 12.4, Color.web("#4d7890"), 1.0);
    Line shine = line(7.2, 6.0, 15.8, 6.0, Color.rgb(255, 255, 255, 0.72), 0.7);
    return new Group(rear, page, tickOne, tickTwo, lineOne, lineTwo, shine);
  }

  private static Group clearFilter() {
    SVGPath funnel = filled(
        "M2.4 3.2 H17.1 L12.3 8.7 V14.2 L8.3 16.8 V8.7 Z",
        gradient("#dff4ff", "#5b9dc2"));
    funnel.setStroke(Color.web("#356b89"));
    funnel.setStrokeWidth(1.0);
    funnel.setStrokeLineJoin(StrokeLineJoin.ROUND);
    Line shine = line(4.3, 4.7, 14.7, 4.7, Color.rgb(255, 255, 255, 0.72), 0.8);
    Circle badge = new Circle(15.5, 14.8, 3.6, gradient("#ffb0ad", "#b83c42"));
    badge.setStroke(Color.web("#ffe7e6"));
    badge.setStrokeWidth(0.8);
    SVGPath x = stroked("M13.7 13 L17.3 16.6 M17.3 13 L13.7 16.6", WHITE, 1.35);
    return new Group(funnel, shine, badge, x);
  }

  private static Group sortLine() {
    Rectangle plate = roundedRect(
        2.2, 2.1, 15.6, 15.8, 2.1, gradient("#f3fbff", "#9bc5dc"), Color.web("#527b93"), 1.0);
    Line one = line(8.2, 6.1, 15.1, 6.1, Color.web("#416a82"), 1.15);
    Line two = line(8.2, 10.0, 13.6, 10.0, Color.web("#416a82"), 1.15);
    Line three = line(8.2, 13.9, 12.2, 13.9, Color.web("#416a82"), 1.15);
    Circle dotOne = new Circle(5.2, 6.1, 1.25, gradient("#9ad8f2", "#2b789e"));
    Circle dotTwo = new Circle(5.2, 10.0, 1.25, gradient("#9ad8f2", "#2b789e"));
    Circle dotThree = new Circle(5.2, 13.9, 1.25, gradient("#9ad8f2", "#2b789e"));
    return new Group(plate, one, two, three, dotOne, dotTwo, dotThree);
  }

  private static Group sortSeverity() {
    Rectangle plate = roundedRect(
        2.2, 2.1, 15.6, 15.8, 2.1, gradient("#f3fbff", "#9bc5dc"), Color.web("#527b93"), 1.0);
    Circle error = new Circle(5.3, 6.0, 1.7, gradient("#ffaca9", "#b8373f"));
    Circle warning = new Circle(5.3, 10.1, 1.7, gradient("#ffe39a", "#bd8420"));
    Circle info = new Circle(5.3, 14.2, 1.7, gradient("#9cdbf4", "#367ca2"));
    Line one = line(8.5, 6.0, 15.0, 6.0, Color.web("#557a8e"), 1.1);
    Line two = line(8.5, 10.1, 13.6, 10.1, Color.web("#557a8e"), 1.1);
    Line three = line(8.5, 14.2, 12.3, 14.2, Color.web("#557a8e"), 1.1);
    return new Group(plate, error, warning, info, one, two, three);
  }

  private static Circle jewel(double x, double y, double radius, String dark, String bright) {
    Circle circle = new Circle(x, y, radius, gradient(bright, dark));
    circle.setStroke(Color.web(bright).deriveColor(0, 0.72, 1.08, 0.9));
    circle.setStrokeWidth(1.0);
    circle.setEffect(new InnerShadow(2.0, Color.rgb(255, 255, 255, 0.2)));
    return circle;
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

  private static Line line(double x1, double y1, double x2, double y2, Color color, double width) {
    Line line = new Line(x1, y1, x2, y2);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static SVGPath filled(String path, javafx.scene.paint.Paint fill) {
    SVGPath shape = new SVGPath();
    shape.setContent(path);
    shape.setFill(fill);
    return shape;
  }

  private static SVGPath stroked(String path, Color color, double width) {
    SVGPath shape = new SVGPath();
    shape.setContent(path);
    shape.setFill(Color.TRANSPARENT);
    shape.setStroke(color);
    shape.setStrokeWidth(width);
    shape.setStrokeLineCap(StrokeLineCap.ROUND);
    shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return shape;
  }

  private static LinearGradient gradient(String top, String bottom) {
    return new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(top)),
        new Stop(0.46, Color.web(top).deriveColor(0, 0.9, 1.08, 1)),
        new Stop(1, Color.web(bottom)));
  }
}
