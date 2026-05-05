package com.jvn.core.physics;

import com.jvn.core.math.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsWorld2DImprovedTest {

  @Test
  void circleAndAabbSeparateAfterCollision() {
    PhysicsWorld2D world = new PhysicsWorld2D();
    RigidBody2D circle = RigidBody2D.circle(0.5, 0.5, 0.5);
    RigidBody2D box = RigidBody2D.box(0.6, 0.5, 0.8, 0.8); // overlap slightly
    world.addBody(circle);
    world.addBody(box);

    world.step(16);

    assertFalse(intersects(circle, box), "Bodies should be separated after resolution");
  }

  @Test
  void staticFrictionDampsTangentialVelocity() {
    PhysicsWorld2D world = new PhysicsWorld2D();
    Rect floor = new Rect(0, 0, 10, 1);
    world.addStaticRect(floor);

    RigidBody2D body = RigidBody2D.box(1, 0.5, 1, 1);
    body.setFriction(0.5);
    body.setVelocity(4, -1); // moving down onto the floor with lateral speed
    world.addBody(body);

    world.step(16);

    assertTrue(Math.abs(body.getVx()) < 4, "Friction should reduce tangential speed");
    assertTrue(body.getY() >= floor.bottom() - 0.001 || body.getAabb().bottom() >= floor.bottom() - 0.001,
        "Body should be pushed out of the floor");
  }

  @Test
  void sensorsTriggerWhenOverlapping() {
    PhysicsWorld2D world = new PhysicsWorld2D();
    RigidBody2D sensor = RigidBody2D.circle(0, 0, 1);
    sensor.setSensor(true);
    RigidBody2D body = RigidBody2D.circle(0.5, 0, 1);
    final int[] triggered = {0};
    world.setSensorListener((s, other) -> triggered[0]++);
    world.addBody(sensor);
    world.addBody(body);

    world.step(16);

    assertTrue(triggered[0] > 0, "Sensor should trigger when overlapping another body");
  }

  private boolean intersects(RigidBody2D a, RigidBody2D b) {
    if (a.getShapeType() == RigidBody2D.ShapeType.CIRCLE && b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      var ca = a.getCircle();
      var cb = b.getCircle();
      double dx = cb.x - ca.x;
      double dy = cb.y - ca.y;
      double rsum = ca.r + cb.r;
      return dx * dx + dy * dy < rsum * rsum;
    } else if (a.getShapeType() == RigidBody2D.ShapeType.AABB && b.getShapeType() == RigidBody2D.ShapeType.AABB) {
      return a.getAabb().intersects(b.getAabb());
    } else if (a.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      return circleIntersectsRect(a.getCircle(), b.getAabb());
    } else {
      return circleIntersectsRect(b.getCircle(), a.getAabb());
    }
  }

  private boolean circleIntersectsRect(com.jvn.core.math.Circle c, Rect r) {
    double closestX = clamp(c.x, r.left(), r.right());
    double closestY = clamp(c.y, r.top(), r.bottom());
    double dx = closestX - c.x;
    double dy = closestY - c.y;
    return dx * dx + dy * dy < c.r * c.r;
  }

  private double clamp(double v, double min, double max) {
    return v < min ? min : Math.min(v, max);
  }
}
