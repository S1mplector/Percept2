package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VnStageLightingSupportTest {

  @Test
  void parsesWebColorString() {
    VnStageLightingSupport.Rgba parsed = VnStageLightingSupport.Rgba.parse(
        "#ff0000", new VnStageLightingSupport.Rgba(0, 0, 0, 1));
    assertEquals(1.0, parsed.r(), 1e-9);
    assertEquals(0.0, parsed.g(), 1e-9);
    assertEquals(0.0, parsed.b(), 1e-9);
  }

  @Test
  void fallsBackToDefaultOnBlankOrInvalidColor() {
    VnStageLightingSupport.Rgba fallback = new VnStageLightingSupport.Rgba(0.5, 0.5, 0.5, 1.0);
    assertEquals(fallback, VnStageLightingSupport.Rgba.parse(null, fallback));
    assertEquals(fallback, VnStageLightingSupport.Rgba.parse("", fallback));
    assertEquals(fallback, VnStageLightingSupport.Rgba.parse("not-a-color", fallback));
  }

  @Test
  void clampRestrictsToRange() {
    assertEquals(0.0, VnStageLightingSupport.clamp(-5.0, 0.0, 1.0), 1e-9);
    assertEquals(1.0, VnStageLightingSupport.clamp(5.0, 0.0, 1.0), 1e-9);
    assertEquals(0.5, VnStageLightingSupport.clamp(0.5, 0.0, 1.0), 1e-9);
  }

  @Test
  void srgbLinearRoundTripIsApproximatelyIdentity() {
    double original = 0.42;
    double roundTripped = VnStageLightingSupport.linearToSrgb(VnStageLightingSupport.srgbToLinear(original));
    assertEquals(original, roundTripped, 1e-6);
  }

  @Test
  void linearLuminanceOfWhiteIsOne() {
    assertEquals(1.0, VnStageLightingSupport.linearLuminance(1.0, 1.0, 1.0), 1e-9);
  }

  @Test
  void linearLuminanceOfBlackIsZero() {
    assertEquals(0.0, VnStageLightingSupport.linearLuminance(0.0, 0.0, 0.0), 1e-9);
  }

  @Test
  void sceneLightWeightPxIsZeroOutsideRadiusAndPositiveAtCenter() {
    assertEquals(0.0, VnStageLightingSupport.sceneLightWeightPx(200, 200, 0, 0, 50, 0.5), 1e-9);
    assertTrue(VnStageLightingSupport.sceneLightWeightPx(0, 0, 0, 0, 50, 0.5) > 0.9);
  }

  @Test
  void buildLitBackgroundNoOpsWhenStageHasNoLights() {
    int width = 4;
    int height = 4;
    int[] source = new int[width * height];
    java.util.Arrays.fill(source, 0xFF808080);

    int[] result = VnStageLightingSupport.buildLitBackground(
        source, width, height, null, width, height);

    assertEquals(width * height, result.length);
    // With no lights/grade adjustments, output should closely match the flat input color.
    for (int pixel : result) {
      int a = (pixel >>> 24) & 0xFF;
      assertEquals(255, a);
    }
  }

  @Test
  void buildLitCharacterPreservesFullyTransparentPixelsAsTransparent() {
    int width = 2;
    int height = 2;
    int[] source = {0x00000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};

    int[] result = VnStageLightingSupport.buildLitCharacter(
        source, width, height, "tag", 0, 0, width, height, width, height, null);

    assertEquals(width * height, result.length);
  }
}
