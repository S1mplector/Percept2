# Timeline Scripting

JVN timeline DSL describes narrative arcs (script files), entry labels, graph layout positions, and arc-to-arc links.

Primary editor/runtime bridge class:
- `editor/src/main/java/com/jvn/editor/ui/StoryTimelineView.java`

Default file location:
- `config/timeline/story.timeline`

Legacy fallbacks are still recognized:
- `story/story.timeline`
- `story.timeline`

## Quick Start

```text
arc "Intro" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "Chapter1" script "scripts/story/ch1.vns" cluster "Main" at 280,40
arc "SideQuest" script "scripts/story/side.vns" cluster "Optional" at 280,180

link Intro -> Chapter1
link Chapter1:branchA -> SideQuest:entry_side
```

## DSL Syntax

## Arc declaration

```text
arc "ArcName" script "path/to/file.vns" entry "optionalLabel" cluster "optionalCluster" at X,Y
```

Fields:
- `ArcName` required
- `script` optional but strongly recommended
- `entry` optional
- `cluster` optional
- `at X,Y` optional in parser, but editor writes it and uses it for graph layout

## Link declaration

```text
link FromArc[:FromLabel] -> ToArc[:ToLabel]
```

Behavior:
- explicit `ToLabel` wins
- if omitted, editor validation falls back to target arc's `entry` label

## Compatibility Lines

Legacy serialized forms are also accepted on load:
- `ARC|...`
- `LINK|...`

Editor always writes modern DSL format when saving.

## Validation Rules

Timeline validation checks:

- arc script file exists
- arc `entry` label exists in target VNS file
- link target arc exists
- link target label exists (`ToLabel` or target arc `entry`)

Validation is powered by parsing referenced VNS scripts through `VnScriptParser`.

## Editor Features

- graph + text dual editing
- drag nodes to set `at X,Y`
- cluster backgrounds and filter
- cluster collapse/expand
- search highlight for arc names
- auto layout
- fit-to-view
- drag/drop `.vns` files into graph to create arcs

## Runtime/Flow Connection

Timeline DSL itself is an authoring model.
Your game flow still transitions through VNS commands/interops (`jump`, `goto`, etc.).

A common pattern:

1. design narrative map with timeline arcs
2. keep arc names aligned with script identities
3. drive transitions in script with explicit labels

## Team Conventions (Recommended)

- treat arc names as stable IDs once linked from many places
- keep each arc's `entry` label explicit
- avoid duplicate arc names
- keep optional/side clusters separated for readability
- validate timeline before release branches

## Example: Branching Narrative Map

```text
arc "Prologue" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "RouteA" script "scripts/story/route_a.vns" entry "start" cluster "Main" at 300,20
arc "RouteB" script "scripts/story/route_b.vns" entry "start" cluster "Main" at 300,120
arc "Secret" script "scripts/story/secret.vns" entry "unlock" cluster "Optional" at 560,120

link Prologue:choice_a -> RouteA:start
link Prologue:choice_b -> RouteB:start
link RouteB:secret_gate -> Secret:unlock
```

## Related Docs

- Editor workflow: `docs/Editor/Editor.md`
- VNS language: `docs/VNS Scripting/VNS Scripting.md`
