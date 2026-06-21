package com.jvn.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Compositor2D;
import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RenderTarget2D;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class SwingCompositor2DTest {

  @Test
  void compositesAnOffscreenTargetIntoTheDestination() {
    BufferedImage output = image();
    SwingBlitter2D destination = blitter(output);

    try (Compositor2D compositor = new Compositor2D(destination)) {
      RenderTarget2D target = compositor.renderToTarget(32, 32, 1.0, b -> {
        b.clear(0, 0, 0, 0);
        b.setFill(1, 0, 0, 1);
        b.fillRect(0, 0, 32, 32);
      });

      destination.clear(0, 0, 0, 1);
      compositor.composite(target, 0, 0, 32, 32);

      assertColor(output, 8, 8, 255, 0, 0);
      assertTrue(destination.supports(RenderFeature.OFFSCREEN_RENDER_TARGETS));
    }
  }

  @Test
  void crossFadesBetweenTargets() {
    BufferedImage output = image();
    SwingBlitter2D destination = blitter(output);

    try (Compositor2D compositor = new Compositor2D(destination)) {
      RenderTarget2D red = solid(compositor, 1, 0, 0);
      RenderTarget2D blue = solid(compositor, 0, 0, 1);
      destination.clear(0, 0, 0, 1);

      compositor.crossFade(red, blue, 0.5, 0, 0, 32, 32);

      int argb = output.getRGB(8, 8);
      assertEquals(128, (argb >> 16) & 0xff, 2);
      assertEquals(128, argb & 0xff, 2);
    }
  }

  @Test
  void appliesAnAlphaMaskInPlaceAndDisposesOwnedTargets() {
    BufferedImage output = image();
    SwingBlitter2D destination = blitter(output);
    RenderTarget2D content;
    RenderTarget2D mask;

    try (Compositor2D compositor = new Compositor2D(destination)) {
      content = solid(compositor, 1, 0, 0);
      mask = compositor.renderToTarget(32, 32, 1.0, b -> {
        b.clear(0, 0, 0, 0);
        b.setFill(1, 1, 1, 1);
        b.fillRect(0, 0, 16, 32);
      });

      compositor.applyAlphaMask(content, mask);
      destination.clear(0, 0, 0, 1);
      compositor.composite(content, 0, 0, 32, 32);

      assertColor(output, 8, 8, 255, 0, 0);
      assertColor(output, 24, 8, 0, 0, 0);
    }

    assertFalse(content.isValid());
    assertFalse(mask.isValid());
  }

  private static RenderTarget2D solid(Compositor2D compositor, double r, double g, double b) {
    return compositor.renderToTarget(32, 32, 1.0, target -> {
      target.clear(0, 0, 0, 0);
      target.setFill(r, g, b, 1);
      target.fillRect(0, 0, 32, 32);
    });
  }

  private static BufferedImage image() {
    return new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
  }

  private static SwingBlitter2D blitter(BufferedImage image) {
    Graphics2D graphics = image.createGraphics();
    SwingBlitter2D blitter = new SwingBlitter2D(graphics);
    graphics.dispose();
    return blitter;
  }

  private static void assertColor(
      BufferedImage image,
      int x,
      int y,
      int expectedR,
      int expectedG,
      int expectedB
  ) {
    int argb = image.getRGB(x, y);
    assertEquals(expectedR, (argb >> 16) & 0xff, 2);
    assertEquals(expectedG, (argb >> 8) & 0xff, 2);
    assertEquals(expectedB, argb & 0xff, 2);
  }
}
