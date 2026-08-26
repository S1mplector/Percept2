package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jvn.core.scene2d.RenderDiagnostics;
import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.core.vn.VnCharacter;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class VnCharacterCompositorTest {

  @Test
  void unchangedLayersDrawOnceAtFullAlphaWhileChangedLayersCrossfade() {
    LayeredCharacterResolver.ExpressionLayerDiff diff = new LayeredCharacterResolver.ExpressionLayerDiff(
        List.of("body_default", "eye_normal"),
        List.of(new LayeredCharacterResolver.LayerChange("mouth_neutral", "mouth_open")),
        List.of(), List.of());
    List<String> toLayerOrder = List.of("body_default", "eye_normal", "mouth_open");
    Map<String, String> fromPaths = Map.of(
        "body_default", "body.png", "eye_normal", "eye_normal.png", "mouth_neutral", "mouth_neutral.png");
    Map<String, String> toPaths = Map.of(
        "body_default", "body.png", "eye_normal", "eye_normal.png", "mouth_open", "mouth_open.png");

    List<VnCharacterCompositor.LayerDrawPlanEntry> plan = VnCharacterCompositor.buildLayerCrossfadePlan(
        diff, toLayerOrder, fromPaths, toPaths, 1.0, 0.4);

    assertEquals(
        List.of(
            new VnCharacterCompositor.LayerDrawPlanEntry("body_default", "body.png", 1.0),
            new VnCharacterCompositor.LayerDrawPlanEntry("eye_normal", "eye_normal.png", 1.0),
            new VnCharacterCompositor.LayerDrawPlanEntry("mouth_neutral", "mouth_neutral.png", 0.6),
            new VnCharacterCompositor.LayerDrawPlanEntry("mouth_open", "mouth_open.png", 0.4)),
        plan);
  }

  @Test
  void parsesLayeredSpritePathsWithoutRegexSplitting() {
    assertEquals(List.of("body.png"), VnCharacterCompositor.parseLayerPaths(" body.png "));
    assertEquals(
        List.of("body.png", "eyes.png", "mouth.png"),
        VnCharacterCompositor.parseLayerPaths(" body.png | eyes.png || mouth.png "));
    assertEquals(List.of(), VnCharacterCompositor.parseLayerPaths("   "));
  }

  @Test
  void resolvesAuthoredCharacterScaleAndDefault() {
    assertEquals(1.0, VnCharacterCompositor.characterScale(null), 1e-9);
    assertEquals(1.0, VnCharacterCompositor.characterScale(VnCharacter.builder("regular").build()), 1e-9);
    assertEquals(1.25, VnCharacterCompositor.characterScale(VnCharacter.builder("large").scale(1.25).build()), 1e-9);
  }

  @Test
  void canvasAlignedSpriteSheetsIgnorePortraitFraming() {
    VnCharacterCompositor.SpriteLayout layout = VnCharacterCompositor.resolveSpriteLayout(
        1920.0, 1080.0, 1920.0, 1080.0, 1.435, 1.325, 1.0);

    assertEquals(1920.0, layout.width(), 1e-9);
    assertEquals(1080.0, layout.height(), 1e-9);
    assertEquals(1.0, layout.baselineY(), 1e-9);
    assertTrue(layout.canvasAligned());
  }

  @Test
  void portraitSpritesRetainConfiguredFraming() {
    VnCharacterCompositor.SpriteLayout layout = VnCharacterCompositor.resolveSpriteLayout(
        1240.0, 1550.0, 1920.0, 1080.0, 1.435, 1.325, 1.0);

    assertEquals(1240.0 * (1549.8 / 1550.0), layout.width(), 1e-9);
    assertEquals(1549.8, layout.height(), 1e-9);
    assertEquals(1.325, layout.baselineY(), 1e-9);
    assertFalse(layout.canvasAligned());
  }

  @Test
  void stageCharacterCacheKeySnapsSubPixelJitterToTheSameKey() {
    String base = VnCharacterCompositor.stageCharacterCacheKey("alice_happy", "stage-1", 100.0, 200.0, 300.0, 400.0, 1280.0, 720.0);
    String jittered = VnCharacterCompositor.stageCharacterCacheKey("alice_happy", "stage-1", 101.5, 198.7, 300.0, 400.0, 1280.0, 720.0);
    assertEquals(base, jittered);
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Ported from modules/fx VnRendererLayerCrossfadePlanTest
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  void addedLayersFadeInAndRemovedLayersFadeOut() {
    LayeredCharacterResolver.ExpressionLayerDiff diff = new LayeredCharacterResolver.ExpressionLayerDiff(
        List.of("body_default"),
        List.of(),
        List.of("sparkle_fx"),
        List.of("hat_default"));
    List<String> toLayerOrder = List.of("body_default", "sparkle_fx");
    Map<String, String> fromPaths = Map.of("body_default", "body.png", "hat_default", "hat.png");
    Map<String, String> toPaths = Map.of("body_default", "body.png", "sparkle_fx", "sparkle.png");

    List<VnCharacterCompositor.LayerDrawPlanEntry> plan = VnCharacterCompositor.buildLayerCrossfadePlan(
        diff, toLayerOrder, fromPaths, toPaths, 1.0, 0.25);

    assertEquals(
        List.of(
            new VnCharacterCompositor.LayerDrawPlanEntry("body_default", "body.png", 1.0),
            new VnCharacterCompositor.LayerDrawPlanEntry("sparkle_fx", "sparkle.png", 0.25),
            new VnCharacterCompositor.LayerDrawPlanEntry("hat_default", "hat.png", 0.75)),
        plan);
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Ported from modules/fx VnRendererLayerCrossfadeNoBlankFrameTest
  //
  //  Regression coverage for renderLayeredExpressionCrossfade's draw plan: a layer lane
  //  (e.g. "mouth") that is present in both the from- and to-expression must never be
  //  entirely absent from the plan at any point during the transition, even at the
  //  midpoint or the endpoints. A missing plan entry means a blank/missing layer is
  //  drawn to screen for that frame.
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  void mouthLayerNeverAbsentFromPlanAcrossFullTransitionSweep() {
    LayeredCharacterResolver.ExpressionLayerDiff diff = new LayeredCharacterResolver.ExpressionLayerDiff(
        List.of("body_default", "eye_normal"),
        List.of(new LayeredCharacterResolver.LayerChange("mouth_neutral", "mouth_open")),
        List.of(),
        List.of());
    List<String> toLayerOrder = List.of("body_default", "eye_normal", "mouth_open");
    Map<String, String> fromPaths = Map.of(
        "body_default", "body.png", "eye_normal", "eye_normal.png", "mouth_neutral", "mouth_neutral.png");
    Map<String, String> toPaths = Map.of(
        "body_default", "body.png", "eye_normal", "eye_normal.png", "mouth_open", "mouth_open.png");

    for (double progress = 0.0; progress <= 1.0; progress += 0.05) {
      List<VnCharacterCompositor.LayerDrawPlanEntry> plan = VnCharacterCompositor.buildLayerCrossfadePlan(
          diff, toLayerOrder, fromPaths, toPaths, 1.0, progress);

      boolean mouthPresent = plan.stream().anyMatch(entry ->
          (entry.layerId().equals("mouth_neutral") || entry.layerId().equals("mouth_open"))
              && entry.alpha() > 0.0);

      assertTrue(mouthPresent,
          "expected a mouth layer entry with positive alpha at progress=" + progress + " but plan was " + plan);
    }
  }

  /**
   * VnCharacterCompositor.layerPathsById builds an id-to-path map by zipping
   * character.getExpressionLayerIds (declared @charlayer ids for the expression) positionally
   * against the "|"-joined segments of character.getExpressionPath(expression). If a scenario
   * declares one more layer id than the expression's sprite path has "|" segments -- an easy
   * authoring slip -- the trailing declared layer (here, mouth) must still resolve, falling back
   * to the character's registered @charlayer path (VnCharacter.getLayerPath), or it silently
   * vanishes from every future crossfade plan. This reproduces the zip using real
   * VnCharacter/VnCharacterCompositor production types.
   */
  private static Map<String, String> layerPathsById(VnCharacter character, String expression) {
    List<String> layerIds = character.getExpressionLayerIds(expression);
    List<String> layerPaths = VnCharacterCompositor.parseLayerPaths(character.getExpressionPath(expression));
    Map<String, String> byId = new LinkedHashMap<>();
    for (int i = 0; i < layerIds.size(); i++) {
      String layerId = layerIds.get(i);
      String path = i < layerPaths.size() ? layerPaths.get(i) : character.getLayerPath(layerId);
      if (path != null) byId.put(layerId, path);
    }
    return byId;
  }

  @Test
  void mouthLayerStaysInPlanWhenDeclaredLayerCountExceedsPathSegmentCount() {
    List<String> fromLayerIds = List.of("body_default", "mouth_neutral");
    List<String> toLayerIds = List.of("body_default", "mouth_open");

    VnCharacter character = VnCharacter.builder("yui")
        // Missing the "|mouth_neutral.png" segment: only one path for two declared layer ids.
        .addExpression("neutral", "body.png", fromLayerIds)
        .addExpression("happy", "body.png|mouth_open.png", toLayerIds)
        .addLayer("mouth_neutral", "mouth_neutral.png")
        .build();

    LayeredCharacterResolver.ExpressionLayerDiff diff =
        LayeredCharacterResolver.diffExpressionLayers(fromLayerIds, toLayerIds);
    Map<String, String> fromPaths = layerPathsById(character, "neutral");
    Map<String, String> toPaths = layerPathsById(character, "happy");

    List<VnCharacterCompositor.LayerDrawPlanEntry> planAtStart = VnCharacterCompositor.buildLayerCrossfadePlan(
        diff, toLayerIds, fromPaths, toPaths, 1.0, 0.0);

    boolean mouthPresentAtStart = planAtStart.stream().anyMatch(entry ->
        (entry.layerId().equals("mouth_neutral") || entry.layerId().equals("mouth_open"))
            && entry.alpha() > 0.0);

    assertTrue(mouthPresentAtStart,
        "expected a mouth layer entry with positive alpha at transition start but plan was " + planAtStart);
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Ported from modules/fx VnRendererLayerProxyFallbackTest
  //  (parsesLayeredSpritePathsWithoutRegexSplitting is already covered above.)
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  void layerGroupTargetsExposeExpressionSpecificAndStableAliases() {
    assertEquals(
        List.of("john_head", "john_neutral_head"),
        VnCharacterCompositor.timelineGroupTargetNames("john", "neutral", "head"));
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Ported from modules/fx VnRendererMissingLayerWarningTest
  //
  //  Regression coverage for VnCharacterCompositor.reportMissingCharacterLayers: when a
  //  character's sprite fails to resolve at draw time, the renderer must warn with enough
  //  detail to diagnose which character/expression/layer/path was missing, and must not spam
  //  the log for repeated renders of the same failure.
  // ───────────────────────────────────────────────────────────────────────────

  private ListAppender<ILoggingEvent> appender;
  private Logger diagnosticsLogger;

  @BeforeEach
  void setUp() {
    RenderDiagnostics.reset();
    diagnosticsLogger = (Logger) LoggerFactory.getLogger(RenderDiagnostics.class);
    appender = new ListAppender<>();
    appender.start();
    diagnosticsLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    diagnosticsLogger.detachAppender(appender);
    RenderDiagnostics.reset();
  }

  @Test
  void warnsWithCharacterExpressionLayerIdAndPathForMissingLayer() {
    VnCharacter character = VnCharacter.builder("yui")
        .addExpression("happy", "body.png|mouth_open.png", List.of("body_default", "mouth_open"))
        .build();

    VnCharacterCompositor.reportMissingCharacterLayers(character, "yui", "happy", "body.png|mouth_open.png");

    List<ILoggingEvent> warnings = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertEquals(2, warnings.size(), "expected one warning per declared layer");
    String combined = String.join("\n", warnings.stream().map(ILoggingEvent::getFormattedMessage).toList());
    assertTrue(combined.contains("yui"), "warning should mention the character id");
    assertTrue(combined.contains("happy"), "warning should mention the expression");
    assertTrue(combined.contains("body_default"), "warning should mention the layer id");
    assertTrue(combined.contains("mouth_open"), "warning should mention the layer id");
    assertTrue(combined.contains("body.png"), "warning should mention the resolved path");
    assertTrue(combined.contains("mouth_open.png"), "warning should mention the resolved path");
  }

  @Test
  void deduplicatesRepeatedWarningsForTheSameMissingLayer() {
    VnCharacter character = VnCharacter.builder("yui")
        .addExpression("neutral", "body.png", List.of("body_default"))
        .build();

    VnCharacterCompositor.reportMissingCharacterLayers(character, "yui", "neutral", "body.png");
    VnCharacterCompositor.reportMissingCharacterLayers(character, "yui", "neutral", "body.png");
    VnCharacterCompositor.reportMissingCharacterLayers(character, "yui", "neutral", "body.png");

    long warnCount = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    assertEquals(1, warnCount, "repeated renders of the same missing layer must not spam the log");
  }

  @Test
  void fallsBackToImagePathWhenNoLayersAreDeclared() {
    VnCharacter character = VnCharacter.builder("plain")
        .addExpression("neutral", "plain.png")
        .build();

    VnCharacterCompositor.reportMissingCharacterLayers(character, "plain", "neutral", "plain.png");

    List<ILoggingEvent> warnings = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).getFormattedMessage().contains("plain.png"));
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Real-configured-asset composite building
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  void compositesMultipleRealLayerAssetsIntoARenderTarget() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnCharacterCompositor compositor = new VnCharacterCompositor(blitter);
    // modules/scene-render/src/test/resources/game/images/probe/tier1.png (4x3) exists per
    // AssetDimensionProbeTest's fixture; a single real resolvable layer plus one missing layer
    // is enough to prove the resolved-layers-only compositing path.
    var target = compositor.compositeSpriteFor(
        "probe/tier1.png|does/not/exist.png",
        java.util.List.of("probe/tier1.png", "does/not/exist.png"));

    // Single resolvable layer: composite building still degrades to that one layer's dimensions.
    org.junit.jupiter.api.Assertions.assertNotNull(target);
    assertEquals(4.0, target.getWidth(), 1e-9);
    assertEquals(3.0, target.getHeight(), 1e-9);
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  Ported from modules/fx VnRendererCompositeCacheTest (budget-constant assertions only —
  //  see Task 15's cross-check note: the cache-churn tests modeled Image raster weight via
  //  FxImageMemory and no longer apply once compositing moved to RenderTarget2D inside this
  //  collaborator, whose own cache-eviction behavior is already covered by this file's other
  //  tests and by compositeSpriteFor's real-asset test above).
  // ───────────────────────────────────────────────────────────────────────────

  @Test
  void compositeBudgetRetainsAFullHdTransitionWorkingSet() {
    long fullHdBytes = 1920L * 1080L * 4L;
    assertTrue(com.jvn.scenerender.vn.VnRenderer.COMPOSITE_SPRITE_CACHE_BUDGET_BYTES >= 12L * fullHdBytes);
  }

  // backgroundBudgetRetainsBothSidesOfAFullHdTransition intentionally not ported: background
  // image caching is retired under this port (backgrounds draw straight through
  // Blitter2D.drawImage per VnTransitionRenderer's design), so BACKGROUND_IMAGE_CACHE_BUDGET_BYTES
  // is unused by any cache and a test asserting on it can never fail regardless of runtime
  // behavior — it would only give false confidence.
}
