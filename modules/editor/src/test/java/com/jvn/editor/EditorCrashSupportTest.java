package com.jvn.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorCrashSupportTest {

  @TempDir
  Path tempDir;

  @Test
  void buildsReadableCrashReport() {
    IllegalStateException failure = new IllegalStateException("Puppeteer exploded");

    String report = EditorCrashSupport.buildCrashReport(failure, new Thread("fx-test-thread"));

    assertTrue(report.contains("JVN Editor Crash Report"));
    assertTrue(report.contains("fx-test-thread"));
    assertTrue(report.contains("Puppeteer exploded"));
    assertTrue(report.contains(IllegalStateException.class.getName()));
  }

  @Test
  void writesCrashLogFile() throws Exception {
    Path previousHome = Path.of(System.getProperty("user.home"));
    System.setProperty("user.home", tempDir.toString());
    try {
      Path written = EditorCrashSupport.writeCrashLog(
          new RuntimeException("boom"),
          new Thread("worker-thread"));
      assertNotNull(written);
      assertTrue(Files.isRegularFile(written));
      String content = Files.readString(written, StandardCharsets.UTF_8);
      assertTrue(content.contains("worker-thread"));
      assertTrue(content.contains("boom"));
    } finally {
      System.setProperty("user.home", previousHome.toString());
    }
  }
}
