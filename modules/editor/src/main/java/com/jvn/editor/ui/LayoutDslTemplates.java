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
# Text-first workflow: edit -> save -> run runtime -> validate -> iterate.
# Format: key=value (Java .properties)
# Units: fractions (0..1) = viewport-relative, pixel values = absolute.
# choiceYStart: -1 = auto-center vertically.
# Font weights: NORMAL, BOLD.  Text keys from config/locales/*.properties
# are resolved via VnTextFormatter at runtime.

# ---- Text box (main dialogue panel) ----
textBoxX=0.0
textBoxY=0.75
textBoxWidth=1.0
textBoxHeight=0.25
textBoxPadding=20

# ---- Name box (speaker tag) ----
nameBoxXOffset=24
nameBoxYOffset=-38
nameBoxWidth=210
nameBoxHeight=38
nameTextXOffset=12
nameTextBaselineOffset=24
nameTextTopPadding=0
nameTextBottomPadding=0
nameTextYAlign=-1

# ---- Dialogue text insets ----
dialogueTextHorizontalPadding=24
dialogueTextTopPadding=36
dialogueTextRightPadding=24
dialogueTextBottomPadding=12

# ---- Choice list ----
choiceXCenter=0.5
choiceYStart=-1
choiceYAnchor=0
choiceWidthFactor=0.56
choiceHeight=48
choiceGap=10
choiceTextXPadding=20
choiceTextTopPadding=0
choiceTextBottomPadding=0
choiceTextYAlign=-1

# ---- Dialogue style ----
textBoxColor=#0C1220E0
textBoxOpacity=0.88
nameBoxColor=#14203890
nameTextColor=#FFD78A
nameTextFontFamily=SansSerif
nameTextFontSize=18
# nameTextFontWeight=NORMAL
# nameBoxOpacity=1.0
dialogueTextColor=#E8EDF6
dialogueTextFontFamily=SansSerif
dialogueTextFontSize=22
# dialogueTextFontWeight=NORMAL

# ---- Choice style ----
choiceCornerRadius=8
choiceBorderWidth=1.5
choiceTextBaselineOffset=4
choiceFontFamily=SansSerif
choiceFontSize=20
# choiceFontWeight=NORMAL
choiceBackgroundColor=#1A2640D8
choiceHoverColor=#243358E8
choiceSelectedColor=#2A3D68E8
choiceDisabledColor=#121826A0
choiceTextColor=#D4DCF0
choiceHoverTextColor=#F0F4FF
choiceSelectedTextColor=#FFD78A
choiceDisabledTextColor=#6878A0
choiceBorderColor=#3A5080A0
choiceHoverBorderColor=#5888CCA0
choiceSelectedBorderColor=#C8A04880
choiceDisabledBorderColor=#28345060

# ---- Character framing ----
characterHeightFactor=0.85
characterBaselineY=1.0

# ---- NVL presentation mode ----
# Switch at runtime with [mode dialogue nvl] and [mode dialogue standard]
nvlX=0.08
nvlY=0.1
nvlWidth=0.84
nvlHeight=0.72
nvlPadding=24
nvlSpeakerWidth=160
nvlEntryGap=18
nvlMaxEntries=6
nvlPanelColor=#08111acc
nvlPanelOpacity=0.84
nvlSpeakerTextColor=#F7D89A
nvlTextColor=#E8EDF6
# nvlPanelAsset=assets/ui/nvl_panel.png

# ---- Bubble presentation mode ----
# Switch at runtime with [mode bubble on] / [mode bubble off]
bubbleWidthFactor=0.28
bubbleMinHeight=92
bubbleTextPadding=18
bubbleYOffset=26
bubbleTailSize=18
bubbleColor=#152238ee
bubbleOpacity=0.96
bubbleBorderColor=#A9BCD9
bubbleSpeakerTextColor=#FFD78A
bubbleTextColor=#F1F5FF
bubbleCornerRadius=20
bubbleBorderWidth=2
# bubbleAsset=assets/ui/bubble.png

# ---- Custom textbox / choice skin assets (uncomment to use) ----
# textBoxAsset=assets/ui/textbox.png
# textBoxBoundsPoints=0,0;1,0;1,1;0,1
# nameBoxAsset=assets/ui/namebox.png
# nameBoxBoundsPoints=0,0;1,0;1,1;0,1
# dialogueTextBoundsPoints=0,0;1,0;1,1;0,1
# choiceButtonAsset=assets/ui/choice_button.png
# choiceButtonHoverAsset=assets/ui/choice_button_hover.png
# choiceButtonSelectedAsset=assets/ui/choice_button_selected.png
# choiceButtonDisabledAsset=assets/ui/choice_button_disabled.png
# choiceButtonBoundsPoints=0,0;1,0;1,1;0,1

# ---- Textbox action buttons (optional) ----
# Define quick-menu buttons here if your project uses them.
# textBoxButton.ids=history,menu
# textBoxButton.history.label=History
# textBoxButton.history.action=history
# textBoxButton.history.enabled=true
# textBoxButton.history.space=viewport
# textBoxButton.history.x=0.08
# textBoxButton.history.y=0.92
# textBoxButton.history.width=0.04
# textBoxButton.history.height=0.055
# textBoxButton.menu.label=Menu
# textBoxButton.menu.action=settings_menu
# textBoxButton.menu.enabled=true
# textBoxButton.menu.space=viewport
# textBoxButton.menu.x=0.88
# textBoxButton.menu.y=0.92
# textBoxButton.menu.width=0.04
# textBoxButton.menu.height=0.055
""";
  }

  public static String defaultMenuLayoutTemplate(MenuLayoutSpec spec) {
    MenuLayoutSpec s = spec == null ? MenuProfile.defaultLayout() : spec;
    return """
# Menu layout (.layout)
# Text-first workflow: edit -> save -> run runtime -> iterate.
# listYStart/titleY: viewport fraction (0..1). lineHeight/hintsBottomMargin/subtitleGap: pixels.
# listWidthFactor: viewport fraction (0.1..1.0). textAlign/titleAlign/hintsAlign: left | center | right.
listYStart=%s
lineHeight=%s
listWidthFactor=%s
textAlign=%s
titleAlign=%s
hintsBottomMargin=%s
subtitleGap=%s
hintsAlign=%s
titleY=0.16
# hintsX=0.5
""".formatted(
        formatDouble(s.listYStart()),
        formatDouble(s.lineHeight()),
        formatDouble(s.listWidthFactor()),
        s.textAlign(),
        s.titleAlign(),
        formatDouble(s.hintsBottomMargin()),
        formatDouble(s.subtitleGap()),
        s.hintsAlign()
    );
  }

  public static String defaultMenuStyleTemplate(MenuStyleSpec style) {
    MenuStyleSpec s = style == null ? MenuProfile.defaultStyle() : style;
    return """
# Menu style (.style)
# Colors: #RRGGBB or #RRGGBBAA. Font keys map to JavaFX font resolver.
itemColor=%s
itemSelectedColor=%s
itemDisabledColor=%s
itemPrefix=%s
itemSelectedPrefix=%s
itemDisabledPrefix=%s
itemFontFamily=SansSerif
itemFontWeight=SEMI_BOLD
itemFontSize=26
buttonTextPaddingX=24
buttonTextPaddingY=2
# itemHoverColor=#E4EEFF
# itemShadowColor=#00000066
# titleColor=#EEF4FF
# titleFontSize=48
# hintsColor=#8898B8
# backgroundAsset=assets/ui/menu/bg.png
# backgroundColor=#060D1A
""".formatted(
        valueOrDefault(s.itemColor(), "#C8D6EC"),
        valueOrDefault(s.itemSelectedColor(), "#FFDFA0"),
        valueOrDefault(s.itemDisabledColor(), "#5C6B84"),
        valueOrDefault(s.itemPrefix(), ""),
        valueOrDefault(s.itemSelectedPrefix(), "\u25b8 "),
        valueOrDefault(s.itemDisabledPrefix(), "")
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
# titleText, subtitleText, hintsText, layout, defaultItemStyle, wrapSelection, items
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
# subtitleText=Optional supporting line
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
# Shared by extras/settings/credits menus.
listYStart=0.32
lineHeight=58
listWidthFactor=0.54
listXCenter=0.5
textAlign=center
hintsBottomMargin=28
titleY=0.13
""";
  }

  public static String settingsLayoutTemplate() {
    return """
# Settings layout (.layout)
# Generic left-aligned settings screen.
listYStart=0.24
lineHeight=56
listWidthFactor=0.6
listXCenter=0.5
textAlign=left
hintsBottomMargin=22
titleY=0.12
""";
  }

  public static String settingsStyleTemplate() {
    return """
# Settings visual style (.style)
# Generic fallback settings presentation. Override in project config for custom skins.
itemColor=#D7E2F4
itemSelectedColor=#FFD78A
itemHoverColor=#EEF4FF
itemDisabledColor=#6A7892
itemPrefix=
itemSelectedPrefix=\u25b8\s
itemDisabledPrefix=
itemFontFamily=SansSerif
itemFontWeight=SEMI_BOLD
itemFontSize=20
itemShadowColor=#00000055
itemShadowOffsetX=1
itemShadowOffsetY=1
itemOpacity=1.0
buttonTextPaddingX=18
buttonTextPaddingY=0
titleColor=#EEF4FF
titleFontFamily=SansSerif
titleFontWeight=BOLD
titleFontSize=40
titleShadowColor=#00000066
hintsColor=#96A4BE
hintsFontFamily=SansSerif
hintsFontSize=14
backgroundColor=#08101E
backgroundOpacity=0.96
""";
  }

  public static String slotsLayoutTemplate() {
    return """
# Save/Load slot layout (.layout)
# Tuned for wider save slot cards.
listYStart=0.22
lineHeight=68
listWidthFactor=0.54
listXCenter=0.5
textAlign=center
hintsBottomMargin=28
titleY=0.12
""";
  }

  public static String defaultMenuStyleFullTemplate(String backgroundAsset) {
    String bg = backgroundAsset == null ? "" : backgroundAsset;
    return """
# Main menu visual style (.style)
# Text-first workflow: edit colors/fonts/asset paths -> save -> run runtime.
# Colors: #RRGGBB or #RRGGBBAA.  Prefix values prepend to rendered labels.
itemColor=#C8D6EC
itemSelectedColor=#FFDFA0
itemHoverColor=#E4EEFF
itemDisabledColor=#5C6B84
itemPrefix=
itemSelectedPrefix=\u25b8\s
itemDisabledPrefix=
itemFontFamily=SansSerif
itemFontWeight=SEMI_BOLD
itemFontSize=26
itemShadowColor=#00000066
itemShadowOffsetX=1.5
itemShadowOffsetY=1.5
titleColor=#EEF4FF
titleFontFamily=SansSerif
titleFontWeight=BOLD
titleFontSize=48
titleShadowColor=#00000088
hintsColor=#8898B8
hintsFontFamily=SansSerif
# hintsFontWeight=NORMAL
hintsFontSize=15
backgroundAsset=%s
backgroundColor=#060D1A
backgroundOpacity=1.0
# itemOpacity=1.0
buttonTextPaddingX=24
buttonTextPaddingY=2
# buttonAsset=assets/ui/menu/button.png
# buttonSelectedAsset=assets/ui/menu/button_selected.png
# buttonHoverAsset=assets/ui/menu/button_hover.png
# buttonDisabledAsset=assets/ui/menu/button_disabled.png
""".formatted(bg);
  }

  public static String submenuStyleTemplate() {
    return """
# Submenu visual style (.style)
# Shared by extras/settings/credits menus.
# Inherits default.style so submenus use the same background image as main menu.
# Renderer applies a frosted/blurred treatment for submenu screens.
extends=default
itemColor=#B8C8E4
itemSelectedColor=#90D4F8
itemHoverColor=#D8E8FF
itemDisabledColor=#5A6E8C
itemPrefix=
itemSelectedPrefix=\u25b8\s
itemDisabledPrefix=
itemFontFamily=SansSerif
itemFontWeight=NORMAL
itemFontSize=22
itemShadowColor=#00000044
itemShadowOffsetX=1
itemShadowOffsetY=1
titleColor=#D8E6FF
titleFontFamily=SansSerif
titleFontWeight=BOLD
titleFontSize=36
titleShadowColor=#00000066
hintsColor=#7888A8
hintsFontFamily=SansSerif
# hintsFontWeight=NORMAL
hintsFontSize=14
backgroundColor=#08101E
backgroundOpacity=0.96
# itemOpacity=1.0
buttonTextPaddingX=20
buttonTextPaddingY=1
# buttonAsset=assets/ui/menu/button.png
# buttonSelectedAsset=assets/ui/menu/button_selected.png
# buttonHoverAsset=assets/ui/menu/button_hover.png
# buttonDisabledAsset=assets/ui/menu/button_disabled.png
""";
  }

  public static String slotStyleTemplate() {
    return """
# Save/load slot row visual style (.style)
# Designed for save slot card rows.
# Inherits default.style so load/save menus reuse the main menu background image.
extends=default
itemColor=#C4D4EC
itemSelectedColor=#FFE4A0
itemHoverColor=#E0ECFF
itemDisabledColor=#5E6E88
itemPrefix=
itemSelectedPrefix=\u25b8\s
itemDisabledPrefix=
itemFontFamily=SansSerif
itemFontWeight=SEMI_BOLD
itemFontSize=20
itemShadowColor=#00000044
itemShadowOffsetX=1
itemShadowOffsetY=1
titleColor=#D8E6FF
titleFontFamily=SansSerif
titleFontWeight=BOLD
titleFontSize=36
titleShadowColor=#00000066
hintsColor=#7888A8
hintsFontFamily=SansSerif
# hintsFontWeight=NORMAL
hintsFontSize=14
backgroundColor=#070E1C
backgroundOpacity=1.0
# itemOpacity=1.0
buttonTextPaddingX=18
buttonTextPaddingY=0
# buttonAsset=assets/ui/menu/button.png
# buttonSelectedAsset=assets/ui/menu/button_selected.png
# buttonHoverAsset=assets/ui/menu/button_hover.png
# buttonDisabledAsset=assets/ui/menu/button_disabled.png
""";
  }

  public static String defaultSettingsMenuTemplate() {
    return """
# Settings menu profile (.menu)
# Generic fallback settings screen. Override in project config for custom skins.
titleText=Settings
hintsText=Up/Down: Navigate    Left/Right: Adjust    Enter: Toggle    Esc: Back
layout=settings
defaultItemStyle=settings
wrapSelection=true
items=text_speed,auto_play_delay,bgm_volume,sfx_volume,voice_volume,skip_unread,skip_after_choices,click_reveal_before_advance,back
item.text_speed.label=Text Speed {value}
item.auto_play_delay.label=Auto-Advance {value}
item.bgm_volume.label=Music {value}
item.sfx_volume.label=Sound Effects {value}
item.voice_volume.label=Voice {value}
item.skip_unread.label=Skip Unread {value}
item.skip_after_choices.label=Skip After Choices {value}
item.click_reveal_before_advance.label=Click Reveal {value}
item.back.label=Back
item.back.action=back

# Optional slider/toggle positioning overrides:
# item.text_speed.sliderX=0.58
# item.text_speed.sliderY=0.36
# item.text_speed.sliderWidth=0.24
# item.skip_unread.toggleX=0.82
# item.skip_unread.toggleY=0.58
# item.skip_unread.toggleWidth=0.03
# item.skip_unread.toggleHeight=0.05
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
