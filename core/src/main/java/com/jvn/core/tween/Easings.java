package com.jvn.core.tween;

public final class Easings {
  private Easings() {}
  public static double linear(double t) { return t; }
  public static double easeInQuad(double t) { return t * t; }
  public static double easeOutQuad(double t) { return t * (2 - t); }
  public static double easeInOutQuad(double t) { return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t; }
}
