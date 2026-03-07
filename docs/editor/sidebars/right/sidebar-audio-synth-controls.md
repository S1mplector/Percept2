# Sidebar — Audio Synth Controls

Synthesizer authoring and preview panel. Configure ambience or chiptune parameters visually, preview audio live, inspect waveform output, and generate VNS commands.

Source: `editor/src/main/java/com/jvn/editor/ui/AudioSynthControlsView.java`

---

## Overview

The Audio Synth Controls panel is a dedicated sidebar tool for authoring and previewing synthesized audio within the editor. It supports both **ambience** (via the Loom native renderer or Java fallback) and **chiptune** (via the Beez engine) synth types.

- **Default side:** Right
- **Tab name:** Audio Synth
- **Panel chooser entry:** Audio Synth Controls
- **Persists settings:** Yes, via `java.util.prefs` (last-used profile restored on restart)

---

## Features

| Feature | Description |
|---------|-------------|
| **Type toggle** | Switch between Ambience and Chiptune synth modes |
| **Preset / Cue selector** | Choose ambience preset (`wind`, `rain`, `ocean`, `thunder`, `fireplace`, `night_insects`) or chiptune cue (`blip`, `confirm`, `error`, `pickup`) |
| **Parameter sliders** | Intensity, Volume, Detail, Motion, Spread, Accent (ambience-only params shown conditionally) |
| **Loop toggle** | Enable/disable looping playback |
| **Live preview** | Play/Stop buttons route through the real `AudioFxController` runtime path |
| **Waveform visualization** | Canvas showing amplitude envelope with mirrored bars, RMS/peak dashed lines |
| **RMS/Peak meters** | Numeric readout below the waveform |
| **VNS command preview** | Live-updating `[synthesizer on ...]` command string |
| **Copy to clipboard** | One-click copy of the generated VNS command |
| **Insert into script** | Inserts the command at the caret position in the active `.vns` file |
| **Diagnostics** | JNI bridge status, ambience/chiptune provider IDs, diagnostic summary |

---

## UI Sections

### 1. Synth Type

Two toggle buttons: **Ambience** and **Chiptune**. Switching types shows/hides the relevant parameter controls.

### 2. Preset / Cue

- **Ambience mode:** ComboBox with presets: `wind`, `rain`, `ocean`, `thunder`, `fireplace`, `night_insects`
- **Chiptune mode:** ComboBox with cues: `blip`, `confirm`, `error`, `pickup`

### 3. Ambience Shaping (ambience only)

| Parameter | Description | VNS key |
|-----------|-------------|---------|
| Detail | High-frequency texture richness | `detail:` |
| Motion | Temporal variation speed | `motion:` |
| Spread | Stereo width | `spread:` |
| Accent | Preset-specific character emphasis | `accent:` |

### 4. Common Controls

| Parameter | Description | VNS key |
|-----------|-------------|---------|
| Intensity | Overall energy level | `intensity:` |
| Volume | Output volume | `volume:` |
| Loop | Looping playback | `loop:` |

### 5. Preview

- **Play** — starts live audio through `AudioFxController`
- **Stop** — stops playback
- Does not require saving a script first

### 6. Waveform

A responsive canvas that renders an amplitude envelope from a short PCM analysis buffer:
- Blue vertical bars (mirrored around center line)
- Filled envelope area
- Dashed blue RMS lines
- Dashed orange peak lines
- "Java fallback" indicator when native bridge is unavailable

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
| Ambience | Provider ID (e.g., `native-loom`) |
| Chiptune | Provider ID (e.g., `native-beez`) |
| Info | Full diagnostic summary string |

---

## Architecture

### Supporting Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `SynthPreviewSettings` | `audio-fx/.../audiofx/` | Mutable model for all synth parameters |
| `VnsCommandBuilder` | `audio-fx/.../audiofx/` | Generates VNS command strings from settings (tested) |
| `WaveformAnalyzer` | `audio-fx/.../audiofx/` | Renders short PCM buffer and extracts envelope/RMS/peak (tested) |
| `AudioFxController` | `audio-fx/.../audiofx/` | Routes play/stop to native or fallback providers |

### Wiring

- `EditorApp` creates `AudioFxController` and passes it via `setController()`
- Insert-into-script callback delegates to `FileEditorTab.insertVnsSnippet()` → `VnsCodeEditor.insertSnippet()`
- Waveform rendering runs on a daemon thread; results posted to FX thread via `Platform.runLater()`

### Persistence

Settings are stored via `java.util.prefs.Preferences` under the `AudioSynthControlsView` class node. Keys: `synthType`, `preset`, `cueId`, `intensity`, `volume`, `loop`, `detail`, `motion`, `spread`, `accent`.

---

## Tests

| Test class | Module | Coverage |
|------------|--------|----------|
| `VnsCommandBuilderTest` | `audio-fx` | Command generation: defaults omitted, non-defaults present, chiptune vs ambience, verbose mode, off commands, null safety, determinism |
| `WaveformAnalyzerTest` | `audio-fx` | Bin counts, non-zero RMS, different presets produce different envelopes, null/zero safety, PCM byte extraction, normalization, chiptune path |

---

## Limitations

- **Waveform is a snapshot**, not real-time streaming. It renders the first ~93ms (4096 frames at 44.1kHz) of the configured preset.
- **Chiptune has fewer parameters** than ambience. Detail/Motion/Spread/Accent are ambience-only; this is a real limitation of the Beez engine, not a missing feature.
- **Insert into Script** only works when a `.vns` file tab is active.
- **No undo** for inserted snippets (uses standard editor undo if available).

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all sidebar panels
- [Audio System](../../../runtime/systems/audio-system.md) — runtime audio architecture
- [VNS Audio Commands](../../../scripting/vns/presentation/vns-audio.md) — `[synthesizer]` command reference
- [Tutorial: Audio and Music](editor/src/main/resources/com/jvn/editor/templates/new-project/scripts/tutorial/06_audio_and_music.vns) — in-editor tutorial with preset examples
