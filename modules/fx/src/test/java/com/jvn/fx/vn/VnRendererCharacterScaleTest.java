package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.vn.VnCharacter;

class VnRendererCharacterScaleTest {

  @Test
  void resolvesAuthoredCharacterScaleAndDefault() {
    assertEquals(1.0, VnRenderer.characterScale(null), 1e-9);
    assertEquals(1.0, VnRenderer.characterScale(
        VnCharacter.builder("regular").build()), 1e-9);
    assertEquals(1.25, VnRenderer.characterScale(
        VnCharacter.builder("large").scale(1.25).build()), 1e-9);
  }

  @Test
  void canvasAlignedSpriteSheetsIgnorePortraitFraming() {
    VnRenderer.SpriteLayout layout = VnRenderer.resolveSpriteLayout(
        1920.0, 1080.0, 1920.0, 1080.0, 1.435, 1.325, 1.0);

    assertEquals(1920.0, layout.width(), 1e-9);
    assertEquals(1080.0, layout.height(), 1e-9);
    assertEquals(1.0, layout.baselineY(), 1e-9);
    assertTrue(layout.canvasAligned());
  }

  @Test
  void portraitSpritesRetainConfiguredFraming() {
    VnRenderer.SpriteLayout layout = VnRenderer.resolveSpriteLayout(
        1240.0, 1550.0, 1920.0, 1080.0, 1.435, 1.325, 1.0);

    assertEquals(1240.0 * (1549.8 / 1550.0), layout.width(), 1e-9);
    assertEquals(1549.8, layout.height(), 1e-9);
    assertEquals(1.325, layout.baselineY(), 1e-9);
    assertFalse(layout.canvasAligned());
  }
}
