# Debugging & Profiling

Practical guide to diagnosing issues in JVN projects — reading console output, interpreting diagnostics, isolating problems, and investigating performance.

---

## Overview

JVN uses SLF4J logging throughout the engine. When something goes wrong — a missing asset, a bad config key, a broken script — the engine produces diagnostic output in the console. This guide teaches you how to read that output, systematically isolate problems, and investigate performance issues.

---

## Console Output

### How to See Console Output

**From the editor:** Click Run in the Project Explorer. Console output appears in the editor's output panel or the terminal running the editor.

**From Gradle directly:**

```bash
./gradlew :runtime:run --args='--assets /path/to/project --script scripts/story/prologue.vns' 2>&1 | tee output.log
```

### Log Levels

| Level | Meaning | Action |
|-------|---------|--------|
| `ERROR` | Something broke — asset load failure, parser crash, save write error | Fix immediately |
| `WARN` | Something is wrong but the engine recovered — missing file, bad value, fallback used | Fix before shipping |
| `INFO` | Normal operation — startup, scene transitions, asset counts | Informational |
| `DEBUG` | Detailed internal state — usually only enabled during development | Ignore unless debugging |

### Common Log Prefixes

| Prefix/Source | Area |
|--------------|------|
| `MenuProfileLoader` | Menu config loading and validation |
| `VnUiLayoutLoader` | Dialogue layout parsing |
| `VnScriptParser` | VNS script parsing |
| `Simp3AudioService` | Audio playback |
| `VnSaveManager` | Save/load operations |
| `JesParser` | JES script parsing |
| `VnScene` | VN runtime state |

---

## Diagnostic Categories

### Script Diagnostics

**"Failed to load script 'X', falling back to DemoScenario"**

The VNS script couldn't be found or parsed. Check:
1. Script path is correct and the file exists
2. `--assets` points to the project root
3. The script has no syntax errors (run VNS diagnostics in the editor)

**Parser errors in console:**

```text
VNS parse error at line 42: unexpected token 'goto'
```

Open the script in the editor — the VNS Diagnostics panel shows all errors with click-to-jump.

### Asset Diagnostics

**"BGM file not found for trackId=X"**

Audio file path doesn't resolve. Check:
1. File exists at the specified path
2. Path is relative to project root (no leading `/`)
3. Case matches exactly (important on Linux)

**"Assets -> images=0, audio=0"**

The runtime can't find any assets. The `--assets` flag is missing or points to the wrong directory.

### Menu Diagnostics

See [Validation & Diagnostics](../../scripting/ui/layout/tooling/validation-diagnostics.md) for the complete list of menu/layout diagnostic messages.

Key patterns:
- `Invalid number for 'KEY'` — typo in a numeric value
- `Unknown menu action` — misspelled action or custom action (may be intentional)
- `extends missing` — parent file doesn't exist
- `Circular inheritance detected` — extends cycle

### Save Diagnostics

**"Save write failed"** — disk full or permissions issue. Check `~/.jvn/saves/`.

**"Migration applied to save"** — an older save was automatically upgraded. Normal behavior.

---

## Systematic Debugging Process

### Step 1: Reproduce

Run the project and confirm the issue. Note:
- What you did (which screen, which action)
- What you expected
- What actually happened
- Any console output

### Step 2: Read Console

Search console output for `WARN` and `ERROR` messages. Most issues produce a diagnostic message that points to the cause.

### Step 3: Isolate

**Is it a script issue?**
- Open the script in the editor
- Check VNS Diagnostics panel
- Test with a minimal script that just has the problematic section

**Is it an asset issue?**
- Verify the file exists at the exact path
- Test with a known-good asset
- Check the `Assets ->` line at startup

**Is it a config issue?**
- Check console for menu/layout diagnostics
- Verify key names against the documentation
- Try reverting to defaults (rename your config file, let the engine use built-in defaults)

**Is it a runtime issue?**
- Test with `--ui fx` (eliminates Swing backend as a variable)
- Test with `--audio fx` (eliminates Simp3 as a variable)
- Test without `--assets` (uses classpath only)

### Step 4: Fix and Verify

Make the smallest possible change. Run again. Confirm the issue is resolved and no new diagnostics appeared.

---

## Common Issues and Solutions

### "Nothing happens when I click a menu item"

1. Check the item's `action` in the `.menu` file
2. Look for "Unknown menu action" in console
3. Verify the action target exists (for `open_menu`, `run_script`)

### "Text doesn't appear / wrong font"

1. Check `dialogueTextFontFamily` in `dialogue.layout`
2. The font may not be installed on the target OS
3. Try a universally available font like `Arial` or `SansSerif`

### "Background is black"

1. Check the `@background` declaration path
2. Verify the image file exists
3. Check console for asset load errors
4. Verify `[bg <id>]` uses the correct background ID

### "Save/Load doesn't work"

1. Check console for save write errors
2. Verify `~/.jvn/saves/` directory exists and is writable
3. Check for schema migration warnings
4. Test with a fresh save (delete old saves)

### "Menu style looks wrong"

1. Check which style the screen references (`defaultItemStyle`)
2. Verify the style file exists and is registered
3. Look for validation warnings (orange borders in Layout Launcher)
4. Check `extends` chain for the style

---

## Performance Investigation

### Startup Performance

If startup is slow:

```bash
# Time the full build + run cycle
time ./gradlew :runtime:run --args='--assets /path/to/project --script scripts/story/prologue.vns'
```

Common causes:
- **Large asset directory** — many images/audio files slow asset catalog scan
- **Large scripts** — complex VNS scripts take longer to parse
- **Gradle overhead** — use `--no-daemon` and targeted module builds

### Runtime Performance

If the game runs slowly:

1. **Check entity count** — JES scenes with many entities are expensive
2. **Check physics body count** — pairwise collision checks scale quadratically
3. **Check text effects** — animated effects (shake, wave, rainbow) are per-character
4. **Check audio** — many simultaneous SFX channels consume resources

### Memory Usage

The editor's top bar shows CPU/GPU/RAM/FPS metrics. For runtime:

```bash
java -Xmx512m -jar jvn-runtime.jar --assets /path/to/project --script demo.vns
```

Common memory issues:
- **Large uncompressed images** — PNG/BMP at 4K resolution
- **Audio file extraction** — classpath audio is extracted to temp files
- **Accumulated SFX engines** — many rapid SFX plays create engine instances

### Build Performance

```bash
# Check Gradle daemon health
./gradlew --status

# Kill stale daemons
./gradlew --stop

# Run without daemon for clean timing
./gradlew --no-daemon :runtime:run --args='...'

# Compile only what you need
./gradlew :core:compileJava :runtime:compileJava
```

---

## Editor Debugging Tools

### VNS Diagnostics Panel

Shows all script errors and warnings with click-to-jump navigation. Available as a side panel (`+` → VNS Diagnostics).

### Label Flow Map

Visualizes label-to-label flow as a directed graph. Helps find:
- Unreachable labels
- Missing jump targets
- Unexpected flow paths

### Menu Navigation Validation

Use the text-first [Menu Actions And Navigation](../../scripting/ui/layout/structure/menu-actions.md)
workflow and runtime validation to find missing `OPEN_MENU` targets, unreachable menus, and wiring
errors.

### Layout Launcher Screen Cards

Shows validation warnings with orange borders:
- Missing layout/style references
- Missing navigation targets
- Unregistered screens

### Inspector Panel

For JES scenes — shows entity properties, physics state, and component values at runtime.

---

## Adding Debug Output

### In VNS Scripts

Use HUD messages for runtime debugging:

```vns
[hud Current label: start]
[hud Score value: ${score}]
```

### In Java Code

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger LOG = LoggerFactory.getLogger(MyClass.class);

LOG.info("Scene loaded: {}", sceneName);
LOG.warn("Unexpected state: {} at node {}", state, nodeIndex);
LOG.error("Failed to process: {}", item, exception);
```

### In JES Scripts

Use the `call` action to trigger debug handlers:

```jes
call "hud" { msg: "Debug: entity at x=100" }
```

---

## Diagnostic Quick Reference

| Symptom | First Check |
|---------|------------|
| Nothing loads | `--assets` flag and asset paths |
| Script not found | Script path and `--script` flag |
| Audio silent | `--audio` flag and file paths |
| Menu looks wrong | Console for menu diagnostics |
| Font wrong | `dialogue.layout` font family |
| Save fails | `~/.jvn/saves/` permissions |
| Slow startup | Asset count and Gradle overhead |
| Slow gameplay | Entity/physics body count |
| Editor crashes | Java version (needs JDK 21) |
| Build fails | `./gradlew --stop` then retry |

---

## Related Docs

- [Validation & Diagnostics (Layout)](../../scripting/ui/layout/tooling/validation-diagnostics.md)
- [Performance](performance.md)
- [Runtime Guide](../../runtime/core/runtime.md)
- [VNS Parsing Internals](../../scripting/vns/internals/vns-parsing.md)
- [Text-First Layout Workflow](../../scripting/ui/layout/workflow/text-first-layout-workflow.md)
