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
class RefreshIconTest {
  @Test
  void standardRefreshArtworkScalesAndRenders() throws Exception {
    for (double size : new double[] {14, 18, 24, 32}) {
      WritableImage image = FxToolkit.runFx(() -> {
        RefreshIcon icon = RefreshIcon.of(size);
        StackPane root = new StackPane(icon);
        new Scene(root, 40, 40);
        root.applyCss();
        root.layout();
        assertEquals(size, icon.iconSize());
        return root.snapshot(null, new WritableImage(40, 40));
      });
      assertTrue(nonTransparentPixels(image) > 50, "Refresh artwork should remain visible at " + size);
    }
  }

  @Test
  void compactAndAeroRegistriesUseTheSharedRefreshIcon() throws Exception {
    FxToolkit.runFx(() -> {
      assertInstanceOf(RefreshIcon.class, CssIcon.refresh("#ff0000"));
      AeroIcon aero = AeroIcon.of(AeroIcon.Kind.REFRESH, 24);
      StackPane root = new StackPane(aero);
      new Scene(root, 32, 32);
      root.applyCss();
      root.layout();
      assertTrue(containsRefreshIcon(aero));
      return null;
    });
  }

  private static boolean containsRefreshIcon(javafx.scene.Parent parent) {
    for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
      if (child instanceof RefreshIcon) return true;
      if (child instanceof javafx.scene.Parent nested && containsRefreshIcon(nested)) return true;
    }
    return false;
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
