package com.jvn.render;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RendererCapabilities;

/**
 * Service provider interface for creating platform-specific renderer implementations.
 *
 * <p>Each platform (JavaFX, Android, iOS, Web) provides a {@code RendererFactory}
 * implementation discoverable via {@link java.util.ServiceLoader}. The engine
 * loads the appropriate factory at startup and uses it to instantiate the
 * platform-specific {@code Blitter2D} and {@code RenderSurface}.</p>
 *
 * <p>Example: JavaFX provides {@code FxRendererFactory}, Android provides
 * {@code AndroidRendererFactory}, etc. The correct one is selected by the launcher
 * or automatically via {@code ServiceLoader}.</p>
 */
public interface RendererFactory {

  /** Capabilities available from renderers created by this factory. */
  default RendererCapabilities getCapabilities() {
    return RendererCapabilities.baseline(getRendererName());
  }

  /**
   * Create a {@code Blitter2D} for the given render surface.
   *
   * @param surface the target surface to render into
   * @return a new Blitter2D instance
   */
  Blitter2D createBlitter2D(RenderSurface surface);

  /**
   * Get the name of this renderer (for logging and debugging).
   *
   * @return e.g. "JavaFX", "Android", "iOS", "WebAssembly"
   */
  String getRendererName();
}
