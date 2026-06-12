# Reactive Overlay Screens

Reactive screens are small in-game overlays loaded from plain `.screen` properties files. They are useful for confirmation dialogs, stat panels, shop prompts, chapter popups, and other VN screens whose text or buttons should react to `VnState` variables.

Core classes:
- `modules/core/src/main/java/com/jvn/core/vn/ui/VnReactiveScreenLoader.java`
- `modules/core/src/main/java/com/jvn/core/vn/ui/VnReactiveOverlayScreenSpec.java`
- `modules/core/src/main/java/com/jvn/core/vn/ui/VnReactiveScreenSpec.java`

---

## Quick Start

Create a screen file:

```properties
# config/screens/shop.screen
title=Shop
text=Coins: ${coins}
x=0.22
y=0.18
width=0.56
height=0.42
modal=true
dim=true

buttons=buy,leave

button.buy.label=Buy potion
button.buy.action=return
button.buy.target=buy
button.buy.enabledIf=coins >= 10

button.leave.label=Leave
button.leave.action=return
button.leave.target=leave
```

Show it from VNS:

```vns
[var set coins 7]
[screen show shop]
```

Call it and wait for a return value:

```vns
[screen call shop]
[if screen.return.shop == "buy" goto bought_potion]
```

When `coins` changes, the overlay text and button enabled state update automatically because the renderer evaluates the file against the live `VnState`.

---

## File Discovery

`[screen show <id>]` and `[screen call <id>]` first look for a file-backed reactive screen:

1. `config/screens/<id>.screen`
2. `config/screens/<id>.properties`
3. `screens/<id>.screen`
4. `screens/<id>.properties`
5. `<id>.screen`
6. `<id>.properties`

If no file exists, JVN falls back to the existing inline screen syntax:

```vns
[screen show confirm title="Confirm" text="Continue?" buttons="Yes|return|yes;No|return|no"]
```

---

## Screen Fields

| Key | Default | Description |
|-----|---------|-------------|
| `id` | filename | Runtime screen ID |
| `title` | screen ID | Title text |
| `text` / `body` | empty | Body text |
| `visibleIf` | empty | Condition; hides/dismisses the screen when false |
| `x`, `y` | `0.18` | Panel position as viewport fractions |
| `width` / `w` | `0.64` | Panel width as viewport fraction |
| `height` / `h` | `0.42` | Panel height as viewport fraction |
| `modal` | `call` value | Blocks clicks behind this overlay |
| `dim` / `dimBackground` | `true` | Draws a dim backdrop |
| `dismiss` / `dismissOnAdvance` | `!call` | Lets advance dismiss the overlay |
| `call` | `false` | Treats the file as a call screen |
| `timer` | `0` | Optional lifetime in milliseconds |
| `timerAction` | `return` for call, otherwise `hide` | Action when timer expires |
| `timerTarget` | empty | Return value when `timerAction=return` |
| `returnKey` | `screen.return.<id>` | Variable key for call return value |

Text values support localization and variable interpolation:

```properties
title=i18n:shop.title
text=Coins: ${coins}
```

Literal text also participates in the translation extractor through generated `source.<hash>` keys.

---

## Buttons

Declare button IDs with `buttons=`:

```properties
buttons=buy,leave
```

Each button uses `button.<id>.*` fields:

| Key | Default | Description |
|-----|---------|-------------|
| `label` | button ID | Button text |
| `action` | `noop` | Runtime action |
| `target` | empty | Action target or return value |
| `enabled` | `true` | Static enabled flag |
| `enabledIf` | empty | Condition that must be true for the button to be enabled |
| `visibleIf` | empty | Condition that must be true for the button to render |
| `x`, `y` | auto row | Button position as fractions |
| `width`, `height` | auto | Button size as fractions |
| `space` / `coordinateSpace` | `screen` | `screen` or `viewport` coordinates |

Common actions include:

| Action | Target Meaning |
|--------|----------------|
| `return` | Sets the call screen return value and closes the overlay |
| `hide` / `close` / `dismiss` | Closes an overlay |
| `goto` | Jumps to a label |
| `advance` | Advances the VN scene |
| `screen` | Runs another screen command |
| `set`, `flag`, `unflag`, `clear`, `inc`, `dec` | Routes to the variable interop provider |
| `persistent` | Routes to the persistent interop provider |

---

## Conditions

`visibleIf` and `enabledIf` use the same condition evaluator as VNS choices:

```properties
button.buy.enabledIf=coins >= 10
button.secret.visibleIf=persistent.trueEnding == true
visibleIf=ui.inventoryOpen == true
```

Persistent values are mirrored into variables as `persistent.<key>`, so unlock flags can drive screen state without custom Java.

---

## Related Docs

- [Menu Profiles](menu-profiles.md)
- [Menu Actions](../layout/structure/menu-actions.md)
- [VNS Commands](../../vns/language/vns-commands.md)
- [VNS Localization](../../vns/runtime/vns-localization.md)
