# UI By Example — Navigation, Scripts, and Custom Actions

Build a nested menu flow with back-stack navigation, direct VNS launches, built-in destinations, and one application-defined action.

**Difficulty:** Intermediate
**Time:** 25 minutes
**Concepts:** `open_menu`, `back`, `run_script`, history, Auto/Skip, custom action handlers

---

## Build an Extras Hub

```properties
# config/menu/menus/extras.menu
titleText=Extras
hintsText=Enter: Select    Esc: Back
layout=submenu
defaultItemStyle=submenu
items=bonus,history,auto,skip,website,back

item.bonus.label=Bonus Scene
item.bonus.action=run_script
item.bonus.target=scripts/story/bonus.vns

item.history.label=Dialogue History
item.history.action=history

item.auto.label=Toggle Auto
item.auto.action=toggle_auto

item.skip.label=Toggle Skip
item.skip.action=toggle_skip

item.website.label=Project Website
item.website.action=open_website
item.website.target=https://example.com

item.back.label=Back
item.back.action=back
```

Add `extras` to `menus=` in the registry and point the main screen at it:

```properties
item.extras.label=Extras
item.extras.action=open_menu
item.extras.target=extras
```

`back` returns to the previous menu on the stack. `main_menu` always returns to the title-level main screen, so it is not a substitute for nested back navigation.

---

## Split and Shorthand Syntax

These declarations are equivalent:

```properties
item.gallery.action=open_menu
item.gallery.target=gallery
```

```properties
item.gallery.action=open_menu:gallery
```

Use split syntax when a target is long or frequently edited. Shorthand is useful in small navigation files.

---

## Built-In Actions

| Action | Result |
|---|---|
| `new_game` | Starts the default new-game flow |
| `run_script:<path>` | Launches a specific VNS script |
| `load_menu` / `save_menu` | Opens save-system screens |
| `settings_menu` | Opens settings |
| `history` | Opens dialogue history |
| `toggle_auto` / `toggle_skip` | Changes VN playback mode |
| `open_menu:<id>` | Pushes a named menu screen |
| `back` | Pops the current menu |
| `main_menu` | Returns to the main menu |
| `gallery` / `music_room` | Opens the built-in extras surfaces |
| `quit` | Exits the application |
| `noop` | Intentionally performs no action |

Use canonical names in new source even though the parser accepts several aliases.

---

## Register a Custom Action

Unknown action names are preserved so the engine host can handle them:

```java
engine.setMenuActionHandler((actionKey, target) -> {
    if ("open_website".equals(actionKey)) {
        openExternalUrl(target);
        return true;
    }
    return false;
});
```

Return `true` only when the action was handled. Custom actions are application code: validate untrusted targets and document which module or plugin owns the action.

---

## Navigation Patterns

### Hub and spoke

```text
main -> extras -> gallery
              -> music room
              -> credits
```

Every spoke uses `back` to return to `extras`.

### Confirmation flow

```properties
item.quit.action=open_menu:confirm_exit
```

The confirmation screen contains `quit` and `back`. This prevents accidental destructive actions without inventing a separate dialog runtime.

---

## Test the Graph

- Follow every visible destination.
- Confirm every submenu has a reachable escape.
- Verify `back` returns to the actual caller.
- Test direct script paths on a case-sensitive filesystem.
- Confirm unknown custom actions are handled and logged appropriately.
- Test keyboard, pointer, and controller navigation where supported.

---

## Key Takeaways

1. Use `open_menu` and `back` for nested navigation.
2. Use `run_script` when a menu item launches a specific story file.
3. Gallery, music room, history, Auto, and Skip have built-in actions.
4. Custom names survive parsing and can be handled by the engine host.
5. Audit the menu graph as behavior, not only as valid properties files.

---

## Next

Reduce duplication and create custom compositions in [Layouts, Inheritance, and Bespoke Buttons](07-layouts-inheritance-and-buttons.md).

[Back to UI By Example](../ui-by-example.md)
