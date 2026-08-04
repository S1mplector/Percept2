package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.animation.Easing;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityTrackInterpolationTest {

    @Test
    void holdModeKeepsPreviousValueUntilSegmentEnd() {
        EntityTrack track = new EntityTrack("hero");
        track.addKeyframe(PropertyType.X, new Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.X, new Keyframe(
            100, 100, Easing.Type.LINEAR, Easing.Interpolation.HOLD));

        assertEquals(0.0, track.getValueAt(PropertyType.X, 50), 1e-6);
        assertEquals(100.0, track.getValueAt(PropertyType.X, 100), 1e-6);
    }

    @Test
    void stepModeJumpsRightAfterSegmentStart() {
        EntityTrack track = new EntityTrack("hero");
        track.addKeyframe(PropertyType.X, new Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.X, new Keyframe(100, 100, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.X, new Keyframe(
            200, 200, Easing.Type.LINEAR, Easing.Interpolation.STEP));

        assertEquals(100.0, track.getValueAt(PropertyType.X, 100), 1e-6);
        assertEquals(200.0, track.getValueAt(PropertyType.X, 150), 1e-6);
    }

    @Test
    void customBezierStillAppliesInTweenMode() {
        EntityTrack track = new EntityTrack("hero");
        track.addKeyframe(PropertyType.X, new Keyframe(0, 0, Easing.Type.LINEAR));
        Keyframe end = new Keyframe(100, 100, Easing.Type.CUSTOM, Easing.Interpolation.TWEEN);
        end.setBezierParams(0.25, 0.1, 0.25, 1.0);
        track.addKeyframe(PropertyType.X, end);

        double mid = track.getValueAt(PropertyType.X, 50);
        assertTrue(mid > 50.0 && mid < 100.0);
    }

    @Test
    void interpolatesLargeSortedTracks() {
        EntityTrack track = new EntityTrack("hero");
        List<Keyframe> keyframes = new ArrayList<>();
        for (int i = 0; i < 4096; i++) {
            keyframes.add(new Keyframe(i * 10.0, i));
        }
        track.setKeyframes(PropertyType.X, keyframes);

        assertEquals(2345.6, track.getValueAt(PropertyType.X, 23456.0), 1e-6);
        assertEquals(4095.0, track.getValueAt(PropertyType.X, 50000.0), 1e-6);
    }

    @Test
    void nanSampleTimeFallsBackToPropertyDefault() {
        EntityTrack track = new EntityTrack("hero");
        track.addKeyframe(PropertyType.X, new Keyframe(0.0, 25.0));
        track.addKeyframe(PropertyType.X, new Keyframe(100.0, 75.0));

        assertEquals(PropertyType.X.getDefaultValue(),
            track.getValueAt(PropertyType.X, Double.NaN), 1e-6);
    }
}
