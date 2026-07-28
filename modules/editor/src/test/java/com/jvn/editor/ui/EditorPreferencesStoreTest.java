package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
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
    assertEquals(EditorPreferences.LAUNCHER_THEME_DARK, preferences.getEditorTheme());
    assertTrue(preferences.isShowWelcomeOnStartup());
    assertTrue(preferences.isLoadSidebarExtensionsOnDemand());
    assertTrue(preferences.isAutoSaveBeforeRun());
    assertTrue(preferences.isEditorRuntimePerfHud());
    assertFalse(preferences.isEditorConfirmRunProject());
    assertEquals(EditorPreferences.TEXT_EDITOR_JVN, preferences.getDefaultTextEditor());
    assertEquals("", preferences.getCustomTextEditorCommand());
    assertEquals(EditorPreferences.LAUNCHER_THEME_DARK, preferences.getLauncherTheme());
    assertTrue(preferences.isLauncherRestoreLastProject());
    assertEquals("", preferences.getLauncherLastProjectPath());
    assertFalse(preferences.isLauncherKeepOpenAfterEditorLaunch());
    assertFalse(preferences.isLauncherConfirmOpenEditor());
    assertFalse(preferences.isLauncherConfirmRunProject());
    assertTrue(preferences.isLauncherRuntimePerfHud());
    assertTrue(preferences.isGradleSkipTestsOnRun());
    assertEquals("", preferences.getLastSeenWhatsNewVersion());
    assertEquals(EditorPanelPlacement.LEFT, preferences.getPlacement(EditorSidebarPanel.PROJECT));
    assertTrue(preferences.isVisibleInChooser(EditorSidebarPanel.PROJECT));
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
    preferences.setEditorTheme(EditorPreferences.LAUNCHER_THEME_LIGHT);
    preferences.setShowWelcomeOnStartup(false);
    preferences.setLoadSidebarExtensionsOnDemand(false);
    preferences.setAutoSaveBeforeRun(false);
    preferences.setEditorRuntimePerfHud(false);
    preferences.setEditorConfirmRunProject(true);
    preferences.setDefaultTextEditor(EditorPreferences.TEXT_EDITOR_CUSTOM);
    preferences.setCustomTextEditorCommand("code --reuse-window {file}");
    preferences.setLauncherTheme(EditorPreferences.LAUNCHER_THEME_LIGHT);
    preferences.setLauncherRestoreLastProject(false);
    preferences.setLauncherLastProjectPath("/tmp/project");
    preferences.setLauncherKeepOpenAfterEditorLaunch(false);
    preferences.setLauncherConfirmOpenEditor(true);
    preferences.setLauncherConfirmRunProject(true);
    preferences.setLauncherRuntimePerfHud(false);
    preferences.setGradleSkipTestsOnRun(false);
    preferences.setLastSeenWhatsNewVersion("v0.4.2");
    preferences.setPlacement(EditorSidebarPanel.TIMELINE, EditorPanelPlacement.LEFT);

    store.save(preferences);
    EditorPreferences loaded = store.load();

    assertEquals(18, loaded.getCodeEditorFontSize());
    assertEquals(EditorPreferences.LAUNCHER_THEME_LIGHT, loaded.getEditorTheme());
    assertFalse(loaded.isShowWelcomeOnStartup());
    assertFalse(loaded.isLoadSidebarExtensionsOnDemand());
    assertFalse(loaded.isAutoSaveBeforeRun());
    assertFalse(loaded.isEditorRuntimePerfHud());
    assertTrue(loaded.isEditorConfirmRunProject());
    assertEquals(EditorPreferences.TEXT_EDITOR_CUSTOM, loaded.getDefaultTextEditor());
    assertEquals("code --reuse-window {file}", loaded.getCustomTextEditorCommand());
    assertEquals(EditorPreferences.LAUNCHER_THEME_LIGHT, loaded.getLauncherTheme());
    assertFalse(loaded.isLauncherRestoreLastProject());
    assertEquals("/tmp/project", loaded.getLauncherLastProjectPath());
    assertFalse(loaded.isLauncherKeepOpenAfterEditorLaunch());
    assertTrue(loaded.isLauncherConfirmOpenEditor());
    assertTrue(loaded.isLauncherConfirmRunProject());
    assertFalse(loaded.isLauncherRuntimePerfHud());
    assertFalse(loaded.isGradleSkipTestsOnRun());
    assertEquals("v0.4.2", loaded.getLastSeenWhatsNewVersion());
    assertEquals(EditorPanelPlacement.LEFT, loaded.getPlacement(EditorSidebarPanel.TIMELINE));
  }

  @Test
  void invalidValuesFallBackToSafeDefaults() throws Exception {
    Path prefsFile = tempDir.resolve("editor-preferences.properties");
    Properties props = new Properties();
    props.setProperty(EditorPreferencesStore.KEY_CODE_EDITOR_FONT_SIZE, "invalid");
    props.setProperty(EditorPreferencesStore.KEY_EDITOR_THEME, "unknown");
    props.setProperty(EditorPreferencesStore.KEY_SHOW_WELCOME_ON_STARTUP, "false");
    props.setProperty(EditorPreferencesStore.KEY_LOAD_SIDEBAR_EXTENSIONS_ON_DEMAND, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_AUTO_SAVE_BEFORE_RUN, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_EDITOR_RUNTIME_PERF_HUD, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_EDITOR_CONFIRM_RUN_PROJECT, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_DEFAULT_TEXT_EDITOR, "unknown");
    props.setProperty(EditorPreferencesStore.KEY_LAUNCHER_THEME, "unknown");
    props.setProperty(EditorPreferencesStore.KEY_LAUNCHER_RESTORE_LAST_PROJECT, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_LAUNCHER_KEEP_OPEN_AFTER_EDITOR_LAUNCH, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_LAUNCHER_CONFIRM_OPEN_EDITOR, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_LAUNCHER_CONFIRM_RUN_PROJECT, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_LAUNCHER_RUNTIME_PERF_HUD, "notabool");
    props.setProperty(EditorPreferencesStore.KEY_GRADLE_SKIP_TESTS_ON_RUN, "notabool");
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
    assertEquals(EditorPreferences.LAUNCHER_THEME_DARK, loaded.getEditorTheme());
    assertFalse(loaded.isShowWelcomeOnStartup());
    assertTrue(loaded.isLoadSidebarExtensionsOnDemand());
    assertTrue(loaded.isAutoSaveBeforeRun());
    assertTrue(loaded.isEditorRuntimePerfHud());
    assertFalse(loaded.isEditorConfirmRunProject());
    assertEquals(EditorPreferences.TEXT_EDITOR_JVN, loaded.getDefaultTextEditor());
    assertEquals(EditorPreferences.LAUNCHER_THEME_DARK, loaded.getLauncherTheme());
    assertTrue(loaded.isLauncherRestoreLastProject());
    assertFalse(loaded.isLauncherKeepOpenAfterEditorLaunch());
    assertFalse(loaded.isLauncherConfirmOpenEditor());
    assertFalse(loaded.isLauncherConfirmRunProject());
    assertTrue(loaded.isLauncherRuntimePerfHud());
    assertTrue(loaded.isGradleSkipTestsOnRun());
    assertEquals("", loaded.getLastSeenWhatsNewVersion());
    assertEquals(EditorPanelPlacement.LEFT, loaded.getPlacement(EditorSidebarPanel.PROJECT));
    assertTrue(loaded.isVisibleInChooser(EditorSidebarPanel.PROJECT));
  }

  @Test
  void malformedPropertiesFileFallsBackToDefaults() throws Exception {
    Path prefsFile = tempDir.resolve("editor-preferences.properties");
    Files.writeString(
        prefsFile,
        EditorPreferencesStore.KEY_CUSTOM_TEXT_EDITOR_COMMAND + "=bad"
            + "\\"
            + "u12\n",
        StandardCharsets.ISO_8859_1);

    EditorPreferencesStore store = new EditorPreferencesStore(prefsFile);
    EditorPreferences loaded = store.load();

    assertEquals(EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE, loaded.getCodeEditorFontSize());
    assertEquals(EditorPreferences.TEXT_EDITOR_JVN, loaded.getDefaultTextEditor());
    assertEquals("", loaded.getCustomTextEditorCommand());
  }

  @Test
  void fontSizeIsClampedOnConstructionAndMutation() {
    EditorPreferences preferences = new EditorPreferences();

    preferences.setCodeEditorFontSize(999);
    assertEquals(EditorPreferences.MAX_CODE_EDITOR_FONT_SIZE, preferences.getCodeEditorFontSize());

    preferences.setCodeEditorFontSize(-10);
    assertEquals(EditorPreferences.MIN_CODE_EDITOR_FONT_SIZE, preferences.getCodeEditorFontSize());
  }

  @Test
  void popOutOnlyPanelsIgnoreDockedPlacements() {
    EditorPreferences preferences = EditorPreferences.defaults();

    preferences.setPlacement(EditorSidebarPanel.SCRIPT_EDITOR, EditorPanelPlacement.RIGHT);

    assertEquals(EditorPanelPlacement.HIDDEN, preferences.getPlacement(EditorSidebarPanel.SCRIPT_EDITOR));
  }
}
