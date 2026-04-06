package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EasingCurveEditorTest {

    @Test
    void resolveCurveInsertXMovesNewPointsIntoAnOpenGap() {
        double[] seeded = new double[]{0.25, 0.25, 0.50, 0.50, 0.75, 0.75};

        double insertNearMiddle = EasingCurveEditor.resolveCurveInsertX(seeded, 0.50);
        double insertNearQuarter = EasingCurveEditor.resolveCurveInsertX(seeded, 0.25);

        assertTrue(insertNearMiddle > 0.5009 && insertNearMiddle < 0.75,
            "expected insertion to move into the next open gap, got " + insertNearMiddle);
        assertTrue(insertNearQuarter > 0.2509 && insertNearQuarter < 0.50,
            "expected insertion to avoid stacking onto an existing point, got " + insertNearQuarter);
    }
}
