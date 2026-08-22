package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class AlertIconTest {
  @Test
  void sharedWarningFactoryRendersPurposeBuiltCautionBadge() throws Exception {
    WritableImage image = onFxThread(() -> {
      AlertIcon icon = assertInstanceOf(AlertIcon.class, CssIcon.warning("#f2c86b"));
      StackPane root = new StackPane(icon);
      new Scene(root, 28, 28);
      root.applyCss();
      root.layout();
      assertEquals(16, icon.iconSize());
      return root.snapshot(null, new WritableImage(28, 28));
    });
    assertTrue(nonTransparentPixels(image) > 90);
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
