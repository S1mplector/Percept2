package com.jvn.core.math;

/**
 * Minimal shared contract for hit-testable 2D primitives.
 */
public interface Shape2D {
  boolean contains(double x, double y);
  Rect bounds(Rect out);
}
