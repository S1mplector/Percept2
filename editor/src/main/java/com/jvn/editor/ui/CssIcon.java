package com.jvn.editor.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Lightweight CSS-only icon factory using SVG path shapes rendered
 * via {@code -fx-shape} on a tiny {@link Region}. No image assets needed.
 */
public final class CssIcon {
  private CssIcon() {}

  // ── SVG path data ──

  private static final String PATH_PLUS =
      "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
  private static final String PATH_MINUS =
      "M19 13H5v-2h14v2z";
  private static final String PATH_ARROW_UP =
      "M4 15l8-8 8 8z";
  private static final String PATH_ARROW_DOWN =
      "M4 9l8 8 8-8z";
  private static final String PATH_SORT =
      "M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z";
  private static final String PATH_FOLDER =
      "M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
  private static final String PATH_CLEAR_X =
      "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
  private static final String PATH_UNDO =
      "M12.5 8c-2.65 0-5.05 1.04-6.83 2.73L2 7v9h9l-3.62-3.62A8.96 8.96 0 0 1 12.5 10c3.86 0 7.12 2.43 8.38 5.85L22.9 15c-1.6-4.35-5.76-7.5-10.4-7z";
  private static final String PATH_REDO =
      "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z";
  private static final String PATH_SPEECH =
      "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z";
  private static final String PATH_LIST =
      "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z";
  private static final String PATH_GRID =
      "M3 3v8h8V3H3zm6 6H5V5h4v4zm-6 4v8h8v-8H3zm6 6H5v-4h4v4zm4-16v8h8V3h-8zm6 6h-4V5h4v4zm-6 4v8h8v-8h-8zm6 6h-4v-4h4v4z";
  private static final String PATH_DOWNLOAD =
      "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
  private static final String PATH_SAVE =
      "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z";
  private static final String PATH_EXPAND =
      "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z";
  private static final String PATH_PALETTE =
      "M12 2C6.49 2 2 6.49 2 12s4.49 10 10 10a2.5 2.5 0 0 0 2.5-2.5c0-.61-.23-1.2-.63-1.63-.37-.4-.58-.92-.58-1.51 0-1.31 1.07-2.36 2.37-2.36H17c2.76 0 5-2.24 5-5 0-4.42-4.03-8-10-8zm-5.5 9a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3-4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm5 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z";
  private static final String PATH_CHECK =
      "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
  private static final String PATH_LINK =
      "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z";
  private static final String PATH_HOME =
      "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
  private static final String PATH_COPY =
      "M16 1H4c-1.1 0-2 .9-2 2v12h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z";
  private static final String PATH_PLAY =
      "M8 5v14l11-7z";
  private static final String PATH_POP_OUT =
      "M19 19H5V5h7V3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z";
  private static final String PATH_DOCK =
      "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14zm-8-2h2V7h-2v10z";
  private static final String PATH_RECT_SELECT =
      "M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z";
  private static final String PATH_POLYGON =
      "M12 2l10 7.5-4 12H6L2 9.5z";
  private static final String PATH_FREEHAND =
      "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zm14.71-9.04c.39-.39.39-1.02 0-1.41l-2.5-2.5a.996.996 0 0 0-1.41 0l-1.96 1.96 3.75 3.75 2.12-2.12z";
  private static final String PATH_VISIBILITY =
      "M12 6.5c-4.77 0-8.8 2.94-10.5 7.5 1.7 4.56 5.73 7.5 10.5 7.5s8.8-2.94 10.5-7.5c-1.7-4.56-5.73-7.5-10.5-7.5zm0 12.5c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
  private static final String PATH_VISIBILITY_OFF =
      "M2.28 2.28 1 3.56l3.05 3.05C2.16 8.17 1.11 10.14.5 12c1.73 4.39 5.99 7.5 11 7.5 1.88 0 3.67-.44 5.26-1.22l3.18 3.18 1.28-1.28L2.28 2.28zm8.79 8.79 3.17 3.17a2.98 2.98 0 0 1-3.17-3.17zM11.5 17c-2.76 0-5-2.24-5-5 0-.78.18-1.51.5-2.17l1.53 1.53a2.98 2.98 0 0 0 3.95 3.95l1.53 1.53c-.66.32-1.39.5-2.17.5zm9.94-3.17A12.59 12.59 0 0 0 22.5 12c-1.73-4.39-5.99-7.5-11-7.5-1.64 0-3.2.36-4.59 1.01l1.55 1.55c.96-.36 1.99-.56 3.04-.56 4.24 0 7.16 2.66 8.5 4.5-.51.69-1.27 1.62-2.28 2.45l1.72 1.72z";

  // ── Factory methods ──

  public static Region plus(String color)     { return icon(PATH_PLUS, color, 14); }
  public static Region minus(String color)    { return icon(PATH_MINUS, color, 14); }
  public static Region arrowUp(String color)  { return icon(PATH_ARROW_UP, color, 12); }
  public static Region arrowDown(String color){ return icon(PATH_ARROW_DOWN, color, 12); }
  public static Region sort(String color)     { return icon(PATH_SORT, color, 14); }
  public static Region folder(String color)   { return icon(PATH_FOLDER, color, 14); }
  public static Region clearX(String color)   { return icon(PATH_CLEAR_X, color, 13); }
  public static Region undo(String color)     { return icon(PATH_UNDO, color, 14); }
  public static Region redo(String color)     { return icon(PATH_REDO, color, 14); }
  public static Region speech(String color)   { return icon(PATH_SPEECH, color, 14); }
  public static Region list(String color)     { return icon(PATH_LIST, color, 14); }
  public static Region grid(String color)     { return icon(PATH_GRID, color, 14); }
  public static Region palette(String color)  { return icon(PATH_PALETTE, color, 14); }
  public static Region download(String color)  { return icon(PATH_DOWNLOAD, color, 14); }
  public static Region save(String color)      { return icon(PATH_SAVE, color, 14); }
  public static Region expand(String color)    { return icon(PATH_EXPAND, color, 14); }
  public static Region check(String color)     { return icon(PATH_CHECK, color, 14); }
  public static Region link(String color)      { return icon(PATH_LINK, color, 14); }
  public static Region home(String color)      { return icon(PATH_HOME, color, 14); }
  public static Region copy(String color)      { return icon(PATH_COPY, color, 14); }
  public static Region play(String color)      { return icon(PATH_PLAY, color, 14); }
  public static Region popOut(String color)    { return icon(PATH_POP_OUT, color, 14); }
  public static Region dock(String color)      { return icon(PATH_DOCK, color, 14); }
  public static Region rectSelect(String color) { return icon(PATH_RECT_SELECT, color, 14); }
  public static Region polygon(String color)    { return icon(PATH_POLYGON, color, 14); }
  public static Region freehand(String color)   { return icon(PATH_FREEHAND, color, 14); }
  public static Region visibility(String color) { return icon(PATH_VISIBILITY, color, 14); }
  public static Region visibilityOff(String color) { return icon(PATH_VISIBILITY_OFF, color, 14); }

  /** Convenience: icon at default muted color. */
  public static Region plus()     { return plus("#b0b8c8"); }
  public static Region minus()    { return minus("#b0b8c8"); }
  public static Region arrowUp()  { return arrowUp("#b0b8c8"); }
  public static Region arrowDown(){ return arrowDown("#b0b8c8"); }
  public static Region sort()     { return sort("#b0b8c8"); }
  public static Region folder()   { return folder("#b0b8c8"); }
  public static Region clearX()   { return clearX("#b0b8c8"); }
  public static Region undo()     { return undo("#b0b8c8"); }
  public static Region redo()     { return redo("#b0b8c8"); }
  public static Region speech()   { return speech("#b0b8c8"); }
  public static Region list()     { return list("#b0b8c8"); }
  public static Region grid()     { return grid("#b0b8c8"); }
  public static Region palette()  { return palette("#b0b8c8"); }
  public static Region download() { return download("#b0b8c8"); }
  public static Region save()     { return save("#b0b8c8"); }
  public static Region expand()   { return expand("#b0b8c8"); }
  public static Region check()    { return check("#b0b8c8"); }
  public static Region link()     { return link("#b0b8c8"); }
  public static Region home()     { return home("#b0b8c8"); }
  public static Region copy()     { return copy("#b0b8c8"); }
  public static Region play()     { return play("#b0b8c8"); }
  public static Region popOut()   { return popOut("#b0b8c8"); }
  public static Region dock()     { return dock("#b0b8c8"); }
  public static Region rectSelect(){ return rectSelect("#b0b8c8"); }
  public static Region polygon()  { return polygon("#b0b8c8"); }
  public static Region freehand() { return freehand("#b0b8c8"); }
  public static Region visibility() { return visibility("#b0b8c8"); }
  public static Region visibilityOff() { return visibilityOff("#b0b8c8"); }

  /**
   * Creates a section header label with a leading CSS icon.
   */
  public static HBox iconLabel(Region icon, String text, String style) {
    Label label = new Label(text);
    label.setStyle(style);
    HBox box = new HBox(6, icon, label);
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  // ── Core builder ──

  public static Region icon(String svgPath, String color, double size) {
    Region r = new Region();
    r.setMinSize(size, size);
    r.setMaxSize(size, size);
    r.setPrefSize(size, size);
    r.setStyle(
        "-fx-shape: '" + svgPath + "';"
        + " -fx-background-color: " + color + ";"
        + " -fx-min-width: " + size + ";"
        + " -fx-min-height: " + size + ";"
        + " -fx-max-width: " + size + ";"
        + " -fx-max-height: " + size + ";"
    );
    return r;
  }
}
