# VN Settings Reference

Complete reference for all JVN runtime settings — fields, defaults, valid ranges, persistence, and how settings integrate with the settings menu, save system, and VNS interop.

Model: `modules/core/src/main/java/com/jvn/core/vn/VnSettings.java`
Store: `modules/core/src/main/java/com/jvn/core/vn/VnSettingsStore.java`

---

## Overview

`VnSettings` is the central configuration object for VN playback behavior. It holds text speed, audio volumes, auto-play timing, skip behavior, accessibility preferences, display settings, physics tuning, and input profile data. Runtime settings are persisted per game at `~/.jvn/games/<game-id>/settings.properties` and also embedded in save data for per-save snapshots.

---

## All Settings Fields

### Text & Dialogue

| Field | Property Key | Type | Default | Range | Description |
|-------|-------------|------|---------|-------|-------------|
| `textSpeed` | `text_speed` | int | `30` | 0–200 | Milliseconds per character during text reveal; 0 is instant |
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

### Display & Resolution

| Field | Property Key | Type | Default | Range | Description |
|-------|-------------|------|---------|-------|-------------|
| `displayWidth` | `display_width` | int | `1920` | 320–7680 | Game window width in pixels |
| `displayHeight` | `display_height` | int | `1080` | 180–4320 | Game window height in pixels |
| `autoFitResolution` | `auto_fit_resolution` | boolean | `false` | — | Automatically adjust resolution to fit player's screen |

### Accessibility

| Field | Property Key | Type | Default | Range | Description |
|-------|-------------|------|---------|-------|-------------|
| `accessibilityTheme` | `accessibility_theme` | String | `none` | `none`, `highcontrast`, `opendyslexic` | Live dialogue/choice color and font preset |
| `textToSpeechEnabled` | `text_to_speech_enabled` | boolean | `false` | — | Self-voice each new dialogue node through the available OS speech service |
| `uiFontScale` | `ui_font_scale` | double | `1.0` | 0.75–2.0 | Scale VN dialogue and settings-menu text |

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
~/.jvn/games/<game-id>/settings.properties
```

The runtime resolves `<game-id>` from the manifest `id`, or derives it from
`author` and `name` for an older manifest. The directory is created
automatically on first save. Code running without an active game uses the
legacy `~/.jvn/settings.properties` path.

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
display_width=1920
display_height=1080
auto_fit_resolution=false
accessibility_theme=none
text_to_speech_enabled=false
ui_font_scale=1.0
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
- Display resolution and auto-fit preference
- Accessibility theme, self-voicing, and UI font scale

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
| `text_to_speech` | `textToSpeechEnabled` | Toggle ON/OFF |
| `ui_font_scale` | `uiFontScale` | ±0.05 per step |
| `accessibility_theme` | `accessibilityTheme` | Cycle built-in themes |
| `physics_fixed_step` | `physicsFixedStepMs` | ±5ms per step |
| `physics_max_substeps` | `physicsMaxSubSteps` | ±1 per step |
| `physics_default_friction` | `physicsDefaultFriction` | ±0.05 per step |
| `display_width` | `displayWidth` | ±64px per step |
| `display_height` | `displayHeight` | ±36px per step |
| `auto_fit_resolution` | `autoFitResolution` | Toggle ON/OFF |
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
| `display_width` | 320px | 7680px | Resolution width adjustment |
| `display_height` | 180px | 4320px | Resolution height adjustment |

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
| `display_width` | `1920px` |
| `display_height` | `1080px` |
| `auto_fit_resolution` | `OFF` |
| `input_profile` | `default` |

---

## Resolution & Display Settings

### Player-Facing Options

The settings menu now includes three display-related options for players:

1. **Screen Width** (Slider: 320–7680px)
   - Adjust the logical game window width
   - Default: 1920px

2. **Screen Height** (Slider: 180–4320px)
   - Adjust the logical game window height
   - Default: 1080px

3. **Auto-Fit Resolution** (Toggle: ON/OFF)
   - Automatically adjust resolution to fit the player's monitor
   - Default: OFF

### Implementing Resolution Changes in Your Game

When a player changes these settings, you can access and apply them:

```java
VnSettings settings = vnScene.getState().getSettings();

// Read resolution settings
int width = settings.getDisplayWidth();
int height = settings.getDisplayHeight();
boolean autoFit = settings.isAutoFitResolution();

// Apply to window (JavaFX example)
if (autoFit) {
  Screen screen = Screen.getPrimary();
  primaryStage.setWidth(screen.getVisualBounds().getWidth());
  primaryStage.setHeight(screen.getVisualBounds().getHeight());
} else {
  primaryStage.setWidth(width);
  primaryStage.setHeight(height);
}

// Update renderer viewport scaling
ViewportScaler2D.Transform transform = ViewportScaler2D.fit(
  width, height,
  windowWidth, windowHeight
);
```

### From VNS Scripts

Players can also change resolution settings from VNS scripts:

```vns
[settings display_width 1280]
[settings display_height 720]
[settings auto_fit_resolution true]
```

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
settings.setTextSpeed(-5);      // clamped to 0
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

- [ ] Settings load on startup (no crash if the per-game `settings.properties` doesn't exist)
- [ ] Changed settings persist after quitting and relaunching
- [ ] Text speed change is visible in next dialogue line
- [ ] Volume changes take effect immediately
- [ ] Auto-play delay change affects auto-advance timing
- [ ] Skip mode respects `skipUnreadText` setting
- [ ] Skip mode respects `skipAfterChoices` setting
- [ ] Click-to-reveal behavior matches `clickRevealBeforeAdvance` setting
- [ ] Settings are embedded in save data and restored on load
- [ ] Physics settings affect JES scene behavior
- [ ] Display width/height sliders respond to input
- [ ] Auto-fit resolution toggle is displayed correctly
- [ ] Resolution values persist after game restart
- [ ] Accessibility theme and text size apply without restarting
- [ ] Self-voicing speaks each new dialogue node once when an OS speech command is available
- [ ] Window/viewport updates when resolution settings change (if implemented)

---

## Common Mistakes

**Settings don't persist:**
Check file permissions on `~/.jvn/games/<game-id>/`. The store silently catches write errors.

**Volume change not audible:**
The audio backend must be non-null. Check that `--audio` flag is set correctly.

**Text speed = 0 causes instant reveal:**
The minimum is clamped to 1. Values close to 1 will reveal very fast.

**Physics settings have no effect in VN-only projects:**
Physics settings only matter when JES scenes with physics bodies are loaded.

**Window doesn't resize when resolution changes:**
The `displayWidth` and `displayHeight` settings store the values, but the actual window resize must be implemented in your runtime's window manager. The settings are available for you to use via `settings.getDisplayWidth()` and `settings.getDisplayHeight()`.

**Auto-fit resolution appears but does nothing:**
Auto-fit detection requires platform-specific code to query the screen dimensions. This setting flag is provided; you implement the actual auto-detect behavior in your window initialization code.

---

## Related Docs

- [Settings Screen Configuration](../../scripting/ui/layout/screens/settings-screen.md)
- [VNS Settings & Modes](../../scripting/vns/runtime/vns-settings-modes.md)
- [Audio System](audio-system.md)
- [Save System](save-system.md)
- [Interop Guide](../core/interop.md)
