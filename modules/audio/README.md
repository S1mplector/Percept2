# audio

High-level audio playback module providing BGM, SFX, and voice channel management. Wraps third-party Java audio libraries behind the core audio abstractions.

## Dependencies

- `:core` — channel abstractions and asset resolution
- `:fx` — JavaFX media integration
- `basicplayer` — audio playback engine
- `vorbisspi` — Ogg Vorbis decoding
- `mp3spi` — MP3 decoding
- `jflac-codec` — FLAC decoding

## Source Layout

Audio playback is built on top of the `simp3` embedded player library (vendored under `simp3/`), providing format-agnostic streaming with crossfade, looping, and volume control.

## Build

```bash
./gradlew :audio:build
```

## Documentation

- [Audio System](../../docs/runtime/systems/audio-system.md)
- [VNS Audio Commands](../../docs/scripting/vns/presentation/vns-audio.md)
