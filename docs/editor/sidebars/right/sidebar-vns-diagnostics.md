# Sidebar — VNS Diagnostics

Live error and warning diagnostics panel for the active `.vns` script file. Updates automatically when the file changes.

Source: `editor/src/main/java/com/jvn/editor/ui/VnsDiagnosticsView.java`

---

## Overview

The VNS Diagnostics panel provides real-time static analysis of VNS scripts, surfacing errors and warnings as you edit. It uses `VnsScriptAnalyzer` to parse the active script and report issues such as undefined labels, unreachable code, syntax problems, and missing asset declarations.

- **Default side:** Right
- **Tab name:** VNS Diagnostics
- **Updates:** Automatically when the active `.vns` file changes or is edited

---

## UI Layout

```text
┌──────────────────────────────┐
│  VNS Diagnostics             │
│  chapter1.vns                │
│  3 errors, 1 warning         │
│  Filter: [________________]  │
├──────────────────────────────┤
│  Error  L42  Missing label   │
│              reference:      │
│              'end_battle'    │
│  Error  L58  Undefined       │
│              character: bob  │
│  Error  L91  Duplicate       │
│              label: start    │
│  Warn   L23  Unreachable     │
│              label: unused   │
└──────────────────────────────┘
```

---

## Panel Elements

| Element | Description |
|---------|-------------|
| **Title** | "VNS Diagnostics" |
| **File name** | Name of the active `.vns` file (e.g., `chapter1.vns`) |
| **Summary** | Counts of errors and warnings (e.g., "3 errors, 1 warning") |
| **Filter** | Text field to narrow the diagnostics list by message content, kind, or line number |
| **Diagnostics list** | Scrollable list of diagnostic entries |

---

## Diagnostic Entry Format

Each entry shows:

```
Severity  Line  Message
```

- **Severity** — `Error` (red, `#f38ba8`) or `Warning` (orange, `#f0b673`)
- **Line** — Line number in the script (e.g., `L42`)
- **Message** — Description of the issue

---

## Interactions

| Action | Result |
|--------|--------|
| **Double-click** a diagnostic | Jumps to that line in the editor |
| **Press Enter** on a selected diagnostic | Same as double-click |
| **Type in the Filter field** | Narrows the list by kind, message text, or line number |

---

## Diagnostic Categories

The `VnsScriptAnalyzer` checks for the following issue types:

### Errors

| Category | Example |
|----------|---------|
| **Undefined label references** | `[goto end_battle]` where `end_battle` doesn't exist |
| **Duplicate labels** | Two `@label start` declarations in the same file |
| **Syntax errors** | Malformed commands like `[show]` with missing arguments |
| **Missing asset declarations** | `[show hero center]` without a prior `@charimg hero` declaration |
| **Invalid command arguments** | Wrong argument count or type for a VNS command |

### Warnings

| Category | Example |
|----------|---------|
| **Unreachable labels** | A label that no `goto`, `choice`, or fallthrough reaches |
| **Unused declarations** | An `@charimg` that is never referenced by a `[show]` |
| **Shadowed variables** | A variable declared with `@set` that overwrites a previous value without use |

---

## Analysis Engine

The diagnostics are produced by `VnsScriptAnalyzer.analyze()`, which:

1. Parses the script into an AST using `VnScriptParser`
2. Builds a label index (name → line number)
3. Traces control flow paths (goto, choice branches, fallthrough)
4. Identifies unreachable labels via graph traversal
5. Validates all reference targets (labels, characters, backgrounds)
6. Returns an `Analysis` object containing:
   - `List<LabelNode>` — all labels with line numbers
   - `List<FlowEdge>` — all transitions between labels
   - `List<Diagnostic>` — all errors and warnings
   - `String startLabel` — the first label in the script

---

## Filtering

The filter field supports free-text search across:
- Diagnostic kind (`error`, `warning`)
- Message text (partial match, case-insensitive)
- Line numbers (e.g., typing `42` shows diagnostics at line 42)

The filter applies instantly as you type.

---

## Auto-Refresh

The panel refreshes automatically when:
- The active tab switches to a `.vns` file
- The content of the active `.vns` file is modified
- The user explicitly triggers a refresh via the editor

When a non-VNS file is active, the panel clears and shows no diagnostics.

---

## Integration

- The `onOpenLine` callback is set by `EditorApp` to navigate the editor's code view to a specific line
- The panel receives analysis data via `setAnalysis(File scriptFile, VnsScriptAnalyzer.Analysis analysis)`
- Clearing is done via `clear()` when no VNS file is active

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all 14 sidebar panels
- [Label Flow Map](sidebar-label-flow-map.md) — visual companion showing the same analysis as a graph
- [VNS Scripting](../../../scripting/vns/overview/vns-scripting.md) — VNS script format and commands
