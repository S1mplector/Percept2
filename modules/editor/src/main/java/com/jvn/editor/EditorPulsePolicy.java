package com.jvn.editor;

/** Shared decisions for work driven by the editor's JavaFX pulse. */
final class EditorPulsePolicy {
  private EditorPulsePolicy() {}

  static boolean shouldRenderActivePreview(
      boolean windowShowing,
      boolean windowIconified,
      boolean windowFocused) {
    return windowShowing && !windowIconified && windowFocused;
  }
}
