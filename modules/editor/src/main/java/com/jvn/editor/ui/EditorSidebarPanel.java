package com.jvn.editor.ui;

import java.util.EnumMap;
import java.util.Map;

public enum EditorSidebarPanel {
  PROJECT("project", "Project", EditorPanelPlacement.LEFT, true, "icon-panel-project", "project_inspector_orange_transparent.png", "1.8", Maturity.STABLE),
  TIMELINE("timeline", "Story Map", EditorPanelPlacement.HIDDEN, true, "icon-panel-timeline", "timeline_editor_orange_transparent.png", "1.2", Maturity.STABLE),
  INSPECTOR("inspector", "Inspector", EditorPanelPlacement.HIDDEN, false, "icon-panel-inspector", "project_inspector_orange_transparent.png", "1.0", Maturity.STABLE),
  VNS_DIAGNOSTICS("vns_diagnostics", "Diagnostics", EditorPanelPlacement.HIDDEN, true, "icon-panel-diagnostics", "vns_diagnostics_orange_transparent.png", "1.1.1", Maturity.STABLE),
  LABEL_FLOW("label_flow", "Label Flow", EditorPanelPlacement.HIDDEN, false, "icon-panel-flow", "label_flow_inspector_orange_transparent.png", "0.9", Maturity.BETA),
  ASSETS("assets", "Assets", EditorPanelPlacement.HIDDEN, false, "icon-panel-assets", null, "1.0", Maturity.STABLE),
  LAYOUT_LAUNCHER("layout_launcher", "Layout Launcher", EditorPanelPlacement.HIDDEN, true, "icon-panel-layouts", "layout_editor_manager_orange_transparent.png", "0.8", Maturity.BETA),
  PHONE_ASSETS("phone_assets", "Phone Assets", EditorPanelPlacement.HIDDEN, true, "icon-panel-phone", null, "0.7", Maturity.BETA),
  STORYBOARD_OVERLAY("storyboard_overlay", "Storyboard Overlay", EditorPanelPlacement.HIDDEN, true, "icon-panel-storyboard", "storyboard_overlay_tool_orange_transparent.png", "0.6", Maturity.BETA),
  LAYERED_IMAGES("layered_images", "Layered Image Visualizer", EditorPanelPlacement.HIDDEN, true, "icon-panel-layered", "layered_image_visualizer_orange_transparent.png", "1.0", Maturity.STABLE),
  IMAGE_ATTRIBUTES("image_attributes", "Image Attributes Tool", EditorPanelPlacement.HIDDEN, false, "icon-panel-image-attributes", null, "0.8", Maturity.BETA),
  IMAGE_TINT("image_tint", "Scene Lighting Studio", EditorPanelPlacement.HIDDEN, true, "icon-panel-image-tint", "scene_lighting_studio_tool_orange_transparent.png", "0.7", Maturity.BETA),
  PARTICLE_FX("particle_fx", "Particle FX", EditorPanelPlacement.HIDDEN, true, "icon-panel-particle-fx", "particle_effects_orange_transparent.png", "0.3", Maturity.ALPHA),
  MENU_FLOW("menu_flow", "Menu Flow", EditorPanelPlacement.HIDDEN, true, "icon-panel-menuflow", "menu_flow_editor_orange_transparent.png", "0.9", Maturity.BETA),
  VERSION_CONTROL("version_control", "Version Control", EditorPanelPlacement.HIDDEN, true, "icon-panel-vcs", "version_control_orange_transparent_v2.png", "1.0", Maturity.STABLE),
  HELP("help", "Help", EditorPanelPlacement.HIDDEN, true, "icon-panel-help", "help.png", "1.2.1", Maturity.STABLE),
  PUPPETEER_LAUNCHER("puppeteer_launcher", "Puppeteer Launcher", EditorPanelPlacement.HIDDEN, true, "icon-panel-puppeteer", "puppetteer_orange_transparent.png", "0.9", Maturity.BETA),
  SCRIPT_EDITOR("script_editor", "Script Editor", EditorPanelPlacement.HIDDEN, true, "icon-panel-text", "code_editor_orange_transparent.png", "1.0", Maturity.STABLE);

  private final String key;
  private final String displayName;
  private final EditorPanelPlacement defaultPlacement;
  private final boolean defaultVisibleInChooser;
  private final String iconStyleClass;
  private final String iconAssetName;
  private final String version;
  private final Maturity maturity;

  EditorSidebarPanel(
      String key,
      String displayName,
      EditorPanelPlacement defaultPlacement,
      boolean defaultVisibleInChooser,
      String iconStyleClass,
      String iconAssetName,
      String version,
      Maturity maturity) {
    this.key = key;
    this.displayName = displayName;
    this.defaultPlacement = defaultPlacement;
    this.defaultVisibleInChooser = defaultVisibleInChooser;
    this.iconStyleClass = iconStyleClass;
    this.iconAssetName = iconAssetName;
    this.version = version;
    this.maturity = maturity == null ? Maturity.STABLE : maturity;
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

  public String version() {
    return version;
  }

  public Maturity maturity() {
    return maturity;
  }

  public String versionBadge() {
    return maturity.prefix + version;
  }

  public String versionStyleClass() {
    return maturity.styleClass;
  }

  public String versionTooltip() {
    return displayName + " " + versionBadge() + " (" + maturity.displayName + ")";
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

  public enum Maturity {
    STABLE("v", "Stable", "panel-chooser-version-stable"),
    BETA("β", "Beta", "panel-chooser-version-beta"),
    ALPHA("α", "Alpha", "panel-chooser-version-alpha");

    private final String prefix;
    private final String displayName;
    private final String styleClass;

    Maturity(String prefix, String displayName, String styleClass) {
      this.prefix = prefix;
      this.displayName = displayName;
      this.styleClass = styleClass;
    }
  }
}
