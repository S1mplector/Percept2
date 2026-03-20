package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
