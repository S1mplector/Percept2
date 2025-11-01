package com.jvn.core.scene2d;

public class NinePatch {
  private final String imagePath;
  private final double srcW;
  private final double srcH;
  private final double left;
  private final double right;
  private final double top;
  private final double bottom;

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

  public void draw(Blitter2D b, double x, double y, double w, double h) {
    double l = Math.min(left, Math.max(0, w / 2));
    double r = Math.min(right, Math.max(0, w / 2));
    double t = Math.min(top, Math.max(0, h / 2));
    double bo = Math.min(bottom, Math.max(0, h / 2));

    double sx0 = 0,    sx1 = left,         sx2 = srcW - right,  sx3 = srcW;
    double sy0 = 0,    sy1 = top,          sy2 = srcH - bottom, sy3 = srcH;

    double dx0 = x,    dx1 = x + l,        dx2 = x + w - r,     dx3 = x + w;
    double dy0 = y,    dy1 = y + t,        dy2 = y + h - bo,    dy3 = y + h;

    // corners
    if (l > 0 && t > 0) b.drawImageRegion(imagePath, sx0, sy0, sx1 - sx0, sy1 - sy0, dx0, dy0, dx1 - dx0, dy1 - dy0);
    if (r > 0 && t > 0) b.drawImageRegion(imagePath, sx2, sy0, sx3 - sx2, sy1 - sy0, dx2, dy0, dx3 - dx2, dy1 - dy0);
    if (l > 0 && bo > 0) b.drawImageRegion(imagePath, sx0, sy2, sx1 - sx0, sy3 - sy2, dx0, dy2, dx1 - dx0, dy3 - dy2);
    if (r > 0 && bo > 0) b.drawImageRegion(imagePath, sx2, sy2, sx3 - sx2, sy3 - sy2, dx2, dy2, dx3 - dx2, dy3 - dy2);

    // edges
    if (t > 0 && dx2 > dx1) b.drawImageRegion(imagePath, sx1, sy0, sx2 - sx1, sy1 - sy0, dx1, dy0, dx2 - dx1, dy1 - dy0);
    if (bo > 0 && dx2 > dx1) b.drawImageRegion(imagePath, sx1, sy2, sx2 - sx1, sy3 - sy2, dx1, dy2, dx2 - dx1, dy3 - dy2);
    if (l > 0 && dy2 > dy1) b.drawImageRegion(imagePath, sx0, sy1, sx1 - sx0, sy2 - sy1, dx0, dy1, dx1 - dx0, dy2 - dy1);
    if (r > 0 && dy2 > dy1) b.drawImageRegion(imagePath, sx2, sy1, sx3 - sx2, sy2 - sy1, dx2, dy1, dx3 - dx2, dy2 - dy1);

    // center
    if (dx2 > dx1 && dy2 > dy1) b.drawImageRegion(imagePath, sx1, sy1, sx2 - sx1, sy2 - sy1, dx1, dy1, dx2 - dx1, dy2 - dy1);
  }
}
