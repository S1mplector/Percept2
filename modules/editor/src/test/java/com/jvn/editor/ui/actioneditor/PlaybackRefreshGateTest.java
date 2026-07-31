package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlaybackRefreshGateTest {
    @Test
    void refreshesImmediatelyThenOnlyAfterTheInterval() {
        PlaybackRefreshGate gate = new PlaybackRefreshGate(100);

        assertTrue(gate.shouldRefresh(1_000));
        assertFalse(gate.shouldRefresh(1_099));
        assertTrue(gate.shouldRefresh(1_100));
    }

    @Test
    void resetAndClockRollbackAllowAnImmediateRefresh() {
        PlaybackRefreshGate gate = new PlaybackRefreshGate(100);

        assertTrue(gate.shouldRefresh(1_000));
        gate.reset();
        assertTrue(gate.shouldRefresh(1_001));
        assertTrue(gate.shouldRefresh(900));
    }

    @Test
    void rejectsNonPositiveIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new PlaybackRefreshGate(0));
    }
}
