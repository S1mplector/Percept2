package com.jvn.fx.scene2d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class PixelEffectsTest {
  @Test
  void alphaMaskScalesWithNearestNeighborAndPreservesRgb() {
    int[] content = {
        0xff112233, 0x80445566,
        0x40778899, 0x20aabbcc
    };
    int[] mask = {0xff000000, 0x80000000};

    PixelEffects.applyAlphaMask(content, 2, 2, mask, 1, 2);

    assertArrayEquals(new int[] {
        0xff112233, 0x80445566,
        0x20778899, 0x10aabbcc
    }, content);
  }

  @Test
  void colorMatrixProducesTheSameArgbChannelTransform() {
    int[] pixels = {0x80402010};
    double[] swapRedAndBlue = {
        0, 0, 1, 0, 0,
        0, 1, 0, 0, 0,
        1, 0, 0, 0, 0,
        0, 0, 0, 1, 0
    };

    PixelEffects.applyColorMatrix(pixels, swapRedAndBlue);

    assertArrayEquals(new int[] {0x80102040}, pixels);
  }
}
