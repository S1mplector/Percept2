# Menu Inheritance & Composition

Complete guide to inheritance in JVN menu screens, layouts, and styles — using `extends` to share configuration, override selectively, and build menu variants without duplication.

Loader: `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

---

## Overview

All three menu file types — screens (`.menu`), layouts (`.layout`), and styles (`.style`) — support the `extends` keyword. A child file inherits every property from its parent and can override individual values. This lets you build families of related menus without copy-pasting.

---

## Syntax

Add `extends=<parentId>` as the first meaningful line in any menu file:

```properties
extends=parent_id
# Then override only what you need
```

The parent ID must match the filename (minus extension) of another file of the same type.

---

## Screen Inheritance

### Basic Example

A credits screen that inherits layout and style from the extras screen:

```properties
# config/menu/menus/extras.menu
titleText=Extras
hintsText=Enter: Select    Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=gallery,music_room,credits,back

item.gallery.label=Gallery
item.gallery.action=open_menu:gallery

item.music_room.label=Music Room
item.music_room.action=noop
item.music_room.enabled=false

item.credits.label=Credits
item.credits.action=open_menu:credits

item.back.label=Return to Main Menu
item.back.action=main_menu
```

```properties
# config/menu/menus/credits.menu
extends=extras

# Override title and hints
titleText=Credits
hintsText=Esc: Back

# Replace items entirely
items=line1,line2,line3,back

item.line1.label=JVN Engine Team
item.line1.action=noop
item.line1.enabled=false

item.line2.label=Runtime, Editor, and VNS by JVN contributors
item.line2.action=noop
item.line2.enabled=false

item.line3.label=Thanks for building with JVN.
item.line3.action=noop
item.line3.enabled=false

item.back.label=Back
item.back.action=back
```

**What the credits screen inherits from extras:**
- `layout=submenu` (not overridden → inherited)
- `defaultItemStyle=submenu` (not overridden → inherited)
- `wrapSelection=true` (not overridden → inherited)

**What it overrides:**
- `titleText`, `hintsText` — new values
- `items` — completely new list
- All `item.*` entries — new definitions

### Inheriting Items

If the child does **not** declare `items=`, it inherits the parent's item list and can override individual items:

```properties
# config/menu/menus/main_variant.menu
extends=main

# Don't declare items= → inherit parent's item list
# Override just the quit item's label
item.quit.label=Exit to Desktop
```

Result: all of main's items appear, but `quit` shows "Exit to Desktop" instead of the original label.

### Overriding a Single Item Property

```properties
# config/menu/menus/hard_mode_main.menu
extends=main

# Disable the load button
item.load.enabled=false

# Change the new game action
item.new_game.action=run_script:scripts/hard_mode.vns
item.new_game.label=New Game (Hard)
```

### Adding Items to an Inherited List

To add items, you must redeclare the full `items=` list:

```properties
extends=main
items=new_game,load,settings,bonus,quit

# New item not in parent
item.bonus.label=Bonus Chapter
item.bonus.action=run_script:scripts/bonus.vns
```

---

## Layout Inheritance

### Basic Example

```properties
# config/menu/layouts/compact.layout
extends=default

# Override only line height and width
lineHeight=36
listWidthFactor=0.50
```

**Inherited from `default`:**
- `listYStart=0.34`
- `textAlign=center`
- `hintsBottomMargin=36`
- `titleY=0.14`

**Overridden:**
- `lineHeight=36` (was 68)
- `listWidthFactor=0.50` (was 0.44)

### More Layout Variants

```properties
# config/menu/layouts/wide_submenu.layout
extends=submenu
listWidthFactor=0.85
```

```properties
# config/menu/layouts/no_title.layout
extends=default
# Omit titleY to remove the title
# (titleY is optional — setting it is an override, not inheriting null)
```

Note: `titleY` is optional. If the parent has it set, the child inherits it. To remove it in the child, you cannot "unset" it in properties format — you would need a separate layout file without `titleY`.

---

## Style Inheritance

### Basic Example

A "danger" style variant for destructive actions:

```properties
# config/menu/styles/danger.style
extends=default

itemColor=#FF4444
itemSelectedColor=#FF0000
itemHoverColor=#FF6666
itemDisabledColor=#993333
```

**Inherited from `default`:**
- All font settings, prefixes, shadow, opacity, button assets, title/hints/background styling

**Overridden:**
- The four item color properties

### Theme Variants

```properties
# config/menu/styles/dark.style
extends=default
itemColor=#B8C4D8
itemSelectedColor=#FF6B6B
backgroundColor=#0A0A0F
backgroundOpacity=1.0
titleColor=#FF4444
hintsColor=#667788
```

```properties
# config/menu/styles/dark_neon.style
extends=dark
itemColor=#00CCFF
itemSelectedColor=#FF00FF
titleColor=#00FFCC
itemShadowColor=#FF00FF44
```

Chain: `default` → `dark` → `dark_neon`. Each level overrides only what it needs.

### Per-Item Style Override

Inheritance works at the file level. For per-item overrides within a single screen, use the item's `style` field:

```properties
# In the .menu file
item.quit.style=danger

# item.quit will use danger.style instead of the screen's defaultItemStyle
```

---

## Inheritance Chains

You can chain inheritance multiple levels deep:

```text
default.style
  └── dark.style (extends=default)
        └── dark_neon.style (extends=dark)
              └── dark_neon_soft.style (extends=dark_neon)
```

Each file only contains its overrides. The engine resolves the full chain at load time.

### Resolution Order

1. Load the child file
2. Find `extends=parentId`
3. Recursively load the parent (which may itself have `extends`)
4. Merge: child values override parent values; unset child values inherit parent values
5. Return the fully resolved spec

---

## Circular Inheritance Detection

The engine detects and breaks circular `extends` chains:

```properties
# a.style
extends=b

# b.style
extends=a
```

Console output: `Circular menu inheritance detected at screen 'a'`

The engine breaks the cycle and uses the built-in default as the base.

---

## Multi-Level Example: Full Game

```properties
# === Layouts ===

# config/menu/layouts/default.layout
listYStart=0.34
lineHeight=68
listWidthFactor=0.44
textAlign=center
hintsBottomMargin=36
titleY=0.14

# config/menu/layouts/submenu.layout
extends=default
listYStart=0.24
lineHeight=62
listWidthFactor=0.64
textAlign=left
titleY=0.11

# config/menu/layouts/slots.layout
extends=submenu
listYStart=0.20
lineHeight=74
listWidthFactor=0.58
titleY=0.10

# config/menu/layouts/compact_slots.layout
extends=slots
lineHeight=54
listWidthFactor=0.70
```

```properties
# === Styles ===

# config/menu/styles/default.style
itemColor=#DCE6F8
itemSelectedColor=#FFE8A3
itemSelectedPrefix=▶ 
itemFontFamily=Segoe UI
itemFontSize=28
titleColor=#F2F7FF
titleFontSize=56
backgroundAsset=assets/bg/title.png

# config/menu/styles/submenu.style
extends=default
itemFontSize=24
titleFontSize=40
itemSelectedPrefix=→ 

# config/menu/styles/slot.style
extends=submenu
itemFontSize=20
```

```properties
# === Screens ===

# config/menu/menus/main.menu
titleText=My Game
layout=default
defaultItemStyle=default
items=new_game,load,settings,quit
# ... items ...

# config/menu/menus/settings.menu
extends=main
titleText=Settings
hintsText=↑↓ Select    ←→ Adjust    Esc Back
layout=submenu
defaultItemStyle=submenu
items=text_speed,bgm_volume,sfx_volume,back
# ... items ...

# config/menu/menus/load.menu
extends=main
titleText=Load Game
layout=slots
defaultItemStyle=slot
items=save_slot
# ... items ...
```

---

## When to Use Inheritance vs. Separate Files

| Scenario | Approach |
|----------|----------|
| Two styles differ by 2-3 color values | Use `extends` |
| Two layouts differ by one key | Use `extends` |
| Two completely different visual themes | Separate files, no `extends` |
| A confirmation dialog shares a parent's layout/style | Use `extends` on the screen |
| Multiple screens share the same layout | Just reference the same `layout=` ID |

**Rule of thumb:** If the child overrides less than half the parent's properties, use `extends`. If it overrides most of them, a standalone file is cleaner.

---

## Runtime Validation Checklist

- [ ] Child files load without "extends missing" warnings
- [ ] Inherited properties (not overridden) appear correctly in runtime
- [ ] Overridden properties take effect
- [ ] Multi-level chains resolve fully (no missing intermediate values)
- [ ] No "circular inheritance" warnings in the console
- [ ] Per-item style overrides apply to the correct items

---

## Common Mistakes

**Parent file doesn't exist:**
```properties
extends=mythical_parent
```
Console: `Menu screen 'xyz' extends missing menu 'mythical_parent'`

**Extends in wrong file type:**
A `.menu` file can only extend another `.menu` file. A `.style` can only extend another `.style`. Cross-type inheritance is not supported.

**Forgetting to register the parent:**
If the parent file exists but its ID isn't in the registry or discoverable path, the engine can't find it. Ensure both parent and child are registered or auto-discoverable.

**Expecting "unset" behavior:**
Properties format has no way to "unset" an inherited value. Once the parent sets `titleY=0.14`, the child inherits it. You can override it to a different value, but you can't remove it.

---

## Related Docs

- [Menu Screens](../../menus/menu-screens.md)
- [Menu Layouts](menu-layouts.md)
- [Menu Styles](../../menus/menu-styles.md)
- [Menu Registry & File Discovery](menu-registry.md)
- [Text-First Layout Workflow](../workflow/text-first-layout-workflow.md)
