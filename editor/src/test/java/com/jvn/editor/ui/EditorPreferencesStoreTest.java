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
    assertTrue(preferences.isLoadSidebarExtensionsOnDemand());
    assertEquals(EditorPreferences.TEXT_EDITOR_JVN, preferences.getDefaultTextEditor());
    assertEquals("", preferences.getCustomTextEditorCommand());
    assertEquals(EditorPreferences.LAUNCHER_THEME_DARK, preferences.getLauncherTheme());
    assertTrue(preferences.isLauncherRestoreLastProject());
    assertEquals("", preferences.getLauncherLastProjectPath());
    assertEquals(EditorPanelPlacement.LEFT, preferences.getPlacement(EditorSidebarPanel.PROJECT));
    assertEquals(EditorPanelPlacement.HIDDEN, preferences.getPlacement(EditorSidebarPanel.HELP));
    assertTrue(preferences.isVisibleInChooser(EditorSidebarPanel.PROJECT));
    assertTrue(preferences.isVisibleInChooser(EditorSidebarPanel.HELP));
    assertFalse(preferences.isVisibleInChooser(EditorSidebarPanel.INSPECTOR));
    assertFalse(preferences.isVisibleInChooser(EditorSidebarPanel.LABEL_FLOW));
    assertFalse(preferences.isVisibleInChooser(EditorSidebarPanel.ASSETS));
    assertFalse(preferences.isVisibleInChooser(EditorSidebarPanel.IMAGE_ATTRIBUTES));
  }

  @Test
  void saveAndLoadRoundTripsSidebarPlacements() throws Exception {
    Path prefsFile = tempDir.resolve("editor-preferences.properties");
    EditorPreferencesStore store = new EditorPreferencesStore(prefsFile);
    EditorPreferences preferences = EditorPreferences.defaults();
    preferences.setCodeEditorFontSize(18);
    preferences.setShowWelcomeOnStartup(false);
    preferences.setLoadSidebarExtensionsOnDemand(false);
    preferences.setDefaultTextEditor(EditorPreferences.TEXT_EDITOR_CUSTOM);
    preferences.setCustomTextEditorCommand("code --reuse-window {file}");
    preferences.setLauncherTheme(EditorPreferences.LAUNCHER_THEME_LIGHT);
    preferences.setLauncherRestoreLastProject(false);
    preferences.setLauncherLastProjectPath("/tmp/project");
    preferences.setPlacement(EditorSidebarPanel.HELP, EditorPanelPlacement.RIGHT);
    preferences.setPlacement(EditorSidebarPanel.TIMELINE, EditorPanelPlacement.LEFT);
    preferences.setVisibleInChooser(EditorSidebarPanel.HELP, false);

    store.save(preferences);
    EditorPreferences loaded = store.load();

    assertEquals(18, loaded.getCodeEditorFontSize());
    assertFalse(loaded.isShowWelcomeOnStartup());
    assertFalse(loaded.isLoadSidebarExtensionsOnDemand());
    assertEquals(EditorPreferences.TEXT_EDITOR_CUSTOM, loaded.getDefaultTextEditor());
    assertEquals("code --reuse-window {file}", loaded.getCustomTextEditorCommand());
    assertEquals(EditorPreferences.LAUNCHER_THEME_LIGHT, loaded.getLauncherTheme());
    assertFalse(loaded.isLauncherRestoreLastProject());
    assertEquals("/tmp/project", loaded.getLauncherLastProjectPath());
    assertEquals(EditorPanelPlacement.RIGHT, loaded.getPlacement(EditorSidebarPanel.HELP));
    assertEquals(EditorPanelPlacement.LEFT, loaded.getPlacement(EditorSidebarPanel.TIMELINE));
    assertFalse(loaded.isVisibleInChooser(EditorSidebarPanel.HELP));
  }

  @Test
  void invalidValuesFallBackToSafeDefaults() throws Exception {
    Path prefsFile = tempDir.resolve("editor-preferences.properties");
    Properties props = new Properties();
    props.setProperty(EditorPreferencesStore.KEY_CODE_EDITOR_FONT_SIZE, "invalid");
    props.setProperty(EditorPreferencesStore.KEY_SHOW_WELCOME_ON_STARTUP, "false");
    props.setProperty(EditorPreferencesStore.KEY_LOAD_SIDEBAR_EXTENSIONS_ON_DEMAND, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_DEFAULT_TEXT_EDITOR, "unknown");
    props.setProperty(EditorPreferencesStore.KEY_LAUNCHER_THEME, "unknown");
    props.setProperty(EditorPreferencesStore.KEY_LAUNCHER_RESTORE_LAST_PROJECT, "notabool");
    props.setProperty(
        EditorPreferencesStore.KEY_PANEL_PREFIX + EditorSidebarPanel.PROJECT.key()
            + EditorPreferencesStore.KEY_PANEL_SUFFIX,
        "nowhere");
    props.setProperty(
        EditorPreferencesStore.KEY_PANEL_PREFIX + EditorSidebarPanel.PROJECT.key()
            + EditorPreferencesStore.KEY_CHOOSER_SUFFIX,
        "notabool");
    try (var out = Files.newOutputStream(prefsFile)) {
      props.store(out, "test");
    }

    EditorPreferencesStore store = new EditorPreferencesStore(prefsFile);
    EditorPreferences loaded = store.load();

    assertEquals(EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE, loaded.getCodeEditorFontSize());
    assertFalse(loaded.isShowWelcomeOnStartup());
    assertTrue(loaded.isLoadSidebarExtensionsOnDemand());
    assertEquals(EditorPreferences.TEXT_EDITOR_JVN, loaded.getDefaultTextEditor());
    assertEquals(EditorPreferences.LAUNCHER_THEME_DARK, loaded.getLauncherTheme());
    assertTrue(loaded.isLauncherRestoreLastProject());
    assertEquals(EditorPanelPlacement.LEFT, loaded.getPlacement(EditorSidebarPanel.PROJECT));
    assertTrue(loaded.isVisibleInChooser(EditorSidebarPanel.PROJECT));
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
