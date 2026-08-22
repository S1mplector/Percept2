package com.jvn.fx.vn;

import com.jvn.core.assets.BoundedImageCache;
import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.fx.FxImageMemory;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnRendererCompositeCacheTest {

  private static final int FULL_HD_WIDTH = 1920;
  private static final int FULL_HD_HEIGHT = 1080;

  @Test
  void dedicatedCompositeCacheSurvivesFullCanvasLayerSourceChurn() {
    Image fullHdRaster = new WritableImage(FULL_HD_WIDTH, FULL_HD_HEIGHT);
    BoundedImageCache<Image> shared = cache(VnRenderer.SOURCE_IMAGE_CACHE_BUDGET_BYTES);

    // Model six layered characters with twelve full-canvas source layers each. In the former
    // shared cache, building the next character evicts the composite from the previous one.
    populateLunchTableFrame(shared, shared, fullHdRaster);
    assertNull(shared.get("composite:0"));

    BoundedImageCache<Image> sources = cache(VnRenderer.SOURCE_IMAGE_CACHE_BUDGET_BYTES);
    BoundedImageCache<Image> composites = cache(VnRenderer.COMPOSITE_SPRITE_CACHE_BUDGET_BYTES);
    populateLunchTableFrame(sources, composites, fullHdRaster);

    assertEquals(6, composites.size());
    for (int frame = 0; frame < 120; frame++) {
      for (int character = 0; character < 6; character++) {
        assertNotNull(composites.get("composite:" + character));
      }
    }
    assertEquals(720, composites.hitCount());
    assertTrue(sources.currentWeight() <= VnRenderer.SOURCE_IMAGE_CACHE_BUDGET_BYTES);
    assertTrue(composites.currentWeight() <= VnRenderer.COMPOSITE_SPRITE_CACHE_BUDGET_BYTES);
  }

  @Test
  void compositeBudgetRetainsAFullHdTransitionWorkingSet() {
    long fullHdBytes = (long) FULL_HD_WIDTH * FULL_HD_HEIGHT * 4L;

    // Six visible characters can each cross-fade between two expressions.
    assertTrue(VnRenderer.COMPOSITE_SPRITE_CACHE_BUDGET_BYTES >= 12L * fullHdBytes);
  }

  @Test
  void dedicatedBackgroundCacheSurvivesExpressionLayerChurn() {
    Image fullHdRaster = new WritableImage(FULL_HD_WIDTH, FULL_HD_HEIGHT);
    BoundedImageCache<Image> shared = cache(VnRenderer.SOURCE_IMAGE_CACHE_BUDGET_BYTES);
    shared.put("background", fullHdRaster);
    for (int layer = 0; layer < 12; layer++) {
      shared.put("next-expression-layer:" + layer, fullHdRaster);
    }
    assertNull(shared.get("background"),
        "The former shared cache evicted the background while composing the next expression");

    BoundedImageCache<Image> sources = cache(VnRenderer.SOURCE_IMAGE_CACHE_BUDGET_BYTES);
    BoundedImageCache<Image> backgrounds = cache(VnRenderer.BACKGROUND_IMAGE_CACHE_BUDGET_BYTES);
    backgrounds.put("background", fullHdRaster);
    for (int line = 0; line < 120; line++) {
      for (int layer = 0; layer < 12; layer++) {
        sources.put("line:" + line + ":layer:" + layer, fullHdRaster);
      }
      assertNotNull(backgrounds.get("background"));
    }

    assertEquals(120, backgrounds.hitCount());
    assertTrue(backgrounds.currentWeight() <= VnRenderer.BACKGROUND_IMAGE_CACHE_BUDGET_BYTES);
  }

  @Test
  void backgroundBudgetRetainsBothSidesOfAFullHdTransition() {
    long fullHdBytes = (long) FULL_HD_WIDTH * FULL_HD_HEIGHT * 4L;

    assertTrue(VnRenderer.BACKGROUND_IMAGE_CACHE_BUDGET_BYTES >= 2L * fullHdBytes);
  }

  @Test
  void staticCompositePathDoesNotProbeEveryLayerRaster() throws Exception {
    VnCharacter character = VnCharacter.builder("table_guest")
        .addLayer("body", "missing-body.png")
        .addLayer("face", "missing-face.png")
        .addExpression(
            "neutral",
            "missing-body.png|missing-face.png",
            List.of("body", "face"))
        .build();
    VnScenario scenario = new VnScenarioBuilder("static_composite")
        .addCharacter(character)
        .end()
        .build();
    VnRenderer renderer = new VnRenderer(new Canvas(64, 64).getGraphicsContext2D());

    Field cacheField = VnRenderer.class.getDeclaredField("imageCache");
    cacheField.setAccessible(true);
    BoundedImageCache<?> sourceCache = (BoundedImageCache<?>) cacheField.get(renderer);
    long missesBeforeLayerCheck = sourceCache.missCount();
    Method renderLayers = VnRenderer.class.getDeclaredMethod(
        "renderTimelineDrivenLayers",
        VnCharacter.class,
        String.class,
        String.class,
        List.class,
        double.class,
        double.class,
        double.class,
        double.class,
        double.class,
        double.class,
        VnState.class,
        VnScenario.class,
        VnStagePreset.class);
    renderLayers.setAccessible(true);

    boolean rendered = (boolean) renderLayers.invoke(
        renderer,
        character,
        "neutral",
        "table_guest",
        List.of("missing-body.png", "missing-face.png"),
        0.0,
        0.0,
        64.0,
        64.0,
        64.0,
        64.0,
        new VnState(),
        scenario,
        null);

    assertFalse(rendered);
    assertEquals(missesBeforeLayerCheck, sourceCache.missCount(),
        "Static composites must not load their raw layers during each render pass");

    VnCharacterSceneAccessor accessor = new VnCharacterSceneAccessor();
    accessor.findEntity("table_guest_body");
    renderer.setTimelineAccessor(accessor);
    boolean renderedAsLayers = (boolean) renderLayers.invoke(
        renderer,
        character,
        "neutral",
        "table_guest",
        List.of("missing-body.png", "missing-face.png"),
        0.0,
        0.0,
        64.0,
        64.0,
        64.0,
        64.0,
        new VnState(),
        scenario,
        null);

    assertTrue(renderedAsLayers);
    assertEquals(missesBeforeLayerCheck + 2, sourceCache.missCount(),
        "An active layer timeline must still request every raster needed for independent drawing");
  }

  private static BoundedImageCache<Image> cache(long budgetBytes) {
    return new BoundedImageCache<>(256, budgetBytes, FxImageMemory::estimatedBytes);
  }

  private static void populateLunchTableFrame(
      BoundedImageCache<Image> sources,
      BoundedImageCache<Image> composites,
      Image raster
  ) {
    for (int character = 0; character < 6; character++) {
      for (int layer = 0; layer < 12; layer++) {
        sources.put("source:" + character + ":" + layer, raster);
      }
      composites.put("composite:" + character, raster);
    }
  }
}
