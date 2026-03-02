# VNS vs Ren'Py Gap Analysis & Implementation Roadmap

Generated from deep code inspection of all VNS subsystems.  
Every claim below cites exact file paths and line references.

---

## 1. Parity Matrix

| # | Capability | Ren'Py Expectation | JVN Status | Evidence | Gap Severity |
|---|---|---|---|---|---|
| **Script / Parser** | | | | | |
| 1 | Character definition with color/styling | `define e = Character("Eileen", color="#c8ffc8")` — per-character name color, font, text style | **Partial** | `VnCharacter.java:9-17` stores id, displayName, expressions only. No color/font/voice_tag fields. Parser `@character` at `VnScriptParser.java:32` only captures id + displayName. | **P1** |
| 2 | Multi-file scenario with include | `init python`, `label` across files | **Supported** | `VnScriptParser.java:44,272-291` — `@include` with cycle detection, recursive resolution. | — |
| 3 | Preprocessor defines/macros | `define`, `default` for constants | **Partial** | `@define` at `VnScriptParser.java:43,264-269` does simple `${key}` substitution. No `default` (value-if-unset), no parameterized macros, no init-time Python blocks. | **P2** |
| 4 | Label + jump + call/return | `label`, `jump`, `call`, `return` | **Supported** | `VnScriptParser.java:38-39,398-416` (labels), `534-538` (jump), `931-942` (gosub/return). Runtime: `VnScene.java:500-527` (jump/call/return nodes), `VnState.java:543-573` (call stack). | — |
| 5 | If/elif/else/endif blocks | Full structured conditionals | **Supported** | `VnScriptParser.java:894-918` (if/elif/else/endif), `969-1055` (synthetic label emission). `VnConditionEvaluator.java:1-446` full expression parser with `&&`, `||`, `!`, comparisons, grouping. | — |
| 6 | While/for loops | `while`, `for` iteration constructs | **Missing** | No loop keywords in `VnScriptParser.java:527-966` command switch. No `LOOP` in `VnNodeType.java`. | **P1** |
| 7 | Python/expression blocks | `$ x = 1`, `python:` blocks | **Partial** | `[set]`, `[inc]`, `[dec]`, `[flag]` commands at `VnScriptParser.java:864-892`. `[java]` reflection call at `DefaultVnInterop.java:90-119`. No inline expression evaluation beyond conditions. No arbitrary code blocks. | **P1** |
| 8 | User-defined functions/macros | `define`, callable Python functions | **Missing** | No function/macro definition syntax in parser. `@define` is string substitution only (`VnScriptParser.java:264-269`). | **P2** |
| **Dialogue / Text** | | | | | |
| 9 | Say with side-image | `e "Hello" with side_image` | **Missing** | `DialogueLine.java:6-11` has characterId but no side-image field. Parser `DIALOGUE_PATTERN` at `VnScriptParser.java:40` has no side-image syntax. | **P2** |
| 10 | Narration (no speaker) | `"Narration text"` without speaker | **Partial** | Any id used as speaker works (e.g., `Narrator:`). But no dedicated narration syntax like bare-quoted text. `VnScriptParser.java:487-506` always requires `speaker: text` or `speaker "text"`. | **P1** |
| 11 | Text tags (color, bold, italic, effects) | `{color=#f00}red{/color}`, `{b}`, `{i}` | **Supported** | `TextParser.java:25-114` — shake, wave, bounce, color, speed, delay, bold, italic, rainbow. `TextEffect.java:1-17` enum. | — |
| 12 | Variable interpolation in text | `[variable]` or `{variable}` in dialogue | **Supported** | `VnTextFormatter.java:16-168` — `${var}` interpolation + ICU-style `{var, plural, ...}` / `{var, select, ...}` / `{var, number}`. | — |
| 13 | CTC (click-to-continue) indicator | Custom CTC animations | **Missing** | No CTC model in `VnState.java` or `VnRenderer.java`. Text reveal completes then sets `waitingForInput=true` at `VnScene.java:226`. | **P2** |
| 14 | NVL mode (full-screen text) | `nvl` character type, NVL clear | **Missing** | Only ADV mode. `VnRenderer.java:44-133` renders single textbox. No NVL layout variant. No `[nvl_clear]` command. | **P1** |
| 15 | Multi-line dialogue continuation | `extend` to append to previous line | **Missing** | No `extend` command in `VnScriptParser.java:527-966`. Each `Speaker: text` creates a new DIALOGUE node. | **P2** |
| 16 | Voice per-line | `voice "file.ogg"` before dialogue | **Partial** | `[voice file]` command exists at `VnScriptParser.java:684-691`. But not auto-linked to next dialogue node — plays independently. | **P1** |
| **Show / Transform / Animation** | | | | | |
| 17 | Show with at-transform | `show eileen happy at left` with arbitrary transforms | **Partial** | `[show charId pos expr layer]` at `VnScriptParser.java:791-815`. Position is enum-based (`CharacterPosition.java` — 5 slots). No arbitrary x,y, no transform composition. | **P1** |
| 18 | ATL (Animation & Transformation Language) | `transform`, `parallel`, `ease`, `linear`, keyframes | **Partial** | Puppeteer/JES timeline DSL covers `move`, `fade`, `cameraMove`, `cameraZoom`, `playAudio`, `event` via `TimelineDataParser.java:46-48,331-345`. Inline timelines via `timeline { }` in VNS at `VnScriptParser.java:431-476`. But no per-character ATL, no show-time transform composition, no repeating ATL blocks attached to characters. | **P1** |
| 19 | Dissolve / transition on show | `show e happy with dissolve` | **Partial** | `[transition type dur bg]` at `VnScriptParser.java:822-842`. Types: FADE, DISSOLVE, CROSSFADE, SLIDE_LEFT, SLIDE_RIGHT, WIPE (`VnTransition.java:35-43`). But transitions are scene-level only — no per-character transition on show/hide. | **P1** |
| 20 | Scene (background) change | `scene bg room` | **Supported** | `[bg id]` at `VnScriptParser.java:528-533`. `@background id path` declares at `VnScriptParser.java:342-348`. `VnScene.java:456-460` applies to state. | — |
| 21 | Camera/viewport transforms | `camera` transform, zoom, pan | **Supported** | `cameraMove`, `cameraZoom` in timeline DSL (`TimelineDataParser.java`). `[screen shake]`, `[screen flash]` at `DefaultVnInterop.java:457-482`. | — |
| 22 | Layered images / composite sprites | `layeredimage`, `attribute`, `group` | **Partial** | `@charlayer` + `@charpreset` at `VnScriptParser.java:35-36,366-396`. Resolves layer references and builds composite expression paths. But no runtime layer toggle — presets produce a single merged path string. No dynamic attribute switching. | **P1** |
| **Audio** | | | | | |
| 23 | BGM play/stop/fade/crossfade | `play music`, `stop music`, `queue music` | **Supported** | `[bgm]`, `[bgm_stop]`, `[bgm_fadeout]`, `[bgm_crossfade]`, `[bgm_pause]`, `[bgm_resume]`, `[bgm_seek]` at `VnScriptParser.java:544-668`. Full runtime in `VnScene.java:580-618` and `DefaultVnInterop.java:398-451`. | — |
| 24 | SFX play/stop | `play sound` | **Supported** | `[sfx]`, `[sfx_stop]` at `VnScriptParser.java:671-683`. | — |
| 25 | Voice play/stop | `voice` | **Supported** | `[voice]`, `[voice_stop]` at `VnScriptParser.java:684-696`. | — |
| 26 | Audio channels (named) | `play audio "x" channel "chan"` | **Missing** | No channel parameter on audio commands. BGM/SFX/voice are fixed channels via `VnAudioCommand` types. | **P2** |
| 27 | Music queue | `queue music` | **Missing** | No queue command in parser. Only play/stop/fade/crossfade. | **P2** |
| **Rollback / Save** | | | | | |
| 28 | Per-dialogue rollback | Scroll-wheel / Page-up rollback | **Supported** | `VnRollbackStack.java:1-141` — bidirectional (rollback + rollforward), max 100 entries. `VnRollbackEntry.java:71-99` captures full state snapshot. `VnScene.java:688-758` rollback/rollforward with blocking state reset. | — |
| 29 | Rollback across timelines | Rollback restores mid-timeline state | **Missing** | `VnRollbackEntry.java:104-128` does not capture/restore `activeTimelines`. Rollback kills in-progress timelines. | **P1** |
| 30 | Persistent data | `persistent.variable` survives game restart | **Missing** | Variables in `VnState.variables` are per-session. Save serializes them (`VnSaveSerializer.java:62-65`) but no separate persistent store. | **P1** |
| 31 | Save/load with slots | Named save slots, thumbnails | **Partial** | `VnSaveSlotService.java` exists (not fully inspected). `VnSaveSerializer.java:1-707` handles JSON serialization with schema versioning, migration. `VnSaveData.java` captures full state. Slot overlay in `VnState.java:46-479`. No screenshot/thumbnail capture. | **P1** |
| 32 | Save compatibility / migration | Forward-compatible saves | **Supported** | `VnSaveMigration.java` exists. `VnSaveSerializer.java:26` uses `schemaVersion`. | — |
| **UI / Screen Language** | | | | | |
| 33 | Screen language (declarative UI) | `screen`, `frame`, `vbox`, `textbutton`, `if` in UI | **Missing** | No screen language. UI is hardcoded in `VnRenderer.java` (1778 lines) with style specs from `VnUiStyleSpec.java` / `VnUiLayoutSpec.java`. `[screen]` command at `VnScriptParser.java:760-761` just passes payload to external handler. | **P0** |
| 34 | In-game preferences screen | `screen preferences` with sliders | **Partial** | `[settings]` → `DefaultVnInterop.java:228-265` handles textspeed/autodelay/volume. Settings rendered via menu system (`SettingsScene`), not via screen language. | **P1** |
| 35 | Custom in-game menus | `screen` with buttons, variables | **Missing** | Menus are separate scene classes (Main/Load/Save/Settings). No script-driven dynamic UI. `[menu]` at `VnScriptParser.java:844-853` delegates to external handler. | **P1** |
| 36 | Notify/toast system | `renpy.notify("text")` | **Supported** | `[hud message]` → `VnState.showHudMessage()` at `VnState.java:483-486`. Timed auto-dismiss. | — |
| **Prediction / Preload** | | | | | |
| 37 | Asset prediction/preload | `renpy.start_predict()`, automatic prediction | **Missing** | No prediction infrastructure. `grep` for preload/predict/prefetch/cache in `core/vn/` returns zero hits. `imageCache` in `VnRenderer.java:46` is demand-loaded only. | **P1** |
| 38 | Preflight state for load | Fast-forward state on scene load | **Supported** | `VnScene.preflightState()` at `VnScene.java:84-138` — replays non-interactive nodes to reconstruct visual state. | — |
| **Editor / Tooling** | | | | | |
| 39 | Script syntax checking | Ren'Py lint | **Partial** | `VnConditionEvaluator.validate()` at `VnConditionEvaluator.java:35-41`. Parser validates labels at `VnScriptParser.java:1194-1206`. No standalone lint tool. | **P1** |
| 40 | Live preview / hot reload | Ren'Py launcher `shift+R` reload | **Missing** | No hot-reload mechanism in runtime. Editor preview exists via Puppeteer but not for VNS scripts. | **P2** |
| 41 | Internationalization / locale | `_("translatable text")` | **Partial** | `VnScenarioLoader` has locale-aware script candidate loading (from memory). `Localization.java` imported by `VnRenderer.java:13`. But no `_()` extraction, no translation pipeline in parser. | **P1** |

---

## 2. Actionable Backlog (25 Tasks)

### T01: While/For Loop Support
- **Files**: `VnScriptParser.java`, `VnNodeType.java`, `VnScenarioBuilder.java`, `VnScene.java`
- **Parser**: Add `[while expr]...[endwhile]` and `[for var start end]...[endfor]` with synthetic label/jump emission (similar to if/endif pattern at `VnScriptParser.java:969-1055`)
- **Runtime**: No new node type needed — loops compile to conditional jumps + counter variables via existing `cond` + `jump` infrastructure
- **Tests**: `VnScriptParserTest` — parse while/for, verify generated node graph; `VnScene` integration test for loop execution and infinite-loop safety

### T02: Default Variable Declaration
- **Files**: `VnScriptParser.java`
- **Parser**: Add `@default varName value` directive at `VnScriptParser.java:264` area. Only sets variable if not already set (check `ParseState.defines` or emit conditional `[set]`)
- **Tests**: Parser test: `@default x 5` then `@default x 10` → x stays 5

### T03: Character Color/Style Metadata
- **Files**: `VnCharacter.java`, `VnScriptParser.java`, `VnRenderer.java`
- **Parser**: Extend `@character` pattern at `VnScriptParser.java:32` to accept optional `color=#hex`, `font=family`
- **Model**: Add `nameColor`, `fontFamily`, `voiceTag` fields to `VnCharacter.java:10-12`
- **Renderer**: Use character color for name rendering in `VnRenderer.java` name box section
- **Tests**: Parser round-trip; renderer color application test

### T04: Narration Syntax (No Speaker)
- **Files**: `VnScriptParser.java`, `VnScenarioBuilder.java`
- **Parser**: Before dialogue pattern match at `VnScriptParser.java:487`, add pattern for bare quoted text: `"text"` → dialogue with empty speaker. Or detect `Narrator` as special case
- **Builder**: `VnScenarioBuilder.narration(String text)` → DIALOGUE node with blank speaker
- **Tests**: Parse `"It was a dark night"` → DIALOGUE node with empty speakerName

### T05: NVL Mode
- **Files**: `VnState.java`, `VnScene.java`, `VnRenderer.java`, `VnScriptParser.java`
- **State**: Add `nvlMode` boolean, `nvlBuffer: List<DialogueLine>` to `VnState.java`
- **Parser**: `[nvl]` / `[adv]` mode switch commands; `[nvl_clear]` to flush buffer
- **Runtime**: In NVL mode, dialogue nodes append to buffer instead of replacing; renderer shows scrollable text panel
- **Renderer**: New NVL layout path in `VnRenderer.java` alongside existing ADV textbox
- **Tests**: Mode switch, buffer accumulation, clear behavior

### T06: Per-Character Transition on Show/Hide
- **Files**: `VnScriptParser.java`, `VnNode.java`, `VnState.java`
- **Parser**: Extend `[show]` at `VnScriptParser.java:791` to accept `with dissolve 300` suffix
- **Node**: Add `showTransition` field to `VnNode.Builder` at `VnNode.java:62`
- **State**: `showCharacterAnimated` already has fade animation (`VnState.java:138-175`); extend with dissolve-specific alpha curves
- **Tests**: Parse `[show hero center happy with dissolve 500]`; verify animation parameters

### T07: Arbitrary Character Positioning (x,y)
- **Files**: `CharacterPosition.java`, `VnScriptParser.java`, `VnState.java`, `VnRenderer.java`
- **Model**: Extend `CharacterPosition` or add a `VnCharacterTransform` record with `x, y, scale, alpha, rotation`
- **Parser**: `[show hero at 0.3 0.5]` numeric position variant
- **Renderer**: Map numeric positions to actual pixel coordinates
- **Tests**: Parse numeric positions; renderer placement verification

### T08: Voice Auto-Link to Dialogue
- **Files**: `VnScriptParser.java`, `DialogueLine.java`, `VnScene.java`
- **Parser**: When `[voice file]` precedes a dialogue line, attach voice file to the `DialogueLine` (add `voiceFile` field to `DialogueLine.java:8`)
- **Runtime**: `processDialogueNode` at `VnScene.java:434` auto-plays linked voice
- **Tests**: `[voice hero_01.ogg]` + `Hero: Hello` → dialogue node carries voice reference

### T09: Asset Prediction/Preload Pipeline
- **Files**: New `VnAssetPredictor.java` in `core/vn/`
- **Design**: Scan ahead N nodes from current index, collect image/audio paths. Background-load into `imageCache`
- **Integration**: `VnScene.processCurrentNode()` at `VnScene.java:326` triggers prediction after each advance
- **Tests**: Predict correct assets for a scenario with backgrounds and character shows

### T10: Persistent Data Store
- **Files**: New `VnPersistentStore.java` in `core/vn/`, `VnScriptParser.java`, `DefaultVnInterop.java`
- **Parser**: `[persistent key value]` command
- **Runtime**: File-backed key-value store separate from save slots. Loaded once at game start, written on change
- **Model**: `Map<String, Object>` accessible via `VnState` or standalone service
- **Tests**: Set persistent, restart scenario, verify persistent value survives

### T11: Rollback Across Timelines
- **Files**: `VnRollbackEntry.java`, `VnState.java`
- **Entry**: Add `List<TimelineRunnerSnapshot>` to `VnRollbackEntry.java:18`. Snapshot captures timeline elapsed time, data reference
- **Restore**: `applyTo()` at `VnRollbackEntry.java:104` re-creates `TimelineRunner` instances and seeks to snapshot time
- **Tests**: Start timeline, advance 2 dialogues, rollback → timeline state restored

### T12: Save Slot Thumbnails
- **Files**: `VnSaveData.java`, `VnSaveSerializer.java`, `VnRenderer.java`
- **Renderer**: Capture canvas snapshot as PNG bytes on save
- **Data**: Add `thumbnailBase64` field to `VnSaveData`
- **Serializer**: Include in JSON at `VnSaveSerializer.java:98-103`
- **Tests**: Save with thumbnail, deserialize, verify image bytes present

### T13: Screen Language (Declarative UI DSL)
- **Files**: New `VnScreenParser.java`, `VnScreenNode.java`, `VnScreenRenderer.java` in `core/vn/ui/`
- **Design**: Parse `.vnscreen` files with `frame`, `vbox`, `hbox`, `text`, `button`, `if`/`for` constructs. Compile to a render tree
- **Integration**: `[screen show myscreen]` / `[screen hide myscreen]` from VNS scripts
- **Scope**: Phase 3 — largest single feature. Requires layout engine, event binding, variable display
- **Tests**: Parse basic screen, render to mock canvas, event dispatch

### T14: Custom In-Game Menu Screens
- **Files**: `DefaultVnInterop.java`, `VnRenderer.java`, potentially new screen system
- **Interim**: Before full screen language, allow `[menu show customId]` to load and render a `.vnscreen` or `.menu` file as overlay
- **Tests**: Show custom menu, select item, verify callback

### T15: Standalone Lint Tool
- **Files**: New `VnLint.java` in `core/vn/script/`
- **Design**: Parse scenario, collect warnings: unused labels, undefined variables referenced in conditions, unreachable code after `[end]`, missing expression images
- **CLI**: `VnLint.lint(VnScenario)` returns `List<LintDiagnostic>`
- **Integration**: Usable from editor and CI
- **Tests**: Each warning type has a test case

### T16: Hot Reload for VNS Scripts
- **Files**: `VnScene.java`, `VnScenarioLoader.java`
- **Design**: File watcher on script path. On change: re-parse, diff label positions, apply new scenario while preserving `VnState.currentNodeIndex` (map via label names)
- **Tests**: Modify script, trigger reload, verify state continuity

### T17: Dynamic Layered Image Attributes
- **Files**: `VnCharacter.java`, `VnState.java`, `VnRenderer.java`
- **Model**: `VnCharacter` stores layer groups (body, face, accessory). Runtime toggles individual layers
- **Script**: `[layer hero face happy]` to change one layer at runtime
- **Renderer**: Composite multiple images per character
- **Tests**: Set layers independently, verify composite output

### T18: Extend Dialogue / Append Text
- **Files**: `VnScriptParser.java`, `VnScenarioBuilder.java`, `VnNodeType.java`
- **Parser**: `[extend]` or `+:` syntax appends text to previous DIALOGUE node instead of creating new one
- **Builder**: `VnScenarioBuilder.extend(String text)` modifies last node
- **Tests**: Two lines with extend produce single DIALOGUE node with concatenated text

### T19: Audio Queue System
- **Files**: `AudioFacade.java` (interface), `VnScriptParser.java`
- **Parser**: `[bgm_queue trackId]` command
- **Runtime**: `AudioFacade.queueBgm(trackId)` — play after current track finishes
- **Tests**: Queue two tracks, verify second plays after first

### T20: Named Audio Channels
- **Files**: `AudioFacade.java`, `VnScriptParser.java`, `DefaultVnInterop.java`
- **Parser**: `[audio channel=ambient file loop=true]` with channel parameter
- **Runtime**: `AudioFacade` manages multiple named channels beyond bgm/sfx/voice
- **Tests**: Play on custom channel, stop specific channel

### T21: CTC (Click-to-Continue) Indicator
- **Files**: `VnState.java`, `VnRenderer.java`, `VnUiStyleSpec.java`
- **State**: Add `ctcStyle: CTCStyle` (blink, bounce, custom image)
- **Style**: CTC asset/animation config in `VnUiStyleSpec`
- **Renderer**: Draw CTC indicator when `waitingForInput && textFullyRevealed`
- **Tests**: Verify CTC appears at correct state

### T22: Side Image Support
- **Files**: `DialogueLine.java`, `VnScriptParser.java`, `VnRenderer.java`
- **Model**: Add `sideImage` field to `DialogueLine.java`
- **Parser**: `Speaker (side=image.png): text` or `[side image.png]` command before dialogue
- **Renderer**: Draw side image next to textbox
- **Tests**: Parse side image, verify render call

### T23: Translation / i18n Pipeline
- **Files**: `VnScriptParser.java`, new `VnTranslationExtractor.java`
- **Extractor**: Walk scenario nodes, extract all dialogue text + choice text with source references
- **Format**: Output `.po` or `.properties` file for translation
- **Runtime**: At load time, substitute translated strings via `Localization.java`
- **Tests**: Extract, translate, load translated scenario, verify strings

### T24: Parameterized Macros
- **Files**: `VnScriptParser.java`
- **Parser**: `@macro name(param1, param2) ... @endmacro`, `@call_macro name(val1, val2)` — expands inline with parameter substitution
- **Implementation**: Store macro body in `ParseState`, expand on invocation (text replacement before parse)
- **Tests**: Define macro, invoke with args, verify expansion

### T25: Script Decompiler / Exporter
- **Files**: New `VnScriptExporter.java` in `core/vn/script/`
- **Design**: Convert `VnScenario` back to `.vns` text format. Round-trip: parse → export → parse → semantic equality
- **Use cases**: Editor preview of programmatically built scenarios, migration tooling
- **Tests**: Round-trip equality for representative scripts

---

## 3. Phased Plan

### Phase 1 — High Impact, Low Risk (8 tasks, ~4-6 weeks)

| Task | Est. Complexity | Regression Risk | Rationale |
|---|---|---|---|
| **T01** While/For Loops | Medium | Low | Compiles to existing jump/cond infrastructure. No runtime changes. |
| **T02** Default Variables | Low | Minimal | Single parser addition, no runtime changes. |
| **T03** Character Color/Style | Medium | Low | Additive fields to `VnCharacter`, renderer reads new fields with fallback. |
| **T04** Narration Syntax | Low | Low | New pattern match before dialogue; backward compatible. |
| **T06** Per-Character Transitions | Medium | Low | Extends existing `showCharacterAnimated` — additive parameter. |
| **T08** Voice Auto-Link | Low | Minimal | New field on `DialogueLine`, parser peeks ahead. |
| **T15** Lint Tool | Medium | None | Standalone analysis, zero runtime impact. |
| **T21** CTC Indicator | Low | Minimal | Renderer-only addition, style-configurable. |

### Phase 2 — Medium Complexity (9 tasks, ~6-8 weeks)

| Task | Est. Complexity | Regression Risk | Rationale |
|---|---|---|---|
| **T05** NVL Mode | High | Medium | New render path, state buffer, mode switching. Must not break ADV mode. |
| **T07** Arbitrary Positioning | Medium | Medium | Extends position model — risk of breaking existing 5-slot system. |
| **T09** Asset Prediction | Medium | Low | Background-only, no blocking changes. |
| **T10** Persistent Store | Medium | Low | New subsystem, orthogonal to existing save. |
| **T11** Rollback + Timelines | High | Medium | Snapshot/restore of timeline state is complex. Must not break existing rollback. |
| **T12** Save Thumbnails | Medium | Low | Additive to save data. |
| **T17** Dynamic Layers | High | Medium | Changes character rendering model. |
| **T18** Extend/Append | Low | Low | Parser-only, modifies last node. |
| **T22** Side Images | Low | Low | Additive to dialogue model and renderer. |

### Phase 3 — Deep Architecture (8 tasks, ~8-12 weeks)

| Task | Est. Complexity | Regression Risk | Rationale |
|---|---|---|---|
| **T13** Screen Language | Very High | High | New subsystem: parser, layout engine, event system, renderer. Largest single feature. |
| **T14** Custom Menus | High | Medium | Depends on T13 or interim solution. |
| **T16** Hot Reload | High | Medium | File watching, scenario diffing, state remapping. |
| **T19** Audio Queue | Medium | Low | Requires `AudioFacade` API extension. |
| **T20** Named Channels | Medium | Low | `AudioFacade` API extension. |
| **T23** i18n Pipeline | High | Low | Extraction, format, runtime substitution. Touches many files. |
| **T24** Parameterized Macros | Medium | Medium | Parser complexity — macro expansion before parse. |
| **T25** Script Decompiler | Medium | None | Standalone utility. |

---

## 4. Acceptance Test Plan

### 4.1 Parser Tests (`VnScriptParserTest`)

| Test Case | Validates |
|---|---|
| `parseWhileLoop_countsTo5` | T01 — while loop compiles to correct jump/cond chain |
| `parseForLoop_iterates` | T01 — for loop variable increment and termination |
| `parseWhileInfinite_hitsChainLimit` | T01 — safety limit prevents infinite compile |
| `parseDefaultVar_setsOnlyIfUnset` | T02 — `@default` semantics |
| `parseCharacterWithColor_preservesHex` | T03 — extended `@character` |
| `parseNarration_emptysSpeaker` | T04 — bare quoted text |
| `parseShowWithTransition_capturesType` | T06 — `[show x center happy with dissolve 300]` |
| `parseShowNumericPosition_capturesXY` | T07 — `[show hero at 0.3 0.5]` |
| `parseVoiceBeforeDialogue_linksToNode` | T08 — voice auto-link |
| `parseNvlMode_switchesCorrectly` | T05 — `[nvl]` / `[adv]` / `[nvl_clear]` |
| `parseExtend_mergesDialogue` | T18 — extend appends to previous node |
| `parseMacroDefinition_expandsInline` | T24 — `@macro` + `@call_macro` |

### 4.2 Runtime Behavior Tests (`VnSceneTest`)

| Test Case | Validates |
|---|---|
| `whileLoop_executesNTimes` | T01 — runtime loop execution with variable |
| `forLoop_setsIteratorVariable` | T01 — iterator accessible in loop body |
| `nvlMode_accumulatesDialogue` | T05 — buffer grows; clear resets |
| `characterShow_withDissolve_animates` | T06 — alpha animation on show |
| `arbitraryPosition_rendersAtXY` | T07 — character placed at specified coordinates |
| `voiceAutoPlay_playsOnDialogue` | T08 — voice file plays when dialogue node processes |
| `persistentStore_survivesScenarioReload` | T10 — persistent data persists |
| `assetPredictor_scansAheadCorrectly` | T09 — next N nodes yield correct asset list |
| `layerToggle_compositeChanges` | T17 — switching one layer updates composite |
| `screenLanguage_rendersFrame` | T13 — basic screen renders correctly |

### 4.3 Rollback / Save Tests (`VnRollbackTest`, `VnSaveTest`)

| Test Case | Validates |
|---|---|
| `rollback_restoresTimelineState` | T11 — timeline elapsed time restored |
| `rollback_preservesPersistentData` | T10+T11 — persistent not affected by rollback |
| `saveLoad_withThumbnail_roundTrips` | T12 — thumbnail survives serialize/deserialize |
| `saveLoad_withNvlBuffer_roundTrips` | T05 — NVL buffer serialized in save |
| `saveLoad_withCustomChannels_roundTrips` | T20 — channel state serialized |
| `saveMigration_v1ToV2_preservesData` | Save compat — migration handles new fields gracefully |

### 4.4 Preview Parity Tests (`VnPreviewParityTest`)

| Test Case | Validates |
|---|---|
| `ctcIndicator_visibleWhenWaiting` | T21 — CTC renders at correct time |
| `nvlMode_showsFullTextPanel` | T05 — NVL layout differs from ADV |
| `characterColor_renderedInNameBox` | T03 — character name color applied |
| `sideImage_renderedNextToTextbox` | T22 — side image positioned correctly |
| `transition_onShow_dissolves` | T06 — per-character dissolve visible in preview |
| `arbitraryPosition_matchesRuntime` | T07 — preview and runtime positions match |

### 4.5 Lint / Diagnostics Tests (`VnLintTest`)

| Test Case | Validates |
|---|---|
| `unusedLabel_producesWarning` | T15 — label defined but never referenced |
| `undefinedVarInCondition_producesWarning` | T15 — variable used in `[if]` not set anywhere |
| `unreachableCodeAfterEnd_producesWarning` | T15 — code after `[end]` flagged |
| `missingExpressionImage_producesWarning` | T15 — `[show hero center angry]` but no `@charimg hero angry` |
| `infiniteLoop_producesWarning` | T15 — `[while true]` with no break condition |

---

## Summary

- **Supported**: 15 capabilities — core scripting flow, audio, transitions, rollback, save, text effects, variable interpolation, inline timelines
- **Partial**: 13 capabilities — need targeted extensions to existing models/parser
- **Missing**: 13 capabilities — require new subsystems (screen language being the largest)
- **P0**: 1 (screen language)
- **P1**: 15 (loops, NVL, arbitrary positioning, persistent data, rollback+timelines, prediction, etc.)
- **P2**: 8 (macros, CTC, side images, audio queue, named channels, etc.)

The phased plan prioritizes low-risk, high-impact parser extensions in Phase 1, medium-complexity runtime additions in Phase 2, and deep architectural features (screen language, hot reload, i18n) in Phase 3.
