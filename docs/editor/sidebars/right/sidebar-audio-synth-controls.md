# Sidebar — Audio Synth Controls

Ambience authoring and preview panel. Configure ambience presets visually, preview them live, inspect spectrum and waveform output, and generate VNS commands.

Source: `editor/src/main/java/com/jvn/editor/ui/AudioSynthControlsView.java`

---

## Overview

The Audio Synth Controls panel is a dedicated sidebar tool for authoring and previewing synthesized ambience within the editor. It is now ambience-focused: fast preset auditioning, live retuning, spectrum/waveform feedback, and one-click command export all route through the same Loom ambience path runtime uses.

- **Default side:** Right
- **Tab name:** Audio Synth
- **Panel chooser entry:** Audio Synth Controls
- **Persists settings:** Yes, via `java.util.prefs` (last-used profile restored on restart)

---

## Features

| Feature | Description |
|---------|-------------|
| **Preset selector** | Choose ambience preset (`wind`, `rain`, `ocean`, `thunder`, `fireplace`, `night_insects`) |
| **Quick preset chips** | One-click audition buttons for the six ambience presets |
| **Parameter sliders** | Intensity, Volume, Detail, Motion, Spread, Accent |
| **Loop toggle** | Enable/disable looping playback |
| **Live preview** | Preview/Stop buttons route through the real `AudioFxController` runtime path |
| **Live retune** | While playing, slider and preset changes retune the running ambience bed instead of hard-restarting it |
| **Spectrum + waveform** | Separate spectrum and waveform canvases with RMS/peak readouts |
| **VNS command preview** | Live-updating `[synthesizer on ...]` command string |
| **Copy to clipboard** | One-click copy of the generated VNS command |
| **Insert into script** | Inserts the command at the caret position in the active `.vns` file |
| **Diagnostics** | JNI bridge status, ambience renderer ID, diagnostic summary |

---

## UI Sections

### 1. Header

- Title + short ambience summary
- State chips for preview status, current preset, and active renderer

### 2. Preset

- ComboBox with presets: `wind`, `rain`, `ocean`, `thunder`, `fireplace`, `night_insects`
- Quick preset chips for rapid auditioning

### 3. Ambience Shaping

| Parameter | Description | VNS key |
|-----------|-------------|---------|
| Detail | High-frequency texture richness | `detail:` |
| Motion | Temporal variation speed | `motion:` |
| Spread | Stereo width | `spread:` |
| Accent | Preset-specific character emphasis | `accent:` |

### 4. Mix

| Parameter | Description | VNS key |
|-----------|-------------|---------|
| Intensity | Overall energy level | `intensity:` |
| Volume | Output volume | `volume:` |
| Loop | Looping playback | `loop:` |

### 5. Preview

- **Preview** — starts live audio through `AudioFxController`
- **Stop** — stops playback
- Slider or preset changes while playing retune the ambience bed in place
- Does not require saving a script first

### 6. Spectrum + Waveform

A responsive pair of canvases showing live output:
- **Real-time streaming** while playing — updates at ~60fps via `AnimationTimer` + `StreamingAnalyzer`
- **Static snapshot** while stopped — debounced, renders first ~93ms of configured preset
- Warm spectrum bars with peak hold
- Mirrored waveform area
- RMS and peak overlays
- "Java fallback" indicator when native bridge is unavailable
- Parameter changes while playing update both the waveform and the live audio output immediately

### 7. VNS Command

Live-updating snippet preview. Example:

```
[synthesizer on mode:"rain" intensity:0.80 detail:0.90 loop:false]
```

Only non-default parameters are included in the concise form. Buttons:
- **Copy** — copies to system clipboard
- **Insert into Script** — inserts at caret in active VNS editor tab

### 8. Audio Backend Diagnostics

| Field | Description |
|-------|-------------|
| JNI Bridge | `Loaded` (green) or `Unavailable` (red) |
| Renderer | Provider ID (e.g., `native-loom`) |
| Info | Full diagnostic summary string |

---

## Architecture

### Supporting Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `SynthPreviewSettings` | `audio-fx/.../audiofx/` | Mutable model for all synth parameters |
| `VnsCommandBuilder` | `audio-fx/.../audiofx/` | Generates VNS command strings from settings (tested) |
| `WaveformAnalyzer` | `audio-fx/.../audiofx/` | One-shot snapshot analysis + `StreamingAnalyzer` for real-time PCM streaming (tested) |
| `AudioFxController` | `audio-fx/.../audiofx/` | Routes play/stop to native or fallback providers |

### Wiring

- `EditorApp` creates `AudioFxController` and passes it via `setController()`
- Insert-into-script callback delegates to `FileEditorTab.insertVnsSnippet()` → `VnsCodeEditor.insertSnippet()`
- **While playing:** `StreamingAnalyzer` renders PCM on a dedicated daemon thread into a rolling 4096-sample buffer; a JavaFX `AnimationTimer` polls `latest()` at ~60fps and redraws the canvas
- **While stopped:** A debounced `ScheduledExecutorService` renders a single static snapshot 50ms after the last parameter change, avoiding unbounded thread spawning
- **While playing:** parameter changes retune the active ambience renderer in place
- `dispose()` shuts down both the streaming analyzer and the snapshot executor

### Persistence

Settings are stored via `java.util.prefs.Preferences` under the `AudioSynthControlsView` class node. Keys: `synthType`, `preset`, `cueId`, `intensity`, `volume`, `loop`, `detail`, `motion`, `spread`, `accent`.

---

## Tests

| Test class | Module | Coverage |
|------------|--------|----------|
| `VnsCommandBuilderTest` | `audio-fx` | Command generation: defaults omitted, non-defaults present, chiptune vs ambience, verbose mode, off commands, null safety, determinism |
| `WaveformAnalyzerTest` | `audio-fx` | Bin counts, non-zero RMS, different presets produce different envelopes, null/zero safety, PCM byte extraction, normalization, chiptune path, `EMPTY` sentinel, `StreamingAnalyzer` lifecycle/reconfigure/stop-idempotent/null-safety, `SynthPreviewSettings.copy()` independence |

---

## Limitations

- **Visualization** mirrors the configured preset's output, not a tap of the actual SourceDataLine audio bus. Both are configured identically so they match.
- **Insert into Script** only works when a `.vns` file tab is active.
- **No undo** for inserted snippets (uses standard editor undo if available).

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all sidebar panels
- [Audio System](../../../runtime/systems/audio-system.md) — runtime audio architecture
- [VNS Audio Commands](../../../scripting/vns/presentation/vns-audio.md) — `[synthesizer]` command reference
- [Tutorial: Audio and Music](editor/src/main/resources/com/jvn/editor/templates/new-project/scripts/tutorial/06_audio_and_music.vns) — in-editor tutorial with preset examples
