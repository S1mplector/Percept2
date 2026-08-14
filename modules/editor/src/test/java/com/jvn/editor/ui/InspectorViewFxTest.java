package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.Sprite2D;
import com.jvn.editor.commands.CommandStack;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InspectorViewFxTest {
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
  void numericFieldsSkipNoOpsAndRestoreInvalidInput() throws Exception {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    runFx(() -> {
      CommandStack commands = new CommandStack();
      Sprite2D sprite = new Sprite2D("assets/hero.png", 100, 200);
      sprite.setPosition(5, 6);
      InspectorView view = new InspectorView(message -> {});
      view.setCommandStack(commands);
      view.setSelection(sprite);

      TextField x = view.lookupAll(".text-field").stream()
          .filter(TextField.class::isInstance)
          .map(TextField.class::cast)
          .filter(field -> "X".equals(field.getAccessibleText()))
          .findFirst()
          .orElseThrow();

      x.fireEvent(new ActionEvent());
      assertFalse(commands.canUndo(), "the current value must not create an undo entry");

      x.setText("12");
      x.fireEvent(new ActionEvent());
      assertEquals(12, sprite.getX());
      assertTrue(commands.canUndo());

      commands.undo();
      assertEquals(5, sprite.getX());
      x.setText("NaN");
      x.fireEvent(new ActionEvent());
      assertEquals("5.0", x.getText());
      assertEquals(5, sprite.getX());
      return null;
    });
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    return task.get(30, TimeUnit.SECONDS);
  }
}
