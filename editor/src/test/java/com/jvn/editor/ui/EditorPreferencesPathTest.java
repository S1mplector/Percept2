package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EditorPreferencesPathTest {

  @Test
  void preservesLauncherLastProjectPathWhitespace() {
    EditorPreferences preferences = EditorPreferences.defaults();
    String path = "/tmp/Was_I_Write (JVN) ";

    preferences.setLauncherLastProjectPath(path);

    assertEquals(path, preferences.getLauncherLastProjectPath());
  }

  @Test
  void collapsesBlankLauncherLastProjectPath() {
    EditorPreferences preferences = EditorPreferences.defaults();

    preferences.setLauncherLastProjectPath("   ");

    assertEquals("", preferences.getLauncherLastProjectPath());
  }
}
