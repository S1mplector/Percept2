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

class PuppeteerAeroIconTest {
  private static boolean toolkitAvailable;

  @BeforeAll
  static void startToolkit() throws Exception {
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
  void everyKeyframeCommandRendersACompactAeroSurface() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    for (PuppeteerAeroIcon.Kind kind : PuppeteerAeroIcon.Kind.values()) {
      WritableImage image = onFxThread(() -> {
        PuppeteerAeroIcon icon = PuppeteerAeroIcon.of(kind);
        StackPane root = new StackPane(icon);
        new Scene(root, 32, 32);
        root.applyCss();
        root.layout();
        return root.snapshot(null, new WritableImage(32, 32));
      });
      assertTrue(nonTransparentPixels(image) > 180, kind + " should render a visible Aero plate and glyph");
    }
  }

  @Test
  void sizeIsClampedAndKindIsPreserved() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    PuppeteerAeroIcon small = onFxThread(() -> PuppeteerAeroIcon.of(PuppeteerAeroIcon.Kind.RIPPLE, 2));
    PuppeteerAeroIcon large = onFxThread(() -> PuppeteerAeroIcon.of(PuppeteerAeroIcon.Kind.RIPPLE, 200));
    assertEquals(18, small.iconSize());
    assertEquals(28, large.iconSize());
    assertEquals(PuppeteerAeroIcon.Kind.RIPPLE, small.kind());
    assertTrue(small.isCache());
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
      try { result.set(work.call()); }
      catch (Throwable error) { failure.set(error); }
      finally { done.countDown(); }
    });
    assertTrue(done.await(10, TimeUnit.SECONDS), "JavaFX work timed out");
    if (failure.get() != null) throw new AssertionError(failure.get());
    return result.get();
  }
}
