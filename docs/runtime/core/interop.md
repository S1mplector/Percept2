# Interop Guide

Interop is how VNS scripts, JES scenes, and Java code coordinate behavior at runtime.

Primary classes:
- `modules/core/src/main/java/com/jvn/core/vn/DefaultVnInterop.java`
- `modules/runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.java`

## Command Routing Model

When VNS parser sees `[call <provider> <payload>]` (or shorthand forms like `[jes ...]`), it emits a `VnExternalCommand`.

At runtime:

1. `VnScene` reaches that node.
2. active `VnInterop` receives provider + payload.
3. interop returns `advance` or `stay` depending on whether control flow already moved.

## Default Providers (`DefaultVnInterop`)

### `hud`

- Shows a temporary HUD message for 2 seconds.
- Example: `[hud Saved!]`

### `java`

- Reflection call to a **public static** method.
- Syntax: `[java fully.qualified.Class#method arg1 arg2 ...]`
- **Security restriction**: only classes under `com.jvn.*` are allowed.
- Argument coercion: `true`/`false` → boolean, numeric → int/double, quoted strings preserve spaces, everything else → string.
- Return value is shown as a HUD message.

```vns
[java com.jvn.game.GameUtils#addScore 100]
[java com.jvn.game.Debug#logEvent "chapter start"]
```

### `var`

State variable operations:

| Operation | Syntax | Description |
|-----------|--------|-------------|
| `set` | `[set key value]` | Set variable to value (auto-typed: int/double/boolean/string) |
| `inc` | `[inc key [delta]]` | Increment by delta (default 1). Auto-converts to int if result is whole |
| `dec` | `[dec key [delta]]` | Decrement by delta (default 1) |
| `mul` | `[mul key factor]` | Multiply by factor |
| `div` | `[div key divisor]` | Divide by divisor (division by zero is silently ignored) |
| `flag` | `[flag key]` | Set variable to `true` |
| `unflag` | `[unflag key]` | Set variable to `false` |
| `toggle` | `[toggle key]` | Flip boolean between `true` and `false` |
| `clear` | `[clear key]` | Remove variable entirely |

### `cond`

- Conditional jump logic using `VnConditionEvaluator`.
- Two accepted forms:
  - `[cond if <expr> goto <label>]`
  - `[cond <expr> goto <label>]` (bare form, `if` optional)
- Returns `stay` if jump occurs, `advance` otherwise.

### `settings`

Live settings changes, applied immediately:

```vns
[settings textspeed 20]               # text reveal speed (ms/char)
[settings autodelay 1500]              # auto-advance delay (ms)
[settings volume bgm 0.5]              # BGM volume (0.0–1.0)
[settings volume sfx 0.6]              # SFX volume
[settings volume voice 0.8]            # Voice volume
[settings display_width 1280]          # screen width (320–7680)
[settings display_height 720]          # screen height (180–4320)
[settings auto_fit_resolution true]    # auto-fit to player's screen
```

Volume changes are applied immediately to all currently playing tracks on that channel. Display settings take effect at the next frame or when the settings scene exits. See [Display & Resolution Settings](../systems/display-settings-guide.md) for implementation details.

### `save`

- `[save]` — quick save; shows "Saved" or "Save failed" HUD message.
- `[save quickload]` — quick load; shows "Loaded" or "No quick save" HUD message.

### `mode`

Runtime mode control for skip, auto-play, and dialogue presentation:

```vns
[mode skip]              # toggle skip mode
[mode skip on]           # enable (also accepts: true, 1)
[mode skip off]          # disable (also accepts: false, 0)
[mode auto]              # toggle auto-play
[mode auto on]           # enable auto-play (disables skip)
[mode dialogue standard] # standard ADV textbox
[mode dialogue nvl]      # NVL stacked dialogue panel
[mode dialogue bubble]   # speech-bubble dialogue
[mode nvl on]            # enable NVL mode
[mode nvl off]           # return to standard textbox
[mode bubble on]         # enable bubble mode
[mode bubble off]        # return to standard textbox
```

Enabling skip disables auto-play, and vice versa.

Dialogue presentation modes are stored in `ui.dialogueMode`. `standard` is the default ADV textbox, which is useful at the top of standalone scripts or tutorials that should not inherit NVL/bubble state from a previous scene. `dialogue`, `presentation`, and `say` are equivalent selectors:

```vns
[mode dialogue standard]
[mode presentation standard]
[mode say standard]
```

Supported dialogue mode tokens:

- `standard`, `normal`, `say`, `adv` -> standard textbox
- `nvl` -> NVL stacked dialogue
- `bubble` -> speech-bubble dialogue

Boolean subcommands accept `on`, `off`, `true`, `false`, `1`, and `0`; `nvl` and `bubble` also accept `toggle`.

`[mode dialogue standard]` selects the standard ADV presentation, but it still uses the project's configured textbox asset if `textBoxAsset` is set in `config/ui/dialogue.layout`. To force the built-in filled textbox at runtime, set the textbox asset mode before dialogue starts:

```vns
[mode dialogue standard]
[set ui.dialogueUi default]
[set ui.textBoxAsset default]
```

`ui.dialogueUi=default` disables custom dialogue skin behavior for standard dialogue: custom textbox/namebox images, custom textbox action buttons, custom dialogue/name text colors and fonts, and custom dialogue clipping. This is the broad override used by tutorial scripts and detached previews.

`ui.textBoxAsset` values `default`, `builtin`, `solid`, `fill`, `none`, `off`, `false`, `0`, and `no` only disable custom textbox and narration textbox images. Any other value leaves the configured assets enabled. The boolean alias `ui.textBoxAssetEnabled` can also be set directly:

```vns
[set ui.textBoxAssetEnabled false]
```

Custom textbox action buttons can be disabled without changing text styling:

```vns
[set ui.textBoxButtons off]
```

### `ui`

UI visibility and audio visualizer controls:

```vns
[ui]                     # toggle UI hidden
[ui hide]                # hide UI (alias: on)
[ui show]                # show UI (alias: off)
[ui toggle]              # toggle
```

**Audio visualizer** subcommand (`visualizer` or `viz` alias):

```vns
[ui visualizer]               # toggle visualizer
[ui viz on]                   # show (also: show, true, 1, yes)
[ui viz off]                  # hide (also: hide, false, 0, no)
[ui viz on bars=32]           # show with 32 bars
[ui viz on bars 64]           # alternative syntax
[ui viz on 48]                # bare number also accepted
[ui viz set color=#7de2ff]    # configure without toggling
[ui viz bars=24 glow=off]     # config-only shorthand
[ui viz set style=minimal]    # styles: dynamic, minimal
[ui viz set z=-15]            # render behind center/right sprites
[ui viz status]               # HUD summary: on/off, bars, style, spectrum status
[ui viz reset]                # clear stored visualizer tuning, keep enabled state
```

Supported visualizer options:

- `bars=<int>`: clamp to `8..96` (default `48`)
- `color=<css-color|auto>`: base color, `auto` enables animated hue cycling
- `accent=<css-color|auto>`: highlight/peak color
- `alpha=<0.1..1.0>`: overall overlay opacity
- `glow=<on|off>`: soft glow and beat flash
- `style=<dynamic|minimal>`: animated Simp3-style bars vs reduced styling
- `height=<0.2..1.0>`: fraction of the space above the textbox used by the visualizer
- `z=<int>` / `z-index=<int>`: layer order relative to character sprites (default `-100`)

The visualizer state is stored in:

- `ui.audioVisualizer`
- `ui.audioVisualizerBars`
- `ui.audioVisualizerColor`
- `ui.audioVisualizerAccent`
- `ui.audioVisualizerAlpha`
- `ui.audioVisualizerGlow`
- `ui.audioVisualizerStyle`
- `ui.audioVisualizerHeight`
- `ui.audioVisualizerZ`

### `history`

History overlay controls:

```vns
[history]                # toggle overlay
[history show]           # show overlay
[history hide]           # hide overlay
[history toggle]         # toggle
[history scroll 5]       # scroll by N lines (positive = down)
[history clear]          # reset scroll position
```

### `audio`

Advanced audio control with aliases:

| Command | Aliases | Description |
|---------|---------|-------------|
| `pause` | — | Pause BGM |
| `resume` | — | Resume BGM |
| `pause_all` | `pauseall` | Pause all audio channels |
| `resume_all` | `resumeall` | Resume all audio channels |
| `bgm_stop` | `stop_bgm` | Stop BGM |
| `sfx_stop` | `stop_sfx` | Stop all SFX |
| `voice_stop` | `stop_voice` | Stop all voice |
| `stop_all` | `all_stop`, `audio_stop_all` | Stop all audio |
| `seek` | — | Seek BGM to position in seconds |
| `crossfade` | — | Crossfade to new track |

**Crossfade syntax:**

```vns
[audio crossfade assets/audio/bgm/calm.ogg 2000]         # crossfade, loop=true (default)
[audio crossfade assets/audio/bgm/calm.ogg 2000 false]   # crossfade, no loop
[audio crossfade assets/audio/bgm/calm.ogg 2000 on]      # explicit loop
```

### `screen`

Visual screen effects with configurable parameters:

```vns
[screen shake]                           # default: intensity=8, duration=300ms
[screen shake 12 500]                    # custom intensity and duration
[screen flash]                           # default: strength=0.7, duration=180ms, white
[screen flash 1.0 400]                   # custom strength and duration (white)
[screen flash 0.8 200 1.0 0.0 0.0]      # red flash (R G B as 0.0–1.0)
[screen clear]                           # cancel all active shake and flash effects
```

| Parameter | Shake default | Flash default |
|-----------|--------------|---------------|
| Intensity/Strength | `8.0` | `0.7` |
| Duration (ms) | `300` | `180` |
| Color (R,G,B) | n/a | `1.0, 1.0, 1.0` (white) |

### `char`

Character choreography helper provider. `[character]` is accepted as an alias for `[char]`.

**Subcommands:**

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `global` | `global_position` | Enable/disable persistent position mode |
| `at` | `position`, `pos` | Set the character's anchor position |
| `move` | — | Animated slide to a new position (with optional expression, easing, duration) |
| `show` | — | Show character at a position with an expression |
| `expression` | `expr` | Change expression without moving; swaps instantly unless given a duration |
| `hide` | — | Animated exit |

**Move with easing and duration:**

```vns
[char hero move right smile ease_out_quad 500]
```

**Expression transition:**

```vns
[char hero expression angry]          # instant swap
[char hero expression neutral dur=120] # opt-in crossfade
[char hero expr surprised dur=180 easing=ease_out_quad]
```

Expression transitions only affect the rendered sprite blend. The character
keeps its current slot, layer order, global/detached position, and timeline
offsets while the old expression fades into the new one.

**Show subcommand:**

```vns
[char hero show center happy]
[char hero show at 0.3,0.5 neutral]
```

**Global mode** gives the character persistent position memory. When enabled, `[move]` and `[show]` produce smooth slide tweens instead of entrance animations. See [Character Motion](../../scripting/vns/presentation/vns-characters.md#global-position-mode) for details.

Example:

```vns
[char hero global on]
[char hero at center]
[char hero move right smile]
[char hero expr surprised]
[char hero hide]
```

### `jes_timeline` / `jes_timeline_inline`

- `jes_timeline`: plays named timeline from `TimelineRegistry`
- `jes_timeline_inline`: plays inline timeline payload generated from VNS `timeline { ... }` blocks
- supports entity transform keyframes, camera keyframes, and timeline audio cues

## Runtime-Only Providers (`RuntimeVnInterop`)

These providers are only available when running through the full runtime (not in editor preview or tests).

### `jes`

JES scene management:

| Subcommand | Syntax | Description |
|------------|--------|-------------|
| `push` | `[jes push <script> [label L] [with k=v ...]]` | Push JES scene onto stack |
| `replace` | `[jes replace <script> [label L] [with k=v ...]]` | Replace current scene with JES |
| `pop` | `[jes pop]` | Pop the top JES scene |
| `call` | `[jes call <name> k=v ...]` | Invoke a registered call handler on the top JES scene |

**Launch properties** (`with k=v`) are passed to the JES scene. If any `init` call handler is registered, it receives these props automatically on load.

```vns
[jes push game/minigames/puzzle.jes label after_puzzle with difficulty=hard lives=3]
[jes replace game/scenes/overworld.jes with chapter=2]
[jes call spawnEnemy type=skeleton count=3]
```

### `menu`

Opens built-in or custom menu scenes:

```vns
[menu settings]                  # push settings screen
[menu save]                      # push save screen
[menu load demo.vns]             # push load screen (defaultScript for new game fallback)
[menu main demo.vns]             # push main menu
[menu extras]                    # push custom menu by ID (loads as MainMenuScene with custom ID)
[menu gallery scripts/gallery.vns]  # custom menu with override script
```

Unknown menu IDs are treated as custom registered menu screens. The optional second token overrides the default script used for `run_script` / `new_game` actions.

### `vns`

VNS script flow transitions:

```vns
[vns push scripts/story/chapter2.vns]             # push new VN scene
[vns push scripts/story/chapter2.vns label act2]   # push and jump to label
[vns replace scripts/story/chapter3.vns]           # replace current VN scene
[vns goto chapter_end]                              # jump to label in current scene
[vns goto Chapter3:start]                           # cross-script jump (Arc:label)
```

**Cross-script `goto`**: `[vns goto Arc:label]` replaces the current scene with a new VN scene loaded from `Arc.vns` and jumps to `label`. If the arc name contains `.`, it's used as-is; otherwise `.vns` is appended.

Settings (text speed, volumes, skip behavior) are automatically carried to new VN scenes.

---

## JES ↔ VNS Bridge

When runtime loads JES from VNS, it attaches bridge call handlers:

### JES → VN (call handlers)

| Call Name | Props | Description |
|-----------|-------|-------------|
| `"return"` | `{ label: "L", score: 42, ... }` | Pop JES, copy props to VN variables, jump to label |
| `"vns"` | (same as return) | Alias for `"return"` |
| `"hud"` | `{ msg: "text" }` | Show HUD message (1.5s) |
| `"pop"` | `{}` | Pop JES scene without returning data |

**Return behavior:**
1. Pops the JES scene from the stack
2. Copies all props (except `label`/`goto`) into VN variables
3. Jumps to return label — explicit `label`/`goto` prop overrides the default label from the `push` call

### VN → JES (call invocation)

```vns
[jes call <handlerName> key1=value1 key2=value2]
```

Invokes a registered call handler on the top JES scene.

### JES → VNS Scene Launch (JesVnBridge)

JES can also launch VN scenes directly via the bridge:

```jes
call "startVns" { script: "scripts/story/dialogue.vns" label: "npc_chat" replace: false popOnExit: true }
```

| Prop | Default | Description |
|------|---------|-------------|
| `script` / `name` | `"demo.vns"` | VNS script to load |
| `label` | none | Starting label |
| `replace` | `false` | Replace vs push |
| `popOnExit` | `true` | Auto-pop the VN scene when it exits |

When the VN scene exits, the JES scene is unpaused and receives a `"vnsEnded"` call with `{ script, label }`.

### VN Character Proxy for Timelines

When a Puppeteer timeline runs in a VN context (no JES scene), character entities are bridged via `VnCharacterProxyEntity`. Timeline `x`/`y` values are interpreted as **pixel offsets** from the character's natural slot position, not absolute coordinates.

## Data Type Conventions

Common token parsing rules:
- `true` / `false` -> boolean
- numeric tokens -> integer or double
- other tokens -> string
- quoted strings preserve spaces for tokenized providers

## Practical Patterns

### Pattern: launch minigame, return score

```vns
[jes push game/minigames/aim.jes label after_game with stage=2]

@label after_game
Narrator: Final score was ${score}.
```

Inside JES:

```jes
call "return" { label: "after_game" score: 987 }
```

### Pattern: central Java utility call

```vns
[java com.example.GameDebug#logEvent chapter_start]
```

### Pattern: reusable Puppeteer timeline from VNS

```vns
[call jes_timeline hero_intro_pan]
```

### Pattern: one-off inline timeline near story text

```vns
timeline {
  cameraMove 0ms 0 0 1.0
  cameraMove 400ms 0 0 0.92
  playAudio "assets/audio/sfx/whoosh.ogg"
}
```

## Safety Notes

- Java interop is reflection-based and should be treated as trusted-script functionality.
- Prefer stable wrapper utility methods instead of exposing deep internals directly.
- Keep provider payload formats explicit in team scripting conventions.
