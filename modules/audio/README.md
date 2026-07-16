# Audio

Production audio backend for JVN. It implements the core `AudioFacade` contract with hybrid codec selection, independent BGM/SFX/voice channels, bounded overlapping playback, crossfade and fade-out, spectrum data, observable state, and deterministic cleanup.

## Dependencies

- `:core` — channel abstractions and asset resolution
- `:fx` — JavaFX media integration
- `basicplayer` — audio playback engine
- `vorbisspi` — Ogg Vorbis decoding
- `mp3spi` — MP3 decoding
- `jflac-codec` — FLAC decoding

## Runtime behavior

Audio playback is built on the embedded Simp3 library under `simp3/`. `Simp3AudioService` is the supported adapter; consumers should use `AudioFacade` instead of the embedded player API.

- BGM: one track, looping, pause/resume, seek, fade-out, and crossfade
- SFX: overlapping playback, limited to 32 live engines
- Voice: dedicated gain and playback pool, limited to 8 live engines
- Mixer: master gain, channel gains, and non-destructive mute
- Operations: capability discovery, snapshots, lifecycle/error listeners, idempotent close

Project-relative paths are configured through `AudioFacade.setProjectRoot(File)`. Packaged resources extracted for playback are deleted by `close()`.

## Build

```bash
./gradlew :core:test :audio:test
```

## Documentation

- [Audio System](../../docs/runtime/systems/audio-system.md)
- [VNS Audio Commands](../../docs/scripting/vns/presentation/vns-audio.md)
