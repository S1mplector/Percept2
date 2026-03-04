# Sidebar — Puppeteer Launcher

Live VNS scene snapshot panel that tracks the editor cursor and provides one-click launch into the Puppeteer animation editor with full scene context.

Source: `editor/src/main/java/com/jvn/editor/ui/PuppeteerLauncherPanel.java`

---

## Overview

The Puppeteer Launcher is the bridge between VNS script authoring and Puppeteer animation editing. As you move the cursor in a `.vns` file, the panel continuously parses the script from line 1 through the cursor position, building a cumulative **scene snapshot** — the exact visual state the player would see at that point. One click launches Puppeteer with all entities pre-positioned.

- **Default side:** Right
- **Tab name:** Puppeteer Launcher
- **Updates:** Automatically on cursor movement within a `.vns` file

---

## UI Layout

```text
┌──────────────────────────────┐
│  Puppeteer Launcher          │
│  ────────────────────────    │
│  Line: 47                    │
│  [show hero center happy]    │
│  ────────────────────────    │
│  Scene Snapshot at Cursor    │
│  Label: battle_start         │
│  Background: park            │
│  Visible Characters:         │
│    hero @ center [happy]     │
│    villain @ right [angry]   │
│  ────────────────────────    │
│  [ Launch Puppeteer Here ]   │
└──────────────────────────────┘
```

---

## Panel Elements

| Element | Description |
|---------|-------------|
| **Header** | "Puppeteer Launcher" — bold title |
| **Line indicator** | Current cursor line number (1-indexed) |
| **Line text** | Trimmed content of the current line (max 80 characters, truncated with `…`) |
| **Scene Snapshot at Cursor** | Blue header for the snapshot section |
| **Label** | The most recent `@label` / `label` declaration before the cursor, or "(before first label)" |
| **Background** | The active background ID from the most recent `[bg]` / `[background]` command, or "—" |
| **Visible Characters** | List of character entries in monospace font, colored `#f0b673` |
| **Launch Puppeteer Here** | Blue button — disabled when no VNS file is active |

### Character Entry Format

Each visible character is displayed as:

```
charId @ position [expression]
```

For example: `hero @ center [happy]`

If the expression is `neutral`, the bracket portion is omitted: `hero @ center`

---

## Scene Snapshot Resolution

The panel implements a lightweight VNS scene state resolver. It processes every line from line 1 through the cursor position, recognizing these patterns:

### Label Tracking

| Pattern | Example | Effect |
|---------|---------|--------|
| `@label <name>` | `@label battle_start` | Sets `currentLabel` to `battle_start` |
| `label <name>` | `label intro` | Sets `currentLabel` to `intro` |

### Background Tracking

| Pattern | Example | Effect |
|---------|---------|--------|
| `[bg <id>]` | `[bg park]` | Sets `backgroundId` to `park` |
| `[background <id>]` | `[background sunset_beach]` | Sets `backgroundId` to `sunset_beach` |
| `@background <id> <path>` | `@background park assets/bg/park.png` | Stores `park → assets/bg/park.png` in `bgPaths` |

### Character Image Declarations

| Pattern | Example | Effect |
|---------|---------|--------|
| `@charimg <char> <expr> <path>` | `@charimg hero neutral assets/char/hero_neutral.png` | Maps `hero/neutral → path` in `charImgPaths` |
| `@charlayer <char> <layer> <path>` | `@charlayer hero eyes assets/char/hero_eyes.png` | Maps `hero.eyes → path` in `charLayerPaths` |
| `@charpreset <char> <expr> <spec>` | `@charpreset hero happy $eyes=happy $mouth=smile` | Resolves `$layer` references against `charLayerPaths`, stores composite in `charImgPaths` |

### Character Show / Hide

| Pattern | Example | Effect |
|---------|---------|--------|
| `[show <char> <pos>]` | `[show hero center]` | Adds `hero` at `center` with expression `neutral` |
| `[show <char> <pos> <expr>]` | `[show hero center happy]` | Adds `hero` at `center` with expression `happy` |
| `[hide <char>]` | `[hide hero]` | Removes `hero` from visible characters |

### External Character Commands

| Pattern | Example | Effect |
|---------|---------|--------|
| `@external character <id> show <pos>` | `@external character hero show center` | Adds character at position with `neutral` expression |
| `@external character <id> show <pos> <expr>` | `@external character hero show center happy` | Adds character at position with expression |
| `@external character <id> hide` | `@external character hero hide` | Removes character from visible set |
| `@external character <id> move <pos>` | `@external character hero move left` | Updates position, preserves current expression |
| `@external character <id> expr <expr>` | `@external character hero expr angry` | Updates expression, preserves current position |

### Processing Rules

- Lines are processed sequentially (order matters)
- Empty lines and comments (`#`) are skipped
- All pattern matching is case-insensitive
- Position values are normalized to lowercase
- A `[show]` for an already-visible character updates its position and expression
- A `[hide]` for a non-visible character is silently ignored
- The resolver handles both shorthand (`char`) and long-form (`character`) in external commands

---

## Scene Snapshot Data Model

The resolved snapshot is passed to Puppeteer as a `SceneSnapshot` object:

```java
static class SceneSnapshot {
    String currentLabel;                              // Active label name
    String backgroundId;                              // Active background ID
    List<CharacterEntry> characters;                  // Visible characters
    Map<String, String> bgPaths;                      // bgId → asset path
    Map<String, String> charImgPaths;                 // "charId/expression" → asset path
    Map<String, Map<String, String>> charLayerPaths;  // charId → (layerId → path)
}

static class CharacterEntry {
    String characterId;   // e.g., "hero"
    String position;      // e.g., "center", "left", "right"
    String expression;    // e.g., "happy", "neutral"
}
```

---

## What Puppeteer Receives

When you click **Launch Puppeteer Here**, the snapshot is used to construct a `JesScene2D`:

1. **Background** — A `Sprite2D` entity positioned at (0, 0) using the resolved asset path from `bgPaths[backgroundId]`
2. **Characters** — One `Sprite2D` per visible character, positioned at VN slot locations:
   - `left` → screen left third
   - `center` → screen center
   - `right` → screen right third
   - Other positions map to their respective screen coordinates
3. **Expression images** — Resolved from `charImgPaths["charId/expression"]` or composited from `charLayerPaths`
4. **Layer paths** — If a character uses layered sprites (`@charlayer`), all layer paths are available for Puppeteer's entity setup

This means the Puppeteer viewport shows exactly what the player would see at that script position, and you can immediately start authoring animations in that context.

---

## Preset Resolution

When the resolver encounters a `@charpreset` declaration like:

```text
@charpreset hero happy $eyes=happy $mouth=smile
```

It resolves `$layer=option` references against previously declared `@charlayer` entries:
- Looks up `charLayerPaths["hero"]["eyes"]` and `charLayerPaths["hero"]["mouth"]`
- Substitutes the base path with the option suffix
- Stores the resolved composite path in `charImgPaths["hero/happy"]`

This allows Puppeteer to display the correct composite sprite for preset expressions.

---

## Empty / Inactive State

When no `.vns` file is active:
- Line shows "Line: —"
- Line text shows "(no VNS file active)"
- Label shows "Label: —"
- Background shows "Background: —"
- Characters shows "—"
- Launch button is **disabled**

---

## Workflow Example

```text
1. Open scripts/chapter1.vns in the editor
2. Scroll to line 47:  [show hero center happy]
3. The Puppeteer Launcher panel instantly shows:
     Label: battle_start
     Background: forest
     Visible Characters:
       hero @ center [happy]
       ally @ left [neutral]
4. Click "Launch Puppeteer Here"
5. Puppeteer opens with:
     - forest background at (0, 0)
     - hero sprite at center position
     - ally sprite at left position
6. Author animation keyframes
7. Export as JES timeline code
8. Paste back into the VNS script
```

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all 14 sidebar panels
- [Puppeteer Editor Guide](../../puppeteer/puppeteer-editor-guide.md) — comprehensive Puppeteer usage after launch
- [Puppeteer JES DSL Reference](../../puppeteer/puppeteer-jes-dsl.md) — exported timeline code syntax
- [Puppeteer Overview & Architecture](../../puppeteer/puppeteer.md) — system architecture and data pipeline
