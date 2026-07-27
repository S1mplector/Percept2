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

/** Purpose-built vector artwork for the runtime window command strip. */
public final class RuntimeConsoleIcon extends Pane {
  public enum Kind {
    RUN,
    STOP,
    CLEAR,
    COPY,
    BUILD_OUTPUT,
    AUTO_SCROLL,
    WORD_WRAP,
    LAUNCH_OPTIONS
  }

  private static final double SIZE = 24.0;
  private static final Color SILVER = Color.web("#b8c4cd");
  private static final Color BRIGHT = Color.web("#f4fbff");
  private final Kind kind;

  private RuntimeConsoleIcon(Kind requestedKind) {
    kind = requestedKind == null ? Kind.RUN : requestedKind;
    setMinSize(SIZE, SIZE);
    setPrefSize(SIZE, SIZE);
    setMaxSize(SIZE, SIZE);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("runtime-console-command-icon");

    Group artwork = switch (kind) {
      case RUN -> run();
      case STOP -> stop();
      case CLEAR -> clear();
      case COPY -> copy();
      case BUILD_OUTPUT -> buildOutput();
      case AUTO_SCROLL -> autoScroll();
      case WORD_WRAP -> wordWrap();
      case LAUNCH_OPTIONS -> launchOptions();
    };
    artwork.setEffect(new DropShadow(2.8, 0, 1.1, Color.rgb(0, 0, 0, 0.76)));
    getChildren().setAll(artwork);
  }

  public static RuntimeConsoleIcon of(Kind kind) {
    return new RuntimeConsoleIcon(kind);
  }

  public Kind kind() {
    return kind;
  }

  private static Group run() {
    Circle bezel = jewel(12, 12, 9.4, "#193b2a", "#75e69e");
    SVGPath play = filled("M9 7.2 L17.2 12 L9 16.8 Z", gradient("#f2fff5", "#63dc8d"));
    SVGPath wake = stroked("M4.4 12 H7.1 M5.2 8.8 L7.5 10 M5.2 15.2 L7.5 14", Color.web("#7feaa4"), 1.35);
    Circle glint = new Circle(8.2, 7.2, 1.05, Color.rgb(255, 255, 255, 0.76));
    return new Group(bezel, wake, play, glint);
  }

  private static Group stop() {
    SVGPath octagon = filled(
        "M7.2 3.2 H16.8 L20.8 7.2 V16.8 L16.8 20.8 H7.2 L3.2 16.8 V7.2 Z",
        gradient("#8f3e58", "#391b2a"));
    octagon.setStroke(Color.web("#f294ac"));
    octagon.setStrokeWidth(1.2);
    Rectangle core = roundedRect(8, 8, 8, 8, 1.5, gradient("#ffe6ec", "#ef7898"), Color.web("#fff5f7"), 0.75);
    Line shine = line(7.1, 5.8, 16.4, 5.8, Color.rgb(255, 255, 255, 0.48), 0.9);
    return new Group(octagon, core, shine);
  }

  private static Group clear() {
    Rectangle terminal =
        roundedRect(2.7, 4.0, 18.6, 15.2, 2.6, gradient("#293640", "#10171c"), SILVER, 1.15);
    SVGPath prompt = stroked("M6.1 8.2 L8.8 10.6 L6.1 13 M10.3 13 H13.1", Color.web("#9edcff"), 1.45);
    SVGPath sweep = stroked("M13.4 15.8 L19.1 10.1 M15 9.1 L20.1 14.2", Color.web("#ffb56c"), 1.7);
    Circle spark = new Circle(18.8, 7.3, 1.15, Color.web("#ffe0a4"));
    return new Group(terminal, prompt, sweep, spark);
  }

  private static Group copy() {
    Rectangle rear =
        roundedRect(4.2, 2.8, 12.2, 14.2, 2.2, gradient("#354652", "#17232b"), Color.web("#718fa3"), 1.15);
    Rectangle front =
        roundedRect(7.3, 6.1, 12.5, 14.1, 2.2, gradient("#24445a", "#101f2a"), Color.web("#9ad9ff"), 1.35);
    SVGPath code = stroked("M11.8 10 L9.8 13.1 L11.8 16.2 M15.2 10 L17.2 13.1 L15.2 16.2", BRIGHT, 1.3);
    Line glint = line(6.5, 5, 13.3, 5, Color.rgb(255, 255, 255, 0.48), 0.8);
    return new Group(rear, front, code, glint);
  }

  private static Group buildOutput() {
    Rectangle console =
        roundedRect(2.4, 3.5, 19.2, 17.0, 2.8, gradient("#34302a", "#141312"), Color.web("#d2b47a"), 1.15);
    SVGPath bricks = stroked(
        "M5.3 8.1 H10.8 M13 8.1 H18.7 M5.3 11.9 H8.4 M10.5 11.9 H18.7 M5.3 15.7 H12.5",
        Color.web("#f5c46b"),
        1.45);
    Circle signal = new Circle(18.0, 16.1, 1.55, Color.web("#7fdfa2"));
    signal.setEffect(new DropShadow(3.0, Color.web("#48cf78")));
    return new Group(console, bricks, signal);
  }

  private static Group autoScroll() {
    Rectangle terminal =
        roundedRect(3.0, 2.8, 18.0, 18.4, 2.8, gradient("#243945", "#111b21"), Color.web("#80bad9"), 1.15);
    Line top = line(6.1, 7.0, 14.8, 7.0, Color.web("#93c9e5"), 1.25);
    Line middle = line(6.1, 10.1, 12.4, 10.1, Color.web("#688ea3"), 1.15);
    SVGPath arrow = stroked("M15.9 9.3 V16.6 M12.9 13.8 L15.9 16.8 L18.9 13.8", BRIGHT, 1.6);
    return new Group(terminal, top, middle, arrow);
  }

  private static Group wordWrap() {
    Rectangle page =
        roundedRect(3.2, 3.0, 17.6, 18.0, 2.5, gradient("#37313d", "#18141d"), Color.web("#c598d4"), 1.1);
    Line first = line(6.1, 7.4, 17.7, 7.4, Color.web("#dfb8ea"), 1.25);
    Line second = line(6.1, 10.8, 15.8, 10.8, Color.web("#b98cc7"), 1.2);
    SVGPath wrap = stroked(
        "M6.1 14.3 H15.8 A2.4 2.4 0 0 1 18.2 16.7 A2.4 2.4 0 0 1 15.8 19.1 H12.8 M14.8 17.1 L12.7 19.1 L14.8 21.1",
        BRIGHT,
        1.35);
    return new Group(page, first, second, wrap);
  }

  private static Group launchOptions() {
    Circle dial = jewel(11.2, 12.3, 8.6, "#3a2a17", "#f3b963");
    SVGPath gauge = stroked("M5.2 14.6 A6.4 6.4 0 0 1 17.2 9.8", Color.web("#ffe4a8"), 1.35);
    Line needle = line(11.2, 12.3, 15.9, 8.0, Color.web("#ff765f"), 1.65);
    Circle pin = new Circle(11.2, 12.3, 1.35, BRIGHT);
    SVGPath cache = filled("M15.4 14.2 H22 V19.9 H15.4 Z", gradient("#77c6ef", "#245878"));
    cache.setStroke(Color.web("#c8efff"));
    cache.setStrokeWidth(0.8);
    SVGPath cacheTop = stroked("M15.4 14.2 C15.4 12.7 22 12.7 22 14.2 C22 15.7 15.4 15.7 15.4 14.2", Color.web("#d6f4ff"), 0.95);
    Line cacheLine = line(15.4, 17.0, 15.4, 19.5, Color.web("#8bd8f5"), 0.8);
    return new Group(dial, gauge, needle, pin, cache, cacheTop, cacheLine);
  }

  private static Circle jewel(double x, double y, double radius, String dark, String bright) {
    Circle circle = new Circle(x, y, radius, gradient(bright, dark));
    circle.setStroke(Color.web(bright).deriveColor(0, 0.68, 1.12, 0.92));
    circle.setStrokeWidth(1.15);
    circle.setEffect(new InnerShadow(2.2, Color.rgb(255, 255, 255, 0.22)));
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

  private static SVGPath filled(String content, javafx.scene.paint.Paint fill) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(fill);
    return path;
  }

  private static Line line(
      double startX, double startY, double endX, double endY, Color color, double width) {
    Line line = new Line(startX, startY, endX, endY);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static LinearGradient gradient(String top, String bottom) {
    return new LinearGradient(
        0,
        0,
        0,
        1,
        true,
        CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(top)),
        new Stop(1, Color.web(bottom)));
  }
}
