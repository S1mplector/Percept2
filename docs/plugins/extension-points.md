# Extension-point reference

All registries use a unique string ID and return a `Registration` handle. The host also tracks ownership and removes every registration when a plugin fails or stops.

## Script commands

`ScriptCommand` receives the source language, registered command ID, parsed arguments, current variables, and project directory. It returns whether it handled the call, an optional value, and variable updates.

```vns
[plugin studio.inventory.grant key 1]
```

Handlers execute on the calling script/runtime thread. Keep them short; schedule expensive work yourself rather than blocking rendering.

## Editor tools

`EditorTool` contributes an action under **Tools → Plugins**. It receives the current project directory and a small services map. The editor currently supplies `window` when an owner window exists.

Plugins may choose JavaFX for editor-only UI, but should isolate that code from runtime classes. Do not cast or reflect into `EditorApp`.

## Asset importers

`AssetImporter` declares whether it supports a source path and transforms an `AssetImportRequest` into an `AssetImportResult`. Results list generated paths and diagnostics explicitly.

Importers should reject unsupported formats cheaply, write only under the supplied destination, avoid implicit overwrites, return actionable diagnostics, and clean partial output after failure.

The registry is available now; editor importer selection will adopt it incrementally as built-in import workflows are migrated.

## Runtime listeners

`RuntimeListener` observes runtime start, project open, and runtime stopping. Listener failures are isolated and recorded as warnings; delivery continues to other plugins.
