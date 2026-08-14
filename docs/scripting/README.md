# JVN Scripting

JVN has two primary authoring languages: VNS for visual-novel flow and JES for interactive 2D scenes.
This section also covers timelines and data-driven UI configuration.

## Choose A Language

| Need | Start Here |
|---|---|
| Dialogue, choices, story flow, and character staging | [VNS Overview](vns/overview/vns-scripting.md) |
| Entities, components, physics, input, and gameplay scenes | [JES Overview](jes/overview/jes-scripting.md) |
| Animation timelines and story maps | [Timeline Scripting](timeline/overview/timeline-scripting.md) |
| Menus, dialogue layout, and composable reactive UI | [UI By Example](../guides/ui-by-example.md) |

## Language Contract

The [JVN Scripting Language Contract](spec/README.md) is the normative entrypoint for portable VNS
and JES syntax, compatibility, deprecation, and diagnostics. Guides teach usage; parser-internals
pages explain implementation; neither overrides the contract.

Language Contract 1 is currently provisional while parser, runtime, editor, and VS Code behavior are
reconciled through shared fixtures. See the [Contract 1 status](spec/v1/README.md).

## Learn

- [VNS By Example](../guides/vns-by-example.md)
- [JES By Example](../guides/jes-by-example.md)
- [UI By Example](../guides/ui-by-example.md)
- [Integration Cookbook](../guides/integration-cookbook.md)
- [VNS And JES Architecture](vns/integration/vns-jes-architecture.md)

## Reference Areas

- `vns/` — VNS language, presentation, flow, runtime, integration, and guides
- `jes/` — JES scenes, components, systems, gameplay, timelines, and integration
- `timeline/` — animation and story timeline APIs and authoring
- `ui/` — Facets, menus, layout, styling, screens, and editor-tooling DSLs
- `spec/` — versioned language contracts and compatibility policy

## Related Sections

- [Guides](../guides/README.md)
- [Runtime](../runtime/README.md)
- [Editor](../editor/README.md)
- [Complete Documentation Index](../INDEX.md)
