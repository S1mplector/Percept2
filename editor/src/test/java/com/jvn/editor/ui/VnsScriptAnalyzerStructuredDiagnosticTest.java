package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VnsScriptAnalyzerStructuredDiagnosticTest {

  @Test
  void exposesAnalyzerIssuesAsSharedLanguageDiagnostics() {
    String source = """
        @scenario demo
        @label start
        [jump missing_label]
        """;

    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(source, null);

    assertFalse(analysis.languageDiagnostics("scripts/story/demo.vns").isEmpty());
    LanguageDiagnostic diagnostic = analysis.languageDiagnostics("scripts/story/demo.vns").stream()
        .filter(d -> "undefined_label".equals(d.code()))
        .findFirst()
        .orElseThrow();
    assertEquals("vns", diagnostic.language());
    assertEquals("scripts/story/demo.vns", diagnostic.sourceName());
    assertEquals(LanguageDiagnostic.Severity.ERROR, diagnostic.severity());
    assertTrue(diagnostic.message().contains("missing_label"));
    assertTrue(diagnostic.line() >= 0);
    assertTrue(diagnostic.column() >= 0);
    assertTrue(diagnostic.startOffset() < diagnostic.endOffset());
  }
}
