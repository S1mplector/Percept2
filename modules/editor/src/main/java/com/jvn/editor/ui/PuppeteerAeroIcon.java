package com.jvn.editor.ui;

import javafx.scene.CacheHint;
import javafx.scene.control.ButtonBase;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/** Compact Aero artwork shared by Puppeteer's floating command dockers. */
public final class PuppeteerAeroIcon extends StackPane {
  public enum Kind {
    COPY, PASTE, HISTORY, DUPLICATE, ADD_ALL, SAVE_CLIP, LOAD_CLIP, CHARACTER_SLOT,
    PREVIOUS, NEXT, FOCUS, ZOOM_FIT, DISTRIBUTE, REVERSE, STRETCH, COMPRESS,
    RIPPLE, COMPACT_EXPORT,
    REWIND, PLAY, PAUSE, STOP, UNDO, REDO,
    FIT_DURATION, LOOP, LOOP_IN, LOOP_OUT, CLEAR,
    PRESET, SNAP, RUNTIME_PREVIEW, VIEWPORT_STABILIZE, AUTO_KEY,
    SNAP_GRID, SNAP_ENTITY, ORBIT, ORBIT_ALIGN, HELP,
    ADD_AUDIO_CUE, ADD_EXPRESSION_CUE, MANAGE_EVENTS,
    SYNC, REGISTER, RECORD_GIF, COPY_CODE, VERIFY
  }

  private record Palette(Color light, Color mid, Color dark, Color glow) {}

  private final Kind kind;
  private final double iconSize;

  private PuppeteerAeroIcon(Kind kind, double requestedSize) {
    this.kind = kind == null ? Kind.COPY : kind;
    this.iconSize = Math.max(18, Math.min(28, requestedSize));
    Palette palette = paletteFor(this.kind);

    Rectangle plate = new Rectangle(iconSize * 0.91, iconSize * 0.91);
    plate.setArcWidth(iconSize * 0.34);
    plate.setArcHeight(iconSize * 0.34);
    plate.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
        new Stop(0, palette.light()),
        new Stop(0.18, palette.mid()),
        new Stop(0.58, palette.dark()),
        new Stop(1, palette.dark().deriveColor(0, 0.78, 0.48, 1))));
    plate.setStroke(Color.rgb(218, 234, 247, 0.76));
    plate.setStrokeWidth(Math.max(0.75, iconSize * 0.045));
    plate.setEffect(new InnerShadow(iconSize * 0.16, Color.rgb(1, 7, 13, 0.82)));

    Rectangle highlight = new Rectangle(iconSize * 0.69, iconSize * 0.25);
    highlight.setArcWidth(iconSize * 0.20);
    highlight.setArcHeight(iconSize * 0.20);
    highlight.setTranslateY(-iconSize * 0.23);
    highlight.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
        new Stop(0, Color.rgb(255, 255, 255, 0.13)),
        new Stop(0.42, Color.rgb(255, 255, 255, 0.48)),
        new Stop(1, Color.rgb(255, 255, 255, 0.08))));
    highlight.setMouseTransparent(true);

    Region glyph = glyphFor(this.kind, iconSize * 0.54);
    glyph.setMouseTransparent(true);
    glyph.setEffect(new DropShadow(Math.max(1.2, iconSize * 0.08), 0, iconSize * 0.035,
        Color.rgb(0, 0, 0, 0.92)));

    Circle status = new Circle(iconSize * 0.055, palette.glow().deriveColor(0, 0.75, 1.45, 0.9));
    status.setTranslateX(iconSize * 0.31);
    status.setTranslateY(iconSize * 0.31);
    status.setEffect(new DropShadow(iconSize * 0.13, palette.glow()));

    setMinSize(iconSize, iconSize);
    setPrefSize(iconSize, iconSize);
    setMaxSize(iconSize, iconSize);
    setMouseTransparent(true);
    setCache(true);
    setCacheHint(CacheHint.SPEED);
    getStyleClass().add("puppeteer-aero-icon");
    getChildren().setAll(plate, highlight, glyph, status);
    parentProperty().addListener((observable, oldParent, newParent) -> {
      if (newParent instanceof ButtonBase button) installButtonTreatment(button, palette);
    });
  }

  public static PuppeteerAeroIcon of(Kind kind) {
    return new PuppeteerAeroIcon(kind, 23);
  }

  public static PuppeteerAeroIcon of(Kind kind, double size) {
    return new PuppeteerAeroIcon(kind, size);
  }

  public Kind kind() { return kind; }

  public double iconSize() { return iconSize; }

  private void installButtonTreatment(ButtonBase button, Palette palette) {
    if (!button.getStyleClass().contains("puppeteer-aero-button")) {
      button.getStyleClass().add("puppeteer-aero-button");
    }
    button.hoverProperty().addListener((obs, before, hovered) -> updateEffect(button, palette));
    button.pressedProperty().addListener((obs, before, pressed) -> updateEffect(button, palette));
    updateEffect(button, palette);
  }

  private void updateEffect(ButtonBase button, Palette palette) {
    if (button.isPressed()) {
      setScaleX(0.92);
      setScaleY(0.92);
      setEffect(new DropShadow(iconSize * 0.16, palette.glow()));
    } else if (button.isHover()) {
      setScaleX(1.07);
      setScaleY(1.07);
      setEffect(new DropShadow(iconSize * 0.36, palette.glow()));
    } else {
      setScaleX(1);
      setScaleY(1);
      setEffect(null);
    }
  }

  private static Region glyphFor(Kind kind, double size) {
    String white = "#f4fbff";
    Region glyph = switch (kind) {
      case COPY -> CssIcon.copy(white);
      case PASTE -> CssIcon.contentPaste(white);
      case HISTORY -> CssIcon.timeline(white);
      case DUPLICATE -> CssIcon.controlPointDuplicate(white);
      case ADD_ALL -> CssIcon.plusBold(white);
      case SAVE_CLIP -> CssIcon.libraryAdd(white);
      case LOAD_CLIP -> CssIcon.input(white);
      case CHARACTER_SLOT -> CssIcon.emojiPeople(white);
      case PREVIOUS -> CssIcon.fastRewind(white);
      case NEXT -> CssIcon.fastForward(white);
      case FOCUS -> CssIcon.myLocation(white);
      case ZOOM_FIT -> CssIcon.zoomOutMap(white);
      case DISTRIBUTE -> CssIcon.formatAlignJustify(white);
      case REVERSE -> CssIcon.swapHoriz(white);
      case STRETCH -> CssIcon.openInFull(white);
      case COMPRESS -> CssIcon.closeFullscreen(white);
      case RIPPLE -> CssIcon.link(white);
      case COMPACT_EXPORT -> CssIcon.folderZip(white);
      case REWIND -> CssIcon.skipPrevious(white);
      case PLAY -> CssIcon.play(white);
      case PAUSE -> CssIcon.pause(white);
      case STOP -> CssIcon.stop(white);
      case UNDO -> CssIcon.undo(white);
      case REDO -> CssIcon.redo(white);
      case FIT_DURATION -> CssIcon.rectSelect(white);
      case LOOP, SYNC -> CssIcon.loop(white);
      case LOOP_IN -> CssIcon.verticalAlignBottom(white);
      case LOOP_OUT -> CssIcon.verticalAlignTop(white);
      case CLEAR -> CssIcon.clearX(white);
      case PRESET -> CssIcon.folder(white);
      case SNAP -> CssIcon.grid4x4(white);
      case RUNTIME_PREVIEW -> CssIcon.movie(white);
      case VIEWPORT_STABILIZE -> CssIcon.myLocation(white);
      case AUTO_KEY, RECORD_GIF -> CssIcon.fiberSmartRecord(white);
      case SNAP_GRID -> CssIcon.borderAll(white);
      case SNAP_ENTITY -> CssIcon.joinInner(white);
      case ORBIT -> CssIcon.threeSixty(white);
      case ORBIT_ALIGN -> CssIcon.explore(white);
      case HELP -> CssIcon.speech(white);
      case ADD_AUDIO_CUE -> CssIcon.plusBold(white);
      case ADD_EXPRESSION_CUE -> CssIcon.theater(white);
      case MANAGE_EVENTS, VERIFY -> CssIcon.list(white);
      case REGISTER -> CssIcon.save(white);
      case COPY_CODE -> CssIcon.copy(white);
    };
    double base = Math.max(1, Math.max(glyph.getPrefWidth(), glyph.getPrefHeight()));
    double scale = size / base;
    glyph.setScaleX(scale);
    glyph.setScaleY(scale);
    return glyph;
  }

  private static Palette paletteFor(Kind kind) {
    return switch (kind) {
      case ADD_ALL, SAVE_CLIP, LOAD_CLIP, PLAY, REGISTER, ADD_AUDIO_CUE, ADD_EXPRESSION_CUE, VERIFY ->
          palette("#83dca9", "#23875f", "#0d3d31", "#62e2a2");
      case PREVIOUS, NEXT, FOCUS, ZOOM_FIT, REWIND, UNDO, REDO, SYNC, COPY_CODE,
          VIEWPORT_STABILIZE -> palette("#91d8ff", "#318ec2", "#123d5c", "#62c8ff");
      case DISTRIBUTE, REVERSE, STRETCH, COMPRESS -> palette("#c7b7f7", "#715aa8", "#30264f", "#ac8cff");
      case RIPPLE, COMPACT_EXPORT, PAUSE, LOOP, LOOP_IN, LOOP_OUT, PRESET ->
          palette("#ffd277", "#bd7621", "#51300f", "#ffc04f");
      case RUNTIME_PREVIEW, ORBIT, ORBIT_ALIGN -> palette("#d0b3ff", "#7954ad", "#34224f", "#b98cff");
      case STOP, CLEAR, AUTO_KEY, RECORD_GIF -> palette("#ffaaa5", "#b84747", "#521d22", "#ff716d");
      default -> palette("#c9d8e5", "#60788d", "#263744", "#a9d7f5");
    };
  }

  private static Palette palette(String light, String mid, String dark, String glow) {
    return new Palette(Color.web(light), Color.web(mid), Color.web(dark), Color.web(glow));
  }
}
