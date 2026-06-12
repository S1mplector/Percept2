# VNS Localization

Complete reference for locale-aware VNS script loading, UI string localization, and multi-language project structure.

Scenario loader: `modules/core/src/main/java/com/jvn/core/vn/VnScenarioLoader.java`
Script loader: `modules/core/src/main/java/com/jvn/core/localization/LocalizedScriptLoader.java`
UI strings: `modules/core/src/main/java/com/jvn/core/localization/Localization.java`

---

## Overview

JVN supports three complementary localization systems:

1. **Script localization** — locale-aware loading of `.vns` files, allowing entire scripts to be translated
2. **UI string localization** — key-based string lookup for engine UI elements (HUD, menus, mode indicators)
3. **Source-text catalogs** — extracted dialogue, choices, menu labels, and overlay text resolved through generated `source.<hash>` keys

---

## Script Localization

### Resolution Order

When loading a script (e.g., `chapter1.vns`) with locale `ja`:

1. `game/scripts/chapter1.ja.vns` — locale suffix before extension
2. `game/scripts/ja/chapter1.vns` — locale subdirectory
3. `game/scripts/chapter1.vns` — fallback (default locale)

The first match wins. English (`en`) is treated as the default and skips locale-specific candidates.

### Project Structure

```text
game/scripts/
├── chapter1.vns              # Default (English)
├── chapter1.ja.vns           # Japanese — suffix style
├── chapter2.vns
├── ja/
│   └── chapter2.vns          # Japanese — directory style
├── de/
│   ├── chapter1.vns          # German
│   └── chapter2.vns
└── fr/
    └── chapter1.vns          # French
```

Both naming conventions work and can be mixed within the same project. Choose one style for consistency.

### VnScenarioLoader

The centralized loader combines locale resolution with asset catalog lookup:

```java
VnScenarioLoader loader = new VnScenarioLoader();
VnScenario scenario = loader.load("chapter1.vns");
```

It resolves candidates via `LocalizedScriptLoader` and then tries each path against the `AssetCatalog`.

### LocalizedScriptLoader API

```java
LocalizedScriptLoader loader = new LocalizedScriptLoader(
    Thread.currentThread().getContextClassLoader(),
    "game/scripts/"
);

// Load with explicit locale
InputStream in = loader.load("chapter1.vns", "ja");

// Load with current global locale
InputStream in = loader.load("chapter1.vns");

// Get candidate paths (for debugging/tooling)
List<String> paths = loader.getCandidatePaths("chapter1.vns", "ja");
// ["game/scripts/chapter1.ja.vns", "game/scripts/ja/chapter1.vns", "game/scripts/chapter1.vns"]

// Check if a localized version exists
boolean hasJa = loader.hasLocalizedVersion("chapter1.vns", "ja");

// Discover available locales for a script
List<String> locales = loader.getAvailableLocales("chapter1.vns");
// e.g., ["en", "ja", "de"]
```

### Checked Locale Codes

`getAvailableLocales()` scans these common codes:

`en`, `ja`, `zh`, `ko`, `de`, `fr`, `es`, `it`, `pt`, `ru`

---

## UI String Localization

### String Files

UI strings are loaded from UTF-8 `.properties` files. New projects use `config/locales`, while `game/strings` and `strings` remain supported for older layouts:

```text
config/locales/
├── en.properties    # English (default)
├── ja.properties    # Japanese
├── de.properties    # German
└── fr.properties    # French
```

### Properties Format

```properties
# config/locales/en.properties
hud.skip=SKIP
hud.auto=AUTO
hud.ui_off=UI OFF
menu.new_game=New Game
menu.load=Load Game
menu.settings=Settings
menu.quit=Quit
```

```properties
# config/locales/ja.properties
hud.skip=スキップ
hud.auto=オート
hud.ui_off=UI非表示
menu.new_game=ニューゲーム
menu.load=ロード
menu.settings=設定
menu.quit=終了
```

### Initialization

```java
// At startup, before any UI is created
Localization.init("ja", Thread.currentThread().getContextClassLoader());
```

Resolution order:
1. `config/locales/ja.properties`
2. `game/strings/ja.properties`
3. `strings/ja.properties`
4. The same directories for `en.properties` fallback

### String Lookup

```java
String text = Localization.t("hud.skip");  // Returns "スキップ" if ja is active
String missing = Localization.t("unknown.key"); // Returns "unknown.key" (key itself)
String source = Localization.tSource("Start Game"); // Source-text catalog lookup
```

The `t()` method returns the key string itself if no translation is found, making missing translations visible in the UI.

### Current Locale

```java
String locale = Localization.locale(); // "ja"
```

---

## Localization Workflow

### 1. Set Up Default Scripts

Write all scripts in the default language (typically English):

```text
game/scripts/prologue.vns
game/scripts/chapter1.vns
game/scripts/chapter2.vns
```

### 2. Extract a Translation Catalog

Run the extractor from the engine workspace:

```bash
./gradlew extractJvnTranslations -PjvnGameProject=/path/to/game -PjvnLocale=ja -PjvnEmptyMissing=true
```

The task scans `.vns`, `.menu`, `.layout`, `.style`, `.screen`, and related properties files, then writes:

```text
config/locales/ja.properties
```

Existing translated values are preserved when you run the command again. Use `updateJvnTranslations` as a clearer alias when refreshing an existing catalog:

```bash
./gradlew updateJvnTranslations -PjvnGameProject=/path/to/game -PjvnLocale=ja -PjvnEmptyMissing=true
```

Useful options:

| Property | Description |
|----------|-------------|
| `-PjvnGameProject=<dir>` | Project to scan. Alias: `-PjvnTranslationProject=<dir>` |
| `-PjvnLocale=<code>` | Target locale. Defaults to `runtime.locale` / `locale` in `jvn.project`, then `en` |
| `-PjvnSourceLocale=<code>` | Source locale, default `en` |
| `-PjvnTranslationOutput=<file>` | Custom output file |
| `-PjvnEmptyMissing=true` | Leave new non-source-locale entries blank for translators |
| `-PjvnDryRun=true` | Print scan summary without writing |

Generated entries look like:

```properties
# kind: dialogue
# source: scripts/story/prologue.vns:12 dialogue speaker=alice
# original: Welcome to JVN.
source.d76c7b3c02a1=JVNへようこそ。
```

At runtime, literal dialogue, choices, menu labels, and reactive screen text use these `source.<hash>` entries automatically. You can still use explicit `i18n:key` values for strings you want to name by hand.

### 3. Create Localized Script Copies When Needed

For each target language, create translated versions:

```text
game/scripts/ja/prologue.vns
game/scripts/ja/chapter1.vns
game/scripts/ja/chapter2.vns
```

Use full script copies when sentence order, branch structure, or language-specific staging needs to diverge from the source. Use source-text catalogs when the script flow stays the same.

### 4. Create or Edit String Files

```text
config/locales/en.properties
config/locales/ja.properties
```

### 5. Initialize at Startup

```java
String userLocale = System.getProperty("user.language", "en");
Localization.init(userLocale, classLoader);
```

### 6. Load Scripts Normally

```java
VnScenarioLoader loader = new VnScenarioLoader();
VnScenario scenario = loader.load("chapter1.vns");
// Automatically picks up ja/chapter1.vns if locale is "ja"
```

---

## Best Practices

- **Keep script structure identical** across locales — same labels, same choices, same variables. Only dialogue text and speaker names should differ.
- **Use the directory style** (`ja/chapter1.vns`) for projects with many scripts, to avoid filename clutter.
- **Use the suffix style** (`chapter1.ja.vns`) for small projects or single-file scripts.
- **Always provide a default** (`chapter1.vns`) as fallback.
- **Test with `getCandidatePaths()`** to verify resolution order for your setup.
- **Use `${var}` interpolation** in all locales — variable names should remain consistent across translations.

---

## Integration with VNS Text Formatting

Localized scripts can use all standard VNS text features:

```vns
# In ja/chapter1.vns
@scenario chapter1
@character hero "勇者"

@label start
hero: こんにちは、${playerName}さん！
hero: {score, plural, other{#ポイント}}獲得しました。
```

ICU formatting patterns (`plural`, `select`, `number`) work in any locale — the patterns themselves should be localized to match the target language's grammar.

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Text Formatting & ICU](../language/vns-text-formatting.md)
- [Variables & Conditions](../language/vns-variables.md)
- [Scene Lifecycle & State](vns-scene-lifecycle.md)
