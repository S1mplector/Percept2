# Assets & Backgrounds

Complete guide to using image assets in JVN layout and menu DSLs — background images, button skins, textbox art, slot preview thumbnails, asset path conventions, and fallback behavior.

---

## Overview

JVN layout files reference image assets by relative path for backgrounds, button skins, textbox overlays, name box art, choice button art, and save slot thumbnails. All asset paths are relative to the project root. The engine loads assets at runtime startup and falls back to solid colors when assets are missing.

---

## Asset Path Convention

All asset paths in layout and style files are **relative to the project root**:

```properties
backgroundAsset=assets/ui/menu/bg.png
textBoxAsset=assets/ui/textbox.png
buttonAsset=assets/ui/menu/btn.png
```

The directory structure typically looks like:

```text
your-project/
├── assets/
│   ├── ui/
│   │   ├── textbox.png
│   │   ├── namebox.png
│   │   ├── choice.png
│   │   ├── choice_hover.png
│   │   ├── choice_selected.png
│   │   ├── slot_empty.png
│   │   ├── slot_frame.png
│   │   └── menu/
│   │       ├── bg.png
│   │       ├── btn.png
│   │       ├── btn_selected.png
│   │       ├── btn_hover.png
│   │       └── btn_disabled.png
│   └── backgrounds/
│       ├── title.png
│       └── field.png
├── config/
│   ├── ui/
│   │   └── dialogue.layout
│   └── menu/
│       ├── styles/
│       │   └── default.style
│       └── ...
```

### Supported Formats

The engine supports standard image formats through the JVM image pipeline:

- **PNG** (recommended for UI elements — supports transparency)
- **JPEG/JPG** (good for photographs/backgrounds — no transparency)
- **BMP** (uncompressed — large files, generally avoid)
- **GIF** (static only — animations not supported in layout context)

### Path Rules

- Paths are **case-sensitive** on Linux/macOS. `assets/ui/Btn.png` and `assets/ui/btn.png` are different files.
- Use forward slashes `/` even on Windows.
- No leading slash — paths are relative: `assets/ui/btn.png`, not `/assets/ui/btn.png`.
- Spaces in paths are supported but discouraged.

---

## All Asset Keys by DSL Type

### dialogue.layout

| Key | Purpose | Fallback |
|-----|---------|----------|
| `textBoxAsset` | Textbox background image | `textBoxColor` solid fill |
| `nameBoxAsset` | Name plate background image | `nameBoxColor` solid fill |
| `choiceButtonAsset` | Choice button normal state | `choiceBackgroundColor` fill |
| `choiceButtonHoverAsset` | Choice button hover state | `choiceHoverColor` fill |
| `choiceButtonSelectedAsset` | Choice button selected state | `choiceSelectedColor` fill |
| `choiceButtonDisabledAsset` | Choice button disabled state | `choiceDisabledColor` fill |

### dialogue.layout — Textbox Action Buttons

| Key Pattern | Purpose |
|-------------|---------|
| `textBoxButton.<id>.asset` | Button normal state image |
| `textBoxButton.<id>.hoverAsset` | Button hover state image |
| `textBoxButton.<id>.disabledAsset` | Button disabled state image |

### Menu .style

| Key | Purpose | Fallback |
|-----|---------|----------|
| `backgroundAsset` | Full-screen background image | `backgroundColor` solid fill |
| `buttonAsset` | Menu item button normal state | Text-only rendering |
| `buttonSelectedAsset` | Menu item button selected state | Text-only rendering |
| `buttonHoverAsset` | Menu item button hover state | Text-only rendering |
| `buttonDisabledAsset` | Menu item button disabled state | Text-only rendering |

### Menu .menu — Per-Item Assets

| Key Pattern | Purpose |
|-------------|---------|
| `item.<id>.icon` | Item icon image |
| `item.<id>.bgAsset` | Per-item button background |
| `item.<id>.bgSelectedAsset` | Per-item selected button background |
| `item.<id>.bgDisabledAsset` | Per-item disabled button background |
| `item.<id>.slotPreviewPlaceholderAsset` | Empty save slot placeholder |
| `item.<id>.slotPreviewFrameAsset` | Save slot thumbnail frame overlay |

### Button Layout (.buttonlayout)

| Key Pattern | Purpose |
|-------------|---------|
| `button.<id>.asset` | Button normal state image |
| `button.<id>.hoverAsset` | Button hover state image |
| `button.<id>.disabledAsset` | Button disabled state image |

---

## Background Images

### Menu Background

The most common asset — a full-screen image behind the menu:

```properties
# config/menu/styles/default.style
backgroundAsset=assets/backgrounds/title.png
backgroundColor=#050B16
backgroundOpacity=1.0
```

**Behavior:**
- The image is scaled to fill the entire viewport
- `backgroundColor` renders behind the image (visible if image has transparency or is missing)
- `backgroundOpacity` affects the overall background layer

**Examples:**

```properties
# Photo background at full opacity
backgroundAsset=assets/backgrounds/sakura_field.jpg
backgroundOpacity=1.0

# Dark overlay on a background
backgroundAsset=assets/backgrounds/castle.png
backgroundColor=#000000
backgroundOpacity=0.8

# Solid color only (no image)
backgroundColor=#1A1A2E
backgroundOpacity=1.0

# No background (transparent — shows whatever is behind)
backgroundColor=#00000000
backgroundOpacity=0.0
```

### Different Backgrounds per Menu Screen

Each style can have a different background. Assign different styles to different screens:

```properties
# config/menu/styles/main_style.style
backgroundAsset=assets/backgrounds/title.png

# config/menu/styles/settings_style.style
backgroundAsset=assets/backgrounds/settings_blur.png

# config/menu/styles/load_style.style
backgroundAsset=assets/backgrounds/library.png
```

```properties
# config/menu/menus/main.menu
defaultItemStyle=main_style

# config/menu/menus/settings.menu
defaultItemStyle=settings_style

# config/menu/menus/load.menu
defaultItemStyle=load_style
```

---

## Textbox Assets

### Textbox Background Image

```properties
# config/ui/dialogue.layout
textBoxAsset=assets/ui/textbox.png
textBoxColor=#000000CC
textBoxOpacity=0.9
```

**Behavior:**
- Image is stretched to fill the textbox rectangle
- `textBoxColor` renders behind the image
- For 9-slice compatible images, the engine stretches the image to fit

**Design tips:**
- Use PNG with transparency for elegant textbox borders
- Design at the aspect ratio matching your `textBoxWidth:textBoxHeight` ratio
- Include subtle gradient or border in the image itself

### Name Box Background Image

```properties
nameBoxAsset=assets/ui/namebox.png
nameBoxColor=#1A1A2E
```

Design the name box image at the aspect ratio matching `nameBoxWidth:nameBoxHeight`.

---

## Button Skin Assets

### Menu Button Skins (4 States)

```properties
# config/menu/styles/default.style
buttonAsset=assets/ui/menu/btn_normal.png
buttonSelectedAsset=assets/ui/menu/btn_selected.png
buttonHoverAsset=assets/ui/menu/btn_hover.png
buttonDisabledAsset=assets/ui/menu/btn_disabled.png
buttonTextPaddingX=28.0
buttonTextPaddingY=4.0
```

**Design guidelines:**
- Create all four states at the same dimensions
- Button images are stretched to fill the item area (width from `listWidthFactor`, height from `lineHeight`)
- Include visual cues in the image: glow for selected, dim for disabled
- `buttonTextPaddingX/Y` offsets the text from the button image edges

### Choice Button Skins (4 States)

```properties
# config/ui/dialogue.layout
choiceButtonAsset=assets/ui/choice/normal.png
choiceButtonHoverAsset=assets/ui/choice/hover.png
choiceButtonSelectedAsset=assets/ui/choice/selected.png
choiceButtonDisabledAsset=assets/ui/choice/disabled.png
```

Design at the aspect ratio matching `choiceWidthFactor * viewport : choiceHeight`.

### Per-Item Button Overrides

Individual menu items can override the style's button assets:

```properties
# config/menu/menus/main.menu
item.new_game.bgAsset=assets/ui/menu/btn_primary.png
item.new_game.bgSelectedAsset=assets/ui/menu/btn_primary_selected.png

item.quit.bgAsset=assets/ui/menu/btn_danger.png
item.quit.bgSelectedAsset=assets/ui/menu/btn_danger_selected.png
```

---

## Save Slot Assets

### Placeholder Image

Shown for empty save slots:

```properties
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_empty.png
```

Design a neutral "no save" image at the preview thumbnail dimensions.

### Frame Overlay

Drawn on top of the slot thumbnail (both empty and filled):

```properties
item.save_slot.slotPreviewFrameAsset=assets/ui/slot_frame.png
```

Design with transparency — only the frame/border should be opaque. The inner area should be transparent to show the thumbnail.

### Complete Slot Configuration

```properties
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_empty.png
item.save_slot.slotPreviewFrameAsset=assets/ui/slot_frame_gold.png
item.save_slot.slotPreviewX=8.0
item.save_slot.slotPreviewY=4.0
item.save_slot.slotPreviewWidth=120.0
item.save_slot.slotPreviewHeight=68.0
```

---

## Examples

### Example 1: Fully Skinned Main Menu

```properties
# config/menu/styles/skinned.style
backgroundAsset=assets/ui/menu/bg_main.jpg
backgroundOpacity=1.0

buttonAsset=assets/ui/menu/btn_ornate.png
buttonSelectedAsset=assets/ui/menu/btn_ornate_glow.png
buttonHoverAsset=assets/ui/menu/btn_ornate_hover.png
buttonDisabledAsset=assets/ui/menu/btn_ornate_dim.png
buttonTextPaddingX=40.0
buttonTextPaddingY=6.0

itemColor=#FFFFFF
itemSelectedColor=#FFD700
itemFontFamily=Georgia
itemFontSize=24

titleColor=#FFD700
titleFontFamily=Georgia
titleFontSize=48
```

### Example 2: Skinned Dialogue Box

```properties
# config/ui/dialogue.layout
textBoxAsset=assets/ui/textbox_ornate.png
textBoxOpacity=0.95
nameBoxAsset=assets/ui/namebox_ornate.png
nameTextColor=#FFE8A3

choiceButtonAsset=assets/ui/choice_ornate.png
choiceButtonHoverAsset=assets/ui/choice_ornate_hover.png
choiceButtonSelectedAsset=assets/ui/choice_ornate_sel.png
```

### Example 3: Color-Only (No Assets)

```properties
# config/menu/styles/minimal.style
# No asset keys at all — pure color rendering
itemColor=#CCCCCC
itemSelectedColor=#FFFFFF
backgroundColor=#1A1A2E
backgroundOpacity=1.0
```

```properties
# config/ui/dialogue.layout
# No asset keys — color fills only
textBoxColor=#0A0A1ADD
nameBoxColor=#1A1A3AEE
choiceBackgroundColor=#2A2A4A
```

---

## Fallback Behavior

When an asset is missing or fails to load:

| Scenario | Fallback |
|----------|----------|
| `backgroundAsset` not found | `backgroundColor` solid fill |
| `textBoxAsset` not found | `textBoxColor` solid fill |
| `nameBoxAsset` not found | `nameBoxColor` solid fill |
| `buttonAsset` not found | Text-only rendering (no button background) |
| `choiceButtonAsset` not found | `choiceBackgroundColor` fill |
| `slotPreviewPlaceholderAsset` not found | Empty area (no placeholder) |
| `slotPreviewFrameAsset` not found | No frame overlay |

The engine logs asset loading errors to the console. Check for messages like:
```text
Failed to load asset: assets/ui/missing_file.png
```

---

## Asset Design Tips

1. **Use PNG for UI elements** — transparency is essential for textboxes, buttons, and frames.
2. **Use JPEG for backgrounds** — smaller file size, no transparency needed.
3. **Design at target resolution** — a 1920x1080 background looks best at that resolution.
4. **Match aspect ratios** — button images should match the aspect ratio of their configured area.
5. **Include state cues in images** — glow for selected, dim for disabled, brighter for hover.
6. **Keep file sizes reasonable** — large PNG files slow down loading. Optimize with tools like `pngquant` or `optipng`.
7. **Organize by purpose** — `assets/ui/menu/`, `assets/ui/dialogue/`, `assets/backgrounds/`.

---

## Runtime Validation Checklist

- [ ] All asset images load without console errors
- [ ] Background image fills the screen without stretching artifacts
- [ ] Textbox image renders with correct transparency
- [ ] Button images switch between states (normal, hover, selected, disabled)
- [ ] Name box image is positioned correctly relative to the textbox
- [ ] Choice button images align with choice text
- [ ] Save slot placeholder images appear for empty slots
- [ ] Save slot frame overlays render on top of thumbnails
- [ ] Per-item button overrides display the correct images
- [ ] Missing assets fall back to colors gracefully (no crashes)
- [ ] Images look correct at the target resolution

---

## Common Mistakes

**Wrong path:**
```properties
# Wrong — absolute path
backgroundAsset=/Users/me/project/assets/bg.png

# Correct — relative to project root
backgroundAsset=assets/bg.png
```

**Case mismatch (Linux/macOS):**
```properties
# File on disk: assets/ui/TextBox.png
textBoxAsset=assets/ui/textbox.png    # won't find it on case-sensitive OS
```

**Wrong extension:**
```properties
# File is .jpeg but you wrote .jpg
backgroundAsset=assets/bg.jpeg    # try both if unsure
```

**Transparent JPEG:**
JPEG doesn't support transparency. If you need a semi-transparent textbox image, use PNG.

**Oversized assets:**
A 4096x4096 PNG for a small button wastes memory. Size assets appropriately for their display size.

---

## Related Docs

- [Dialogue Layout & Style](../components/dialogue-layout.md)
- [Menu Styles](../../menus/menu-styles.md)
- [Choice Buttons](../components/choice-buttons.md)
- [Save & Load Screens](../screens/save-load-screens.md)
- [Menu Button Layouts](../structure/menu-button-layouts.md)
- [Colors & Theming](colors-theming.md)
