package com.jvn.core.scene2d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RendererCapabilitiesTest {

  @Test
  void reportsSupportedFeaturesAndRejectsMissingRequirements() {
    RendererCapabilities capabilities = RendererCapabilities.of(
        "test", RenderFeature.RECTANGULAR_CLIP)
        .withBlendModes(RenderBlendMode.NORMAL, RenderBlendMode.MULTIPLY);

    assertTrue(capabilities.supports(RenderFeature.BLEND_MODES));
    assertTrue(capabilities.supportsAll(RenderFeature.BLEND_MODES, RenderFeature.RECTANGULAR_CLIP));
    assertFalse(capabilities.supports(RenderFeature.BLUR));
    assertTrue(capabilities.supportsBlendMode(RenderBlendMode.MULTIPLY));
    assertThrows(UnsupportedOperationException.class,
        () -> capabilities.requireBlendMode(RenderBlendMode.SCREEN));
    assertThrows(UnsupportedOperationException.class, () -> capabilities.require(RenderFeature.BLUR));
  }

  @Test
  void baselineContainsNoOptionalFeatures() {
    RendererCapabilities capabilities = RendererCapabilities.baseline("minimal");

    assertTrue(capabilities.supportedFeatures().isEmpty());
    assertThrows(UnsupportedOperationException.class,
        () -> capabilities.require(RenderFeature.AFFINE_TRANSFORM));
  }
}
