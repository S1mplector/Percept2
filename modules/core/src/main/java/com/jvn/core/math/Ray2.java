package com.jvn.core.math;

/**
 * Mutable 2D ray defined by an origin and direction vector.
 */
public class Ray2 {
  public double x;
  public double y;
  public double dx;
  public double dy;

  public Ray2() {
    this(0, 0, 0, 0);
  }

  public Ray2(double x, double y, double dx, double dy) {
    this.x = x;
    this.y = y;
    this.dx = dx;
    this.dy = dy;
  }

  public Ray2 set(double x, double y, double dx, double dy) {
    this.x = x;
    this.y = y;
    this.dx = dx;
    this.dy = dy;
    return this;
  }

  public Ray2 set(Vec2 origin, Vec2 direction) {
    double ox = origin == null ? 0.0 : origin.x;
    double oy = origin == null ? 0.0 : origin.y;
    double vx = direction == null ? 0.0 : direction.x;
    double vy = direction == null ? 0.0 : direction.y;
    return set(ox, oy, vx, vy);
  }

  public Ray2 translate(double dx, double dy) {
    this.x += dx;
    this.y += dy;
    return this;
  }

  public Ray2 copy() {
    return new Ray2(x, y, dx, dy);
  }

  public double directionLengthSquared() {
    return dx * dx + dy * dy;
  }

  public double directionLength() {
    return Math.sqrt(directionLengthSquared());
  }

  public boolean isDegenerate() {
    return Scalars.approxEquals(dx, 0.0) && Scalars.approxEquals(dy, 0.0);
  }

  public Ray2 normalizeDirection() {
    double len = directionLength();
    if (len > 0.0) {
      dx /= len;
      dy /= len;
    }
    return this;
  }

  public Vec2 origin(Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    return target.set(x, y);
  }

  public Vec2 direction(Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    return target.set(dx, dy);
  }

  public Vec2 pointAt(double t, Vec2 out) {
    Vec2 target = out == null ? new Vec2() : out;
    return target.set(x + dx * t, y + dy * t);
  }
}
