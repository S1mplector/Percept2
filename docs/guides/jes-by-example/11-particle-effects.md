# JES By Example — Particle Effects

Build a magical flare from a burst emitter, then add continuous snow to learn the two main particle-authoring patterns.

**Difficulty:** Advanced
**Time:** 20 minutes
**Concepts:** `ParticleEmitter2D`, `emitParticles`, continuous emission, lifetime, speed, angle, gravity, color fade, blending

---

## Burst and Continuous Emitters

JES uses the same component for both patterns:

- Set `emissionRate` above zero for a continuous ambient effect.
- Set `emissionRate: 0` and trigger `emitParticles` from a timeline for a deliberate burst.

This chapter puts both in one scene so their behavior is easy to compare.

---

## The Complete Scene

Create `game/effects/particle-demo.jes`:

```jes
scene "ParticleDemo" {
  entity "night" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.02, 0.04, 0.10, 1)
    }
  }

  entity "instructions" {
    component Label2D {
      text: "A burst at the center, continuous snow from above"
      x: 400
      y: 545
      size: 18
      bold: false
      color: rgb(0.85, 0.90, 1, 1)
      align: center
    }
  }

  entity "magic_burst" {
    component ParticleEmitter2D {
      x: 400
      y: 300
      emissionRate: 0
      minLife: 0.5
      maxLife: 1.1
      minSize: 3
      maxSize: 9
      endSizeScale: 0.05
      minSpeed: 70
      maxSpeed: 190
      minAngle: 0
      maxAngle: 360
      gravityY: 45
      additive: true
      startColor: rgb(0.35, 0.85, 1, 1)
      endColor: rgb(0.55, 0.20, 1, 0)
    }
  }

  entity "snow" {
    component ParticleEmitter2D {
      x: 400
      y: -10
      emissionRate: 15
      minLife: 3
      maxLife: 6
      minSize: 2
      maxSize: 5
      endSizeScale: 0.8
      minSpeed: 20
      maxSpeed: 40
      minAngle: 80
      maxAngle: 100
      gravityY: 30
      additive: false
      startColor: rgb(1, 1, 1, 0.9)
      endColor: rgb(1, 1, 1, 0.2)
    }
  }

  timeline {
    wait 400
    emitParticles "magic_burst" { count: 36 }
    wait 900
    emitParticles "magic_burst" { count: 18 }
  }
}
```

The `magic_burst` entity stays dormant until the timeline addresses it by name. The `snow` entity begins emitting as soon as the scene runs because its emission rate is nonzero.

---

## How Particle Motion Fits Together

Tune related properties as groups instead of changing everything at once.

### Lifetime and population

```jes
emissionRate: 15
minLife: 3
maxLife: 6
```

Emission rate controls how quickly particles appear; lifetime controls how long they remain. Increasing both can multiply the number alive at the same time. For bursts, `count` replaces emission rate as the initial population control.

### Direction and speed

```jes
minSpeed: 70
maxSpeed: 190
minAngle: 0
maxAngle: 360
```

Angles are degrees: `0` points right. A `0`–`360` span scatters in every direction, while a narrow range creates a stream or cone.

### Gravity

```jes
gravityY: 45
```

Positive gravity bends particles downward; negative gravity pulls them upward. Fire commonly uses a negative value, while sparks, rain, and snow usually use a positive one.

### Size and fade

```jes
minSize: 3
maxSize: 9
endSizeScale: 0.05
startColor: rgb(0.35, 0.85, 1, 1)
endColor: rgb(0.55, 0.20, 1, 0)
```

Each particle starts with a random size in the configured range. `endSizeScale` multiplies that size over its lifetime. Start and end colors interpolate together with alpha, so an end alpha of `0` produces a clean fade.

---

## Additive Versus Normal Blending

| Setting | Appearance | Good uses |
|---|---|---|
| `additive: true` | Light accumulates where particles overlap | Magic, sparks, fire, energy |
| `additive: false` | Ordinary alpha compositing | Snow, rain, leaves, smoke |

Additive blending is striking but can wash out a dense emitter. Tune population first, then decide whether glow improves the effect.

---

## Add a Texture

Particles work without a texture, which is ideal during motion tuning. Add one only after the movement feels right:

```jes
texture: "assets/effects/soft-star.png"
```

Use a small transparent image with tight empty margins. Oversized transparent borders make particles appear offset or smaller than their configured size.

---

## Common Recipes

| Effect | Angle | Gravity | Lifetime | Blend |
|---|---|---|---|---|
| Fire | Narrow upward range | Negative | Short | Additive |
| Smoke | Narrow upward range | Slightly negative | Long | Normal |
| Sparks | Wide or radial | Positive | Short | Additive |
| Snow | Narrow downward range | Positive | Long | Normal |
| Magic pulse | Full circle | Small positive or zero | Short | Additive |

For a campfire, start from the burst entity and change it to continuous emission:

```jes
emissionRate: 30
minAngle: 250
maxAngle: 290
gravityY: -50
```

---

## A Disciplined Tuning Loop

1. Start without a texture.
2. Decide whether the effect is continuous or event-driven.
3. Tune population and lifetime together.
4. Tune angle, speed, and gravity as one motion group.
5. Adjust size over life.
6. Add color interpolation and choose blending.
7. Add a texture last.
8. Test alongside the busiest version of the actual scene.

This order keeps a visual problem traceable to the small group of properties most likely to cause it.

---

## Performance and Readability

- Prefer one well-tuned emitter over several nearly identical emitters.
- Avoid combining a high emission rate with a long lifetime unless the scene needs that density.
- Keep emitter entity IDs stable because timelines reference them by name.
- Use bursts for impacts and one-shot cues; they make timing and particle counts explicit.
- Preview effects against their real background because additive colors change dramatically over light surfaces.
- Treat textures like other project assets: use project-relative paths and exact filename case.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| A burst never appears | The `emitParticles` target must match the emitter entity ID |
| Particles never stop | Use `emissionRate: 0` for a burst-only emitter |
| The effect disappears too quickly | Increase `minLife` and `maxLife`, or reduce speed |
| Motion goes the wrong way | Recheck the angle range and the sign of `gravityY` |
| The effect is a white blob | Reduce population or disable additive blending |
| Textured particles look offset | Crop transparent padding from the source image |

---

## Key Takeaways

1. `ParticleEmitter2D` defines both continuous effects and timeline-triggered bursts.
2. Use `emissionRate: 0` with `emitParticles` for repeatable one-shot cues.
3. Tune lifetime/population, motion, size, and color in separate passes.
4. Positive `gravityY` pulls down; negative values pull up.
5. Additive blending fits luminous effects, while normal blending fits physical material.
6. Add textures after the untextured motion is working.

---

## Next

Continue to [Interactive UI](12-interactive-ui.md), or open the complete [`ParticleEmitter2D` reference](../../scripting/jes/scene/components.md#particleemitter2d).

[Back to JES By Example](../jes-by-example.md)
