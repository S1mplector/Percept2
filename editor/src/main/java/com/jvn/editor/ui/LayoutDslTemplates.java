package com.jvn.editor.ui;

import java.util.Locale;

import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuStyleSpec;

/**
 * Centralized source of default DSL template text for layout/menu files.
 * Keep all default template content in one place to avoid drift.
 */
public final class LayoutDslTemplates {
  private LayoutDslTemplates() {
  }

  public static String defaultDialogueLayoutTemplate() {
    return """
# Dialogue UI layout (.layout)
# Text-first workflow:
# 1) Edit values in this file.
# 2) Save.
# 3) Run project in runtime.
# 4) Validate on real scene, then iterate.
#
# Format: key=value (Java .properties)
# Units:
# - Fractions (0..1) are viewport-relative.
# - Pixel values are absolute screen-space units.
# - choiceYStart uses -1 for auto-center, otherwise a 0..1 fraction.

# --- Text box container (main dialogue panel) ---
# textBoxX/textBoxY: top-left anchor in viewport fractions.
# textBoxWidth/textBoxHeight: size in viewport fractions.
# textBoxPadding: inner pixel padding fallback for legacy text flow.
textBoxX=0.0026
textBoxY=0.7076
textBoxWidth=0.9974
textBoxHeight=0.25
textBoxPadding=20

# --- Name box (speaker tag panel) ---
# Offsets are measured from textBox top-left.
# Use negative Y to lift the nameplate above the dialogue box.
nameBoxXOffset=20
nameBoxYOffset=-40
nameBoxWidth=200
nameBoxHeight=40
nameTextXOffset=10
nameTextBaselineOffset=25

# --- Dialogue text bounds inside the text box ---
# dialogueTextHorizontalPadding = left text padding.
# Top/Right/Bottom paddings let you fit custom textbox art exactly.
dialogueTextHorizontalPadding=20
dialogueTextTopPadding=40
dialogueTextRightPadding=20
dialogueTextBottomPadding=10

# --- Choice list geometry ---
# choiceXCenter: center point of the choice stack (0=left, 1=right).
# choiceYStart: first choice Y (fraction). Set to -1 for auto-center.
# choiceWidthFactor: each choice width as viewport fraction.
# choiceHeight / choiceGap / choiceTextXPadding are pixels.
choiceXCenter=0.5853
choiceYStart=0.3601
choiceWidthFactor=0.6
choiceHeight=50
choiceGap=10
choiceTextXPadding=20
choiceCornerRadius=10
choiceBorderWidth=2
choiceTextBaselineOffset=5

# --- Optional textbox / choice skin assets ---
# Uncomment to use custom textures.
# textBoxAsset=assets/ui/textbox.png
# nameBoxAsset=assets/ui/namebox.png
# choiceButtonAsset=assets/ui/choice_button.png
# choiceButtonHoverAsset=assets/ui/choice_button_hover.png
# choiceButtonSelectedAsset=assets/ui/choice_button_selected.png
# choiceButtonDisabledAsset=assets/ui/choice_button_disabled.png

# --- Optional text styling ---
# nameTextColor=#FFFFFFFF
# nameTextFontFamily=Segoe UI
# nameTextFontSize=18
# dialogueTextColor=#FFFFFFFF
# dialogueTextFontFamily=Segoe UI
# dialogueTextFontSize=22

# --- Optional choice colors (fallback when no texture is set) ---
# choiceBackgroundColor=#323246E6
# choiceHoverColor=#464664E6
# choiceSelectedColor=#464664E6
# choiceDisabledColor=#323232A0
# choiceTextColor=#FFFFFFFF
# choiceHoverTextColor=#FFFFFFFF
# choiceSelectedTextColor=#FFFFFFFF
# choiceDisabledTextColor=#AAAAAAFF
# choiceBorderColor=#FFFFFFFF
# choiceHoverBorderColor=#FFFFFFFF
# choiceSelectedBorderColor=#FFFFFFFF
# choiceDisabledBorderColor=#AAAAAAFF

# --- Optional character framing tweaks ---
# characterHeightFactor=0.85
# characterBaselineY=1.0

# --- Optional clickable textbox action buttons ---
# Buttons render inside dialogue textbox bounds.
# textBoxButton.ids controls order and active ids.
# Per-button keys use textBoxButton.<id>.*.
# x/y/width/height are normalized (0..1) inside textbox bounds.
# action can be save_menu, load_menu, settings_menu, main_menu, open_menu, back.
# textBoxButton.ids=save,load,settings
# textBoxButton.save.label=Save
# textBoxButton.save.action=save_menu
# textBoxButton.save.x=0.74
# textBoxButton.save.y=0.08
# textBoxButton.save.width=0.1
# textBoxButton.save.height=0.24
# textBoxButton.save.asset=assets/ui/save_btn.png
# textBoxButton.save.hoverAsset=assets/ui/save_btn_hover.png
# textBoxButton.save.disabledAsset=assets/ui/save_btn_disabled.png
# textBoxButton.load.label=Load
# textBoxButton.load.action=load_menu
# textBoxButton.load.x=0.85
# textBoxButton.load.y=0.08
# textBoxButton.load.width=0.1
# textBoxButton.load.height=0.24
""";
  }

  public static String defaultMenuLayoutTemplate(MenuLayoutSpec spec) {
    MenuLayoutSpec s = spec == null ? MenuProfile.defaultLayout() : spec;
    return """
# Menu layout template (.layout)
# Text-first workflow: edit -> save -> run runtime -> validate -> iterate.
# Format: key=value (Java .properties)
# Units:
# - listYStart/titleY: <=1 means viewport fraction, >1 means pixels.
# - lineHeight/hintsBottomMargin: pixels.
# - listWidthFactor: viewport fraction (0.1..1.0).
# textAlign options: left | center | right.
#
# Recommended tweak order:
# 1) listYStart
# 2) lineHeight
# 3) listWidthFactor + textAlign
# 4) titleY/hintsBottomMargin
listYStart=%s
lineHeight=%s
listWidthFactor=%s
textAlign=%s
hintsBottomMargin=%s
# titleY is optional. Uncomment to override runtime/default style title offset.
# titleY=0.12
""".formatted(
        formatDouble(s.listYStart()),
        formatDouble(s.lineHeight()),
        formatDouble(s.listWidthFactor()),
        s.textAlign(),
        formatDouble(s.hintsBottomMargin())
    );
  }

  public static String defaultMenuStyleTemplate(MenuStyleSpec style) {
    MenuStyleSpec s = style == null ? MenuProfile.defaultStyle() : style;
    return """
# Menu style template (.style)
# Text-first workflow: edit -> save -> run runtime -> compare in-game fonts/colors.
# Colors can be #RRGGBB or #RRGGBBAA.
# Prefix keys are prepended to rendered labels.
# Font keys map to JavaFX font resolver.
# Asset keys should be project-relative paths.
#
# --- Required baseline keys ---
itemPrefix=%s
itemSelectedPrefix=%s
itemDisabledPrefix=%s
itemDisabledColor=%s
buttonTextPaddingX=18
buttonTextPaddingY=0

# --- Optional row colors / typography ---
# itemColor=#D3D3D3
# itemSelectedColor=#FFFF00
# itemHoverColor=#FFE066
# itemFontFamily=Segoe UI
# itemFontWeight=NORMAL
# itemFontSize=22
# titleColor=#FFFFFF
# titleFontSize=44
# hintsColor=#AAB4C8

# --- Optional background and button textures ---
# backgroundAsset=assets/ui/menu/bg.png
# buttonAsset=assets/ui/menu/button.png
# buttonSelectedAsset=assets/ui/menu/button_selected.png
# buttonHoverAsset=assets/ui/menu/button_hover.png
# buttonDisabledAsset=assets/ui/menu/button_disabled.png
""".formatted(
        valueOrDefault(s.itemPrefix(), "  "),
        valueOrDefault(s.itemSelectedPrefix(), "> "),
        valueOrDefault(s.itemDisabledPrefix(), "- "),
        valueOrDefault(s.itemDisabledColor(), "#808080")
    );
  }

  public static String defaultMenuScreenTemplate(String screenId) {
    String id = normalize(screenId, "main");
    String title = titleize(id);
    return """
# Menu screen template (.menu)
# Text-first workflow: edit -> save -> run runtime -> validate navigation/actions.
# Format: key=value (Java .properties)
#
# Core keys:
# titleText, hintsText, layout, defaultItemStyle, wrapSelection, items
#
# Per-item keys:
# item.<id>.label / action / target / style / enabled
# Optional visual keys:
# item.<id>.icon / bgAsset / bgSelectedAsset / bgDisabledAsset
# Optional bounds keys (normalized viewport fractions):
# item.<id>.boundsX / boundsY / boundsWidth / boundsHeight
#
# Common actions:
# open_menu (requires target), back, main_menu, settings_menu, save_menu, load_menu, quit, noop
titleText=%s
hintsText=Enter/Click: Select    Esc: Back
layout=default
defaultItemStyle=default
wrapSelection=true
items=start,back
item.start.label=Start
item.start.action=noop
item.back.label=Back
item.back.action=back
# item.start.target=extras
# item.start.style=submenu
# item.start.enabled=false
# item.start.boundsX=0.28
# item.start.boundsY=0.34
# item.start.boundsWidth=0.44
# item.start.boundsHeight=0.072
# item.start.icon=assets/ui/icons/start.png
""".formatted(title);
  }

  public static String defaultMenuRegistryTemplate() {
    return menuRegistryTemplate("main", "main", "default", "default");
  }

  public static String menuRegistryTemplate(String defaultMenu, String menus, String layouts, String styles) {
    return """
# Menu registry
# File format: Java properties (key=value)
# Text-first workflow: update ids -> save -> run runtime to verify discoverability/wiring.
# defaultMenu: menu id to open first
# menus: comma-separated .menu ids
# layouts: comma-separated .layout ids
# styles: comma-separated .style ids
defaultMenu=%s
menus=%s
layouts=%s
styles=%s
""".formatted(defaultMenu, menus, layouts, styles);
  }

  public static String submenuLayoutTemplate() {
    return """
# Submenu layout (.layout)
# Text-first workflow: edit values, save, run runtime, iterate.
# Reused by extras/settings/credits style menus.
# Keys use same semantics as default.layout.
listYStart=0.24
lineHeight=62
listWidthFactor=0.64
textAlign=left
hintsBottomMargin=30
titleY=0.11
""";
  }

  public static String slotsLayoutTemplate() {
    return """
# Save/Load slot layout (.layout)
# Text-first workflow: edit values, save, run runtime, iterate.
# Tuned for wider save slot cards and preview thumbnails.
# Keys use same semantics as default.layout.
listYStart=0.20
lineHeight=74
listWidthFactor=0.58
textAlign=left
hintsBottomMargin=30
titleY=0.10
""";
  }

  public static String defaultMenuStyleFullTemplate(String backgroundAsset) {
    String bg = backgroundAsset == null ? "" : backgroundAsset;
    return """
# Main menu visual style (.style)
# Text-first workflow: edit colors/fonts/asset paths, save, run runtime.
# Colors accept #RRGGBB or #RRGGBBAA.
# Prefix values are prepended to rendered labels.
# Font keys map to JavaFX font family/weight/size.
# Uncomment buttonAsset keys to use textured row backgrounds.
itemColor=#DCE6F8
itemSelectedColor=#FFE8A3
itemHoverColor=#F4F8FF
itemDisabledColor=#7D8CA8
itemPrefix=
itemSelectedPrefix=▶\s
itemDisabledPrefix=•\s
itemFontFamily=Segoe UI
itemFontWeight=SEMI_BOLD
itemFontSize=28
itemShadowColor=#000000AA
itemShadowOffsetX=1
itemShadowOffsetY=1
titleColor=#F2F7FF
titleFontFamily=Segoe UI
titleFontWeight=BOLD
titleFontSize=56
titleShadowColor=#000000A8
hintsColor=#A8B6D2
hintsFontFamily=Segoe UI
hintsFontSize=18
backgroundAsset=%s
backgroundColor=#050B16
backgroundOpacity=1.0
# buttonAsset=assets/ui/menu/button.png
# buttonSelectedAsset=assets/ui/menu/button_selected.png
# buttonHoverAsset=assets/ui/menu/button_hover.png
# buttonDisabledAsset=assets/ui/menu/button_disabled.png
buttonTextPaddingX=28
buttonTextPaddingY=2
""".formatted(bg);
  }

  public static String submenuStyleTemplate() {
    return """
# Submenu visual style (.style)
# Text-first workflow: edit colors/fonts/asset paths, save, run runtime.
# Shared by extras/settings/credits-oriented menus.
# Keys use the same semantics as default.style.
itemColor=#D6E0F4
itemSelectedColor=#B8EAFF
itemHoverColor=#EAF4FF
itemDisabledColor=#7387AA
itemPrefix=\s\s
itemSelectedPrefix=▸\s
itemDisabledPrefix=\s\s
itemFontFamily=Segoe UI
itemFontWeight=NORMAL
itemFontSize=24
titleColor=#EAF2FF
titleFontFamily=Segoe UI
titleFontWeight=BOLD
titleFontSize=42
hintsColor=#97AACE
hintsFontFamily=Segoe UI
hintsFontSize=16
backgroundColor=#091222
backgroundOpacity=1.0
buttonTextPaddingX=24
buttonTextPaddingY=1
""";
  }

  public static String slotStyleTemplate() {
    return """
# Save/load slot row visual style (.style)
# Text-first workflow: edit colors/fonts/asset paths, save, run runtime.
# Designed for save slot card rows and metadata-heavy labels.
# Keys use the same semantics as default.style.
itemColor=#E4EDF8
itemSelectedColor=#FFF1B5
itemHoverColor=#F0F7FF
itemDisabledColor=#7B87A0
itemPrefix=
itemSelectedPrefix=▶\s
itemFontFamily=Segoe UI
itemFontWeight=SEMI_BOLD
itemFontSize=22
backgroundColor=#060F1D
backgroundOpacity=1.0
buttonTextPaddingX=22
buttonTextPaddingY=0
""";
  }

  private static String formatDouble(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static String valueOrDefault(String value, String fallback) {
    String normalized = normalize(value, "");
    return normalized.isBlank() ? fallback : normalized;
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isBlank() ? fallback : t;
  }

  private static String titleize(String raw) {
    String source = normalize(raw, "Menu").replace('_', ' ').replace('-', ' ');
    if (source.isBlank()) return "Menu";
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        upper = true;
        out.append(c);
      } else if (upper) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
