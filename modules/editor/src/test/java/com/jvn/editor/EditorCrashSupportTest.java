package com.jvn.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

  @Test
  void recognizesOnlyTheKnownJavaFxToolbarTraversalFailure() {
    NullPointerException toolbarFailure = new NullPointerException("item");
    toolbarFailure.setStackTrace(new StackTraceElement[] {
        new StackTraceElement(
            "javafx.scene.control.skin.ToolBarSkin$1", "select", "ToolBarSkin.java", 195),
        new StackTraceElement(
            "javafx.scene.Scene$ScenePulseListener", "focusCleanup", "Scene.java", 2568)
    });

    assertTrue(EditorCrashSupport.isRecoverableJavaFxToolbarTraversalBug(toolbarFailure));
    assertFalse(EditorCrashSupport.isRecoverableJavaFxToolbarTraversalBug(
        new NullPointerException("ordinary application bug")));
    assertFalse(EditorCrashSupport.isRecoverableJavaFxToolbarTraversalBug(
        new IllegalStateException("not the upstream NPE")));
  }

  @Test
  void recognizesWrappedOutOfMemoryFailures() {
    assertTrue(EditorCrashSupport.containsOutOfMemory(
        new RuntimeException("preview failed", new OutOfMemoryError("Java heap space"))));
    assertFalse(EditorCrashSupport.containsOutOfMemory(new RuntimeException("ordinary failure")));
  }
}
