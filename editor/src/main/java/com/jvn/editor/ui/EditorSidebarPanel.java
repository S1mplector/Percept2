package com.jvn.editor.ui;

import java.util.EnumMap;
import java.util.Map;

public enum EditorSidebarPanel {
  PROJECT("project", "Project", EditorPanelPlacement.LEFT, true),
  TIMELINE("timeline", "Timeline", EditorPanelPlacement.HIDDEN, true),
  INSPECTOR("inspector", "Inspector", EditorPanelPlacement.HIDDEN, false),
  VNS_DIAGNOSTICS("vns_diagnostics", "VNS Diagnostics", EditorPanelPlacement.HIDDEN, true),
  LABEL_FLOW("label_flow", "Label Flow", EditorPanelPlacement.HIDDEN, false),
  ASSETS("assets", "Assets", EditorPanelPlacement.HIDDEN, false),
  LAYOUT_LAUNCHER("layout_launcher", "Layout Launcher", EditorPanelPlacement.HIDDEN, true),
  PHONE_ASSETS("phone_assets", "Phone Assets", EditorPanelPlacement.HIDDEN, true),
  LAYERED_IMAGES("layered_images", "Layered Image Visualizer", EditorPanelPlacement.HIDDEN, true),
  IMAGE_ATTRIBUTES("image_attributes", "Image Attributes Tool", EditorPanelPlacement.HIDDEN, false),
  IMAGE_TINT("image_tint", "Image Tint Tool", EditorPanelPlacement.HIDDEN, true),
  MENU_FLOW("menu_flow", "Menu Flow", EditorPanelPlacement.HIDDEN, true),
  VERSION_CONTROL("version_control", "Version Control", EditorPanelPlacement.HIDDEN, true),
  HELP("help", "Help", EditorPanelPlacement.HIDDEN, true),
  PUPPETEER_LAUNCHER("puppeteer_launcher", "Puppeteer Launcher", EditorPanelPlacement.HIDDEN, true),
  AUDIO_SYNTH("audio_synth", "Audio Synth Controls", EditorPanelPlacement.HIDDEN, false),
  SCRIPT_EDITOR("script_editor", "Script Editor", EditorPanelPlacement.HIDDEN, true);

  private final String key;
  private final String displayName;
  private final EditorPanelPlacement defaultPlacement;
  private final boolean defaultVisibleInChooser;

  EditorSidebarPanel(
      String key,
      String displayName,
      EditorPanelPlacement defaultPlacement,
      boolean defaultVisibleInChooser) {
    this.key = key;
    this.displayName = displayName;
    this.defaultPlacement = defaultPlacement;
    this.defaultVisibleInChooser = defaultVisibleInChooser;
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

  public boolean defaultVisibleInChooser() {
    return defaultVisibleInChooser;
  }

  public static Map<EditorSidebarPanel, EditorPanelPlacement> defaultPlacements() {
    Map<EditorSidebarPanel, EditorPanelPlacement> placements =
        new EnumMap<>(EditorSidebarPanel.class);
    for (EditorSidebarPanel panel : values()) {
      placements.put(panel, panel.defaultPlacement);
    }
    return placements;
  }

  public static Map<EditorSidebarPanel, Boolean> defaultChooserVisibility() {
    Map<EditorSidebarPanel, Boolean> visibility =
        new EnumMap<>(EditorSidebarPanel.class);
    for (EditorSidebarPanel panel : values()) {
      visibility.put(panel, panel.defaultVisibleInChooser);
    }
    return visibility;
  }

  public static EditorSidebarPanel fromKey(String key) {
    if (key == null || key.isBlank()) return null;
    for (EditorSidebarPanel panel : values()) {
      if (panel.key.equalsIgnoreCase(key.trim())) return panel;
    }
    return null;
  }
}
