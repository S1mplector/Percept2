package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jvn.editor.vcs.GitVcsService;

import org.junit.jupiter.api.Test;

class PreflightSummaryFormatterTest {

  @Test
  void formatsCleanRepositoryWithNoRemote() {
    GitVcsService.PreflightResult result = new GitVcsService.PreflightResult(
        "main", null, false, 0, 0, 0, false, "No remote configured.", true, null);

    var lines = PreflightSummaryFormatter.format(result);

    assertEquals("Branch: main", lines.get(0));
    assertEquals("Remote: not configured", lines.get(1));
    assertEquals("Sync: up to date", lines.get(2));
    assertEquals("Changes: none", lines.get(3));
    assertEquals("Connection: no remote configured", lines.get(4));
  }

  @Test
  void formatsRepositoryWithRemoteAheadBehindAndChanges() {
    GitVcsService.PreflightResult result = new GitVcsService.PreflightResult(
        "main", "https://example.com/repo.git", true, 2, 1, 5, true, null, true, null);

    var lines = PreflightSummaryFormatter.format(result);

    assertEquals("Branch: main", lines.get(0));
    assertEquals("Remote: https://example.com/repo.git", lines.get(1));
    assertEquals("Sync: 2 ahead, 1 behind", lines.get(2));
    assertEquals("Changes: 5 files", lines.get(3));
    assertEquals("Connection: OK", lines.get(4));
  }

  @Test
  void formatsCredentialIssueDistinctlyFromGenericConnectionFailure() {
    GitVcsService.PreflightResult result = new GitVcsService.PreflightResult(
        "main", "https://example.com/repo.git", true, 0, 0, 1, false,
        "fatal: Authentication failed", false, "fatal: Authentication failed");

    var lines = PreflightSummaryFormatter.format(result);

    assertEquals("Connection: sign-in required", lines.get(4));
  }
}
