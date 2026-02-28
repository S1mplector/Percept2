# Native Library Audit (`native-math`)

This document summarizes what was imported, what is currently used, and what should be integrated next.

## Scope Audited

Vendored library root:
- `native-math/`

Primary public header:
- `native-math/include/simjot_native.h`

Major source groups found:
- `io`, `fs`, `compression`, `json`, `text`, `math`, `simd`
- `image`, `render`, `monitoring`, `watchdog`
- `analytics`, `poetry`, `spell`, `cloud`, `ui`, `input`

## Current Integration Status in JVN

Now integrated:
- JNI bridge library: `jvn_native_bridge`
- Java bridge class: `core/src/main/java/com/jvn/core/nativebridge/NativeIoBridge.java`
- Active usage: save write path (`VnSaveManager.writeAtomically`) attempts native `simjot_atomic_write` first.

Fallback behavior:
- If native bridge is missing or fails, JVN uses Java temp-file + atomic-move write path.
- This preserves cross-platform correctness and avoids hard native dependency.

## Build Tooling Added

Cross-platform build scripts were added under `native-math/`:
- `build.sh` (macOS/Linux)
- `build_mac.sh`
- `build_linux.sh`
- `build.ps1` (Windows)
- `build_windows.bat` (Windows wrapper)

Build system updates:
- `native-math/CMakeLists.txt` now has optional `JVN_BUILD_JNI_BRIDGE` target.

## Recommended Next Integrations (Ordered)

1. `simjot_ensure_space`
- Use before large save writes and thumbnail exports.
- Benefit: proactive disk-space validation.

2. `simjot_copy_file`
- Use in save rename/backup/slot migration paths.
- Benefit: faster copy path on POSIX with sendfile/copyfile optimization.

3. `simjot_compress` / `simjot_decompress`
- Optional compression for autosave payloads.
- Benefit: smaller disk footprint and potentially lower I/O latency.

Deferred for now:
- `cloud/*`, `ui/*`, and most `poetry/*` modules (out of scope for VN runtime critical path).

## Risks / Constraints

- Some functions have POSIX-focused implementations and Windows stubs.
- JNI bridge must remain narrow and explicit to keep failure handling predictable.
- Native path is optional by design; Java fallback must remain tested as default.
