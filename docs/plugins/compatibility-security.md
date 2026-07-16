# Compatibility and security

## API compatibility

Plugin API `1.x` preserves existing public types and method behavior. New default methods, extension families, and optional context data may be added in minor releases. Removing or changing an existing contract requires API `2.0` and a migration guide.

Plugins should depend only on `plugin-api`. Classes from `core`, `runtime`, `editor`, JavaFX internals, or embedded libraries are not part of the compatibility guarantee. Deprecations remain documented for at least one minor API release before removal in the next major line.

## Trust model

JVN plugins execute JVM bytecode in the application process. Capability declarations organize and audit supported integration points; they do not prevent direct filesystem, network, process, reflection, or native access.

Install only plugins whose publisher and artifact you trust. Review requested capabilities and checksums. Back up projects before enabling importers or migration tools.

## Author responsibilities

- Do not collect telemetry without explicit consent.
- Do not transmit project content by default.
- Store secrets using an operating-system credential facility, not `config.properties`.
- Treat project files and script arguments as untrusted input.
- Bound threads, queues, caches, and generated files.
- Stop executors and release handles from `stop()`.
- Avoid static references that prevent classloader collection.

Strong isolation requires a separate process and IPC protocol. Untrusted marketplace execution should be deferred until such a host exists.
