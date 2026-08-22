package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FxToolkitExtension.class)
class ProjectExplorerViewFxTest {
  @Test
  void headerActionsAndKeyboardNavigationAreAvailable(@TempDir Path project) throws Exception {
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
    return FxToolkit.runFx(callable);
  }
}
