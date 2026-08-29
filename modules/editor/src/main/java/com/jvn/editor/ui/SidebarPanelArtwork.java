package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
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
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;

/** Professional Windows 7 shell-style objects for the editor's top-level sidebar tools. */
final class SidebarPanelArtwork {
  private static final double DESIGN_SIZE = 24.0;

  private SidebarPanelArtwork() {}

  static Pane of(AeroIcon.Kind kind, double size) {
    Group art = switch (kind) {
      case PROJECT -> project();
      case TRASHMAN -> trashman();
      case STORY_MAP -> storyMap();
      case INSPECTOR -> inspector();
      case DIAGNOSTICS -> diagnostics();
      case LABEL_FLOW -> labelFlow();
      case TIMELINE_OUTLINE -> timelineOutline();
      case ASSETS -> assets();
      case LAYOUT -> layout();
      case STORYBOARD -> storyboard();
      case LAYERS -> layers();
      case IMAGE_ATTRIBUTES -> imageAttributes();
      case LIGHTING -> lighting();
      case VERSION_CONTROL -> versionControl();
      case PUPPETEER -> puppeteer();
      case SCRIPT_EDITOR -> scriptEditor();
      case SETTINGS -> settings();
      default -> throw new IllegalArgumentException("Not a sidebar panel icon: " + kind);
    };
    return finish(art, size);
  }

  private static Group project() {
    Rectangle tab = rect(3.0, 3.1, 9.2, 5.2, enamel("#ffe58a", "#b96b18"), "#6f4519", 0.75, 2.2);
    SVGPath folder = svg("M2.2 6.2 H10.8 L12.8 8.0 H21.8 V20.1 H2.2 Z",
        enamel("#ffd96d", "#b35d12"), Color.web("#6f3f14"), 0.85);
    folder.setEffect(new InnerShadow(1.25, 0, 0.7, Color.rgb(92, 43, 7, 0.52)));
    Rectangle face = rect(3.3, 9.0, 17.4, 9.8, glass("#fff1a7", "#d07c20"),
        "#fff0b5", 0.55, 1.6);
    Rectangle page = rect(6.0, 8.0, 10.8, 7.9, glass("#ffffff", "#b9d8e8"),
        "#526b79", 0.55, 1.1);
    Polygon fold = new Polygon(13.6, 8.0, 16.8, 11.1, 13.6, 11.1);
    fold.setFill(Color.web("#7ebad5"));
    Circle status = jewel(18.4, 16.6, 2.45, "#eaffef", "#45c970", "#126234");
    Line plusH = line(17.2, 16.6, 19.6, 16.6, Color.WHITE, 0.9);
    Line plusV = line(18.4, 15.4, 18.4, 17.8, Color.WHITE, 0.9);
    return group(tab, folder, face, page, fold, status, plusH, plusV, glint(5.0, 10.1, 1.5));
  }

  private static Group trashman() {
    Rectangle rear = rect(6.0, 4.3, 12.2, 16.4, metal(), "#43525d", 0.8, 2.0);
    Polygon bin = new Polygon(5.2, 7.0, 18.8, 7.0, 17.4, 21.0, 6.6, 21.0);
    bin.setFill(glass("#dff4ff", "#5b8197"));
    bin.setStroke(Color.web("#344d5c"));
    bin.setStrokeWidth(0.85);
    bin.setEffect(new InnerShadow(1.2, Color.rgb(7, 34, 48, 0.58)));
    Rectangle lid = rect(4.3, 4.8, 15.4, 3.2, metal(), "#34444f", 0.8, 1.3);
    Rectangle handle = rect(9.0, 2.7, 6.0, 2.7, metal(), "#44545e", 0.65, 1.2);
    Line slot1 = line(9.2, 9.1, 8.7, 18.6, Color.rgb(239, 250, 255, 0.62), 1.1);
    Line slot2 = line(12.0, 9.1, 12.0, 18.6, Color.rgb(239, 250, 255, 0.62), 1.1);
    Line slot3 = line(14.8, 9.1, 15.3, 18.6, Color.rgb(239, 250, 255, 0.62), 1.1);
    Circle warning = jewel(18.2, 17.8, 2.5, "#fff1ef", "#df5b61", "#7b1720");
    Line slash1 = line(17.1, 16.7, 19.3, 18.9, Color.WHITE, 0.9);
    Line slash2 = line(19.3, 16.7, 17.1, 18.9, Color.WHITE, 0.9);
    return group(rear, bin, lid, handle, slot1, slot2, slot3, warning, slash1, slash2, glint(7.2, 6.0, 1.15));
  }

  private static Group storyMap() {
    Polygon map = new Polygon(2.0, 5.0, 8.2, 3.2, 14.8, 5.0, 22.0, 3.1,
        22.0, 19.0, 15.1, 21.0, 8.1, 19.0, 2.0, 21.0);
    map.setFill(glass("#fff0b0", "#d28430"));
    map.setStroke(Color.web("#77451d"));
    map.setStrokeWidth(0.8);
    map.setStrokeLineJoin(StrokeLineJoin.ROUND);
    Line fold1 = line(8.2, 3.7, 8.1, 18.8, Color.rgb(119, 69, 29, 0.5), 0.65);
    Line fold2 = line(14.8, 5.1, 15.1, 20.4, Color.rgb(119, 69, 29, 0.5), 0.65);
    Polyline route = new Polyline(4.4, 16.6, 7.0, 12.7, 11.6, 14.0, 14.0, 9.0, 19.4, 6.8);
    route.setFill(Color.TRANSPARENT);
    route.setStroke(Color.web("#277fc0"));
    route.setStrokeWidth(1.65);
    route.setStrokeLineCap(StrokeLineCap.ROUND);
    route.setStrokeLineJoin(StrokeLineJoin.ROUND);
    Circle start = jewel(4.4, 16.6, 1.55, "#e9fff0", "#47c872", "#175d36");
    Circle mid = jewel(11.6, 14.0, 1.35, "#effaff", "#4aaada", "#164e72");
    Circle finish = jewel(19.4, 6.8, 1.7, "#fff5da", "#e87936", "#783312");
    return group(map, fold1, fold2, route, start, mid, finish, glint(4.2, 6.7, 1.5));
  }

  private static Group inspector() {
    Rectangle page = rect(3.0, 2.0, 13.8, 19.2, glass("#ffffff", "#9ec6da"),
        "#486778", 0.75, 1.7);
    Rectangle header = rect(5.0, 4.2, 9.6, 2.3, enamel("#85dcff", "#246fa8"),
        "#2d627f", 0.45, 0.8);
    Line row1 = line(5.0, 8.5, 14.1, 8.5, Color.web("#55788a"), 0.75);
    Line row2 = line(5.0, 11.3, 13.1, 11.3, Color.web("#55788a"), 0.75);
    Line row3 = line(5.0, 14.1, 11.7, 14.1, Color.web("#55788a"), 0.75);
    Circle lensRim = new Circle(15.6, 15.4, 5.0, metal());
    lensRim.setStroke(Color.web("#334d5c"));
    lensRim.setStrokeWidth(0.7);
    Circle lens = new Circle(15.6, 15.4, 3.85,
        new RadialGradient(-35, 0.28, 14.2, 13.8, 5.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(255, 255, 255, 0.9)), new Stop(0.4, Color.rgb(117, 211, 245, 0.72)),
            new Stop(1, Color.rgb(31, 92, 133, 0.86))));
    lens.setStroke(Color.rgb(225, 248, 255, 0.82));
    Line handleShadow = line(19.0, 18.9, 22.0, 21.9, Color.rgb(0, 22, 34, 0.78), 3.2);
    Line handle = line(18.7, 18.6, 21.7, 21.6, metal(), 2.1);
    return group(page, header, row1, row2, row3, lensRim, lens, handleShadow, handle, glint(14.1, 13.6, 1.15));
  }

  private static Group diagnostics() {
    Rectangle screen = rect(2.0, 3.2, 20.0, 14.5, glass("#dff8ff", "#123f61"),
        "#dff8ff", 0.8, 2.5);
    screen.setEffect(new InnerShadow(1.4, Color.rgb(2, 25, 42, 0.78)));
    Polyline pulse = new Polyline(4.0, 11.0, 7.0, 11.0, 8.5, 7.7, 10.5, 14.2,
        13.0, 9.0, 15.0, 11.0, 20.0, 11.0);
    pulse.setFill(Color.TRANSPARENT);
    pulse.setStroke(Color.web("#6cff8e"));
    pulse.setStrokeWidth(1.65);
    pulse.setStrokeLineJoin(StrokeLineJoin.ROUND);
    pulse.setEffect(new DropShadow(1.6, Color.web("#34e969")));
    Rectangle stem = rect(10.2, 17.5, 3.6, 2.3, metal(), Color.TRANSPARENT, 0, 0.2);
    Ellipse foot = new Ellipse(12.0, 20.5, 6.4, 1.2);
    foot.setFill(metal());
    foot.setStroke(Color.web("#607481"));
    foot.setStrokeWidth(0.55);
    Circle ok = jewel(19.3, 5.6, 1.4, "#edfff2", "#47cf73", "#145b35");
    return group(stem, foot, screen, pulse, ok, glint(5.0, 5.5, 1.4));
  }

  private static Group labelFlow() {
    Line path1 = line(7.0, 6.5, 12.2, 11.8, Color.web("#47798e"), 1.25);
    Line path2 = line(12.2, 11.8, 17.3, 7.0, Color.web("#47798e"), 1.25);
    Line path3 = line(12.2, 11.8, 17.3, 17.3, Color.web("#47798e"), 1.25);
    Polygon tag1 = tag(2.0, 3.1, "#83efad", "#278653");
    Polygon tag2 = tag(13.2, 3.7, "#71d7ff", "#24699b");
    Polygon tag3 = tag(13.2, 14.0, "#ffe083", "#bb6f24");
    Circle node = jewel(12.0, 11.8, 2.0, "#f3fffa", "#43c87a", "#145d3a");
    Circle h1 = new Circle(5.0, 6.6, 0.75, Color.web("#f4fff8"));
    Circle h2 = new Circle(16.2, 7.2, 0.75, Color.web("#f5fcff"));
    Circle h3 = new Circle(16.2, 17.5, 0.75, Color.web("#fff9e7"));
    return group(path1, path2, path3, tag1, tag2, tag3, node, h1, h2, h3, glint(10.9, 10.6, 0.8));
  }

  private static Group timelineOutline() {
    Rectangle panel = rect(2.5, 3.0, 19.0, 18.0, glass("#f8fcff", "#718b9b"),
        "#3c515f", 0.75, 2.0);
    Rectangle rail = rect(4.0, 5.0, 4.3, 14.0, enamel("#8cdcff", "#286f9b"),
        "#315f78", 0.45, 1.0);
    Line spine = line(6.15, 6.6, 6.15, 17.4, Color.web("#eafaff"), 0.85);
    Circle d1 = jewel(6.15, 7.0, 0.85, "#ffffff", "#63c8eb", "#1b5673");
    Circle d2 = jewel(6.15, 10.2, 0.85, "#ffffff", "#63c8eb", "#1b5673");
    Circle d3 = jewel(6.15, 13.4, 0.85, "#ffffff", "#63c8eb", "#1b5673");
    Circle d4 = jewel(6.15, 16.6, 0.85, "#ffffff", "#63c8eb", "#1b5673");
    Line r1 = line(10.0, 7.0, 18.8, 7.0, Color.web("#425e6e"), 0.9);
    Line r2 = line(10.0, 10.2, 17.1, 10.2, Color.web("#425e6e"), 0.9);
    Line r3 = line(10.0, 13.4, 19.1, 13.4, Color.web("#425e6e"), 0.9);
    Line r4 = line(10.0, 16.6, 15.8, 16.6, Color.web("#425e6e"), 0.9);
    return group(panel, rail, spine, d1, d2, d3, d4, r1, r2, r3, r4, glint(4.6, 4.4, 1.0));
  }

  private static Group assets() {
    Rectangle tab = rect(2.8, 4.0, 9.2, 4.4, enamel("#ffe587", "#b86a19"),
        "#754517", 0.65, 1.8);
    Rectangle folder = rect(2.0, 7.0, 20.0, 13.5, glass("#ffe58d", "#bc6819"),
        "#714014", 0.8, 2.0);
    Rectangle backPhoto = rect(6.0, 5.0, 13.2, 11.0, metal(), "#445864", 0.65, 1.2);
    backPhoto.setRotate(-7);
    Rectangle photo = rect(6.2, 7.0, 14.2, 11.2, glass("#ecfbff", "#69acd0"),
        "#365d73", 0.65, 1.1);
    Polygon mountain = new Polygon(7.6, 16.6, 11.3, 11.5, 14.0, 14.3, 16.3, 11.9, 19.0, 16.6);
    mountain.setFill(enamel("#72c678", "#2c7242"));
    Circle sun = jewel(17.4, 9.9, 1.35, "#fff9dc", "#f0b64a", "#9c581c");
    return group(tab, folder, backPhoto, photo, mountain, sun, glint(7.6, 8.3, 1.0));
  }

  private static Group layout() {
    Rectangle window = rect(2.0, 2.5, 20.0, 19.0, glass("#f1fbff", "#4c83a5"),
        "#304c5e", 0.8, 2.0);
    Rectangle title = rect(3.3, 3.8, 17.4, 3.0, enamel("#8de0ff", "#2b75a5"),
        "#d9f7ff", 0.4, 1.0);
    Rectangle left = rect(3.8, 8.0, 5.3, 11.7, glass("#dcebf2", "#748d9c"),
        "#536977", 0.45, 0.9);
    Rectangle stage = rect(10.2, 8.0, 10.0, 7.1, glass("#ffffff", "#a6d1e6"),
        "#4f7285", 0.45, 0.9);
    Rectangle footer = rect(10.2, 16.2, 10.0, 3.5, glass("#dbeaf1", "#7d96a4"),
        "#536977", 0.45, 0.9);
    Line gridV = line(15.2, 8.2, 15.2, 14.8, Color.rgb(60, 117, 148, 0.48), 0.5);
    Line gridH = line(10.4, 11.55, 20.0, 11.55, Color.rgb(60, 117, 148, 0.48), 0.5);
    Circle control1 = jewel(18.7, 5.2, 0.72, "#fff2ef", "#df5b61", "#7b1720");
    Circle control2 = jewel(16.5, 5.2, 0.72, "#fff9dc", "#e8b44d", "#88521b");
    return group(window, title, left, stage, footer, gridV, gridH, control1, control2, glint(4.5, 4.7, 0.9));
  }

  private static Group storyboard() {
    Rectangle body = rect(2.5, 7.5, 19.0, 13.3, glass("#f9fbfc", "#6e7e88"),
        "#303d45", 0.8, 1.7);
    Rectangle screen = rect(4.3, 10.0, 15.4, 8.5, glass("#dff6ff", "#356b8b"),
        "#d9f6ff", 0.5, 0.9);
    Rectangle clapper = rect(2.2, 3.3, 19.7, 5.0, metal(), "#303d45", 0.8, 1.2);
    clapper.setRotate(-5);
    Polygon play = new Polygon(9.6, 11.5, 15.0, 14.2, 9.6, 16.9);
    play.setFill(enamel("#ffbc61", "#bf531e"));
    play.setStroke(Color.web("#fff0ca"));
    play.setStrokeWidth(0.55);
    Group stripes = new Group();
    for (int i = 0; i < 5; i++) {
      Polygon stripe = new Polygon(2.8 + i * 4.0, 3.0, 4.8 + i * 4.0, 3.0,
          2.5 + i * 4.0, 8.0, 0.5 + i * 4.0, 8.0);
      stripe.setFill(i % 2 == 0 ? Color.web("#26343d") : Color.web("#f3f7f9"));
      stripes.getChildren().add(stripe);
    }
    stripes.setRotate(-5);
    return group(body, screen, clapper, stripes, play, glint(5.0, 9.3, 1.0));
  }

  private static Group layers() {
    Polygon bottom = sheet(3.0, 12.0, "#7d93a4", "#344957");
    Polygon middle = sheet(3.0, 8.0, "#8fd6ef", "#2e7191");
    Polygon top = sheet(3.0, 4.0, "#d5effa", "#4f8299");
    Rectangle thumbnail = rect(7.2, 6.6, 9.6, 4.6, glass("#f7fdff", "#6fafd0"),
        "#3c6a80", 0.45, 0.7);
    Polygon mountain = new Polygon(8.0, 10.5, 10.3, 7.7, 12.0, 9.2, 13.6, 7.4, 16.1, 10.5);
    mountain.setFill(enamel("#79c983", "#347848"));
    Circle pin = jewel(18.8, 5.2, 1.45, "#fff1fb", "#d76bb3", "#78305f");
    return group(bottom, middle, top, thumbnail, mountain, pin, glint(5.3, 5.5, 1.1));
  }

  private static Group imageAttributes() {
    Rectangle frame = rect(2.0, 3.0, 14.5, 15.8, metal(), "#354752", 0.75, 1.7);
    Rectangle image = rect(3.7, 4.7, 11.1, 12.3, glass("#e8fbff", "#63a9cd"),
        "#476b7d", 0.45, 0.8);
    Polygon mountain = new Polygon(4.3, 16.2, 7.3, 11.1, 9.5, 13.7, 11.7, 10.5, 14.2, 16.2);
    mountain.setFill(enamel("#7acb80", "#347743"));
    Circle sun = jewel(12.5, 7.2, 1.2, "#fff9d9", "#efb34b", "#935119");
    Rectangle controls = rect(15.0, 5.0, 7.0, 15.3, glass("#f7fbfd", "#7f98a6"),
        "#435865", 0.6, 1.3);
    Line s1 = line(16.4, 8.0, 20.7, 8.0, Color.web("#445f70"), 0.75);
    Line s2 = line(16.4, 12.2, 20.7, 12.2, Color.web("#445f70"), 0.75);
    Line s3 = line(16.4, 16.4, 20.7, 16.4, Color.web("#445f70"), 0.75);
    Circle k1 = jewel(18.8, 8.0, 1.0, "#effaff", "#58bfe8", "#1e607f");
    Circle k2 = jewel(17.4, 12.2, 1.0, "#fff4d8", "#e99a45", "#854619");
    Circle k3 = jewel(19.8, 16.4, 1.0, "#f2eaff", "#a57ad9", "#52337b");
    return group(frame, image, mountain, sun, controls, s1, s2, s3, k1, k2, k3, glint(4.6, 4.2, 1.0));
  }

  private static Group lighting() {
    Ellipse pool = new Ellipse(11.5, 20.5, 9.3, 1.6);
    pool.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.7, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.rgb(255, 229, 139, 0.72)), new Stop(1, Color.TRANSPARENT)));
    Line arm1Shadow = line(6.0, 18.2, 9.7, 10.7, Color.rgb(32, 22, 26, 0.8), 3.2);
    Line arm1 = line(6.0, 17.7, 9.7, 10.2, metal(), 2.0);
    Line arm2Shadow = line(9.7, 10.7, 15.3, 7.0, Color.rgb(32, 22, 26, 0.8), 3.2);
    Line arm2 = line(9.7, 10.2, 15.3, 6.5, metal(), 2.0);
    Ellipse base = new Ellipse(6.0, 19.4, 4.3, 1.8);
    base.setFill(metal());
    base.setStroke(Color.web("#45545e"));
    base.setStrokeWidth(0.65);
    Polygon shade = new Polygon(13.0, 4.0, 21.0, 7.6, 17.1, 13.0, 10.0, 9.7);
    shade.setFill(enamel("#f5a8d6", "#963f83"));
    shade.setStroke(Color.web("#662853"));
    shade.setStrokeWidth(0.8);
    Circle bulb = new Circle(16.0, 9.0, 1.6, new RadialGradient(0, 0, 0.4, 0.35, 0.8, true,
        CycleMethod.NO_CYCLE, new Stop(0, Color.WHITE), new Stop(0.45, Color.web("#fff0a7")),
        new Stop(1, Color.web("#e7a63d"))));
    bulb.setEffect(new DropShadow(3.0, Color.web("#ffd96b")));
    Line ray1 = line(18.7, 12.5, 21.0, 15.2, Color.web("#ffd96b"), 0.75);
    Line ray2 = line(15.9, 13.1, 15.9, 16.4, Color.web("#ffd96b"), 0.75);
    return group(pool, arm1Shadow, arm1, arm2Shadow, arm2, base, shade, bulb, ray1, ray2, glint(13.0, 5.6, 0.9));
  }

  private static Group versionControl() {
    Line trunk = line(7.0, 5.0, 7.0, 18.7, Color.web("#d9f4e1"), 1.8);
    Polyline branch = new Polyline(7.0, 9.0, 12.0, 9.0, 15.5, 12.2, 15.5, 16.7);
    branch.setFill(Color.TRANSPARENT);
    branch.setStroke(Color.web("#c8b4f4"));
    branch.setStrokeWidth(1.8);
    branch.setStrokeLineCap(StrokeLineCap.ROUND);
    branch.setStrokeLineJoin(StrokeLineJoin.ROUND);
    Circle n1 = jewel(7.0, 5.0, 2.0, "#ecfff2", "#55d17d", "#17633a");
    Circle n2 = jewel(7.0, 13.0, 2.0, "#ecfff2", "#55d17d", "#17633a");
    Circle n3 = jewel(15.5, 16.7, 2.0, "#f1eaff", "#9873d5", "#51347a");
    Circle globeRim = new Circle(18.3, 6.2, 4.0, metal());
    globeRim.setStroke(Color.web("#3d505d"));
    globeRim.setStrokeWidth(0.65);
    Circle globe = new Circle(18.3, 6.2, 3.1,
        new RadialGradient(-35, 0.25, 17.0, 5.0, 4.0, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#e9fbff")), new Stop(0.38, Color.web("#69c9ec")),
            new Stop(1, Color.web("#1e6595"))));
    globe.setStroke(Color.web("#dff8ff"));
    globe.setStrokeWidth(0.45);
    Line lat = line(15.6, 6.2, 21.0, 6.2, Color.rgb(235, 252, 255, 0.7), 0.45);
    Ellipse lon = new Ellipse(18.3, 6.2, 1.25, 3.0);
    lon.setFill(Color.TRANSPARENT);
    lon.setStroke(Color.rgb(235, 252, 255, 0.68));
    lon.setStrokeWidth(0.45);
    return group(trunk, branch, n1, n2, n3, globeRim, globe, lat, lon, glint(16.8, 4.6, 0.8));
  }

  private static Group puppeteer() {
    Rectangle control = rect(4.0, 3.0, 16.0, 2.7, enamel("#f0b16d", "#8b481f"),
        "#663518", 0.65, 1.1);
    Rectangle grip = rect(10.7, 1.5, 2.6, 5.0, metal(), "#4a5962", 0.55, 0.9);
    Line string1 = line(6.5, 5.4, 8.5, 12.0, Color.rgb(230, 241, 246, 0.84), 0.55);
    Line string2 = line(17.5, 5.4, 15.5, 12.0, Color.rgb(230, 241, 246, 0.84), 0.55);
    Line string3 = line(12.0, 5.4, 12.0, 10.0, Color.rgb(230, 241, 246, 0.84), 0.55);
    Circle head = jewel(12.0, 11.5, 2.5, "#ffe7f5", "#d46cad", "#71315f");
    Polygon body = new Polygon(8.9, 14.1, 15.1, 14.1, 16.8, 20.6, 7.2, 20.6);
    body.setFill(enamel("#d998c4", "#7e3b72"));
    body.setStroke(Color.web("#54284c"));
    body.setStrokeWidth(0.75);
    Line arm1 = line(8.6, 15.1, 5.5, 18.0, metal(), 1.45);
    Line arm2 = line(15.4, 15.1, 18.5, 18.0, metal(), 1.45);
    Circle joint1 = jewel(8.5, 15.0, 0.8, "#fff4fa", "#d879b6", "#6b3159");
    Circle joint2 = jewel(15.5, 15.0, 0.8, "#fff4fa", "#d879b6", "#6b3159");
    return group(grip, control, string1, string2, string3, arm1, arm2, body, head,
        joint1, joint2, glint(7.0, 3.8, 0.8));
  }

  private static Group scriptEditor() {
    Rectangle page = rect(3.0, 2.0, 15.4, 19.5, glass("#ffffff", "#9bc7dc"),
        "#476778", 0.75, 1.7);
    Polygon fold = new Polygon(14.0, 2.0, 18.4, 6.5, 14.0, 6.5);
    fold.setFill(enamel("#a7daf0", "#4e8dac"));
    Polyline left = new Polyline(8.0, 8.2, 5.7, 11.0, 8.0, 13.8);
    Polyline right = new Polyline(11.4, 8.2, 13.7, 11.0, 11.4, 13.8);
    for (Polyline bracket : new Polyline[] {left, right}) {
      bracket.setFill(Color.TRANSPARENT);
      bracket.setStroke(Color.web("#347fa7"));
      bracket.setStrokeWidth(1.25);
      bracket.setStrokeLineCap(StrokeLineCap.ROUND);
      bracket.setStrokeLineJoin(StrokeLineJoin.ROUND);
    }
    Rectangle penBody = rect(16.3, 8.0, 3.1, 12.8, enamel("#77d6f5", "#236d9b"),
        "#264e65", 0.55, 1.0);
    penBody.getTransforms().add(new Rotate(38, 17.85, 14.4));
    Polygon nib = new Polygon(19.0, 19.2, 21.5, 21.6, 17.8, 22.0);
    nib.setFill(metal());
    nib.setStroke(Color.web("#46555e"));
    nib.setStrokeWidth(0.5);
    return group(page, fold, left, right, penBody, nib, glint(5.1, 4.0, 1.1));
  }

  private static Group settings() {
    Rectangle chassis = rect(1.8, 3.0, 18.6, 16.9, metal(), "#354a58", 0.8, 2.4);
    Rectangle panel = rect(3.0, 4.2, 16.2, 14.4, glass("#eafaff", "#367ba6"),
        "#dff7ff", 0.55, 1.7);
    panel.setEffect(new InnerShadow(1.25, Color.rgb(9, 48, 75, 0.72)));
    Rectangle titleBar = rect(3.8, 5.0, 14.6, 2.3, enamel("#91ddff", "#2675a7"),
        "#dff7ff", 0.35, 0.9);

    Line track1 = line(5.0, 9.4, 17.2, 9.4, Color.web("#174d70"), 1.05);
    Line track2 = line(5.0, 12.6, 17.2, 12.6, Color.web("#174d70"), 1.05);
    Line track3 = line(5.0, 15.8, 17.2, 15.8, Color.web("#174d70"), 1.05);
    Circle knob1 = jewel(8.0, 9.4, 1.3, "#fff7df", "#f0a74f", "#884115");
    Circle knob2 = jewel(13.9, 12.6, 1.3, "#effff3", "#55ca78", "#17603a");
    Circle knob3 = jewel(10.5, 15.8, 1.3, "#f1faff", "#52bce7", "#175a81");

    Shape gear = new Circle(18.0, 17.0, 3.0);
    for (int i = 0; i < 8; i++) {
      Rectangle tooth = new Rectangle(17.1, 12.3, 1.8, 2.8);
      tooth.getTransforms().add(new Rotate(i * 45.0, 18.0, 17.0));
      gear = Shape.union(gear, tooth);
    }
    gear = Shape.subtract(gear, new Circle(18.0, 17.0, 1.45));
    gear.setFill(metal());
    gear.setStroke(Color.web("#354650"));
    gear.setStrokeWidth(0.6);
    gear.setEffect(new DropShadow(1.3, 0, 0.7, Color.rgb(0, 0, 0, 0.82)));
    Circle gearCore = jewel(18.0, 17.0, 1.35, "#eefaff", "#4aadd8", "#174f73");

    return group(chassis, panel, titleBar, track1, track2, track3, knob1, knob2, knob3,
        gear, gearCore, glint(4.4, 4.8, 0.9));
  }

  private static Pane finish(Group art, double requestedSize) {
    double size = Math.max(10.0, requestedSize);
    double scale = size / DESIGN_SIZE;
    art.getTransforms().add(new Scale(scale, scale, 0, 0));
    Pane pane = new Pane(art);
    pane.setMinSize(size, size);
    pane.setPrefSize(size, size);
    pane.setMaxSize(size, size);
    pane.setPickOnBounds(false);
    pane.setMouseTransparent(true);
    pane.getStyleClass().add("jvn-sidebar-panel-bespoke-artwork");
    pane.setEffect(new DropShadow(Math.max(1.4, size * 0.08), 0, Math.max(0.8, size * 0.04),
        Color.rgb(0, 0, 0, 0.82)));
    pane.setCache(true);
    pane.setCacheHint(CacheHint.SPEED);
    return pane;
  }

  private static Group group(Node... nodes) {
    return new Group(nodes);
  }

  private static Polygon tag(double x, double y, String top, String bottom) {
    Polygon tag = new Polygon(x, y, x + 6.0, y, x + 9.0, y + 3.5, x + 6.0, y + 7.0, x, y + 7.0);
    tag.setFill(enamel(top, bottom));
    tag.setStroke(Color.web(bottom).darker());
    tag.setStrokeWidth(0.6);
    tag.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return tag;
  }

  private static Polygon sheet(double x, double y, String top, String bottom) {
    Polygon sheet = new Polygon(x, y, 12.0, y - 4.0, 21.0, y, 12.0, y + 4.0);
    sheet.setFill(enamel(top, bottom));
    sheet.setStroke(Color.web(bottom).darker());
    sheet.setStrokeWidth(0.7);
    sheet.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return sheet;
  }

  private static Circle jewel(double x, double y, double radius, String highlight, String middle, String edge) {
    Circle circle = new Circle(x, y, radius,
        new RadialGradient(-35, 0.28, x - radius * 0.35, y - radius * 0.38, radius * 1.25,
            false, CycleMethod.NO_CYCLE, new Stop(0, Color.web(highlight)),
            new Stop(0.43, Color.web(middle)), new Stop(1, Color.web(edge))));
    circle.setStroke(Color.rgb(255, 255, 255, 0.82));
    circle.setStrokeWidth(Math.max(0.35, radius * 0.22));
    circle.setEffect(new DropShadow(Math.max(0.8, radius * 0.45), Color.rgb(0, 0, 0, 0.72)));
    return circle;
  }

  private static Circle glint(double x, double y, double radius) {
    Circle glint = new Circle(x, y, radius, Color.rgb(255, 255, 255, 0.58));
    glint.setEffect(new DropShadow(radius * 1.2, Color.rgb(255, 255, 255, 0.42)));
    return glint;
  }

  private static Rectangle rect(double x, double y, double width, double height,
      Paint fill, Paint stroke, double strokeWidth, double radius) {
    Rectangle rectangle = new Rectangle(x, y, width, height);
    rectangle.setArcWidth(radius * 2.0);
    rectangle.setArcHeight(radius * 2.0);
    rectangle.setFill(fill);
    rectangle.setStroke(stroke);
    rectangle.setStrokeWidth(strokeWidth);
    return rectangle;
  }

  private static Rectangle rect(double x, double y, double width, double height,
      Paint fill, String stroke, double strokeWidth, double radius) {
    return rect(x, y, width, height, fill, Color.web(stroke), strokeWidth, radius);
  }

  private static Line line(double x1, double y1, double x2, double y2, Paint color, double width) {
    Line line = new Line(x1, y1, x2, y2);
    line.setStroke(color);
    line.setStrokeWidth(width);
    line.setStrokeLineCap(StrokeLineCap.ROUND);
    return line;
  }

  private static SVGPath svg(String content, Paint fill, Paint stroke, double width) {
    SVGPath path = new SVGPath();
    path.setContent(content);
    path.setFill(fill);
    path.setStroke(stroke);
    path.setStrokeWidth(width);
    path.setStrokeLineJoin(StrokeLineJoin.ROUND);
    return path;
  }

  private static Paint enamel(String top, String bottom) {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(top).brighter()), new Stop(0.18, Color.web(top)),
        new Stop(0.53, Color.web(bottom).brighter()), new Stop(1, Color.web(bottom).darker()));
  }

  private static Paint metal() {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE), new Stop(0.17, Color.web("#eaf1f5")),
        new Stop(0.48, Color.web("#8796a0")), new Stop(0.70, Color.web("#d9e2e7")),
        new Stop(1, Color.web("#56656f")));
  }

  private static Paint glass(String top, String bottom) {
    return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(top)), new Stop(0.18, Color.web(top).deriveColor(0, 0.75, 1.0, 0.92)),
        new Stop(0.42, Color.web(bottom).brighter()), new Stop(1, Color.web(bottom)));
  }

}
