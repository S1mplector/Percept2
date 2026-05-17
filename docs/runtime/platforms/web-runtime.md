# Web Runtime

**Module:** `modules/web-runtime/`  
**Package:** `com.jvn.web`  
**Purpose:** Deploy JVN applications as web apps (browser-based, WebGL/Canvas)

---

## Overview

The web runtime transpiles JVN's Java bytecode to JavaScript (via GWT or similar) and renders to HTML5 Canvas or WebGL. This enables:
- No installation — play in browser
- Cross-platform (Windows, Mac, Linux, mobile browsers)
- WebGL 2D rendering with hardware acceleration
- Canvas 2D fallback for older browsers
- LocalStorage for save games
- Web Audio API for sound
- Touch and mouse input handling

---

## Architecture

### WebRendererFactory

**Location:** `modules/web-runtime/src/main/java/com/jvn/web/WebRendererFactory.java`

Implements `RendererFactory` to create WebGL or Canvas 2D renderer.

```java
public class WebRendererFactory implements RendererFactory {
  @Override public String getRendererName() { return "WebGL"; }
  @Override public Renderer createRenderer(RenderConfig config) { ... }
}
```

**Renderers:**
- `WebGlRenderer` — hardware-accelerated via WebGL2
- `Canvas2DRenderer` — fallback for older browsers

---

### Web Build Pipeline

```
JVN Java Source
        ↓
   Gradle compile
        ↓
   Java bytecode (.class)
        ↓
   GWT / TeaVM transpiler
        ↓
   JavaScript bundle
        ↓
   Minify + obfuscate (ProGuard)
        ↓
   dist/jvn-game.js (~500KB gzipped)
        ↓
   HTML + index.html
        ↓
   Deploy to CDN / static hosting
```

---

## Building for Web

### Gradle Configuration

In `build.gradle.kts`:

```kotlin
plugins {
  id("com.google.gwt") version "1.0.0"
}

gwt {
  gwtVersion = "2.10.0"
  devModuleFile = "WebIndex.gwt.xml"
}

tasks.register<GwtCompile>("gwtCompile") {
  source = files(sourceSets.main.java)
  classpath = configurations.runtimeClasspath
  entryPoint = "com.jvn.web.WebIndex"
  
  optimizationLevel = 9  // max optimization for production
}
```

### Module Definition: WebIndex.gwt.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<module>
  <!-- Inherit GWT core -->
  <inherits name="com.google.gwt.core.Core" />
  
  <!-- Inherit JVN core -->
  <inherits name="com.jvn.core.JvnCore" />
  <inherits name="com.jvn.scripting.JesScripting" />
  <inherits name="com.jvn.web.WebRuntime" />
  
  <!-- Entry point (Java class that contains onModuleLoad()) -->
  <entry-point class="com.jvn.web.JvnWebApp" />
  
  <!-- Output -->
  <source path="src" />
  <public path="public" />
</module>
```

### Build Command

```bash
# GWT dev mode (unoptimized, fast iteration)
./gradlew gwtDevMode

# GWT compile (optimized, production-ready)
./gradlew gwtCompile

# Output: war/jvn-game/ directory with .html and .js
```

---

## HTML Template

Create `src/main/webapp/index.html`:

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>My JVN Game</title>
  <style>
    body { margin: 0; padding: 0; background: #000; }
    canvas { display: block; width: 100%; height: 100vh; }
    #game { width: 100%; height: 100%; }
  </style>
</head>
<body>
  <!-- GWT injects compiled JS here -->
  <div id="game"></div>
  <script src="jvn-game/jvn-game.nocache.js"></script>
</body>
</html>
```

---

## Asset Loading

### Web Asset Server

Assets served via HTTP:

```
www.example.com/
├── index.html
├── jvn-game/
│   ├── jvn-game.nocache.js
│   ├── jvn-game-abc123.js (actual bundle)
│   └── clear-cache.gif
└── assets/
    ├── game/
    │   ├── scenes/
    │   │   └── main.jes
    │   └── story/
    │       └── intro.vns
    └── audio/
        └── bgm/
            └── theme.ogg
```

### CORS Configuration

Assets must allow cross-origin requests (if hosted on different domain):

```
# For Netlify, add netlify.toml:
[[headers]]
  for = "/assets/*"
  [headers.values]
    Access-Control-Allow-Origin = "*"
    Access-Control-Allow-Methods = "GET, OPTIONS"
    Cache-Control = "public, max-age=31536000"  # 1 year cache for assets
```

### Dynamic Asset Loading

```java
// JVN web asset manager
WebAssetManager assetManager = new WebAssetManager("https://cdn.example.com/assets/");
assetManager.load("game/scenes/main.jes", onComplete, onError);
```

Fetches via `fetch()` API with progress tracking.

---

## WebGL vs Canvas2D

### WebGL (Recommended)

**Pros:**
- Hardware-accelerated
- Fast for large scenes
- Batch rendering
- Shader support (effects, lighting)

**Cons:**
- Not supported in IE 11
- Requires WebGL driver on client
- Steeper learning curve

**Browser Support:** Chrome 8+, Firefox 4+, Safari 5.1+, Edge all versions

```java
WebGlRenderer renderer = new WebGlRenderer(canvasElement, width, height);
```

### Canvas2D (Fallback)

**Pros:**
- Works in all modern browsers
- No special hardware needed
- Simpler API

**Cons:**
- CPU rendering (slower)
- Better for 2D sprites only
- No shader effects

```java
Canvas2DRenderer renderer = new Canvas2DRenderer(canvasElement, width, height);
```

### Auto-Detection

```java
WebGlRenderer glRenderer = WebGlRenderer.create(canvasElement);
if (glRenderer != null) {
  return glRenderer;  // WebGL available
} else {
  return new Canvas2DRenderer(canvasElement);  // Fallback
}
```

---

## Input Handling

### Mouse & Touch

```javascript
// Transpiled to JavaScript; events auto-mapped to JVN Input
canvas.addEventListener("mousemove", (e) => {
  jvnEngine.onMouseMove(e.clientX, e.clientY);
});

canvas.addEventListener("mousedown", (e) => {
  jvnEngine.onMouseDown(e.clientX, e.clientY, e.button);
});

canvas.addEventListener("touchstart", (e) => {
  const touch = e.touches[0];
  jvnEngine.onTouchDown(touch.clientX, touch.clientY);
});
```

### Keyboard

```javascript
document.addEventListener("keydown", (e) => {
  jvnEngine.onKeyDown(e.code);  // "KeyW", "Enter", etc.
});

document.addEventListener("keyup", (e) => {
  jvnEngine.onKeyUp(e.code);
});
```

---

## Save & Load

Game data stored in browser localStorage:

```java
public class WebSaveManager implements SaveManager {
  @Override
  public void save(String slotId, VnSaveData data) {
    String json = gson.toJson(data);
    localStorage.setItem("jvn-save-" + slotId, json);
  }
  
  @Override
  public VnSaveData load(String slotId) {
    String json = localStorage.getItem("jvn-save-" + slotId);
    return gson.fromJson(json, VnSaveData.class);
  }
}
```

**Storage Limits:**
- Most browsers: 5-10MB per domain
- Can exceed via IndexedDB (more complex)
- User can clear data (warn in UI)

### Cloud Saves

For cross-device saving, sync to cloud:

```
JVN Game → localStorage (fast)
        → POST /api/save (cloud sync)
        → User's account
```

---

## Audio Playback

Web Audio API:

```java
public class WebAudioManager {
  private AudioContext context = new AudioContext();
  
  public void play(String audioPath, double volume, boolean loop) {
    fetch(audioPath)
      .then(response -> response.arrayBuffer())
      .then(arrayBuffer -> {
        context.decodeAudioData(arrayBuffer, audioBuffer -> {
          AudioBufferSourceNode source = context.createBufferSource();
          source.buffer = audioBuffer;
          source.loop = loop;
          source.connect(context.destination);
          source.start(0);
        });
      });
  }
}
```

**Supported Formats:**
- `.ogg` (Vorbis codec) — modern browsers
- `.mp3` — universal support
- `.wav` — uncompressed, larger files
- `.m4a` (AAC) — Safari preference

---

## Responsive Design

Scaling game to different screen sizes:

```html
<canvas id="gameCanvas" width="1920" height="1080"></canvas>

<script>
  const canvas = document.getElementById("gameCanvas");
  const rect = canvas.getBoundingClientRect();
  const scale = Math.min(
    window.innerWidth / 1920,
    window.innerHeight / 1080
  );
  canvas.style.width = (1920 * scale) + "px";
  canvas.style.height = (1080 * scale) + "px";
</script>
```

Or use CSS aspect-ratio:
```css
canvas {
  display: block;
  width: 100%;
  max-width: 1920px;
  aspect-ratio: 16 / 9;
  margin: auto;
}
```

---

## Deployment

### Static Hosting (GitHub Pages, Netlify, Vercel)

1. **Build:**
   ```bash
   ./gradlew gwtCompile
   ```

2. **Deploy:**
   ```bash
   # GitHub Pages
   cp -r war/jvn-game/* docs/
   git add docs/
   git commit -m "Deploy to GitHub Pages"
   git push origin main
   # Live at: https://username.github.io/repo-name/
   ```

   Or use Netlify:
   ```bash
   netlify deploy --prod --dir=war/jvn-game
   ```

### Server-Side Hosting (Node.js, Python, etc.)

```javascript
// server.js (Node.js)
const express = require('express');
const app = express();

app.use(express.static('war/jvn-game'));

app.get('/api/save', (req, res) => {
  // Save game to database (optional)
});

app.listen(8080, () => {
  console.log('JVN Game Server running on port 8080');
});
```

---

## Performance Optimization

### Code Optimization

1. **ProGuard minification:**
   ```gradle
   proguardFiles 'proguard-rules.pro'
   ```

2. **GWT optimization level:**
   ```xml
   <set-property name="compiler.optimization.level" value="9" />
   ```

3. **Lazy module loading:**
   Split large scenes into separate .js modules; load on-demand.

### Network Optimization

1. **Gzip compression:**
   ```
   jvn-game.js → 2MB → gzip → 500KB
   ```

2. **CDN (Content Delivery Network):**
   - Serve assets from geographically close servers
   - Use CloudFront, Cloudflare, or Fastly

3. **Asset streaming:**
   - Load intro scene immediately
   - Stream later scenes in background

### Browser Optimization

1. **Service Worker caching:**
   ```javascript
   // Cache game files for offline play
   self.addEventListener('install', (event) => {
     event.waitUntil(
       caches.open('v1').then((cache) => {
         return cache.addAll([
           '/index.html',
           '/jvn-game/jvn-game.nocache.js',
           '/assets/game/...'
         ]);
       })
     );
   });
   ```

2. **Request Animation Frame:**
   Use `requestAnimationFrame` for 60fps smooth animation.

---

## Browser Compatibility

| Browser | WebGL | Canvas2D | Touch | Storage |
|---------|-------|----------|-------|---------|
| Chrome 60+ | ✓ | ✓ | ✓ | ✓ |
| Firefox 55+ | ✓ | ✓ | ✓ | ✓ |
| Safari 11+ | ✓ | ✓ | ✓ | ✓ |
| Edge 79+ | ✓ | ✓ | ✓ | ✓ |
| IE 11 | ✗ | ✓ | ✗ | ✓ |
| Mobile browsers | ✓ | ✓ | ✓ | ✓ |

**Recommendation:** Target Chrome, Firefox, Safari, Edge. IE 11 support requires Canvas2D + polyfills.

---

## Debugging

### Browser DevTools

1. **JavaScript console:**
   ```javascript
   // View JVN logs
   console.log("JVN debug:", jvnEngine.state);
   ```

2. **Network tab:**
   - Monitor asset downloads
   - Check CORS headers

3. **Performance tab:**
   - Profile frame rate
   - Identify bottlenecks

### GWT Dev Mode

For development iteration:
```bash
./gradlew gwtDevMode
# Live reload: edit Java → compile → refresh browser
# Faster than full production compile
```

---

## Related Documentation

- **Render-API:** [docs/architecture/core/render-api.md](../architecture/core/render-api.md) — backend abstraction
- **Runtime Core:** [docs/runtime/core/runtime.md](../core/runtime.md) — JvnApp integration
- **Asset Management:** [docs/runtime/systems/asset-management.md](../systems/asset-management.md) — asset resolution
- **Save System:** [docs/runtime/systems/save-system.md](../systems/save-system.md) — save/load persistence

---

**Last Updated:** May 2026  
**Target Browsers:** Chrome 60+, Firefox 55+, Safari 11+, Edge 79+
