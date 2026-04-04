# audio-fx

Native audio synthesis module. Provides JNI bridges to C/C++ synthesizer backends (BeezChip, LoomAmbience) and a waveform analysis toolkit for real-time audio visualization.

## Key Classes

| Class | Purpose |
|-------|---------|
| `AudioFxController` | High-level synth playback controller |
| `AudioFxNativeBridge` | JNI entry point for native synth libraries |
| `NativeBeezChipSynth` | Chiptune-style synthesizer backend |
| `NativeLoomAmbienceSynth` | Ambient/atmospheric synthesizer backend |
| `SynthPreviewSettings` | Mutable preview model with thread-safe `copy()` |
| `VnsCommandBuilder` | Generates VNS `[synthesizer on ...]` commands from settings |
| `WaveformAnalyzer` | One-shot analysis + `StreamingAnalyzer` for real-time PCM streaming |

## Native Build

The module includes a CMake-based native build that compiles JNI bridges and synth backends. The Gradle build triggers this automatically:

```bash
./gradlew :audio-fx:build
```

To skip the native build (e.g. on CI without a C++ toolchain):

```bash
./gradlew :audio-fx:build -PskipAudioFxNativeBuild=true
```

Requires CMake and a C/C++ toolchain. The native library is platform-specific (`libjvn_audiofx_native.dylib` / `.so` / `.dll`).

## Dependencies

- `:core` — engine abstractions

## Documentation
