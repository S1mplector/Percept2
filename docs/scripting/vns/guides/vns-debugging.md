# VNS Debugging & Troubleshooting

How to diagnose and fix problems in VNS scripts — parse errors, runtime issues, visual glitches, audio problems, and interop failures.

---

## Diagnostic Tools

### Editor VNS Diagnostics Panel

The editor's **VNS Diagnostics** sidebar panel (`View → Sidebar → VNS Diagnostics`) provides live error detection.

- **Parse errors** — syntax errors, missing labels, unclosed `[if]` blocks
- **Warnings** — unused labels, unreachable code, missing assets
- **Click-to-jump** — click any diagnostic to navigate to the offending line
- Re-parsed on every save.

This is the fastest way to catch structural problems.

### Label Flow Map

The **Label Flow Map** sidebar (`View → Sidebar → Label Flow Map`) visualizes all label-to-label jumps as a directed graph. Use it to:

- Spot unreachable labels (nodes with no incoming edges)
- Find infinite cycles (circular jump paths with no interactive node in between)
- Verify choice branch targets connect back to shared paths

### Console Output

Runtime warnings appear in the Java console (stderr). Key messages to watch for:

```text
[VnScene] Warning: Hit max instant chain limit (1000). Possible infinite loop in script.
VN external [provider] failed: ExceptionType: message
```

### HUD Messages

The engine displays interop errors as temporary HUD overlays during playback. If you see a red/orange HUD message, it indicates a failed interop command. The format is:

```text
VN external [provider] failed: ExceptionType: detail
```

### Full-Screen Error Overlay

When a script error is severe enough (interop crash, inline Java compilation/runtime failure), the engine displays a **full-screen error overlay** that covers the entire canvas — similar to Ren'Py's traceback screen. This works in both the **editor VNS preview** and the **runtime game**.

The overlay shows:

- **Error type** — Parse Error, Runtime Error, Compilation Error, or Interop Error
- **File and line number** — when available from the error source
- **Cause message** — the exception detail in a monospace box
- **Stack trace** — truncated Java stack trace for debugging
- **Timestamp** — when the error occurred

Three action buttons appear at the bottom:

| Button | Action |
|--------|--------|
| **Ignore** | Dismiss the overlay and continue (the script remains at the current node) |
| **Reload** | Re-parse and reload the current script from disk (editor) or restart from node 0 (runtime) |
| **Copy** | Copy the full error summary to the system clipboard for pasting into bug reports |

The overlay can also be set programmatically from the editor:

```java
previewView.setActiveError(VnErrorOverlay.parseError("script.vns", 42, "Duplicate label"));
previewView.clearActiveError();  // dismiss
```

---

## Parse Errors

Parse errors halt script loading entirely. The error message includes file path, line number, and the offending text.

### Error: `Duplicate @scenario declaration`

**Cause:** Two `@scenario` directives in one file (or across includes).

```vns
@scenario chapter1
# ... content ...
@scenario chapter1_v2    # ERROR
```

**Fix:** Remove the duplicate. Each file should have exactly one `@scenario`.

### Error: `Duplicate label '<name>'`

**Cause:** Same `@label` name appears twice.

```vns
@label start
narrator: Hello.
@label start    # ERROR — duplicate
narrator: World.
```

**Fix:** Rename one of the labels. Watch for duplicates across `@include` files — use prefixed label names.

### Error: `Undefined label '<name>'`

**Cause:** A jump, choice, or conditional references a label that doesn't exist.

```vns
> Go to park -> park_scene    # ERROR if @label park_scene doesn't exist
```

**Fix:** Add the missing `@label` or fix the typo. Common causes:
- Misspelled label name
- Label in a different script file (use `[goto Script:label]` for cross-script jumps)
- Label was renamed but references weren't updated

### Error: `Unknown command '<cmd>'`

**Cause:** Unrecognized `[command]` in brackets.

```vns
[fadeout 1000]    # ERROR — not a valid command; use [bgm_fadeout 1000]
```

**Fix:** Check the [Commands Reference](../language/vns-commands.md) for correct syntax.

### Error: `Unclosed [if] block`

**Cause:** An `[if]` without a matching `[endif]`.

```vns
[if score >= 100]
  narrator: High score!
# Missing [endif]
```

**Fix:** Add `[endif]`. Use the diagnostics panel to find unmatched blocks.

### Error: `Unmatched [elif]` / `Unmatched [else]`

**Cause:** `[elif]` or `[else]` appears without a preceding `[if]`.

**Fix:** Add the opening `[if]` or remove the stray `[elif]`/`[else]`.

### Error: `Include cycle detected`

**Cause:** File A includes file B, which includes file A (directly or transitively).

```vns
# a.vns
@include b.vns    # b.vns includes a.vns → cycle!
```

**Fix:** Break the cycle. Extract shared definitions into a separate file that both include.

### Error: `Invalid condition expression`

**Cause:** Malformed boolean expression in `[if]`, `[elif]`, or choice condition.

```vns
[if gold >> 100 goto rich]    # ERROR — >> is not a valid operator
```

**Fix:** Use supported operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `&&`, `||`, `!`

---

## Runtime Issues

### Problem: Script appears to freeze

**Symptom:** No text appears, clicking does nothing.

**Possible causes:**

1. **Hit `MAX_INSTANT_CHAIN`** — an infinite loop in instant nodes (jumps, backgrounds, sets). Check the console for the warning message.
   - **Fix:** Ensure every loop path passes through an interactive node (dialogue or choice) or a `[wait]`.

2. **Blocking `[wait]` with 0 or negative duration** — technically completes instantly but might interact oddly with transitions.
   - **Fix:** Use `[wait]` only with positive durations.

3. **Transition blocking** — a `[transition]` command blocks until its duration completes.
   - **Fix:** Check that transition durations aren't excessively long.

4. **Non-advancing interop** — a `VnInteropResult.stay()` response from an external command that doesn't update the node index.
   - **Fix:** Check custom interop handlers for correct return values.

### Problem: Variables not updating

**Symptom:** `${variableName}` shows the wrong value or the raw `${...}` text.

**Possible causes:**

1. **Variable not declared** — `${score}` displays literally if `score` was never set.
   - **Fix:** Add `@var score = 0` or `[set score 0]` before use.

2. **Typo in variable name** — `${scroe}` (misspelled) won't match `score`.
   - **Fix:** Check spelling carefully.

3. **Variable set after interpolation** — the value changes on a later node, but you're reading the old state.
   - **Fix:** Ensure `[set]`/`[inc]`/`[dec]` commands come before the dialogue that reads the variable.

4. **Type mismatch in conditions** — comparing string `"10"` with number `10` works in VNS, but be aware that `[set score "10"]` (quoted) creates a string.
   - **Fix:** Omit quotes for numeric values: `[set score 10]`.

### Problem: Skip mode doesn't advance

**Symptom:** Skip mode is active but dialogue doesn't auto-advance.

**Possible causes:**

1. **`skipUnreadText` is false** — by default, skip mode only advances through previously-read dialogue.
   - **Fix:** Set `[settings skipUnreadText true]` or change the default in `VnSettings`.

2. **At a choice node** — skip mode pauses at choices unless `skipAfterChoices` is true.
   - **Fix:** Expected behavior. Player must select a choice.

### Problem: Save/load fails silently

**Symptom:** Quick save or load doesn't seem to work.

**Possible causes:**

1. **Scenario ID mismatch** — loading a save from a different `@scenario` is rejected.
   - **Fix:** Ensure the save was made from the same scenario.

2. **Save directory not writable** — the default `~/.jvn/games/<game-id>/saves/` might have permission issues.
   - **Fix:** Check filesystem permissions.

3. **Schema version mismatch** — older saves are auto-migrated, but extremely old formats might fail.
   - **Fix:** Check console logs for migration errors.

---

## Visual Issues

### Problem: Character doesn't appear

**Symptom:** `[show hero center]` does nothing visible.

**Possible causes:**

1. **Missing `@character` declaration** — the character ID isn't registered.
   - **Fix:** Add `@character hero "Hero Name"`.

2. **Missing `@charimg`** — the expression image isn't mapped.
   - **Fix:** Add `@charimg hero neutral assets/characters/hero_neutral.png`.

3. **Invalid position** — the position name is misspelled.
   - **Fix:** Use `left`, `center`, `right`, `far_left`, `far_right`, or a custom `@position` name.

4. **Character hidden by another** — overlapping layer orders.
   - **Fix:** Use explicit layer order: `[show hero center neutral 5]`.

### Problem: Character slides instead of appearing

**Symptom:** Character entrance animation looks wrong.

**Cause:** Global position mode is enabled — the character "remembers" its old position and slides from there.

**Fix:** If you want a fresh entrance, disable global mode first:

```vns
[char hero global off]
[show hero center neutral]
```

### Problem: Background doesn't change

**Symptom:** `[bg park]` doesn't update the background.

**Possible causes:**

1. **Missing `@background` declaration** — the ID isn't registered.
   - **Fix:** Add `@background park assets/backgrounds/park.png`.

2. **Asset file not found** — the path doesn't exist in the project or classpath.
   - **Fix:** Verify the file exists at the specified path.

### Problem: Transition looks broken

**Symptom:** `[transition FADE 800 night_sky]` shows black or flickers.

**Cause:** The target background ID in the transition isn't declared.

**Fix:** Ensure the target background is declared: `@background night_sky assets/bg/night.png`.

---

## Audio Issues

### Problem: BGM doesn't play

**Symptom:** `[bgm assets/audio/bgm/track.ogg]` produces no sound.

**Possible causes:**

1. **No `AudioFacade` set** — the scene doesn't have an audio backend.
   - **Fix:** Call `scene.setAudioFacade(audioFacade)` during initialization.

2. **File not found** — the audio path doesn't exist.
   - **Fix:** Verify the file exists at the exact path.

3. **Volume at zero** — BGM volume might be set to 0.
   - **Fix:** Check `[volume bgm]` commands and `VnSettings.bgmVolume`.

4. **Unsupported format** — only formats supported by your audio backend work (typically OGG, WAV, MP3).
   - **Fix:** Convert to a supported format.

### Problem: BGM plays over previous track

**Symptom:** Two BGM tracks overlap.

**Cause:** `[bgm]` starts new playback but the previous track wasn't stopped.

**Fix:** The engine should stop the previous BGM automatically. If it doesn't, explicitly stop first:

```vns
[bgm stop]
[bgm assets/audio/bgm/new_track.ogg]
```

### Problem: Audio visualizer not showing

**Symptom:** `[visualizer on 32]` doesn't display bars.

**Cause:** Audio visualizer requires FFT data from the audio backend. Not all backends support it.

**Fix:** Check if your audio backend supports spectrum analysis. Use `[visualizer status]` to confirm whether the runtime sees the backend as `live`, `waiting`, `stale`, or `unsupported`. The visualizer enable flag is `ui.audioVisualizer`.

---

## Interop Issues

### Problem: `[jes push]` does nothing

**Symptom:** JES scene doesn't appear.

**Possible causes:**

1. **JES script not found** — the path is wrong.
   - **Fix:** Verify the `.jes` file exists at the specified path.

2. **No `RuntimeVnInterop`** — the default `DefaultVnInterop` only shows a HUD message for `jes` commands. The full implementation is in `RuntimeVnInterop`.
   - **Fix:** Use `RuntimeVnInterop` instead of `DefaultVnInterop`.

3. **Return label missing** — if the `label` parameter references a non-existent label, the JES scene loads but can't return properly.
   - **Fix:** Ensure the return label exists in the VNS scenario.

### Problem: `[java]` reflection fails

**Symptom:** HUD shows "java: method not found" or "java: class not allowed".

**Possible causes:**

1. **Class not in allowlist** — by default, only classes with `com.jvn.` prefix are allowed.
   - **Fix:** Override `ALLOWED_JAVA_CLASS_PREFIXES` or use an allowed package.

2. **Method not static** — only `static` methods can be called from VNS.
   - **Fix:** Make the target method `static`.

3. **Wrong arg count** — the method signature doesn't match the number of args passed.
   - **Fix:** Use `String... args` for flexible arity, or match the exact count.

### Problem: Inline timeline doesn't animate

**Symptom:** `[jes_timeline_inline]` completes without visible animation.

**Possible causes:**

1. **No `SceneAccessor`** — the interop doesn't have a scene accessor configured.
   - **Fix:** Call `setSceneAccessor()` on the interop handler.

2. **Wrong entity name** — the timeline targets an entity name that doesn't match any character or scene entity.
   - **Fix:** Use the character ID as the entity name in the timeline block.

3. **Zero duration** — the timeline has no keyframes or all keyframes are at time 0.
   - **Fix:** Add keyframes with non-zero time values.

---

## Debugging Techniques

### Technique 1: HUD message breadcrumbs

Insert `[hud]` messages to trace execution flow:

```vns
[hud "Reached label: ch1_start" 1500]
@label ch1_start
# ...
[hud "Score is now: ${score}" 2000]
[if score >= 100 goto high_score]
[hud "Taking low score path" 1500]
```

### Technique 2: Variable inspection

Display variable values inline to verify state:

```vns
narrator: DEBUG: gold=${gold} trust=${trust} has_key=${has_key}
```

Remove these before shipping.

### Technique 3: Force-jump to a label

During testing, skip to a specific scene by adding a temporary jump at the top:

```vns
@label start
[jump debug_target]    # TEMP: skip to the scene under test

# ... normal content ...

@label debug_target
narrator: Testing this scene.
```

### Technique 4: Condition evaluation testing

Test conditions in isolation:

```vns
@label test_conditions
[set gold 150]
[hud "gold = ${gold}" 2000]
[if gold >= 100]
  [hud "PASS: gold >= 100" 2000]
[else]
  [hud "FAIL: gold < 100" 2000]
[endif]
[end]
```

### Technique 5: CI parse validation

Run all VNS scripts through the parser in CI to catch errors before release:

```bash
./gradlew :core:test   # includes VNS parser tests
```

Or create a custom validation task that loads each `.vns` file and reports parse errors.

### Technique 6: Save state inspection

Save files are stored as JSON at `~/.jvn/games/<game-id>/saves/`. Open them in a text editor to inspect:

- `currentNodeIndex` — which node the player is at
- `variables` — current variable state
- `visibleCharacters` — which characters are shown
- `currentBackgroundId` — active background

This helps diagnose state issues after loading a save.

---

## Error Message Quick Reference

| Message | Source | Meaning |
|---------|--------|---------|
| `Hit max instant chain limit (1000)` | VnScene | Possible infinite loop in instant nodes |
| `VN external [provider] failed: ...` | VnScene | Interop command threw an exception |
| `java: invalid target` | DefaultVnInterop | `[java]` target isn't `Class#method` format |
| `java: class not allowed` | DefaultVnInterop | Class not in allowed prefix list |
| `java: method not found` | DefaultVnInterop | No matching static method |
| `inline timeline: empty block` | DefaultVnInterop | `[jes_timeline_inline]` has no content |
| `inline timeline: no scene accessor` | DefaultVnInterop | No SceneAccessor configured |
| `jes_timeline: not found: <name>` | DefaultVnInterop | Named timeline not in TimelineRegistry |
| `Parse error in <file> at line <n>` | VnScriptParser | Syntax or structural error during parsing |

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Parsing Internals](../internals/vns-parsing.md) — parser pipeline and error catalog
- [Best Practices](vns-best-practices.md) — patterns to avoid bugs
- [Commands Reference](../language/vns-commands.md) — correct command syntax
- [Scene Lifecycle](../runtime/vns-scene-lifecycle.md) — node processing internals
- [Interop & Integration](../integration/vns-interop.md) — external command handling
