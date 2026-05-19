package com.jvn.editor.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EditorSidebarPanelTest {

  @Test
  void diagnosticsPanelUsesGenericNameAndVersion111() {
    assertEquals("Diagnostics", EditorSidebarPanel.VNS_DIAGNOSTICS.displayName());
    assertEquals("1.1.1", EditorSidebarPanel.VNS_DIAGNOSTICS.version());
  }

  @Test
  void helpPanelVersionReflectsGuideTreeUpgrade() {
    assertEquals("1.2.2", EditorSidebarPanel.HELP.version());
    assertEquals("v1.2.2", EditorSidebarPanel.HELP.versionBadge());
  }

  @Test
  void scriptEditorIsPopOutOnly() {
    assertFalse(EditorSidebarPanel.SCRIPT_EDITOR.supportsDocking());
    assertEquals(EditorPanelPlacement.HIDDEN, EditorSidebarPanel.SCRIPT_EDITOR.defaultPlacement());
  }
}
