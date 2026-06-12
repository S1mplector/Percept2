package com.jvn.core.scene2d;

import com.jvn.core.math.Aabb2;
import com.jvn.core.math.Capsule2;
import com.jvn.core.math.Circle;
import com.jvn.core.math.Polygon2;
import com.jvn.core.math.Rect;
import com.jvn.core.math.Segment2;
import com.jvn.core.math.Shape2D;

/**
 * Platform-agnostic 2D rendering abstraction for the engine's scene graph.
 *
 * <p>{@code Blitter2D} defines the drawing contract that every platform backend
 * (JavaFX Canvas, Swing Graphics2D, headless test stub, etc.) must implement.
 * It provides:</p>
 * <ul>
 *   <li><b>State management</b> — fill/stroke colour, alpha, font, transform stack</li>
 *   <li><b>Primitives</b> — rectangles, circles, lines, text</li>
 *   <li><b>Images</b> — full-image and sub-region blitting</li>
 *   <li><b>Optional extensions</b> — vector paths, gradients, clipping, blend modes
 *       (default no-op so basic backends can ignore them)</li>
 * </ul>
 *
 * <h2>Transform Stack</h2>
 * <p>{@link #push()} / {@link #pop()} save and restore the current affine
 * transform (translation, rotation, scale). The stack is used by
 * {@link Scene2DBase} to isolate per-entity transforms during rendering.</p>
 *
 * <h2>Colour Model</h2>
 * <p>All colour arguments use <b>normalised doubles</b> in the range [0.0, 1.0]
 * for each of the R, G, B, and A channels.</p>
 *
 * @see Scene2D
 * @see Entity2D
 */
public interface Blitter2D {

  // ──────────────────────────────────────────────────────────────────────────
  //  Canvas clearing
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Clear the entire canvas to a solid colour.
   *
   * @param r red   [0.0, 1.0]
   * @param g green [0.0, 1.0]
   * @param b blue  [0.0, 1.0]
   * @param a alpha [0.0, 1.0]
   */
  void clear(double r, double g, double b, double a);

  // ──────────────────────────────────────────────────────────────────────────
  //  Render state
  // ──────────────────────────────────────────────────────────────────────────

  /** Set the fill colour used by subsequent {@code fill*} calls. */
  void setFill(double r, double g, double b, double a);

  /** Set the stroke colour used by subsequent {@code stroke*} / {@code drawLine} calls. */
  void setStroke(double r, double g, double b, double a);

  /** Set the stroke line width in pixels. */
  void setStrokeWidth(double w);

  /**
   * Set the global alpha multiplier. This is combined with per-call alpha values
   * and affects all subsequent draw operations until changed.
   *
   * @param a alpha [0.0, 1.0]
   */
  void setGlobalAlpha(double a);

  /**
   * Set the font used by {@link #drawText} and {@link #measureTextWidth}.
   *
   * @param family font family name (e.g. "Arial", "Noto Sans")
   * @param size   font size in logical pixels
   * @param bold   whether to use bold weight
   */
  void setFont(String family, double size, boolean bold);

  // ──────────────────────────────────────────────────────────────────────────
  //  Transform stack
  // ──────────────────────────────────────────────────────────────────────────

  /** Save the current transform and render state onto the stack. */
  void push();

  /** Restore the most recently saved transform and render state from the stack. */
  void pop();

  /** Apply a translation to the current transform. */
  void translate(double x, double y);

  /** Apply a clockwise rotation (in degrees) to the current transform. */
  void rotateDeg(double degrees);

  /** Apply a non-uniform scale to the current transform. */
  void scale(double sx, double sy);

  /**
   * Multiply the current transform by an arbitrary affine matrix.
   *
   * <p>The parameters follow the conventional 2x3 layout:
   * <pre>
   * [ mxx mxy tx ]
   * [ myx myy ty ]
   * </pre>
   */
  default void transform(double mxx, double myx, double mxy, double myy, double tx, double ty) {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Shape primitives
  // ──────────────────────────────────────────────────────────────────────────

  /** Fill an axis-aligned rectangle with the current fill colour. */
  void fillRect(double x, double y, double w, double h);

  /** Stroke the outline of an axis-aligned rectangle. */
  void strokeRect(double x, double y, double w, double h);

  /** Fill a circle centred at {@code (cx, cy)} with the given radius. */
  void fillCircle(double cx, double cy, double radius);

  /** Stroke the outline of a circle centred at {@code (cx, cy)}. */
  void strokeCircle(double cx, double cy, double radius);

  /** Draw a line from {@code (x1, y1)} to {@code (x2, y2)} using the current stroke. */
  void drawLine(double x1, double y1, double x2, double y2);

  /** Draw a typed segment using the current stroke. */
  default void drawSegment(Segment2 segment) {
    if (segment == null) return;
    drawLine(segment.x1, segment.y1, segment.x2, segment.y2);
  }

  /** Fill a known core primitive with the current fill colour where supported. */
  default void fillShape(Shape2D shape) {
    if (shape == null) return;
    if (shape instanceof Rect r) {
      fillRect(r.x, r.y, r.w, r.h);
    } else if (shape instanceof Aabb2 aabb) {
      fillRect(aabb.minX, aabb.minY, aabb.width(), aabb.height());
    } else if (shape instanceof Circle c) {
      fillCircle(c.x, c.y, c.r);
    } else if (shape instanceof Polygon2 p) {
      fillPolygon(p.toArray());
    } else if (shape instanceof Capsule2 capsule) {
      fillCapsule(capsule);
    }
  }

  /** Stroke a known core primitive with the current stroke colour. */
  default void strokeShape(Shape2D shape) {
    if (shape == null) return;
    if (shape instanceof Rect r) {
      strokeRect(r.x, r.y, r.w, r.h);
    } else if (shape instanceof Aabb2 aabb) {
      strokeRect(aabb.minX, aabb.minY, aabb.width(), aabb.height());
    } else if (shape instanceof Circle c) {
      strokeCircle(c.x, c.y, c.r);
    } else if (shape instanceof Polygon2 p) {
      strokePolygon(p.toArray());
    } else if (shape instanceof Capsule2 capsule) {
      strokeCapsule(capsule);
    }
  }

  /** Best-effort filled capsule. Backends with round stroke caps render this exactly. */
  default void fillCapsule(Capsule2 capsule) {
    if (capsule == null) return;
    push();
    setStrokeWidth(capsule.r * 2.0);
    setStrokeCap("round");
    drawLine(capsule.x1, capsule.y1, capsule.x2, capsule.y2);
    pop();
  }

  /** Stroke a capsule outline with endpoint circles and side rails. */
  default void strokeCapsule(Capsule2 capsule) {
    if (capsule == null) return;
    double dx = capsule.x2 - capsule.x1;
    double dy = capsule.y2 - capsule.y1;
    double len = Math.hypot(dx, dy);
    if (len == 0.0) {
      strokeCircle(capsule.x1, capsule.y1, capsule.r);
      return;
    }
    double nx = -dy / len * capsule.r;
    double ny = dx / len * capsule.r;
    drawLine(capsule.x1 + nx, capsule.y1 + ny, capsule.x2 + nx, capsule.y2 + ny);
    drawLine(capsule.x1 - nx, capsule.y1 - ny, capsule.x2 - nx, capsule.y2 - ny);
    strokeCircle(capsule.x1, capsule.y1, capsule.r);
    strokeCircle(capsule.x2, capsule.y2, capsule.r);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Image drawing
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Draw an entire image at the given position and size.
   *
   * @param classpath asset path (classpath or filesystem, resolved by the backend)
   * @param x         destination X
   * @param y         destination Y
   * @param w         destination width
   * @param h         destination height
   */
  void drawImage(String classpath, double x, double y, double w, double h);

  /**
   * Draw a rectangular sub-region of an image (sprite-sheet / atlas slice).
   *
   * @param classpath asset path
   * @param sx source X within the image
   * @param sy source Y within the image
   * @param sw source width
   * @param sh source height
   * @param dx destination X
   * @param dy destination Y
   * @param dw destination width
   * @param dh destination height
   */
  void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                       double dx, double dy, double dw, double dh);

  // ──────────────────────────────────────────────────────────────────────────
  //  Text
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Draw a single line of text at the given position.
   *
   * @param text the string to draw
   * @param x    baseline X
   * @param y    baseline Y
   * @param size font size in logical pixels
   * @param bold whether to use bold weight
   */
  void drawText(String text, double x, double y, double size, boolean bold);

  /**
   * Measure the pixel width of a string without drawing it.
   *
   * @param text the string to measure
   * @param size font size in logical pixels
   * @param bold whether bold weight is used
   * @return width in logical pixels
   */
  double measureTextWidth(String text, double size, boolean bold);

  // ──────────────────────────────────────────────────────────────────────────
  //  Optional: vector path API (default no-op for basic backends)
  // ──────────────────────────────────────────────────────────────────────────

  /** Begin a new vector path. */
  default void beginPath() {}

  /** Move the pen to {@code (x, y)} without drawing. */
  default void moveTo(double x, double y) {}

  /** Add a line segment from the current pen position to {@code (x, y)}. */
  default void lineTo(double x, double y) {}

  /** Close the current sub-path by connecting back to the first point. */
  default void closePath() {}

  /** Fill the current path with the current fill colour. */
  default void fillPath() {}

  /** Stroke the current path outline. */
  default void strokePath() {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Optional: advanced stroke settings
  // ──────────────────────────────────────────────────────────────────────────

  /** Set the line cap style (e.g. "butt", "round", "square"). */
  default void setStrokeCap(String cap) {}

  /** Set the line join style (e.g. "miter", "round", "bevel"). */
  default void setStrokeJoin(String join) {}

  /** Set the miter limit for miter joins. */
  default void setMiterLimit(double limit) {}

  /**
   * Set a dash pattern for stroked lines.
   *
   * @param dashes array of on/off lengths
   * @param phase  initial offset into the dash pattern
   */
  default void setDash(double[] dashes, double phase) {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Optional: clipping
  // ──────────────────────────────────────────────────────────────────────────

  /** Set an axis-aligned rectangular clip region. Drawing outside is suppressed. */
  default void setClipRect(double x, double y, double w, double h) {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Optional: polygon helpers
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Fill a polygon defined by alternating x/y coordinates.
   *
   * @param xy flattened array {@code [x0, y0, x1, y1, ...]}
   */
  default void fillPolygon(double[] xy) {}

  /**
   * Stroke a polygon outline defined by alternating x/y coordinates.
   *
   * @param xy flattened array {@code [x0, y0, x1, y1, ...]}
   */
  default void strokePolygon(double[] xy) {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Optional: arc helpers (degrees)
  // ──────────────────────────────────────────────────────────────────────────

  /** Fill an arc (pie-slice) centred at {@code (cx, cy)}. Angles in degrees. */
  default void fillArc(double cx, double cy, double r, double startDeg, double sweepDeg) {}

  /** Stroke an arc outline. Angles in degrees. */
  default void strokeArc(double cx, double cy, double r, double startDeg, double sweepDeg) {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Optional: gradient fills
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Set a linear gradient as the current fill.
   *
   * @param x1         start X of the gradient line
   * @param y1         start Y
   * @param x2         end X
   * @param y2         end Y
   * @param positions  array of stop positions in [0.0, 1.0]
   * @param colorsRgba flattened [r,g,b,a] per stop (length = positions.length × 4)
   */
  default void setFillLinearGradient(double x1, double y1, double x2, double y2, double[] positions, double[] colorsRgba) {}

  /**
   * Set a radial gradient as the current fill.
   *
   * @param cx         centre X
   * @param cy         centre Y
   * @param r          radius
   * @param positions  array of stop positions in [0.0, 1.0]
   * @param colorsRgba flattened [r,g,b,a] per stop
   */
  default void setFillRadialGradient(double cx, double cy, double r, double[] positions, double[] colorsRgba) {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Optional: text alignment & blend mode
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Set text alignment for subsequent {@link #drawText} calls.
   *
   * @param hAlign horizontal: "left", "center", or "right"
   * @param vAlign vertical: "baseline", "top", "middle", or "bottom"
   */
  default void setTextAlign(String hAlign, String vAlign) {}

  /**
   * Set the compositing blend mode for subsequent draw calls.
   *
   * @param mode blend mode name, e.g. "normal", "additive", "multiply"
   */
  default void setBlendMode(String mode) {}

  /**
   * Set a full 4x5 colour matrix for subsequent image draws.
   *
   * <p>The matrix is laid out row-major as 20 doubles. Backends that do not
   * support image colour transforms may ignore this request.</p>
   */
  default void setColorMatrix(double[] matrix) {}

  /** Clear any previously configured colour matrix. */
  default void clearColorMatrix() {}

  /**
   * Set a blur radius for subsequent draw calls.
   *
   * <p>Backends may apply this as a post-effect during image and text draws.</p>
   */
  default void setBlurRadius(double radius) {}
}
