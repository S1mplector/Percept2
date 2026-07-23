# Swing Desktop Runtime

**Module:** `modules/swing/`  
**Status:** Supported secondary desktop renderer

Swing is an alternate AWT/Swing window and `Blitter2D` path for JVN's Java 21
desktop runtime. It is useful for compatibility testing and `Scene2D` content.
JavaFX remains the primary and most complete runtime.

Run a project with Swing:

```bash
./jvnw runtime -- --assets /path/to/game --ui swing
```

## Implemented Surface

- `SwingLauncher` creates the window, timer-driven update loop, and keyboard /
  mouse input bridge.
- `SwingBlitter2D` maps JVN drawing operations to `Graphics2D`.
- `SwingRenderTarget2D` supports backend-local offscreen targets.
- `SwingSceneRendererRegistry` currently registers the `Scene2D` rendering
  path.

## Constraints

- Swing uses the repository's Java 21 toolchain; it is not a Java 8 build.
- The Swing registry is smaller than JavaFX's runtime renderer registry. In
  particular, JavaFX remains the supported path for the complete VN/menu/phone
  presentation stack.
- Desktop packaging is shared with the normal runtime. There is no separate
  `swing-runtime` module or Swing-specific distribution task.
- Test actual game flows before choosing Swing for a release.

Compile and test the backend with:

```bash
./gradlew :swing:test
```
