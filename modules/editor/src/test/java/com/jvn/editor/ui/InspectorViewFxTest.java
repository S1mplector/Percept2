package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.Sprite2D;
import com.jvn.editor.commands.CommandStack;
import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.concurrent.Callable;
import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class InspectorViewFxTest {
  @Test
  void numericFieldsSkipNoOpsAndRestoreInvalidInput() throws Exception {
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
    return FxToolkit.runFx(callable);
  }
}
