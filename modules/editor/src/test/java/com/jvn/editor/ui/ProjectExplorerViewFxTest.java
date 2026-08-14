package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectExplorerViewFxTest {
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
  void headerActionsAndKeyboardNavigationAreAvailable(@TempDir Path project) throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    Path script = Files.writeString(project.resolve("intro.vns"), "@label start\n[end]\n");

    runFx(() -> {
      ProjectExplorerView view = new ProjectExplorerView();
      AtomicInteger opens = new AtomicInteger();
      view.setOnOpenFile(file -> {
        assertEquals(script.toFile(), file);
        opens.incrementAndGet();
      });
      view.setRootDirectory(project.toFile());

      assertTrue(view.lookupAll(".help-button").size() == 1);
      assertTrue(view.lookupAll(".button").stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .anyMatch(button -> "Refresh".equals(button.getText())));

      @SuppressWarnings("unchecked")
      TreeView<java.io.File> tree = (TreeView<java.io.File>) view.lookup(".project-explorer-tree");
      TreeItem<java.io.File> fileItem = tree.getRoot().getChildren().stream()
          .filter(item -> script.toFile().equals(item.getValue()))
          .findFirst()
          .orElseThrow();
      tree.getSelectionModel().select(fileItem);
      tree.fireEvent(keyPressed(KeyCode.ENTER));
      assertEquals(1, opens.get());

      TextField filter = (TextField) view.lookup(".project-explorer-filter");
      filter.setText("intro");
      filter.fireEvent(keyPressed(KeyCode.ESCAPE));
      assertEquals("", filter.getText());
      return null;
    });
  }

  private static KeyEvent keyPressed(KeyCode code) {
    return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    return task.get(30, TimeUnit.SECONDS);
  }
}
