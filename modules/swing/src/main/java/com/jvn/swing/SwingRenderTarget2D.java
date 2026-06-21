package com.jvn.swing;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderTarget2D;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** BufferedImage-backed offscreen render target for the Swing renderer. */
final class SwingRenderTarget2D implements RenderTarget2D {
  private final double width;
  private final double height;
  private final double pixelScale;
  private final BufferedImage image;
  private final SwingBlitter2D blitter;
  private boolean valid = true;

  SwingRenderTarget2D(double width, double height, double pixelScale) {
    validateDimensions(width, height, pixelScale);
    this.width = width;
    this.height = height;
    this.pixelScale = pixelScale;
    int physicalWidth = Math.max(1, (int) Math.ceil(width * pixelScale));
    int physicalHeight = Math.max(1, (int) Math.ceil(height * pixelScale));
    this.image = new BufferedImage(physicalWidth, physicalHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    graphics.scale(pixelScale, pixelScale);
    this.blitter = new SwingBlitter2D(graphics);
    this.blitter.attachRenderTarget(this);
    graphics.dispose();
  }

  BufferedImage image() {
    ensureValid();
    return image;
  }

  @Override public double getWidth() { return width; }
  @Override public double getHeight() { return height; }
  @Override public double getPixelScale() { return pixelScale; }
  @Override public Blitter2D getBlitter() { ensureValid(); return blitter; }
  @Override public boolean isValid() { return valid; }

  @Override
  public void dispose() {
    if (!valid) return;
    valid = false;
    blitter.dispose();
    image.flush();
  }

  private void ensureValid() {
    if (!valid) throw new IllegalStateException("Render target has been disposed");
  }

  private static void validateDimensions(double width, double height, double pixelScale) {
    if (!Double.isFinite(width) || width <= 0.0
        || !Double.isFinite(height) || height <= 0.0
        || !Double.isFinite(pixelScale) || pixelScale <= 0.0) {
      throw new IllegalArgumentException("Render target dimensions and pixel scale must be positive and finite");
    }
  }
}
