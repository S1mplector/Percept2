# JVN Engine Hub Announcements

Format:

    ## YYYY-MM-DD — Short title
    Body text describing what changed. Multiple paragraphs are fine; blank
    lines separate them. The block ends at the next "## " header or end of file.

----

## 2026-05-22 — Puppeteer layout controls
Puppeteer's top toolbar can now be resized, collapsed, and restored from the workspace or View menu, and the focused preview/keyframe split remembers its vertical layout between sessions.

## 2026-05-21 — VNS editor navigation polish
Large pasted `timeline { ... }` blocks are easier to work with in the VNS editor. Timeline folding now has a compact in-editor navigator while scrolling long blocks, showing the current timeline number, line range, progress, and target summary with quick Top/Bottom jumps.

The VNS minimap is now JVN-specific instead of a tiny text clone: it highlights labels, timeline blocks, dialogue beats, choices, commands, diagnostics, bookmarks, and the current viewport as a script map.

## 2026-05-13 — Puppeteer persistence and charpreset exports
Puppeteer now preserves more editor-side animation state when saving and reopening timelines, including group locks, constraints, named anchors, and orbit-anchor tooling data. Parent-child constraints also respect grouped parent transforms more reliably.

The Layered Image Visualizer export panel now puts the runtime-ready `@charlayer` + `@charpreset` workflow up front, with direct copy and `.vns` snippet export actions alongside PNG and editor-only `.layersetup` export.

Developer Mode from the Engine Hub now gives the editor and launcher a collapsed top `Logs` panel for quick log-file viewing, plus launcher-to-editor Developer Mode handoff and child-process log capture.

Developer Mode also adds a `DevTools` menu to the editor and launcher with runtime diagnostics, manual GC, log refresh, editor heap configuration for launcher-started editor sessions, and a capture-output toggle.

The Version Control sidebar now uses clearer snapshot/sync language, checks for incoming remote work, highlights the next recommended action, adds more helpful tooltips and colored buttons, and refreshes status reliably after operations.

The Version Control changes list now supports multi-select and shift-select ranges, so staging, unstaging, discarding, and diff inspection can be applied to several files at once.

The runtime console toolbar now uses clearer colored JavaFX icons for run, stop, clear, and copy actions.

The build wrapper now exposes faster daily workflow commands: `./jvnw compile`, `./jvnw quick`, and `./jvnw build-info`, with matching Gradle tasks for compile-only checks, a focused verification slice, and build environment diagnostics.

The Build & Publish quick-mode presets now use the same animated orange selected arrow from the launcher project list, making the active release flow easier to spot at a glance.

The editor workspace welcome chip now has a more professional editor-dashboard layout with a status pill, workspace/project context, entry readiness, script/asset counts, runtime version, and last-modified context.

## 2026-05-10 — Puppeteer group anchors and constraint removal
The Puppeteer Anchors window now supports group entities. When a group is selected in the Entities tab, it appears in the Anchors window with a "[Group]" prefix and orange color indicator. Groups display a placeholder bounding box instead of a sprite image, allowing anchors to be placed on the group's normalized coordinate space.

## 2026-05-06 — VNS/Java interop improvements
Inline Java error reporting now has accurate line numbers when using `@jimport` or `@bind` directives, and runtime exceptions (NPE, ClassCast, etc.) are also remapped back to the original VNS source line. The error overlay now distinguishes between compilation errors and runtime errors with the correct source location.

## 2026-05-04 — Video and GIF support for Character Sprites
The JVN engine now officially supports animated `.gif`, `.mp4`, and `.mov` formats for character sprites.
Video playback is fully hardware-accelerated and uses a dynamic texture snapshotting system to ensure that all layer blending modes, color matrices, and z-ordering work nicely. You can freely mix and match these formats within the same `@charpreset`—for example, pairing a looping 3D `.mp4` character body with static `.png` facial expressions. Check out `docs/scripting/vns/presentation/vns-layered-charpresets.md` for more details.

## 2026-04-27 — Expanded VNS particle ambience presets
The VNS particle preset library now has tuned implementations for `sakura`, `fireflies`, `dust`, and `leaves` instead of routing those preset names through the neutral fallback.

Use `[particles preset=sakura]`, `[pfx fireflies intensity=0.4]`, `[weather dust opacity=0.5]`, or `[fx leaves wind=-20 tint=#ccdd7722]` to add scene-wide petals, night glows, floating motes, and autumn leaves. The existing shaping options still apply: `intensity`, `layer`, `opacity`, `speed`, `wind`, `duration`, and `tint`.

Focused preset tests cover the new tuning so these ambience effects keep their scene-sized spawn areas, alpha behavior, wind handling, and blend modes stable.

## 2026-04-27 — Documentation update covering the engine hub
New documentation has been added regarding the engine hub. You can read engine-hub.md for more information. 

## 2026-04-27 — Particle FX presets for VNS
VNS particle effects now support richer weather controls and render in game through the JavaFX VN renderer. Use `[particles]`, `[weather]`, `[pfx]`, or `[fx]` with presets like `snow` and `rain`, plus shaping options for `intensity`, `layer`, `opacity`, `speed`, `wind`, `duration`, and `tint`.

Snow and rain now use tuned scene-wide spawn areas, wind drift, opacity/tint scaling, and layer-aware rendering alongside characters and the audio visualizer. Timed effects automatically expire, while stop commands let existing particles fade out instead of disappearing abruptly.

Editor autocomplete and hover docs were updated for the expanded command surface, and focused parser/runtime tests cover the new options, presets, stop behavior, and duration expiry.

## 2026-04-27 — Puppeteer GIF export: Reveal in Folder
The "Recording Complete" dialog now has a "Reveal in Folder" action next to Close. One click opens the export directory in your OS file manager. Falls back gracefully on headless or sandboxed environments where desktop integration isn't available.
