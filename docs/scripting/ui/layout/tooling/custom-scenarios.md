# Custom Layout Scenarios

Advanced recipes for common and creative layout configurations — multi-button textboxes,
custom submenus with per-item fonts, per-screen backgrounds, auto-width name boxes,
confirmation dialogs, chapter selects, sidebar menus, and more.

Each scenario is self-contained with complete file listings, runtime checklists, and tips.

---

## Scenario 1: Multi-Button Textbox (Full Control Bar)

A dialogue textbox with 6 action buttons arranged as a horizontal bar along the bottom
edge — Auto, Skip, Log, Save, Load, and Hide.

### File: `config/ui/dialogue.layout`

```properties
# === Textbox ===
textBoxX=0.05
textBoxY=0.72
textBoxWidth=0.90
textBoxHeight=0.26
textBoxPadding=16
textBoxAsset=assets/ui/textbox.png
textBoxOpacity=0.92

# === Name Box ===
nameBoxXOffset=20
nameBoxYOffset=-38
nameBoxWidth=200
nameBoxHeight=38
nameTextXOffset=10
nameTextBaselineOffset=24
nameBoxAutoWidth=true
nameBoxColor=#1A1A2EEE
nameTextColor=#FFE8A3
nameTextFontFamily=Georgia
nameTextFontSize=18
nameTextFontWeight=BOLD

# === Dialogue Text ===
dialogueTextHorizontalPadding=20
dialogueTextTopPadding=32
dialogueTextRightPadding=20
dialogueTextBottomPadding=40
dialogueTextColor=#F0F0F0
dialogueTextFontFamily=Noto Sans
dialogueTextFontSize=18

# === Choices ===
choiceXCenter=0.5
choiceYStart=-1
choiceWidthFactor=0.50
choiceHeight=46
choiceGap=8
choiceTextXPadding=16
choiceBackgroundColor=#2A2A4ACC
choiceHoverColor=#3A3A6ACC
choiceTextColor=#FFFFFF
choiceHoverTextColor=#FFE8A3
choiceCornerRadius=6
choiceBorderWidth=1
choiceBorderColor=#4A4A8AAA

# === Textbox Action Buttons (bottom bar) ===
textBoxButton.ids=auto,skip,log,save,load,hide

textBoxButton.auto.label=Auto
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.x=0.52
textBoxButton.auto.y=0.82
textBoxButton.auto.width=0.075
textBoxButton.auto.height=0.14

textBoxButton.skip.label=Skip
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.x=0.60
textBoxButton.skip.y=0.82
textBoxButton.skip.width=0.075
textBoxButton.skip.height=0.14

textBoxButton.log.label=Log
textBoxButton.log.action=history
textBoxButton.log.target=toggle
textBoxButton.log.x=0.68
textBoxButton.log.y=0.82
textBoxButton.log.width=0.075
textBoxButton.log.height=0.14

textBoxButton.save.label=Save
textBoxButton.save.action=save
textBoxButton.save.target=quick
textBoxButton.save.x=0.76
textBoxButton.save.y=0.82
textBoxButton.save.width=0.075
textBoxButton.save.height=0.14

textBoxButton.load.label=Load
textBoxButton.load.action=save
textBoxButton.load.target=menu
textBoxButton.load.x=0.84
textBoxButton.load.y=0.82
textBoxButton.load.width=0.075
textBoxButton.load.height=0.14

textBoxButton.hide.label=Hide
textBoxButton.hide.action=ui
textBoxButton.hide.target=hide
textBoxButton.hide.x=0.92
textBoxButton.hide.y=0.82
textBoxButton.hide.width=0.06
textBoxButton.hide.height=0.14
```

### Key Design Decisions

- **`dialogueTextBottomPadding=40`** — Extra bottom padding prevents dialogue text from
  overlapping the button bar.
- **`nameBoxAutoWidth=true`** — Name box stretches for long character names but never
  shrinks below `nameBoxWidth=200`.
- Buttons are spaced at 0.08 intervals starting at `x=0.52`, forming a compact row.
- Each button is 7.5% of textbox width and 14% of textbox height.

### Runtime Checklist

- [ ] All 6 buttons render along the bottom edge of the textbox
- [ ] Buttons don't overlap dialogue text
- [ ] Auto/Skip toggle their respective modes
- [ ] Log opens the backlog overlay
- [ ] Save triggers quick save
- [ ] Load opens the load menu
- [ ] Hide hides the textbox (click to restore)
- [ ] Name box expands for long names and shrinks back for short ones

### Variations

**Image-based button bar:**
```properties
textBoxButton.auto.label=
textBoxButton.auto.asset=assets/ui/btn_auto.png
textBoxButton.auto.hoverAsset=assets/ui/btn_auto_hover.png
```

**Vertical stack on right edge instead of bottom bar:**
```properties
# Change all buttons to x=0.92, stack vertically:
textBoxButton.auto.x=0.92
textBoxButton.auto.y=0.05
textBoxButton.auto.width=0.06
textBoxButton.auto.height=0.12

textBoxButton.skip.x=0.92
textBoxButton.skip.y=0.19
# ... and so on with y increments of 0.14
```

---

## Scenario 2: Auto-Width Name Box with Styled Text

A dialogue layout where the name box dynamically resizes to fit each speaker's name,
combined with custom font styling for a polished look.

### File: `config/ui/dialogue.layout`

```properties
# === Textbox ===
textBoxX=0.0
textBoxY=0.75
textBoxWidth=1.0
textBoxHeight=0.25
textBoxPadding=16
textBoxColor=#0A0A1ACC

# === Name Box (auto-width) ===
nameBoxXOffset=24
nameBoxYOffset=-36
nameBoxWidth=120
nameBoxHeight=36
nameTextXOffset=14
nameTextBaselineOffset=24
nameBoxAutoWidth=true

nameBoxAsset=assets/ui/namebox_gradient.png
nameTextColor=#FFD700
nameTextFontFamily=Georgia
nameTextFontSize=20
nameTextFontWeight=BOLD
nameBoxOpacity=0.95

# === Dialogue Text ===
dialogueTextHorizontalPadding=28
dialogueTextTopPadding=36
dialogueTextRightPadding=28
dialogueTextBottomPadding=12
dialogueTextColor=#E8E8F0
dialogueTextFontFamily=Noto Sans
dialogueTextFontSize=18
dialogueTextFontWeight=NORMAL

# === Choices ===
choiceXCenter=0.5
choiceYStart=-1
choiceWidthFactor=0.55
choiceHeight=48
choiceGap=8
choiceTextXPadding=18
```

### How Auto-Width Works

The renderer computes the effective name box width each frame:

```
effective = max(nameBoxWidth, computeTextWidth(speakerName, nameFont) + nameTextXOffset * 2)
```

- **Short name** ("Al"): name box stays at `nameBoxWidth=120`
- **Medium name** ("Sakura"): likely still within 120px at 20pt Georgia Bold
- **Long name** ("Professor Henderson"): box expands to ~220px automatically
- **No speaker** (narrator): name box is hidden entirely

The `nameBoxAsset` image stretches horizontally. For best results, use a horizontally
tileable or 9-slice-friendly asset. Solid colors (`nameBoxColor`) stretch perfectly.

### Runtime Checklist

- [ ] Name box expands for long character names
- [ ] Name box uses the minimum width for short names
- [ ] Name text is horizontally centered within the expanded box
- [ ] Name box image stretches cleanly without visible artifacts
- [ ] Narrator lines (no speaker) hide the name box entirely

---

## Scenario 3: Per-Screen Background Overrides

Different menu screens use different background images while sharing the same style.
The `backgroundAsset` on a `.menu` screen overrides the style-level `backgroundAsset`.

### File structure

```
config/menu/
  styles/default.style       # Shared style with a default background
  menus/main.menu            # Uses its own background
  menus/settings.menu        # Uses its own background
  menus/extras.menu          # Falls back to the style's background
```

### File: `config/menu/styles/default.style`

```properties
itemColor=#DCE6F8
itemSelectedColor=#FFE8A3
itemFontFamily=Segoe UI
itemFontWeight=SEMI_BOLD
itemFontSize=26

titleColor=#FFFFFF
titleFontFamily=Georgia
titleFontWeight=BOLD
titleFontSize=48

hintsColor=#667788

# Default background for screens that don't override
backgroundAsset=assets/backgrounds/default_menu_bg.png
backgroundOpacity=1.0
```

### File: `config/menu/menus/main.menu`

```properties
titleText=Echoes of Time
hintsText=Navigate: Up/Down    Select: Enter
layout=default
defaultItemStyle=default
backgroundAsset=assets/backgrounds/title_screen.png

items=new_game,load,extras,settings,quit

item.new_game.label=New Game
item.new_game.action=new_game
item.load.label=Continue
item.load.action=load_menu
item.extras.label=Extras
item.extras.action=open_menu:extras
item.settings.label=Settings
item.settings.action=settings_menu
item.quit.label=Quit
item.quit.action=open_menu:confirm_exit
```

### File: `config/menu/menus/settings.menu`

```properties
titleText=Settings
hintsText=Left/Right: Adjust    Esc: Back
layout=settings
defaultItemStyle=default
backgroundAsset=assets/backgrounds/settings_bg.png

items=text_speed,bgm_volume,sfx_volume,back

item.text_speed.label=Text Speed: {value}
item.bgm_volume.label=BGM Volume: {value}
item.sfx_volume.label=SFX Volume: {value}
item.back.label=Back
item.back.action=back
```

### File: `config/menu/menus/extras.menu`

```properties
# No backgroundAsset here — falls back to default.style's backgroundAsset
titleText=Extras
hintsText=Esc: Back
layout=submenu
defaultItemStyle=default

items=gallery,credits,back

item.gallery.label=Gallery
item.gallery.action=noop
item.gallery.enabled=false
item.credits.label=Credits
item.credits.action=open_menu:credits
item.back.label=Back
item.back.action=main_menu
```

### Priority Order

1. **Screen-level `backgroundAsset`** — highest priority, checked first
2. **Style-level `backgroundAsset`** — used if the screen doesn't define one
3. **Style-level `backgroundColor`** — solid color fallback if no image
4. **Engine default** — black or transparent

### Runtime Checklist

- [ ] Main menu shows `title_screen.png` background
- [ ] Settings shows `settings_bg.png` background
- [ ] Extras shows `default_menu_bg.png` (from the style)
- [ ] Navigating between screens swaps backgrounds correctly
- [ ] No console errors about missing background assets

---

## Scenario 4: Per-Item Font Overrides

Individual menu items can override the style's font family, weight, and size. This is
useful for titles, section headers, disclaimers, and mixed-style menus.

### File: `config/menu/menus/main.menu`

```properties
titleText=My Visual Novel
hintsText=Enter: Select
layout=default
defaultItemStyle=default

items=new_game,continue,divider,extras,settings,quit

# Standard items use the style's font
item.new_game.label=New Game
item.new_game.action=new_game

item.continue.label=Continue
item.continue.action=load_menu

# Section divider — smaller, italic-style font
item.divider.label=── More ──
item.divider.action=noop
item.divider.enabled=false
item.divider.fontSize=16
item.divider.fontWeight=NORMAL
item.divider.fontFamily=Georgia

# Extras with a bolder look
item.extras.label=Extras
item.extras.action=open_menu:extras
item.extras.fontWeight=BOLD
item.extras.fontSize=28

item.settings.label=Settings
item.settings.action=settings_menu

# Quit in a distinct style
item.quit.label=Quit Game
item.quit.action=open_menu:confirm_exit
item.quit.fontFamily=Georgia
item.quit.fontSize=20
item.quit.fontWeight=NORMAL
```

### How Per-Item Fonts Work

The renderer resolves fonts in this priority order:

1. **Per-item `fontFamily` / `fontWeight` / `fontSize`** — if set on the item
2. **Style-level `itemFontFamily` / `itemFontWeight` / `itemFontSize`** — from the item's style
3. **Engine defaults** — system font, NORMAL, 24

Each of the three fields is independent. You can override just `fontSize` on an item
while inheriting `fontFamily` and `fontWeight` from the style.

### Runtime Checklist

- [ ] "New Game" and "Continue" use the default style font
- [ ] Divider text is smaller (16px) and in Georgia
- [ ] "Extras" is larger (28px) and bold
- [ ] "Quit Game" uses Georgia at 20px
- [ ] Font changes don't affect item spacing or alignment
- [ ] Selection highlight applies correctly to all items regardless of font

---

## Scenario 5: Sidebar Menu with Artwork

A main menu where items are positioned in a narrow column on the left, leaving the
right side of the screen for character artwork or a scene illustration.

### File: `config/menu/layouts/sidebar.layout`

```properties
listYStart=0.28
lineHeight=58
listWidthFactor=0.28
textAlign=left
listXCenter=0.16
titleX=0.16
titleY=0.10
hintsBottomMargin=24
```

### File: `config/menu/styles/sidebar.style`

```properties
itemColor=#E0E4F0
itemSelectedColor=#FFD700
itemHoverColor=#FFFFFF
itemDisabledColor=#556677
itemSelectedPrefix=▸ 
itemFontFamily=Segoe UI
itemFontWeight=SEMI_BOLD
itemFontSize=24

titleColor=#FFFFFF
titleFontFamily=Georgia
titleFontWeight=BOLD
titleFontSize=40
titleShadowColor=#000000AA

hintsColor=#889AAA
hintsFontFamily=Segoe UI
hintsFontSize=14

backgroundAsset=assets/backgrounds/title_sidebar.png
backgroundOpacity=1.0
```

### File: `config/menu/menus/main.menu`

```properties
titleText=Starlight Academy
hintsText=↑↓ Navigate    Enter Select
layout=sidebar
defaultItemStyle=sidebar

items=new_game,load,settings,quit

item.new_game.label=New Game
item.new_game.action=new_game
item.load.label=Load Game
item.load.action=load_menu
item.settings.label=Settings
item.settings.action=settings_menu
item.quit.label=Quit
item.quit.action=quit
```

### Design Notes

- **`listXCenter=0.16`** places the item list center at 16% from the left edge
- **`titleX=0.16`** aligns the title with the item list
- **`listWidthFactor=0.28`** keeps the list narrow (28% of viewport width)
- The background image should have artwork on the right side and a darker
  area on the left for readability
- The list is clamped so it never goes off-screen even at narrow viewport widths

### Runtime Checklist

- [ ] Menu items appear in a narrow column on the left
- [ ] Title aligns above the item column
- [ ] Background artwork on the right is fully visible and unobstructed
- [ ] Selection prefix (▸) appears correctly
- [ ] At narrow window sizes, items don't overlap the artwork

---

## Scenario 6: Chapter Select with Scrolling

A chapter select screen with many entries that scroll when the visible limit is reached.

### File: `config/menu/layouts/chapters.layout`

```properties
listYStart=0.22
lineHeight=54
listWidthFactor=0.60
textAlign=left
titleY=0.08
hintsBottomMargin=24
maxVisibleItems=6
```

### File: `config/menu/menus/chapter_select.menu`

```properties
titleText=Chapter Select
hintsText=↑↓ Select Chapter    Enter Play    Esc Back
layout=chapters
defaultItemStyle=submenu
wrapSelection=true

items=ch1,ch2,ch3,ch4,ch5,ch6,ch7,ch8,ch9,ch10,back

item.ch1.label=Chapter 1: The Beginning
item.ch1.action=run_script:scripts/chapter1.vns

item.ch2.label=Chapter 2: First Contact
item.ch2.action=run_script:scripts/chapter2.vns

item.ch3.label=Chapter 3: Rising Tension
item.ch3.action=run_script:scripts/chapter3.vns

item.ch4.label=Chapter 4: The Turning Point
item.ch4.action=run_script:scripts/chapter4.vns

item.ch5.label=Chapter 5: Revelations
item.ch5.action=run_script:scripts/chapter5.vns

item.ch6.label=Chapter 6: The Decision
item.ch6.action=run_script:scripts/chapter6.vns

item.ch7.label=Chapter 7: Consequences
item.ch7.action=run_script:scripts/chapter7.vns

item.ch8.label=Chapter 8: Point of No Return
item.ch8.action=run_script:scripts/chapter8.vns

item.ch9.label=Chapter 9: The Climax
item.ch9.action=run_script:scripts/chapter9.vns

item.ch10.label=Chapter 10: Epilogue
item.ch10.action=run_script:scripts/chapter10.vns

# Locked chapters can use disabled state
# item.ch8.enabled=false
# item.ch9.enabled=false
# item.ch10.enabled=false

item.back.label=Back
item.back.action=back
```

### How `maxVisibleItems` Works

With `maxVisibleItems=6`, only 6 items are visible at a time out of 11 total. As the
player moves the selection cursor down past the 6th visible item, the visible window
scrolls to keep the selected item in view.

```
Visible window (6 items):
┌─────────────────────────────┐
│ Chapter 1: The Beginning    │  ← selected
│ Chapter 2: First Contact    │
│ Chapter 3: Rising Tension   │
│ Chapter 4: The Turning Point│
│ Chapter 5: Revelations      │
│ Chapter 6: The Decision     │
└─────────────────────────────┘
  Chapter 7: Consequences        (hidden, scroll down to see)
  Chapter 8: Point of No Return  (hidden)
  ...
```

With `wrapSelection=true`, pressing Down on "Back" wraps to "Chapter 1".

### Runtime Checklist

- [ ] Only 6 items visible at a time
- [ ] Scrolling works when moving past the last visible item
- [ ] Wrapping from last to first item works
- [ ] Each chapter launches the correct VNS script
- [ ] Disabled chapters are visually distinct and can't be selected

---

## Scenario 7: Confirmation Dialog Submenu

A reusable confirmation dialog pattern — warn the user before destructive actions.

### File: `config/menu/menus/confirm_exit.menu`

```properties
titleText=Exit Game?
hintsText=Enter: Confirm    Esc: Cancel
layout=submenu
defaultItemStyle=submenu

items=warning,yes,no

item.warning.label=All unsaved progress will be lost.
item.warning.action=noop
item.warning.enabled=false
item.warning.fontFamily=Georgia
item.warning.fontSize=18
item.warning.fontWeight=NORMAL

item.yes.label=Yes, Quit
item.yes.action=quit
item.yes.fontWeight=BOLD

item.no.label=No, Return
item.no.action=back
```

### File: `config/menu/menus/confirm_reset.menu`

```properties
titleText=Reset All Settings?
hintsText=Enter: Confirm    Esc: Cancel
layout=submenu
defaultItemStyle=submenu

items=warning,yes,no

item.warning.label=This will restore all settings to their default values.
item.warning.action=noop
item.warning.enabled=false
item.warning.fontSize=18

item.yes.label=Yes, Reset
item.yes.action=settings_menu

item.no.label=Cancel
item.no.action=back
```

### Wiring Confirmation Dialogs

From any menu, route through the confirmation screen:

```properties
# In main.menu — quit goes through confirmation
item.quit.label=Quit
item.quit.action=open_menu:confirm_exit

# In settings.menu — reset goes through confirmation
item.reset.label=Reset All Settings
item.reset.action=open_menu:confirm_reset
```

Register the confirmation screens in your registry:

```properties
# menu.registry
menus=main,load,save,settings,extras,confirm_exit,confirm_reset
```

### Design Notes

- The warning message is a **disabled `noop` item** — it renders as text but can't
  be selected. Use per-item font overrides to style it differently from the buttons.
- "Yes" uses `fontWeight=BOLD` to draw attention.
- "No" uses `action=back` so it returns to wherever the user came from.
- The "Yes" action for confirm_exit is `quit`; for confirm_reset it's `settings_menu`
  (which causes the settings screen to reinitialize).

### Runtime Checklist

- [ ] Selecting "Quit" from main menu opens the confirmation dialog
- [ ] Warning text is visible and non-selectable
- [ ] "Yes, Quit" exits the application
- [ ] "No, Return" goes back to the main menu
- [ ] Pressing Esc also goes back (if default key bindings are active)

---

## Scenario 8: Gallery / Extras Hub with Nested Submenus

A multi-level extras menu structure: Extras → Gallery / Music Room / Credits, where
each sub-screen has its own content.

### File structure

```
config/menu/menus/
  extras.menu              # Hub screen
  gallery.menu             # CG gallery (nested)
  music_room.menu          # Music room (nested)
  credits.menu             # Credits scroll (nested)
```

### File: `config/menu/menus/extras.menu`

```properties
titleText=Extras
hintsText=Enter: Open    Esc: Back
layout=submenu
defaultItemStyle=submenu
backgroundAsset=assets/backgrounds/extras_bg.png

items=gallery,music_room,credits,back

item.gallery.label=CG Gallery
item.gallery.action=open_menu:gallery

item.music_room.label=Music Room
item.music_room.action=open_menu:music_room

item.credits.label=Credits
item.credits.action=open_menu:credits

item.back.label=Return to Main Menu
item.back.action=main_menu
```

### File: `config/menu/menus/gallery.menu`

```properties
extends=extras
titleText=CG Gallery
hintsText=Enter: View    Esc: Back
backgroundAsset=assets/backgrounds/gallery_bg.png
maxVisibleItems=8

items=cg_ch1,cg_ch2,cg_ch3,cg_ch4,cg_ch5,cg_bonus,back

item.cg_ch1.label=Chapter 1 — 3 CGs Unlocked
item.cg_ch1.action=noop
item.cg_ch1.enabled=false

item.cg_ch2.label=Chapter 2 — 2 CGs Unlocked
item.cg_ch2.action=noop
item.cg_ch2.enabled=false

item.cg_ch3.label=Chapter 3 — ???
item.cg_ch3.action=noop
item.cg_ch3.enabled=false

item.cg_ch4.label=Chapter 4 — ???
item.cg_ch4.action=noop
item.cg_ch4.enabled=false

item.cg_ch5.label=Chapter 5 — ???
item.cg_ch5.action=noop
item.cg_ch5.enabled=false

item.cg_bonus.label=Bonus CGs — ???
item.cg_bonus.action=noop
item.cg_bonus.enabled=false

item.back.label=Back to Extras
item.back.action=back
```

### File: `config/menu/menus/music_room.menu`

```properties
extends=extras
titleText=Music Room
hintsText=Enter: Play    Esc: Back

items=track_1,track_2,track_3,track_4,track_5,back

item.track_1.label=Opening Theme — "New Horizons"
item.track_1.action=noop

item.track_2.label=Daily Life — "Sunny Afternoon"
item.track_2.action=noop

item.track_3.label=Tension — "Gathering Storm"
item.track_3.action=noop

item.track_4.label=Romance — "Starlit Promise"
item.track_4.action=noop

item.track_5.label=Ending Theme — "Until Tomorrow"
item.track_5.action=noop

item.back.label=Back to Extras
item.back.action=back
```

### File: `config/menu/menus/credits.menu`

```properties
extends=extras
titleText=Credits
hintsText=Esc: Back

items=line1,line2,line3,line4,line5,line6,back

item.line1.label=Story & Direction
item.line1.action=noop
item.line1.enabled=false
item.line1.fontWeight=BOLD

item.line2.label=  Original Concept by Example Studio
item.line2.action=noop
item.line2.enabled=false
item.line2.fontWeight=NORMAL
item.line2.fontSize=20

item.line3.label=Art & Character Design
item.line3.action=noop
item.line3.enabled=false
item.line3.fontWeight=BOLD

item.line4.label=  Illustrations by Artist Name
item.line4.action=noop
item.line4.enabled=false
item.line4.fontWeight=NORMAL
item.line4.fontSize=20

item.line5.label=Music & Sound
item.line5.action=noop
item.line5.enabled=false
item.line5.fontWeight=BOLD

item.line6.label=  Composed by Composer Name
item.line6.action=noop
item.line6.enabled=false
item.line6.fontWeight=NORMAL
item.line6.fontSize=20

item.back.label=Back to Extras
item.back.action=back
```

### Design Notes

- **`extends=extras`** on sub-screens inherits `layout` and `defaultItemStyle` from
  the extras hub. Only title, hints, background, and items need overriding.
- **Per-screen `backgroundAsset`** gives each sub-screen a distinct atmosphere while
  sharing the same style (fonts, colors, selection behavior).
- **Per-item fonts** in credits create a visual hierarchy: bold category headers with
  normal-weight detail lines at a smaller size.
- **`back` action** returns to extras (not main menu) because it uses the navigation
  stack. The user can then return to main from extras.

### Registry

```properties
menus=main,load,save,settings,extras,gallery,music_room,credits,confirm_exit
layouts=default,submenu,slots
styles=default,submenu,slot
```

### Runtime Checklist

- [ ] Extras hub shows 4 items with its own background
- [ ] Gallery, Music Room, Credits each open with their own background
- [ ] Credits shows bold headers and smaller detail text
- [ ] "Back to Extras" returns to the extras hub, not main menu
- [ ] All sub-screens inherit the submenu layout and style

---

## Scenario 9: Mixed-Style Settings Screen

A settings screen with section headers, value items, and a "Back" button, each with
distinct visual treatment using per-item font overrides.

### File: `config/menu/menus/settings.menu`

```properties
titleText=Settings
hintsText=←→ Adjust    Esc: Back
layout=settings
defaultItemStyle=submenu
wrapSelection=true

items=hdr_display,text_speed,auto_delay,hdr_audio,bgm_vol,sfx_vol,voice_vol,hdr_controls,skip_unread,fullscreen,back

# Section headers — disabled, bold, smaller
item.hdr_display.label=── Display ──
item.hdr_display.action=noop
item.hdr_display.enabled=false
item.hdr_display.fontWeight=BOLD
item.hdr_display.fontSize=18

item.text_speed.label=Text Speed: {value}
item.auto_delay.label=Auto Advance: {value}

item.hdr_audio.label=── Audio ──
item.hdr_audio.action=noop
item.hdr_audio.enabled=false
item.hdr_audio.fontWeight=BOLD
item.hdr_audio.fontSize=18

item.bgm_vol.label=BGM Volume: {value}
item.sfx_vol.label=SFX Volume: {value}
item.voice_vol.label=Voice Volume: {value}

item.hdr_controls.label=── Controls ──
item.hdr_controls.action=noop
item.hdr_controls.enabled=false
item.hdr_controls.fontWeight=BOLD
item.hdr_controls.fontSize=18

item.skip_unread.label=Skip Unread: {value}
item.fullscreen.label=Fullscreen: {value}

item.back.label=Done
item.back.action=back
item.back.fontWeight=BOLD
```

### Design Notes

- Section headers use `noop` + `enabled=false` + a smaller bold font
- The `── Display ──` prefix uses box-drawing characters for visual separation
- `{value}` placeholders are filled by the runtime with the current setting value
- "Done" gets `fontWeight=BOLD` to stand out as the primary action
- `maxVisibleItems` on the layout (from the `settings` built-in) prevents the long
  list from rendering off-screen

---

## Scenario 10: Dialogue with Image Buttons and No Text Labels

A minimal, icon-only textbox button setup using image assets instead of text labels.
Ideal for a clean, modern aesthetic.

### In `config/ui/dialogue.layout`

```properties
# ... (textbox, name box, dialogue text, choices as usual) ...

# === Icon Buttons (top-right corner) ===
textBoxButton.ids=auto,skip,log

textBoxButton.auto.label=
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.asset=assets/ui/icons/ic_auto.png
textBoxButton.auto.hoverAsset=assets/ui/icons/ic_auto_hover.png
textBoxButton.auto.disabledAsset=assets/ui/icons/ic_auto_off.png
textBoxButton.auto.x=0.88
textBoxButton.auto.y=0.04
textBoxButton.auto.width=0.035
textBoxButton.auto.height=0.08

textBoxButton.skip.label=
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.asset=assets/ui/icons/ic_skip.png
textBoxButton.skip.hoverAsset=assets/ui/icons/ic_skip_hover.png
textBoxButton.skip.x=0.92
textBoxButton.skip.y=0.04
textBoxButton.skip.width=0.035
textBoxButton.skip.height=0.08

textBoxButton.log.label=
textBoxButton.log.action=history
textBoxButton.log.target=toggle
textBoxButton.log.asset=assets/ui/icons/ic_log.png
textBoxButton.log.hoverAsset=assets/ui/icons/ic_log_hover.png
textBoxButton.log.x=0.96
textBoxButton.log.y=0.04
textBoxButton.log.width=0.035
textBoxButton.log.height=0.08
```

### Design Notes

- **Empty `label=`** suppresses text rendering — only the image is shown.
- Small button size (3.5% × 8% of textbox) keeps icons unobtrusive.
- `hoverAsset` provides visual feedback on mouse-over.
- `disabledAsset` is optional — only needed if buttons can be conditionally disabled.
- Icons should be designed at a resolution matching your target (e.g., 48×48 for 1080p).

---

## Combining Scenarios

These scenarios are composable. A typical polished game might combine:

- **Scenario 1** (multi-button textbox) + **Scenario 2** (auto-width name box) for
  the dialogue UI
- **Scenario 3** (per-screen backgrounds) + **Scenario 5** (sidebar layout) for the
  main menu
- **Scenario 4** (per-item fonts) + **Scenario 7** (confirmation dialogs) for
  settings and exit flows
- **Scenario 6** (chapter select with scroll) as an extras sub-screen within
  **Scenario 8** (gallery hub)

The properties DSL is intentionally flat and composable. Every key is independent, so
you can pick and choose features without conflicts.

---

## Quick Reference: New Properties

Properties introduced in recent updates that may not appear in older project templates:

| Key | File Type | Description |
|-----|-----------|-------------|
| `nameBoxAutoWidth` | `dialogue.layout` | Dynamic name box width based on speaker name |
| `listXCenter` | `.layout` (menu) | Explicit horizontal center for item list |
| `titleX` | `.layout` (menu) | Explicit horizontal center for title text |
| `maxVisibleItems` | `.layout` (menu) | Scrollable item list limit |
| `backgroundAsset` | `.menu` (screen) | Per-screen background override |
| `item.<id>.fontFamily` | `.menu` (screen) | Per-item font family |
| `item.<id>.fontWeight` | `.menu` (screen) | Per-item font weight |
| `item.<id>.fontSize` | `.menu` (screen) | Per-item font size |

All new properties are **backward-compatible** — omitting them preserves existing
behavior. They can be added incrementally to any existing project.

---

## Related Docs

- [Dialogue Layout & Style](dialogue-layout.md) — textbox geometry, name box, choices
- [Menu Layouts](menu-layouts.md) — list positioning, line height, text alignment
- [Textbox Action Buttons](textbox-action-buttons.md) — button declaration, positioning
- [Menu Actions & Navigation](menu-actions.md) — action types, navigation flow
- [Menu Inheritance & Composition](menu-inheritance.md) — `extends` for screens/layouts/styles
- [Layout DSL Cookbook](layout-dsl-cookbook.md) — complete key reference, runtime checklists
- [Scala DSL Reference](scala-dsl.md) — type-safe Scala builders
- [Fonts & Typography](fonts-typography.md) — font keys, availability
- [Colors & Theming](colors-theming.md) — hex format, color palettes
