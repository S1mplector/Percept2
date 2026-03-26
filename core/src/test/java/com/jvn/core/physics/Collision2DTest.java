package com.jvn.core.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.math.Circle;
import com.jvn.core.math.Ray2;
import com.jvn.core.math.Rect;
import com.jvn.core.math.Segment2;

class Collision2DTest {

  @Test
  void supportsCircleRectIntersection() {
    Circle circle = new Circle(5, 5, 2);
    Rect rect = new Rect(6, 4, 10, 3);

    assertTrue(Collision2D.intersects(circle, rect));
    assertTrue(Collision2D.intersects(rect, circle));
  }

  @Test
  void raycastsTypedSegmentsAgainstAabbs() {
    Segment2 segment = new Segment2(-5, 5, 5, 5);
    Rect rect = new Rect(0, 0, 10, 10);

    double[] hit = Collision2D.raycastSegmentAABB(segment, rect);

    assertNotNull(hit);
    assertEquals(0.0, hit[0], 1e-9);
    assertEquals(5.0, hit[1], 1e-9);
    assertEquals(0.5, hit[2], 1e-9);
  }

  @Test
  void raycastsTypedRaysAgainstAabbs() {
    Ray2 ray = new Ray2(-5, 5, 1, 0);
    Rect rect = new Rect(0, 0, 10, 10);

    double[] hit = Collision2D.raycastRayAABB(ray, rect);

    assertNotNull(hit);
    assertEquals(0.0, hit[0], 1e-9);
    assertEquals(5.0, hit[1], 1e-9);
    assertEquals(5.0, hit[2], 1e-9);
  }

  @Test
  void rayMissReturnsNull() {
    Ray2 ray = new Ray2(-5, -5, -1, 0);
    Rect rect = new Rect(0, 0, 10, 10);

    assertNull(Collision2D.raycastRayAABB(ray, rect));
  }
}
