package com.jvn.core.math;

public class Rect {
  public double x;
  public double y;
  public double w;
  public double h;

  public Rect() { this(0, 0, 0, 0); }
  public Rect(double x, double y, double w, double h) { this.x = x; this.y = y; this.w = w; this.h = h; }

  public double left() { return x; }
  public double right() { return x + w; }
  public double top() { return y; }
  public double bottom() { return y + h; }

  public boolean contains(double px, double py) {
    return px >= left() && px <= right() && py >= top() && py <= bottom();
  }

  public boolean intersects(Rect o) {
    return right() >= o.left() && left() <= o.right() && bottom() >= o.top() && top() <= o.bottom();
  }
}
