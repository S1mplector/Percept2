package com.jvn.core.engine;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.scene.Scene;
import com.jvn.core.scene.SceneManager;

public class Engine {
  private final ApplicationConfig config;
  private boolean started;
  private final SceneManager sceneManager = new SceneManager();

  public Engine(ApplicationConfig config) {
    this.config = config;
  }

  public void start() {
    this.started = true;
  }

  public void stop() {
    this.started = false;
  }

  public boolean isStarted() {
    return started;
  }

  public ApplicationConfig getConfig() {
    return config;
  }

   public void update(long deltaMs) {
     if (!started) return;
     Scene current = sceneManager.peek();
     if (current != null) {
       current.update(deltaMs);
     }
   }

   public SceneManager scenes() {
     return sceneManager;
   }
}
