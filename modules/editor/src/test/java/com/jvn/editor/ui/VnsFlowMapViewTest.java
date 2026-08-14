package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VnsFlowMapViewTest {

  @Test
  void summarySeparatesUnreachableLabelsFromUndefinedTargets() {
    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze("""
        @label start
        [goto ending]

        @label unreachable_middle
        narrator "This block is not reachable."

        @label ending
        [goto missing]
        """, null);

    VnsFlowMapView.FlowSummary summary = VnsFlowMapView.summarizeAnalysis(analysis);
    assertEquals(3, summary.labels());
    assertEquals(1, summary.unreachable());
    assertEquals(1, summary.undefined());
    assertTrue(summary.displayText().contains("1 unreachable"));
    assertTrue(summary.displayText().contains("1 undefined"));
  }
}
