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

  /**
   * Reads the target's full backing pixel buffer as packed ARGB, row-major, one int per pixel
   * at {@code Math.ceil(getWidth()*getPixelScale())} by {@code Math.ceil(getHeight()*getPixelScale())}
   * resolution. Gated behind {@link RenderFeature#PIXEL_ACCESS}.
   */
  default int[] readPixelsArgb() {
    getBlitter().require(RenderFeature.PIXEL_ACCESS);
    throw new IllegalStateException("Render target advertised pixel access without implementing readPixelsArgb");
  }

  /**
   * Replaces the target's full backing pixel buffer from packed ARGB, row-major data matching
   * the layout {@link #readPixelsArgb()} returns. Gated behind {@link RenderFeature#PIXEL_ACCESS}.
   */
  default void writePixelsArgb(int[] argb) {
    getBlitter().require(RenderFeature.PIXEL_ACCESS);
    throw new IllegalStateException("Render target advertised pixel access without implementing writePixelsArgb");
  }

  @Override
  default void close() {
    dispose();
  }
}
