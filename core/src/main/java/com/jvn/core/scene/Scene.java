package com.jvn.core.scene;

public interface Scene {
  default void onEnter() {}
  default void onExit() {}
  void update(long deltaMs);
}
