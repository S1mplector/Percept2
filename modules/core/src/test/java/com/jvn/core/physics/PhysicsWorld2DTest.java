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
}
