package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jvn.core.vn.LayeredCharacterResolver;

class VnRendererLayerCrossfadePlanTest {

  @Test
  void unchangedLayersDrawOnceAtFullAlphaWhileChangedLayersCrossfade() {
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

    List<VnRenderer.LayerDrawPlanEntry> plan = VnRenderer.buildLayerCrossfadePlan(
        diff, toLayerOrder, fromPaths, toPaths, 1.0, 0.4);

    assertEquals(
        List.of(
            new VnRenderer.LayerDrawPlanEntry("body_default", "body.png", 1.0),
            new VnRenderer.LayerDrawPlanEntry("eye_normal", "eye_normal.png", 1.0),
            new VnRenderer.LayerDrawPlanEntry("mouth_neutral", "mouth_neutral.png", 0.6),
            new VnRenderer.LayerDrawPlanEntry("mouth_open", "mouth_open.png", 0.4)),
        plan);
  }

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

    List<VnRenderer.LayerDrawPlanEntry> plan = VnRenderer.buildLayerCrossfadePlan(
        diff, toLayerOrder, fromPaths, toPaths, 1.0, 0.25);

    assertEquals(
        List.of(
            new VnRenderer.LayerDrawPlanEntry("body_default", "body.png", 1.0),
            new VnRenderer.LayerDrawPlanEntry("sparkle_fx", "sparkle.png", 0.25),
            new VnRenderer.LayerDrawPlanEntry("hat_default", "hat.png", 0.75)),
        plan);
  }
}
