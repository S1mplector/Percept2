package com.jvn.swing;

import com.jvn.testkit.render.Blitter2DContract;
import com.jvn.testkit.render.Blitter2DContract.Rgba;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class SwingBlitter2DContractTest {
  private BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);

  @Test
  void satisfiesSharedPixelContract() {
    resetImage();

    Blitter2DContract.assertPixelContract(
        this::newBlitter,
        this::pixelAt,
        this::resetImage);
  }

  @Test
  void measuresTextAndBoldTextConsistently() {
    resetImage();
    SwingBlitter2D b = newBlitter();

    double regular = b.measureTextWidth("JVN", 18.0, false);
    double bold = b.measureTextWidth("JVN", 18.0, true);
    double longer = b.measureTextWidth("JVN renderer", 18.0, false);

    org.junit.jupiter.api.Assertions.assertTrue(regular > 0.0);
    org.junit.jupiter.api.Assertions.assertTrue(bold >= regular);
    org.junit.jupiter.api.Assertions.assertTrue(longer > regular);
  }

  private SwingBlitter2D newBlitter() {
    Graphics2D g = image.createGraphics();
    return new SwingBlitter2D(g);
  }

  private void resetImage() {
    image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
  }

  private Rgba pixelAt(int x, int y) {
    int argb = image.getRGB(x, y);
    return new Rgba(
        (argb >> 16) & 0xff,
        (argb >> 8) & 0xff,
        argb & 0xff,
        (argb >>> 24) & 0xff);
  }
}
