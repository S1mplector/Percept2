# VNS Settings & Playback Modes

Complete reference for VN runtime settings and playback modes — text speed, audio volumes, skip mode, auto-play, UI visibility, click behavior, and physics tuning.

Settings: `core/src/main/java/com/jvn/core/vn/VnSettings.java`
Scene modes: `core/src/main/java/com/jvn/core/vn/VnScene.java`

---

## Overview

`VnSettings` holds all configurable player preferences for a VN playback session. Settings are:

- Persisted inside save data (restored on load)
- Adjustable from VNS scripts via commands
- Editable from the settings menu at runtime
- Independent per VnState instance

---

## Settings Reference

### Text & Dialogue

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| `textSpeed` | 30 | 1–200 | Milliseconds per character for typewriter reveal |
| `clickRevealBeforeAdvance` | true | — | First click reveals full text; second click advances |

### Audio Volumes

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| `bgmVolume` | 0.7 | 0–1 | Background music volume |
| `sfxVolume` | 0.8 | 0–1 | Sound effects volume |
| `voiceVolume` | 1.0 | 0–1 | Voice playback volume |

### Auto-Play

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| `autoPlayDelay` | 2000 | ≥ 500 | Milliseconds to wait before auto-advancing after text is fully revealed |

### Skip Mode

| Setting | Default | Description |
|---------|---------|-------------|
| `skipUnreadText` | false | If true, skip mode advances even through unread dialogue |
| `skipAfterChoices` | false | If true, skip mode continues after making a choice |

### Physics (JES integration)

| Setting | Default | Description |
|---------|---------|-------------|
| `physicsFixedStepMs` | 0 | Physics step interval (0 = variable timestep) |
| `physicsMaxSubSteps` | 4 | Max physics sub-steps per frame |
| `physicsDefaultFriction` | 0.2 | Default friction coefficient |

### Input

| Setting | Default | Description |
|---------|---------|-------------|
| `inputProfilePath` | `~/.jvn/input-bindings.properties` | Path to input bindings file |
| `inputProfileSerialized` | `""` | Serialized ActionBindingProfile |

---

## Playback Modes

### Skip Mode

Skip mode rapidly advances through dialogue without waiting for player input. It is mutually exclusive with auto-play.

**Behavior:**
- Text is revealed instantly (no typewriter animation)
- Node is auto-advanced immediately
- By default, only **read** dialogue is skipped (previously visited nodes)
- If `skipUnreadText=true`, all dialogue is skipped
- At **choice** nodes, skip mode pauses (unless `skipAfterChoices=true`)
- Activating skip mode automatically disables auto-play

**Toggle:**

```java
vnScene.toggleSkipMode();
```

**VNS commands:**

```vns
# Toggle skip mode
[skip toggle]

# Enable skip mode
[skip on]

# Disable skip mode
[skip off]
```

**Default key binding:** **Ctrl** (hold) or **S** (toggle)

### Auto-Play Mode

Auto-play mode automatically advances to the next dialogue line after a configurable delay, once text is fully revealed.

**Behavior:**
- Text reveals at normal speed (typewriter animation)
- After full reveal, waits `autoPlayDelay` milliseconds
- Then automatically advances to the next node
- At **choice** nodes, auto-play is disabled
- Activating auto-play automatically disables skip mode

**Toggle:**

```java
vnScene.toggleAutoPlayMode();
```

**VNS commands:**

```vns
# Toggle auto-play
[auto toggle]

# Enable auto-play
[auto on]

# Disable auto-play
[auto off]

# Set auto-play delay (ms)
[autodelay 3000]
```

**Default key binding:** **A** (toggle)

### UI Hidden Mode

Hides the textbox, name box, and dialogue UI to view the background and characters without obstruction.

**Behavior:**
- Textbox and all dialogue-related UI elements are hidden
- Characters and background remain visible
- Mode indicators (SKIP, AUTO) are still shown
- Any click or key press restores the UI

**Toggle from VNS:**

```vns
# Toggle UI
[ui toggle]

# Hide UI
[ui hide]

# Show UI
[ui show]
```

**Default key binding:** **H** (toggle) or **Right Click**

---

## Mode Indicators

The renderer displays HUD indicators in the top-right corner:

| Mode | Indicator Text |
|------|---------------|
| Skip active | `SKIP` |
| Auto-play active | `AUTO` |
| UI hidden | `UI OFF` |

These are localized via `Localization.t("hud.skip")`, `Localization.t("hud.auto")`, `Localization.t("hud.ui_off")`.

---

## VNS Settings Commands

### Text Speed

```vns
# Set text speed (ms per character)
[textspeed 20]   # Fast
[textspeed 50]   # Slow
[textspeed 30]   # Default
```

### Volume

```vns
# Set volumes (0.0 – 1.0)
[volume bgm 0.5]
[volume sfx 1.0]
[volume voice 0.8]
```

### Auto-Play Delay

```vns
# Set delay in milliseconds (minimum 500)
[autodelay 1500]
[autodelay 3000]
```

---

## Mode Interaction Rules

| Action | Skip | Auto-Play | Notes |
|--------|------|-----------|-------|
| Enable Skip | **ON** | OFF | Mutually exclusive |
| Enable Auto | OFF | **ON** | Mutually exclusive |
| Reach Choice | OFF* | OFF | *Unless `skipAfterChoices=true` |
| Load Save | Restored | Restored | State preserved in save data |
| New Game | OFF | OFF | Fresh state |

---

## Settings Persistence

Settings are saved and loaded as part of `VnSaveData.SettingsData`:

```java
// Saving
VnSaveData saveData = new VnSaveData();
VnSettings s = state.getSettings();
VnSaveData.SettingsData sd = new VnSaveData.SettingsData();
sd.setTextSpeed(s.getTextSpeed());
sd.setBgmVolume(s.getBgmVolume());
// ... all fields
saveData.setSettings(sd);

// Loading
VnSaveData.SettingsData sd = saveData.getSettings();
VnSettings s = state.getSettings();
s.setTextSpeed(sd.getTextSpeed());
s.setBgmVolume(sd.getBgmVolume());
// ... all fields
```

When loading a save, audio volumes are re-applied to the audio facade:

```java
audioFacade.setBgmVolume(s.getBgmVolume());
audioFacade.setSfxVolume(s.getSfxVolume());
audioFacade.setVoiceVolume(s.getVoiceVolume());
```

---

## HUD Messages

Temporary messages can be displayed as a HUD overlay:

```java
state.showHudMessage("Quick save complete!", 2000); // 2 seconds
```

HUD messages auto-expire after their duration. They are used internally for:
- Interop error reporting
- Quick save/load confirmations
- Mode change notifications

From VNS:

```vns
[hud "Quick save complete!" 2000]
```

---

## Click Behavior

The `clickRevealBeforeAdvance` setting controls click/tap behavior:

| Setting | First Click | Second Click |
|---------|------------|--------------|
| `true` (default) | Reveals full text instantly | Advances to next node |
| `false` | Advances immediately (skipping remaining text) | — |

This is implemented in `VnScene.advanceFromClick()`.

---

## Default Key Bindings

| Key | Action |
|-----|--------|
| **Space** / **Enter** / **Left Click** | Advance / reveal text |
| **S** | Toggle skip mode |
| **A** | Toggle auto-play mode |
| **H** / **Right Click** | Toggle UI hidden |
| **L** / **Backspace** | Toggle history overlay |
| **F5** | Quick save |
| **F9** | Quick load |
| **Scroll Up** / **Page Up** | Rollback |
| **Scroll Down** / **Page Down** | Roll forward |
| **Esc** | Open menu / back |

---

## Related Docs

- [VNS Overview](vns-scripting.md)
- [Save System](vns-save-system.md)
- [Rollback & History](vns-rollback-history.md)
- [Scene Lifecycle & State](vns-scene-lifecycle.md)
- [Commands Reference](vns-commands.md)
