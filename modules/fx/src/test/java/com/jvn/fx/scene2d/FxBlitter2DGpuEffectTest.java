package com.jvn.fx.scene2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FxBlitter2DGpuEffectTest {

  @Test
  void recognizesExactDimmingMatrixForGpuShader() {
    double[] matrix = brightnessMatrix(0.35);

    assertEquals(0.35, FxBlitter2D.gpuBrightnessFor(matrix), 0.0000001);
  }

  @Test
  void leavesExposureAndGeneralColorMatricesOnExactCpuFallback() {
    assertTrue(Double.isNaN(FxBlitter2D.gpuBrightnessFor(brightnessMatrix(1.0))));
    assertTrue(Double.isNaN(FxBlitter2D.gpuBrightnessFor(brightnessMatrix(1.2))));

    double[] tinted = brightnessMatrix(0.8);
    tinted[1] = 0.1;
    assertTrue(Double.isNaN(FxBlitter2D.gpuBrightnessFor(tinted)));
  }

  @Test
  void rejectsMissingAndNonFiniteMatrices() {
    assertTrue(Double.isNaN(FxBlitter2D.gpuBrightnessFor(null)));
    assertTrue(Double.isNaN(FxBlitter2D.gpuBrightnessFor(new double[4])));

    double[] matrix = brightnessMatrix(0.5);
    matrix[0] = Double.NaN;
    assertTrue(Double.isNaN(FxBlitter2D.gpuBrightnessFor(matrix)));
  }

  private static double[] brightnessMatrix(double brightness) {
    return new double[] {
        brightness, 0.0, 0.0, 0.0, 0.0,
        0.0, brightness, 0.0, 0.0, 0.0,
        0.0, 0.0, brightness, 0.0, 0.0,
        0.0, 0.0, 0.0, 1.0, 0.0
    };
  }
}
