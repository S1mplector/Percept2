package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class EyeFocusResolverTest {
  @Test
  void resolvesAllKeypadDirections() {
    assertEquals(8, EyeFocusResolver.resolve(0, 0, 0, -1, 0.01, 3, 1).keypadIndex());
    assertEquals(9, EyeFocusResolver.resolve(0, 0, 1, -1, 0.01, 3, 1).keypadIndex());
    assertEquals(6, EyeFocusResolver.resolve(0, 0, 1, 0, 0.01, 3, 1).keypadIndex());
    assertEquals(3, EyeFocusResolver.resolve(0, 0, 1, 1, 0.01, 3, 1).keypadIndex());
    assertEquals(2, EyeFocusResolver.resolve(0, 0, 0, 1, 0.01, 3, 1).keypadIndex());
    assertEquals(1, EyeFocusResolver.resolve(0, 0, -1, 1, 0.01, 3, 1).keypadIndex());
    assertEquals(4, EyeFocusResolver.resolve(0, 0, -1, 0, 0.01, 3, 1).keypadIndex());
    assertEquals(7, EyeFocusResolver.resolve(0, 0, -1, -1, 0.01, 3, 1).keypadIndex());
  }

  @Test
  void deadZoneReturnsNeutralAndNudgeStrengthIsClamped() {
    EyeFocusResolver.Result neutral = EyeFocusResolver.resolve(0, 0, 0.05, 0.05, 0.12, 3, 1);
    assertEquals(5, neutral.keypadIndex());
    assertEquals(0.0, neutral.nudgeX(), 0.0001);
    assertEquals(0.0, neutral.nudgeY(), 0.0001);

    EyeFocusResolver.Result clamped = EyeFocusResolver.resolve(0, 0, 10, 0, 0.01, 3, 99);
    assertEquals(6, clamped.keypadIndex());
    assertEquals(6.0, clamped.nudgeX(), 0.0001);
    assertEquals(0.0, clamped.nudgeY(), 0.0001);
  }

  @Test
  void autoDetectsLayerIdsAndPropertiesRoundTrip() {
    VnCharacter character = VnCharacter.builder("john")
        .addLayer("john_neutral_eyes_01", "eyes/01.png")
        .addLayer("john_neutral_eyes_02", "eyes/02.png")
        .addLayer("john_neutral_pupil_09", "eyes/09.png")
        .addExpression("neutral", "$", java.util.List.of("john_neutral_eyes_01", "john_neutral_eyes_02", "john_neutral_pupil_09"))
        .build();

    VnEyeFocusProfile detected = VnEyeFocusProfile.autoDetect(character, "neutral").orElseThrow();
    assertEquals("john_neutral_eyes_01", detected.layerIdFor(1));
    assertEquals("john_neutral_eyes_02", detected.layerIdFor(2));
    assertEquals("john_neutral_pupil_09", detected.layerIdFor(9));

    Map<Integer, String> layers = new LinkedHashMap<>();
    for (int i = 1; i <= 9; i++) layers.put(i, "eyes_%02d".formatted(i));
    VnEyeFocusProfile profile = new VnEyeFocusProfile("john", "neutral", "eyes", 0.51, 0.27, 0.1, 4, 0.8, layers);
    Properties written = VnEyeFocusProfileStore.write(java.util.List.of(profile));
    VnEyeFocusProfile parsed = VnEyeFocusProfileStore.parse(written).get(0);

    assertEquals("john/neutral", parsed.key());
    assertEquals("eyes_06", parsed.layerIdFor(6));
    assertEquals(0.51, parsed.sourceX(), 0.0001);
    assertTrue(parsed.complete());
  }
}
