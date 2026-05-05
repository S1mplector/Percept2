package com.jvn.core.scene2d;

/**
 * Scalable image renderer that preserves border detail by splitting the source
 * image into 9 rectangular patches and stretching only the centre and edge strips.
 *
 * <pre>
 *   Source image                       Destination (any size)
 *   ┌────┬──────────┬────┐            ┌────┬────────────────┬────┐
 *   │ TL │  top     │ TR │            │ TL │  top (stretch) │ TR │
 *   ├────┼──────────┼────┤            ├────┼────────────────┼────┤
 *   │ L  │  centre  │ R  │   ──▶     │ L  │  centre (tile) │ R  │
 *   ├────┼──────────┼────┤            ├────┼────────────────┼────┤
 *   │ BL │  bottom  │ BR │            │ BL │ bottom(stretch)│ BR │
 *   └────┴──────────┴────┘            └────┴────────────────┴────┘
 *     ◄l►             ◄r►               corners stay fixed
 * </pre>
 *
 * <p>The four corner patches are drawn at their original size. The four edge
 * patches are stretched along one axis. The centre patch is stretched in both
 * directions. This allows UI panels, dialogue boxes, and buttons to scale to
 * arbitrary sizes without distorting their borders.</p>
 *
 * @see Panel2D
 */
public class NinePatch {

  /** Asset path to the nine-patch image. */
  private final String imagePath;

  /** Full source image width in pixels. */
  private final double srcW;

  /** Full source image height in pixels. */
  private final double srcH;

  /** Left border inset in source pixels. */
  private final double left;

  /** Right border inset in source pixels. */
  private final double right;

  /** Top border inset in source pixels. */
  private final double top;

  /** Bottom border inset in source pixels. */
  private final double bottom;

  /**
   * Construct a nine-patch descriptor.
   *
   * @param imagePath asset path to the nine-patch image
   * @param srcW      source image width in pixels
   * @param srcH      source image height in pixels
   * @param left      left border inset (pixels kept unscaled)
   * @param right     right border inset
   * @param top       top border inset
   * @param bottom    bottom border inset
   */
  public NinePatch(String imagePath, double srcW, double srcH,
                   double left, double right, double top, double bottom) {
    this.imagePath = imagePath;
    this.srcW = srcW;
    this.srcH = srcH;
    this.left = left;
    this.right = right;
    this.top = top;
    this.bottom = bottom;
  }

  /**
   * Draw this nine-patch at the given destination rectangle.
   *
   * <p>Border insets are clamped so they never exceed half the destination
   * dimension, preventing overlap when the target is very small.</p>
   *
   * @param b the blitter
   * @param x destination X
   * @param y destination Y
   * @param w destination width
   * @param h destination height
   */
  public void draw(Blitter2D b, double x, double y, double w, double h) {
    // Clamp border insets so they don't exceed half the destination dimension
    double l = Math.min(left, Math.max(0, w / 2));
    double r = Math.min(right, Math.max(0, w / 2));
    double t = Math.min(top, Math.max(0, h / 2));
    double bo = Math.min(bottom, Math.max(0, h / 2));

    // Source region boundaries
    double sx0 = 0,    sx1 = left,         sx2 = srcW - right,  sx3 = srcW;
    double sy0 = 0,    sy1 = top,          sy2 = srcH - bottom, sy3 = srcH;

    // Destination region boundaries
    double dx0 = x,    dx1 = x + l,        dx2 = x + w - r,     dx3 = x + w;
    double dy0 = y,    dy1 = y + t,        dy2 = y + h - bo,    dy3 = y + h;

    // 4 corners (fixed size)
    if (l > 0 && t > 0) b.drawImageRegion(imagePath, sx0, sy0, sx1 - sx0, sy1 - sy0, dx0, dy0, dx1 - dx0, dy1 - dy0);
    if (r > 0 && t > 0) b.drawImageRegion(imagePath, sx2, sy0, sx3 - sx2, sy1 - sy0, dx2, dy0, dx3 - dx2, dy1 - dy0);
    if (l > 0 && bo > 0) b.drawImageRegion(imagePath, sx0, sy2, sx1 - sx0, sy3 - sy2, dx0, dy2, dx1 - dx0, dy3 - dy2);
    if (r > 0 && bo > 0) b.drawImageRegion(imagePath, sx2, sy2, sx3 - sx2, sy3 - sy2, dx2, dy2, dx3 - dx2, dy3 - dy2);

    // 4 edges (stretched along one axis)
    if (t > 0 && dx2 > dx1) b.drawImageRegion(imagePath, sx1, sy0, sx2 - sx1, sy1 - sy0, dx1, dy0, dx2 - dx1, dy1 - dy0);
    if (bo > 0 && dx2 > dx1) b.drawImageRegion(imagePath, sx1, sy2, sx2 - sx1, sy3 - sy2, dx1, dy2, dx2 - dx1, dy3 - dy2);
    if (l > 0 && dy2 > dy1) b.drawImageRegion(imagePath, sx0, sy1, sx1 - sx0, sy2 - sy1, dx0, dy1, dx1 - dx0, dy2 - dy1);
    if (r > 0 && dy2 > dy1) b.drawImageRegion(imagePath, sx2, sy1, sx3 - sx2, sy2 - sy1, dx2, dy1, dx3 - dx2, dy2 - dy1);

    // Centre patch (stretched in both directions)
    if (dx2 > dx1 && dy2 > dy1) b.drawImageRegion(imagePath, sx1, sy1, sx2 - sx1, sy2 - sy1, dx1, dy1, dx2 - dx1, dy2 - dy1);
  }
}
