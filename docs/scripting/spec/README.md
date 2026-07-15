# JVN Scripting Language Contract

This is the authoritative entry point for the syntax and compatibility contract shared by VNS,
JES, the JVN runtime, the editor, and external language tooling.

## Current Contract

- [Language Contract 1](v1/README.md)
- [Compatibility And Deprecation Policy](compatibility-policy.md)
- [Diagnostic Contract](diagnostics.md)
- [Contract Changelog](v1/changelog.md)

JVN engine releases and language-contract versions are intentionally separate. JVN `0.3.x` may
implement Language Contract 1, and later engine releases may continue implementing that contract
without changing valid script syntax.

## Authority

The documents in this directory define the portable scripting contract:

- **Normative specification:** states what valid VNS and JES programs mean.
- **Language guides:** teach features and provide examples, but do not create new syntax.
- **Parser internals:** describe the current implementation, but do not override the contract.
- **Editor and extension documentation:** describe tooling behavior, but do not override parser or
  runtime behavior.

If a guide conflicts with this specification, the specification wins. If an implementation differs
from the specification, that difference is a compatibility defect and should be recorded in the
[Language Contract 1 status table](v1/README.md#implementation-status).

## Requirement Words

The words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** describe required,
recommended, and optional behavior. Examples labelled **valid**, **invalid**, or **deprecated** are
part of the contract. Unlabelled examples are explanatory.

## Scope

Language Contract 1 covers:

- VNS source syntax, declarations, dialogue, choices, commands, and includes
- JES tokens, scenes, entities, components, bindings, and timelines
- parse and validation diagnostics
- runtime-visible behavior defined by those languages
- compatibility and deprecation rules

Menu, layout, style, Story Map, and other JVN DSLs are not yet versioned by this contract.
