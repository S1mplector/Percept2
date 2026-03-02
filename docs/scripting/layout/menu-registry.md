# Menu Registry & File Discovery

Complete guide to the `menu.registry` file and how the JVN engine discovers, loads, and resolves menu screens, layouts, and styles.

Loader: `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

---

## Overview

The menu registry is the **wiring file** that tells the engine which menus, layouts, and styles exist in your project. It declares explicit IDs and sets the default entry point screen. While the engine can auto-discover files, the registry gives you precise control over what gets loaded and in what priority.

---

## File Location

The engine searches for the registry in this order (first match wins):

1. `config/menu/registry/menu.registry`
2. `config/menu/menu.registry`
3. `config/menu/registry.properties`
4. `menu.registry`

The recommended location is `config/menu/registry/menu.registry`.

---

## Registry Keys

```properties
# config/menu/registry/menu.registry

# Which menu screen to show first when the game starts
defaultMenu=main

# Comma-separated list of menu screen IDs
menus=main,load,save,settings,extras,credits,confirm_exit

# Comma-separated list of layout IDs
layouts=default,submenu,slots

# Comma-separated list of style IDs
styles=default,submenu,slot
```

| Key | Alias | Required | Description |
|-----|-------|----------|-------------|
| `defaultMenu` | `defaultScreen` | Recommended | ID of the first screen to show |
| `menus` | — | Optional | Comma-separated screen IDs |
| `layouts` | — | Optional | Comma-separated layout IDs |
| `styles` | — | Optional | Comma-separated style IDs |

---

## How Discovery Works

The engine uses a **three-tier discovery** system. All tiers merge together — duplicates are resolved by first-seen-wins.

### Tier 1: Built-in Defaults

The engine always starts with built-in defaults:

**Screens:** `main`, `extras`, `credits`, `confirm_exit`, `load`, `save`, `settings`
**Layouts:** `default`, `submenu`, `slots`
**Styles:** `default`, `submenu`, `slot`

### Tier 2: Registry Declarations

IDs listed in the `menus`, `layouts`, and `styles` fields are added to the discovery set. Each ID maps to a file:

- Menu `main` → searches `config/menu/menus/main.menu`, then `config/menu/menus/main.properties`, then `config/menu/main.menu`, then `main.menu`
- Layout `default` → searches `config/menu/layouts/default.layout`, then `config/menu/layouts/default.properties`, then `config/menu/default.layout`, then `default.layout`
- Style `default` → searches `config/menu/styles/default.style`, then `config/menu/styles/default.properties`, then `config/menu/default.style`, then `default.style`

### Tier 3: Auto-Discovery

The engine scans these directories for files matching known extensions:

| Type | Scan directories | Extensions |
|------|-----------------|------------|
| Menus | `config/menu/menus/`, `config/menu/` | `.menu`, `.properties` |
| Layouts | `config/menu/layouts/`, `config/menu/` | `.layout`, `.properties` |
| Styles | `config/menu/styles/`, `config/menu/` | `.style`, `.properties` |

The filename (minus extension) becomes the ID. For example, `config/menu/styles/neon.style` is discovered as style ID `neon`.

---

## Examples

### Example 1: Minimal Registry

```properties
# config/menu/registry/menu.registry
defaultMenu=main
```

With just `defaultMenu`, the engine:
- Uses built-in defaults for all screens, layouts, and styles
- Auto-discovers any custom files in `config/menu/`
- Starts on the `main` screen

### Example 2: Explicit Full Registry

```properties
# config/menu/registry/menu.registry
defaultMenu=main

menus=main,load,save,settings,extras,credits,confirm_exit,gallery
layouts=default,submenu,slots,compact
styles=default,submenu,slot,dark,neon
```

Every ID is listed explicitly. The engine will load the corresponding files and warn about any that are missing.

### Example 3: Custom Default Screen

```properties
# config/menu/registry/menu.registry
# Start on a title card instead of the main menu
defaultMenu=title_card

menus=title_card,main,load,save,settings
layouts=default,title
styles=default,title
```

```properties
# config/menu/menus/title_card.menu
titleText=
hintsText=Press Enter to Start
layout=title
defaultItemStyle=title
items=start

item.start.label=
item.start.action=open_menu
item.start.target=main
```

### Example 4: Adding a New Screen

Starting from a working project:

**Step 1:** Create the menu file:
```properties
# config/menu/menus/gallery.menu
titleText=Gallery
hintsText=Enter: View    Esc: Back
layout=submenu
defaultItemStyle=submenu

items=cg_mode,music_room,back

item.cg_mode.label=CG Gallery
item.cg_mode.action=noop
item.cg_mode.enabled=false

item.music_room.label=Music Room
item.music_room.action=noop
item.music_room.enabled=false

item.back.label=Return
item.back.action=back
```

**Step 2:** Add it to the registry:
```properties
menus=main,load,save,settings,extras,gallery
```

**Step 3:** Link to it from another menu:
```properties
# In main.menu or extras.menu
item.gallery.label=Gallery
item.gallery.action=open_menu:gallery
```

**Step 4:** Run the project. Navigate to Gallery. Confirm it works.

### Example 5: Removing a Built-in Screen

To remove the built-in `extras` screen, simply don't include it in the registry and don't link to it:

```properties
# config/menu/registry/menu.registry
defaultMenu=main
menus=main,load,save,settings,confirm_exit
```

If no menu links to `extras` and it's not in the registry, it won't appear. The built-in default is only used as a fallback if the ID is requested.

---

## File Search Paths

### Menu Screen Search Order

For a menu ID `xyz`:

1. `config/menu/menus/xyz.menu`
2. `config/menu/menus/xyz.properties`
3. `config/menu/xyz.menu`
4. `config/menu/xyz.properties`
5. `xyz.menu`

### Layout Search Order

For a layout ID `xyz`:

1. `config/menu/layouts/xyz.layout`
2. `config/menu/layouts/xyz.properties`
3. `config/menu/xyz.layout`
4. `xyz.layout`

### Style Search Order

For a style ID `xyz`:

1. `config/menu/styles/xyz.style`
2. `config/menu/styles/xyz.properties`
3. `config/menu/xyz.style`
4. `xyz.style`

---

## Fallback Behavior

When the engine can't find a referenced resource:

| Scenario | Behavior |
|----------|----------|
| `defaultMenu` screen not found | Falls back to `main`, then first available screen |
| Layout ID not found | Falls back to `default` layout, then built-in default |
| Style ID not found | Falls back to `default` style, then built-in default |
| Menu file not found for declared ID | Diagnostic warning; built-in defaults used if available |
| Registry file not found | Engine uses all built-in defaults + auto-discovery |

---

## Runtime Validation Checklist

After modifying the registry, run the project and check:

- [ ] Game starts on the expected `defaultMenu` screen
- [ ] Every screen listed in `menus=` is reachable via navigation
- [ ] No "undefined layout" or "undefined style" console warnings
- [ ] No "configured default menu is undefined" warnings
- [ ] Adding a new ID to the registry and creating the file makes it accessible
- [ ] Removing an ID from the registry makes it use the fallback or disappear

---

## Common Mistakes

**Typo in defaultMenu:**
```properties
# Wrong — 'mian' instead of 'main'
defaultMenu=mian
```
Console output: `Configured default menu 'mian' is undefined; using fallback`

**ID in registry but file missing:**
```properties
menus=main,load,gallery
# But config/menu/menus/gallery.menu doesn't exist
```
The engine logs a warning and the screen won't be accessible.

**Comma without space in CSV:**
```properties
# Both are fine — the engine trims whitespace
menus=main,load,save
menus=main, load, save
```

**Wrong extension:**
```properties
# The engine searches for .menu, not .txt
# config/menu/menus/main.txt — NOT discovered
```

---

## Related Docs

- [Menu Profiles Overview](../menus-submenus/menu-profiles.md)
- [Menu Screens](../menus-submenus/menu-screens.md)
- [Menu Layouts](menu-layouts.md)
- [Menu Styles](../menus-submenus/menu-styles.md)
- [Text-First Layout Workflow](text-first-layout-workflow.md)
