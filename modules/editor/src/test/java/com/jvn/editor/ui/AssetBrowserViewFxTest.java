package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetBrowserViewFxTest {
  private static boolean toolkitAvailable;

  @BeforeAll
  static void startToolkit() {
    if (System.getProperty("os.name", "").toLowerCase().contains("linux")
        && System.getenv().getOrDefault("DISPLAY", "").isBlank()) return;
    try {
      CountDownLatch ready = new CountDownLatch(1);
      Platform.startup(ready::countDown);
      toolkitAvailable = ready.await(10, TimeUnit.SECONDS);
    } catch (IllegalStateException alreadyStarted) {
      toolkitAvailable = true;
    } catch (Exception unavailable) {
      toolkitAvailable = false;
    }
  }

  @Test
  void selectingOnlyPreviewsAndUseAssetIsExplicit(@TempDir Path project) throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    Files.createDirectories(project.resolve("assets/characters"));
    Files.writeString(project.resolve("assets/characters/hero.png"), "placeholder");

    runFx(() -> {
      AssetBrowserView view = new AssetBrowserView();
      AtomicInteger uses = new AtomicInteger();
      view.setOnAssetSelected(path -> uses.incrementAndGet());
      view.setProjectRoot(project.toFile());

      @SuppressWarnings("unchecked")
      ListView<Object> assets = (ListView<Object>) view.lookup(".list-view");
      assets.getSelectionModel().select(0);
      assertEquals(0, uses.get(), "browsing must not insert an asset path");

      Button use = view.lookupAll(".button").stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .filter(button -> "Use Asset".equals(button.getText()))
          .findFirst()
          .orElseThrow();
      assertFalse(use.isDisabled());
      use.fire();
      assertEquals(1, uses.get());
      return null;
    });
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    return task.get(30, TimeUnit.SECONDS);
  }
}
