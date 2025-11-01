package com.jvn.core.physics;

import com.jvn.core.math.Rect;
import java.util.ArrayList;
import java.util.List;

public class PhysicsWorld2D {
  private final List<RigidBody2D> bodies = new ArrayList<>();
  private double gravityX = 0;
  private double gravityY = 0;
  private Rect bounds; // optional world bounds, null = unbounded

  public void setGravity(double gx, double gy) { this.gravityX = gx; this.gravityY = gy; }
  public void setBounds(Rect bounds) { this.bounds = bounds; }

  public void addBody(RigidBody2D b) { if (b != null) bodies.add(b); }
  public void removeBody(RigidBody2D b) { bodies.remove(b); }
  public List<RigidBody2D> getBodies() { return bodies; }

  public void step(double deltaMs) {
    double dt = deltaMs / 1000.0;
    // Integrate velocities and apply gravity
    for (RigidBody2D b : bodies) {
      if (b.isStatic()) continue;
      b.setVelocity(b.getVx() + gravityX * dt, b.getVy() + gravityY * dt);
      double nx = b.getX() + b.getVx() * dt;
      double ny = b.getY() + b.getVy() * dt;
      b.setPosition(nx, ny);
      resolveWorldBounds(b);
    }

    // Naive pairwise collision resolution
    int n = bodies.size();
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        RigidBody2D a = bodies.get(i);
        RigidBody2D c = bodies.get(j);
        resolveCollision(a, c);
      }
    }
  }

  private void resolveWorldBounds(RigidBody2D b) {
    if (bounds == null) return;
    if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      var cir = b.getCircle();
      if (cir.x - cir.r < bounds.left()) { cir.x = bounds.left() + cir.r; b.setVelocity(Math.abs(b.getVx()) * b.getRestitution(), b.getVy()); }
      if (cir.x + cir.r > bounds.right()) { cir.x = bounds.right() - cir.r; b.setVelocity(-Math.abs(b.getVx()) * b.getRestitution(), b.getVy()); }
      if (cir.y - cir.r < bounds.top()) { cir.y = bounds.top() + cir.r; b.setVelocity(b.getVx(), Math.abs(b.getVy()) * b.getRestitution()); }
      if (cir.y + cir.r > bounds.bottom()) { cir.y = bounds.bottom() - cir.r; b.setVelocity(b.getVx(), -Math.abs(b.getVy()) * b.getRestitution()); }
    } else {
      var r = b.getAabb();
      if (r.left() < bounds.left()) { r.x = bounds.left(); b.setVelocity(Math.abs(b.getVx()) * b.getRestitution(), b.getVy()); }
      if (r.right() > bounds.right()) { r.x = bounds.right() - r.w; b.setVelocity(-Math.abs(b.getVx()) * b.getRestitution(), b.getVy()); }
      if (r.top() < bounds.top()) { r.y = bounds.top(); b.setVelocity(b.getVx(), Math.abs(b.getVy()) * b.getRestitution()); }
      if (r.bottom() > bounds.bottom()) { r.y = bounds.bottom() - r.h; b.setVelocity(b.getVx(), -Math.abs(b.getVy()) * b.getRestitution()); }
    }
  }

  private void resolveCollision(RigidBody2D a, RigidBody2D b) {
    if (a.isStatic() && b.isStatic()) return;
    if (a.getShapeType() == RigidBody2D.ShapeType.CIRCLE && b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      resolveCircleCircle(a, b);
    } else if (a.getShapeType() == RigidBody2D.ShapeType.AABB && b.getShapeType() == RigidBody2D.ShapeType.AABB) {
      resolveAabbAabb(a, b);
    } else {
      // Simplified circle/AABB: treat circle as AABB
      resolveAabbAabb(a, b);
    }
  }

  private void resolveCircleCircle(RigidBody2D ra, RigidBody2D rb) {
    var a = ra.getCircle();
    var b = rb.getCircle();
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double dist2 = dx * dx + dy * dy;
    double rsum = a.r + b.r;
    if (dist2 >= rsum * rsum || dist2 == 0) return;
    double dist = Math.sqrt(dist2);
    double nx = dx / dist;
    double ny = dy / dist;
    double penetration = rsum - dist;

    double totalMass = (ra.isStatic() ? 0 : ra.getMass()) + (rb.isStatic() ? 0 : rb.getMass());
    if (totalMass == 0) totalMass = 1;
    double moveA = rb.isStatic() ? 0 : (penetration * (rb.getMass() / totalMass));
    double moveB = ra.isStatic() ? 0 : (penetration * (ra.getMass() / totalMass));

    // Separate
    if (!ra.isStatic()) ra.setPosition(a.x - nx * moveA, a.y - ny * moveA);
    if (!rb.isStatic()) rb.setPosition(b.x + nx * moveB, b.y + ny * moveB);

    // Reflect velocities along normal
    double vaN = ra.getVx() * nx + ra.getVy() * ny;
    double vbN = rb.getVx() * nx + rb.getVy() * ny;
    double restitution = Math.min(ra.getRestitution(), rb.getRestitution());

    double m1 = ra.isStatic() ? Double.POSITIVE_INFINITY : ra.getMass();
    double m2 = rb.isStatic() ? Double.POSITIVE_INFINITY : rb.getMass();

    // 1D elastic collision along normal with restitution
    double newVaN = (vaN * (m1 - restitution * m2) + (1 + restitution) * m2 * vbN) / (m1 + m2);
    double newVbN = (vbN * (m2 - restitution * m1) + (1 + restitution) * m1 * vaN) / (m1 + m2);

    double dvA = newVaN - vaN;
    double dvB = newVbN - vbN;

    ra.setVelocity(ra.getVx() + dvA * nx, ra.getVy() + dvA * ny);
    rb.setVelocity(rb.getVx() + dvB * nx, rb.getVy() + dvB * ny);
  }

  private void resolveAabbAabb(RigidBody2D a, RigidBody2D b) {
    var ra = a.getAabb();
    var rb = b.getAabb();
    if (!ra.intersects(rb)) return;

    double overlapX1 = ra.right() - rb.left();
    double overlapX2 = rb.right() - ra.left();
    double overlapY1 = ra.bottom() - rb.top();
    double overlapY2 = rb.bottom() - ra.top();

    double minOverlapX = Math.min(overlapX1, overlapX2);
    double minOverlapY = Math.min(overlapY1, overlapY2);

    double nx = 0, ny = 0, penetration;
    if (minOverlapX < minOverlapY) {
      penetration = minOverlapX;
      nx = (overlapX1 < overlapX2) ? 1 : -1;
    } else {
      penetration = minOverlapY;
      ny = (overlapY1 < overlapY2) ? 1 : -1;
    }

    double totalMass = (a.isStatic() ? 0 : a.getMass()) + (b.isStatic() ? 0 : b.getMass());
    if (totalMass == 0) totalMass = 1;
    double moveA = b.isStatic() ? 0 : (penetration * (b.getMass() / totalMass));
    double moveB = a.isStatic() ? 0 : (penetration * (a.getMass() / totalMass));

    if (!a.isStatic()) a.setPosition(a.getX() - nx * moveA, a.getY() - ny * moveA);
    if (!b.isStatic()) b.setPosition(b.getX() + nx * moveB, b.getY() + ny * moveB);

    double vaN = a.getVx() * nx + a.getVy() * ny;
    double vbN = b.getVx() * nx + b.getVy() * ny;
    double restitution = Math.min(a.getRestitution(), b.getRestitution());

    double m1 = a.isStatic() ? Double.POSITIVE_INFINITY : a.getMass();
    double m2 = b.isStatic() ? Double.POSITIVE_INFINITY : b.getMass();

    double newVaN = (vaN * (m1 - restitution * m2) + (1 + restitution) * m2 * vbN) / (m1 + m2);
    double newVbN = (vbN * (m2 - restitution * m1) + (1 + restitution) * m1 * vaN) / (m1 + m2);

    double dvA = newVaN - vaN;
    double dvB = newVbN - vbN;

    a.setVelocity(a.getVx() + dvA * nx, a.getVy() + dvA * ny);
    b.setVelocity(b.getVx() + dvB * nx, b.getVy() + dvB * ny);
  }
}
