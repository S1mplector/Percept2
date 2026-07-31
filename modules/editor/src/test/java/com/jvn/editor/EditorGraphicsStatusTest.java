package com.jvn.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.diagnostics.GraphicsPipeline;
import org.junit.jupiter.api.Test;

class EditorGraphicsStatusTest {
  @Test
  void reportsAnActiveHardwarePipeline() {
    EditorGraphicsStatus.Snapshot status = EditorGraphicsStatus.classify(
        true,
        GraphicsPipeline.Mode.HARDWARE,
        "Linux");

    assertTrue(status.hardwareAccelerated());
    assertTrue(status.chipText().contains("active"));
  }

  @Test
  void distinguishesAnExplicitSoftwareChoiceFromFallback() {
    EditorGraphicsStatus.Snapshot selected = EditorGraphicsStatus.classify(
        false,
        GraphicsPipeline.Mode.SOFTWARE,
        "Linux");
    EditorGraphicsStatus.Snapshot fallback = EditorGraphicsStatus.classify(
        false,
        GraphicsPipeline.Mode.AUTO,
        "Linux");

    assertFalse(selected.hardwareAccelerated());
    assertTrue(selected.chipText().contains("off"));
    assertTrue(fallback.chipText().contains("fallback"));
    assertTrue(fallback.tooltip().contains("glxinfo"));
  }
}
