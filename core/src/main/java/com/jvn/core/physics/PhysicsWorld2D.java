package com.jvn.core.physics;

import com.jvn.core.math.Rect;
import java.util.ArrayList;
import java.util.List;

public class PhysicsWorld2D {
  private final List<RigidBody2D> bodies = new ArrayList<>();
  private double gravityX = 0;
  private double gravityY = 0;
  private Rect bounds; // optional world bounds, null = unbounded
  private final List<Rect> staticRects = new ArrayList<>();
  private PhysicsSensorListener sensorListener;

  public static class RaycastHit {
    public RigidBody2D body;
    public double x;
    public double y;
    public double nx;
    public double ny;
    public double distance;
  }

  public interface PhysicsSensorListener {
    void onTrigger(RigidBody2D sensor, RigidBody2D other);
  }

  public void setGravity(double gx, double gy) { this.gravityX = gx; this.gravityY = gy; }
  public void setBounds(Rect bounds) { this.bounds = bounds; }
  public void addStaticRect(Rect r) { if (r != null) staticRects.add(r); }
  public void clearStaticRects() { staticRects.clear(); }
  public void setSensorListener(PhysicsSensorListener l) { this.sensorListener = l; }

  public void addBody(RigidBody2D b) { if (b != null) bodies.add(b); }
  public void removeBody(RigidBody2D b) { bodies.remove(b); }
  public List<RigidBody2D> getBodies() { return bodies; }

  public RaycastHit raycast(double x1, double y1, double x2, double y2) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double segLen = Math.sqrt(dx * dx + dy * dy);
    if (segLen == 0) return null;
    RaycastHit best = null;
    double bestDist = Double.POSITIVE_INFINITY;
    for (RigidBody2D b : bodies) {
      RaycastHit hit = (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE)
          ? raycastCircle(b, x1, y1, dx, dy, segLen)
          : raycastAabb(b, x1, y1, dx, dy);
      if (hit != null && hit.distance < bestDist) {
        bestDist = hit.distance;
        best = hit;
      }
    }
    return best;
  }

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
      resolveStaticColliders(b);
    }

    // Naive pairwise collision resolution
    int n = bodies.size();
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        RigidBody2D a = bodies.get(i);
        RigidBody2D c = bodies.get(j);
        if (a.isSensor() || c.isSensor()) { handleSensor(a, c); }
        else { resolveCollision(a, c); }
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

  private void resolveStaticColliders(RigidBody2D b) {
    if (b.isStatic() || b.isSensor()) return;
    for (Rect tile : staticRects) {
      if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
        // Approximate circle as AABB for simple resolution
        Rect r = new Rect(b.getCircle().x - b.getCircle().r, b.getCircle().y - b.getCircle().r, b.getCircle().r * 2, b.getCircle().r * 2);
        if (!r.intersects(tile)) continue;
        // Push out like AABB
        double overlapX1 = r.right() - tile.left();
        double overlapX2 = tile.right() - r.left();
        double overlapY1 = r.bottom() - tile.top();
        double overlapY2 = tile.bottom() - r.top();
        double minOverlapX = Math.min(overlapX1, overlapX2);
        double minOverlapY = Math.min(overlapY1, overlapY2);
        if (minOverlapX < minOverlapY) {
          double dir = (overlapX1 < overlapX2) ? 1 : -1;
          b.setPosition(b.getX() - dir * minOverlapX, b.getY());
          b.setVelocity(-dir * Math.abs(b.getVx()) * b.getRestitution(), b.getVy());
        } else {
          double dir = (overlapY1 < overlapY2) ? 1 : -1;
          b.setPosition(b.getX(), b.getY() - dir * minOverlapY);
          b.setVelocity(b.getVx(), -dir * Math.abs(b.getVy()) * b.getRestitution());
        }
      } else {
        Rect r = b.getAabb();
        if (!r.intersects(tile)) continue;
        double overlapX1 = r.right() - tile.left();
        double overlapX2 = tile.right() - r.left();
        double overlapY1 = r.bottom() - tile.top();
        double overlapY2 = tile.bottom() - r.top();
        double minOverlapX = Math.min(overlapX1, overlapX2);
        double minOverlapY = Math.min(overlapY1, overlapY2);
        if (minOverlapX < minOverlapY) {
          double dir = (overlapX1 < overlapX2) ? 1 : -1;
          r.x -= dir * minOverlapX;
          b.setVelocity(-dir * Math.abs(b.getVx()) * b.getRestitution(), b.getVy());
        } else {
          double dir = (overlapY1 < overlapY2) ? 1 : -1;
          r.y -= dir * minOverlapY;
          b.setVelocity(b.getVx(), -dir * Math.abs(b.getVy()) * b.getRestitution());
        }
      }
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

  private RaycastHit raycastCircle(RigidBody2D body, double sx, double sy, double dx, double dy, double segLen) {
    double cx = body.getCircle().x;
    double cy = body.getCircle().y;
    double r = body.getCircle().r;
    double fx = sx - cx;
    double fy = sy - cy;
    double a = dx * dx + dy * dy;
    double bb = 2 * (fx * dx + fy * dy);
    double c = fx * fx + fy * fy - r * r;
    double disc = bb * bb - 4 * a * c;
    if (disc < 0) return null;
    double sqrt = Math.sqrt(disc);
    double t1 = (-bb - sqrt) / (2 * a);
    double t2 = (-bb + sqrt) / (2 * a);
    double t = Double.POSITIVE_INFINITY;
    if (t1 >= 0 && t1 <= 1) t = Math.min(t, t1);
    if (t2 >= 0 && t2 <= 1) t = Math.min(t, t2);
    if (!Double.isFinite(t)) return null;
    double hx = sx + dx * t;
    double hy = sy + dy * t;
    double nx = hx - cx;
    double ny = hy - cy;
    double nlen = Math.sqrt(nx * nx + ny * ny);
    if (nlen != 0) { nx /= nlen; ny /= nlen; }
    RaycastHit hit = new RaycastHit();
    hit.body = body;
    hit.x = hx;
    hit.y = hy;
    hit.nx = nx;
    hit.ny = ny;
    hit.distance = t * segLen;
    return hit;
  }

  private RaycastHit raycastAabb(RigidBody2D body, double sx, double sy, double dx, double dy) {
    double minX = body.getAabb().left();
    double minY = body.getAabb().top();
    double maxX = body.getAabb().right();
    double maxY = body.getAabb().bottom();

    double tmin = 0.0;
    double tmax = 1.0;
    double nx = 0, ny = 0;

    if (dx == 0) {
      if (sx < minX || sx > maxX) return null;
    } else {
      double inv = 1.0 / dx;
      double t1 = (minX - sx) * inv;
      double t2 = (maxX - sx) * inv;
      double txmin = Math.min(t1, t2);
      double txmax = Math.max(t1, t2);
      double nxCand = (t1 < t2) ? -1 : 1;
      if (txmin > tmin) { tmin = txmin; nx = nxCand; ny = 0; }
      if (txmax < tmax) { tmax = txmax; }
      if (tmin > tmax) return null;
    }

    if (dy == 0) {
      if (sy < minY || sy > maxY) return null;
    } else {
      double inv = 1.0 / dy;
      double t1 = (minY - sy) * inv;
      double t2 = (maxY - sy) * inv;
      double tymin = Math.min(t1, t2);
      double tymax = Math.max(t1, t2);
      double nyCand = (t1 < t2) ? -1 : 1;
      if (tymin > tmin) { tmin = tymin; nx = 0; ny = nyCand; }
      if (tymax < tmax) { tmax = tymax; }
      if (tmin > tmax) return null;
    }

    if (tmin < 0 || tmin > 1) return null;
    RaycastHit hit = new RaycastHit();
    hit.body = body;
    hit.x = sx + dx * tmin;
    hit.y = sy + dy * tmin;
    hit.nx = nx;
    hit.ny = ny;
    hit.distance = Math.sqrt((hit.x - sx) * (hit.x - sx) + (hit.y - sy) * (hit.y - sy));
    return hit;
  }

  private void handleSensor(RigidBody2D a, RigidBody2D b) {
    if (sensorListener == null) return;
    boolean hit;
    if (a.getShapeType() == RigidBody2D.ShapeType.CIRCLE && b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      double dx = b.getCircle().x - a.getCircle().x;
      double dy = b.getCircle().y - a.getCircle().y;
      double rsum = a.getCircle().r + b.getCircle().r;
      hit = (dx * dx + dy * dy) <= rsum * rsum;
    } else {
      Rect ra = (a.getShapeType() == RigidBody2D.ShapeType.AABB) ? a.getAabb() : new Rect(a.getCircle().x - a.getCircle().r, a.getCircle().y - a.getCircle().r, a.getCircle().r * 2, a.getCircle().r * 2);
      Rect rb = (b.getShapeType() == RigidBody2D.ShapeType.AABB) ? b.getAabb() : new Rect(b.getCircle().x - b.getCircle().r, b.getCircle().y - b.getCircle().r, b.getCircle().r * 2, b.getCircle().r * 2);
      hit = ra.intersects(rb);
    }
    if (hit) {
      if (a.isSensor() && !b.isSensor()) sensorListener.onTrigger(a, b);
      if (b.isSensor() && !a.isSensor()) sensorListener.onTrigger(b, a);
    }
  }
}
