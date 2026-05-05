package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JesScriptAnalyzerTest {

  @Test
  void validSceneReportsSymbolsWithoutErrors() {
    String source = """
        scene "Demo" {
          entity "hero" { component Sprite2D { image: "assets/characters/hero.png" } }
          timeline {
            label "intro"
            wait 100
          }
        }
        """;

    JesScriptAnalyzer.Analysis analysis = JesScriptAnalyzer.analyze(source);

    assertTrue(analysis.errors().isEmpty());
    assertEquals("hero", analysis.entityNames().get(0));
    assertEquals("intro", analysis.timelineLabelNames().get(0));
  }

  @Test
  void invalidSceneReportsStructuredParseDiagnostic() {
    String source = """
        scene "Demo" {
          entity "logo" {
            component Sprite2D { x: 1 bogus: 2 }
          }
        }
        """;

    JesScriptAnalyzer.Analysis analysis = JesScriptAnalyzer.analyze(source);

    assertFalse(analysis.errors().isEmpty());
    LanguageDiagnostic diagnostic = analysis.errors().get(0);
    assertEquals("jes", diagnostic.language());
    assertEquals(LanguageDiagnostic.Severity.ERROR, diagnostic.severity());
    assertEquals("jes_parse_error", diagnostic.code());
    assertTrue(diagnostic.message().contains("Unknown property 'bogus'"), diagnostic.message());
    assertTrue(diagnostic.line() >= 0);
    assertTrue(diagnostic.column() >= 0);
    assertTrue(diagnostic.startOffset() < diagnostic.endOffset());
  }

  @Test
  void timelineBlockModeDoesNotRequireSceneWrapper() {
    JesScriptAnalyzer.Analysis analysis = JesScriptAnalyzer.analyze(
        "move \"hero\" { x: 200 y: 120 dur: 300 }",
        JesScriptAnalyzer.Mode.TIMELINE_BLOCK);

    assertTrue(analysis.errors().isEmpty());
    assertEquals("jes_timeline_block", analysis.diagnostics().get(0).code());
    assertEquals(LanguageDiagnostic.Severity.INFO, analysis.diagnostics().get(0).severity());
  }
}
