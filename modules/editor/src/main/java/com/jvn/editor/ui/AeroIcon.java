package com.jvn.editor.ui;

import java.util.Optional;
import javafx.scene.control.ButtonBase;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
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
    README, DOCUMENTATION, REVEAL, ARROW_BACK
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
      case DIAGNOSTICS -> sized(CssIcon.warning(color), size);
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
    };
    glyph = decorate(kind, glyph, size, palette);
    DropShadow depth = new DropShadow(Math.max(2, size * 0.14), 0, Math.max(1, size * 0.08),
        Color.rgb(0, 0, 0, 0.82));
    depth.setInput(glyph.getEffect());
    glyph.setEffect(depth);
    return glyph;
  }

  private static Region decorate(Kind kind, Region base, double size, Palette palette) {
    Optional<Badge> optionalBadge = badgeFor(kind, size);
    StackPane artwork = new StackPane(base);
    artwork.setMinSize(size, size);
    artwork.setPrefSize(size, size);
    artwork.setMaxSize(size, size);

    Circle glint = new Circle(size * 0.055, Color.rgb(255, 255, 255, 0.78));
    glint.setEffect(new DropShadow(size * 0.08, Color.rgb(255, 255, 255, 0.52)));
    StackPane.setAlignment(glint, Pos.TOP_LEFT);
    StackPane.setMargin(glint, new Insets(size * 0.16, 0, 0, size * 0.19));
    artwork.getChildren().add(glint);

    if (optionalBadge.isPresent()) {
      Badge badge = optionalBadge.get();
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
      badgeGlyph.setStyle(badgeGlyph.getStyle()
          + " -fx-background-color: linear-gradient(to bottom, #ffffff, #dcecff 58%, #8295a6);");
      StackPane jewel = new StackPane(rim, badgeGlyph);
      jewel.setMinSize(badgeSize, badgeSize);
      jewel.setPrefSize(badgeSize, badgeSize);
      jewel.setMaxSize(badgeSize, badgeSize);
      jewel.setEffect(new InnerShadow(Math.max(0.5, size * 0.035), Color.rgb(255, 255, 255, 0.36)));
      StackPane.setAlignment(jewel, Pos.BOTTOM_RIGHT);
      StackPane.setMargin(jewel, new Insets(0, -size * 0.04, -size * 0.03, 0));
      artwork.getChildren().add(jewel);
    }
    return artwork;
  }

  private static Optional<Badge> badgeFor(Kind kind, double size) {
    double glyphSize = Math.max(5, size * 0.21);
    return switch (kind) {
      case NEW_PROJECT -> Optional.of(new Badge(sized(CssIcon.plusBold("#ffffff"), glyphSize), Color.web("#28a75d")));
      case OPEN_PROJECT -> Optional.of(new Badge(sized(CssIcon.arrowRight("#ffffff"), glyphSize), Color.web("#2689d8")));
      case ASSETS -> Optional.of(new Badge(sized(CssIcon.landscape("#ffffff"), glyphSize), Color.web("#298ac4")));
      case DOCUMENTATION -> Optional.of(new Badge(sized(CssIcon.help("#ffffff"), glyphSize), Color.web("#397cc0")));
      case REVEAL -> Optional.of(new Badge(sized(CssIcon.popOut("#ffffff"), glyphSize), Color.web("#397cc0")));
      case IMAGE_ATTRIBUTES, SCRIPT_EDITOR ->
          Optional.of(new Badge(sized(CssIcon.edit("#ffffff"), glyphSize), Color.web("#168dbf")));
      case MANIFEST -> Optional.of(new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#35a45c")));
      case README -> Optional.of(new Badge(sized(CssIcon.list("#ffffff"), glyphSize), Color.web("#4b7ba6")));
      case ENTRY_SCRIPT -> Optional.of(new Badge(sized(CssIcon.play("#ffffff"), glyphSize), Color.web("#2fa35a")));
      case BUILD -> Optional.of(new Badge(sized(CssIcon.check("#ffffff"), glyphSize), Color.web("#2e9b50")));
      case LIGHTING, PUPPETEER ->
          Optional.of(new Badge(sized(CssIcon.sparkles("#ffffff"), glyphSize), Color.web("#b54f95")));
      default -> Optional.empty();
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

  private static Palette paletteFor(Kind kind) {
    return switch (kind) {
      case PROJECT, OPEN_PROJECT, ASSETS, DOCUMENTATION, REVEAL ->
          palette("#ffe37a", "#b76a17", "#fff1b0");
      case TRASHMAN -> palette("#d9e5ec", "#61717e", "#f5fbff");
      case STORY_MAP, STORYBOARD, RUN ->
          palette("#ffbd69", "#c5521c", "#ffe0a2");
      case INSPECTOR, SCRIPT_EDITOR, IMAGE_ATTRIBUTES ->
          palette("#79d8ff", "#2267ad", "#c9f2ff");
      case DIAGNOSTICS -> palette("#ffd86b", "#c06a18", "#fff1ac");
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
