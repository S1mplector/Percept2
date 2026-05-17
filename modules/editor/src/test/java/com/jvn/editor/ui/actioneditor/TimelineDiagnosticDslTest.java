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

    @Test
    void acceptsSpringAndNamedCurveEasing() {
        String code = """
            timeline {
              move "hero" {
                x: 320
                dur: 500
                easing: spring(220, 24, 1.0, 0)
              }
              wait 500
              move "hero" {
                x: 480
                dur: 400
                easing: hero_pop
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);

        assertFalse(messages.stream().anyMatch(m -> m.description().contains("Unknown easing")));
    }

    @Test
    void acceptsMultiPointCurveEasing() {
        String code = """
            timeline {
              move "hero" {
                x: 320
                dur: 500
                easing: curve(0.20, 0.10, 0.48, 0.84, 0.76, 1.06)
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);

        assertFalse(messages.stream().anyMatch(m ->
            m.description().contains("Unknown easing") || m.description().contains("curve requires")));
    }

    @Test
    void flagsUnknownInterpolation() {
        String code = """
            timeline {
              move \"hero\" {
                x: 100
                dur: 200
                interp: holdd
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("Unknown interpolation")
                && m.hasLine()
                && m.line() == 5));
    }

    @Test
    void validatesPlayAudioProperties() {
        String code = """
            timeline {
              playAudio {
                channel: ambience
                volume: 1.2
                loop: maybe
                fadeInMs: -15
              }
              playAudio {
                channel: ""
                volume: loud
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);

        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("Unknown audio channel 'ambience'")
                && m.hasLine()
                && m.line() == 3));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("volume 1.2 is out of [0,1] range")
                && m.hasLine()
                && m.line() == 4));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("loop value 'maybe' is not a standard boolean token")
                && m.hasLine()
                && m.line() == 5));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("fadeInMs must be >= 0")
                && m.hasLine()
                && m.line() == 6));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("channel cannot be empty")
                && m.hasLine()
                && m.line() == 9));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("volume must be numeric")
                && m.hasLine()
                && m.line() == 10));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("playAudio is missing an audio path")
                && m.hasLine()
                && m.line() == 2));
    }

    @Test
    void flagsNoOpPropertyAndVisibilityActions() {
        String code = """
            timeline {
              property "hero" {
                key: ""
                value: loud
              }
              visible "hero" {
                visible: maybe
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);

        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("property action is missing key")
                && m.hasLine()
                && m.line() == 2));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("property value must be numeric")
                && m.hasLine()
                && m.line() == 2));
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("visible value 'maybe'")
                && m.hasLine()
                && m.line() == 6));
    }

    @Test
    void acceptsLayeredExpressionKeys() {
        String code = """
            timeline {
              expression "hero" {
                value: "angry"
                path: "assets/chars/hero/body.png | assets/chars/hero/face_angry.png"
                layers: "body=assets/chars/hero/body.png | face=assets/chars/hero/face_angry.png"
              }
            }
            """;

        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(code);

        assertTrue(messages.stream().noneMatch(m ->
            m.description().contains("Unknown key")
                && (m.description().contains("layers") || m.description().contains("path"))));
    }

    @Test
    void validatesBrightnessAction() {
        String good = """
            timeline {
              brightness "hero" {
                value: 0.6
                dur: 400
              }
            }
            """;
        assertFalse(TimelineDiagnostic.diagnoseDsl(good).stream().anyMatch(m ->
            m.severity() == TimelineDiagnostic.Severity.ERROR));

        String bad = """
            timeline {
              brightness "hero" {
                value: dark
              }
            }
            """;
        List<TimelineDiagnostic.Message> messages = TimelineDiagnostic.diagnoseDsl(bad);
        assertTrue(messages.stream().anyMatch(m ->
            m.description().contains("value must be numeric") && m.hasLine() && m.line() == 3));
    }
}
