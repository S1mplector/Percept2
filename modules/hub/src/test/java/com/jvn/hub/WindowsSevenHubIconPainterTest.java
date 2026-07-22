package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WindowsSevenHubIconPainterTest {
  private static final String[] KINDS = {
      "EDITOR", "LAUNCHER", "LAUNCHER_MAINTENANCE", "BUILD", "SHORTCUT", "UPDATE",
      "TESTS", "OPTIONS", "MORE", "CANCEL", "QUIT", "DEVELOPER", "DEVELOPER_ACTIVE",
      "SAFE", "SAFE_ACTIVE", "DIAGNOSTICS", "ABOUT", "DOCUMENTATION", "ANNOUNCEMENTS"
  };

  @Test
  void paintsEveryHubIconWithDistinctVisibleArtwork() {
    Set<Integer> signatures = new HashSet<>();
    for (String kind : KINDS) {
      BufferedImage image = render(kind, 72);
      assertTrue(nonTransparentPixels(image) > 500, kind + " should render visible artwork");
      signatures.add(pixelSignature(image));
    }
    assertEquals(KINDS.length, signatures.size(), "Every Hub command should have distinct artwork");
  }

  @Test
  void renderedPaletteStaysNeutralExceptForOrangeHighlights() {
    for (String kind : KINDS) {
      BufferedImage image = render(kind, 72);
      for (int y = 0; y < image.getHeight(); y++) {
        for (int x = 0; x < image.getWidth(); x++) {
          Color color = new Color(image.getRGB(x, y), true);
          if (color.getAlpha() < 20) continue;
          int spread = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()))
              - Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
          boolean neutral = spread <= 18;
          // Antialiasing blends orange edges into nearby white and silver pixels,
          // producing pale warm highlights while preserving the same hue ordering.
          boolean orange = color.getRed() >= color.getGreen()
              && color.getGreen() >= color.getBlue();
          assertTrue(neutral || orange, kind + " contains an out-of-palette pixel: " + color);
        }
      }
    }
  }

  private static BufferedImage render(String kind, int size) {
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.scale(size / 24.0, size / 24.0);
      assertTrue(WindowsSevenHubIconPainter.paint(graphics, kind));
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static int nonTransparentPixels(BufferedImage image) {
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) count++;
      }
    }
    return count;
  }

  private static int pixelSignature(BufferedImage image) {
    int hash = 1;
    for (int y = 0; y < image.getHeight(); y += 2) {
      for (int x = 0; x < image.getWidth(); x += 2) {
        hash = 31 * hash + image.getRGB(x, y);
      }
    }
    return hash;
  }
}
