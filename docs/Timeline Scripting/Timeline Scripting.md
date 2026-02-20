# Timeline Scripting

JVN timeline DSL describes narrative arcs (script files), entry labels, graph layout positions, and arc-to-arc links.

Primary editor bridge class:
- `editor/src/main/java/com/jvn/editor/ui/StoryTimelineView.java`

Timeline files are usually authored through the editor graph + DSL view, then committed to source control.

## File Location

Default:
- `config/timeline/story.timeline`

Legacy fallbacks (still recognized on load):
- `story/story.timeline`
- `story.timeline`

## Quick Start

```text
arc "Intro" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "Chapter1" script "scripts/story/ch1.vns" entry "start" cluster "Main" at 300,40
arc "SideQuest" script "scripts/story/side.vns" entry "entry_side" cluster "Optional" at 300,180

link Intro:intro_choice -> Chapter1:start
link Chapter1:offer_side -> SideQuest:entry_side
```

## DSL Reference

### Arc

```text
arc "ArcName" script "path/to/file.vns" entry "optionalLabel" cluster "optionalCluster" at X,Y
```

Fields:
- `ArcName` required and should be unique.
- `script` optional in parser, but recommended in production.
- `entry` optional; if present, validation checks that label in `script`.
- `cluster` optional; used by editor grouping/filter UI.
- `at X,Y` optional in parser; editor writes and maintains it.

Notes:
- `X,Y` may be integer or decimal.
- Keywords are case-insensitive (`arc`, `ARC`, `Arc` all parse).

### Link

```text
link FromArc[:FromLabel] -> ToArc[:ToLabel]
```

Behavior:
- `FromLabel` is a design annotation (where you conceptually branch from).
- `ToLabel` is the concrete target label hint.
- If `ToLabel` is omitted, validation falls back to the target arc `entry`.

### Legacy Compatibility

Older serialized forms are still accepted:
- `ARC|...`
- `LINK|...`

Editor saves in modern DSL format.

## How Timeline and VNS Work Together

Timeline is an authoring/navigation map. Story flow still executes through VNS commands like:
- `[jump label]`
- `[goto ArcName:label]`
- `[choice ...]`

Use timeline to keep structure readable at project level, and use VNS labels/commands to control exact runtime transitions.

## Validation Rules

Timeline validation checks:
- arc script file exists
- arc `entry` label exists in that script
- link target arc exists
- link target label exists (`ToLabel` or target arc `entry`)

Validation is performed by parsing referenced VNS files with `VnScriptParser`.

## Example Set

### 1. Linear Chapter Flow

```text
arc "Prologue" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "Chapter1" script "scripts/story/chapter1.vns" entry "start" cluster "Main" at 280,40
arc "Chapter2" script "scripts/story/chapter2.vns" entry "start" cluster "Main" at 520,40

link Prologue:end -> Chapter1:start
link Chapter1:end -> Chapter2:start
```

Matching VNS snippet:

```text
label end
[goto Chapter1:start]
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

Matching VNS snippet (`scripts/story/act1.vns`):

```text
label start
[choice Go with A->choose_a | Go with B->choose_b]

label choose_a
[goto RouteA:start]

label choose_b
[goto RouteB:start]
```

### 3. Optional Side Route (Clustered)

```text
arc "Town" script "scripts/story/town.vns" entry "start" cluster "Main" at 40,40
arc "GuildQuest" script "scripts/story/guild_quest.vns" entry "start" cluster "Optional" at 320,180
arc "ForestQuest" script "scripts/story/forest_quest.vns" entry "start" cluster "Optional" at 600,180

link Town:guild_offer -> GuildQuest:start
link Town:forest_offer -> ForestQuest:start
link GuildQuest:return -> Town:start
link ForestQuest:return -> Town:start
```

Recommended pattern:
- Keep optional arcs in a dedicated `Optional` cluster.
- Always link back to a stable mainline anchor arc.

### 4. Hub-and-Spoke Structure

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

This pattern is useful for:
- chapter-select style experiences
- codex/memory unlock navigation
- repeatable side content before a gated finale

### 5. Failure/Retry Loop

```text
arc "Trial" script "scripts/story/trial.vns" entry "start" cluster "Main" at 280,80
arc "Failure" script "scripts/story/failure.vns" entry "start" cluster "Main" at 560,180

link Trial:fail -> Failure:start
link Failure:retry -> Trial:start
```

Helpful for challenge segments where the player can retry without losing story context.

## Common Mistakes and Fixes

### Missing label in target arc

Bad:

```text
arc "Intro" script "scripts/story/prologue.vns" entry "start" at 40,40
link Intro -> Chapter1:does_not_exist
```

Fix:
- Create `label does_not_exist` in `Chapter1` script, or
- change link to an existing label (or omit `ToLabel` and rely on arc `entry`).

### Arc renamed without updating links

Bad:
- Renamed `RouteA` to `RouteAlpha` manually in one line only.

Fix:
- Use graph/editor rename so references are updated consistently.

### Duplicate arc names

Bad:
- Two arcs both named `Intro`.

Fix:
- Keep names globally unique (treat arc names as IDs).

## Authoring Workflow (Recommended)

1. Create arcs from scripts first (drag `.vns` files onto timeline graph).
2. Set explicit `entry` labels for every arc.
3. Add links and annotate branch labels.
4. Run timeline validation.
5. Commit timeline + related scripts in the same change.

## Team Conventions

- Use stable arc IDs (`Prologue`, `RouteA`, `Finale`) and avoid frequent renames.
- Prefer explicit `entry` labels instead of relying on implicit defaults.
- Separate mainline vs optional content via clusters.
- Validate timeline before release branches or milestone tags.
- Keep timeline layout readable (`at X,Y`) so diffs remain understandable.

## Editor Features for Timeline

- graph + text dual editing
- drag nodes to set `at X,Y`
- cluster backgrounds, filtering, collapse/expand
- search highlight for arc names
- auto layout + fit-to-view
- drag/drop `.vns` files into graph to create arcs
- quick edit/delete/open actions on selected arcs/links

## Related Docs

- Editor workflow: `docs/Editor/Editor.md`
- VNS language and commands: `docs/VNS Scripting/VNS Scripting.md`
- Project bootstrap and default timeline path: `docs/Project Setup/New Project Wizard.md`
