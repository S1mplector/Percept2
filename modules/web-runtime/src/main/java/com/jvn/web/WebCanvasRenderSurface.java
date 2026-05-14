package com.jvn.web;

import com.jvn.render.RenderSurface;

/**
 * {@code RenderSurface} implementation for HTML5 canvas rendering in the browser.
 *
 * <p>This surface wraps a DOM canvas element and provides access to its 2D rendering context.</p>
 */
public class WebCanvasRenderSurface implements RenderSurface {

  private final String canvasElementId;
  private double cachedWidth;
  private double cachedHeight;
  private double pixelScale = 1.0;
  private boolean valid = true;

  /**
   * Construct a web canvas surface bound to a DOM element.
   *
   * @param canvasElementId the HTML ID of the canvas element
   */
  public WebCanvasRenderSurface(String canvasElementId) {
    this.canvasElementId = canvasElementId;
    this.cachedWidth = getCanvasWidth();
    this.cachedHeight = getCanvasHeight();
  }

  @Override
  public double getWidth() {
    return cachedWidth;
  }

  @Override
  public double getHeight() {
    return cachedHeight;
  }

  @Override
  public double getPixelScale() {
    return pixelScale;
  }

  /**
   * Set the device pixel ratio (e.g., 2.0 for Retina displays).
   */
  public void setPixelScale(double scale) {
    this.pixelScale = Math.max(1.0, scale);
  }

  @Override
  public void present() {
    // Canvas renders directly to screen; no explicit present needed
  }

  @Override
  public boolean isValid() {
    return valid && getCanvasElement() != null;
  }

  @Override
  public void dispose() {
    valid = false;
  }

  /**
   * Get the underlying HTML canvas element (for native JS interop).
   */
  public Object getCanvasElement() {
    return getCanvasElementNative(canvasElementId);
  }

  private double getCanvasWidth() {
    return getCanvasWidthNative(canvasElementId);
  }

  private double getCanvasHeight() {
    return getCanvasHeightNative(canvasElementId);
  }

  // Native JS interop methods (implemented via TeaVM JSO)
  private static native Object getCanvasElementNative(String elementId) /*-{
    return document.getElementById(elementId);
  }-*/;

  private static native double getCanvasWidthNative(String elementId) /*-{
    var canvas = document.getElementById(elementId);
    return canvas ? canvas.width : 0;
  }-*/;

  private static native double getCanvasHeightNative(String elementId) /*-{
    var canvas = document.getElementById(elementId);
    return canvas ? canvas.height : 0;
  }-*/;
}
