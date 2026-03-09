package com.jvn.core.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jvn.core.math.Rect;

/**
 * Simple 2-D physics simulation supporting AABB and circle rigid bodies.
 *
 * <p>Features include:</p>
 * <ul>
 *   <li>Configurable gravity, world bounds, and static colliders.</li>
 *   <li>Spatial-hash broadphase for O(n) pair generation.</li>
 *   <li>Circle–circle, AABB–AABB, and circle–AABB narrow-phase detection.</li>
 *   <li>Impulse-based collision response with restitution and friction.</li>
 *   <li>Sensor bodies (overlap-only triggers).</li>
 *   <li>Optional fixed-timestep sub-stepping for determinism.</li>
 *   <li>Raycasting against all dynamic bodies.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PhysicsWorld2D world = new PhysicsWorld2D();
 * world.setGravity(0, 980);
 * world.setBounds(new Rect(0, 0, 1920, 1080));
 * world.addBody(RigidBody2D.box(100, 900, 1720, 20)); // floor
 * world.addBody(playerBody);
 * // each frame:
 * world.step(deltaMs);
 * }</pre>
 *
 * @see RigidBody2D
 * @see Collision2D
 */
public class PhysicsWorld2D {

  // ──────────────────────────────────────────────────────────────────────────
  //  World state
  // ──────────────────────────────────────────────────────────────────────────

  /** All dynamic/static bodies registered in the world. */
  private final List<RigidBody2D> bodies = new ArrayList<>();

  /** World gravity X component (pixels / s²). */
  private double gravityX = 0;

  /** World gravity Y component (pixels / s²). */
  private double gravityY = 0;

  /** Optional world bounds; {@code null} = unbounded. */
  private Rect bounds;

  /** Immovable rectangular colliders (e.g. tile map geometry). */
  private final List<Rect> staticRects = new ArrayList<>();

  /** Callback for sensor overlap events. */
  private PhysicsSensorListener sensorListener;

  /** Callback for collision events. */
  private CollisionListener collisionListener;

  /** Maximum single-step duration in ms (prevents tunnelling on lag spikes). */
  private double maxStepMs = 50.0;

  /** Fixed sub-step size in ms; 0 = variable step (disabled). */
  private double fixedTimeStepMs = 0.0;

  /** Maximum number of fixed sub-steps per frame. */
  private int maxSubSteps = 8;

  /** Accumulator for fixed-timestep remainder between frames. */
  private double accumulatorMs = 0.0;

  /** Spatial-hash cell size in pixels for broadphase. */
  private int broadphaseCellSize = 128;

  /** Spatial-hash grid: cell-key → list of body indices. */
  private final Map<Long, List<Integer>> broadphaseCells = new HashMap<>();

  /** Unique broadphase collision pairs for the current step. */
  private final List<int[]> broadphasePairs = new ArrayList<>();

  // ──────────────────────────────────────────────────────────────────────────
  //  Inner types
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Result of a {@link #raycast} call, containing the hit body, contact
   * point, surface normal, and distance along the ray.
   */
  public static class RaycastHit {
    /** The body that was hit. */
    public RigidBody2D body;
    /** Contact point X. */
    public double x;
    /** Contact point Y. */
    public double y;
    /** Surface normal X at the contact point. */
    public double nx;
    /** Surface normal Y at the contact point. */
    public double ny;
    /** Distance from the ray origin to the contact point. */
    public double distance;
  }

  /**
   * Listener for sensor overlap events (no physical response).
   */
  public interface PhysicsSensorListener {
    /** Called when a sensor overlaps another non-sensor body. */
    void onTrigger(RigidBody2D sensor, RigidBody2D other);
  }

  /**
   * Listener for collision events (after physical response).
   */
  public interface CollisionListener {
    /** Two dynamic bodies collided. Normal points from {@code a} to {@code b}. */
    void onBodiesCollide(RigidBody2D a, RigidBody2D b, double nx, double ny);
    /** A body collided with the world bounds. */
    void onBoundsCollide(RigidBody2D b, String side);
    /** A body collided with a static rectangle. */
    void onStaticCollide(RigidBody2D b, Rect tile, double nx, double ny);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Configuration
  // ──────────────────────────────────────────────────────────────────────────

  /** Set world gravity in pixels / s². */
  public void setGravity(double gx, double gy) { this.gravityX = gx; this.gravityY = gy; }

  /** Set the optional world boundary rectangle ({@code null} = unbounded). */
  public void setBounds(Rect bounds) { this.bounds = bounds; }

  /** Add an immovable rectangular collider (e.g. a tile). */
  public void addStaticRect(Rect r) { if (r != null) staticRects.add(r); }

  /** Remove all static rectangular colliders. */
  public void clearStaticRects() { staticRects.clear(); }

  /** Set the sensor overlap callback. */
  public void setSensorListener(PhysicsSensorListener l) { this.sensorListener = l; }

  /** Set the collision event callback. */
  public void setCollisionListener(CollisionListener l) { this.collisionListener = l; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Body management
  // ──────────────────────────────────────────────────────────────────────────

  /** Add a body to the simulation. */
  public void addBody(RigidBody2D b) { if (b != null) bodies.add(b); }

  /** Remove a body from the simulation. */
  public void removeBody(RigidBody2D b) { bodies.remove(b); }

  /** @return all registered bodies (mutable list) */
  public List<RigidBody2D> getBodies() { return bodies; }

  /** Set the maximum single-step duration in ms. 0 = no limit. */
  public void setMaxStepMs(double ms) { this.maxStepMs = ms <= 0 ? 0 : ms; }

  /** @return the maximum step duration in ms */
  public double getMaxStepMs() { return maxStepMs; }

  /**
   * Enable fixed-timestep sub-stepping for deterministic simulation.
   *
   * @param stepMs      the fixed sub-step size in ms; 0 = disabled
   * @param maxSubSteps the maximum number of sub-steps per frame
   */
  public void setFixedTimeStepMs(double stepMs, int maxSubSteps) {
    this.fixedTimeStepMs = stepMs <= 0 ? 0 : stepMs;
    this.maxSubSteps = Math.max(1, maxSubSteps);
  }

  /** @return the fixed sub-step size in ms (0 = disabled) */
  public double getFixedTimeStepMs() { return fixedTimeStepMs; }

  /** Set the broadphase spatial-hash cell size in pixels. */
  public void setBroadphaseCellSize(int size) { this.broadphaseCellSize = size <= 0 ? 1 : size; }

  /** @return the broadphase cell size in pixels */
  public int getBroadphaseCellSize() { return broadphaseCellSize; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Raycasting
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Cast a ray (segment) through the world and return the closest hit.
   *
   * @param x1 ray start X
   * @param y1 ray start Y
   * @param x2 ray end X
   * @param y2 ray end Y
   * @return the closest {@link RaycastHit}, or {@code null} if nothing was hit
   */
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

  // ──────────────────────────────────────────────────────────────────────────
  //  Simulation step
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Advance the simulation by {@code deltaMs} milliseconds.
   *
   * <p>If a fixed timestep is configured, the delta is accumulated and
   * consumed in fixed sub-steps. Otherwise a single variable step is
   * performed (clamped by {@link #maxStepMs}).</p>
   *
   * @param deltaMs frame delta in milliseconds
   */
  public void step(double deltaMs) {
    if (deltaMs < 0) return;
    double stepMs = deltaMs;
    if (maxStepMs > 0 && stepMs > maxStepMs) stepMs = maxStepMs;

    if (fixedTimeStepMs > 0) {
      accumulatorMs += stepMs;
      int steps = 0;
      while (accumulatorMs >= fixedTimeStepMs && steps < maxSubSteps) {
        stepOnce(fixedTimeStepMs);
        accumulatorMs -= fixedTimeStepMs;
        steps++;
      }
      // Avoid unbounded accumulation if frame time explodes
      if (steps == maxSubSteps && accumulatorMs > fixedTimeStepMs) {
        accumulatorMs = fixedTimeStepMs;
      }
    } else {
      stepOnce(stepMs);
    }
  }

  /** Execute a single integration + collision pass for the given duration. */
  private void stepOnce(double stepMs) {
    double dt = stepMs / 1000.0;
    if (dt <= 0) return;

    for (RigidBody2D b : bodies) {
      if (b.isStatic()) continue;
      b.setVelocity(b.getVx() + gravityX * dt, b.getVy() + gravityY * dt);
      if (b.getLinearDamping() > 0) {
        double damp = Math.max(0.0, 1.0 - b.getLinearDamping() * dt);
        b.setVelocity(b.getVx() * damp, b.getVy() * damp);
      }
      double nx = b.getX() + b.getVx() * dt;
      double ny = b.getY() + b.getVy() * dt;
      b.setPosition(nx, ny);
      resolveWorldBounds(b);
      resolveStaticColliders(b);
    }

    for (int[] pair : gatherPairs()) {
      RigidBody2D a = bodies.get(pair[0]);
      RigidBody2D b = bodies.get(pair[1]);
      CollisionInfo info = findCollision(a, b);
      if (info == null) continue;
      if (a.isSensor() || b.isSensor()) {
        handleSensor(a, b, info);
        continue;
      }
      applyCollisionResponse(a, b, info);
    }
  }

  /** Populate broadphase grid and gather unique overlapping body pairs. */
  private List<int[]> gatherPairs() {
    broadphasePairs.clear();
    broadphaseCells.clear();
    for (int i = 0; i < bodies.size(); i++) {
      Bounds bb = computeBounds(bodies.get(i));
      int minCx = (int) Math.floor(bb.minX / broadphaseCellSize);
      int maxCx = (int) Math.floor(bb.maxX / broadphaseCellSize);
      int minCy = (int) Math.floor(bb.minY / broadphaseCellSize);
      int maxCy = (int) Math.floor(bb.maxY / broadphaseCellSize);
      for (int cx = minCx; cx <= maxCx; cx++) {
        for (int cy = minCy; cy <= maxCy; cy++) {
          long key = hashCell(cx, cy);
          broadphaseCells.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
      }
    }
    Set<Long> seen = new HashSet<>();
    for (List<Integer> bucket : broadphaseCells.values()) {
      int n = bucket.size();
      if (n < 2) continue;
      for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
          int a = bucket.get(i);
          int b = bucket.get(j);
          long key = pairKey(a, b);
          if (seen.add(key)) broadphasePairs.add(new int[] {a, b});
        }
      }
    }
    return broadphasePairs;
  }

  /** Compute the axis-aligned bounding box of a body for broadphase insertion. */
  private Bounds computeBounds(RigidBody2D b) {
    Bounds out = new Bounds();
    if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      var c = b.getCircle();
      out.minX = c.x - c.r;
      out.maxX = c.x + c.r;
      out.minY = c.y - c.r;
      out.maxY = c.y + c.r;
    } else {
      var r = b.getAabb();
      out.minX = r.left();
      out.maxX = r.right();
      out.minY = r.top();
      out.maxY = r.bottom();
    }
    return out;
  }

  /** Narrow-phase collision detection; returns contact info or {@code null}. */
  private CollisionInfo findCollision(RigidBody2D a, RigidBody2D b) {
    if (a.getShapeType() == RigidBody2D.ShapeType.CIRCLE && b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      return collideCircleCircle(a, b);
    } else if (a.getShapeType() == RigidBody2D.ShapeType.AABB && b.getShapeType() == RigidBody2D.ShapeType.AABB) {
      return collideAabbAabb(a, b);
    } else if (a.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      return collideCircleAabb(a, b, true);
    } else {
      CollisionInfo info = collideCircleAabb(b, a, true);
      if (info != null) {
        // Flip normal to keep orientation from a -> b
        info.nx = -info.nx;
        info.ny = -info.ny;
      }
      return info;
    }
  }

  /** Circle vs circle narrow-phase. */
  private CollisionInfo collideCircleCircle(RigidBody2D a, RigidBody2D b) {
    var ca = a.getCircle();
    var cb = b.getCircle();
    double dx = cb.x - ca.x;
    double dy = cb.y - ca.y;
    double dist2 = dx * dx + dy * dy;
    double rsum = ca.r + cb.r;
    if (dist2 >= rsum * rsum) return null;
    double dist = Math.sqrt(Math.max(1e-9, dist2));
    CollisionInfo info = new CollisionInfo();
    if (dist > 1e-6) {
      info.nx = dx / dist;
      info.ny = dy / dist;
      info.penetration = rsum - dist;
    } else {
      info.nx = 1;
      info.ny = 0;
      info.penetration = rsum;
    }
    return info;
  }

  /** AABB vs AABB narrow-phase (minimum overlap axis). */
  private CollisionInfo collideAabbAabb(RigidBody2D a, RigidBody2D b) {
    Rect ra = a.getAabb();
    Rect rb = b.getAabb();
    if (!ra.intersects(rb)) return null;
    double overlapX1 = ra.right() - rb.left();
    double overlapX2 = rb.right() - ra.left();
    double overlapY1 = ra.bottom() - rb.top();
    double overlapY2 = rb.bottom() - ra.top();
    double minOverlapX = Math.min(overlapX1, overlapX2);
    double minOverlapY = Math.min(overlapY1, overlapY2);

    CollisionInfo info = new CollisionInfo();
    if (minOverlapX < minOverlapY) {
      double dir = (overlapX1 < overlapX2) ? 1 : -1; // normal from a to b
      info.nx = dir;
      info.ny = 0;
      info.penetration = minOverlapX;
    } else {
      double dir = (overlapY1 < overlapY2) ? 1 : -1;
      info.nx = 0;
      info.ny = dir;
      info.penetration = minOverlapY;
    }
    return info;
  }

  /** Circle vs AABB narrow-phase (closest-point test). */
  private CollisionInfo collideCircleAabb(RigidBody2D circleBody, RigidBody2D boxBody, boolean circleFirst) {
    var c = circleBody.getCircle();
    var r = boxBody.getAabb();
    double closestX = clamp(c.x, r.left(), r.right());
    double closestY = clamp(c.y, r.top(), r.bottom());
    double dx = closestX - c.x;
    double dy = closestY - c.y;
    double dist2 = dx * dx + dy * dy;
    double radius = c.r;
    if (dist2 > radius * radius) return null;
    CollisionInfo info = new CollisionInfo();
    if (dist2 > 1e-9) {
      double dist = Math.sqrt(dist2);
      double nx = dx / dist; // normal from circle (a) toward box (b)
      double ny = dy / dist;
      info.nx = circleFirst ? nx : -nx;
      info.ny = circleFirst ? ny : -ny;
      info.penetration = radius - dist;
    } else {
      // Circle center inside box; push out via shallowest axis
      double leftPen = c.x - r.left();
      double rightPen = r.right() - c.x;
      double topPen = c.y - r.top();
      double bottomPen = r.bottom() - c.y;
      double minPen = Math.min(Math.min(leftPen, rightPen), Math.min(topPen, bottomPen));
      double nx = 0, ny = 0;
      if (minPen == leftPen) nx = 1;
      else if (minPen == rightPen) nx = -1;
      else if (minPen == topPen) ny = 1;
      else ny = -1;
      info.nx = circleFirst ? nx : -nx;
      info.ny = circleFirst ? ny : -ny;
      // Distance to clear when center is inside includes the radius.
      info.penetration = radius + minPen;
    }
    return info;
  }

  /** Apply positional correction and impulse-based velocity response. */
  private void applyCollisionResponse(RigidBody2D a, RigidBody2D b, CollisionInfo info) {
    if (a.isStatic() && b.isStatic()) return;
    double invMassA = a.isStatic() ? 0 : 1.0 / a.getMass();
    double invMassB = b.isStatic() ? 0 : 1.0 / b.getMass();
    double invMassSum = invMassA + invMassB;
    if (invMassSum <= 0) return;

    // Positional correction
    double penetration = info.penetration;
    double moveA = invMassA / invMassSum * penetration;
    double moveB = invMassB / invMassSum * penetration;
    double nx = info.nx;
    double ny = info.ny;
    if (!a.isStatic()) a.setPosition(a.getX() - nx * moveA, a.getY() - ny * moveA);
    if (!b.isStatic()) b.setPosition(b.getX() + nx * moveB, b.getY() + ny * moveB);

    // Velocity response (impulse)
    double rvx = b.getVx() - a.getVx();
    double rvy = b.getVy() - a.getVy();
    double relVel = rvx * nx + rvy * ny;
    if (relVel < 0) {
      double restitution = Math.min(a.getRestitution(), b.getRestitution());
      double j = -(1 + restitution) * relVel / invMassSum;
      double ix = j * nx;
      double iy = j * ny;
      if (!a.isStatic()) a.setVelocity(a.getVx() - ix * invMassA, a.getVy() - iy * invMassA);
      if (!b.isStatic()) b.setVelocity(b.getVx() + ix * invMassB, b.getVy() + iy * invMassB);
    }

    applyFriction(a, b, nx, ny, invMassSum);
    if (collisionListener != null) collisionListener.onBodiesCollide(a, b, nx, ny);
  }

  /** Apply tangential friction impulse between two colliding bodies. */
  private void applyFriction(RigidBody2D a, RigidBody2D b, double nx, double ny, double invMassSum) {
    double rvx = b.getVx() - a.getVx();
    double rvy = b.getVy() - a.getVy();
    double relNormal = rvx * nx + rvy * ny;
    double tx = rvx - relNormal * nx;
    double ty = rvy - relNormal * ny;
    double tLen = Math.sqrt(tx * tx + ty * ty);
    if (tLen < 1e-6) return;
    tx /= tLen;
    ty /= tLen;
    double friction = Math.max(a.getFriction(), b.getFriction());
    if (friction <= 0) return;
    double jt = -(tLen) * friction / invMassSum;
    double ix = jt * tx;
    double iy = jt * ty;
    double invMassA = a.isStatic() ? 0 : 1.0 / a.getMass();
    double invMassB = b.isStatic() ? 0 : 1.0 / b.getMass();
    if (!a.isStatic()) a.setVelocity(a.getVx() - ix * invMassA, a.getVy() - iy * invMassA);
    if (!b.isStatic()) b.setVelocity(b.getVx() + ix * invMassB, b.getVy() + iy * invMassB);
  }

  /** Internal collision contact: normal direction and penetration depth. */
  private static class CollisionInfo {
    double nx;
    double ny;
    double penetration;
  }

  /** Internal axis-aligned bounding box for broadphase. */
  private static class Bounds {
    double minX, minY, maxX, maxY;
  }

  private long hashCell(int cx, int cy) {
    return (((long) cx) << 32) ^ (cy & 0xffffffffL);
  }

  private long pairKey(int a, int b) {
    int lo = Math.min(a, b);
    int hi = Math.max(a, b);
    return (((long) lo) << 32) ^ (hi & 0xffffffffL);
  }

  private double clamp(double v, double min, double max) {
    return v < min ? min : Math.min(v, max);
  }

  /** Clamp a body inside the world bounds and reflect velocity on contact. */
  private void resolveWorldBounds(RigidBody2D b) {
    if (bounds == null) return;
    if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      var cir = b.getCircle();
      if (cir.x - cir.r < bounds.left()) { cir.x = bounds.left() + cir.r; reflectVelocityAlong(b, 1, 0); if (collisionListener != null) collisionListener.onBoundsCollide(b, "left"); }
      if (cir.x + cir.r > bounds.right()) { cir.x = bounds.right() - cir.r; reflectVelocityAlong(b, -1, 0); if (collisionListener != null) collisionListener.onBoundsCollide(b, "right"); }
      if (cir.y - cir.r < bounds.top()) { cir.y = bounds.top() + cir.r; reflectVelocityAlong(b, 0, 1); if (collisionListener != null) collisionListener.onBoundsCollide(b, "top"); }
      if (cir.y + cir.r > bounds.bottom()) { cir.y = bounds.bottom() - cir.r; reflectVelocityAlong(b, 0, -1); if (collisionListener != null) collisionListener.onBoundsCollide(b, "bottom"); }
    } else {
      var r = b.getAabb();
      if (r.left() < bounds.left()) { r.x = bounds.left(); reflectVelocityAlong(b, 1, 0); if (collisionListener != null) collisionListener.onBoundsCollide(b, "left"); }
      if (r.right() > bounds.right()) { r.x = bounds.right() - r.w; reflectVelocityAlong(b, -1, 0); if (collisionListener != null) collisionListener.onBoundsCollide(b, "right"); }
      if (r.top() < bounds.top()) { r.y = bounds.top(); reflectVelocityAlong(b, 0, 1); if (collisionListener != null) collisionListener.onBoundsCollide(b, "top"); }
      if (r.bottom() > bounds.bottom()) { r.y = bounds.bottom() - r.h; reflectVelocityAlong(b, 0, -1); if (collisionListener != null) collisionListener.onBoundsCollide(b, "bottom"); }
    }
  }

  /** Resolve collisions between a dynamic body and all static rectangles. */
  private void resolveStaticColliders(RigidBody2D b) {
    if (b.isStatic() || b.isSensor()) return;
    for (Rect tile : staticRects) {
      if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) resolveStaticCircle(b, tile);
      else resolveStaticAabb(b, tile);
    }
  }

  /** Resolve a circle body against a static rectangle. */
  private void resolveStaticCircle(RigidBody2D body, Rect tile) {
    var c = body.getCircle();
    double closestX = clamp(c.x, tile.left(), tile.right());
    double closestY = clamp(c.y, tile.top(), tile.bottom());
    double dx = closestX - c.x;
    double dy = closestY - c.y;
    double dist2 = dx * dx + dy * dy;
    double r = c.r;
    if (dist2 > r * r) return;
    double nx, ny, penetration;
    if (dist2 > 1e-9) {
      double dist = Math.sqrt(dist2);
      nx = dx / dist;
      ny = dy / dist;
      penetration = r - dist;
    } else {
      double leftPen = c.x - tile.left();
      double rightPen = tile.right() - c.x;
      double topPen = c.y - tile.top();
      double bottomPen = tile.bottom() - c.y;
      penetration = Math.min(Math.min(leftPen, rightPen), Math.min(topPen, bottomPen));
      if (penetration == leftPen) { nx = 1; ny = 0; }
      else if (penetration == rightPen) { nx = -1; ny = 0; }
      else if (penetration == topPen) { nx = 0; ny = 1; }
      else { nx = 0; ny = -1; }
    }
    body.setPosition(body.getX() - nx * penetration, body.getY() - ny * penetration);
    reflectVelocityAlong(body, nx, ny);
    applyStaticFriction(body, nx, ny);
    if (collisionListener != null) collisionListener.onStaticCollide(body, tile, nx, ny);
  }

  /** Resolve an AABB body against a static rectangle. */
  private void resolveStaticAabb(RigidBody2D body, Rect tile) {
    Rect r = body.getAabb();
    if (!r.intersects(tile)) return;
    double overlapX1 = r.right() - tile.left();
    double overlapX2 = tile.right() - r.left();
    double overlapY1 = r.bottom() - tile.top();
    double overlapY2 = tile.bottom() - r.top();
    double minOverlapX = Math.min(overlapX1, overlapX2);
    double minOverlapY = Math.min(overlapY1, overlapY2);
    double nx, ny, penetration;
    if (minOverlapX < minOverlapY) {
      penetration = minOverlapX;
      nx = (overlapX1 < overlapX2) ? 1 : -1;
      ny = 0;
    } else {
      penetration = minOverlapY;
      ny = (overlapY1 < overlapY2) ? 1 : -1;
      nx = 0;
    }
    r.x -= nx * penetration;
    r.y -= ny * penetration;
    reflectVelocityAlong(body, nx, ny);
    applyStaticFriction(body, nx, ny);
    if (collisionListener != null) collisionListener.onStaticCollide(body, tile, nx, ny);
  }

  /** Reflect a body's velocity along a collision normal, applying restitution. */
  private void reflectVelocityAlong(RigidBody2D body, double nx, double ny) {
    double vn = body.getVx() * nx + body.getVy() * ny;
    double rx = body.getVx() - (1 + body.getRestitution()) * vn * nx;
    double ry = body.getVy() - (1 + body.getRestitution()) * vn * ny;
    body.setVelocity(rx, ry);
  }

  /** Reduce tangential velocity after a static collision using the body's friction. */
  private void applyStaticFriction(RigidBody2D body, double nx, double ny) {
    double vx = body.getVx();
    double vy = body.getVy();
    double normal = vx * nx + vy * ny;
    double tx = vx - normal * nx;
    double ty = vy - normal * ny;
    double tLen = Math.sqrt(tx * tx + ty * ty);
    if (tLen < 1e-6) return;
    double friction = body.getFriction();
    double scale = Math.max(0, 1.0 - friction);
    double finalVx = normal * nx + tx * scale;
    double finalVy = normal * ny + ty * scale;
    body.setVelocity(finalVx, finalVy);
  }

  /** Dispatch sensor overlap events without applying collision response. */
  private void handleSensor(RigidBody2D a, RigidBody2D b, CollisionInfo info) {
    if (sensorListener == null || info == null) return;
    if (a.isSensor() && !b.isSensor()) sensorListener.onTrigger(a, b);
    if (b.isSensor() && !a.isSensor()) sensorListener.onTrigger(b, a);
  }

  /** Raycast a segment against a circle-shaped body. */
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

  /** Raycast a segment against an AABB-shaped body (slab method). */
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

}
