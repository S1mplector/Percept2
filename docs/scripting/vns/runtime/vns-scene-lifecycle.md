# VNS Scene Lifecycle & State

Complete reference for the VN runtime execution model — VnScene node processing, VnState management, node types, preflight, character visuals and tweening, screen effects, HUD messages, and timeline runners.

Scene: `core/src/main/java/com/jvn/core/vn/VnScene.java`
State: `core/src/main/java/com/jvn/core/vn/VnState.java`
Node types: `core/src/main/java/com/jvn/core/vn/VnNodeType.java`
Positions: `core/src/main/java/com/jvn/core/vn/CharacterPosition.java`

---

## Overview

`VnScene` is the runtime executor for VNS scripts. It implements the `Scene` interface and drives playback by processing nodes from a `VnScenario` one at a time, managed through a `VnState` object that holds all mutable playback state.

---

## Node Types

Each line in a compiled VNS script becomes a `VnNode` with a specific type:

| Type | Category | Description |
|------|----------|-------------|
| `DIALOGUE` | Interactive | Display dialogue text; waits for player input |
| `CHOICE` | Interactive | Present choices; waits for selection |
| `BACKGROUND` | Instant | Change the background image |
| `SHOW` | Instant | Show a character at a position |
| `HIDE` | Instant | Hide a character |
| `MOVE` | Instant | Slide a character to a new position (starts tween, chains immediately) |
| `JUMP` | Instant | Jump to a label |
| `CALL` | Instant | Call a subroutine (push return address) |
| `RETURN` | Instant | Return from subroutine |
| `AUDIO` | Instant | Play/stop/fade audio |
| `WAIT` | Blocking | Wait for a duration (ms) |
| `TRANSITION` | Blocking | Scene transition effect |
| `EXTERNAL` | Varies | External interop call (JES/Java/custom) |
| `END` | Terminal | End of scenario |

### Node Classification

```java
nodeType.isInteractive()  // DIALOGUE, CHOICE — require player input
nodeType.isInstant()      // BACKGROUND, SHOW, HIDE, MOVE, JUMP, CALL, RETURN, AUDIO — execute and chain
nodeType.isBlocking()     // WAIT, TRANSITION — block for a duration
```

---

## Scene Lifecycle

### Initialization

```java
VnScenario scenario = loader.load("story.vns");
VnScene scene = new VnScene(scenario);
scene.setAudioFacade(audioFacade);
scene.setInterop(interop);
```

### `onEnter()`

Called when the scene becomes active. Triggers `processCurrentNode()` to begin processing from node 0.

### `update(deltaMs)`

Called every frame. Handles:

1. **BGM fade** — ongoing volume reduction for `[bgm_fadeout]`
2. **Screen effects** — shake/flash decay
3. **Character animations** — entrance/exit/move tweens
4. **Timeline runners** — active Puppeteer timelines
5. **Wait nodes** — countdown for `[wait]` blocking
6. **Transition blocking** — countdown for `[transition]` blocking
7. **Text reveal** — typewriter animation (character-by-character)
8. **Skip mode** — instant text + auto-advance if applicable
9. **Auto-play** — countdown after full text reveal
10. **Choice nodes** — disable skip/auto at choices

### `processCurrentNode()` — The Command Loop

The core execution engine is an **iterative command loop** that processes nodes until reaching an interactive or blocking node:

```text
while (instantCount < MAX_INSTANT_CHAIN) {
    node = state.getCurrentNode()
    
    DIALOGUE  → process and RETURN (interactive)
    CHOICE    → disable skip/auto and RETURN (interactive)
    BACKGROUND → process and CONTINUE
    SHOW      → process and CONTINUE
    HIDE      → process and CONTINUE
    MOVE      → start slide tween, queue expression switch, CONTINUE
    JUMP      → process and CONTINUE
    CALL      → push return, jump, CONTINUE
    RETURN    → pop return, CONTINUE
    AUDIO     → process and CONTINUE
    WAIT      → start timer and RETURN (blocking)
    TRANSITION → start transition and RETURN (blocking)
    EXTERNAL  → process and CONTINUE (or RETURN if non-advancing)
    END       → RETURN (terminal)
}
```

**Safety limit:** `MAX_INSTANT_CHAIN = 1000` prevents infinite loops from misconfigured scripts.

---

## Preflight State

When jumping to a label (e.g., from a loaded save), the engine needs to reconstruct the visual state that *would* exist at that point. `preflightState(targetIndex)` replays nodes 0 through `targetIndex - 1` non-interactively:

```java
scene.preflightState(targetNodeIndex);
scene.onEnter();
```

Preflight applies:
- `BACKGROUND` — sets current background
- `SHOW` — shows characters (immediate, no animation)
- `HIDE` — hides characters
- `MOVE` — applies target position immediately (no slide tween)
- `TRANSITION` — applies target background
- `AUDIO` — processes audio commands
- `EXTERNAL` — only safe providers (`var`, `ui`, `audio`, `char`, `settings`, `mode`, `screen`, `history`)

Preflight skips: `DIALOGUE`, `CHOICE`, `WAIT`, `JUMP`, `CALL`, `RETURN`, `END`

---

## VnState

`VnState` is the central mutable state container. It holds everything about the current playback:

### Core State

| Field | Description |
|-------|-------------|
| `scenario` | The active `VnScenario` |
| `currentNodeIndex` | Position in the node list |
| `currentBackgroundId` | Active background ID |
| `variables` | Map of script variables |
| `waitingForInput` | Whether player input is needed |
| `textRevealProgress` | Characters revealed so far |

### Character System

| Field | Description |
|-------|-------------|
| `visibleCharacters` | Position → `CharacterSlot` (charId, expression, layerOrder) |
| `characterVisuals` | Position → `CharacterVisual` (tweening state) |
| `pendingExpressionSwitches` | Delayed expression changes (after move completes) |
| `globalPositionCharacters` | Characters with persistent position memory |
| `characterDefinedPositions` | Character → last assigned position |

### Character Positions

```java
public enum CharacterPosition {
    LEFT,
    CENTER,
    RIGHT,
    FAR_LEFT,
    FAR_RIGHT
}
```

### Character Tweening

Character show/hide/move operations use `CharacterVisual` for smooth animation:

| Property | Description |
|----------|-------------|
| `alpha` | Current opacity (0–1) |
| `offsetX` | Horizontal pixel offset |
| `offsetY` | Vertical pixel offset |
| `animating` | Whether a tween is active |
| `removeOnComplete` | Remove slot when animation finishes |

**Animation constants:**
- Entrance tween: 220ms, ease-out-quad, 60px slide offset
- Move between positions: 320ms, ease-out-quad, 220px step offset
- Expression fade: 180ms crossfade

```java
// Show with entrance animation (fade in + slide from edge)
state.showCharacterAnimated(position, characterId, expression);

// Hide with exit animation (fade out + slide)
state.hideCharacterAnimated(position);

// Show immediately (no animation, e.g., during load)
state.showCharacter(position, characterId, expression);

// Global position: character moves between positions instead of teleporting
state.showCharacterAnimated(CENTER, "hero", "neutral");
// Later:
state.showCharacterAnimated(RIGHT, "hero", "happy"); // slides from CENTER to RIGHT
```

### Subsystems

| Field | Description |
|-------|-------------|
| `history` | `VnHistory` — dialogue backlog |
| `settings` | `VnSettings` — player preferences |
| `rollbackStack` | `VnRollbackStack` — rewind/forward system |
| `readNodes` | Set of visited node indices |
| `callStack` | Subroutine return addresses |
| `activeTimelines` | Running `TimelineRunner` instances |

### Screen Effects

```java
// Screen shake
state.startScreenShake(float intensity, long durationMs);
// intensity: pixel displacement magnitude
// Decays over duration via update loop

// Screen flash
state.startScreenFlash(float r, float g, float b, float strength, long durationMs);
// RGB color (0–1), strength (0–1), decays over duration
```

Fields:

| Field | Description |
|-------|-------------|
| `screenShakeIntensity` | Current shake magnitude |
| `screenShakeDurationMs` | Total shake duration |
| `screenShakeRemainingMs` | Remaining shake time |
| `flashR/G/B` | Flash color |
| `flashStrength` | Current flash opacity |
| `flashDurationMs` | Total flash duration |
| `flashRemainingMs` | Remaining flash time |

### HUD Messages

```java
state.showHudMessage("Saved!", 2000); // Display for 2 seconds
```

| Field | Description |
|-------|-------------|
| `hudMessage` | Current message text |
| `hudMessageExpireAt` | Epoch millis when message disappears |

### Overlay State

| Field | Description |
|-------|-------------|
| `uiHidden` | Textbox/dialogue hidden (H key) |
| `historyOverlayShown` | History backlog visible |
| `historyScroll` | Scroll offset in history |
| `saveSlotOverlayShown` | Save/load slot picker visible |
| `saveSlotOverlayIsSaveMode` | True = save, false = load |
| `saveSlotSelected` | Currently selected slot (0–9) |

---

## Dialogue Processing

When a `DIALOGUE` node is reached:

1. Speaker name and text are **interpolated** (variable substitution via `VnTextFormatter`)
2. Entry is added to **history**
3. If the dialogue has a character, that character is **shown** at the specified position
4. **Rollback state** is captured
5. Text reveal begins (typewriter at `textSpeed` ms/char)
6. After full reveal, `waitingForInput=true`

### Text Reveal Flow

```text
DIALOGUE node reached
  ├── textRevealProgress = 0
  ├── Each frame: timer += deltaMs
  │   └── If timer >= textSpeed: reveal one more character
  ├── When progress == textLength: waitingForInput = true
  │   ├── Skip mode: instant reveal + auto-advance
  │   └── Auto-play mode: wait autoPlayDelay then advance
  └── Player click/key: advance()
```

---

## Choice Processing

When a `CHOICE` node is reached:

1. Skip mode is disabled (unless `skipAfterChoices=true`)
2. Auto-play mode is disabled
3. `waitingForInput=true`
4. Player selects a choice via `selectChoice(index)`
5. Choice **condition** is evaluated if present
6. If the choice has a `targetLabel`, jump to it; otherwise advance

---

## Advance Mechanism

```java
// Keyboard/automatic advance
scene.advance();

// Mouse/touch advance (respects clickRevealBeforeAdvance)
scene.advanceFromClick();

// Choice selection
scene.selectChoice(choiceIndex);
```

---

## Timeline Runners

Active Puppeteer timelines are stored in `VnState.activeTimelines`:

```java
state.addTimelineRunner(runner);
// Updated each frame:
state.updateTimelineRunners(deltaMs);
// Finished runners are auto-removed
```

---

## Interop Flow

`EXTERNAL` nodes are dispatched through `VnInterop`:

```java
VnInteropResult result = interop.handle(externalCommand, scene);
if (result.shouldAdvance()) {
    state.advance();
}
```

Non-advancing results (e.g., conditional jumps) keep the command loop running from the updated node index.

Interop errors are caught and reported as HUD messages:

```text
VN external [provider] failed: ExceptionType: message
```

---

## RPG State

An optional `rpgState` object on `VnState` provides passthrough for game-specific state:

```java
state.setRpgState(new RpgState());
RpgState rpg = (RpgState) state.getRpgState();
```

This is serialized/deserialized alongside VN saves.

---

## Related Docs

- [VNS Overview](vns-scripting.md)
- [Save System](vns-save-system.md)
- [Rollback & History](vns-rollback-history.md)
- [Settings & Playback Modes](vns-settings-modes.md)
- [Characters & Sprites](vns-characters.md)
- [Transitions & Screen Effects](vns-transitions.md)
- [Interop & Integration](vns-interop.md)
