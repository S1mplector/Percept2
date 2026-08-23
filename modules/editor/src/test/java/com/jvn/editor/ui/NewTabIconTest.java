package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class NewTabIconTest {
  @Test
  void rendersPlainPlusAtCompactSize() throws Exception {
    WritableImage image = onFxThread(() -> {
      NewTabIcon icon = NewTabIcon.compact();
      StackPane root = new StackPane(icon);
      new Scene(root, 28, 28);
      root.applyCss();
      root.layout();
      assertEquals(20, icon.iconSize());
      Group artwork = (Group) icon.getChildren().get(0);
      assertEquals(2, artwork.getChildren().size());
      assertTrue(artwork.getChildren().get(1) instanceof SVGPath);
      return root.snapshot(null, new WritableImage(28, 28));
    });
    assertTrue(nonTransparentPixels(image) > 20);
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
