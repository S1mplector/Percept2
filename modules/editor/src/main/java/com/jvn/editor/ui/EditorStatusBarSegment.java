package com.jvn.editor.ui;

public enum EditorStatusBarSegment {
  PRODUCT("product", "Product", "Editor product name and home menu."),
  BRANCH("branch", "Git Branch", "Current Git branch."),
  GIT_STATE("gitState", "Git State", "Tracked working-tree status."),
  MESSAGE("message", "Status Message", "Current editor status message."),
  PROJECT("project", "Project", "Open project name."),
  ACTIVE_FILE("activeFile", "Active File", "Current file tab."),
  POSITION("position", "Cursor Line", "Current cursor line."),
  FILE_META("fileMeta", "File Metadata", "File size, modified time, and writable state."),
  WORKSPACE("workspace", "Workspace Tabs", "Open tab count and editor command state."),
  DIRTY("dirty", "Save State", "Saved or unsaved tab state."),
  DIAGNOSTICS("diagnostics", "Diagnostics", "Current file errors and warnings."),
  ENCODING("encoding", "Encoding", "Active text encoding."),
  LINE_ENDING("lineEnding", "Line Ending", "Active line-ending mode."),
  MEMORY("memory", "Heap Memory", "Current editor heap memory usage."),
  JAVA("java", "Java Runtime", "Java runtime feature version."),
  THEME("theme", "Theme", "Current editor theme."),
  VERSION("version", "Version", "JVN editor version label.");

  private final String key;
  private final String displayName;
  private final String description;

  EditorStatusBarSegment(String key, String displayName, String description) {
    this.key = key;
    this.displayName = displayName;
    this.description = description;
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }

  public String description() {
    return description;
  }

  public boolean defaultVisible() {
    return true;
  }
}
