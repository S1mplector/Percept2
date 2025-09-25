package com.jvn.core.scene;

import java.util.ArrayDeque;
import java.util.Deque;

public class SceneManager {
  private final Deque<Scene> stack = new ArrayDeque<>();

  public void push(Scene scene) {
    if (scene == null) return;
    if (!stack.isEmpty()) {
      stack.peek().onExit();
    }
    stack.push(scene);
    scene.onEnter();
  }

  public Scene pop() {
    if (stack.isEmpty()) return null;
    Scene s = stack.pop();
    s.onExit();
    if (!stack.isEmpty()) stack.peek().onEnter();
    return s;
  }

  public void replace(Scene scene) {
    if (!stack.isEmpty()) {
      Scene s = stack.pop();
      s.onExit();
    }
    push(scene);
  }

  public Scene peek() { return stack.peek(); }

  public boolean isEmpty() { return stack.isEmpty(); }
}
