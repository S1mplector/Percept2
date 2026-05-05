import sys

content = open("editor/src/main/java/com/jvn/editor/EditorApp.java").read()

new_sidebar_icon_method = """  private Node sidebarPanelIcon(EditorSidebarPanel panel, String... extraStyleClasses) {
    if (panel == null) return null;
    Region icon = getSidebarCssIcon(panel);
    if (extraStyleClasses != null && extraStyleClasses.length > 0) {
      icon.getStyleClass().addAll(extraStyleClasses);
    }
    return icon;
  }

  private Region getSidebarCssIcon(EditorSidebarPanel panel) {
    switch (panel) {
      case PROJECT: return com.jvn.editor.ui.CssIcon.folder("#f5c56f");
      case TIMELINE: return com.jvn.editor.ui.CssIcon.timeline("#cfd7e6");
      case INSPECTOR: return com.jvn.editor.ui.CssIcon.search("#e2d3c3");
      case VNS_DIAGNOSTICS: return com.jvn.editor.ui.CssIcon.warning("#f0b673");
      case LABEL_FLOW: return com.jvn.editor.ui.CssIcon.link("#91d7a5");
      case ASSETS: return com.jvn.editor.ui.CssIcon.folder("#d8cbb3");
      case LAYOUT_LAUNCHER: return com.jvn.editor.ui.CssIcon.rectSelect("#dbcab8");
      case PHONE_ASSETS: return com.jvn.editor.ui.CssIcon.dock("#80d4ff");
      case STORYBOARD_OVERLAY: return com.jvn.editor.ui.CssIcon.movie("#e7bc72");
      case LAYERED_IMAGES: return com.jvn.editor.ui.CssIcon.copy("#f5b971");
      case IMAGE_ATTRIBUTES: return com.jvn.editor.ui.CssIcon.edit("#c0d7ef");
      case IMAGE_TINT: return com.jvn.editor.ui.CssIcon.palette("#f6a2c8");
      case PARTICLE_FX: return com.jvn.editor.ui.CssIcon.rocket("#ff9f3d");
      case MENU_FLOW: return com.jvn.editor.ui.CssIcon.list("#7dd6b7");
      case VERSION_CONTROL: return com.jvn.editor.ui.CssIcon.timeline("#86e4be");
      case HELP: return com.jvn.editor.ui.CssIcon.speech("#ffd166");
      case PUPPETEER_LAUNCHER: return com.jvn.editor.ui.CssIcon.movie("#f0a0d0");
      case SCRIPT_EDITOR: return com.jvn.editor.ui.CssIcon.edit("#9cc7ff");
      default: return com.jvn.editor.ui.CssIcon.folder("#ffffff");
    }
  }"""

old_sidebar_icon_method = """  private Node sidebarPanelIcon(EditorSidebarPanel panel, String... extraStyleClasses) {
    if (panel == null) return null;
    ImageView assetIcon = sidebarPanelAssetIcon(panel, extraStyleClasses);
    if (assetIcon != null) return assetIcon;
    Region icon = icon("icon", panel.iconStyleClass());
    if (extraStyleClasses != null && extraStyleClasses.length > 0) {
      icon.getStyleClass().addAll(extraStyleClasses);
    }
    return icon;
  }"""

if old_sidebar_icon_method in content:
    content = content.replace(old_sidebar_icon_method, new_sidebar_icon_method)
else:
    print("Could not find old_sidebar_icon_method")

new_settings_icon = """  private Node editorSettingsSidebarIcon(String... extraStyleClasses) {
    Region icon = com.jvn.editor.ui.CssIcon.settings("#c4b5fd");
    if (extraStyleClasses != null && extraStyleClasses.length > 0) {
      icon.getStyleClass().addAll(extraStyleClasses);
    }
    return icon;
  }"""

old_settings_icon = """  private Node editorSettingsSidebarIcon(String... extraStyleClasses) {
    ImageView assetIcon = sidebarAssetIcon("settings_orange_transparent.png", extraStyleClasses);
    if (assetIcon != null) return assetIcon;
    Region icon = icon("icon", "icon-panel-settings");
    if (extraStyleClasses != null && extraStyleClasses.length > 0) {
      icon.getStyleClass().addAll(extraStyleClasses);
    }
    return icon;
  }"""

if old_settings_icon in content:
    content = content.replace(old_settings_icon, new_settings_icon)
else:
    print("Could not find old_settings_icon")

open("editor/src/main/java/com/jvn/editor/EditorApp.java", "w").write(content)

