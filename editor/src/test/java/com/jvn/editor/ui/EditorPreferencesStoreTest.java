package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorPreferencesStoreTest {

  @TempDir
  Path tempDir;

  @Test
  void loadDefaultsWhenPreferencesFileDoesNotExist() {
    EditorPreferencesStore store =
        new EditorPreferencesStore(tempDir.resolve("editor-preferences.properties"));

    EditorPreferences preferences = store.load();

    assertEquals(EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE, preferences.getCodeEditorFontSize());
    assertTrue(preferences.isShowWelcomeOnStartup());
    assertEquals(EditorPanelPlacement.LEFT, preferences.getPlacement(EditorSidebarPanel.PROJECT));
    assertEquals(EditorPanelPlacement.HIDDEN, preferences.getPlacement(EditorSidebarPanel.HELP));
  }

  @Test
  void saveAndLoadRoundTripsSidebarPlacements() throws Exception {
    Path prefsFile = tempDir.resolve("editor-preferences.properties");
    EditorPreferencesStore store = new EditorPreferencesStore(prefsFile);
    EditorPreferences preferences = EditorPreferences.defaults();
    preferences.setCodeEditorFontSize(18);
    preferences.setShowWelcomeOnStartup(false);
    preferences.setPlacement(EditorSidebarPanel.HELP, EditorPanelPlacement.RIGHT);
    preferences.setPlacement(EditorSidebarPanel.TIMELINE, EditorPanelPlacement.LEFT);

    store.save(preferences);
    EditorPreferences loaded = store.load();

    assertEquals(18, loaded.getCodeEditorFontSize());
    assertFalse(loaded.isShowWelcomeOnStartup());
    assertEquals(EditorPanelPlacement.RIGHT, loaded.getPlacement(EditorSidebarPanel.HELP));
    assertEquals(EditorPanelPlacement.LEFT, loaded.getPlacement(EditorSidebarPanel.TIMELINE));
  }

  @Test
  void invalidValuesFallBackToSafeDefaults() throws Exception {
    Path prefsFile = tempDir.resolve("editor-preferences.properties");
    Properties props = new Properties();
    props.setProperty(EditorPreferencesStore.KEY_CODE_EDITOR_FONT_SIZE, "invalid");
    props.setProperty(EditorPreferencesStore.KEY_SHOW_WELCOME_ON_STARTUP, "false");
    props.setProperty(
        EditorPreferencesStore.KEY_PANEL_PREFIX + EditorSidebarPanel.PROJECT.key()
            + EditorPreferencesStore.KEY_PANEL_SUFFIX,
        "nowhere");
    try (var out = Files.newOutputStream(prefsFile)) {
      props.store(out, "test");
    }

    EditorPreferencesStore store = new EditorPreferencesStore(prefsFile);
    EditorPreferences loaded = store.load();

    assertEquals(EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE, loaded.getCodeEditorFontSize());
    assertFalse(loaded.isShowWelcomeOnStartup());
    assertEquals(EditorPanelPlacement.LEFT, loaded.getPlacement(EditorSidebarPanel.PROJECT));
  }

  @Test
  void fontSizeIsClampedOnConstructionAndMutation() {
    EditorPreferences preferences = new EditorPreferences();

    preferences.setCodeEditorFontSize(999);
    assertEquals(EditorPreferences.MAX_CODE_EDITOR_FONT_SIZE, preferences.getCodeEditorFontSize());

    preferences.setCodeEditorFontSize(-10);
    assertEquals(EditorPreferences.MIN_CODE_EDITOR_FONT_SIZE, preferences.getCodeEditorFontSize());
  }
}
