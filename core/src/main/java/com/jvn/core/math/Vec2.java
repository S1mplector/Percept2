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

  // --- Additional operations --------------------------------------------------

  /**
   * Squared length. Prefer this over {@link #length()} when comparing magnitudes —
   * skips the {@code sqrt}.
   */
  public double lengthSq() { return x * x + y * y; }

  /** Set both components to zero and return {@code this}. */
  public Vec2 zero() { this.x = 0; this.y = 0; return this; }

  /** Copy components from {@code o} into this vector and return {@code this}. */
  public Vec2 set(Vec2 o) { this.x = o.x; this.y = o.y; return this; }

  /** Negate both components in place. */
  public Vec2 negate() { this.x = -x; this.y = -y; return this; }

  /**
   * Fused multiply-add: {@code this += o * s}. Useful in integrators to avoid
   * allocating a scaled temporary.
   */
  public Vec2 addScaled(Vec2 o, double s) { this.x += o.x * s; this.y += o.y * s; return this; }

  /**
   * Rotate this vector 90° counter-clockwise in place: {@code (x, y) → (-y, x)}.
   */
  public Vec2 perp() { double nx = -y; this.y = x; this.x = nx; return this; }

  /**
   * 2D cross product (z-component of the 3D cross). Positive when {@code b}
   * is counter-clockwise from {@code a}, negative when clockwise.
   */
  public static double cross(Vec2 a, Vec2 b) { return a.x * b.y - a.y * b.x; }

  /** Squared Euclidean distance between two vectors. */
  public static double distanceSq(Vec2 a, Vec2 b) {
    double dx = a.x - b.x;
    double dy = a.y - b.y;
    return dx * dx + dy * dy;
  }

  /** Euclidean distance between two vectors. */
  public static double distance(Vec2 a, Vec2 b) { return Math.sqrt(distanceSq(a, b)); }

  /**
   * Linear interpolation between {@code a} and {@code b}: returns a new vector
   * {@code a + (b - a) * t}. {@code t} is not clamped.
   */
  public static Vec2 lerp(Vec2 a, Vec2 b, double t) {
    return new Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
  }

  /**
   * In-place linear interpolation: mutates this vector towards {@code target} by
   * factor {@code t} and returns {@code this}.
   */
  public Vec2 lerpTo(Vec2 target, double t) {
    this.x += (target.x - x) * t;
    this.y += (target.y - y) * t;
    return this;
  }

  /**
   * Angle of this vector from the +X axis in radians, in {@code [-π, π]}.
   * Returns {@code 0} for a zero-length vector.
   */
  public double angle() { return Math.atan2(y, x); }

  /**
   * Rotate this vector by {@code radians} in place (CCW in a +Y-up frame;
   * CW in a +Y-down screen frame).
   */
  public Vec2 rotate(double radians) {
    double c = Math.cos(radians);
    double s = Math.sin(radians);
    double nx = x * c - y * s;
    double ny = x * s + y * c;
    this.x = nx;
    this.y = ny;
    return this;
  }

  /**
   * Reflect this vector across the given {@code normal} (which should be unit length)
   * in place. Implements {@code v - 2 * (v·n) * n}.
   */
  public Vec2 reflect(Vec2 normal) {
    double d = 2.0 * (x * normal.x + y * normal.y);
    this.x -= d * normal.x;
    this.y -= d * normal.y;
    return this;
  }

  /**
   * Clamp this vector's magnitude to at most {@code maxLength}, in place.
   * If the vector is already shorter, it is left unchanged. Zero vectors are safe.
   */
  public Vec2 clampLength(double maxLength) {
    double lenSq = lengthSq();
    double max = Math.max(0.0, maxLength);
    if (lenSq > max * max && lenSq > 0.0) {
      double scale = max / Math.sqrt(lenSq);
      this.x *= scale;
      this.y *= scale;
    }
    return this;
  }

  /** Non-allocating static add: returns a new {@code Vec2} holding {@code a + b}. */
  public static Vec2 add(Vec2 a, Vec2 b) { return new Vec2(a.x + b.x, a.y + b.y); }

  /** Non-allocating static subtract: returns a new {@code Vec2} holding {@code a - b}. */
  public static Vec2 sub(Vec2 a, Vec2 b) { return new Vec2(a.x - b.x, a.y - b.y); }

  @Override
  public String toString() { return "Vec2(" + x + ", " + y + ")"; }
}
