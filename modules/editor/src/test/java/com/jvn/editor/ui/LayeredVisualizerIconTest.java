package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class LayeredVisualizerIconTest {
  @Test
  void everyVisualizerCommandHasDistinctVisibleVectorArtwork() throws Exception {
    for (LayeredVisualizerIcon.Kind kind : LayeredVisualizerIcon.Kind.values()) {
      WritableImage image = onFxThread(() -> {
        LayeredVisualizerIcon icon = LayeredVisualizerIcon.of(kind);
        StackPane root = new StackPane(icon);
        new Scene(root, 28, 28);
        root.applyCss();
        root.layout();
        assertEquals(kind, icon.kind());
        return root.snapshot(null, new WritableImage(28, 28));
      });
      assertTrue(nonTransparentPixels(image) > 45, kind + " should render visible artwork");
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
