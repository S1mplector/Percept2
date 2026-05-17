package com.jvn.fx.render;

import com.jvn.render.RenderSurface;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

/**
 * JavaFX implementation of {@code RenderSurface} wrapping a JavaFX {@code Canvas}.
 */
public class FxRenderSurface implements RenderSurface {

  private final Canvas canvas;
  private final GraphicsContext gc;

  public FxRenderSurface(Canvas canvas) {
    this.canvas = canvas;
    this.gc = canvas.getGraphicsContext2D();
  }

  public GraphicsContext getGraphicsContext() {
    return gc;
  }

  @Override
  public double getWidth() {
    return canvas.getWidth();
  }

  @Override
  public double getHeight() {
    return canvas.getHeight();
  }

  @Override
  public double getPixelScale() {
    // JavaFX Canvas already handles DPI scaling internally
    // Return 1.0 as the logical pixel scale
    return 1.0;
  }

  @Override
  public void present() {
    // JavaFX Canvas renders directly to screen; no explicit present needed
  }

  @Override
  public boolean isValid() {
    return canvas.getScene() != null;
  }

  @Override
  public void dispose() {
    // Canvas cleanup is handled by JavaFX GC
  }
}
