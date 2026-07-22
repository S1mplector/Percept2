package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TimelinePanelQoLTest {

    @Test
    void normalizeNavigationTimesSortsAndDeduplicatesNearMatches() {
        List<Double> normalized = TimelinePanel.normalizeNavigationTimes(
            List.of(300.0, 100.0, 100.0004, 200.0, Double.NaN)
        );

        assertEquals(List.of(100.0, 200.0, 300.0), normalized);
    }

    @Test
    void computeFocusWindowPadsSinglePointIntoUsableRange() {
        double[] window = TimelinePanel.computeFocusWindow(250.0, 250.0, 1000.0);

        assertArrayEquals(new double[]{170.0, 330.0}, window, 0.0001);
    }

    @Test
    void computeFocusWindowPadsSpanAndClampsToDuration() {
        double[] window = TimelinePanel.computeFocusWindow(900.0, 980.0, 1000.0);

        assertArrayEquals(new double[]{820.0, 1000.0}, window, 0.0001);
    }

    @Test
    void expressionTargetMatchesLayeredCharacterEntities() {
        assertTrue(TimelinePanel.expressionTargetMatchesEntity(
            "lavender", "lavender_idle_mouth_neutral"));
        assertTrue(TimelinePanel.expressionTargetMatchesEntity(
            "lavender", "lavender.mouth"));
        assertTrue(TimelinePanel.expressionTargetMatchesEntity(
            "lavender", "lavender:mouth"));
        assertTrue(TimelinePanel.expressionTargetMatchesEntity(
            "lavender", "lavender"));
        assertFalse(TimelinePanel.expressionTargetMatchesEntity(
            "lavender", "lavenderish_mouth"));
    }
}
