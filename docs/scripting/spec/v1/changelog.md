# Language Contract 1 Changelog

This changelog records changes to the VNS/JES scripting contract rather than general engine changes.

## Unreleased

- **VNS, additive:** Added optional `scale=<0.1..3.0>` metadata to
  `@character`. The multiplier persists across character appearances and
  composes with global framing and temporary timeline transforms. Parser,
  renderer, editor preview/help, VS Code snippets, tests, and authoring
  references define the same form.
- **VNS, additive:** Added `[plugin <command-id> [arguments...]]` as the stable shorthand for commands registered through the JVN Plugin API. Parser, runtime interop, editor highlighting/help, parser tests, and authoring documentation define the same form.
- Established Language Contract 1 as a version independent from the JVN engine version.
- Defined the authority of normative specifications, guides, and implementation documentation.
- Added compatibility, deprecation, diagnostic, and tooling-agreement policies.
- Recorded known VNS grammar coverage and JES component-naming reconciliation work.

Future entries should identify the affected language, whether the change is additive, clarifying,
deprecated, or breaking, and the fixtures that establish the behavior.
