package com.jvn.scripting.jes.runtime;

import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Entity2D;

public class PhysicsBodyEntity2D extends Entity2D {
  private final RigidBody2D body;
  private double r = 0.9, g = 0.9, b = 0.9, a = 1.0;
  private double sr = 0, sg = 0, sb = 0, sa = 1.0, sw = 0.01;

  public PhysicsBodyEntity2D(RigidBody2D body) { this.body = body; }
  public RigidBody2D getBody() { return body; }

  public void setColor(double r, double g, double b, double a) { this.r = r; this.g = g; this.b = b; this.a = a; }
  public void setStroke(double r, double g, double b, double a, double w) { this.sr = r; this.sg = g; this.sb = b; this.sa = a; this.sw = Math.max(0, w); }
  public double getColorR() { return r; }
  public double getColorG() { return g; }
  public double getColorB() { return b; }
  public double getColorA() { return a; }

  @Override
  public void render(Blitter2D blit) {
    if (body == null) return;
    blit.push();
    blit.setGlobalAlpha(a);
    blit.setFill(r, g, b, a);
    if (body.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
      double cx = body.getCircle().x;
      double cy = body.getCircle().y;
      double rr = body.getCircle().r;
      blit.fillCircle(cx, cy, rr);
      if (sw > 0) { blit.setStroke(sr, sg, sb, sa); blit.setStrokeWidth(sw); blit.strokeCircle(cx, cy, rr); }
    } else {
      var aabb = body.getAabb();
      blit.fillRect(aabb.x, aabb.y, aabb.w, aabb.h);
      if (sw > 0) { blit.setStroke(sr, sg, sb, sa); blit.setStrokeWidth(sw); blit.strokeRect(aabb.x, aabb.y, aabb.w, aabb.h); }
    }
    blit.pop();
  }
}
