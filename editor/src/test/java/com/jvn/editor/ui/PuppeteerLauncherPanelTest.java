package com.jvn.editor.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PuppeteerLauncherPanelTest {

  @Test
  void resolveSnapshotClampsLineToLastSourceLine() {
    String source = """
        @label start
        [background school]
        [show alice center neutral]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, 999);
    assertEquals(source.split("\n", -1).length - 1, snapshot.atLine);
  }

  @Test
  void resolveSnapshotClampsNegativeLineToZero() {
    String source = """
        @label start
        [background school]
        """;

    PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(source, -12);
    assertEquals(0, snapshot.atLine);
    assertEquals("start", snapshot.currentLabel);
  }
}
