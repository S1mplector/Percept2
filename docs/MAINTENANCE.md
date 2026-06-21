# Documentation Maintenance Guide

This document explains how to keep JVN documentation accurate and up-to-date as the codebase evolves.

---

## Overview

Documentation is a living artifact. As the code changes, docs must change with it to stay useful and prevent user confusion.

**Responsibilities:**
- **Developers:** Update docs when you modify code (same PR)
- **Docs team:** Audit and refresh docs quarterly
- **CI/CD:** Automatically catch broken links and stale symbols

---

## When to Update Docs

### Update Immediately (Same PR as Code Change)

1. **Renamed a public class, method, or interface?**
   - Find docs mentioning the old name
   - Update all references
   - Example: `Character2D` → `CharacterEntity2D`

2. **Changed API signature or behavior?**
   - Update code examples in related docs
   - Update parameter descriptions
   - Example: Changed function signature, update "Usage Example" section

3. **Added a new public class or system?**
   - Create a new documentation page (or extend existing guide)
   - Link it from docs/INDEX.md
   - Example: New rendering backend → new "Platform Runtimes" section

4. **Modified or removed a configuration option?**
   - Update configuration examples and docs
   - Example: Gradle property renamed, update build guide

### Schedule for Later (Document in Task)

1. **Internal refactoring that doesn't change APIs**
   - File a doc task: "Update internals documentation after refactoring X"
   - Update implementation details, keep user-facing guide same

2. **Performance or design improvements**
   - File task: "Update performance tips based on optimization Y"

---

## Audit Cycle

### Quarterly (Every 3 months)

1. **Run doc-lint:**
   ```bash
   node scripts/doc-lint.mjs
   ```
   Fixes errors (broken links, missing symbols).

2. **Refresh screenshots:**
   ```bash
   ./gradlew :editor:generateDocsScreenshots
   ```
   Regenerate UI screenshots from current editor state.

3. **Review stale sections:**
   - Anything >6 months old → verify still accurate
   - Update "Last Updated" date if verified

### Yearly (Every 12 months)

Full documentation audit (similar to [Phase 0 Audit](plans/docs-audit-2026-05.md)):

1. Cross-check all modules against docs
2. Symbol verification across all pages
3. Coverage analysis (what's missing?)
4. Create new tasks for gaps found

---

## Making Code Changes

### Checklist for Developers

When modifying code:

- [ ] **Will this break any docs?**
  - Renamed class → docs reference it?
  - Changed signature → docs show old example?
  - Removed feature → docs mention it?

- [ ] **If breaking docs: Update in this PR**
  - Search docs for old name/feature
  - Update all occurrences
  - Test links still work

- [ ] **If adding new feature: Add to docs**
  - Create page or expand existing guide
  - Link from docs/INDEX.md
  - Include code example from tests

- [ ] **Run doc-lint before pushing:**
  ```bash
  node scripts/doc-lint.mjs
  ```
  Fix any errors it reports.

### PR Template Reminder

The GitHub PR template includes:

```
- [ ] Docs updated (or N/A)
- [ ] No broken links introduced
- [ ] Symbols mentioned in docs still exist
```

---

## Documentation Governance

### Structure

```
docs/
├── README.md                    # Entry point, Fast Routes
├── INDEX.md                     # Complete navigation index
├── MAINTENANCE.md               # This file
├── architecture/                # Design, internals, performance
├── editor/                      # Editor UI, tools, panels
├── guides/                      # Tutorials, onboarding, cookbooks
├── project-setup/               # Build, release, deployment
├── runtime/                     # Runtime behavior, systems
├── scripting/                   # VNS, JES, menus, UI
└── plans/                       # Improvement plans and audits
```

### Update Patterns

When updating an existing guide:

1. **Preserve section structure** — don't reorganize; readers have bookmarks
2. **Add dates** — "Updated: May 2026" at bottom
3. **Link related docs** — add cross-references if you reference other pages
4. **Keep examples current** — test code snippets before committing

---

## Tools & Automation

### Local Validation

Before pushing, run locally:

```bash
# Full validation
node scripts/doc-lint.mjs

# Links only (faster)
node scripts/doc-lint.mjs --links-only

# Strict mode (fail on warnings)
node scripts/doc-lint.mjs --strict
```

### CI/CD Integration

GitHub Actions runs doc-lint on every PR:

- `.github/workflows/docs-lint.yml` — automated checks
- Fails PR if links broken or symbols stale
- Comment on PR with fix suggestions

### Manual Screenshot Refresh

Puppeteer screenshot tools may get out of sync. Refresh manually:

```bash
./gradlew :editor:generateDocsScreenshots
git add docs/
git commit -m "docs: refresh editor screenshots (May 2026)"
```

---

## Creating New Documentation

### When You Need a New Page

1. **Decide location** — where does it fit?
   - Core API → `docs/architecture/internals/`
   - Editor UI → `docs/editor/sidebars/right/`
   - Tutorial → `docs/guides/`
   - Runtime feature → `docs/runtime/systems/`

2. **Create the file** with markdown:
   ```
   # Clear Title
   
   **Module:** `package.name`
   **Purpose:** One-line summary
   
   ## Overview
   ## Usage Example
   ## API Reference
   ## Related Documentation
   ```

3. **Add to INDEX.md:**
   - Find the relevant section
   - Add a Markdown link with the page title and its repository-relative path

4. **Test links:**
   ```bash
   node scripts/doc-lint.mjs
   ```

5. **Commit with context:**
   ```
   docs: add X documentation
   
   Covers [module/feature] with usage examples and API reference.
   Related to [related pages].
   ```

---

## Common Updates

### Class Renamed

```bash
# Search docs for old name
grep -r "OldClassName" docs/

# Update all occurrences
sed -i 's/OldClassName/NewClassName/g' docs/**/*.md

# Verify
grep -r "OldClassName" docs/
# (should return 0 results)
```

### Example Code Changes

In a docs file with code examples:

```markdown
**Before:**
\`\`\`java
Engine engine = new Engine(config, oldRenderer);
\`\`\`

**After:**
\`\`\`java
Engine engine = new Engine(config, newRenderer);
\`\`\`
```

### Link Rewording (keeping URL)

If you reword but keep the link target the same:

```markdown
**Before:** [API Reference](runtime/systems/audio-system.md)
**After:** [Audio System Guide](runtime/systems/audio-system.md)
```

---

## Troubleshooting

### "Doc-lint reports broken link"

1. Check if file exists: `ls docs/path/to/file.md`
2. Verify relative path: Is it from the right directory?
3. Fix the link in the source file
4. Re-run: `node scripts/doc-lint.mjs`

### "Editor screenshots are out of date"

1. Check screenshot age: `ls -l docs/editor/core/generated-*.md`
2. Run generator: `./gradlew :editor:generateDocsScreenshots`
3. Commit: `git add docs/ && git commit -m "docs: refresh screenshots"`

### "Can't find how to do X"

1. Search docs: `grep -r "keyword" docs/`
2. Check docs/README.md "Fast Routes"
3. Check docs/INDEX.md sections
4. If still missing, file a task to add it

---

## Contact & Escalation

**For doc questions:** Ask in #documentation Slack channel  
**For code-specific guidance:** Check related architecture doc  
**For missing docs:** Create a GitHub issue with "docs" label

---

## Resources

- **Audit Report:** [Phase 0 Audit (May 2026)](plans/docs-audit-2026-05.md)
- **Index:** [Complete Documentation Index](INDEX.md)

---

**Last Updated:** May 2026  
**Maintenance Cadence:** Quarterly audits, immediate updates on code changes
