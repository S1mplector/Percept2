# scripting

JES (Java Engine Script) parser and runtime. Parses `.jes` scene/timeline files into an AST that the engine executes.

## Packages

The `jes/` package contains the full JES pipeline:

- **Tokenizer** — lexical analysis of JES source text
- **Parser** — recursive-descent parser producing a typed AST
- **AST nodes** — scene declarations, entity blocks, component properties, timeline actions
- **Validation** — strict structural and type checking with diagnostic messages

## Dependencies

- `:core` — engine scene graph types, timeline model

## Build

```bash
./gradlew :scripting:build
```

## Documentation

- [JES Overview](../docs/scripting/jes/overview/jes-scripting.md)
- [Scenes & Entities](../docs/scripting/jes/scene/jes-scenes-entities.md)
- [Timeline & Actions](../docs/scripting/jes/timeline/jes-timeline.md)
- [Parsing Internals](../docs/scripting/jes/internals/jes-parsing.md)
