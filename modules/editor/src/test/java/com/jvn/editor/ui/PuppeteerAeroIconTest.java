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
class PuppeteerAeroIconTest {
  @Test
  void everyKeyframeCommandRendersACompactWindows7Object() throws Exception {
    for (PuppeteerAeroIcon.Kind kind : PuppeteerAeroIcon.Kind.values()) {
      WritableImage image = onFxThread(() -> {
        PuppeteerAeroIcon icon = PuppeteerAeroIcon.of(kind);
        StackPane root = new StackPane(icon);
        new Scene(root, 32, 32);
        root.applyCss();
        root.layout();
        return root.snapshot(null, new WritableImage(32, 32));
      });
      assertTrue(nonTransparentPixels(image) > 120, kind + " should render a visible Aero object and glyph");
    }
  }

  @Test
  void everyCommandUsesAFunctionSpecificPhysicalShell() throws Exception {
    onFxThread(() -> {
      for (PuppeteerAeroIcon.Kind kind : PuppeteerAeroIcon.Kind.values()) {
        PuppeteerAeroIcon icon = PuppeteerAeroIcon.of(kind);
        assertTrue(icon.getChildren().getFirst().getStyleClass()
            .contains("jvn-puppeteer-bespoke-shell"));
      }
      return null;
    });
  }

  @Test
  void sizeIsClampedAndKindIsPreserved() throws Exception {
    PuppeteerAeroIcon small = onFxThread(() -> PuppeteerAeroIcon.of(PuppeteerAeroIcon.Kind.RIPPLE, 2));
    PuppeteerAeroIcon large = onFxThread(() -> PuppeteerAeroIcon.of(PuppeteerAeroIcon.Kind.RIPPLE, 200));
    assertEquals(18, small.iconSize());
    assertEquals(28, large.iconSize());
    assertEquals(PuppeteerAeroIcon.Kind.RIPPLE, small.kind());
    assertTrue(small.isCache());
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
