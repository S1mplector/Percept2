# Puppeteer — Hardness Audit & Expansion Roadmap

## Part 1: Hardness Audit (Function-by-Function)

### Keyframe.java — SOLID
| Function | Verdict | Issue |
|----------|---------|-------|
| `Keyframe(double, double, Easing.Type)` | ✅ OK | Null-safe easing, clamps time ≥ 0 |
| `setTimeMs` | ✅ OK | Clamps ≥ 0 |
| `setValue` | ⚠️ MINOR | No bounds — intentional (values can be negative for position) |
| `setEasing` | ✅ OK | Null → LINEAR fallback |
| `compareTo` | ✅ OK | Consistent with Double.compare |
| `copy` | ✅ OK | Deep copy |

**No fixes needed.**

---

### PropertyType.java — SOLID
| Function | Verdict | Issue |
|----------|---------|-------|
| `getDefaultValue` | ✅ OK | Correct defaults (1.0 for scale/alpha/zoom, 0.0 for rest) |
| `isEntityProperty` / `isCameraProperty` | ✅ OK | Correct partitioning |

**No fixes needed.**

---

### EntityTrack.java — 3 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `EntityTrack(String)` | ⚠️ WEAK | No null/blank check on entityName — a blank name silently creates a broken track |
| `getKeyframes` | ✅ OK | Returns empty list for missing properties |
| `addKeyframe` | ✅ OK | Auto-sorts after add |
| `removeKeyframe` | ✅ OK | Cleans up empty lists |
| `getValueAt` | 🔴 BUG | Division by zero if two keyframes have identical times: `k1.getTimeMs() - k0.getTimeMs()` = 0 |
| `getMaxTimeMs` | ✅ OK | |
| `copy` | ⚠️ WEAK | Doesn't deep-copy the groupTrack reference in EntityGroup.copy() (separate class but related) |

---

### EntityGroup.java — 2 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `EntityGroup(String)` | ⚠️ WEAK | No null/blank check on name |
| `addChildEntity` | ✅ OK | Null-safe, dedup check |
| `addChildGroup` | ✅ OK | Null-safe, dedup check |
| `copy` | 🔴 BUG | Does NOT copy the `groupTrack` — the copy shares the same mutable EntityTrack object. Editing keyframes on the copy mutates the original. |

---

### AnimationProject.java — 4 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `getOrCreateTrack` | ⚠️ WEAK | Doesn't add to `rootEntityNames` — track exists in map but invisible in EntitySelector |
| `addTrack` | ✅ OK | Properly handles root list |
| `removeTrack` | ⚠️ WEAK | Doesn't clean up group membership — if entity was in a group, the group still references a deleted entity name |
| `removeGroup` | ⚠️ WEAK | Doesn't orphan child groups — only orphans child entities. Nested groups become dangling references |
| `addGroupToGroup` | ⚠️ WEAK | No cycle detection — A→B→A creates infinite recursion in `computeGroupValueAt` |
| `computeValueAt` | ⚠️ DESIGN | Only applies hierarchical addition for X/Y — rotation, scale, alpha on groups are silently ignored. Should document this or extend |
| `computeGroupValueAt` | 🔴 BUG | Infinite recursion if circular group parent chain exists (see addGroupToGroup above) |
| `copy` | ⚠️ WEAK | Copies EntityGroups but EntityGroup.copy() doesn't deep-copy groupTrack (see above) |

---

### CodeExporter.java — 3 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `export` | ⚠️ DESIGN | Uses simple sequential emission — overlapping animations at different start times won't produce `parallel` blocks. Only `exportWithGroups` does that, but nothing calls it |
| `collectPropertyEvents` | ⚠️ PERF | `times.contains()` is O(n) on ArrayList — fine for small keyframe counts, but should use LinkedHashSet for correctness |
| `collectPropertyEvents` | ⚠️ WEAK | Compares times with `!times.contains(kf.getTimeMs())` using double equality — floating point imprecision could create near-duplicate times |
| `formatNumber` | ✅ OK | Clean number formatting |
| `findEasingAt` | ✅ OK | 0.5ms tolerance is reasonable |
| `exportGroupRecursive` | ⚠️ DESIGN | Only emits comments, no actual animation code for groups |
| `formatEvent` | ✅ OK | |

---

### TimelinePanel.java — 5 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `addKeyframeAtPlayhead` | ⚠️ WEAK | If no track exists for selectedEntity (entity header selected but no track created), silently does nothing. Should auto-create track |
| `deleteSelectedKeyframe` | ✅ OK | Null-safe |
| `handleMouseDragged` | ⚠️ WEAK | Keyframe drag allows negative time briefly (setTimeMs clamps, but the visual position flickers) |
| `handleScroll` | ⚠️ DESIGN | Only handles horizontal scroll via `deltaX` — vertical scrollY field exists but is never used. Vertical track scrolling is broken for many-track projects |
| `findKeyframeAt` | ⚠️ WEAK | Hit detection uses Euclidean distance (good) but the radius (10px) is fixed — at extreme zoom levels keyframes overlap or become impossible to click |
| `selectTrackAt` | ✅ OK | |
| `drawKeyframes` | ✅ OK | Culls off-screen keyframes |
| `computeGridStep` | ✅ OK | Adaptive step sizes |

---

### EntitySelector.java — 2 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `refresh` | ✅ OK | Null-safe |
| `buildGroupItem` | ⚠️ WEAK | Uses emoji "📁" prefix in TreeItem value — later stripped with `replace("📁 ", "")` which is fragile. If user names an entity "📁 foo" it breaks |
| `setupContextMenu` | ⚠️ WEAK | "Delete" menu item has no action handler — clicking Delete does nothing |
| `applyFilter` | ✅ OK | Case-insensitive, recursive |

---

### KeyframeEditor.java — 1 ISSUE
| Function | Verdict | Issue |
|----------|---------|-------|
| `setKeyframe` | ✅ OK | Null-safe |
| `applyChanges` | ⚠️ WEAK | Silently swallows parse errors — user types "abc" and nothing happens, no feedback. Should flash the field red or show a tooltip |
| `btnDelete action` | 🔴 BUG | Sets `currentKeyframe = null` and fires `onKeyframeChanged`, but does NOT actually remove the keyframe from the track. The keyframe persists in the timeline |

---

### AnimationPreview.java — 2 ISSUES
| Function | Verdict | Issue |
|----------|---------|-------|
| `render` | ✅ OK | Null-safe scene check |
| `drawGrid` | ⚠️ PERF | Draws every grid line even when zoomed way out — can produce thousands of lines. The `step < 8` guard helps but could still be slow at zoom=0.1 |
| `fitToContent` | ⚠️ WEAK | Uses `Double.MIN_VALUE` for maxX/maxY — this is the smallest positive double (~5e-324), not negative infinity. An entity at x=-100 would not update maxX. Should use `-Double.MAX_VALUE` |
| `setupMouseControls` | ⚠️ DESIGN | Uses array hack `boolean[] panning = {false}` for lambda capture — works but an inner class or AtomicBoolean would be cleaner |

---

### CodePreviewPane.java — SOLID
| Function | Verdict | Issue |
|----------|---------|-------|
| All functions | ✅ OK | Simple, no edge cases |

**No fixes needed.**

---

### PuppeteerWindow.java — 2 ISSUES  
| Function | Verdict | Issue |
|----------|---------|-------|
| `updatePreview` | ⚠️ DESIGN | Doesn't handle camera properties (CAMERA_X, CAMERA_Y, CAMERA_ZOOM) — keyframes for these exist in the model but are never applied to the preview camera |
| `setEntityAlpha` | ⚠️ WEAK | Only handles Sprite2D, Label2D, Panel2D — any other Entity2D subclass silently ignores alpha changes |
| `close` | ✅ OK | Stops timer |

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
| **Real-time property sliders** (drag bars to adjust x/y/rot/scale live) | ❌ Missing — we only have numeric text fields | 🔴 HIGH |
| **Right-click to reset** a property to default | ❌ Missing | 🟡 MEDIUM |
| **Spline/curved motion paths** (non-linear spatial movement) | ❌ Missing — we only do linear interpolation between keyframes | 🔴 HIGH |
| **Loop keyframes** (repeat a segment) | ❌ Missing | 🟡 MEDIUM |
| **Image Viewer** (browse/filter/add images to scene from asset library) | ❌ Missing — we rely on pre-loaded scene entities | 🟡 MEDIUM |
| **Sound Viewer** (browse/preview/add audio cues to timeline) | ❌ Missing | 🟡 MEDIUM |
| **Show/Hide with transition** (fade in/out with configurable transition) | ❌ Missing — we have alpha but no transition presets | 🟡 MEDIUM |
| **Camera drag icon** (drag camera position visually on the preview) | ❌ Missing — we have pan but not a dedicated camera position handle | 🟢 LOW |
| **Keyboard camera controls** (HJKL/WASD for camera position) | ❌ Missing | 🟢 LOW |
| **Depth of field / blur simulation** | ❌ Missing (JVN doesn't have blur yet) | 🟢 LOW |
| **Matrix transforms** (3D rotation, skew, color matrix) | ❌ Missing | 🟢 LOW |
| **Custom property registration** (user adds arbitrary properties to editor) | ❌ Missing | 🟡 MEDIUM |
| **Window hide option** during animation playback | ❌ Missing | 🟢 LOW |
| **Skip animation option** in generated code | ❌ Missing | 🟡 MEDIUM |
| **Clipboard includes diff from open state** (incremental code) | ❌ Missing — we export full timeline | 🟡 MEDIUM |
| **Property groups** (e.g. "crop" = cropX + cropY + cropW + cropH as one) | ❌ Missing | 🟢 LOW |
| **Exclusive properties** (e.g. tile vs pan — can't use both) | ❌ Missing | 🟢 LOW |

### What We Have That Ren'Py DOESN'T

| Our Feature | Ren'Py Equivalent |
|-------------|-------------------|
| **Hierarchical entity groups** (parent-child animation) | ❌ Ren'Py has no nested group animation |
| **Visual keyframe timeline** with diamond markers | ❌ Ren'Py uses bar sliders, not a timeline |
| **Keyframe drag repositioning** | ❌ Ren'Py doesn't have draggable keyframes |
| **Multiple easing curves per segment** | Partial — Ren'Py uses warpers |
| **Live code preview pane** updating in real-time | ❌ Ren'Py only shows clipboard output |

---

## Part 4: Recommended Expansion Roadmap

### Phase 1 — Hardening (do first)
Apply all P0/P1 fixes from Part 2 above.

### Phase 2 — Parity Features (HIGH priority)
1. **Property Sliders** — Add drag sliders alongside text fields in KeyframeEditor for position, rotation, scale, alpha. Live-update preview as user drags.
2. **Spline Motion Paths** — Add a `MotionPath` class supporting Catmull-Rom or cubic Bezier curves. Draw the path on AnimationPreview as a visual curve. Export as multiple short `move` segments or a custom `path` action.
3. **Visual Property Manipulation** — Click an entity in AnimationPreview to select it, then drag to move, scroll to scale, Shift+drag to rotate. Creates/updates keyframes at playhead automatically.

### Phase 3 — Workflow Features (MEDIUM priority)  
4. **Asset Picker Panel** — Browse project images/sprites, drag onto preview to add entity + track.
5. **Audio Track** — Add a waveform track to timeline, attach `playAudio`/`stopAudio` actions at specific times.
6. **Animation Presets** — Built-in templates: "Bounce In", "Fade In", "Shake", "Slide From Left", etc. One-click apply to selected entity.
7. **Loop Markers** — Define a loop region on the timeline (start/end markers). Playback loops that segment. Export with loop syntax.
8. **Incremental Export** — Track the initial state when Puppeteer opens, export only the delta (like Ren'Py does). More useful for inserting into existing scripts.

### Phase 4 — Polish (NICE-TO-HAVE)
9. **Onion Skinning** — Ghost images showing entity position at adjacent keyframes.
10. **Multi-select keyframes** — Shift+click, box select, move/delete batch.
11. **Undo/Redo** — Command stack for all Puppeteer operations.
12. **Custom Properties** — Let users register arbitrary numeric properties that map to JES props.
13. **Curve Editor** — Visual Bezier handle editor for easing curves (like After Effects graph editor).
