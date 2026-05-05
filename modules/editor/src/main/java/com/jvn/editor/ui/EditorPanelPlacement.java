package com.jvn.editor.ui;

public enum EditorPanelPlacement {
  HIDDEN("Hidden"),
  LEFT("Left Sidebar"),
  RIGHT("Right Sidebar");

  private final String displayName;

  EditorPanelPlacement(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
