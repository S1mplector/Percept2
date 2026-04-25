package com.jvn.editor.ui;

import java.util.EnumMap;
import java.util.Map;

public enum EditorSidebarPanel {
  PROJECT("project", "Project", EditorPanelPlacement.LEFT, true, "icon-panel-project"),
  TIMELINE("timeline", "Timeline", EditorPanelPlacement.HIDDEN, true, "icon-panel-timeline"),
  INSPECTOR("inspector", "Inspector", EditorPanelPlacement.HIDDEN, false, "icon-panel-inspector"),
  VNS_DIAGNOSTICS("vns_diagnostics", "VNS Diagnostics", EditorPanelPlacement.HIDDEN, true, "icon-panel-diagnostics"),
  LABEL_FLOW("label_flow", "Label Flow", EditorPanelPlacement.HIDDEN, false, "icon-panel-flow"),
  ASSETS("assets", "Assets", EditorPanelPlacement.HIDDEN, false, "icon-panel-assets"),
  LAYOUT_LAUNCHER("layout_launcher", "Layout Launcher", EditorPanelPlacement.HIDDEN, true, "icon-panel-layouts"),
  PHONE_ASSETS("phone_assets", "Phone Assets", EditorPanelPlacement.HIDDEN, true, "icon-panel-phone"),
  STORYBOARD_OVERLAY("storyboard_overlay", "Storyboard Overlay", EditorPanelPlacement.HIDDEN, true, "icon-panel-storyboard"),
  LAYERED_IMAGES("layered_images", "Layered Image Visualizer", EditorPanelPlacement.HIDDEN, true, "icon-panel-layered"),
  IMAGE_ATTRIBUTES("image_attributes", "Image Attributes Tool", EditorPanelPlacement.HIDDEN, false, "icon-panel-image-attributes"),
  IMAGE_TINT("image_tint", "Scene Lighting Studio", EditorPanelPlacement.HIDDEN, true, "icon-panel-image-tint"),
  MENU_FLOW("menu_flow", "Menu Flow", EditorPanelPlacement.HIDDEN, true, "icon-panel-menuflow"),
  VERSION_CONTROL("version_control", "Version Control", EditorPanelPlacement.HIDDEN, true, "icon-panel-vcs"),
  HELP("help", "Help", EditorPanelPlacement.HIDDEN, true, "icon-panel-help"),
  PUPPETEER_LAUNCHER("puppeteer_launcher", "Puppeteer Launcher", EditorPanelPlacement.HIDDEN, true, "icon-panel-puppeteer"),
  SCRIPT_EDITOR("script_editor", "Script Editor", EditorPanelPlacement.HIDDEN, true, "icon-panel-text");

  private final String key;
  private final String displayName;
  private final EditorPanelPlacement defaultPlacement;
  private final boolean defaultVisibleInChooser;
  private final String iconStyleClass;

  EditorSidebarPanel(
      String key,
      String displayName,
      EditorPanelPlacement defaultPlacement,
      boolean defaultVisibleInChooser,
      String iconStyleClass) {
    this.key = key;
    this.displayName = displayName;
    this.defaultPlacement = defaultPlacement;
    this.defaultVisibleInChooser = defaultVisibleInChooser;
    this.iconStyleClass = iconStyleClass;
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
