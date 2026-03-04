# Scala DSL Reference

Complete guide to the type-safe Scala DSL for building JVN menu styles, layouts, and button layouts programmatically — using Scala 3 context functions for a concise builder syntax.

Source: `scala-utils/src/main/scala/com/jvn/scala/dsl/MenuDsl.scala`

---

## Overview

The Scala DSL provides a type-safe, code-first alternative to `.properties` files for defining menu styles, layouts, and button layouts. It uses Scala 3 context functions (`?=>`) to create a clean builder pattern. The DSL produces the same `MenuStyleSpec`, `MenuLayoutSpec`, and `MenuButtonLayoutSpec` records used by the engine — the output is identical to what the properties file loader creates.

---

## Setup

```scala
import com.jvn.scala.dsl.MenuDsl.*
```

---

## Menu Style DSL

### Basic Usage

```scala
val style = menuStyle("neon") {
  itemColor("#00ff88")
  selectedColor("#ffff00")
  hoverColor("#66ffaa")
  disabledColor("#555555")
  font("Monospace", "BOLD", 24)
  shadow("#00000088", 2.0, 2.0)
  opacity(0.95)
  prefix("  ", "> ", "- ")
  buttonAssets("btn.png", "btn_sel.png", "btn_hover.png", "btn_dis.png")
  buttonPadding(18.0, 0.0)
  title("#ffffff", "Georgia", "BOLD", 36, "#000000")
  hints("#aaaaaa", "Arial", 14)
  background("bg.png", "#1a1a2e", 0.9)
}
```

### All Style Methods

| Method | Parameters | Description |
|--------|-----------|-------------|
| `itemColor(c)` | Hex string | Normal item text color |
| `selectedColor(c)` | Hex string | Selected item text color |
| `hoverColor(c)` | Hex string | Hover item text color |
| `disabledColor(c)` | Hex string | Disabled item text color |
| `font(family, weight, size)` | String, String, Int | Item font (weight: NORMAL/BOLD/SEMI_BOLD) |
| `shadow(color, offsetX, offsetY)` | Hex, Double, Double | Drop shadow |
| `opacity(v)` | Double | Item opacity (0.0–1.0) |
| `prefix(normal, selected, disabled)` | 3 Strings | Text prefixes for each state |
| `buttonAssets(normal, selected, hover, disabled)` | 4 Strings | Button image asset paths |
| `buttonPadding(x, y)` | Double, Double | Text padding inside buttons |
| `title(color, fontFamily, fontWeight, fontSize, shadowColor)` | Mixed | Title text styling |
| `hints(color, fontFamily, fontSize)` | Mixed | Hints bar styling |
| `background(asset, color, opacity)` | Mixed | Background image and color |

### Style Examples

**Dark Blue Theme:**

```scala
val darkBlue = menuStyle("dark_blue") {
  itemColor("#B8C4D8")
  selectedColor("#FFD700")
  hoverColor("#E8E8F0")
  disabledColor("#4A5568")
  font("Segoe UI", "SEMI_BOLD", 28)
  shadow("#000000CC", 1.0, 1.0)
  prefix("  ", "▶ ", "  ")
  title("#FFD700", "Georgia", "BOLD", 56, "#000000CC")
  hints("#667788", "Segoe UI", 16)
  background("assets/backgrounds/title.png", "#0A0A1A", 1.0)
}
```

**Minimal Style (Overrides Only):**

```scala
val danger = menuStyle("danger") {
  itemColor("#FF4444")
  selectedColor("#FF0000")
}
```

---

## Menu Layout DSL

### Basic Usage

```scala
val layout = menuLayout("compact") {
  listYStart(0.4)
  lineHeight(36.0)
  listWidthFactor(0.8)
  textAlign("center")
  hintsBottomMargin(16.0)
  titleY(50.0)
}
```

### All Layout Methods

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `listYStart(v)` | Double | `0.35` | Top of item list (fraction of viewport) |
| `lineHeight(v)` | Double | `40.0` | Pixels per item row |
| `listWidthFactor(v)` | Double | `1.0` | List width (fraction of viewport) |
| `textAlign(v)` | String | `"center"` | `"left"`, `"center"`, or `"right"` |
| `hintsBottomMargin(v)` | Double | `20.0` | Bottom margin for hints text |
| `titleY(v)` | Double | `null` | Title Y position (optional) |
| `listXCenter(v)` | Double | `null` | Horizontal center of the item list (0–1). Overrides `textAlign` positioning. |
| `titleX(v)` | Double | `null` | Horizontal center of the title (0–1). Overrides default centered title. |
| `maxVisibleItems(v)` | Int | `null` | Max visible items before scrolling. |

### Layout Examples

**Submenu Layout:**

```scala
val submenu = menuLayout("submenu") {
  listYStart(0.24)
  lineHeight(62.0)
  listWidthFactor(0.64)
  textAlign("left")
  hintsBottomMargin(30.0)
  titleY(0.11)
}
```

**Save Slot Layout:**

```scala
val slots = menuLayout("slots") {
  listYStart(0.20)
  lineHeight(110.0)
  listWidthFactor(0.60)
  textAlign("left")
  hintsBottomMargin(30.0)
  titleY(0.10)
}
```

**Sidebar Layout (Off-Center):**

```scala
val sidebar = menuLayout("sidebar") {
  listYStart(0.20)
  lineHeight(58.0)
  listWidthFactor(0.30)
  textAlign("left")
  listXCenter(0.18)
  titleX(0.18)
  titleY(0.08)
  hintsBottomMargin(24.0)
}
```

**Chapter Select with Scroll:**

```scala
val chapters = menuLayout("chapters") {
  listYStart(0.22)
  lineHeight(54.0)
  listWidthFactor(0.60)
  textAlign("left")
  titleY(0.08)
  maxVisibleItems(6)
}
```

---

## Button Layout DSL

### Basic Usage

```scala
val layout = buttonLayout("main", resolution = "1920x1080", menuType = "main") {
  button("new_game") {
    label("New Game")
    tag("primary")
    bounds(0.25, 0.30, 0.50, 0.08)
    asset("assets/ui/btn.png", hover = "assets/ui/btn_hover.png")
  }
  button("load") {
    label("Load Game")
    bounds(0.25, 0.40, 0.50, 0.08)
  }
  button("quit") {
    label("Quit")
    bounds(0.25, 0.50, 0.50, 0.08)
    asset("assets/ui/btn_danger.png")
  }
}
```

### Top-Level Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `menuId` | String | required | Menu screen ID |
| `resolution` | String | `"default"` | Target resolution (e.g., `"1920x1080"`) |
| `menuType` | String | `null` | Menu type hint (e.g., `"main"`, `"save"`) |

### Button Methods

| Method | Parameters | Description |
|--------|-----------|-------------|
| `label(v)` | String | Display label |
| `tag(v)` | String | Custom tag for grouping |
| `bounds(x, y, w, h)` | 4 Doubles | Position and size |
| `boundsX(v)` / `boundsY(v)` / `boundsW(v)` / `boundsH(v)` | Double | Individual bound setters |
| `asset(normal, hover, disabled)` | 1–3 Strings | Button state images |
| `extra(key, value)` | 2 Strings | Custom key-value metadata |

### Button Layout Examples

**Scattered Menu (Non-List Layout):**

```scala
val scattered = buttonLayout("title", resolution = "1920x1080", menuType = "main") {
  button("new_game") {
    label("New Game")
    bounds(100, 400, 300, 60)
    asset("assets/ui/btn_gold.png", hover = "assets/ui/btn_gold_hover.png")
  }
  button("continue") {
    label("Continue")
    bounds(100, 480, 300, 60)
    asset("assets/ui/btn_silver.png", hover = "assets/ui/btn_silver_hover.png")
  }
  button("settings") {
    label("Settings")
    bounds(1520, 400, 300, 60)
  }
  button("quit") {
    label("Quit")
    bounds(1520, 480, 300, 60)
  }
  extra("theme", "fantasy")
}
```

**Save Slots with Per-Button Extras:**

```scala
val saveSlots = buttonLayout("save", resolution = "1920x1080", menuType = "save") {
  for i <- 1 to 6 do
    button(s"slot_$i") {
      label(s"Slot $i")
      val row = (i - 1) / 3
      val col = (i - 1) % 3
      bounds(100 + col * 600, 200 + row * 300, 560, 260)
      extra("slotIndex", i.toString)
    }
}
```

---

## DSL vs. Properties File

The DSL and properties files produce identical specs. Choose based on your workflow:

| Aspect | Properties File | Scala DSL |
|--------|----------------|-----------|
| **Runtime editing** | Edit text, run, observe | Recompile required |
| **Type safety** | None (strings only) | Compile-time checks |
| **IDE support** | Basic text editing | Full Scala IDE features |
| **Programmatic generation** | Manual string building | Native loops, variables |
| **Editor visual tools** | Full support | Not applicable |
| **Version control** | Clean diffs | Clean diffs |

### When to Use the DSL

- Generating many similar layouts/styles programmatically (e.g., themes)
- Building menu specs from data (e.g., generating save slot buttons)
- Test fixtures that need type-safe menu configuration
- Projects using Scala as the primary language

### When to Use Properties Files

- Standard content authoring (most projects)
- Using the editor's visual tools
- Text-first iteration workflow
- Team members who don't write Scala

---

## Integration with Java

The DSL produces standard Java records. Use them directly in Java code:

```java
// In Java, receive the Scala-built specs
MenuStyleSpec style = myScalaModule.darkBlueStyle();
MenuLayoutSpec layout = myScalaModule.compactLayout();
MenuButtonLayoutSpec buttons = myScalaModule.mainButtonLayout();

// Use like any loader-produced spec
String itemColor = style.itemColor();
double lineHeight = layout.lineHeight();
```

---

## Complete Example

```scala
import com.jvn.scala.dsl.MenuDsl.*

object MyMenuConfig:

  val mainStyle = menuStyle("main") {
    itemColor("#DCE6F8")
    selectedColor("#FFE8A3")
    hoverColor("#FFFFFF")
    disabledColor("#666688")
    font("Segoe UI", "SEMI_BOLD", 28)
    shadow("#00000088", 1.0, 1.0)
    opacity(1.0)
    prefix("  ", "▶ ", "  ")
    title("#F2F7FF", "Georgia", "BOLD", 56, "#000000AA")
    hints("#667788", "Segoe UI", 14)
    background("assets/backgrounds/title.png", "#050B16", 1.0)
  }

  val mainLayout = menuLayout("main") {
    listYStart(0.34)
    lineHeight(68.0)
    listWidthFactor(0.44)
    textAlign("center")
    hintsBottomMargin(36.0)
    titleY(0.14)
  }

  val submenuStyle = menuStyle("submenu") {
    itemColor("#C8D0E8")
    selectedColor("#FFE8A3")
    font("Segoe UI", "NORMAL", 24)
    prefix("  ", "→ ", "  ")
    title("#F2F7FF", "Georgia", "NORMAL", 40)
    hints("#667788", "Segoe UI", 14)
  }

  val submenuLayout = menuLayout("submenu") {
    listYStart(0.24)
    lineHeight(62.0)
    listWidthFactor(0.64)
    textAlign("left")
    titleY(0.11)
    listXCenter(0.35)
    titleX(0.35)
  }

  val mainButtons = buttonLayout("main", "1920x1080", "main") {
    button("new_game") {
      label("New Game")
      bounds(710, 367, 500, 55)
      asset("assets/ui/menu/btn.png", hover = "assets/ui/menu/btn_hover.png")
    }
    button("load") {
      label("Load Game")
      bounds(710, 435, 500, 55)
      asset("assets/ui/menu/btn.png", hover = "assets/ui/menu/btn_hover.png")
    }
    button("settings") {
      label("Settings")
      bounds(710, 503, 500, 55)
      asset("assets/ui/menu/btn.png", hover = "assets/ui/menu/btn_hover.png")
    }
    button("quit") {
      label("Quit")
      bounds(710, 571, 500, 55)
      asset("assets/ui/menu/btn_danger.png", hover = "assets/ui/menu/btn_danger_hover.png")
    }
  }
```

---

## Related Docs

- [Menu Styles](../../menus/menu-styles.md)
- [Menu Layouts](../structure/menu-layouts.md)
- [Menu Button Layouts](../structure/menu-button-layouts.md)
- [Colors & Theming](../styling/colors-theming.md)
- [Text-First Layout Workflow](../workflow/text-first-layout-workflow.md)
