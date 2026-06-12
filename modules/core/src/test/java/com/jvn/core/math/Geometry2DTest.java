package com.jvn.core.math;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.physics.Collision2D;
import org.junit.jupiter.api.Test;

class Geometry2DTest {
  private static final double EPS = 1e-9;

  @Test
  void aabbNormalizesEdgesAndConvertsToRect() {
    Aabb2 box = new Aabb2(10, 20, -5, 2);
    assertEquals(-5.0, box.minX, EPS);
    assertEquals(2.0, box.minY, EPS);
    assertEquals(10.0, box.maxX, EPS);
    assertEquals(20.0, box.maxY, EPS);
    assertTrue(box.contains(0, 10));
    assertFalse(box.contains(12, 10));

    Rect rect = box.toRect(null);
    assertEquals(-5.0, rect.x, EPS);
    assertEquals(2.0, rect.y, EPS);
    assertEquals(15.0, rect.w, EPS);
    assertEquals(18.0, rect.h, EPS);
  }

  @Test
  void lineProjectsAndMeasuresDistance() {
    Line2 line = Line2.through(0, 0, 10, 0);
    assertEquals(5.0, line.distance(4, 5), EPS);
    Vec2 projected = line.project(4, 5, null);
    assertEquals(4.0, projected.x, EPS);
    assertEquals(0.0, projected.y, EPS);
  }

  @Test
  void polygonContainsInteriorAndBoundary() {
    Polygon2 triangle = Polygon2.of(0, 0, 10, 0, 5, 10);
    assertTrue(triangle.contains(5, 5));
    assertTrue(triangle.contains(5, 0));
    assertFalse(triangle.contains(9, 9));

    Rect bounds = triangle.bounds(null);
    assertEquals(0.0, bounds.x, EPS);
    assertEquals(0.0, bounds.y, EPS);
    assertEquals(10.0, bounds.w, EPS);
    assertEquals(10.0, bounds.h, EPS);
  }

  @Test
  void capsuleContainsPointsNearSegmentAndReportsBounds() {
    Capsule2 capsule = new Capsule2(0, 0, 10, 0, 2);
    assertTrue(capsule.contains(5, 1.5));
    assertTrue(capsule.contains(0, -2));
    assertFalse(capsule.contains(5, 3));

    Rect bounds = capsule.bounds(null);
    assertEquals(-2.0, bounds.x, EPS);
    assertEquals(-2.0, bounds.y, EPS);
    assertEquals(14.0, bounds.w, EPS);
    assertEquals(4.0, bounds.h, EPS);
  }

  @Test
  void geometryIntersectionsCoverCorePairs() {
    assertTrue(Geometry2D.intersects(new Circle(0, 0, 3), new Circle(5, 0, 2)));
    assertFalse(Geometry2D.intersects(new Circle(0, 0, 3), new Circle(6.1, 0, 2)));
    assertTrue(Geometry2D.intersects(new Circle(5, 5, 2), new Rect(0, 0, 4, 4)));
    assertTrue(Geometry2D.intersects(new Capsule2(0, 0, 10, 0, 2), new Circle(5, 3, 1)));
    assertFalse(Geometry2D.intersects(new Capsule2(0, 0, 10, 0, 1), new Circle(5, 3, 1)));
  }

  @Test
  void raycastsMatchExistingCollisionFacade() {
    Rect box = new Rect(10, 10, 10, 10);
    assertArrayEquals(new double[] { 10.0, 15.0, 0.5 },
        Geometry2D.raycastSegmentAABB(0, 15, 20, 15, box), EPS);
    assertArrayEquals(Collision2D.raycastRayAABB(0, 15, 1, 0, box),
        Geometry2D.raycastRayAABB(0, 15, 1, 0, box), EPS);
    assertNull(Geometry2D.raycastSegmentAABB(0, 0, 5, 5, box));
  }

  @Test
  void polygonDefensivelyCopiesInputAndOutput() {
    double[] points = { 0, 0, 4, 0, 4, 4, 0, 4 };
    Polygon2 square = new Polygon2(points);
    points[0] = 99;
    assertTrue(square.contains(1, 1));

    double[] copy = square.toArray();
    copy[0] = 99;
    assertTrue(square.contains(1, 1));
  }
}
