package com.jvn.scripting.jes.runtime;

import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Entity2D;

public class PhysicsDebugOverlay2D extends Entity2D {
  private final JesScene2D scene;
  public PhysicsDebugOverlay2D(JesScene2D scene) { this.scene = scene; }

  @Override
  public void render(Blitter2D b) {
    if (scene == null || scene.getWorld() == null) return;
    b.push();
    b.setStroke(1, 0.2, 0.2, 0.9);
    b.setStrokeWidth(0.01);
    for (RigidBody2D body : scene.getWorld().getBodies()) {
      if (body.isSensor()) { b.setStroke(0.2,0.6,1,0.9); } else { b.setStroke(1,0.2,0.2,0.9); }
      if (body.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
        double r = body.getCircle().r;
        b.strokeCircle(body.getCircle().x, body.getCircle().y, r);
      } else {
        var a = body.getAabb();
        b.strokeRect(a.x, a.y, a.w, a.h);
      }
    }
    b.pop();
  }
}
