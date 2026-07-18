package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GamePackageIconServiceTest {
  @TempDir Path tempDir;

  @Test
  void rejectsSmallOpaqueAndNonSquareSources() throws Exception {
    Path small = writePng("small.png", 256, 256, true);
    Path opaque = writePng("opaque.png", 512, 512, false);
    Path wide = writePng("wide.png", 640, 512, true);

    assertThrows(Exception.class, () -> GamePackageIconService.readAndValidate(small.toFile()));
    assertThrows(Exception.class, () -> GamePackageIconService.readAndValidate(opaque.toFile()));
    assertThrows(Exception.class, () -> GamePackageIconService.readAndValidate(wide.toFile()));
  }

  @Test
  void generatesPngBackedWindowsIconAndStoresSource() throws Exception {
    Path source = writePng("source.png", 512, 512, true);

    GamePackageIconService.IconResult result =
        GamePackageIconService.install(source.toFile(), tempDir.toFile(), "windows");

    assertEquals("icon-source.png", result.source().getName());
    assertEquals("icon.ico", result.platformIcon().getName());
    byte[] bytes = Files.readAllBytes(result.platformIcon().toPath());
    assertTrue(bytes.length > 22);
    assertEquals(0, bytes[0]);
    assertEquals(1, bytes[2]);
    assertEquals((byte) 0x89, bytes[22]);
    assertEquals('P', bytes[23]);
    assertEquals('N', bytes[24]);
    assertEquals('G', bytes[25]);
  }

  private Path writePng(String name, int width, int height, boolean transparent) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    if (!transparent) {
      int[] pixels = new int[width * height];
      java.util.Arrays.fill(pixels, 0xff00ff00);
      image.setRGB(0, 0, width, height, pixels, 0, width);
    } else {
      image.setRGB(width / 2, height / 2, 0xff00ff00);
    }
    Path path = tempDir.resolve(name);
    ImageIO.write(image, "png", path.toFile());
    return path;
  }
}
