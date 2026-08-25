package com.jvn.fx.scene2d;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderTarget2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/** JavaFX Canvas-backed offscreen render target. */
final class FxRenderTarget2D implements RenderTarget2D {
  private final double width;
  private final double height;
  private final double pixelScale;
  private final Canvas canvas;
  private final FxBlitter2D blitter;
  private boolean valid = true;
  private boolean snapshotDirty = true;
  private WritableImage cachedSnapshot;

  FxRenderTarget2D(double width, double height, double pixelScale) {
    validateDimensions(width, height, pixelScale);
    this.width = width;
    this.height = height;
    this.pixelScale = pixelScale;
    this.canvas = new Canvas(Math.ceil(width * pixelScale), Math.ceil(height * pixelScale));
    this.canvas.getGraphicsContext2D().scale(pixelScale, pixelScale);
    this.blitter = new FxBlitter2D(canvas.getGraphicsContext2D());
    this.blitter.setViewport(width, height);
    this.blitter.attachRenderTarget(this);
  }

  WritableImage snapshot() {
    ensureValid();
    if (!snapshotDirty && cachedSnapshot != null) return cachedSnapshot;
    cachedSnapshot = new WritableImage(
        Math.max(1, (int) Math.ceil(canvas.getWidth())),
        Math.max(1, (int) Math.ceil(canvas.getHeight())));
    SnapshotParameters parameters = new SnapshotParameters();
    parameters.setFill(Color.TRANSPARENT);
    canvas.snapshot(parameters, cachedSnapshot);
    snapshotDirty = false;
    return cachedSnapshot;
  }

  void markDirty() { snapshotDirty = true; }

  @Override public double getWidth() { return width; }
  @Override public double getHeight() { return height; }
  @Override public double getPixelScale() { return pixelScale; }
  @Override public Blitter2D getBlitter() { ensureValid(); return blitter; }
  @Override public boolean isValid() { return valid; }
  @Override public void dispose() { valid = false; cachedSnapshot = null; }

  @Override
  public int[] readPixelsArgb() {
    ensureValid();
    WritableImage image = snapshot();
    int w = (int) Math.ceil(image.getWidth());
    int h = (int) Math.ceil(image.getHeight());
    int[] pixels = new int[w * h];
    PixelReader reader = image.getPixelReader();
    reader.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
    return pixels;
  }

  @Override
  public void writePixelsArgb(int[] argb) {
    ensureValid();
    int w = Math.max(1, (int) Math.ceil(canvas.getWidth()));
    int h = Math.max(1, (int) Math.ceil(canvas.getHeight()));
    if (argb.length != w * h) {
      throw new IllegalArgumentException(
          "Expected " + (w * h) + " packed ARGB pixels but got " + argb.length);
    }
    WritableImage image = new WritableImage(w, h);
    PixelWriter writer = image.getPixelWriter();
    writer.setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
    canvas.getGraphicsContext2D().save();
    canvas.getGraphicsContext2D().setTransform(1, 0, 0, 1, 0, 0);
    canvas.getGraphicsContext2D().setImageSmoothing(false);
    canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    canvas.getGraphicsContext2D().drawImage(image, 0, 0);
    canvas.getGraphicsContext2D().restore();
    markDirty();
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
