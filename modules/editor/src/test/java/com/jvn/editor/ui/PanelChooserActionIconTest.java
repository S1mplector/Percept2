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
class PanelChooserActionIconTest {
  @Test
  void everyPanelActionHasVisibleVectorArtwork() throws Exception {
    for (PanelChooserActionIcon.Kind kind : PanelChooserActionIcon.Kind.values()) {
      WritableImage image = FxToolkit.runFx(() -> {
        PanelChooserActionIcon icon = PanelChooserActionIcon.of(kind);
        StackPane root = new StackPane(icon);
        new Scene(root, 28, 28);
        root.applyCss();
        root.layout();
        assertEquals(kind, icon.kind());
        assertEquals(20.0, icon.getPrefWidth());
        return root.snapshot(null, new WritableImage(28, 28));
      });
      assertTrue(nonTransparentPixels(image) > 90, kind + " should render detailed vector artwork");
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
}
