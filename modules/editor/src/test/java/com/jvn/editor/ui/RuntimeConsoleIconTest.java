package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class RuntimeConsoleIconTest {
  @Test
  void everyRuntimeCommandHasVisiblePurposeBuiltArtwork() throws Exception {
    for (RuntimeConsoleIcon.Kind kind : RuntimeConsoleIcon.Kind.values()) {
      WritableImage image = onFxThread(() -> {
        RuntimeConsoleIcon icon = RuntimeConsoleIcon.of(kind);
        Button button = new Button();
        icon.installButtonTreatment(button);
        StackPane root = new StackPane(icon);
        new Scene(root, 30, 30);
        root.applyCss();
        root.layout();
        assertEquals(kind, icon.kind());
        assertTrue(button.getStyleClass().contains("runtime-console-aero-button"));
        return root.snapshot(null, new WritableImage(30, 30));
      });
      assertTrue(nonTransparentPixels(image) > 60, kind + " should render visible vector artwork");
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
    return FxToolkit.runFx(work);
  }
}
