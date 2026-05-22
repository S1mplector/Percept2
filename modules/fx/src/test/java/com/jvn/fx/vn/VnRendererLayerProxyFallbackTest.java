package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VnRendererLayerProxyFallbackTest {

  @Test
  void fallsBackOnlyWhenOneLayerIsMissingFromABroadTimelineDrivenExpression() {
    assertTrue(VnRenderer.shouldFallbackMissingLayerProxy(8, 7));
    assertTrue(VnRenderer.shouldFallbackMissingLayerProxy(3, 2));

    assertFalse(VnRenderer.shouldFallbackMissingLayerProxy(8, 6));
    assertFalse(VnRenderer.shouldFallbackMissingLayerProxy(2, 1));
    assertFalse(VnRenderer.shouldFallbackMissingLayerProxy(8, 0));
  }
}
