package com.jvn.core.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EasingInterpolationTest {

    @Test
    void quintAndCircCurvesStayWithinUnitRange() {
        Easing.Type[] types = {
            Easing.Type.EASE_IN_QUINT,
            Easing.Type.EASE_OUT_QUINT,
            Easing.Type.EASE_IN_OUT_QUINT,
            Easing.Type.EASE_IN_CIRC,
            Easing.Type.EASE_OUT_CIRC,
            Easing.Type.EASE_IN_OUT_CIRC
        };
        for (Easing.Type type : types) {
            assertEquals(0.0, Easing.apply(type, 0.0), 1e-9, type + " @0");
            assertEquals(1.0, Easing.apply(type, 1.0), 1e-9, type + " @1");
            double mid = Easing.apply(type, 0.5);
            assertTrue(mid >= -1e-9 && mid <= 1.0 + 1e-9, type + " midpoint should be within [0,1]");
        }
    }

    @Test
    void interpolationModesHoldAndStepBehaveAsExpected() {
        assertEquals(0.0,
            Easing.applyInterpolation(Easing.Type.LINEAR, Easing.Interpolation.HOLD, 0.5), 1e-9);
        assertEquals(1.0,
            Easing.applyInterpolation(Easing.Type.LINEAR, Easing.Interpolation.HOLD, 1.0), 1e-9);

        assertEquals(0.0,
            Easing.applyInterpolation(Easing.Type.LINEAR, Easing.Interpolation.STEP, 0.0), 1e-9);
        assertEquals(1.0,
            Easing.applyInterpolation(Easing.Type.LINEAR, Easing.Interpolation.STEP, 0.0001), 1e-9);
    }

    @Test
    void timelineTrackRespectsInterpolationAndCustomBezier() {
        TimelineData.Track track = new TimelineData.Track("hero");
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(
            100, 100, Easing.Type.LINEAR, Easing.Interpolation.HOLD));
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(
            200, 200, Easing.Type.LINEAR, Easing.Interpolation.STEP));
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(
            300, 300, Easing.Type.CUSTOM, Easing.Interpolation.TWEEN,
            new double[]{0.25, 0.1, 0.25, 1.0}));

        assertEquals(0.0, track.getValueAt(TimelineData.Property.X, 50), 1e-6);
        assertEquals(100.0, track.getValueAt(TimelineData.Property.X, 100), 1e-6);
        assertEquals(200.0, track.getValueAt(TimelineData.Property.X, 150), 1e-6);

        double bezierMid = track.getValueAt(TimelineData.Property.X, 250);
        assertTrue(bezierMid > 250.0 && bezierMid < 300.0);
    }

    @Test
    void springAndNamedCurvesRespectEndpoints() {
        double[] springParams = {220.0, 24.0, 1.0, 0.0};
        assertEquals(0.0, Easing.apply(Easing.Type.SPRING, springParams, 0.0), 1e-9);
        assertEquals(1.0, Easing.apply(Easing.Type.SPRING, springParams, 1.0), 1e-9);
        assertEquals(0.0, Easing.apply(Easing.Type.DAMPED_SPRING, null, 0.0), 1e-9);
        assertEquals(1.0, Easing.apply(Easing.Type.DAMPED_SPRING, null, 1.0), 1e-9);
        assertEquals(0.0, Easing.apply(Easing.Type.HERO_POP, 0.0), 1e-9);
        assertEquals(1.0, Easing.apply(Easing.Type.HERO_POP, 1.0), 1e-9);

        double max = 0.0;
        for (int i = 0; i <= 100; i++) {
            max = Math.max(max, Easing.apply(Easing.Type.HERO_POP, i / 100.0));
        }
        assertTrue(max > 1.0, "hero_pop should overshoot to feel springy");
    }
}
