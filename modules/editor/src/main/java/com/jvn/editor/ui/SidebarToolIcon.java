package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Windows 7-style command artwork shared by the utility controls inside sidebar tools.
 *
 * <p>The underlying silhouettes stay compact enough for dense toolbars, while this wrapper
 * standardises their dimensional enamel treatment, sizing, and the same transparent hover glow
 * used by the Help, New Panel, Refresh, and Version Control controls.</p>
 */
public final class SidebarToolIcon extends StackPane {
  public enum Kind {
    ADD, REMOVE, PREVIOUS, NEXT, UP, DOWN, SORT, OPEN, CLOSE, UNDO, REDO, REFRESH,
    SPEECH, LIST, SEARCH, GRID, PALETTE, WARNING, DOWNLOAD, SAVE, EXPAND, APPLY,
    LINK, HOME, COPY, POP_OUT, SELECT, POLYGON, FREEHAND, SHOW, HIDE, PERSON,
    IMAGE, EDIT, DELETE, TIMELINE, AUTO, SPARKLES, HELP, LABEL, MOVIE, LOCATE,
    MEMORY, DOCK
  }

  private static final double DEFAULT_SIZE = 20.0;

  private final Kind kind;
  private final double iconSize;
  private final Color glow;

  private SidebarToolIcon(Kind requestedKind, String requestedColor, double requestedSize) {
    kind = requestedKind == null ? Kind.HELP : requestedKind;
    iconSize = Math.max(14, Math.min(32, requestedSize));
    String color = requestedColor == null || requestedColor.isBlank()
        ? defaultColor(kind)
        : requestedColor;
    glow = parseColor(color, Color.web(defaultColor(kind)));

    Region artwork = artwork(kind, color, iconSize);
    artwork.setMouseTransparent(true);
    artwork.setCache(true);
    artwork.setCacheHint(CacheHint.SPEED);

    double artworkWidth = artwork.prefWidth(-1);
    double artworkHeight = artwork.prefHeight(-1);
    double largest = Math.max(artworkWidth, artworkHeight);
    if (largest > 0 && kind != Kind.REFRESH) {
      double scale = Math.min(1.55, Math.max(1.0, (iconSize - 1.5) / largest));
      artwork.setScaleX(scale);
      artwork.setScaleY(scale);
    }

    setAlignment(Pos.CENTER);
    setMinSize(iconSize, iconSize);
    setPrefSize(iconSize, iconSize);
    setMaxSize(iconSize, iconSize);
    setPickOnBounds(false);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().addAll("jvn-fx-icon", "jvn-sidebar-tool-icon");
    getChildren().setAll(artwork);
    parentProperty().addListener((obs, oldParent, newParent) -> {
      if (newParent instanceof ButtonBase button) installButtonTreatment(button);
    });
  }

  public static SidebarToolIcon of(Kind kind) {
    return new SidebarToolIcon(kind, "", DEFAULT_SIZE);
  }

  public static SidebarToolIcon of(Kind kind, String color) {
    return new SidebarToolIcon(kind, color, DEFAULT_SIZE);
  }

  public static SidebarToolIcon of(Kind kind, String color, double size) {
    return new SidebarToolIcon(kind, color, size);
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
    button.hoverProperty().addListener((obs, wasHovered, hovered) -> updateEffect(button));
    button.pressedProperty().addListener((obs, wasPressed, pressed) -> updateEffect(button));
    updateEffect(button);
  }

  private void updateEffect(ButtonBase button) {
    if (button.isPressed()) {
      setScaleX(0.94);
      setScaleY(0.94);
      setEffect(new DropShadow(Math.max(2.5, iconSize * 0.15),
          glow.deriveColor(0, 0.82, 0.78, 0.72)));
    } else if (button.isHover()) {
      setScaleX(1.06);
      setScaleY(1.06);
      setEffect(new DropShadow(Math.max(5.5, iconSize * 0.32), glow));
    } else {
      setScaleX(1.0);
      setScaleY(1.0);
      setEffect(null);
    }
  }

  private static Region artwork(Kind kind, String color, double size) {
    return switch (kind) {
      case ADD -> PanelChooserActionIcon.of(PanelChooserActionIcon.Kind.ADD_HERE);
      case POP_OUT -> PanelChooserActionIcon.of(PanelChooserActionIcon.Kind.POP_OUT);
      case REFRESH -> RefreshIcon.of(size);
      case REMOVE -> SidebarToolArtwork.remove(color);
      case PREVIOUS -> SidebarToolArtwork.previous(color);
      case NEXT -> SidebarToolArtwork.next(color);
      case UP -> SidebarToolArtwork.up(color);
      case DOWN -> SidebarToolArtwork.down(color);
      case SORT -> SidebarToolArtwork.sort(color);
      case OPEN -> SidebarToolArtwork.open(color);
      case CLOSE -> SidebarToolArtwork.close(color);
      case UNDO -> SidebarToolArtwork.undo(color);
      case REDO -> SidebarToolArtwork.redo(color);
      case SPEECH -> SidebarToolArtwork.speech(color);
      case LIST -> SidebarToolArtwork.list(color);
      case SEARCH -> SidebarToolArtwork.search(color);
      case GRID -> SidebarToolArtwork.grid(color);
      case PALETTE -> SidebarToolArtwork.palette(color);
      case WARNING -> CssIcon.warning(color);
      case DOWNLOAD -> SidebarToolArtwork.download(color);
      case SAVE -> SidebarToolArtwork.save(color);
      case EXPAND -> SidebarToolArtwork.expand(color);
      case APPLY -> SidebarToolArtwork.apply(color);
      case LINK -> SidebarToolArtwork.link(color);
      case HOME -> SidebarToolArtwork.home(color);
      case COPY -> SidebarToolArtwork.copy(color);
      case SELECT -> SidebarToolArtwork.select(color);
      case POLYGON -> SidebarToolArtwork.polygon(color);
      case FREEHAND -> SidebarToolArtwork.freehand(color);
      case SHOW -> SidebarToolArtwork.show(color);
      case HIDE -> SidebarToolArtwork.hide(color);
      case PERSON -> SidebarToolArtwork.person(color);
      case IMAGE -> SidebarToolArtwork.image(color);
      case EDIT -> SidebarToolArtwork.edit(color);
      case DELETE -> SidebarToolArtwork.delete(color);
      case TIMELINE -> SidebarToolArtwork.timeline(color);
      case AUTO -> SidebarToolArtwork.auto(color);
      case SPARKLES -> SidebarToolArtwork.sparkles(color);
      case HELP -> CssIcon.help(color);
      case LABEL -> SidebarToolArtwork.label(color);
      case MOVIE -> SidebarToolArtwork.movie(color);
      case LOCATE -> SidebarToolArtwork.locate(color);
      case MEMORY -> SidebarToolArtwork.memory(color);
      case DOCK -> SidebarToolArtwork.dock(color);
    };
  }

  private static String defaultColor(Kind kind) {
    return switch (kind) {
      case ADD, APPLY, HOME -> "#62d56e";
      case REMOVE, CLOSE, DELETE, HIDE -> "#e16b78";
      case OPEN, PALETTE, WARNING, SPARKLES -> "#e7ad55";
      case UNDO, FREEHAND, POLYGON -> "#ae91e8";
      case REDO, EDIT -> "#58bce8";
      case SORT, AUTO -> "#efa15b";
      case SAVE, DOWNLOAD, EXPAND, GRID, SELECT, SHOW, PERSON, IMAGE -> "#68aee3";
      case COPY, LINK, SPEECH, LIST, SEARCH, TIMELINE, LABEL, MOVIE, LOCATE,
          PREVIOUS, NEXT, UP, DOWN, POP_OUT, REFRESH, HELP, MEMORY, DOCK -> "#69c6eb";
    };
  }

  private static Color parseColor(String value, Color fallback) {
    try {
      return Color.web(value);
    } catch (IllegalArgumentException ignored) {
      return fallback;
    }
  }

  public static HBox iconLabel(Region icon, String text, String style) {
    Label label = new Label(text);
    label.setStyle(style == null ? "" : style);
    HBox box = new HBox(6, icon, label);
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  public static Region plus() { return of(Kind.ADD); }
  public static Region plus(String color) { return of(Kind.ADD, color); }
  public static Region minus() { return of(Kind.REMOVE); }
  public static Region minus(String color) { return of(Kind.REMOVE, color); }
  public static Region arrowLeft() { return of(Kind.PREVIOUS); }
  public static Region arrowLeft(String color) { return of(Kind.PREVIOUS, color); }
  public static Region arrowRight() { return of(Kind.NEXT); }
  public static Region arrowRight(String color) { return of(Kind.NEXT, color); }
  public static Region arrowUp() { return of(Kind.UP); }
  public static Region arrowUp(String color) { return of(Kind.UP, color); }
  public static Region arrowDown() { return of(Kind.DOWN); }
  public static Region arrowDown(String color) { return of(Kind.DOWN, color); }
  public static Region sort() { return of(Kind.SORT); }
  public static Region sort(String color) { return of(Kind.SORT, color); }
  public static Region folder() { return of(Kind.OPEN); }
  public static Region folder(String color) { return of(Kind.OPEN, color); }
  public static Region clearX() { return of(Kind.CLOSE); }
  public static Region clearX(String color) { return of(Kind.CLOSE, color); }
  public static Region undo() { return of(Kind.UNDO); }
  public static Region undo(String color) { return of(Kind.UNDO, color); }
  public static Region redo() { return of(Kind.REDO); }
  public static Region redo(String color) { return of(Kind.REDO, color); }
  public static Region refresh() { return of(Kind.REFRESH); }
  public static Region refresh(String color) { return of(Kind.REFRESH, color); }
  public static Region speech() { return of(Kind.SPEECH); }
  public static Region speech(String color) { return of(Kind.SPEECH, color); }
  public static Region list() { return of(Kind.LIST); }
  public static Region list(String color) { return of(Kind.LIST, color); }
  public static Region search() { return of(Kind.SEARCH); }
  public static Region search(String color) { return of(Kind.SEARCH, color); }
  public static Region grid() { return of(Kind.GRID); }
  public static Region grid(String color) { return of(Kind.GRID, color); }
  public static Region palette() { return of(Kind.PALETTE); }
  public static Region palette(String color) { return of(Kind.PALETTE, color); }
  public static Region warning() { return of(Kind.WARNING); }
  public static Region warning(String color) { return of(Kind.WARNING, color); }
  public static Region download() { return of(Kind.DOWNLOAD); }
  public static Region download(String color) { return of(Kind.DOWNLOAD, color); }
  public static Region save() { return of(Kind.SAVE); }
  public static Region save(String color) { return of(Kind.SAVE, color); }
  public static Region expand() { return of(Kind.EXPAND); }
  public static Region expand(String color) { return of(Kind.EXPAND, color); }
  public static Region check() { return of(Kind.APPLY); }
  public static Region check(String color) { return of(Kind.APPLY, color); }
  public static Region link() { return of(Kind.LINK); }
  public static Region link(String color) { return of(Kind.LINK, color); }
  public static Region home() { return of(Kind.HOME); }
  public static Region home(String color) { return of(Kind.HOME, color); }
  public static Region copy() { return of(Kind.COPY); }
  public static Region copy(String color) { return of(Kind.COPY, color); }
  public static Region popOut() { return of(Kind.POP_OUT); }
  public static Region popOut(String color) { return of(Kind.POP_OUT, color); }
  public static Region rectSelect() { return of(Kind.SELECT); }
  public static Region rectSelect(String color) { return of(Kind.SELECT, color); }
  public static Region polygon() { return of(Kind.POLYGON); }
  public static Region polygon(String color) { return of(Kind.POLYGON, color); }
  public static Region freehand() { return of(Kind.FREEHAND); }
  public static Region freehand(String color) { return of(Kind.FREEHAND, color); }
  public static Region visibility() { return of(Kind.SHOW); }
  public static Region visibility(String color) { return of(Kind.SHOW, color); }
  public static Region visibilityOff() { return of(Kind.HIDE); }
  public static Region visibilityOff(String color) { return of(Kind.HIDE, color); }
  public static Region person() { return of(Kind.PERSON); }
  public static Region person(String color) { return of(Kind.PERSON, color); }
  public static Region landscape() { return of(Kind.IMAGE); }
  public static Region landscape(String color) { return of(Kind.IMAGE, color); }
  public static Region edit() { return of(Kind.EDIT); }
  public static Region edit(String color) { return of(Kind.EDIT, color); }
  public static Region delete() { return of(Kind.DELETE); }
  public static Region delete(String color) { return of(Kind.DELETE, color); }
  public static Region timeline() { return of(Kind.TIMELINE); }
  public static Region timeline(String color) { return of(Kind.TIMELINE, color); }
  public static Region auto() { return of(Kind.AUTO); }
  public static Region auto(String color) { return of(Kind.AUTO, color); }
  public static Region sparkles() { return of(Kind.SPARKLES); }
  public static Region sparkles(String color) { return of(Kind.SPARKLES, color); }
  public static Region help() { return of(Kind.HELP); }
  public static Region help(String color) { return of(Kind.HELP, color); }
  public static Region label() { return of(Kind.LABEL); }
  public static Region label(String color) { return of(Kind.LABEL, color); }
  public static Region movie() { return of(Kind.MOVIE); }
  public static Region movie(String color) { return of(Kind.MOVIE, color); }
  public static Region nearMe() { return of(Kind.LOCATE); }
  public static Region nearMe(String color) { return of(Kind.LOCATE, color); }
  public static Region memory() { return of(Kind.MEMORY); }
  public static Region memory(String color) { return of(Kind.MEMORY, color); }
  public static Region dock() { return of(Kind.DOCK); }
  public static Region dock(String color) { return of(Kind.DOCK, color); }
}
