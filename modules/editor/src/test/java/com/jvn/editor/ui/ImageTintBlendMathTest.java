package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class ImageTintBlendMathTest {

  private static final String[] MODES = {
      "Normal",
      "Multiply",
      "Screen",
      "Overlay",
      "Soft Light",
      "Hard Light",
      "Color Dodge",
      "Color Burn",
      "Difference",
      "Exclusion",
      "Lighten",
      "Darken",
      "Add",
      "Subtract"
  };

  private static final Method APPLY_BLEND = lookup("applyBlend", double.class, double.class, double.class, int.class);
  private static final Method BLEND_MODE_INDEX = lookup("blendModeIndex", String.class);
  private static final double EPS = 1e-12;

  @Test
  void blendModeIndexMapsAllModesAndFallsBackForUnknown() {
    for (int i = 0; i < MODES.length; i++) {
      assertEquals(i, blendModeIndex(MODES[i]), "Mode index mismatch for " + MODES[i]);
    }
    assertEquals(4, blendModeIndex("soft light"));
    assertEquals(13, blendModeIndex("subtract"));
    assertEquals(0, blendModeIndex("not-a-mode"));
  }

  @Test
  void applyBlendWeightZeroAlwaysReturnsBase() {
    double[] bases = {0.0, 0.2, 0.5, 0.9, 1.0};
    double[] zones = {0.0, 0.1, 0.5, 0.8, 1.0};
    for (int mode = 0; mode < MODES.length; mode++) {
      for (double base : bases) {
        for (double zone : zones) {
          assertEquals(base, applyBlend(base, zone, 0.0, mode), EPS,
              "weight=0 should preserve base for mode " + MODES[mode]);
        }
      }
    }
  }

  @Test
  void applyBlendWeightOneMatchesReferenceForAllModes() {
    double[] samples = {0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0};
    for (int mode = 0; mode < MODES.length; mode++) {
      for (double base : samples) {
        for (double zone : samples) {
          double expected = referenceBlend(base, zone, 1.0, mode);
          double actual = applyBlend(base, zone, 1.0, mode);
          assertEquals(expected, actual, EPS,
              "reference mismatch for mode " + MODES[mode] + ", base=" + base + ", zone=" + zone);
        }
      }
    }
  }

  @Test
  void applyBlendInterpolatesWithWeightAcrossModes() {
    double base = 0.37;
    double zone = 0.81;
    double weight = 0.29;
    for (int mode = 0; mode < MODES.length; mode++) {
      double expected = referenceBlend(base, zone, weight, mode);
      double actual = applyBlend(base, zone, weight, mode);
      assertEquals(expected, actual, EPS, "interpolation mismatch for mode " + MODES[mode]);
    }
  }

  @Test
  void differenceAndExclusionAreSymmetric() {
    double a = 0.19;
    double b = 0.82;
    int difference = blendModeIndex("Difference");
    int exclusion = blendModeIndex("Exclusion");
    assertEquals(applyBlend(a, b, 1.0, difference), applyBlend(b, a, 1.0, difference), EPS);
    assertEquals(applyBlend(a, b, 1.0, exclusion), applyBlend(b, a, 1.0, exclusion), EPS);
  }

  @Test
  void overlayAndHardLightAreDualOperations() {
    double base = 0.28;
    double zone = 0.73;
    int overlay = blendModeIndex("Overlay");
    int hardLight = blendModeIndex("Hard Light");
    assertEquals(
        applyBlend(base, zone, 1.0, overlay),
        applyBlend(zone, base, 1.0, hardLight),
        EPS);
  }

  @Test
  void softLightNeutralSourcePreservesBase() {
    int softLight = blendModeIndex("Soft Light");
    double neutralSource = linearToSrgb(0.5);
    for (double base : new double[]{0.0, 0.15, 0.5, 0.78, 1.0}) {
      assertEquals(base, applyBlend(base, neutralSource, 1.0, softLight), EPS);
    }
  }

  @Test
  void dodgeAndBurnHandleSingularEndpoints() {
    int dodge = blendModeIndex("Color Dodge");
    int burn = blendModeIndex("Color Burn");
    for (double base : new double[]{0.0, 0.15, 0.5, 0.85, 1.0}) {
      assertEquals(1.0, applyBlend(base, 1.0, 1.0, dodge), EPS);
      assertEquals(0.0, applyBlend(base, 0.0, 1.0, burn), EPS);
    }
  }

  @Test
  void lightenAndDarkenMatchChannelExtrema() {
    double a = 0.32;
    double b = 0.79;
    int lighten = blendModeIndex("Lighten");
    int darken = blendModeIndex("Darken");
    assertEquals(Math.max(a, b), applyBlend(a, b, 1.0, lighten), EPS);
    assertEquals(Math.min(a, b), applyBlend(a, b, 1.0, darken), EPS);
  }

  @Test
  void addAndSubtractClampWithinRange() {
    int add = blendModeIndex("Add");
    int subtract = blendModeIndex("Subtract");

    double addResult = applyBlend(0.92, 0.85, 1.0, add);
    assertEquals(1.0, addResult, EPS);

    double subtractFloor = applyBlend(0.12, 0.84, 1.0, subtract);
    assertEquals(0.0, subtractFloor, EPS);

    double subtractMid = applyBlend(0.84, 0.12, 1.0, subtract);
    assertTrue(subtractMid >= 0.0 && subtractMid <= 1.0, "Subtract should stay in [0,1]");
  }

  private static Method lookup(String name, Class<?>... parameterTypes) {
    try {
      Method m = ImageTintToolView.class.getDeclaredMethod(name, parameterTypes);
      m.setAccessible(true);
      return m;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static double applyBlend(double base, double zone, double weight, int mode) {
    try {
      return (double) APPLY_BLEND.invoke(null, base, zone, weight, mode);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static int blendModeIndex(String mode) {
    try {
      return (int) BLEND_MODE_INDEX.invoke(null, mode);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static double referenceBlend(double base, double zone, double weight, int mode) {
    double b = clamp(base, 0.0, 1.0);
    double s = clamp(zone, 0.0, 1.0);
    double w = clamp(weight, 0.0, 1.0);
    if (w <= 0.0) return b;
    if (mode == 0) {
      return b * (1.0 - w) + s * w;
    }
    double bLin = srgbToLinear(b);
    double sLin = srgbToLinear(s);
    double blended = switch (mode) {
      case 1 -> bLin * sLin;
      case 2 -> 1.0 - (1.0 - bLin) * (1.0 - sLin);
      case 3 -> bLin < 0.5 ? 2.0 * bLin * sLin : 1.0 - 2.0 * (1.0 - bLin) * (1.0 - sLin);
      case 4 -> sLin <= 0.5
          ? bLin - (1.0 - 2.0 * sLin) * bLin * (1.0 - bLin)
          : bLin + (2.0 * sLin - 1.0) * (softLightCurve(bLin) - bLin);
      case 5 -> sLin < 0.5 ? 2.0 * bLin * sLin : 1.0 - 2.0 * (1.0 - bLin) * (1.0 - sLin);
      case 6 -> sLin >= (1.0 - 1e-6) ? 1.0 : clamp(bLin / (1.0 - sLin), 0.0, 1.0);
      case 7 -> sLin <= 1e-6 ? 0.0 : 1.0 - clamp((1.0 - bLin) / sLin, 0.0, 1.0);
      case 8 -> Math.abs(bLin - sLin);
      case 9 -> bLin + sLin - (2.0 * bLin * sLin);
      case 10 -> Math.max(bLin, sLin);
      case 11 -> Math.min(bLin, sLin);
      case 12 -> Math.min(1.0, bLin + sLin);
      case 13 -> Math.max(0.0, bLin - sLin);
      default -> sLin;
    };
    double blendedSrgb = linearToSrgb(clamp(blended, 0.0, 1.0));
    return b * (1.0 - w) + blendedSrgb * w;
  }

  private static double softLightCurve(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.25) {
      return ((16.0 * v - 12.0) * v + 4.0) * v;
    }
    return Math.sqrt(v);
  }

  private static double srgbToLinear(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.04045) return v / 12.92;
    return Math.pow((v + 0.055) / 1.055, 2.4);
  }

  private static double linearToSrgb(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.0031308) return v * 12.92;
    return 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
