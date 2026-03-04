# Validation & Diagnostics

Complete guide to JVN's layout and menu validation system — all diagnostic messages, what triggers them, how to read console output, and how to fix every warning.

Validator: `core/src/main/java/com/jvn/core/menu/config/MenuProfileValidator.java`
Loader: `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`
Dialogue loader: `core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java`

---

## Overview

The JVN engine validates all layout, style, menu, and registry files at load time. When it finds issues — typos, out-of-range values, missing references, circular inheritance — it produces **diagnostic messages** that appear in the console output. Diagnostics are warnings, not fatal errors: the engine always falls back to safe defaults so your game still runs.

Reading diagnostics after each run is a critical part of the text-first workflow.

---

## How to Read Diagnostics

Diagnostics appear in the console/log output when you run your project. They follow this format:

```text
<Description> in <source_path>: <details> (using <fallback_value>)
```

Examples:

```text
Invalid number for 'lineHeight' in config/menu/layouts/default.layout: 'abc' (using 40.0)
Value for 'listWidthFactor' in config/menu/layouts/default.layout was out of range (0.1..1.0): 2.5 (using 1.0)
Unknown menu action 'play_credits' in config/menu/menus/main.menu (item.credits.action); falling back to noop
```

### Diagnostic Severity

All diagnostics are **warnings**. The engine never refuses to load a file. It always produces a usable result by falling back to defaults.

---

## Menu Profile Diagnostics

### Registry Diagnostics

**"Configured default menu 'X' is undefined; using fallback"**

The `defaultMenu` ID in `menu.registry` doesn't match any loaded screen.

```properties
# Triggers this warning:
defaultMenu=mian    # typo — should be 'main'
```

Fix: Correct the spelling in `menu.registry`.

---

**"Default menu 'X' is not explicitly defined; fallback will be used"**

The default screen ID exists but isn't defined by a `.menu` file — the engine uses its built-in default.

Fix: Create a `.menu` file for the default screen, or change `defaultMenu` to an existing screen.

---

### Layout Diagnostics

**"Invalid number for 'KEY' in PATH: 'VALUE' (using DEFAULT)"**

A numeric key has a non-numeric value.

```properties
# Triggers this warning:
lineHeight=tall    # should be a number like 68
```

Fix: Use a valid number.

---

**"Invalid value for 'listYStart' in PATH: must be >= 0 (using DEFAULT)"**

`listYStart` is negative.

```properties
listYStart=-0.5    # invalid, must be >= 0
```

Fix: Use a value >= 0.

---

**"Invalid value for 'lineHeight' in PATH: must be > 0 (using DEFAULT)"**

`lineHeight` is zero or negative.

```properties
lineHeight=0    # invalid, must be > 0
```

Fix: Use a positive value (typically 40--80).

---

**"Value for 'listWidthFactor' in PATH was out of range (0.1..1.0): VALUE (using CLAMPED)"**

`listWidthFactor` is outside the valid range.

```properties
listWidthFactor=2.0    # clamped to 1.0
listWidthFactor=0.01   # clamped to 0.1
```

Fix: Use a value between 0.1 and 1.0.

---

**"Invalid value for 'textAlign' in PATH: 'VALUE' (expected left/center/right; using DEFAULT)"**

`textAlign` has an unrecognized value.

```properties
textAlign=middle    # invalid — use 'center'
```

Fix: Use `left`, `center`, or `right`.

---

**"Invalid value for 'hintsBottomMargin' in PATH: must be >= 0 (using DEFAULT)"**

Negative hints margin.

Fix: Use a value >= 0.

---

**"Invalid value for 'titleY' in PATH: must be >= 0 (using DEFAULT)"**

Negative title position.

Fix: Use a value >= 0, or omit `titleY` entirely to have no title.

---

**"Circular layout inheritance detected at 'ID'"**

Layout A extends B, and B extends A (directly or through a chain).

Fix: Break the cycle by removing one `extends=` reference.

---

**"Layout 'ID' extends missing layout 'PARENT'"**

The parent layout referenced by `extends` doesn't exist.

Fix: Create the parent layout file, or fix the `extends` value.

---

### Style Diagnostics

**"Layout 'ID' has negative listYStart"** (from validator)
**"Layout 'ID' has non-positive lineHeight"** (from validator)
**"Layout 'ID' has listWidthFactor outside 0.1..1.0"** (from validator)
**"Layout 'ID' has invalid textAlign 'VALUE'"** (from validator)
**"Layout 'ID' has negative hintsBottomMargin"** (from validator)
**"Layout 'ID' has negative titleY"** (from validator)

These are post-load validation messages confirming the issue persists after parsing.

---

**"Circular style inheritance detected at 'ID'"**

Same as layout circular inheritance, but for styles.

Fix: Break the `extends` cycle.

---

**"Style 'ID' extends missing style 'PARENT'"**

The parent style doesn't exist.

Fix: Create it or fix the `extends` value.

---

### Screen Diagnostics

**"Circular menu inheritance detected at screen 'ID'"**

Screen A extends B and B extends A.

Fix: Break the cycle.

---

**"Menu screen 'ID' extends missing menu 'PARENT'"**

The parent screen doesn't exist.

Fix: Create it or fix the `extends` value.

---

**"Duplicate item id 'ID' in PATH; later declaration ignored"**

The same item ID appears twice in the `items=` list.

```properties
items=new_game,load,new_game    # 'new_game' duplicated
```

Fix: Remove the duplicate.

---

### Action Diagnostics

**"Unknown menu action 'VALUE' in PATH (PROPERTY); falling back to noop"**

An action string doesn't match any built-in type.

```properties
item.credits.action=show_credits    # not a built-in action
```

This is expected for **custom actions** (handled by runtime code). If it's a typo, fix the spelling. If it's intentional, you can ignore the warning.

---

**"OPEN_MENU action requires a target in PATH (PROPERTY)"**

`open_menu` is used without a `target`.

```properties
item.extras.action=open_menu
# Missing: item.extras.target=extras
```

Fix: Add the target or use shorthand: `action=open_menu:extras`.

---

**"RUN_SCRIPT action requires a script target in PATH (PROPERTY)"**

`run_script` is used without a target.

Fix: Add `item.<id>.target=scripts/path.vns`.

---

**"Target for action 'TYPE' is ignored in PATH (PROPERTY)"**

A target is specified for an action that doesn't use one (e.g., `quit`, `back`, `new_game`).

```properties
item.quit.action=quit
item.quit.target=something    # ignored — quit doesn't use a target
```

Fix: Remove the unnecessary `target` line.

---

### Bounds Diagnostics

**"Item 'ID' in PATH has partial bounds; X/Y/Width/Height must be set together"**

Some but not all four bounds fields are set.

```properties
item.start.boundsX=100
item.start.boundsY=200
# Missing boundsWidth and boundsHeight
```

Fix: Set all four, or none.

---

**"Item 'ID' in PATH has negative boundsX; using 0"**

Negative position value.

Fix: Use a value >= 0.

---

**"Item 'ID' in PATH has non-positive boundsWidth; dropping explicit bounds"**

Width or height is zero or negative. The engine drops all bounds for this item.

Fix: Use positive values for width and height.

---

### Profile Validation Diagnostics

These come from `MenuProfileValidator.validate()` after the entire profile is loaded:

**"Screen 'ID' references layout 'LID' which is not defined"**

A screen's `layout=` references a layout ID that doesn't exist.

Fix: Create the layout file or change the screen's `layout=` to a valid ID.

---

**"Screen 'ID' references style 'SID' which is not defined"**

The screen's `defaultItemStyle` or an item's `style` references a missing style.

Fix: Create the style file or change the reference.

---

**"Screen 'ID' has no items"**

A screen was loaded but has zero items.

Fix: Add items to the `.menu` file.

---

**"Screen 'ID' item 'ITEM' uses OPEN_MENU targeting 'TARGET' which is not defined"**

An `open_menu` action targets a screen that doesn't exist.

Fix: Create the target screen or fix the target ID.

---

**"Screen 'ID' item 'ITEM' uses RUN_SCRIPT without a target"**

A `run_script` action has no target path.

Fix: Add the script path as the target.

---

## Dialogue Layout Diagnostics

**"Invalid number for 'KEY': 'VALUE' (using DEFAULT)"**

Same as menu diagnostics but for dialogue layout keys.

---

**"Unknown dialogue layout key: 'KEY'"**

A key in `dialogue.layout` isn't recognized.

```properties
textboxX=0.5    # typo — should be 'textBoxX'
```

Fix: Check spelling against the key reference. All keys use camelCase.

---

**Value clamping warnings:**

The engine silently clamps values to valid ranges. If the loaded value differs from the clamped value, a diagnostic is emitted:

```text
Value 'textBoxWidth' was adjusted from 1.5 to 1.0
```

This means your value was out of range. Check the documented range for that key.

---

## Using Diagnostics Programmatically

### Menu Profile Diagnostics

```java
MenuProfileLoader.LoadResult result = MenuProfileLoader.loadWithDiagnostics(assets);
MenuProfile profile = result.profile();
List<String> diagnostics = result.diagnostics();

for (String d : diagnostics) {
    System.err.println("[MENU] " + d);
}
```

### Dialogue Layout Diagnostics

```java
VnUiLayoutLoader.LoadResult result = VnUiLayoutLoader.parseWithDiagnostics(
    props, VnUiLayoutSpec.defaults(), VnUiStyleSpec.defaults()
);
VnUiLayoutSpec layout = result.layout();
VnUiStyleSpec style = result.style();
List<String> diagnostics = result.diagnostics();

for (String d : diagnostics) {
    System.err.println("[DIALOGUE] " + d);
}
```

### Post-Load Validation

```java
List<String> issues = MenuProfileValidator.validate(profile);
for (String issue : issues) {
    System.err.println("[VALIDATE] " + issue);
}
```

---

## Diagnostic Checklist Workflow

After every run, scan console output for diagnostics:

1. **Search for `[MENU]` or `[DIALOGUE]` or `[VALIDATE]` prefixes** (if your runtime logs them)
2. **Fix typos first** — unknown keys and misspelled actions are the most common
3. **Fix range violations** — values clamped to valid ranges
4. **Fix missing references** — layouts, styles, or screens that don't exist
5. **Resolve circular inheritance** — break `extends` cycles
6. **Ignore intentional custom action warnings** — `Unknown menu action` for custom handlers is expected
7. **Re-run and confirm zero diagnostics** (or only expected ones)

---

## Quick Reference: All Diagnostic Messages

| Message Pattern | Cause | Fix |
|----------------|-------|-----|
| Invalid number for 'KEY' | Non-numeric value | Use a valid number |
| Invalid value for 'KEY': must be >= 0 | Negative value | Use >= 0 |
| Invalid value for 'KEY': must be > 0 | Zero or negative | Use > 0 |
| Value for 'KEY' was out of range | Outside min..max | Use value in range |
| Invalid value for 'textAlign' | Not left/center/right | Fix spelling |
| Unknown dialogue layout key | Typo in key name | Fix camelCase spelling |
| Circular inheritance detected | A extends B extends A | Break the cycle |
| extends missing menu/layout/style | Parent doesn't exist | Create it or fix extends |
| Duplicate item id | Same ID twice in items= | Remove duplicate |
| Unknown menu action | Unrecognized action string | Fix spelling or register handler |
| OPEN_MENU requires a target | No target for open_menu | Add target |
| RUN_SCRIPT requires a target | No target for run_script | Add script path |
| Target is ignored | Target on action that doesn't use one | Remove target |
| Partial bounds | Some but not all 4 bounds set | Set all 4 or none |
| Negative boundsX/Y | Position < 0 | Use >= 0 |
| Non-positive boundsWidth/Height | Size <= 0 | Use > 0 |
| references layout which is not defined | Missing layout ID | Create file or fix reference |
| references style which is not defined | Missing style ID | Create file or fix reference |
| has no items | Empty screen | Add items |
| OPEN_MENU targeting which is not defined | Target screen missing | Create it or fix target |
| Configured default menu is undefined | Bad defaultMenu in registry | Fix spelling |

---

## Related Docs

- [Text-First Layout Workflow](../workflow/text-first-layout-workflow.md)
- [Menu Registry & File Discovery](../structure/menu-registry.md)
- [Menu Screens](../../menus/menu-screens.md)
- [Dialogue Layout & Style](../components/dialogue-layout.md)
