# Story Timeline — Arcs & Links DSL

Complete reference for the story timeline DSL that maps narrative arcs, script files, entry labels, clusters, and arc-to-arc links into a visual graph.

Editor: `editor/src/main/java/com/jvn/editor/ui/StoryTimelineView.java`
Code editor: `editor/src/main/java/com/jvn/editor/ui/TimelineCodeEditor.java`

---

## Overview

The story timeline is a **project-level navigation map** of your VNS scripts. It does not execute story logic — VNS labels, jumps, and choices handle runtime flow. Instead, the timeline provides:

- A visual graph of all narrative arcs
- Validation that script files and labels exist
- Cluster grouping for organizational clarity
- Link annotations showing branching relationships
- A DSL that doubles as both text and graph representation

---

## File Location

Default path:

```text
config/timeline/story.timeline
```

Legacy fallbacks (still recognized on load):

```text
story/story.timeline
story.timeline
```

---

## Arc Declaration

An arc represents one narrative segment (typically one `.vns` script file).

```text
arc "ArcName" script "path/to/file.vns" entry "labelName" cluster "ClusterName" priority 0 color "#88ccff" tags "main,route" at X,Y
```

| Field | Required | Description |
|-------|----------|-------------|
| `"ArcName"` | Yes | Unique identifier for the arc |
| `script "path"` | Recommended | Path to the VNS script file |
| `entry "label"` | Optional | Entry label in the script (validated) |
| `cluster "name"` | Optional | Grouping for editor UI filtering |
| `priority N` | Optional | Integer sort/importance hint shown in graph/list |
| `color "#RRGGBB"` | Optional | Arc accent color in graph view |
| `tags "a,b,c"` | Optional | Free-form metadata tags for filtering/conventions |
| `at X,Y` | Optional | Position in the editor graph (visual only) |

### Rules

- Arc names must be **unique** across the timeline.
- Keywords are **case-insensitive** (`arc`, `ARC`, `Arc` all work).
- `at X,Y` accepts integers or decimals. These are visual coordinates only — they do not affect runtime.
- Arc names can be quoted (`"Intro"`) or bare identifiers (`Intro`).
- Use quotes for values containing spaces, punctuation, or `:`.

### Examples

```text
# Full declaration
arc "Prologue" script "scripts/story/prologue.vns" entry "start" cluster "Main" priority 10 color "#8ecaff" tags "main,opening" at 40,40

# Minimal — just a name
arc "Placeholder"

# No entry label (relies on script default)
arc "Chapter1" script "scripts/story/chapter1.vns" cluster "Main" at 280,40

# Bare identifier name
arc Finale script "scripts/story/finale.vns" entry "start" at 600,100
```

---

## Link Declaration

A link represents a narrative connection between two arcs.

```text
link FromArc[:FromLabel] -> ToArc[:ToLabel]
```

| Part | Required | Description |
|------|----------|-------------|
| `FromArc` | Yes | Source arc name |
| `FromLabel` | Optional | Label annotation (where the branch conceptually originates) |
| `ToArc` | Yes | Target arc name |
| `ToLabel` | Optional | Target label hint (falls back to target arc's `entry`) |

`FromArc`, `ToArc`, and labels may be quoted when they contain spaces or punctuation:

```text
link "Route A":"choice yes" -> "Act 2":"start"
```

### Examples

```text
# Simple link
link Prologue -> Chapter1

# With labels on both sides
link Prologue:end -> Chapter1:start

# Branch point annotation
link Act1:choose_a -> RouteA:start
link Act1:choose_b -> RouteB:start

# Only target label
link RouteA -> Act2:merge_point
```

### Validation

- `FromArc` and `ToArc` must reference existing arc names.
- If `ToLabel` is specified, it must exist in the target arc's script file.
- If `ToLabel` is omitted, validation uses the target arc's `entry` label.

---

## Comments

Lines starting with `#` are comments:

```text
# Main story flow
arc "Prologue" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "Chapter1" script "scripts/story/chapter1.vns" entry "start" cluster "Main" at 280,40

# Branch
link Prologue:end -> Chapter1:start
```

---

## Clusters

Clusters group arcs visually in the editor graph. They have no runtime effect.

```text
arc "Intro" script "scripts/story/intro.vns" cluster "Main" at 40,40
arc "Ch1" script "scripts/story/ch1.vns" cluster "Main" at 280,40
arc "SideA" script "scripts/side/quest_a.vns" cluster "Optional" at 280,180
arc "SideB" script "scripts/side/quest_b.vns" cluster "Optional" at 280,300
```

The editor displays:
- Cluster background regions with labels
- Filter/collapse controls per cluster
- Search highlighting within clusters

Conventions:
- `"Main"` — primary story path
- `"Optional"` — side content
- `"Memories"` / `"Flashbacks"` — unlockable segments
- `"Debug"` — test arcs (exclude from release)

---

## Legacy Format

Older serialized formats are still accepted on load:

```text
ARC|Prologue|scripts/story/prologue.vns|start|40|40
LINK|Prologue|end|Chapter1|start
```

The editor always saves in the modern DSL format. There is no need to use the legacy format for new projects.

---

## How Timeline and VNS Work Together

The timeline is an **authoring tool**, not a runtime executor. Story flow is controlled by VNS commands:

| Timeline concept | VNS equivalent |
|-----------------|----------------|
| Arc → Arc link | `[jump label]` or `[goto ArcName:label]` |
| Branch links | `[choice ...]` with goto targets |
| Entry label | `@label start` in the script |

**Example — timeline:**

```text
arc "Act1" script "scripts/story/act1.vns" entry "start" at 40,100
arc "RouteA" script "scripts/story/route_a.vns" entry "start" at 320,40
arc "RouteB" script "scripts/story/route_b.vns" entry "start" at 320,180

link Act1:choose_a -> RouteA:start
link Act1:choose_b -> RouteB:start
```

**Matching VNS (`act1.vns`):**

```vns
@scenario act1

@label start
narrator: Which path do you choose?

[choice Go with Route A->choose_a | Go with Route B->choose_b]

@label choose_a
[goto RouteA:start]

@label choose_b
[goto RouteB:start]
```

---

## Validation Rules

The editor validates that:

1. **Arc script file exists** — referenced `.vns` file is found on disk
2. **Arc entry label exists** — the `entry` label is found inside the script (by parsing with `VnScriptParser`)
3. **Link target arc exists** — `ToArc` matches a declared arc
4. **Link target label exists** — `ToLabel` (or target arc's `entry`) is found in the script

Validation errors are shown:
- In the code editor as **red underline** error spans
- In a validation dialog (accessible via toolbar)
- With context menu quick-fixes: "Create arc" or "Change to existing"

---

## Story Patterns

### 1. Linear Chapter Flow

```text
arc "Prologue" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "Chapter1" script "scripts/story/chapter1.vns" entry "start" cluster "Main" at 280,40
arc "Chapter2" script "scripts/story/chapter2.vns" entry "start" cluster "Main" at 520,40
arc "Epilogue" script "scripts/story/epilogue.vns" entry "start" cluster "Main" at 760,40

link Prologue:end -> Chapter1:start
link Chapter1:end -> Chapter2:start
link Chapter2:end -> Epilogue:start
```

### 2. Branch and Merge

```text
arc "Act1" script "scripts/story/act1.vns" entry "start" cluster "Main" at 40,100
arc "RouteA" script "scripts/story/route_a.vns" entry "start" cluster "Main" at 320,40
arc "RouteB" script "scripts/story/route_b.vns" entry "start" cluster "Main" at 320,180
arc "Act2" script "scripts/story/act2.vns" entry "start" cluster "Main" at 600,100

link Act1:choose_a -> RouteA:start
link Act1:choose_b -> RouteB:start
link RouteA:merge -> Act2:start
link RouteB:merge -> Act2:start
```

### 3. Hub-and-Spoke

```text
arc "Hub" script "scripts/story/hub.vns" entry "start" cluster "Main" at 300,120
arc "Memory1" script "scripts/story/memory1.vns" entry "start" cluster "Memories" at 40,20
arc "Memory2" script "scripts/story/memory2.vns" entry "start" cluster "Memories" at 40,220
arc "Finale" script "scripts/story/finale.vns" entry "start" cluster "Main" at 620,120

link Hub:m1 -> Memory1:start
link Hub:m2 -> Memory2:start
link Memory1:return -> Hub:start
link Memory2:return -> Hub:start
link Hub:finale_gate -> Finale:start
```

### 4. Optional Side Content

```text
arc "Town" script "scripts/story/town.vns" entry "start" cluster "Main" at 40,40
arc "GuildQuest" script "scripts/story/guild.vns" entry "start" cluster "Optional" at 320,180
arc "ForestQuest" script "scripts/story/forest.vns" entry "start" cluster "Optional" at 600,180

link Town:guild_offer -> GuildQuest:start
link Town:forest_offer -> ForestQuest:start
link GuildQuest:return -> Town:start
link ForestQuest:return -> Town:start
```

### 5. Failure/Retry Loop

```text
arc "Trial" script "scripts/story/trial.vns" entry "start" cluster "Main" at 280,80
arc "Failure" script "scripts/story/failure.vns" entry "start" cluster "Main" at 560,180

link Trial:fail -> Failure:start
link Failure:retry -> Trial:start
```

---

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Missing label in target arc | Create the label in the script, or change link to an existing label |
| Arc renamed without updating links | Use editor rename so references update consistently |
| Duplicate arc names | Keep names globally unique (treat arc names as IDs) |
| Script path wrong | Use relative paths from project root |

---

## Authoring Workflow

1. Create arcs by dragging `.vns` files onto the graph or using the DSL editor.
2. Set explicit `entry` labels for every arc.
3. Add links and annotate branch labels.
4. Run timeline validation (toolbar button or `Ctrl+Shift+V`).
5. Commit the timeline file + related scripts in the same change.

---

## Editor Features

| Feature | Description |
|---------|-------------|
| **Dual editing** | Graph view and text DSL editor stay in sync |
| **Drag nodes** | Move arcs to set `at X,Y` positions |
| **Cluster UI** | Background regions, filtering, collapse/expand |
| **Search** | Highlight arcs/links by name |
| **Auto layout** | Fit-to-view, automatic arrangement |
| **Drag & drop** | Drop `.vns` files into graph to create arcs |
| **Context menu** | Edit/delete/open actions on arcs and links |
| **Zoom** | Mouse wheel zoom (0.6x – 2.0x) |
| **Quick-fix** | Right-click red errors to create arcs or change labels |
| **Syntax highlighting** | Keywords, strings, numbers, arrows, comments |

---

## Team Conventions

- Use stable arc IDs (`Prologue`, `RouteA`, `Finale`) and avoid frequent renames.
- Prefer explicit `entry` labels instead of relying on implicit defaults.
- Separate mainline vs optional content via clusters.
- Validate the timeline before release branches or milestone tags.
- Keep timeline layout readable (`at X,Y`) so diffs remain understandable.

---

## Related Docs

- [Timeline Overview](../overview/timeline-scripting.md)
- [Puppeteer Animation Timelines](../animation/timeline-animation.md)
- [VNS Scripting](../../vns/overview/vns-scripting.md)
- [Editor Guide](../../../editor/core/editor.md)
