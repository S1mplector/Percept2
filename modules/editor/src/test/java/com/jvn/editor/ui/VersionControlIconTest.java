package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.fx.testkit.FxToolkit;
import com.jvn.fx.testkit.FxToolkitExtension;
import java.util.HashSet;
import java.util.Set;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FxToolkitExtension.class)
class VersionControlIconTest {
  @Test
  void everyVersionControlRoleRendersDetailedVectorArtwork() throws Exception {
    Set<Long> artworkHashes = new HashSet<>();
    for (VersionControlIcon.Kind kind : VersionControlIcon.Kind.values()) {
      WritableImage image = FxToolkit.runFx(() -> {
        VersionControlIcon icon = VersionControlIcon.of(kind, 22);
        StackPane root = new StackPane(icon);
        new Scene(root, 32, 32);
        root.applyCss();
        root.layout();
        assertEquals(kind, icon.kind());
        assertEquals(22.0, icon.iconSize());
        return root.snapshot(null, new WritableImage(32, 32));
      });
      assertTrue(nonTransparentPixels(image) > 65, kind + " should render visible vector artwork");
      artworkHashes.add(pixelHash(image));
    }
    assertEquals(
        VersionControlIcon.Kind.values().length,
        artworkHashes.size(),
        "Every Version Control action should retain a distinct silhouette");
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

  private static long pixelHash(WritableImage image) {
    long hash = 0xcbf29ce484222325L;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        hash ^= image.getPixelReader().getArgb(x, y);
        hash *= 0x100000001b3L;
      }
    }
    return hash;
  }
}
