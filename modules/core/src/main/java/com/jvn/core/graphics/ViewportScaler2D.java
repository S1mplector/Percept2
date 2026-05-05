package com.jvn.core.graphics;

import com.jvn.core.math.Rect;

/**
 * Utility for fitting a logical 2D resolution into an arbitrary physical viewport
 * with uniform scaling and centred letterboxing.
 *
 * <p>Games and visual-novel projects define a <b>target resolution</b> (e.g. 1920×1080)
 * that represents the logical coordinate space used by all artwork, UI, and layout.
 * At runtime the actual window may be a different size or aspect ratio. This class
 * computes the uniform scale factor and pixel offsets needed to centre the logical
 * canvas inside the physical viewport, adding <b>letterbox / pillarbox bars</b>
 * as necessary.</p>
 *
 * <pre>
 *   Physical viewport
 *   ┌──────────────────────────────────────┐
 *   │  pillarbox  ┌──────────────┐  bar   │
 *   │   (offsetX) │  logical     │        │
 *   │             │  canvas      │        │
 *   │             │  (target)    │        │
 *   │             └──────────────┘        │
 *   └──────────────────────────────────────┘
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * var t = ViewportScaler2D.fit(1920, 1080, windowW, windowH);
 * gc.translate(t.offsetX(), t.offsetY());
 * gc.scale(t.scale(), t.scale());
 * // ...draw everything in 1920×1080 logical space...
 * }</pre>
 *
 * @see Camera2D
 */
public final class ViewportScaler2D {

  /** Static utility — no instances. */
  private ViewportScaler2D() {}

  /**
   * Immutable result of a {@link #fit} computation.
   *
   * @param scale        uniform scale factor (logical → physical pixels)
   * @param offsetX      horizontal offset (px) to centre the canvas (pillarbox)
   * @param offsetY      vertical offset (px) to centre the canvas (letterbox)
   * @param targetWidth  effective logical width used (may differ from input if ≤ 0)
   * @param targetHeight effective logical height used
   */
  public record Transform(double scale, double offsetX, double offsetY, double targetWidth, double targetHeight) {
    public double contentWidth() {
      return targetWidth * scale;
    }

    public double contentHeight() {
      return targetHeight * scale;
    }

    public double minX() {
      return offsetX;
    }

    public double minY() {
      return offsetY;
    }

    public double maxX() {
      return offsetX + contentWidth();
    }

    public double maxY() {
      return offsetY + contentHeight();
    }

    public boolean containsScreen(double x, double y) {
      return x >= minX() && x <= maxX() && y >= minY() && y <= maxY();
    }

    public boolean containsLogical(double x, double y) {
      return x >= 0.0 && x <= targetWidth && y >= 0.0 && y <= targetHeight;
    }

    public double logicalToScreenX(double logicalX) {
      return offsetX + logicalX * scale;
    }

    public double logicalToScreenY(double logicalY) {
      return offsetY + logicalY * scale;
    }

    public double screenToLogicalX(double screenX) {
      return (screenX - offsetX) / Math.max(1e-9, scale);
    }

    public double screenToLogicalY(double screenY) {
      return (screenY - offsetY) / Math.max(1e-9, scale);
    }

    public Rect logicalBounds(Rect out) {
      Rect result = out == null ? new Rect() : out;
      result.x = 0.0;
      result.y = 0.0;
      result.w = targetWidth;
      result.h = targetHeight;
      return result;
    }

    public Rect screenBounds(Rect out) {
      Rect result = out == null ? new Rect() : out;
      result.x = offsetX;
      result.y = offsetY;
      result.w = contentWidth();
      result.h = contentHeight();
      return result;
    }
  }

  /**
   * Compute the uniform scale and centred offset to fit a logical resolution
   * into a physical viewport.
   *
   * <p>The scale factor is the <b>minimum</b> of the horizontal and vertical
   * ratios, guaranteeing the entire logical area is visible (no cropping).
   * Non-positive target or viewport dimensions are replaced with safe defaults
   * to prevent division-by-zero.</p>
   *
   * @param targetWidth    logical canvas width (e.g. 1920); ≤ 0 defaults to viewport width
   * @param targetHeight   logical canvas height (e.g. 1080); ≤ 0 defaults to viewport height
   * @param viewportWidth  physical viewport width in pixels
   * @param viewportHeight physical viewport height in pixels
   * @return a {@link Transform} with scale, offsets, and effective target dimensions
   */
  public static Transform fit(double targetWidth, double targetHeight, double viewportWidth, double viewportHeight) {
    double tw = targetWidth > 0 ? targetWidth : (viewportWidth > 0 ? viewportWidth : 1.0);
    double th = targetHeight > 0 ? targetHeight : (viewportHeight > 0 ? viewportHeight : 1.0);
    double vw = viewportWidth <= 0 ? 1 : viewportWidth;
    double vh = viewportHeight <= 0 ? 1 : viewportHeight;
    double scale = Math.min(vw / tw, vh / th);
    double ox = (vw - tw * scale) * 0.5;
    double oy = (vh - th * scale) * 0.5;
    return new Transform(scale, ox, oy, tw, th);
  }
}
