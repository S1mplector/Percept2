# UI By Example — JES HUDs and Interactive Widgets

Build a gameplay-owned HUD with a label, two buttons, and a continuously updating volume slider.

**Difficulty:** Advanced
**Time:** 25 minutes
**Concepts:** `Panel2D`, `Label2D`, `Button2D`, `Slider2D`, call handlers, screen-fixed HUDs

---

## Author the HUD in JES

```jes
scene "GameplayHud" {
  entity "hud_panel" {
    component Panel2D {
      x: 18
      y: 18
      w: 300
      h: 128
      fill: rgb(0.03, 0.06, 0.12, 0.90)
    }
  }

  entity "score_label" {
    component Label2D {
      text: "Score: 0"
      x: 38
      y: 42
      size: 22
      bold: true
      color: rgb(0.95, 0.97, 1, 1)
    }
  }

  entity "add_button" {
    component Button2D {
      x: 38
      y: 82
      w: 112
      h: 42
      text: "+ Point"
      call: "addPoint"
      normal: rgb(0.12, 0.30, 0.55, 1)
      hover: rgb(0.18, 0.44, 0.78, 1)
      pressed: rgb(0.08, 0.22, 0.42, 1)
      textColor: rgb(1, 1, 1, 1)
      fontSize: 17
    }
  }

  entity "reset_button" {
    component Button2D {
      x: 166
      y: 82
      w: 112
      h: 42
      text: "Reset"
      call: "resetScore"
      normal: rgb(0.42, 0.18, 0.22, 1)
      hover: rgb(0.64, 0.25, 0.30, 1)
      pressed: rgb(0.31, 0.11, 0.15, 1)
      textColor: rgb(1, 1, 1, 1)
      fontSize: 17
    }
  }

  entity "volume_label" {
    component Label2D {
      text: "Music: 70%"
      x: 600
      y: 34
      size: 16
      bold: false
      color: rgb(0.90, 0.94, 1, 1)
    }
  }

  entity "volume_slider" {
    component Slider2D {
      x: 590
      y: 68
      w: 180
      h: 22
      min: 0
      max: 1
      value: 0.7
      call: "musicVolumeChanged"
      trackColor: rgb(0.16, 0.18, 0.25, 1)
      fillColor: rgb(0.30, 0.68, 1, 1)
      knobColor: rgb(0.94, 0.97, 1, 1)
    }
  }

  timeline {
    setParallax "hud_panel" { px: 0 py: 0 }
    setParallax "score_label" { px: 0 py: 0 }
    setParallax "add_button" { px: 0 py: 0 }
    setParallax "reset_button" { px: 0 py: 0 }
    setParallax "volume_label" { px: 0 py: 0 }
    setParallax "volume_slider" { px: 0 py: 0 }
  }
}
```

`setParallax` with zero factors keeps HUD entities fixed while the gameplay camera moves.

---

## Register Widget Behavior

```java
import com.jvn.scripting.jes.runtime.JesScene2D;
import java.util.Map;

public static void wireHud(JesScene2D scene) {
    int[] score = {0};

    Runnable refreshScore = () -> scene.invokeCall(
        "setLabelText",
        Map.of("target", "score_label", "text", "Score: " + score[0])
    );

    scene.registerCall("addPoint", props -> {
        score[0]++;
        refreshScore.run();
    });

    scene.registerCall("resetScore", props -> {
        score[0] = 0;
        refreshScore.run();
    });

    scene.registerCall("musicVolumeChanged", props -> {
        double value = ((Number) props.getOrDefault("value", 0.7)).doubleValue();
        int percent = (int) Math.round(value * 100.0);
        scene.invokeCall(
            "setLabelText",
            Map.of("target", "volume_label", "text", "Music: " + percent + "%")
        );
        // Preview the value through the project's audio service.
    });
}
```

Buttons invoke their named handler on click. Sliders invoke continuously while dragging and include the current numeric `value`.

---

## Choose JES Widgets Deliberately

JES widgets fit controls embedded in a gameplay scene: a puzzle console, combat HUD, world-space terminal, or arcade overlay.

Use menu profiles for application-level screens and controller-first lists. Use a Facet for a story overlay bound directly to VN variables. JES places more behavior responsibility in Java, which is powerful but unnecessary for simple story UI.

---

## Key Takeaways

1. Compose a HUD from ordinary JES entities.
2. Use `Button2D` and `Slider2D` for gameplay-owned interaction.
3. Register custom behavior by the exact `call` name.
4. Read callback numbers through `Number` before converting.
5. Keep slider callbacks lightweight because they run continuously during drag.
6. Set parallax factors to zero for screen-fixed HUD elements.

---

## Next

Configure JVN's purpose-built extras in [Phone, Gallery, and Music Room Surfaces](13-specialized-surfaces.md).

[Back to UI By Example](../ui-by-example.md)
