# Menu Button Layouts

Complete reference for per-button positional layouts — the `.buttonlayout` properties format for placing menu buttons at exact coordinates with custom assets.

Model: `core/src/main/java/com/jvn/core/menu/config/MenuButtonLayoutSpec.java`
Loader: `core/src/main/java/com/jvn/core/menu/config/MenuButtonLayoutLoader.java`

---

## Overview

Button layouts provide **explicit per-button positioning** for menu screens. Instead of relying on the list-based flow of `MenuLayoutSpec`, you can place each button at a specific position with custom dimensions and assets. This is useful for:

- Title screens with custom button artwork at specific positions
- Non-list-based menu designs (radial, grid, scattered)
- Resolution-specific button placement
- Screens with mixed button shapes and sizes

---

## File Format

Button layouts use the `.properties` format:

```properties
# Header
menuId=main
resolution=1920x1080
menuType=main

# Button IDs
button.ids=new_game,load,settings,quit

# Per-button properties
button.new_game.label=New Game
button.new_game.boundsX=0.25
button.new_game.boundsY=0.30
button.new_game.boundsW=0.50
button.new_game.boundsH=0.08
button.new_game.tag=primary
button.new_game.asset=assets/ui/btn_main.png
button.new_game.hoverAsset=assets/ui/btn_main_hover.png
button.new_game.disabledAsset=assets/ui/btn_main_disabled.png

button.load.label=Load Game
button.load.boundsX=0.25
button.load.boundsY=0.40
button.load.boundsW=0.50
button.load.boundsH=0.08

button.settings.label=Settings
button.settings.boundsX=0.25
button.settings.boundsY=0.50
button.settings.boundsW=0.50
button.settings.boundsH=0.08

button.quit.label=Quit
button.quit.boundsX=0.25
button.quit.boundsY=0.60
button.quit.boundsW=0.50
button.quit.boundsH=0.08
```

---

## Header Properties

| Property | Default | Description |
|----------|---------|-------------|
| `menuId` | `"default"` | Which menu screen this layout targets |
| `resolution` | `"default"` | Design resolution hint (e.g., `"1920x1080"`) |
| `menuType` | — | Menu type tag (e.g., `"main"`, `"save"`, `"settings"`) |

---

## Button IDs

Buttons are declared via a comma-separated `button.ids` list:

```properties
button.ids=new_game,load,settings,quit
```

If `button.ids` is omitted, the loader **auto-discovers** button IDs from any `button.<id>.*` keys present in the file.

---

## Per-Button Properties

Each button uses the prefix `button.<id>.`:

| Property | Description |
|----------|-------------|
| `label` | Display label text |
| `tag` | Semantic tag (e.g., `"primary"`, `"danger"`) |
| `boundsX` | Left edge position |
| `boundsY` | Top edge position |
| `boundsW` | Width |
| `boundsH` | Height |
| `asset` | Normal state image asset path |
| `hoverAsset` | Hover state image asset path |
| `disabledAsset` | Disabled state image asset path |

### Coordinate Rules

- Values **≤ 1.0** are treated as **fractions** of the menu viewport
- Values **> 1.0** are treated as **absolute pixels**
- Use `hasBounds()` to check if all four bounds are fully defined

### Extras

Any `button.<id>.<key>` property not in the known set is preserved as a per-button **extra**. Extras are accessible at runtime via `ButtonBounds.extras()`. This enables custom metadata without modifying the spec.

```properties
button.new_game.tooltip=Start a new adventure
button.new_game.soundEffect=assets/audio/sfx/click.ogg
```

### Top-Level Extras

Any top-level key not in the known header set (`menuId`, `resolution`, `menuType`, `button.ids`) and not starting with `button.` is preserved as a top-level extra:

```properties
animationStyle=slide_in
transitionDuration=300
```

---

## Complete Example: Stylized Title Screen

```properties
# config/menu/buttons/main_buttons.properties
menuId=main
resolution=1920x1080
menuType=main

button.ids=new_game,continue,gallery,settings,quit

# New Game — large primary button
button.new_game.label=New Game
button.new_game.tag=primary
button.new_game.boundsX=0.30
button.new_game.boundsY=0.35
button.new_game.boundsW=0.40
button.new_game.boundsH=0.10
button.new_game.asset=assets/ui/title/btn_new.png
button.new_game.hoverAsset=assets/ui/title/btn_new_hover.png

# Continue — secondary button
button.continue.label=Continue
button.continue.boundsX=0.32
button.continue.boundsY=0.48
button.continue.boundsW=0.36
button.continue.boundsH=0.07
button.continue.asset=assets/ui/title/btn_secondary.png
button.continue.hoverAsset=assets/ui/title/btn_secondary_hover.png
button.continue.disabledAsset=assets/ui/title/btn_secondary_disabled.png

# Gallery — small button, left column
button.gallery.label=Gallery
button.gallery.boundsX=0.25
button.gallery.boundsY=0.60
button.gallery.boundsW=0.22
button.gallery.boundsH=0.06
button.gallery.asset=assets/ui/title/btn_small.png

# Settings — small button, right column
button.settings.label=Settings
button.settings.boundsX=0.53
button.settings.boundsY=0.60
button.settings.boundsW=0.22
button.settings.boundsH=0.06
button.settings.asset=assets/ui/title/btn_small.png

# Quit — bottom center
button.quit.label=Quit
button.quit.tag=danger
button.quit.boundsX=0.38
button.quit.boundsY=0.72
button.quit.boundsW=0.24
button.quit.boundsH=0.05
button.quit.asset=assets/ui/title/btn_quit.png
button.quit.hoverAsset=assets/ui/title/btn_quit_hover.png
```

---

## Serialization

Button layouts can be serialized back to text for saving:

```java
String text = MenuButtonLayoutLoader.serialize(spec);
// Writes formatted properties with comments
```

Or to a `Properties` object:

```java
Properties props = MenuButtonLayoutLoader.toProperties(spec);
```

---

## Diagnostics

The loader reports diagnostics for invalid values:

```text
Invalid double for 'button.new_game.boundsX': abc
```

Use `parseWithDiagnostics()` to get both the spec and any diagnostic messages:

```java
MenuButtonLayoutLoader.ParseResult result = MenuButtonLayoutLoader.parseWithDiagnostics(props);
MenuButtonLayoutSpec spec = result.spec();
List<String> issues = result.diagnostics();
```

---

## Editor Support

The **Bounds Studio** tool provides visual button placement:

- **Select mode** — click to select, drag to move, corner handles to resize
- **Rectangle mode** — click-drag to draw new rectangular bounds
- **Point-Nail mode** — click to place corner points, then generate bounding rect
- Background asset image display
- Grid overlay for alignment
- Color-coded bounds with labels
- Coordinate readout

Access via the "Bounds Studio" button in the Menu Screen Visual Editor's Item Bounds section.

---

## Relationship to Item Bounds

Button layouts and per-item `boundsX/Y/Width/Height` in `.menu` files serve similar purposes but at different levels:

| Feature | `.menu` item bounds | Button layout |
|---------|-------------------|---------------|
| Scope | Per-item in one screen | Standalone file for one screen |
| Asset support | Per-item `bgAsset` | Per-button `asset`/`hoverAsset`/`disabledAsset` |
| Resolution | Not resolution-aware | Has `resolution` header |
| Extras | Per-item extras | Per-button + top-level extras |
| Editor | Item Bounds inspector | Bounds Studio |

---

## Related Docs

- [Menu Profiles Overview](menu-profiles.md)
- [Menu Screens](menu-screens.md)
- [Menu Layouts](menu-layouts.md)
- [Menu Styles](menu-styles.md)
