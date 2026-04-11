package com.jvn.editor.ui;

import java.util.EnumMap;
import java.util.Map;

public final class EditorPreferences {
  public static final int DEFAULT_CODE_EDITOR_FONT_SIZE = 13;
  public static final int MIN_CODE_EDITOR_FONT_SIZE = 10;
  public static final int MAX_CODE_EDITOR_FONT_SIZE = 28;
  public static final String TEXT_EDITOR_JVN = "jvn";
  public static final String TEXT_EDITOR_SYSTEM = "system";
  public static final String TEXT_EDITOR_CUSTOM = "custom";
  public static final String LAUNCHER_THEME_DARK = "dark";
  public static final String LAUNCHER_THEME_LIGHT = "light";

  private int codeEditorFontSize;
  private boolean showWelcomeOnStartup;
  private boolean loadSidebarExtensionsOnDemand;
  private String defaultTextEditor;
  private String customTextEditorCommand;
  private String launcherTheme;
  private boolean launcherRestoreLastProject;
  private String launcherLastProjectPath;
  private final EnumMap<EditorSidebarPanel, EditorPanelPlacement> panelPlacements =
      new EnumMap<>(EditorSidebarPanel.class);
  private final EnumMap<EditorSidebarPanel, Boolean> chooserVisibility =
      new EnumMap<>(EditorSidebarPanel.class);

  public EditorPreferences() {
    this(
        DEFAULT_CODE_EDITOR_FONT_SIZE,
        true,
        true,
        TEXT_EDITOR_JVN,
        "",
        LAUNCHER_THEME_DARK,
        true,
        "",
        EditorSidebarPanel.defaultPlacements(),
        EditorSidebarPanel.defaultChooserVisibility());
  }

  public EditorPreferences(
      int codeEditorFontSize,
      boolean showWelcomeOnStartup,
      boolean loadSidebarExtensionsOnDemand,
      String defaultTextEditor,
      String customTextEditorCommand,
      String launcherTheme,
      boolean launcherRestoreLastProject,
      String launcherLastProjectPath,
      Map<EditorSidebarPanel, EditorPanelPlacement> placements,
      Map<EditorSidebarPanel, Boolean> chooserVisibility) {
    this.codeEditorFontSize =
        clampCodeEditorFontSize(codeEditorFontSize);
    this.showWelcomeOnStartup = showWelcomeOnStartup;
    this.loadSidebarExtensionsOnDemand = loadSidebarExtensionsOnDemand;
    this.defaultTextEditor = normalizeTextEditor(defaultTextEditor);
    this.customTextEditorCommand = cleanText(customTextEditorCommand);
    this.launcherTheme = normalizeLauncherTheme(launcherTheme);
    this.launcherRestoreLastProject = launcherRestoreLastProject;
    this.launcherLastProjectPath = cleanText(launcherLastProjectPath);
    panelPlacements.putAll(EditorSidebarPanel.defaultPlacements());
    this.chooserVisibility.putAll(EditorSidebarPanel.defaultChooserVisibility());
    if (placements != null) {
      for (Map.Entry<EditorSidebarPanel, EditorPanelPlacement> entry : placements.entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) continue;
        panelPlacements.put(entry.getKey(), entry.getValue());
      }
    }
    if (chooserVisibility != null) {
      for (Map.Entry<EditorSidebarPanel, Boolean> entry : chooserVisibility.entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) continue;
        this.chooserVisibility.put(entry.getKey(), entry.getValue());
      }
    }
  }

  public static EditorPreferences defaults() {
    return new EditorPreferences();
  }

  public int getCodeEditorFontSize() {
    return codeEditorFontSize;
  }

  public void setCodeEditorFontSize(int codeEditorFontSize) {
    this.codeEditorFontSize = clampCodeEditorFontSize(codeEditorFontSize);
  }

  public boolean isShowWelcomeOnStartup() {
    return showWelcomeOnStartup;
  }

  public void setShowWelcomeOnStartup(boolean showWelcomeOnStartup) {
    this.showWelcomeOnStartup = showWelcomeOnStartup;
  }

  public boolean isLoadSidebarExtensionsOnDemand() {
    return loadSidebarExtensionsOnDemand;
  }

  public void setLoadSidebarExtensionsOnDemand(boolean loadSidebarExtensionsOnDemand) {
    this.loadSidebarExtensionsOnDemand = loadSidebarExtensionsOnDemand;
  }

  public String getDefaultTextEditor() {
    return defaultTextEditor;
  }

  public void setDefaultTextEditor(String defaultTextEditor) {
    this.defaultTextEditor = normalizeTextEditor(defaultTextEditor);
  }

  public String getCustomTextEditorCommand() {
    return customTextEditorCommand;
  }

  public void setCustomTextEditorCommand(String customTextEditorCommand) {
    this.customTextEditorCommand = cleanText(customTextEditorCommand);
  }

  public String getLauncherTheme() {
    return launcherTheme;
  }

  public void setLauncherTheme(String launcherTheme) {
    this.launcherTheme = normalizeLauncherTheme(launcherTheme);
  }

  public boolean isLauncherRestoreLastProject() {
    return launcherRestoreLastProject;
  }

  public void setLauncherRestoreLastProject(boolean launcherRestoreLastProject) {
    this.launcherRestoreLastProject = launcherRestoreLastProject;
  }

  public String getLauncherLastProjectPath() {
    return launcherLastProjectPath;
  }

  public void setLauncherLastProjectPath(String launcherLastProjectPath) {
    this.launcherLastProjectPath = cleanText(launcherLastProjectPath);
  }

  public EditorPanelPlacement getPlacement(EditorSidebarPanel panel) {
    if (panel == null) return EditorPanelPlacement.HIDDEN;
    return panelPlacements.getOrDefault(panel, panel.defaultPlacement());
  }

  public void setPlacement(EditorSidebarPanel panel, EditorPanelPlacement placement) {
    if (panel == null || placement == null) return;
    panelPlacements.put(panel, placement);
  }

  public EnumMap<EditorSidebarPanel, EditorPanelPlacement> copyPlacements() {
    return new EnumMap<>(panelPlacements);
  }

  public boolean isVisibleInChooser(EditorSidebarPanel panel) {
    if (panel == null) return false;
    return chooserVisibility.getOrDefault(panel, panel.defaultVisibleInChooser());
  }

  public void setVisibleInChooser(EditorSidebarPanel panel, boolean visible) {
    if (panel == null) return;
    chooserVisibility.put(panel, visible);
  }

  public EnumMap<EditorSidebarPanel, Boolean> copyChooserVisibility() {
    return new EnumMap<>(chooserVisibility);
  }

  public EditorPreferences copy() {
    return new EditorPreferences(
        codeEditorFontSize,
        showWelcomeOnStartup,
        loadSidebarExtensionsOnDemand,
        defaultTextEditor,
        customTextEditorCommand,
        launcherTheme,
        launcherRestoreLastProject,
        launcherLastProjectPath,
        panelPlacements,
        chooserVisibility);
  }

  public static int clampCodeEditorFontSize(int value) {
    return Math.max(MIN_CODE_EDITOR_FONT_SIZE, Math.min(MAX_CODE_EDITOR_FONT_SIZE, value));
  }

  public static String normalizeTextEditor(String value) {
    String normalized = cleanText(value).toLowerCase();
    if (TEXT_EDITOR_SYSTEM.equals(normalized)) return TEXT_EDITOR_SYSTEM;
    if (TEXT_EDITOR_CUSTOM.equals(normalized)) return TEXT_EDITOR_CUSTOM;
    return TEXT_EDITOR_JVN;
  }

  public static String normalizeLauncherTheme(String value) {
    String normalized = cleanText(value).toLowerCase();
    return LAUNCHER_THEME_LIGHT.equals(normalized) ? LAUNCHER_THEME_LIGHT : LAUNCHER_THEME_DARK;
  }

  private static String cleanText(String value) {
    return value == null ? "" : value.trim();
  }
}
