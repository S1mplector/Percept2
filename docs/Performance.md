# Lightweight Builds & Performance

These tips help keep JVN shipped games small and fast.
Quickly whipped up doc, I'll add more to it later. 

## Assets
- Favor smaller textures; target your runtime resolution to avoid scaling oversized art.
- Use spritesheets to reduce file count and texture binds.
- Prefer compressed audio (OGG/MP3) for BGM/SFX; avoid uncompressed WAV for long tracks.
- Keep optional high-res assets in external packs and load via `--assets`.

## VNS
- Reuse backgrounds and character sprites across scenes.
- Limit heavy transitions in rapid succession.
- Avoid excessive HUD messages or screen effects during fast input sequences.

## JES / Scene2D
- Keep entity counts low per scene; reuse pooled entities where possible.
- Minimize active physics bodies and sensors.
- Disable debug overlays in production.
- Prefer simple timeline actions over per-frame scripted logic.

## Runtime
- Use `ViewportScaler2D.fit` to render at a stable base resolution.
- Cache assets using `AssetCatalog` and avoid reloading each frame.
- Use the JavaFX backend only when needed; Swing remains a lighter option.

## Native Bridge (Optional)
- Native math kernels are optional. If the native library is missing, the Java fallback runs.
- Bundle native libs only when a measurable win is needed on target hardware.
