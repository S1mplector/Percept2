package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jvn.core.animation.Easing;

class EntityTrackKeyframeUpsertTest {

  @Test
  void addKeyframeUpsertsAtNearlyIdenticalTimestamp() {
    EntityTrack track = new EntityTrack("hero");

    track.addKeyframe(PropertyType.X, new Keyframe(100.0, 10.0));
    track.addKeyframe(PropertyType.X, new Keyframe(100.0005, 42.0));

    List<Keyframe> keyframes = track.getKeyframes(PropertyType.X);
    assertEquals(1, keyframes.size());
    assertEquals(100.0005, keyframes.get(0).getTimeMs(), 0.0000001);
    assertEquals(42.0, keyframes.get(0).getValue(), 0.0000001);
  }

  @Test
  void upsertPreservesExistingEasingCurve() {
    EntityTrack track = new EntityTrack("hero");
    Keyframe existing = new Keyframe(250.0, 5.0, Easing.Type.CUSTOM);
    existing.setBezierParams(0.1, 0.2, 0.7, 0.8);
    track.addKeyframe(PropertyType.X, existing);

    Keyframe replaced = track.upsertKeyframe(PropertyType.X, new Keyframe(250.0, 9.0, Easing.Type.LINEAR));

    assertSame(existing, replaced);
    assertEquals(1, track.getKeyframes(PropertyType.X).size());
    assertEquals(Easing.Type.CUSTOM, replaced.getEasing());
    assertEquals(9.0, replaced.getValue(), 0.0000001);
    assertEquals(0.1, replaced.getCx1(), 0.0000001);
    assertEquals(0.2, replaced.getCy1(), 0.0000001);
    assertEquals(0.7, replaced.getCx2(), 0.0000001);
    assertEquals(0.8, replaced.getCy2(), 0.0000001);
  }

  @Test
  void upsertAddsKeyframeWhenTimestampIsDistinct() {
    EntityTrack track = new EntityTrack("hero");

    track.upsertKeyframe(PropertyType.X, new Keyframe(100.0, 1.0));
    track.upsertKeyframe(PropertyType.X, new Keyframe(101.5, 2.0));

    assertEquals(2, track.getKeyframes(PropertyType.X).size());
  }

  @Test
  void findsTimestampWithinToleranceOnLargeTrack() {
    EntityTrack track = new EntityTrack("hero");
    for (int i = 0; i < 4096; i++) {
      track.upsertKeyframe(PropertyType.X, new Keyframe(i * 10.0, i));
    }

    Keyframe found = track.findKeyframeAt(PropertyType.X, 30000.0005);

    assertEquals(3000.0, found.getValue(), 0.0000001);
    assertNull(track.findKeyframeAt(PropertyType.X, 30000.01));
  }

  @Test
  void samplesCorrectlyAcrossPlaybackAndSeekJumps() {
    EntityTrack track = new EntityTrack("hero");
    for (int i = 0; i < 64; i++) {
      track.addKeyframe(PropertyType.X, new Keyframe(i * 10.0, i * 100.0));
    }

    assertEquals(50.0, track.getValueAt(PropertyType.X, 5.0), 0.0000001);
    assertEquals(2450.0, track.getValueAt(PropertyType.X, 245.0), 0.0000001);
    assertEquals(2550.0, track.getValueAt(PropertyType.X, 255.0), 0.0000001);
    assertEquals(6150.0, track.getValueAt(PropertyType.X, 615.0), 0.0000001);
    assertEquals(150.0, track.getValueAt(PropertyType.X, 15.0), 0.0000001);
  }

  @Test
  void animatedPropertyIndexTracksSetRemoveAndCopy() {
    EntityTrack track = new EntityTrack("hero");
    Keyframe x = new Keyframe(100.0, 1.0);
    track.addKeyframe(PropertyType.X, x);
    track.setKeyframes(PropertyType.ALPHA, List.of(new Keyframe(200.0, 0.5)));

    assertIterableEquals(List.of(PropertyType.X, PropertyType.ALPHA), track.getAnimatedProperties());

    EntityTrack copy = track.copy();
    track.removeKeyframe(PropertyType.X, x);
    track.setKeyframes(PropertyType.ALPHA, List.of());

    assertFalse(track.hasKeyframes(PropertyType.X));
    assertFalse(track.hasKeyframes(PropertyType.ALPHA));
    assertIterableEquals(List.of(), track.getAnimatedProperties());
    assertTrue(copy.hasKeyframes(PropertyType.X));
    assertTrue(copy.hasKeyframes(PropertyType.ALPHA));
    assertIterableEquals(List.of(PropertyType.X, PropertyType.ALPHA), copy.getAnimatedProperties());
  }
}
