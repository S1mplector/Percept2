# Render-API: Graphics Abstraction Layer

**Module:** `modules/render-api/`  
**Package:** `com.jvn.render`  
**Purpose:** Abstract graphics rendering interface enabling multi-platform backend support (JavaFX, Web, Android, iOS)

---

## Overview

The render-api module defines the contract between JVN's scene graph and platform-specific rendering backends. Instead of coupling the scene system to a single graphics library, render-api provides an abstraction that allows different backends to implement 2D drawing primitives independently.

### Key Abstractions

- **RendererRegistry** — auto-discovers and manages available renderer backends via Java ServiceLoader
- **RendererFactory** — creates platform-specific Renderer instances
- **RenderSurface** — window/framebuffer abstraction (size, refresh, input source)
- **Blitter2D** (from core) — drawing primitive interface (rect, circle, sprite, text)
- **InputSource** — platform input (keyboard, mouse, gamepad) event stream

---

## Architecture

```
Application (Editor/Runtime)
          ↓
     Engine.java (core)
          ↓
   RendererRegistry (render-api)
          ↓ (discovers)
   RendererFactory implementations
     (FX, Web, Android, iOS)
          ↓
  RenderSurface (window/framebuffer)
  + Blitter2D (drawing commands)
  + InputSource (events)
```

---

## Core Classes

### RendererRegistry

**Location:** `modules/render-api/src/main/java/com/jvn/render/RendererRegistry.java`

Discovers and manages platform-specific renderer implementations using Java `ServiceLoader`.

```java
public class RendererRegistry {
  public RendererRegistry();  // auto-loads factories from classpath
  
  public RendererFactory get(String name);           // get by name
  public RendererFactory getFirst();                 // get first available
  public List<String> getAvailableRenderers();       // list all names
  public boolean isAvailable(String name);           // check availability
}
```

**Usage:**
```java
RendererRegistry registry = new RendererRegistry();
RendererFactory factory = registry.get("JavaFX");
if (factory != null) {
  Renderer renderer = factory.createRenderer(config);
}
```

**How it works:**
1. Scans classpath for `RendererFactory` implementations
2. Each platform module (fx, web-runtime, android-runtime, ios-runtime) provides a factory
3. Factories self-register via Java ServiceLoader; no manual registration needed

---

### RendererFactory

**Location:** `modules/render-api/src/main/java/com/jvn/render/RendererFactory.java`

Abstract factory for creating platform-specific Renderer instances.

```java
public interface RendererFactory {
  String getRendererName();  // e.g. "JavaFX", "WebGL", "Android"
  Renderer createRenderer(RenderConfig config);
}
```

**Implementation Examples:**
- `modules/fx/...` — JavaFX backend
- `modules/web-runtime/...` — WebGL/Canvas2D backend
- `modules/android-runtime/...` — Android Canvas backend
- `modules/ios-runtime/...` — iOS Metal/UIKit backend

**Pattern:** Each platform module implements `RendererFactory` and registers itself via ServiceLoader config file.

---

### RenderSurface

**Location:** `modules/render-api/src/main/java/com/jvn/render/RenderSurface.java`

Abstracts the window or framebuffer that receives drawing commands.

```java
public interface RenderSurface {
  int getWidth();
  int getHeight();
  
  void setSize(int w, int h);
  void requestFrame();          // signal ready to render next frame
  
  InputSource getInputSource(); // get input event stream
  
  void present();               // display rendered frame to screen
  void close();                 // cleanup
}
```

**Platform Examples:**
- **FX:** wraps JavaFX Canvas or Stage
- **Web:** wraps HTML5 Canvas or WebGL context
- **Android:** wraps android.view.SurfaceView
- **iOS:** wraps UIView or Metal rendering target

---

### InputSource

**Location:** `modules/render-api/src/main/java/com/jvn/render/InputSource.java`

Provides raw input events from keyboard, mouse, gamepad.

```java
public interface InputSource {
  void addKeyListener(KeyListener listener);
  void addMouseListener(MouseListener listener);
  void addGamepadListener(GamepadListener listener);
  
  void removeKeyListener(KeyListener listener);
  // ... remove methods for other listeners
}
```

Gets set on RenderSurface; the runtime's Input system polls it for events.

---

## How Rendering Flows

### 1. Backend Selection

At application startup:
```java
RendererRegistry registry = new RendererRegistry();
RendererFactory factory = registry.get(selectedBackend);
RenderConfig config = new RenderConfig(800, 600, title);
Renderer renderer = factory.createRenderer(config);
RenderSurface surface = renderer.getSurface();
```

### 2. Frame Loop

Main thread per-frame:
```java
// Update scene
scene.update(deltaMs);

// Get drawing interface for this frame
Blitter2D blitter = renderer.getBlitter2D();

// Scene renders itself
scene.render(blitter, camera);

// Wait for input
InputSource input = surface.getInputSource();
// (engine processes input events)

// Present to screen
surface.present();

// Request next frame
surface.requestFrame();
```

### 3. Drawing Primitives

The scene doesn't care which backend it's using; it just calls Blitter2D methods:

```java
// Core always uses Blitter2D interface
blitter.drawRect(x, y, w, h, color);      // rectangle
blitter.drawCircle(cx, cy, r, color);     // circle
blitter.drawImage(image, x, y, w, h);     // sprite
blitter.drawText(text, x, y, font, size); // text
blitter.pushTransform();                   // save state
blitter.translate(x, y);
blitter.rotate(angleDeg);
blitter.scale(sx, sy);
// ... draw child entities ...
blitter.popTransform();                    // restore
```

Each backend (FX, Web, Android) maps these calls to its native graphics API.

---

## Multi-Platform Deployment

### Adding a New Platform

To support a new platform (e.g., Switch, PS5):

1. **Create a new platform module:**
   ```
   modules/switch-runtime/
   ├── src/main/java/com/jvn/switch/
   │   ├── SwitchRendererFactory.java (implements RendererFactory)
   │   ├── SwitchRenderer.java (implements Renderer)
   │   ├── SwitchRenderSurface.java (implements RenderSurface)
   │   └── ...
   └── src/main/resources/META-INF/services/
       └── com.jvn.render.RendererFactory
   ```

2. **Implement RendererFactory:**
   ```java
   public class SwitchRendererFactory implements RendererFactory {
     @Override public String getRendererName() { return "Switch"; }
     @Override public Renderer createRenderer(RenderConfig cfg) { ... }
   }
   ```

3. **Register with ServiceLoader:**
   Create file `src/main/resources/META-INF/services/com.jvn.render.RendererFactory`:
   ```
   com.jvn.switch.SwitchRendererFactory
   ```

4. **Add to build:**
   Include the module in `settings.gradle.kts` and runtime classpath.

5. **Use at runtime:**
   ```java
   RendererRegistry registry = new RendererRegistry();
   Renderer renderer = registry.get("Switch").createRenderer(config);
   ```

---

## Integration Points

### From Engine (core)

The Engine creates a RendererRegistry during init:
```java
// Engine.java (core)
public Engine(ApplicationConfig config) {
  RendererRegistry registry = new RendererRegistry();
  RendererFactory factory = registry.get(config.getRendererName());
  this.renderer = factory.createRenderer(config.getRenderConfig());
  this.surface = renderer.getSurface();
}
```

### From JES Scenes

JES scenes receive a Blitter2D and render via the abstraction:
```java
// JesScene2D (scripting)
@Override
public void render(Blitter2D blitter, Camera2D camera) {
  // z-sort entities
  // for each entity:
  //   entity.render(blitter);  // entity calls blitter methods
}
```

No knowledge of which backend is active; Blitter2D hides implementation.

---

## Performance Considerations

### Blitter2D Batching

Some backends (especially Web/Canvas) benefit from batching draw calls. Consider:
- Grouping shapes by color/texture to minimize state changes
- Using viewport/scissor tests to cull off-screen entities
- Rendering to intermediate textures for effects

### Platform-Specific Optimizations

- **JavaFX:** Uses double-buffering internally; avoid excessive `Canvas.getGraphicsContext2D()` calls
- **Web:** Prefer OffscreenCanvas or requestAnimationFrame for smooth 60fps
- **Android:** Minimize garbage allocation in frame loop
- **iOS:** Use Metal vs OpenGL based on target devices

---

## Testing

### Integration Tests

See `modules/render-api/src/test/java/com/jvn/render/`:
- `RendererRegistryTest.java` — registry auto-discovery
- `RenderSurfaceIntegrationTest.java` — surface + input flow
- `Blitter2DIntegrationTest.java` — drawing command execution

Example:
```java
@Test
void testRegistryFindsAllBackends() {
  RendererRegistry registry = new RendererRegistry();
  List<String> renderers = registry.getAvailableRenderers();
  assertTrue(renderers.contains("JavaFX"));
  // (other platforms if on classpath)
}
```

---

## Related Documentation

- **2D Engine:** [docs/architecture/core/2d-engine.md](2d-engine.md) — scene graph, entities, rendering pipeline
- **Platform Runtimes:**
  - [Android Runtime](../platforms/android-runtime.md)
  - [iOS Runtime](../platforms/ios-runtime.md)
  - [Web Runtime](../platforms/web-runtime.md)
- **System Architecture:** [docs/architecture/core/system-architecture.md](system-architecture.md) — how renderer fits into Engine
- **Interop:** [docs/runtime/core/interop.md](../../runtime/core/interop.md) — Java↔native integration

---

**Last Updated:** May 2026  
**Module Status:** Stable API, used by all platforms
