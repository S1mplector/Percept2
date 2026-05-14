# Web Runtime Module

Browser-based game runtime for Java Vector Nexus using TeaVM and HTML5 Canvas.

## Overview

Enables JVN games to run in web browsers via WebAssembly (WASM) compilation using TeaVM.

## Components

### WebCanvasRenderSurface
Wraps an HTML5 `<canvas>` element for use as a `RenderSurface`.

```java
WebCanvasRenderSurface surface = new WebCanvasRenderSurface("game-canvas");
surface.setPixelScale(2.0);  // Retina support
```

**Features:**
- Direct DOM element access via JavaScript native bindings
- Device pixel ratio support for high-DPI displays
- Canvas size tracking with caching

### WebRenderer
Implements `Blitter2D` using Canvas 2D API.

```java
Blitter2D renderer = new WebRenderer(surface);

// Drawing operations
renderer.setFill(1.0, 0.0, 0.0, 1.0);
renderer.fillRect(10, 10, 100, 50);
renderer.drawImage("game/images/hero.png", 50, 50, 32, 32);
renderer.drawText("Score: 100", 10, 10, 14, false);
renderer.present();
```

**Supported Features:**
- Rectangles, circles, lines, polygons
- Text rendering with font control
- Image drawing (full and region-based)
- Transformations (translate, rotate, scale, matrix)
- Clipping regions
- State stack (push/pop)
- Blend modes

### WebImageCache
Asynchronous image loading from HTTP/HTTPS URLs.

```java
WebImageCache cache = new WebImageCache();

// Returns null initially; image loads asynchronously
Object img = cache.getOrLoad("game/images/background.png", () -> {
  // Called when image loads
  renderer.drawImage("game/images/background.png", 0, 0, 800, 600);
  canvas.requestRepaint();
});

if (img != null) {
  renderer.drawImage("game/images/background.png", 0, 0, 800, 600);
}
```

**Features:**
- LRU cache with configurable size
- Async loading via JavaScript Image API
- Callback notification on load completion
- Error tracking for failed loads

### WebGameLoop
Browser animation frame loop for synchronized rendering.

```java
WebGameLoop gameLoop = new WebGameLoop(engine, renderer, surface);
gameLoop.start();

// Runs approximately 60 times per second (synchronized to display refresh)
```

**Features:**
- `requestAnimationFrame` for optimal frame pacing
- Automatic delta-time calculation
- App lifecycle pause/resume support
- Exception handling and error logging

### WebLauncher
Entry point for web game initialization.

```java
WebLauncher launcher = new WebLauncher();
ApplicationConfig config = new ApplicationConfig("MyGame", 800, 600);
launcher.initialize(config);
```

**Initialization:**
1. Creates `WebCanvasRenderSurface` for DOM element
2. Sets up asset manager (HTTP + classpath fallback)
3. Initializes game engine
4. Starts game loop

### WebRendererFactory
Service provider for renderer discovery.

```java
RendererRegistry registry = new RendererRegistry();
RendererFactory factory = registry.get("WebGL/Canvas2D");
Blitter2D renderer = factory.createBlitter2D(surface);
```

## Asset Loading Strategy

Assets are served via HTTP with the following fallback chain:

1. **Primary:** Remote HTTP(S) URL (e.g., `/assets/game/images/hero.png`)
2. **Fallback:** Classpath resource (embedded in WASM bundle)

Cache management:
- LRU eviction when cache exceeds 64MB (default)
- Case-insensitive path normalization for cross-platform compatibility

## JavaScript Interop

Native methods use TeaVM's JavaScript Object (JSO) bindings:

```java
// Native JS code is embedded in method body
private static native void fillRectNative(Object ctx, double x, double y, double w, double h) /*-{
  ctx.fillRect(x, y, w, h);
}-*/;
```

Supported operations:
- Canvas 2D context access and manipulation
- Image element creation and loading
- DOM element queries
- Window/document access

## HTML Template

Minimal HTML required to run a web game:

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>JVN Game</title>
  <script src="game.js"></script>
  <style>
    canvas { border: 1px solid black; }
  </style>
</head>
<body>
  <canvas id="game-canvas" width="800" height="600"></canvas>
</body>
</html>
```

## Build Configuration

Requires TeaVM Gradle plugin (pending):

```gradle
plugins {
  id 'org.teavm:teavm-gradle-plugin' version '0.10.0'
}

teavm {
  mainClass = 'com.jvn.web.WebLauncher'
  outputDir = file('build/web')
  wasmVersion = 'wasm32'  // or 'javascript' for fallback
}
```

## Limitations & Future Work

**Current (Scaffolding):**
- Native method stubs (not compiled by TeaVM yet)
- No actual WASM generation
- Image region drawing not optimized

**Next Phase:**
- [ ] Full TeaVM integration and WASM compilation
- [ ] Performance optimization (batching, dirty regions)
- [ ] Input event handling (keyboard, mouse, touch)
- [ ] Audio playback via Web Audio API
- [ ] Persistent storage (LocalStorage, IndexedDB)
- [ ] Network requests and WebSockets

## Testing

Run tests via Gradle:

```bash
./gradlew :web-runtime:test
```

Tests cover:
- Image cache async loading behavior
- Service provider registration
- Basic renderer functionality (mocked)

## Browser Compatibility

Target browsers:
- Chrome/Chromium 90+
- Firefox 88+
- Safari 14+
- Edge 90+

Requirements:
- WebAssembly support
- ES2015+ JavaScript
- HTML5 Canvas 2D

## Performance Considerations

- Canvas 2D is 2D CPU-rasterized (not GPU accelerated in most browsers)
- For GPU acceleration, consider WebGL or WebGPU variants
- Image caching minimizes repeated decoding
- Frame rate limited to display refresh rate (~60 FPS)

## Debugging

Enable browser DevTools:
1. Open DevTools (F12)
2. Console tab shows JS errors and logging
3. Network tab shows asset HTTP requests
4. Performance tab profiles rendering

For WASM debugging (future):
- Chrome DevTools supports WASM disassembly
- Source maps enable debugging at source level
