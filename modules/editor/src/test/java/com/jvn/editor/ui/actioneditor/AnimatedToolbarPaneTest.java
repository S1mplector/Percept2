package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class AnimatedToolbarPaneTest {

    @Test
    void packWidthRowsBackfillsEarlierRowsBeforeCreatingNewOnes() {
        List<List<Integer>> rows = AnimatedToolbarPane.packWidthRows(
            List.of(70.0, 70.0, 30.0, 30.0),
            110.0,
            10.0
        );

        assertEquals(List.of(List.of(0, 2), List.of(1, 3)), rows);
    }

    @Test
    void packWidthRowsHandlesEmptyInput() {
        assertEquals(List.of(), AnimatedToolbarPane.packWidthRows(List.of(), 200.0, 10.0));
    }
}
