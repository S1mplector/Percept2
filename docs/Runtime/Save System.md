# Save System

JVN save/load is versioned, migration-aware, and uses failure-safe write semantics.

Core classes:
- data model: `core/src/main/java/com/jvn/core/vn/save/VnSaveData.java`
- migration: `core/src/main/java/com/jvn/core/vn/save/VnSaveMigration.java`
- manager: `core/src/main/java/com/jvn/core/vn/save/VnSaveManager.java`

## Save Payload Model

`VnSaveData` stores:
- schema version
- scenario id
- current node index
- current background id
- variables map
- read nodes set
- visible character slots
- skip/auto/UI flags
- settings snapshot
- timestamp and slot name
- optional `rpgState` payload

Current schema:
- `CURRENT_SCHEMA_VERSION = 2`

## Migration Layer

`VnSaveMigration.migrateInPlace(...)` handles:
- legacy/default schema normalization
- v1 -> v2 updates
- null/empty field normalization
- save name/timestamp recovery
- schema bump to current

Migration can occur during both save and load paths.

## Write Reliability

`VnSaveManager` writes are failure-safe:

1. serialize to `<slot>.sav.tmp`
2. fsync output
3. move temp -> final with `ATOMIC_MOVE` when supported
4. fallback to replace move if atomic not supported
5. cleanup stale temp files

This reduces risk of corrupted half-written saves.

## Autosave System

Autosave behavior:
- prefix: `_autosave_`
- slot count: `3`
- rotation strategy: overwrite oldest slot (or unreadable slot first)

APIs include:
- `autosave(state)`
- `autosave(state, index)`
- `listAutoSaves()`
- `loadLatestAutoSave()`

## Slot Management

Manager supports:
- list saves
- delete save
- rename save
- apply save data onto current `VnState`

Sidecar thumbnails (`.png`) are cleaned/renamed alongside save slot operations where applicable.

## Menu Integration

Save/load scenes use this manager directly:
- `LoadMenuScene`
- `SaveMenuScene`

Load scene sorts by timestamp (newest first) and can preview metadata.
Save scene supports new slot creation and overwrite flows.

## Default Save Location

Default manager directory:

```text
~/.jvn/saves
```

Custom directory can be provided in `VnSaveManager(String saveDir)`.

## Best Practices for Game Teams

- keep custom `rpgState` serializable and compact
- avoid very high-frequency save calls
- use autosaves strategically (scene boundaries/checkpoints)
- maintain backward compatibility through migration updates when schema changes

## Extending Schema Safely

When adding fields:

1. bump `CURRENT_SCHEMA_VERSION`
2. add migration logic in `VnSaveMigration`
3. keep defaults sensible for missing/old values
4. test load of old save fixtures
