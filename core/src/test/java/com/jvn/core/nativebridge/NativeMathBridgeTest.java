package com.jvn.core.nativebridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class NativeMathBridgeTest {
  @Test
  void dotProductMatches() {
    double[] a = {1, 2, 3};
    double[] b = {4, 5, 6};
    double res = NativeMathBridge.dotProduct(a, b);
    assertEquals(32.0, res, 1e-9);
  }

  @Test
  void matMulMatches() {
    double[] a = {
      1, 2, 3,
      4, 5, 6
    };
    double[] b = {
      7, 8,
      9, 10,
      11, 12
    };
    double[] out = NativeMathBridge.matMul(a, b, 2, 3, 2);
    assertArrayEquals(new double[] {58, 64, 139, 154}, out, 1e-9);
  }

  @Test
  void matVecMatches() {
    double[] a = {
      1, 2, 3,
      4, 5, 6
    };
    double[] x = {2, 3, 4};
    double[] out = NativeMathBridge.matVec(a, x, 2, 3);
    assertArrayEquals(new double[] {20, 47}, out, 1e-9);
  }

  @Test
  void matMulBlockedMatches() {
    double[] a = {
      1, 2, 3,
      4, 5, 6
    };
    double[] b = {
      7, 8,
      9, 10,
      11, 12
    };
    double[] out = NativeMathBridge.matMulBlocked(a, b, 2, 3, 2, 2);
    assertArrayEquals(new double[] {58, 64, 139, 154}, out, 1e-9);
  }

  @Test
  void conv2dMatches() {
    double[] input = {
      1, 2, 1,
      0, 1, 0,
      2, 1, 2
    };
    double[] kernel = {
      0, 1, 0,
      1, -4, 1,
      0, 1, 0
    };
    double[] out = NativeMathBridge.conv2d(input, 3, 3, kernel, 3, 3);
    assertArrayEquals(new double[] {
      -2, -5, -2,
      4, -1, 4,
      -7, 1, -7
    }, out, 1e-9);
  }
}
