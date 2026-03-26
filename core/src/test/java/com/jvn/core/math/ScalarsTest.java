package com.jvn.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScalarsTest {

  @Test
  void clampsDoubleFloatAndIntValues() {
    assertEquals(2.0, Scalars.clamp(5.0, -1.0, 2.0));
    assertEquals(-1.0, Scalars.clamp(-3.0, -1.0, 2.0));
    assertEquals(0.25, Scalars.clamp01(0.25));
    assertEquals(1.0, Scalars.clamp01(3.0));
    assertEquals(2.0f, Scalars.clamp(5.0f, 2.0f, -1.0f));
    assertEquals(3, Scalars.clamp(8, 0, 3));
  }

  @Test
  void computesInverseLerpAndRemap() {
    assertEquals(0.25, Scalars.inverseLerp(10.0, 20.0, 12.5), 1e-9);
    assertEquals(50.0, Scalars.remap(0.5, 0.0, 1.0, 0.0, 100.0), 1e-9);
    assertEquals(-5.0, Scalars.remap(0.25, 0.0, 1.0, -10.0, 10.0), 1e-9);
    assertEquals(0.0, Scalars.inverseLerp(5.0, 5.0, 99.0), 1e-9);
  }

  @Test
  void supportsApproximateEqualityChecks() {
    assertTrue(Scalars.approxEquals(1.0, 1.0 + 1e-10));
    assertTrue(Scalars.approxEquals(10_000.0, 10_000.0 + 1e-5, 1e-9));
    assertFalse(Scalars.approxEquals(1.0, 1.1));
    assertFalse(Scalars.approxEquals(Double.POSITIVE_INFINITY, 1.0));
  }
}
