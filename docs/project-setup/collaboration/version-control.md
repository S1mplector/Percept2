# Version Control (Git)

JVN includes built-in project version-control tooling for team workflows.

The editor's Version Control panel talks to Git repositories directly through
[JGit](https://www.eclipse.org/jgit/) — no `git` executable, `gh` CLI, or other external binary is
required on `PATH`. See [Sidebar — Version Control](../../editor/sidebars/right/sidebar-version-control.md)
for the full panel reference, including GitHub sign-in and repository creation.

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

## GitHub Sign-In And Remote Creation

When a project has no remote configured, the panel offers two ways to connect a GitHub repository:

- **Create GitHub Repository** — creates a new repo on your GitHub account via the GitHub REST API,
  sets it as `origin`, and pushes. Requires signing in first (device flow or a personal access
  token); see [GitHub Sign-In](../../editor/sidebars/right/sidebar-version-control.md#github-sign-in).
- **Add Remote Manually** — paste an existing repository URL (GitHub, GitLab, Bitbucket, or any Git
  host) to use as `origin`.

Signing in stores a GitHub token using the current OS's native credential store (Windows Credential
Manager, macOS Keychain, or Linux Secret Service) when available, falling back to a locally
encrypted file otherwise. The token is only used to authenticate Git operations and GitHub API calls
made by the editor; it is never written into project files or committed.

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

- If commit fails due to missing identity, configure Git user globally (JGit reads the same global
  `.gitconfig` a command-line `git` would use):
  - `git config --global user.name "Your Name"`
  - `git config --global user.email "you@example.com"`
- If push fails due missing upstream, set it once:
  - `git push -u origin <branch>`
- If GitHub sign-in or repo creation fails, check the panel's log area for the GitHub API error
  message. A `401`/token-rejected error usually means the stored token expired or was revoked; use
  **Change** to sign in again.
- If a GitHub token seems "lost" after an OS update or profile move, note that it is stored in the
  OS-native credential store (Windows Credential Manager, macOS Keychain, or Linux Secret Service),
  not in the project or a dotfile — it does not travel with the project directory.
