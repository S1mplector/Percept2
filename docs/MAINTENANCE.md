# Documentation Maintenance Guide

The Markdown files under `docs/` are JVN's documentation source of truth. The public website and the
editor Help Center are reading surfaces built from this material; changes should begin here.

## Documentation Model

JVN documentation has four distinct roles:

| Role | Purpose | Typical location |
|---|---|---|
| Specification | Defines portable, versioned behavior | `scripting/spec/` |
| Reference | Describes commands, APIs, systems, and file formats | `architecture/`, `runtime/`, `scripting/` |
| Guide | Teaches a task or workflow | `guides/`, selected subsystem guides |
| Implementation notes | Explain internals, audits, or future work | `internals/`, `plans/`, roadmap files |

A guide or implementation page MUST NOT silently redefine a normative specification. When a page
serves more than one role, state which sections are normative and which are explanatory.

## Navigation Architecture

Readers should be able to enter at three levels:

1. [Documentation Home](README.md) — short task routes and section map
2. Section landing pages — local orientation and recommended reading order
3. [Complete Index](INDEX.md) — exhaustive catalog and cross-subsystem workflows

Major section landing pages:

- [Guides](guides/README.md)
- [Editor](editor/README.md)
- [Scripting](scripting/README.md)
- [Runtime](runtime/README.md)
- [Project Setup And Delivery](project-setup/README.md)
- [Architecture](architecture/README.md)

Add a new page to its section landing page when it is a primary route. Add every user-facing page to
`INDEX.md`. Avoid adding every deep reference to `docs/README.md`; that page should remain concise.

## Where New Pages Belong

| Content | Location |
|---|---|
| First-run and task tutorials | `guides/` |
| Editor windows and tools | `editor/` |
| Engine design and public architecture | `architecture/` |
| Runtime behavior and platform backends | `runtime/` |
| Project creation, collaboration, and release | `project-setup/` |
| VNS, JES, timelines, menus, and layout DSLs | `scripting/` |
| Versioned VNS/JES guarantees | `scripting/spec/` |
| Time-bound audits and proposals | `plans/` or an explicitly named roadmap |

Prefer extending an existing page when the new material answers the same reader question. Create a
new page when it has a distinct audience, lifecycle, or navigation destination.

## Change Rules

Update documentation in the same change when you:

- rename or remove a public class, command, property, task, or file format;
- change runtime behavior, defaults, requirements, or supported platforms;
- add an editor tool or a public scripting feature;
- change project generation, packaging, installation, or release behavior;
- change a screenshot-visible editor workflow.

For VNS or JES syntax changes, follow the
[Scripting Compatibility And Deprecation Policy](scripting/spec/compatibility-policy.md). A syntax
change is incomplete until its specification, changelog, fixtures, parser/runtime behavior, editor
diagnostics, and external language tooling agree.

## Writing Conventions

- Start with one `#` heading that names the reader-facing subject.
- State the audience or purpose near the beginning when it is not obvious.
- Prefer task-oriented headings such as “Package A Game” over vague headings such as “More.”
- Use repository-relative links and descriptive link text.
- Name commands, file paths, properties, and code symbols exactly as implemented.
- Mark experimental behavior, platform limitations, and future work explicitly.
- Do not use calendar dates as proof that content is current. Verify behavior against code or tests.
- Keep generated pages clearly named `generated-*.md`; do not hand-edit generated output.

## Safe Refactoring

Existing paths may be bookmarked by users, the hosted site, and the editor Help Center. Prefer
improving pages in place. Before moving or deleting a page:

1. search for inbound references with `rg`;
2. check Help Center routing and generated guide trees;
3. update every repository-relative link;
4. provide a redirect or compatibility stub when the publishing surface supports it;
5. run the complete validation checklist.

Do not perform blind repository-wide replacements. Inspect matches and keep unrelated terminology,
examples, generated files, and historical audit evidence intact.

## Local Validation

Run the full documentation lint when Node.js is available:

```bash
node scripts/doc-lint.mjs
```

Useful focused modes:

```bash
node scripts/doc-lint.mjs --links-only
node scripts/doc-lint.mjs --strict
```

Also run:

```bash
git diff --check
rg -n 'old-or-renamed-symbol' docs
```

The documentation workflow runs `scripts/doc-lint.mjs` for documentation-related pull requests.
Local validation remains important because CI configuration can change and some checks are heuristic.

## Screenshots

Generate all configured editor documentation screenshots with:

```bash
./gradlew :editor:generateDocsScreenshots
```

Focused tasks are also available:

```bash
./gradlew :editor:generateSidebarDocsScreenshots
./gradlew :editor:generateCoreDocsScreenshots
```

Review generated images and Markdown before committing them. Screenshot generation verifies capture,
not whether the depicted workflow is still the best explanation.

## Review Checklist

- [ ] The page has one clear purpose and audience.
- [ ] Current behavior was checked against code, tests, or a working build.
- [ ] Commands and examples are runnable from the stated working directory.
- [ ] New or renamed pages are linked from their section landing page and `INDEX.md`.
- [ ] Normative, explanatory, implementation, and roadmap material are not conflated.
- [ ] Platform support and experimental status are stated accurately.
- [ ] Relative links, anchors, and image references resolve.
- [ ] `node scripts/doc-lint.mjs` passes when Node.js is available.
- [ ] `git diff --check` passes.

## Audits And Plans

Audit reports are historical snapshots. Keep their original findings intact and add a status banner
when later work supersedes them. Do not use an old audit's counts as current repository facts.

- [May 2026 Documentation Audit](plans/docs-audit-2026-05.md)

When an audit finding is resolved, update the current documentation and optionally annotate the audit
with a link to the resolution. Avoid rewriting historical measurements to match the present tree.
