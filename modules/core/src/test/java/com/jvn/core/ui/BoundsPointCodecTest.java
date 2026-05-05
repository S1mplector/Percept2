package com.jvn.core.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundsPointCodecTest {

  @Test
  void parseAndEncodeNormalizedPoints() {
    List<BoundsPointCodec.Point> points = BoundsPointCodec.parse("0,0;1,0;1,1;0,1");
    assertEquals(4, points.size());
    assertEquals(1.0, points.get(2).x(), 1e-6);
    assertEquals(1.0, points.get(2).y(), 1e-6);
    assertEquals("0,0;1,0;1,1;0,1", BoundsPointCodec.encode(points));
  }

  @Test
  void ignoresInvalidPointTokens() {
    List<BoundsPointCodec.Point> points = BoundsPointCodec.parse("0,0;bad;1,1,1;1,0;0,1");
    assertEquals(3, points.size());
    assertEquals("0,0;1,0;0,1", BoundsPointCodec.encode(points));
  }

  @Test
  void hitTestInRectUsesPolygonShape() {
    List<BoundsPointCodec.Point> triangle = BoundsPointCodec.parse("0,0;1,0;0,1");
    assertTrue(BoundsPointCodec.containsInRect(triangle, 100, 100, 200, 200, 130, 130));
    assertFalse(BoundsPointCodec.containsInRect(triangle, 100, 100, 200, 200, 290, 290));
  }
}
