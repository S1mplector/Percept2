package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EasingCurveEditorTest {

    @Test
    void resolveCurveInsertXMovesNewPointsIntoAnOpenGap() {
        double[] seeded = new double[]{0.25, 0.25, 0.50, 0.50, 0.75, 0.75};

        double insertNearMiddle = EasingCurveEditor.resolveCurveInsertX(seeded, 0.50);
        double insertNearQuarter = EasingCurveEditor.resolveCurveInsertX(seeded, 0.25);

        assertTrue(insertNearMiddle > 0.25 && insertNearMiddle < 0.75
                && Math.abs(insertNearMiddle - 0.50) > 0.0009,
            "expected insertion to move into a real open gap, got " + insertNearMiddle);
        assertTrue(insertNearQuarter > 0.0 && insertNearQuarter < 0.50
                && Math.abs(insertNearQuarter - 0.25) > 0.0009,
            "expected insertion to avoid stacking onto an existing point, got " + insertNearQuarter);
    }
}
