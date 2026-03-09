package com.jvn.core.graphics;

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
  public record Transform(double scale, double offsetX, double offsetY, double targetWidth, double targetHeight) {}

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
    double tw = targetWidth <= 0 ? viewportWidth : targetWidth;
    double th = targetHeight <= 0 ? viewportHeight : targetHeight;
    double vw = viewportWidth <= 0 ? 1 : viewportWidth;
    double vh = viewportHeight <= 0 ? 1 : viewportHeight;
    double scale = Math.min(vw / tw, vh / th);
    double ox = (vw - tw * scale) * 0.5;
    double oy = (vh - th * scale) * 0.5;
    return new Transform(scale, ox, oy, tw, th);
  }
}
