package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import org.junit.jupiter.api.Test;

class KeyframeEditorNudgeTest {

    @Test
    void timeNudgeStepSupportsFineAndLargeAdjustments() {
        assertEquals(10.0, KeyframeEditor.resolveTimeNudgeStep(false));
        assertEquals(50.0, KeyframeEditor.resolveTimeNudgeStep(true));
    }

    @Test
    void valueNudgeStepMatchesPropertyScale() {
        assertEquals(1.0, KeyframeEditor.resolveValueNudgeStep(PropertyType.X, false));
        assertEquals(10.0, KeyframeEditor.resolveValueNudgeStep(PropertyType.X, true));
        assertEquals(0.01, KeyframeEditor.resolveValueNudgeStep(PropertyType.ALPHA, false));
        assertEquals(0.05, KeyframeEditor.resolveValueNudgeStep(PropertyType.PIVOT_Y, true));
        assertEquals(15.0, KeyframeEditor.resolveValueNudgeStep(PropertyType.ROTATION, true));
        assertEquals(0.10, KeyframeEditor.resolveValueNudgeStep(PropertyType.CAMERA_ZOOM, true));
    }

    @Test
    void curveNudgeStepSupportsFineAndLargeAdjustments() {
        assertEquals(0.01, KeyframeEditor.resolveCurveNudgeStep(false));
        assertEquals(0.05, KeyframeEditor.resolveCurveNudgeStep(true));
    }

    @Test
    void editableCurveSpecPreservesCustomBezierValues() {
        EasingSpec custom = EasingSpec.cubicBezier(0.2, 0.8, 0.4, 1.0);

        EasingSpec editable = KeyframeEditor.toEditableCurveSpec(custom);

        assertEquals(Easing.Type.CUSTOM, editable.getType());
        assertEquals(custom, editable);
    }

    @Test
    void editableCurveSpecApproximatesBuiltInEasingAsCustomBezier() {
        EasingSpec source = EasingSpec.of(Easing.Type.EASE_IN_OUT_SINE);
        EasingSpec editable = KeyframeEditor.toEditableCurveSpec(source);

        assertEquals(Easing.Type.CUSTOM, editable.getType());
        for (double t : new double[]{0.1, 0.25, 0.5, 0.75, 0.9}) {
            double expected = Easing.apply(source, t);
            double actual = Easing.apply(editable, t);
            assertTrue(Math.abs(expected - actual) < 0.08,
                "approximation drifted too far at t=" + t + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
