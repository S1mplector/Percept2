# JES 1 Specification

This document defines the normative JES 1 source model. Detailed system catalogs remain in the
[JES language documentation](../../jes/overview/jes-scripting.md) while they are reconciled into this
specification.

## Source Model

- A JES source file MUST be decodable as UTF-8.
- Implementations MUST accept LF and CRLF line endings.
- Whitespace separates tokens and is otherwise insignificant outside strings.
- `//` introduces a line comment outside a string.
- String literals use double quotes and MUST preserve documented escape behavior.
- Keywords and public component/action names are case-sensitive unless a construct explicitly says
  otherwise.

## Lexical Values

JES 1 supports:

- quoted strings;
- integer and decimal numbers, including negative numbers where the receiving property permits them;
- `true` and `false` boolean values;
- bare identifiers where a grammar production or property permits them;
- `rgb(...)` and `rgba(...)` color expressions;
- braces, parentheses, colons, commas, and documented operators.

Malformed strings, invalid tokens, and unexpected end-of-file MUST produce error diagnostics with a
source location.

## Program Structure

A JES program contains one or more scene declarations:

```jes
scene "Demo" {
  entity "title" {
    component Label2D {
      text: "Hello"
      x: 40
      y: 40
    }
  }
}
```

A scene MAY contain documented scene properties, tilesets, items, maps, entities, input bindings,
and a timeline. An entity MAY contain documented entity properties and component declarations.

Unknown structural keywords MUST be rejected unless they occur in a documented property position.

## Components And Properties

- A component declaration MUST use a supported component name or a documented extension component.
- Components with a closed property schema MUST reject unknown properties.
- Components explicitly documented as extensible MAY accept free-form properties.
- Property types and ranges are part of the language contract, not merely editor hints.

The current component catalog is maintained in the [JES Component Reference](../../jes/scene/components.md).
The character-component naming discrepancy recorded in the [Contract 1 status](README.md#known-reconciliation-items)
MUST be resolved before either spelling is declared canonical here.

## Timelines

- Timeline actions MUST use a supported action name or documented extension action.
- Closed-schema actions MUST reject unknown properties.
- Required targets and properties MUST be validated before runtime execution.
- Aliases MUST be documented and tested if they are part of the portable contract.

The current action catalog is maintained in the [JES Timeline Reference](../../jes/timeline/jes-timeline.md).

## Runtime Agreement

The editor and runtime MUST use equivalent tokenization and parsing rules for the same source and
language version. Runtime loading MUST NOT reinterpret a parser error as valid source. Semantic or
project validation MAY add errors after parsing and must preserve parser diagnostics.

## Invalid Example

```jes
scene "Broken" {
  entity "hero" {
    component ComponentThatDoesNotExist { }
  }
}
```

This source is invalid unless the component is registered through a documented extension mechanism.

## Version 1 Reconciliation Checklist

- Move the complete component, property, binding, and action schemas into normative appendices.
- Resolve canonical component names and define deprecation aliases where needed.
- Assign stable codes to tokenizer, parser, and semantic failures.
- Add accepted, rejected, deprecated, and runtime behavior fixtures.
- Verify editor, runtime, and VS Code tooling against the same fixtures.
