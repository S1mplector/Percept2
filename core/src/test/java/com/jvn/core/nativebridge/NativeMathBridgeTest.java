package com.jvn.core.nativebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.jvn.core.math.Circle;
import com.jvn.core.math.Ray2;
import com.jvn.core.math.Rect;
import com.jvn.core.math.Segment2;
import com.jvn.core.math.Transform2;
import com.jvn.core.math.Vec2;

class NativeMathBridgeTest {

  @Nested
  @SuppressWarnings("unused")
  class NullHandlingTests {

    @Test
    void segmentSetNullStartAndEndDefaultsToZeros() {
      Segment2 segment = new Segment2();
      segment.set(null, null);

      assertEquals(0.0, segment.x1, 1e-9);
      assertEquals(0.0, segment.y1, 1e-9);
      assertEquals(0.0, segment.x2, 1e-9);
      assertEquals(0.0, segment.y2, 1e-9);
    }

    @Test
    void raySetNullOriginAndDirectionDefaultsToZeros() {
      Ray2 ray = new Ray2();
      ray.set(null, null);

      assertEquals(0.0, ray.x, 1e-9);
      assertEquals(0.0, ray.y, 1e-9);
      assertEquals(0.0, ray.dx, 1e-9);
      assertEquals(0.0, ray.dy, 1e-9);
    }

    @Test
    void transformApplyToNullReturnsNull() {
      Transform2 transform = new Transform2(2, 3, 45, 2, 2);
      assertNull(transform.applyTo(null));
    }

    @Test
    void pointSamplingAllocatesOutputWhenNullPassed() {
      Segment2 segment = new Segment2(0, 0, 10, 20);
      Ray2 ray = new Ray2(1, 2, 3, 4);

      Vec2 segmentPoint = segment.pointAt(0.5, null);
      Vec2 rayPoint = ray.pointAt(2.0, null);

      assertNotNull(segmentPoint);
      assertNotNull(rayPoint);
      assertEquals(5.0, segmentPoint.x, 1e-9);
      assertEquals(10.0, segmentPoint.y, 1e-9);
      assertEquals(7.0, rayPoint.x, 1e-9);
      assertEquals(10.0, rayPoint.y, 1e-9);
    }
  }

  @Nested
  @SuppressWarnings("unused")
  class DegenerateInputTests {

    @Test
    void zeroLengthSegmentHasZeroLengthAndCollapsedBounds() {
      Segment2 segment = new Segment2(5, 5, 5, 5);
      Rect bounds = segment.bounds(null);

      assertEquals(0.0, segment.length(), 1e-9);
      assertEquals(5.0, bounds.x, 1e-9);
      assertEquals(5.0, bounds.y, 1e-9);
      assertEquals(0.0, bounds.w, 1e-9);
      assertEquals(0.0, bounds.h, 1e-9);
    }

    @Test
    void zeroDirectionRayIsDegenerateAndSafeToNormalize() {
      Ray2 ray = new Ray2(4, 8, 0, 0);
      assertTrue(ray.isDegenerate());

      ray.normalizeDirection();

      assertEquals(0.0, ray.dx, 1e-9);
      assertEquals(0.0, ray.dy, 1e-9);
      assertEquals(0.0, ray.directionLength(), 1e-9);
    }

    @Test
    void zeroVectorNormalizeDoesNotChangeVector() {
      Vec2 vector = new Vec2(0, 0);
      vector.normalize();

      assertEquals(0.0, vector.x, 1e-9);
      assertEquals(0.0, vector.y, 1e-9);
    }

    @Test
    void zeroScaleTransformCollapsesPointToTranslation() {
      Transform2 transform = new Transform2(9, 11, 15, 0, 0);
      Vec2 result = transform.transform(200, -50, null);

      assertEquals(9.0, result.x, 1e-9);
      assertEquals(11.0, result.y, 1e-9);
    }
  }

  @Nested
  @SuppressWarnings("unused")
  class BoundaryAndExtrapolationTests {

    @Test
    void segmentPointAtSupportsBoundaryAndExtrapolatedT() {
      Segment2 segment = new Segment2(10, 10, 20, 30);

      Vec2 start = segment.pointAt(0.0, null);
      Vec2 mid = segment.pointAt(0.5, null);
      Vec2 end = segment.pointAt(1.0, null);
      Vec2 before = segment.pointAt(-1.0, null);
      Vec2 after = segment.pointAt(2.0, null);

      assertVecEquals(10, 10, start);
      assertVecEquals(15, 20, mid);
      assertVecEquals(20, 30, end);
      assertVecEquals(0, -10, before);
      assertVecEquals(30, 50, after);
    }

    @Test
    void rayPointAtSupportsNegativeAndPositiveDistance() {
      Ray2 ray = new Ray2(5, 6, 2, 3);

      Vec2 atZero = ray.pointAt(0.0, null);
      Vec2 backward = ray.pointAt(-2.0, null);
      Vec2 forward = ray.pointAt(3.5, null);

      assertVecEquals(5, 6, atZero);
      assertVecEquals(1, 0, backward);
      assertVecEquals(12, 16.5, forward);
    }
  }

  @Nested
  @SuppressWarnings("unused")
  class OutputReuseTests {

    @Test
    void segmentUsesProvidedOutputBuffer() {
      Segment2 segment = new Segment2(1, 2, 5, 6);
      Vec2 out = new Vec2();

      Vec2 result = segment.pointAt(0.5, out);

      assertSame(out, result);
      assertVecEquals(3, 4, out);
    }

    @Test
    void rayUsesProvidedOutputBuffer() {
      Ray2 ray = new Ray2(1, 2, 3, 4);
      Vec2 out = new Vec2();

      Vec2 result = ray.pointAt(2.0, out);

      assertSame(out, result);
      assertVecEquals(7, 10, out);
    }

    @Test
    void transformUsesProvidedOutputBuffer() {
      Transform2 transform = new Transform2(10, 20, 0, 2, 3);
      Vec2 out = new Vec2();

      Vec2 result = transform.transform(1, 1, out);

      assertSame(out, result);
      assertVecEquals(12, 23, out);
    }

    @Test
    void segmentBoundsUsesProvidedOutputBuffer() {
      Segment2 segment = new Segment2(2, 9, -3, 4);
      Rect out = new Rect();

      Rect result = segment.bounds(out);

      assertSame(out, result);
      assertEquals(-3.0, out.x, 1e-9);
      assertEquals(4.0, out.y, 1e-9);
      assertEquals(5.0, out.w, 1e-9);
      assertEquals(5.0, out.h, 1e-9);
    }
  }

  @Nested
  @SuppressWarnings("unused")
  class ExtremeAndSignTests {

    @Test
    void vectorOperationsHandleInfinityAndNaN() {
      Vec2 v = new Vec2(Double.POSITIVE_INFINITY, 1);
      v.add(new Vec2(1, Double.NaN));

      assertTrue(Double.isInfinite(v.x));
      assertTrue(Double.isNaN(v.y));
    }

    @Test
    void veryLargeRotationBehavesLikeEquivalentAngle() {
      Transform2 transform = new Transform2(0, 0, 3600, 1, 1);
      Vec2 rotated = transform.transform(1, 0, null);

      assertApproxEquals(1.0, rotated.x);
      assertApproxEquals(0.0, rotated.y);
    }

    @Test
    void negativeScaleFlipsDirection() {
      Transform2 transform = new Transform2(0, 0, 0, -2, 1);
      Vec2 result = transform.transform(3, 4, null);

      assertVecEquals(-6, 4, result);
    }

    @Test
    void negativeRadiusFollowsSquaredDistanceRule() {
      Circle circle = new Circle(5, 5, -2);

      assertTrue(circle.contains(5, 5));
      assertFalse(circle.contains(10, 10));
    }
  }

  @Nested
  @SuppressWarnings("unused")
  class PublicEntryPointSanityTests {

    @Test
    void vec2PublicMethodsRemainChainable() {
      Vec2 v = new Vec2(1, 2);

      assertSame(v, v.add(new Vec2(3, 4)));
      assertSame(v, v.sub(new Vec2(1, 1)));
      assertSame(v, v.scale(2.0));
      assertSame(v, v.normalize());
      assertTrue(Double.isFinite(v.length()));
    }

    @Test
    void rectIntersectsAndContainsCoverEdgeBoundaries() {
      Rect a = new Rect(0, 0, 10, 10);
      Rect b = new Rect(10, 10, 5, 5);

      assertTrue(a.contains(0, 0));
      assertTrue(a.contains(10, 10));
      assertTrue(a.intersects(b));
      assertTrue(b.intersects(a));
    }
  }

  private static void assertVecEquals(double expectedX, double expectedY, Vec2 value) {
    assertEquals(expectedX, value.x, 1e-9);
    assertEquals(expectedY, value.y, 1e-9);
  }

  private static void assertApproxEquals(double expected, double actual) {
    assertEquals(expected, actual, 1e-6);
  }
}
