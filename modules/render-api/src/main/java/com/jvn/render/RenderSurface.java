package com.jvn.render;

/**
 * Abstraction for a rendering target surface (canvas, framebuffer, HTML element, etc.).
 *
 * <p>Concrete backends provide target-specific surfaces. The JavaFX and Swing
 * desktop paths are supported; mobile surfaces remain architectural scaffolds,
 * while the web surface has an executable Canvas 2D bootstrap but not full game
 * scene support.</p>
 */
public interface RenderSurface {

  /**
   * Get the width of this render surface in logical pixels.
   */
  double getWidth();

  /**
   * Get the height of this render surface in logical pixels.
   */
  double getHeight();

  /**
   * Get the physical pixel density multiplier (DPI scale).
   *
   * <p>On desktop, this is typically 1.0 (1x display) or 2.0+ (Retina, 4K, etc.).
   * On mobile, this varies widely. Renderers should use this to scale stroke widths,
   * font sizes, and other pixel-precise drawing operations for crisp output.</p>
   *
   * @return pixel scale; at minimum 1.0
   */
  double getPixelScale();

  /**
   * Request the surface to present the next frame to the display.
   * Called once per render loop iteration after all drawing is complete.
   */
  void present();

  /**
   * Check whether this surface is valid and ready for rendering.
   * Returns false if the surface has been disposed or is in an invalid state.
   */
  boolean isValid();

  /**
   * Dispose of the render surface and release associated resources.
   * After calling dispose(), the surface must not be used.
   */
  void dispose();
}
