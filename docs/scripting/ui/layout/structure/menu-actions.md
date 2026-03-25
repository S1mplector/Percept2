# Menu Actions & Navigation

Complete guide to menu item actions — all built-in action types, navigation flow between screens, custom action handlers, and the action shorthand syntax.

Model: `core/src/main/java/com/jvn/core/menu/config/MenuActionSpec.java`
Types: `core/src/main/java/com/jvn/core/menu/config/MenuActionType.java`
Loader: `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

---

## Overview

Every selectable menu item has an **action** — what happens when the player activates it. Actions range from starting a new game, navigating between menu screens, running VNS scripts, to custom behaviors you define yourself.

---

## Syntax

Actions are specified in `.menu` files on a per-item basis.

### Split Syntax (Recommended)

Separate `action` and `target` fields:

```properties
item.extras.action=open_menu
item.extras.target=extras
```

### Shorthand Syntax

Action and target joined with a colon:

```properties
item.extras.action=open_menu:extras
```

Both forms produce identical results. The shorthand is concise; the split form is more readable for longer targets.

---

## Built-in Action Types

### NEW_GAME — Start a New Game

Starts the VN from the beginning. Resets state and begins script execution.

```properties
item.new_game.label=New Game
item.new_game.action=new_game
```

**Aliases:** `new`, `start`, `start_game`

**Example — Start with a specific script:**

```properties
item.new_game.label=Begin Story
item.new_game.action=run_script
item.new_game.target=scripts/story/prologue.vns
```

Note: `new_game` uses the project's default start script. Use `run_script` if you need to specify which script to run.

---

### LOAD_MENU — Open Load Screen

Navigates to the load game screen.

```properties
item.load.label=Load Game
item.load.action=load_menu
```

**Aliases:** `load`, `continue`

**Example — "Continue" button that opens load screen:**

```properties
item.continue.label=Continue
item.continue.action=continue
```

---

### SAVE_MENU — Open Save Screen

Navigates to the save game screen. Only meaningful during gameplay (not from the main menu).

```properties
item.save.label=Save Game
item.save.action=save_menu
```

**Aliases:** `save`

---

### SETTINGS_MENU — Open Settings

Navigates to the settings screen.

```properties
item.settings.label=Settings
item.settings.action=settings_menu
```

**Aliases:** `settings`, `options`

---

### MAIN_MENU — Return to Main Menu

Navigates back to the main menu screen.

```properties
item.title.label=Return to Title
item.title.action=main_menu
```

**Aliases:** `main`, `title`, `title_menu`

---

### OPEN_MENU — Navigate to Any Screen

Opens a named sub-menu screen. **Requires a target.**

```properties
item.extras.label=Extras
item.extras.action=open_menu
item.extras.target=extras
```

**Aliases:** `submenu`, `menu`

**Shorthand:**

```properties
item.extras.action=open_menu:extras
```

**Examples:**

```properties
# Navigate to a custom gallery screen
item.gallery.label=Gallery
item.gallery.action=open_menu:gallery

# Navigate to credits
item.credits.label=Credits
item.credits.action=open_menu:credits

# Navigate to a confirmation dialog
item.quit.label=Quit
item.quit.action=open_menu:confirm_exit
```

**Error:** If you omit the target, the engine logs:
`OPEN_MENU action requires a target in <file> (item.<id>.action)`

---

### RUN_SCRIPT — Execute a VNS Script

Runs a VNS script file. **Requires a target** — the script path.

```properties
item.prologue.label=Play Prologue
item.prologue.action=run_script
item.prologue.target=scripts/story/prologue.vns
```

**Aliases:** `script`, `start_script`, `play_script`

**Shorthand:**

```properties
item.prologue.action=run_script:scripts/story/prologue.vns
```

**Examples:**

```properties
# Main menu "New Game" that runs a specific script
item.new_game.label=New Game
item.new_game.action=play_script:scripts/chapter1.vns

# Extras screen bonus scene
item.bonus.label=Bonus Scene
item.bonus.action=script:scripts/bonus/after_credits.vns
```

**Error:** If you omit the target, the engine logs:
`RUN_SCRIPT action requires a script target in <file> (item.<id>.action)`

---

### BACK — Go Back

Returns to the previous screen in the navigation stack.

```properties
item.back.label=Back
item.back.action=back
```

**Aliases:** `return`

**Examples:**

```properties
# Return button in a submenu
item.back.label=Return to Previous Menu
item.back.action=return

# Back button in settings
item.back.label=Done
item.back.action=back
```

---

### QUIT — Exit the Game

Exits the application.

```properties
item.quit.label=Quit Game
item.quit.action=quit
```

**Aliases:** `exit`

**Example — Confirmation before quit:**

Instead of quitting directly, navigate to a confirmation screen:

```properties
# In main.menu
item.quit.label=Quit
item.quit.action=open_menu:confirm_exit
```

```properties
# config/menu/menus/confirm_exit.menu
titleText=Exit Game
hintsText=Enter: Confirm    Esc: Cancel
layout=submenu
defaultItemStyle=submenu

items=prompt,yes,no

item.prompt.label=Leave this session?
item.prompt.action=noop
item.prompt.enabled=false

item.yes.label=Yes, Quit
item.yes.action=quit

item.no.label=No, Return
item.no.action=main_menu
```

---

### NOOP — No Action

Does nothing. Used for decorative or informational items.

```properties
item.divider.label=── Chapter Select ──
item.divider.action=noop
item.divider.enabled=false
```

**Aliases:** `none`, `no_op`

**Examples:**

```properties
# Section header (non-interactive)
item.header.label=Audio Settings
item.header.action=noop
item.header.enabled=false

# Placeholder for future content
item.coming_soon.label=Coming Soon...
item.coming_soon.action=noop
item.coming_soon.enabled=false

# Credits line
item.credit1.label=Engine by JVN Team
item.credit1.action=none
item.credit1.enabled=false
```

---

## Custom Actions

Any action string that doesn't match a built-in type is preserved as a custom action. The engine treats it as `NOOP` by default, but the raw action key is accessible at runtime.

### Defining a Custom Action

```properties
item.credits.label=Show Credits
item.credits.action=show_credits
item.credits.target=credits_scene
```

The engine logs a diagnostic: `Unknown menu action 'show_credits' in <file>; falling back to noop`
This is expected behavior, not an error.

### Handling Custom Actions at Runtime

Register a handler in your game code:

```java
engine.setMenuActionHandler((actionKey, target) -> {
    switch (actionKey) {
        case "show_credits" -> {
            showCreditsAnimation(target);
            return true; // handled
        }
        case "open_url" -> {
            openExternalUrl(target);
            return true;
        }
    }
    return false; // not handled, let default processing continue
});
```

### Non-Destructive Preservation

Custom action keys and their targets are preserved exactly as written across all editing workflows:

- **Loader round-trip** — `MenuProfileLoader` reads and writes custom action strings without normalizing or discarding them
- **Editor round-trip** — the Menu Screen Visual Editor, Menu Flow Editor, and Layout Launcher all preserve unknown action keys when saving `.menu` files
- **Runtime delegation** — the engine delegates unrecognized actions to the registered `MenuActionHandler` before falling back to noop

This means you can safely add game-specific actions like `show_credits` or `play_video` and they will never be silently removed by the tools.

### Detecting Custom Actions

```java
MenuActionSpec action = item.action();
if (action.isCustomAction()) {
    String key = action.actionKey(); // "show_credits"
    String target = action.target(); // "credits_scene"
}
```

### Examples of Custom Actions

```properties
# Open an external URL
item.website.label=Visit Website
item.website.action=open_url
item.website.target=https://example.com

# Trigger a custom animation
item.intro.label=Watch Intro
item.intro.action=play_video
item.intro.target=assets/video/intro.mp4

# Toggle a game mode
item.hardcore.label=Hardcore Mode
item.hardcore.action=toggle_mode
item.hardcore.target=hardcore
```

---

## Navigation Flow Patterns

### Linear Menu Chain

```text
main → extras → gallery
                ↓ back ↑
main → extras → credits
                ↓ back ↑
```

```properties
# main.menu
item.extras.action=open_menu:extras

# extras.menu
item.gallery.action=open_menu:gallery
item.credits.action=open_menu:credits
item.back.action=main_menu

# gallery.menu / credits.menu
item.back.action=back
```

### Hub-and-Spoke

All submenus return to main:

```properties
# main.menu
item.load.action=load_menu
item.save.action=save_menu
item.settings.action=settings_menu
item.extras.action=open_menu:extras

# Each submenu has:
item.back.action=main_menu
```

### Nested with Back Stack

Using `back` instead of `main_menu` preserves the navigation history:

```properties
# extras.menu
item.gallery.action=open_menu:gallery

# gallery.menu — "back" returns to extras, not main
item.back.action=back
```

### Confirmation Dialogs

Route through a confirmation screen before destructive actions:

```properties
# settings.menu
item.reset.label=Reset All Settings
item.reset.action=open_menu:confirm_reset

# confirm_reset.menu
titleText=Reset Settings?
items=warning,yes,no

item.warning.label=This will reset all settings to default.
item.warning.action=noop
item.warning.enabled=false

item.yes.label=Yes, Reset
item.yes.action=settings_menu

item.no.label=Cancel
item.no.action=back
```

---

## Complete Navigation Example

A full game menu structure:

```properties
# config/menu/registry/menu.registry
defaultMenu=main
menus=main,load,save,settings,extras,gallery,credits,confirm_exit
layouts=default,submenu,slots
styles=default,submenu,slot
```

```properties
# config/menu/menus/main.menu
titleText=Echoes of Time
hintsText=↑↓ Navigate    Enter Select
layout=default
defaultItemStyle=default
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

```properties
# config/menu/menus/extras.menu
titleText=Extras
hintsText=Enter: Select    Esc: Back
layout=submenu
defaultItemStyle=submenu
items=gallery,credits,back

item.gallery.label=Gallery
item.gallery.action=open_menu:gallery

item.credits.label=Credits
item.credits.action=open_menu:credits

item.back.label=Return to Main Menu
item.back.action=main_menu
```

```properties
# config/menu/menus/gallery.menu
titleText=Gallery
hintsText=Esc: Back
layout=submenu
defaultItemStyle=submenu
items=cg_1,cg_2,cg_3,back

item.cg_1.label=Chapter 1 CGs
item.cg_1.action=noop
item.cg_1.enabled=false

item.cg_2.label=Chapter 2 CGs
item.cg_2.action=noop
item.cg_2.enabled=false

item.cg_3.label=Chapter 3 CGs
item.cg_3.action=noop
item.cg_3.enabled=false

item.back.label=Back
item.back.action=back
```

---

## Runtime Validation Checklist

- [ ] Every menu item triggers the correct action when activated
- [ ] `open_menu` items navigate to the correct target screen
- [ ] `back` items return to the previous screen (not always main)
- [ ] `quit` exits the application
- [ ] `new_game` starts the game from the beginning
- [ ] `load_menu` / `save_menu` / `settings_menu` open the correct built-in screens
- [ ] `run_script` items launch the specified VNS script
- [ ] Disabled items cannot be activated
- [ ] Custom action handlers fire for non-standard action strings
- [ ] No "unknown action" warnings for intentional custom actions
- [ ] No "requires a target" warnings in the console
- [ ] Navigation cycles (A → B → A) work without stack overflow

---

## Common Mistakes

**Missing target for open_menu:**
```properties
# Wrong — no target specified
item.extras.action=open_menu
```
Fix: Add `item.extras.target=extras` or use shorthand `action=open_menu:extras`.

**Using main_menu when back is intended:**
```properties
# This always goes to main, even if you came from extras
item.back.action=main_menu

# This goes back to wherever you came from
item.back.action=back
```

**Typo in action type:**
```properties
# Wrong — 'new game' with a space
item.start.action=new game

# Correct
item.start.action=new_game
```

**Circular navigation without an exit:**
```properties
# A goes to B, B goes to A — no way out!
# Always provide a back or main_menu escape
```

---

## Related Docs

- [Menu Screens](../../menus/menu-screens.md)
- [Menu Registry & File Discovery](menu-registry.md)
- [Menu Inheritance & Composition](menu-inheritance.md)
- [Text-First Layout Workflow](../workflow/text-first-layout-workflow.md)
