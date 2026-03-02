# VN Settings Reference

Complete reference for all JVN runtime settings — fields, defaults, valid ranges, persistence, and how settings integrate with the settings menu, save system, and VNS interop.

Model: `core/src/main/java/com/jvn/core/vn/VnSettings.java`
Store: `core/src/main/java/com/jvn/core/vn/VnSettingsStore.java`

---

## Overview

`VnSettings` is the central configuration object for VN playback behavior. It holds text speed, audio volumes, auto-play timing, skip behavior, physics tuning, and input profile data. Settings are persisted to `~/.jvn/settings.properties` and also embedded in save data for per-save snapshots.

---

## All Settings Fields

### Text & Dialogue

| Field | Property Key | Type | Default | Range | Description |
|-------|-------------|------|---------|-------|-------------|
| `textSpeed` | `text_speed` | int | `30` | 1–200 | Milliseconds per character during text reveal |
| `autoPlayDelay` | `auto_play_delay` | long | `2000` | ≥ 500 | Milliseconds to wait before auto-advancing |
| `skipUnreadText` | `skip_unread_text` | boolean | `false` | — | Allow skip mode to skip text the player hasn't read |
| `skipAfterChoices` | `skip_after_choices` | boolean | `false` | — | Continue skip mode after a choice is made |
| `clickRevealBeforeAdvance` | `click_reveal_before_advance` | boolean | `true` | — | First click reveals full text; second click advances |

### Audio

| Field | Property Key | Type | Default | Range | Description |
|-------|-------------|------|---------|-------|-------------|
| `bgmVolume` | `bgm_volume` | float | `0.7` | 0.0–1.0 | Background music volume |
| `sfxVolume` | `sfx_volume` | float | `0.8` | 0.0–1.0 | Sound effects volume |
| `voiceVolume` | `voice_volume` | float | `1.0` | 0.0–1.0 | Voice audio volume |

### Physics

| Field | Property Key | Type | Default | Range | Description |
|-------|-------------|------|---------|-------|-------------|
| `physicsFixedStepMs` | `physics_fixed_step_ms` | long | `0` | ≥ 0 | Fixed physics timestep in ms (0 = variable) |
| `physicsMaxSubSteps` | `physics_max_substeps` | int | `4` | ≥ 1 | Maximum physics substeps per frame |
| `physicsDefaultFriction` | `physics_default_friction` | double | `0.2` | 0.0–1.0 | Default friction coefficient for physics bodies |

### Input

| Field | Property Key | Type | Default | Description |
|-------|-------------|------|---------|-------------|
| `inputProfilePath` | `input_profile_path` | String | `~/.jvn/input-bindings.properties` | Path to input binding profile |
| `inputProfileSerialized` | `input_profile_serialized` | String | `""` | Serialized `ActionBindingProfile` |

---

## Persistence

### Settings Store Location

Settings are stored as a Java `.properties` file at:

```text
~/.jvn/settings.properties
```

The directory is created automatically on first save.

### Properties File Format

```properties
# JVN Settings
text_speed=30
bgm_volume=0.7
sfx_volume=0.8
voice_volume=1.0
auto_play_delay=2000
skip_unread_text=false
skip_after_choices=false
click_reveal_before_advance=true
physics_fixed_step_ms=0
physics_max_substeps=4
physics_default_friction=0.2
input_profile_path=/Users/me/.jvn/input-bindings.properties
input_profile_serialized=
```

### Load/Save Cycle

```java
// Load settings at startup
VnSettingsStore store = new VnSettingsStore();
VnSettings settings = store.load();

// Modify settings (e.g., from settings menu)
settings.setBgmVolume(0.5f);
settings.setTextSpeed(20);

// Save settings to disk
store.save(settings);
```

### Custom Store Path

```java
VnSettingsStore store = new VnSettingsStore("/custom/path/settings.properties");
```

---

## Settings in Save Data

When a save is created, the current `VnSettings` values are embedded in the save data (`VnSaveData`). When a save is loaded, these settings are restored. This means each save can have different settings — useful for games where settings change during gameplay.

Persisted in save:
- All audio volumes
- Text speed and auto-play delay
- Skip behavior flags
- Click reveal setting
- Physics tuning
- Input profile data

---

## Settings Menu Integration

The settings menu screen binds to settings fields by item ID. Each recognized item ID maps to a specific setting:

| Menu Item ID | Setting Field | Adjust Behavior |
|-------------|--------------|----------------|
| `text_speed` | `textSpeed` | ±1 per step |
| `bgm_volume` | `bgmVolume` | ±0.05 per step |
| `sfx_volume` | `sfxVolume` | ±0.05 per step |
| `voice_volume` | `voiceVolume` | ±0.05 per step |
| `auto_play_delay` | `autoPlayDelay` | ±100ms per step |
| `skip_unread` | `skipUnreadText` | Toggle ON/OFF |
| `skip_after_choices` | `skipAfterChoices` | Toggle ON/OFF |
| `click_reveal_before_advance` | `clickRevealBeforeAdvance` | Toggle ON/OFF |
| `physics_fixed_step` | `physicsFixedStepMs` | ±5ms per step |
| `physics_max_substeps` | `physicsMaxSubSteps` | ±1 per step |
| `physics_default_friction` | `physicsDefaultFriction` | ±0.05 per step |
| `input_profile` | Input bindings | Load/save profile |
| `back` | — | Close settings screen |

### Slider Ranges

Settings items that show as sliders have these value ranges:

| Item | Min | Max | Slider Behavior |
|------|-----|-----|----------------|
| `text_speed` | 10 | 120 | Lower = faster reveal |
| `bgm_volume` | 0.0 | 1.0 | Linear |
| `sfx_volume` | 0.0 | 1.0 | Linear |
| `voice_volume` | 0.0 | 1.0 | Linear |
| `auto_play_delay` | 500ms | 5000ms | Lower = faster auto-advance |
| `physics_fixed_step` | 0ms | 50ms | 0 = variable timestep |
| `physics_max_substeps` | 1 | 8 | Higher = more stable physics |
| `physics_default_friction` | 0.0 | 1.0 | Higher = more friction |

### {value} Display Format

| Item | Display Example |
|------|----------------|
| `text_speed` | `30` |
| `bgm_volume` | `70%` |
| `sfx_volume` | `80%` |
| `voice_volume` | `100%` |
| `auto_play_delay` | `2000` |
| `skip_unread` | `OFF` |
| `skip_after_choices` | `OFF` |
| `click_reveal_before_advance` | `ON` |
| `physics_fixed_step` | `0` |
| `physics_max_substeps` | `4` |
| `physics_default_friction` | `0.20` |
| `input_profile` | `default` |

---

## VNS Interop Access

Settings can be changed from VNS scripts via the `settings` interop provider:

```vns
[settings textspeed 20]
[settings autodelay 1500]
[settings volume bgm 0.5]
[settings volume sfx 0.6]
```

---

## Validation and Clamping

All setter methods enforce valid ranges:

```java
settings.setTextSpeed(-5);      // clamped to 1
settings.setTextSpeed(999);     // clamped to 200
settings.setBgmVolume(1.5f);    // clamped to 1.0
settings.setBgmVolume(-0.1f);   // clamped to 0.0
settings.setAutoPlayDelay(100); // clamped to 500
settings.setPhysicsDefaultFriction(Double.NaN); // reset to 0.0
```

---

## Copy Method

`VnSettings.copy()` creates a deep copy of all fields. Used when creating save data snapshots:

```java
VnSettings snapshot = currentSettings.copy();
saveData.setSettings(snapshot);
```

---

## Runtime Validation Checklist

- [ ] Settings load on startup (no crash if `~/.jvn/settings.properties` doesn't exist)
- [ ] Changed settings persist after quitting and relaunching
- [ ] Text speed change is visible in next dialogue line
- [ ] Volume changes take effect immediately
- [ ] Auto-play delay change affects auto-advance timing
- [ ] Skip mode respects `skipUnreadText` setting
- [ ] Skip mode respects `skipAfterChoices` setting
- [ ] Click-to-reveal behavior matches `clickRevealBeforeAdvance` setting
- [ ] Settings are embedded in save data and restored on load
- [ ] Physics settings affect JES scene behavior

---

## Common Mistakes

**Settings don't persist:**
Check file permissions on `~/.jvn/`. The store silently catches write errors.

**Volume change not audible:**
The audio backend must be non-null. Check that `--audio` flag is set correctly.

**Text speed = 0 causes instant reveal:**
The minimum is clamped to 1. Values close to 1 will reveal very fast.

**Physics settings have no effect in VN-only projects:**
Physics settings only matter when JES scenes with physics bodies are loaded.

---

## Related Docs

- [Settings Screen Configuration](../scripting/layout/settings-screen.md)
- [VNS Settings & Modes](../scripting/vns/vns-settings-modes.md)
- [Audio System](audio-system.md)
- [Save System](save-system.md)
- [Interop Guide](interop.md)
