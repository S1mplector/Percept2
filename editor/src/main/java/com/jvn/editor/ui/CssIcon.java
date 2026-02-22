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
  private static final String PATH_PALETTE =
      "M12 2C6.49 2 2 6.49 2 12s4.49 10 10 10a2.5 2.5 0 0 0 2.5-2.5c0-.61-.23-1.2-.63-1.63-.37-.4-.58-.92-.58-1.51 0-1.31 1.07-2.36 2.37-2.36H17c2.76 0 5-2.24 5-5 0-4.42-4.03-8-10-8zm-5.5 9a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3-4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm5 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z";

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

  private static Region icon(String svgPath, String color, double size) {
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
