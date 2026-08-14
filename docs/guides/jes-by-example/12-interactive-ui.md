# JES By Example — Interactive UI

Build an in-scene settings panel with clickable buttons, a draggable slider, live labels, and Java call handlers.

**Difficulty:** Advanced
**Time:** 25 minutes
**Concepts:** `Button2D`, `Slider2D`, hover/pressed states, `registerCall`, callback properties, `setLabelText`, continuous slider updates

---

## What You Are Building

The scene has three buttons that adjust or reset a difficulty value and a slider that controls music volume. JES owns the visual layout and callback names; Java owns the behavior and mutable state.

This separation keeps UI composition readable without pretending arbitrary game logic belongs in the scene file.

---

## 1. Author the Scene

Create `game/ui/settings-demo.jes`:

```jes
scene "SettingsDemo" {
  entity "background" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.03, 0.05, 0.10, 1)
    }
  }

  entity "card" {
    component Panel2D {
      x: 170
      y: 105
      w: 460
      h: 390
      fill: rgb(0.10, 0.13, 0.22, 0.96)
    }
  }

  entity "title" {
    component Label2D {
      text: "Mission Settings"
      x: 400
      y: 145
      size: 30
      bold: true
      color: rgb(0.92, 0.95, 1, 1)
      align: center
    }
  }

  entity "difficulty_label" {
    component Label2D {
      text: "Difficulty: 3"
      x: 400
      y: 225
      size: 22
      bold: true
      color: rgb(1, 0.78, 0.30, 1)
      align: center
    }
  }

  entity "easier_button" {
    component Button2D {
      x: 235
      y: 270
      w: 100
      h: 44
      text: "Easier"
      call: "difficultyDown"
      normal: rgb(0.20, 0.30, 0.48, 1)
      hover: rgb(0.28, 0.42, 0.66, 1)
      pressed: rgb(0.13, 0.22, 0.38, 1)
      textColor: rgb(1, 1, 1, 1)
      fontSize: 17
    }
  }

  entity "reset_button" {
    component Button2D {
      x: 350
      y: 270
      w: 100
      h: 44
      text: "Reset"
      call: "difficultyReset"
      normal: rgb(0.32, 0.28, 0.40, 1)
      hover: rgb(0.46, 0.39, 0.58, 1)
      pressed: rgb(0.23, 0.19, 0.31, 1)
      textColor: rgb(1, 1, 1, 1)
      fontSize: 17
    }
  }

  entity "harder_button" {
    component Button2D {
      x: 465
      y: 270
      w: 100
      h: 44
      text: "Harder"
      call: "difficultyUp"
      normal: rgb(0.48, 0.22, 0.25, 1)
      hover: rgb(0.68, 0.30, 0.34, 1)
      pressed: rgb(0.37, 0.14, 0.18, 1)
      textColor: rgb(1, 1, 1, 1)
      fontSize: 17
    }
  }

  entity "volume_label" {
    component Label2D {
      text: "Music: 70%"
      x: 400
      y: 360
      size: 18
      bold: false
      color: rgb(0.82, 0.88, 1, 1)
      align: center
    }
  }

  entity "volume_slider" {
    component Slider2D {
      x: 275
      y: 405
      w: 250
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
}
```

Each interactive component names a call handler. The scene parses and renders without custom Java, but clicks on those names need behavior registered by the host.

---

## 2. Register the Behavior

Wire the handlers after loading the `JesScene2D` and before the player can interact with it:

```java
import com.jvn.scripting.jes.runtime.JesScene2D;
import java.util.Map;

public final class SettingsUi {
    private SettingsUi() {}

    public static void wire(JesScene2D scene) {
        int[] difficulty = {3};

        Runnable refreshDifficulty = () -> scene.invokeCall(
            "setLabelText",
            Map.of(
                "target", "difficulty_label",
                "text", "Difficulty: " + difficulty[0]
            )
        );

        scene.registerCall("difficultyDown", props -> {
            difficulty[0] = Math.max(1, difficulty[0] - 1);
            refreshDifficulty.run();
        });

        scene.registerCall("difficultyReset", props -> {
            difficulty[0] = 3;
            refreshDifficulty.run();
        });

        scene.registerCall("difficultyUp", props -> {
            difficulty[0] = Math.min(5, difficulty[0] + 1);
            refreshDifficulty.run();
        });

        scene.registerCall("musicVolumeChanged", props -> {
            double value = ((Number) props.getOrDefault("value", 0.7)).doubleValue();
            int percent = (int) Math.round(value * 100.0);

            scene.invokeCall(
                "setLabelText",
                Map.of("target", "volume_label", "text", "Music: " + percent + "%")
            );

            // Forward value to your audio/settings service here.
        });
    }
}
```

`setLabelText` is a built-in action, so the custom handlers can update `Label2D` entities without casting scene objects.

The one-element array is only a compact mutable holder for this example. In production, keep settings in your game state or preferences service and let handlers update that source of truth.

---

## What Callbacks Receive

Button clicks include the component properties plus pointer data and a conventional numeric value:

```text
{ x: <pointer-x>, y: <pointer-y>, value: 1.0, ...button properties }
```

Slider changes include the current numeric value plus the slider properties:

```text
{ value: <current-value>, ...slider properties }
```

Always read numeric callback values through `Number` before converting to `int`, `float`, or `double`. That keeps handlers independent of the concrete number type produced by the runtime.

---

## Button Visual States

A `Button2D` has three explicit colors:

| Property | Used when |
|---|---|
| `normal` | Pointer is outside the button |
| `hover` | Pointer is over the button |
| `pressed` | Pointer is down on the button |

Make every state visibly distinct, but keep the same overall palette so the button still feels like one control. Ensure `textColor` remains readable against all three backgrounds.

---

## Slider Behavior

`Slider2D` maps horizontal pointer position into its `min`–`max` range. Its handler runs continuously while the player drags, which is ideal for live preview but has two consequences:

- Keep the callback fast; avoid disk writes or expensive asset work on every update.
- Persist the final setting through your normal settings lifecycle rather than saving on every drag frame.

For discrete choices such as difficulty levels, buttons are usually clearer. Use a slider for genuinely continuous values such as volume, brightness, or camera sensitivity.

---

## Built-In and Custom Actions

Use a built-in action when the runtime already owns the operation:

```java
scene.invokeCall(
    "setLabelText",
    Map.of("target", "volume_label", "text", "Music: 50%")
);
```

Register a named handler for game-specific behavior:

```java
scene.registerCall("applyLoadout", props -> {
    // Validate selection, update game state, and refresh the scene.
});
```

Use `setActionHandler` only as a fallback for otherwise unregistered action names. Named handlers are easier to find, test, and reason about when a scene has several controls.

---

## When to Use JES Widgets

Use `Button2D` and `Slider2D` for controls embedded in a JES scene: an arcade HUD, puzzle controls, a world-space terminal, or a small settings card.

For a full main menu, save/load surface, or keyboard/controller-first row navigation, use the JVN menu system. For a custom story overlay bound directly to VNS variables, use a Facet. Picking the system that owns the interaction lifecycle avoids duplicating navigation and state logic.

---

## Testing Checklist

- Hover every button and verify its label remains readable.
- Click the edges of each button to confirm its hit area matches the artwork.
- Drag the slider to both limits and verify the callback reaches `min` and `max`.
- Confirm repeated button presses respect the intended bounds.
- Reload the scene and verify the visual defaults agree with the backing state.
- Test at the project's real viewport and input scaling.
- Keep slider handlers lightweight enough for continuous updates.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| A click changes color but does nothing | The `call` name must match a registered handler exactly |
| A label does not update | `target` must be the entity ID containing the `Label2D` |
| A slider throws a cast error | Read `value` as `Number`, then convert it |
| The slider floods a service | Cache or preview during drag; persist through the normal settings lifecycle |
| Controls sit behind artwork | Review entity declaration order and the scene's layering |
| Unknown properties fail parsing | Use only properties supported by that component; keep custom state in Java |

---

## Key Takeaways

1. JES defines widget layout, styling, initial values, and callback names.
2. Java handlers own custom behavior and mutable application state.
3. Buttons provide normal, hover, and pressed visual states.
4. Sliders invoke their callback continuously with a numeric `value`.
5. Use built-in `setLabelText` to refresh labels without manual entity casts.
6. Prefer named `registerCall` handlers over a global fallback when behavior is known.

---

## Where to Go Next

You have completed the JES By Example path. Continue with the [JES UI Widgets reference](../../scripting/jes/gameplay/jes-ui-widgets.md), connect the scene to a story through [VNS Bridge Integration](10-vns-bridge.md), or explore [Reactive VNS UI](../vns-by-example/11-reactive-ui-and-facets.md).

[Back to JES By Example](../jes-by-example.md)
