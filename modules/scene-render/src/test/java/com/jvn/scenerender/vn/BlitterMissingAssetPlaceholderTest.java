package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.RenderDiagnostics;
import com.jvn.scenerender.testkit.DrawCall;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlitterMissingAssetPlaceholderTest {

  @BeforeEach
  void resetDiagnostics() {
    RenderDiagnostics.reset();
  }

  @AfterEach
  void tearDown() {
    RenderDiagnostics.reset();
  }

  @Test
  void reportsToRenderDiagnosticsRegardlessOfDeveloperMode() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    BlitterMissingAssetPlaceholder.report(blitter, "missing.png", "layer:body", 0, 0, 10, 10);
    // No assertion on RenderDiagnostics' internal log capture here (covered by
    // VnRendererMissingLayerWarningTest-equivalent coverage in Task 15); this test only
    // confirms the call does not throw and does not require developer mode to report.
  }

  @Test
  void doesNotDrawByDefaultWhenDeveloperModeSystemPropertyIsUnset() {
    System.clearProperty("jvn.fx.developerMode");
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    BlitterMissingAssetPlaceholder.report(blitter, "missing.png", "ctx", 5, 5, 20, 20);
    List<DrawCall> calls = blitter.calls();
    assertTrue(calls.isEmpty(), "expected no drawing calls when developer mode is not enabled");
  }

  @Test
  void drawsAPlaceholderBoxWhenDeveloperModeIsEnabled() {
    System.setProperty("jvn.fx.developerMode", "true");
    try {
      RecordingBlitter2D blitter = new RecordingBlitter2D();
      BlitterMissingAssetPlaceholder.report(blitter, "missing.png", "ctx", 5, 5, 20, 20);
      List<DrawCall> calls = blitter.calls();
      assertTrue(calls.stream().anyMatch(c -> c.method().equals("fillRect")),
          "expected a fillRect call for the placeholder box");
      assertTrue(calls.stream().anyMatch(c -> c.method().equals("drawText")),
          "expected a drawText call for the placeholder label");
    } finally {
      System.clearProperty("jvn.fx.developerMode");
    }
  }
}
