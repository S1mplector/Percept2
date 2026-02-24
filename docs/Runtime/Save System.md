# Save System

JVN save/load is schema-versioned, migration-aware, and write-failure-safe.

Primary classes:
- `core/src/main/java/com/jvn/core/vn/save/VnSaveData.java`
- `core/src/main/java/com/jvn/core/vn/save/VnSaveMigration.java`
- `core/src/main/java/com/jvn/core/vn/save/VnSaveManager.java`
- `core/src/main/java/com/jvn/core/vn/save/VnSaveSerializer.java`

## Storage Format

Save slots are stored as JSON files:

- `<slot>.json`
- temp write path: `<slot>.json.tmp`

Legacy `.sav` files are still readable and are migrated forward to JSON on load.

Default save directory:

```text
~/.jvn/saves
```

You can override it with `new VnSaveManager("/custom/path")`.

## Current Schema

Current schema version:
- `CURRENT_SCHEMA_VERSION = 4`

`VnSaveData` persists:
- identity: `schemaVersion`, `scenarioId`, `saveName`, `saveTimestamp`
- progression: `currentNodeIndex`, `readNodes`
- scene state: `currentBackgroundId`, `visibleCharacters`
- state variables: `variables`
- call/return flow: `callStack`
- character-global-position metadata:
  - `globalPositionCharacters`
  - `characterDefinedPositions`
- UX modes:
  - `skipMode`
  - `autoPlayMode`
  - `autoPlayTimer`
  - `uiHidden`
- settings snapshot:
  - text/audio/autoplay flags
  - physics defaults (`physicsFixedStepMs`, `physicsMaxSubSteps`, `physicsDefaultFriction`)
  - input profile fields (`inputProfilePath`, `inputProfileSerialized`)
- optional RPG payload: `rpgState` (serialized to base64 when serializable)

## Example Save Payload (Abbreviated)

```json
{
  "schemaVersion": 4,
  "scenarioId": "prologue",
  "nodeIndex": 42,
  "backgroundId": "field_day",
  "callStack": [13, 27],
  "globalPositionCharacters": ["codel"],
  "characterDefinedPositions": {
    "codel": "RIGHT"
  },
  "skipMode": false,
  "autoPlayMode": true,
  "autoPlayTimer": 834,
  "uiHidden": false,
  "settings": {
    "textSpeed": 28,
    "bgmVolume": 0.7,
    "clickRevealBeforeAdvance": true
  },
  "rpgStateSerialized": "..."
}
```

## Migration Behavior

`VnSaveMigration.migrateInPlace(...)` handles:
- legacy/null schema normalization
- `v1 -> v2`
- `v2 -> v3`
- `v3 -> v4`
- null collection/object normalization
- save-name and timestamp recovery
- schema bump to current

Migration is run on:
- save write path (normalization before writing)
- load path (normalization after reading)

If load-time migration changes the payload, manager writes migrated JSON back to disk.

## Write Reliability

`VnSaveManager` uses failure-safe writes:

1. prefer native atomic write via `NativeIoBridge` (`simjot_atomic_write`) when native bridge is available
2. otherwise write JSON to `<slot>.json.tmp`
3. move temp -> final with `ATOMIC_MOVE` when available
4. fallback to replace move when atomic move is unsupported
5. cleanup temp residue

This avoids partially-written save files on interruptions/crashes.

Native prerequisites (optional):
- `cmake`
- platform C/C++ toolchain
- build `native-math` with one of:
  - `./native-math/build.sh`
  - `./native-math/build_mac.sh`
  - `./native-math/build_linux.sh`
  - `native-math\build_windows.bat`

## Autosave

Autosave defaults:
- prefix: `_autosave_`
- slots: `3`
- policy: overwrite oldest slot (or unreadable slot first)

Main APIs:
- `autosave(state)`
- `autosave(state, slotIndex)`
- `listAutoSaves()`
- `loadLatestAutoSave()`

## Applying Save Data Back to Runtime

`VnSaveManager.applyToState(...)` restores:
- node index/background/variables/read nodes
- call stack
- visible characters (+ their layer orders)
- global character position model
- skip/auto/ui mode values (including autoplay timer)
- full settings snapshot
- optional RPG state

Important detail:
- global character position metadata is applied after visible characters are restored, so authored character anchors are retained.

## Rollback vs Save/Load

Rollback (`VnRollbackEntry`) and save/load both restore state, but they are for different scopes:

- rollback: short-lived in-session history stack (undo/redo style)
- save/load: persistent disk snapshots across sessions

Both now preserve key continuity fields like call stack and character-global-position metadata.

## Operational Best Practices

- Keep custom `rpgState` compact and serializable.
- Use autosave at chapter/label checkpoints instead of every line.
- Keep save slot naming stable and machine-safe (manager sanitizes names).
- Add migration tests whenever schema is bumped.

## Schema Evolution Checklist

When adding save fields:

1. Update `VnSaveData`.
2. Bump `CURRENT_SCHEMA_VERSION`.
3. Add migration step in `VnSaveMigration`.
4. Update serializer read/write in `VnSaveSerializer`.
5. Update manager apply/save paths if needed.
6. Add/extend tests (`VnSavePersistenceTest`, robustness tests).
