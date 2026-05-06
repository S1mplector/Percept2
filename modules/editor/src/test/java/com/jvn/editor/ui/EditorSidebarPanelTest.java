package com.jvn.editor.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorSidebarPanelTest {

  @Test
  void diagnosticsPanelUsesGenericNameAndVersion111() {
    assertEquals("Diagnostics", EditorSidebarPanel.VNS_DIAGNOSTICS.displayName());
    assertEquals("1.1.1", EditorSidebarPanel.VNS_DIAGNOSTICS.version());
  }
}
