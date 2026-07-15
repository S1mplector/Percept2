# Scripting Compatibility And Deprecation Policy

This policy applies to VNS and JES beginning with [Language Contract 1](v1/README.md).

## Versioning

Language versions use a single contract number, such as `1`. Clarifying documentation, adding
diagnostic detail, or adding syntax in a backward-compatible way does not require a new contract
number. A new contract number is required when a previously valid portable script becomes invalid
or its defined runtime meaning changes incompatibly.

The JVN engine version does not select a language version by itself. Until project manifests gain an
explicit language-version field, Language Contract 1 is the default contract for `.vns` and `.jes`
files.

## Portable Scripts

A portable script:

- uses syntax documented by the selected language contract;
- does not depend on undocumented parser acceptance;
- does not contain error diagnostics;
- may contain deprecated syntax during its announced compatibility window.

Parser quirks and editor-only conveniences are not portable unless added to the contract.

## Adding Syntax

New syntax MUST include, in the same change:

1. a normative specification update;
2. at least one accepted compatibility fixture;
3. malformed-input coverage where applicable;
4. parser and runtime coverage;
5. editor diagnostic coverage;
6. VS Code grammar or language-tooling coverage;
7. a contract changelog entry.

Until all applicable surfaces are updated, the feature MUST be marked as implementation-specific or
incomplete in the contract status table.

## Deprecating Syntax

Syntax MUST NOT be removed without a warning period. A deprecation must:

- remain accepted by the parser;
- preserve its previous runtime meaning during the compatibility window;
- emit a stable warning code;
- identify the supported replacement when one exists;
- be listed in the contract changelog;
- remain covered by a deprecated compatibility fixture.

Deprecated syntax SHOULD remain supported for at least one complete minor engine release cycle.
Removal requires a new language-contract version unless the syntax was explicitly documented as
experimental.

## Experimental Features

Experimental syntax MUST be labelled **experimental** in the specification and changelog. It MAY
change without a new contract version, but SHOULD still produce a migration diagnostic when a safe
replacement exists. Merely being undocumented does not make syntax experimental; undocumented
acceptance is an implementation detail.

## Diagnostic Compatibility

Diagnostic codes and severities are compatibility surfaces. Message wording MAY improve without a
contract change. A code MUST keep the same general meaning. Splitting one broad code into more
specific codes is backward-compatible when tooling can continue treating the original category the
same way.

## Tooling Agreement

The runtime, editor, command-line validation, and external language tooling MUST agree on whether a
fixture is valid, invalid, or deprecated. Syntax highlighting is not validation, but it MUST
recognize all public language constructs and MUST be tested against the shared fixtures.
