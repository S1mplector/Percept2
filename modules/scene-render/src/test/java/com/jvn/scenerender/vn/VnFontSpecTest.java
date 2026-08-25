package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VnFontSpecTest {

  @Test
  void holdsFamilySizeAndBoldness() {
    VnFontSpec spec = new VnFontSpec("SansSerif", 22.0, true);
    assertEquals("SansSerif", spec.family());
    assertEquals(22.0, spec.size());
    assertEquals(true, spec.bold());
  }

  @Test
  void withSizeReturnsACopyWithOnlySizeChanged() {
    VnFontSpec spec = new VnFontSpec("SansSerif", 22.0, false);
    VnFontSpec scaled = spec.withSize(33.0);
    assertEquals("SansSerif", scaled.family());
    assertEquals(33.0, scaled.size());
    assertEquals(false, scaled.bold());
  }
}
