package com.jvn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WebRendererUtilityTest {

  @Test
  void convertsClampedColorsToCanvasCss() {
    assertEquals("rgb(255,0,128)", WebRenderer.rgbToCss(1.2, -1.0, 0.5, 1.0));
    assertEquals("rgba(0,64,255,0.25)", WebRenderer.rgbToCss(0.0, 0.25, 1.0, 0.25));
    assertEquals("rgba(0,0,0,0.0)", WebRenderer.rgbToCss(Double.NaN, 0, 0, Double.NaN));
  }

  @Test
  void resolvesProjectAssetsRelativeToStaticDistribution() {
    assertEquals("assets/game/images/hero.png", WebImageCache.resolveAssetUrl("game/images/hero.png"));
    assertEquals("assets/game/images/hero.png", WebImageCache.resolveAssetUrl("/game/images/hero.png"));
    assertEquals("assets/game/images/hero.png", WebImageCache.resolveAssetUrl("assets/game/images/hero.png"));
    assertEquals("https://cdn.example/hero.png", WebImageCache.resolveAssetUrl("https://cdn.example/hero.png"));
  }

  @Test
  void rejectsBlankAssetPath() {
    assertThrows(IllegalArgumentException.class, () -> WebImageCache.resolveAssetUrl(" "));
  }
}
