# Performance and Build Footprint

This guide covers practical optimization for JVN projects and editor/runtime workflows.

## Build-Level Performance

### Use targeted Gradle tasks during development

Instead of full `build` every iteration, prefer focused tasks:

```bash
./gradlew :core:compileJava :scripting:compileJava :editor:compileJava
./gradlew :core:test :scripting:test
```

### Keep build graph deterministic

Simp3-compatible audio is bundled in `audio` and built by default.
No extra `-PuseSimp3` toggles are needed.

### Avoid stale/global Gradle contention

Lock-heavy environments (especially Linux) should:
- run with VFS watch disabled (`org.gradle.vfs.watch=false`)
- isolate Gradle user home when running from tools (editor already does this)

## Runtime Performance

### VNS rendering and flow

- Keep dialogue effects focused; animated per-character spans cost more than plain text.
- Avoid excessive flash/shake spam in rapid loops.
- Keep history overlay closed during normal play in low-spec targets.

### Menu rendering

- Prefer lightweight style overrides in profile files instead of large dynamic image stacks.
- Keep menu item counts bounded for keyboard/controller-first flows.

### JES scenes

- Control entity count in active scenes.
- Minimize dynamic physics body churn where possible.
- Prefer declarative timeline actions over per-frame custom callback logic.
- Disable debug overlays in production gameplay.

### Physics

- High body counts with pairwise checks scale poorly.
- Use static colliders for map geometry instead of many dynamic bodies.
- Tune fixed-step settings in `VnSettings` to balance stability and CPU cost.

## Asset Strategy

### Images

- Match source resolution to target viewport scale.
- Use sprite sheets where practical.
- Avoid oversized transparent textures for small UI elements.

### Audio

- Use compressed formats for long tracks.
- Keep simultaneous channels intentional.
- Normalize volumes to avoid heavy runtime gain swings.

### External asset overlays

Using `--assets` is good for iteration, but production packaging should keep required assets deterministic and versioned.

## Save/Load Throughput

- Save writes use temp file + atomic move, which is robust but still disk-bound.
- Avoid saving every frame; use explicit checkpoints/autosave cadence.
- Keep large custom payloads in `rpgState` compact and serializable.

## Editor Responsiveness Tips

- Keep huge generated files outside regular script/config editing tabs when possible.
- For large Markdown documentation sets, use the public documentation website for read-only lookup and open only what you edit.
- Use project filters in tree and timeline views to reduce noise.

## Quick Diagnostic Checklist

If a build or runtime session feels slow:

1. Confirm Gradle daemon/lock health (`./gradlew --status`, `./gradlew --stop`).
2. Check whether full build is necessary vs targeted module tasks.
3. Verify environment/toolchain consistency (Java version, Gradle wrapper health).
4. Profile scene content density (entities, physics, text effects, timeline activity).
5. Re-test with simplified asset set to isolate content cost vs engine cost.
