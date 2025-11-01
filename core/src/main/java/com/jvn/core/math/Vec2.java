package com.jvn.core.math;

public class Vec2 {
  public double x;
  public double y;

  public Vec2() { this(0, 0); }
  public Vec2(double x, double y) { this.x = x; this.y = y; }

  public Vec2 set(double x, double y) { this.x = x; this.y = y; return this; }
  public Vec2 copy() { return new Vec2(x, y); }
  public Vec2 add(Vec2 o) { this.x += o.x; this.y += o.y; return this; }
  public Vec2 sub(Vec2 o) { this.x -= o.x; this.y -= o.y; return this; }
  public Vec2 scale(double s) { this.x *= s; this.y *= s; return this; }
  public double length() { return Math.sqrt(x * x + y * y); }
  public Vec2 normalize() { double len = length(); if (len != 0) { x /= len; y /= len; } return this; }
  public static double dot(Vec2 a, Vec2 b) { return a.x * b.x + a.y * b.y; }
}
