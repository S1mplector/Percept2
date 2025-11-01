package com.jvn.core.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ActionMap {
  private final Input input;
  private final Map<String, Set<String>> keyBindings = new HashMap<>();
  private final Map<String, Set<Integer>> mouseBindings = new HashMap<>();

  public ActionMap(Input input) { this.input = input; }

  public ActionMap bindKey(String action, String keyName) {
    keyBindings.computeIfAbsent(action, k -> new HashSet<>()).add(keyName);
    return this;
  }

  public ActionMap unbindKey(String action, String keyName) {
    Set<String> set = keyBindings.get(action);
    if (set != null) set.remove(keyName);
    return this;
  }

  public ActionMap bindMouse(String action, int button) {
    mouseBindings.computeIfAbsent(action, k -> new HashSet<>()).add(button);
    return this;
  }

  public ActionMap unbindMouse(String action, int button) {
    Set<Integer> set = mouseBindings.get(action);
    if (set != null) set.remove(button);
    return this;
  }

  public boolean isDown(String action) {
    Set<String> ks = keyBindings.get(action);
    if (ks != null) for (String k : ks) if (input.isKeyDown(k)) return true;
    Set<Integer> ms = mouseBindings.get(action);
    if (ms != null) for (Integer b : ms) if (input.isMouseDown(b)) return true;
    return false;
  }

  public boolean wasPressed(String action) {
    Set<String> ks = keyBindings.get(action);
    if (ks != null) for (String k : ks) if (input.wasKeyPressed(k)) return true;
    Set<Integer> ms = mouseBindings.get(action);
    if (ms != null) for (Integer b : ms) if (input.wasMousePressed(b)) return true;
    return false;
  }

  public boolean wasReleased(String action) {
    Set<String> ks = keyBindings.get(action);
    if (ks != null) for (String k : ks) if (input.wasKeyReleased(k)) return true;
    Set<Integer> ms = mouseBindings.get(action);
    if (ms != null) for (Integer b : ms) if (input.wasMouseReleased(b)) return true;
    return false;
  }
}
