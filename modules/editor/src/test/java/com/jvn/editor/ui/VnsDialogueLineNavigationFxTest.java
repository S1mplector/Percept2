package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class VnsDialogueLineNavigationFxTest {
  private static final String SCRIPT =
      "@label start\n"
          + "Alice: Hello there\n"
          + "timeline {\n"
          + "  move \"hero\" {\n"
          + "    x: 100\n"
          + "    dur: 200\n"
          + "  }\n"
          + "}\n"
          + "Bob: General Kenobi\n"
          + "\n"
          + "timeline {\n"
          + "  move \"hero\" {\n"
          + "    x: 0\n"
          + "    dur: 100\n"
          + "  }\n"
          + "}\n"
          + "Alice: You are a bold one\n";

  @Test
  void jumpToNextDialogueLineSkipsTimelineBody() throws Exception {
    runFx(() -> {
      VnsCodeEditor editor = new VnsCodeEditor();
      editor.setText(SCRIPT);
      editor.goToLine(1);

      editor.jumpToNextDialogueLine();
      assertEquals(1, editor.getCurrentLine());

      editor.jumpToNextDialogueLine();
      assertEquals(8, editor.getCurrentLine());

      editor.jumpToNextDialogueLine();
      assertEquals(16, editor.getCurrentLine());
      return null;
    });
  }

  @Test
  void jumpToNextDialogueLineWrapsAroundAtEndOfDocument() throws Exception {
    runFx(() -> {
      VnsCodeEditor editor = new VnsCodeEditor();
      editor.setText(SCRIPT);
      editor.goToLine(17);

      editor.jumpToNextDialogueLine();
      assertEquals(1, editor.getCurrentLine());
      return null;
    });
  }

  @Test
  void jumpToPreviousDialogueLineSkipsTimelineBodyAndWraps() throws Exception {
    runFx(() -> {
      VnsCodeEditor editor = new VnsCodeEditor();
      editor.setText(SCRIPT);
      editor.goToLine(1);

      editor.jumpToPreviousDialogueLine();
      assertEquals(16, editor.getCurrentLine());

      editor.jumpToPreviousDialogueLine();
      assertEquals(8, editor.getCurrentLine());

      editor.jumpToPreviousDialogueLine();
      assertEquals(1, editor.getCurrentLine());
      return null;
    });
  }

  private static <T> T runFx(Callable<T> callable) throws Exception {
    return FxToolkit.runFx(callable);
  }
}
