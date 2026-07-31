package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RenderGraphCaptureTest {
  @Test
  void keepsTheLatestCompletePulseGraph() {
    RenderGraphCapture capture = new RenderGraphCapture();
    capture.beginSession("Run Editor", true);
    capture.accept("unrelated startup output");
    capture.accept("PULSE: 41 [18ms:23ms]");
    capture.accept("T34 :NGGroup [d]");
    capture.accept("  NGRegion [c]");
    capture.accept("");
    capture.accept("PULSE: 42 [19ms:24ms]");
    capture.accept("T34 :NGGroup [d]");
    capture.accept("  NGCanvas [d]");
    capture.endSession();

    RenderGraphCapture.Snapshot snapshot = capture.snapshot();
    assertFalse(snapshot.processRunning());
    assertEquals(2, snapshot.capturedGraphs());
    assertTrue(snapshot.graph().startsWith("PULSE: 42"));
    assertTrue(snapshot.graph().contains("NGCanvas"));
    assertFalse(snapshot.graph().contains("NGRegion"));
  }

  @Test
  void disabledSessionsDoNotReplaceThePreviousGraph() {
    RenderGraphCapture capture = new RenderGraphCapture();
    capture.beginSession("Run Editor", true);
    capture.accept("PULSE: 1 [18ms:20ms]");
    capture.accept("NGGroup");
    capture.endSession();

    capture.beginSession("Build All", false);
    capture.accept("PULSE: 2 [18ms:20ms]");
    capture.accept("NGRegion");
    capture.endSession();

    assertTrue(capture.snapshot().graph().contains("NGGroup"));
    assertFalse(capture.snapshot().graph().contains("NGRegion"));
  }

  @Test
  void consumesCompactPulseNoiseWithoutTreatingItAsAGraph() {
    RenderGraphCapture capture = new RenderGraphCapture();
    capture.beginSession("Run Editor", true);

    assertTrue(capture.accept("[4 16ms:5ms][5 16ms:4ms]"));
    assertFalse(capture.accept("ordinary application output"));
    assertEquals("", capture.snapshot().graph());
  }

  @Test
  void boundsLargeGraphsAndCanClearThem() {
    RenderGraphCapture capture = new RenderGraphCapture();
    capture.beginSession("Run Game", true);
    capture.accept("PULSE: 7 [25ms:40ms]");
    capture.accept("x".repeat(RenderGraphCapture.MAX_GRAPH_CHARS * 2));
    capture.endSession();

    assertTrue(capture.snapshot().graph().length() <= RenderGraphCapture.MAX_GRAPH_CHARS);
    assertTrue(capture.snapshot().graph().contains("truncated"));

    capture.clear();
    assertEquals("", capture.snapshot().graph());
    assertEquals(0, capture.snapshot().capturedGraphs());
  }
}
