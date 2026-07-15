# Scripting Diagnostic Contract

VNS and JES diagnostics are part of the public scripting surface. The runtime and editor should not
invent different meanings for the same source problem.

## Diagnostic Shape

Every structured language diagnostic MUST provide:

| Field | Meaning |
|---|---|
| `language` | `vns` or `jes` |
| `code` | Stable machine-readable identifier |
| `severity` | `error`, `warning`, or `info` |
| `message` | Human-readable explanation |
| `source` | Source file or logical source name when known |
| `line` | One-based source line for user-facing output |
| `column` | One-based source column when known |

Diagnostics SHOULD also provide an end position, related source locations, and a suggested
replacement where applicable.

## Severity

- **Error:** the source is not a valid program or cannot be executed safely.
- **Warning:** the source remains executable, but is deprecated, suspicious, or non-portable.
- **Info:** contextual guidance that does not indicate invalid or risky source.

Editor-only project checks, such as missing assets, MAY extend parser diagnostics. They MUST use
distinct codes and MUST NOT silently change a parser error into a warning or vice versa.

## Code Families

The following namespaces are reserved. The exact catalog will be filled as parser errors are
migrated to structured diagnostics.

| Range | Meaning |
|---|---|
| `VNS1xxx` | VNS lexical, syntax, and argument errors |
| `VNS2xxx` | VNS declarations, labels, references, and semantic errors |
| `VNS3xxx` | VNS deprecations and portability warnings |
| `VNS4xxx` | VNS project and asset diagnostics |
| `JES1xxx` | JES lexical and syntax errors |
| `JES2xxx` | JES components, properties, references, and semantic errors |
| `JES3xxx` | JES deprecations and portability warnings |
| `JES4xxx` | JES project and asset diagnostics |

Generic codes such as `parse_error` are transitional and are not stable Language Contract 1 codes.
New diagnostics SHOULD use a specific code from the appropriate family.

## Agreement Rules

For the same source and language version:

- runtime parsing and editor parsing MUST return the same parser diagnostic codes, severities, and
  source locations;
- editor semantic analysis MAY add diagnostics after parsing;
- a frontend MAY change presentation but MUST preserve the underlying code and severity;
- tests SHOULD assert codes and locations rather than exact message prose.

## Multiple Errors

A parser MAY stop after the first error or recover and report several errors. When several errors are
reported, their ordering MUST be deterministic: source order first, then diagnostic code.

## Deprecation Example

```text
JES3001 warning at game/scenes/demo.jes:12:5
`OldComponentName` is deprecated; use `NewComponentName`.
```

The warning code and replacement are stable. The message wording may be refined.
