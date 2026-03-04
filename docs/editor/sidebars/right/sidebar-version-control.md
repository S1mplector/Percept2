# Sidebar — Version Control

Team-focused Git control panel integrated into the JVN editor. Supports init, commit, push, pull, branch management, stash, and remote setup.

Source: `editor/src/main/java/com/jvn/editor/ui/VersionControlView.java`

---

## Overview

The Version Control panel provides a complete Git workflow without leaving the editor. It handles repository initialization, remote configuration (including GitHub repo creation via `gh` CLI), branching, staging, committing, pushing, pulling, stashing, and per-file diff viewing.

- **Default side:** Right
- **Tab name:** Version Control
- **Backend:** `GitVcsService` (Git-only, no LFS)
- **Auto-refresh:** Periodic status polling via `Timeline` timer

---

## UI Layout

```text
┌────────────────────────────────────┐
│  Version Control                   │
│  my-project                        │
│  Git: 2.43.0                       │
│  Branch: main                      │
│  Sync: ↑2 ↓0                      │
│  Status: 3 changed files           │
│  Remote: origin → github.com/...   │
├────────────────────────────────────┤
│  ┌────────────────────────────┐    │
│  │ ⚠ Repository Not Initialized│   │  ← shown only if no .git
│  │ [☑ Create initial commit]  │    │
│  │ [Initialize]               │    │
│  └────────────────────────────┘    │
├────────────────────────────────────┤
│  [↻ Refresh] [↓↓ Fetch] [↓ Pull]  │
│  [↑ Push]   |  [Stash] [Pop]      │
├────────────────────────────────────┤
│  Branch: [▼ main     ] [+ New]    │
├────────────────────────────────────┤
│  Changed Files:                    │
│   M  src/main/Scene.java          │
│   A  assets/bg/new_park.png       │
│   D  scripts/old_scene.vns        │
│  [Stage] [Unstage] [Discard] [Diff]│
├────────────────────────────────────┤
│  Commit: [________________________]│
│  [Commit]                          │
├────────────────────────────────────┤
│  Log:                              │
│  > git push origin main            │
│  Everything up-to-date             │
└────────────────────────────────────┘
```

---

## Status Display

| Label | Description |
|-------|-------------|
| **Repo** | Project directory name |
| **Git** | Detected Git version (e.g., "Git: 2.43.0") or "Git: not found" |
| **Branch** | Current branch name (e.g., "Branch: main") |
| **Sync** | Ahead/behind remote count (e.g., "↑2 ↓0") |
| **Status** | Count of changed files |
| **Conflict** | Shown in red (`#f38ba8`) when merge conflicts exist |
| **Remote** | Remote URL or "Remote: not configured" (shown in orange `#f0b673`) |

---

## Repository Initialization

When no `.git` directory exists, a prominent warning banner is displayed:

| Element | Description |
|---------|-------------|
| **⚠ Repository Not Initialized** | Orange warning title |
| **Hint text** | "Repository is not initialized for this project." |
| **Create initial commit** | Checkbox (default: checked) — auto-commits all files after init |
| **Initialize** | Button — runs `git init` and optionally `git add -A && git commit -m "Initial commit"` |

The banner has an orange background with rounded corners and orange border for high visibility.

---

## Remote Setup

When a repo exists but has no remote, a setup guide banner is shown:

### Option A — Create GitHub Repository

Requires the `gh` CLI to be installed:

| Element | Description |
|---------|-------------|
| **Visibility** | ComboBox: Private (default) or Public |
| **Create GitHub Repository** | Green button — runs `gh repo create` with the project name |

### Option B — Add Remote Manually

| Element | Description |
|---------|-------------|
| **Add Remote** | Button — opens a text input dialog for the remote URL |

The dialog prompts for a URL (e.g., `https://github.com/user/repo.git`) and runs `git remote add origin <url>`.

---

## Sync Toolbar

| Button | Icon Class | Tooltip | Git Command |
|--------|-----------|---------|-------------|
| **Refresh** | `vcs-icon-refresh` | Refresh status | `git status`, `git branch`, `git remote` |
| **Fetch** | `vcs-icon-fetch` | Fetch all remotes | `git fetch --all` |
| **Pull** | `vcs-icon-pull` | Pull with rebase | `git pull --rebase` |
| **Push** | `vcs-icon-push` | Push to remote | `git push` |

---

## Stash Toolbar

| Button | Icon Class | Tooltip | Git Command |
|--------|-----------|---------|-------------|
| **Stash** | `vcs-icon-stash` | Stash changes | `git stash` |
| **Stash Pop** | `vcs-icon-stash-pop` | Pop stash | `git stash pop` |

---

## Branch Management

| Element | Description |
|---------|-------------|
| **Branch ComboBox** | Lists all local branches. Select one to switch (`git checkout <branch>`). |
| **New Branch** | Button (`vcs-icon-new-branch`) — prompts for a branch name, runs `git checkout -b <name>` |

---

## Changed Files List

A `ListView` showing all files with uncommitted changes. Each entry shows:
- **Status indicator** — M (modified), A (added), D (deleted), R (renamed), C (conflicted)
- **File path** — project-relative path

### Interactions

| Action | Result |
|--------|--------|
| **Click** | Selects the file for per-file actions |
| **Double-click** | Opens the file in the editor |
| **Enter** | Same as double-click |

### Per-File Action Buttons

| Button | Icon Class | Tooltip | Git Command |
|--------|-----------|---------|-------------|
| **Stage** | `vcs-icon-stage` | Stage selected | `git add <file>` |
| **Unstage** | `vcs-icon-unstage` | Unstage selected | `git reset HEAD <file>` |
| **Discard** | `vcs-icon-discard` | Discard changes | `git checkout -- <file>` ⚠ destructive |
| **Diff** | `vcs-icon-diff` | Show diff | `git diff <file>` (shown in log area) |

**Warning:** The Discard action permanently removes uncommitted changes to the selected file.

---

## Commit

| Element | Description |
|---------|-------------|
| **Commit message** | TextField with placeholder "Commit message..." |
| **Commit button** | Icon button (`vcs-icon-commit`) |

### Commit Workflow

1. Type a commit message in the text field
2. Press **Enter** or click the **Commit** button
3. Runs: `git add -A && git commit -m "<message>"`
4. The commit message field is cleared on success
5. Status refreshes automatically

The commit stages all changes (`-A`) before committing, ensuring nothing is accidentally left unstaged.

---

## Log Area

A read-only `TextArea` at the bottom showing recent Git operation output:
- Command executed (e.g., `> git push origin main`)
- Git response (e.g., "Everything up-to-date")
- Error messages if operations fail

The log is useful for debugging failed operations.

---

## Auto-Refresh

The panel uses an `AnimationTimer`-based periodic refresh to keep the status display current:
- Polls `git status` at regular intervals
- Updates branch, sync, and changed files displays
- Pauses during active operations to avoid conflicts

---

## Threading

All Git operations run on a dedicated background thread (`jvn-vcs-worker`, daemon) to keep the UI responsive:
- Operations are queued via `ExecutorService`
- Results are marshaled back to the JavaFX Application Thread via `Platform.runLater()`
- The `busy` flag prevents concurrent operations

---

## Button Styling

All toolbar buttons use a consistent icon-button pattern:
- CSS class: `vcs-icon-btn`
- Icon region with CSS classes: `vcs-icon` + specific icon class
- Tooltips on all buttons for discoverability

---

## Keyboard Shortcuts

| Key | Context | Action |
|-----|---------|--------|
| **Enter** | Commit message field | Commit all changes |
| **Enter** | Changed files list | Open selected file |

---

## Related Docs

- [Sidebar Utilities Overview](sidebar-utilities.md) — all 14 sidebar panels
- [Version Control Guide](../project-setup/version-control.md) — Git workflow documentation
- [Project Explorer](sidebar-project-explorer.md) — file tree navigation
