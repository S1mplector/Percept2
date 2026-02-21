package com.jvn.editor.vcs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Local Git/Git-LFS integration service for editor workflows.
 */
public class GitVcsService {
  private static final String GITIGNORE_BLOCK_START = "# --- JVN Git Defaults (managed) BEGIN ---";
  private static final String GITIGNORE_BLOCK_END = "# --- JVN Git Defaults (managed) END ---";
  private static final String GITATTR_BLOCK_START = "# --- JVN Git LFS Defaults (managed) BEGIN ---";
  private static final String GITATTR_BLOCK_END = "# --- JVN Git LFS Defaults (managed) END ---";

  private static final List<String> DEFAULT_LFS_PATTERNS = List.of(
      "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif", "*.bmp", "*.psd",
      "*.ogg", "*.wav", "*.mp3", "*.flac", "*.m4a", "*.aac",
      "*.mp4", "*.webm", "*.mov",
      "*.ttf", "*.otf"
  );

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

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  private final Duration commandTimeout;

  public GitVcsService() {
    this(DEFAULT_TIMEOUT);
  }

  public GitVcsService(Duration commandTimeout) {
    this.commandTimeout = commandTimeout == null ? DEFAULT_TIMEOUT : commandTimeout;
  }

  public boolean isGitAvailable() {
    CommandResult result = execute(null, List.of("git", "--version"), true);
    return result.success();
  }

  public boolean isGitLfsAvailable() {
    CommandResult result = execute(null, List.of("git", "lfs", "version"), true);
    return result.success();
  }

  public boolean isRepository(File root) {
    if (root == null || !root.isDirectory()) return false;
    CommandResult result = execute(root, List.of("git", "rev-parse", "--is-inside-work-tree"), true);
    return result.success() && "true".equalsIgnoreCase(result.output().trim());
  }

  public void bootstrapRepository(File root,
                                  boolean enableLfs,
                                  boolean createInitialCommit,
                                  String initialCommitMessage) throws GitVcsException {
    requireDirectory(root);
    requireGitAvailable();
    if (enableLfs) requireGitLfsAvailable();

    initRepositoryIfNeeded(root);
    installDefaultTrackingFiles(root, enableLfs);

    if (enableLfs) {
      ensureSuccess(execute(root, List.of("git", "lfs", "install", "--local"), false),
          "Failed to install git-lfs hooks in repository.");
    }

    if (createInitialCommit) {
      String message = (initialCommitMessage == null || initialCommitMessage.isBlank())
          ? "Initialize JVN project scaffold"
          : initialCommitMessage.trim();
      commitAll(root, message);
    }
  }

  public void installDefaultTrackingFiles(File root, boolean includeLfsDefaults) throws GitVcsException {
    requireDirectory(root);

    try {
      writeManagedBlock(
          root.toPath().resolve(".gitignore"),
          GITIGNORE_BLOCK_START,
          GITIGNORE_BLOCK_END,
          DEFAULT_GITIGNORE_BLOCK
      );

      if (includeLfsDefaults) {
        writeManagedBlock(
            root.toPath().resolve(".gitattributes"),
            GITATTR_BLOCK_START,
            GITATTR_BLOCK_END,
            buildDefaultGitattributesBlock()
        );
      }
    } catch (IOException ex) {
      throw new GitVcsException("Failed to write Git tracking files: " + ex.getMessage());
    }
  }

  public RepositoryStatus getRepositoryStatus(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "status", "--porcelain", "--branch"), false);
    ensureSuccess(result, "Failed to read git status.");
    return parseStatus(result.output());
  }

  public CommandResult commitAll(File root, String message) throws GitVcsException {
    requireRepository(root);
    if (message == null || message.isBlank()) {
      throw new GitVcsException("Commit message cannot be empty.");
    }

    ensureSuccess(execute(root, List.of("git", "add", "-A"), false), "Failed to stage changes.");

    CommandResult staged = execute(root, List.of("git", "diff", "--cached", "--quiet"), true);
    if (staged.exitCode() == 0) {
      return new CommandResult(List.of("git", "commit", "-m", message.trim()), 0, "Nothing to commit.");
    }
    if (staged.exitCode() != 1) {
      throw new GitVcsException("Unable to inspect staged changes.", staged);
    }

    CommandResult commit = execute(root, List.of("git", "commit", "-m", message.trim()), false);
    ensureSuccess(commit, "Failed to create commit.");
    return commit;
  }

  public CommandResult pullRebase(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult pull = execute(root, List.of("git", "pull", "--rebase", "--autostash"), false);
    ensureSuccess(pull, "Failed to pull with rebase.");
    return pull;
  }

  public CommandResult push(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult push = execute(root, List.of("git", "push"), false);
    ensureSuccess(push, "Failed to push changes.");
    return push;
  }

  public CommandResult fetch(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult fetch = execute(root, List.of("git", "fetch", "--all", "--prune"), false);
    ensureSuccess(fetch, "Failed to fetch remote state.");
    return fetch;
  }

  // --- Conflict detection ---

  public boolean hasConflicts(File root) throws GitVcsException {
    requireRepository(root);
    RepositoryStatus status = getRepositoryStatus(root);
    for (StatusEntry e : status.entries()) {
      if ("U".equals(e.indexStatus()) || "U".equals(e.workTreeStatus()) ||
          ("D".equals(e.indexStatus()) && "D".equals(e.workTreeStatus())) ||
          ("A".equals(e.indexStatus()) && "A".equals(e.workTreeStatus()))) {
        return true;
      }
    }
    return false;
  }

  public List<String> getConflictedFiles(File root) throws GitVcsException {
    requireRepository(root);
    RepositoryStatus status = getRepositoryStatus(root);
    List<String> conflicts = new ArrayList<>();
    for (StatusEntry e : status.entries()) {
      if ("U".equals(e.indexStatus()) || "U".equals(e.workTreeStatus()) ||
          ("D".equals(e.indexStatus()) && "D".equals(e.workTreeStatus())) ||
          ("A".equals(e.indexStatus()) && "A".equals(e.workTreeStatus()))) {
        conflicts.add(e.path());
      }
    }
    return conflicts;
  }

  // --- Stash support ---

  public CommandResult stash(File root, String message) throws GitVcsException {
    requireRepository(root);
    List<String> cmd = message != null && !message.isBlank()
        ? List.of("git", "stash", "push", "-m", message.trim())
        : List.of("git", "stash", "push");
    CommandResult result = execute(root, cmd, false);
    ensureSuccess(result, "Failed to stash changes.");
    return result;
  }

  public CommandResult stashPop(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "stash", "pop"), false);
    ensureSuccess(result, "Failed to pop stash.");
    return result;
  }

  public List<String> stashList(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "stash", "list"), true);
    if (!result.success() || result.output().isBlank()) return List.of();
    return Arrays.asList(result.output().split("\\r?\\n"));
  }

  // --- Remote validation ---

  public boolean hasRemote(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "remote"), true);
    return result.success() && !result.output().isBlank();
  }

  public String getRemoteUrl(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "remote", "get-url", "origin"), true);
    return result.success() ? result.output().trim() : null;
  }

  public boolean isGhCliAvailable() {
    CommandResult result = execute(null, List.of("gh", "--version"), true);
    return result.success();
  }

  public boolean isGhCliAuthenticated() {
    CommandResult result = execute(null, List.of("gh", "auth", "status"), true);
    return result.success();
  }

  public CommandResult createGitHubRepo(File root, String repoName, boolean isPrivate) throws GitVcsException {
    requireRepository(root);
    if (repoName == null || repoName.isBlank()) throw new GitVcsException("Repository name cannot be empty.");
    String visibility = isPrivate ? "--private" : "--public";
    CommandResult result = execute(root, List.of("gh", "repo", "create", repoName.trim(), visibility, "--source=.", "--remote=origin", "--push"), false);
    ensureSuccess(result, "Failed to create GitHub repository.");
    return result;
  }

  public CommandResult addRemote(File root, String name, String url) throws GitVcsException {
    requireRepository(root);
    if (name == null || name.isBlank()) name = "origin";
    if (url == null || url.isBlank()) throw new GitVcsException("Remote URL cannot be empty.");
    CommandResult result = execute(root, List.of("git", "remote", "add", name.trim(), url.trim()), false);
    ensureSuccess(result, "Failed to add remote '" + name + "'.");
    return result;
  }

  // --- Diff support ---

  public String diff(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "diff", "--stat"), true);
    return result.success() ? result.output() : "";
  }

  public String diffFile(File root, String relativePath) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "diff", "--", relativePath), true);
    return result.success() ? result.output() : "";
  }

  public String diffCached(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "diff", "--cached", "--stat"), true);
    return result.success() ? result.output() : "";
  }

  // --- Log retrieval ---

  public List<String> log(File root, int count) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "log", "--oneline", "-" + Math.max(1, count)), true);
    if (!result.success() || result.output().isBlank()) return List.of();
    return Arrays.asList(result.output().split("\\r?\\n"));
  }

  // --- Branch operations ---

  public String getCurrentBranch(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "branch", "--show-current"), true);
    return result.success() ? result.output().trim() : "unknown";
  }

  public List<String> listBranches(File root) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "branch", "--list", "--no-color"), true);
    if (!result.success() || result.output().isBlank()) return List.of();
    List<String> branches = new ArrayList<>();
    for (String line : result.output().split("\\r?\\n")) {
      String name = line.trim();
      if (name.startsWith("* ")) name = name.substring(2).trim();
      if (!name.isBlank()) branches.add(name);
    }
    return branches;
  }

  public CommandResult switchBranch(File root, String branchName) throws GitVcsException {
    requireRepository(root);
    if (branchName == null || branchName.isBlank()) throw new GitVcsException("Branch name cannot be empty.");
    CommandResult result = execute(root, List.of("git", "switch", branchName.trim()), false);
    ensureSuccess(result, "Failed to switch to branch '" + branchName + "'.");
    return result;
  }

  public CommandResult createBranch(File root, String branchName) throws GitVcsException {
    requireRepository(root);
    if (branchName == null || branchName.isBlank()) throw new GitVcsException("Branch name cannot be empty.");
    CommandResult result = execute(root, List.of("git", "switch", "-c", branchName.trim()), false);
    ensureSuccess(result, "Failed to create branch '" + branchName + "'.");
    return result;
  }

  // --- Stage/unstage individual files ---

  public CommandResult stageFile(File root, String path) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "add", "--", path), false);
    ensureSuccess(result, "Failed to stage file: " + path);
    return result;
  }

  public CommandResult unstageFile(File root, String path) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "restore", "--staged", "--", path), false);
    ensureSuccess(result, "Failed to unstage file: " + path);
    return result;
  }

  public CommandResult discardFile(File root, String path) throws GitVcsException {
    requireRepository(root);
    CommandResult result = execute(root, List.of("git", "checkout", "--", path), false);
    ensureSuccess(result, "Failed to discard changes in: " + path);
    return result;
  }

  // --- Hardened push with upstream check ---

  public CommandResult pushSafe(File root) throws GitVcsException {
    requireRepository(root);
    if (!hasRemote(root)) {
      throw new GitVcsException("No remote configured. Add a remote before pushing.");
    }
    // Check if upstream is set
    CommandResult tracking = execute(root, List.of("git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"), true);
    if (!tracking.success()) {
      // No upstream set, push with -u
      String branch = getCurrentBranch(root);
      CommandResult push = execute(root, List.of("git", "push", "-u", "origin", branch), false);
      ensureSuccess(push, "Failed to push (setting upstream).");
      return push;
    }
    CommandResult push = execute(root, List.of("git", "push"), false);
    ensureSuccess(push, "Failed to push changes.");
    return push;
  }

  private void requireDirectory(File root) throws GitVcsException {
    if (root == null || !root.exists() || !root.isDirectory()) {
      throw new GitVcsException("Project root is not a valid directory.");
    }
  }

  private void requireRepository(File root) throws GitVcsException {
    requireDirectory(root);
    requireGitAvailable();
    if (!isRepository(root)) {
      throw new GitVcsException("No Git repository found at: " + root.getAbsolutePath());
    }
  }

  private void requireGitAvailable() throws GitVcsException {
    if (!isGitAvailable()) {
      throw new GitVcsException("Git is not available. Install Git and ensure it is on PATH.");
    }
  }

  private void requireGitLfsAvailable() throws GitVcsException {
    if (!isGitLfsAvailable()) {
      throw new GitVcsException("Git LFS is not available. Install Git LFS and ensure it is on PATH.");
    }
  }

  private void initRepositoryIfNeeded(File root) throws GitVcsException {
    if (isRepository(root)) return;

    CommandResult init = execute(root, List.of("git", "init", "--initial-branch=main"), true);
    if (!init.success()) {
      ensureSuccess(execute(root, List.of("git", "init"), false), "Failed to initialize git repository.");
      execute(root, List.of("git", "symbolic-ref", "HEAD", "refs/heads/main"), true);
    }
  }

  private String buildDefaultGitattributesBlock() {
    StringBuilder sb = new StringBuilder();
    for (String pattern : DEFAULT_LFS_PATTERNS) {
      sb.append(pattern).append(" filter=lfs diff=lfs merge=lfs -text\n");
    }
    sb.append("*.vns text eol=lf\n");
    sb.append("*.jes text eol=lf\n");
    sb.append("*.menu text eol=lf\n");
    sb.append("*.layout text eol=lf\n");
    sb.append("*.style text eol=lf\n");
    return sb.toString().trim();
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

  private RepositoryStatus parseStatus(String output) {
    String branch = "unknown";
    String upstream = null;
    int ahead = 0;
    int behind = 0;
    List<StatusEntry> entries = new ArrayList<>();

    if (output == null || output.isBlank()) {
      return new RepositoryStatus(branch, upstream, ahead, behind, entries);
    }

    List<String> lines = Arrays.asList(output.split("\\r?\\n"));
    int startLine = 0;

    if (!lines.isEmpty() && lines.get(0).startsWith("## ")) {
      String info = lines.get(0).substring(3).trim();
      startLine = 1;

      if (info.toLowerCase(Locale.ROOT).startsWith("no commits yet on ")) {
        branch = info.substring("No commits yet on ".length()).trim();
      } else if (info.startsWith("HEAD (")) {
        branch = "detached";
      } else {
        String branchPart = info;
        String relation = null;

        int relationStart = info.indexOf(" [");
        if (relationStart >= 0 && info.endsWith("]")) {
          branchPart = info.substring(0, relationStart);
          relation = info.substring(relationStart + 2, info.length() - 1);
        }

        int upstreamIndex = branchPart.indexOf("...");
        if (upstreamIndex >= 0) {
          branch = branchPart.substring(0, upstreamIndex);
          upstream = branchPart.substring(upstreamIndex + 3);
        } else {
          branch = branchPart;
        }

        if (relation != null) {
          String[] parts = relation.split(",");
          for (String part : parts) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (p.startsWith("ahead ")) {
              ahead = parseTrailingInt(p.substring("ahead ".length()));
            } else if (p.startsWith("behind ")) {
              behind = parseTrailingInt(p.substring("behind ".length()));
            }
          }
        }
      }
    }

    for (int i = startLine; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line == null || line.isBlank()) continue;
      if (line.length() < 3) continue;
      if (line.startsWith("!! ")) continue;

      String xy = line.substring(0, 2);
      String path = line.substring(3);
      if (path.contains(" -> ")) {
        String[] rename = path.split(" -> ", 2);
        if (rename.length == 2) {
          path = rename[0] + " -> " + rename[1];
        }
      }

      String index = String.valueOf(xy.charAt(0));
      String workTree = String.valueOf(xy.charAt(1));
      entries.add(new StatusEntry(index, workTree, path));
    }

    return new RepositoryStatus(branch, upstream, ahead, behind, entries);
  }

  private int parseTrailingInt(String raw) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ignored) {
      return 0;
    }
  }

  private String normalizeEol(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n');
  }

  private void ensureSuccess(CommandResult result, String message) throws GitVcsException {
    if (result != null && result.success()) return;
    throw new GitVcsException(message, result);
  }

  private CommandResult execute(File workingDir, List<String> command, boolean allowNonZero) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      if (workingDir != null) pb.directory(workingDir);
      pb.redirectErrorStream(true);

      Process process = pb.start();
      boolean finished = process.waitFor(commandTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new CommandResult(command, 124, "Command timed out after " + commandTimeout.toSeconds() + "s.");
      }

      int exit = process.exitValue();
      byte[] bytes = process.getInputStream().readAllBytes();
      String output = new String(bytes, StandardCharsets.UTF_8).trim();

      if (!allowNonZero && exit != 0 && output.isBlank()) {
        output = "Command failed with exit code " + exit + ".";
      }
      return new CommandResult(command, exit, output);
    } catch (Exception ex) {
      return new CommandResult(command, 126, ex.getMessage() == null ? "Failed to execute command." : ex.getMessage());
    }
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

    public GitVcsException(String message) {
      super(message);
      this.result = null;
    }

    public GitVcsException(String message, CommandResult result) {
      super(formatMessage(message, result));
      this.result = result;
    }

    public CommandResult getResult() {
      return result;
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
