# Swing Runtime

**Module:** `modules/swing/`  
**Package:** `com.jvn.swing`  
**Purpose:** Render JVN games using Swing (alternative to JavaFX desktop backend)

---

## Overview

Swing is Java's original GUI toolkit (pre-JavaFX). JVN provides a Swing backend for:
- **Compatibility:** Java 8+ (JavaFX requires Java 11+)
- **Existing projects:** Using Swing in your codebase already
- **Lightweight alternatives:** Embedded rendering scenarios
- **Specific use cases:** Integration with Swing-based tools

**When to use Swing:**
- ✅ Java 8 is a hard requirement
- ✅ Building on existing Swing infrastructure
- ✅ Embedded JVN games in larger Swing apps
- ❌ Modern, performance-critical games → use JavaFX or Web instead

---

## Architecture

### SwingRendererFactory

**Location:** `modules/swing/src/main/java/com/jvn/swing/SwingRendererFactory.java`

Implements `RendererFactory` to create Swing-based renderer.

```java
public class SwingRendererFactory implements RendererFactory {
  @Override public String getRendererName() { return "Swing"; }
  @Override public Renderer createRenderer(RenderConfig config) { ... }
}
```

**Called by:**
- JVM application on startup via RendererRegistry
- Auto-discovered if Swing module is on classpath

---

## Building for Swing

### Gradle Configuration

In `build.gradle.kts`:

```kotlin
dependencies {
  implementation(project(":core"))
  implementation(project(":scripting"))
  implementation(project(":runtime"))
  implementation(project(":swing-runtime"))
}

tasks.register<Jar>("jarWithDependencies") {
  from(sourceSets.main.output)
  from({
    configurations.runtimeClasspath.map {
      if (it.isDirectory) it else zipTree(it)
    }
  })
  manifest {
    attributes["Main-Class"] = "com.example.game.SwingGameApp"
  }
}
```

### Main Application

```java
import com.jvn.runtime.JvnApp;
import com.jvn.core.engine.Engine;
import com.jvn.core.config.ApplicationConfig;

public class SwingGameApp {
  public static void main(String[] args) {
    // 1. Configuration
    ApplicationConfig config = new ApplicationConfig("My Game", 1024, 768);
    config.setRenderer("Swing");  // Select Swing backend
    config.setTargetFps(60);
    config.setFullscreen(false);
    
    // 2. Run with Swing
    JvnApp app = new SwingJvnApp(config);
    app.launch();
  }
}

// Swing implementation
public class SwingJvnApp extends JvnApp {
  private JFrame frame;
  private Engine engine;
  
  @Override
  public void launch() {
    // Create Swing window
    frame = new JFrame("My Game");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setResizable(false);
    
    // Create renderer
    RendererRegistry registry = new RendererRegistry();
    Renderer renderer = registry.get("Swing")
      .createRenderer(new RenderConfig(1024, 768, frame));
    
    // Create engine
    engine = new Engine(config, renderer);
    engine.pushScene(new VnScene("game/intro.vns"));
    
    // Show window
    frame.setSize(1024, 768);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    
    // Start game loop
    engine.start();
  }
}
```

---

## Rendering Pipeline

### Swing Canvas

Swing renderer uses `JPanel` for drawing via `Graphics2D`:

```java
public class SwingRenderPanel extends JPanel {
  private Blitter2D blitter;
  
  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;
    
    // Anti-aliasing for smoother graphics
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON);
    
    // Delegate to JVN blitter
    blitter = new Swing2DBlitter(g2d);
    scene.render(blitter, camera);
  }
}
```

### Blitter2D Implementation

Swing's `Blitter2D` translates JVN drawing calls to `Graphics2D`:

```java
public class Swing2DBlitter implements Blitter2D {
  private Graphics2D g2d;
  
  @Override
  public void drawRect(double x, double y, double w, double h, Color color) {
    g2d.setColor(toAwtColor(color));
    g2d.fillRect((int)x, (int)y, (int)w, (int)h);
  }
  
  @Override
  public void drawImage(Image image, double x, double y, double w, double h) {
    BufferedImage buffered = (BufferedImage) image.getHandle();
    g2d.drawImage(buffered, (int)x, (int)y, (int)w, (int)h, null);
  }
  
  @Override
  public void drawText(String text, double x, double y, Font font, double size) {
    g2d.setFont(new java.awt.Font(font.getName(), 0, (int)size));
    g2d.drawString(text, (int)x, (int)y);
  }
}
```

---

## Input Handling

### Swing KeyListener & MouseListener

```java
public class SwingInputAdapter {
  private Engine engine;
  
  public SwingInputAdapter(Engine engine) {
    this.engine = engine;
  }
  
  // Keyboard input
  public void keyPressed(KeyEvent e) {
    InputEvent event = new InputEvent(InputType.KEY_DOWN, 
      toJvnKeyCode(e.getKeyCode()));
    engine.onInput(event);
  }
  
  public void keyReleased(KeyEvent e) {
    InputEvent event = new InputEvent(InputType.KEY_UP, 
      toJvnKeyCode(e.getKeyCode()));
    engine.onInput(event);
  }
  
  // Mouse input
  public void mousePressed(MouseEvent e) {
    InputEvent event = new InputEvent(InputType.MOUSE_DOWN, e.getX(), e.getY());
    engine.onInput(event);
  }
  
  public void mouseMoved(MouseEvent e) {
    InputEvent event = new InputEvent(InputType.MOUSE_MOVE, e.getX(), e.getY());
    engine.onInput(event);
  }
}
```

### Wiring Input

```java
SwingRenderPanel panel = (SwingRenderPanel) frame.getContentPane();
SwingInputAdapter input = new SwingInputAdapter(engine);

panel.addKeyListener(input);
panel.addMouseListener(input);
panel.addMouseMotionListener(input);
panel.setFocusable(true);  // Receive key events
```

---

## Audio Playback

### Java Sound API

Swing uses Java's built-in `javax.sound.sampled` for audio:

```java
public class SwingAudioManager implements AudioManager {
  private Map<String, Clip> clips = new HashMap<>();
  
  @Override
  public void play(String audioPath, double volume, boolean loop) {
    try {
      AudioInputStream stream = AudioSystem.getAudioInputStream(
        new File(audioPath));
      Clip clip = AudioSystem.getClip();
      clip.open(stream);
      
      // Volume (0.0 to 1.0)
      FloatControl control = (FloatControl) clip.getControl(
        FloatControl.Type.MASTER_GAIN);
      float db = (float) (Math.log(volume) / Math.log(10.0) * 20.0);
      control.setValue(db);
      
      if (loop) {
        clip.loop(Clip.LOOP_CONTINUOUSLY);
      } else {
        clip.start();
      }
      clips.put(audioPath, clip);
    } catch (Exception e) {
      log.error("Failed to play audio", e);
    }
  }
  
  @Override
  public void stop(String audioPath) {
    Clip clip = clips.get(audioPath);
    if (clip != null) {
      clip.stop();
      clip.close();
      clips.remove(audioPath);
    }
  }
}
```

**Format Support:**
- `.wav` — WAV (RIFF) format
- `.au` — Sun audio format
- `.aiff` — Audio Interchange File Format
- `.mp3` — limited support (may require extra library like Tritonus)
- `.ogg` — not natively supported (use MP3 or WAV instead)

---

## Save & Load

Game data saved to filesystem:

```java
public class SwingFileManager implements FileManager {
  private static final File SAVES_DIR = 
    new File(System.getProperty("user.home") + "/.mygame/saves");
  
  @Override
  public void save(String slotId, VnSaveData data) throws IOException {
    SAVES_DIR.mkdirs();
    File saveFile = new File(SAVES_DIR, slotId + ".jvnsave");
    try (ObjectOutputStream oos = new ObjectOutputStream(
        new FileOutputStream(saveFile))) {
      oos.writeObject(data);
    }
  }
  
  @Override
  public VnSaveData load(String slotId) throws IOException {
    File saveFile = new File(SAVES_DIR, slotId + ".jvnsave");
    if (!saveFile.exists()) return null;
    
    try (ObjectInputStream ois = new ObjectInputStream(
        new FileInputStream(saveFile))) {
      return (VnSaveData) ois.readObject();
    }
  }
}
```

Default location: `~/.mygame/saves/` (user's home directory)

---

## Deployment

### Packaging as JAR

```bash
# Build fat JAR with all dependencies
./gradlew jarWithDependencies

# Run
java -jar build/libs/game-all.jar
```

### Platform-Specific Launchers

**Windows .exe wrapper:**
```bash
# Use a tool like Launch4j to wrap JAR in .exe
# Creates a Windows desktop shortcut
```

**macOS .app bundle:**
```bash
# Create .app directory structure
MyGame.app/
├── Contents/
│   ├── MacOS/
│   │   └── game.sh (shell script to launch java -jar)
│   ├── Resources/
│   │   └── icon.icns
│   └── Info.plist
```

**Linux .deb package:**
```bash
# Debian packaging:
deb-helper --jar game-all.jar --maintainer "Author" \
  --name "My Game" --version 1.0
```

### System Requirements

- **Java:** JRE 8+ (or 11+ for JavaFX)
- **OS:** Windows, macOS, Linux (any with Java installed)
- **Memory:** 512MB minimum, 1GB recommended
- **Graphics:** Built-in (no GPU required for Swing)

---

## Performance Considerations

### CPU Rendering

Swing's `Graphics2D` renders on CPU, not GPU. This is slower than JavaFX or WebGL.

**Performance targets:**
- Simple scenes (VNs, text): 60 FPS easy
- Sprite-heavy scenes: 30-60 FPS depending on count
- Complex physics: 30 FPS or less

**Optimization tips:**
1. **Reduce redraw:** Only paint dirty areas
2. **Batch drawing:** Group similar shapes
3. **Reuse images:** Cache `BufferedImage` objects
4. **Avoid animations:** Or use low frame rate (30 FPS)
5. **Profile:** Use JProfiler to find CPU hotspots

### Memory

Swing creates many Java objects. Monitor heap:

```java
FrameStats stats = engine.getStats();
if (stats.getMemoryUsageMB() > 500) {
  log.warn("High memory: {}MB", stats.getMemoryUsageMB());
  System.gc();  // Force garbage collection
}
```

---

## FX vs Swing Comparison

| Feature | Swing | JavaFX |
|---------|-------|--------|
| **GPU Rendering** | ❌ CPU only | ✅ GPU accelerated |
| **Modern Look** | ❌ Windows 95 feel | ✅ Modern UI |
| **Performance** | 🟡 CPU-limited | ✅ Fast |
| **Java Requirement** | ✅ Java 8+ | ❌ Java 11+ |
| **Ecosystem** | 🟡 Mature but dated | ✅ Active community |
| **Learning Curve** | ✅ Familiar to Java devs | 🟡 Steeper |
| **Game Dev** | 🟡 Possible but slow | ✅ Better |
| **Mobile/Web** | ❌ Not supported | ❌ Partial (e.g., JavaFXPorts) |

**Recommendation:**
- **Use Swing:** Java 8 requirement, existing Swing codebase
- **Use JavaFX:** New desktop projects, better performance
- **Use Web:** Maximum reach, no installation

---

## Troubleshooting

### "Swing not found" Error

```
java.lang.ClassNotFoundException: com.jvn.swing.SwingRendererFactory
```

**Fix:** Add `modules/swing-runtime` to classpath or gradle dependencies.

### Input Not Responding

**Fix:** Ensure panel has focus:
```java
panel.setFocusable(true);
panel.requestFocusInWindow();
```

### Audio Won't Play

**Supported formats:** Check that audio is in `.wav` or `.au` format
- `.mp3` requires additional library (Tritonus)
- `.ogg` not supported natively

### Low FPS

Swing renders on CPU. For better performance:
1. Reduce entity count
2. Disable animations or run at 30 FPS
3. Switch to JavaFX or Web

---

## Related Documentation

- **Render-API:** [docs/architecture/core/render-api.md](../../architecture/core/render-api.md) — backend abstraction
- **Platform Runtimes:** [docs/runtime/platforms/README.md](README.md) — all platforms
- **Desktop (JavaFX):** Not separately documented; see Architecture Overview
- **Runtime Core:** [docs/runtime/core/runtime.md](../core/runtime.md) — JvnApp wrapper

---

**Last Updated:** May 2026  
**Minimum Java Version:** 8 (compiled for Java 8 compatibility)
