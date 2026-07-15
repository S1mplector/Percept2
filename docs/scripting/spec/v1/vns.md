# VNS 1 Specification

This document defines the normative VNS 1 source model. Detailed feature catalogs remain in the
[VNS language documentation](../../vns/overview/vns-scripting.md) while they are reconciled into this
specification.

## Source Model

- A VNS source file MUST be decodable as UTF-8.
- Implementations MUST accept LF and CRLF line endings.
- Parsing is line-oriented except for explicitly delimited blocks.
- Blank lines and lines whose first non-whitespace character is `#` are comments.
- Directive and command names are case-insensitive in the current Contract 1 implementation.
- Identifiers, labels, asset paths, and user-provided values retain their documented case behavior.

## Top-Level Forms

A non-empty, non-comment line MUST be one of these forms:

| Form | Shape | Reference |
|---|---|---|
| Directive | `@name arguments` | [Directives](../../vns/language/vns-directives.md) |
| Label | `@label name` or compatible label form | [Flow control](../../vns/flow/vns-flow-control.md) |
| Dialogue | `speaker: text` or documented quoted form | [Dialogue](../../vns/language/vns-dialogue.md) |
| Choice | `> text -> label` or a documented choice block | [Choices](../../vns/language/vns-choices.md) |
| Command | `[name arguments]` | [Commands](../../vns/language/vns-commands.md) |
| Timeline block | `timeline ...` block | [Interop](../../vns/integration/vns-interop.md) |
| Java block | explicitly delimited documented Java form | [Interop](../../vns/integration/vns-interop.md) |

Any other non-empty line MUST produce an error diagnostic.

## Declarations And References

- A script MAY declare scenarios, characters, backgrounds, character images and layers, character
  groups and presets, display presets, stage presets, positions, variables, defines, external
  bindings, includes, and labels using their documented directive forms.
- Duplicate labels in the same resolved script MUST be rejected.
- A statically resolvable local label reference MUST name a declared label.
- Include resolution MUST preserve source names and line locations in diagnostics.
- Include cycles and missing includes MUST be reported as errors rather than ignored.

The exhaustive directive argument grammar is currently defined by the runtime parser and the
[directive reference](../../vns/language/vns-directives.md). Moving that catalog into this normative
document is tracked as Contract 1 reconciliation work.

## Commands

- Commands use square brackets and MUST name a supported command.
- Required arguments, option names, value types, ranges, and defaults are part of the contract.
- Unknown commands and unknown options MUST be errors unless a specific extension mechanism says
  otherwise.
- Conditions MUST be parsed and validated before runtime execution.

The current command catalog is maintained in the [VNS Commands Reference](../../vns/language/vns-commands.md).
That page is explanatory during reconciliation; parser acceptance alone does not permanently reserve
undocumented syntax.

## Runtime Agreement

A source accepted by the editor's parser MUST also be accepted by the runtime parser under the same
project context and language version. Editor-only checks MAY additionally reject launching a project
with missing assets or broken project references, but they MUST identify those as project diagnostics.

## Invalid Example

```vns
@scenario invalid
@label start
[command_that_does_not_exist]
```

This source is invalid because unknown commands are not portable extension points.

## Version 1 Reconciliation Checklist

- Move the complete directive and command tables into normative appendices.
- Assign stable codes to parser and semantic failures.
- Add accepted, rejected, deprecated, include, and runtime behavior fixtures.
- Verify editor and runtime results against the same fixtures.
- Verify VS Code highlighting recognizes every public directive and command.
