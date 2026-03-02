package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TimelineDiagnosticDslTest {

    @Test
    void flagsUnknownActionAndUnknownKeyWithLineNumbers() {
        String code = """
            timeline {
              moev \"hero\" {
                x: 100
                dur: 200
              }
              move \"hero\" {
                xx: 10
                y: 20
                dur: 200
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);

        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("Unknown timeline action")
                && m.hasLine()
                && m.line() == 2));

        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("Unknown key 'xx'")
                && m.hasLine()
                && m.line() == 7));
    }

    @Test
    void flagsRangeAndEasingProblems() {
        String code = """
            timeline {
              fade \"hero\" {
                alpha: 1.5
                dur: -10
                easing: ease_in_out_snee
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);

        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("Alpha") && m.hasLine() && m.line() == 3));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("Duration") && m.hasLine() && m.line() == 4));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("Unknown easing") && m.hasLine() && m.line() == 5));
    }

    @Test
    void acceptsCubicBezierEasing() {
        String code = """
            timeline {
              move \"hero\" {
                x: 320
                y: 400
                dur: 500
                easing: cubic_bezier(0.25, 0.1, 0.25, 1.0)
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);

        assertFalse(messages.stream().anyMatch(m ->
            m.description().contains("Unknown easing") || m.description().contains("cubic_bezier")));
    }
}
