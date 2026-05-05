package com.jvn.swing;

import com.jvn.core.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingSceneRendererRegistryTest {

  @Test
  void dispatchesToRegisteredRenderer() {
    SwingSceneRendererRegistry reg = new SwingSceneRendererRegistry();
    AtomicBoolean called = new AtomicBoolean(false);
    reg.register(DummyScene.class, (scene, ctx) -> called.set(true));

    reg.render(new DummyScene(), new SwingSceneRendererRegistry.RenderContext(null, null, 0, 0));

    assertTrue(called.get());
  }

  private static final class DummyScene implements Scene {
    @Override public void update(long deltaMs) {}
  }
}
