package com.jvn.core.math;

/**
 * Mutable 2-component double-precision vector.
 *
 * <p>{@code Vec2} is the workhorse math primitive used throughout the engine for
 * positions, velocities, directions, and UV coordinates. It is intentionally
 * <b>mutable</b> to avoid allocation pressure in hot loops — most mutator
 * methods return {@code this} so they can be chained fluently:</p>
 *
 * <pre>{@code
 * Vec2 velocity = direction.copy().normalize().scale(speed);
 * position.add(velocity);
 * }</pre>
 *
 * <h2>Coordinate Convention</h2>
 * <p>The engine uses a <b>screen-space</b> coordinate system by default:
 * +X points right, +Y points <em>down</em>. Individual subsystems (e.g. physics)
 * may adopt different conventions internally.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>Not thread-safe. If a vector must be shared across threads, use
 * {@link #copy()} to create an independent snapshot.</p>
 *
 * @see Rect
 * @see Circle
 */
public class Vec2 {

  /** The X component of this vector. */
  public double x;

  /** The Y component of this vector. */
  public double y;

  /** Construct a zero vector {@code (0, 0)}. */
  public Vec2() { this(0, 0); }

  /**
   * Construct a vector with the given components.
   *
   * @param x the X component
   * @param y the Y component
   */
  public Vec2(double x, double y) { this.x = x; this.y = y; }

  /**
   * Set both components and return {@code this} for chaining.
   *
   * @param x new X value
   * @param y new Y value
   * @return this vector (mutated)
   */
  public Vec2 set(double x, double y) { this.x = x; this.y = y; return this; }

  /**
   * Create an independent deep copy of this vector.
   *
   * @return a new {@code Vec2} with identical component values
   */
  public Vec2 copy() { return new Vec2(x, y); }

  /**
   * Add another vector's components to this vector (component-wise).
   *
   * @param o the vector to add
   * @return this vector (mutated)
   */
  public Vec2 add(Vec2 o) { this.x += o.x; this.y += o.y; return this; }

  /**
   * Subtract another vector's components from this vector (component-wise).
   *
   * @param o the vector to subtract
   * @return this vector (mutated)
   */
  public Vec2 sub(Vec2 o) { this.x -= o.x; this.y -= o.y; return this; }

  /**
   * Uniformly scale both components by the given scalar.
   *
   * @param s the scale factor
   * @return this vector (mutated)
   */
  public Vec2 scale(double s) { this.x *= s; this.y *= s; return this; }

  /**
   * Compute the Euclidean length (magnitude) of this vector.
   *
   * @return √(x² + y²)
   */
  public double length() { return Math.sqrt(x * x + y * y); }

  /**
   * Normalize this vector to unit length in place.
   * If the vector is zero-length, it is left unchanged to avoid division by zero.
   *
   * @return this vector (mutated), now with length ≈ 1.0 (or unchanged if zero)
   */
  public Vec2 normalize() { double len = length(); if (len != 0) { x /= len; y /= len; } return this; }

  /**
   * Compute the dot product of two vectors.
   *
   * <p>The dot product is useful for projections, angle tests, and
   * determining whether two vectors point in similar directions:
   * {@code dot > 0} → same hemisphere, {@code dot < 0} → opposing, {@code dot == 0} → perpendicular.</p>
   *
   * @param a first vector
   * @param b second vector
   * @return a·b = a.x*b.x + a.y*b.y
   */
  public static double dot(Vec2 a, Vec2 b) { return a.x * b.x + a.y * b.y; }
}
