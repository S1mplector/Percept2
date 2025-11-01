package com.jvn.core.demo;

import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.math.Rect;

import java.util.ArrayList;
import java.util.List;

public class Example2DScene extends Scene2DBase {
  private static class CircleEntity extends Entity2D {
    double radius;
    double r = 0.2, g = 0.7, b = 1.0, a = 1.0;
    CircleEntity(double radius) { this.radius = radius; }
    void setColor(double r, double g, double b, double a) { this.r = r; this.g = g; this.b = b; this.a = a; }
    @Override public void render(Blitter2D bl) {
      bl.push();
      bl.setGlobalAlpha(a);
      bl.setFill(r, g, b, 1);
      bl.fillCircle(0, 0, radius);
      bl.pop();
    }
  }

  private final PhysicsWorld2D world = new PhysicsWorld2D();
  private final List<RigidBody2D> bodies = new ArrayList<>();
  private final List<CircleEntity> entities = new ArrayList<>();
  private Rect bounds = new Rect(0, 0, 1600, 900);

  @Override public void onEnter() {
    setCamera(new com.jvn.core.graphics.Camera2D());
    if (getInput() == null) setInput(null);
    world.setBounds(bounds);
    for (int i = 0; i < 6; i++) {
      double x = 200 + i * 120;
      double y = 200 + (i % 2) * 80;
      double r = 20 + (i % 3) * 8;
      RigidBody2D rb = RigidBody2D.circle(x, y, r);
      rb.setVelocity(80 + i * 15, 60 + i * 10);
      rb.setRestitution(0.9);
      bodies.add(rb);
      world.addBody(rb);
      CircleEntity ce = new CircleEntity(r);
      ce.setColor(0.2 + 0.1 * i, 0.6, 0.9 - 0.1 * i, 1.0);
      ce.setPosition(x, y);
      ce.setZ(1);
      entities.add(ce);
      add(ce);
    }
  }

  @Override public void update(long deltaMs) {
    double dt = deltaMs / 1000.0;
    if (input != null && camera != null) {
      double speed = 400 * dt;
      if (input.isKeyDown("W")) camera.setPosition(camera.getX(), camera.getY() - speed);
      if (input.isKeyDown("S")) camera.setPosition(camera.getX(), camera.getY() + speed);
      if (input.isKeyDown("A")) camera.setPosition(camera.getX() - speed, camera.getY());
      if (input.isKeyDown("D")) camera.setPosition(camera.getX() + speed, camera.getY());
      double scroll = input.getScrollDeltaY();
      if (scroll != 0 && camera.getZoom() > 0) {
        double z = camera.getZoom() + scroll * 0.0015;
        if (z < 0.1) z = 0.1;
        if (z > 4.0) z = 4.0;
        camera.setZoom(z);
      }
    }
    world.step(deltaMs);
    for (int i = 0; i < bodies.size(); i++) {
      RigidBody2D rb = bodies.get(i);
      CircleEntity ce = entities.get(i);
      ce.setPosition(rb.getX(), rb.getY());
    }
    super.update(deltaMs);
  }

  @Override public void render(Blitter2D b, double width, double height) {
    b.clear(0, 0, 0, 1);
    b.push();
    if (camera != null) {
      b.translate(-camera.getX(), -camera.getY());
      b.scale(camera.getZoom(), camera.getZoom());
    }
    b.setStroke(0.2, 0.2, 0.2, 1);
    b.setStrokeWidth(1);
    for (int x = 0; x <= bounds.w; x += 100) b.drawLine(x, 0, x, bounds.h);
    for (int y = 0; y <= bounds.h; y += 100) b.drawLine(0, y, bounds.w, y);
    b.pop();
    super.render(b, width, height);
    b.setFill(1, 1, 1, 1);
    b.setFont("Arial", 14, false);
    b.drawText("WASD to pan, Scroll to zoom", 16, 24, 14, false);
  }
}
