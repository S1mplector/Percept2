package com.jvn.fx.render;

import com.jvn.core.scene.Scene;
import com.jvn.fx.scene2d.FxBlitter2D;
import javafx.scene.canvas.GraphicsContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Simple registry that maps scene types to renderer callbacks so the launcher
 * does not need to hard-code instanceof chains.
 */
public class FxSceneRendererRegistry {
  public record RenderContext(GraphicsContext gc, FxBlitter2D blitter,
                              double width, double height,
                              double mouseX, double mouseY) {}

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

  /**
   * Render the given scene using the first renderer that supports its type.
   * @return true if a renderer handled the scene.
   */
  public boolean render(Scene scene, RenderContext ctx) {
    if (scene == null) return false;
    for (Entry<?> entry : renderers) {
      if (entry.type.isInstance(scene)) {
        @SuppressWarnings("unchecked")
        Entry<Scene> e = (Entry<Scene>) entry;
        e.renderer.accept(scene, ctx);
        return true;
      }
    }
    return false;
  }
}
