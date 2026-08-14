# UI By Example — Choose the Right UI System

Map an interface idea to the JVN system that owns its lifecycle before writing layout properties.

**Difficulty:** Beginner
**Time:** 10 minutes
**Concepts:** UI layers, source ownership, normalized geometry, state boundaries

---

## The Decision Rule

Start with the interaction's owner:

| If the player is... | Start with... |
|---|---|
| Reading dialogue or choosing a story branch | `dialogue.layout` |
| Navigating a game-level screen or list | Menu profiles |
| Responding to a small story-state overlay | Reactive `.screen` |
| Inspecting a composed, variable-driven panel | `.facet` |
| Interacting inside a gameplay scene | JES widgets |
| Using the story phone | `phone.properties` plus VNS phone commands |

Do not pick by appearance alone. A Facet and a menu can both look like a card, but a menu owns focus navigation and a screen stack while a Facet owns a live VN-state overlay.

---

## One Project, Several UI Sources

```text
config/
├── ui/
│   └── dialogue.layout
├── menu/
│   ├── registry/menu.registry
│   ├── menus/main.menu
│   ├── layouts/default.layout
│   └── styles/default.style
├── screens/
│   └── confirm.screen
├── facets/
│   └── status.facet
├── phone/
│   └── phone.properties
├── gallery/
│   ├── gallery.properties
│   └── music-room.properties
└── locales/
    └── en.properties

scripts/
├── story/prologue.vns
└── ui/hud.jes
```

All properties formats use `key=value`, UTF-8 text, and `#` comments. Asset paths are project-relative and use forward slashes.

---

## The Smallest Example in Each Layer

### Dialogue presentation

```properties
# config/ui/dialogue.layout
textBoxX=0.05
textBoxY=0.74
textBoxWidth=0.90
textBoxHeight=0.22
textBoxColor=#111827DD
dialogueTextColor=#F8FAFC
```

### Menu profile

```properties
# config/menu/menus/main.menu
titleText=My Game
items=start,quit
item.start.label=Start
item.start.action=new_game
item.quit.label=Quit
item.quit.action=quit
```

### Reactive screen

```properties
# config/screens/notice.screen
title=Notice
text=${notice_text}
buttons=close
button.close.label=Close
button.close.action=hide
```

### Facet

```properties
# config/facets/status.facet
title=Status
nodes=name
node.name.type=text
node.name.text=${player_name}
buttons=close
button.close.action=hide
```

### JES widget

```jes
entity "pause_button" {
  component Button2D {
    x: 680
    y: 24
    w: 96
    h: 40
    text: "Pause"
    call: "pauseGame"
  }
}
```

---

## Who Owns What?

| Concern | Owner |
|---|---|
| Dialogue and choice geometry | `dialogue.layout` |
| Menu content and actions | `.menu` |
| Menu list geometry | `.layout` |
| Menu colors, fonts, button skins | `.style` |
| Overlay content and conditions | `.screen` or `.facet` |
| Story variables | VNS/VN state |
| Gameplay widget behavior | JES call handlers, usually registered in Java |
| Translated strings | `config/locales/<locale>.properties` |

Keep that separation even when several files produce one screen. It lets one layout or style serve many menu screens without duplicating content.

---

## Coordinate Systems

JVN uses both normalized and pixel measurements:

- Viewport fractions such as `0.5` scale with the window.
- Font sizes, line heights, padding, and some component sizes are pixels.
- Menu item bounds treat values at or below `1.0` as fractions and larger values as pixels.
- Facet node coordinates are normalized within their parent.
- JES scene coordinates are pixels in the scene's coordinate space.

Always identify the coordinate space before tuning a number.

---

## Key Takeaways

1. Choose the UI system by lifecycle and input behavior, not visual shape.
2. Dialogue, menu, reactive, Facet, JES, phone, and gallery sources solve different problems.
3. Keep content, geometry, presentation, state, and behavior in their owning files.
4. Use project-relative asset paths and stable IDs across every layer.
5. Test fractional and pixel measurements at more than one viewport.

---

## Next

Build the story-facing layer in [Dialogue, Name Boxes, and Character Framing](02-dialogue-and-character-framing.md).

[Back to UI By Example](../ui-by-example.md)
