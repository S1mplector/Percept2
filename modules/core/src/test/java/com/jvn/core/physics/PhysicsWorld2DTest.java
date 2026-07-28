package com.jvn.core.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PhysicsWorld2DTest {
  @Test
  public void clampsDeltaAndAppliesDamping() {
    PhysicsWorld2D world = new PhysicsWorld2D();
    RigidBody2D body = RigidBody2D.box(0, 0, 1, 1);
    body.setVelocity(100, 0);
    body.setLinearDamping(2.0); // strong damping
    world.addBody(body);

    world.step(200); // clamped to 50ms

    assertEquals(4.5, body.getX(), 1e-3);
    assertEquals(0.0, body.getY(), 1e-6);
    assertEquals(90.0, body.getVx(), 1e-3);
  }

  @Test
  public void nonFiniteInputsCannotPoisonTheSimulation() {
    PhysicsWorld2D world = new PhysicsWorld2D();
    world.setGravity(Double.NaN, Double.POSITIVE_INFINITY);
    world.setMaxStepMs(Double.NaN);
    world.setFixedTimeStepMs(Double.NaN, 4);
    RigidBody2D body = RigidBody2D.box(Double.NaN, 2, -4, Double.POSITIVE_INFINITY);
    body.setVelocity(Double.NaN, Double.NEGATIVE_INFINITY);
    body.setMass(Double.NaN);
    world.addBody(body);

    world.step(Double.NaN);
    world.step(16);

    assertTrue(Double.isFinite(body.getX()));
    assertTrue(Double.isFinite(body.getY()));
    assertTrue(Double.isFinite(body.getVx()));
    assertTrue(Double.isFinite(body.getVy()));
    assertTrue(body.getAabb().w >= 0 && body.getAabb().h >= 0);
  }

  @Test
  public void collisionCallbacksCanSafelyMutateBodies() {
    PhysicsWorld2D world = new PhysicsWorld2D();
    RigidBody2D a = RigidBody2D.circle(0, 0, 2);
    RigidBody2D b = RigidBody2D.circle(1, 0, 2);
    RigidBody2D replacement = RigidBody2D.box(20, 20, 1, 1);
    world.addBody(a);
    world.addBody(b);
    world.setCollisionListener(new PhysicsWorld2D.CollisionListener() {
      @Override public void onBodiesCollide(RigidBody2D left, RigidBody2D right, double nx, double ny) {
        world.removeBody(b);
        world.addBody(replacement);
      }
      @Override public void onBoundsCollide(RigidBody2D body, String side) {}
      @Override public void onStaticCollide(RigidBody2D body, com.jvn.core.math.Rect tile, double nx, double ny) {}
    });

    assertDoesNotThrow(() -> world.step(16));
    assertEquals(2, world.getBodies().size());
    assertFalse(world.getBodies().contains(b));
    assertTrue(world.getBodies().contains(replacement));
  }

  @Test
  public void oversizedBroadphaseShapeFallsBackWithoutWalkingBillionsOfCells() {
    PhysicsWorld2D world = new PhysicsWorld2D();
    world.addBody(RigidBody2D.box(0, 0, 1e15, 1e15));
    world.addBody(RigidBody2D.circle(10, 10, 1));
    assertDoesNotThrow(() -> world.step(16));
  }
}
