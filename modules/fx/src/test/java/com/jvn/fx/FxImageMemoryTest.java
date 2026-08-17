package com.jvn.fx;

import com.jvn.core.assets.BoundedImageCache;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxImageMemoryTest {

  @Test
  void estimatesArgbRasterStorageFromActualJavaFxImage() {
    Image image = new WritableImage(513, 257);

    assertEquals(513L * 257L * 4L, FxImageMemory.estimatedBytes(image));
  }

  @Test
  void animatedJavaFxRastersRemainWithinPreviewBudget() {
    long budgetBytes = 64L * 1024L * 1024L;
    long frameBytes = 1024L * 1024L * 4L;
    BoundedImageCache<Image> cache =
        new BoundedImageCache<>(256, budgetBytes, FxImageMemory::estimatedBytes);

    // A position-dependent cache key models a moving, lit character in VNS preview.
    // The old entry-only policy retained up to 1 GiB for this image size.
    for (int frame = 0; frame < 300; frame++) {
      cache.put("character-x-" + frame, new WritableImage(1024, 1024));
      assertTrue(cache.currentWeight() <= budgetBytes);
    }

    assertEquals(16, cache.size());
    assertEquals(16L * frameBytes, cache.currentWeight());
    assertNull(cache.get("character-x-0"));
  }
}
