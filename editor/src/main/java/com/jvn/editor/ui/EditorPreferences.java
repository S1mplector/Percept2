package com.jvn.editor.ui;

import java.util.EnumMap;
import java.util.Map;

public final class EditorPreferences {
  public static final int DEFAULT_CODE_EDITOR_FONT_SIZE = 13;
  public static final int MIN_CODE_EDITOR_FONT_SIZE = 10;
  public static final int MAX_CODE_EDITOR_FONT_SIZE = 28;

  private int codeEditorFontSize;
  private boolean showWelcomeOnStartup;
  private final EnumMap<EditorSidebarPanel, EditorPanelPlacement> panelPlacements =
      new EnumMap<>(EditorSidebarPanel.class);

  public EditorPreferences() {
    this(DEFAULT_CODE_EDITOR_FONT_SIZE, true, EditorSidebarPanel.defaultPlacements());
  }

  public EditorPreferences(
      int codeEditorFontSize,
      boolean showWelcomeOnStartup,
      Map<EditorSidebarPanel, EditorPanelPlacement> placements) {
    this.codeEditorFontSize =
        clampCodeEditorFontSize(codeEditorFontSize);
    this.showWelcomeOnStartup = showWelcomeOnStartup;
    panelPlacements.putAll(EditorSidebarPanel.defaultPlacements());
    if (placements != null) {
      for (Map.Entry<EditorSidebarPanel, EditorPanelPlacement> entry : placements.entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) continue;
        panelPlacements.put(entry.getKey(), entry.getValue());
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

  public EditorPreferences copy() {
    return new EditorPreferences(codeEditorFontSize, showWelcomeOnStartup, panelPlacements);
  }

  public static int clampCodeEditorFontSize(int value) {
    return Math.max(MIN_CODE_EDITOR_FONT_SIZE, Math.min(MAX_CODE_EDITOR_FONT_SIZE, value));
  }
}
