package com.jvn.editor.ui;

import javafx.scene.control.ButtonBase;
import javafx.scene.CacheHint;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Rotate;

/** Dimensional glass-and-metal icon used by spacious editor command surfaces. */
public final class AeroIcon extends StackPane {
  public enum Kind {
    PROJECT, TRASHMAN, STORY_MAP, INSPECTOR, DIAGNOSTICS, LABEL_FLOW, ASSETS,
    LAYOUT, STORYBOARD, LAYERS, IMAGE_ATTRIBUTES, LIGHTING, VERSION_CONTROL,
    PUPPETEER, SCRIPT_EDITOR, SETTINGS,
    NEW_PROJECT, OPEN_PROJECT, RUN, BUILD, REFRESH, ENTRY_SCRIPT, MANIFEST,
    README, DOCUMENTATION, REVEAL, ARROW_BACK, HELP, NO_PROJECT
  }

  private record Palette(Color top, Color bottom, Color edge) {}
  private record Badge(Region glyph, Color color) {}

  private final Kind kind;
  private final double iconSize;

  private AeroIcon(Kind kind, double size) {
    this.kind = kind == null ? Kind.PROJECT : kind;
    this.iconSize = clampSize(size);
    Region glyph = glyphFor(this.kind, Math.max(10, iconSize * 0.72));
    glyph.setMouseTransparent(true);

    setMinSize(iconSize, iconSize);
    setPrefSize(iconSize, iconSize);
    setMaxSize(iconSize, iconSize);
    setPickOnBounds(false);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("jvn-aero-icon");
    getChildren().setAll(glyph);
    parentProperty().addListener((obs, oldParent, newParent) -> {
      if (newParent instanceof ButtonBase button) installButtonTreatment(button);
    });
  }

  public static AeroIcon of(Kind kind, double size) {
    return new AeroIcon(kind, size);
  }

  public Kind kind() {
    return kind;
  }

  public double iconSize() {
    return iconSize;
  }

  private void installButtonTreatment(ButtonBase button) {
    if (!button.getStyleClass().contains("aero-icon-button")) {
      button.getStyleClass().add("aero-icon-button");
    }
    button.hoverProperty().addListener((obs, wasHovered, hovered) -> updateButtonEffect(button, hovered));
    button.pressedProperty().addListener((obs, wasPressed, pressed) -> updateButtonEffect(button, button.isHover()));
    updateButtonEffect(button, button.isHover());
  }

  private void updateButtonEffect(ButtonBase button, boolean hovered) {
    if (button.isPressed()) {
      setScaleX(0.94);
      setScaleY(0.94);
      setEffect(new DropShadow(Math.max(3, iconSize * 0.16), paletteFor(kind).bottom()));
    } else if (hovered) {
      setScaleX(1.04);
      setScaleY(1.04);
      setEffect(new DropShadow(Math.max(7, iconSize * 0.34), paletteFor(kind).top()));
    } else {
      setScaleX(1.0);
      setScaleY(1.0);
      setEffect(null);
    }
  }

  private static Region glyphFor(Kind kind, double size) {
    Palette palette = paletteFor(kind);
    String color = toCss(mix(palette.top(), palette.edge(), 0.34));
    Region glyph = switch (kind) {
      case PROJECT, ASSETS, OPEN_PROJECT, DOCUMENTATION, REVEAL, NEW_PROJECT ->
          CssIcon.folder(color, size);
      case TRASHMAN -> sized(CssIcon.delete(color), size);
      case STORY_MAP -> sized(CssIcon.timeline(color), size);
      case INSPECTOR -> sized(CssIcon.search(color), size);
      case DIAGNOSTICS -> diagnosticsGlyph(size);
      case LABEL_FLOW, VERSION_CONTROL -> sized(CssIcon.branchPlus(color), size);
      case LAYOUT -> sized(CssIcon.rectSelect(color), size);
      case STORYBOARD -> sized(CssIcon.movie(color), size);
      case LAYERS -> sized(CssIcon.copy(color), size);
      case IMAGE_ATTRIBUTES -> sized(CssIcon.landscape(color), size);
      case SCRIPT_EDITOR -> sized(CssIcon.document(color), size);
      case LIGHTING -> sized(CssIcon.lightbulb(color), size);
      case PUPPETEER -> sized(CssIcon.theater(color), size);
      case SETTINGS -> settingsGlyph(color, size);
      case RUN -> sized(CssIcon.play(color), size);
      case BUILD -> sized(CssIcon.download(color), size);
      case REFRESH -> sized(CssIcon.redo(color), size);
      case ENTRY_SCRIPT -> sized(CssIcon.speech(color), size);
      case MANIFEST, README -> sized(CssIcon.document(color), size);
      case ARROW_BACK -> sized(CssIcon.arrowLeft(color), size);
      case HELP -> helpGlyph(size);
      case NO_PROJECT -> noProjectGlyph(size);
    };
    if (kind != Kind.HELP && kind != Kind.NO_PROJECT) glyph = decorate(kind, glyph, size, palette);
    DropShadow depth = new DropShadow(Math.max(2, size * 0.14), 0, Math.max(1, size * 0.08),
        Color.rgb(0, 0, 0, 0.82));
    depth.setInput(glyph.getEffect());
    glyph.setEffect(depth);
    glyph.setCache(true);
    glyph.setCacheHint(CacheHint.SPEED);
    return glyph;
  }

  private static Region decorate(Kind kind, Region base, double size, Palette palette) {
    Badge badge = badgeFor(kind, size);
    StackPane artwork = new StackPane(base);
    artwork.setMinSize(size, size);
    artwork.setPrefSize(size, size);
    artwork.setMaxSize(size, size);

    Circle glint = new Circle(size * 0.055, Color.rgb(255, 255, 255, 0.78));
    glint.setEffect(new DropShadow(size * 0.08, Color.rgb(255, 255, 255, 0.52)));
    StackPane.setAlignment(glint, Pos.TOP_LEFT);
    StackPane.setMargin(glint, new Insets(size * 0.16, 0, 0, size * 0.19));
    artwork.getChildren().add(glint);

    double badgeSize = Math.max(7, size * 0.39);
    Circle rim = new Circle(badgeSize / 2.0);
    rim.setFill(new RadialGradient(
        0, 0, 0.34, 0.27, 0.88, true, CycleMethod.NO_CYCLE,
        new Stop(0, badge.color().deriveColor(0, 0.52, 1.42, 1)),
        new Stop(0.58, badge.color()),
        new Stop(1, badge.color().deriveColor(0, 1, 0.42, 1))));
    rim.setStroke(Color.rgb(255, 255, 255, 0.82));
    rim.setStrokeWidth(Math.max(0.55, size * 0.032));
    rim.setEffect(new DropShadow(Math.max(1.4, size * 0.11), 0, size * 0.04,
        Color.rgb(0, 0, 0, 0.82)));

    Region badgeGlyph = badge.glyph();
    StackPane jewel = new StackPane(rim, badgeGlyph);
    jewel.setMinSize(badgeSize, badgeSize);
    jewel.setPrefSize(badgeSize, badgeSize);
    jewel.setMaxSize(badgeSize, badgeSize);
    jewel.setEffect(new InnerShadow(Math.max(0.5, size * 0.035), Color.rgb(255, 255, 255, 0.36)));
    StackPane.setAlignment(jewel, Pos.BOTTOM_RIGHT);
    StackPane.setMargin(jewel, new Insets(0, -size * 0.04, -size * 0.03, 0));
    artwork.getChildren().add(jewel);
    return artwork;
  }

  private static Badge badgeFor(Kind kind, double size) {
    double glyphSize = Math.max(5, size * 0.21);
    return switch (kind) {
      case PROJECT -> new Badge(sized(CssIcon.home("#ffffff"), glyphSize), Color.web("#4b82b1"));
      case TRASHMAN -> new Badge(sized(CssIcon.clearX("#ffffff"), glyphSize), Color.web("#c44b55"));
      case STORY_MAP -> new Badge(sized(CssIcon.arrowRight("#ffffff"), glyphSize), Color.web("#d16c25"));
      case INSPECTOR -> new Badge(sized(CssIcon.info("#ffffff"), glyphSize), Color.web("#288fc5"));
      case DIAGNOSTICS -> new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#27a45a"));
      case LABEL_FLOW -> new Badge(sized(CssIcon.arrowRight("#ffffff"), glyphSize), Color.web("#2c9a5a"));
      case NEW_PROJECT -> new Badge(sized(CssIcon.plusBold("#ffffff"), glyphSize), Color.web("#28a75d"));
      case OPEN_PROJECT -> new Badge(sized(CssIcon.arrowRight("#ffffff"), glyphSize), Color.web("#2689d8"));
      case ASSETS -> new Badge(sized(CssIcon.landscape("#ffffff"), glyphSize), Color.web("#298ac4"));
      case LAYOUT -> new Badge(sized(CssIcon.grid("#ffffff"), glyphSize), Color.web("#4d7ea5"));
      case STORYBOARD -> new Badge(sized(CssIcon.play("#ffffff"), glyphSize), Color.web("#d16c25"));
      case LAYERS -> new Badge(sized(CssIcon.plusBold("#ffffff"), glyphSize), Color.web("#587fa2"));
      case DOCUMENTATION -> new Badge(sized(CssIcon.help("#ffffff"), glyphSize), Color.web("#397cc0"));
      case REVEAL -> new Badge(sized(CssIcon.popOut("#ffffff"), glyphSize), Color.web("#397cc0"));
      case IMAGE_ATTRIBUTES, SCRIPT_EDITOR ->
          new Badge(sized(CssIcon.edit("#ffffff"), glyphSize), Color.web("#168dbf"));
      case VERSION_CONTROL -> new Badge(sized(CssIcon.download("#ffffff"), glyphSize), Color.web("#2c9a5a"));
      case MANIFEST -> new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#35a45c"));
      case README -> new Badge(sized(CssIcon.list("#ffffff"), glyphSize), Color.web("#4b7ba6"));
      case ENTRY_SCRIPT -> new Badge(sized(CssIcon.play("#ffffff"), glyphSize), Color.web("#2fa35a"));
      case BUILD -> new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#2e9b50"));
      case LIGHTING, PUPPETEER ->
          new Badge(sized(CssIcon.sparkles("#ffffff"), glyphSize), Color.web("#b54f95"));
      case SETTINGS -> new Badge(sized(CssIcon.auto("#ffffff"), glyphSize), Color.web("#d27128"));
      case RUN -> new Badge(sized(CssIcon.rocket("#ffffff"), glyphSize), Color.web("#d16c25"));
      case REFRESH -> new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#2789c5"));
      case ARROW_BACK -> new Badge(sized(CssIcon.home("#ffffff"), glyphSize), Color.web("#c76328"));
      case HELP -> new Badge(sized(CssIcon.questionMark("#ffffff"), glyphSize), Color.web("#397cc0"));
      case NO_PROJECT -> new Badge(sized(CssIcon.minus("#ffffff"), glyphSize), Color.web("#c97b2d"));
    };
  }

  private static Region sized(Region glyph, double size) {
    double scale = size / Math.max(1, glyph.getPrefWidth());
    glyph.setScaleX(scale);
    glyph.setScaleY(scale);
    return glyph;
  }

  private static Region settingsGlyph(String color, double size) {
    double center = size / 2.0;
    Shape gear = new Circle(center, center, size * 0.31);
    for (int i = 0; i < 8; i++) {
      Rectangle tooth = new Rectangle(
          center - size * 0.075, size * 0.075, size * 0.15, size * 0.27);
      tooth.getTransforms().add(new Rotate(i * 45.0, center, center));
      gear = Shape.union(gear, tooth);
    }
    gear = Shape.subtract(gear, new Circle(center, center, size * 0.115));
    gear.setFill(Color.web(color));
    gear.setStroke(Color.web(color).deriveColor(0, 0.72, 0.68, 0.9));
    gear.setStrokeWidth(Math.max(0.45, size * 0.025));
    StackPane wrapper = new StackPane(gear);
    wrapper.setMinSize(size, size);
    wrapper.setPrefSize(size, size);
    wrapper.setMaxSize(size, size);
    return wrapper;
  }

  private static Region diagnosticsGlyph(double size) {
    Pane artwork = new Pane();
    artwork.setMinSize(size, size);
    artwork.setPrefSize(size, size);
    artwork.setMaxSize(size, size);

    Rectangle screen = new Rectangle(size * 0.07, size * 0.10, size * 0.86, size * 0.62);
    screen.setArcWidth(size * 0.16);
    screen.setArcHeight(size * 0.16);
    screen.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#e8f7ff")),
        new Stop(0.18, Color.web("#65c9ed")),
        new Stop(0.30, Color.web("#153e61")),
        new Stop(1, Color.web("#071b2b"))));
    screen.setStroke(Color.web("#dff7ff"));
    screen.setStrokeWidth(Math.max(0.7, size * 0.045));
    screen.setEffect(new DropShadow(Math.max(1.4, size * 0.11), 0, size * 0.05,
        Color.rgb(0, 0, 0, 0.82)));

    Rectangle reflection = new Rectangle(size * 0.13, size * 0.16, size * 0.52, size * 0.09);
    reflection.setArcWidth(size * 0.08);
    reflection.setArcHeight(size * 0.08);
    reflection.setFill(Color.rgb(255, 255, 255, 0.38));

    Polyline pulse = new Polyline(
        size * 0.15, size * 0.47,
        size * 0.31, size * 0.47,
        size * 0.39, size * 0.34,
        size * 0.48, size * 0.60,
        size * 0.59, size * 0.40,
        size * 0.67, size * 0.47,
        size * 0.84, size * 0.47);
    pulse.setFill(Color.TRANSPARENT);
    pulse.setStroke(Color.web("#72ff8f"));
    pulse.setStrokeWidth(Math.max(1.1, size * 0.075));
    pulse.setEffect(new DropShadow(Math.max(1.2, size * 0.09), Color.web("#38ff70")));

    Rectangle stem = new Rectangle(size * 0.43, size * 0.70, size * 0.14, size * 0.13);
    stem.setFill(new LinearGradient(
        0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#677987")),
        new Stop(0.48, Color.web("#f2fbff")),
        new Stop(1, Color.web("#566775"))));
    Ellipse foot = new Ellipse(size * 0.50, size * 0.86, size * 0.28, size * 0.075);
    foot.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#f5fbff")),
        new Stop(1, Color.web("#617584"))));
    foot.setStroke(Color.web("#d8ecf7"));
    foot.setStrokeWidth(Math.max(0.45, size * 0.025));

    artwork.getChildren().addAll(stem, foot, screen, reflection, pulse);
    return artwork;
  }

  private static Region helpGlyph(double size) {
    Circle orb = new Circle(size * 0.39);
    orb.setFill(new RadialGradient(
        0, 0, 0.33, 0.25, 0.88, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#f7fdff")),
        new Stop(0.34, Color.web("#94d9f4")),
        new Stop(0.72, Color.web("#3c7fae")),
        new Stop(1, Color.web("#173b5a"))));
    orb.setStroke(Color.web("#e8f8ff"));
    orb.setStrokeWidth(Math.max(0.7, size * 0.045));
    orb.setEffect(new InnerShadow(Math.max(1.2, size * 0.08), Color.rgb(5, 28, 45, 0.78)));

    Region question = sized(CssIcon.questionMark("#ffffff"), size * 0.48);
    question.setEffect(new DropShadow(Math.max(1, size * 0.06), 0, size * 0.04,
        Color.rgb(0, 23, 42, 0.88)));

    Ellipse reflection = new Ellipse(size * 0.43, size * 0.31, size * 0.19, size * 0.075);
    reflection.setFill(Color.rgb(255, 255, 255, 0.52));

    StackPane canvas = new StackPane(orb, question, reflection);
    canvas.setMinSize(size, size);
    canvas.setPrefSize(size, size);
    canvas.setMaxSize(size, size);
    StackPane.setAlignment(reflection, Pos.TOP_CENTER);
    StackPane.setMargin(reflection, new Insets(size * 0.17, 0, 0, 0));
    return canvas;
  }

  private static Region noProjectGlyph(double size) {
    Rectangle tab = new Rectangle(size * 0.13, size * 0.16, size * 0.38, size * 0.24);
    tab.setArcWidth(size * 0.10);
    tab.setArcHeight(size * 0.10);
    tab.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#e9f4fb")),
        new Stop(1, Color.web("#70889a"))));

    Rectangle folder = new Rectangle(size * 0.08, size * 0.27, size * 0.84, size * 0.57);
    folder.setArcWidth(size * 0.13);
    folder.setArcHeight(size * 0.13);
    folder.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#d8e7f0")),
        new Stop(0.22, Color.web("#7892a5")),
        new Stop(1, Color.web("#344b5c"))));
    folder.setStroke(Color.web("#edf8ff"));
    folder.setStrokeWidth(Math.max(0.8, size * 0.035));
    folder.setEffect(new DropShadow(Math.max(2, size * 0.10), 0, size * 0.06,
        Color.rgb(0, 0, 0, 0.82)));

    Rectangle emptyWell = new Rectangle(size * 0.18, size * 0.40, size * 0.64, size * 0.30);
    emptyWell.setArcWidth(size * 0.10);
    emptyWell.setArcHeight(size * 0.10);
    emptyWell.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#172b3a")),
        new Stop(1, Color.web("#0a1822"))));
    emptyWell.setStroke(Color.rgb(220, 241, 252, 0.44));
    emptyWell.setStrokeWidth(Math.max(0.5, size * 0.022));

    double markerSize = size * 0.31;
    Circle marker = new Circle(markerSize / 2.0);
    marker.setFill(new RadialGradient(
        0, 0, 0.32, 0.27, 0.88, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web("#fff2bc")),
        new Stop(0.55, Color.web("#e8a948")),
        new Stop(1, Color.web("#9b541e"))));
    marker.setStroke(Color.web("#fff5d8"));
    marker.setStrokeWidth(Math.max(0.6, size * 0.026));
    marker.setEffect(new DropShadow(Math.max(1.5, size * 0.08), Color.rgb(0, 0, 0, 0.75)));
    Rectangle minus = new Rectangle(markerSize * 0.24, markerSize * 0.45, markerSize * 0.52, markerSize * 0.11);
    minus.setArcWidth(markerSize * 0.08);
    minus.setArcHeight(markerSize * 0.08);
    minus.setFill(Color.web("#fff8df"));
    StackPane markerJewel = new StackPane(marker, minus);
    markerJewel.setMinSize(markerSize, markerSize);
    markerJewel.setPrefSize(markerSize, markerSize);
    markerJewel.setMaxSize(markerSize, markerSize);

    Pane artwork = new Pane(tab, folder, emptyWell, markerJewel);
    artwork.setMinSize(size, size);
    artwork.setPrefSize(size, size);
    artwork.setMaxSize(size, size);
    markerJewel.relocate(size * 0.66, size * 0.63);
    return artwork;
  }

  private static Palette paletteFor(Kind kind) {
    return switch (kind) {
      case PROJECT, OPEN_PROJECT, ASSETS, DOCUMENTATION, REVEAL ->
          palette("#ffe37a", "#b76a17", "#fff1b0");
      case TRASHMAN -> palette("#d9e5ec", "#61717e", "#f5fbff");
      case STORY_MAP, STORYBOARD, RUN ->
          palette("#ffbd69", "#c5521c", "#ffe0a2");
      case INSPECTOR, SCRIPT_EDITOR, IMAGE_ATTRIBUTES ->
          palette("#79d8ff", "#2267ad", "#c9f2ff");
      case DIAGNOSTICS -> palette("#72ddff", "#19547a", "#dff8ff");
      case LABEL_FLOW, VERSION_CONTROL, ENTRY_SCRIPT, NEW_PROJECT ->
          palette("#8de8aa", "#26824e", "#d8ffe4");
      case LAYOUT, LAYERS, MANIFEST, README ->
          palette("#b9c9d8", "#586b7e", "#edf6ff");
      case LIGHTING, PUPPETEER ->
          palette("#f7a9dc", "#9a478c", "#ffd9ef");
      case SETTINGS -> palette("#ffb15a", "#9b4f1d", "#fff0d5");
      case BUILD -> palette("#87e79a", "#2d873e", "#ddffe3");
      case REFRESH -> palette("#7edbff", "#2471b3", "#d7f5ff");
      case ARROW_BACK -> palette("#ffb55d", "#b74c15", "#ffe0b0");
      case HELP -> palette("#a9e5fb", "#235b82", "#f1fbff");
      case NO_PROJECT -> palette("#d8e7f0", "#344b5c", "#edf8ff");
    };
  }

  private static Palette palette(String top, String bottom, String edge) {
    return new Palette(Color.web(top), Color.web(bottom), Color.web(edge));
  }

  private static Color mix(Color first, Color second, double amount) {
    return first.interpolate(second, Math.max(0, Math.min(1, amount)));
  }

  private static String toCss(Color color) {
    return String.format("#%02x%02x%02x",
        Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
        Math.round(color.getBlue() * 255));
  }

  private static double clampSize(double size) {
    if (!Double.isFinite(size)) return 24;
    return Math.max(14, Math.min(48, size));
  }
}
