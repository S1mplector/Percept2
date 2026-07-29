package com.jvn.editor.ui;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.CacheHint;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * Compact Windows 7-style glass icons for the editor's responsive top-level menus.
 *
 * <p>The full menu labels remain visible when space permits. At narrower widths, each label is
 * replaced with its descriptive icon while the original {@link Menu} and all of its items remain
 * unchanged.
 */
public final class EditorMenuIcon extends StackPane {
  private static final double SIZE = 20;
  private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");

  public enum Kind {
    FILE,
    EDIT,
    VIEW,
    NAVIGATE,
    PROJECT,
    TEXT,
    VNS,
    ASSETS,
    DIAGNOSTICS,
    RUN,
    BUILD,
    TOOLS,
    VERSION_CONTROL,
    WINDOW,
    HELP,
    DEVELOPER
  }

  public record MenuSpec(Menu menu, String label, Kind kind) {
    public MenuSpec {
      if (menu == null) throw new IllegalArgumentException("menu is required");
      label = label == null ? "" : label.trim();
      kind = kind == null ? Kind.FILE : kind;
    }
  }

  private record Palette(String top, String bottom, String edge) {}

  private final Kind kind;

  private EditorMenuIcon(Kind kind) {
    this.kind = kind == null ? Kind.FILE : kind;
    Palette palette = paletteFor(this.kind);

    Rectangle plate = new Rectangle(SIZE, SIZE);
    plate.setArcWidth(6);
    plate.setArcHeight(6);
    plate.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.web(palette.top())),
        new Stop(0.48, Color.web(palette.bottom())),
        new Stop(1, darken(Color.web(palette.bottom()), 0.28))));
    plate.setStroke(Color.web(palette.edge()));
    plate.setStrokeWidth(0.9);
    plate.setEffect(new InnerShadow(2.2, Color.rgb(0, 0, 0, 0.64)));

    Rectangle gloss = new Rectangle(SIZE - 3, 7.5);
    gloss.setArcWidth(5);
    gloss.setArcHeight(5);
    gloss.setTranslateY(-4.4);
    gloss.setFill(new LinearGradient(
        0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.rgb(255, 255, 255, 0.52)),
        new Stop(1, Color.rgb(255, 255, 255, 0.05))));
    gloss.setMouseTransparent(true);

    Region glyph = glyphFor(this.kind);
    glyph.setScaleX(0.74);
    glyph.setScaleY(0.74);
    glyph.setMouseTransparent(true);
    glyph.setEffect(new DropShadow(1.4, 0, 0.8, Color.rgb(0, 0, 0, 0.88)));

    setMinSize(SIZE, SIZE);
    setPrefSize(SIZE, SIZE);
    setMaxSize(SIZE, SIZE);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("editor-menu-icon");
    getChildren().setAll(plate, gloss, glyph);
  }

  public static EditorMenuIcon of(Kind kind) {
    return new EditorMenuIcon(kind);
  }

  public Kind kind() {
    return kind;
  }

  /**
   * Installs responsive label/icon switching using the available width of the supplied command bar.
   */
  public static void installResponsive(
      MenuBar menuBar,
      Region widthSource,
      List<MenuSpec> specs) {
    if (menuBar == null || widthSource == null || specs == null || specs.isEmpty()) return;

    List<MenuSpec> safeSpecs = List.copyOf(specs);
    Map<Menu, EditorMenuIcon> icons = new IdentityHashMap<>();
    for (MenuSpec spec : safeSpecs) {
      EditorMenuIcon icon = of(spec.kind());
      Tooltip tooltip = new Tooltip(spec.label());
      tooltip.setShowDelay(Duration.ZERO);
      Tooltip.install(icon, tooltip);
      icons.put(spec.menu(), icon);
    }

    Runnable refresh = () -> {
      double width = widthSource.getWidth();
      if (width <= 0 && widthSource.getScene() != null) {
        width = widthSource.getScene().getWidth();
      }
      boolean compact = shouldCompact(width, safeSpecs.size());
      menuBar.pseudoClassStateChanged(COMPACT, compact);
      for (MenuSpec spec : safeSpecs) {
        // Keep the model text intact for menu semantics and accessibility; compact CSS presents
        // the graphic only.
        spec.menu().setText(spec.label());
        spec.menu().setGraphic(compact ? icons.get(spec.menu()) : null);
      }
    };
    widthSource.widthProperty().addListener((observable, oldWidth, newWidth) -> refresh.run());
    Platform.runLater(refresh);
  }

  static boolean shouldCompact(double availableWidth, int menuCount) {
    if (availableWidth <= 0 || menuCount <= 0) return false;
    double breakpoint = Math.max(1500, 950 + menuCount * 78.0);
    return availableWidth < breakpoint;
  }

  private static Region glyphFor(Kind kind) {
    String light = "#f7fbff";
    return switch (kind) {
      case FILE -> CssIcon.document(light);
      case EDIT -> CssIcon.edit(light);
      case VIEW -> CssIcon.grid(light);
      case NAVIGATE -> CssIcon.arrowRight(light);
      case PROJECT -> CssIcon.folder(light);
      case TEXT -> letterGlyph(light);
      case VNS -> CssIcon.speech(light);
      case ASSETS -> CssIcon.landscape(light);
      case DIAGNOSTICS -> CssIcon.warning(light);
      case RUN -> CssIcon.play(light);
      case BUILD -> CssIcon.download(light);
      case TOOLS -> CssIcon.theater(light);
      case VERSION_CONTROL -> CssIcon.branchPlus(light);
      case WINDOW -> CssIcon.copy(light);
      case HELP -> CssIcon.help(light);
      case DEVELOPER -> CssIcon.memory(light);
    };
  }

  private static Region letterGlyph(String color) {
    Label glyph = new Label("a");
    glyph.setTextFill(Color.web(color));
    glyph.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
    glyph.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    return glyph;
  }

  private static Palette paletteFor(Kind kind) {
    return switch (kind) {
      case FILE -> new Palette("#78baf0", "#28689b", "#a9d9ff");
      case EDIT -> new Palette("#f0b36f", "#9b5926", "#ffd4a3");
      case VIEW -> new Palette("#9aa8ba", "#4e6075", "#d9e2ed");
      case NAVIGATE -> new Palette("#67c8d2", "#23747d", "#b6f4f8");
      case PROJECT -> new Palette("#e8c76e", "#9a7120", "#fff0a8");
      case TEXT -> new Palette("#8bbcf2", "#3f68a0", "#cce4ff");
      case VNS -> new Palette("#cc8bea", "#77409a", "#efc6ff");
      case ASSETS -> new Palette("#74c98a", "#2d7b43", "#bdf2ca");
      case DIAGNOSTICS -> new Palette("#ee8c72", "#a33e2c", "#ffd0c3");
      case RUN -> new Palette("#70d997", "#237c48", "#bdffd2");
      case BUILD -> new Palette("#e6b768", "#9a681e", "#ffe0a3");
      case TOOLS -> new Palette("#a9b5c7", "#596a83", "#e1e8f2");
      case VERSION_CONTROL -> new Palette("#8e9cf0", "#4656a2", "#d0d7ff");
      case WINDOW -> new Palette("#68b9dc", "#2e718f", "#b9e9ff");
      case HELP -> new Palette("#63a9eb", "#285f9d", "#b8dcff");
      case DEVELOPER -> new Palette("#d08beb", "#7d3e9a", "#f0c7ff");
    };
  }

  private static Color darken(Color color, double amount) {
    double factor = Math.max(0, 1.0 - amount);
    return new Color(
        color.getRed() * factor,
        color.getGreen() * factor,
        color.getBlue() * factor,
        color.getOpacity());
  }
}
