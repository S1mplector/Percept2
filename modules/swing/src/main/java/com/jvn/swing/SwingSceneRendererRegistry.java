package com.jvn.swing;

import com.jvn.core.scene.Scene;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Lightweight registry to decouple rendering from instanceof chains in Swing launcher.
 */
public class SwingSceneRendererRegistry {
  public record RenderContext(Graphics2D g2, SwingBlitter2D blitter, double width, double height) {}

  private static class Entry<T extends Scene> {
    final Class<T> type;
    final BiConsumer<T, RenderContext> renderer;
    Entry(Class<T> type, BiConsumer<T, RenderContext> renderer) {
      this.type = type;
      this.renderer = renderer;
    }
  }

  private final List<Entry<?>> renderers = new ArrayList<>();

  public <T extends Scene> void register(Class<T> type, BiConsumer<T, RenderContext> renderer) {
    if (type == null || renderer == null) return;
    renderers.add(new Entry<>(type, renderer));
  }

  public boolean render(Scene scene, RenderContext ctx) {
    if (scene == null) return false;
    for (Entry<?> e : renderers) {
      if (e.type.isInstance(scene)) {
        @SuppressWarnings("unchecked")
        Entry<Scene> entry = (Entry<Scene>) e;
        entry.renderer.accept(scene, ctx);
        return true;
      }
    }
    return false;
  }
}
