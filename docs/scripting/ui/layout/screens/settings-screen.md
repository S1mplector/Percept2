# Settings Screen Configuration

Complete guide to configuring the JVN settings menu screen — built-in setting items, dynamic `{value}` placeholders, slider/toggle behavior, and custom settings.

Model: `core/src/main/java/com/jvn/core/menu/config/MenuScreenSpec.java`
Defaults: `core/src/main/java/com/jvn/core/menu/config/MenuProfile.java`

---

## Overview

The settings screen is a standard menu screen whose items represent adjustable game settings. Item labels use `{value}` placeholders that the runtime replaces with the current setting value (e.g., "BGM Volume: 80%"). The engine provides built-in handling for common settings like text speed, volume levels, auto-advance delay, and skip behavior.

---

## File Location

```text
config/menu/menus/settings.menu
```

---

## Basic Settings Screen

```properties
# config/menu/menus/settings.menu
titleText=Settings
hintsText=Up/Down: Select    Left/Right: Adjust    Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=text_speed,bgm_volume,sfx_volume,back

item.text_speed.label=Text Speed: {value}
item.bgm_volume.label=BGM Volume: {value}
item.sfx_volume.label=SFX Volume: {value}

item.back.label=Back
item.back.style=slot
item.back.action=back
```

---

## The {value} Placeholder

The `{value}` token in item labels is replaced at runtime with the current setting value. The format depends on the setting type:

| Setting | Display Format | Example |
|---------|---------------|---------|
| Volume sliders | Percentage | `80%` |
| Text speed | ms per character | `30` |
| Auto delay | Milliseconds | `2000` |
| Boolean toggles | `ON` / `OFF` | `ON` |

```properties
# At runtime, the player sees:
# "Text Speed: 30"     (not "Text Speed: {value}")
# "BGM Volume: 80%"    (not "BGM Volume: {value}")
item.text_speed.label=Text Speed: {value}
item.bgm_volume.label=BGM Volume: {value}
```

---

## Built-in Setting Item IDs

The engine recognizes these item IDs and automatically binds them to the corresponding game setting:

### Audio Settings

| Item ID | Setting | Adjust | Range |
|---------|---------|--------|-------|
| `bgm_volume` | Background music volume | Left/Right | 0--100% |
| `sfx_volume` | Sound effects volume | Left/Right | 0--100% |
| `voice_volume` | Voice audio volume | Left/Right | 0--100% |

```properties
item.bgm_volume.label=BGM Volume: {value}
item.sfx_volume.label=SFX Volume: {value}
item.voice_volume.label=Voice Volume: {value}
```

### Text Settings

| Item ID | Setting | Adjust | Range |
|---------|---------|--------|-------|
| `text_speed` | Text reveal speed (ms/char) | Left/Right | Numeric |
| `auto_play_delay` | Auto-advance delay (ms) | Left/Right | Numeric |
| `skip_unread` | Skip unread text | Left/Right | ON/OFF |
| `skip_after_choices` | Continue skipping after choices | Left/Right | ON/OFF |
| `click_reveal_before_advance` | Click reveals text before advancing | Left/Right | ON/OFF |

```properties
item.text_speed.label=Text Speed: {value}
item.auto_play_delay.label=Auto Advance Delay: {value}
item.skip_unread.label=Skip Unread Text: {value}
item.skip_after_choices.label=Skip After Choices: {value}
item.click_reveal_before_advance.label=Click Reveals Before Advance: {value}
```

### Physics Settings (Advanced)

| Item ID | Setting | Adjust | Range |
|---------|---------|--------|-------|
| `physics_fixed_step` | Physics fixed timestep | Left/Right | Numeric |
| `physics_max_substeps` | Physics max substeps | Left/Right | Numeric |
| `physics_default_friction` | Default friction coefficient | Left/Right | Numeric |

```properties
item.physics_fixed_step.label=Physics Fixed Step: {value}
item.physics_max_substeps.label=Physics Max Substeps: {value}
item.physics_default_friction.label=Physics Friction: {value}
```

### Input Settings

| Item ID | Setting | Adjust | Range |
|---------|---------|--------|-------|
| `input_profile` | Active input profile | Left/Right | Profile names |

```properties
item.input_profile.label=Input Profile: {value}
```

---

## Examples

### Example 1: Standard Settings Screen

```properties
# config/menu/menus/settings.menu
titleText=Settings
hintsText=Up/Down: Select    Left/Right: Adjust    Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=text_speed,bgm_volume,sfx_volume,voice_volume,auto_play_delay,skip_unread,skip_after_choices,click_reveal_before_advance,back

item.text_speed.label=Text Speed: {value}
item.bgm_volume.label=BGM Volume: {value}
item.sfx_volume.label=SFX Volume: {value}
item.voice_volume.label=Voice Volume: {value}
item.auto_play_delay.label=Auto Advance Delay: {value}
item.skip_unread.label=Skip Unread Text: {value}
item.skip_after_choices.label=Skip After Choices: {value}
item.click_reveal_before_advance.label=Click Reveals Before Advance: {value}

item.back.label=Back
item.back.style=slot
item.back.action=back
```

### Example 2: Minimal Settings (Audio Only)

```properties
# config/menu/menus/settings.menu
titleText=Audio Settings
hintsText=Left/Right: Adjust    Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=bgm_volume,sfx_volume,back

item.bgm_volume.label=Music: {value}
item.sfx_volume.label=Sound Effects: {value}

item.back.label=Done
item.back.action=back
```

### Example 3: Categorized Settings with Section Headers

Use `noop` disabled items as visual section dividers:

```properties
# config/menu/menus/settings.menu
titleText=Settings
hintsText=Up/Down: Select    Left/Right: Adjust    Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=audio_header,bgm_volume,sfx_volume,voice_volume,text_header,text_speed,auto_play_delay,skip_unread,back

# Section header (non-interactive)
item.audio_header.label=── Audio ──
item.audio_header.action=noop
item.audio_header.enabled=false

item.bgm_volume.label=Music Volume: {value}
item.sfx_volume.label=SFX Volume: {value}
item.voice_volume.label=Voice Volume: {value}

# Section header
item.text_header.label=── Text ──
item.text_header.action=noop
item.text_header.enabled=false

item.text_speed.label=Text Speed: {value}
item.auto_play_delay.label=Auto Advance: {value}
item.skip_unread.label=Skip Unread: {value}

item.back.label=Back
item.back.style=slot
item.back.action=back
```

### Example 4: Settings with Custom Labels

You can use any label text; the `{value}` placeholder is replaced regardless:

```properties
item.bgm_volume.label=🎵 Background Music ({value})
item.sfx_volume.label=🔊 Sound Effects ({value})
item.text_speed.label=⏱ Reading Speed: {value} ms/char
```

### Example 5: Settings Accessible from In-Game Pause Menu

```properties
# config/menu/menus/pause_settings.menu
extends=settings
titleText=Pause Settings
hintsText=Esc: Resume Game

# Remove physics/input settings; keep only gameplay-relevant ones
items=text_speed,bgm_volume,sfx_volume,auto_play_delay,resume

item.resume.label=Resume Game
item.resume.style=slot
item.resume.action=back
```

---

## Custom Settings

For settings not built into the engine, use custom item IDs with extras:

```properties
item.difficulty.label=Difficulty: {value}
item.difficulty.action=noop
item.difficulty.settingKey=game.difficulty
item.difficulty.settingType=enum
item.difficulty.settingValues=Easy,Normal,Hard
```

The `settingKey`, `settingType`, and `settingValues` keys are preserved as extras in `MenuItemSpec.extras()`. Your runtime code reads these extras and handles the setting adjustment:

```java
MenuItemSpec item = screen.items().get(idx);
String settingKey = item.extras().get("settingKey");
String settingType = item.extras().get("settingType");
// Handle custom setting logic
```

---

## Recommended Layout and Style

The built-in `submenu` layout works well for settings:

```properties
# config/menu/layouts/submenu.layout
listYStart=0.24
lineHeight=62
listWidthFactor=0.64
textAlign=left
hintsBottomMargin=30
titleY=0.11
```

Left alignment keeps `{value}` placeholders aligned when setting names have different lengths.

The `submenu` style provides appropriate colors:

```properties
# config/menu/styles/submenu.style
# (uses engine defaults or extends=default)
```

---

## Runtime Validation Checklist

- [ ] Settings screen opens from the main menu or pause menu
- [ ] All `{value}` placeholders are replaced with actual values (no literal `{value}` text)
- [ ] Left/Right keys adjust slider values (volume, speed)
- [ ] Left/Right keys toggle boolean values (ON/OFF)
- [ ] Adjusted values are reflected immediately in the label
- [ ] Audio changes take effect immediately (BGM volume change is audible)
- [ ] Text speed changes affect the next dialogue line
- [ ] Settings persist when navigating away and returning
- [ ] Settings persist after quitting and relaunching
- [ ] Disabled section header items are not selectable
- [ ] "Back" navigation returns to the previous screen
- [ ] Hints text shows the correct key bindings

---

## Common Mistakes

**Literal {value} shown instead of the actual value:**
The item ID must match a built-in setting name, or your custom runtime code must handle the replacement. If neither applies, `{value}` appears literally.

**Wrong item ID:**
`bgm` won't work; it must be `bgm_volume`. Check the built-in ID table above.

**Settings don't persist:**
Settings are saved in the game's save data. If the save system isn't configured, settings reset on restart. Check the save system configuration.

**Left/Right doesn't adjust:**
Ensure the item ID matches a recognized setting. Custom settings require runtime handler code.

---

## Related Docs

- [Menu Screens](../menus-submenus/menu-screens.md)
- [Menu Actions & Navigation](menu-actions.md)
- [VNS Settings & Modes](../vns/vns-settings-modes.md)
- [VNS Save System](../vns/vns-save-system.md)
