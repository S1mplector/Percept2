package com.jvn.editor.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnsDiagnosticsViewTest {

  @Test
  void lineStartOffsetFindsRequestedLine() {
    String source = "alpha\nbeta\ncharlie";
    assertEquals(0, VnsDiagnosticsView.lineStartOffset(source, 0));
    assertEquals(6, VnsDiagnosticsView.lineStartOffset(source, 1));
    assertEquals(11, VnsDiagnosticsView.lineStartOffset(source, 2));
  }

  @Test
  void lineStartOffsetFallsBackToSourceEndForOutOfRangeLine() {
    String source = "a\nb";
    assertEquals(source.length(), VnsDiagnosticsView.lineStartOffset(source, 5));
  }

  @Test
  void computeOneBasedColumnUsesOffsetWithinLine() {
    String source = "alpha\nbetaX\ncharlie";
    int offsetOfX = source.indexOf('X');
    int column = VnsDiagnosticsView.computeOneBasedColumn(source, 1, offsetOfX);
    assertEquals(5, column);
  }

  @Test
  void computeOneBasedColumnNeverDropsBelowOne() {
    String source = "alpha\nbeta\ncharlie";
    int column = VnsDiagnosticsView.computeOneBasedColumn(source, 2, 0);
    assertEquals(1, column);
  }

  @Test
  void buildSearchTextContainsKindMessageAndLocationTokens() {
    VnsScriptAnalyzer.Diagnostic issue = VnsScriptAnalyzer.Diagnostic.error(
        "undefined_label",
        "Undefined label: intro",
        8,
        13,
        1,
        "intro",
        null,
        -1
    );

    String haystack = VnsDiagnosticsView.buildSearchText(issue, 2, 4, "Error", "Undefined label");
    assertTrue(haystack.contains("undefined_label"));
    assertTrue(haystack.contains("undefined label: intro"));
    assertTrue(haystack.contains("line 2"));
    assertTrue(haystack.contains("column 4"));
    assertTrue(haystack.contains("l2:4"));
  }

  @Test
  void buildSearchTextSupportsGenericDiagnostics() {
    VnsDiagnosticsView.Diagnostic issue = VnsDiagnosticsView.Diagnostic.error(
        "jes_parse_error",
        "Expected entity block",
        0,
        1,
        0
    );

    String haystack = VnsDiagnosticsView.buildSearchText(issue, 1, 1, "Error", "Jes parse error", "scene \"intro\"");
    assertTrue(haystack.contains("jes_parse_error"));
    assertTrue(haystack.contains("expected entity block"));
    assertTrue(haystack.contains("scene \"intro\""));
    assertTrue(haystack.contains("l1:1"));
  }

  @Test
  void sourceLineReturnsRequestedLineWithoutNewline() {
    String source = "label start\nsay alice Hello\njump missing";

    assertEquals("say alice Hello", VnsDiagnosticsView.sourceLine(source, 1));
  }

  @Test
  void buildSourcePreviewPlacesCaretAtOneBasedColumn() {
    VnsDiagnosticsView.SourcePreview preview = VnsDiagnosticsView.buildSourcePreview(
        "label start\njump missing_label",
        1,
        6
    );

    assertEquals("jump missing_label", preview.sourceLine());
    assertEquals("     ^", preview.caretLine());
  }
}
