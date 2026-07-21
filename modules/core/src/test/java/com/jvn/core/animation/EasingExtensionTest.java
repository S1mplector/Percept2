package com.jvn.core.animation;

import static com.jvn.plugin.api.animation.AnimationEasingDefinition.easing;
import static com.jvn.plugin.api.animation.AnimationEasingDefinition.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.plugin.api.ExtensionEntry;
import com.jvn.plugin.api.ExtensionRegistry;
import com.jvn.plugin.api.Registration;
import com.jvn.plugin.api.animation.AnimationEasing;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EasingExtensionTest {
  @AfterEach void clear() { EasingExtensions.clear(); }

  @Test
  void parsesValidatesFormatsAndEvaluatesNamedParameters() {
    AnimationEasing extension = easing("Elastic Pop")
        .parameter("overshoot", 1.0, range(0.0, 3.0))
        .evaluate(frame -> frame.progress() + frame.parameter("overshoot") * 0.1);
    EasingExtensions.install(registry("studio.elastic-pop", extension));

    EasingSpec spec = EasingSpec.tryParse("studio.elastic-pop(overshoot: 1.4)");
    assertTrue(spec.isExtension());
    assertEquals("studio.elastic-pop(overshoot: 1.4)", spec.toDslString());
    assertEquals(0.64, Easing.apply(spec, 0.5), 0.000001);
    assertNull(EasingSpec.tryParse("studio.elastic-pop(unknown: 1)"));
    assertNull(EasingSpec.tryParse("studio.elastic-pop(overshoot: 9)"));
  }

  @Test
  void usesMetadataDefaultsAndFallsBackSafelyAfterUninstall() {
    AnimationEasing extension = easing("Smooth")
        .parameter("power", 2.0, range(1.0, 4.0))
        .evaluate(frame -> Math.pow(frame.progress(), frame.parameter("power")));
    EasingExtensions.install(registry("studio.smooth", extension));
    EasingSpec spec = EasingSpec.tryParse("studio.smooth");
    assertEquals(0.25, Easing.apply(spec, 0.5), 0.000001);
    EasingExtensions.clear();
    assertEquals(0.5, Easing.apply(spec, 0.5), 0.000001);
  }

  @Test
  void survivesTimelineParsingAndDrivesTrackInterpolation() {
    AnimationEasing extension = easing("Accelerate")
        .parameter("power", 2.0, range(1.0, 4.0))
        .evaluate(frame -> Math.pow(frame.progress(), frame.parameter("power")));
    EasingExtensions.install(registry("studio.accelerate", extension));

    TimelineData data = TimelineDataParser.parse("extension-test", """
        timeline {
          move "hero" {
            x: 100
            dur: 1000
            easing: "studio.accelerate(power: 2)"
          }
        }
        """);
    TimelineData.Track track = data.getTrack("hero");
    TimelineData.Keyframe end = track.getKeyframes(TimelineData.Property.X).get(1);
    assertTrue(end.getEasingSpec().isExtension());
    assertEquals("studio.accelerate", end.getEasingSpec().getExtensionId());
    assertEquals(25.0, track.getValueAt(TimelineData.Property.X, 500), 0.000001);
  }

  private static ExtensionRegistry<AnimationEasing> registry(String id, AnimationEasing easing) {
    ExtensionEntry<AnimationEasing> entry = new ExtensionEntry<>(id, "test", easing);
    return new ExtensionRegistry<>() {
      @Override public Registration register(String ignored, AnimationEasing value) {
        throw new UnsupportedOperationException();
      }
      @Override public Optional<AnimationEasing> find(String requested) {
        return id.equals(requested) ? Optional.of(easing) : Optional.empty();
      }
      @Override public List<ExtensionEntry<AnimationEasing>> entries() { return List.of(entry); }
    };
  }
}
