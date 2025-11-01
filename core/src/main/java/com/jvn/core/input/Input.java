package com.jvn.core.input;

import java.util.HashSet;
import java.util.Set;

public class Input {
  private final Set<String> downKeys = new HashSet<>();
  private final Set<String> pressedKeys = new HashSet<>();
  private final Set<String> releasedKeys = new HashSet<>();

  private final Set<Integer> downButtons = new HashSet<>();
  private final Set<Integer> pressedButtons = new HashSet<>();
  private final Set<Integer> releasedButtons = new HashSet<>();

  private double mouseX;
  private double mouseY;

  public void keyDown(String key) {
    if (key == null) return;
    if (downKeys.add(key)) pressedKeys.add(key);
  }

  public void keyUp(String key) {
    if (key == null) return;
    if (downKeys.remove(key)) releasedKeys.add(key);
  }

  public boolean isKeyDown(String key) { return downKeys.contains(key); }
  public boolean wasKeyPressed(String key) { return pressedKeys.contains(key); }
  public boolean wasKeyReleased(String key) { return releasedKeys.contains(key); }

  public void mouseDown(int button) { if (downButtons.add(button)) pressedButtons.add(button); }
  public void mouseUp(int button) { if (downButtons.remove(button)) releasedButtons.add(button); }

  public boolean isMouseDown(int button) { return downButtons.contains(button); }
  public boolean wasMousePressed(int button) { return pressedButtons.contains(button); }
  public boolean wasMouseReleased(int button) { return releasedButtons.contains(button); }

  public void setMousePosition(double x, double y) { this.mouseX = x; this.mouseY = y; }
  public double getMouseX() { return mouseX; }
  public double getMouseY() { return mouseY; }

  public void endFrame() {
    pressedKeys.clear();
    releasedKeys.clear();
    pressedButtons.clear();
    releasedButtons.clear();
  }

  public void reset() {
    downKeys.clear();
    pressedKeys.clear();
    releasedKeys.clear();
    downButtons.clear();
    pressedButtons.clear();
    releasedButtons.clear();
  }
}
