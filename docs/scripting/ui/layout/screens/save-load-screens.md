# Save & Load Screen Configuration

Complete guide to configuring save and load menu screens — slot templates, thumbnail previews, placeholder assets, frame overlays, and the save/load item lifecycle.

Model: `modules/core/src/main/java/com/jvn/core/menu/config/MenuItemSpec.java`
Screen: `modules/core/src/main/java/com/jvn/core/menu/config/MenuScreenSpec.java`
Loader: `modules/core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

---

## Overview

Save and load screens in JVN are standard menu screens with special **template items** that the engine replicates for each save slot. Template items can display inline screenshot thumbnails, save metadata, and custom frame overlays. The save/load lifecycle is fully data-driven through `.menu` files.

---

## File Location

```text
config/menu/menus/save.menu
config/menu/menus/load.menu
```

These are regular `.menu` files with save/load-specific features.

---

## Template Item IDs

Certain item IDs are recognized as **slot templates** — the engine generates one copy per existing save slot:

| ID | Auto-detected as template |
|----|--------------------------|
| `save_slot` | Yes |
| `slot` | Yes |
| `entry` | Yes |
| `new_slot` | Yes |
| `new_save` | Yes |
| `new` | Yes |

For any other item ID, set `slotPreviewEnabled=true` explicitly to enable slot preview behavior.

---

## Save Screen Configuration

### Basic Save Screen

```properties
# config/menu/menus/save.menu
titleText=Save Journey
hintsText=Enter: Save    Esc: Back    Del: Delete    R: Rename
layout=slots
defaultItemStyle=slot
wrapSelection=true

items=new_slot,save_slot

# "Create New Save" button at the top
item.new_slot.label=Create New Save
item.new_slot.style=submenu
item.new_slot.action=save_menu

# Template for each existing save slot
item.save_slot.action=save_menu
```

### Save Screen with Slot Previews

```properties
# config/menu/menus/save.menu
titleText=Save Journey
hintsText=Enter: Save    Esc: Back    Del: Delete    R: Rename
layout=slots
defaultItemStyle=slot
wrapSelection=true

items=new_slot,save_slot

item.new_slot.label=Create New Save
item.new_slot.style=submenu
item.new_slot.action=save_menu

item.save_slot.action=save_menu
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_empty.png
item.save_slot.slotPreviewFrameAsset=assets/ui/slot_frame.png
item.save_slot.slotPreviewX=10.0
item.save_slot.slotPreviewY=4.0
item.save_slot.slotPreviewWidth=120.0
item.save_slot.slotPreviewHeight=68.0
```

---

## Load Screen Configuration

### Basic Load Screen

```properties
# config/menu/menus/load.menu
titleText=Load Journey
hintsText=Enter: Load    Esc: Back    Del: Delete    R: Rename
layout=slots
defaultItemStyle=slot
wrapSelection=true

items=save_slot

item.save_slot.action=load_menu
```

### Load Screen with Previews

```properties
# config/menu/menus/load.menu
titleText=Load Journey
hintsText=Enter: Load    Esc: Back    Del: Delete
layout=slots
defaultItemStyle=slot
wrapSelection=true

items=save_slot

item.save_slot.action=load_menu
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_empty.png
item.save_slot.slotPreviewFrameAsset=assets/ui/slot_frame.png
item.save_slot.slotPreviewX=8.0
item.save_slot.slotPreviewY=4.0
item.save_slot.slotPreviewWidth=130.0
item.save_slot.slotPreviewHeight=72.0
```

---

## Slot Preview Properties

All slot preview properties use the `item.<id>.` prefix:

| Property | Type | Description |
|----------|------|-------------|
| `slotPreviewEnabled` | Boolean | Enable inline thumbnail preview |
| `slotPreviewPlaceholderAsset` | String | Image shown when no save screenshot exists |
| `slotPreviewFrameAsset` | String | Frame/border overlay drawn on top of the thumbnail |
| `slotPreviewX` | Double | Thumbnail left position within the item row |
| `slotPreviewY` | Double | Thumbnail top position within the item row |
| `slotPreviewWidth` | Double | Thumbnail width |
| `slotPreviewHeight` | Double | Thumbnail height |

### Coordinate Rules

- Slot preview coordinates are in **pixels** relative to the item's bounding rectangle
- All four position/size values (`X`, `Y`, `Width`, `Height`) must be set together (partial is an error)
- The preview is clipped to the item bounds

### Placeholder vs. Screenshot

- **Empty slot:** Shows `slotPreviewPlaceholderAsset` (or nothing if unset)
- **Occupied slot:** Shows the save screenshot thumbnail
- **Frame overlay:** `slotPreviewFrameAsset` is drawn on top of both

---

## Examples

### Example 1: Save Screen with Large Thumbnails

```properties
# config/menu/menus/save.menu
titleText=Save Progress
hintsText=Enter: Save    Esc: Back    Del: Delete
layout=slots
defaultItemStyle=slot
wrapSelection=true

items=new_slot,save_slot

item.new_slot.label=+ New Save
item.new_slot.style=submenu
item.new_slot.action=save_menu
item.new_slot.icon=assets/ui/icons/plus.png

item.save_slot.action=save_menu
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_placeholder_large.png
item.save_slot.slotPreviewFrameAsset=assets/ui/slot_frame_gold.png
item.save_slot.slotPreviewX=4.0
item.save_slot.slotPreviewY=2.0
item.save_slot.slotPreviewWidth=180.0
item.save_slot.slotPreviewHeight=100.0
```

With a layout that has tall rows:

```properties
# config/menu/layouts/slots.layout
listYStart=0.20
lineHeight=110
listWidthFactor=0.60
textAlign=left
hintsBottomMargin=30
titleY=0.10
```

### Example 2: Compact Load Screen (No Thumbnails)

```properties
# config/menu/menus/load.menu
titleText=Load Game
hintsText=Enter: Load    Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=save_slot

item.save_slot.action=load_menu
item.save_slot.slotPreviewEnabled=false
```

No thumbnails — the engine just shows the save name/date as text.

### Example 3: Load Screen with Custom Per-Slot Button Art

```properties
# config/menu/menus/load.menu
titleText=Load Game
hintsText=Enter: Load    Esc: Back
layout=slots
defaultItemStyle=slot
wrapSelection=true

items=save_slot

item.save_slot.action=load_menu
item.save_slot.bgAsset=assets/ui/save_slot_bg.png
item.save_slot.bgSelectedAsset=assets/ui/save_slot_bg_selected.png
item.save_slot.bgDisabledAsset=assets/ui/save_slot_bg_empty.png
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_empty.png
item.save_slot.slotPreviewX=8.0
item.save_slot.slotPreviewY=6.0
item.save_slot.slotPreviewWidth=100.0
item.save_slot.slotPreviewHeight=56.0
```

### Example 4: Combined Save/Load Screen

A single screen that handles both saving and loading:

```properties
# config/menu/menus/saveload.menu
titleText=Save & Load
hintsText=Enter: Save/Load    Tab: Toggle Mode    Esc: Back
layout=slots
defaultItemStyle=slot
wrapSelection=true

items=new_slot,save_slot,back

item.new_slot.label=Create New Save
item.new_slot.style=submenu
item.new_slot.action=save_menu

item.save_slot.action=save_menu
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_empty.png
item.save_slot.slotPreviewX=8.0
item.save_slot.slotPreviewY=4.0
item.save_slot.slotPreviewWidth=120.0
item.save_slot.slotPreviewHeight=68.0

item.back.label=Back
item.back.style=submenu
item.back.action=back
```

### Example 5: Slot Extras for Custom Metadata

You can attach custom data to slot items using extras:

```properties
item.save_slot.action=save_menu
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_empty.png
item.save_slot.slotPreviewX=8.0
item.save_slot.slotPreviewY=4.0
item.save_slot.slotPreviewWidth=120.0
item.save_slot.slotPreviewHeight=68.0

# Custom extras (preserved, accessible at runtime)
item.save_slot.showTimestamp=true
item.save_slot.showPlaytime=true
item.save_slot.dateFormat=yyyy-MM-dd HH:mm
```

These extras are preserved in `MenuItemSpec.extras()` and accessible from custom runtime code.

---

## Recommended Layout for Save/Load

The built-in `slots` layout is designed for save/load screens:

```properties
# config/menu/layouts/slots.layout
listYStart=0.20
lineHeight=74
listWidthFactor=0.58
textAlign=left
hintsBottomMargin=30
titleY=0.10
```

Key considerations:
- **lineHeight** must be tall enough to contain the slot preview thumbnail plus padding
- **listWidthFactor** should be wide enough for thumbnail + text side by side
- **textAlign=left** keeps labels aligned consistently

### Custom Tall Slots

```properties
# config/menu/layouts/tall_slots.layout
extends=slots
lineHeight=110
listWidthFactor=0.65
```

---

## Recommended Style for Save/Load

```properties
# config/menu/styles/slot.style
extends=submenu
itemFontSize=20
itemColor=#C8D0E8
itemSelectedColor=#FFE8A3
itemDisabledColor=#505068
```

---

## Runtime Validation Checklist

- [ ] Save screen shows "Create New Save" at the top (if configured)
- [ ] Existing saves appear as slot items below
- [ ] Each slot shows the correct save name or date
- [ ] Slot preview thumbnails render (if `slotPreviewEnabled=true`)
- [ ] Empty slots show the placeholder image
- [ ] Frame overlay renders on top of thumbnails
- [ ] Thumbnail position and size are correct within the item row
- [ ] Selecting a slot on the save screen creates/overwrites the save
- [ ] Selecting a slot on the load screen loads the save
- [ ] Delete key removes the save
- [ ] "Back" navigation returns to the previous screen
- [ ] Hints text shows the correct key bindings

---

## Common Mistakes

**Thumbnail larger than lineHeight:**
If `slotPreviewHeight` is greater than `lineHeight`, the thumbnail clips. Ensure `slotPreviewY + slotPreviewHeight <= lineHeight`.

**Partial slot preview bounds:**
Setting only `slotPreviewX` and `slotPreviewY` without `Width` and `Height` triggers the partial bounds warning. All four must be set together.

**Wrong action type:**
Save screens should use `action=save_menu`; load screens should use `action=load_menu`. Using the wrong one causes the slot to behave incorrectly.

**Template ID not recognized:**
If your item ID isn't one of the auto-detected names (`save_slot`, `slot`, `entry`, `new_slot`, `new_save`, `new`), you must set `slotPreviewEnabled=true` explicitly.

**Missing placeholder asset:**
If `slotPreviewPlaceholderAsset` points to a nonexistent file, empty slots show nothing. Check the asset path.

---

## Related Docs

- [Menu Screens](../../menus/menu-screens.md)
- [Menu Layouts](../structure/menu-layouts.md)
- [Menu Actions & Navigation](../structure/menu-actions.md)
- [Assets & Backgrounds](../styling/assets-backgrounds.md)
- [VNS Save System](../../../vns/runtime/vns-save-system.md)
