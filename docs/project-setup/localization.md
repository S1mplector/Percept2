# Localization Workflow

Complete guide to building multi-language JVN projects — locale-aware script loading, UI string localization, directory structure for translations, and practical patterns.

---

## Overview

JVN supports localization through two mechanisms:

1. **Locale-aware script loading** — the engine automatically selects translated script variants based on the active locale.
2. **UI string localization** — menu titles, hints, and system messages are resolved through a `Localization` class that maps keys to locale-specific strings.

The active locale is set at startup with the `--locale` CLI flag (default: `en`).

---

## Setting the Locale

### At Runtime

```bash
./gradlew :runtime:run --args='--locale ja --script scripts/story/prologue.vns --assets /path/to/project'
```

### In the Project Manifest

The `jvn.project` file can specify a default locale:

```properties
locale=en
```

---

## Locale-Aware Script Loading

When the engine loads a VNS script, it tries a locale-specific variant first:

### Resolution Order

For `scripts/story/prologue.vns` with `--locale ja`:

1. `scripts/story/prologue.ja.vns` ← locale-specific
2. `scripts/story/prologue.vns` ← fallback (default language)

### File Naming Convention

```text
scripts/story/
├── prologue.vns          # Default (English)
├── prologue.ja.vns       # Japanese
├── prologue.zh.vns       # Chinese
├── prologue.ko.vns       # Korean
├── prologue.de.vns       # German
├── prologue.fr.vns       # French
└── prologue.es.vns       # Spanish
```

The locale code is inserted before the `.vns` extension: `<name>.<locale>.vns`.

### How It Works

The `VnScenarioLoader` (used by `RuntimeVnInterop`, `JesVnBridge`, and all menu scenes) builds a candidate list:

```java
// For script "scripts/story/prologue.vns" with locale "ja":
candidates = [
    "scripts/story/prologue.ja.vns",   // locale variant
    "scripts/story/prologue.vns"        // fallback
]
```

The first candidate found on the filesystem or classpath is loaded.

### What Gets Localized in Scripts

The entire script file is replaced — this means **all** content is locale-specific:

- Dialogue text
- Choice labels
- Character display names (via `@character`)
- HUD messages
- Variable interpolation text

### Example: Bilingual Project

**Default (English):**
```vns
# scripts/story/prologue.vns
@scenario prologue
@character hero "Aria"

@label start
hero: Welcome to our story!

> Begin the adventure -> chapter1
> Learn more -> tutorial
```

**Japanese:**
```vns
# scripts/story/prologue.ja.vns
@scenario prologue
@character hero "アリア"

@label start
hero: 物語へようこそ！

> 冒険を始める -> chapter1
> もっと知る -> tutorial
```

Labels, jumps, and flow control remain the same — only display text changes.

---

## UI String Localization

Menu titles, hints, and system messages use the `Localization` class to resolve display text.

### Built-in Keys

The engine provides default English strings for standard UI elements:

| Key | Default (en) |
|-----|-------------|
| `main.title` | `"Main Menu"` |
| `load.title` | `"Load Game"` |
| `save.title` | `"Save Game"` |
| `settings.title` | `"Settings"` |
| `common.back` | `"Back"` |
| `common.confirm` | `"Confirm"` |

### Overriding via Menu Files

Menu screen `titleText` and `hintsText` in `.menu` files override the localization defaults:

```properties
# config/menu/menus/main.menu
titleText=My Visual Novel
hintsText=Enter: Select    Esc: Quit
```

For multi-language menus, you have two options:

**Option A: Locale-specific menu files**

```text
config/menu/menus/
├── main.menu           # Default
├── main.ja.menu        # Japanese (if engine supports locale-aware config loading)
```

**Option B: Use `{locale}` keys in a shared file** (if supported by your custom runtime code)

---

## Directory Structure for Translations

### Small Project (2–3 Languages)

Keep translations alongside originals with locale suffixes:

```text
scripts/story/
├── prologue.vns
├── prologue.ja.vns
├── chapter1.vns
├── chapter1.ja.vns
```

### Large Project (Many Languages)

Organize by locale directories for easier management:

```text
scripts/
├── story/              # Default (English)
│   ├── prologue.vns
│   └── chapter1.vns
├── story.ja/           # Japanese translations
│   ├── prologue.ja.vns
│   └── chapter1.ja.vns
└── story.zh/           # Chinese translations
    ├── prologue.zh.vns
    └── chapter1.zh.vns
```

Note: The engine resolves by filename pattern (`<name>.<locale>.vns`), not by directory. Place locale files wherever they're discoverable by the asset system.

### Shared Assets

Most assets (images, audio, UI) don't need localization. Only localize assets that contain text:

```text
assets/
├── backgrounds/        # Shared across all locales
├── characters/         # Shared
├── ui/
│   ├── textbox.png     # Shared (no text in image)
│   ├── title_en.png    # English title logo
│   └── title_ja.png    # Japanese title logo
```

Reference locale-specific assets in the locale-specific scripts:

```vns
# prologue.vns (English)
@background title assets/ui/title_en.png

# prologue.ja.vns (Japanese)
@background title assets/ui/title_ja.png
```

---

## Practical Patterns

### Pattern 1: Translate Only Dialogue, Share Logic

Keep game logic in one file and override only dialogue:

```vns
# scripts/common/characters.vns (shared)
@character hero "Aria"
@charimg hero neutral assets/characters/aria/neutral.png
```

```vns
# scripts/common/characters.ja.vns (Japanese override)
@character hero "アリア"
@charimg hero neutral assets/characters/aria/neutral.png
```

Both files define the same characters with different display names. The locale-aware loader picks the right one.

### Pattern 2: ICU Text Formatting for Numbers/Plurals

VNS supports ICU message format for locale-aware number and plural formatting:

```vns
narrator: You collected ${count, plural, one{# item} other{# items}}.
narrator: Your score is ${score, number}.
```

These format correctly regardless of locale.

### Pattern 3: Incremental Translation

Start with default language, add translations progressively:

1. Write all scripts in your primary language
2. The game is fully playable with `--locale en`
3. Add `.ja.vns` files one at a time
4. Missing translations automatically fall back to the default
5. Track translation progress with a checklist

### Pattern 4: Translation Testing

Run with each locale to verify:

```bash
# Test English
./gradlew :runtime:run --args='--locale en --assets /path/to/project --script scripts/story/prologue.vns'

# Test Japanese
./gradlew :runtime:run --args='--locale ja --assets /path/to/project --script scripts/story/prologue.vns'
```

---

## Font Considerations

Different languages may need different fonts:

| Language | Recommended Font | Notes |
|----------|-----------------|-------|
| English | Segoe UI, Arial | Standard Latin fonts |
| Japanese | Noto Sans JP, Yu Gothic | Must support kanji |
| Chinese | Noto Sans SC/TC, Microsoft YaHei | Simplified or Traditional |
| Korean | Noto Sans KR, Malgun Gothic | Must support Hangul |

If your project targets multiple languages, use a font that covers all required character sets (e.g., Noto Sans CJK) or switch fonts per locale in the dialogue layout.

---

## Runtime Validation Checklist

- [ ] Default locale loads all scripts without errors
- [ ] Target locale loads locale-specific script variants
- [ ] Missing locale files fall back to default gracefully
- [ ] Character display names appear in the correct language
- [ ] All dialogue text is in the expected language
- [ ] Choice labels are translated
- [ ] Menu titles and hints display correctly (if localized)
- [ ] ICU formatting works for numbers and plurals
- [ ] Fonts render all characters in the target language (no missing glyphs)
- [ ] Game flow (labels, jumps, choices) works identically in all locales

---

## Common Mistakes

**Labels don't match between locales:**
All locale variants must use the same `@label` names and `@scenario` IDs. Only display text should differ.

**Missing translation shows English:**
This is correct fallback behavior. If `prologue.ja.vns` doesn't exist, the engine uses `prologue.vns`. Track which files need translation.

**Wrong locale code:**
Use standard ISO 639-1 codes: `en`, `ja`, `zh`, `ko`, `de`, `fr`, `es`, `pt`, `ru`, etc.

**Font doesn't support target language:**
If Japanese text shows as boxes, the font doesn't have kanji glyphs. Switch to a CJK-capable font in `dialogue.layout`.

**Locale not passed to runtime:**
The `--locale` flag must be passed to the runtime. The editor's Run button uses the project manifest's locale setting.

---

## Related Docs

- [VNS Localization](../scripting/vns/vns-localization.md)
- [VNS Text Formatting & ICU](../scripting/vns/vns-text-formatting.md)
- [Fonts & Typography](../scripting/layout/fonts-typography.md)
- [Project Structure Conventions](project-structure.md)
- [Runtime Guide](../runtime/runtime.md)
