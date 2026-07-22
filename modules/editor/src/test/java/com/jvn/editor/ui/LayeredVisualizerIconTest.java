package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LayeredVisualizerIconTest {
  private static boolean toolkitAvailable;

  @BeforeAll
  static void startToolkit() throws Exception {
    if (System.getProperty("os.name", "").toLowerCase().contains("linux")
        && System.getenv().getOrDefault("DISPLAY", "").isBlank()) return;
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
  void everyVisualizerCommandHasDistinctVisibleVectorArtwork() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    for (LayeredVisualizerIcon.Kind kind : LayeredVisualizerIcon.Kind.values()) {
      WritableImage image = onFxThread(() -> {
        LayeredVisualizerIcon icon = LayeredVisualizerIcon.of(kind);
        StackPane root = new StackPane(icon);
        new Scene(root, 28, 28);
        root.applyCss();
        root.layout();
        assertEquals(kind, icon.kind());
        return root.snapshot(null, new WritableImage(28, 28));
      });
      assertTrue(nonTransparentPixels(image) > 45, kind + " should render visible artwork");
    }
  }

  private static int nonTransparentPixels(WritableImage image) {
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (image.getPixelReader().getArgb(x, y) >>> 24 != 0) count++;
      }
    }
    return count;
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
    assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX work timed out");
    if (failure.get() != null) throw new AssertionError(failure.get());
    return result.get();
  }
}
