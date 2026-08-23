package com.jvn.editor.ui;

import java.util.List;

import com.jvn.editor.vcs.GitVcsService;

final class PreflightSummaryFormatter {

  private PreflightSummaryFormatter() {}

  static List<String> format(GitVcsService.PreflightResult result) {
    return List.of(
        "Branch: " + result.branch(),
        "Remote: " + (result.hasRemote() ? result.remoteUrl() : "not configured"),
        "Sync: " + syncText(result),
        "Changes: " + changesText(result),
        "Connection: " + connectionText(result));
  }

  private static String syncText(GitVcsService.PreflightResult result) {
    if (result.ahead() == 0 && result.behind() == 0) return "up to date";
    if (result.ahead() > 0 && result.behind() > 0) {
      return result.ahead() + " ahead, " + result.behind() + " behind";
    }
    if (result.ahead() > 0) return result.ahead() + " ahead";
    return result.behind() + " behind";
  }

  private static String changesText(GitVcsService.PreflightResult result) {
    if (result.changedFileCount() == 0) return "none";
    return result.changedFileCount() + (result.changedFileCount() == 1 ? " file" : " files");
  }

  private static String connectionText(GitVcsService.PreflightResult result) {
    if (!result.hasRemote()) return "no remote configured";
    if (!result.credentialsOk()) return "sign-in required";
    if (result.remoteReachable()) return "OK";
    return "could not reach remote";
  }
}
