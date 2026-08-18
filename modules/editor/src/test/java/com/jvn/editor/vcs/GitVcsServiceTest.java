package com.jvn.editor.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitVcsServiceTest {

  private final GitVcsService vcs = new GitVcsService();

  @TempDir
  Path tempDir;

  private File repoRoot;

  @BeforeEach
  void setUp() {
    assumeTrue(vcs.isGitAvailable(), "git must be on PATH to run these tests");
    repoRoot = tempDir.toFile();
  }

  @Test
  void preflightReportsBranchAndNoRemoteWhenRepositoryHasNoRemoteConfigured() throws Exception {
    initRepoWithCommit();

    GitVcsService.PreflightResult result = vcs.preflight(repoRoot);

    assertFalse(result.hasRemote());
    assertEquals(0, result.ahead());
    assertEquals(0, result.behind());
  }

  @Test
  void preflightReportsRemoteUrlWhenRemoteIsConfigured() throws Exception {
    initRepoWithCommit();
    File bareRemote = tempDir.resolve("remote.git").toFile();
    run("git", "init", "--bare", bareRemote.getAbsolutePath());
    vcs.addRemote(repoRoot, "origin", bareRemote.getAbsolutePath());

    GitVcsService.PreflightResult result = vcs.preflight(repoRoot);

    assertTrue(result.hasRemote());
    assertEquals(bareRemote.getAbsolutePath(), result.remoteUrl());
  }

  @Test
  void removeRemoteClearsAPreviouslyConfiguredRemote() throws Exception {
    initRepoWithCommit();
    File bareRemote = tempDir.resolve("remote.git").toFile();
    run("git", "init", "--bare", bareRemote.getAbsolutePath());
    vcs.addRemote(repoRoot, "origin", bareRemote.getAbsolutePath());
    assertTrue(vcs.hasRemote(repoRoot));

    vcs.removeRemote(repoRoot, "origin");

    assertFalse(vcs.hasRemote(repoRoot));
  }

  @Test
  void preflightMarksRemoteUnreachableWhenNoRemoteConfigured() throws Exception {
    initRepoWithCommit();

    GitVcsService.PreflightResult result = vcs.preflight(repoRoot);

    assertFalse(result.remoteReachable());
    assertEquals("No remote configured.", result.remoteCheckError());
  }

  @Test
  void preflightMarksRemoteUnreachableWhenRemotePathDoesNotExist() throws Exception {
    initRepoWithCommit();
    File missingRemote = tempDir.resolve("does-not-exist-remote").toFile();
    vcs.addRemote(repoRoot, "origin", missingRemote.getAbsolutePath());

    GitVcsService.PreflightResult result = vcs.preflight(repoRoot);

    assertFalse(result.remoteReachable());
    assertTrue(result.remoteCheckError() != null && !result.remoteCheckError().isBlank());
  }

  @Test
  void preflightDoesNotFlagCredentialIssueWhenRemotePathIsMerelyMissing() throws Exception {
    initRepoWithCommit();
    File missingRemote = tempDir.resolve("does-not-exist-remote").toFile();
    vcs.addRemote(repoRoot, "origin", missingRemote.getAbsolutePath());

    GitVcsService.PreflightResult result = vcs.preflight(repoRoot);

    assertTrue(result.credentialsOk());
    assertEquals(null, result.credentialIssue());
  }

  @Test
  void preflightMarksRemoteReachableWhenRemoteIsAValidBareRepository() throws Exception {
    initRepoWithCommit();
    File bareRemote = tempDir.resolve("remote.git").toFile();
    run("git", "init", "--bare", bareRemote.getAbsolutePath());
    vcs.addRemote(repoRoot, "origin", bareRemote.getAbsolutePath());

    GitVcsService.PreflightResult result = vcs.preflight(repoRoot);

    assertTrue(result.remoteReachable());
    assertEquals(null, result.remoteCheckError());
  }

  @Test
  void classifiesAuthenticationFailedMessageAsCredentialIssue() {
    assertTrue(GitVcsService.isCredentialFailure("fatal: Authentication failed for 'https://example.com/repo.git'"));
  }

  @Test
  void classifiesPermissionDeniedMessageAsCredentialIssue() {
    assertTrue(GitVcsService.isCredentialFailure("remote: Permission to user/repo.git denied to other-user."));
  }

  @Test
  void classifiesCouldNotReadUsernameMessageAsCredentialIssue() {
    assertTrue(GitVcsService.isCredentialFailure("fatal: could not read Username for 'https://example.com': terminal prompts disabled"));
  }

  @Test
  void doesNotClassifyGenericNetworkFailureAsCredentialIssue() {
    assertFalse(GitVcsService.isCredentialFailure("fatal: unable to access 'https://example.com/repo.git': Could not resolve host"));
  }

  @Test
  void authCooldownIsActiveImmediatelyAfterAKnownCredentialFailure() {
    long lastFailureMs = 1_000L;
    long now = 1_500L;
    long cooldownMs = 120_000L;

    assertTrue(GitVcsService.isAuthCooldownActive(lastFailureMs, now, cooldownMs));
  }

  @Test
  void authCooldownExpiresAfterTheCooldownWindowElapses() {
    long lastFailureMs = 1_000L;
    long now = 1_000L + 120_000L;
    long cooldownMs = 120_000L;

    assertFalse(GitVcsService.isAuthCooldownActive(lastFailureMs, now, cooldownMs));
  }

  @Test
  void authCooldownIsNotActiveWhenNoFailureHasBeenRecorded() {
    assertFalse(GitVcsService.isAuthCooldownActive(-1L, System.currentTimeMillis(), 120_000L));
  }

  @Test
  void preflightReportsChangedFileCount() throws Exception {
    initRepoWithCommit();
    Files.writeString(repoRoot.toPath().resolve("untracked.txt"), "new", StandardCharsets.UTF_8);

    GitVcsService.PreflightResult result = vcs.preflight(repoRoot);

    assertEquals(1, result.changedFileCount());
  }

  private void initRepoWithCommit() throws Exception {
    vcs.bootstrapRepository(repoRoot, false, null);
    run("git", "config", "user.email", "test@example.com");
    run("git", "config", "user.name", "Test User");
    Files.writeString(repoRoot.toPath().resolve("file.txt"), "hello", StandardCharsets.UTF_8);
    vcs.commitAll(repoRoot, "initial commit");
  }

  private void run(String... command) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(repoRoot);
    pb.redirectErrorStream(true);
    Process p = pb.start();
    p.getInputStream().readAllBytes();
    p.waitFor();
  }
}
