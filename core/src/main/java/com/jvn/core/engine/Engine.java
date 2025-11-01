package com.jvn.core.engine;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.scene.Scene;
import com.jvn.core.scene.SceneManager;
import com.jvn.core.input.Input;
import com.jvn.core.tween.TweenRunner;

public class Engine {
  private final ApplicationConfig config;
  private boolean started;
  private final SceneManager sceneManager = new SceneManager();
  private final Input input = new Input();
  private final TweenRunner tweens = new TweenRunner();

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
     // Update global tween runner
     tweens.update(deltaMs);
     Scene current = sceneManager.peek();
     if (current != null) {
       current.update(deltaMs);
     }
     // Clear per-frame input edges
     input.endFrame();
   }

   public SceneManager scenes() {
     return sceneManager;
   }

   public Input input() {
     return input;
   }

   public TweenRunner tweens() {
     return tweens;
   }
}
