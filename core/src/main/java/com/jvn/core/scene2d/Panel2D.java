package com.jvn.core.scene2d;

/**
 * A rectangular panel entity with configurable fill, stroke, and optional
 * {@link NinePatch} rendering.
 *
 * <p>{@code Panel2D} is used for UI backgrounds, dialogue boxes, health bars,
 * and any rectangular visual element. When a {@link NinePatch} is assigned, it
 * is drawn instead of the flat fill/stroke, allowing the panel to scale
 * gracefully with bordered artwork.</p>
 *
 * @see NinePatch
 * @see Entity2D
 */
public class Panel2D extends Entity2D {

  /** Panel width in logical pixels. */
  private double width;

  /** Panel height in logical pixels. */
  private double height;

  /** Fill colour — red channel [0.0, 1.0]. */
  private double r = 0;

  /** Fill colour — green channel [0.0, 1.0]. */
  private double g = 0;

  /** Fill colour — blue channel [0.0, 1.0]. */
  private double blue = 0;

  /** Fill opacity [0.0, 1.0]. Default 0.6 for a semi-transparent overlay. */
  private double a = 0.6;

  /** Stroke colour — red channel [0.0, 1.0]. */
  private double strokeR = 1;

  /** Stroke colour — green channel [0.0, 1.0]. */
  private double strokeG = 1;

  /** Stroke colour — blue channel [0.0, 1.0]. */
  private double strokeB = 1;

  /** Stroke opacity [0.0, 1.0]. */
  private double strokeA = 1;

  /** Stroke line width in pixels. 0 = no stroke. */
  private double strokeWidth = 0;

  /** Optional nine-patch for scalable bordered artwork; overrides flat fill when set. */
  private NinePatch ninePatch;

  /**
   * Construct a panel with the given dimensions and default semi-transparent black fill.
   *
   * @param width  panel width in logical pixels
   * @param height panel height in logical pixels
   */
  public Panel2D(double width, double height) {
    this.width = width;
    this.height = height;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** Resize the panel. */
  public void setSize(double w, double h) { this.width = w; this.height = h; }

  /** @return panel width */
  public double getWidth() { return width; }

  /** @return panel height */
  public double getHeight() { return height; }

  /**
   * Set the fill colour and opacity.
   *
   * @param r red   [0.0, 1.0]
   * @param g green [0.0, 1.0]
   * @param b blue  [0.0, 1.0]
   * @param a alpha [0.0, 1.0]
   */
  public void setFill(double r, double g, double b, double a) { this.r = r; this.g = g; this.blue = b; this.a = a; }

  /** @return fill red component */
  public double getFillR() { return r; }

  /** @return fill green component */
  public double getFillG() { return g; }

  /** @return fill blue component */
  public double getFillB() { return blue; }

  /** @return fill alpha (opacity) */
  public double getFillA() { return a; }

  /**
   * Set the stroke (border) colour, opacity, and line width.
   *
   * @param r     red   [0.0, 1.0]
   * @param g     green [0.0, 1.0]
   * @param b     blue  [0.0, 1.0]
   * @param a     alpha [0.0, 1.0]
   * @param width stroke line width in pixels (clamped to ≥ 0)
   */
  public void setStroke(double r, double g, double b, double a, double width) {
    this.strokeR = r; this.strokeG = g; this.strokeB = b; this.strokeA = a; this.strokeWidth = Math.max(0, width);
  }

  /** @return stroke line width (0 = no stroke) */
  public double getStrokeWidth() { return strokeWidth; }

  /**
   * Set a nine-patch to use for rendering instead of flat fill/stroke.
   * Pass {@code null} to revert to flat rendering.
   *
   * @param np the nine-patch, or {@code null}
   */
  public void setNinePatch(NinePatch np) { this.ninePatch = np; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Rendering
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Draw the panel. If a {@link NinePatch} is set, it is used; otherwise a
   * filled rectangle is drawn with an optional stroked border.
   */
  @Override
  public void render(Blitter2D b) {
    b.push();
    if (ninePatch != null) {
      ninePatch.draw(b, 0, 0, width, height);
    } else {
      b.setGlobalAlpha(a);
      b.setFill(r, g, blue, a);
      b.fillRect(0, 0, width, height);
      if (strokeWidth > 0) {
        b.setGlobalAlpha(strokeA);
        b.setStroke(strokeR, strokeG, strokeB, strokeA);
        b.setStrokeWidth(strokeWidth);
        b.strokeRect(0, 0, width, height);
      }
    }
    b.pop();
  }
}

