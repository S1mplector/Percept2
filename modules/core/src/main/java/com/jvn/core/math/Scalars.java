package com.jvn.core.math;

/**
 * Shared scalar math helpers used across engine subsystems.
 */
public final class Scalars {
  private static final double DEFAULT_EPSILON = 1e-9;

  private Scalars() {
  }

  public static double clamp(double value, double min, double max) {
    double lo = Math.min(min, max);
    double hi = Math.max(min, max);
    return value < lo ? lo : Math.min(value, hi);
  }

  public static float clamp(float value, float min, float max) {
    float lo = Math.min(min, max);
    float hi = Math.max(min, max);
    return value < lo ? lo : Math.min(value, hi);
  }

  public static int clamp(int value, int min, int max) {
    int lo = Math.min(min, max);
    int hi = Math.max(min, max);
    return value < lo ? lo : Math.min(value, hi);
  }

  public static double clamp01(double value) {
    return clamp(value, 0.0, 1.0);
  }

  public static float clamp01(float value) {
    return clamp(value, 0.0f, 1.0f);
  }

  /**
   * Returns the interpolation factor {@code t} such that
   * {@code value = a + (b - a) * t}. The result is not clamped.
   */
  public static double inverseLerp(double a, double b, double value) {
    double span = b - a;
    if (approxEquals(span, 0.0)) return 0.0;
    return (value - a) / span;
  }

  /**
   * Remaps a value from one scalar range into another range.
   */
  public static double remap(double value, double inMin, double inMax, double outMin, double outMax) {
    double t = inverseLerp(inMin, inMax, value);
    return outMin + (outMax - outMin) * t;
  }

  public static boolean approxEquals(double a, double b) {
    return approxEquals(a, b, DEFAULT_EPSILON);
  }

  public static boolean approxEquals(double a, double b, double epsilon) {
    if (Double.doubleToLongBits(a) == Double.doubleToLongBits(b)) return true;
    if (!Double.isFinite(a) || !Double.isFinite(b)) return false;
    double scale = Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
    return Math.abs(a - b) <= Math.abs(epsilon) * scale;
  }
}
