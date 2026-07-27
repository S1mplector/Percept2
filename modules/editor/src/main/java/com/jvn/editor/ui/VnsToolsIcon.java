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
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Purpose-built vector artwork for commands in the VNS editor tool strip. */
public final class VnsToolsIcon extends Pane {
  public enum Kind {
    RUN_LABEL,
    RUN_ENTRY,
    GO_TO_SYMBOL,
    INSERT_SNIPPET,
    OPEN_PREVIEW
  }

  private static final double SIZE = 24.0;
  private static final Color PAPER = Color.web("#edf8ff");
  private final Kind kind;

  private VnsToolsIcon(Kind requestedKind) {
    kind = requestedKind == null ? Kind.RUN_LABEL : requestedKind;
    setMinSize(SIZE, SIZE);
    setPrefSize(SIZE, SIZE);
    setMaxSize(SIZE, SIZE);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("vns-tools-command-icon");

    Group artwork = switch (kind) {
      case RUN_LABEL -> runLabel();
      case RUN_ENTRY -> runEntry();
      case GO_TO_SYMBOL -> goToSymbol();
      case INSERT_SNIPPET -> insertSnippet();
      case OPEN_PREVIEW -> openPreview();
    };
    artwork.setEffect(new DropShadow(2.6, 0, 1.0, Color.rgb(0, 0, 0, 0.76)));
    getChildren().setAll(artwork);
  }

  public static VnsToolsIcon of(Kind kind) {
    return new VnsToolsIcon(kind);
  }

  public Kind kind() {
    return kind;
  }

  private static Group runLabel() {
    SVGPath tag = filled(
        "M2.8 5.2 H12.5 L17.2 10 L12.5 14.8 H2.8 Z",
        Color.web("#245d78"));
    tag.setStroke(Color.web("#8edcff"));
    tag.setStrokeWidth(1.25);
    Circle pin = new Circle(6.1, 10, 1.25, Color.web("#d9f6ff"));
    SVGPath play = filled("M13.1 10.8 L21 15.4 L13.1 20 Z", Color.web("#69e69b"));
    play.setStroke(Color.web("#eafff0"));
    play.setStrokeWidth(0.75);
    Line stem = line(11.2, 15.4, 11.2, 20, Color.web("#73b9d9"), 1.2);
    return new Group(tag, pin, stem, play);
  }

  private static Group runEntry() {
    Rectangle document = roundedRect(3.2, 2.5, 13.7, 18.5, 2.2, Color.web("#18394d"), Color.web("#81c9ef"), 1.25);
    SVGPath fold = filled("M12.1 2.6 L16.8 7.3 H12.1 Z", Color.web("#8fd7f7"));
    Line top = line(6.1, 8.3, 10.4, 8.3, PAPER, 1.15);
    Line middle = line(6.1, 11.4, 11.5, 11.4, Color.web("#83c6e8"), 1.0);
    SVGPath play = filled("M13.4 12.1 L21.3 16.5 L13.4 20.9 Z", Color.web("#65e49a"));
    play.setStroke(Color.web("#effff4"));
    play.setStrokeWidth(0.75);
    return new Group(document, fold, top, middle, play);
  }

  private static Group goToSymbol() {
    SVGPath symbol = filled(
        "M2.7 5.1 H12.1 L16.2 9.2 L12.1 13.3 H2.7 Z",
        Color.web("#20526d"));
    symbol.setStroke(Color.web("#83d4ff"));
    symbol.setStrokeWidth(1.2);
    Circle pin = new Circle(5.9, 9.2, 1.15, PAPER);
    Circle lens = new Circle(15.1, 15.0, 4.35, Color.rgb(55, 40, 20, 0.9));
    lens.setStroke(Color.web("#ffc66f"));
    lens.setStrokeWidth(1.55);
    Line handle = line(18.3, 18.2, 21.3, 21.2, Color.web("#ffe0a6"), 2.0);
    Line target = line(13.1, 15, 17.1, 15, Color.web("#fff3d4"), 1.0);
    return new Group(symbol, pin, lens, target, handle);
  }

  private static Group insertSnippet() {
    Rectangle document = roundedRect(3.1, 2.6, 14.4, 18.2, 2.2, Color.web("#28314f"), Color.web("#aab9ff"), 1.25);
    SVGPath fold = filled("M12.5 2.7 L17.4 7.5 H12.5 Z", Color.web("#bfc9ff"));
    SVGPath code = stroked("M8.4 9.2 L6.2 12 L8.4 14.8 M11.8 9.2 L14 12 L11.8 14.8", PAPER, 1.25);
    Circle plusDisc = new Circle(18.4, 17.9, 4.05, Color.web("#7b4a20"));
    plusDisc.setStroke(Color.web("#ffc66f"));
    plusDisc.setStrokeWidth(1.1);
    Line plusH = line(16.2, 17.9, 20.6, 17.9, Color.web("#fff2cd"), 1.45);
    Line plusV = line(18.4, 15.7, 18.4, 20.1, Color.web("#fff2cd"), 1.45);
    return new Group(document, fold, code, plusDisc, plusH, plusV);
  }

  private static Group openPreview() {
    Rectangle window = roundedRect(2.3, 4.0, 17.2, 15.1, 2.4, Color.web("#16394a"), Color.web("#84d2f4"), 1.3);
    Line title = line(3.2, 7.1, 18.5, 7.1, Color.web("#638ca0"), 0.9);
    Circle dot = new Circle(5.0, 5.6, 0.7, Color.web("#ffbd68"));
    SVGPath play = filled("M7.4 9.5 L13.8 13.1 L7.4 16.7 Z", Color.web("#65e39a"));
    SVGPath arrow = stroked("M14.2 9.8 L21.2 2.8 M16.7 2.8 H21.2 V7.3", PAPER, 1.65);
    return new Group(window, title, dot, play, arrow);
  }

  private static Rectangle roundedRect(
      double x, double y, double width, double height, double radius, Color fill, Color stroke, double strokeWidth) {
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
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
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
