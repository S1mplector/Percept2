package com.jvn.fx;

import javafx.scene.image.Image;

/** Conservative heap-size estimates used to byte-bound JavaFX raster caches. */
public final class FxImageMemory {
  private static final long BYTES_PER_PIXEL = 4L;

  private FxImageMemory() {}

  public static long estimatedBytes(Image image) {
    if (image == null) return 1L;
    long width = finiteDimension(image.getWidth());
    long height = finiteDimension(image.getHeight());
    if (width > Long.MAX_VALUE / height / BYTES_PER_PIXEL) return Long.MAX_VALUE;
    return Math.max(1L, width * height * BYTES_PER_PIXEL);
  }

  private static long finiteDimension(double value) {
    if (!Double.isFinite(value) || value < 1.0) return 1L;
    if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return Math.max(1L, (long) Math.ceil(value));
  }
}
