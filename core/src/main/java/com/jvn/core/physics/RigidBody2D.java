package com.jvn.core.physics;

import com.jvn.core.math.Circle;
import com.jvn.core.math.Rect;

public class RigidBody2D {
  public enum ShapeType { CIRCLE, AABB }

  private ShapeType shapeType = ShapeType.AABB;
  private final Rect aabb = new Rect();
  private final Circle circle = new Circle();

  private double vx;
  private double vy;
  private double mass = 1.0;
  private boolean isStatic = false;
  private double restitution = 0.2; // bounciness

  public static RigidBody2D box(double x, double y, double w, double h) {
    RigidBody2D b = new RigidBody2D();
    b.shapeType = ShapeType.AABB;
    b.aabb.x = x; b.aabb.y = y; b.aabb.w = w; b.aabb.h = h;
    return b;
  }

  public static RigidBody2D circle(double x, double y, double r) {
    RigidBody2D b = new RigidBody2D();
    b.shapeType = ShapeType.CIRCLE;
    b.circle.x = x; b.circle.y = y; b.circle.r = r;
    return b;
  }

  public ShapeType getShapeType() { return shapeType; }
  public Rect getAabb() { return aabb; }
  public Circle getCircle() { return circle; }

  public double getX() { return shapeType == ShapeType.AABB ? aabb.x : circle.x; }
  public double getY() { return shapeType == ShapeType.AABB ? aabb.y : circle.y; }
  public void setPosition(double x, double y) { if (shapeType == ShapeType.AABB) { aabb.x = x; aabb.y = y; } else { circle.x = x; circle.y = y; } }

  public double getVx() { return vx; }
  public double getVy() { return vy; }
  public void setVelocity(double vx, double vy) { this.vx = vx; this.vy = vy; }

  public double getMass() { return mass; }
  public void setMass(double mass) { this.mass = mass <= 0 ? 1.0 : mass; }

  public boolean isStatic() { return isStatic; }
  public void setStatic(boolean aStatic) { isStatic = aStatic; }

  public double getRestitution() { return restitution; }
  public void setRestitution(double restitution) { this.restitution = Math.max(0, Math.min(1, restitution)); }
}
