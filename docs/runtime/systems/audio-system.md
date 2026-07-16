# Audio system

JVN provides one audio contract for runtime code, scripting, previews, and platform backends. It separates background music, sound effects, and voice playback while keeping mixing and lifecycle behavior consistent.

## Architecture

`AudioFacade` is the public boundary in `modules/core`. Platform modules implement it; runtime and scripting code do not depend on a media library directly.

| Component | Responsibility |
| --- | --- |
| `AudioFacade` | Playback, transport, mixing, capabilities, diagnostics, and cleanup |
| `AudioMix` | Thread-safe master and per-channel gains, including non-destructive mute |
| `AudioStateTracker` | Playback state, snapshots, and lifecycle events |
| `FxAudioService` | JavaFX Media implementation |
| `Simp3AudioService` | Hybrid implementation with additional codecs |
| `AudioAssetResolver` | Project-relative and classpath asset lookup |

The runtime configures the project root through the facade before playback. Backends must release players, worker threads, listeners, extracted classpath resources, and media handles from `close()`.

## Channels and mixing

| Channel | Model | Default gain | Concurrency |
| --- | --- | ---: | --- |
| BGM | One loopable track | `0.7` | One active track; two during crossfade |
| SFX | Short one-shot clips | `0.8` | Up to 32 active clips |
| Voice | Spoken one-shot clips | `1.0` | Up to 8 active clips |

The effective gain is `master × channel`. All gains are clamped to `0.0–1.0`. Muting changes the effective gain to zero without overwriting saved gain values. Limits on one-shot clips prevent runaway scripts from retaining unbounded media players; the oldest clip is stopped when a limit is reached.

```java
audio.setMasterVolume(0.8f);
audio.setBgmVolume(0.6f);
audio.setMuted(true);
audio.setMuted(false); // restores the configured gains
```

## Playback and transport

```java
audio.playBgm("assets/audio/bgm/theme.ogg", true);
audio.playSfx("assets/audio/sfx/confirm.wav");
audio.playVoice("assets/audio/voice/intro.ogg");

audio.pauseBgm();
audio.seekBgmSeconds(12.5);
audio.resumeBgm();
audio.crossfadeBgm("assets/audio/bgm/night.ogg", 1500, true);
audio.fadeOutBgm(800);
```

Backends clamp negative seek positions and treat zero-duration fades as immediate changes. Changing master or BGM gain during a crossfade preserves the current fade ratio.

## Capabilities

Optional behavior is declared explicitly. Callers that can work with partial backends should check `capabilities()` before invoking advanced controls.

```java
AudioCapabilities features = audio.capabilities();
if (features.crossfade()) {
  audio.crossfadeBgm(nextTrack, 1200, true);
} else {
  audio.playBgm(nextTrack, true);
}
```

The production JavaFX and Simp3 adapters declare dedicated voice, overlapping SFX, pause/resume, seek, crossfade, fade-out, spectrum data, and lifecycle events. A minimal third-party facade can retain the source-compatible defaults and report `AudioCapabilities.basic()`.

## Diagnostics and events

`snapshot()` returns a stable, point-in-time view suitable for logs, developer tools, and health reporting. It includes:

- backend identifier and BGM transport state;
- current track, loop flag, position, and duration;
- configured master/channel gains and mute state;
- active SFX and voice counts;
- the latest backend or asset error.

Lifecycle listeners receive loading, start, pause, resume, stop, completion, error, mix-change, and close events. A failing listener is isolated and cannot interrupt playback.

```java
audio.addListener(event -> {
  if (event.type() == AudioEvent.Type.ERROR) {
    log.warn("Audio failure on {}: {}", event.channel(), event.message());
  }
});

AudioSnapshot state = audio.snapshot();
log.debug("Audio backend={}, bgm={}, sfx={}",
    state.backendId(), state.bgmStatus(), state.activeSfxCount());
```

Backend failures remain non-fatal to the scene runtime. Missing and undecodable assets move BGM to `ERROR`, populate `lastError`, and emit an error event.

## Backend selection

```bash
# Prefer Simp3 and fall back to JavaFX
./gradlew :runtime:run --args='--audio auto'

# Select a backend explicitly
./gradlew :runtime:run --args='--audio simp3'
./gradlew :runtime:run --args='--audio fx'
```

Simp3 uses a hybrid engine: JavaFX Media handles common native formats and JavaZoom-based providers handle extended codecs. JavaFX is also the dependable fallback when the optional audio module is absent.

| Format | Typical provider | Recommended use |
| --- | --- | --- |
| Ogg Vorbis | JavaZoom | General BGM, SFX, and voice |
| MP3 | JavaFX; JavaZoom on Linux | BGM and distribution compatibility |
| WAV / AIFF | JavaFX | Short, lossless effects |
| FLAC | JavaZoom | Lossless music |
| Opus | JavaZoom | Efficient voice assets |
| M4A / AAC | JavaFX | Platform-compatible music |

Codec availability can still vary by operating system and media stack. Ogg Vorbis is the preferred portable project format; test every shipping format on each target platform.

## Asset resolution

Use project-relative identifiers and keep audio under `assets/audio`:

```text
assets/audio/
├── bgm/
├── sfx/
└── voice/
```

Resolution checks the configured project, conventional asset locations, and classpath resources. Packaged classpath audio is extracted to temporary files when a backend requires a filesystem path; the Simp3 adapter deletes those files when closed.

## VNS usage

```vns
[bgm assets/audio/bgm/theme.ogg loop=true vol=0.7]
[sfx assets/audio/sfx/confirm.wav]
[voice assets/audio/voice/intro.ogg]
[bgm_crossfade assets/audio/bgm/night.ogg 1500 true]
[bgm_fadeout 800]
[volume bgm 0.5]
[audio_pause_all]
[audio_resume_all]
[audio_stop_all]
```

See [VNS audio commands](../../scripting/vns/presentation/vns-audio.md) for the language reference.

## Backend implementation contract

A new backend should:

1. resolve asset identifiers from the configured project root;
2. clamp mixer inputs and apply master gain and mute immediately;
3. accurately declare optional capabilities;
4. update transport state and emit errors for rejected assets;
5. bound overlapping player resources;
6. return safe snapshots while asynchronous disposal is in progress;
7. make `close()` idempotent and release every owned resource.

Run the core contract tests and the backend module build after modifying audio behavior:

```bash
./gradlew :core:test :fx:test :audio:test
```

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Asset not found | Confirm the project root and use a relative path such as `assets/audio/bgm/theme.ogg` |
| No audible output | Inspect `snapshot().muted()`, master gain, and the channel gain |
| Decode failure | Check `snapshot().lastError()` and try Ogg Vorbis or WAV |
| Abrupt crossfade | Use roughly 1000–3000 ms and verify both tracks decode before the transition |
| Linux MP3 failure | Select Simp3 or ship Ogg Vorbis assets |
| Playback survives shutdown | Ensure the application owner calls `audio.close()` exactly once during teardown |

## Related documentation

- [VNS audio commands](../../scripting/vns/presentation/vns-audio.md)
- [Asset management](asset-management.md)
- [VN settings](vn-settings.md)
- [Runtime guide](../core/runtime.md)
