package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeveloperDiagnosticsExporterTest {
  @TempDir
  Path tempDir;

  @Test
  void exportsLogsAndDiagnosticsBundle() throws Exception {
    Path project = tempDir.resolve("game");
    Path jvnLogs = project.resolve(".jvn").resolve("logs");
    Path workspaceLogs = tempDir.resolve("workspace").resolve("logs");
    Files.createDirectories(jvnLogs);
    Files.createDirectories(workspaceLogs);
    Files.writeString(jvnLogs.resolve("editor-process.log"), "editor log");
    Files.writeString(project.resolve(".jvn").resolve("engine-audit-report.md"), "audit");
    Files.writeString(workspaceLogs.resolve("runtime.err"), "runtime log");

    Path destination = tempDir.resolve("desktop");
    DeveloperDiagnosticsExporter.ExportResult result =
        DeveloperDiagnosticsExporter.export(destination, "JVN Test", List.of(project, tempDir.resolve("workspace")));

    assertTrue(Files.isDirectory(result.bundleDir()));
    assertTrue(Files.isRegularFile(result.zipFile()));
    assertTrue(Files.isRegularFile(result.bundleDir().resolve("runtime-info.txt")));
    assertTrue(Files.isRegularFile(result.bundleDir().resolve("diagnostics-manifest.txt")));
    assertTrue(bundleContains(result.bundleDir(), "editor-process.log"));
    assertTrue(bundleContains(result.bundleDir(), "engine-audit-report.md"));
    assertTrue(bundleContains(result.bundleDir(), "runtime.err"));
    assertEquals(0, result.skippedFiles());
  }

  private static boolean bundleContains(Path bundleDir, String fileName) throws Exception {
    try (var stream = Files.walk(bundleDir)) {
      return stream.anyMatch(path -> path.getFileName().toString().equals(fileName));
    }
  }
}
