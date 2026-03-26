package com.jvn.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MathPrimitivesTest {

  @Test
  void segmentSupportsBoundsMidpointAndSampling() {
    Segment2 segment = new Segment2(2, 3, -1, 7);

    Rect bounds = segment.bounds(null);
    Vec2 midpoint = segment.midpoint(null);
    Vec2 quarter = segment.pointAt(0.25, null);

    assertEquals(-1.0, bounds.x, 1e-9);
    assertEquals(3.0, bounds.y, 1e-9);
    assertEquals(3.0, bounds.w, 1e-9);
    assertEquals(4.0, bounds.h, 1e-9);
    assertEquals(0.5, midpoint.x, 1e-9);
    assertEquals(5.0, midpoint.y, 1e-9);
    assertEquals(1.25, quarter.x, 1e-9);
    assertEquals(4.0, quarter.y, 1e-9);
  }

  @Test
  void raySupportsPointSamplingAndDirectionNormalization() {
    Ray2 ray = new Ray2(1, 2, 3, 4);

    Vec2 point = ray.pointAt(2.0, null);
    ray.normalizeDirection();

    assertEquals(7.0, point.x, 1e-9);
    assertEquals(10.0, point.y, 1e-9);
    assertEquals(1.0, ray.directionLength(), 1e-9);
  }

  @Test
  void transformAppliesScaleRotationAndTranslation() {
    Transform2 transform = new Transform2(10, 20, 90, 2, 3);

    Vec2 result = transform.transform(1, 0, null);

    assertEquals(10.0, result.x, 1e-9);
    assertEquals(22.0, result.y, 1e-9);
  }
}
