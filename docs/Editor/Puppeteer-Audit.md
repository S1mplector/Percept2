# Puppeteer — Hardness Audit & Expansion Roadmap

## Part 1: Hardness Audit (Function-by-Function)

### Keyframe.java — SOLID
| Function | Verdict | Issue |
|----------|---------|-------|
| `Keyframe(double, double, Easing.Type)` | OK | Null-safe easing, clamps time ≥ 0 |
| `setTimeMs` | OK | Clamps ≥ 0 |
| `setValue` | MINOR | No bounds — intentional (values can be negative for position) |
| `setEasing` | OK | Null → LINEAR fallback |
| `compareTo` | OK | Consistent with Double.compare |
| `copy` | OK | Deep copy |

**No fixes needed.**

---

### PropertyType.java — SOLID
| Function | Verdict | Issue |
|----------|---------|-------|
| `getDefaultValue` | OK | Correct defaults (1.0 for scale/alpha/zoom, 0.0 for rest) |
| `isEntityProperty` / `isCameraProperty` | OK | Correct partitioning |

**No fixes needed.**

---

### EntityTrack.java — 3 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `EntityTrack(String)` | WEAK | No null/blank check on entityName — a blank name silently creates a broken track |
| `getKeyframes` | OK | Returns empty list for missing properties |
| `addKeyframe` | OK | Auto-sorts after add |
| `removeKeyframe` | OK | Cleans up empty lists |
| `getValueAt` | BUG | Division by zero if two keyframes have identical times: `k1.getTimeMs() - k0.getTimeMs()` = 0 |
| `getMaxTimeMs` | OK | |
| `copy` | WEAK | Doesn't deep-copy the groupTrack reference in EntityGroup.copy() (separate class but related) |

---

### EntityGroup.java — 2 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `EntityGroup(String)` | WEAK | No null/blank check on name |
| `addChildEntity` | OK | Null-safe, dedup check |
| `addChildGroup` | OK | Null-safe, dedup check |
| `copy` | BUG | Does NOT copy the `groupTrack` — the copy shares the same mutable EntityTrack object. Editing keyframes on the copy mutates the original. |

---

### AnimationProject.java — 4 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `getOrCreateTrack` | WEAK | Doesn't add to `rootEntityNames` — track exists in map but invisible in EntitySelector |
| `addTrack` | OK | Properly handles root list |
| `removeTrack` | WEAK | Doesn't clean up group membership — if entity was in a group, the group still references a deleted entity name |
| `removeGroup` | WEAK | Doesn't orphan child groups — only orphans child entities. Nested groups become dangling references |
| `addGroupToGroup` | WEAK | No cycle detection — A→B→A creates infinite recursion in `computeGroupValueAt` |
| `computeValueAt` | DESIGN | Only applies hierarchical addition for X/Y — rotation, scale, alpha on groups are silently ignored. Should document this or extend |
| `computeGroupValueAt` | BUG | Infinite recursion if circular group parent chain exists (see addGroupToGroup above) |
| `copy` | WEAK | Copies EntityGroups but EntityGroup.copy() doesn't deep-copy groupTrack (see above) |

---

### CodeExporter.java — 3 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `export` | DESIGN | Uses simple sequential emission — overlapping animations at different start times won't produce `parallel` blocks. Only `exportWithGroups` does that, but nothing calls it |
| `collectPropertyEvents` | PERF | `times.contains()` is O(n) on ArrayList — fine for small keyframe counts, but should use LinkedHashSet for correctness |
| `collectPropertyEvents` | WEAK | Compares times with `!times.contains(kf.getTimeMs())` using double equality — floating point imprecision could create near-duplicate times |
| `formatNumber` | OK | Clean number formatting |
| `findEasingAt` | OK | 0.5ms tolerance is reasonable |
| `exportGroupRecursive` | DESIGN | Only emits comments, no actual animation code for groups |
| `formatEvent` | OK | |

---

### TimelinePanel.java — 5 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `addKeyframeAtPlayhead` | WEAK | If no track exists for selectedEntity (entity header selected but no track created), silently does nothing. Should auto-create track |
| `deleteSelectedKeyframe` | OK | Null-safe |
| `handleMouseDragged` | WEAK | Keyframe drag allows negative time briefly (setTimeMs clamps, but the visual position flickers) |
| `handleScroll` | DESIGN | Only handles horizontal scroll via `deltaX` — vertical scrollY field exists but is never used. Vertical track scrolling is broken for many-track projects |
| `findKeyframeAt` | WEAK | Hit detection uses Euclidean distance (good) but the radius (10px) is fixed — at extreme zoom levels keyframes overlap or become impossible to click |
| `selectTrackAt` | OK | |
| `drawKeyframes` | OK | Culls off-screen keyframes |
| `computeGridStep` | OK | Adaptive step sizes |

---

### EntitySelector.java — 2 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `refresh` | OK | Null-safe |
| `buildGroupItem` | WEAK | Uses emoji "📁" prefix in TreeItem value — later stripped with `replace("📁 ", "")` which is fragile. If user names an entity "📁 foo" it breaks |
| `setupContextMenu` | WEAK | "Delete" menu item has no action handler — clicking Delete does nothing |
| `applyFilter` | OK | Case-insensitive, recursive |

---

### KeyframeEditor.java — 1 ISSUE
| Function | Verdict | Issue |
|----------|---------|-------|
| `setKeyframe` | OK | Null-safe |
| `applyChanges` | WEAK | Silently swallows parse errors — user types "abc" and nothing happens, no feedback. Should flash the field red or show a tooltip |
| `btnDelete action` | BUG | Sets `currentKeyframe = null` and fires `onKeyframeChanged`, but does NOT actually remove the keyframe from the track. The keyframe persists in the timeline |

---

### AnimationPreview.java — 2 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `render` | OK | Null-safe scene check |
| `drawGrid` | PERF | Draws every grid line even when zoomed way out — can produce thousands of lines. The `step < 8` guard helps but could still be slow at zoom=0.1 |
| `fitToContent` | WEAK | Uses `Double.MIN_VALUE` for maxX/maxY — this is the smallest positive double (~5e-324), not negative infinity. An entity at x=-100 would not update maxX. Should use `-Double.MAX_VALUE` |
| `setupMouseControls` | DESIGN | Uses array hack `boolean[] panning = {false}` for lambda capture — works but an inner class or AtomicBoolean would be cleaner |

---

### CodePreviewPane.java — SOLID
| Function | Verdict | Issue |
|----------|---------|-------|
| All functions | OK | Simple, no edge cases |

**No fixes needed.**

---

### PuppeteerWindow.java — 2 ISSUES  
| Function | Verdict | Issue |
|----------|---------|-------|
| `updatePreview` | DESIGN | Doesn't handle camera properties (CAMERA_X, CAMERA_Y, CAMERA_ZOOM) — keyframes for these exist in the model but are never applied to the preview camera |
| `setEntityAlpha` | WEAK | Only handles Sprite2D, Label2D, Panel2D — any other Entity2D subclass silently ignores alpha changes |
| `close` | OK | Stops timer |

---

## Part 2: Critical Fixes (Prioritized)

### P0 — Bugs that cause crashes or data corruption

1. **EntityTrack.getValueAt — division by zero** when two keyframes share identical time
2. **AnimationProject.computeGroupValueAt — infinite recursion** on circular group parents
3. **EntityGroup.copy — shallow groupTrack copy** causes mutation leaks
4. **KeyframeEditor delete button — doesn't actually remove keyframe from track**

### P1 — Functional gaps

5. **AnimationPreview.fitToContent — Double.MIN_VALUE** should be `-Double.MAX_VALUE`
6. **EntitySelector delete menu item — no handler**
7. **TimelinePanel vertical scroll — scrollY is never updated**
8. **CodeExporter.export — should use exportWithGroups by default** (parallel blocks)
9. **AnimationProject.removeTrack — doesn't clean group membership**
10. **AnimationProject.removeGroup — doesn't orphan child groups**

---

## Part 3: Ren'Py Action Editor Gap Analysis

### What Ren'Py Has That We DON'T Have Yet

| Ren'Py Feature | Status in Puppeteer | Priority |
|----------------|---------------------|----------|
| **Real-time property sliders** (drag bars to adjust x/y/rot/scale live) | Missing — we only have numeric text fields | HIGH |
| **Right-click to reset** a property to default | Missing | MEDIUM |
| **Spline/curved motion paths** (non-linear spatial movement) | Missing — we only do linear interpolation between keyframes | HIGH |
| **Loop keyframes** (repeat a segment) | Missing | MEDIUM |
| **Image Viewer** (browse/filter/add images to scene from asset library) | Missing — we rely on pre-loaded scene entities | MEDIUM |
| **Sound Viewer** (browse/preview/add audio cues to timeline) | Missing | MEDIUM |
| **Show/Hide with transition** (fade in/out with configurable transition) | Missing — we have alpha but no transition presets | MEDIUM |
| **Camera drag icon** (drag camera position visually on the preview) | Missing — we have pan but not a dedicated camera position handle | LOW |
| **Keyboard camera controls** (HJKL/WASD for camera position) | Missing | LOW |
| **Depth of field / blur simulation** | Missing (JVN doesn't have blur yet) | LOW |
| **Matrix transforms** (3D rotation, skew, color matrix) | Missing | LOW |
| **Custom property registration** (user adds arbitrary properties to editor) | Missing | MEDIUM |
| **Window hide option** during animation playback | Missing | LOW |
| **Skip animation option** in generated code | Missing | MEDIUM |
| **Clipboard includes diff from open state** (incremental code) | Missing — we export full timeline | MEDIUM |
| **Property groups** (e.g. "crop" = cropX + cropY + cropW + cropH as one) | Missing | LOW |
| **Exclusive properties** (e.g. tile vs pan — can't use both) | Missing | LOW |

### What We Have That Ren'Py DOESN'T

| Our Feature | Ren'Py Equivalent |
|-------------|-------------------|
| **Hierarchical entity groups** (parent-child animation) | Ren'Py has no nested group animation |
| **Visual keyframe timeline** with diamond markers | Ren'Py uses bar sliders, not a timeline |
| **Keyframe drag repositioning** | Ren'Py doesn't have draggable keyframes |
| **Multiple easing curves per segment** | Partial — Ren'Py uses warpers |
| **Live code preview pane** updating in real-time | Ren'Py only shows clipboard output |

---

## Part 4: Expansion Roadmap — Implementation Status

### Phase 1 — Hardening COMPLETE
All P0/P1 fixes applied:
- `EntityTrack.getValueAt` — guarded division by zero (`span < 0.001`)
- `AnimationProject.computeGroupValueAt` — cycle detection via `Set<String> visited`
- `AnimationProject.addGroupToGroup` — rejects circular parenting
- `EntityGroup.copy` — deep-copies `groupTrack` keyframes
- `KeyframeEditor` delete — wired `onDeleteRequested` callback, delete actually removes from track
- `AnimationPreview.fitToContent` — uses `-Double.MAX_VALUE` for bounding box init
- `EntitySelector` delete menu — handler removes groups/tracks from project
- `TimelinePanel` vertical scroll — `scrollY` implemented with Shift+scroll
- `CodeExporter.export` — groups simultaneous events into `parallel {}` blocks
- `AnimationProject.removeTrack/removeGroup` — cleans up dangling parent/child refs

### Phase 2 — Parity Features COMPLETE
- **Property Sliders** (`KeyframeEditor.java`) — Draggable `Slider` widgets alongside text fields for time and value. Auto-configures range per `PropertyType` (e.g. alpha 0–1, rotation -360–360). Live-updates keyframe + preview. Added "Reset" button to restore property default.
- **Spline Motion Paths** (`SplinePath.java`, `AnimationPreview.java`) — New `SplinePath` utility class with Catmull-Rom interpolation. `buildControlPoints` extracts X/Y keyframes from `EntityTrack`. Preview renders dashed curve + control point dots in blue overlay.
- **Visual Entity Manipulation** (`AnimationPreview.java`, `PuppeteerWindow.java`) — Click entity in preview to select (yellow dashed highlight). Drag to move — creates X/Y keyframes at playhead. Uses `JesScene2D.exportNamed()` for entity↔name reverse lookup.

### Phase 3 — Workflow Features COMPLETE
- **Animation Presets** (`AnimationPreset.java`, `PuppeteerWindow.java`) — 12 built-in presets across 4 categories: Entrance (Fade In, Slide From Left/Right/Bottom, Bounce In), Exit (Fade Out, Zoom Out), Emphasis (Shake, Pulse, Spin), Loop (Float, Breathe). Applied via toolbar `MenuButton` dropdown at playhead time.
- **Audio Cue Track** (`AudioCue.java`, `AnimationProject.java`, `TimelinePanel.java`) — `AudioCue` model with time, file, channel (music/sound/voice), volume, fade-in. Rendered on timeline as yellow dots with vertical guide lines. `CodeExporter.exportAudioCues()` generates `play` statements.
- **Loop Markers** (`AnimationProject.java`, `TimelinePanel.java`) — `setLoopRegion(start, end)` / `clearLoopRegion()` on project. Timeline renders green semi-transparent region with dashed boundary lines and "LOOP" label.
- **Incremental Export** (`AnimationProject.java`, `CodeExporter.java`) — `captureInitialSnapshot()` records property values at t=0 when Puppeteer opens. `CodeExporter.exportIncremental()` filters events, only emitting those that differ from snapshot. Useful for inserting deltas into existing scripts.

### Phase 4 — Polish COMPLETE
- **Onion Skinning** (`AnimationPreview.java`) — Toggle via `Cmd+O`. Renders ghost outlines at ±N timesteps from playhead. Past frames in red, future in green, with fading alpha. Configurable frame count (1–10).
- **Multi-select Keyframes** (`TimelinePanel.java`) — `Shift+click` toggles keyframe in/out of selection set. `Delete` removes all selected keyframes at once. Selected keyframes highlighted with accent color.
- **Undo/Redo** (`PuppeteerCommand.java`, `PuppeteerWindow.java`) — Generic command stack (100 depth). Factory methods for add/remove/move keyframe, change value, apply preset. `Cmd+Z` undo, `Cmd+Shift+Z` redo. Presets are fully reversible.

### Future Work (not yet implemented)
- **Asset Picker Panel** — Browse project images/sprites, drag onto preview to add entity + track.
- **Custom Properties** — Let users register arbitrary numeric properties that map to JES props.
- **Curve Editor** — Visual Bezier handle editor for easing curves (like After Effects graph editor).
- **Property Groups** — e.g. "crop" = cropX + cropY + cropW + cropH as one adjustable unit.

---

## New Files Created
| File | Purpose |
|------|---------|
| `SplinePath.java` | Catmull-Rom spline interpolation + control point extraction from EntityTrack |
| `AnimationPreset.java` | 12 built-in animation presets with factory keyframe arrays |
| `AudioCue.java` | Audio cue model (time, file, channel, volume, fade) |
| `PuppeteerCommand.java` | Undo/redo command stack with factory methods |

## Keyboard Shortcuts Added
| Key | Action |
|-----|--------|
| `Space` | Toggle play/pause |
| `Home` | Rewind to start |
| `K` | Add keyframe at playhead |
| `Delete` | Delete selected keyframe(s) |
| `Cmd+C` | Copy generated code to clipboard |
| `Cmd+Z` | Undo |
| `Cmd+Shift+Z` | Redo |
| `Cmd+O` | Toggle onion skinning |
