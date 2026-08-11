package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import org.junit.jupiter.api.Test;

class LayeredCharacterResolverTest {

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
