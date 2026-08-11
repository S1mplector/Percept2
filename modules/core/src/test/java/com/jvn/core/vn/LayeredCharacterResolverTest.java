package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LayeredCharacterResolverTest {

  @Test
  void expandsLayerGlobsDeterministically() {
    Map<String, Map<String, String>> layers = Map.of("john", Map.of(
        "body_no_limbs", "body-no-limbs.png",
        "arm_front_default", "arm.png",
        "body_default", "body.png"));

    assertEquals(
        List.of("body_default", "body_no_limbs"),
        LayeredCharacterResolver.resolveLayerMatches(layers, "john", "body_*").stream()
            .map(LayeredCharacterResolver.LayerMatch::layerId)
            .toList());
    assertEquals(
        List.of("arm_front_default"),
        LayeredCharacterResolver.resolveLayerMatches(layers, "", "john.arm_front_*").stream()
            .map(LayeredCharacterResolver.LayerMatch::layerId)
            .toList());
  }

  @Test
  void collapsesLayerDirectiveContinuationsWithoutChangingLineCount() {
    String source = "@chargroup john body \\\n  $body_* | \\\n  $neck_*\n@label start\n";

    String collapsed = LayeredCharacterResolver.collapseLayerDirectiveContinuations(source);

    assertEquals(source.split("\\n", -1).length, collapsed.split("\\n", -1).length);
    assertEquals("@chargroup john body $body_* | $neck_*", collapsed.lines().findFirst().orElseThrow());
  }

  @Test
  void infersLightningBodyAndFrontArmReplacementLanes() {
    Set<String> animated = Set.of("body_default", "arm_front_default", "neck_normal");

    assertEquals(
        "body_default",
        LayeredCharacterResolver.inferReplacementLayerId("body_no_limbs", animated));
    assertEquals(
        "arm_front_default",
        LayeredCharacterResolver.inferReplacementLayerId("arm_front_holding_wrist", animated));
  }

  @Test
  void keepsAnatomicalAndDirectionalLanesSeparate() {
    assertEquals(
        "normal_face_common_05",
        LayeredCharacterResolver.inferReplacementLayerId(
            "normal_face_common_07",
            Set.of("normal_face_common_05", "normal_mouth_common_01")));
    assertEquals(
        "arm_front_default",
        LayeredCharacterResolver.inferReplacementLayerId(
            "arm_front_crossed",
            Set.of("arm_front_default", "arm_behind_default")));
    assertNull(LayeredCharacterResolver.inferReplacementLayerId(
        "normal_mouth_common_02",
        Set.of("normal_face_common_05")));
  }

  @Test
  void refusesAnAmbiguousConventionMatch() {
    assertNull(LayeredCharacterResolver.inferReplacementLayerId(
        "body_no_limbs",
        Set.of("body_default", "body_alternate")));
  }
}
