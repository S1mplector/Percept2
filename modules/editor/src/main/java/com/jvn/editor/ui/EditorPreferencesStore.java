package com.jvn.editor.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class EditorPreferencesStore {
  static final String KEY_CODE_EDITOR_FONT_SIZE = "codeEditorFontSize";
  static final String KEY_EDITOR_MAX_FPS = "editorMaxFps";
  static final String KEY_EDITOR_THEME = "editor.theme";
  static final String KEY_SHOW_WELCOME_ON_STARTUP = "showWelcomeOnStartup";
  static final String KEY_LOAD_SIDEBAR_EXTENSIONS_ON_DEMAND = "loadSidebarExtensionsOnDemand";
  static final String KEY_AUTO_SAVE_BEFORE_RUN = "autoSaveBeforeRun";
  static final String KEY_EDITOR_RUNTIME_PERF_HUD = "editor.runtimePerfHud";
  static final String KEY_EDITOR_CONFIRM_RUN_PROJECT = "editor.confirmRunProject";
  static final String KEY_DEFAULT_TEXT_EDITOR = "defaultTextEditor";
  static final String KEY_CUSTOM_TEXT_EDITOR_COMMAND = "customTextEditorCommand";
  static final String KEY_LAUNCHER_THEME = "launcher.theme";
  static final String KEY_LAUNCHER_RESTORE_LAST_PROJECT = "launcher.restoreLastProject";
  static final String KEY_LAUNCHER_LAST_PROJECT_PATH = "launcher.lastProjectPath";
  static final String KEY_LAUNCHER_KEEP_OPEN_AFTER_EDITOR_LAUNCH =
      "launcher.keepOpenAfterEditorLaunch";
  static final String KEY_LAUNCHER_CONFIRM_OPEN_EDITOR = "launcher.confirmOpenEditor";
  static final String KEY_LAUNCHER_CONFIRM_RUN_PROJECT = "launcher.confirmRunProject";
  static final String KEY_LAUNCHER_RUNTIME_PERF_HUD = "launcher.runtimePerfHud";
  static final String KEY_GRADLE_SKIP_TESTS_ON_RUN = "gradle.skipTestsOnRun";
  static final String KEY_PANEL_PREFIX = "panel.";
  static final String KEY_PANEL_SUFFIX = ".placement";
  static final String KEY_CHOOSER_SUFFIX = ".chooserVisible";
  static final String KEY_STATUS_BAR_PREFIX = "statusBar.";
  static final String KEY_STATUS_BAR_SUFFIX = ".visible";
  static final String KEY_CENTER_DIVIDER_LEFT = "centerDividerLeft";
  static final String KEY_CENTER_DIVIDER_RIGHT = "centerDividerRight";
  static final String KEY_ACTIVE_LEFT_TAB = "activeLeftTab";
  static final String KEY_ACTIVE_RIGHT_TAB = "activeRightTab";

  private final Path preferencesFile;

  public EditorPreferencesStore() {
    this(defaultPreferencesPath());
  }

  public EditorPreferencesStore(Path preferencesFile) {
    this.preferencesFile = preferencesFile;
  }

  public Path preferencesFile() {
    return preferencesFile;
  }

  public EditorPreferences load() {
    Properties props = new Properties();
    if (preferencesFile != null && Files.isRegularFile(preferencesFile)) {
      try (InputStream in = Files.newInputStream(preferencesFile)) {
        props.load(in);
      } catch (IOException | IllegalArgumentException ignored) {
        // reason: preferences are user-local state; malformed files must not block editor startup
      }
    }
    return fromProperties(props);
  }

  public void save(EditorPreferences preferences) throws IOException {
    if (preferencesFile == null || preferences == null) return;
    Path parent = preferencesFile.getParent();
    if (parent != null) Files.createDirectories(parent);
    Properties props = toProperties(preferences);
    try (OutputStream out = Files.newOutputStream(preferencesFile)) {
      props.store(out, "JVN Editor Preferences");
    }
  }

  static Path defaultPreferencesPath() {
    return Path.of(
        System.getProperty("user.home", "."),
        ".jvn-editor",
        "editor-preferences.properties");
  }

  static Properties toProperties(EditorPreferences preferences) {
    Properties props = new Properties();
    if (preferences == null) return props;
    props.setProperty(
        KEY_CODE_EDITOR_FONT_SIZE,
        Integer.toString(preferences.getCodeEditorFontSize()));
    props.setProperty(
        KEY_EDITOR_MAX_FPS,
        Integer.toString(preferences.getEditorMaxFps()));
    props.setProperty(
        KEY_EDITOR_THEME,
        preferences.getEditorTheme());
    props.setProperty(
        KEY_SHOW_WELCOME_ON_STARTUP,
        Boolean.toString(preferences.isShowWelcomeOnStartup()));
    props.setProperty(
        KEY_LOAD_SIDEBAR_EXTENSIONS_ON_DEMAND,
        Boolean.toString(preferences.isLoadSidebarExtensionsOnDemand()));
    props.setProperty(
        KEY_AUTO_SAVE_BEFORE_RUN,
        Boolean.toString(preferences.isAutoSaveBeforeRun()));
    props.setProperty(
        KEY_EDITOR_RUNTIME_PERF_HUD,
        Boolean.toString(preferences.isEditorRuntimePerfHud()));
    props.setProperty(
        KEY_EDITOR_CONFIRM_RUN_PROJECT,
        Boolean.toString(preferences.isEditorConfirmRunProject()));
    props.setProperty(
        KEY_DEFAULT_TEXT_EDITOR,
        preferences.getDefaultTextEditor());
    props.setProperty(
        KEY_CUSTOM_TEXT_EDITOR_COMMAND,
        preferences.getCustomTextEditorCommand());
    props.setProperty(
        KEY_LAUNCHER_THEME,
        preferences.getLauncherTheme());
    props.setProperty(
        KEY_LAUNCHER_RESTORE_LAST_PROJECT,
        Boolean.toString(preferences.isLauncherRestoreLastProject()));
    props.setProperty(
        KEY_LAUNCHER_LAST_PROJECT_PATH,
        preferences.getLauncherLastProjectPath());
    props.setProperty(
        KEY_LAUNCHER_KEEP_OPEN_AFTER_EDITOR_LAUNCH,
        Boolean.toString(preferences.isLauncherKeepOpenAfterEditorLaunch()));
    props.setProperty(
        KEY_LAUNCHER_CONFIRM_OPEN_EDITOR,
        Boolean.toString(preferences.isLauncherConfirmOpenEditor()));
    props.setProperty(
        KEY_LAUNCHER_CONFIRM_RUN_PROJECT,
        Boolean.toString(preferences.isLauncherConfirmRunProject()));
    props.setProperty(
        KEY_LAUNCHER_RUNTIME_PERF_HUD,
        Boolean.toString(preferences.isLauncherRuntimePerfHud()));
    props.setProperty(
        KEY_GRADLE_SKIP_TESTS_ON_RUN,
        Boolean.toString(preferences.isGradleSkipTestsOnRun()));
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      props.setProperty(
          KEY_PANEL_PREFIX + panel.key() + KEY_PANEL_SUFFIX,
          preferences.getPlacement(panel).name());
      props.setProperty(
          KEY_PANEL_PREFIX + panel.key() + KEY_CHOOSER_SUFFIX,
          Boolean.toString(preferences.isVisibleInChooser(panel)));
    }
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      props.setProperty(
          KEY_STATUS_BAR_PREFIX + segment.key() + KEY_STATUS_BAR_SUFFIX,
          Boolean.toString(preferences.isStatusBarSegmentVisible(segment)));
    }
    props.setProperty(KEY_CENTER_DIVIDER_LEFT, Double.toString(preferences.getCenterDividerLeft()));
    props.setProperty(KEY_CENTER_DIVIDER_RIGHT, Double.toString(preferences.getCenterDividerRight()));
    props.setProperty(KEY_ACTIVE_LEFT_TAB, preferences.getActiveLeftTab());
    props.setProperty(KEY_ACTIVE_RIGHT_TAB, preferences.getActiveRightTab());
    return props;
  }

  static EditorPreferences fromProperties(Properties props) {
    EditorPreferences preferences = EditorPreferences.defaults();
    if (props == null) return preferences;
    preferences.setCodeEditorFontSize(parseInt(
        props.getProperty(KEY_CODE_EDITOR_FONT_SIZE),
        EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE));
    preferences.setEditorMaxFps(parseInt(
        props.getProperty(KEY_EDITOR_MAX_FPS),
        EditorPreferences.DEFAULT_EDITOR_MAX_FPS));
    preferences.setEditorTheme(props.getProperty(
        KEY_EDITOR_THEME,
        EditorPreferences.LAUNCHER_THEME_DARK));
    preferences.setShowWelcomeOnStartup(Boolean.parseBoolean(
        props.getProperty(KEY_SHOW_WELCOME_ON_STARTUP, "true")));
    preferences.setLoadSidebarExtensionsOnDemand(parseBoolean(
        props.getProperty(KEY_LOAD_SIDEBAR_EXTENSIONS_ON_DEMAND), true));
    preferences.setAutoSaveBeforeRun(parseBoolean(
        props.getProperty(KEY_AUTO_SAVE_BEFORE_RUN), true));
    preferences.setEditorRuntimePerfHud(parseBoolean(
        props.getProperty(KEY_EDITOR_RUNTIME_PERF_HUD), true));
    preferences.setEditorConfirmRunProject(parseBoolean(
        props.getProperty(KEY_EDITOR_CONFIRM_RUN_PROJECT), false));
    preferences.setDefaultTextEditor(props.getProperty(
        KEY_DEFAULT_TEXT_EDITOR,
        EditorPreferences.TEXT_EDITOR_JVN));
    preferences.setCustomTextEditorCommand(props.getProperty(
        KEY_CUSTOM_TEXT_EDITOR_COMMAND,
        ""));
    preferences.setLauncherTheme(props.getProperty(
        KEY_LAUNCHER_THEME,
        EditorPreferences.LAUNCHER_THEME_DARK));
    preferences.setLauncherRestoreLastProject(parseBoolean(
        props.getProperty(KEY_LAUNCHER_RESTORE_LAST_PROJECT), true));
    preferences.setLauncherLastProjectPath(props.getProperty(
        KEY_LAUNCHER_LAST_PROJECT_PATH,
        ""));
    preferences.setLauncherKeepOpenAfterEditorLaunch(parseBoolean(
        props.getProperty(KEY_LAUNCHER_KEEP_OPEN_AFTER_EDITOR_LAUNCH), false));
    preferences.setLauncherConfirmOpenEditor(parseBoolean(
        props.getProperty(KEY_LAUNCHER_CONFIRM_OPEN_EDITOR), false));
    preferences.setLauncherConfirmRunProject(parseBoolean(
        props.getProperty(KEY_LAUNCHER_CONFIRM_RUN_PROJECT), false));
    preferences.setLauncherRuntimePerfHud(parseBoolean(
        props.getProperty(KEY_LAUNCHER_RUNTIME_PERF_HUD), true));
    preferences.setGradleSkipTestsOnRun(parseBoolean(
        props.getProperty(KEY_GRADLE_SKIP_TESTS_ON_RUN), true));
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      String key = KEY_PANEL_PREFIX + panel.key() + KEY_PANEL_SUFFIX;
      EditorPanelPlacement placement = parsePlacement(props.getProperty(key), panel.defaultPlacement());
      preferences.setPlacement(panel, placement);
      String chooserKey = KEY_PANEL_PREFIX + panel.key() + KEY_CHOOSER_SUFFIX;
      preferences.setVisibleInChooser(
          panel,
          parseBoolean(props.getProperty(chooserKey), panel.defaultVisibleInChooser()));
    }
    for (EditorStatusBarSegment segment : EditorStatusBarSegment.values()) {
      String key = KEY_STATUS_BAR_PREFIX + segment.key() + KEY_STATUS_BAR_SUFFIX;
      preferences.setStatusBarSegmentVisible(
          segment,
          parseBoolean(props.getProperty(key), segment.defaultVisible()));
    }
    preferences.setCenterDividerLeft(parseDouble(props.getProperty(KEY_CENTER_DIVIDER_LEFT), 0.22));
    preferences.setCenterDividerRight(parseDouble(props.getProperty(KEY_CENTER_DIVIDER_RIGHT), 0.78));
    preferences.setActiveLeftTab(props.getProperty(KEY_ACTIVE_LEFT_TAB, ""));
    preferences.setActiveRightTab(props.getProperty(KEY_ACTIVE_RIGHT_TAB, ""));
    return preferences;
  }

  private static int parseInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return fallback;
    }
  }

  private static double parseDouble(String raw, double fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return fallback;
    }
  }

  private static EditorPanelPlacement parsePlacement(
      String raw, EditorPanelPlacement fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return EditorPanelPlacement.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
            // reason: invalid argument from untrusted input; caller handles absent result
      return fallback;
    }
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    String normalized = raw.trim().toLowerCase();
    if ("true".equals(normalized)) return true;
    if ("false".equals(normalized)) return false;
    return fallback;
  }
}
