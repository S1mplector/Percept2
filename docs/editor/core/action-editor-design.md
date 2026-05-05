# Puppeteer Design Notes

Puppeteer is the current visual animation editor for JVN. It replaced the older Action Editor design plan and is implemented under `modules/editor/src/main/java/com/jvn/editor/ui/actioneditor/`.

Use this page as an implementation-oriented map. For author-facing usage, start with [Puppeteer Editor Guide](../puppeteer/puppeteer-editor-guide.md). For exported syntax, use [Puppeteer JES Timeline DSL Reference](../puppeteer/puppeteer-jes-dsl.md).

## Current Scope

Puppeteer provides a mature timeline workspace for:

- visual entity animation
- runtime camera motion and frame authoring
- advanced keyframe channels such as matrix, blur, color matrix, DOF, and custom numeric properties
- audio cues and event cues
- groups, clips, presets, easing libraries, and runtime verification
- VNS launcher snapshots, including active Scene Lighting Studio stage presets
- JES timeline export, preview parsing, registration, and round-trip import

## Primary Source Files

| Area | Source |
|------|--------|
| Main window and workspace orchestration | `PuppeteerWindow.java` |
| Project data model | `AnimationProject.java` |
| Timeline canvas and keyframe interaction | `TimelinePanel.java` |
| Preview canvas and scene manipulation | `AnimationPreview.java` |
| Entity/group tree | `EntitySelector.java` |
| Keyframe inspector and easing editor | `KeyframeEditor.java` |
| Asset browser/importer | `AssetPickerPanel.java` |
| Code preview and parse staging | `CodePreviewPane.java` |
| JES export | `CodeExporter.java` |
| JES import/round-trip | `CodeImporter.java` |
| Undo/redo commands | `PuppeteerCommand.java` |
| VNS launch context | `PuppeteerLauncherPanel.java` |

## Workspace Layout

```text
PuppeteerWindow
├── toolbar
│   ├── transport, duration, loop controls
│   ├── presets, property target, keyframe operations
│   ├── snap, auto-key, preview behavior, orbit tools
│   └── cue management, timeline name, register, help
├── left workspace
│   ├── Entities tab
│   ├── Assets tab
│   └── Keyframe Editor
├── center workspace
│   ├── AnimationPreview
│   └── TimelinePanel
├── right sidebar
│   ├── Selection tab
│   └── Scene tab
└── code pane
    └── generated/editable JES timeline source
```

The layout is intentionally split between preview, timeline, selection inspection, and source output so authors can work visually while still seeing the generated runtime representation.

## Data Model

`AnimationProject` is the root model. It stores:

- scene name, duration, playhead, loop region, and playback flags
- entity `EntityTrack` records
- hierarchical `EntityGroup` records
- keyframes per `PropertyType`
- audio and editor event cues
- orbit anchors and preview metadata
- scene-entity snapshots for reopen flows
- optional `StageContext` from Scene Lighting Studio handoff

`StageContext` preserves the active VNS stage preset in animation projects:

```java
record StageContext(
    String presetId,
    String sourcePath,
    String backgroundTag,
    String subjectTag,
    int lightCount,
    int occluderCount,
    int responseZoneCount
)
```

This context is exported as Puppeteer metadata comments and imported again during round-trip parsing.

## Timeline Model

Each `EntityTrack` contains property lanes keyed by `PropertyType`.

Common entity lanes:

- `X`, `Y`, `Z`
- `PIVOT_X`, `PIVOT_Y`
- `ROTATION`
- `SCALE_X`, `SCALE_Y`
- `ALPHA`
- `VISIBILITY`
- matrix and blur custom lanes

Runtime camera lanes:

- `CAMERA_X`
- `CAMERA_Y`
- `CAMERA_ZOOM`
- `CAMERA_DOF_FOCUS`
- `CAMERA_DOF_STRENGTH`
- `CAMERA_DOF_MAX_BLUR`

The timeline canvas renders:

- adaptive time ruler with minor and major grid ticks
- entity and property rows
- keyframe diamonds with selected/hover states
- interpolation segment lines between keyframes
- playhead line and time badge
- loop region
- audio cue markers
- event cue markers
- hover readouts for time, target, property, value, interpolation, and easing
- marquee selection

## Preview Model

`AnimationPreview` renders a scene with:

- world overview and runtime frame
- camera HUD and safe/title guides
- entity selection and drag handles
- pivot editing
- runtime camera frame dragging
- motion paths
- onion skinning
- orbit anchors and linked-orbit workflows

Dragging entities creates or updates keyframes at the playhead. Camera manipulation writes to the dedicated runtime camera lane.

## VNS Launch Handoff

`PuppeteerLauncherPanel` scans VNS source up to the caret and builds a `SceneSnapshot` with:

- active label
- active background
- visible characters
- image, layer, and preset asset mappings
- inline or external timeline context
- active `@stagepreset` / `[stage ...]` context

When Puppeteer launches from that snapshot, it builds a `JesScene2D` using the resolved background and character assets. If a stage preset is active, `PuppeteerWindow` stores it in `AnimationProject.StageContext`; the Scene sidebar displays the preset id, source path, and light/occluder/response-zone counts.

## Scene Lighting Handoff

Scene Lighting Studio exports runtime `.stagepreset` files with VNS handoff metadata:

```properties
# @stagepreset sunset_park config/stage/sunset_park.stagepreset
# [stage sunset_park]
jvn.stagePreset.schema=2
jvn.stagePreset.id=sunset_park
jvn.stagePreset.file=sunset_park.stagepreset
jvn.stagePreset.vnsDeclaration=@stagepreset sunset_park config/stage/sunset_park.stagepreset
jvn.stagePreset.vnsCommand=[stage sunset_park]
jvn.stagePreset.lightCount=3
jvn.stagePreset.occluderCount=1
jvn.stagePreset.responseZoneCount=4
```

The VNS parser loads these presets through `@stagepreset`, and the launcher carries the active `[stage ...]` state into Puppeteer so lighting context does not get lost during animation authoring.

## Export Pipeline

```text
AnimationProject
  -> CodeExporter
  -> JES timeline text
  -> CodePreviewPane
  -> copy, preview parse, register, or save
```

Export groups simultaneous events into `parallel` blocks, emits waits for gaps, merges compatible properties into higher-level actions, and uses generic `property` actions for advanced/custom channels.

Named export adds:

- `// Timeline: <name>`
- VNS usage hint
- optional `// @jvn-puppeteer-stage ...` metadata when a stage context is present

`CodeImporter` reads generated code back into `AnimationProject`, including stage metadata, so registered timelines can be reopened without losing context.

## Runtime Registration

Registration flow:

1. Validate the current project.
2. Run timeline diagnostics.
3. Serialize to `TimelineData`.
4. Register with `TimelineRegistry`.
5. Write `scripts/timelines/<name>.jes` for project reuse.

Runtime playback is handled by `TimelineRunner` through `SceneAccessor`, which lets the same exported timeline drive JES entities or VN scene entities.

## Verification And Quality Gates

Puppeteer should keep these checks intact as features evolve:

- missing or mixed camera lanes are flagged before registration
- audio cue paths are checked
- generated code can be preview-parsed before commit
- imported code should round-trip without losing keyframes, advanced channels, cues, or stage metadata
- UI changes should keep timeline interaction stable under zoom, scroll, and selection

Relevant focused tests live under:

- `modules/editor/src/test/java/com/jvn/editor/ui/actioneditor/`
- `modules/editor/src/test/java/com/jvn/editor/ui/`

## Related Docs

- [Puppeteer Overview](../puppeteer/puppeteer.md)
- [Puppeteer Editor Guide](../puppeteer/puppeteer-editor-guide.md)
- [Puppeteer JES Timeline DSL Reference](../puppeteer/puppeteer-jes-dsl.md)
- [Puppeteer Launcher](../sidebars/right/sidebar-puppeteer-launcher.md)
- [Scene Lighting Studio](../sidebars/right/sidebar-image-tint-tool.md)
