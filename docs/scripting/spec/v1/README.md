# JVN Language Contract 1

Language Contract 1 is the first versioned contract for VNS and JES.

## Specifications

- [VNS 1 Specification](vns.md)
- [JES 1 Specification](jes.md)
- [Contract Changelog](changelog.md)
- [Compatibility And Deprecation Policy](../compatibility-policy.md)
- [Diagnostic Contract](../diagnostics.md)

## Status

The contract is **provisional** while existing parser behavior, editor diagnostics, documentation,
and VS Code language support are inventoried against it. Provisional means the contract is the
target for stabilization; it does not mean every documented construct is already verified across
every frontend.

The contract becomes stable when shared compatibility fixtures verify all public constructs through
the parser, editor, runtime, and VS Code tooling.

## Implementation Status

| Surface | Current state | Contract work remaining |
|---|---|---|
| VNS runtime parser | Implemented and strict | Catalog stable diagnostic codes and fixtures |
| JES tokenizer/parser | Implemented and strict | Catalog stable diagnostic codes and fixtures |
| Editor diagnostics | Uses the real parsers, then adds analysis | Share one diagnostic model and fixture expectations |
| Runtime behavior | Implemented | Add contract-level behavior fixtures |
| VS Code extension | Highlighting and snippets available | Add fixture-based grammar tests and close keyword drift |
| Language guides | Extensive | Mark explanatory pages and resolve conflicts against this contract |

## Known Reconciliation Items

These are recorded here so documentation does not silently choose one implementation:

- JES character-component naming is inconsistent across existing parser/tooling documentation and
  must be resolved before a canonical spelling and deprecation alias are declared.
- The VS Code VNS grammar does not yet recognize every directive accepted by the runtime parser.
- Existing VNS and JES parser failures do not yet share the stable diagnostic-code catalog defined
  by the diagnostic contract.

## Conformance

An implementation conforms to Language Contract 1 when it:

1. accepts all valid Contract 1 fixtures;
2. rejects all invalid Contract 1 fixtures with the expected diagnostic category;
3. accepts deprecated fixtures with the expected warning;
4. preserves the defined runtime result of behavior fixtures;
5. does not advertise undocumented syntax as portable Contract 1 syntax.
