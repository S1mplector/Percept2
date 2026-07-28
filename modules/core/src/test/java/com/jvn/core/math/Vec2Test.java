package com.jvn.core.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the {@link Vec2} operations added alongside the engine-core pass.
 * Uses a tight epsilon so transforms like rotate/reflect stay numerically sound.
 */
public class Vec2Test {

  private static final double EPS = 1e-10;

  private static void assertVec(Vec2 v, double x, double y) {
    assertEquals(x, v.x, EPS, "x");
    assertEquals(y, v.y, EPS, "y");
  }

  @Test
  public void lengthSqAvoidsSqrt() {
    assertEquals(25.0, new Vec2(3, 4).lengthSq(), EPS);
    assertEquals(0.0, new Vec2().lengthSq(), EPS);
  }

  @Test
  public void zeroAndSetFromOther() {
    Vec2 a = new Vec2(3, 4);
    assertSame(a, a.zero());
    assertVec(a, 0, 0);
    Vec2 b = new Vec2(7, -2);
    assertSame(a, a.set(b));
    assertVec(a, 7, -2);
  }

  @Test
  public void negateFlipsBothAxes() {
    Vec2 v = new Vec2(2, -3).negate();
    assertVec(v, -2, 3);
  }

  @Test
  public void addScaledFMA() {
    Vec2 pos = new Vec2(10, 10);
    Vec2 vel = new Vec2(1, -2);
    pos.addScaled(vel, 5);
    assertVec(pos, 15, 0);
  }

  @Test
  public void perpIsNinetyDegCcw() {
    assertVec(new Vec2(1, 0).perp(), 0, 1);
    assertVec(new Vec2(0, 1).perp(), -1, 0);
    assertVec(new Vec2(3, 4).perp(), -4, 3);
  }

  @Test
  public void crossProductSignCcwVsCw() {
    Vec2 right = new Vec2(1, 0);
    Vec2 up = new Vec2(0, 1);
    // up is CCW from right → positive.
    assertTrue(Vec2.cross(right, up) > 0);
    assertTrue(Vec2.cross(up, right) < 0);
  }

  @Test
  public void distanceAndDistanceSqAgree() {
    Vec2 a = new Vec2(1, 2);
    Vec2 b = new Vec2(4, 6);
    assertEquals(25.0, Vec2.distanceSq(a, b), EPS);
    assertEquals(5.0, Vec2.distance(a, b), EPS);
  }

  @Test
  public void lerpStaticAllocatesNewVector() {
    Vec2 a = new Vec2(0, 0);
    Vec2 b = new Vec2(10, 20);
    Vec2 m = Vec2.lerp(a, b, 0.25);
    assertVec(m, 2.5, 5.0);
    assertNotSame(m, a);
    assertNotSame(m, b);
    // Source vectors untouched.
    assertVec(a, 0, 0);
    assertVec(b, 10, 20);
  }

  @Test
  public void lerpToMutatesInPlace() {
    Vec2 pos = new Vec2(0, 0);
    Vec2 target = new Vec2(10, 10);
    assertSame(pos, pos.lerpTo(target, 0.5));
    assertVec(pos, 5, 5);
    pos.lerpTo(target, 1.0);
    assertVec(pos, 10, 10);
  }

  @Test
  public void angleMatchesAtan2() {
    assertEquals(0.0, new Vec2(1, 0).angle(), EPS);
    assertEquals(Math.PI / 2, new Vec2(0, 1).angle(), EPS);
    assertEquals(Math.PI, new Vec2(-1, 0).angle(), EPS);
    assertEquals(-Math.PI / 2, new Vec2(0, -1).angle(), EPS);
  }

  @Test
  public void rotateBy90Eq_perp() {
    Vec2 v = new Vec2(3, 4).rotate(Math.PI / 2);
    assertVec(v, -4, 3);
  }

  @Test
  public void reflectOverXAxisFlipsY() {
    // Normal pointing +Y (xy plane's X axis as the mirror line).
    Vec2 normal = new Vec2(0, 1);
    Vec2 v = new Vec2(3, 4).reflect(normal);
    assertVec(v, 3, -4);
  }

  @Test
  public void clampLengthNoopsWhenUnderMax() {
    Vec2 v = new Vec2(3, 4);
    v.clampLength(10);
    assertVec(v, 3, 4);
  }

  @Test
  public void clampLengthRescalesWhenOverMax() {
    Vec2 v = new Vec2(3, 4); // length 5
    v.clampLength(2.5);
    assertEquals(2.5, v.length(), EPS);
    // Direction preserved: ratio y/x stays 4/3.
    assertEquals(4.0 / 3.0, v.y / v.x, EPS);
  }

  @Test
  public void staticAddAndSubReturnNewVectors() {
    Vec2 a = new Vec2(1, 2);
    Vec2 b = new Vec2(3, 5);
    assertVec(Vec2.add(a, b), 4, 7);
    assertVec(Vec2.sub(a, b), -2, -3);
    // Originals unchanged.
    assertVec(a, 1, 2);
    assertVec(b, 3, 5);
  }

  @Test
  public void outputOverloadsReuseCallerOwnedStorage() {
    Vec2 out = new Vec2();
    assertSame(out, Vec2.add(new Vec2(1, 2), new Vec2(3, 4), out));
    assertVec(out, 4, 6);
    assertSame(out, Vec2.lerp(new Vec2(0, 0), new Vec2(10, 20), 0.25, out));
    assertVec(out, 2.5, 5);
  }

  @Test
  public void nonFiniteVectorsSanitizeBeforeNormalization() {
    Vec2 v = new Vec2(Double.NaN, Double.POSITIVE_INFINITY);
    v.normalize();
    assertVec(v, 0, 0);
    assertTrue(v.isFinite());
  }

  @Test
  public void toStringIncludesComponents() {
    String s = new Vec2(1.5, -2.25).toString();
    assertTrue(s.contains("1.5"));
    assertTrue(s.contains("-2.25"));
  }
}
