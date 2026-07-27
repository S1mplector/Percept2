package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEditorTabVnsLaunchTest {
  private static boolean toolkitAvailable;

  @BeforeAll
  static void startToolkit() throws Exception {
    if (System.getProperty("os.name", "").toLowerCase().contains("linux")
        && System.getenv().getOrDefault("DISPLAY", "").isBlank()) {
      return;
    }
    CountDownLatch ready = new CountDownLatch(1);
    try {
      Platform.startup(ready::countDown);
      toolkitAvailable = ready.await(5, TimeUnit.SECONDS);
    } catch (IllegalStateException alreadyStarted) {
      toolkitAvailable = true;
    } catch (RuntimeException unavailable) {
      toolkitAvailable = false;
    }
  }

  @Test
  void runningVnsFromTheStripMakesTheDetachedPreviewVisible(@TempDir Path tempDir)
      throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    Path script = tempDir.resolve("launch_test.vns");
    Files.writeString(
        script,
        """
        @scenario launch_test
        @character narrator ""
        @label start
        narrator: The preview is running.
        """);

    onFxThread(() -> {
      FileEditorTab tab = new FileEditorTab(script.toFile());
      try {
        tab.runFromLabel(null);
        assertTrue(tab.isDetachedPreviewVisible());
      } finally {
        tab.dispose();
      }
      return null;
    });
  }

  private static <T> T onFxThread(java.util.concurrent.Callable<T> work) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(() -> {
      try {
        result.set(work.call());
      } catch (Throwable error) {
        failure.set(error);
      } finally {
        done.countDown();
      }
    });
    assertTrue(done.await(15, TimeUnit.SECONDS), "JavaFX work timed out");
    if (failure.get() != null) throw new AssertionError(failure.get());
    return result.get();
  }
}
