package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntitySelectorFxTest {
  private static boolean toolkitAvailable;

  @BeforeAll
  static void startToolkit() throws Exception {
    if (System.getProperty("os.name", "").toLowerCase().contains("linux")
        && System.getenv().getOrDefault("DISPLAY", "").isBlank()) {
      return;
    }
    try {
      CountDownLatch ready = new CountDownLatch(1);
      Platform.startup(ready::countDown);
      toolkitAvailable = ready.await(10, TimeUnit.SECONDS);
    } catch (IllegalStateException alreadyStarted) {
      toolkitAvailable = true;
    } catch (RuntimeException unavailable) {
      toolkitAvailable = false;
    }
  }

  @Test
  void supportsMultipleSelectedEntities() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");

    List<String> selected = runFx(() -> {
      AnimationProject project = new AnimationProject();
      project.getOrCreateTrack("body");
      project.getOrCreateTrack("eyes");
      project.getOrCreateTrack("mouth");

      EntitySelector selector = new EntitySelector();
      selector.refresh(project);
      // Mirrors Puppeteer's timeline acknowledgement after a picker selection.
      selector.setOnSelectionChanged((name, group) -> {
        if (group) selector.selectGroup(name);
        else selector.selectEntity(name);
      });
      selector.selectEntities(List.of("body", "eyes", "mouth"));
      return selector.getSelectedEntityNames();
    });

    assertEquals(List.of("body", "eyes", "mouth"), selected);
  }

  private static <T> T runFx(java.util.concurrent.Callable<T> callable) throws Exception {
    FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    assertTrue(task.get(30, TimeUnit.SECONDS) != null);
    return task.get();
  }
}
