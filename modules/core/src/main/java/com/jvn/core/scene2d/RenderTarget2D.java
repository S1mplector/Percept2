package com.jvn.core.scene2d;

/**
 * Backend-owned offscreen surface that can be drawn through a {@link Blitter2D}
 * and later composited into another surface from the same backend.
 */
public interface RenderTarget2D extends AutoCloseable {
  double getWidth();
  double getHeight();
  double getPixelScale();
  Blitter2D getBlitter();
  boolean isValid();
  void dispose();

  @Override
  default void close() {
    dispose();
  }
}
