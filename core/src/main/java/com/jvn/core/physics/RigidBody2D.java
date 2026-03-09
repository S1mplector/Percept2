package com.jvn.core.physics;

import com.jvn.core.math.Circle;
import com.jvn.core.math.Rect;

/**
 * A 2-D rigid body that participates in {@link PhysicsWorld2D} simulation.
 *
 * <p>Each body wraps either an axis-aligned bounding box ({@link ShapeType#AABB})
 * or a circle ({@link ShapeType#CIRCLE}). Physical properties such as mass,
 * restitution (bounciness), friction, and linear damping control how the body
 * interacts with other bodies and the world bounds.</p>
 *
 * <h2>Factory Methods</h2>
 * <pre>{@code
 * RigidBody2D player = RigidBody2D.box(100, 200, 32, 48);
 * RigidBody2D ball   = RigidBody2D.circle(400, 300, 16);
 * }</pre>
 *
 * <h2>Special Modes</h2>
 * <ul>
 *   <li><b>Static</b> — immovable; not affected by gravity or impulses.</li>
 *   <li><b>Sensor</b> — detects overlaps without generating collision response.</li>
 * </ul>
 *
 * @see PhysicsWorld2D
 * @see Collision2D
 */
public class RigidBody2D {

  /** Collision shape type. */
  public enum ShapeType { CIRCLE, AABB }

  // ──────────────────────────────────────────────────────────────────────────
  //  Shape data
  // ──────────────────────────────────────────────────────────────────────────

  /** The active collision shape type. */
  private ShapeType shapeType = ShapeType.AABB;

  /** AABB shape data (used when {@code shapeType == AABB}). */
  private final Rect aabb = new Rect();

  /** Circle shape data (used when {@code shapeType == CIRCLE}). */
  private final Circle circle = new Circle();

  // ──────────────────────────────────────────────────────────────────────────
  //  Physical properties
  // ──────────────────────────────────────────────────────────────────────────

  /** Horizontal velocity (pixels / second). */
  private double vx;

  /** Vertical velocity (pixels / second). */
  private double vy;

  /** Mass of the body (kg). Must be &gt; 0. */
  private double mass = 1.0;

  /** If {@code true}, the body is immovable and unaffected by forces. */
  private boolean isStatic = false;

  /** Coefficient of restitution (bounciness) [0, 1]. */
  private double restitution = 0.2;

  /** If {@code true}, overlaps are detected but no collision response is applied. */
  private boolean sensor = false;

  /** Per-second linear damping factor (velocity decay). 0 = no damping. */
  private double linearDamping = 0.0;

  /** Simple kinetic friction coefficient [0, 1]. */
  private double friction = 0.2;

  // ──────────────────────────────────────────────────────────────────────────
  //  Factory methods
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Create an AABB-shaped body.
   *
   * @param x left edge
   * @param y top edge
   * @param w width
   * @param h height
   * @return the new body
   */
  public static RigidBody2D box(double x, double y, double w, double h) {
    RigidBody2D b = new RigidBody2D();
    b.shapeType = ShapeType.AABB;
    b.aabb.x = x; b.aabb.y = y; b.aabb.w = w; b.aabb.h = h;
    return b;
  }

  /**
   * Create a circle-shaped body.
   *
   * @param x centre X
   * @param y centre Y
   * @param r radius
   * @return the new body
   */
  public static RigidBody2D circle(double x, double y, double r) {
    RigidBody2D b = new RigidBody2D();
    b.shapeType = ShapeType.CIRCLE;
    b.circle.x = x; b.circle.y = y; b.circle.r = r;
    return b;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Shape accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @return the collision shape type */
  public ShapeType getShapeType() { return shapeType; }

  /** @return the AABB shape data (mutable) */
  public Rect getAabb() { return aabb; }

  /** @return the circle shape data (mutable) */
  public Circle getCircle() { return circle; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Position / velocity
  // ──────────────────────────────────────────────────────────────────────────

  /** @return the X position (left edge for AABB, centre for circle) */
  public double getX() { return shapeType == ShapeType.AABB ? aabb.x : circle.x; }

  /** @return the Y position (top edge for AABB, centre for circle) */
  public double getY() { return shapeType == ShapeType.AABB ? aabb.y : circle.y; }

  /** Set the body's position, updating the active shape. */
  public void setPosition(double x, double y) { if (shapeType == ShapeType.AABB) { aabb.x = x; aabb.y = y; } else { circle.x = x; circle.y = y; } }

  /** @return the horizontal velocity */
  public double getVx() { return vx; }

  /** @return the vertical velocity */
  public double getVy() { return vy; }

  /** Set the body's velocity. */
  public void setVelocity(double vx, double vy) { this.vx = vx; this.vy = vy; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Physical property accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @return the body mass */
  public double getMass() { return mass; }

  /** Set the mass. Values ≤ 0 are clamped to 1. */
  public void setMass(double mass) { this.mass = mass <= 0 ? 1.0 : mass; }

  /** @return {@code true} if the body is immovable */
  public boolean isStatic() { return isStatic; }

  /** Mark the body as static (immovable) or dynamic. */
  public void setStatic(boolean aStatic) { isStatic = aStatic; }

  /** @return the restitution (bounciness) coefficient [0, 1] */
  public double getRestitution() { return restitution; }

  /** Set the restitution, clamped to [0, 1]. */
  public void setRestitution(double restitution) { this.restitution = Math.max(0, Math.min(1, restitution)); }

  /** @return {@code true} if this body is a sensor (overlap-only) */
  public boolean isSensor() { return sensor; }

  /** Set sensor mode. */
  public void setSensor(boolean sensor) { this.sensor = sensor; }

  /** @return the linear damping factor */
  public double getLinearDamping() { return linearDamping; }

  /** Set the linear damping. Negative / NaN / infinite values reset to 0. */
  public void setLinearDamping(double damping) {
    if (Double.isNaN(damping) || Double.isInfinite(damping) || damping < 0) damping = 0;
    this.linearDamping = damping;
  }

  /** @return the friction coefficient [0, 1] */
  public double getFriction() { return friction; }

  /** Set the friction, clamped to [0, 1]. NaN / infinite values reset to 0. */
  public void setFriction(double friction) {
    if (Double.isNaN(friction) || Double.isInfinite(friction)) friction = 0;
    if (friction < 0) friction = 0;
    if (friction > 1) friction = 1;
    this.friction = friction;
  }
}
