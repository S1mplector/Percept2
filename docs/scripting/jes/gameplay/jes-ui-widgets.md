# JES UI Widgets

Complete reference for interactive UI components in JES — buttons, sliders, and labels used for in-scene HUD and menu elements.

Runtime: `modules/scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Button2D

Interactive clickable buttons with visual states and call handlers.

```jes
entity "start_btn" {
  component Button2D {
    x: 300
    y: 400
    w: 200
    h: 50
    text: "Start Game"
    call: "startGame"
    normal: rgb(0.2, 0.3, 0.6, 1)
    hover: rgb(0.3, 0.4, 0.8, 1)
    pressed: rgb(0.1, 0.2, 0.5, 1)
    textColor: rgb(1, 1, 1, 1)
    fontSize: 18
  }
}
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `x`, `y` | 0 | Position |
| `w` | 100 | Width |
| `h` | 32 | Height |
| `text` | `""` | Button label text |
| `call` | — | Call handler invoked on click |
| `normal` | — | Background color for normal state |
| `hover` | — | Background color when mouse hovers |
| `pressed` | — | Background color when mouse is down |
| `textColor` | — | Text color |
| `fontSize` | 14 | Font size |

### Behavior

- **Hover detection** — checks mouse position against button bounds each frame
- **Click detection** — triggers on mouse press while hovering
- **Call invocation** — on click, invokes the named handler with `{ x, y, value: 1.0 }` plus any extra button props

### Example: Menu Buttons

```jes
scene "MainMenu" {
  entity "title" {
    component Label2D {
      text: "My Game"
      x: 400
      y: 100
      size: 48
      bold: true
      color: rgb(1, 0.85, 0, 1)
      align: center
    }
  }

  entity "play_btn" {
    component Button2D {
      x: 300
      y: 250
      w: 200
      h: 50
      text: "Play"
      call: "startGame"
      normal: rgb(0.2, 0.5, 0.2, 1)
      hover: rgb(0.3, 0.7, 0.3, 1)
      pressed: rgb(0.1, 0.4, 0.1, 1)
      textColor: rgb(1, 1, 1, 1)
      fontSize: 20
    }
  }

  entity "settings_btn" {
    component Button2D {
      x: 300
      y: 320
      w: 200
      h: 50
      text: "Settings"
      call: "openSettings"
      normal: rgb(0.3, 0.3, 0.5, 1)
      hover: rgb(0.4, 0.4, 0.7, 1)
      pressed: rgb(0.2, 0.2, 0.4, 1)
      textColor: rgb(1, 1, 1, 1)
      fontSize: 20
    }
  }

  entity "quit_btn" {
    component Button2D {
      x: 300
      y: 390
      w: 200
      h: 50
      text: "Quit"
      call: "quitGame"
      normal: rgb(0.5, 0.2, 0.2, 1)
      hover: rgb(0.7, 0.3, 0.3, 1)
      pressed: rgb(0.4, 0.1, 0.1, 1)
      textColor: rgb(1, 1, 1, 1)
      fontSize: 20
    }
  }
}
```

---

## Slider2D

Draggable sliders for numeric value input (volume, brightness, etc.).

```jes
entity "volume_slider" {
  component Slider2D {
    x: 200
    y: 300
    w: 200
    h: 20
    min: 0
    max: 1
    value: 0.7
    call: "volumeChanged"
    trackColor: rgb(0.3, 0.3, 0.3, 1)
    fillColor: rgb(0.2, 0.6, 1, 1)
    knobColor: rgb(1, 1, 1, 1)
  }
}
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `x`, `y` | 0 | Position |
| `w` | 120 | Width |
| `h` | 20 | Height |
| `min` | 0 | Minimum value |
| `max` | 1 | Maximum value |
| `value` | 0 | Current value |
| `call` | — | Handler invoked when value changes |
| `trackColor` | — | Background track color |
| `fillColor` | — | Filled portion color |
| `knobColor` | — | Knob/handle color |

### Behavior

- **Mouse interaction** — click and drag to adjust value
- **Value mapping** — mouse X position is mapped to `min`–`max` range
- **Continuous callback** — the handler is called each frame while dragging (not just on release)
- **Callback props** — `{ value: <currentValue> }` plus any extra slider props

### Example: Settings Panel

```jes
scene "Settings" {
  entity "music_label" {
    component Label2D {
      text: "Music Volume"
      x: 100
      y: 195
      size: 14
      color: rgb(0.8, 0.8, 0.8, 1)
    }
  }
  entity "music_slider" {
    component Slider2D {
      x: 250
      y: 200
      w: 200
      h: 16
      min: 0
      max: 1
      value: 0.7
      call: "setMusicVolume"
      trackColor: rgb(0.2, 0.2, 0.2, 1)
      fillColor: rgb(0.3, 0.7, 0.3, 1)
      knobColor: rgb(1, 1, 1, 1)
    }
  }

  entity "sfx_label" {
    component Label2D {
      text: "SFX Volume"
      x: 100
      y: 245
      size: 14
      color: rgb(0.8, 0.8, 0.8, 1)
    }
  }
  entity "sfx_slider" {
    component Slider2D {
      x: 250
      y: 250
      w: 200
      h: 16
      min: 0
      max: 1
      value: 0.8
      call: "setSfxVolume"
      trackColor: rgb(0.2, 0.2, 0.2, 1)
      fillColor: rgb(0.3, 0.5, 0.8, 1)
      knobColor: rgb(1, 1, 1, 1)
    }
  }

  entity "speed_label" {
    component Label2D {
      text: "Game Speed"
      x: 100
      y: 295
      size: 14
      color: rgb(0.8, 0.8, 0.8, 1)
    }
  }
  entity "speed_slider" {
    component Slider2D {
      x: 250
      y: 300
      w: 200
      h: 16
      min: 0.5
      max: 2.0
      value: 1.0
      call: "setGameSpeed"
      trackColor: rgb(0.2, 0.2, 0.2, 1)
      fillColor: rgb(0.8, 0.6, 0.2, 1)
      knobColor: rgb(1, 1, 1, 1)
    }
  }
}
```

---

## Label2D for HUD

While `Label2D` is a visual component (not interactive), it's commonly used for HUD elements and can be updated at runtime.

### Updating Labels at Runtime

**Via call handler:**

```jes
call "setLabelText" { target: "score_label" text: "Score: 100" }
```

**Via Java:**

```java
Entity2D e = scene.find("score_label");
if (e instanceof Label2D lbl) {
    lbl.setText("HP: " + currentHp + "/" + maxHp);
}
```

### HUD Layout Example

```jes
scene "GameHUD" {
  entity "hp_bar_bg" {
    component Panel2D {
      x: 10
      y: 10
      w: 200
      h: 20
      fill: rgb(0.3, 0.1, 0.1, 0.8)
    }
  }
  entity "hp_bar" {
    component Panel2D {
      x: 10
      y: 10
      w: 200
      h: 20
      fill: rgb(0.8, 0.2, 0.2, 1)
    }
  }
  entity "hp_text" {
    component Label2D {
      text: "HP: 100/100"
      x: 15
      y: 13
      size: 12
      bold: true
      color: rgb(1, 1, 1, 1)
    }
  }
  entity "score_label" {
    component Label2D {
      text: "Score: 0"
      x: 700
      y: 10
      size: 16
      bold: true
      color: rgb(1, 0.85, 0, 1)
      align: right
    }
  }
  entity "mini_map" {
    component Panel2D {
      x: 650
      y: 400
      w: 140
      h: 140
      fill: rgb(0.1, 0.1, 0.2, 0.7)
    }
  }
}
```

### Fixing HUD to Screen

Use `setParallax` with factor 0 to keep HUD elements fixed regardless of camera movement:

```jes
timeline {
  setParallax "hp_bar_bg" { px: 0 py: 0 }
  setParallax "hp_bar" { px: 0 py: 0 }
  setParallax "hp_text" { px: 0 py: 0 }
  setParallax "score_label" { px: 0 py: 0 }
  setParallax "mini_map" { px: 0 py: 0 }
}
```

---

## Related Docs

- [JES Overview](../overview/jes-scripting.md)
- [Component Reference](../scene/components.md) — `Button2D`, `Slider2D`, `Label2D`, `Panel2D`
- [Timeline & Actions](../timeline/jes-timeline.md) — `call`, `setParallax`
- [VN Bridge & Java Hooks](../integration/jes-bridge.md) — registering call handlers
