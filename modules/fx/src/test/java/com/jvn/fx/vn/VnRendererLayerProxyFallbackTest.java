package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VnRendererLayerProxyFallbackTest {

  @Test
  void layerGroupTargetsExposeExpressionSpecificAndStableAliases() {
    assertEquals(
        java.util.List.of("john_head", "john_neutral_head"),
        VnRenderer.timelineGroupTargetNames("john", "neutral", "head"));
  }

  @Test
  void parsesLayeredSpritePathsWithoutRegexSplitting() {
    assertEquals(java.util.List.of("body.png"), VnRenderer.parseLayerPaths(" body.png "));
    assertEquals(
        java.util.List.of("body.png", "eyes.png", "mouth.png"),
        VnRenderer.parseLayerPaths(" body.png | eyes.png || mouth.png "));
    assertEquals(java.util.List.of(), VnRenderer.parseLayerPaths("   "));
  }
}
