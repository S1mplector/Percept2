package com.jvn.web;

import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

import com.jvn.render.RenderSurface;

/**
 * {@code RenderSurface} implementation for HTML5 canvas rendering in the browser.
 *
 * <p>This surface wraps a DOM canvas element and provides access to its 2D rendering context.</p>
 */
public class WebCanvasRenderSurface implements RenderSurface {

  private final String canvasElementId;
  private final HTMLCanvasElement canvas;
  private final double logicalWidth;
  private final double logicalHeight;
  private final double pixelScale;
  private boolean valid = true;

  /**
   * Construct a web canvas surface bound to a DOM element.
   *
   * @param canvasElementId the HTML ID of the canvas element
   */
  public WebCanvasRenderSurface(String canvasElementId) {
    this(canvasElementId, 0, 0);
  }

  /**
   * Construct and size a web canvas surface for an engine viewport.
   *
   * @param canvasElementId the HTML ID of the canvas element
   * @param width requested logical width, or {@code 0} to retain the canvas width
   * @param height requested logical height, or {@code 0} to retain the canvas height
   */
  public WebCanvasRenderSurface(String canvasElementId, int width, int height) {
    if (canvasElementId == null || canvasElementId.isBlank()) {
      throw new IllegalArgumentException("Canvas element ID must not be blank");
    }
    this.canvasElementId = canvasElementId;

    HTMLElement element = HTMLDocument.current().getElementById(canvasElementId);
    if (!(element instanceof HTMLCanvasElement canvasElement)) {
      throw new IllegalArgumentException(
          "Element '" + canvasElementId + "' does not exist or is not a canvas");
    }
    this.canvas = canvasElement;

    double browserScale = Window.current().getDevicePixelRatio();
    this.pixelScale = Double.isFinite(browserScale) ? Math.max(1.0, browserScale) : 1.0;
    this.logicalWidth = width > 0 ? width : Math.max(1.0, canvas.getWidth() / pixelScale);
    this.logicalHeight = height > 0 ? height : Math.max(1.0, canvas.getHeight() / pixelScale);
    configureBackingStore();
  }

  @Override
  public double getWidth() {
    return logicalWidth;
  }

  @Override
  public double getHeight() {
    return logicalHeight;
  }

  @Override
  public double getPixelScale() {
    return pixelScale;
  }

  @Override
  public void present() {
    // Canvas renders directly to screen; no explicit present needed
  }

  @Override
  public boolean isValid() {
    return valid;
  }

  @Override
  public void dispose() {
    valid = false;
  }

  /** Get the underlying HTML canvas element. */
  public HTMLCanvasElement getCanvasElement() {
    return canvas;
  }

  /** Get the canvas's 2D context. */
  public CanvasRenderingContext2D getContext2D() {
    CanvasRenderingContext2D context = (CanvasRenderingContext2D) canvas.getContext("2d");
    if (context == null) {
      throw new IllegalStateException(
          "Canvas '" + canvasElementId + "' does not provide a 2D rendering context");
    }
    return context;
  }

  private void configureBackingStore() {
    canvas.setWidth((int) Math.round(logicalWidth * pixelScale));
    canvas.setHeight((int) Math.round(logicalHeight * pixelScale));
    canvas.getStyle().setProperty("width", formatCssPixels(logicalWidth));
    canvas.getStyle().setProperty("height", formatCssPixels(logicalHeight));
  }

  private static String formatCssPixels(double value) {
    long rounded = Math.round(value);
    return rounded + "px";
  }
}
