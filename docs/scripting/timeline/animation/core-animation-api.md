# Core Animation API

**Module:** `modules/core`  
**Package:** `com.jvn.core.animation`  
**Purpose:** Low-level animation engine providing timeline execution, easing, and scene property animation

---

## Overview

The animation system is the foundation for all timed motion in JVN. It powers:
- **Puppeteer timeline editor** — visual animation authoring
- **VNS transitions** — character and background fade/move effects
- **JES actions** — gameplay timeline scripting (`move`, `rotate`, `scale`, etc.)
- **UI animation** — menu transitions, fades, tweens

The core is runtime-agnostic; it operates on abstract "scene accessors" that don't know whether they're driving a VN character or a JES entity.

---

## Core Classes

### TimelineRunner

**Location:** `modules/core/src/main/java/com/jvn/core/animation/TimelineRunner.java`

Executes a timeline over time, updating scene properties frame-by-frame.

```java
public class TimelineRunner {
  // Create and configure
  public TimelineRunner(TimelineData timeline, SceneAccessor scene, double timeScale);
  
  // Lifecycle
  public void start();
  public void pause();
  public void resume();
  public void stop();
  
  // Update per frame
  public void update(double deltaMs);
  
  // Query state
  public boolean isRunning();
  public double getProgress();  // 0.0 to 1.0
  public double getCurrentTime();  // milliseconds
  
  // Events
  public void addListener(TimelineListener listener);
  public void removeListener(TimelineListener listener);
}
```

**Usage Example:**
```java
TimelineData anim = new TimelineData("hero_jump", 500);  // 500ms animation
anim.addKeyframe(0, property("y"), 100);    // start at y=100
anim.addKeyframe(250, property("y"), 50);   // jump to y=50 at midpoint
anim.addKeyframe(500, property("y"), 100);  // land at y=100

TimelineRunner runner = new TimelineRunner(anim, sceneAccessor, 1.0);
runner.addListener(new TimelineListener() {
  @Override public void onComplete() { /* jump done */ }
});

// In game loop:
runner.start();
// later...
runner.update(deltaMs);  // call each frame
```

---

### TimelineData

**Location:** `modules/core/src/main/java/com/jvn/core/animation/TimelineData.java`

Immutable animation definition: keyframes, easing, duration, and cues.

```java
public class TimelineData {
  // Create
  public TimelineData(String name, double durationMs);
  
  // Define keyframes
  public void addKeyframe(double timeMs, String property, Object value);
  public void addKeyframe(double timeMs, String property, Object value, Easing easing);
  
  // Event cues (fire at specific times)
  public void addCue(double timeMs, String eventName, String... args);
  
  // Query
  public String getName();
  public double getDurationMs();
  public List<Keyframe> getKeyframes();
  public List<Cue> getCues();
}
```

**Structure:**
```
Timeline "hero_walk" (1000ms total)
├─ Keyframe @ 0ms: x=0, easing=linear
├─ Keyframe @ 500ms: x=400, easing=ease_out
├─ Keyframe @ 1000ms: x=800, easing=linear
├─ Cue @ 250ms: "footstep_sound" → play audio
├─ Cue @ 750ms: "footstep_sound" → play audio
└─ [implicit complete event @ 1000ms]
```

---

### SceneAccessor

**Location:** `modules/core/src/main/java/com/jvn/core/animation/SceneAccessor.java`

Interface allowing animations to read/write scene properties without knowing the scene type.

```java
public interface SceneAccessor {
  // Spatial properties
  double getX();
  void setX(double x);
  double getY();
  void setY(double y);
  double getRotation();  // degrees
  void setRotation(double degrees);
  double getScaleX();
  void setScaleX(double sx);
  double getScaleY();
  void setScaleY(double sy);
  
  // Visual properties
  double getAlpha();  // 0.0 to 1.0 opacity
  void setAlpha(double alpha);
  boolean isVisible();
  void setVisible(boolean visible);
  
  // Event dispatch
  void fireEvent(String eventName, String... args);
  
  // Lookup sub-entities (for compound animations)
  SceneAccessor getChild(String name);
}
```

**Implementation Examples:**

VNS Character:
```java
public class VnCharacterAccessor implements SceneAccessor {
  private CharacterVisual character;
  
  @Override public double getX() { return character.x; }
  @Override public void setX(double x) { character.x = x; }
  // ... etc
}
```

JES Entity:
```java
public class JesEntityAccessor implements SceneAccessor {
  private Entity2D entity;
  
  @Override public double getX() { return entity.x; }
  @Override public void setX(double x) { entity.x = x; }
  // ... etc
}
```

---

### Easing

**Location:** `modules/core/src/main/java/com/jvn/core/animation/Easing.java`

Easing functions control how properties interpolate between keyframes.

```java
public enum Easing {
  // Linear (no acceleration)
  LINEAR(t -> t),
  
  // Quadratic
  EASE_IN_QUAD(t -> t * t),
  EASE_OUT_QUAD(t -> 1 - (1 - t) * (1 - t)),
  EASE_IN_OUT_QUAD(t -> t < 0.5 ? 2*t*t : -1 + 4*t - 2*t*t),
  
  // Cubic, Quartic, Exponential, Sine, Elastic, Back, Bounce
  // ... (full suite of standard easing curves)
  
  // Custom easing from Bezier points
  static Easing cubic(double cp1x, double cp1y, double cp2x, double cp2y) { ... }
}
```

**Visual Effects:**
- **ease_in**: slow start, fast end (acceleration)
- **ease_out**: fast start, slow end (deceleration)
- **ease_in_out**: slow start and end, fast middle
- **ease_out_elastic**: bounce overshoot at end
- **ease_in_back**: pull back slightly, then move forward

**Interpolation:**
```java
// Linear interpolation between keyframes
double progress = currentTime / (endTime - startTime);  // 0 to 1
double eased = easing.apply(progress);  // apply easing curve
double value = startValue + (endValue - startValue) * eased;
```

---

## Animation Flow

### Creating an Animation

**Manual (Java):**
```java
TimelineData timeline = new TimelineData("fade_in", 600);
timeline.addKeyframe(0, "alpha", 0.0);          // start invisible
timeline.addKeyframe(600, "alpha", 1.0);        // fade to opaque
timeline.addKeyframe(600, "alpha", Easing.EASE_OUT_QUAD);  // smooth fade
```

**Via Puppeteer (editor):**
1. Open Puppeteer editor for a VNS scene
2. Drag entities, set keyframes on timeline
3. Editor saves to `.jes` DSL (which includes `component TimelineAnimation { ... }`)
4. Runtime parses DSL and creates TimelineData

**Via JES DSL (scripting):**
```jes
timeline {
  move "hero" { x: 400 y: 300 dur: 500 easing: ease_out }
  parallel {
    fade "bg" { alpha: 0.5 dur: 500 }
    rotate "compass" { rot: 360 dur: 1000 easing: ease_in_out }
  }
  wait 200
  label done
}
```

### Running an Animation

```java
// 1. Create accessor (how animation reads/writes scene)
SceneAccessor accessor = new JesEntityAccessor(entity);

// 2. Create runner
TimelineRunner runner = new TimelineRunner(timeline, accessor, 1.0);

// 3. Hook up event listeners
runner.addListener(new TimelineListener() {
  @Override public void onCue(String eventName, String... args) {
    if ("footstep".equals(eventName)) playSound("audio/footstep.ogg");
  }
  @Override public void onComplete() {
    entity.isAnimating = false;
  }
});

// 4. Start
runner.start();

// 5. Update each frame (in game loop)
runner.update(deltaMs);

// 6. Can pause/resume
runner.pause();   // freeze at current position
runner.resume();  // continue from pause point
runner.stop();    // cancel and reset
```

---

## Event Cues

Animations can fire events at specific times without knowing what they do.

```java
timeline.addCue(250, "footstep");
timeline.addCue(500, "dialogue_start", "character=hero", "text=Look out!");
timeline.addCue(750, "particle_effect", "type=dust", "x=100", "y=50");
```

Listener receives events:
```java
runner.addListener(new TimelineListener() {
  @Override
  public void onCue(String eventName, String... args) {
    switch (eventName) {
      case "footstep":
        audioManager.play("audio/footstep.ogg");
        break;
      case "dialogue_start":
        String character = args[0].split("=")[1];
        String text = args[1].split("=")[1];
        dialogueManager.show(character, text);
        break;
      case "particle_effect":
        // ... parse and create particle effect
        break;
    }
  }
});
```

---

## Compound Animations

Timelines can nest and reference sub-entities:

```java
// Main timeline
TimelineData mainTimeline = new TimelineData("scene_intro", 3000);
mainTimeline.addKeyframe(0, "background.alpha", 0.0);
mainTimeline.addKeyframe(500, "background.alpha", 1.0, EASE_OUT);
mainTimeline.addKeyframe(500, "hero.x", 0);
mainTimeline.addKeyframe(1500, "hero.x", 400, EASE_OUT);

// Access nested entities
accessor.getChild("background").setAlpha(0.5);
```

---

## Integration Points

### From Puppeteer

Puppeteer exports timelines as JES code:
```jes
timeline {
  move "character_name" { x: 200 y: 100 dur: 500 easing: ease_out }
}
```

Runtime parses and executes via TimelineRunner.

### From JES Actions

JES `move`, `rotate`, `fade`, etc. actions create TimelineData:
```java
// In JES executor
case "move":
  timeline = new TimelineData("move_" + entityName, duration);
  timeline.addKeyframe(0, "x", entity.x);
  timeline.addKeyframe(duration, "x", targetX);
  runner = new TimelineRunner(timeline, accessor, 1.0);
  runner.start();
  break;
```

### From VNS Transitions

VNS transitions (`fade out`, `move left`) create animations:
```java
// VNS executor
case "[fade out 500]":
  timeline = new TimelineData("vns_fade", 500);
  timeline.addKeyframe(0, "alpha", 1.0);
  timeline.addKeyframe(500, "alpha", 0.0);
  runner.start();
  break;
```

---

## Performance Tips

1. **Reuse TimelineData:** Don't create new timeline objects per animation; share definitions.
2. **Batch updates:** If many animations run, update all in one pass.
3. **Easing complexity:** Linear and quadratic are faster than elastic/bounce.
4. **Scene accessor overhead:** Keep getX/setX calls fast (direct field access, not method chains).

---

## Testing Examples

From `modules/core/src/test/java/com/jvn/core/assets/AsyncAssetLoaderTest.java`:

```java
@Test
public void testTimelineExecution() {
  TimelineData timeline = new TimelineData("test", 1000);
  timeline.addKeyframe(0, "x", 0.0);
  timeline.addKeyframe(1000, "x", 100.0);
  
  SceneAccessor accessor = mock(SceneAccessor.class);
  TimelineRunner runner = new TimelineRunner(timeline, accessor, 1.0);
  
  runner.start();
  runner.update(500);  // halfway through
  
  // Should be at x=50 (linear interpolation)
  verify(accessor, times(2)).setX(50.0);
}

@Test
public void testEasingCurves() {
  Easing easeOut = Easing.EASE_OUT_QUAD;
  double t = 0.5;
  double eased = easeOut.apply(t);  // should be > 0.5 (deceleration)
  assertTrue(eased > t);
}
```

---

## Related Documentation

- **Puppeteer Editor Guide:** [docs/editor/puppeteer/puppeteer-editor-guide.md](../../../editor/puppeteer/puppeteer-editor-guide.md) — how to create timelines visually
- **Puppeteer JES DSL:** [docs/editor/puppeteer/puppeteer-jes-dsl.md](../../../editor/puppeteer/puppeteer-jes-dsl.md) — exported timeline syntax
- **JES Actions:** [docs/scripting/jes/timeline/jes-timeline.md](../../jes/timeline/jes-timeline.md) — `move`, `rotate`, `fade` etc.
- **VNS Transitions:** [docs/scripting/vns/presentation/vns-transitions.md](../../vns/presentation/vns-transitions.md) — screen effects
- **2D Engine:** [docs/architecture/core/2d-engine.md](../../architecture/core/2d-engine.md) — Scene2DBase render pipeline

---

**Last Updated:** May 2026  
**Stability:** Core API, stable for years
