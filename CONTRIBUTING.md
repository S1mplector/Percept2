# Contributing to Java Vector Nexus

Thank you for improving JVN. Contributions are welcome across the engine, editor, scripting languages, documentation, tests, and supported extension APIs.

## Before you begin

- Use Java 21. Run `./jvnw doctor` before troubleshooting Gradle or editor failures.
- Search existing issues and pull requests before starting overlapping work.
- Open an issue first for breaking language changes, public API changes, new dependencies, large UI redesigns, or architectural rewrites.
- Keep pull requests focused. Unrelated cleanup makes behavior changes harder to review.
- Follow the [Code of Conduct](CODE_OF_CONDUCT.md) and report vulnerabilities through [SECURITY.md](SECURITY.md), not public issues.

## Repository model

`stable` is the default, integration, and release branch. New clones check it out automatically, and contributor pull requests should target it unless a maintainer says otherwise.

```bash
git clone https://github.com/S1mplector/Java-Vector-Nexus.git
cd Java-Vector-Nexus
git pull --ff-only
git switch -c feat/short-description
./jvnw doctor
./jvnw compile
```

Use a descriptive prefix such as `feat/`, `fix/`, `docs/`, `test/`, or `chore/`. Never commit generated build directories, local IDE state, credentials, or project-specific media unless the change explicitly requires reviewed fixtures.

## Find the right module

| Area | Primary location | Typical verification |
| --- | --- | --- |
| Engine, VNS runtime, assets, saves | `modules/core` | `./gradlew :core:test` |
| JES loading and execution | `modules/scripting` | `./gradlew :scripting:test` |
| JavaFX rendering and media | `modules/fx` | `./gradlew :fx:test` |
| Game runtime and interop | `modules/runtime` | `./gradlew :runtime:test` |
| Editor and authoring tools | `modules/editor` | `./gradlew :editor:test` |
| Plugin contracts and host | `modules/plugin-api`, `modules/plugin-runtime` | `./gradlew :plugin-api:javadoc :plugin-runtime:test` |
| Documentation | `docs`, `scripts/doc-lint.mjs` | `node scripts/doc-lint.mjs --strict` |
| Packaging | root `build.gradle.kts`, `.github/workflows` | packaging smoke workflow or focused task |

See [Architecture](docs/architecture/README.md), [Documentation Index](docs/INDEX.md), and [Plugin API reference](docs/plugins/api-reference.md) before modifying a public boundary.

## Development loop

Use the smallest relevant check while editing:

```bash
./jvnw compile
./gradlew :module:test --tests com.example.ChangedBehaviorTest
./jvnw quick
```

Before opening or updating a pull request, run the contributor verification path:

```bash
./scripts/verify-contribution.sh
```

It checks the bootstrap wrapper, compiles every module, runs the full Gradle verification suite, builds Plugin API Javadocs, lints documentation strictly, and checks patch whitespace. Windows contributors can run the commands printed by the script individually from PowerShell.

## Engineering expectations

- Preserve platform-neutral boundaries. `core` and `plugin-api` must not acquire JavaFX/editor dependencies.
- Prefer explicit contracts, immutable snapshots, bounded resources, and deterministic cleanup.
- Preserve user changes in dirty worktrees and avoid destructive Git operations.
- Keep public APIs source-compatible when possible. Document deprecations before removal.
- Do not add reflection where a supported interface can express the dependency.
- Use SLF4J rather than standard output for application diagnostics.
- Include useful error context without logging secrets or complete user-authored content unnecessarily.
- Keep tests deterministic and independent of network access, user home contents, locale, and wall-clock timing.

The existing codebase contains some historical conventions. Match the local style of a file unless the pull request is explicitly a formatting migration.

## Tests

Every behavior change should include a regression test at the lowest sensible layer. Prefer:

- unit tests for parsing, validation, state, and pure transformations;
- contract tests for backends and plugin extension points;
- focused integration tests for runtime/editor wiring;
- small checked-in fixtures over generated opaque data.

Do not weaken, disable, or broadly exclude a failing test to make CI green. If a failure is unrelated and reproducible on `stable`, link an issue and state the evidence in the pull request.

## VNS and JES changes

Syntax, diagnostics, runtime behavior, documentation, editor support, and VS Code fixtures form one contract. A language change normally requires:

1. parser/runtime implementation;
2. valid and malformed-script tests;
3. editor diagnostics/highlighting updates;
4. VS Code fixture coverage when relevant;
5. normative specification and changelog updates;
6. deprecation behavior for incompatible transitions.

Read [Scripting Language Contract](docs/scripting/spec/README.md) before implementation.

## Documentation changes

The engine repository is the documentation source of truth. Follow [Documentation Maintenance](docs/MAINTENANCE.md), link new pages from their section index and `docs/INDEX.md`, and run strict lint. Do not edit the website mirror as a substitute for updating engine docs.

## Commits and pull requests

Write imperative commit subjects that describe the outcome, for example:

```text
fix(runtime): isolate plugin listener failures
docs(vns): specify plugin command diagnostics
```

Pull requests should explain the problem, solution, compatibility impact, verification performed, documentation changes, and screenshots for visible UI work. Complete the repository pull-request template and keep the branch current with `stable`.

Reviewers may request smaller commits, additional malformed-input tests, documentation changes, or a migration path. A maintainer merges after required checks and review are complete.

## Licensing

By submitting a contribution, you certify that you have the right to submit it and agree that it will be distributed under the repository's license.
