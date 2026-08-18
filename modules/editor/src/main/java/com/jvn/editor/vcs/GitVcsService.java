package com.jvn.editor.vcs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.ChainingCredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.transport.SshSessionFactory;

/**
 * Local Git integration service for editor workflows, backed by JGit (no external git binary required).
 */
public class GitVcsService {
  private static final Logger log = Logger.getLogger(GitVcsService.class.getName());
  private static final String GITIGNORE_BLOCK_START = "# --- JVN Git Defaults (managed) BEGIN ---";
  private static final String GITIGNORE_BLOCK_END = "# --- JVN Git Defaults (managed) END ---";

  private static final String DEFAULT_GITIGNORE_BLOCK = String.join("\n",
      "# OS/editor",
      ".DS_Store",
      "Thumbs.db",
      "*.swp",
      "*.swo",
      ".idea/",
      ".vscode/",
      "",
      "# Gradle/build",
      ".gradle/",
      "build/",
      "**/build/",
      ".jvn-gradle-user-home/",
      "",
      "# Runtime/editor generated",
      "save/"
  );

  static {
    SshdSessionFactory sshFactory = new SshdSessionFactoryBuilder()
        .setPreferredAuthentications("publickey")
        .setHomeDirectory(new File(System.getProperty("user.home", ".")))
        .setSshDirectory(new File(System.getProperty("user.home", "."), ".ssh"))
        .build(null);
    SshSessionFactory.setInstance(sshFactory);
  }

  private final GitHubTokenStore githubTokenStore = new GitHubTokenStore();

  public GitVcsService() {
  }

  /**
   * Builds the credentials provider used for HTTPS git operations: a saved GitHub sign-in
   * token (if any) tried first, falling back to {@code ~/.netrc}/{@code ~/_netrc} for other hosts.
   */
  private CredentialsProvider credentialsProvider() {
    Optional<String> token = githubTokenStore.loadToken();
    if (token.isPresent()) {
      return new ChainingCredentialsProvider(
          new UsernamePasswordCredentialsProvider("x-access-token", token.get()),
          new NetRCCredentialsProvider());
    }
    return new NetRCCredentialsProvider();
  }

  public boolean isGitAvailable() {
    return true;
  }

  public boolean isRepository(File root) {
    if (root == null || !root.isDirectory()) return false;
    try {
      File gitDir = new FileRepositoryBuilder()
          .setWorkTree(root)
          .findGitDir(root)
          .getGitDir();
      return gitDir != null && gitDir.isDirectory();
    } catch (Exception ex) {
      return false;
    }
  }

  public void bootstrapRepository(File root,
                                  boolean createInitialCommit,
                                  String initialCommitMessage) throws GitVcsException {
    requireDirectory(root);

    initRepositoryIfNeeded(root);
    installDefaultTrackingFiles(root);

    if (createInitialCommit) {
      String message = (initialCommitMessage == null || initialCommitMessage.isBlank())
          ? "Initialize JVN project scaffold"
          : initialCommitMessage.trim();
      commitAll(root, message);
    }
  }

  public void installDefaultTrackingFiles(File root) throws GitVcsException {
    requireDirectory(root);

    try {
      writeManagedBlock(
          root.toPath().resolve(".gitignore"),
          GITIGNORE_BLOCK_START,
          GITIGNORE_BLOCK_END,
          DEFAULT_GITIGNORE_BLOCK
      );
    } catch (IOException ex) {
      throw new GitVcsException("Failed to write Git tracking files: " + ex.getMessage());
    }
  }

  public RepositoryStatus getRepositoryStatus(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      Repository repo = git.getRepository();
      Status status = git.status().call();
      List<StatusEntry> entries = toStatusEntries(status);

      String branch = repo.getBranch();
      if (branch == null || branch.isBlank()) branch = "unknown";

      String upstream = null;
      int ahead = 0;
      int behind = 0;
      BranchTrackingStatus tracking = BranchTrackingStatus.of(repo, branch);
      if (tracking != null) {
        upstream = tracking.getRemoteTrackingBranch();
        ahead = tracking.getAheadCount();
        behind = tracking.getBehindCount();
      }

      return new RepositoryStatus(branch, upstream, ahead, behind, entries);
    } catch (IOException | GitAPIException ex) {
      throw new GitVcsException("Failed to read git status.", failureResult("status", ex));
    }
  }

  public PreflightResult preflight(File root) throws GitVcsException {
    RepositoryStatus status = getRepositoryStatus(root);
    boolean hasRemote = hasRemote(root);
    String remoteUrl = hasRemote ? getRemoteUrl(root) : null;

    boolean remoteReachable = false;
    String remoteCheckError = null;
    if (!hasRemote) {
      remoteCheckError = "No remote configured.";
    } else {
      try (Git git = open(root)) {
        git.lsRemote().setRemote("origin").setHeads(true).setCredentialsProvider(credentialsProvider()).call();
        remoteReachable = true;
      } catch (Exception ex) {
        remoteCheckError = describeFailure(ex);
      }
    }

    boolean credentialsOk = !isCredentialFailure(remoteCheckError);
    String credentialIssue = credentialsOk ? null : remoteCheckError;

    return new PreflightResult(
        status.branch(),
        remoteUrl,
        hasRemote,
        status.ahead(),
        status.behind(),
        status.entries().size(),
        remoteReachable,
        remoteCheckError,
        credentialsOk,
        credentialIssue);
  }

  public CommandResult commitAll(File root, String message) throws GitVcsException {
    requireRepository(root);
    if (message == null || message.isBlank()) {
      throw new GitVcsException("Commit message cannot be empty.");
    }
    String trimmed = message.trim();

    try (Git git = open(root)) {
      git.add().addFilepattern(".").call();
      git.add().setUpdate(true).addFilepattern(".").call();

      Status status = git.status().call();
      if (!status.hasUncommittedChanges() && status.getAdded().isEmpty()
          && status.getChanged().isEmpty() && status.getRemoved().isEmpty()) {
        return new CommandResult(commandLine("commit", "-m", trimmed), 0, "Nothing to commit.");
      }

      RevCommit commit = git.commit().setMessage(trimmed).call();
      return new CommandResult(commandLine("commit", "-m", trimmed), 0,
          "Created commit " + abbreviate(commit) + ".");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to create commit.", failureResult("commit", ex));
    }
  }

  public CommandResult pullRebase(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      Status statusBefore = git.status().call();
      boolean dirty = statusBefore.hasUncommittedChanges();
      RevCommit stashed = null;
      if (dirty) {
        stashed = git.stashCreate().call();
      }
      String stashRestoreWarning = null;
      try {
        PullResult result = git.pull()
            .setRebase(true)
            .setCredentialsProvider(credentialsProvider())
            .call();
        if (!result.isSuccessful()) {
          throw new GitVcsException("Pull with rebase did not complete successfully.",
              new CommandResult(commandLine("pull", "--rebase"), 1, String.valueOf(result)));
        }
      } finally {
        if (stashed != null) {
          try {
            git.stashApply().call();
            git.stashDrop().call();
          } catch (GitAPIException ex) {
            stashRestoreWarning = "Your stashed local changes could not be reapplied automatically "
                + "(" + describeFailure(ex) + "). They are still saved in the stash list "
                + "(Restore Shelf) — resolve manually before continuing.";
            log.warning("Autostash restore failed; stash left in place: " + ex.getMessage());
          }
        }
      }
      String message = "Pull with rebase completed."
          + (dirty ? " Local changes were temporarily shelved and restored." : "");
      if (stashRestoreWarning != null) {
        throw new GitVcsException(stashRestoreWarning,
            new CommandResult(commandLine("pull", "--rebase"), 0, message));
      }
      return new CommandResult(commandLine("pull", "--rebase"), 0, message);
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to pull with rebase.", failureResult("pull --rebase", ex));
    }
  }

  public CommandResult push(File root) throws GitVcsException {
    requireRepository(root);
    return doPush(root, null);
  }

  public CommandResult fetch(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      git.fetch()
          .setRemoveDeletedRefs(true)
          .setCredentialsProvider(credentialsProvider())
          .call();
      return new CommandResult(commandLine("fetch", "--all", "--prune"), 0, "Fetched remote state.");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to fetch remote state.", failureResult("fetch", ex));
    }
  }

  // --- Conflict detection ---

  public boolean hasConflicts(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      return !git.status().call().getConflicting().isEmpty();
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to inspect conflicts.", failureResult("status", ex));
    }
  }

  public List<String> getConflictedFiles(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      return new ArrayList<>(git.status().call().getConflicting());
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to inspect conflicts.", failureResult("status", ex));
    }
  }

  // --- Stash support ---

  public CommandResult stash(File root, String message) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      var cmd = git.stashCreate();
      if (message != null && !message.isBlank()) cmd.setWorkingDirectoryMessage(message.trim());
      RevCommit stash = cmd.call();
      if (stash == null) {
        return new CommandResult(commandLine("stash", "push"), 0, "No local changes to save.");
      }
      return new CommandResult(commandLine("stash", "push"), 0, "Saved stash " + abbreviate(stash) + ".");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to stash changes.", failureResult("stash push", ex));
    }
  }

  public CommandResult stashPop(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      var stashes = git.stashList().call();
      if (stashes.isEmpty()) {
        throw new GitVcsException("No stash entries found.");
      }
      git.stashApply().call();
      git.stashDrop().call();
      return new CommandResult(commandLine("stash", "pop"), 0, "Restored stashed changes.");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to pop stash.", failureResult("stash pop", ex));
    }
  }

  public List<String> stashList(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      List<String> lines = new ArrayList<>();
      int index = 0;
      for (RevCommit stash : git.stashList().call()) {
        lines.add("stash@{" + index + "}: " + stash.getShortMessage());
        index++;
      }
      return lines;
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to list stashes.", failureResult("stash list", ex));
    }
  }

  // --- Remote validation ---

  public boolean hasRemote(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      return !git.getRepository().getConfig().getSubsections("remote").isEmpty();
    } catch (Exception ex) {
      throw new GitVcsException("Failed to inspect remotes.", failureResult("remote", ex));
    }
  }

  public String getRemoteUrl(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      return git.getRepository().getConfig().getString("remote", "origin", "url");
    } catch (Exception ex) {
      throw new GitVcsException("Failed to read remote URL.", failureResult("remote get-url origin", ex));
    }
  }

  public CommandResult addRemote(File root, String name, String url) throws GitVcsException {
    requireRepository(root);
    if (name == null || name.isBlank()) name = "origin";
    if (url == null || url.isBlank()) throw new GitVcsException("Remote URL cannot be empty.");
    String trimmedName = name.trim();
    String trimmedUrl = url.trim();
    try (Git git = open(root)) {
      git.remoteAdd().setName(trimmedName).setUri(new URIish(trimmedUrl)).call();
      return new CommandResult(commandLine("remote", "add", trimmedName, trimmedUrl), 0,
          "Added remote '" + trimmedName + "'.");
    } catch (Exception ex) {
      throw new GitVcsException("Failed to add remote '" + trimmedName + "'.",
          failureResult("remote add " + trimmedName + " " + trimmedUrl, ex));
    }
  }

  public CommandResult removeRemote(File root, String name) throws GitVcsException {
    requireRepository(root);
    String trimmedName = (name == null || name.isBlank()) ? "origin" : name.trim();
    try (Git git = open(root)) {
      git.remoteRemove().setRemoteName(trimmedName).call();
      return new CommandResult(commandLine("remote", "remove", trimmedName), 0,
          "Removed remote '" + trimmedName + "'.");
    } catch (Exception ex) {
      throw new GitVcsException("Failed to remove remote '" + trimmedName + "'.",
          failureResult("remote remove " + trimmedName, ex));
    }
  }

  // --- Diff support ---

  public String diff(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      List<DiffEntry> entries = git.diff().call();
      return formatDiffStat(entries);
    } catch (GitAPIException ex) {
      return "";
    }
  }

  public String diffFile(File root, String relativePath) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      git.diff()
          .setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(relativePath))
          .setOutputStream(out)
          .call();
      return out.toString(StandardCharsets.UTF_8);
    } catch (GitAPIException | IOException ex) {
      return "";
    }
  }

  public String diffCached(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      List<DiffEntry> entries = git.diff().setCached(true).call();
      return formatDiffStat(entries);
    } catch (GitAPIException ex) {
      return "";
    }
  }

  // --- Log retrieval ---

  public List<String> log(File root, int count) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      List<String> lines = new ArrayList<>();
      for (RevCommit commit : git.log().setMaxCount(Math.max(1, count)).call()) {
        lines.add(abbreviate(commit) + " " + commit.getShortMessage());
      }
      return lines;
    } catch (GitAPIException ex) {
      return List.of();
    }
  }

  public String changeGraph(File root, int count) throws GitVcsException {
    requireRepository(root);
    List<ChangeGraphEntry> entries = changeGraphEntries(root, count);
    StringBuilder sb = new StringBuilder();
    for (ChangeGraphEntry entry : entries) {
      if (sb.length() > 0) sb.append('\n');
      sb.append(entry.graphPrefix()).append(entry.hash()).append(' ').append(entry.subject());
    }
    return sb.toString();
  }

  public List<ChangeGraphEntry> changeGraphEntries(File root, int count) throws GitVcsException {
    requireRepository(root);
    int safeCount = Math.max(1, Math.min(250, count));

    try (Repository repo = openRepository(root);
         PlotWalk walk = new PlotWalk(repo)) {
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix("refs/heads/", "refs/tags/", "refs/remotes/")) {
        try {
          walk.markStart(walk.parseCommit(ref.getObjectId()));
        } catch (Exception ignored) {
          // Skip refs that don't resolve to a commit (e.g. annotated tags handled below, dangling refs).
        }
      }

      PlotCommitList<PlotLane> commitList = new PlotCommitList<>();
      commitList.source(walk);
      commitList.fillTo(safeCount);

      Map<ObjectId, List<String>> refsByCommit = collectRefLabels(repo);

      List<ChangeGraphEntry> entries = new ArrayList<>();
      for (int i = 0; i < commitList.size(); i++) {
        PlotCommit<PlotLane> commit = commitList.get(i);
        String graphPrefix = buildGraphPrefix(commit, commitList, i);
        String hash = commit.abbreviate(7).name();
        List<String> refs = refsByCommit.getOrDefault(commit.getId(), List.of());
        PersonIdent author = commit.getAuthorIdent();
        entries.add(new ChangeGraphEntry(
            graphPrefix,
            hash,
            String.join(", ", refs),
            commit.getShortMessage(),
            author == null ? "unknown" : author.getName()));
      }
      return entries;
    } catch (IOException ex) {
      throw new GitVcsException("Failed to read change graph.", failureResult("log --graph", ex));
    }
  }

  // --- Branch operations ---

  public String getCurrentBranch(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      String branch = git.getRepository().getBranch();
      return branch == null || branch.isBlank() ? "unknown" : branch;
    } catch (IOException ex) {
      return "unknown";
    }
  }

  public List<String> listBranches(File root) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      List<String> branches = new ArrayList<>();
      for (Ref ref : git.branchList().call()) {
        String name = ref.getName();
        if (name.startsWith("refs/heads/")) name = name.substring("refs/heads/".length());
        if (!name.isBlank()) branches.add(name);
      }
      return branches;
    } catch (GitAPIException ex) {
      return List.of();
    }
  }

  public CommandResult switchBranch(File root, String branchName) throws GitVcsException {
    requireRepository(root);
    if (branchName == null || branchName.isBlank()) throw new GitVcsException("Branch name cannot be empty.");
    String trimmed = branchName.trim();
    try (Git git = open(root)) {
      git.checkout().setName(resolveLocalBranchRef(git, trimmed)).call();
      return new CommandResult(commandLine("switch", trimmed), 0, "Switched to branch '" + trimmed + "'.");
    } catch (CheckoutConflictException ex) {
      throw new GitVcsException("Failed to switch to branch '" + branchName + "': local changes would be overwritten.",
          failureResult("switch " + trimmed, ex), true);
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to switch to branch '" + branchName + "'.", failureResult("switch " + trimmed, ex));
    }
  }

  public CommandResult createBranch(File root, String branchName) throws GitVcsException {
    requireRepository(root);
    if (branchName == null || branchName.isBlank()) throw new GitVcsException("Branch name cannot be empty.");
    String trimmed = branchName.trim();
    try (Git git = open(root)) {
      git.checkout().setCreateBranch(true).setName(trimmed)
          .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
          .call();
      return new CommandResult(commandLine("switch", "-c", trimmed), 0, "Created and switched to branch '" + trimmed + "'.");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to create branch '" + branchName + "'.", failureResult("switch -c " + trimmed, ex));
    }
  }

  /**
   * Resolves a branch name to an unambiguous ref. JGit's checkout treats a bare
   * "remote/name"-shaped argument as remote-tracking shorthand, which fails when no such
   * remote ref exists even though a local branch with that literal name does. Fully
   * qualifying the name to refs/heads/... when a matching local branch exists sidesteps that.
   */
  private String resolveLocalBranchRef(Git git, String name) {
    try {
      Ref localRef = git.getRepository().exactRef("refs/heads/" + name);
      return localRef != null ? "refs/heads/" + name : name;
    } catch (IOException ex) {
      return name;
    }
  }

  // --- Stage/unstage individual files ---

  public CommandResult stageFile(File root, String path) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      git.add().addFilepattern(path).call();
      return new CommandResult(commandLine("add", "--", path), 0, "Staged " + path + ".");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to stage file: " + path, failureResult("add -- " + path, ex));
    }
  }

  public CommandResult unstageFile(File root, String path) throws GitVcsException {
    requireRepository(root);
    try (Git git = open(root)) {
      git.reset().addPath(path).call();
      return new CommandResult(commandLine("restore", "--staged", "--", path), 0, "Unstaged " + path + ".");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to unstage file: " + path, failureResult("restore --staged -- " + path, ex));
    }
  }

  public CommandResult discardFile(File root, String path) throws GitVcsException {
    requireRepository(root);
    if (path == null || path.isBlank()) throw new GitVcsException("File path cannot be empty.");
    try (Git git = open(root)) {
      if (isUntracked(git, path)) {
        git.clean().setPaths(Set.of(path)).call();
      } else {
        git.checkout().addPath(path).call();
      }
      return new CommandResult(commandLine("restore", "--", path), 0, "Discarded changes in " + path + ".");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to discard changes in: " + path, failureResult("restore -- " + path, ex));
    }
  }

  // --- Hardened push with upstream check ---

  public CommandResult pushSafe(File root) throws GitVcsException {
    requireRepository(root);
    if (!hasRemote(root)) {
      throw new GitVcsException("No remote configured. Add a remote before pushing.");
    }
    String branch = getCurrentBranch(root);
    try (Git git = open(root)) {
      BranchTrackingStatus tracking = BranchTrackingStatus.of(git.getRepository(), branch);
      if (tracking == null) {
        CommandResult result = doPush(root, branch);
        StoredConfig config = git.getRepository().getConfig();
        config.setString("branch", branch, "remote", "origin");
        config.setString("branch", branch, "merge", "refs/heads/" + branch);
        config.save();
        return result;
      }
    } catch (IOException ex) {
      throw new GitVcsException("Failed to push (setting upstream).", failureResult("push -u origin " + branch, ex));
    }
    return doPush(root, null);
  }

  public static boolean isAuthCooldownActive(long lastFailureMs, long nowMs, long cooldownMs) {
    if (lastFailureMs < 0L) return false;
    return nowMs - lastFailureMs < cooldownMs;
  }

  public static boolean isCredentialFailure(String message) {
    if (message == null) return false;
    String lower = message.toLowerCase(Locale.ROOT);
    return lower.contains("authentication failed")
        || lower.contains("permission")
        || lower.contains("could not read username")
        || lower.contains("could not read password")
        || lower.contains("403")
        || lower.contains("auth fail")
        || lower.contains("not authorized")
        || lower.contains("invalid credentials")
        || lower.contains("credentials required");
  }

  private CommandResult doPush(File root, String setUpstreamBranch) throws GitVcsException {
    try (Git git = open(root)) {
      var pushCmd = git.push().setCredentialsProvider(credentialsProvider());
      if (setUpstreamBranch != null) {
        pushCmd.add(setUpstreamBranch);
        pushCmd.setRemote("origin");
      }
      Iterable<PushResult> results = pushCmd.call();
      List<String> rejections = new ArrayList<>();
      for (PushResult result : results) {
        for (RemoteRefUpdate update : result.getRemoteUpdates()) {
          RemoteRefUpdate.Status status = update.getStatus();
          if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
            rejections.add(update.getRemoteName() + ": " + status
                + (update.getMessage() != null ? " (" + update.getMessage() + ")" : ""));
          }
        }
      }
      if (!rejections.isEmpty()) {
        throw new GitVcsException("Failed to push changes.",
            new CommandResult(commandLine("push"), 1, String.join("\n", rejections)));
      }
      return new CommandResult(commandLine("push"), 0, "Pushed to origin.");
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to push changes.", failureResult("push", ex));
    }
  }

  private boolean isUntracked(Git git, String path) throws GitVcsException {
    try {
      return git.status().call().getUntracked().contains(path);
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to inspect status for: " + path, failureResult("status -- " + path, ex));
    }
  }

  private void requireDirectory(File root) throws GitVcsException {
    if (root == null || !root.exists() || !root.isDirectory()) {
      throw new GitVcsException("Project root is not a valid directory.");
    }
  }

  private void requireRepository(File root) throws GitVcsException {
    requireDirectory(root);
    if (!isRepository(root)) {
      throw new GitVcsException("No Git repository found at: " + root.getAbsolutePath());
    }
  }

  private void initRepositoryIfNeeded(File root) throws GitVcsException {
    if (isRepository(root)) return;
    try (Git git = Git.init().setDirectory(root).setInitialBranch("main").call()) {
      // Repository created; nothing further to do.
    } catch (GitAPIException ex) {
      throw new GitVcsException("Failed to initialize git repository.", failureResult("init --initial-branch=main", ex));
    }
  }

  private Git open(File root) throws GitVcsException {
    try {
      return Git.open(root);
    } catch (IOException ex) {
      throw new GitVcsException("No Git repository found at: " + root.getAbsolutePath());
    }
  }

  private Repository openRepository(File root) throws GitVcsException {
    try {
      return new FileRepositoryBuilder()
          .setWorkTree(root)
          .findGitDir(root)
          .build();
    } catch (IOException ex) {
      throw new GitVcsException("No Git repository found at: " + root.getAbsolutePath());
    }
  }

  private void writeManagedBlock(Path file,
                                 String markerStart,
                                 String markerEnd,
                                 String block) throws IOException {
    String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    String normalized = normalizeEol(existing);

    String managed = markerStart + "\n" + block.trim() + "\n" + markerEnd;
    String updated;

    int start = normalized.indexOf(markerStart);
    int end = normalized.indexOf(markerEnd);
    if (start >= 0 && end > start) {
      int afterEnd = end + markerEnd.length();
      if (afterEnd < normalized.length() && normalized.charAt(afterEnd) == '\n') afterEnd++;
      updated = normalized.substring(0, start) + managed + "\n" + normalized.substring(afterEnd);
    } else {
      if (!normalized.isBlank() && !normalized.endsWith("\n")) normalized += "\n";
      updated = normalized + managed + "\n";
    }

    Files.writeString(file, updated, StandardCharsets.UTF_8);
  }

  private String normalizeEol(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n');
  }

  private List<StatusEntry> toStatusEntries(Status status) {
    Map<String, char[]> codes = new LinkedHashMap<>();
    applyCode(codes, status.getAdded(), 0, 'A');
    applyCode(codes, status.getChanged(), 0, 'M');
    applyCode(codes, status.getRemoved(), 0, 'D');
    applyCode(codes, status.getModified(), 1, 'M');
    applyCode(codes, status.getMissing(), 1, 'D');
    applyCode(codes, status.getUntracked(), 0, '?');
    applyCode(codes, status.getUntracked(), 1, '?');
    applyCode(codes, status.getConflicting(), 0, 'U');
    applyCode(codes, status.getConflicting(), 1, 'U');

    List<StatusEntry> entries = new ArrayList<>();
    for (Map.Entry<String, char[]> e : codes.entrySet()) {
      char[] xy = e.getValue();
      String index = xy[0] == 0 ? " " : String.valueOf(xy[0]);
      String workTree = xy[1] == 0 ? " " : String.valueOf(xy[1]);
      entries.add(new StatusEntry(index, workTree, e.getKey()));
    }
    return entries;
  }

  private void applyCode(Map<String, char[]> codes, Set<String> paths, int position, char code) {
    for (String path : paths) {
      char[] xy = codes.computeIfAbsent(path, p -> new char[2]);
      xy[position] = code;
    }
  }

  private String formatDiffStat(List<DiffEntry> entries) {
    if (entries.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (DiffEntry entry : entries) {
      String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
          ? entry.getOldPath()
          : entry.getNewPath();
      sb.append(' ').append(path).append(" | ").append(entry.getChangeType().name().toLowerCase(Locale.ROOT)).append('\n');
    }
    sb.append(entries.size()).append(entries.size() == 1 ? " file changed" : " files changed");
    return sb.toString();
  }

  private Map<ObjectId, List<String>> collectRefLabels(Repository repo) throws IOException {
    Map<ObjectId, List<String>> map = new LinkedHashMap<>();
    for (Ref ref : repo.getRefDatabase().getRefsByPrefix("refs/heads/", "refs/remotes/", "refs/tags/")) {
      ObjectId target = ref.getPeeledObjectId() != null ? ref.getPeeledObjectId() : ref.getObjectId();
      if (target == null) continue;
      String name = ref.getName();
      if (name.startsWith("refs/heads/")) name = name.substring("refs/heads/".length());
      else if (name.startsWith("refs/remotes/")) name = name.substring("refs/remotes/".length());
      else if (name.startsWith("refs/tags/")) name = "tag: " + name.substring("refs/tags/".length());
      map.computeIfAbsent(target, k -> new ArrayList<>()).add(name);
    }
    return map;
  }

  private String buildGraphPrefix(PlotCommit<PlotLane> commit, PlotCommitList<PlotLane> list, int index) {
    int commitLane = commit.getLane() == null ? 0 : commit.getLane().getPosition();
    java.util.Set<Integer> passingLanes = new java.util.TreeSet<>();
    passingLanes.add(commitLane);

    List<PlotLane> passing = new ArrayList<>();
    list.findPassingThrough(commit, passing);
    int maxLane = commitLane;
    for (PlotLane lane : passing) {
      passingLanes.add(lane.getPosition());
      maxLane = Math.max(maxLane, lane.getPosition());
    }

    StringBuilder sb = new StringBuilder();
    for (int lane = 0; lane <= maxLane; lane++) {
      if (lane == commitLane) {
        sb.append('*');
      } else if (passingLanes.contains(lane)) {
        sb.append('|');
      } else {
        sb.append(' ');
      }
      sb.append(' ');
    }
    return sb.toString();
  }

  private String abbreviate(RevCommit commit) {
    return commit.abbreviate(7).name();
  }

  private String describeFailure(Exception ex) {
    if (ex instanceof InvalidRemoteException || ex instanceof RefNotAdvertisedException) {
      return "Could not reach remote repository.";
    }
    String message = ex.getMessage();
    return message == null || message.isBlank() ? "Could not reach remote repository." : message;
  }

  private CommandResult failureResult(String subcommand, Exception ex) {
    String message = ex.getMessage();
    return new CommandResult(commandLine(subcommand.split(" ")), 1,
        message == null || message.isBlank() ? ex.getClass().getSimpleName() : message);
  }

  private List<String> commandLine(String... parts) {
    List<String> line = new ArrayList<>();
    line.add("git");
    for (String part : parts) line.add(part);
    return line;
  }

  public record CommandResult(List<String> command, int exitCode, String output) {
    public boolean success() {
      return exitCode == 0;
    }

    public String commandLine() {
      if (command == null || command.isEmpty()) return "";
      return String.join(" ", command);
    }
  }

  public record StatusEntry(String indexStatus, String workTreeStatus, String path) {
    public String code() {
      return indexStatus + workTreeStatus;
    }

    public boolean isUntracked() {
      return "?".equals(indexStatus) && "?".equals(workTreeStatus);
    }
  }

  public record ChangeGraphEntry(String graphPrefix,
                                 String hash,
                                 String refs,
                                 String subject,
                                 String author) {}

  public record PreflightResult(String branch,
                                String remoteUrl,
                                boolean hasRemote,
                                int ahead,
                                int behind,
                                int changedFileCount,
                                boolean remoteReachable,
                                String remoteCheckError,
                                boolean credentialsOk,
                                String credentialIssue) {}

  public record RepositoryStatus(String branch,
                                 String upstream,
                                 int ahead,
                                 int behind,
                                 List<StatusEntry> entries) {
    public boolean clean() {
      return entries == null || entries.isEmpty();
    }
  }

  public static class GitVcsException extends Exception {
    private final CommandResult result;
    private final boolean localChangesConflict;

    public GitVcsException(String message) {
      super(message);
      this.result = null;
      this.localChangesConflict = false;
    }

    public GitVcsException(String message, CommandResult result) {
      this(message, result, false);
    }

    public GitVcsException(String message, CommandResult result, boolean localChangesConflict) {
      super(formatMessage(message, result));
      this.result = result;
      this.localChangesConflict = localChangesConflict;
    }

    public CommandResult getResult() {
      return result;
    }

    public boolean hasLocalChangesConflict() {
      return localChangesConflict;
    }

    private static String formatMessage(String message, CommandResult result) {
      if (result == null) return message;
      StringBuilder sb = new StringBuilder();
      sb.append(message == null ? "Git operation failed." : message);
      if (result.commandLine() != null && !result.commandLine().isBlank()) {
        sb.append("\nCommand: ").append(result.commandLine());
      }
      sb.append("\nExit: ").append(result.exitCode());
      if (result.output() != null && !result.output().isBlank()) {
        sb.append("\n").append(result.output());
      }
      return sb.toString();
    }
  }
}
