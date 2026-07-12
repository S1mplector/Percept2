# Layered Character Presets Guide

Practical guide to creating character expression presets from layered sprite assets. Covers the full pipeline from organizing art files through `@charlayer`, `@chargroup`, and `@charpreset` declarations to runtime compositing, movable rigs, and editor tooling.

Source: `modules/core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java` (parsing), `modules/fx/src/main/java/com/jvn/fx/vn/VnRenderer.java` (rendering)

---

## Overview

JVN supports two approaches for character sprites:

| Approach | Directive | Best For |
|----------|-----------|----------|
| **Single-image** | `@charimg` with one path | Simple characters with pre-rendered expressions |
| **Layered compositing** | `@charlayer` + `@charpreset` | Characters built from interchangeable parts (eyes, mouth, brows, accessories) |
| **Movable layer groups** | `@chargroup` | Reused body parts that should move together in Puppeteer, such as heads, faces, arms, or hair chunks |

The layered approach lets artists create a small number of individual layer images that combine into a large number of distinct expressions, dramatically reducing art production time.

**Expression math:** A character with 5 eye variants × 4 mouth variants × 3 brow variants = **60 unique expressions** from only **12 layer images** + 1 base.

---

## Asset Organization

### Recommended directory structure

```text
assets/characters/<characterId>/
├── base/
│   └── body.png                  # Full body silhouette (bottommost layer)
├── eyes/
│   ├── eyes_neutral.png
│   ├── eyes_happy.png
│   ├── eyes_angry.png
│   ├── eyes_surprised.png
│   └── eyes_sad.png
├── mouth/
│   ├── mouth_neutral.png
│   ├── mouth_smile.png
│   ├── mouth_frown.png
│   ├── mouth_open.png
│   └── mouth_pout.png
├── brow/
│   ├── brow_neutral.png
│   ├── brow_raised.png
│   └── brow_furrowed.png
└── accessories/
    ├── glasses.png
    └── hat.png
```

### Supported Formats

JVN's compositing engine seamlessly blends multiple media types:
- **Images:** `.png`, `.jpg`, `.jpeg` (Transparent PNGs highly recommended)
- **Animated:** `.gif` (Plays automatically, loops infinitely)
- **Video:** `.mp4`, `.mov` (Fully hardware-accelerated. Video frames are dynamically extracted and rendered into the canvas. You can use these for complex looping 3D renders or live-action overlays. *Note: Video extraction is computationally heavier than images, so use video layers sparingly.*)

You can freely mix and match these formats in a single `@charpreset`. For example, you can have a looping `.mp4` video body, static `.png` facial features, and an animated `.gif` accessory, all compositing flawlessly on top of each other.

### Layer asset requirements

- **All layers for a character must share the same canvas dimensions.** The renderer scales every layer to the same bounding box.
- Layers are drawn bottom-to-top (left-to-right in the declaration order).
- Upper layers must have transparent backgrounds (e.g., transparent PNGs or GIFs) so they overlay cleanly on lower ones.
- The **base** layer typically contains the body, hair, and any parts that never change.

---

## Step-by-Step Workflow

### Step 1: Declare the character

```vns
@character aria "Aria"
```

### Step 2: Register individual layers with `@charlayer`

```vns
@charlayer <characterId> <layerId> <path>
```

Each `@charlayer` gives a **name** (the `layerId`) to a single image file so it can be referenced by presets.

```vns
# Base body (always present)
@charlayer aria base assets/characters/aria/base/body.png

# Eyes
@charlayer aria eyes_neutral assets/characters/aria/eyes/eyes_neutral.png
@charlayer aria eyes_happy assets/characters/aria/eyes/eyes_happy.png
@charlayer aria eyes_angry assets/characters/aria/eyes/eyes_angry.png
@charlayer aria eyes_surprised assets/characters/aria/eyes/eyes_surprised.png
@charlayer aria eyes_sad assets/characters/aria/eyes/eyes_sad.png

# Mouth
@charlayer aria mouth_neutral assets/characters/aria/mouth/mouth_neutral.png
@charlayer aria mouth_smile assets/characters/aria/mouth/mouth_smile.png
@charlayer aria mouth_frown assets/characters/aria/mouth/mouth_frown.png
@charlayer aria mouth_open assets/characters/aria/mouth/mouth_open.png

# Brow
@charlayer aria brow_neutral assets/characters/aria/brow/brow_neutral.png
@charlayer aria brow_raised assets/characters/aria/brow/brow_raised.png
@charlayer aria brow_furrowed assets/characters/aria/brow/brow_furrowed.png

# Accessories (optional layers)
@charlayer aria glasses assets/characters/aria/accessories/glasses.png
@charlayer aria hat assets/characters/aria/accessories/hat.png
```

### Step 3: Define movable groups with `@chargroup`

```vns
@chargroup <characterId> <groupId> [parent=<parentGroupId>] [pivot=<x>,<y>] <layerSpec>
```

Groups let you name a set of layers once, reuse that set in presets, and move it as one rig target in the puppeteer timeline. The `layerSpec` uses the same `$layerId` references as `@charpreset`.

```vns
@chargroup aria face $eyes_neutral | $mouth_neutral | $brow_neutral
@chargroup aria head pivot=0.5,0.28 $face | $glasses
```

After launching a scene, Puppeteer exposes stable group targets such as `aria_head` and `aria_face`, plus expression-specific aliases such as `aria_neutral_head`. Moving `aria_head` moves every layer in the group, and nested groups inherit parent movement.

Use groups when the same collection of layers is repeated across many expressions or when a body part needs to be animated separately from the body. Common examples:

| Group | Typical Layers | Puppeteer Target |
|-------|----------------|------------------|
| `head` | head base, eyes, mouth, brows, face shadows | `aria_head` |
| `face` | eyes, mouth, brows, blush | `aria_face` |
| `left_arm` | sleeve, hand, prop held in hand | `aria_left_arm` |
| `hair_front` | bangs, front hair highlights, hair clips | `aria_hair_front` |

The group is expanded where it appears in a preset. This means draw order is still explicit:

```vns
@charpreset aria neutral $base | $head | $hat
```

Here `base` draws first, every layer in `head` draws next, and `hat` draws last.

### Step 4: Compose presets with `@charpreset`

```vns
@charpreset <characterId> <expressionId> <layerSpec>
```

The `layerSpec` is a pipe-separated (`|`) list of **layer references** (`$layerId`), **group references** (`$groupId`), and/or **literal paths**. References resolve against the same character's `@charlayer` and `@chargroup` declarations.

```vns
# Core expressions
@charpreset aria neutral    $base | $head
@charpreset aria happy      $base | $eyes_happy     | $mouth_smile   | $brow_neutral
@charpreset aria angry      $base | $eyes_angry     | $mouth_frown   | $brow_furrowed
@charpreset aria surprised  $base | $eyes_surprised | $mouth_open    | $brow_raised
@charpreset aria sad        $base | $eyes_sad       | $mouth_frown   | $brow_neutral

# Compound expressions (mixing different groups)
@charpreset aria smirk      $base | $eyes_happy     | $mouth_frown   | $brow_raised
@charpreset aria worried    $base | $eyes_sad       | $mouth_neutral | $brow_furrowed
@charpreset aria excited    $base | $eyes_surprised | $mouth_smile   | $brow_raised

# With accessories
@charpreset aria thinking   $base | $eyes_neutral   | $mouth_neutral | $brow_furrowed | $glasses
@charpreset aria formal     $base | $eyes_neutral   | $mouth_smile   | $brow_neutral  | $glasses | $hat
```

### Step 5: Use in script

```vns
@label start
[bg classroom]

[show aria center neutral]
aria: Good morning, everyone.

[show aria center happy]
aria: I'm so glad to see you all!

[show aria center thinking]
aria: Now, let me check my notes...

[show aria center surprised]
aria: Wait — the test is TODAY?!

[show aria center worried]
aria: I really should have studied more...
```

## Migrating Repeated Head Layers to a Group

Before `@chargroup`, authors often had to split one character into multiple show targets just to move the head:

```vns
[show aria center $base+$body_shadow+$arm_left+$arm_right]
[show aria center $head_base+$eyes_neutral+$mouth_smile+$brow_neutral]
```

That works, but it repeats the character and every head layer each time the head needs separate motion.

With `@chargroup`, keep one character expression and make the head movable:

```vns
@charlayer aria base assets/characters/aria/base/body.png
@charlayer aria arm_left assets/characters/aria/body/arm_left.png
@charlayer aria arm_right assets/characters/aria/body/arm_right.png
@charlayer aria head_base assets/characters/aria/head/head_base.png
@charlayer aria eyes_neutral assets/characters/aria/eyes/neutral.png
@charlayer aria mouth_smile assets/characters/aria/mouth/smile.png
@charlayer aria brow_neutral assets/characters/aria/brow/neutral.png

@chargroup aria body $base | $arm_left | $arm_right
@chargroup aria head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile | $brow_neutral

@charpreset aria neutral $body | $head
```

Then in Puppeteer, animate `aria_head` instead of every head layer. The individual layer targets still exist, so you can move `aria_eyes_neutral` or `aria_mouth_smile` for fine expression work without losing the broader head motion.

## Nested Movable Groups

Nested groups are useful when one rig part should move with a parent but also have its own local motion.

```vns
@chargroup aria face parent=head $eyes_neutral | $mouth_smile | $brow_neutral
@chargroup aria head pivot=0.5,0.28 $head_base | $face
```

In Puppeteer:

- Move `aria_head` to bob, tilt, or scale the whole head.
- Move `aria_face` for a local facial slide or expression squash.
- Move `aria_eyes_neutral` for a tiny eye nudge.

Transforms stack from parent group to child group to individual layer. If `aria_head` moves right by 8px and `aria_face` moves left by 2px, face layers render with both transforms applied.

## Pivot and Rotation

`pivot=<x>,<y>` is normalized against the sprite bounds:

| Pivot | Meaning |
|-------|---------|
| `0.5,0.5` | Center of the sprite. |
| `0.5,0.28` | Centered horizontally, upper part of the sprite; useful for head rotation. |
| `0.5,1.0` | Bottom center; useful for full-body scale or sway. |

The pivot from `@chargroup` is only the default. If a Puppeteer timeline target explicitly authors a pivot/origin on `aria_head`, the authored timeline pivot wins.

## Troubleshooting Groups

| Symptom | Check |
|---------|-------|
| `$head` says unknown layer/group | Declare `@chargroup aria head ...` before the preset or inline expression that uses `$head`. |
| A group renders in the wrong order | Move `$groupId` earlier or later in the `@charpreset` layer list. |
| `aria_head` does not appear in Puppeteer | Make sure the active expression includes `$head` or every layer contained by that group. |
| Group motion affects the wrong layers | Check the layer list in `@chargroup`; nested groups expand into their declared layers. |
| Rotation pivots around the feet | Add `pivot=0.5,0.28` or author a pivot directly on the Puppeteer group target. |

Each `[show]` command swaps the expression instantly by switching which layers are composited. The transition uses a 180ms crossfade by default.

### Faster preset switching and inline composites

Once the layered presets exist, you can reference them explicitly or build small one-off composites directly in `show`, `move`, and `char` commands:

```vns
[show aria center @happy]
[show aria center @thinking+$hat]
[move aria right @formal+$shared.bow ease_out_back 500]
[char aria expression $base+$eyes_happy+$mouth_smile+$glasses]
```

- `@presetName` selects an existing preset explicitly.
- `$layerId` pulls in a declared layer.
- `+` combines preset references and layer references inline.

Use this for small variants such as glasses, hats, blush overlays, or event-specific accessories without needing to mint a brand-new permanent preset for every combination.

---

## How It Works Internally

### Parse-time resolution

When the parser encounters `@charpreset aria happy $base | $eyes_happy | $mouth_smile`:

1. Splits the spec on `|`
2. For each token starting with `$`, strips the `$` prefix and looks up the `layerId` in the character's `@charlayer` map
3. Resolves `$base` → `assets/characters/aria/base/body.png`
4. Resolves `$eyes_happy` → `assets/characters/aria/eyes/eyes_happy.png`
5. Resolves `$mouth_smile` → `assets/characters/aria/mouth/mouth_smile.png`
6. Joins the resolved paths with ` | ` separators
7. Stores the result as an expression path: `"happy"` → `"assets/.../body.png | assets/.../eyes_happy.png | assets/.../mouth_smile.png"`

The resolved expression path is stored identically to a regular `@charimg` path. At runtime, there is **no difference** between a preset-built expression and a manually declared one.

### Runtime compositing

When the renderer draws a character with a pipe-separated expression path:

1. `parseLayerPaths()` splits the stored path on `|`
2. `firstAvailableImage()` finds the first loadable image to determine dimensions
3. `drawLayerStack()` iterates through all layer paths and draws each image at the same position and size, bottom-to-top

```java
// From VnRenderer.java
private void drawLayerStack(List<String> layerPaths, double x, double y, double w, double h) {
    for (String path : layerPaths) {
        Image img = loadImage(path);
        if (img != null) gc.drawImage(img, x, y, w, h);
    }
}
```

All layers are scaled to the same bounding box, which is why **all layer images must share the same canvas dimensions**.

---

## Advanced Patterns

### Mixing `$` references with literal paths

You can combine layer references with direct file paths in the same preset:

```vns
@charpreset aria special $base | $eyes_happy | assets/characters/aria/mouth/custom_grin.png
```

This is useful for one-off expressions that don't warrant a dedicated `@charlayer` declaration.

### Cross-character layer references

Reference layers belonging to a different character using `$charId.layerId` or `$charId:layerId`:

```vns
# Shared accessory layers
@charlayer shared bow assets/characters/shared/bow.png
@charlayer shared crown assets/characters/shared/crown.png

# Aria borrows the shared bow
@charpreset aria festive $base | $eyes_happy | $mouth_smile | $shared.bow

# Alternative syntax with colon
@charpreset aria royal $base | $eyes_neutral | $mouth_smile | $shared:crown
```

Both `.` and `:` separators work identically. Use whichever feels more readable.

### Multi-layer `@charimg` shortcut

If you don't need reusable layer references, you can specify layered paths directly in `@charimg`:

```vns
@charimg aria battle assets/characters/aria/base/body.png | assets/characters/aria/eyes/eyes_angry.png | assets/characters/aria/mouth/mouth_frown.png
```

This is equivalent to declaring three `@charlayer` entries and one `@charpreset`, but the layers can't be reused in other presets. **Use `@charimg` for one-off expressions and `@charlayer` + `@chargroup` + `@charpreset` for reusable, movable layer sets.**

### Header file pattern

For projects with many characters, extract all declarations into a shared header file:

```vns
# characters/aria_layers.vns — Aria's layer & preset declarations
@character aria "Aria"

@charlayer aria base            assets/characters/aria/base/body.png
@charlayer aria eyes_neutral    assets/characters/aria/eyes/eyes_neutral.png
@charlayer aria eyes_happy      assets/characters/aria/eyes/eyes_happy.png
@charlayer aria eyes_angry      assets/characters/aria/eyes/eyes_angry.png
@charlayer aria mouth_neutral   assets/characters/aria/mouth/mouth_neutral.png
@charlayer aria mouth_smile     assets/characters/aria/mouth/mouth_smile.png
@charlayer aria mouth_frown     assets/characters/aria/mouth/mouth_frown.png

@charpreset aria neutral   $base | $eyes_neutral | $mouth_neutral
@charpreset aria happy     $base | $eyes_happy   | $mouth_smile
@charpreset aria angry     $base | $eyes_angry   | $mouth_frown
```

Then include it in any script that uses Aria:

```vns
@scenario chapter_1
@include characters/aria_layers.vns
@include characters/mentor_layers.vns

@label start
[show aria center happy]
aria: Ready for adventure!
```

This keeps scene scripts clean and ensures consistent layer definitions across your project.

### Conditional accessories

Layer presets can include or exclude optional layers to create variants with accessories:

```vns
# Without glasses
@charpreset aria neutral     $base | $eyes_neutral | $mouth_neutral | $brow_neutral
# With glasses — same expression but add the glasses layer on top
@charpreset aria neutral_gl  $base | $eyes_neutral | $mouth_neutral | $brow_neutral | $glasses
```

**Naming convention tip:** Use a suffix like `_gl` (glasses), `_hat`, `_armor` for accessory variants so they're easy to identify in scripts.

---

## Using the Editor's Layered Image Visualizer

The **Layered Image Visualizer** sidebar panel provides a visual workflow for exploring layered sprites and generating the `@charlayer` + `@charpreset` code automatically.

### Quick workflow

1. Open the **Layered Image Visualizer** panel (right sidebar → **+** → "Layered Image Visualizer")
2. The tool scans your project's `assets/` directory and discovers layered sprite sets
3. Select a character set from the dropdown
4. Toggle layer options per group (eyes, mouth, brow, etc.) using the ComboBox selectors
5. Preview the composited result in real-time on the canvas
6. Set the **Character ID** and **Expression** fields
7. In the main **Export** section, click **Copy Charpreset** or **Save Charpreset**
8. Paste the generated declarations into your script, or keep the saved `_charpreset.vns` snippet as a source file to include/copy from

### Export formats

| Format | What It Generates |
|--------|-------------------|
| **@charimg + [show]** | `@charimg aria happy <path1>\|<path2>\|<path3>` + `[show aria center @happy]` |
| **@charimg only** | Just the `@charimg` declaration |
| **@charpreset + [show]** | `@charlayer` declarations + `@charpreset aria happy $layer1 \| $layer2 \| $layer3` + `[show aria center @happy]` |
| **@charpreset only** | Just the `@charlayer` + `@charpreset` declarations |
| **Inline composite [show]** | Just `[show aria center $base+$eyes_happy+$mouth_smile]` |
| **[show] only** | Just `[show aria center @happy]` |
| **Recipe comments** | Full commented recipe with both `@charpreset` and `@charimg` forms |

The recommended reusable-character path is exposed directly in the main Export
section:

| Export control | Use it for |
|----------------|------------|
| **Copy Charpreset** | Copy `@charlayer` + `@charpreset` declarations to the clipboard |
| **Save Charpreset** | Save those declarations as `<tag>_<expression>_charpreset.vns` in the configured export folder |
| **Charpreset As** | Choose a specific `.vns` snippet destination |

The `.layersetup` controls in the same panel are editor-only snapshots for
reopening a layer selection inside the visualizer. They are not parsed by the
runtime.

### Generating multiple expressions quickly

1. Select layer options for your first expression (e.g., neutral eyes + neutral mouth)
2. Export as `@charpreset + [show]` or use **Copy Charpreset** from the main Export section
3. Paste the `@charlayer` declarations into your script header
4. Change the layer selections (e.g., switch to happy eyes + smile mouth)
5. Export as `@charpreset only`, **Copy Charpreset**, or **Save Charpreset**
6. Paste just the `@charpreset` line — the `@charlayer` declarations are already in the header

The **Randomize** button is useful for discovering interesting expression combinations you might not have considered.

### Presets

Save named presets within the Layered Image Visualizer to bookmark specific layer combinations for quick recall. Presets are stored in `.jvn/layered-image-visualizer.properties`.

### Using the Image Attributes Tool

The **Image Attributes Tool** is an alternative for teams that prefer attribute-string workflows. It generates similar output but uses a group-based attribute selector instead of individual layer toggles. See [Image Attributes Tool](../../../editor/sidebars/right/sidebar-image-attributes-tool.md) for details.

---

## Common Layer Groups

The editor's image tools recognize these standard naming conventions and normalize them automatically:

| File Name Tokens | Normalized Group |
|-----------------|-----------------|
| `eye`, `eyes` | **eyes** |
| `mouth`, `lip`, `lips` | **mouth** |
| `brow`, `eyebrow`, `eyebrows` | **brow** |
| `base`, `body` | **base** |
| `hair` | **hair** |
| `face` | **face** |
| `outfit`, `clothes` | **outfit** |
| `accessory`, `accessories`, `acc` | **accessory** |

Name your layer files or directories using these tokens (e.g., `eyes_happy.png`, `mouth/smile.png`) to get automatic group detection in the editor tools.

---

## Layer Order Matters

Layers are drawn **bottom-to-top**, meaning the first layer in the spec is drawn first (behind everything else) and the last layer is drawn on top.

```vns
# Draw order: body → eyes → mouth → brow → accessory
@charpreset aria formal $base | $eyes_neutral | $mouth_smile | $brow_neutral | $glasses | $hat
#                       ^^^^    ^^^^^^^^^^^^^   ^^^^^^^^^^^^   ^^^^^^^^^^^^^   ^^^^^^^^   ^^^^
#                       bottom                                                           top
```

**Practical rule:** Put the base/body layer first, then facial features, then accessories on top.

---

## Error Handling

The parser validates presets at parse time:

| Error | Cause | Fix |
|-------|-------|-----|
| `Unknown @charlayer reference '$eyes_neutral'` | The referenced `layerId` was never declared for this character | Add the missing `@charlayer` declaration before the `@charpreset` |
| `@charpreset layer spec cannot be empty` | The layer spec after the expression ID is blank | Add at least one `$layerId` or literal path |
| `@charpreset contains empty $layer reference` | A bare `$` with nothing after it | Fix the typo in the layer reference |
| `Malformed layer reference '$ref'` | Cross-character reference with invalid separator | Use `$charId.layerId` or `$charId:layerId` |
| `@charlayer path cannot be empty` | The path field of a `@charlayer` is blank | Provide a valid file path |

All errors include the source file name and line number for quick navigation.

---

## Complete Example: Full Character Setup

```vns
@scenario school_drama
@character aria "Aria"
@character kai "Kai"

# ── Aria's layers ─────────────────────────────────────
@charlayer aria base            assets/characters/aria/base/body.png
@charlayer aria eyes_neutral    assets/characters/aria/eyes/neutral.png
@charlayer aria eyes_happy      assets/characters/aria/eyes/happy.png
@charlayer aria eyes_angry      assets/characters/aria/eyes/angry.png
@charlayer aria eyes_sad        assets/characters/aria/eyes/sad.png
@charlayer aria mouth_neutral   assets/characters/aria/mouth/neutral.png
@charlayer aria mouth_smile     assets/characters/aria/mouth/smile.png
@charlayer aria mouth_frown     assets/characters/aria/mouth/frown.png
@charlayer aria mouth_open      assets/characters/aria/mouth/open.png
@charlayer aria brow_neutral    assets/characters/aria/brow/neutral.png
@charlayer aria brow_raised     assets/characters/aria/brow/raised.png
@charlayer aria brow_furrowed   assets/characters/aria/brow/furrowed.png
@charlayer aria glasses         assets/characters/aria/accessories/glasses.png

@charpreset aria neutral    $base | $eyes_neutral | $mouth_neutral | $brow_neutral
@charpreset aria happy      $base | $eyes_happy   | $mouth_smile   | $brow_neutral
@charpreset aria angry      $base | $eyes_angry   | $mouth_frown   | $brow_furrowed
@charpreset aria sad        $base | $eyes_sad     | $mouth_frown   | $brow_neutral
@charpreset aria surprised  $base | $eyes_happy   | $mouth_open    | $brow_raised
@charpreset aria thinking   $base | $eyes_neutral | $mouth_neutral | $brow_furrowed | $glasses
@charpreset aria smirk      $base | $eyes_happy   | $mouth_frown   | $brow_raised

# ── Kai's layers ──────────────────────────────────────
@charlayer kai base          assets/characters/kai/base/body.png
@charlayer kai eyes_neutral  assets/characters/kai/eyes/neutral.png
@charlayer kai eyes_stern    assets/characters/kai/eyes/stern.png
@charlayer kai eyes_soft     assets/characters/kai/eyes/soft.png
@charlayer kai mouth_neutral assets/characters/kai/mouth/neutral.png
@charlayer kai mouth_grin    assets/characters/kai/mouth/grin.png
@charlayer kai mouth_tight   assets/characters/kai/mouth/tight.png

@charpreset kai neutral     $base | $eyes_neutral | $mouth_neutral
@charpreset kai stern       $base | $eyes_stern   | $mouth_tight
@charpreset kai friendly    $base | $eyes_soft    | $mouth_grin

# ── Background ────────────────────────────────────────
@background classroom assets/backgrounds/school/classroom.png
@background hallway assets/backgrounds/school/hallway.png

# ── Scene ─────────────────────────────────────────────
@label start
[bg classroom]

[show aria left neutral]
[show kai right neutral]

kai: Morning. Ready for the exam?

[show aria left surprised]
aria: The exam?! That's today?!

[show kai right stern]
kai: You didn't study, did you.

[show aria left sad]
aria: I meant to, but...

[show kai right friendly]
kai: Come on. I'll quiz you before class starts.

[show aria left happy]
aria: You're a lifesaver!

[show aria left thinking]
aria: Okay, let me grab my notes...

[hide kai]
[transition FADE 600 hallway]

[show aria center neutral]
aria: Now where did I put that notebook...

[end]
```

---

## When to Use Each Approach

| Scenario | Recommended Approach |
|----------|---------------------|
| Character has < 5 expressions | `@charimg` with single images — simpler, less overhead |
| Character has 5+ expressions with shared body | `@charlayer` + `@charpreset` — saves art time |
| Facial parts need to move together in Puppeteer | `@chargroup` — author once, animate the group target |
| One-off layered expression | `@charimg` with pipe-separated paths |
| Shared accessories across characters | Cross-character `$charId.layerId` references |
| Large team / many characters | Header file pattern with `@include` |
| Rapid expression iteration | Layered Image Visualizer in the editor |

---

## Related Docs

- [Characters & Sprites](vns-characters.md) — full character system: positions, motion, framing, save/load
- [Movable Character Layer Groups](vns-movable-layer-groups.md) — focused guide for `@chargroup`, nested groups, Puppeteer targets, and migration from repeated layer lists
- [Directives & Declarations](../language/vns-directives.md) — `@charimg`, `@charlayer`, `@chargroup`, `@charpreset` syntax reference
- [Commands Reference](../language/vns-commands.md) — `[show]`, `[hide]`, `[char]` commands
- [Layered Image Visualizer](../../../editor/sidebars/right/sidebar-layered-image-visualizer.md) — editor tool for exploring layered sprites
- [Image Attributes Tool](../../../editor/sidebars/right/sidebar-image-attributes-tool.md) — editor tool for attribute-based image assembly
- [Puppeteer Launcher](../../../editor/sidebars/right/sidebar-puppeteer-launcher.md) — live scene preview understands layered presets
