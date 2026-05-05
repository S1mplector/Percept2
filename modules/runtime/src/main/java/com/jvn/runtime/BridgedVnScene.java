package com.jvn.runtime;

import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScene;

/**
 * VnScene that notifies a callback on exit so JES can resume.
 */
public class BridgedVnScene extends VnScene {
  private Runnable onExit;
  private Runnable onEnter;

  public BridgedVnScene(VnScenario scenario) {
    super(scenario);
  }

  public void setOnEnter(Runnable onEnter) { this.onEnter = onEnter; }
  public void setOnExit(Runnable onExit) { this.onExit = onExit; }

  @Override
  public void onEnter() {
    if (onEnter != null) {
      try { onEnter.run(); } catch (Exception ignored) {}
    }
    super.onEnter();
  }

  @Override
  public void onExit() {
    if (onExit != null) {
      try { onExit.run(); } catch (Exception ignored) {}
    }
    super.onExit();
  }
}
