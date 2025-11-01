package com.jvn.core.scene2d;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.input.Input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scene2DBase implements Scene2D {
  protected final List<Entity2D> children = new ArrayList<>();
  protected Camera2D camera;
  protected Input input;

  public void setCamera(Camera2D camera) { this.camera = camera; }
  public Camera2D getCamera() { return camera; }
  public void setInput(Input input) { this.input = input; }
  public Input getInput() { return input; }

  public void add(Entity2D e) { if (e != null) children.add(e); }
  public void remove(Entity2D e) { children.remove(e); }
  public void clear() { children.clear(); }

  @Override public void onEnter() {}
  @Override public void onExit() {}

  @Override
  public void update(long deltaMs) {
    for (int i = 0; i < children.size(); i++) {
      children.get(i).update(deltaMs);
    }
  }

  @Override
  public void render(Blitter2D b, double width, double height) {
    children.sort(Comparator.comparingDouble(Entity2D::getZ));
    b.push();
    if (camera != null) {
      b.translate(-camera.getX(), -camera.getY());
      b.scale(camera.getZoom(), camera.getZoom());
    }
    for (int i = 0; i < children.size(); i++) {
      Entity2D e = children.get(i);
      if (!e.isVisible()) continue;
      b.push();
      b.translate(e.getX(), e.getY());
      if (e.getRotationDeg() != 0) b.rotateDeg(e.getRotationDeg());
      if (e.getScaleX() != 1.0 || e.getScaleY() != 1.0) b.scale(e.getScaleX(), e.getScaleY());
      e.render(b);
      b.pop();
    }
    b.pop();
  }
}
