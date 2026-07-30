package com.jvn.fx.scene2d;

/** Bulk CPU pixel effects used only when JavaFX cannot express an effect in the Prism pipeline. */
final class PixelEffects {
  private PixelEffects() {
  }

  static void applyAlphaMask(
      int[] content,
      int contentWidth,
      int contentHeight,
      int[] mask,
      int maskWidth,
      int maskHeight) {
    if (content == null || mask == null
        || contentWidth <= 0 || contentHeight <= 0
        || maskWidth <= 0 || maskHeight <= 0) {
      return;
    }
    for (int y = 0; y < contentHeight; y++) {
      int maskY = Math.min(maskHeight - 1, (int) ((long) y * maskHeight / contentHeight));
      int contentRow = y * contentWidth;
      int maskRow = maskY * maskWidth;
      for (int x = 0; x < contentWidth; x++) {
        int maskX = Math.min(maskWidth - 1, (int) ((long) x * maskWidth / contentWidth));
        int index = contentRow + x;
        int argb = content[index];
        int maskAlpha = (mask[maskRow + maskX] >>> 24) & 0xff;
        int contentAlpha = (argb >>> 24) & 0xff;
        int resultAlpha = contentAlpha * maskAlpha / 255;
        content[index] = (argb & 0x00ffffff) | (resultAlpha << 24);
      }
    }
  }

  static void applyColorMatrix(int[] pixels, double[] matrix) {
    if (pixels == null || matrix == null || matrix.length < 20) return;
    for (int i = 0; i < pixels.length; i++) {
      int argb = pixels[i];
      double a = ((argb >>> 24) & 0xff) / 255.0;
      double r = ((argb >>> 16) & 0xff) / 255.0;
      double g = ((argb >>> 8) & 0xff) / 255.0;
      double b = (argb & 0xff) / 255.0;

      double outR = clamp01(matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4]);
      double outG = clamp01(matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9]);
      double outB = clamp01(matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14]);
      double outA = clamp01(matrix[15] * r + matrix[16] * g + matrix[17] * b + matrix[18] * a + matrix[19]);

      pixels[i] =
          ((int) Math.round(outA * 255.0) << 24)
              | ((int) Math.round(outR * 255.0) << 16)
              | ((int) Math.round(outG * 255.0) << 8)
              | (int) Math.round(outB * 255.0);
    }
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
