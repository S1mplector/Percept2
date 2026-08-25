# Scene Render

**Module:** `modules/scene-render/`
**Status:** Platform-agnostic VN/menu scene rendering

Houses `MenuRenderer` and (once migrated) `VnRenderer` — the code that draws
every real JVN game's menu and VN scenes. Both draw exclusively through
`com.jvn.core.scene2d.Blitter2D` (defined in `modules/core`), so any current
or future `Blitter2D` backend — `FxBlitter2D` (`modules/fx`), `WebRenderer`
(`modules/web-runtime`), `AndroidRenderer`, `IosRenderer` — can drive the
same rendering code with no duplication.

See `docs/superpowers/specs/2026-08-25-scene-renderer-blitter2d-retrofit-design.md`
for the retrofit design this module exists to implement.
