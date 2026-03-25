# native-math

C/C++ native library providing high-performance math, rendering, text, image processing, and utility functions accessed from Java via JNI.

## Structure

| Directory | Purpose |
|-----------|---------|
| `src/` | C/C++ source files organized by domain |
| `include/` | Public C headers |
| `jni/` | JNI bridge headers and stubs |
| `tests/` | Native unit tests (CTest) |
| `CMakeLists.txt` | CMake build configuration |

## Source Domains

`analytics`, `anim`, `cache`, `cloud`, `collections`, `compression`, `concurrent`, `core`, `crypto`, `datetime`, `editing`, `encoding`, `font`, `fs`, `graphics`, `image`, `input`, `io`, `json`, `math`, `memory`, `monitoring`, `poetry`, `profiler`, `render`, `search`, `simd`, `spell`, `text`, `ui`, `util`, `watchdog`

## Build

Requires CMake and a C/C++ toolchain. The root Gradle build invokes CMake automatically:

```bash
# Via Gradle (recommended)
./gradlew buildNativeMathIfNeeded

# Direct CMake
cmake -S native-math -B native-math/build -DCMAKE_BUILD_TYPE=Release
cmake --build native-math/build --parallel
```

Produces platform-specific libraries:
- macOS: `libsimjot_native.dylib`, `libjvn_native_bridge.dylib`
- Linux: `libsimjot_native.so`, `libjvn_native_bridge.so`
- Windows: `simjot_native.dll`, `jvn_native_bridge.dll`

## Tests

```bash
ctest --test-dir native-math/build --output-on-failure
```

## Documentation

- [Native Library Audit](../docs/architecture/native/native-library-audit.md)
