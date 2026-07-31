package com.jvn.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditorPulsePolicyTest {
  @Test
  void rendersOnlyForAVisibleFocusedEditorWindow() {
    assertTrue(EditorPulsePolicy.shouldRenderActivePreview(true, false, true));
    assertFalse(EditorPulsePolicy.shouldRenderActivePreview(false, false, true));
    assertFalse(EditorPulsePolicy.shouldRenderActivePreview(true, true, true));
    assertFalse(EditorPulsePolicy.shouldRenderActivePreview(true, false, false));
  }
}
