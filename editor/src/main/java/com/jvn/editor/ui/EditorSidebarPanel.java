package com.jvn.editor.ui;

import java.util.EnumMap;
import java.util.Map;

public enum EditorSidebarPanel {
  PROJECT("project", "Project", EditorPanelPlacement.LEFT),
  TIMELINE("timeline", "Timeline", EditorPanelPlacement.HIDDEN),
  INSPECTOR("inspector", "Inspector", EditorPanelPlacement.HIDDEN),
  VNS_DIAGNOSTICS("vns_diagnostics", "VNS Diagnostics", EditorPanelPlacement.HIDDEN),
  LABEL_FLOW("label_flow", "Label Flow", EditorPanelPlacement.HIDDEN),
  ASSETS("assets", "Assets", EditorPanelPlacement.HIDDEN),
  LAYOUT_LAUNCHER("layout_launcher", "Layout Launcher", EditorPanelPlacement.HIDDEN),
  LAYERED_IMAGES("layered_images", "Layered Image Visualizer", EditorPanelPlacement.HIDDEN),
  IMAGE_ATTRIBUTES("image_attributes", "Image Attributes Tool", EditorPanelPlacement.HIDDEN),
  IMAGE_TINT("image_tint", "Image Tint Tool", EditorPanelPlacement.HIDDEN),
  MENU_FLOW("menu_flow", "Menu Flow", EditorPanelPlacement.HIDDEN),
  VERSION_CONTROL("version_control", "Version Control", EditorPanelPlacement.HIDDEN),
  HELP("help", "Help", EditorPanelPlacement.HIDDEN),
  PUPPETEER_LAUNCHER("puppeteer_launcher", "Puppeteer Launcher", EditorPanelPlacement.HIDDEN),
  AUDIO_SYNTH("audio_synth", "Audio Synth Controls", EditorPanelPlacement.HIDDEN),
  SCRIPT_EDITOR("script_editor", "Script Editor", EditorPanelPlacement.HIDDEN);

  private final String key;
  private final String displayName;
  private final EditorPanelPlacement defaultPlacement;

  EditorSidebarPanel(String key, String displayName, EditorPanelPlacement defaultPlacement) {
    this.key = key;
    this.displayName = displayName;
    this.defaultPlacement = defaultPlacement;
  }

  public String key() {
    return key;
  }

  public String displayName() {
    return displayName;
  }

  public EditorPanelPlacement defaultPlacement() {
    return defaultPlacement;
  }

  public static Map<EditorSidebarPanel, EditorPanelPlacement> defaultPlacements() {
    Map<EditorSidebarPanel, EditorPanelPlacement> placements =
        new EnumMap<>(EditorSidebarPanel.class);
    for (EditorSidebarPanel panel : values()) {
      placements.put(panel, panel.defaultPlacement);
    }
    return placements;
  }

  public static EditorSidebarPanel fromKey(String key) {
    if (key == null || key.isBlank()) return null;
    for (EditorSidebarPanel panel : values()) {
      if (panel.key.equalsIgnoreCase(key.trim())) return panel;
    }
    return null;
  }
}
