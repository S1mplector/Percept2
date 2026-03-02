package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.*;

import com.jvn.core.animation.Easing;
import org.junit.jupiter.api.Test;

/**
 * Tests for AnimationClip capture, apply, serialize/deserialize (category D + H).
 */
class AnimationClipTest {

    @Test
    void captureAndApplyPreservesKeyframes() {
        EntityTrack source = new EntityTrack("sprite");
        source.addKeyframe(PropertyType.X, new Keyframe(100, 0, Easing.Type.LINEAR));
        source.addKeyframe(PropertyType.X, new Keyframe(300, 200, Easing.Type.EASE_OUT_QUAD));
        source.addKeyframe(PropertyType.Y, new Keyframe(100, 50, Easing.Type.LINEAR));
        source.addKeyframe(PropertyType.Y, new Keyframe(300, 150, Easing.Type.EASE_OUT_QUAD));

        AnimationClip clip = new AnimationClip("walk");
        clip.captureFromTrack(source, 100, 300);

        assertEquals(200.0, clip.getDurationMs(), 0.001);
        assertEquals(2, clip.getChannels().size());
        assertEquals(2, clip.getChannels().get(PropertyType.X).size());
        assertEquals(2, clip.getChannels().get(PropertyType.Y).size());

        // Apply to a different track at t=500
        EntityTrack target = new EntityTrack("other");
        clip.applyToTrack(target, 500, 1.0);

        assertTrue(target.hasKeyframes(PropertyType.X));
        assertTrue(target.hasKeyframes(PropertyType.Y));
        assertEquals(0.0, target.getValueAt(PropertyType.X, 500), 0.01);
        assertEquals(200.0, target.getValueAt(PropertyType.X, 700), 0.01);
    }

    @Test
    void applyWithDurationScaling() {
        EntityTrack source = new EntityTrack("sprite");
        source.addKeyframe(PropertyType.X, new Keyframe(0, 0, Easing.Type.LINEAR));
        source.addKeyframe(PropertyType.X, new Keyframe(100, 100, Easing.Type.LINEAR));

        AnimationClip clip = new AnimationClip("quick");
        clip.captureFromTrack(source, 0, 100);

        // Apply at 2x duration
        EntityTrack target = new EntityTrack("target");
        clip.applyToTrack(target, 0, 2.0);

        // At 2x scale, the keyframe at offset 100 should be at time 200
        assertEquals(100.0, target.getValueAt(PropertyType.X, 200), 0.01);
    }

    @Test
    void serializeDeserializeRoundTrip() {
        EntityTrack source = new EntityTrack("sprite");
        source.addKeyframe(PropertyType.X, new Keyframe(0, 10, Easing.Type.LINEAR));
        source.addKeyframe(PropertyType.X, new Keyframe(500, 300, Easing.Type.EASE_IN_OUT_CUBIC));
        source.addKeyframe(PropertyType.ALPHA, new Keyframe(0, 1.0, Easing.Type.LINEAR));
        source.addKeyframe(PropertyType.ALPHA, new Keyframe(500, 0.5, Easing.Type.EASE_OUT_QUAD));

        AnimationClip original = new AnimationClip("test_clip");
        original.captureFromTrack(source, 0, 500);

        String serialized = original.serialize();
        assertNotNull(serialized);
        assertTrue(serialized.contains("clip.name=test_clip"));
        assertTrue(serialized.contains("clip.durationMs=500"));

        AnimationClip restored = AnimationClip.deserialize(serialized);
        assertEquals("test_clip", restored.getName());
        assertEquals(500.0, restored.getDurationMs(), 0.001);
        assertEquals(2, restored.getChannels().size());
        assertEquals(2, restored.getChannels().get(PropertyType.X).size());

        // Verify values survived
        AnimationClip.ClipKeyframe ckf = restored.getChannels().get(PropertyType.X).get(1);
        assertEquals(500.0, ckf.getOffsetMs(), 0.01);
        assertEquals(300.0, ckf.getValue(), 0.01);
        assertEquals(Easing.Type.EASE_IN_OUT_CUBIC, ckf.getEasing());
    }

    @Test
    void emptyClipSerializesAndDeserializes() {
        AnimationClip empty = new AnimationClip("empty");
        String serialized = empty.serialize();
        AnimationClip restored = AnimationClip.deserialize(serialized);
        assertEquals("empty", restored.getName());
        assertTrue(restored.getChannels().isEmpty());
    }

    @Test
    void captureIgnoresKeyframesOutsideRange() {
        EntityTrack source = new EntityTrack("sprite");
        source.addKeyframe(PropertyType.X, new Keyframe(50, 10, Easing.Type.LINEAR));
        source.addKeyframe(PropertyType.X, new Keyframe(200, 100, Easing.Type.LINEAR));
        source.addKeyframe(PropertyType.X, new Keyframe(400, 300, Easing.Type.LINEAR));

        AnimationClip clip = new AnimationClip("range");
        clip.captureFromTrack(source, 100, 300);

        // Only the keyframe at 200 should be captured (50 and 400 are outside)
        assertEquals(1, clip.getChannels().get(PropertyType.X).size());
    }
}
