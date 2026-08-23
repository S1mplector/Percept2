package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.control.ButtonBase;
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
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Dimensional Windows 7-style artwork for every icon role in Version Control. */
public final class VersionControlIcon extends Pane {
  public enum Kind {
    CHECK_ONLINE,
    PULL,
    PUSH,
    FORCE_PULL,
    FORCE_PUSH,
    SNAPSHOT,
    SHELVE,
    RESTORE_SHELF,
    STAGE,
    UNSTAGE,
    DISCARD,
    DIFF,
    NEW_BRANCH,
    BRANCH,
    INITIALIZE,
    SETUP,
    WARNING,
    GUIDE_UP,
    CLOSE
  }

  private static final double ARTBOARD_SIZE = 24.0;
  private final Kind kind;
  private final double iconSize;
  private final Color glow;

  private VersionControlIcon(Kind requestedKind, double requestedSize) {
    kind = requestedKind == null ? Kind.CHECK_ONLINE : requestedKind;
    iconSize = Math.max(14, Math.min(28, requestedSize));
    glow = glowFor(kind);

    Group artwork = artworkFor(kind);
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
    getStyleClass().addAll("jvn-fx-icon", "jvn-vcs-aero-icon");
    getChildren().setAll(artwork);
    parentProperty().addListener((obs, oldParent, newParent) -> {
      if (newParent instanceof ButtonBase button) installButtonTreatment(button);
    });
  }

  public static VersionControlIcon of(Kind kind) {
    return of(kind, 20);
  }

  public static VersionControlIcon of(Kind kind, double size) {
    return new VersionControlIcon(kind, size);
  }

  public Kind kind() {
    return kind;
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
      setEffect(new DropShadow(Math.max(2.4, iconSize * 0.14), glow.deriveColor(0, 0.8, 0.8, 0.72)));
    } else if (button.isHover()) {
      setScaleX(1.05);
      setScaleY(1.05);
      setEffect(new DropShadow(Math.max(5.0, iconSize * 0.3), glow));
    } else {
      setScaleX(1.0);
      setScaleY(1.0);
      setEffect(null);
    }
  }

  private static Group artworkFor(Kind kind) {
    return switch (kind) {
      case CHECK_ONLINE -> checkOnlineArtwork();
      case PULL -> transferArtwork(false, false);
      case PUSH -> transferArtwork(true, false);
      case FORCE_PULL -> transferArtwork(false, true);
      case FORCE_PUSH -> transferArtwork(true, true);
      case SNAPSHOT -> snapshotArtwork();
      case SHELVE -> shelfArtwork(false);
      case RESTORE_SHELF -> shelfArtwork(true);
      case STAGE -> changeOrbArtwork(true);
      case UNSTAGE -> changeOrbArtwork(false);
      case DISCARD -> discardArtwork();
      case DIFF -> diffArtwork();
      case NEW_BRANCH -> branchArtwork(true);
      case BRANCH -> branchArtwork(false);
      case INITIALIZE -> initializeArtwork();
      case SETUP -> setupArtwork();
      case WARNING -> warningArtwork();
      case GUIDE_UP -> guideUpArtwork();
      case CLOSE -> closeArtwork();
    };
  }

  private static Group checkOnlineArtwork() {
    SVGPath cloudShadow = filled(
        "M6.2 18.5 C3.4 18.5 2.3 16.8 2.3 14.9 C2.3 12.8 3.9 11.1 6 10.9 "
            + "C6.8 7.5 9.4 5.4 12.7 5.4 C16.2 5.4 18.8 7.8 19.2 11.1 "
            + "C21 11.5 22.1 12.9 22.1 14.8 C22.1 17 20.4 18.5 17.9 18.5 Z",
        Color.rgb(12, 45, 63, 0.78));
    cloudShadow.setTranslateY(0.65);
    SVGPath cloud = filled(
        "M6.2 18 C3.4 18 2.3 16.3 2.3 14.4 C2.3 12.3 3.9 10.6 6 10.4 "
            + "C6.8 7 9.4 4.9 12.7 4.9 C16.2 4.9 18.8 7.3 19.2 10.6 "
            + "C21 11 22.1 12.4 22.1 14.3 C22.1 16.5 20.4 18 17.9 18 Z",
        gloss("#f7fdff", "#8fd5ef", "#3b88ae"));
    cloud.setStroke(Color.web("#dff7ff"));
    cloud.setStrokeWidth(0.8);
    SVGPath arrow = filled("M10.25 9 H13.75 V13 H17 L12 18 L7 13 H10.25 Z",
        gloss("#eaffdf", "#68d447", "#24851d"));
    arrow.setStroke(Color.web("#f3ffed"));
    arrow.setStrokeWidth(0.55);
    return art(cloudShadow, cloud, arrow);
  }

  private static Group transferArtwork(boolean push, boolean force) {
    String light = force ? "#ffd8df" : push ? "#dfffe6" : "#fff3c9";
    String mid = force ? "#e25872" : push ? "#59cf72" : "#e3ad43";
    String dark = force ? "#8e2039" : push ? "#217d39" : "#8b5719";
    Rectangle tray = roundedRect(3.2, 17.1, 17.6, 3.6, 1.2,
        gloss("#f7fbfd", "#aebcc6", "#52636f"), Color.web("#32434f"), 0.8);
    SVGPath arrowPath = filled(
        push
            ? "M9.5 16.8 V9.4 H5.8 L12 3.2 L18.2 9.4 H14.5 V16.8 Z"
            : "M9.5 3.2 H14.5 V10.6 H18.2 L12 16.8 L5.8 10.6 H9.5 Z",
        gloss(light, mid, dark));
    arrowPath.setStroke(Color.web(force ? "#ffe9ee" : push ? "#eaffed" : "#fff8df"));
    arrowPath.setStrokeWidth(0.75);
    if (!force) return art(tray, arrowPath);

    Circle badge = glassOrb(18.6, 17.8, 4.2, "#fff3ad", "#f2b83e", "#b85f16", "#66300c");
    SVGPath mark = filled("M17.9 14.8 H19.3 V18.5 H17.9 Z M17.9 19.5 H19.3 V20.9 H17.9 Z", Color.WHITE);
    return art(tray, arrowPath, badge, mark);
  }

  private static Group snapshotArtwork() {
    Rectangle body = roundedRect(3.2, 2.5, 17.6, 19, 2.2,
        gloss("#bfe5ff", "#4c91c7", "#214f79"), Color.web("#193d5e"), 1.0);
    Rectangle label = roundedRect(6.1, 3.8, 10.8, 6.6, 0.8,
        gloss("#ffffff", "#e7edf0", "#aab6bd"), Color.web("#5e7180"), 0.65);
    Rectangle slot = roundedRect(13.2, 4.4, 2.2, 4.8, 0.35, Color.web("#4e6270"), Color.web("#263944"), 0.4);
    Rectangle inset = roundedRect(6.2, 13.1, 11.6, 7.1, 1.0,
        gloss("#d9efff", "#91bdd8", "#547e99"), Color.web("#dff6ff"), 0.55);
    Circle led = new Circle(17.1, 4.9, 0.8, Color.web("#a8ff75"));
    led.setEffect(new DropShadow(2.4, Color.web("#55d82f")));
    return art(body, label, slot, inset, led);
  }

  private static Group shelfArtwork(boolean restore) {
    SVGPath folder = filled(
        "M2.6 7 Q2.6 5.5 4.1 5.5 H9 L11 7.5 H20 Q21.4 7.5 21.4 9 V19 "
            + "Q21.4 20.5 19.9 20.5 H4.1 Q2.6 20.5 2.6 19 Z",
        gloss("#eadfff", "#8d70bd", "#4a326f"));
    folder.setStroke(Color.web("#d9c8ff"));
    folder.setStrokeWidth(0.8);
    SVGPath lip = stroked("M3.8 9.2 H20.2", Color.rgb(255, 255, 255, 0.62), 0.75);
    if (!restore) return art(folder, lip);

    SVGPath arrowShadow = stroked("M12 18 V10 M8.9 13.2 L12 10 L15.1 13.2", Color.rgb(48, 24, 3, 0.8), 3.1);
    arrowShadow.setTranslateY(0.55);
    SVGPath arrow = stroked("M12 17.5 V9.5 M8.9 12.7 L12 9.5 L15.1 12.7", Color.web("#fff3bd"), 1.75);
    return art(folder, lip, arrowShadow, arrow);
  }

  private static Group changeOrbArtwork(boolean add) {
    Circle orb = add
        ? glassOrb(12, 12, 9.0, "#eaffd5", "#7ddd52", "#319d23", "#155b12")
        : glassOrb(12, 12, 9.0, "#fff5ce", "#e8bd58", "#ad6e22", "#60350e");
    SVGPath shadow = filled(
        add
            ? "M10.1 5.8 H13.9 V10.1 H18.2 V13.9 H13.9 V18.2 H10.1 V13.9 H5.8 V10.1 H10.1 Z"
            : "M5.8 10.1 H18.2 V13.9 H5.8 Z",
        Color.rgb(38, 38, 24, 0.72));
    shadow.setTranslateY(0.65);
    SVGPath mark = filled(
        add
            ? "M10.1 5.4 H13.9 V10.1 H18.6 V13.9 H13.9 V18.6 H10.1 V13.9 H5.4 V10.1 H10.1 Z"
            : "M5.4 10.1 H18.6 V13.9 H5.4 Z",
        gloss("#ffffff", "#f3f8ef", "#bcc8b5"));
    mark.setStroke(Color.rgb(36, 66, 31, 0.8));
    mark.setStrokeWidth(0.5);
    return art(orb, shadow, mark, orbShine());
  }

  private static Group discardArtwork() {
    Rectangle body = roundedRect(6.2, 7.3, 11.6, 13.8, 1.5,
        gloss("#ffcad3", "#d85b72", "#78283a"), Color.web("#571a2a"), 0.9);
    Rectangle lid = roundedRect(4.7, 4.8, 14.6, 3.1, 1.0,
        gloss("#ffe1e7", "#e66c81", "#882d42"), Color.web("#5e1c2d"), 0.75);
    Rectangle handle = roundedRect(9, 2.7, 6, 2.8, 0.9,
        gloss("#ffd7df", "#c84e68", "#742238"), Color.web("#5a1728"), 0.65);
    Line left = line(9.1, 10, 9.6, 18.6, Color.web("#ffdbe2"), 1.0);
    Line center = line(12, 10, 12, 18.6, Color.web("#ffdbe2"), 1.0);
    Line right = line(14.9, 10, 14.4, 18.6, Color.web("#ffdbe2"), 1.0);
    return art(body, lid, handle, left, center, right);
  }

  private static Group diffArtwork() {
    Rectangle page = roundedRect(3.6, 2.7, 12.8, 17.2, 1.6,
        gloss("#ffffff", "#d9e5ec", "#8298a7"), Color.web("#405766"), 0.85);
    Line first = line(6, 7, 13.7, 7, Color.web("#5e91b0"), 1.0);
    Line second = line(6, 10.3, 12.5, 10.3, Color.web("#6aa870"), 1.0);
    Line third = line(6, 13.6, 10.7, 13.6, Color.web("#c56d78"), 1.0);
    Circle lens = new Circle(15.9, 15.8, 4.35, new RadialGradient(
        0, 0, 14.5, 14.4, 5.1, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.rgb(224, 248, 255, 0.78)),
        new Stop(1, Color.rgb(58, 139, 184, 0.78))));
    lens.setStroke(Color.web("#dff7ff"));
    lens.setStrokeWidth(1.1);
    Line handle = line(18.8, 18.8, 22, 22, Color.web("#607987"), 2.4);
    return art(page, first, second, third, lens, handle);
  }

  private static Group branchArtwork(boolean withPlus) {
    SVGPath branch = stroked(
        "M6.2 4.2 V14.4 Q6.2 18.2 10 18.2 H15.4 M6.2 9.2 H11.4 Q15.2 9.2 15.2 5.4",
        Color.web("#d9ceff"), 2.15);
    branch.setEffect(new DropShadow(1.4, 0, 0.7, Color.web("#342456")));
    Circle root = node(6.2, 4.2, "#f2edff", "#7655b4");
    Circle upper = node(15.2, 4.2, "#dff7ff", "#3183b7");
    Circle lower = node(16.7, 18.2, "#e4ffdc", "#3a9d36");
    if (!withPlus) return art(branch, root, upper, lower);

    Circle badge = glassOrb(18.6, 7.1, 4.1, "#ecffdc", "#82df55", "#329b25", "#14540e");
    SVGPath plus = filled("M17.8 4.6 H19.4 V6.3 H21.1 V7.9 H19.4 V9.6 H17.8 V7.9 H16.1 V6.3 H17.8 Z", Color.WHITE);
    return art(branch, root, upper, lower, badge, plus);
  }

  private static Group initializeArtwork() {
    Ellipse top = new Ellipse(11.2, 5.1, 7.8, 3.1);
    top.setFill(gloss("#f8fcff", "#a9c4d5", "#526f82"));
    top.setStroke(Color.web("#dff5ff"));
    top.setStrokeWidth(0.8);
    SVGPath body = filled(
        "M3.4 5 V16.5 C3.4 19 6.9 20.9 11.2 20.9 C15.5 20.9 19 19 19 16.5 V5 "
            + "C18 7.2 15.1 8.2 11.2 8.2 C7.3 8.2 4.4 7.2 3.4 5 Z",
        gloss("#d9eff9", "#769eb7", "#38566c"));
    body.setStroke(Color.web("#d9f0fa"));
    body.setStrokeWidth(0.75);
    SVGPath band = stroked("M3.8 11.5 C7.5 14 15 14 18.7 11.5", Color.rgb(235, 250, 255, 0.6), 0.75);
    Circle badge = glassOrb(18.4, 17.8, 4.35, "#eaffd8", "#79dc50", "#319a25", "#15540f");
    SVGPath plus = filled("M17.5 15.1 H19.3 V16.9 H21.1 V18.7 H19.3 V20.5 H17.5 V18.7 H15.7 V16.9 H17.5 Z", Color.WHITE);
    return art(body, top, band, badge, plus);
  }

  private static Group setupArtwork() {
    Circle plate = glassOrb(12, 12, 9.2, "#edfaff", "#82ccea", "#337da7", "#173f61");
    SVGPath wrenchShadow = stroked(
        "M7.1 17 L15.4 8.7 C14.8 6.4 16.3 4.1 18.7 3.8 L17 5.5 L18.6 7.1 L20.3 5.4 C20.6 7.9 18.2 9.5 16 8.9 L7.8 17.8 Z",
        Color.rgb(0, 31, 53, 0.78), 3.0);
    SVGPath wrench = stroked(
        "M7 16.5 L15.3 8.2 C14.7 5.9 16.2 3.6 18.6 3.3 L16.9 5 L18.5 6.6 L20.2 4.9 C20.5 7.4 18.1 9 15.9 8.4 L7.7 17.3 Z",
        Color.web("#f5fbff"), 1.7);
    return art(plate, wrenchShadow, wrench, orbShine());
  }

  private static Group warningArtwork() {
    SVGPath plate = filled("M12 2.1 L22 20.7 H2 Z", gloss("#fff8bc", "#efbd44", "#a65b13"));
    plate.setStroke(Color.web("#fff6cf"));
    plate.setStrokeWidth(1.0);
    plate.setEffect(new InnerShadow(1.2, Color.rgb(92, 48, 5, 0.58)));
    Rectangle stem = roundedRect(10.65, 8, 2.7, 7.2, 0.8,
        gloss("#ffffff", "#f3f3ea", "#a9aa9f"), Color.web("#71511b"), 0.4);
    Circle dot = new Circle(12, 18, 1.45, Color.web("#f8f8ed"));
    return art(plate, stem, dot);
  }

  private static Group guideUpArtwork() {
    SVGPath shadow = filled("M4 14 L12 6 L20 14 H15 V20 H9 V14 Z", Color.rgb(0, 35, 58, 0.76));
    shadow.setTranslateY(0.65);
    SVGPath arrow = filled("M4 13.4 L12 5.4 L20 13.4 H15 V19.4 H9 V13.4 Z",
        gloss("#ecfbff", "#67c5ef", "#246c9d"));
    arrow.setStroke(Color.web("#dff7ff"));
    arrow.setStrokeWidth(0.7);
    return art(shadow, arrow);
  }

  private static Group closeArtwork() {
    Circle orb = glassOrb(12, 12, 9, "#ffe7eb", "#eb8192", "#bd3e55", "#681d2e");
    SVGPath shadow = stroked("M8 8 L16 16 M16 8 L8 16", Color.rgb(69, 12, 24, 0.8), 3.4);
    shadow.setTranslateY(0.55);
    SVGPath cross = stroked("M8 7.6 L16 15.6 M16 7.6 L8 15.6", Color.WHITE, 1.9);
    return art(orb, shadow, cross, orbShine());
  }

  private static Group art(javafx.scene.Node... nodes) {
    Rectangle artboard = new Rectangle(ARTBOARD_SIZE, ARTBOARD_SIZE, Color.TRANSPARENT);
    Group group = new Group(artboard);
    group.getChildren().addAll(nodes);
    return group;
  }

  private static Circle glassOrb(
      double centerX,
      double centerY,
      double radius,
      String highlight,
      String light,
      String mid,
      String dark) {
    Circle orb = new Circle(centerX, centerY, radius, new RadialGradient(
        0, 0, centerX - radius * 0.3, centerY - radius * 0.38, radius * 1.18,
        false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(highlight)),
        new Stop(0.34, Color.web(light)),
        new Stop(0.72, Color.web(mid)),
        new Stop(1, Color.web(dark))));
    orb.setStroke(Color.rgb(240, 250, 255, 0.9));
    orb.setStrokeWidth(0.9);
    orb.setEffect(new InnerShadow(1.5, 0, 0.7, Color.rgb(5, 24, 39, 0.72)));
    return orb;
  }

  private static SVGPath orbShine() {
    return stroked("M5.5 9 C7.3 4.5 14.1 2.7 18.4 6.4", Color.rgb(255, 255, 255, 0.62), 0.8);
  }

  private static Paint gloss(String light, String mid, String dark) {
    return new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(light)),
        new Stop(0.48, Color.web(mid)),
        new Stop(1, Color.web(dark)));
  }

  private static Rectangle roundedRect(
      double x,
      double y,
      double width,
      double height,
      double radius,
      Paint fill,
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

  private static SVGPath filled(String content, Paint fill) {
    SVGPath path = path(content);
    path.setFill(fill);
    return path;
  }

  private static SVGPath stroked(String content, Paint stroke, double width) {
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

  private static Line line(double x1, double y1, double x2, double y2, Color color, double width) {
    Line line = new Line(x1, y1, x2, y2);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static Circle node(double x, double y, String light, String dark) {
    Circle node = new Circle(x, y, 2.25, new RadialGradient(
        0, 0, x - 0.7, y - 0.8, 2.8, false, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(light)),
        new Stop(1, Color.web(dark))));
    node.setStroke(Color.rgb(245, 250, 255, 0.9));
    node.setStrokeWidth(0.65);
    return node;
  }

  private static Color glowFor(Kind kind) {
    return switch (kind) {
      case CHECK_ONLINE, DIFF, SETUP, GUIDE_UP -> Color.web("#63c9f2");
      case PUSH, SNAPSHOT, STAGE, NEW_BRANCH, INITIALIZE -> Color.web("#68d66f");
      case PULL, RESTORE_SHELF, UNSTAGE, WARNING -> Color.web("#efbb55");
      case FORCE_PULL, FORCE_PUSH, DISCARD, CLOSE -> Color.web("#e65d77");
      case SHELVE, BRANCH -> Color.web("#a78ce5");
    };
  }
}
