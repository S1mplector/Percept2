package com.jvn.editor.ui;

import java.util.EnumMap;
import java.util.Map;

public enum EditorSidebarPanel {
  PROJECT("project", "Project", EditorPanelPlacement.LEFT, true, "icon-panel-project", null),
  TIMELINE("timeline", "Timeline", EditorPanelPlacement.HIDDEN, true, "icon-panel-timeline", "timeline_editor_orange_transparent.png"),
  INSPECTOR("inspector", "Inspector", EditorPanelPlacement.HIDDEN, false, "icon-panel-inspector", "project_inspector_orange_transparent.png"),
  VNS_DIAGNOSTICS("vns_diagnostics", "VNS Diagnostics", EditorPanelPlacement.HIDDEN, true, "icon-panel-diagnostics", "vns_diagnostics_orange_transparent.png"),
  LABEL_FLOW("label_flow", "Label Flow", EditorPanelPlacement.HIDDEN, false, "icon-panel-flow", "label_flow_inspector_orange_transparent.png"),
  ASSETS("assets", "Assets", EditorPanelPlacement.HIDDEN, false, "icon-panel-assets", null),
  LAYOUT_LAUNCHER("layout_launcher", "Layout Launcher", EditorPanelPlacement.HIDDEN, true, "icon-panel-layouts", "layout_editor_manager_orange_transparent.png"),
  PHONE_ASSETS("phone_assets", "Phone Assets", EditorPanelPlacement.HIDDEN, true, "icon-panel-phone", null),
  STORYBOARD_OVERLAY("storyboard_overlay", "Storyboard Overlay", EditorPanelPlacement.HIDDEN, true, "icon-panel-storyboard", "storyboard_overlay_tool_orange_transparent.png"),
  LAYERED_IMAGES("layered_images", "Layered Image Visualizer", EditorPanelPlacement.HIDDEN, true, "icon-panel-layered", null),
  IMAGE_ATTRIBUTES("image_attributes", "Image Attributes Tool", EditorPanelPlacement.HIDDEN, false, "icon-panel-image-attributes", null),
  IMAGE_TINT("image_tint", "Scene Lighting Studio", EditorPanelPlacement.HIDDEN, true, "icon-panel-image-tint", "scene_lighting_studio_tool_orange_transparent.png"),
  MENU_FLOW("menu_flow", "Menu Flow", EditorPanelPlacement.HIDDEN, true, "icon-panel-menuflow", "menu_flow_editor_orange_transparent.png"),
  VERSION_CONTROL("version_control", "Version Control", EditorPanelPlacement.HIDDEN, true, "icon-panel-vcs", null),
  HELP("help", "Help", EditorPanelPlacement.HIDDEN, true, "icon-panel-help", "help.png"),
  PUPPETEER_LAUNCHER("puppeteer_launcher", "Puppeteer Launcher", EditorPanelPlacement.HIDDEN, true, "icon-panel-puppeteer", "puppetteer_orange_transparent.png"),
  SCRIPT_EDITOR("script_editor", "Script Editor", EditorPanelPlacement.HIDDEN, true, "icon-panel-text", "code_editor_orange_transparent.png");

  private final String key;
  private final String displayName;
  private final EditorPanelPlacement defaultPlacement;
  private final boolean defaultVisibleInChooser;
  private final String iconStyleClass;
  private final String iconAssetName;

  EditorSidebarPanel(
      String key,
      String displayName,
      EditorPanelPlacement defaultPlacement,
      boolean defaultVisibleInChooser,
      String iconStyleClass,
      String iconAssetName) {
    this.key = key;
    this.displayName = displayName;
    this.defaultPlacement = defaultPlacement;
    this.defaultVisibleInChooser = defaultVisibleInChooser;
    this.iconStyleClass = iconStyleClass;
    this.iconAssetName = iconAssetName;
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

  public String iconStyleClass() {
    return iconStyleClass;
  }

  public String iconAssetName() {
    return iconAssetName;
  }

  public boolean editableInSettings() {
    return true;
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
