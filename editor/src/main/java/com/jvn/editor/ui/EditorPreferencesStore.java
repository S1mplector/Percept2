package com.jvn.editor.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class EditorPreferencesStore {
  static final String KEY_CODE_EDITOR_FONT_SIZE = "codeEditorFontSize";
  static final String KEY_SHOW_WELCOME_ON_STARTUP = "showWelcomeOnStartup";
  static final String KEY_PANEL_PREFIX = "panel.";
  static final String KEY_PANEL_SUFFIX = ".placement";
  static final String KEY_CHOOSER_SUFFIX = ".chooserVisible";

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
      } catch (IOException ignored) {
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
        KEY_SHOW_WELCOME_ON_STARTUP,
        Boolean.toString(preferences.isShowWelcomeOnStartup()));
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      props.setProperty(
          KEY_PANEL_PREFIX + panel.key() + KEY_PANEL_SUFFIX,
          preferences.getPlacement(panel).name());
      props.setProperty(
          KEY_PANEL_PREFIX + panel.key() + KEY_CHOOSER_SUFFIX,
          Boolean.toString(preferences.isVisibleInChooser(panel)));
    }
    return props;
  }

  static EditorPreferences fromProperties(Properties props) {
    EditorPreferences preferences = EditorPreferences.defaults();
    if (props == null) return preferences;
    preferences.setCodeEditorFontSize(parseInt(
        props.getProperty(KEY_CODE_EDITOR_FONT_SIZE),
        EditorPreferences.DEFAULT_CODE_EDITOR_FONT_SIZE));
    preferences.setShowWelcomeOnStartup(Boolean.parseBoolean(
        props.getProperty(KEY_SHOW_WELCOME_ON_STARTUP, "true")));
    for (EditorSidebarPanel panel : EditorSidebarPanel.values()) {
      String key = KEY_PANEL_PREFIX + panel.key() + KEY_PANEL_SUFFIX;
      EditorPanelPlacement placement = parsePlacement(props.getProperty(key), panel.defaultPlacement());
      preferences.setPlacement(panel, placement);
      String chooserKey = KEY_PANEL_PREFIX + panel.key() + KEY_CHOOSER_SUFFIX;
      preferences.setVisibleInChooser(
          panel,
          parseBoolean(props.getProperty(chooserKey), panel.defaultVisibleInChooser()));
    }
    return preferences;
  }

  private static int parseInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static EditorPanelPlacement parsePlacement(
      String raw, EditorPanelPlacement fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return EditorPanelPlacement.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
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
