package com.jvn.core.math;

public class Circle {
  public double x;
  public double y;
  public double r;

  public Circle() { this(0, 0, 0); }
  public Circle(double x, double y, double r) { this.x = x; this.y = y; this.r = r; }

  public boolean contains(double px, double py) {
    double dx = px - x;
    double dy = py - y;
    return dx * dx + dy * dy <= r * r;
  }
}
