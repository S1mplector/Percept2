# Audio System

Complete guide to JVN's audio architecture — BGM, SFX, and Voice channels, dual-backend engine, crossfade, spectrum analysis, format support, and volume management.

Core interface: `core/src/main/java/com/jvn/core/audio/AudioFacade.java`
Simp3 backend: `audio/src/main/java/com/jvn/audio/simp3/Simp3AudioService.java`
Hybrid engine: `audio/simp3/src/main/java/com/musicplayer/core/audio/HybridAudioEngine.java`

---

## Overview

JVN audio is a three-channel system — **BGM** (background music), **SFX** (sound effects), and **Voice** — with independent volume controls and two backend engines. The `AudioFacade` interface abstracts all audio operations; the runtime selects a backend at startup based on CLI flags.

---

## Audio Channels

| Channel | Purpose | Behavior |
|---------|---------|----------|
| **BGM** | Background music | Single track, supports looping and crossfade |
| **SFX** | Sound effects | Multiple simultaneous, fire-and-forget |
| **Voice** | Character voice lines | Multiple simultaneous, independent volume |

Each channel has its own volume control (0.0–1.0).

---

## Audio Backends

### Simp3 Backend (Default)

The Simp3 backend uses a `HybridAudioEngine` that intelligently delegates between two underlying engines based on file format:

| Format | Engine Used | Notes |
|--------|------------|-------|
| MP3, M4A, WAV, AIFF | JavaFX Audio | Native JVM support |
| FLAC, OGG, Opus, WMA | JavaZoom Audio | Extended codec support |
| MP3 on Linux | JavaZoom Audio | Workaround for JavaFX codec issues on Linux |

The hybrid engine switches transparently — it stops the current engine, transfers volume/callbacks, and loads the new track on the appropriate engine.

### FX Backend (Fallback)

A simpler JavaFX-only backend. Used when Simp3 isn't available on the classpath.

### Backend Selection at Runtime

```bash
# Auto mode (default): tries Simp3, falls back to FX
./gradlew :runtime:run --args='--audio auto'

# Force Simp3
./gradlew :runtime:run --args='--audio simp3'

# Force JavaFX only
./gradlew :runtime:run --args='--audio fx'
```

Backend selection happens at startup in `JvnApp`:
1. If `--audio simp3` or `--audio auto`: attempt to load `Simp3AudioService` via reflection
2. If unavailable or `--audio fx`: fall back to `FxAudioService`

---

## AudioFacade API

The `AudioFacade` interface defines all audio operations:

### Core Playback

```java
void playBgm(String trackId, boolean loop);
void stopBgm();
void playSfx(String sfxId);
void playVoice(String voiceId);
void stopSfx();
void stopVoice();
void stopAllAudio();
```

### Volume Control

```java
void setBgmVolume(float volume);   // 0.0–1.0
void setSfxVolume(float volume);   // 0.0–1.0
void setVoiceVolume(float volume); // 0.0–1.0
```

### Advanced Controls

```java
void pauseBgm();
void resumeBgm();
void pauseAllAudio();
void resumeAllAudio();
void seekBgmSeconds(double seconds);
void crossfadeBgm(String trackId, long ms, boolean loop);
```

### Spectrum Analysis

```java
float[] getBgmSpectrumMagnitudes();     // dB values (~-60..0), null if unsupported
long getBgmSpectrumUpdatedAtNanos();    // System.nanoTime() of latest sample
```

---

## Using Audio from VNS Scripts

### BGM (Background Music)

```vns
[bgm assets/audio/bgm/title_theme.ogg]
[bgm assets/audio/bgm/battle.mp3 loop]
[bgm stop]
```

### SFX (Sound Effects)

```vns
[sfx assets/audio/sfx/door_open.ogg]
[sfx assets/audio/sfx/explosion.wav]
```

### Voice

```vns
[voice assets/audio/voices/hero/line_001.ogg]
```

### Advanced Audio Commands via Interop

```vns
# Pause/resume
[audio pause]
[audio resume]

# Seek to position
[audio seek 30.5]

# Crossfade to new BGM over 2 seconds
[audio crossfade assets/audio/bgm/calm.ogg 2000]

# Stop specific channels
[audio sfx_stop]
[audio voice_stop]
[audio stop_all]

# Pause/resume everything
[audio pause_all]
[audio resume_all]
```

---

## Crossfade

Crossfade smoothly transitions between two BGM tracks over a specified duration:

```vns
[audio crossfade assets/audio/bgm/night_theme.ogg 3000]
```

**How it works:**
1. A new engine instance loads the target track
2. The new track starts playing at volume 0
3. A scheduler ticks every ~33ms, linearly interpolating:
   - Old track: volume fades from current → 0
   - New track: volume fades from 0 → target
4. When complete, the old engine is stopped and disposed
5. The new engine takes over as the active BGM engine

**Crossfade with duration 0** is an instant switch — the old track stops, the new one starts at full volume immediately.

---

## Audio File Resolution

The Simp3 backend resolves audio file paths through a multi-step search:

1. **Direct file path** — if the path is an absolute or existing relative file
2. **Project root relative** — `<projectRoot>/<normalized_path>`
3. **Strip project name prefix** — handles paths like `MyProject/assets/audio/bgm.ogg`
4. **Asset type prefixes** — tries `assets/demo/audio/`, `assets/audio/`, `game/audio/`
5. **AssetPaths.build()** — standard asset path construction
6. **Classpath extraction** — extracts from JAR to temp file for playback

### Recommended Audio File Organization

```text
assets/
├── audio/
│   ├── bgm/
│   │   ├── title_theme.ogg
│   │   ├── calm.ogg
│   │   └── battle.mp3
│   ├── sfx/
│   │   ├── click.ogg
│   │   ├── door_open.wav
│   │   └── explosion.ogg
│   └── voices/
│       ├── hero/
│       │   ├── line_001.ogg
│       │   └── line_002.ogg
│       └── narrator/
│           └── intro.ogg
```

---

## Supported Formats

| Format | Extension | Backend | Transparency | Best For |
|--------|-----------|---------|-------------|----------|
| OGG Vorbis | `.ogg` | JavaZoom | Lossy | BGM, SFX, voice (recommended) |
| MP3 | `.mp3` | JavaFX (JavaZoom on Linux) | Lossy | BGM |
| WAV | `.wav` | JavaFX | Lossless | Short SFX |
| FLAC | `.flac` | JavaZoom | Lossless | High-quality BGM |
| M4A/AAC | `.m4a` | JavaFX | Lossy | BGM |
| AIFF | `.aiff` | JavaFX | Lossless | Short SFX |
| Opus | `.opus` | JavaZoom | Lossy | Voice, BGM |
| WMA | `.wma` | JavaZoom | Lossy | Legacy support |

### Format Recommendations

- **BGM:** OGG Vorbis or MP3 — good compression, wide compatibility
- **SFX:** OGG Vorbis or WAV — WAV for very short effects, OGG for everything else
- **Voice:** OGG Vorbis or Opus — good quality at low bitrates

---

## Volume Management

### Default Volumes

| Channel | Default | Range |
|---------|---------|-------|
| BGM | 0.7 | 0.0–1.0 |
| SFX | 0.8 | 0.0–1.0 |
| Voice | 1.0 | 0.0–1.0 |

### Volume Persistence

Volumes are stored in `VnSettings` and persisted by `VnSettingsStore` to `~/.jvn/settings.properties`:

```properties
bgm_volume=0.7
sfx_volume=0.8
voice_volume=1.0
```

### Volume in Save Data

The current volume settings are included in save data (`VnSaveData`). When a save is loaded, volumes are restored to the state at save time.

### Runtime Volume Changes

Volumes can be adjusted from:
1. **Settings menu** — Left/Right keys on volume items
2. **VNS interop** — `[settings bgm_volume 0.5]`
3. **Java code** — `audio.setBgmVolume(0.5f)`

Volume changes are applied immediately to all currently playing tracks on that channel.

---

## Spectrum Analysis

The Simp3 backend provides real-time BGM spectrum data for visualizers:

```java
AudioFacade audio = ...; // Simp3AudioService
float[] magnitudes = audio.getBgmSpectrumMagnitudes();
if (magnitudes != null) {
    // magnitudes are in dB, typically -60..0
    // Array length depends on FFT size
    for (float db : magnitudes) {
        double normalized = (db + 60.0) / 60.0; // 0..1
        // Use for visualization
    }
}
```

The spectrum listener updates on the JavaFX audio thread. The `getBgmSpectrumUpdatedAtNanos()` method returns the timestamp for staleness detection.

---

## Engine Lifecycle

### SFX/Voice Engine Cleanup

Each SFX or voice play creates a new engine instance. When the track ends:
1. The engine is stopped and disposed
2. It's removed from the active engines list
3. `cleanupEngines()` also removes dead engines periodically

### BGM Looping

When `loop=true` and the BGM track ends:
1. The `onSongEnded` callback fires
2. The callback reloads and replays the same track
3. Volume is maintained across the loop boundary

---

## Runtime Validation Checklist

- [ ] BGM plays on startup (title screen music)
- [ ] BGM loops correctly (no gap or pop at loop point)
- [ ] BGM stops when navigating away from a scene with music
- [ ] SFX plays on trigger (menu selection, in-game events)
- [ ] Multiple SFX can play simultaneously
- [ ] Voice plays during dialogue (if voice files exist)
- [ ] Volume sliders in settings affect playback immediately
- [ ] Crossfade transitions smoothly between tracks
- [ ] OGG files play correctly
- [ ] MP3 files play correctly
- [ ] Audio continues after save/load
- [ ] No audio errors in console on missing files (graceful fallback)
- [ ] Audio stops completely when `stopAllAudio` is called

---

## Common Mistakes

**File not found — no audio plays:**
Check the asset path. Paths are relative to the project root. `assets/audio/bgm/theme.ogg`, not `/assets/audio/bgm/theme.ogg`.

**Audio plays but at wrong volume:**
Settings may have been loaded from a previous save. Check `VnSettings` values and `settings.properties`.

**Crossfade sounds wrong:**
Very short crossfade durations (< 500ms) can sound abrupt. Use 1000–3000ms for smooth transitions.

**MP3 doesn't play on Linux:**
The engine should auto-switch to JavaZoom for MP3 on Linux. If it fails, convert to OGG.

**SFX keeps playing after scene change:**
Call `audio.stopSfx()` in scene teardown. The engine doesn't auto-stop SFX on scene transitions.

---

## Related Docs

- [VNS Audio Commands](../scripting/vns/vns-audio.md)
- [VNS Settings & Modes](../scripting/vns/vns-settings-modes.md)
- [Interop Guide](interop.md)
- [Runtime Guide](runtime.md)
- [Settings Screen](../scripting/layout/settings-screen.md)
