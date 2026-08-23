package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.core.vn.VnCharacter;

/**
 * Regression coverage for renderLayeredExpressionCrossfade's draw plan: a layer lane
 * (e.g. "mouth") that is present in both the from- and to-expression must never be
 * entirely absent from the plan at any point during the transition, even at the
 * midpoint or the endpoints. A missing plan entry means a blank/missing layer is
 * drawn to screen for that frame.
 */
class VnRendererLayerCrossfadeNoBlankFrameTest {

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
      List<VnRenderer.LayerDrawPlanEntry> plan = VnRenderer.buildLayerCrossfadePlan(
          diff, toLayerOrder, fromPaths, toPaths, 1.0, progress);

      boolean mouthPresent = plan.stream().anyMatch(entry ->
          (entry.layerId().equals("mouth_neutral") || entry.layerId().equals("mouth_open"))
              && entry.alpha() > 0.0);

      assertTrue(mouthPresent,
          "expected a mouth layer entry with positive alpha at progress=" + progress + " but plan was " + plan);
    }
  }

  /**
   * VnRenderer.layerPathsById builds an id-to-path map by zipping character.getExpressionLayerIds
   * (declared @charlayer ids for the expression) positionally against the "|"-joined segments of
   * character.getExpressionPath(expression). If a scenario declares one more layer id than the
   * expression's sprite path has "|" segments -- an easy authoring slip -- the trailing declared
   * layer (here, mouth) must still resolve, falling back to the character's registered @charlayer
   * path (VnCharacter.getLayerPath), or it silently vanishes from every future crossfade plan.
   * This reproduces the zip using real VnCharacter/VnRenderer production types.
   */
  private static Map<String, String> layerPathsById(VnCharacter character, String expression) {
    List<String> layerIds = character.getExpressionLayerIds(expression);
    List<String> layerPaths = VnRenderer.parseLayerPaths(character.getExpressionPath(expression));
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

    List<VnRenderer.LayerDrawPlanEntry> planAtStart = VnRenderer.buildLayerCrossfadePlan(
        diff, toLayerIds, fromPaths, toPaths, 1.0, 0.0);

    boolean mouthPresentAtStart = planAtStart.stream().anyMatch(entry ->
        (entry.layerId().equals("mouth_neutral") || entry.layerId().equals("mouth_open"))
            && entry.alpha() > 0.0);

    assertTrue(mouthPresentAtStart,
        "expected a mouth layer entry with positive alpha at transition start but plan was " + planAtStart);
  }
}
