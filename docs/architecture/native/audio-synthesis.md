# Audio Synthesis Architecture

Technical reference for the JVN native audio synthesis subsystem — JNI bridges, synth backends, waveform analysis, and editor integration.

---

## Overview

The audio synthesis subsystem provides procedural audio generation for ambience and chiptune sound effects. It is implemented as a C/C++ native library (`jvn_audiofx_native`) accessed from Java via JNI, with a pure-Java fallback for environments without a native toolchain.

Module: `:audio-fx`

---

## Architecture Layers

```
┌─────────────────────────────────────────┐
│            Editor UI Layer              │
│  AudioSynthControlsView (sidebar)       │
│  - parameter sliders, waveform canvas   │
│  - VNS command generation               │
├─────────────────────────────────────────┤
│          Controller Layer               │
│  AudioFxController                      │
│  - routes play/stop to native/fallback  │
│  - manages SourceDataLine playback      │
├─────────────────────────────────────────┤
│          Analysis Layer                 │
│  WaveformAnalyzer                       │
│  - one-shot snapshot analysis           │
│  - StreamingAnalyzer (real-time PCM)    │
├─────────────────────────────────────────┤
│            JNI Bridge                   │
│  AudioFxNativeBridge                    │
│  - loadLibrary, create/destroy renderer │
│  - render PCM frames                    │
├─────────────────────────────────────────┤
│         Native Backends (C/C++)         │
│  NativeLoomAmbienceSynth (ambience)     │
│  NativeBeezChipSynth (chiptune)         │
└─────────────────────────────────────────┘
```

---

## Native Backends

### Loom Ambience Synth

Procedural ambient sound generator. Supports six presets with continuous shaping parameters:

| Preset | Character |
|--------|-----------|
| `wind` | Filtered noise with gusting modulation |
| `rain` | Stochastic droplet patterns |
| `ocean` | Low-frequency wave cycling with surf texture |
| `thunder` | Rumble with transient crack events |
| `fireplace` | Crackling with warm base tone |
| `night_insects` | Chirp patterns with spatial variation |

**Shaping parameters:** intensity, volume, detail (texture richness), motion (temporal variation), spread (stereo width), accent (preset-specific emphasis).

### Beez Chip Synth

Chiptune-style sound effect generator for UI and game feedback sounds:

| Cue | Character |
|-----|-----------|
| `blip` | Short pitched click |
| `confirm` | Ascending two-tone |
| `error` | Descending buzz |
| `pickup` | Quick ascending sweep |

**Parameters:** intensity, volume, loop.

---

## JNI Bridge

`AudioFxNativeBridge` manages the native lifecycle:

1. **Library loading** — resolved via `jvn.native.path.jvn_audiofx_native` system property, set automatically by Gradle
2. **Renderer creation** — `createRenderer()` allocates a native synth context
3. **PCM rendering** — `renderFrames(handle, params, buffer, frameCount)` fills a byte array with signed 16-bit PCM at 44100 Hz
4. **Renderer destruction** — `destroyRenderer(handle)` frees native memory

The bridge is thread-safe at the native level. Java-side synchronization is handled by `AudioFxController` and `StreamingAnalyzer`.

---

## Waveform Analysis

`WaveformAnalyzer` provides two analysis modes:

### One-Shot Snapshot

`analyze(settings)` renders a short burst (~93ms at 4096 frames) and returns:

- **Bin amplitudes** — per-bin peak values for visualization
- **RMS level** — root-mean-square signal energy
- **Peak level** — maximum absolute sample value

Used when the synth is **not playing** to show a static preview of the configured sound.

### StreamingAnalyzer (Real-Time)

An inner class that owns a dedicated native renderer and runs on a daemon thread:

- Renders 1024-frame chunks at real-time rate into a rolling 4096-sample circular buffer
- Exposes `latest()` via volatile field for lock-free reads from the JavaFX thread
- `start()` / `reconfigure()` / `stop()` are synchronized
- The editor's `AnimationTimer` polls at ~60fps and redraws the waveform canvas

This architecture replaced an earlier design that spawned a new thread per parameter change, which caused native SIGABRT crashes from concurrent create/destroy cycles.

---

## SPI Provider System

The `spi/` package defines a provider interface allowing alternative synth implementations:

- `AudioFxProvider` — interface for synth backends
- Provider discovery via `ServiceLoader`
- Default providers: native Loom (ambience), native Beez (chiptune), Java fallback (ambience only)

---

## VNS Integration

Synthesized audio is triggered from VNS scripts via the `[synthesizer]` command:

```
[synthesizer on mode:"rain" intensity:0.80 detail:0.90 loop:true]
[synthesizer off]
```

`VnsCommandBuilder` generates these commands from `SynthPreviewSettings`, omitting default-valued parameters for concise output.

---

## Build

The native library is built via CMake, triggered automatically by Gradle:

```bash
./gradlew :audio-fx:build
```

Skip native build on CI without a C++ toolchain:

```bash
./gradlew :audio-fx:build -PskipAudioFxNativeBuild=true
```

Native tests run via CTest:

```bash
./gradlew runAudioFxNativeTests
```

---

## Tests

| Test Class | Coverage |
|------------|----------|
| `VnsCommandBuilderTest` | Command generation, defaults omission, chiptune vs ambience, verbose mode, off commands, null safety |
| `WaveformAnalyzerTest` | Bin counts, RMS, different presets, null/zero safety, PCM extraction, normalization, `StreamingAnalyzer` lifecycle, reconfigure, stop idempotent, `EMPTY` sentinel, `SynthPreviewSettings.copy()` independence |

---

## Related Docs

- [Audio System](../../runtime/systems/audio-system.md) — runtime audio architecture
- [VNS Audio Commands](../../scripting/vns/presentation/vns-audio.md) — `[synthesizer]` command reference
- [Native Library Audit](native-library-audit.md) — native integration status
