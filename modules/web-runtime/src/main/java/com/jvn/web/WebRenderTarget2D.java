package com.jvn.web;

import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.canvas.ImageData;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.typedarrays.Uint8ClampedArray;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderTarget2D;

/**
 * Detached-canvas offscreen render target for {@link WebRenderer}, mirroring the desktop
 * {@code FxRenderTarget2D}/{@code FxBlitter2D} pattern. The backing canvas is created via
 * {@code document.createElement("canvas")} and never attached to the DOM.
 */
final class WebRenderTarget2D implements RenderTarget2D {
  private final double width;
  private final double height;
  private final double pixelScale;
  private final HTMLCanvasElement canvas;
  private final CanvasRenderingContext2D context;
  private final WebRenderer blitter;
  private boolean valid = true;

  WebRenderTarget2D(double width, double height, double pixelScale, WebImageCache sharedImageCache) {
    validateDimensions(width, height, pixelScale);
    this.width = width;
    this.height = height;
    this.pixelScale = pixelScale;
    this.canvas = (HTMLCanvasElement) HTMLDocument.current().createElement("canvas");
    this.canvas.setWidth((int) Math.ceil(width * pixelScale));
    this.canvas.setHeight((int) Math.ceil(height * pixelScale));
    this.context = (CanvasRenderingContext2D) canvas.getContext("2d");
    this.blitter = new WebRenderer(context, width, height, pixelScale, sharedImageCache);
  }

  HTMLCanvasElement getCanvas() {
    ensureValid();
    return canvas;
  }

  @Override
  public double getWidth() { return width; }

  @Override
  public double getHeight() { return height; }

  @Override
  public double getPixelScale() { return pixelScale; }

  @Override
  public Blitter2D getBlitter() {
    ensureValid();
    return blitter;
  }

  @Override
  public boolean isValid() { return valid; }

  @Override
  public void dispose() { valid = false; }

  @Override
  public int[] readPixelsArgb() {
    ensureValid();
    int w = (int) Math.ceil(width * pixelScale);
    int h = (int) Math.ceil(height * pixelScale);
    ImageData data = context.getImageData(0, 0, w, h);
    Uint8ClampedArray bytes = data.getData();
    int[] argb = new int[w * h];
    for (int i = 0; i < argb.length; i++) {
      int o = i * 4;
      int r = bytes.get(o) & 0xFF;
      int g = bytes.get(o + 1) & 0xFF;
      int b = bytes.get(o + 2) & 0xFF;
      int a = bytes.get(o + 3) & 0xFF;
      argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    return argb;
  }

  @Override
  public void writePixelsArgb(int[] argb) {
    ensureValid();
    int w = (int) Math.ceil(width * pixelScale);
    int h = (int) Math.ceil(height * pixelScale);
    if (argb.length != w * h) {
      throw new IllegalArgumentException(
          "Expected " + (w * h) + " packed ARGB pixels but got " + argb.length);
    }
    Uint8ClampedArray bytes = new Uint8ClampedArray(w * h * 4);
    for (int i = 0; i < argb.length; i++) {
      int px = argb[i];
      int o = i * 4;
      bytes.set(o, (px >> 16) & 0xFF);
      bytes.set(o + 1, (px >> 8) & 0xFF);
      bytes.set(o + 2, px & 0xFF);
      bytes.set(o + 3, (px >> 24) & 0xFF);
    }
    ImageData data = new ImageData(bytes, w, h);
    context.putImageData(data, 0, 0);
  }

  private void ensureValid() {
    if (!valid) throw new IllegalStateException("Render target has been disposed");
  }

  private static void validateDimensions(double width, double height, double pixelScale) {
    if (!Double.isFinite(width) || width <= 0.0
        || !Double.isFinite(height) || height <= 0.0
        || !Double.isFinite(pixelScale) || pixelScale <= 0.0) {
      throw new IllegalArgumentException(
          "Render target dimensions and pixel scale must be positive and finite");
    }
  }
}
