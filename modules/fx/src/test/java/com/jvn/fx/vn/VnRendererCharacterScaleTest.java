package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
