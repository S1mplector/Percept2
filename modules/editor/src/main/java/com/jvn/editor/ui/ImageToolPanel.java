package com.jvn.editor.ui;

import java.io.File;

public interface ImageToolPanel {
  void setProjectRoot(File projectRoot);
  void refreshCatalog();
  void setOnToggleFullscreen(Runnable handler);
  void setFullscreenActive(boolean active);
}
