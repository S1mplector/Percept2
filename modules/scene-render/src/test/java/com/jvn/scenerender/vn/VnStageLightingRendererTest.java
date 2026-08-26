package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.RenderTarget2D;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VnStageLightingRendererTest {

  private static VnStagePreset stageWithLight(String id) {
    VnStagePreset.Light light = new VnStagePreset.Light(
        "key",
        VnStagePreset.LightType.RADIAL,
        VnStagePreset.LightLayer.CHARACTER,
        0.5, 0.5,
        0.38, 0.2,
        "#ffd7a8",
        0.8,
        0.6,
        0.5,
        0.4,
        false, false, false,
        "",
        null);
    return new VnStagePreset(
        id, "", "", "", VnStagePreset.BackgroundGrade.defaults(),
        List.of(light), List.of(), List.of());
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Lit character / lit composite drawing
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  void buildsALitCharacterCompositeOverAResolvableAsset() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnStageLightingRenderer renderer = new VnStageLightingRenderer(blitter);

    renderer.drawLitCharacter(
        "probe/tier1.png", "alice_happy", 0, 0, 40, 30, 200, 100, stageWithLight("stage-1"));

    assertTrue(
        blitter.calls().stream().anyMatch(c -> c.method().equals("drawRenderTarget")),
        "expected the lit result to be composited via drawRenderTarget, got: " + blitter.calls());
  }

  @Test
  void litCharacterReusesTheCachedTargetOnASecondIdenticalDraw() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnStageLightingRenderer renderer = new VnStageLightingRenderer(blitter);
    VnStagePreset stage = stageWithLight("stage-1");

    renderer.drawLitCharacter("probe/tier1.png", "alice_happy", 0, 0, 40, 30, 200, 100, stage);
    long createsAfterFirst =
        blitter.calls().stream().filter(c -> c.method().equals("createRenderTarget")).count();
    renderer.drawLitCharacter("probe/tier1.png", "alice_happy", 0, 0, 40, 30, 200, 100, stage);
    long createsAfterSecond =
        blitter.calls().stream().filter(c -> c.method().equals("createRenderTarget")).count();

    assertEquals(createsAfterFirst, createsAfterSecond,
        "a cache hit must not build a new render target");
    assertEquals(2,
        blitter.calls().stream().filter(c -> c.method().equals("drawRenderTarget")).count(),
        "both draws must still reach the canvas");
  }

  @Test
  void litCompositeSourcesPixelsFromTheGivenCompositeAndDoesNotDisposeIt() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnStageLightingRenderer renderer = new VnStageLightingRenderer(blitter);

    RenderTarget2D composite = blitter.createRenderTarget(4, 4, 1.0);
    int[] pixels = new int[16];
    java.util.Arrays.fill(pixels, 0xFF204080);
    composite.writePixelsArgb(pixels);
    blitter.clearCalls();

    renderer.drawLitComposite(
        composite, "alice:composite", 5, 6, 40, 40, 200, 100, stageWithLight("stage-1"));

    assertTrue(
        blitter.calls().stream().anyMatch(c -> c.method().equals("drawRenderTarget")),
        "expected the lit composite to be drawn, got: " + blitter.calls());
    assertTrue(composite.isValid(), "the caller-owned composite must not be disposed");
    assertNotNull(composite.readPixelsArgb(), "the composite must still be readable");
  }

  @Test
  void litCompositeDoesNotResolveTheSpriteByPath() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnStageLightingRenderer renderer = new VnStageLightingRenderer(blitter);

    RenderTarget2D composite = blitter.createRenderTarget(4, 4, 1.0);
    blitter.clearCalls();

    renderer.drawLitComposite(
        composite, "alice:composite", 0, 0, 40, 30, 200, 100, stageWithLight("stage-1"));

    assertTrue(
        blitter.calls().stream().noneMatch(c -> c.method().equals("drawImage")),
        "composite pixels come from the passed target, never from a path redraw: " + blitter.calls());
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Compositor wiring (correction 3: composited sprites must be stage-lit)
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * A real multi-layer composite built by {@link VnCharacterCompositor} must survive the round
   * trip through {@code drawLitComposite}: this is the exact handoff
   * {@code VnCharacterCompositor.drawSpriteSource}'s composite branch performs once a stage
   * lighting renderer is wired and the stage has active lights.
   */
  @Test
  void litsARealCompositeBuiltByTheCompositor() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnCharacterCompositor compositor = new VnCharacterCompositor(blitter);
    VnStageLightingRenderer renderer = new VnStageLightingRenderer(blitter);
    compositor.setStageLightingRenderer(renderer);

    RenderTarget2D composite = compositor.compositeSpriteFor(
        "probe/tier1.png|raw-assets/tier2.png",
        List.of("probe/tier1.png", "raw-assets/tier2.png"));
    assertNotNull(composite, "expected the two real fixture layers to composite");
    blitter.clearCalls();

    renderer.drawLitComposite(
        composite, "probe/tier1.png|raw-assets/tier2.png",
        10, 20, 100, 200, 1280, 720, stageWithLight("stage-1"));

    assertTrue(
        blitter.calls().stream().anyMatch(c -> c.method().equals("drawRenderTarget")),
        "expected the lit composite to be drawn, got: " + blitter.calls());
    assertTrue(composite.isValid(), "the compositor still owns and caches the composite");
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Stage-character cache keys, ported from modules/fx
  //  VnRendererStageLightingCacheKeyTest (the key function itself moved to
  //  VnCharacterCompositor in Task 10; these assert it unchanged).
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  void subPixelIdleJitterReusesTheSameCacheKey() {
    String base = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String jittered = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 101.5, 198.7, 300.0, 400.0, 1280.0, 720.0);
    assertEquals(base, jittered);
  }

  @Test
  void rapidExpressionSwapProducesFarFewerKeysThanFrames() {
    Set<String> keys = new HashSet<>();
    int frames = 200;
    for (int i = 0; i < frames; i++) {
      double jitterX = Math.sin(i * 0.3) * 1.5;
      double jitterY = Math.cos(i * 0.3) * 1.5;
      keys.add(VnCharacterCompositor.stageCharacterCacheKey(
          "alice_happy", "stage-1", 100.0 + jitterX, 200.0 + jitterY, 300.0, 400.0, 1280.0, 720.0));
    }
    assertEquals(1, keys.size(),
        "idle jitter within the quantization grid should collapse to one cache key");
  }

  @Test
  void meaningfulPositionChangeInvalidatesTheCache() {
    String base = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String moved = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 140.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    assertNotEquals(base, moved);
  }

  @Test
  void expressionOrLayerChangeInvalidatesTheCache() {
    String happy = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String sad = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_sad", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    assertNotEquals(happy, sad);
  }

  @Test
  void stageChangeInvalidatesTheCache() {
    String stage1 = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String stage2 = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-2", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    assertNotEquals(stage1, stage2);
  }

  @Test
  void sizeOrCanvasChangeInvalidatesTheCache() {
    String base = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String resized = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 100.0, 200.0, 320.0, 400.0, 1280.0, 720.0);
    String resizedCanvas = VnCharacterCompositor.stageCharacterCacheKey(
        "alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1920.0, 1080.0);
    assertNotEquals(base, resized);
    assertNotEquals(base, resizedCanvas);
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Stage-background cache key
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  void stageBackgroundCacheKeyVariesByPathStageAndSize() {
    String base = VnStageLightingRenderer.stageBackgroundCacheKey("bg/room.png", "day", 1280, 720);

    assertEquals(base,
        VnStageLightingRenderer.stageBackgroundCacheKey("bg/room.png", "day", 1280, 720),
        "identical inputs must produce identical keys");
    assertNotEquals(base,
        VnStageLightingRenderer.stageBackgroundCacheKey("bg/hall.png", "day", 1280, 720),
        "asset path must affect the key");
    assertNotEquals(base,
        VnStageLightingRenderer.stageBackgroundCacheKey("bg/room.png", "night", 1280, 720),
        "stage identity must affect the key");
    assertNotEquals(base,
        VnStageLightingRenderer.stageBackgroundCacheKey("bg/room.png", "day", 1920, 1080),
        "canvas size must affect the key");
    assertNotEquals(base,
        VnStageLightingRenderer.stageBackgroundCacheKey("bg/room.png", "day", 720, 1280),
        "canvas width and height must not be interchangeable");
  }
}
