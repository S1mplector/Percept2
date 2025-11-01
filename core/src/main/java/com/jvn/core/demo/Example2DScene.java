package com.jvn.core.demo;

import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.math.Rect;
import com.jvn.core.input.ActionMap;

import java.util.ArrayList;
import java.util.List;

public class Example2DScene extends Scene2DBase {
  private static class GridEntity extends Entity2D {
    final double spacing;
    final double r, g, b, a;
    final Rect area;
    GridEntity(double spacing, double r, double g, double b, double a, Rect area) {
      this.spacing = spacing; this.r = r; this.g = g; this.b = b; this.a = a; this.area = area;
    }
    @Override public void render(Blitter2D bl) {
      bl.push();
      bl.setGlobalAlpha(a);
      bl.setStroke(r, g, b, 1);
      bl.setStrokeWidth(1);
      for (int x = 0; x <= area.w; x += spacing) bl.drawLine(x, 0, x, area.h);
      for (int y = 0; y <= area.h; y += spacing) bl.drawLine(0, y, area.w, y);
      bl.pop();
    }
  }

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
  private boolean debug = false;
  private boolean follow = false;
  private ActionMap actions;

  @Override public void onEnter() {
    setCamera(new com.jvn.core.graphics.Camera2D());
    if (getInput() == null) setInput(null);
    actions = (getInput() != null) ? new ActionMap(getInput()) : null;
    if (actions != null) {
      actions.bindKey("up", "W").bindKey("down", "S").bindKey("left", "A").bindKey("right", "D");
    }
    world.setBounds(bounds);
    if (camera != null) {
      camera.setSmoothingMs(250);
      camera.setBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
      camera.setTarget(bounds.w * 0.5, bounds.h * 0.5);
      camera.setPosition(bounds.w * 0.5, bounds.h * 0.5);
    }

    GridEntity gridFar = new GridEntity(200, 0.15, 0.15, 0.18, 1.0, bounds);
    gridFar.setZ(0);
    gridFar.setParallax(0.7, 0.7);
    add(gridFar);
    GridEntity gridNear = new GridEntity(100, 0.25, 0.25, 0.3, 1.0, bounds);
    gridNear.setZ(0.1);
    gridNear.setParallax(0.85, 0.85);
    add(gridNear);

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
      boolean moved = false;
      if (actions != null && actions.isDown("up")) { camera.setTarget(camera.getTargetX(), camera.getTargetY() - speed); moved = true; }
      if (actions != null && actions.isDown("down")) { camera.setTarget(camera.getTargetX(), camera.getTargetY() + speed); moved = true; }
      if (actions != null && actions.isDown("left")) { camera.setTarget(camera.getTargetX() - speed, camera.getTargetY()); moved = true; }
      if (actions != null && actions.isDown("right")) { camera.setTarget(camera.getTargetX() + speed, camera.getTargetY()); moved = true; }

      if (input.wasKeyPressed("F1")) debug = !debug;
      if (input.wasKeyPressed("F")) follow = !follow;

      if (follow && !bodies.isEmpty()) {
        RigidBody2D rb = bodies.get(0);
        camera.setTarget(rb.getX(), rb.getY());
      } else if (moved) {
      }

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
    super.render(b, width, height);
    b.setFill(1, 1, 1, 1);
    b.setFont("Arial", 14, false);
    b.drawText("WASD to pan (smoothed), F=Follow, F1=Debug, Scroll to zoom", 16, 24, 14, false);
    if (debug && camera != null) {
      b.drawText(String.format("Cam(%.1f, %.1f) z=%.2f", camera.getX(), camera.getY(), camera.getZoom()), 16, 44, 14, false);
      b.drawText(String.format("Bodies=%d Entities=%d", bodies.size(), children.size()), 16, 64, 14, false);
    }
  }
}

