# Version Control (Git)

JVN includes built-in project version-control tooling for team workflows.

Prerequisites:
- `git` installed and available on `PATH`

## Where It Is Integrated

1. **New Project Wizard**
   - Initialize Git repository
   - Add managed `.gitignore` defaults
   - Optional initial commit

2. **Editor Version Control Panel**
   - Open from menu: `Version Control -> Open Version Control`
   - Shortcut: `Cmd/Ctrl+Shift+G`
   - Or add panel via side-tab `+` chooser

## Panel Operations

- `Refresh`: reads branch/sync/change status
- `Init Repo`: initializes repo and writes managed Git defaults
- `Commit All`: stages all changes and commits with provided message
- `Pull --rebase`: pulls with `--rebase --autostash`
- `Push`: pushes current branch

Changed files are listed and can be opened directly by double-clicking an entry.

## Managed Defaults

### `.gitignore`

Managed block includes rules for:
- OS/editor files
- Gradle/build output
- local JVN build cache (`.jvn-gradle-user-home`)
- runtime save files (`save/`)

## Collaboration Recommendations

JVN uses `stable` as the integration branch. Treat it as the branch that should always be buildable and suitable for normal editor/runtime use.

Use short-lived topic branches for work in progress:

- `feature/<short-name>` for new editor/runtime/scripting features
- `fix/<short-name>` for bug fixes and regressions
- `perf/<short-name>` for performance, memory, startup, or build-speed work

Merge topic branches back to `stable` only after the change has been reviewed or manually checked and the relevant build/test command has passed. Prefer small, focused branches over long-running mixed branches.

Recommended flow:

```bash
git switch stable
git pull --rebase
git switch -c feature/my-change
# edit, build, test, commit
git push -u origin feature/my-change
```

Then merge into `stable` when the branch is ready.

Additional recommendations:

1. Pull before starting work and before pushing.
2. Keep commits scoped (script changes separate from layout/menu changes when possible).
3. Keep binary assets (images, audio, video) reasonably sized; consider compressing large files before committing.
4. Resolve merge conflicts in `.vns`, `.menu`, `.layout`, and `.timeline` promptly.

## Troubleshooting

- If `Init Repo` fails with tool errors, verify `git --version` in terminal.
- If commit fails due identity, configure Git user:
  - `git config --global user.name "Your Name"`
  - `git config --global user.email "you@example.com"`
- If push fails due missing upstream, set it once:
  - `git push -u origin <branch>`
