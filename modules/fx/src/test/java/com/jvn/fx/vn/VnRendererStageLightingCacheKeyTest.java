package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class VnRendererStageLightingCacheKeyTest {

  @Test
  void subPixelIdleJitterReusesTheSameCacheKey() {
    String base = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    // Idle-bob/breathing offsets of a couple of pixels should stay within the position
    // quantization grid and therefore hit the same cache entry.
    String jittered = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 101.5, 198.7, 300.0, 400.0, 1280.0, 720.0);
    assertEquals(base, jittered);
  }

  @Test
  void rapidExpressionSwapProducesFarFewerKeysThanFrames() {
    // Simulate a rapid idle-bob loop at a fixed expression: many frames, tiny position jitter.
    Set<String> keys = new HashSet<>();
    int frames = 200;
    for (int i = 0; i < frames; i++) {
      double jitterX = Math.sin(i * 0.3) * 1.5; // sub-grid amplitude
      double jitterY = Math.cos(i * 0.3) * 1.5;
      keys.add(VnRenderer.stageCharacterCacheKey(
          "alice_happy", "stage-1", 100.0 + jitterX, 200.0 + jitterY, 300.0, 400.0, 1280.0, 720.0));
    }
    assertEquals(1, keys.size(), "idle jitter within the quantization grid should collapse to one cache key");
  }

  @Test
  void meaningfulPositionChangeInvalidatesTheCache() {
    String base = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String moved = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 140.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    assertNotEquals(base, moved);
  }

  @Test
  void expressionOrLayerChangeInvalidatesTheCache() {
    String happy = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String sad = VnRenderer.stageCharacterCacheKey("alice_sad", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    assertNotEquals(happy, sad);
  }

  @Test
  void stageChangeInvalidatesTheCache() {
    String stage1 = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String stage2 = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-2", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    assertNotEquals(stage1, stage2);
  }

  @Test
  void sizeOrCanvasChangeInvalidatesTheCache() {
    String base = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String resized = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 100.0, 200.0, 320.0, 400.0, 1280.0, 720.0);
    String resizedCanvas = VnRenderer.stageCharacterCacheKey("alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1920.0, 1080.0);
    assertNotEquals(base, resized);
    assertNotEquals(base, resizedCanvas);
  }
}
