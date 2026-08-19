package com.jvn.editor.ui.actioneditor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpressionPreviewPanelFilterTest {

    @Test
    void blankQueryReturnsAllNamesInOriginalOrder() {
        List<String> names = List.of("neutral", "happy", "sad_closed_eyes");
        assertEquals(names, ExpressionPreviewPanel.filterExpressionNames(names, ""));
        assertEquals(names, ExpressionPreviewPanel.filterExpressionNames(names, null));
    }

    @Test
    void queryFiltersCaseInsensitiveSubstringMatch() {
        List<String> names = List.of("neutral", "happy", "sad_closed_eyes", "Surprised");
        assertEquals(List.of("sad_closed_eyes", "Surprised"),
            ExpressionPreviewPanel.filterExpressionNames(names, "s"));
        assertEquals(List.of("Surprised"),
            ExpressionPreviewPanel.filterExpressionNames(names, "surp"));
    }

    @Test
    void queryWithNoMatchesReturnsEmptyList() {
        List<String> names = List.of("neutral", "happy");
        assertEquals(List.of(), ExpressionPreviewPanel.filterExpressionNames(names, "zzz"));
    }
}
