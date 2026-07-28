package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RunConsoleViewLogFormatTest {

  @Test
  void recognizesRuntimeLogbackOutputAsEngineOutput() {
    String line =
        "12:34:56.789 INFO  [JavaFX Application Thread] com.jvn.fx.FxLauncher"
            + " - Runtime viewport -> window=1280x720, logical=1920x1080";

    assertTrue(RunConsoleView.isEngineOutputLine(line));
    assertEquals("run-console-line-info", RunConsoleView.classifyLine(line));
  }

  @Test
  void recognizesLegacyBracketedEngineOutput() {
    assertTrue(RunConsoleView.isEngineOutputLine("[Engine] Scene ready"));
  }

  @Test
  void doesNotTreatGradleTaskOutputAsRuntimeOutput() {
    assertFalse(RunConsoleView.isEngineOutputLine("> Task :runtime:run"));
  }
}
