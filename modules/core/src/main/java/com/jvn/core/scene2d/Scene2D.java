package com.jvn.core.scene2d;

import com.jvn.core.scene.Scene;

/**
 * A {@link Scene} that adds a 2D rendering pass to the standard update lifecycle.
 *
 * <p>After the engine completes its update phases ({@code fixedUpdate → update → lateUpdate}),
 * the platform renderer calls {@link #render(Blitter2D, double, double)} to draw the scene's
 * visual content onto the canvas. The {@code width} and {@code height} parameters represent
 * the <b>logical</b> (target-resolution) dimensions, already adjusted by
 * {@link com.jvn.core.graphics.ViewportScaler2D}.</p>
 *
 * @see Scene
 * @see Blitter2D
 * @see Entity2D
 */
public interface Scene2D extends Scene {

  /**
   * Draw the scene's 2D content.
   *
   * @param b      platform-specific 2D rendering context
   * @param width  logical canvas width in target-resolution pixels
   * @param height logical canvas height in target-resolution pixels
   */
  void render(Blitter2D b, double width, double height);
}
